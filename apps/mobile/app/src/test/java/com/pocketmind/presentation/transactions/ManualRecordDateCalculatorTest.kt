package com.pocketmind.presentation.transactions

import com.pocketmind.data.time.JavaTimeCreditCardPaymentDateCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualRecordDateCalculatorTest {
    private val calculator = JavaTimeCreditCardPaymentDateCalculator()

    @Test
    fun `purchase before closing date is due the following month`() {
        val purchase = dateMillis(2026, 7, 10)

        val dueDate = calculator.firstPaymentAt(
            purchasedAtEpochMillis = purchase,
            closingDay = 20,
            paymentDay = 8,
        )

        assertEquals(LocalDate.of(2026, 8, 8), dueDate.toLocalDate())
    }

    @Test
    fun `purchase after closing date is due in two months`() {
        val purchase = dateMillis(2026, 7, 25)

        val dueDate = calculator.firstPaymentAt(
            purchasedAtEpochMillis = purchase,
            closingDay = 20,
            paymentDay = 8,
        )

        assertEquals(LocalDate.of(2026, 9, 8), dueDate.toLocalDate())
    }

    @Test
    fun `payment day is limited to the last day of a short month`() {
        val purchase = dateMillis(2026, 1, 10)

        val dueDate = calculator.firstPaymentAt(
            purchasedAtEpochMillis = purchase,
            closingDay = 20,
            paymentDay = 31,
        )

        assertEquals(LocalDate.of(2026, 2, 28), dueDate.toLocalDate())
    }
}

private fun dateMillis(year: Int, month: Int, day: Int): Long =
    LocalDate.of(year, month, day)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
