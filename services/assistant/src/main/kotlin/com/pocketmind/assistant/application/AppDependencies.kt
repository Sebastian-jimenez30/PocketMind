package com.pocketmind.assistant.application

import com.pocketmind.assistant.auth.SupabaseTokenVerifier
import com.pocketmind.assistant.config.AssistantConfig
import com.pocketmind.assistant.infrastructure.openai.KoogRuntimeFactory

data class AppDependencies(
    val config: AssistantConfig,
    val tokenVerifier: SupabaseTokenVerifier,
    val koogRuntimeFactory: KoogRuntimeFactory,
)
