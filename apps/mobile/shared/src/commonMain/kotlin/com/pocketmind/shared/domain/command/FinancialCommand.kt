package com.pocketmind.shared.domain.command

import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialProductConfiguration
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.SavingsMovementType
import com.pocketmind.shared.domain.model.TransactionSource
import com.pocketmind.shared.domain.model.TransactionType

/**
 * Stable, provider-neutral commands accepted by PocketMind's financial domain.
 *
 * Manual forms, notification automation and the future Koog assistant must all
 * translate their input into one of these commands. Commands contain intent
 * and user-provided values; repositories and persistence details stay outside
 * this contract.
 */
sealed interface FinancialCommand {
    val commandId: String

    data class RecordIncome(
        override val commandId: String,
        val productId: String,
        val amount: Money,
        val occurredAtEpochMillis: Long,
        val source: TransactionSource,
        val categoryId: String? = null,
        val merchant: String? = null,
        val note: String? = null,
    ) : FinancialCommand

    data class RecordExpense(
        override val commandId: String,
        val productId: String,
        val amount: Money,
        val occurredAtEpochMillis: Long,
        val source: TransactionSource,
        val categoryId: String? = null,
        val merchant: String? = null,
        val note: String? = null,
    ) : FinancialCommand

    data class Transfer(
        override val commandId: String,
        val sourceProductId: String,
        val destinationProductId: String,
        val amount: Money,
        val occurredAtEpochMillis: Long,
        val source: TransactionSource,
        val categoryId: String? = null,
        val merchant: String? = null,
        val note: String? = null,
    ) : FinancialCommand

    data class CreateProduct(
        override val commandId: String,
        val account: FinancialAccount,
        val configuration: FinancialProductConfiguration,
    ) : FinancialCommand

    data class UpdateProduct(
        override val commandId: String,
        val account: FinancialAccount,
        val configuration: FinancialProductConfiguration,
    ) : FinancialCommand

    data class ArchiveProduct(
        override val commandId: String,
        val productId: String,
    ) : FinancialCommand

    data class RecordCardPurchase(
        override val commandId: String,
        val cardId: String,
        val merchant: String,
        val principal: Money,
        val installmentCount: Int,
        val purchasedAtEpochMillis: Long,
        val source: TransactionSource,
        val categoryId: String? = null,
        val note: String? = null,
    ) : FinancialCommand

    data class RecordCardPayment(
        override val commandId: String,
        val cardId: String,
        val amount: Money,
        val paidAtEpochMillis: Long,
        val source: TransactionSource,
        val sourceProductId: String? = null,
        val note: String? = null,
    ) : FinancialCommand

    data class RecordSavingsMovement(
        override val commandId: String,
        val savingsId: String,
        val movementType: SavingsMovementType,
        val amount: Money,
        val occurredAtEpochMillis: Long,
        val source: TransactionSource,
        val relatedProductId: String? = null,
        val annualYieldBasisPoints: Int? = null,
        val note: String? = null,
    ) : FinancialCommand

    data class RecordLoanPayment(
        override val commandId: String,
        val loanId: String,
        val amount: Money,
        val paidAtEpochMillis: Long,
        val source: TransactionSource,
        val sourceProductId: String? = null,
        val note: String? = null,
    ) : FinancialCommand

    data class UpdateTransaction(
        override val commandId: String,
        val transactionId: String,
        val productId: String,
        val type: TransactionType,
        val amount: Money,
        val occurredAtEpochMillis: Long,
        val source: TransactionSource,
        val categoryId: String? = null,
        val merchant: String? = null,
        val note: String? = null,
        val relatedProductId: String? = null,
    ) : FinancialCommand

    data class DeleteTransaction(
        override val commandId: String,
        val transactionId: String,
    ) : FinancialCommand
}
