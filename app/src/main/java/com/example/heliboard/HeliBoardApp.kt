package com.example.heliboard

import android.app.Application
import com.example.heliboard.data.AssetRepository
import com.example.heliboard.data.PreferencesRepository
import com.example.heliboard.data.SuggestionEngine

class HeliBoardApp : Application() {

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var assetRepository: AssetRepository
        private set

    lateinit var suggestionEngine: SuggestionEngine
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = PreferencesRepository(this)
        assetRepository = AssetRepository(this)
        suggestionEngine = SuggestionEngine()
    }
}
