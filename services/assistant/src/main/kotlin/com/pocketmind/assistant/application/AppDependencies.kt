package com.pocketmind.assistant.application

import com.pocketmind.assistant.agent.tools.AssistantReadToolRegistryFactory
import com.pocketmind.assistant.auth.SupabaseTokenVerifier
import com.pocketmind.assistant.config.AssistantConfig
import com.pocketmind.assistant.domain.finance.FinancialContextRepository
import com.pocketmind.assistant.domain.memory.AssistantMemoryRepository
import com.pocketmind.assistant.domain.turn.AssistantTurnHandler
import com.pocketmind.assistant.infrastructure.openai.KoogRuntimeFactory

data class AppDependencies(
    val config: AssistantConfig,
    val tokenVerifier: SupabaseTokenVerifier,
    val koogRuntimeFactory: KoogRuntimeFactory,
    val memoryRepository: AssistantMemoryRepository,
    val financialContextRepository: FinancialContextRepository,
    val readToolRegistryFactory: AssistantReadToolRegistryFactory,
    val turnHandler: AssistantTurnHandler,
)
