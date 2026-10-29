package com.flowveo.prompter.claude

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The only part of the app that uses the internet. Uses the
 * official Anthropic Java SDK with the user's own API key; nothing is sent until the user
 * taps Polish (or saves a key, which lists the models to check it).
 */
@Singleton
class ClaudeClient @Inject constructor() {

    /** The models this key can use, newest first. */
    suspend fun listModels(apiKey: String): Result<List<ModelOption>> = call(apiKey) { client ->
        val models = client.models().list().autoPager().map { m ->
            ModelOption(m.id(), m.displayName(), m.createdAt().toEpochSecond())
        }
        ModelChoice.sorted(models.toList())
    }

    /** Rewrites [script] with [preset]; returns the cleaned script text. */
    suspend fun polish(apiKey: String, model: String, script: String, preset: PolishPreset): Result<String> =
        call(apiKey) { client ->
            val params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16_000L)
                .system(PolishPrompts.SYSTEM)
                .addUserMessage(PolishPrompts.userMessage(script, preset))
                // A rewrite needs some care but not deep reasoning.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                .build()
            val response = client.messages().create(params)
            if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
                throw PolishException("Claude declined to rewrite this script.")
            }
            val text = response.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")
            val cleaned = PolishPrompts.cleanResponse(text)
            if (cleaned.isBlank()) throw PolishException("Claude returned an empty script. Try again.")
            if (response.stopReason().orElse(null) == StopReason.MAX_TOKENS) {
                throw PolishException("The script is too long to polish in one go. Try a shorter script.")
            }
            cleaned
        }

    /** Runs [block] off the main thread with a short-lived client and turns errors into readable messages. */
    private suspend fun <T> call(apiKey: String, block: (AnthropicClient) -> T): Result<T> = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        try {
            Result.success(block(client))
        } catch (e: PolishException) {
            Result.failure(e)
        } catch (e: UnauthorizedException) {
            Result.failure(PolishException("The API key was rejected. Check it in Settings › Claude."))
        } catch (e: RateLimitException) {
            Result.failure(PolishException("Too many requests right now. Wait a minute and try again."))
        } catch (e: AnthropicServiceException) {
            val msg = when (e.statusCode()) {
                404 -> "That model isn't available for this key. Pick another in Settings › Claude."
                529, 503 -> "Claude is busy right now. Try again in a moment."
                else -> "Claude returned an error (${e.statusCode()}). Try again."
            }
            Result.failure(PolishException(msg))
        } catch (e: AnthropicIoException) {
            Result.failure(PolishException("Couldn't reach Claude. Check your internet connection."))
        } catch (e: Exception) {
            Result.failure(PolishException("Something went wrong: ${e.message ?: e.javaClass.simpleName}"))
        } finally {
            client.close()
        }
    }
}

class PolishException(message: String) : Exception(message)
