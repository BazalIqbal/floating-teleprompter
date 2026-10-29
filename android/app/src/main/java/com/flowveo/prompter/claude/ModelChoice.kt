package com.flowveo.prompter.claude

/** One model from the user's account (Settings › Claude › Model). */
data class ModelOption(val id: String, val name: String, val createdAtEpochSec: Long)

/**
 * No model id is hard-coded in the app: the list comes from the API with the
 * user's key, and the default is the newest Sonnet (the spec's "latest Sonnet").
 */
object ModelChoice {

    fun pickDefault(models: List<ModelOption>): ModelOption? =
        models.filter { it.id.contains("sonnet", ignoreCase = true) }.maxByOrNull { it.createdAtEpochSec }
            ?: models.maxByOrNull { it.createdAtEpochSec }

    /** Newest first, for the picker. */
    fun sorted(models: List<ModelOption>) = models.sortedByDescending { it.createdAtEpochSec }
}
