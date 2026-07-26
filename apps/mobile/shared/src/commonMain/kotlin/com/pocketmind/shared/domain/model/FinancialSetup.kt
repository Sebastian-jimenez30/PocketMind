package com.pocketmind.shared.domain.model

/** Frequency used for an expected income or recurring financial obligation. */
enum class Recurrence {
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    VARIABLE,
}

enum class SavingsPlanType {
    FLEXIBLE,
    GOAL,
    TERM_DEPOSIT,
    POCKET,
}

/** A predictable or variable source of money that belongs to the user. */
data class IncomeSource(
    val id: String,
    val name: String,
    val expectedAmount: Money,
    val recurrence: Recurrence,
    val nextExpectedAtEpochMillis: Long? = null,
    val isActive: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "An income source id is required." }
        require(name.isNotBlank()) { "An income source name is required." }
        require(expectedAmount.isPositive) { "An income source amount must be positive." }
        require(nextExpectedAtEpochMillis == null || nextExpectedAtEpochMillis > 0) {
            "Next expected date must be valid."
        }
    }
}

/** A liability whose balance and payment schedule must remain visible to the user. */
data class Debt(
    val id: String,
    val name: String,
    val outstandingBalance: Money,
    val interestRateAnnualBasisPoints: Int? = null,
    val installmentAmount: Money? = null,
    val dueDayOfMonth: Int? = null,
    val nextDueAtEpochMillis: Long? = null,
    val isActive: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "A debt id is required." }
        require(name.isNotBlank()) { "A debt name is required." }
        require(outstandingBalance.isPositive) { "A debt balance must be positive." }
        require(interestRateAnnualBasisPoints == null || interestRateAnnualBasisPoints >= 0) {
            "Interest rate must not be negative."
        }
        require(installmentAmount == null || installmentAmount.isPositive) { "Installment amount must be positive." }
        require(installmentAmount == null || installmentAmount.currency == outstandingBalance.currency) {
            "Debt currencies must match."
        }
        require(dueDayOfMonth == null || dueDayOfMonth in 1..31) { "Due day must be between 1 and 31." }
        require(nextDueAtEpochMillis == null || nextDueAtEpochMillis > 0) { "Next due date must be valid." }
    }
}

/** A saving container or plan. It is independent from the account that holds the money. */
data class SavingsPlan(
    val id: String,
    val name: String,
    val type: SavingsPlanType,
    val currentAmount: Money,
    val targetAmount: Money? = null,
    val monthlyContribution: Money? = null,
    val annualYieldBasisPoints: Int? = null,
    val targetDateEpochMillis: Long? = null,
    val isActive: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "A savings plan id is required." }
        require(name.isNotBlank()) { "A savings plan name is required." }
        require(currentAmount.minorUnits >= 0) { "Current savings must not be negative." }
        require(targetAmount == null || targetAmount.isPositive) { "Savings target must be positive." }
        require(targetAmount == null || targetAmount.currency == currentAmount.currency) { "Savings currencies must match." }
        require(monthlyContribution == null || monthlyContribution.isPositive) {
            "Monthly contribution must be positive."
        }
        require(monthlyContribution == null || monthlyContribution.currency == currentAmount.currency) {
            "Savings currencies must match."
        }
        require(annualYieldBasisPoints == null || annualYieldBasisPoints >= 0) {
            "Yield must not be negative."
        }
        require(targetDateEpochMillis == null || targetDateEpochMillis > 0) { "Target date must be valid." }
    }
}

/** A recurring payment not represented by a debt, such as rent or a subscription. */
data class RecurringObligation(
    val id: String,
    val name: String,
    val amount: Money,
    val recurrence: Recurrence,
    val dueDayOfMonth: Int? = null,
    val isActive: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "An obligation id is required." }
        require(name.isNotBlank()) { "An obligation name is required." }
        require(amount.isPositive) { "An obligation amount must be positive." }
        require(dueDayOfMonth == null || dueDayOfMonth in 1..31) { "Due day must be between 1 and 31." }
    }
}

/** Immutable initial snapshot collected during onboarding. */
data class FinancialSetup(
    val accounts: List<FinancialAccount>,
    val incomeSources: List<IncomeSource>,
    val debts: List<Debt>,
    val savingsPlans: List<SavingsPlan>,
    val recurringObligations: List<RecurringObligation>,
)
