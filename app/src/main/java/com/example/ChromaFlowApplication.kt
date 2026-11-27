package com.example

import android.app.Application
import com.example.audio.SoundManager
import com.example.data.ProgressRepository
import com.example.data.local.AppDatabase
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.transfer.PeerTransferEngine

class ChromaFlowApplication : Application() {

    val database: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppDatabase.getInstance(this)
    }

    val preferencesRepository: UserPreferencesRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        UserPreferencesRepository(this)
    }

    val progressRepository: ProgressRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProgressRepository(
            dao = database.gameDao(),
            preferences = preferencesRepository
        )
    }

    val peerTransferEngine: PeerTransferEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PeerTransferEngine(this)
    }

    val soundManager: SoundManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SoundManager(this)
    }
}
