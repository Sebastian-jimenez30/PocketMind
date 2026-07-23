package com.pocketmind.di

import android.content.Context
import androidx.room.Room
import com.pocketmind.data.local.PocketMindDatabase
import com.pocketmind.data.local.dao.AccountDao
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
        Room.databaseBuilder(context, PocketMindDatabase::class.java, "pocketmind.db").build()
    @Provides fun provideAccountDao(database: PocketMindDatabase): AccountDao = database.accountDao()
    @Provides fun provideTransactionDao(database: PocketMindDatabase): TransactionDao = database.transactionDao()
}
