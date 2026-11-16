package com.openjarvis.agent

import android.content.Context
import com.openjarvis.accessibility.JarvisAccessibilityService
import com.openjarvis.accessibility.ScreenReader
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.llm.UniversalAdapter
import kotlinx.coroutines.delay

class SelfHealingExecutor(private val context: Context) {
    private val screenReader = ScreenReader(context)
    private val graphifyRepo = GraphifyRepository(context)
    private val llm = UniversalAdapter(context)
    private val maxAttempts = 3
    private val baseDelayMs = 1000L

    suspend fun executeWithHealing(
        action: Action,
        context: ExecutionContext,
        attempt: Int = 1
    ): ActionResult {
        val result = tryExecuteAction(action)
        if (result.success) return result
        if (attempt >= maxAttempts) return ActionResult.Failed("Could not complete after $maxAttempts attempts")

        val currentScreen = screenReader.extractAllText()
        val prompt = buildHealingPrompt(action, context, currentScreen, attempt)
        val alternativeResponse = llm.complete(prompt.first, prompt.second).getOrNull()
        delay(baseDelayMs * attempt)

        if (!alternativeResponse.isNullOrBlank()) {
            val alternative = parseActionFromLLM(alternativeResponse)
            if (alternative != null) return executeWithHealing(alternative, context, attempt + 1)
        }
        return executeWithHealing(action, context, attempt + 1)
    }

    private fun tryExecuteAction(action: Action): ActionResult {
        return try {
            val service = JarvisAccessibilityService.instance
            when (action.action) {
                Action.TAP ->
                    if (service?.tapByText(action.text ?: "") == true) ActionResult.Success("Tapped ${action.text}")
                    else ActionResult.Failed("Tap failed")
                Action.TYPE ->
                    if (service?.typeText(action.value ?: "") == true) ActionResult.Success("Typed text")
                    else ActionResult.Failed("Typing failed")
                Action.OPEN_APP ->
                    if (service?.openAppByPackage(action.packageName ?: "") == true) ActionResult.Success("Opened app")
                    else ActionResult.Failed("Could not open app")
                Action.PRESS_BACK ->
                    if (service?.pressBack() == true) ActionResult.Success("Pressed back")
                    else ActionResult.Failed("Back action failed")
                else -> ActionResult.Failed("Unsupported action: ${action.action}")
            }
        } catch (e: Exception) {
            ActionResult.Failed(e.message ?: "Unknown error")
        }
    }

    private fun buildHealingPrompt(
        action: Action,
        context: ExecutionContext,
        currentScreen: String,
        attempt: Int
    ): Pair<String, String> {
        val system = """
Action failed: ${action.action}
Expected to see: ${context.expectedState ?: "task completion"}
Current screen shows: ${currentScreen.take(500)}
Attempt: $attempt/$maxAttempts

What went wrong and what alternative action should I try?
Respond with a single alternative Action JSON.
        """.trimIndent()
        val user = """
The action failed. Current screen: "$currentScreen"
Give me one alternative action that might work.
        """.trimIndent()
        return system to user
    }

    private fun parseActionFromLLM(response: String): Action? = try {
        val json = org.json.JSONObject(response.trim())
        Action(
            action = json.getString("action"),
            text = json.optString("text", null),
            value = json.optString("value", null),
            packageName = json.optString("package", null)
        )
    } catch (_: Exception) {
        null
    }

    data class ExecutionContext(
        val expectedState: String?,
        val phaseId: String,
        val previousPhaseOutcome: String?,
        val screenBefore: String
    )

    sealed class ActionResult {
        val success: Boolean get() = this is Success
        data class Success(val message: String) : ActionResult()
        data class Failed(val reason: String) : ActionResult()
    }
}
