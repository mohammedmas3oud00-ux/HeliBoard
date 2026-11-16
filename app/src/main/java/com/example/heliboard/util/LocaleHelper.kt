package com.example.heliboard.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.unit.LayoutDirection
import com.example.heliboard.data.HeliSettings
import kotlinx.serialization.json.Json
import java.util.Locale

object LocaleHelper {
    const val LANG_SYSTEM = "system"
    const val LANG_ARABIC = "ar"
    const val LANG_ENGLISH = "en"

    private val json = Json { ignoreUnknownKeys = true }

    fun readSavedAppLanguage(context: Context): String {
        val prefs = context.getSharedPreferences("heliboard_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("settings_json", null) ?: return LANG_SYSTEM
        return runCatching {
            json.decodeFromString<HeliSettings>(raw).appLanguage
        }.getOrDefault(LANG_SYSTEM)
    }

    fun resolveLocale(appLanguage: String): Locale {
        return when (appLanguage.lowercase()) {
            LANG_ARABIC -> Locale.forLanguageTag("ar")
            LANG_ENGLISH -> Locale.forLanguageTag("en")
            else -> {
                val sysLocales = Resources.getSystem().configuration.locales
                if (!sysLocales.isEmpty) sysLocales[0] else Locale.getDefault()
            }
        }
    }

    fun resolveLayoutDirection(appLanguage: String): LayoutDirection {
        val locale = resolveLocale(appLanguage)
        return if (locale.language.equals("ar", ignoreCase = true) ||
            locale.language.equals("fa", ignoreCase = true) ||
            locale.language.equals("he", ignoreCase = true) ||
            locale.language.equals("ur", ignoreCase = true)
        ) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
    }

    fun createLocalizedContext(base: Context, appLanguage: String): Pair<Context, Configuration> {
        val locale = resolveLocale(appLanguage)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        val localized = base.createConfigurationContext(config)
        return localized to config
    }

    fun syncSystemApplicationLocales(context: Context, appLanguage: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val lm = context.getSystemService(LocaleManager::class.java)
                val list = when (appLanguage.lowercase()) {
                    LANG_ARABIC -> LocaleList.forLanguageTags("ar")
                    LANG_ENGLISH -> LocaleList.forLanguageTags("en")
                    else -> LocaleList.getEmptyLocaleList()
                }
                lm?.applicationLocales = list
            }
        }
    }
}
