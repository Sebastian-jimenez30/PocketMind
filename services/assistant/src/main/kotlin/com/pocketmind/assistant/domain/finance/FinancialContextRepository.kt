package com.pocketmind.assistant.domain.finance

import com.pocketmind.assistant.auth.AuthenticatedUser

/**
 * Read-only boundary for the assistant's financial context.
 *
 * Deliberately exposes no insert, update or delete operation. The assistant
 * cannot bypass the confirmable command pipeline through this dependency.
 */
fun interface FinancialContextRepository {
    suspend fun fetchSnapshot(session: AuthenticatedUser): FinancialContextSnapshot
}
