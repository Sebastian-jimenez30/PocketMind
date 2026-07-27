package com.pocketmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pocketmind.data.local.dao.AccountDao
import com.pocketmind.data.local.dao.FinancialSetupDao
import com.pocketmind.data.local.dao.ManualFinanceDao
import com.pocketmind.data.local.dao.SyncDao
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
import com.pocketmind.data.local.entity.LoanPaymentEntity
import com.pocketmind.data.local.entity.LoanProfileEntity
import com.pocketmind.data.local.entity.TransactionEntity
import com.pocketmind.data.local.entity.SyncControlEntity
import com.pocketmind.data.local.entity.SyncOutboxEntity

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
        LoanProfileEntity::class,
        LoanPaymentEntity::class,
        SyncOutboxEntity::class,
        SyncControlEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class PocketMindDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun financialSetupDao(): FinancialSetupDao
    abstract fun manualFinanceDao(): ManualFinanceDao
    abstract fun syncDao(): SyncDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loan_profiles` (`accountId` TEXT NOT NULL, " +
                        "`annualInterestBasisPoints` INTEGER NOT NULL, `monthlyPaymentMinorUnits` INTEGER NOT NULL, " +
                        "`currency` TEXT NOT NULL, `paymentDueDay` INTEGER NOT NULL, " +
                        "`openedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`accountId`), " +
                        "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loan_payments` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, " +
                        "`amountMinorUnits` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                        "`paidAtEpochMillis` INTEGER NOT NULL, `sourceAccountId` TEXT, `note` TEXT, " +
                        "PRIMARY KEY(`id`), FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_loan_payments_accountId` ON `loan_payments` (`accountId`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_loan_payments_paidAtEpochMillis` ON `loan_payments` (`paidAtEpochMillis`)",
                )
                database.execSQL(
                    "INSERT OR IGNORE INTO `loan_profiles` " +
                        "(`accountId`, `annualInterestBasisPoints`, `monthlyPaymentMinorUnits`, `currency`, " +
                        "`paymentDueDay`, `openedAtEpochMillis`) " +
                        "SELECT `accounts`.`id`, COALESCE(`debts`.`interestRateAnnualBasisPoints`, 0), " +
                        "COALESCE(`debts`.`installmentAmountMinorUnits`, 0), `accounts`.`currency`, " +
                        "COALESCE(`debts`.`dueDayOfMonth`, 1), " +
                        "COALESCE((SELECT `completedAtEpochMillis` FROM `financial_setup` WHERE `id` = 1), " +
                        "CAST(strftime('%s','now') AS INTEGER) * 1000) " +
                        "FROM `accounts` LEFT JOIN `debts` ON `debts`.`id` = `accounts`.`id` " +
                        "WHERE `accounts`.`type` = 'LOAN'",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_outbox` (" +
                        "`entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `operation` TEXT NOT NULL, " +
                        "`queuedAtEpochMillis` INTEGER NOT NULL, `attemptCount` INTEGER NOT NULL, " +
                        "`lastError` TEXT, PRIMARY KEY(`entityType`, `entityId`))",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_control` (" +
                        "`id` INTEGER NOT NULL, `userId` TEXT, `isApplyingRemote` INTEGER NOT NULL, " +
                        "`isInitialSyncCompleted` INTEGER NOT NULL, `isSyncing` INTEGER NOT NULL, " +
                        "`lastSyncedAtEpochMillis` INTEGER, `lastError` TEXT, PRIMARY KEY(`id`))",
                )
                ensureSyncInfrastructure(database)
            }
        }

        /**
         * Migrations install this infrastructure for upgraded databases, while
         * the Room callback invokes it for both fresh and restored databases.
         */
        fun ensureSyncInfrastructure(database: SupportSQLiteDatabase) {
            database.execSQL(
                "INSERT OR IGNORE INTO `sync_control` " +
                    "(`id`, `userId`, `isApplyingRemote`, `isInitialSyncCompleted`, `isSyncing`, " +
                    "`lastSyncedAtEpochMillis`, `lastError`) VALUES (1, NULL, 0, 0, 0, NULL, NULL)",
            )
            listOf(
                SyncTrigger("financial_setup", "id", "FINANCIAL_SETUP"),
                SyncTrigger("accounts", "id", "ACCOUNT"),
                SyncTrigger("transactions", "id", "TRANSACTION"),
                SyncTrigger("income_sources", "id", "INCOME_SOURCE"),
                SyncTrigger("debts", "id", "DEBT"),
                SyncTrigger("savings_plans", "id", "SAVINGS_PLAN"),
                SyncTrigger("recurring_obligations", "id", "RECURRING_OBLIGATION"),
                SyncTrigger("credit_card_profiles", "accountId", "CREDIT_CARD_PROFILE"),
                SyncTrigger("installment_purchases", "id", "INSTALLMENT_PURCHASE"),
                SyncTrigger("credit_card_payments", "id", "CREDIT_CARD_PAYMENT"),
                SyncTrigger("savings_profiles", "accountId", "SAVINGS_PROFILE"),
                SyncTrigger("savings_movements", "id", "SAVINGS_MOVEMENT"),
                SyncTrigger("loan_profiles", "accountId", "LOAN_PROFILE"),
                SyncTrigger("loan_payments", "id", "LOAN_PAYMENT"),
            ).forEach { trigger ->
                createSyncTriggers(database, trigger)
            }
        }

        private data class SyncTrigger(
            val table: String,
            val idColumn: String,
            val entityType: String,
        )

        private fun createSyncTriggers(
            database: SupportSQLiteDatabase,
            trigger: SyncTrigger,
        ) {
            listOf("INSERT", "UPDATE").forEach { action ->
                database.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `sync_${trigger.table}_${action.lowercase()}` " +
                        "AFTER $action ON `${trigger.table}` " +
                        "WHEN COALESCE((SELECT `isApplyingRemote` FROM `sync_control` WHERE `id` = 1), 0) = 0 " +
                        "BEGIN INSERT OR REPLACE INTO `sync_outbox` " +
                        "(`entityType`, `entityId`, `operation`, `queuedAtEpochMillis`, `attemptCount`, `lastError`) " +
                        "VALUES ('${trigger.entityType}', CAST(NEW.`${trigger.idColumn}` AS TEXT), 'UPSERT', " +
                        "COALESCE((SELECT `queuedAtEpochMillis` + 1 FROM `sync_outbox` " +
                        "WHERE `entityType` = '${trigger.entityType}' AND " +
                        "`entityId` = CAST(NEW.`${trigger.idColumn}` AS TEXT)), " +
                        "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)), 0, NULL); END",
                )
            }
            database.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `sync_${trigger.table}_delete` " +
                    "AFTER DELETE ON `${trigger.table}` " +
                    "WHEN COALESCE((SELECT `isApplyingRemote` FROM `sync_control` WHERE `id` = 1), 0) = 0 " +
                    "BEGIN INSERT OR REPLACE INTO `sync_outbox` " +
                    "(`entityType`, `entityId`, `operation`, `queuedAtEpochMillis`, `attemptCount`, `lastError`) " +
                    "VALUES ('${trigger.entityType}', CAST(OLD.`${trigger.idColumn}` AS TEXT), 'DELETE', " +
                    "COALESCE((SELECT `queuedAtEpochMillis` + 1 FROM `sync_outbox` " +
                    "WHERE `entityType` = '${trigger.entityType}' AND " +
                    "`entityId` = CAST(OLD.`${trigger.idColumn}` AS TEXT)), " +
                    "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)), 0, NULL); END",
            )
        }
    }
}
