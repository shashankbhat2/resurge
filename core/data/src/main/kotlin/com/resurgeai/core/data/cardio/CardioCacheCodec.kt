package com.resurgeai.core.data.cardio

import com.resurgeai.core.model.CardioContribution
import com.resurgeai.core.model.CardioMethod
import com.resurgeai.core.model.CardioPointsEngine
import com.resurgeai.core.model.CardioStatus
import com.resurgeai.core.model.DailyCardioPoints
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.Base64

data class CachedCardioDay(val summary: DailyCardioPoints, val workoutFingerprint: String)
data class CardioCacheSnapshot(val days: List<CachedCardioDay>, val fullRefreshDate: LocalDate?)

/** Versioned aggregate serialization; contains no timestamps or values of raw sensor samples. */
internal object CardioCacheCodec {
    fun encode(day: CachedCardioDay): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            val s = day.summary
            out.writeInt(s.version); out.writeUTF(s.date.toString()); out.writeLong(s.calculatedAt.toEpochMilli())
            out.writeUTF(s.status.name); out.writeLong(s.unclassifiedMs); out.writeUTF(day.workoutFingerprint)
            out.writeInt(s.contributions.size)
            s.contributions.forEach { c ->
                out.writeUTF(c.method.name); out.writeUTF(c.source)
                out.writeBoolean(c.workoutId != null); c.workoutId?.let(out::writeUTF)
                out.writeBoolean(c.workoutTitle != null); c.workoutTitle?.let(out::writeUTF)
                out.writeLong(c.durationMs); out.writeLong(c.weightedMs)
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    fun decode(value: String): CachedCardioDay? = runCatching {
        DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(value))).use { input ->
            val version = input.readInt()
            require(version == CardioPointsEngine.VERSION)
            val date = LocalDate.parse(input.readUTF()); val at = Instant.ofEpochMilli(input.readLong())
            val status = CardioStatus.valueOf(input.readUTF()); val unknown = input.readLong()
            require(unknown >= 0)
            val fingerprint = input.readUTF(); val count = input.readInt()
            require(count in 0..100_000)
            val contributions = List(count) {
                val method = CardioMethod.valueOf(input.readUTF()); val source = input.readUTF()
                val id = if (input.readBoolean()) input.readUTF() else null
                val title = if (input.readBoolean()) input.readUTF() else null
                val duration = input.readLong(); val weighted = input.readLong()
                require(duration >= 0 && weighted >= 0 && weighted <= duration * 2)
                CardioContribution(method, source, id, title, duration, weighted)
            }
            require(input.available() == 0)
            CachedCardioDay(DailyCardioPoints(date, contributions, unknown, status, version, at), fingerprint)
        }
    }.getOrNull()
}
