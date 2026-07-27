package com.pocketmind.shared.domain.command

/** Expected domain rejections that adapters can translate into user-facing copy. */
enum class FinancialCommandError {
    MISSING_COMMAND_ID,
    PRODUCT_NOT_FOUND,
    PRODUCT_ALREADY_EXISTS,
    INVALID_PRODUCT_CONFIGURATION,
    UNSUPPORTED_PRODUCT_OPERATION,
    INVALID_AMOUNT,
    INVALID_DATE,
    INVALID_INSTALLMENTS,
    INVALID_MERCHANT,
    CURRENCY_MISMATCH,
    SAME_TRANSFER_PRODUCT,
    MISSING_TRANSFER_DESTINATION,
    MISSING_CARD_PROFILE,
    PURCHASE_EXCEEDS_AVAILABLE_CREDIT,
    PAYMENT_EXCEEDS_CARD_DEBT,
    MISSING_SAVINGS_PROFILE,
    WITHDRAWAL_EXCEEDS_SAVINGS,
    INVALID_SAVINGS_RATE,
    MISSING_LOAN_PROFILE,
    PAYMENT_EXCEEDS_LOAN_DEBT,
    TRANSACTION_NOT_FOUND,
    LINKED_TRANSACTION_REQUIRES_PRODUCT_ACTION,
}

sealed interface FinancialCommandResult {
    val commandId: String

    data class Success(
        override val commandId: String,
        val affectedEntityIds: Set<String>,
    ) : FinancialCommandResult

    data class Rejected(
        override val commandId: String,
        val errors: Set<FinancialCommandError>,
    ) : FinancialCommandResult
}
