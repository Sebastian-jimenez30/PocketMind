package com.pocketmind.shared.domain.model

import kotlin.math.pow
import kotlin.math.roundToLong
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SavingsProductType {
    @SerialName("simple")
    SIMPLE,

    @SerialName("pocket")
    POCKET,

    @SerialName("term_deposit")
    TERM_DEPOSIT,
}

@Serializable
enum class SavingsMovementType {
    @SerialName("deposit")
    DEPOSIT,

    @SerialName("withdrawal")
    WITHDRAWAL,

    @SerialName("rate_change")
    RATE_CHANGE,
}

@Serializable
data class CreditCardProfile(
    @SerialName("account_id")
    val accountId: String,
    @SerialName("credit_limit")
    val creditLimit: Money,
    @SerialName("annual_interest_basis_points")
    val annualInterestBasisPoints: Int,
    @SerialName("statement_closing_day")
    val statementClosingDay: Int,
    @SerialName("payment_due_day")
    val paymentDueDay: Int,
    @SerialName("opening_debt_installment_count")
    val openingDebtInstallmentCount: Int = 1,
    @SerialName("opening_debt_first_payment_at_epoch_millis")
    val openingDebtFirstPaymentAtEpochMillis: Long? = null,
    @SerialName("schedule_rule_version")
    val scheduleRuleVersion: Int = CURRENT_FINANCIAL_RULE_VERSION,
) {
    init {
        require(accountId.isNotBlank())
        require(creditLimit.minorUnits >= 0)
        require(annualInterestBasisPoints >= 0)
        require(statementClosingDay in 1..31)
        require(paymentDueDay in 1..31)
        require(openingDebtInstallmentCount in 1..60)
        require(openingDebtFirstPaymentAtEpochMillis == null || openingDebtFirstPaymentAtEpochMillis > 0)
        require(scheduleRuleVersion > 0)
    }
}

@Serializable
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
    @SerialName("promotional_rate_periods")
    val promotionalRatePeriods: List<InstallmentRatePeriod> = emptyList(),
    @SerialName("calculation_rule_version")
    val calculationRuleVersion: Int = CURRENT_FINANCIAL_RULE_VERSION,
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
        require(calculationRuleVersion > 0)
        require(promotionalRatePeriods.all { it.lastInstallment <= installmentCount })
        require(
            promotionalRatePeriods
                .flatMap { it.firstInstallment..it.lastInstallment }
                .distinct()
                .size == promotionalRatePeriods.sumOf {
                it.lastInstallment - it.firstInstallment + 1
            },
        ) { "Promotional installment periods cannot overlap." }
    }

    val installmentSchedule: List<InstallmentScheduleEntry>
        get() = calculateInstallmentSchedule()

    val installmentAmount: Money
        get() = installmentSchedule.first().amount

    val financedTotal: Money
        get() = Money(installmentSchedule.sumOf { it.amount.minorUnits }, principal.currency)

    private fun calculateInstallmentSchedule(): List<InstallmentScheduleEntry> {
        var outstandingPrincipal = principal.minorUnits
        return (1..installmentCount).map { installmentNumber ->
            val remainingInstallments = installmentCount - installmentNumber + 1
            val appliedRate = promotionalRatePeriods
                .firstOrNull { installmentNumber in it }
                ?.annualInterestBasisPoints
                ?: annualInterestBasisPoints
            val periodicRate = monthlyRate(appliedRate)
            val rawPayment = if (periodicRate == 0.0) {
                outstandingPrincipal.toDouble() / remainingInstallments
            } else {
                val factor = (1.0 + periodicRate).pow(remainingInstallments)
                outstandingPrincipal * periodicRate * factor / (factor - 1.0)
            }
            val interest = (outstandingPrincipal * periodicRate).roundToLong().coerceAtLeast(0)
            val principalPayment = if (installmentNumber == installmentCount) {
                outstandingPrincipal
            } else {
                (rawPayment.roundToLong() - interest)
                    .coerceAtLeast(1)
                    .coerceAtMost(outstandingPrincipal)
            }
            outstandingPrincipal = (outstandingPrincipal - principalPayment).coerceAtLeast(0)
            InstallmentScheduleEntry(
                installmentNumber = installmentNumber,
                amount = Money(principalPayment + interest, principal.currency),
                principal = Money(principalPayment, principal.currency),
                interest = Money(interest, principal.currency),
                annualInterestBasisPoints = appliedRate,
                calculationRuleVersion = calculationRuleVersion,
            )
        }
    }
}

