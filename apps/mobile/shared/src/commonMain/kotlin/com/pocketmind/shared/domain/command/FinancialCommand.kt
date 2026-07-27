package com.pocketmind.shared.domain.command

import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialProductConfiguration
import com.pocketmind.shared.domain.model.CURRENT_FINANCIAL_RULE_VERSION
import com.pocketmind.shared.domain.model.DebtPaymentType
import com.pocketmind.shared.domain.model.InstallmentRatePeriod
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.SavingsMovementType
import com.pocketmind.shared.domain.model.TransactionSource
import com.pocketmind.shared.domain.model.TransactionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Stable, provider-neutral commands accepted by PocketMind's financial domain.
 *
 * Manual forms, notification automation and the future Koog assistant must all
 * translate their input into one of these commands. Commands contain intent
 * and user-provided values; repositories and persistence details stay outside
 * this contract.
 */
@Serializable
sealed interface FinancialCommand {
    val commandId: String

    @Serializable
    @SerialName("record_income")
    data class RecordIncome(
        @SerialName("command_id")
        override val commandId: String,
        @SerialName("product_id")
        val productId: String,
        val amount: Money,
        @SerialName("occurred_at_epoch_millis")
        val occurredAtEpochMillis: Long,
        val source: TransactionSource,
        @SerialName("category_id")
        val categoryId: String? = null,
        val merchant: String? = null,
        val note: String? = null,
    ) : FinancialCommand

    @Serializable
    @SerialName("record_expense")
    data class RecordExpense(
        @SerialName("command_id")
        override val commandId: String,
        @SerialName("product_id")
        val productId: String,
        val amount: Money,
        @SerialName("occurred_at_epoch_millis")
        val occurredAtEpochMillis: Long,
        val source: TransactionSource,
        @SerialName("category_id")
        val categoryId: String? = null,
        val merchant: String? = null,
        val note: String? = null,
    ) : FinancialCommand

    @Serializable
    @SerialName("transfer")
    data class Transfer(
        @SerialName("command_id")
        override val commandId: String,
        @SerialName("source_product_id")
        val sourceProductId: String,
        @SerialName("destination_product_id")
        val destinationProductId: String,
        val amount: Money,
        @SerialName("occurred_at_epoch_millis")
        val occurredAtEpochMillis: Long,
        val source: TransactionSource,
        @SerialName("category_id")
        val categoryId: String? = null,
        val merchant: String? = null,
        val note: String? = null,
    ) : FinancialCommand

    @Serializable
    @SerialName("create_product")
    data class CreateProduct(
        @SerialName("command_id")
        override val commandId: String,
        val account: FinancialAccount,
        val configuration: FinancialProductConfiguration,
    ) : FinancialCommand

    @Serializable
    @SerialName("update_product")
    data class UpdateProduct(
        @SerialName("command_id")
        override val commandId: String,
        val account: FinancialAccount,
        val configuration: FinancialProductConfiguration,
    ) : FinancialCommand

    @Serializable
    @SerialName("archive_product")
    data class ArchiveProduct(
        @SerialName("command_id")
        override val commandId: String,
        @SerialName("product_id")
        val productId: String,
    ) : FinancialCommand

    @Serializable
    @SerialName("record_card_purchase")
    data class RecordCardPurchase(
        @SerialName("command_id")
        override val commandId: String,
        @SerialName("card_id")
        val cardId: String,
        val merchant: String,
        val principal: Money,
        @SerialName("installment_count")
        val installmentCount: Int,
        @SerialName("purchased_at_epoch_millis")
        val purchasedAtEpochMillis: Long,
        val source: TransactionSource,
        @SerialName("category_id")
        val categoryId: String? = null,
        val note: String? = null,
        @SerialName("promotional_rate_periods")
        val promotionalRatePeriods: List<InstallmentRatePeriod> = emptyList(),
        @SerialName("calculation_rule_version")
        val calculationRuleVersion: Int = CURRENT_FINANCIAL_RULE_VERSION,
    ) : FinancialCommand

    @Serializable
    @SerialName("record_card_payment")
    data class RecordCardPayment(
        @SerialName("command_id")
        override val commandId: String,
        @SerialName("card_id")
        val cardId: String,
        val amount: Money? = null,
        @SerialName("payment_type")
        val paymentType: DebtPaymentType = DebtPaymentType.CUSTOM,
        @SerialName("paid_at_epoch_millis")
        val paidAtEpochMillis: Long,
        val source: TransactionSource,
        @SerialName("source_product_id")
        val sourceProductId: String? = null,
        val note: String? = null,
        @SerialName("calculation_rule_version")
        val calculationRuleVersion: Int = CURRENT_FINANCIAL_RULE_VERSION,
    ) : FinancialCommand

    @Serializable
    @SerialName("record_savings_movement")
    data class RecordSavingsMovement(
        @SerialName("command_id")
        override val commandId: String,
        @SerialName("savings_id")
        val savingsId: String,
        @SerialName("movement_type")
        val movementType: SavingsMovementType,
        val amount: Money,
        @SerialName("occurred_at_epoch_millis")
        val occurredAtEpochMillis: Long,
        val source: TransactionSource,
        @SerialName("source_product_id")
        val sourceProductId: String? = null,
        @SerialName("destination_product_id")
        val destinationProductId: String? = null,
        @SerialName("annual_yield_basis_points")
        val annualYieldBasisPoints: Int? = null,
        val note: String? = null,
        @SerialName("calculation_rule_version")
        val calculationRuleVersion: Int = CURRENT_FINANCIAL_RULE_VERSION,
    ) : FinancialCommand

    @Serializable
    @SerialName("record_loan_payment")
    data class RecordLoanPayment(
        @SerialName("command_id")
        override val commandId: String,
        @SerialName("loan_id")
        val loanId: String,
        val amount: Money? = null,
        @SerialName("payment_type")
        val paymentType: DebtPaymentType = DebtPaymentType.CUSTOM,
        @SerialName("paid_at_epoch_millis")
        val paidAtEpochMillis: Long,
        val source: TransactionSource,
        @SerialName("source_product_id")
        val sourceProductId: String? = null,
        val note: String? = null,
        @SerialName("calculation_rule_version")
        val calculationRuleVersion: Int = CURRENT_FINANCIAL_RULE_VERSION,
    ) : FinancialCommand

    @Serializable
    @SerialName("update_transaction")
    data class UpdateTransaction(
        @SerialName("command_id")
        override val commandId: String,
        @SerialName("transaction_id")
        val transactionId: String,
        @SerialName("product_id")
        val productId: String,
        val type: TransactionType,
        val amount: Money,
        @SerialName("occurred_at_epoch_millis")
        val occurredAtEpochMillis: Long,
        val source: TransactionSource,
        @SerialName("category_id")
        val categoryId: String? = null,
        val merchant: String? = null,
        val note: String? = null,
        @SerialName("related_product_id")
        val relatedProductId: String? = null,
    ) : FinancialCommand

    @Serializable
    @SerialName("delete_transaction")
    data class DeleteTransaction(
        @SerialName("command_id")
        override val commandId: String,
        @SerialName("transaction_id")
        val transactionId: String,
    ) : FinancialCommand
}
