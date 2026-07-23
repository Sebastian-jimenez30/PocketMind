package com.pocketmind.shared.domain.model

enum class TransactionType {
    INCOME,
    EXPENSE,
}

enum class TransactionSource {
    MANUAL,
    BANK_NOTIFICATION,
    VOICE,
    RECEIPT_OCR,
    IMPORT,
}

enum class TransactionStatus {
    PENDING,
    POSTED,
    IGNORED,
}

/** Immutable ledger record shared by Android and iOS. */
data class FinancialTransaction(
    val id: String,
    val accountId: String,
    val type: TransactionType,
    val amount: Money,
    val occurredAtEpochMillis: Long,
    val categoryId: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    val source: TransactionSource,
    val status: TransactionStatus = TransactionStatus.POSTED,
) {
    init {
        require(id.isNotBlank()) { "A transaction id is required." }
        require(accountId.isNotBlank()) { "An account id is required." }
        require(amount.isPositive) { "A transaction amount must be positive." }
        require(occurredAtEpochMillis > 0) { "A transaction date is required." }
    }
}
