package com.resurgeai.core.data.cardio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import com.resurgeai.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CardioPointsCacheTest {
    private val today = LocalDate.of(2026, 10, 20)
    private val first = today.minusDays(29)
    private fun entry(date: LocalDate = today) = CachedCardioDay(DailyCardioPoints(date, calculatedAt = Instant.EPOCH), "fingerprint")
    private fun cache() = CardioPointsCache(MemoryStore())

    @Test fun `cached summaries survive a reload and prune outside rolling history`() = runBlocking {
        val cache = cache()
        cache.save("a", "UTC", "granted", listOf(entry(today.minusDays(30)), entry(first), entry()), today)
        val loaded = cache.load("a", "UTC", "granted", first, today)
        assertEquals(listOf(first, today), loaded.days.map { it.summary.date })
        assertEquals(today, loaded.fullRefreshDate)
    }
    @Test fun `account timezone permission and version changes invalidate cache`() = runBlocking {
        for ((uid, zone, access) in listOf(Triple("b", "UTC", "granted"), Triple("a", "Asia/Kolkata", "granted"), Triple("a", "UTC", "revoked"))) {
            val cache = cache()
            cache.save("a", "UTC", "granted", listOf(entry()), today)
            assertTrue(cache.load(uid, zone, access, first, today).days.isEmpty())
            assertTrue(cache.load("a", "UTC", "granted", first, today).days.isEmpty())
        }
        val store = MemoryStore()
        val cache = CardioPointsCache(store)
        cache.save("a", "UTC", "granted", listOf(entry()), today)
        store.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("version")] = "0" }
        assertTrue(cache.load("a", "UTC", "granted", first, today).days.isEmpty())
    }
    @Test fun `signout clears only the previous user's data`() = runBlocking {
        val cache = cache()
        cache.save("new", "UTC", "granted", listOf(entry()), today)
        cache.clearForUser("old")
        assertEquals(1, cache.load("new", "UTC", "granted", first, today).days.size)
        cache.clearForUser("new")
        assertTrue(cache.load("new", "UTC", "granted", first, today).days.isEmpty())
    }

    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val lock = Mutex()
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = lock.withLock {
            transform(state.value).also { state.value = it }
        }
    }
}
