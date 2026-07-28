package com.pocketmind.shared.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TransactionType {
    @SerialName("income")
    INCOME,

    @SerialName("expense")
    EXPENSE,

    @SerialName("transfer")
    TRANSFER,
}

@Serializable
enum class TransactionSource {
    @SerialName("manual")
    MANUAL,

    @SerialName("assistant_text")
    ASSISTANT_TEXT,

    @SerialName("bank_notification")
    BANK_NOTIFICATION,

    @SerialName("voice")
    VOICE,

    @SerialName("receipt_ocr")
    RECEIPT_OCR,

    @SerialName("import")
    IMPORT,
}

@Serializable
enum class TransactionStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("posted")
    POSTED,

    @SerialName("ignored")
    IGNORED,
}

/** Immutable ledger record shared by Android and iOS. */
@Serializable
data class FinancialTransaction(
    val id: String,
    val accountId: String,
    val type: TransactionType,
    val amount: Money,
    @SerialName("occurred_at_epoch_millis")
    val occurredAtEpochMillis: Long,
    @SerialName("category_id")
    val categoryId: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    val source: TransactionSource,
    val status: TransactionStatus = TransactionStatus.POSTED,
    @SerialName("related_account_id")
    val relatedAccountId: String? = null,
    @SerialName("manual_revision")
    val manualRevision: Int = 0,
) {
    init {
        require(id.isNotBlank()) { "A transaction id is required." }
        require(accountId.isNotBlank()) { "An account id is required." }
        require(amount.isPositive) { "A transaction amount must be positive." }
        require(occurredAtEpochMillis > 0) { "A transaction date is required." }
        require(type != TransactionType.TRANSFER || !relatedAccountId.isNullOrBlank()) {
            "A transfer destination is required."
        }
        require(manualRevision >= 0)
    }
}
