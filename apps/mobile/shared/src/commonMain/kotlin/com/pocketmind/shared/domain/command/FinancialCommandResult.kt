package com.pocketmind.shared.domain.command

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Expected domain rejections that adapters can translate into user-facing copy. */
@Serializable
enum class FinancialCommandError {
    @SerialName("missing_command_id")
    MISSING_COMMAND_ID,

    @SerialName("product_not_found")
    PRODUCT_NOT_FOUND,

    @SerialName("product_already_exists")
    PRODUCT_ALREADY_EXISTS,

    @SerialName("invalid_product_configuration")
    INVALID_PRODUCT_CONFIGURATION,

    @SerialName("unsupported_product_operation")
    UNSUPPORTED_PRODUCT_OPERATION,

    @SerialName("invalid_amount")
    INVALID_AMOUNT,

    @SerialName("invalid_date")
    INVALID_DATE,

    @SerialName("invalid_installments")
    INVALID_INSTALLMENTS,

    @SerialName("invalid_promotional_rate_periods")
    INVALID_PROMOTIONAL_RATE_PERIODS,

    @SerialName("invalid_merchant")
    INVALID_MERCHANT,

    @SerialName("currency_mismatch")
    CURRENCY_MISMATCH,

    @SerialName("same_transfer_product")
    SAME_TRANSFER_PRODUCT,

    @SerialName("missing_transfer_destination")
    MISSING_TRANSFER_DESTINATION,

    @SerialName("missing_card_profile")
    MISSING_CARD_PROFILE,

    @SerialName("purchase_exceeds_available_credit")
    PURCHASE_EXCEEDS_AVAILABLE_CREDIT,

    @SerialName("payment_exceeds_card_debt")
    PAYMENT_EXCEEDS_CARD_DEBT,

    @SerialName("missing_payment_amount")
    MISSING_PAYMENT_AMOUNT,

    @SerialName("payment_amount_mismatch")
    PAYMENT_AMOUNT_MISMATCH,

    @SerialName("missing_savings_profile")
    MISSING_SAVINGS_PROFILE,

    @SerialName("withdrawal_exceeds_savings")
    WITHDRAWAL_EXCEEDS_SAVINGS,

    @SerialName("invalid_savings_rate")
    INVALID_SAVINGS_RATE,

    @SerialName("missing_loan_profile")
    MISSING_LOAN_PROFILE,

    @SerialName("payment_exceeds_loan_debt")
    PAYMENT_EXCEEDS_LOAN_DEBT,

    @SerialName("invalid_money_flow_endpoints")
    INVALID_MONEY_FLOW_ENDPOINTS,

    @SerialName("unsupported_rule_version")
    UNSUPPORTED_RULE_VERSION,

    @SerialName("transaction_not_found")
    TRANSACTION_NOT_FOUND,

    @SerialName("linked_transaction_requires_product_action")
    LINKED_TRANSACTION_REQUIRES_PRODUCT_ACTION,
}

@Serializable
sealed interface FinancialCommandResult {
    val commandId: String

    @Serializable
    @SerialName("success")
    data class Success(
        @SerialName("command_id")
        override val commandId: String,
        @SerialName("affected_entity_ids")
        val affectedEntityIds: Set<String>,
    ) : FinancialCommandResult

    @Serializable
    @SerialName("rejected")
    data class Rejected(
        @SerialName("command_id")
        override val commandId: String,
        val errors: Set<FinancialCommandError>,
    ) : FinancialCommandResult
}
