package com.freelink.autopilot

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.SystemClock
import android.graphics.Rect
import kotlin.math.abs

/**
 * Automates the official ChatGPT UI only while it is in the foreground.
 * Waits for generation to stop, inserts one dot, then watches UI changes
 * until the enabled send-arrow button appears.
 */
class ChatGptAccessibilityService : AccessibilityService() {
    companion object {
        const val PREFS = "autopilot"
        const val KEY_ACTIVE = "active"
        @Volatile var requestCheck = false
        private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    }

    private var sawGeneration = false
    private var awaitingSendArrow = false
    private var sentForThisGeneration = false
    private var lastActionAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val active = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(KEY_ACTIVE, false)
        if (!active) {
            resetCycle()
            return
        }
        if (event.packageName?.toString() != CHATGPT_PACKAGE) return

        val root = rootInActiveWindow ?: return
        val labels = collectLabels(root).joinToString(" ").lowercase()
        val generating = isGenerating(labels)

        if (generating) {
            sawGeneration = true
            awaitingSendArrow = false
            sentForThisGeneration = false
            return
        }

        // The stop/pause control has disappeared: put the dot in the composer once.
        if (sawGeneration && !awaitingSendArrow && !sentForThisGeneration) {
            if (SystemClock.elapsedRealtime() - lastActionAt < 1000L) return
            val edit = findEditable(root) ?: return
            val args = android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "."
                )
            }
            if (edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                awaitingSendArrow = true
                lastActionAt = SystemClock.elapsedRealtime()
            }
            return
        }

        // Do not guess or press Enter. Keep observing accessibility events until
        // ChatGPT exposes its enabled send arrow after the composer updates.
        if (awaitingSendArrow && !sentForThisGeneration && !generating) {
            val edit = findEditable(root)
            val send = findSendButton(root)
                ?: if (edit != null) findSpatialSendButton(root, edit) else null
            if (send != null && send.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                sentForThisGeneration = true
                awaitingSendArrow = false
                sawGeneration = false
                lastActionAt = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun resetCycle() {
        sawGeneration = false
        awaitingSendArrow = false
        sentForThisGeneration = false
    }

    private fun isGenerating(labels: String): Boolean {
        return listOf(
            "stop generating", "остановить генерацию", "остановить",
            "прервать", "stop response", "pause"
        ).any { labels.contains(it) }
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
        val resource = (node.viewIdResourceName ?: "").lowercase()
        val labelMatches = listOf(
            "send", "отправ", "submit", "послать"
        ).any { desc.contains(it) || text.contains(it) || resource.contains(it) }

        if (labelMatches && node.isClickable && node.isEnabled) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSendButton(child)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    /**
     * Fallback for ChatGPT builds where the send arrow has no accessible label.
     * The current mobile composer places the send arrow after the microphone,
     * so choose the rightmost enabled clickable control on the composer row.
     */
    private fun findSpatialSendButton(root: AccessibilityNodeInfo, edit: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val editBounds = Rect()
        edit.getBoundsInScreen(editBounds)
        if (editBounds.isEmpty) return null
        val density = resources.displayMetrics.density
        val rowTolerance = (64 * density).toInt()
        val rightSearch = (180 * density).toInt()
        var best: AccessibilityNodeInfo? = null
        var bestRight = Int.MIN_VALUE

        fun visit(node: AccessibilityNodeInfo) {
            val desc = (node.contentDescription?.toString() ?: "").lowercase()
            val text = (node.text?.toString() ?: "").lowercase()
            val resource = (node.viewIdResourceName ?: "").lowercase()
            val combined = desc + " " + text + " " + resource
            val voiceControl = listOf("microphone", "mic", "voice", "диктофон", "микрофон").any { combined.contains(it) }
            if (node.isClickable && node.isEnabled && !node.isEditable && !voiceControl) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val sameRow = abs(bounds.centerY() - editBounds.centerY()) <= rowTolerance
                val nearEnd = bounds.centerX() >= editBounds.right - rightSearch
                val sensibleSize = bounds.width() in (20 * density).toInt()..(120 * density).toInt() &&
                    bounds.height() in (20 * density).toInt()..(120 * density).toInt()
                if (!bounds.isEmpty && sameRow && nearEnd && sensibleSize && bounds.right > bestRight) {
                    best?.recycle()
                    best = AccessibilityNodeInfo.obtain(node)
                    bestRight = bounds.right
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                visit(child)
                child.recycle()
            }
        }
        visit(root)
        return best
    }

    override fun onInterrupt() {
        // No action. User can always disable the service in Android settings.
    }
}
