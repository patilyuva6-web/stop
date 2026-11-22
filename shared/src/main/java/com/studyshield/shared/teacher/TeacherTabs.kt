package com.studyshield.shared.teacher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.studyshield.shared.R
import com.studyshield.shared.model.BreakRequest
import com.studyshield.shared.model.BreakStatus
import com.studyshield.shared.model.ClassActivity
import com.studyshield.shared.model.ClassSchedule
import com.studyshield.shared.model.FocusScheduleUtils
import com.studyshield.shared.model.StudentRecord
import com.studyshield.shared.model.UiState
import com.studyshield.shared.theme.AmberBreak
import com.studyshield.shared.theme.CrimsonAlert
import com.studyshield.shared.theme.EmeraldFocus
import com.studyshield.shared.theme.JetBrainsMonoFontFamily

@Composable
fun TeacherDashboardTab(
    teacherId: String,
    teacherName: String,
    classesState: UiState<List<ClassSchedule>>,
    studentsState: UiState<List<StudentRecord>>,
    activityState: UiState<List<ClassActivity>>,
    breakRequestsState: UiState<List<BreakRequest>>,
    onStartLiveSession: () -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenLiveMonitor: () -> Unit
) {
    val classes = (classesState as? UiState.Success)?.data.orEmpty()
    val students = (studentsState as? UiState.Success)?.data.orEmpty()
    val activities = (activityState as? UiState.Success)?.data.orEmpty()
    val requests = (breakRequestsState as? UiState.Success)?.data.orEmpty()

    val focusedUsns = remember(students, activities) {
        buildSet {
            activities.filter { it.active }.forEach { add(it.usn) }
            students.filter { it.inClassMode }.forEach { add(it.usn) }
        }
    }
    val pendingBreaks = requests.count { BreakStatus.fromWire(it.status) == BreakStatus.PENDING }
    val activeNowClass = classes.firstOrNull { FocusScheduleUtils.isClassActiveNow(it) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_classroom_banner),
                    contentDescription = "TeacherFocus campus telemetry banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x330F172A), Color(0xDD0F172A))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "FACULTY ID $teacherId",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF5EEAD4)
                    )
                    Text(
                        text = "Welcome, $teacherName",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = if (activeNowClass != null) {
                            "Live now: ${activeNowClass.subject} (Div ${activeNowClass.division})"
                        } else {
                            "No lecture active right now • ${classes.size} classes scheduled"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    title = "In Class Mode",
                    value = "${focusedUsns.size}/${students.size}",
                    accent = EmeraldFocus,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Break Requests",
                    value = "$pendingBreaks",
                    accent = AmberBreak,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "My Classes",
                    value = "${classes.size}",
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Quick Classroom Actions",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onStartLiveSession,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("start_live_session_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Live Class")
                        }
                        FilledTonalButton(
                            onClick = onOpenTimetable,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_open_timetable_button")
                        ) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Timetable")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenLiveMonitor,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_open_live_button")
                    ) {
                        Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Live Monitoring & Break Requests ($pendingBreaks)")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = JetBrainsMonoFontFamily,
                color = accent
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TeacherStudentsTab(
    studentsState: UiState<List<StudentRecord>>,
    activityState: UiState<List<ClassActivity>>
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedDiv by remember { mutableStateOf("ALL") }

    val activities = (activityState as? UiState.Success)?.data.orEmpty()
    val activeTodayMap = remember(activities) {
        activities.associateBy { it.usn }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Search USN or student name") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("students_search_input")
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selectedDiv == "ALL",
                    onClick = { selectedDiv = "ALL" },
                    label = { Text("All") }
                )
            }
            items(FocusScheduleUtils.DIVISIONS) { div ->
                FilterChip(
                    selected = selectedDiv == div,
                    onClick = { selectedDiv = div },
                    label = { Text("Div $div") }
                )
            }
        }

        when (studentsState) {
            UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Text(studentsState.message, color = MaterialTheme.colorScheme.error)
            }
            is UiState.Success -> {
                val filtered = studentsState.data.filter { student ->
                    val matchesDiv = selectedDiv == "ALL" || student.division == selectedDiv
                    val matchesQuery = searchQuery.isBlank() ||
                        student.usn.contains(searchQuery, ignoreCase = true) ||
                        student.name.contains(searchQuery, ignoreCase = true)
                    matchesDiv && matchesQuery
                }

                if (filtered.isEmpty()) {
                    EmptyTabNotice(
                        icon = Icons.Rounded.Groups,
                        title = "No Students Found",
                        subtitle = "Students registered under students/ in Firebase blck-43179 will appear here."
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filtered, key = { it.usn }) { student ->
                            val todayAct = activeTodayMap[student.usn]
                            val inClassNow = (todayAct?.active == true) || student.inClassMode
                            val dailyCode = FocusScheduleUtils.computeDailyBreakCode(student.usn)

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("student_row_${student.usn}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(
                                                color = if (inClassNow) EmeraldFocus else CrimsonAlert,
                                                shape = CircleShape
                                            )
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = student.name,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "${student.usn} • Div ${student.division}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (inClassNow && (todayAct?.subject?.isNotBlank() == true || student.activeSubject.isNotBlank())) {
                                            Text(
                                                text = "Active: ${todayAct?.subject?.ifBlank { student.activeSubject } ?: student.activeSubject}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldFocus
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("CODE", style = MaterialTheme.typography.labelSmall)
                                            Text(
                                                text = dailyCode,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontFamily = JetBrainsMonoFontFamily
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TeacherTimetableTab(
    classesState: UiState<List<ClassSchedule>>,
    onAddClick: () -> Unit,
    onEditClick: (ClassSchedule) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("My Timetable", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Clash guard prevents teacher & division overlaps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("add_class_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add")
            }
        }

        when (classesState) {
            UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Text(classesState.message, color = MaterialTheme.colorScheme.error)
            }
            is UiState.Success -> {
                if (classesState.data.isEmpty()) {
                    EmptyTabNotice(
                        icon = Icons.Rounded.CalendarMonth,
                        title = "No Classes Added Yet",
                        subtitle = "Tap Add to schedule a lecture. Students in that division enter Class Mode automatically."
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(classesState.data, key = { it.id }) { cls ->
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("timetable_item_${cls.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${cls.day} • ${cls.start}–${cls.end} • Div ${cls.division}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = cls.subject,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        if (cls.room.isNotBlank()) {
                                            Text(
                                                text = "Room ${cls.room}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onEditClick(cls) },
                                        modifier = Modifier.testTag("edit_class_${cls.id}")
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit class")
                                    }
                                    IconButton(
                                        onClick = { onDeleteClick(cls.id) },
                                        modifier = Modifier.testTag("delete_class_${cls.id}")
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Delete class",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TeacherSessionsTab(
    classesState: UiState<List<ClassSchedule>>,
    activityState: UiState<List<ClassActivity>>,
    onStartInstantSession: () -> Unit
) {
    val classes = (classesState as? UiState.Success)?.data.orEmpty()
    val activities = (activityState as? UiState.Success)?.data.orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Class Sessions", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Live participation counts from today's activity logs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onStartInstantSession,
                        modifier = Modifier.testTag("start_instant_session_button")
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run Now")
                    }
                }
            }
        }

        if (classes.isEmpty()) {
            item {
                EmptyTabNotice(
                    icon = Icons.Rounded.PlayArrow,
                    title = "No Sessions Scheduled",
                    subtitle = "Add classes in Timetable or tap Run Now to trigger an instant Division A lecture."
                )
            }
        } else {
            items(classes, key = { it.id }) { cls ->
                val isLive = FocusScheduleUtils.isClassActiveNow(cls)
                val sessionActs = activities.filter { it.classId == cls.id }
                val activeCount = sessionActs.count { it.active }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${cls.day} ${cls.start}–${cls.end} • Div ${cls.division}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isLive) EmeraldFocus else MaterialTheme.colorScheme.primary
                            )
                            Text(cls.subject, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "$activeCount students focused today",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isLive) EmeraldFocus.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (isLive) "ACTIVE" else "SCHEDULED",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isLive) EmeraldFocus else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TeacherLiveMonitorTab(
    activityState: UiState<List<ClassActivity>>,
    breakRequestsState: UiState<List<BreakRequest>>,
    onApproveBreak: (BreakRequest) -> Unit,
    onDenyBreak: (BreakRequest) -> Unit
) {
    val activities = (activityState as? UiState.Success)?.data.orEmpty()
    val requests = (breakRequestsState as? UiState.Success)?.data.orEmpty()
    val pending = requests.filter { BreakStatus.fromWire(it.status) == BreakStatus.PENDING }
    val reviewed = requests.filter { BreakStatus.fromWire(it.status) != BreakStatus.PENDING }
    val liveFocused = activities.filter { it.active || it.breakActive }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Break Requests (${pending.size} pending)",
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (pending.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Coffee,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "No pending break requests right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(pending, key = { it.id }) { req ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("break_request_card_${req.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${req.studentName} (${req.usn})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${req.subject} • Div ${req.division}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = AmberBreak.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "10 MIN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberBreak,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "Reason: ${req.reason}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onDenyBreak(req) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("deny_break_${req.id}")
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Deny")
                            }
                            Button(
                                onClick = { onApproveBreak(req) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("approve_break_${req.id}")
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Approve Code")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Live Class Mode Telemetry (${liveFocused.size})",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (liveFocused.isEmpty()) {
            item {
                EmptyTabNotice(
                    icon = Icons.Rounded.Radar,
                    title = "No Active Student Telemetry Today",
                    subtitle = "Students entering Class Mode write live heartbeats to activity/ in real time."
                )
            }
        } else {
            items(liveFocused, key = { it.id }) { act ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (act.breakActive) AmberBreak else EmeraldFocus,
                                    shape = CircleShape
                                )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(act.studentName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${act.usn} • Div ${act.division} • ${act.subject}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = (if (act.breakActive) AmberBreak else EmeraldFocus).copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = if (act.breakActive) "BREAK" else "FOCUSED",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (act.breakActive) AmberBreak else EmeraldFocus,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        if (reviewed.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Reviewed Breaks",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(reviewed.take(5), key = { it.id }) { req ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${req.usn} • ${req.status}", style = MaterialTheme.typography.labelLarge)
                        if (req.breakCode.isNotBlank()) {
                            Text(
                                text = "Code ${req.breakCode}",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetBrainsMonoFontFamily
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTabNotice(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
