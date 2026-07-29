package com.pocketmind.data.repository

import androidx.room.withTransaction
import com.pocketmind.data.local.PocketMindDatabase
import com.pocketmind.data.local.dao.AccountDao
import com.pocketmind.data.local.dao.ManualFinanceDao
import com.pocketmind.data.local.entity.CreditCardPaymentEntity
import com.pocketmind.data.local.entity.CreditCardProfileEntity
import com.pocketmind.data.local.entity.InstallmentPurchaseEntity
import com.pocketmind.data.local.entity.SavingsMovementEntity
import com.pocketmind.data.local.entity.SavingsProfileEntity
import com.pocketmind.data.local.entity.LoanPaymentEntity
import com.pocketmind.data.local.entity.LoanProfileEntity
import com.pocketmind.shared.domain.model.CreditCardPayment
import com.pocketmind.shared.domain.model.CreditCardProfile
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.DebtPaymentType
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialProductConfiguration
import com.pocketmind.shared.domain.model.InstallmentPurchase
import com.pocketmind.shared.domain.model.InstallmentRatePeriodCodec
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.LoanPayment
import com.pocketmind.shared.domain.model.LoanProfile
import com.pocketmind.shared.domain.model.SavingsMovement
import com.pocketmind.shared.domain.model.SavingsMovementType
import com.pocketmind.shared.domain.model.SavingsProductType
import com.pocketmind.shared.domain.model.SavingsProfile
import com.pocketmind.shared.domain.repository.ManualFinanceRepository
import com.pocketmind.shared.domain.repository.TransactionRepository
import com.pocketmind.shared.domain.command.recordedMovementEffects
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomManualFinanceRepository @Inject constructor(
    private val dao: ManualFinanceDao,
    private val accountDao: AccountDao,
    private val database: PocketMindDatabase,
    private val transactionRepository: TransactionRepository,
) : ManualFinanceRepository {
    override fun observeCreditCardProfiles(): Flow<List<CreditCardProfile>> =
        dao.observeCreditCardProfiles().map { items -> items.map(CreditCardProfileEntity::toDomain) }

    override fun observeInstallmentPurchases(): Flow<List<InstallmentPurchase>> =
        dao.observeInstallmentPurchases().map { items -> items.map(InstallmentPurchaseEntity::toDomain) }

    override fun observeCreditCardPayments(): Flow<List<CreditCardPayment>> =
        dao.observeCreditCardPayments().map { items -> items.map(CreditCardPaymentEntity::toDomain) }

    override fun observeSavingsProfiles(): Flow<List<SavingsProfile>> =
        dao.observeSavingsProfiles().map { items -> items.map(SavingsProfileEntity::toDomain) }

    override fun observeSavingsMovements(): Flow<List<SavingsMovement>> =
        dao.observeSavingsMovements().map { items -> items.map(SavingsMovementEntity::toDomain) }

    override fun observeLoanProfiles(): Flow<List<LoanProfile>> =
        dao.observeLoanProfiles().map { items -> items.map(LoanProfileEntity::toDomain) }

    override fun observeLoanPayments(): Flow<List<LoanPayment>> =
        dao.observeLoanPayments().map { items -> items.map(LoanPaymentEntity::toDomain) }

    override suspend fun getCreditCardProfile(accountId: String): CreditCardProfile? =
        dao.getCreditCardProfile(accountId)?.toDomain()

    override suspend fun getSavingsProfile(accountId: String): SavingsProfile? =
        dao.getSavingsProfile(accountId)?.toDomain()

    override suspend fun getLoanProfile(accountId: String): LoanProfile? =
        dao.getLoanProfile(accountId)?.toDomain()

    override suspend fun saveProduct(
        account: FinancialAccount,
        configuration: FinancialProductConfiguration,
    ) = database.withTransaction {
        accountDao.upsert(account.toAccountEntity())
        when (configuration) {
            FinancialProductConfiguration.Standard -> {
                dao.deleteCreditCardProfile(account.id)
                dao.deleteSavingsProfile(account.id)
                dao.deleteLoanProfile(account.id)
            }
            is FinancialProductConfiguration.CreditCard -> {
                dao.upsertCreditCardProfile(configuration.profile.toEntity())
                dao.deleteSavingsProfile(account.id)
                dao.deleteLoanProfile(account.id)
            }
            is FinancialProductConfiguration.Savings -> {
                dao.upsertSavingsProfile(configuration.profile.toEntity())
                dao.deleteCreditCardProfile(account.id)
                dao.deleteLoanProfile(account.id)
            }
            is FinancialProductConfiguration.Loan -> {
                dao.upsertLoanProfile(configuration.profile.toEntity())
                dao.deleteCreditCardProfile(account.id)
                dao.deleteSavingsProfile(account.id)
            }
        }
    }

    override suspend fun saveInstallmentPurchase(
        purchase: InstallmentPurchase,
        ledgerTransaction: FinancialTransaction,
    ) = database.withTransaction {
        dao.upsertInstallmentPurchase(purchase.toEntity())
        transactionRepository.save(ledgerTransaction)
    }

    override suspend fun saveCreditCardPayment(
        payment: CreditCardPayment,
        ledgerTransaction: FinancialTransaction,
        sourceSavingsMovement: SavingsMovement?,
    ) = database.withTransaction {
        dao.upsertCreditCardPayment(payment.toEntity())
        sourceSavingsMovement?.let { dao.upsertSavingsMovement(it.toEntity()) }
        transactionRepository.save(ledgerTransaction)
    }

    override suspend fun saveSavingsMovement(
        movement: SavingsMovement,
        ledgerTransaction: FinancialTransaction?,
        relatedSavingsMovement: SavingsMovement?,
    ): Unit = database.withTransaction {
        dao.upsertSavingsMovement(movement.toEntity())
        relatedSavingsMovement?.let { dao.upsertSavingsMovement(it.toEntity()) }
        ledgerTransaction?.let { transactionRepository.save(it) }
        Unit
    }

    override suspend fun saveLoanPayment(
        payment: LoanPayment,
        ledgerTransaction: FinancialTransaction,
        sourceSavingsMovement: SavingsMovement?,
    ) = database.withTransaction {
        dao.upsertLoanPayment(payment.toEntity())
        sourceSavingsMovement?.let { dao.upsertSavingsMovement(it.toEntity()) }
        transactionRepository.save(ledgerTransaction)
    }

    override suspend fun deleteRecordedMovement(commandId: String) = database.withTransaction {
        val effects = recordedMovementEffects(commandId)
        dao.deleteInstallmentPurchase(effects.commandId)
        dao.deleteSavingsMovementEffects(effects.savingsMovementIds)
        dao.deleteCreditCardPayment(effects.commandId)
        dao.deleteLoanPayment(effects.commandId)
        effects.ledgerTransactionIds.forEach { transactionRepository.delete(it) }
    }
}

