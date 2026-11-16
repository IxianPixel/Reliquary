package com.malgra.reliquary.core.data.settings

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** DataStore's usual place in the app's files folder. */
@Singleton
class AndroidPreferencesLocation @Inject constructor(@ApplicationContext private val context: Context) : PreferencesLocation {
    override fun file(name: String): File = context.preferencesDataStoreFile(name)
}
