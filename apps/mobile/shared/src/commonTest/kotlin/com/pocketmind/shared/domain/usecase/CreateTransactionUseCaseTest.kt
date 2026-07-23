package com.pocketmind.shared.domain.usecase

import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionSource
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.shared.domain.repository.TransactionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

class CreateTransactionUseCaseTest {
    @Test
    fun `does not persist an invalid transaction`() = runTest {
        val repository = RecordingTransactionRepository()
        val result = CreateTransactionUseCase(repository)(
            validInput().copy(amount = Money(0, CurrencyCode.COP), accountId = ""),
        )

        val invalid = assertIs<CreateTransactionResult.Invalid>(result)
        assertEquals(
            setOf(TransactionValidationError.MISSING_ACCOUNT, TransactionValidationError.INVALID_AMOUNT),
            invalid.errors,
        )
        assertEquals(emptyList(), repository.saved)
    }

    @Test
    fun `persists a valid transaction with normalized optional fields`() = runTest {
        val repository = RecordingTransactionRepository()
        val result = CreateTransactionUseCase(repository)(
            validInput().copy(merchant = "  Cafeteria  ", note = "   "),
        )

        val success = assertIs<CreateTransactionResult.Success>(result)
        assertEquals("Cafeteria", success.transaction.merchant)
        assertEquals(null, success.transaction.note)
        assertEquals(listOf(success.transaction), repository.saved)
    }

    private fun validInput() = NewTransaction(
        id = "transaction-1",
        accountId = "account-1",
        type = TransactionType.EXPENSE,
        amount = Money(35_000, CurrencyCode.COP),
        occurredAtEpochMillis = 1_700_000_000_000,
        source = TransactionSource.MANUAL,
    )

    private class RecordingTransactionRepository : TransactionRepository {
        val saved = mutableListOf<FinancialTransaction>()

        override fun observeAll(): Flow<List<FinancialTransaction>> = emptyFlow()

        override suspend fun save(transaction: FinancialTransaction) {
            saved += transaction
        }
    }
}
