package com.pocketmind.data.repository

import com.pocketmind.data.local.dao.AccountDao
import com.pocketmind.data.local.entity.AccountEntity
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.repository.FinancialAccountRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomFinancialAccountRepository @Inject constructor(
    private val accountDao: AccountDao,
) : FinancialAccountRepository {
    override fun observeActive(): Flow<List<FinancialAccount>> = accountDao.observeActive().map { entities ->
        entities.map(AccountEntity::toDomain)
    }

    override suspend fun getById(id: String): FinancialAccount? = accountDao.getById(id)?.toDomain()

    override suspend fun save(account: FinancialAccount) = accountDao.upsert(account.toEntity())
}

private fun AccountEntity.toDomain() = FinancialAccount(
    id = id,
    name = name,
    type = FinancialAccountType.valueOf(type),
    currency = CurrencyCode.valueOf(currency),
    openingBalance = Money(openingBalanceMinorUnits, CurrencyCode.valueOf(currency)),
    isArchived = isArchived,
)

private fun FinancialAccount.toEntity() = AccountEntity(
    id = id,
    name = name,
    type = type.name,
    currency = currency.name,
    openingBalanceMinorUnits = openingBalance.minorUnits,
    isArchived = isArchived,
)
