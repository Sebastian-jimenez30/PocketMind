package com.pocketmind.data.repository

import com.pocketmind.data.local.dao.TransactionDao
import com.pocketmind.data.local.dao.AccountDao
import com.pocketmind.data.local.entity.TransactionEntity
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.DashboardSummary
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionSource
import com.pocketmind.shared.domain.model.TransactionStatus
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.shared.domain.repository.DashboardRepository
import com.pocketmind.shared.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomTransactionRepository @Inject constructor(private val transactionDao: TransactionDao) : TransactionRepository {
    override fun observeAll(): Flow<List<FinancialTransaction>> = transactionDao.observeAll().map { entities -> entities.map(TransactionEntity::toDomain) }
    override suspend fun getById(id: String): FinancialTransaction? = transactionDao.getById(id)?.toDomain()
    override suspend fun save(transaction: FinancialTransaction) = transactionDao.upsert(transaction.toEntity())
    override suspend fun delete(id: String) = transactionDao.deleteById(id)
}

class RoomDashboardRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
) : DashboardRepository {
    override fun observeSummary(): Flow<DashboardSummary> = kotlinx.coroutines.flow.combine(
        transactionDao.observeAll(),
        accountDao.observeActive(),
    ) { transactions, accounts ->
        val postedTransactions = transactions.filter { it.status == TransactionStatus.POSTED.name }
        val income = postedTransactions.filter { it.type == TransactionType.INCOME.name }.sumOf { it.amountMinorUnits }
        val expense = postedTransactions.filter { it.type == TransactionType.EXPENSE.name }.sumOf { it.amountMinorUnits }
        val openingBalance = accounts.sumOf { it.openingBalanceMinorUnits }
        DashboardSummary(Money(openingBalance + income - expense, CurrencyCode.COP), Money(income, CurrencyCode.COP), Money(expense, CurrencyCode.COP))
    }
}

private fun TransactionEntity.toDomain() = FinancialTransaction(
    id,
    accountId,
    TransactionType.valueOf(type),
    Money(amountMinorUnits, CurrencyCode.valueOf(currency)),
    occurredAtEpochMillis,
    categoryId,
    merchant,
    note,
    TransactionSource.valueOf(source),
    TransactionStatus.valueOf(status),
    relatedAccountId,
)
private fun FinancialTransaction.toEntity() = TransactionEntity(
    id,
    accountId,
    type.name,
    amount.minorUnits,
    amount.currency.name,
    occurredAtEpochMillis,
    categoryId,
    merchant,
    note,
    source.name,
    status.name,
    relatedAccountId,
)
