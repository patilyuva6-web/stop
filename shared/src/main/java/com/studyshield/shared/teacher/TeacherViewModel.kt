package com.studyshield.shared.teacher

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.studyshield.shared.model.BreakRequest
import com.studyshield.shared.model.ClassActivity
import com.studyshield.shared.model.ClassSchedule
import com.studyshield.shared.model.FocusScheduleUtils
import com.studyshield.shared.model.StudentRecord
import com.studyshield.shared.model.UiState
import com.studyshield.shared.repository.FocusGuardRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TeacherActionBanner(
    val error: String? = null,
    val success: String? = null,
    val isBusy: Boolean = false
)

class TeacherViewModel(
    private val repository: FocusGuardRepository,
    private val currentUserId: String,
    val teacherId: String,
    val teacherName: String
) : ViewModel() {

    val myClassesState: StateFlow<UiState<List<ClassSchedule>>> =
        repository.observeTeacherClasses(currentUserId, teacherId)
            .map<List<ClassSchedule>, UiState<List<ClassSchedule>>> { UiState.Success(it) }
            .catch { error ->
                emit(UiState.Error(unwrapFirestoreError(error, "observeTeacherClasses")))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val studentsState: StateFlow<UiState<List<StudentRecord>>> =
        repository.observeAllStudents(currentUserId)
            .map<List<StudentRecord>, UiState<List<StudentRecord>>> { UiState.Success(it) }
            .catch { error ->
                emit(UiState.Error(unwrapFirestoreError(error, "observeAllStudents")))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val todayActivityState: StateFlow<UiState<List<ClassActivity>>> =
        repository.observeTodayActivity(currentUserId, FocusScheduleUtils.todayDateKey())
            .map<List<ClassActivity>, UiState<List<ClassActivity>>> { UiState.Success(it) }
            .catch { error ->
                emit(UiState.Error(unwrapFirestoreError(error, "observeTodayActivity")))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val breakRequestsState: StateFlow<UiState<List<BreakRequest>>> =
        repository.observeTeacherBreakRequests(currentUserId, teacherId)
            .map<List<BreakRequest>, UiState<List<BreakRequest>>> { UiState.Success(it) }
            .catch { error ->
                emit(UiState.Error(unwrapFirestoreError(error, "observeTeacherBreakRequests")))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val _banner = MutableStateFlow(TeacherActionBanner())
    val banner: StateFlow<TeacherActionBanner> = _banner.asStateFlow()

    fun saveClassSchedule(
        existingClassId: String?,
        subject: String,
        division: String,
        day: String,
        start: String,
        end: String,
        room: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _banner.value = TeacherActionBanner(isBusy = true)
            val res = repository.saveClassSchedule(
                existingClassId = existingClassId,
                teacherId = teacherId,
                teacherName = teacherName,
                subject = subject,
                division = division,
                day = day,
                start = start,
                end = end,
                room = room
            )
            if (res.isSuccess) {
                _banner.value = TeacherActionBanner(
                    success = if (existingClassId.isNullOrBlank()) "Class added to timetable." else "Class updated."
                )
                onSuccess()
            } else {
                _banner.value = TeacherActionBanner(
                    error = res.exceptionOrNull()?.localizedMessage ?: "Failed to save class."
                )
            }
        }
    }

    fun deleteClassSchedule(classId: String) {
        viewModelScope.launch {
            val res = repository.deleteClassSchedule(classId)
            _banner.value = if (res.isSuccess) {
                TeacherActionBanner(success = "Class removed from timetable.")
            } else {
                TeacherActionBanner(error = res.exceptionOrNull()?.localizedMessage ?: "Delete failed.")
            }
        }
    }

    fun reviewBreakRequest(request: BreakRequest, approve: Boolean) {
        viewModelScope.launch {
            val res = repository.reviewBreakRequest(
                requestId = request.id,
                approve = approve,
                usn = request.usn
            )
            _banner.value = if (res.isSuccess) {
                val code = FocusScheduleUtils.computeDailyBreakCode(request.usn)
                TeacherActionBanner(
                    success = if (approve) {
                        "Approved break for ${request.usn} (Code: $code)."
                    } else {
                        "Denied break request from ${request.usn}."
                    }
                )
            } else {
                TeacherActionBanner(error = res.exceptionOrNull()?.localizedMessage ?: "Review failed.")
            }
        }
    }

    fun startInstantSession(division: String = "A") {
        viewModelScope.launch {
            _banner.value = TeacherActionBanner(isBusy = true)
            val res = repository.startInstantLiveSession(
                teacherId = teacherId,
                teacherName = teacherName,
                division = division
            )
            _banner.value = if (res.isSuccess) {
                TeacherActionBanner(
                    success = "Started live lecture session for Division $division."
                )
            } else {
                TeacherActionBanner(
                    error = res.exceptionOrNull()?.localizedMessage ?: "Failed to start live session."
                )
            }
        }
    }

    fun clearBanner() {
        _banner.value = TeacherActionBanner()
    }

    private fun unwrapFirestoreError(error: Throwable, opName: String): String {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error in $opName", rootError)
        return rootError.message ?: "Failed to load data"
    }

    private companion object {
        const val TAG = "TeacherVM"
    }
}
