package com.pocketmind.presentation.products

/**
 * Product shortcuts mapped to the unified manual-record screen.
 * Financial conditions such as rates and payment dates remain in the product configuration.
 */
enum class ManualActionType {
    CARD_PURCHASE,
    CARD_PAYMENT,
    SAVINGS_DEPOSIT,
    SAVINGS_WITHDRAWAL,
    SAVINGS_RATE,
    LOAN_PAYMENT,
}