@Serializable
data class CreditCardPayment(
    val id: String,
    val accountId: String,
    val amount: Money,
    val paidAtEpochMillis: Long,
    val sourceAccountId: String?,
    val note: String?,
    val type: DebtPaymentType = DebtPaymentType.CUSTOM,
    @SerialName("calculation_rule_version")
    val calculationRuleVersion: Int = CURRENT_FINANCIAL_RULE_VERSION,
) {
    init {
        require(id.isNotBlank())
        require(accountId.isNotBlank())
        require(amount.isPositive)
        require(paidAtEpochMillis > 0)
        require(calculationRuleVersion > 0)
    }
}

@Serializable
data class SavingsProfile(
    val accountId: String,
    val type: SavingsProductType,
    val annualYieldBasisPoints: Int,
    val openedAtEpochMillis: Long,
    val maturityAtEpochMillis: Long?,
    @SerialName("calculation_rule_version")
    val calculationRuleVersion: Int = CURRENT_FINANCIAL_RULE_VERSION,
) {
    init {
        require(accountId.isNotBlank())
        require(annualYieldBasisPoints >= 0)
        require(openedAtEpochMillis > 0)
        require(maturityAtEpochMillis == null || maturityAtEpochMillis > openedAtEpochMillis)
        require(calculationRuleVersion > 0)
    }
}

@Serializable
data class SavingsMovement(
    val id: String,
    val accountId: String,
    val type: SavingsMovementType,
    val amount: Money,
    val annualYieldBasisPoints: Int?,
    val occurredAtEpochMillis: Long,
    val note: String?,
    @SerialName("calculation_rule_version")
    val calculationRuleVersion: Int = CURRENT_FINANCIAL_RULE_VERSION,
) {
    init {
        require(id.isNotBlank())
        require(accountId.isNotBlank())
        require(amount.minorUnits >= 0)
        require(annualYieldBasisPoints == null || annualYieldBasisPoints >= 0)
        require(occurredAtEpochMillis > 0)
        require(type != SavingsMovementType.RATE_CHANGE || annualYieldBasisPoints != null)
        require(calculationRuleVersion > 0)
    }
}

data class CreditCardOverview(
    val profile: CreditCardProfile,
    val currentDebt: Money,
    val availableCredit: Money,
    val nextPayment: Money,
    val paidInstallments: Map<String, Int>,
)

data class SavingsDailyBalance(
    val atEpochMillis: Long,
    val balance: Money,
    val estimatedYield: Money,
)

@Serializable
data class LoanProfile(
    val accountId: String,
    val annualInterestBasisPoints: Int,
    val monthlyPayment: Money,
    val paymentDueDay: Int,
    val openedAtEpochMillis: Long,
    @SerialName("schedule_rule_version")
    val scheduleRuleVersion: Int = CURRENT_FINANCIAL_RULE_VERSION,
) {
    init {
        require(accountId.isNotBlank())
        require(annualInterestBasisPoints >= 0)
        require(monthlyPayment.minorUnits >= 0)
        require(paymentDueDay in 1..31)
        require(openedAtEpochMillis > 0)
        require(scheduleRuleVersion > 0)
    }
}

@Serializable
data class LoanPayment(
    val id: String,
    val accountId: String,
    val amount: Money,
    val paidAtEpochMillis: Long,
    val sourceAccountId: String?,
    val note: String?,
    val type: DebtPaymentType = DebtPaymentType.CUSTOM,
    @SerialName("calculation_rule_version")
    val calculationRuleVersion: Int = CURRENT_FINANCIAL_RULE_VERSION,
) {
    init {
        require(id.isNotBlank())
        require(accountId.isNotBlank())
        require(amount.isPositive)
        require(paidAtEpochMillis > 0)
        require(calculationRuleVersion > 0)
    }
}