private fun CreditCardProfileEntity.toDomain() = CreditCardProfile(
    accountId = accountId,
    creditLimit = Money(creditLimitMinorUnits, CurrencyCode.valueOf(currency)),
    annualInterestBasisPoints = annualInterestBasisPoints,
    statementClosingDay = statementClosingDay,
    paymentDueDay = paymentDueDay,
    openingDebtInstallmentCount = openingDebtInstallmentCount,
    openingDebtFirstPaymentAtEpochMillis = openingDebtFirstPaymentAtEpochMillis,
    scheduleRuleVersion = scheduleRuleVersion,
)

private fun CreditCardProfile.toEntity() = CreditCardProfileEntity(
    accountId = accountId,
    creditLimitMinorUnits = creditLimit.minorUnits,
    currency = creditLimit.currency.name,
    annualInterestBasisPoints = annualInterestBasisPoints,
    statementClosingDay = statementClosingDay,
    paymentDueDay = paymentDueDay,
    openingDebtInstallmentCount = openingDebtInstallmentCount,
    openingDebtFirstPaymentAtEpochMillis = openingDebtFirstPaymentAtEpochMillis,
    scheduleRuleVersion = scheduleRuleVersion,
)

private fun InstallmentPurchaseEntity.toDomain() = InstallmentPurchase(
    id = id,
    accountId = accountId,
    merchant = merchant,
    principal = Money(principalMinorUnits, CurrencyCode.valueOf(currency)),
    installmentCount = installmentCount,
    annualInterestBasisPoints = annualInterestBasisPoints,
    purchasedAtEpochMillis = purchasedAtEpochMillis,
    firstPaymentAtEpochMillis = firstPaymentAtEpochMillis,
    categoryId = categoryId,
    note = note,
    promotionalRatePeriods = InstallmentRatePeriodCodec.decode(promotionalRatePeriodsJson),
    calculationRuleVersion = calculationRuleVersion,
)

