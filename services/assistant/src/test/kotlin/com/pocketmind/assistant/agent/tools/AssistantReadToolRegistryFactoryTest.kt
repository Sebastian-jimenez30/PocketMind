package com.pocketmind.assistant.agent.tools

import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.auth.SupabaseAccessToken
import com.pocketmind.assistant.domain.finance.FinancialContextRepository
import com.pocketmind.assistant.domain.finance.FinancialContextSnapshot
import com.pocketmind.assistant.testing.ReadOnlyMemoryRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AssistantReadToolRegistryFactoryTest {
    @Test
    fun `registry exposes only the four approved read tools`() {
        val factory = AssistantReadToolRegistryFactory(
            contextRepository = FinancialContextRepository { emptySnapshot() },
            memoryRepository = ReadOnlyMemoryRepository(),
        )

        val names = factory.create(TEST_SESSION).tools.map { it.name }.toSet()

        assertEquals(
            setOf(
                "get_financial_overview",
                "list_financial_products",
                "get_financial_product",
                "list_financial_transactions",
            ),
            names,
        )
        assertFalse(names.any { name ->
            listOf("create", "update", "delete", "save", "execute")
                .any(name::contains)
        })
    }
}

private fun emptySnapshot(): FinancialContextSnapshot =
    FinancialContextSnapshot(
        stateVersion = 0,
        latestRemoteUpdateEpochMillis = null,
        supportedSchemaVersion = 2,
        remoteRecordCount = 0,
        unknownEntityTypes = emptySet(),
        accounts = emptyList(),
        transactions = emptyList(),
        incomeSources = emptyList(),
        debts = emptyList(),
        savingsPlans = emptyList(),
        recurringObligations = emptyList(),
        creditCardProfiles = emptyList(),
        installmentPurchases = emptyList(),
        creditCardPayments = emptyList(),
        savingsProfiles = emptyList(),
        savingsMovements = emptyList(),
        loanProfiles = emptyList(),
        loanPayments = emptyList(),
    )

private val TEST_SESSION = AuthenticatedUser(
    userId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
    role = "authenticated",
    accessToken = SupabaseAccessToken("access-token"),
)
