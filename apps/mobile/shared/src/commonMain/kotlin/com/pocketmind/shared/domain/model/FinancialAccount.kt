package com.pocketmind.shared.domain.model

enum class FinancialAccountType {
    CASH,
    BANK_ACCOUNT,
    SAVINGS,
    CREDIT_CARD,
    LOAN,
}

/** A user-managed financial account, liability, or savings container. */
data class FinancialAccount(
    val id: String,
    val name: String,
    val type: FinancialAccountType,
    val currency: CurrencyCode,
    val openingBalance: Money = Money(0, currency),
    val isArchived: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "An account id is required." }
        require(name.isNotBlank()) { "An account name is required." }
        require(openingBalance.currency == currency) { "Account currency must match its opening balance." }
    }
}
