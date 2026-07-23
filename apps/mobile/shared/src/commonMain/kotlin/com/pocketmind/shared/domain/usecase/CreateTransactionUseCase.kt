package com.pocketmind.shared.domain.usecase

import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionSource
import com.pocketmind.shared.domain.model.TransactionStatus
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.shared.domain.repository.TransactionRepository

data class NewTransaction(
    val id: String,
    val accountId: String,
    val type: TransactionType,
    val amount: Money,
    val occurredAtEpochMillis: Long,
    val source: TransactionSource,
    val categoryId: String? = null,
    val merchant: String? = null,
    val note: String? = null,
)

enum class TransactionValidationError {
    MISSING_ID,
    MISSING_ACCOUNT,
    INVALID_AMOUNT,
    INVALID_DATE,
}

sealed interface CreateTransactionResult {
    data class Success(val transaction: FinancialTransaction) : CreateTransactionResult
    data class Invalid(val errors: Set<TransactionValidationError>) : CreateTransactionResult
}

/** Validates and persists records regardless of whether their source is manual or automated. */
class CreateTransactionUseCase(
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(input: NewTransaction): CreateTransactionResult {
        val errors = validate(input)
        if (errors.isNotEmpty()) return CreateTransactionResult.Invalid(errors)

        val transaction = FinancialTransaction(
            id = input.id.trim(),
            accountId = input.accountId.trim(),
            type = input.type,
            amount = input.amount,
            occurredAtEpochMillis = input.occurredAtEpochMillis,
            categoryId = input.categoryId?.trim()?.takeIf(String::isNotEmpty),
            merchant = input.merchant?.trim()?.takeIf(String::isNotEmpty),
            note = input.note?.trim()?.takeIf(String::isNotEmpty),
            source = input.source,
            status = TransactionStatus.POSTED,
        )
        transactionRepository.save(transaction)
        return CreateTransactionResult.Success(transaction)
    }

    private fun validate(input: NewTransaction): Set<TransactionValidationError> = buildSet {
        if (input.id.isBlank()) add(TransactionValidationError.MISSING_ID)
        if (input.accountId.isBlank()) add(TransactionValidationError.MISSING_ACCOUNT)
        if (!input.amount.isPositive) add(TransactionValidationError.INVALID_AMOUNT)
        if (input.occurredAtEpochMillis <= 0) add(TransactionValidationError.INVALID_DATE)
    }
}
