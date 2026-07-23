package com.pocketmind.data.profile

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

class SupabaseProfileRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : ProfileRepository {
    override suspend fun load(): ProfileSettings {
        val user = supabase.auth.retrieveUserForCurrentSession()
        val profile = supabase.postgrest.from(PROFILES_TABLE)
            .select { filter { eq("id", user.id) } }
            .decodeList<ProfileDto>()
            .firstOrNull()
            ?: ProfileDto(id = user.id).also { supabase.postgrest.from(PROFILES_TABLE).insert(it) }
        val preferences = supabase.postgrest.from(PREFERENCES_TABLE)
            .select { filter { eq("user_id", user.id) } }
            .decodeList<UserPreferencesDto>()
            .firstOrNull()
            ?: UserPreferencesDto(userId = user.id).also { supabase.postgrest.from(PREFERENCES_TABLE).insert(it) }

        return ProfileSettings(
            email = user.email.orEmpty(),
            displayName = profile.displayName.orEmpty(),
            currencyCode = preferences.currencyCode,
            weekStartsOn = preferences.weekStartsOn,
            monthlySummaryNotificationsEnabled = preferences.monthlySummaryNotificationsEnabled,
        )
    }

    override suspend fun save(settings: ProfileSettings) {
        val user = supabase.auth.retrieveUserForCurrentSession()
        supabase.postgrest.from(PROFILES_TABLE).upsert(
            ProfileDto(id = user.id, displayName = settings.displayName.trim().ifBlank { null }),
        )
        supabase.postgrest.from(PREFERENCES_TABLE).upsert(
            UserPreferencesDto(
                userId = user.id,
                currencyCode = settings.currencyCode,
                weekStartsOn = settings.weekStartsOn,
                monthlySummaryNotificationsEnabled = settings.monthlySummaryNotificationsEnabled,
            ),
        )
    }

    override suspend fun changeEmail(email: String) {
        supabase.auth.updateUser { this.email = email.trim() }
    }

    override suspend fun changePassword(password: String) {
        supabase.auth.updateUser { this.password = password }
    }

    override suspend fun signOut() {
        supabase.auth.signOut()
    }

    private companion object {
        const val PROFILES_TABLE = "profiles"
        const val PREFERENCES_TABLE = "user_preferences"
    }
}

@Serializable
private data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
private data class UserPreferencesDto(
    @SerialName("user_id") val userId: String,
    @SerialName("currency_code") val currencyCode: String = "COP",
    @SerialName("week_starts_on") val weekStartsOn: Int = 1,
    @SerialName("monthly_summary_notifications_enabled")
    val monthlySummaryNotificationsEnabled: Boolean = false,
)
