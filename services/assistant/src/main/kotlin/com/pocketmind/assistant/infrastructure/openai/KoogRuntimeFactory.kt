package com.pocketmind.assistant.infrastructure.openai

import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import com.pocketmind.assistant.config.AssistantConfig

/**
 * Single construction boundary for Koog.
 *
 * Agent graphs will depend on this factory instead of constructing OpenAI
 * clients directly. Creating it performs no network request.
 */
class KoogRuntimeFactory(
    private val config: AssistantConfig,
) {
    internal val primaryModelId: String
        get() = config.primaryModel

    internal val fallbackModelId: String
        get() = config.fallbackModel

    internal fun createPromptExecutor(): MultiLLMPromptExecutor =
        MultiLLMPromptExecutor(
            OpenAILLMClient(config.openAiApiKey.reveal()),
        )

    internal fun resolveModel(modelId: String): LLModel = when (modelId) {
        OpenAIModels.Chat.GPT4oMini.id -> OpenAIModels.Chat.GPT4oMini
        OpenAIModels.Chat.GPT4o.id -> OpenAIModels.Chat.GPT4o
        else -> error(
            "Unsupported PocketMind agent model. " +
                "Use gpt-4o-mini or gpt-4o.",
        )
    }
}
