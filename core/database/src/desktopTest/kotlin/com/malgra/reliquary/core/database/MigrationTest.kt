package com.malgra.reliquary.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path

class MigrationTest {

    @get:Rule val folder = TemporaryFolder()

    // Gradle runs tests from the module folder, where the committed schemas live.
    private val helper by lazy {
        MigrationTestHelper(
            schemaDirectoryPath = Path.of("schemas"),
            databasePath = folder.root.toPath().resolve("migration.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = ReliquaryDatabase::class,
        )
    }

    /** Runs every AutoMigration from the first exported schema and checks the result against the latest. */
    @Test
    fun migratesFromFirstVersionToLatest() {
        helper.createDatabase(1).close()
        helper.runMigrationsAndValidate(LATEST).close()
    }

    @Test
    fun opensFreshDatabase() = runTest {
        val db = inMemoryReliquaryDatabase()
        db.keepOfflineRuleDao().all()
        db.close()
    }

    private companion object {
        const val LATEST = 7
    }
}
