package com.malgra.reliquary.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.malgra.reliquary.core.data.di.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The user's settings. Kept on sign-out: they belong to the device, not the account. */
@Singleton
class AppSettings @Inject constructor(@SettingsStore private val store: DataStore<Preferences>) {

    /** Downloads wait for Wi-Fi (an unmetered network) unless this is off. On by default. */
    val wifiOnlyDownloads: Flow<Boolean> = store.data.map { it[WIFI_ONLY_DOWNLOADS] ?: true }

    suspend fun wifiOnlyDownloads(): Boolean = wifiOnlyDownloads.first()

    suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        store.edit { it[WIFI_ONLY_DOWNLOADS] = enabled }
    }

    /** The subtitle language last chosen in the player; null means subtitles off (the default). */
    val subtitleLanguage: Flow<String?> = store.data.map { it[SUBTITLE_LANGUAGE]?.takeIf(String::isNotEmpty) }

    suspend fun setSubtitleLanguage(language: String?) {
        store.edit { prefs -> if (language == null) prefs.remove(SUBTITLE_LANGUAGE) else prefs[SUBTITLE_LANGUAGE] = language }
    }

    private companion object {
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        val SUBTITLE_LANGUAGE = stringPreferencesKey("subtitle_language")
    }
}
