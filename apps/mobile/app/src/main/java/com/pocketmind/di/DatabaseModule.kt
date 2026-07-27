package com.pocketmind.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pocketmind.data.local.PocketMindDatabase
import com.pocketmind.data.local.dao.AccountDao
import com.pocketmind.data.local.dao.FinancialSetupDao
import com.pocketmind.data.local.dao.ManualFinanceDao
import com.pocketmind.data.local.dao.SyncDao
import com.pocketmind.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PocketMindDatabase =
        Room.databaseBuilder(context, PocketMindDatabase::class.java, "pocketmind.db")
            .addMigrations(PocketMindDatabase.MIGRATION_1_2)
            .addMigrations(PocketMindDatabase.MIGRATION_2_3)
            .addMigrations(PocketMindDatabase.MIGRATION_3_4)
            .addMigrations(PocketMindDatabase.MIGRATION_4_5)
            .addMigrations(PocketMindDatabase.MIGRATION_5_6)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        PocketMindDatabase.ensureSyncInfrastructure(db)
                    }
                },
            )
            .build()
    @Provides fun provideAccountDao(database: PocketMindDatabase): AccountDao = database.accountDao()
    @Provides fun provideTransactionDao(database: PocketMindDatabase): TransactionDao = database.transactionDao()
    @Provides fun provideFinancialSetupDao(database: PocketMindDatabase): FinancialSetupDao = database.financialSetupDao()
    @Provides fun provideManualFinanceDao(database: PocketMindDatabase): ManualFinanceDao = database.manualFinanceDao()
    @Provides fun provideSyncDao(database: PocketMindDatabase): SyncDao = database.syncDao()
}