private fun InstallmentPurchase.toEntity() = InstallmentPurchaseEntity(
    id = id,
    accountId = accountId,
    merchant = merchant,
    principalMinorUnits = principal.minorUnits,
    currency = principal.currency.name,
    installmentCount = installmentCount,
    annualInterestBasisPoints = annualInterestBasisPoints,
    purchasedAtEpochMillis = purchasedAtEpochMillis,
    firstPaymentAtEpochMillis = firstPaymentAtEpochMillis,
    categoryId = categoryId,
    note = note,
    promotionalRatePeriodsJson = InstallmentRatePeriodCodec.encode(promotionalRatePeriods),
    calculationRuleVersion = calculationRuleVersion,
)

private fun CreditCardPaymentEntity.toDomain() = CreditCardPayment(
    id = id,
    accountId = accountId,
    amount = Money(amountMinorUnits, CurrencyCode.valueOf(currency)),
    paidAtEpochMillis = paidAtEpochMillis,
    sourceAccountId = sourceAccountId,
    note = note,
    type = DebtPaymentType.valueOf(paymentType),
    calculationRuleVersion = calculationRuleVersion,
)

private fun CreditCardPayment.toEntity() = CreditCardPaymentEntity(
    id = id,
    accountId = accountId,
    amountMinorUnits = amount.minorUnits,
    currency = amount.currency.name,
    paidAtEpochMillis = paidAtEpochMillis,
    sourceAccountId = sourceAccountId,
    note = note,
    paymentType = type.name,
    calculationRuleVersion = calculationRuleVersion,
)

private fun SavingsProfileEntity.toDomain() = SavingsProfile(
    accountId = accountId,
    type = SavingsProductType.valueOf(type),
    annualYieldBasisPoints = annualYieldBasisPoints,
    openedAtEpochMillis = openedAtEpochMillis,
    maturityAtEpochMillis = maturityAtEpochMillis,
    calculationRuleVersion = calculationRuleVersion,
)

private fun SavingsProfile.toEntity() = SavingsProfileEntity(
    accountId = accountId,
    type = type.name,
    annualYieldBasisPoints = annualYieldBasisPoints,
    openedAtEpochMillis = openedAtEpochMillis,
    maturityAtEpochMillis = maturityAtEpochMillis,
    calculationRuleVersion = calculationRuleVersion,
)

private fun SavingsMovementEntity.toDomain() = SavingsMovement(
    id = id,
    accountId = accountId,
    type = SavingsMovementType.valueOf(type),
    amount = Money(amountMinorUnits, CurrencyCode.valueOf(currency)),
    annualYieldBasisPoints = annualYieldBasisPoints,
    occurredAtEpochMillis = occurredAtEpochMillis,
    note = note,
    calculationRuleVersion = calculationRuleVersion,
)

private fun SavingsMovement.toEntity() = SavingsMovementEntity(
    id = id,
    accountId = accountId,
    type = type.name,
    amountMinorUnits = amount.minorUnits,
    currency = amount.currency.name,
    annualYieldBasisPoints = annualYieldBasisPoints,
    occurredAtEpochMillis = occurredAtEpochMillis,
    note = note,
    calculationRuleVersion = calculationRuleVersion,
)

private fun LoanProfileEntity.toDomain() = LoanProfile(
    accountId = accountId,
    annualInterestBasisPoints = annualInterestBasisPoints,
    monthlyPayment = Money(monthlyPaymentMinorUnits, CurrencyCode.valueOf(currency)),
    paymentDueDay = paymentDueDay,
    openedAtEpochMillis = openedAtEpochMillis,
    scheduleRuleVersion = scheduleRuleVersion,
)

private fun LoanProfile.toEntity() = LoanProfileEntity(
    accountId = accountId,
    annualInterestBasisPoints = annualInterestBasisPoints,
    monthlyPaymentMinorUnits = monthlyPayment.minorUnits,
    currency = monthlyPayment.currency.name,
    paymentDueDay = paymentDueDay,
    openedAtEpochMillis = openedAtEpochMillis,
    scheduleRuleVersion = scheduleRuleVersion,
)

private fun LoanPaymentEntity.toDomain() = LoanPayment(
    id = id,
    accountId = accountId,
    amount = Money(amountMinorUnits, CurrencyCode.valueOf(currency)),
    paidAtEpochMillis = paidAtEpochMillis,
    sourceAccountId = sourceAccountId,
    note = note,
    type = DebtPaymentType.valueOf(paymentType),
    calculationRuleVersion = calculationRuleVersion,
)

private fun LoanPayment.toEntity() = LoanPaymentEntity(
    id = id,
    accountId = accountId,
    amountMinorUnits = amount.minorUnits,
    currency = amount.currency.name,
    paidAtEpochMillis = paidAtEpochMillis,
    sourceAccountId = sourceAccountId,
    note = note,
    paymentType = type.name,
    calculationRuleVersion = calculationRuleVersion,
)
