package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.StudioThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chromaflow_prefs")

data class UserPreferences(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val colorblindEnabled: Boolean = false,
    val randomizeThemePerLevel: Boolean = true,
    val pinnedTheme: StudioThemeId = StudioThemeId.CLEAN_GLASS,
    val playGamesSyncEnabled: Boolean = true,
    val highestUnlockedLevel: Int = 1
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val COLORBLIND_ENABLED = booleanPreferencesKey("colorblind_enabled")
        val RANDOMIZE_THEME = booleanPreferencesKey("randomize_theme_per_level")
        val PINNED_THEME_ORDINAL = intPreferencesKey("pinned_theme_ordinal")
        val PLAY_GAMES_SYNC_ENABLED = booleanPreferencesKey("play_games_sync_enabled")
        val HIGHEST_UNLOCKED_LEVEL = intPreferencesKey("highest_unlocked_level")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val themeOrdinal = (prefs[Keys.PINNED_THEME_ORDINAL] ?: 0)
            .coerceIn(0, StudioThemeId.entries.lastIndex)
        UserPreferences(
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            colorblindEnabled = prefs[Keys.COLORBLIND_ENABLED] ?: false,
            randomizeThemePerLevel = prefs[Keys.RANDOMIZE_THEME] ?: true,
            pinnedTheme = StudioThemeId.entries[themeOrdinal],
            playGamesSyncEnabled = prefs[Keys.PLAY_GAMES_SYNC_ENABLED] ?: true,
            highestUnlockedLevel = (prefs[Keys.HIGHEST_UNLOCKED_LEVEL] ?: 1).coerceAtLeast(1)
        )
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setColorblindEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.COLORBLIND_ENABLED] = enabled }
    }

    suspend fun setRandomizeThemePerLevel(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RANDOMIZE_THEME] = enabled }
    }

    suspend fun setPinnedTheme(theme: StudioThemeId) {
        context.dataStore.edit { it[Keys.PINNED_THEME_ORDINAL] = theme.ordinal }
    }

    suspend fun setPlayGamesSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PLAY_GAMES_SYNC_ENABLED] = enabled }
    }

    suspend fun setHighestUnlockedLevel(level: Int) {
        context.dataStore.edit {
            val current = it[Keys.HIGHEST_UNLOCKED_LEVEL] ?: 1
            if (level > current) {
                it[Keys.HIGHEST_UNLOCKED_LEVEL] = level
            }
        }
    }
}
