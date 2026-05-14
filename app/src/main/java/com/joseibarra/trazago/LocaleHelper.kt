package com.joseibarra.trazago

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "TrazaGoPrefs"
    private const val KEY_LANGUAGE = "app_language"

    val supportedLocales = listOf(
        Locale("es") to "Español",
        Locale("en") to "English"
    )

    fun applyLocale(context: Context): Context {
        val locale = getSavedLocale(context)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun setLocale(context: Context, languageCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    fun getCurrentLanguageCode(context: Context): String {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        if (saved != null) return saved
        return Locale.getDefault().language.takeIf { it in listOf("es", "en") } ?: "es"
    }

    fun getCurrentLanguageName(context: Context): String {
        val code = getCurrentLanguageCode(context)
        return supportedLocales.firstOrNull { it.first.language == code }?.second ?: "Español"
    }

    private fun getSavedLocale(context: Context): Locale {
        return Locale(getCurrentLanguageCode(context))
    }
}
