package com.pocketmind.shared.domain.usecase

import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.repository.TransactionRepository

class GetTransactionUseCase(private val repository: TransactionRepository) {
    suspend operator fun invoke(id: String): FinancialTransaction? = repository.getById(id)
}
