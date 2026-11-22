package com.example.ui.student

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studyshield.shared.auth.GoogleSignInButton
import com.studyshield.shared.model.ClassSchedule
import com.studyshield.shared.model.FocusScheduleUtils
import com.studyshield.shared.model.LecturePhase
import com.studyshield.shared.model.UiState
import com.studyshield.shared.theme.DeepSlate
import com.studyshield.shared.theme.EmeraldFocus
import com.studyshield.shared.theme.IndigoPrimary
import com.studyshield.shared.theme.JetBrainsMonoFontFamily
import com.studyshield.shared.theme.Slate400
import com.studyshield.shared.theme.Slate800

enum class LectureFilterMode {
    ALL_UPCOMING,
    TODAY_ONLY
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpcomingLecturesScreen(
    division: String,
    classesState: UiState<List<ClassSchedule>>,
    isFirestoreConnected: Boolean,
    webClientId: String,
    onGoogleAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filterMode by rememberSaveable { mutableStateOf(LectureFilterMode.ALL_UPCOMING) }
    var includeFinishedToday by rememberSaveable { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }

    val todayCode = remember { FocusScheduleUtils.currentDayCode() }
    val currentTime = remember(classesState) { FocusScheduleUtils.currentTimeHHmm() }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
        ) {
            when (classesState) {
                UiState.Loading -> {
                    UpcomingLecturesHeaderCard(
                        division = division,
                        todayCode = todayCode,
                        upcomingCount = 0,
                        liveCount = 0,
                        isFirestoreConnected = isFirestoreConnected,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Loading Division $division lectures...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is UiState.Error -> {
                    UpcomingLecturesHeaderCard(
                        division = division,
                        todayCode = todayCode,
                        upcomingCount = 0,
                        liveCount = 0,
                        isFirestoreConnected = isFirestoreConnected,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = "Error loading lectures",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = classesState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    val sortedAll = remember(classesState.data, todayCode, currentTime, includeFinishedToday) {
                        FocusScheduleUtils.sortUpcomingLectures(
                            classes = classesState.data,
                            todayDay = todayCode,
                            timeHHmm = currentTime,
                            includeFinishedToday = includeFinishedToday
                        )
                    }
                    val filteredLectures = remember(sortedAll, filterMode, todayCode) {
                        when (filterMode) {
                            LectureFilterMode.ALL_UPCOMING -> sortedAll
                            LectureFilterMode.TODAY_ONLY -> sortedAll.filter {
                                FocusScheduleUtils.normalizeDay(it.day) == todayCode
                            }
                        }
                    }
                    val liveCount = remember(sortedAll, todayCode, currentTime) {
                        sortedAll.count {
                            FocusScheduleUtils.lecturePhase(it, todayCode, currentTime) == LecturePhase.LIVE_NOW
                        }
                    }

                    UpcomingLecturesLazyColumn(
                        division = division,
                        todayCode = todayCode,
                        currentTime = currentTime,
                        lectures = filteredLectures,
                        upcomingCount = sortedAll.size,
                        liveCount = liveCount,
                        filterMode = filterMode,
                        includeFinishedToday = includeFinishedToday,
                        isFirestoreConnected = isFirestoreConnected,
                        webClientId = webClientId,
                        googleError = googleError,
                        onSelectFilter = { filterMode = it },
                        onToggleIncludePast = { includeFinishedToday = !includeFinishedToday },
                        onResetFilter = {
                            filterMode = LectureFilterMode.ALL_UPCOMING
                            includeFinishedToday = true
                        },
                        onGoogleAuthSuccess = {
                            googleError = null
                            onGoogleAuthSuccess()
                        },
                        onGoogleAuthError = { googleError = it }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpcomingLecturesLazyColumn(
    division: String,
    todayCode: String,
    currentTime: String,
    lectures: List<ClassSchedule>,
    upcomingCount: Int,
    liveCount: Int,
    filterMode: LectureFilterMode,
    includeFinishedToday: Boolean,
    isFirestoreConnected: Boolean,
    webClientId: String,
    googleError: String?,
    onSelectFilter: (LectureFilterMode) -> Unit,
    onToggleIncludePast: () -> Unit,
    onResetFilter: () -> Unit,
    onGoogleAuthSuccess: () -> Unit,
    onGoogleAuthError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val liveLectures = remember(lectures, todayCode, currentTime) {
        lectures.filter { FocusScheduleUtils.lecturePhase(it, todayCode, currentTime) == LecturePhase.LIVE_NOW }
    }
    val todayLectures = remember(lectures, todayCode, currentTime) {
        lectures.filter { FocusScheduleUtils.lecturePhase(it, todayCode, currentTime) == LecturePhase.UPCOMING_TODAY }
    }
    val weekLectures = remember(lectures, todayCode, currentTime) {
        lectures.filter { FocusScheduleUtils.lecturePhase(it, todayCode, currentTime) == LecturePhase.UPCOMING_LATER }
    }
    val finishedLectures = remember(lectures, todayCode, currentTime) {
        lectures.filter { FocusScheduleUtils.lecturePhase(it, todayCode, currentTime) == LecturePhase.FINISHED_TODAY }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("upcoming_lectures_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header_banner") {
            UpcomingLecturesHeaderCard(
                division = division,
                todayCode = todayCode,
                upcomingCount = upcomingCount,
                liveCount = liveCount,
                isFirestoreConnected = isFirestoreConnected
            )
        }

        if (!isFirestoreConnected) {
            item(key = "firestore_connect_card") {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Connect Google for Live Firestore Timetable",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = "Demo sessions show local classes immediately. Link your Google account to stream Division $division lectures from Firebase Firestore.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!googleError.isNullOrBlank()) {
                            Text(
                                text = googleError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        GoogleSignInButton(
                            webClientId = webClientId,
                            onAuthSuccess = onGoogleAuthSuccess,
                            onAuthError = onGoogleAuthError
                        )
                    }
                }
            }
        }

        item(key = "filter_chips") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filterMode == LectureFilterMode.ALL_UPCOMING,
                    onClick = { onSelectFilter(LectureFilterMode.ALL_UPCOMING) },
                    label = { Text("All Upcoming") },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("filter_upcoming_all")
                )
                FilterChip(
                    selected = filterMode == LectureFilterMode.TODAY_ONLY,
                    onClick = { onSelectFilter(LectureFilterMode.TODAY_ONLY) },
                    label = { Text("Today ($todayCode)") },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("filter_upcoming_today")
                )
                FilterChip(
                    selected = includeFinishedToday,
                    onClick = onToggleIncludePast,
                    label = { Text("Include past today") },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_past_today")
                )
            }
        }

        if (lectures.isEmpty()) {
            item(key = "empty_lectures_state") {
                EmptyLecturesCard(
                    division = division,
                    isTodayFiltered = filterMode == LectureFilterMode.TODAY_ONLY,
                    onResetFilter = onResetFilter
                )
            }
        } else {
            if (liveLectures.isNotEmpty()) {
                item(key = "section_live") {
                    SectionLabel(title = "Happening Now", count = liveLectures.size, accent = EmeraldFocus)
                }
                items(liveLectures, key = { "live_${it.id}" }) { lecture ->
                    UpcomingLectureCard(
                        schedule = lecture,
                        phase = LecturePhase.LIVE_NOW,
                        relativeLabel = FocusScheduleUtils.relativeStartLabel(lecture, todayCode, currentTime)
                    )
                }
            }

            if (todayLectures.isNotEmpty()) {
                item(key = "section_today") {
                    SectionLabel(title = "Later Today ($todayCode)", count = todayLectures.size, accent = IndigoPrimary)
                }
                items(todayLectures, key = { "today_${it.id}" }) { lecture ->
                    UpcomingLectureCard(
                        schedule = lecture,
                        phase = LecturePhase.UPCOMING_TODAY,
                        relativeLabel = FocusScheduleUtils.relativeStartLabel(lecture, todayCode, currentTime)
                    )
                }
            }

            if (weekLectures.isNotEmpty()) {
                item(key = "section_week") {
                    SectionLabel(
                        title = "Upcoming This Week",
                        count = weekLectures.size,
                        accent = MaterialTheme.colorScheme.primary
                    )
                }
                items(weekLectures, key = { "week_${it.id}" }) { lecture ->
                    UpcomingLectureCard(
                        schedule = lecture,
                        phase = LecturePhase.UPCOMING_LATER,
                        relativeLabel = FocusScheduleUtils.relativeStartLabel(lecture, todayCode, currentTime)
                    )
                }
            }

            if (finishedLectures.isNotEmpty()) {
                item(key = "section_finished") {
                    SectionLabel(
                        title = "Completed Today",
                        count = finishedLectures.size,
                        accent = Slate400
                    )
                }
                items(finishedLectures, key = { "past_${it.id}" }) { lecture ->
                    UpcomingLectureCard(
                        schedule = lecture,
                        phase = LecturePhase.FINISHED_TODAY,
                        relativeLabel = FocusScheduleUtils.relativeStartLabel(lecture, todayCode, currentTime)
                    )
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun UpcomingLecturesHeaderCard(
    division: String,
    todayCode: String,
    upcomingCount: Int,
    liveCount: Int,
    isFirestoreConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(listOf(DeepSlate, Slate800)),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isFirestoreConnected) EmeraldFocus.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isFirestoreConnected) Icons.Rounded.CloudDone else Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = if (isFirestoreConnected) Color(0xFF34D399) else Color(0xFFCBD5E1),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isFirestoreConnected) "FIRESTORE LIVE" else "LOCAL TIMETABLE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
                Text(
                    text = "DIV $division • $todayCode",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF94A3B8)
                )
            }

            Text(
                text = "Upcoming Lectures",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Text(
                text = when {
                    liveCount > 0 -> "$liveCount lecture active now • $upcomingCount total upcoming"
                    upcomingCount > 0 -> "$upcomingCount upcoming lecture${if (upcomingCount == 1) "" else "s"} scheduled for Division $division"
                    else -> "Class Mode activates automatically when a scheduled lecture begins"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1)
            )
        }
    }
}

@Composable
private fun SectionLabel(
    title: String,
    count: Int,
    accent: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accent, CircleShape)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UpcomingLectureCard(
    schedule: ClassSchedule,
    phase: LecturePhase,
    relativeLabel: String
) {
    var expanded by rememberSaveable(schedule.id) { mutableStateOf(false) }

    val accentColor = when (phase) {
        LecturePhase.LIVE_NOW -> EmeraldFocus
        LecturePhase.UPCOMING_TODAY -> IndigoPrimary
        LecturePhase.UPCOMING_LATER -> MaterialTheme.colorScheme.primary
        LecturePhase.FINISHED_TODAY -> Slate400
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (phase == LecturePhase.LIVE_NOW) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { expanded = !expanded }
            .testTag("upcoming_lecture_card_${schedule.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Monospace Time Column + Vertical Accent Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(accentColor)
                    )
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = FocusScheduleUtils.normalizeTime(schedule.start),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = FocusScheduleUtils.normalizeTime(schedule.end),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Main Lecture Details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = accentColor.copy(alpha = if (phase == LecturePhase.LIVE_NOW) 1f else 0.14f)
                        ) {
                            Text(
                                text = relativeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (phase == LecturePhase.LIVE_NOW) Color.White else accentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = schedule.subject,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = buildString {
                            append(schedule.teacherName.ifBlank { schedule.teacherId })
                            if (schedule.room.isNotBlank()) {
                                append(" • Room ${schedule.room}")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (phase == LecturePhase.LIVE_NOW) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = "Lecture active now",
                        tint = EmeraldFocus
                    )
                } else {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse lecture details" else "Expand lecture details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Faculty: ${schedule.teacherName.ifBlank { "Lecturer" }} (${schedule.teacherId})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Day ${FocusScheduleUtils.normalizeDay(schedule.day)} • Division ${schedule.division}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (schedule.room.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Classroom: ${schedule.room}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        Text(
                            text = "Class Mode automatically engages at ${FocusScheduleUtils.normalizeTime(schedule.start)}.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLecturesCard(
    division: String,
    isTodayFiltered: Boolean,
    onResetFilter: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = if (isTodayFiltered) {
                    "No more lectures left today"
                } else {
                    "No upcoming lectures for Division $division"
                },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Lectures published by your teachers in Firestore will stream here automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isTodayFiltered) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onResetFilter,
                    modifier = Modifier.testTag("show_all_upcoming_button")
                ) {
                    Text("Show Full Week")
                }
            }
        }
    }
}
