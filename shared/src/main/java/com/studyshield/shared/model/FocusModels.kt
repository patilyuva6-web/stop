package com.studyshield.shared.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class WorkspaceRole(val wireValue: String) {
    STUDENT("student"),
    TEACHER("teacher");

    companion object {
        fun fromWire(raw: String?): WorkspaceRole =
            entries.firstOrNull { it.wireValue == raw } ?: STUDENT
    }
}

enum class BreakStatus(val wireValue: String) {
    PENDING("PENDING"),
    APPROVED("APPROVED"),
    DENIED("DENIED");

    companion object {
        fun fromWire(raw: String?): BreakStatus =
            entries.firstOrNull { it.wireValue == raw } ?: PENDING
    }
}

data class StudentSession(
    val usn: String,
    val name: String,
    val division: String
)

data class TeacherSession(
    val teacherId: String,
    val name: String
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "student",
    val displayName: String = "",
    val usn: String = "",
    val teacherId: String = "",
    val division: String = "A",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudentRecord(
    val usn: String = "",
    val ownerUid: String = "",
    val name: String = "",
    val division: String = "A",
    val inClassMode: Boolean = false,
    val activeClassId: String = "",
    val activeSubject: String = "",
    val breakUntilEpochMs: Long = 0L,
    val lastLoginEpochMs: Long = 0L,
    val watcherUids: List<String> = emptyList(),
    @ServerTimestamp val lastLogin: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ClassSchedule(
    @DocumentId val id: String = "",
    val teacherUid: String = "",
    val teacherId: String = "T001",
    val teacherName: String = "",
    val subject: String = "",
    val division: String = "A",
    val day: String = "MON",
    val start: String = "09:00",
    val end: String = "10:00",
    val room: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ClassActivity(
    @DocumentId val id: String = "",
    val dateKey: String = "",
    val classId: String = "",
    val usn: String = "",
    val studentName: String = "",
    val division: String = "A",
    val subject: String = "",
    val ownerUid: String = "",
    val teacherUid: String = "",
    val teacherId: String = "",
    val active: Boolean = false,
    val breakActive: Boolean = false,
    @ServerTimestamp val startedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BreakRequest(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val usn: String = "",
    val studentName: String = "",
    val division: String = "A",
    val classId: String = "",
    val subject: String = "",
    val teacherUid: String = "",
    val teacherId: String = "",
    val reason: String = "",
    val status: String = "PENDING",
    val breakCode: String = "",
    val durationMinutes: Long = 10L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

object FocusScheduleUtils {
    val DIVISIONS = listOf("A", "B", "C", "D", "E", "F")
    val DAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    fun sanitizeId(raw: String): String =
        raw.trim().uppercase(Locale.US).replace(Regex("[^A-Z0-9_\\-]"), "_")

    fun normalizeDay(raw: String): String {
        val up = raw.trim().uppercase(Locale.US)
        return when {
            up.startsWith("MON") -> "MON"
            up.startsWith("TUE") -> "TUE"
            up.startsWith("WED") -> "WED"
            up.startsWith("THU") -> "THU"
            up.startsWith("FRI") -> "FRI"
            up.startsWith("SAT") -> "SAT"
            up.startsWith("SUN") -> "SUN"
            else -> up.take(3)
        }
    }

    fun normalizeTime(raw: String): String {
        val parts = raw.trim().split(":")
        if (parts.size != 2) return raw.trim()
        val h = parts[0].toIntOrNull() ?: return raw.trim()
        val m = parts[1].toIntOrNull() ?: return raw.trim()
        return String.format(Locale.US, "%02d:%02d", h.coerceIn(0, 23), m.coerceIn(0, 59))
    }

    fun todayDateKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun currentDayCode(): String {
        val cal = Calendar.getInstance()
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "MON"
            Calendar.TUESDAY -> "TUE"
            Calendar.WEDNESDAY -> "WED"
            Calendar.THURSDAY -> "THU"
            Calendar.FRIDAY -> "FRI"
            Calendar.SATURDAY -> "SAT"
            else -> "SUN"
        }
    }

    fun currentTimeHHmm(): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date())

    fun parseMinutes(hhmm: String): Int? {
        val parts = hhmm.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun formatMinutesAsHHmm(totalMinutes: Int): String {
        val clamped = totalMinutes.coerceIn(0, 23 * 60 + 59)
        val h = clamped / 60
        val m = clamped % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    fun isClassActiveNow(
        schedule: ClassSchedule,
        day: String = currentDayCode(),
        timeHHmm: String = currentTimeHHmm()
    ): Boolean {
        if (!schedule.active) return false
        if (normalizeDay(schedule.day) != normalizeDay(day)) return false
        val nowMins = parseMinutes(timeHHmm) ?: return false
        val startMins = parseMinutes(normalizeTime(schedule.start)) ?: return false
        val endMins = parseMinutes(normalizeTime(schedule.end)) ?: return false
        return nowMins in startMins until endMins
    }

    fun remainingSecondsForClass(schedule: ClassSchedule): Long {
        val endMins = parseMinutes(normalizeTime(schedule.end)) ?: return 0L
        val cal = Calendar.getInstance()
        val nowSeconds = cal.get(Calendar.HOUR_OF_DAY) * 3600L +
            cal.get(Calendar.MINUTE) * 60L +
            cal.get(Calendar.SECOND)
        val endSeconds = endMins * 60L
        return (endSeconds - nowSeconds).coerceAtLeast(0L)
    }

    fun formatCountdown(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val hours = safe / 3600
        val mins = (safe % 3600) / 60
        val secs = safe % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun findScheduleClash(
        candidate: ClassSchedule,
        existingClasses: List<ClassSchedule>,
        ignoreClassId: String = ""
    ): ClassSchedule? {
        val cStart = parseMinutes(normalizeTime(candidate.start)) ?: return null
        val cEnd = parseMinutes(normalizeTime(candidate.end)) ?: return null
        val cDay = normalizeDay(candidate.day)
        return existingClasses.firstOrNull { other ->
            if (other.id == ignoreClassId || !other.active) return@firstOrNull false
            if (normalizeDay(other.day) != cDay) return@firstOrNull false
            val sameTeacher = (candidate.teacherId.isNotBlank() &&
                other.teacherId.equals(candidate.teacherId, ignoreCase = true)) ||
                (candidate.teacherUid.isNotBlank() && other.teacherUid == candidate.teacherUid)
            val sameDivision = other.division.equals(candidate.division, ignoreCase = true)
            if (!sameTeacher && !sameDivision) return@firstOrNull false
            val oStart = parseMinutes(normalizeTime(other.start)) ?: return@firstOrNull false
            val oEnd = parseMinutes(normalizeTime(other.end)) ?: return@firstOrNull false
            cStart < oEnd && cEnd > oStart
        }
    }

    fun computeDailyBreakCode(usn: String, dateKey: String = todayDateKey()): String {
        val seed = "${usn.trim().uppercase(Locale.US)}:$dateKey:FOCUSGUARD_V1"
        var hash = 7
        for (ch in seed) {
            hash = hash * 31 + ch.code
        }
        val code = (abs(hash) % 9000) + 1000
        return code.toString()
    }
}
