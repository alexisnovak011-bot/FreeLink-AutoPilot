package com.freelink.autopilot

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.SystemClock
import android.os.Handler
import android.os.Looper

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

    private val handler = Handler(Looper.getMainLooper())
    private var sawGeneration = false
    private var sentForThisGeneration = false
    private var sendPending = false
    private var lastActionAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val active = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(KEY_ACTIVE, false)
        if (!active) {
            sawGeneration = false
            sentForThisGeneration = false
            sendPending = false
            return
        }

        if (event.packageName?.toString() != CHATGPT_PACKAGE) return
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

        if (sawGeneration && !sentForThisGeneration && !sendPending &&
            SystemClock.elapsedRealtime() - lastActionAt > 2500L) {
            queueDotAndSend(root)
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

    private fun queueDotAndSend(root: AccessibilityNodeInfo) {
        val edit = findEditable(root) ?: return
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, ".")
        }
        if (!edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return

        // ChatGPT can rebuild the composer/button after the text changes.
        // Re-read the accessibility tree after a short delay instead of clicking
        // a potentially stale button node from the previous tree.
        sendPending = true
        lastActionAt = SystemClock.elapsedRealtime()
        handler.postDelayed({
            val freshRoot = rootInActiveWindow
            var sent = false
            if (freshRoot != null) {
                val send = findSendButton(freshRoot)
                if (send != null) {
                    sent = send.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                if (!sent) {
                    val freshEdit = findEditable(freshRoot)
                    sent = freshEdit?.performAction(AccessibilityNodeInfo.ACTION_IME_ENTER) == true
                }
            }

            sendPending = false
            if (sent) {
                sentForThisGeneration = true
                sawGeneration = false
                lastActionAt = SystemClock.elapsedRealtime()
            }
        }, 600L)
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
        val labelMatches = listOf(
            "send", "отправ", "submit", "послать"
        ).any { desc.contains(it) || text.contains(it) }
        if (labelMatches && node.isClickable && node.isEnabled) return node

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
