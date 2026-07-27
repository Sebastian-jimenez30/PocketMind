package com.pocketmind.shared.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Type-specific data attached to a [FinancialAccount].
 *
 * Keeping this model in the shared domain lets Android forms and future agent
 * adapters create products through the same command contract.
 */
@Serializable
sealed interface FinancialProductConfiguration {
    @Serializable
    @SerialName("standard")
    data object Standard : FinancialProductConfiguration

    @Serializable
    @SerialName("credit_card")
    data class CreditCard(
        val profile: CreditCardProfile,
    ) : FinancialProductConfiguration

    @Serializable
    @SerialName("savings")
    data class Savings(
        val profile: SavingsProfile,
    ) : FinancialProductConfiguration

    @Serializable
    @SerialName("loan")
    data class Loan(
        val profile: LoanProfile,
    ) : FinancialProductConfiguration
}
