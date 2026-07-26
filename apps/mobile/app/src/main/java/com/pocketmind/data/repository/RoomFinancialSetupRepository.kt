package com.pocketmind.data.repository

import androidx.room.withTransaction
import com.pocketmind.data.local.PocketMindDatabase
import com.pocketmind.data.local.dao.FinancialSetupDao
import com.pocketmind.data.local.dao.ManualFinanceDao
import com.pocketmind.data.local.entity.AccountEntity
import com.pocketmind.data.local.entity.DebtEntity
import com.pocketmind.data.local.entity.FinancialSetupEntity
import com.pocketmind.data.local.entity.IncomeSourceEntity
import com.pocketmind.data.local.entity.RecurringObligationEntity
import com.pocketmind.data.local.entity.SavingsPlanEntity
import com.pocketmind.data.local.entity.SavingsProfileEntity
import com.pocketmind.shared.domain.model.Debt
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialSetup
import com.pocketmind.shared.domain.model.IncomeSource
import com.pocketmind.shared.domain.model.RecurringObligation
import com.pocketmind.shared.domain.model.SavingsPlan
import com.pocketmind.shared.domain.repository.FinancialSetupRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Saves the starting picture atomically so partial onboarding is never considered complete. */
class RoomFinancialSetupRepository @Inject constructor(
    private val database: PocketMindDatabase,
    private val financialSetupDao: FinancialSetupDao,
    private val manualFinanceDao: ManualFinanceDao,
) : FinancialSetupRepository {
    override fun observeIsCompleted(): Flow<Boolean> = financialSetupDao.observeIsCompleted()

    override suspend fun saveInitialSetup(setup: FinancialSetup) {
        database.withTransaction {
            setup.accounts.forEach { account -> database.accountDao().upsert(account.toEntity()) }
            financialSetupDao.upsertIncomeSources(setup.incomeSources.map(IncomeSource::toEntity))
            financialSetupDao.upsertDebts(setup.debts.map(Debt::toEntity))
            financialSetupDao.upsertSavingsPlans(setup.savingsPlans.map(SavingsPlan::toEntity))
            setup.savingsPlans.forEach { plan ->
                manualFinanceDao.upsertSavingsProfile(
                    SavingsProfileEntity(
                        accountId = plan.id,
                        type = when (plan.type) {
                            com.pocketmind.shared.domain.model.SavingsPlanType.TERM_DEPOSIT -> "TERM_DEPOSIT"
                            com.pocketmind.shared.domain.model.SavingsPlanType.POCKET -> "POCKET"
                            else -> "SIMPLE"
                        },
                        annualYieldBasisPoints = plan.annualYieldBasisPoints ?: 0,
                        openedAtEpochMillis = System.currentTimeMillis(),
                        maturityAtEpochMillis = plan.targetDateEpochMillis,
                    ),
                )
            }
            financialSetupDao.upsertRecurringObligations(setup.recurringObligations.map(RecurringObligation::toEntity))
            financialSetupDao.upsertSetup(
                FinancialSetupEntity(completedAtEpochMillis = System.currentTimeMillis()),
            )
        }
    }
}

private fun FinancialAccount.toEntity() = AccountEntity(
    id = id,
    name = name,
    type = type.name,
    currency = currency.name,
    openingBalanceMinorUnits = openingBalance.minorUnits,
    isArchived = isArchived,
)

private fun IncomeSource.toEntity() = IncomeSourceEntity(
    id = id,
    name = name,
    expectedAmountMinorUnits = expectedAmount.minorUnits,
    currency = expectedAmount.currency.name,
    recurrence = recurrence.name,
    nextExpectedAtEpochMillis = nextExpectedAtEpochMillis,
    isActive = isActive,
)

private fun Debt.toEntity() = DebtEntity(
    id = id,
    name = name,
    outstandingBalanceMinorUnits = outstandingBalance.minorUnits,
    currency = outstandingBalance.currency.name,
    interestRateAnnualBasisPoints = interestRateAnnualBasisPoints,
    installmentAmountMinorUnits = installmentAmount?.minorUnits,
    dueDayOfMonth = dueDayOfMonth,
    nextDueAtEpochMillis = nextDueAtEpochMillis,
    isActive = isActive,
)

private fun SavingsPlan.toEntity() = SavingsPlanEntity(
    id = id,
    name = name,
    type = type.name,
    currentAmountMinorUnits = currentAmount.minorUnits,
    targetAmountMinorUnits = targetAmount?.minorUnits,
    monthlyContributionMinorUnits = monthlyContribution?.minorUnits,
    currency = currentAmount.currency.name,
    annualYieldBasisPoints = annualYieldBasisPoints,
    targetDateEpochMillis = targetDateEpochMillis,
    isActive = isActive,
)

private fun RecurringObligation.toEntity() = RecurringObligationEntity(
    id = id,
    name = name,
    amountMinorUnits = amount.minorUnits,
    currency = amount.currency.name,
    recurrence = recurrence.name,
    dueDayOfMonth = dueDayOfMonth,
    isActive = isActive,
)
