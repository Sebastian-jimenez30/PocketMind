package com.pocketmind.data.time

import com.pocketmind.shared.domain.usecase.CreditCardPaymentDateCalculator
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** Android/JVM calendar implementation for the shared credit-card due-date policy. */
class JavaTimeCreditCardPaymentDateCalculator @Inject constructor() :
    CreditCardPaymentDateCalculator {
    override fun firstPaymentAt(
        purchasedAtEpochMillis: Long,
        statementClosingDay: Int,
        paymentDueDay: Int,
    ): Long {
        val purchaseDate = Instant.ofEpochMilli(purchasedAtEpochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val monthsToAdd = if (purchaseDate.dayOfMonth <= statementClosingDay) 1L else 2L
        val dueMonth = purchaseDate.plusMonths(monthsToAdd).withDayOfMonth(1)
        return dueMonth.withDayOfMonth(paymentDueDay.coerceAtMost(dueMonth.lengthOfMonth()))
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
