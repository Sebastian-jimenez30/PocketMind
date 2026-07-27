package com.pocketmind.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PocketMindDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PocketMindDatabase::class.java,
    )

    @Test
    fun migration2To3_preservesOnboardingSavingsAndDebtAsProducts() {
        helper.createDatabase(DATABASE_NAME, 2).apply {
            execSQL(
                "INSERT INTO financial_setup (id, completedAtEpochMillis) VALUES (1, 1000000)",
            )
            execSQL(
                "INSERT INTO savings_plans " +
                    "(id, name, type, currentAmountMinorUnits, targetAmountMinorUnits, monthlyContributionMinorUnits, " +
                    "currency, annualYieldBasisPoints, targetDateEpochMillis, isActive) " +
                    "VALUES ('saving', 'CDT', 'TERM_DEPOSIT', 1000000, NULL, NULL, 'COP', 1000, NULL, 1)",
            )
            execSQL(
                "INSERT INTO debts " +
                    "(id, name, outstandingBalanceMinorUnits, currency, interestRateAnnualBasisPoints, " +
                    "installmentAmountMinorUnits, dueDayOfMonth, nextDueAtEpochMillis, isActive) " +
                    "VALUES ('debt', 'Crédito', 500000, 'COP', 1200, 100000, 15, NULL, 1)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            3,
            true,
            PocketMindDatabase.MIGRATION_2_3,
        )

        assertEquals(1, migrated.singleLong("SELECT COUNT(*) FROM accounts WHERE id = 'saving' AND type = 'SAVINGS'"))
        assertEquals(1, migrated.singleLong("SELECT COUNT(*) FROM savings_profiles WHERE accountId = 'saving'"))
        assertEquals(1, migrated.singleLong("SELECT COUNT(*) FROM accounts WHERE id = 'debt' AND type = 'LOAN'"))
        migrated.close()
    }

    private fun SupportSQLiteDatabase.singleLong(query: String): Long =
        this.query(query).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private companion object {
        const val DATABASE_NAME = "migration-test"
    }
}
