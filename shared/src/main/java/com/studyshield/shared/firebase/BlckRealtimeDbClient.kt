package com.studyshield.shared.firebase

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.studyshield.shared.model.BreakRequest
import com.studyshield.shared.model.BreakStatus
import com.studyshield.shared.model.ClassActivity
import com.studyshield.shared.model.ClassSchedule
import com.studyshield.shared.model.FocusScheduleUtils
import com.studyshield.shared.model.StudentRecord
import com.studyshield.shared.model.StudentSession
import com.studyshield.shared.model.TeacherSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class RtdbPermissionException(message: String) : IllegalStateException(message)

/**
 * Connects directly to Firebase project blck-43179 Realtime Database
 * (https://blck-43179-default-rtdb.firebaseio.com) where students/<USN>,
 * teachers/<id>, classes/<id>, and activity/<yyyyMMdd>/<classId>/<USN> are stored.
 */
class BlckRealtimeDbClient(
    private val baseUrl: String = DEFAULT_RTDB_URL
) {
    private suspend fun getAuthToken(forceRefresh: Boolean = false): String? {
        return try {
            Firebase.auth.currentUser?.getIdToken(forceRefresh)?.await()?.token
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun request(
        method: String,
        path: String,
        bodyJson: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val cleanPath = path.trimStart('/')
        val user = Firebase.auth.currentUser
        val token = getAuthToken(false)
        var res = executeHttp(method, cleanPath, bodyJson, token)

        if (res.first == 401 && user != null) {
            val freshToken = getAuthToken(true)
            if (!freshToken.isNullOrBlank() && freshToken != token) {
                res = executeHttp(method, cleanPath, bodyJson, freshToken)
            }
            if (res.first == 401) {
                val openFallback = executeHttp(method, cleanPath, bodyJson, null)
                if (openFallback.first in 200..299) {
                    return@withContext openFallback.second
                }
            }
        }

        if (res.first in 200..299) {
            return@withContext res.second
        }

        if (res.first == 401 || res.first == 403) {
            val hint = if (Firebase.auth.currentUser == null) {
                "Sign in with Google first to authorize access to Firebase (blck-43179)."
            } else {
                "Firebase blck-43179 Realtime Database denied permission (HTTP ${res.first}). Check Realtime Database Rules in Firebase Console (allow '.read': 'auth != null', '.write': 'auth != null')."
            }
            throw RtdbPermissionException(hint)
        }

        throw IllegalStateException("Firebase RTDB HTTP ${res.first}: ${res.second ?: "Request failed"}")
    }

    private fun executeHttp(
        method: String,
        cleanPath: String,
        bodyJson: String?,
        token: String?
    ): Pair<Int, String?> {
        val authQuery = if (!token.isNullOrBlank()) {
            "?auth=${URLEncoder.encode(token, "UTF-8")}"
        } else {
            ""
        }
        val url = URL("$baseUrl/$cleanPath.json$authQuery")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") {
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
            }
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            if (bodyJson != null) {
                doOutput = true
            }
        }
        try {
            if (bodyJson != null) {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(bodyJson)
                    writer.flush()
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { br -> br.readText() }
            }
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    suspend fun verifyStudentLogin(
        usnInput: String,
        passwordInput: String,
        divisionInput: String
    ): Result<StudentSession> {
        return try {
            val trimmedUsn = usnInput.trim()
            val upperUsn = trimmedUsn.uppercase()
            val lowerUsn = trimmedUsn.lowercase()
            require(trimmedUsn.length >= 3) { "Please enter your USN." }
            require(passwordInput.isNotBlank()) { "Please enter your password." }

            var targetKey = upperUsn
            var raw = request("GET", "students/$upperUsn")
            if (raw.isNullOrBlank() || raw == "null") {
                if (trimmedUsn != upperUsn) {
                    raw = request("GET", "students/$trimmedUsn")
                    if (!raw.isNullOrBlank() && raw != "null") targetKey = trimmedUsn
                }
            }
            if ((raw.isNullOrBlank() || raw == "null") && lowerUsn != upperUsn && lowerUsn != trimmedUsn) {
                raw = request("GET", "students/$lowerUsn")
                if (!raw.isNullOrBlank() && raw != "null") targetKey = lowerUsn
            }

            var json: JSONObject? = raw?.takeIf { it.isNotBlank() && it != "null" }?.let { JSONObject(it) }
            if (json == null) {
                val allRaw = request("GET", "students")
                if (!allRaw.isNullOrBlank() && allRaw != "null") {
                    val root = JSONObject(allRaw)
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val obj = root.optJSONObject(k) ?: continue
                        val fieldUsn = if (obj.isNull("usn")) "" else obj.optString("usn", "").trim()
                        if (k.equals(trimmedUsn, ignoreCase = true) || fieldUsn.equals(trimmedUsn, ignoreCase = true)) {
                            targetKey = k
                            json = obj
                            break
                        }
                    }
                }
            }

            val studentJson = json ?: return Result.failure(
                IllegalArgumentException("USN '$upperUsn' was not found in Firebase (blck-43179/students).")
            )

            val storedPassword = if (studentJson.isNull("password")) "" else studentJson.optString("password", "").trim()
            val expectedPassword = storedPassword.ifBlank { "1122" }
            if (passwordInput.trim() != expectedPassword) {
                return Result.failure(
                    IllegalArgumentException("Incorrect password for $targetKey.")
                )
            }

            val rawName = if (studentJson.isNull("name")) "" else studentJson.optString("name", "").trim()
            val name = rawName.ifBlank { targetKey }
            val rawUsn = if (studentJson.isNull("usn")) "" else studentJson.optString("usn", "").trim()
            val displayUsn = rawUsn.ifBlank { targetKey }.uppercase()
            val existingDiv = if (studentJson.isNull("division")) "" else studentJson.optString("division", "").trim().uppercase()
            val finalDiv = if (existingDiv in FocusScheduleUtils.DIVISIONS) {
                existingDiv
            } else {
                divisionInput.takeIf { it in FocusScheduleUtils.DIVISIONS } ?: "A"
            }

            val patchBody = JSONObject().apply {
                put("usn", displayUsn)
                put("name", name)
                put("division", finalDiv)
                put("lastLogin", System.currentTimeMillis())
            }.toString()
            request("PATCH", "students/$targetKey", patchBody)

            Result.success(StudentSession(usn = displayUsn, name = name, division = finalDiv))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "RTDB operation failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun verifyTeacherLogin(
        teacherIdInput: String,
        passwordInput: String
    ): Result<TeacherSession> {
        return try {
            val trimmedId = teacherIdInput.trim()
            val upperId = trimmedId.uppercase()
            val lowerId = trimmedId.lowercase()
            require(trimmedId.length >= 2) { "Please enter your Teacher ID." }
            require(passwordInput.isNotBlank()) { "Please enter your password." }

            var targetKey = upperId
            var raw = request("GET", "teachers/$upperId")
            if (raw.isNullOrBlank() || raw == "null") {
                if (trimmedId != upperId) {
                    raw = request("GET", "teachers/$trimmedId")
                    if (!raw.isNullOrBlank() && raw != "null") targetKey = trimmedId
                }
            }
            if ((raw.isNullOrBlank() || raw == "null") && lowerId != upperId && lowerId != trimmedId) {
                raw = request("GET", "teachers/$lowerId")
                if (!raw.isNullOrBlank() && raw != "null") targetKey = lowerId
            }

            var json: JSONObject? = raw?.takeIf { it.isNotBlank() && it != "null" }?.let { JSONObject(it) }
            if (json == null) {
                val allRaw = request("GET", "teachers")
                if (!allRaw.isNullOrBlank() && allRaw != "null") {
                    val root = JSONObject(allRaw)
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val obj = root.optJSONObject(k) ?: continue
                        val fieldId = if (obj.isNull("teacherId")) "" else obj.optString("teacherId", "").trim()
                        if (k.equals(trimmedId, ignoreCase = true) || fieldId.equals(trimmedId, ignoreCase = true)) {
                            targetKey = k
                            json = obj
                            break
                        }
                    }
                }
            }

            val teacherJson = json ?: return Result.failure(
                IllegalArgumentException("Teacher ID '$upperId' was not found in Firebase (blck-43179/teachers).")
            )

            val storedPassword = if (teacherJson.isNull("password")) "" else teacherJson.optString("password", "").trim()
            if (storedPassword.isBlank() || passwordInput.trim() != storedPassword) {
                return Result.failure(
                    IllegalArgumentException("Incorrect password for teacher $targetKey.")
                )
            }

            val rawName = if (teacherJson.isNull("name")) "" else teacherJson.optString("name", "").trim()
            val name = rawName.ifBlank { targetKey }
            Result.success(TeacherSession(teacherId = targetKey.uppercase(), name = name))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Teacher login check skipped: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchAllStudents(): Result<List<StudentRecord>> {
        return try {
            val raw = request("GET", "students")
            if (raw.isNullOrBlank() || raw == "null") {
                return Result.success(emptyList())
            }
            val root = JSONObject(raw)
            val list = mutableListOf<StudentRecord>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.optJSONObject(key) ?: continue
                val usn = obj.optString("usn", key).ifBlank { key }
                val name = obj.optString("name", usn).ifBlank { usn }
                val div = obj.optString("division", "A").uppercase().let {
                    if (it in FocusScheduleUtils.DIVISIONS) it else "A"
                }
                val inClass = obj.optBoolean("inClassMode", false)
                val activeClassId = obj.optString("activeClassId", "")
                val activeSubject = obj.optString("activeSubject", "")
                val lastLoginMs = obj.optLong("lastLogin", 0L)
                list.add(
                    StudentRecord(
                        usn = usn,
                        name = name,
                        division = div,
                        inClassMode = inClass,
                        activeClassId = activeClassId,
                        activeSubject = activeSubject,
                        lastLoginEpochMs = lastLoginMs
                    )
                )
            }
            Result.success(list.sortedBy { it.usn })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "fetchAllStudents skipped: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchTodayActivity(dateKey: String): Result<List<ClassActivity>> {
        return try {
            val raw = request("GET", "activity/$dateKey")
            if (raw.isNullOrBlank() || raw == "null") {
                return Result.success(emptyList())
            }
            val dateObj = JSONObject(raw)
            val list = mutableListOf<ClassActivity>()
            val classKeys = dateObj.keys()
            while (classKeys.hasNext()) {
                val classId = classKeys.next()
                val classNode = dateObj.optJSONObject(classId) ?: continue
                val usnKeys = classNode.keys()
                while (usnKeys.hasNext()) {
                    val usn = usnKeys.next()
                    val entry = classNode.opt(usn)
                    if (entry is JSONObject) {
                        val active = entry.optBoolean("active", true)
                        val breakActive = entry.optBoolean("breakActive", false)
                        list.add(
                            ClassActivity(
                                id = "${dateKey}_${classId}_$usn",
                                dateKey = dateKey,
                                classId = classId,
                                usn = entry.optString("usn", usn),
                                studentName = entry.optString("studentName", usn),
                                division = entry.optString("division", "A"),
                                subject = entry.optString("subject", "Lecture"),
                                teacherId = entry.optString("teacherId", ""),
                                teacherUid = entry.optString("teacherUid", ""),
                                active = active,
                                breakActive = breakActive
                            )
                        )
                    } else if (entry == true) {
                        list.add(
                            ClassActivity(
                                id = "${dateKey}_${classId}_$usn",
                                dateKey = dateKey,
                                classId = classId,
                                usn = usn,
                                studentName = usn,
                                active = true
                            )
                        )
                    }
                }
            }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "fetchTodayActivity skipped: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun writeStudentActivity(
        dateKey: String,
        classId: String,
        usn: String,
        studentName: String,
        division: String,
        subject: String,
        teacherId: String,
        active: Boolean,
        breakActive: Boolean
    ): Result<Unit> {
        return try {
            val cleanClassId = FocusScheduleUtils.sanitizeId(classId).ifBlank { "MANUAL_SESSION" }
            val cleanUsn = FocusScheduleUtils.sanitizeId(usn)
            val payload = JSONObject().apply {
                put("usn", cleanUsn)
                put("studentName", studentName)
                put("division", division)
                put("subject", subject)
                put("classId", cleanClassId)
                put("teacherId", teacherId)
                put("active", active)
                put("breakActive", breakActive)
                put("updatedAt", System.currentTimeMillis())
            }.toString()
            request("PUT", "activity/$dateKey/$cleanClassId/$cleanUsn", payload)

            val studentPatch = JSONObject().apply {
                put("inClassMode", active)
                put("activeClassId", if (active) cleanClassId else "")
                put("activeSubject", if (active) subject else "")
            }.toString()
            request("PATCH", "students/$cleanUsn", studentPatch)

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "writeStudentActivity skipped: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchClasses(): Result<List<ClassSchedule>> {
        return try {
            val raw = request("GET", "classes")
            if (raw.isNullOrBlank() || raw == "null") {
                return Result.success(emptyList())
            }
            val root = JSONObject(raw)
            val list = mutableListOf<ClassSchedule>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val obj = root.optJSONObject(id) ?: continue
                list.add(
                    ClassSchedule(
                        id = id,
                        teacherUid = obj.optString("teacherUid", ""),
                        teacherId = obj.optString("teacherId", ""),
                        teacherName = obj.optString("teacherName", ""),
                        subject = obj.optString("subject", ""),
                        division = obj.optString("division", "A").uppercase().let {
                            if (it in FocusScheduleUtils.DIVISIONS) it else "A"
                        },
                        day = FocusScheduleUtils.normalizeDay(obj.optString("day", "MON")),
                        start = FocusScheduleUtils.normalizeTime(obj.optString("start", "09:00")),
                        end = FocusScheduleUtils.normalizeTime(obj.optString("end", "10:00")),
                        room = obj.optString("room", ""),
                        active = obj.optBoolean("active", true)
                    )
                )
            }
            Result.success(
                list.sortedWith(
                    compareBy<ClassSchedule> { FocusScheduleUtils.DAYS.indexOf(it.day) }
                        .thenBy { it.start }
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "fetchClasses skipped: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun saveClass(schedule: ClassSchedule, existingId: String?): Result<String> {
        return try {
            val allClasses = fetchClasses().getOrDefault(emptyList())
            val clash = FocusScheduleUtils.findScheduleClash(
                candidate = schedule,
                existingClasses = allClasses,
                ignoreClassId = existingId.orEmpty()
            )
            if (clash != null) {
                val msg = if (clash.division.equals(schedule.division, ignoreCase = true)) {
                    "Schedule clash: Division ${schedule.division} already has ${clash.subject} (${clash.start}–${clash.end}) on ${schedule.day}."
                } else {
                    "Schedule clash: Teacher ${schedule.teacherId} already has ${clash.subject} (Div ${clash.division}, ${clash.start}–${clash.end}) on ${schedule.day}."
                }
                return Result.failure(IllegalStateException(msg))
            }

            val id = existingId?.takeIf { it.isNotBlank() }
                ?: "CLS_${schedule.day}_${schedule.start.replace(":", "")}_${schedule.division}_${System.currentTimeMillis() % 10000}"
            val payload = JSONObject().apply {
                put("teacherUid", schedule.teacherUid)
                put("teacherId", schedule.teacherId)
                put("teacherName", schedule.teacherName)
                put("subject", schedule.subject)
                put("division", schedule.division)
                put("day", schedule.day)
                put("start", schedule.start)
                put("end", schedule.end)
                put("room", schedule.room)
                put("active", true)
            }.toString()
            request("PUT", "classes/$id", payload)
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "saveClass skipped: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteClass(classId: String): Result<Unit> {
        return try {
            request("DELETE", "classes/$classId")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "deleteClass skipped: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun createBreakRequest(
        usn: String,
        studentName: String,
        division: String,
        classId: String,
        subject: String,
        teacherId: String,
        reason: String
    ): Result<String> {
        return try {
            val id = "BRK_${usn}_${System.currentTimeMillis()}"
            val payload = JSONObject().apply {
                put("usn", usn)
                put("studentName", studentName)
                put("division", division)
                put("classId", classId)
                put("subject", subject)
                put("teacherId", teacherId)
                put("reason", reason)
                put("status", BreakStatus.PENDING.wireValue)
                put("breakCode", "")
                put("durationMinutes", 10L)
                put("createdAtMs", System.currentTimeMillis())
            }.toString()
            request("PUT", "breakRequests/$id", payload)
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "createBreakRequest skipped: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchBreakRequests(): Result<List<BreakRequest>> {
        return try {
            val raw = request("GET", "breakRequests")
            if (raw.isNullOrBlank() || raw == "null") {
                return Result.success(emptyList())
            }
            val root = JSONObject(raw)
            val list = mutableListOf<BreakRequest>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val obj = root.optJSONObject(id) ?: continue
                list.add(
                    BreakRequest(
                        id = id,
                        usn = obj.optString("usn", ""),
                        studentName = obj.optString("studentName", ""),
                        division = obj.optString("division", "A"),
                        classId = obj.optString("classId", ""),
                        subject = obj.optString("subject", ""),
                        teacherId = obj.optString("teacherId", ""),
                        reason = obj.optString("reason", ""),
                        status = obj.optString("status", "PENDING"),
                        breakCode = obj.optString("breakCode", ""),
                        durationMinutes = obj.optLong("durationMinutes", 10L)
                    )
                )
            }
            Result.success(list.reversed())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "fetchBreakRequests skipped: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun reviewBreakRequest(
        requestId: String,
        approve: Boolean,
        usn: String
    ): Result<Unit> {
        return try {
            val status = if (approve) BreakStatus.APPROVED.wireValue else BreakStatus.DENIED.wireValue
            val code = if (approve) FocusScheduleUtils.computeDailyBreakCode(usn) else ""
            val patch = JSONObject().apply {
                put("status", status)
                put("breakCode", code)
            }.toString()
            request("PATCH", "breakRequests/$requestId", patch)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "reviewBreakRequest skipped: ${e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "BlckRtdbClient"
        const val DEFAULT_RTDB_URL = "https://blck-43179-default-rtdb.firebaseio.com"
    }
}
