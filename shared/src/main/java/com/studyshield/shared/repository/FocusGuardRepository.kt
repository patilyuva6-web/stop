package com.studyshield.shared.repository

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.studyshield.shared.firebase.BlckRealtimeDbClient
import com.studyshield.shared.firebase.OperationType
import com.studyshield.shared.firebase.RtdbPermissionException
import com.studyshield.shared.firebase.handleFirestoreError
import com.studyshield.shared.model.BreakRequest
import com.studyshield.shared.model.BreakStatus
import com.studyshield.shared.model.ClassActivity
import com.studyshield.shared.model.ClassSchedule
import com.studyshield.shared.model.FocusScheduleUtils
import com.studyshield.shared.model.StudentRecord
import com.studyshield.shared.model.StudentSession
import com.studyshield.shared.model.TeacherSession
import com.studyshield.shared.model.WorkspaceRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import java.util.Calendar

/**
 * In-process shared state for StudyShield & TeacherFocus demo sessions so
 * timetable classes, live Class Mode dots, and break requests work instantly
 * offline/without Firebase login, while syncing to Firestore/RTDB whenever
 * the user links a Google account.
 */
private object LocalFocusStore {
    val students = MutableStateFlow<Map<String, StudentRecord>>(emptyMap())
    val classes = MutableStateFlow<Map<String, ClassSchedule>>(emptyMap())
    val activity = MutableStateFlow<Map<String, ClassActivity>>(emptyMap())
    val breakRequests = MutableStateFlow<Map<String, BreakRequest>>(emptyMap())
}

class FocusGuardRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val rtdb: BlckRealtimeDbClient = BlckRealtimeDbClient()
) {
    private fun currentGoogleUid(): String? = auth.currentUser?.uid

    /**
     * Local demo student login (USN + demo password 1122).
     * Does not depend on Firebase authentication or RTDB rules.
     */
    suspend fun verifyAndLoginStudent(
        usn: String,
        password: String,
        division: String,
        studentName: String = ""
    ): Result<StudentSession> {
        val cleanUsn = FocusScheduleUtils.sanitizeId(usn)
        if (cleanUsn.length < 3) {
            return Result.failure(IllegalArgumentException("Please enter a valid USN (at least 3 characters)."))
        }
        if (password.trim() != DEMO_PASSWORD) {
            return Result.failure(IllegalArgumentException("Incorrect password. Demo password is $DEMO_PASSWORD."))
        }
        val cleanDiv = if (division in FocusScheduleUtils.DIVISIONS) division else "A"
        val cleanName = studentName.trim().ifBlank { "Student $cleanUsn" }.take(80)

        LocalFocusStore.students.update { current ->
            val existing = current[cleanUsn]
            val updated = (existing ?: StudentRecord(usn = cleanUsn)).copy(
                usn = cleanUsn,
                name = cleanName,
                division = cleanDiv,
                lastLoginEpochMs = System.currentTimeMillis()
            )
            current + (cleanUsn to updated)
        }

        if (currentGoogleUid() != null) {
            runCatching {
                saveUserProfile(
                    role = WorkspaceRole.STUDENT,
                    displayName = cleanName,
                    usn = cleanUsn,
                    teacherId = "",
                    division = cleanDiv
                )
                syncStudentRecordToFirestore(
                    usn = cleanUsn,
                    name = cleanName,
                    division = cleanDiv,
                    inClassMode = false,
                    activeClassId = "",
                    activeSubject = "",
                    breakUntilEpochMs = 0L,
                    watcherTeacherUids = emptyList()
                )
            }
        }
        return Result.success(StudentSession(usn = cleanUsn, name = cleanName, division = cleanDiv))
    }

    /**
     * Local demo teacher login (Teacher ID e.g. T001 + demo password 1122).
     * Does not depend on Firebase authentication or RTDB rules.
     */
    suspend fun verifyAndLoginTeacher(
        teacherId: String,
        password: String,
        teacherName: String = ""
    ): Result<TeacherSession> {
        val cleanId = FocusScheduleUtils.sanitizeId(teacherId)
        if (cleanId.length < 2) {
            return Result.failure(IllegalArgumentException("Please enter a valid Teacher ID (e.g. T001)."))
        }
        if (password.trim() != DEMO_PASSWORD) {
            return Result.failure(IllegalArgumentException("Incorrect password. Demo password is $DEMO_PASSWORD."))
        }
        val cleanName = teacherName.trim().ifBlank { "Faculty $cleanId" }.take(80)

        if (currentGoogleUid() != null) {
            runCatching {
                saveUserProfile(
                    role = WorkspaceRole.TEACHER,
                    displayName = cleanName,
                    usn = "",
                    teacherId = cleanId,
                    division = "A"
                )
            }
        }
        return Result.success(TeacherSession(teacherId = cleanId, name = cleanName))
    }

    suspend fun saveUserProfile(
        role: WorkspaceRole,
        displayName: String,
        usn: String,
        teacherId: String,
        division: String
    ): Result<Unit> {
        val uid = currentGoogleUid() ?: return Result.success(Unit)
        val cleanName = displayName.trim().take(80)
        val cleanUsn = if (usn.isBlank()) "" else FocusScheduleUtils.sanitizeId(usn).take(32)
        val cleanTeacherId = if (teacherId.isBlank()) "" else FocusScheduleUtils.sanitizeId(teacherId).take(32)
        val cleanDiv = if (division in FocusScheduleUtils.DIVISIONS) division else "A"

        var docPath = "users/$uid"
        return try {
            require(cleanName.isNotEmpty()) { "Display name is required." }
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "role" to role.wireValue,
                        "displayName" to cleanName,
                        "usn" to cleanUsn,
                        "teacherId" to cleanTeacherId,
                        "division" to cleanDiv,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "role" to role.wireValue,
                        "displayName" to cleanName,
                        "usn" to cleanUsn,
                        "teacherId" to cleanTeacherId,
                        "division" to cleanDiv,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeDivisionClasses(division: String): Flow<List<ClassSchedule>> {
        val localFlow = LocalFocusStore.classes.map { map ->
            map.values.filter { it.active && it.division.equals(division, ignoreCase = true) }
        }

        val uid = currentGoogleUid()
        if (uid.isNullOrBlank()) {
            return localFlow.map { list ->
                list.sortedWith(
                    compareBy<ClassSchedule> { FocusScheduleUtils.DAYS.indexOf(it.day) }
                        .thenBy { it.start }
                )
            }
        }

        val rtdbFlow: Flow<List<ClassSchedule>> = flow {
            emit(emptyList())
            while (currentCoroutineContext().isActive) {
                val res = rtdb.fetchClasses()
                if (res.exceptionOrNull() is RtdbPermissionException) break
                val all = res.getOrDefault(emptyList())
                    .filter { it.active && it.division.equals(division, ignoreCase = true) }
                emit(all)
                delay(4000L)
            }
        }

        val firestoreFlow: Flow<List<ClassSchedule>> = db.collection("classes")
            .whereEqualTo("active", true)
            .whereEqualTo("division", division)
            .snapshots()
            .map { snap ->
                snap.toObjects(ClassSchedule::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "classes", uid)
                emit(emptyList())
            }

        return combine(localFlow, rtdbFlow, firestoreFlow) { localList, rtdbList, fsList ->
            val merged = LinkedHashMap<String, ClassSchedule>()
            localList.forEach { merged[it.id] = it }
            fsList.forEach { merged[it.id] = it }
            rtdbList.forEach { merged[it.id] = it }
            merged.values.sortedWith(
                compareBy<ClassSchedule> { FocusScheduleUtils.DAYS.indexOf(it.day) }
                    .thenBy { it.start }
            )
        }
    }

    fun observeTeacherClasses(teacherUid: String, teacherId: String): Flow<List<ClassSchedule>> {
        val localFlow = LocalFocusStore.classes.map { map ->
            map.values.filter { it.teacherId.equals(teacherId, ignoreCase = true) }
        }

        val uid = currentGoogleUid()
        if (uid.isNullOrBlank() || teacherUid.isBlank()) {
            return localFlow.map { list ->
                list.sortedWith(
                    compareBy<ClassSchedule> { FocusScheduleUtils.DAYS.indexOf(it.day) }
                        .thenBy { it.start }
                )
            }
        }

        val rtdbFlow: Flow<List<ClassSchedule>> = flow {
            emit(emptyList())
            while (currentCoroutineContext().isActive) {
                val res = rtdb.fetchClasses()
                if (res.exceptionOrNull() is RtdbPermissionException) break
                val mine = res.getOrDefault(emptyList())
                    .filter { it.teacherId.equals(teacherId, ignoreCase = true) }
                emit(mine)
                delay(4000L)
            }
        }

        val firestoreFlow: Flow<List<ClassSchedule>> = db.collection("classes")
            .whereEqualTo("teacherUid", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(ClassSchedule::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "classes", uid)
                emit(emptyList())
            }

        return combine(localFlow, rtdbFlow, firestoreFlow) { localList, rtdbList, fsList ->
            val merged = LinkedHashMap<String, ClassSchedule>()
            localList.forEach { merged[it.id] = it }
            fsList.forEach { merged[it.id] = it }
            rtdbList.forEach { merged[it.id] = it }
            merged.values.sortedWith(
                compareBy<ClassSchedule> { FocusScheduleUtils.DAYS.indexOf(it.day) }
                    .thenBy { it.start }
            )
        }
    }

    suspend fun saveClassSchedule(
        existingClassId: String?,
        teacherId: String,
        teacherName: String,
        subject: String,
        division: String,
        day: String,
        start: String,
        end: String,
        room: String
    ): Result<Unit> {
        return try {
            val uid = currentGoogleUid().orEmpty()
            val cleanSubject = subject.trim().take(80)
            val cleanTeacherId = FocusScheduleUtils.sanitizeId(teacherId).ifBlank { "T001" }.take(24)
            val cleanTeacherName = teacherName.trim().ifBlank { cleanTeacherId }.take(80)
            val cleanRoom = room.trim().take(40)
            val normStart = FocusScheduleUtils.normalizeTime(start)
            val normEnd = FocusScheduleUtils.normalizeTime(end)
            val normDay = FocusScheduleUtils.normalizeDay(day)

            require(cleanSubject.isNotEmpty()) { "Subject name is required." }
            require(division in FocusScheduleUtils.DIVISIONS) { "Division must be A–F." }
            require(normDay in FocusScheduleUtils.DAYS) { "Invalid day of week." }
            val startMins = FocusScheduleUtils.parseMinutes(normStart)
                ?: throw IllegalArgumentException("Start time must be HH:mm.")
            val endMins = FocusScheduleUtils.parseMinutes(normEnd)
                ?: throw IllegalArgumentException("End time must be HH:mm.")
            require(startMins < endMins) { "End time must be after start time." }

            val candidateId = existingClassId?.takeIf { it.isNotBlank() }
                ?: "CLS_${normDay}_${normStart.replace(":", "")}_${division}_${System.currentTimeMillis() % 10000}"

            val candidate = ClassSchedule(
                id = candidateId,
                teacherUid = uid,
                teacherId = cleanTeacherId,
                teacherName = cleanTeacherName,
                subject = cleanSubject,
                division = division,
                day = normDay,
                start = normStart,
                end = normEnd,
                room = cleanRoom,
                active = true
            )

            // Check clash against local store first
            val localClash = FocusScheduleUtils.findScheduleClash(
                candidate = candidate,
                existingClasses = LocalFocusStore.classes.value.values.toList(),
                ignoreClassId = existingClassId.orEmpty()
            )
            if (localClash != null) {
                val msg = if (localClash.division.equals(candidate.division, ignoreCase = true)) {
                    "Schedule clash: Division ${candidate.division} already has ${localClash.subject} (${localClash.start}–${localClash.end}) on ${candidate.day}."
                } else {
                    "Schedule clash: Teacher ${candidate.teacherId} already has ${localClash.subject} (Div ${localClash.division}, ${localClash.start}–${localClash.end}) on ${candidate.day}."
                }
                return Result.failure(IllegalStateException(msg))
            }

            LocalFocusStore.classes.update { it + (candidateId to candidate) }

            // Best-effort sync to RTDB & Firestore only when Google-authenticated
            if (uid.isNotBlank()) {
                val rtdbSave = rtdb.saveClass(candidate, candidateId)
                val savedId = rtdbSave.getOrNull() ?: candidateId
                val docRef = db.collection("classes").document(savedId)
                val snap = runCatching { docRef.get().await() }.getOrNull()
                if (snap != null && snap.exists() && snap.getString("teacherUid") == uid) {
                    docRef.update(
                        mapOf(
                            "teacherId" to cleanTeacherId,
                            "teacherName" to cleanTeacherName,
                            "subject" to cleanSubject,
                            "division" to division,
                            "day" to normDay,
                            "start" to normStart,
                            "end" to normEnd,
                            "room" to cleanRoom,
                            "active" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    docRef.set(
                        mapOf(
                            "teacherUid" to uid,
                            "teacherId" to cleanTeacherId,
                            "teacherName" to cleanTeacherName,
                            "subject" to cleanSubject,
                            "division" to division,
                            "day" to normDay,
                            "start" to normStart,
                            "end" to normEnd,
                            "room" to cleanRoom,
                            "active" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "classes")
            Result.failure(e)
        }
    }

    suspend fun deleteClassSchedule(classId: String): Result<Unit> {
        return try {
            require(classId.isNotBlank()) { "Class ID required." }
            LocalFocusStore.classes.update { it - classId }
            if (currentGoogleUid() != null) {
                runCatching { rtdb.deleteClass(classId) }
                runCatching { db.collection("classes").document(classId).delete().await() }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "classes/$classId")
            Result.failure(e)
        }
    }

    suspend fun syncStudentRecordToFirestore(
        usn: String,
        name: String,
        division: String,
        inClassMode: Boolean,
        activeClassId: String,
        activeSubject: String,
        breakUntilEpochMs: Long,
        watcherTeacherUids: List<String>
    ): Result<Unit> {
        val cleanUsn = FocusScheduleUtils.sanitizeId(usn).take(32)
        val cleanName = name.trim().ifBlank { cleanUsn }.take(80)
        val cleanDiv = if (division in FocusScheduleUtils.DIVISIONS) division else "A"

        if (cleanUsn.length >= 3) {
            LocalFocusStore.students.update { current ->
                val updated = (current[cleanUsn] ?: StudentRecord(usn = cleanUsn)).copy(
                    usn = cleanUsn,
                    name = cleanName,
                    division = cleanDiv,
                    inClassMode = inClassMode,
                    activeClassId = activeClassId.take(128),
                    activeSubject = activeSubject.take(80),
                    breakUntilEpochMs = breakUntilEpochMs.coerceAtLeast(0L),
                    lastLoginEpochMs = System.currentTimeMillis()
                )
                current + (cleanUsn to updated)
            }
        }

        val uid = currentGoogleUid() ?: return Result.success(Unit)
        var docPath = "students/$cleanUsn"
        return try {
            require(cleanUsn.length >= 3) { "USN must be at least 3 characters." }
            val watchers = (listOf(uid) + watcherTeacherUids).filter { it.isNotBlank() }.distinct().take(25)

            val docRef = db.collection("students").document(cleanUsn)
            docPath = docRef.path
            val snap = runCatching { docRef.get().await() }.getOrNull()
            if (snap != null && snap.exists()) {
                if (snap.getString("ownerUid") == uid) {
                    docRef.update(
                        mapOf(
                            "name" to cleanName,
                            "division" to cleanDiv,
                            "inClassMode" to inClassMode,
                            "activeClassId" to activeClassId.take(128),
                            "activeSubject" to activeSubject.take(80),
                            "breakUntilEpochMs" to breakUntilEpochMs.coerceAtLeast(0L),
                            "watcherUids" to watchers,
                            "lastLogin" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } else {
                docRef.set(
                    mapOf(
                        "usn" to cleanUsn,
                        "ownerUid" to uid,
                        "name" to cleanName,
                        "division" to cleanDiv,
                        "inClassMode" to inClassMode,
                        "activeClassId" to activeClassId.take(128),
                        "activeSubject" to activeSubject.take(80),
                        "breakUntilEpochMs" to breakUntilEpochMs.coerceAtLeast(0L),
                        "watcherUids" to watchers,
                        "lastLogin" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeAllStudents(teacherUid: String): Flow<List<StudentRecord>> {
        val localFlow = LocalFocusStore.students.map { it.values.sortedBy { s -> s.usn } }
        val uid = currentGoogleUid()
        if (uid.isNullOrBlank() || teacherUid.isBlank()) return localFlow

        val rtdbFlow: Flow<List<StudentRecord>> = flow {
            emit(emptyList())
            while (currentCoroutineContext().isActive) {
                val res = rtdb.fetchAllStudents()
                if (res.exceptionOrNull() is RtdbPermissionException) break
                emit(res.getOrDefault(emptyList()))
                delay(4000L)
            }
        }

        val firestoreFlow: Flow<List<StudentRecord>> = db.collection("students")
            .whereArrayContains("watcherUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(StudentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "students", uid)
                emit(emptyList())
            }

        return combine(localFlow, rtdbFlow, firestoreFlow) { localList, rtdbList, fsList ->
            val merged = LinkedHashMap<String, StudentRecord>()
            localList.forEach { merged[it.usn] = it }
            fsList.forEach { merged[it.usn] = it }
            rtdbList.forEach { merged[it.usn] = it }
            merged.values.sortedBy { it.usn }
        }
    }

    suspend fun upsertClassActivity(
        dateKey: String,
        classId: String,
        usn: String,
        studentName: String,
        division: String,
        subject: String,
        teacherUid: String,
        teacherId: String,
        active: Boolean,
        breakActive: Boolean
    ): Result<Unit> {
        val cleanUsn = FocusScheduleUtils.sanitizeId(usn)
        val cleanClassId = FocusScheduleUtils.sanitizeId(classId).ifBlank { "MANUAL_SESSION" }
        val activityId = "${dateKey}_${cleanClassId}_${cleanUsn}".take(128)

        LocalFocusStore.activity.update { current ->
            val record = ClassActivity(
                id = activityId,
                dateKey = dateKey,
                classId = cleanClassId,
                usn = cleanUsn,
                studentName = studentName.trim().ifBlank { cleanUsn }.take(80),
                division = division,
                subject = subject.trim().ifBlank { "Lecture Session" }.take(80),
                ownerUid = currentGoogleUid().orEmpty(),
                teacherUid = teacherUid,
                teacherId = teacherId,
                active = active,
                breakActive = breakActive
            )
            current + (activityId to record)
        }

        val uid = currentGoogleUid() ?: return Result.success(Unit)
        runCatching {
            rtdb.writeStudentActivity(
                dateKey = dateKey,
                classId = cleanClassId,
                usn = cleanUsn,
                studentName = studentName,
                division = division,
                subject = subject,
                teacherId = teacherId,
                active = active,
                breakActive = breakActive
            )
        }

        val docPath = "activity/$activityId"
        return try {
            val effectiveTeacherUid = teacherUid.ifBlank { uid }
            val docRef = db.collection("activity").document(activityId)
            val snap = runCatching { docRef.get().await() }.getOrNull()
            if (snap != null && snap.exists() && snap.getString("ownerUid") == uid) {
                docRef.update(
                    mapOf(
                        "studentName" to studentName.trim().ifBlank { cleanUsn }.take(80),
                        "division" to division,
                        "subject" to subject.trim().ifBlank { "Lecture Session" }.take(80),
                        "teacherUid" to effectiveTeacherUid,
                        "active" to active,
                        "breakActive" to breakActive,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else if (snap == null || !snap.exists()) {
                docRef.set(
                    mapOf(
                        "dateKey" to dateKey,
                        "classId" to cleanClassId,
                        "usn" to cleanUsn,
                        "studentName" to studentName.trim().ifBlank { cleanUsn }.take(80),
                        "division" to division,
                        "subject" to subject.trim().ifBlank { "Lecture Session" }.take(80),
                        "ownerUid" to uid,
                        "teacherUid" to effectiveTeacherUid,
                        "active" to active,
                        "breakActive" to breakActive,
                        "startedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeTodayActivity(teacherUid: String, dateKey: String): Flow<List<ClassActivity>> {
        val localFlow = LocalFocusStore.activity.map { map ->
            map.values.filter { it.dateKey == dateKey }
        }

        val uid = currentGoogleUid()
        if (uid.isNullOrBlank() || teacherUid.isBlank()) return localFlow

        val rtdbFlow: Flow<List<ClassActivity>> = flow {
            emit(emptyList())
            while (currentCoroutineContext().isActive) {
                val res = rtdb.fetchTodayActivity(dateKey)
                if (res.exceptionOrNull() is RtdbPermissionException) break
                emit(res.getOrDefault(emptyList()))
                delay(3000L)
            }
        }

        val firestoreFlow: Flow<List<ClassActivity>> = db.collection("activity")
            .whereEqualTo("teacherUid", uid)
            .whereEqualTo("dateKey", dateKey)
            .snapshots()
            .map { snap ->
                snap.toObjects(ClassActivity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "activity", uid)
                emit(emptyList())
            }

        return combine(localFlow, rtdbFlow, firestoreFlow) { localList, rtdbList, fsList ->
            val merged = LinkedHashMap<String, ClassActivity>()
            localList.forEach { merged[it.id] = it }
            fsList.forEach { merged[it.id] = it }
            rtdbList.forEach { merged[it.id] = it }
            merged.values.toList()
        }
    }

    suspend fun createBreakRequest(
        usn: String,
        studentName: String,
        division: String,
        classId: String,
        subject: String,
        teacherUid: String,
        teacherId: String,
        reason: String
    ): Result<Unit> {
        val cleanReason = reason.trim().take(200)
        if (cleanReason.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter a brief reason for your break."))
        }
        val cleanUsn = FocusScheduleUtils.sanitizeId(usn)
        val cleanClassId = FocusScheduleUtils.sanitizeId(classId).ifBlank { "MANUAL_SESSION" }
        val localId = "BRK_${cleanUsn}_${System.currentTimeMillis()}"

        LocalFocusStore.breakRequests.update { current ->
            val req = BreakRequest(
                id = localId,
                ownerUid = currentGoogleUid().orEmpty(),
                usn = cleanUsn,
                studentName = studentName.trim().ifBlank { cleanUsn }.take(80),
                division = division,
                classId = cleanClassId,
                subject = subject.trim().ifBlank { "Lecture" }.take(80),
                teacherUid = teacherUid,
                teacherId = teacherId,
                reason = cleanReason,
                status = BreakStatus.PENDING.wireValue,
                breakCode = "",
                durationMinutes = 10L
            )
            current + (localId to req)
        }

        val uid = currentGoogleUid() ?: return Result.success(Unit)
        val rtdbRes = runCatching {
            rtdb.createBreakRequest(
                usn = cleanUsn,
                studentName = studentName,
                division = division,
                classId = cleanClassId,
                subject = subject,
                teacherId = teacherId,
                reason = cleanReason
            )
        }.getOrNull()

        return try {
            val reqId = rtdbRes?.getOrNull() ?: localId
            db.collection("breakRequests").document(reqId).set(
                mapOf(
                    "ownerUid" to uid,
                    "usn" to cleanUsn,
                    "studentName" to studentName.trim().ifBlank { cleanUsn }.take(80),
                    "division" to division,
                    "classId" to cleanClassId,
                    "subject" to subject.trim().ifBlank { "Lecture" }.take(80),
                    "teacherUid" to teacherUid.ifBlank { uid },
                    "reason" to cleanReason,
                    "status" to "PENDING",
                    "breakCode" to "",
                    "durationMinutes" to 10L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "breakRequests")
            Result.failure(e)
        }
    }

    fun observeStudentBreakRequests(ownerUid: String, usn: String): Flow<List<BreakRequest>> {
        val localFlow = LocalFocusStore.breakRequests.map { map ->
            map.values.filter { it.usn.equals(usn, ignoreCase = true) }.reversed()
        }

        val uid = currentGoogleUid()
        if (uid.isNullOrBlank() || ownerUid.isBlank()) return localFlow

        val rtdbFlow: Flow<List<BreakRequest>> = flow {
            emit(emptyList())
            while (currentCoroutineContext().isActive) {
                val res = rtdb.fetchBreakRequests()
                if (res.exceptionOrNull() is RtdbPermissionException) break
                val mine = res.getOrDefault(emptyList())
                    .filter { it.usn.equals(usn, ignoreCase = true) }
                emit(mine)
                delay(3500L)
            }
        }

        val firestoreFlow: Flow<List<BreakRequest>> = db.collection("breakRequests")
            .whereEqualTo("ownerUid", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(BreakRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "breakRequests", uid)
                emit(emptyList())
            }

        return combine(localFlow, rtdbFlow, firestoreFlow) { localList, rtdbList, fsList ->
            val merged = LinkedHashMap<String, BreakRequest>()
            localList.forEach { merged[it.id] = it }
            fsList.forEach { merged[it.id] = it }
            rtdbList.forEach { merged[it.id] = it }
            merged.values.toList()
        }
    }

    fun observeTeacherBreakRequests(teacherUid: String, teacherId: String): Flow<List<BreakRequest>> {
        val localFlow = LocalFocusStore.breakRequests.map { map ->
            map.values.filter { it.teacherId.isBlank() || it.teacherId.equals(teacherId, ignoreCase = true) }.reversed()
        }

        val uid = currentGoogleUid()
        if (uid.isNullOrBlank() || teacherUid.isBlank()) return localFlow

        val rtdbFlow: Flow<List<BreakRequest>> = flow {
            emit(emptyList())
            while (currentCoroutineContext().isActive) {
                val res = rtdb.fetchBreakRequests()
                if (res.exceptionOrNull() is RtdbPermissionException) break
                val list = res.getOrDefault(emptyList())
                    .filter { it.teacherId.isBlank() || it.teacherId.equals(teacherId, ignoreCase = true) }
                emit(list)
                delay(3500L)
            }
        }

        val firestoreFlow: Flow<List<BreakRequest>> = db.collection("breakRequests")
            .whereEqualTo("teacherUid", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(BreakRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "breakRequests", uid)
                emit(emptyList())
            }

        return combine(localFlow, rtdbFlow, firestoreFlow) { localList, rtdbList, fsList ->
            val merged = LinkedHashMap<String, BreakRequest>()
            localList.forEach { merged[it.id] = it }
            fsList.forEach { merged[it.id] = it }
            rtdbList.forEach { merged[it.id] = it }
            merged.values.toList()
        }
    }

    suspend fun reviewBreakRequest(
        requestId: String,
        approve: Boolean,
        usn: String
    ): Result<Unit> {
        val code = if (approve) FocusScheduleUtils.computeDailyBreakCode(usn) else ""
        val status = if (approve) BreakStatus.APPROVED.wireValue else BreakStatus.DENIED.wireValue

        LocalFocusStore.breakRequests.update { current ->
            val existing = current[requestId] ?: return@update current
            current + (requestId to existing.copy(status = status, breakCode = code))
        }

        if (currentGoogleUid() == null) return Result.success(Unit)
        return try {
            runCatching { rtdb.reviewBreakRequest(requestId, approve, usn) }
            runCatching {
                db.collection("breakRequests").document(requestId).update(
                    mapOf(
                        "status" to status,
                        "breakCode" to code,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "breakRequests/$requestId")
            Result.failure(e)
        }
    }

    suspend fun startInstantLiveSession(
        teacherId: String,
        teacherName: String,
        division: String = "A"
    ): Result<Unit> {
        val cal = Calendar.getInstance()
        val nowMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = FocusScheduleUtils.formatMinutesAsHHmm((nowMins - 5).coerceAtLeast(0))
        val end = FocusScheduleUtils.formatMinutesAsHHmm((nowMins + 55).coerceAtMost(23 * 60 + 59))
        return saveClassSchedule(
            existingClassId = null,
            teacherId = teacherId,
            teacherName = teacherName,
            subject = "Live Lecture Session",
            division = division,
            day = FocusScheduleUtils.currentDayCode(),
            start = start,
            end = end,
            room = "LH-Main"
        )
    }

    private fun handleFlowError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    companion object {
        const val DEMO_PASSWORD = "1122"
    }
}
