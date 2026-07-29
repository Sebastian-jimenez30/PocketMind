package com.pocketmind.presentation.transactions

import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.TransactionCategoryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualRecordTypeTest {

    @Test
    fun `expense family contains every outgoing manual action`() {
        assertEquals(
            listOf(
                ManualRecordType.EXPENSE,
                ManualRecordType.CARD_PURCHASE,
                ManualRecordType.CARD_PAYMENT,
                ManualRecordType.LOAN_PAYMENT,
                ManualRecordType.SAVINGS_DEPOSIT,
            ),
            ManualRecordGroup.EXPENSE.operations(),
        )
    }

    @Test
    fun `income family contains income and savings withdrawal`() {
        assertEquals(
            listOf(ManualRecordType.INCOME, ManualRecordType.SAVINGS_WITHDRAWAL),
            ManualRecordGroup.INCOME.operations(),
        )
    }

    @Test
    fun `own transfer uses only liquid products`() {
        assertTrue(FinancialAccountType.BANK_ACCOUNT.isCompatibleWith(ManualRecordType.TRANSFER))
        assertTrue(FinancialAccountType.CASH.isCompatibleWith(ManualRecordType.TRANSFER))
        assertTrue(FinancialAccountType.SAVINGS.isCompatibleWith(ManualRecordType.TRANSFER))
        assertFalse(FinancialAccountType.CREDIT_CARD.isCompatibleWith(ManualRecordType.TRANSFER))
        assertFalse(FinancialAccountType.LOAN.isCompatibleWith(ManualRecordType.TRANSFER))
    }

    @Test
    fun `categories are relevant to selected operation`() {
        assertEquals(
            listOf(TransactionCategoryId.DEBT_PAYMENT),
            ManualRecordType.CARD_PAYMENT.categories(),
        )
        assertEquals(
            listOf(TransactionCategoryId.SAVINGS),
            ManualRecordType.SAVINGS_DEPOSIT.categories(),
        )
        assertEquals(
            listOf(TransactionCategoryId.TRANSFER),
            ManualRecordType.TRANSFER.categories(),
        )
    }
}

