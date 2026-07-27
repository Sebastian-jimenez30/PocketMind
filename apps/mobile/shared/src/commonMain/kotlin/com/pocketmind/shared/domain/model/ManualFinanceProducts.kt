package com.pocketmind.shared.domain.model

import kotlin.math.pow

enum class SavingsProductType {
    SIMPLE,
    POCKET,
    TERM_DEPOSIT,
}

enum class SavingsMovementType {
    DEPOSIT,
    WITHDRAWAL,
    RATE_CHANGE,
}

data class CreditCardProfile(
    val accountId: String,
    val creditLimit: Money,
    val annualInterestBasisPoints: Int,
    val statementClosingDay: Int,
    val paymentDueDay: Int,
    val openingDebtInstallmentCount: Int = 1,
    val openingDebtFirstPaymentAtEpochMillis: Long? = null,
) {
    init {
        require(accountId.isNotBlank())
        require(creditLimit.minorUnits >= 0)
        require(annualInterestBasisPoints >= 0)
        require(statementClosingDay in 1..31)
        require(paymentDueDay in 1..31)
        require(openingDebtInstallmentCount in 1..60)
        require(openingDebtFirstPaymentAtEpochMillis == null || openingDebtFirstPaymentAtEpochMillis > 0)
    }
}

data class InstallmentPurchase(
    val id: String,
    val accountId: String,
    val merchant: String,
    val principal: Money,
    val installmentCount: Int,
    val annualInterestBasisPoints: Int,
    val purchasedAtEpochMillis: Long,
    val firstPaymentAtEpochMillis: Long,
    val categoryId: String?,
    val note: String?,
) {
    init {
        require(id.isNotBlank())
        require(accountId.isNotBlank())
        require(merchant.isNotBlank())
        require(principal.isPositive)
        require(installmentCount in 1..60)
        require(annualInterestBasisPoints >= 0)
        require(purchasedAtEpochMillis > 0)
        require(firstPaymentAtEpochMillis > 0)
    }

    val installmentAmount: Money
        get() {
            val monthlyRate = monthlyRate(annualInterestBasisPoints)
            val payment = if (monthlyRate == 0.0) {
                principal.minorUnits.toDouble() / installmentCount
            } else {
                val factor = (1.0 + monthlyRate).pow(installmentCount)
                principal.minorUnits * monthlyRate * factor / (factor - 1.0)
            }
            return Money(payment.toLong().coerceAtLeast(1), principal.currency)
        }

    val financedTotal: Money
        get() = Money(installmentAmount.minorUnits * installmentCount, principal.currency)
}

data class CreditCardPayment(
    val id: String,
    val accountId: String,
    val amount: Money,
    val paidAtEpochMillis: Long,
    val sourceAccountId: String?,
    val note: String?,
) {
    init {
        require(id.isNotBlank())
        require(accountId.isNotBlank())
        require(amount.isPositive)
        require(paidAtEpochMillis > 0)
    }
}

data class SavingsProfile(
    val accountId: String,
    val type: SavingsProductType,
    val annualYieldBasisPoints: Int,
    val openedAtEpochMillis: Long,
    val maturityAtEpochMillis: Long?,
) {
    init {
        require(accountId.isNotBlank())
        require(annualYieldBasisPoints >= 0)
        require(openedAtEpochMillis > 0)
        require(maturityAtEpochMillis == null || maturityAtEpochMillis > openedAtEpochMillis)
    }
}

data class SavingsMovement(
    val id: String,
    val accountId: String,
    val type: SavingsMovementType,
    val amount: Money,
    val annualYieldBasisPoints: Int?,
    val occurredAtEpochMillis: Long,
    val note: String?,
) {
    init {
        require(id.isNotBlank())
        require(accountId.isNotBlank())
        require(amount.minorUnits >= 0)
        require(annualYieldBasisPoints == null || annualYieldBasisPoints >= 0)
        require(occurredAtEpochMillis > 0)
        require(type != SavingsMovementType.RATE_CHANGE || annualYieldBasisPoints != null)
    }
}

data class CreditCardOverview(
    val profile: CreditCardProfile,
    val currentDebt: Money,
    val availableCredit: Money,
    val nextPayment: Money,
    val paidInstallments: Map<String, Int>,
)

data class SavingsProjection(
    val currentBalance: Money,
    val contributedBalance: Money,
    val estimatedYield: Money,
    val annualYieldBasisPoints: Int,
)

