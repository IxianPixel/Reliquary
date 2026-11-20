package com.malgra.reliquary.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.malgra.reliquary.core.data.di.SettingsStore
import com.malgra.reliquary.core.model.ThemeMode
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

    /** The player skips SponsorBlock segments (sponsors, intros and so on) unless this is off. On by default. */
    val skipSponsorSegments: Flow<Boolean> = store.data.map { it[SKIP_SPONSOR_SEGMENTS] ?: true }

    suspend fun setSkipSponsorSegments(enabled: Boolean) {
        store.edit { it[SKIP_SPONSOR_SEGMENTS] = enabled }
    }

    /** The subtitle language last chosen in the player; null means subtitles off (the default). */
    val subtitleLanguage: Flow<String?> = store.data.map { it[SUBTITLE_LANGUAGE]?.takeIf(String::isNotEmpty) }

    suspend fun setSubtitleLanguage(language: String?) {
        store.edit { prefs -> if (language == null) prefs.remove(SUBTITLE_LANGUAGE) else prefs[SUBTITLE_LANGUAGE] = language }
    }

    /** Light, dark, or whatever the system uses (the default). */
    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[THEME_MODE] } ?: ThemeMode.System
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    /** The wallpaper's colours instead of Catppuccin, where the platform has them. Off by default. */
    val dynamicColor: Flow<Boolean> = store.data.map { it[DYNAMIC_COLOR] ?: false }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR] = enabled }
    }

    /** Most space downloads may take, in bytes; null (the default) means no limit beyond the disk. */
    val storageLimitBytes: Flow<Long?> = store.data.map { it[STORAGE_LIMIT_BYTES]?.takeIf { bytes -> bytes > 0 } }

    suspend fun storageLimitBytes(): Long? = storageLimitBytes.first()

    suspend fun setStorageLimitBytes(bytes: Long?) {
        store.edit { prefs -> if (bytes == null) prefs.remove(STORAGE_LIMIT_BYTES) else prefs[STORAGE_LIMIT_BYTES] = bytes }
    }

    private companion object {
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        val SUBTITLE_LANGUAGE = stringPreferencesKey("subtitle_language")
        val SKIP_SPONSOR_SEGMENTS = booleanPreferencesKey("skip_sponsor_segments")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val STORAGE_LIMIT_BYTES = longPreferencesKey("storage_limit_bytes")
    }
}