data class LoanOverview(
    val profile: LoanProfile,
    val currentDebt: Money,
    val nextPayment: Money,
    val estimatedInterest: Money,
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
        var paid = 0
        var nextInstallmentCaptured = false
        purchase.installmentSchedule.forEach { installment ->
            if (unallocatedPayments >= installment.amount.minorUnits) {
                unallocatedPayments -= installment.amount.minorUnits
                paid += 1
            } else if (!nextInstallmentCaptured) {
                nextPayment += (
                    installment.amount.minorUnits -
                        unallocatedPayments.coerceAtMost(installment.amount.minorUnits)
                    ).coerceAtLeast(0)
                unallocatedPayments = 0
                nextInstallmentCaptured = true
            }
        }
        paidInstallments[purchase.id] = paid
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

fun calculateSavingsDailyProgress(
    profile: SavingsProfile,
    openingBalance: Money,
    movements: List<SavingsMovement>,
    fromEpochMillis: Long,
    toEpochMillis: Long,
): List<SavingsDailyBalance> {
    require(fromEpochMillis <= toEpochMillis)
    val start = maxOf(fromEpochMillis, profile.openedAtEpochMillis)
    return generateSequence(start) { previous ->
        (previous + MILLIS_PER_DAY_LONG).takeIf { it <= toEpochMillis }
    }.map { timestamp ->
        val projection = calculateSavingsProjection(profile, openingBalance, movements, timestamp)
        SavingsDailyBalance(
            atEpochMillis = timestamp,
            balance = projection.currentBalance,
            estimatedYield = projection.estimatedYield,
        )
    }.toList().let { progress ->
        if (progress.lastOrNull()?.atEpochMillis == toEpochMillis) {
            progress
        } else {
            progress + calculateSavingsProjection(
                profile,
                openingBalance,
                movements,
                toEpochMillis,
            ).let { projection ->
                SavingsDailyBalance(toEpochMillis, projection.currentBalance, projection.estimatedYield)
            }
        }
    }
}

fun calculateLoanOverview(
    profile: LoanProfile,
    openingDebt: Money,
    payments: List<LoanPayment>,
    atEpochMillis: Long,
): LoanOverview {
    var debt = openingDebt.minorUnits.toDouble()
    var lastTimestamp = profile.openedAtEpochMillis
    val annualRate = profile.annualInterestBasisPoints / 10_000.0
    val loanPayments = payments
        .filter { it.accountId == profile.accountId && it.paidAtEpochMillis <= atEpochMillis }
        .sortedBy { it.paidAtEpochMillis }

    fun accrue(until: Long) {
        if (until <= lastTimestamp || debt <= 0 || annualRate == 0.0) {
            lastTimestamp = maxOf(lastTimestamp, until)
            return
        }
        val days = (until - lastTimestamp).toDouble() / MILLIS_PER_DAY
        debt *= (1.0 + annualRate).pow(days / 365.0)
        lastTimestamp = until
    }

    loanPayments.forEach { payment ->
        accrue(payment.paidAtEpochMillis)
        debt = (debt - payment.amount.minorUnits).coerceAtLeast(0.0)
    }
    accrue(atEpochMillis)

    val paid = loanPayments.sumOf { it.amount.minorUnits }
    val currentDebt = debt.toLong().coerceAtLeast(0)
    val interest = (currentDebt - openingDebt.minorUnits + paid).coerceAtLeast(0)
    return LoanOverview(
        profile = profile,
        currentDebt = Money(currentDebt, openingDebt.currency),
        nextPayment = Money(
            profile.monthlyPayment.minorUnits.coerceAtMost(currentDebt),
            openingDebt.currency,
        ),
        estimatedInterest = Money(interest, openingDebt.currency),
    )
}

private fun monthlyRate(annualBasisPoints: Int): Double {
    if (annualBasisPoints == 0) return 0.0
    val annualRate = annualBasisPoints / 10_000.0
    return (1.0 + annualRate).pow(1.0 / 12.0) - 1.0
}

private const val MILLIS_PER_DAY = 86_400_000.0
private const val MILLIS_PER_DAY_LONG = 86_400_000L
