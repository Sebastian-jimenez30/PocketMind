package com.pocketmind.data.profile

data class ProfileSettings(
    val email: String,
    val displayName: String,
    val currencyCode: String,
    val weekStartsOn: Int,
    val monthlySummaryNotificationsEnabled: Boolean,
)

interface ProfileRepository {
    suspend fun load(): ProfileSettings
    suspend fun save(settings: ProfileSettings)
    suspend fun changeEmail(email: String)
    suspend fun changePassword(password: String)
    suspend fun signOut()
}
