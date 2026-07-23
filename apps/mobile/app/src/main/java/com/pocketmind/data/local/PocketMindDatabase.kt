package com.pocketmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pocketmind.data.local.dao.AccountDao
import com.pocketmind.data.local.dao.TransactionDao
import com.pocketmind.data.local.entity.AccountEntity
import com.pocketmind.data.local.entity.TransactionEntity

@Database(entities = [AccountEntity::class, TransactionEntity::class], version = 1, exportSchema = true)
abstract class PocketMindDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
}
