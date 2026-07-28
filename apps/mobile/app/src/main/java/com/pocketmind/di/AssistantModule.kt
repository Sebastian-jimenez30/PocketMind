package com.pocketmind.di

import com.pocketmind.data.assistant.AssistantRepository
import com.pocketmind.data.assistant.KtorAssistantRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantBindingsModule {
    @Binds
    abstract fun bindAssistantRepository(
        implementation: KtorAssistantRepository,
    ): AssistantRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AssistantNetworkModule {
    @Provides
    @Singleton
    fun provideAssistantHttpClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = true
        defaultRequest {
            headers.append("X-PocketMind-Client", "android")
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }
}
