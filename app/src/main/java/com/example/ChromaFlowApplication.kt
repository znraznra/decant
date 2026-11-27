package com.example

import android.app.Application
import com.example.audio.SoundFeedbackEngine
import com.example.data.ProgressRepository
import com.example.data.cloud.PlayGamesSaveManager
import com.example.data.local.AppDatabase
import com.example.data.prefs.UserPreferencesRepository
import com.google.android.gms.games.PlayGamesSdk

class ChromaFlowApplication : Application() {

    val database: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppDatabase.getInstance(this)
    }

    val preferencesRepository: UserPreferencesRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        UserPreferencesRepository(this)
    }

    val playGamesSaveManager: PlayGamesSaveManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PlayGamesSaveManager(this)
    }

    val progressRepository: ProgressRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProgressRepository(
            dao = database.gameDao(),
            preferences = preferencesRepository,
            playGamesSave = playGamesSaveManager
        )
    }

    val soundFeedbackEngine: SoundFeedbackEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SoundFeedbackEngine(this)
    }

    override fun onCreate() {
        super.onCreate()
        initPlayGamesIfConfigured()
    }

    private fun initPlayGamesIfConfigured() {
        runCatching {
            val appId = getString(R.string.game_services_project_id).trim()
            if (isValidPlayGamesAppId(appId)) {
                PlayGamesSdk.initialize(this)
            }
        }
    }

    companion object {
        fun isValidPlayGamesAppId(appId: String?): Boolean {
            if (appId.isNullOrBlank()) return false
            if (appId == "000000000000" || appId.startsWith("YOUR_")) return false
            return appId.all { it.isDigit() }
        }
    }
}
