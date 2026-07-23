package com.pocketmind.shared.domain.model

/** ISO currencies currently supported by PocketMind's financial domain. */
enum class CurrencyCode {
    COP,
    USD,
}

/**
 * A signed monetary value stored in the smallest unit of its currency.
 *
 * Transaction amounts are validated as positive by [FinancialTransaction]. Aggregates such as
 * an account balance can be negative, which represents debt or an overdraft.
 */
data class Money(
    val minorUnits: Long,
    val currency: CurrencyCode,
) {
    val isPositive: Boolean
        get() = minorUnits > 0
}
