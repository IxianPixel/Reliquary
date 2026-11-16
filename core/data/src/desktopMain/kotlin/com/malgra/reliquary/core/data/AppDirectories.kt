package com.malgra.reliquary.core.data

import java.io.File

/**
 * The desktop app's data folder, in each OS's usual place: Application
 * Support on macOS, `%LOCALAPPDATA%` on Windows (local, not roaming, because
 * downloads are large) and `$XDG_DATA_HOME` on Linux.
 */
class AppDirectories(val root: File = defaultRoot()) {

    /** The Room database file; same name as on Android. */
    val database: File get() = File(root, "reliquary.db")

    val downloads: File get() = File(root, "videos")

    val datastore: File get() = File(root, "datastore")

    companion object {
        fun defaultRoot(
            os: String = System.getProperty("os.name").orEmpty(),
            home: String = System.getProperty("user.home"),
            env: (String) -> String? = System::getenv,
        ): File {
            val os = os.lowercase()
            return when {
                os.startsWith("mac") -> File(home, "Library/Application Support/Reliquary")
                os.startsWith("windows") ->
                    File(env("LOCALAPPDATA")?.takeIf(String::isNotBlank) ?: "$home\\AppData\\Local", "Reliquary")
                else -> File(env("XDG_DATA_HOME")?.takeIf(String::isNotBlank) ?: "$home/.local/share", "reliquary")
            }
        }
    }
}
