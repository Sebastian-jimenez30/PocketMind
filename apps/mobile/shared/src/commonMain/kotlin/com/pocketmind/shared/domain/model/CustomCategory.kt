package com.pocketmind.shared.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomCategory(
    val id: String = "",
    val name: String,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long = 0L,
)


object RecommendedCategories {
    val COMMON_SUGGESTIONS = listOf(
        "Educación",
        "Ropa y calzado",
        "Deportes",
        "Mascotas",
        "Regalos",
        "Viajes",
        "Hogar y mantenimiento",
        "Suscripciones",
        "Belleza y cuidado personal",
        "Tecnología",
    )
}

object DefaultExpenseCategories {
    val CORE = listOf(
        TransactionCategoryId.FOOD,
        TransactionCategoryId.TRANSPORT,
        TransactionCategoryId.SERVICES,
        TransactionCategoryId.HEALTH,
        TransactionCategoryId.ENTERTAINMENT,
    )
}
