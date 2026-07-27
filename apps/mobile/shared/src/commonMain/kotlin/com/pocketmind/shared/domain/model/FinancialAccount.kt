package com.pocketmind.shared.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FinancialAccountType {
    @SerialName("cash")
    CASH,

    @SerialName("bank_account")
    BANK_ACCOUNT,

    @SerialName("savings")
    SAVINGS,

    @SerialName("credit_card")
    CREDIT_CARD,

    @SerialName("loan")
    LOAN,
}

/** A user-managed financial account, liability, or savings container. */
@Serializable
data class FinancialAccount(
    val id: String,
    val name: String,
    val type: FinancialAccountType,
    val currency: CurrencyCode,
    @SerialName("opening_balance")
    val openingBalance: Money = Money(0, currency),
    val aliases: List<String> = emptyList(),
    @SerialName("is_archived")
    val isArchived: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "An account id is required." }
        require(name.isNotBlank()) { "An account name is required." }
        require(openingBalance.currency == currency) { "Account currency must match its opening balance." }
        require(aliases.all { it.isNotBlank() && it == it.trim() }) {
            "Aliases must be trimmed and non-blank."
        }
        require(aliases.map { it.lowercase() }.distinct().size == aliases.size) {
            "Aliases must be unique ignoring case."
        }
    }
}
