package com.pocketmind.shared.domain.model

/**
 * Type-specific data attached to a [FinancialAccount].
 *
 * Keeping this model in the shared domain lets Android forms and future agent
 * adapters create products through the same command contract.
 */
sealed interface FinancialProductConfiguration {
    data object Standard : FinancialProductConfiguration

    data class CreditCard(
        val profile: CreditCardProfile,
    ) : FinancialProductConfiguration

    data class Savings(
        val profile: SavingsProfile,
    ) : FinancialProductConfiguration

    data class Loan(
        val profile: LoanProfile,
    ) : FinancialProductConfiguration
}
