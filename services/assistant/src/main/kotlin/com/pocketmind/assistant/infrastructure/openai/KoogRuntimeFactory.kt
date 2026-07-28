package com.pocketmind.assistant.infrastructure.openai

import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
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
    internal fun createPromptExecutor(): MultiLLMPromptExecutor =
        MultiLLMPromptExecutor(
            OpenAILLMClient(config.openAiApiKey.reveal()),
        )
}
