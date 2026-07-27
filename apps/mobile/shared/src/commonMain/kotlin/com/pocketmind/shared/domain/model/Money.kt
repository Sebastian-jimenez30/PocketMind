package com.pocketmind.shared.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** ISO currencies currently supported by PocketMind's financial domain. */
@Serializable
enum class CurrencyCode {
    @SerialName("COP")
    COP,

    @SerialName("USD")
    USD,
}

/**
 * A signed monetary value stored in the smallest unit of its currency.
 *
 * Transaction amounts are validated as positive by [FinancialTransaction]. Aggregates such as
 * an account balance can be negative, which represents debt or an overdraft.
 */
@Serializable
data class Money(
    @SerialName("minor_units")
    val minorUnits: Long,
    val currency: CurrencyCode,
) {
    val isPositive: Boolean
        get() = minorUnits > 0
}
