package com.pocketmind.assistant.infrastructure.supabase

import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.auth.SupabaseAccessToken
import com.pocketmind.assistant.config.AssistantConfig
import com.pocketmind.assistant.config.source
import com.pocketmind.assistant.config.validValues
import com.pocketmind.assistant.domain.finance.FinancialContextException
import com.pocketmind.assistant.domain.finance.FinancialContextProblem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SupabaseFinancialContextRepositoryTest {
    @Test
    fun `snapshot uses caller JWT and maps only user scoped records`() = runTest {
        val client = financialTestClient(
            MockEngine { request ->
                assertEquals(
                    "Bearer user-access-token",
                    request.headers[HttpHeaders.Authorization],
                )
                assertEquals("publishable-test", request.headers["apikey"])
                assertEquals(
                    "eq.$USER_ID",
                    request.url.parameters["user_id"],
                )
                respondFinancialJson(
                    """
                    [
                      {
                        "user_id":"$USER_ID",
                        "entity_type":"CUSTOM_CATEGORY",
                        "entity_id":"category-pets",
                        "schema_version":2,
                        "payload":{
                          "id":"category-pets",
                          "name":"Mascotas",
                          "createdAtEpochMillis":800
                        },
                        "is_deleted":false,
                        "updated_at_epoch_millis":1050
                      },
                      {
                        "user_id":"$USER_ID",
                        "entity_type":"ACCOUNT",
                        "entity_id":"bank-1",
                        "schema_version":2,
                        "payload":{
                          "id":"bank-1",
                          "name":"Cuenta Bancolombia",
                          "type":"BANK_ACCOUNT",
                          "currency":"COP",
                          "openingBalanceMinorUnits":500000,
                          "isArchived":false,
                          "aliasesJson":"[\"principal\"]"
                        },
                        "is_deleted":false,
                        "updated_at_epoch_millis":1000
                      },
                      {
                        "user_id":"$USER_ID",
                        "entity_type":"TRANSACTION",
                        "entity_id":"movement-1",
                        "schema_version":2,
                        "payload":{
                          "id":"movement-1",
                          "accountId":"bank-1",
                          "type":"EXPENSE",
                          "amountMinorUnits":25000,
                          "currency":"COP",
                          "occurredAtEpochMillis":900,
                          "categoryId":"FOOD",
                          "merchant":"Restaurante",
                          "note":null,
                          "source":"MANUAL",
                          "status":"POSTED",
                          "relatedAccountId":null,
                          "manualRevision":0
                        },
                        "is_deleted":false,
                        "updated_at_epoch_millis":1100
                      }
                    ]
                    """.trimIndent(),
                )
            },
        )

        val snapshot = SupabaseFinancialContextRepository(
            client,
            financialTestConfig(),
        ).fetchSnapshot(financialTestSession())

        assertEquals(1, snapshot.accounts.size)
        assertEquals("Cuenta Bancolombia", snapshot.accounts.single().name)
        assertEquals(listOf("principal"), snapshot.accounts.single().aliases)
        assertEquals(1, snapshot.transactions.size)
        assertEquals("Mascotas", snapshot.customCategories.single().name)
        assertEquals(1_100L, snapshot.latestRemoteUpdateEpochMillis)
        assertTrue(snapshot.stateVersion > 0)
        client.close()
    }

    @Test
    fun `known future schema blocks the whole financial snapshot`() = runTest {
        val record = RemoteFinanceRecordDto(
            userId = USER_ID,
            entityType = "ACCOUNT",
            entityId = "bank-1",
            schemaVersion = 3,
            payload = null,
            isDeleted = true,
            updatedAtEpochMillis = 1_000,
        )

        val error = assertFailsWith<FinancialContextException> {
            FinancialSnapshotDecoder().decode(listOf(record))
        }

        assertEquals(FinancialContextProblem.UNSUPPORTED_SCHEMA, error.problem)
    }

    @Test
    fun `cross user response is rejected even if provider filtering regresses`() =
        runTest {
            val client = financialTestClient(
                MockEngine {
                    respondFinancialJson(
                        """
                        [{
                          "user_id":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                          "entity_type":"UNKNOWN",
                          "entity_id":"record",
                          "schema_version":1,
                          "payload":{},
                          "is_deleted":false,
                          "updated_at_epoch_millis":1000
                        }]
                        """.trimIndent(),
                    )
                },
            )

            val error = assertFailsWith<FinancialContextException> {
                SupabaseFinancialContextRepository(
                    client,
                    financialTestConfig(),
                ).fetchSnapshot(financialTestSession())
            }

            assertEquals(FinancialContextProblem.CROSS_USER_RECORD, error.problem)
            client.close()
        }

    @Test
    fun `payload identity mismatch is rejected instead of silently remapped`() {
        val payload = Json.parseToJsonElement(
            """
            {
              "id":"different-id",
              "name":"Cuenta",
              "type":"BANK_ACCOUNT",
              "currency":"COP",
              "openingBalanceMinorUnits":0,
              "isArchived":false,
              "aliasesJson":"[]"
            }
            """.trimIndent(),
        ).jsonObject
        val record = RemoteFinanceRecordDto(
            userId = USER_ID,
            entityType = "ACCOUNT",
            entityId = "bank-1",
            schemaVersion = 2,
            payload = payload,
            isDeleted = false,
            updatedAtEpochMillis = 1_000,
        )

        val error = assertFailsWith<FinancialContextException> {
            FinancialSnapshotDecoder().decode(listOf(record))
        }

        assertEquals(FinancialContextProblem.INVALID_REMOTE_DATA, error.problem)
    }
}

private fun financialTestClient(engine: MockEngine): HttpClient =
    HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondFinancialJson(
    content: String,
) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(
        HttpHeaders.ContentType,
        ContentType.Application.Json.toString(),
    ),
)

private fun financialTestConfig(): AssistantConfig =
    AssistantConfig.load(source(validValues()))

private fun financialTestSession(): AuthenticatedUser = AuthenticatedUser(
    userId = USER_ID,
    role = "authenticated",
    accessToken = SupabaseAccessToken("user-access-token"),
)

private const val USER_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
