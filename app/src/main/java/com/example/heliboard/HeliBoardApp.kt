package com.example.heliboard

import android.app.Application
import com.example.heliboard.data.AssetRepository
import com.example.heliboard.data.PreferencesRepository
import com.example.heliboard.data.SuggestionEngine
import com.example.heliboard.data.room.ClipboardRoomRepository
import com.example.heliboard.data.room.HeliBoardDatabase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

class HeliBoardApp : Application() {

    lateinit var clipboardRoomRepository: ClipboardRoomRepository
        private set

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var assetRepository: AssetRepository
        private set

    lateinit var suggestionEngine: SuggestionEngine
        private set

    private val _voicePermissionEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val voicePermissionEvents: SharedFlow<Boolean> = _voicePermissionEvents.asSharedFlow()

    private val pendingVoiceAutoStart = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        val database = HeliBoardDatabase.getInstance(this)
        clipboardRoomRepository = ClipboardRoomRepository(database)
        preferencesRepository = PreferencesRepository(this, clipboardRoomRepository)
        assetRepository = AssetRepository(this)
        suggestionEngine = SuggestionEngine()
    }

    fun notifyVoicePermissionResult(granted: Boolean, fromImeRequest: Boolean) {
        if (granted && fromImeRequest) {
            pendingVoiceAutoStart.set(true)
        }
        _voicePermissionEvents.tryEmit(granted)
    }

    fun consumePendingVoiceAutoStart(): Boolean = pendingVoiceAutoStart.getAndSet(false)
}

