package com.resurgeai.core.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ReadRecordsResponse
import com.resurgeai.core.model.CardioPointsEngine
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CardioHealthDataSourceTest {
    private val manager = mockk<HealthConnectManager>()
    private val client = mockk<HealthConnectClient>()
    private val labels = mockk<HealthAppLabels>()
    private val reader = CardioHealthDataSource(manager, labels)
    private val start = Instant.parse("2026-10-20T00:00:00Z")
    private val watch = Device(type = Device.TYPE_WATCH, model = "Test Watch")
    private val requests = mutableListOf<ReadRecordsRequest<*>>()

    init {
        every { manager.client } returns client
        every { labels.label(any()) } answers { SourceResolver.knownAppName(firstArg()) }
        coEvery { manager.grantedPermissions() } returns setOf(HealthPermission.getReadPermission(ExerciseSessionRecord::class))
    }

    /** Emulate Health Connect's start-time filtering instead of returning out-of-range fixtures. */
    private fun records(vararg records: Record) {
        coEvery { client.readRecords(any<ReadRecordsRequest<Record>>()) } answers {
            val request = firstArg<ReadRecordsRequest<Record>>()
            requests += request
            val from = requireNotNull(request.timeRangeFilter.startTime) { "Unbounded read" }
            val to = requireNotNull(request.timeRangeFilter.endTime) { "Unbounded read" }
            val filtered = records.filter { record ->
                val at = when (record) {
                    is StepsCadenceRecord -> record.startTime
                    is StepsRecord -> record.startTime
                    is ExerciseSessionRecord -> record.startTime
                    else -> error("Unexpected test record")
                }
                request.recordType.java.isInstance(record) && at >= from && at < to
            }
            ReadRecordsResponse(filtered, null)
        }
    }

    @Test fun `manual sensor records and long step records are excluded`() = runBlocking {
        records(
            StepsRecord(start, null, start.plusSeconds(60), null, 110, Metadata.autoRecorded(watch)),
            StepsRecord(start, null, start.plusSeconds(60), null, 140, Metadata.manualEntry()),
            StepsRecord(start, null, start.plusSeconds(120), null, 400, Metadata.autoRecorded(watch)),
        )
        val evidence = reader.evidence(start, start.plusSeconds(300))
        assertEquals(1, evidence.size)
        assertEquals(1, evidence.single().intensity)
        assertEquals("Test Watch", evidence.single().source.deviceName)
    }

    @Test fun `bounded cadence reads preserve series crossing midnight but exclude older history`() = runBlocking {
        val to = start.plusSeconds(300)
        val zone = ZoneId.systemDefault()
        val historyStart = to.atZone(zone).toLocalDate().minusDays(CardioPointsEngine.HISTORY_DAYS - 1)
            .atStartOfDay(zone).toInstant()
        records(
            StepsCadenceRecord(start.minusSeconds(3600), null, start.plusSeconds(3600), null,
                listOf(StepsCadenceRecord.Sample(start.plusSeconds(10), 130.0)), Metadata.autoRecorded(watch)),
            StepsCadenceRecord(historyStart.minusSeconds(3600), null, start.plusSeconds(3600), null,
                listOf(StepsCadenceRecord.Sample(start.plusSeconds(10), 110.0)), Metadata.autoRecorded(watch)),
        )
        val evidence = reader.evidence(start, to)
        assertEquals(start.plusSeconds(10), evidence.single().start)
        assertEquals(start.plusSeconds(70), evidence.single().end)
        assertEquals(2, evidence.single().intensity)
        val cadenceRequest = requests.single { it.recordType == StepsCadenceRecord::class }
        assertEquals(historyStart, cadenceRequest.timeRangeFilter.startTime)
        assertEquals(to, cadenceRequest.timeRangeFilter.endTime)
        val stepRequest = requests.single { it.recordType == StepsRecord::class }
        assertEquals(start.minusSeconds(60), stepRequest.timeRangeFilter.startTime)
        assertEquals(to, stepRequest.timeRangeFilter.endTime)
    }

    @Test fun `workout reads include only sessions started in the requested period`() = runBlocking {
        val to = start.plusSeconds(300)
        fun workout(at: Instant) = ExerciseSessionRecord(at, null, at.plusSeconds(2400), null,
            Metadata.autoRecorded(watch), ExerciseSessionRecord.EXERCISE_TYPE_RUNNING)
        records(workout(start.minusSeconds(1200)), workout(start), workout(to))
        val workouts = reader.workouts(start, to)
        assertEquals(start, workouts.single().start)
        assertEquals("running", workouts.single().typeKey)
        assertEquals(start, requests.single().timeRangeFilter.startTime)
        assertEquals(to, requests.single().timeRangeFilter.endTime)
    }

    @Test fun `workout sources retain resolved app names without device metadata`() = runBlocking {
        val pkg = "com.example.trailjournal"
        val origin = mockk<DataOrigin> { every { packageName } returns pkg }
        val metadata = mockk<Metadata> {
            every { id } returns "workout-id"
            every { recordingMethod } returns Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED
            every { dataOrigin } returns origin
            every { device } returns null
        }
        every { labels.label(pkg) } returns "Trail Journal"
        records(ExerciseSessionRecord(start, null, start.plusSeconds(300), null, metadata, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING))
        assertEquals("Trail Journal", reader.workouts(start, start.plusSeconds(300)).single().source?.appName)
        verify { labels.label(pkg) }
    }

    @Test fun `empty periods and denied exercise permission make no provider reads`() = runBlocking {
        assertTrue(reader.evidence(start, start).isEmpty())
        assertTrue(reader.workouts(start, start).isEmpty())
        coEvery { manager.grantedPermissions() } returns emptySet()
        assertTrue(reader.workouts(start, start.plusSeconds(300)).isEmpty())
        coVerify(exactly = 0) { client.readRecords(any<ReadRecordsRequest<Record>>()) }
    }
}
