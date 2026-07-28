package com.pocketmind.assistant

import com.pocketmind.assistant.agent.tools.AssistantReadToolRegistryFactory
import com.pocketmind.assistant.application.AppDependencies
import com.pocketmind.assistant.application.assistantModule
import com.pocketmind.assistant.config.AssistantConfig
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
    val dependencies = AppDependencies(
        config = config,
        tokenVerifier = RemoteSupabaseTokenVerifier(supabaseClient, config),
        koogRuntimeFactory = KoogRuntimeFactory(config),
        memoryRepository = memoryRepository,
        readToolRegistryFactory = AssistantReadToolRegistryFactory(
            contextRepository = financialContextRepository,
            memoryRepository = memoryRepository,
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
