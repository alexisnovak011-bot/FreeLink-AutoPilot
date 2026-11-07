package com.freelink.autopilot

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.SystemClock

/**
 * MVP prototype. ChatGPT UI labels and accessibility tree can change.
 * This service deliberately only acts while the official ChatGPT app is foregrounded.
 */
class ChatGptAccessibilityService : AccessibilityService() {
    companion object {
        const val PREFS = "autopilot"
        const val KEY_ACTIVE = "active"
        @Volatile var requestCheck = false
        private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    }

    private var sawGeneration = false
    private var sentForThisGeneration = false
    private var lastActionAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val active = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(KEY_ACTIVE, false)
        if (!active) {
            sawGeneration = false
            sentForThisGeneration = false
            return
        }

        val pkg = event.packageName?.toString()
        if (pkg != CHATGPT_PACKAGE) return

        val root = rootInActiveWindow ?: return
        val labels = collectLabels(root).joinToString(" ").lowercase()
        val generating = listOf(
            "stop generating", "остановить генерацию", "остановить",
            "прервать", "stop response"
        ).any { labels.contains(it) }

        if (generating) {
            sawGeneration = true
            sentForThisGeneration = false
            return
        }

        if (sawGeneration && !sentForThisGeneration &&
            SystemClock.elapsedRealtime() - lastActionAt > 2500L) {
            if (sendDot(root)) {
                sentForThisGeneration = true
                sawGeneration = false
                lastActionAt = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun collectLabels(node: AccessibilityNodeInfo): List<String> {
        val out = mutableListOf<String>()
        node.text?.toString()?.let { out.add(it) }
        node.contentDescription?.toString()?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                out.addAll(collectLabels(child))
                child.recycle()
            }
        }
        return out
    }

    private fun sendDot(root: AccessibilityNodeInfo): Boolean {
        val edit = findEditable(root) ?: return false
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, ".")
        }
        if (!edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
        val send = findSendButton(root) ?: return false
        return send.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isEnabled) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditable(child)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = (node.contentDescription?.toString() ?: "").lowercase()
        val text = (node.text?.toString() ?: "").lowercase()
        val candidate = (desc.contains("send") || desc.contains("отправ") ||
            text == "send" || text == "отправить") && node.isClickable && node.isEnabled
        if (candidate) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSendButton(child)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    override fun onInterrupt() {
        // No action. User can always disable the service in Android settings.
    }
}
