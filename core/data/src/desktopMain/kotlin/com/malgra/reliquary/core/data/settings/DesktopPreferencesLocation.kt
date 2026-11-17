package com.malgra.reliquary.core.data.settings

import com.malgra.reliquary.core.data.AppDirectories
import java.io.File

/** The same file names as Android's `preferencesDataStoreFile`, under the app data folder. */
class DesktopPreferencesLocation(private val directories: AppDirectories) : PreferencesLocation {
    override fun file(name: String): File = File(directories.datastore, "$name.preferences_pb")
}