fun calculateCreditCardOverview(
    profile: CreditCardProfile,
    openingDebt: Money,
    purchases: List<InstallmentPurchase>,
    payments: List<CreditCardPayment>,
): CreditCardOverview {
    val cardPurchases = purchases.filter { it.accountId == profile.accountId }
    var unallocatedPayments = payments
        .filter { it.accountId == profile.accountId }
        .sumOf { it.amount.minorUnits }
    val paidInstallments = mutableMapOf<String, Int>()
    var nextPayment = 0L

    if (openingDebt.minorUnits > 0) {
        val initialInstallment = (
            openingDebt.minorUnits + profile.openingDebtInstallmentCount - 1
        ) / profile.openingDebtInstallmentCount
        val paidInitialInstallments = (unallocatedPayments / initialInstallment)
            .toInt()
            .coerceAtMost(profile.openingDebtInstallmentCount)
        unallocatedPayments = (
            unallocatedPayments - initialInstallment * paidInitialInstallments
        ).coerceAtLeast(0)
        if (paidInitialInstallments < profile.openingDebtInstallmentCount) {
            nextPayment += (initialInstallment - unallocatedPayments.coerceAtMost(initialInstallment))
                .coerceAtLeast(0)
            unallocatedPayments = 0
        }
    }

    cardPurchases.sortedBy { it.firstPaymentAtEpochMillis }.forEach { purchase ->
        val installment = purchase.installmentAmount.minorUnits
        val paid = (unallocatedPayments / installment).toInt().coerceAtMost(purchase.installmentCount)
        paidInstallments[purchase.id] = paid
        val allocatedToFullInstallments = installment * paid
        unallocatedPayments = (unallocatedPayments - allocatedToFullInstallments).coerceAtLeast(0)
        if (paid < purchase.installmentCount) {
            nextPayment += (installment - unallocatedPayments.coerceAtMost(installment)).coerceAtLeast(0)
            unallocatedPayments = 0
        }
    }

    val totalDebt = openingDebt.minorUnits + cardPurchases.sumOf { it.financedTotal.minorUnits }
    val paidAmount = payments.filter { it.accountId == profile.accountId }.sumOf { it.amount.minorUnits }
    val currentDebt = (totalDebt - paidAmount).coerceAtLeast(0)
    val available = (profile.creditLimit.minorUnits - currentDebt).coerceAtLeast(0)
    return CreditCardOverview(
        profile = profile,
        currentDebt = Money(currentDebt, profile.creditLimit.currency),
        availableCredit = Money(available, profile.creditLimit.currency),
        nextPayment = Money(nextPayment.coerceAtMost(currentDebt), profile.creditLimit.currency),
        paidInstallments = paidInstallments,
    )
}

fun calculateSavingsProjection(
    profile: SavingsProfile,
    openingBalance: Money,
    movements: List<SavingsMovement>,
    atEpochMillis: Long,
): SavingsProjection {
    var balance = openingBalance.minorUnits.toDouble()
    var rate = profile.annualYieldBasisPoints
    var lastTimestamp = profile.openedAtEpochMillis
    val ordered = movements.filter { it.accountId == profile.accountId }.sortedBy { it.occurredAtEpochMillis }

    fun accrue(until: Long) {
        if (until <= lastTimestamp || balance <= 0 || rate == 0) {
            lastTimestamp = maxOf(lastTimestamp, until)
            return
        }
        val days = (until - lastTimestamp).toDouble() / MILLIS_PER_DAY
        val annualRate = rate / 10_000.0
        balance *= (1.0 + annualRate).pow(days / 365.0)
        lastTimestamp = until
    }

    ordered.filter { it.occurredAtEpochMillis <= atEpochMillis }.forEach { movement ->
        accrue(movement.occurredAtEpochMillis)
        when (movement.type) {
            SavingsMovementType.DEPOSIT -> balance += movement.amount.minorUnits
            SavingsMovementType.WITHDRAWAL -> balance = (balance - movement.amount.minorUnits).coerceAtLeast(0.0)
            SavingsMovementType.RATE_CHANGE -> rate = movement.annualYieldBasisPoints ?: rate
        }
    }
    accrue(atEpochMillis)

    val contributed = openingBalance.minorUnits +
        ordered.filter { it.occurredAtEpochMillis <= atEpochMillis }.sumOf {
            when (it.type) {
                SavingsMovementType.DEPOSIT -> it.amount.minorUnits
                SavingsMovementType.WITHDRAWAL -> -it.amount.minorUnits
                SavingsMovementType.RATE_CHANGE -> 0
            }
        }
    val finalBalance = balance.toLong().coerceAtLeast(0)
    return SavingsProjection(
        currentBalance = Money(finalBalance, openingBalance.currency),
        contributedBalance = Money(contributed.coerceAtLeast(0), openingBalance.currency),
        estimatedYield = Money((finalBalance - contributed).coerceAtLeast(0), openingBalance.currency),
        annualYieldBasisPoints = rate,
    )
}

private fun monthlyRate(annualBasisPoints: Int): Double {
    if (annualBasisPoints == 0) return 0.0
    val annualRate = annualBasisPoints / 10_000.0
    return (1.0 + annualRate).pow(1.0 / 12.0) - 1.0
}

private const val MILLIS_PER_DAY = 86_400_000.0
