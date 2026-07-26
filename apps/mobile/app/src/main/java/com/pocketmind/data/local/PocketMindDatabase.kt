package com.pocketmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pocketmind.data.local.dao.AccountDao
import com.pocketmind.data.local.dao.FinancialSetupDao
import com.pocketmind.data.local.dao.ManualFinanceDao
import com.pocketmind.data.local.dao.TransactionDao
import com.pocketmind.data.local.entity.AccountEntity
import com.pocketmind.data.local.entity.DebtEntity
import com.pocketmind.data.local.entity.CreditCardPaymentEntity
import com.pocketmind.data.local.entity.CreditCardProfileEntity
import com.pocketmind.data.local.entity.FinancialSetupEntity
import com.pocketmind.data.local.entity.IncomeSourceEntity
import com.pocketmind.data.local.entity.InstallmentPurchaseEntity
import com.pocketmind.data.local.entity.RecurringObligationEntity
import com.pocketmind.data.local.entity.SavingsPlanEntity
import com.pocketmind.data.local.entity.SavingsMovementEntity
import com.pocketmind.data.local.entity.SavingsProfileEntity
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
        CreditCardProfileEntity::class,
        InstallmentPurchaseEntity::class,
        CreditCardPaymentEntity::class,
        SavingsProfileEntity::class,
        SavingsMovementEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class PocketMindDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun financialSetupDao(): FinancialSetupDao
    abstract fun manualFinanceDao(): ManualFinanceDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `transactions` ADD COLUMN `relatedAccountId` TEXT")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `credit_card_profiles` (`accountId` TEXT NOT NULL, " +
                        "`creditLimitMinorUnits` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                        "`annualInterestBasisPoints` INTEGER NOT NULL, `statementClosingDay` INTEGER NOT NULL, " +
                        "`paymentDueDay` INTEGER NOT NULL, `openingDebtInstallmentCount` INTEGER NOT NULL, " +
                        "`openingDebtFirstPaymentAtEpochMillis` INTEGER, PRIMARY KEY(`accountId`), " +
                        "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `installment_purchases` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, " +
                        "`merchant` TEXT NOT NULL, `principalMinorUnits` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                        "`installmentCount` INTEGER NOT NULL, `annualInterestBasisPoints` INTEGER NOT NULL, " +
                        "`purchasedAtEpochMillis` INTEGER NOT NULL, `firstPaymentAtEpochMillis` INTEGER NOT NULL, " +
                        "`categoryId` TEXT, `note` TEXT, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_purchases_accountId` ON `installment_purchases` (`accountId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_purchases_firstPaymentAtEpochMillis` ON `installment_purchases` (`firstPaymentAtEpochMillis`)")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `credit_card_payments` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, " +
                        "`amountMinorUnits` INTEGER NOT NULL, `currency` TEXT NOT NULL, `paidAtEpochMillis` INTEGER NOT NULL, " +
                        "`sourceAccountId` TEXT, `note` TEXT, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_credit_card_payments_accountId` ON `credit_card_payments` (`accountId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_credit_card_payments_paidAtEpochMillis` ON `credit_card_payments` (`paidAtEpochMillis`)")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `savings_profiles` (`accountId` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                        "`annualYieldBasisPoints` INTEGER NOT NULL, `openedAtEpochMillis` INTEGER NOT NULL, " +
                        "`maturityAtEpochMillis` INTEGER, PRIMARY KEY(`accountId`), " +
                        "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `savings_movements` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, `amountMinorUnits` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                        "`annualYieldBasisPoints` INTEGER, `occurredAtEpochMillis` INTEGER NOT NULL, `note` TEXT, " +
                        "PRIMARY KEY(`id`), FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_movements_accountId` ON `savings_movements` (`accountId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_movements_occurredAtEpochMillis` ON `savings_movements` (`occurredAtEpochMillis`)")
                database.execSQL(
                    "INSERT OR IGNORE INTO `accounts` (`id`, `name`, `type`, `currency`, `openingBalanceMinorUnits`, `isArchived`) " +
                        "SELECT `id`, `name`, 'SAVINGS', `currency`, `currentAmountMinorUnits`, 0 FROM `savings_plans`",
                )
                database.execSQL(
                    "INSERT OR IGNORE INTO `savings_profiles` (`accountId`, `type`, `annualYieldBasisPoints`, `openedAtEpochMillis`, `maturityAtEpochMillis`) " +
                        "SELECT `id`, CASE `type` WHEN 'TERM_DEPOSIT' THEN 'TERM_DEPOSIT' WHEN 'POCKET' THEN 'POCKET' ELSE 'SIMPLE' END, " +
                        "COALESCE(`annualYieldBasisPoints`, 0), " +
                        "COALESCE((SELECT `completedAtEpochMillis` FROM `financial_setup` WHERE `id` = 1), CAST(strftime('%s','now') AS INTEGER) * 1000), " +
                        "`targetDateEpochMillis` FROM `savings_plans`",
                )
                database.execSQL(
                    "INSERT OR IGNORE INTO `accounts` (`id`, `name`, `type`, `currency`, `openingBalanceMinorUnits`, `isArchived`) " +
                        "SELECT `id`, `name`, 'LOAN', `currency`, `outstandingBalanceMinorUnits`, 0 FROM `debts`",
                )
            }
        }
    }
}
