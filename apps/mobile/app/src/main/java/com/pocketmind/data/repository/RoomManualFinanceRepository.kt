package com.pocketmind.data.repository

import androidx.room.withTransaction
import com.pocketmind.data.local.PocketMindDatabase
import com.pocketmind.data.local.dao.ManualFinanceDao
import com.pocketmind.data.local.entity.CreditCardPaymentEntity
import com.pocketmind.data.local.entity.CreditCardProfileEntity
import com.pocketmind.data.local.entity.InstallmentPurchaseEntity
import com.pocketmind.data.local.entity.SavingsMovementEntity
import com.pocketmind.data.local.entity.SavingsProfileEntity
import com.pocketmind.shared.domain.model.CreditCardPayment
import com.pocketmind.shared.domain.model.CreditCardProfile
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.InstallmentPurchase
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.SavingsMovement
import com.pocketmind.shared.domain.model.SavingsMovementType
import com.pocketmind.shared.domain.model.SavingsProductType
import com.pocketmind.shared.domain.model.SavingsProfile
import com.pocketmind.shared.domain.repository.ManualFinanceRepository
import com.pocketmind.shared.domain.repository.TransactionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomManualFinanceRepository @Inject constructor(
    private val dao: ManualFinanceDao,
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

    override suspend fun getCreditCardProfile(accountId: String): CreditCardProfile? =
        dao.getCreditCardProfile(accountId)?.toDomain()

    override suspend fun getSavingsProfile(accountId: String): SavingsProfile? =
        dao.getSavingsProfile(accountId)?.toDomain()

    override suspend fun saveCreditCardProfile(profile: CreditCardProfile) =
        dao.upsertCreditCardProfile(profile.toEntity())

    override suspend fun saveSavingsProfile(profile: SavingsProfile) =
        dao.upsertSavingsProfile(profile.toEntity())

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
    ) = database.withTransaction {
        dao.upsertCreditCardPayment(payment.toEntity())
        transactionRepository.save(ledgerTransaction)
    }

    override suspend fun saveSavingsMovement(
        movement: SavingsMovement,
        ledgerTransaction: FinancialTransaction?,
    ): Unit = database.withTransaction {
        dao.upsertSavingsMovement(movement.toEntity())
        ledgerTransaction?.let { transactionRepository.save(it) }
        Unit
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
)

private fun CreditCardPaymentEntity.toDomain() = CreditCardPayment(
    id = id,
    accountId = accountId,
    amount = Money(amountMinorUnits, CurrencyCode.valueOf(currency)),
    paidAtEpochMillis = paidAtEpochMillis,
    sourceAccountId = sourceAccountId,
    note = note,
)

private fun CreditCardPayment.toEntity() = CreditCardPaymentEntity(
    id = id,
    accountId = accountId,
    amountMinorUnits = amount.minorUnits,
    currency = amount.currency.name,
    paidAtEpochMillis = paidAtEpochMillis,
    sourceAccountId = sourceAccountId,
    note = note,
)

private fun SavingsProfileEntity.toDomain() = SavingsProfile(
    accountId = accountId,
    type = SavingsProductType.valueOf(type),
    annualYieldBasisPoints = annualYieldBasisPoints,
    openedAtEpochMillis = openedAtEpochMillis,
    maturityAtEpochMillis = maturityAtEpochMillis,
)

private fun SavingsProfile.toEntity() = SavingsProfileEntity(
    accountId = accountId,
    type = type.name,
    annualYieldBasisPoints = annualYieldBasisPoints,
    openedAtEpochMillis = openedAtEpochMillis,
    maturityAtEpochMillis = maturityAtEpochMillis,
)

private fun SavingsMovementEntity.toDomain() = SavingsMovement(
    id = id,
    accountId = accountId,
    type = SavingsMovementType.valueOf(type),
    amount = Money(amountMinorUnits, CurrencyCode.valueOf(currency)),
    annualYieldBasisPoints = annualYieldBasisPoints,
    occurredAtEpochMillis = occurredAtEpochMillis,
    note = note,
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
)
