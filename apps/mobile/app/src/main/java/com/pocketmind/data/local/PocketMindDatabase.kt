package com.pocketmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pocketmind.data.local.dao.AccountDao
import com.pocketmind.data.local.dao.FinancialSetupDao
import com.pocketmind.data.local.dao.TransactionDao
import com.pocketmind.data.local.entity.AccountEntity
import com.pocketmind.data.local.entity.DebtEntity
import com.pocketmind.data.local.entity.FinancialSetupEntity
import com.pocketmind.data.local.entity.IncomeSourceEntity
import com.pocketmind.data.local.entity.RecurringObligationEntity
import com.pocketmind.data.local.entity.SavingsPlanEntity
import com.pocketmind.data.local.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        FinancialSetupEntity::class,
        IncomeSourceEntity::class,
        DebtEntity::class,
        SavingsPlanEntity::class,
        RecurringObligationEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class PocketMindDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun financialSetupDao(): FinancialSetupDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `financial_setup` (`id` INTEGER NOT NULL, " +
                        "`completedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `income_sources` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`expectedAmountMinorUnits` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                        "`recurrence` TEXT NOT NULL, `nextExpectedAtEpochMillis` INTEGER, " +
                        "`isActive` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `debts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`outstandingBalanceMinorUnits` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                        "`interestRateAnnualBasisPoints` INTEGER, `installmentAmountMinorUnits` INTEGER, " +
                        "`dueDayOfMonth` INTEGER, `nextDueAtEpochMillis` INTEGER, `isActive` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `savings_plans` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, `currentAmountMinorUnits` INTEGER NOT NULL, " +
                        "`targetAmountMinorUnits` INTEGER, `monthlyContributionMinorUnits` INTEGER, " +
                        "`currency` TEXT NOT NULL, `annualYieldBasisPoints` INTEGER, " +
                        "`targetDateEpochMillis` INTEGER, `isActive` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring_obligations` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`amountMinorUnits` INTEGER NOT NULL, `currency` TEXT NOT NULL, `recurrence` TEXT NOT NULL, " +
                        "`dueDayOfMonth` INTEGER, `isActive` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }
    }
}
