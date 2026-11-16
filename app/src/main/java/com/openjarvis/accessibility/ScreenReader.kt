package com.openjarvis.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

class ScreenReader private constructor(
    private val serviceProvider: () -> JarvisAccessibilityService?
) {
    constructor(service: JarvisAccessibilityService) : this({ service })
    constructor(context: Context) : this({ JarvisAccessibilityService.instance })

    fun extractAllText(): String {
        val root = serviceProvider()?.rootInActiveWindow ?: return ""
        val output = StringBuilder()
        extractTextRecursive(root, output)
        root.recycle()
        return output.toString().trim()
    }

    private fun extractTextRecursive(node: AccessibilityNodeInfo, output: StringBuilder) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { output.append(it).append(' ') }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { output.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                extractTextRecursive(child, output)
                child.recycle()
            }
        }
    }

    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = serviceProvider()?.rootInActiveWindow ?: return null
        val result = findNodeRecursive(root, text)
        root.recycle()
        return result
    }

    private fun findNodeRecursive(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val needle = text.lowercase()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val nodeText = node.text?.toString()?.lowercase()
            val description = node.contentDescription?.toString()?.lowercase()
            if (nodeText?.contains(needle) == true || description?.contains(needle) == true) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
            node.recycle()
        }
        return null
    }

    fun findNodeByHint(hint: String): AccessibilityNodeInfo? {
        val root = serviceProvider()?.rootInActiveWindow ?: return null
        val needle = hint.lowercase()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.hintText?.toString()?.lowercase()?.contains(needle) == true) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
            node.recycle()
        }
        return null
    }

    fun getFocusedNode(): AccessibilityNodeInfo? {
        val root = serviceProvider()?.rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        root.recycle()
        return focused
    }
}
