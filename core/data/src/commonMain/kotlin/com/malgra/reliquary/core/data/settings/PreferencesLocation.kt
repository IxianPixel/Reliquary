package com.malgra.reliquary.core.data.settings

import java.io.File

/** Where the platform keeps the app's preference files. */
interface PreferencesLocation {
    /** The file backing the DataStore called [name]. */
    fun file(name: String): File

    companion object {
        /** The encrypted session and the last server and username. */
        const val SESSION = "session"

        /** The user's app settings. */
        const val SETTINGS = "settings"
    }
}
