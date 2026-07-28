package com.pocketmind.assistant

import com.pocketmind.assistant.agent.chat.KoogAssistantTurnInterpreter
import com.pocketmind.assistant.agent.tools.AssistantReadToolRegistryFactory
import com.pocketmind.assistant.application.AppDependencies
import com.pocketmind.assistant.application.assistantModule
import com.pocketmind.assistant.config.AssistantConfig
import com.pocketmind.assistant.domain.finance.FinancialReadServiceFactory
import com.pocketmind.assistant.domain.turn.AssistantTurnService
import com.pocketmind.assistant.infrastructure.openai.KoogRuntimeFactory
import com.pocketmind.assistant.infrastructure.supabase.RemoteSupabaseTokenVerifier
import com.pocketmind.assistant.infrastructure.supabase.SupabaseAssistantMemoryRepository
import com.pocketmind.assistant.infrastructure.supabase.SupabaseFinancialContextRepository
import com.pocketmind.assistant.infrastructure.supabase.createSupabaseHttpClient
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val config = AssistantConfig.load()
    val supabaseClient = createSupabaseHttpClient(config)
    val memoryRepository = SupabaseAssistantMemoryRepository(
        supabaseClient,
        config,
    )
    val financialContextRepository = SupabaseFinancialContextRepository(
        supabaseClient,
        config,
    )
    val readServiceFactory = FinancialReadServiceFactory(
        contextRepository = financialContextRepository,
        memoryRepository = memoryRepository,
    )
    val readToolRegistryFactory = AssistantReadToolRegistryFactory()
    val koogRuntimeFactory = KoogRuntimeFactory(config)
    val dependencies = AppDependencies(
        config = config,
        tokenVerifier = RemoteSupabaseTokenVerifier(supabaseClient, config),
        koogRuntimeFactory = koogRuntimeFactory,
        memoryRepository = memoryRepository,
        financialContextRepository = financialContextRepository,
        readToolRegistryFactory = readToolRegistryFactory,
        turnHandler = AssistantTurnService(
            memoryRepository = memoryRepository,
            readServiceFactory = readServiceFactory,
            toolRegistryFactory = readToolRegistryFactory,
            interpreter = KoogAssistantTurnInterpreter(koogRuntimeFactory),
            promptVersion = config.promptVersion,
            toolSchemaVersion = config.toolSchemaVersion,
            modelId = config.primaryModel,
        ),
    )

    try {
        embeddedServer(
            factory = Netty,
            host = "0.0.0.0",
            port = config.port,
        ) {
            assistantModule(dependencies)
        }.start(wait = true)
    } finally {
        supabaseClient.close()
    }
}
