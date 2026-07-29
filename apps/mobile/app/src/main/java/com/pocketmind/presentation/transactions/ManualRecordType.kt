package com.pocketmind.presentation.transactions

import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.TransactionCategoryId

/**
 * User-facing movement families. They simplify selection without flattening the
 * financial commands that keep card, debt and savings calculations correct.
 */
enum class ManualRecordGroup {
    EXPENSE,
    INCOME,
    TRANSFER,
}

enum class ManualRecordType {
    INCOME,
    EXPENSE,
    TRANSFER,
    CARD_PURCHASE,
    CARD_PAYMENT,
    SAVINGS_DEPOSIT,
    SAVINGS_WITHDRAWAL,
    LOAN_PAYMENT,
}

internal fun ManualRecordGroup.defaultOperation(): ManualRecordType = when (this) {
    ManualRecordGroup.EXPENSE -> ManualRecordType.EXPENSE
    ManualRecordGroup.INCOME -> ManualRecordType.INCOME
    ManualRecordGroup.TRANSFER -> ManualRecordType.TRANSFER
}

internal fun ManualRecordGroup.operations(): List<ManualRecordType> = when (this) {
    ManualRecordGroup.EXPENSE -> listOf(
        ManualRecordType.EXPENSE,
        ManualRecordType.CARD_PURCHASE,
        ManualRecordType.CARD_PAYMENT,
        ManualRecordType.LOAN_PAYMENT,
        ManualRecordType.SAVINGS_DEPOSIT,
    )
    ManualRecordGroup.INCOME -> listOf(
        ManualRecordType.INCOME,
        ManualRecordType.SAVINGS_WITHDRAWAL,
    )
    ManualRecordGroup.TRANSFER -> listOf(ManualRecordType.TRANSFER)
}

internal val ManualRecordType.group: ManualRecordGroup
    get() = when (this) {
        ManualRecordType.INCOME,
        ManualRecordType.SAVINGS_WITHDRAWAL,
        -> ManualRecordGroup.INCOME
        ManualRecordType.TRANSFER -> ManualRecordGroup.TRANSFER
        ManualRecordType.EXPENSE,
        ManualRecordType.CARD_PURCHASE,
        ManualRecordType.CARD_PAYMENT,
        ManualRecordType.SAVINGS_DEPOSIT,
        ManualRecordType.LOAN_PAYMENT,
        -> ManualRecordGroup.EXPENSE
    }

internal fun ManualRecordType.defaultCategory(): TransactionCategoryId = when (this) {
    ManualRecordType.INCOME -> TransactionCategoryId.SALARY
    ManualRecordType.EXPENSE,
    ManualRecordType.CARD_PURCHASE,
    -> TransactionCategoryId.SHOPPING
    ManualRecordType.CARD_PAYMENT,
    ManualRecordType.LOAN_PAYMENT,
    -> TransactionCategoryId.DEBT_PAYMENT
    ManualRecordType.SAVINGS_DEPOSIT,
    ManualRecordType.SAVINGS_WITHDRAWAL,
    -> TransactionCategoryId.SAVINGS
    ManualRecordType.TRANSFER -> TransactionCategoryId.TRANSFER
}

internal fun ManualRecordType.categories(): List<TransactionCategoryId> = when (this) {
    ManualRecordType.INCOME -> listOf(
        TransactionCategoryId.SALARY,
        TransactionCategoryId.FREELANCE,
        TransactionCategoryId.OTHER,
    )
    ManualRecordType.EXPENSE,
    ManualRecordType.CARD_PURCHASE,
    -> listOf(
        TransactionCategoryId.FOOD,
        TransactionCategoryId.TRANSPORT,
        TransactionCategoryId.HOME,
        TransactionCategoryId.HEALTH,
        TransactionCategoryId.EDUCATION,
        TransactionCategoryId.ENTERTAINMENT,
        TransactionCategoryId.SHOPPING,
        TransactionCategoryId.SERVICES,
        TransactionCategoryId.OTHER,
    )
    ManualRecordType.CARD_PAYMENT,
    ManualRecordType.LOAN_PAYMENT,
    -> listOf(TransactionCategoryId.DEBT_PAYMENT)
    ManualRecordType.SAVINGS_DEPOSIT,
    ManualRecordType.SAVINGS_WITHDRAWAL,
    -> listOf(TransactionCategoryId.SAVINGS)
    ManualRecordType.TRANSFER -> listOf(TransactionCategoryId.TRANSFER)
}

internal fun FinancialAccountType.isCompatibleWith(operation: ManualRecordType): Boolean = when (operation) {
    ManualRecordType.INCOME,
    ManualRecordType.EXPENSE,
    -> this == FinancialAccountType.BANK_ACCOUNT || this == FinancialAccountType.CASH
    ManualRecordType.TRANSFER ->
        this == FinancialAccountType.BANK_ACCOUNT ||
            this == FinancialAccountType.CASH ||
            this == FinancialAccountType.SAVINGS
    ManualRecordType.CARD_PURCHASE,
    ManualRecordType.CARD_PAYMENT,
    -> this == FinancialAccountType.CREDIT_CARD
    ManualRecordType.SAVINGS_DEPOSIT,
    ManualRecordType.SAVINGS_WITHDRAWAL,
    -> this == FinancialAccountType.SAVINGS
    ManualRecordType.LOAN_PAYMENT -> this == FinancialAccountType.LOAN
}

internal fun ManualRecordType.requiresRelatedProduct(): Boolean =
    this == ManualRecordType.TRANSFER

