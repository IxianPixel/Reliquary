package com.malgra.reliquary.core.data.di

import javax.inject.Qualifier

/** A scope that lives as long as the app, for work that must outlive a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** DataStore holding the encrypted session and the last server and username. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SessionStore

/** DataStore holding the user's app settings. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsStore
