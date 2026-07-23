package com.pocketmind.shared.domain.model

/** ISO currencies currently supported by PocketMind's financial domain. */
enum class CurrencyCode {
    COP,
    USD,
}

/**
 * A non-negative monetary amount stored in the smallest unit of its currency.
 * Colombian pesos use whole units today; the representation supports fractional currencies too.
 */
data class Money(
    val minorUnits: Long,
    val currency: CurrencyCode,
) {
    init {
        require(minorUnits >= 0) { "A monetary amount cannot be negative." }
    }

    val isPositive: Boolean
        get() = minorUnits > 0
}
