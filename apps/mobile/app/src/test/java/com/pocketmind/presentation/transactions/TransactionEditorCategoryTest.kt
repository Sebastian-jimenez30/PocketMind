package com.pocketmind.presentation.transactions

import com.pocketmind.shared.domain.model.TransactionCategoryId
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionEditorCategoryTest {

    @Test
    fun missingStoredCategoryIsShownAsOtherInsteadOfFood() {
        assertEquals(
            TransactionCategoryId.OTHER.name,
            null.editableCategoryId(),
        )
    }

    @Test
    fun storedCategoryIsPreservedWhenOpeningTheEditor() {
        assertEquals(
            TransactionCategoryId.FOOD.name,
            TransactionCategoryId.FOOD.name.editableCategoryId(),
        )
    }
}
