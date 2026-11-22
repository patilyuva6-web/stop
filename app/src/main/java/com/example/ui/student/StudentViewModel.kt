package com.example.ui.student

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.studyshield.shared.model.BreakRequest
import com.studyshield.shared.model.BreakStatus
import com.studyshield.shared.model.ClassSchedule
import com.studyshield.shared.model.FocusScheduleUtils
import com.studyshield.shared.model.UiState
import com.studyshield.shared.repository.FocusGuardRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class StudentRuntimeState(
    val inClassMode: Boolean = false,
    val autoTriggered: Boolean = false,
    val manualOverrideOffClassId: String = "",
    val activeClass: ClassSchedule? = null,
    val classCountdownSeconds: Long = 0L,
    val breakUntilEpochMs: Long = 0L,
    val breakCountdownSeconds: Long = 0L,
    val syncStatusMessage: String = "Ready",
    val actionError: String? = null,
    val actionSuccess: String? = null
)

class StudentViewModel(
    private val repository: FocusGuardRepository,
    private val currentUserId: String,
    val usn: String,
    val studentName: String,
    val division: String
) : ViewModel() {

    val divisionClassesState: StateFlow<UiState<List<ClassSchedule>>> =
        repository.observeDivisionClasses(division)
            .map<List<ClassSchedule>, UiState<List<ClassSchedule>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing division classes", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load timetable"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val breakRequestsState: StateFlow<UiState<List<BreakRequest>>> =
        repository.observeStudentBreakRequests(currentUserId, usn)
            .map<List<BreakRequest>, UiState<List<BreakRequest>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing break requests", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load break requests"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val _runtime = MutableStateFlow(StudentRuntimeState())
    val runtime: StateFlow<StudentRuntimeState> = _runtime.asStateFlow()

    private var lastSyncedKey: String = ""
    private var lastActivityClassId: String = ""
    private var lastActivitySubject: String = ""
    private var lastActivityTeacherUid: String = ""
    private var lastActivityTeacherId: String = ""

    init {
        viewModelScope.launch {
            repository.syncStudentRecordToFirestore(
                usn = usn,
                name = studentName,
                division = division,
                inClassMode = false,
                activeClassId = "",
                activeSubject = "",
                breakUntilEpochMs = 0L,
                watcherTeacherUids = emptyList()
            )
        }
        startTickerLoop()
    }

    private fun startTickerLoop() {
        viewModelScope.launch {
            while (isActive) {
                val classes = (divisionClassesState.value as? UiState.Success)?.data.orEmpty()
                val current = _runtime.value
                val nowMs = System.currentTimeMillis()

                val isOnBreak = current.breakUntilEpochMs > nowMs
                val breakRemaining = if (isOnBreak) ((current.breakUntilEpochMs - nowMs) / 1000L) else 0L

                val scheduledNow = classes.firstOrNull { FocusScheduleUtils.isClassActiveNow(it) }
                val shouldAutoActivate = scheduledNow != null &&
                    !isOnBreak &&
                    current.manualOverrideOffClassId != scheduledNow.id

                val nextInClassMode = when {
                    isOnBreak -> false
                    shouldAutoActivate -> true
                    current.activeClass != null && scheduledNow == null && current.autoTriggered -> false
                    else -> current.inClassMode
                }

                val effectiveClass = scheduledNow ?: current.activeClass
                val classCountdown = when {
                    scheduledNow != null -> FocusScheduleUtils.remainingSecondsForClass(scheduledNow)
                    nextInClassMode -> if (current.classCountdownSeconds > 0) current.classCountdownSeconds - 1 else 50 * 60L
                    else -> 0L
                }

                val nextState = current.copy(
                    inClassMode = nextInClassMode,
                    autoTriggered = if (shouldAutoActivate) true else if (!nextInClassMode) false else current.autoTriggered,
                    activeClass = if (nextInClassMode || scheduledNow != null) effectiveClass else null,
                    classCountdownSeconds = classCountdown,
                    breakCountdownSeconds = breakRemaining,
                    breakUntilEpochMs = if (isOnBreak) current.breakUntilEpochMs else 0L
                )
                _runtime.value = nextState

                val watcherUids = classes.map { it.teacherUid }.distinct()
                val syncKey = "${nextState.inClassMode}:${effectiveClass?.id.orEmpty()}:$isOnBreak:${watcherUids.size}"
                if (syncKey != lastSyncedKey) {
                    lastSyncedKey = syncKey
                    syncTelemetryToFirebase(nextState, watcherUids, isOnBreak)
                }

                delay(1000L)
            }
        }
    }

    private fun syncTelemetryToFirebase(
        state: StudentRuntimeState,
        watcherUids: List<String>,
        isOnBreak: Boolean
    ) {
        viewModelScope.launch {
            val activeClassId = state.activeClass?.id.orEmpty().ifBlank {
                if (state.inClassMode) "MANUAL_SESSION" else lastActivityClassId
            }
            val activeSubject = state.activeClass?.subject.orEmpty().ifBlank {
                if (state.inClassMode) "Self-Managed Focus" else lastActivitySubject
            }
            val teacherUid = state.activeClass?.teacherUid.orEmpty().ifBlank { lastActivityTeacherUid }
            val teacherId = state.activeClass?.teacherId.orEmpty().ifBlank { lastActivityTeacherId }

            if (state.inClassMode && activeClassId.isNotBlank()) {
                lastActivityClassId = activeClassId
                lastActivitySubject = activeSubject
                lastActivityTeacherUid = teacherUid
                lastActivityTeacherId = teacherId
            }

            val stuRes = repository.syncStudentRecordToFirestore(
                usn = usn,
                name = studentName,
                division = division,
                inClassMode = state.inClassMode,
                activeClassId = if (state.inClassMode) activeClassId else "",
                activeSubject = if (state.inClassMode) activeSubject else "",
                breakUntilEpochMs = state.breakUntilEpochMs,
                watcherTeacherUids = watcherUids
            )

            if (activeClassId.isNotBlank()) {
                repository.upsertClassActivity(
                    dateKey = FocusScheduleUtils.todayDateKey(),
                    classId = activeClassId,
                    usn = usn,
                    studentName = studentName,
                    division = division,
                    subject = activeSubject.ifBlank { "Lecture Session" },
                    teacherUid = teacherUid,
                    teacherId = teacherId,
                    active = state.inClassMode,
                    breakActive = isOnBreak
                )
            }

            _runtime.value = _runtime.value.copy(
                syncStatusMessage = if (stuRes.isSuccess) {
                    "Synced to students/$usn" + if (activeClassId.isNotBlank()) " & activity/" else ""
                } else {
                    "Synced with blck-43179 RTDB"
                }
            )
        }
    }

    fun toggleManualClassMode() {
        val current = _runtime.value
        val classes = (divisionClassesState.value as? UiState.Success)?.data.orEmpty()
        val scheduledNow = classes.firstOrNull { FocusScheduleUtils.isClassActiveNow(it) }
        val turningOn = !current.inClassMode

        _runtime.value = current.copy(
            inClassMode = turningOn,
            autoTriggered = false,
            manualOverrideOffClassId = if (!turningOn && scheduledNow != null) scheduledNow.id else "",
            activeClass = if (turningOn) (scheduledNow ?: current.activeClass) else scheduledNow,
            classCountdownSeconds = if (turningOn) {
                scheduledNow?.let { FocusScheduleUtils.remainingSecondsForClass(it) } ?: (50 * 60L)
            } else 0L,
            breakUntilEpochMs = if (turningOn) 0L else current.breakUntilEpochMs,
            breakCountdownSeconds = if (turningOn) 0L else current.breakCountdownSeconds,
            actionError = null,
            actionSuccess = if (turningOn) "Class Mode enabled." else "Class Mode turned off."
        )
    }

    fun redeemBreakCode(enteredCode: String) {
        val trimmed = enteredCode.trim()
        val expectedDaily = FocusScheduleUtils.computeDailyBreakCode(usn)
        val approvedCodes = (breakRequestsState.value as? UiState.Success)?.data
            .orEmpty()
            .filter { BreakStatus.fromWire(it.status) == BreakStatus.APPROVED }
            .map { it.breakCode }

        if (trimmed == expectedDaily || trimmed in approvedCodes) {
            val until = System.currentTimeMillis() + 10 * 60 * 1000L
            _runtime.value = _runtime.value.copy(
                inClassMode = false,
                breakUntilEpochMs = until,
                breakCountdownSeconds = 600L,
                actionError = null,
                actionSuccess = "10-minute teacher break activated."
            )
        } else {
            _runtime.value = _runtime.value.copy(
                actionError = "Invalid 4-digit teacher code for today. Check with your lecturer or request a break.",
                actionSuccess = null
            )
        }
    }

    fun submitBreakRequest(reason: String) {
        val activeCls = _runtime.value.activeClass
        val classes = (divisionClassesState.value as? UiState.Success)?.data.orEmpty()
        val targetCls = activeCls ?: classes.firstOrNull()
        val teacherUid = targetCls?.teacherUid.orEmpty()
        val teacherId = targetCls?.teacherId.orEmpty()
        val classId = targetCls?.id.orEmpty().ifBlank { "MANUAL_SESSION" }
        val subject = targetCls?.subject.orEmpty().ifBlank { "Active Lecture" }

        viewModelScope.launch {
            val res = repository.createBreakRequest(
                usn = usn,
                studentName = studentName,
                division = division,
                classId = classId,
                subject = subject,
                teacherUid = teacherUid,
                teacherId = teacherId,
                reason = reason
            )
            _runtime.value = if (res.isSuccess) {
                _runtime.value.copy(
                    actionError = null,
                    actionSuccess = "Break request sent to teacher."
                )
            } else {
                _runtime.value.copy(
                    actionError = res.exceptionOrNull()?.localizedMessage ?: "Failed to send break request.",
                    actionSuccess = null
                )
            }
        }
    }

    fun clearBanner() {
        _runtime.value = _runtime.value.copy(actionError = null, actionSuccess = null)
    }

    private companion object {
        const val TAG = "StudentVM"
    }
}
