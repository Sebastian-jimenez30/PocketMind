package com.pocketmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "custom_categories")
data class CustomCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
)
