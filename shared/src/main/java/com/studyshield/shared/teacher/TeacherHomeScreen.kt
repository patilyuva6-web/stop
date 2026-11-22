package com.studyshield.shared.teacher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyshield.shared.model.BreakStatus
import com.studyshield.shared.model.ClassSchedule
import com.studyshield.shared.model.UiState
import com.studyshield.shared.repository.FocusGuardRepository
import com.studyshield.shared.ui.SystemLimitsSheet

private enum class TeacherTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("Home", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    STUDENTS("Students", Icons.Rounded.Groups, Icons.Outlined.Groups),
    TIMETABLE("Timetable", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    SESSIONS("Sessions", Icons.Rounded.PlayCircle, Icons.Outlined.PlayCircle),
    LIVE("Live", Icons.Rounded.Radar, Icons.Outlined.Radar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherHomeScreen(
    currentUserId: String,
    teacherId: String,
    teacherName: String,
    repository: FocusGuardRepository,
    onSignOutTeacher: () -> Unit
) {
    val viewModel: TeacherViewModel = viewModel(
        key = "${currentUserId}_$teacherId",
        factory = androidx.lifecycle.viewmodel.viewModelFactory {
            addInitializer(TeacherViewModel::class) {
                TeacherViewModel(repository, currentUserId, teacherId, teacherName)
            }
        }
    )

    val classesState by viewModel.myClassesState.collectAsStateWithLifecycle()
    val studentsState by viewModel.studentsState.collectAsStateWithLifecycle()
    val activityState by viewModel.todayActivityState.collectAsStateWithLifecycle()
    val breakRequestsState by viewModel.breakRequestsState.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(TeacherTab.DASHBOARD) }
    var editingClass by remember { mutableStateOf<ClassSchedule?>(null) }
    var showAddClassDialog by remember { mutableStateOf(false) }
    var showLimitsSheet by remember { mutableStateOf(false) }

    val pendingCount = (breakRequestsState as? UiState.Success)?.data
        ?.count { BreakStatus.fromWire(it.status) == BreakStatus.PENDING } ?: 0

    BackHandler {
        if (currentTab != TeacherTab.DASHBOARD) {
            currentTab = TeacherTab.DASHBOARD
        } else {
            onSignOutTeacher()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("TeacherFocus", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "$teacherName ($teacherId)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showLimitsSheet = true },
                            modifier = Modifier.testTag("teacher_limits_button")
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = "System Limits")
                        }
                        IconButton(
                            onClick = onSignOutTeacher,
                            modifier = Modifier.testTag("teacher_logout_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Log out Teacher")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        TeacherTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    if (tab == TeacherTab.LIVE && pendingCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$pendingCount") } }) {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    }
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("teacher_nav_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        TeacherTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    if (tab == TeacherTab.LIVE && pendingCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$pendingCount") } }) {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    }
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("teacher_rail_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxSize()
                    ) {
                        AnimatedVisibility(visible = banner.error != null || banner.success != null) {
                            val isErr = banner.error != null
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isErr) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = banner.error ?: banner.success ?: "",
                                        color = if (isErr) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = viewModel::clearBanner) {
                                        Text("Dismiss")
                                    }
                                }
                            }
                        }

                        when (currentTab) {
                            TeacherTab.DASHBOARD -> TeacherDashboardTab(
                                teacherId = teacherId,
                                teacherName = teacherName,
                                classesState = classesState,
                                studentsState = studentsState,
                                activityState = activityState,
                                breakRequestsState = breakRequestsState,
                                onStartLiveSession = { viewModel.startInstantSession("A") },
                                onOpenTimetable = { currentTab = TeacherTab.TIMETABLE },
                                onOpenLiveMonitor = { currentTab = TeacherTab.LIVE }
                            )
                            TeacherTab.STUDENTS -> TeacherStudentsTab(
                                studentsState = studentsState,
                                activityState = activityState
                            )
                            TeacherTab.TIMETABLE -> TeacherTimetableTab(
                                classesState = classesState,
                                onAddClick = {
                                    viewModel.clearBanner()
                                    editingClass = null
                                    showAddClassDialog = true
                                },
                                onEditClick = { cls ->
                                    viewModel.clearBanner()
                                    editingClass = cls
                                    showAddClassDialog = true
                                },
                                onDeleteClick = { classId ->
                                    viewModel.deleteClassSchedule(classId)
                                }
                            )
                            TeacherTab.SESSIONS -> TeacherSessionsTab(
                                classesState = classesState,
                                activityState = activityState,
                                onStartInstantSession = { viewModel.startInstantSession("A") }
                            )
                            TeacherTab.LIVE -> TeacherLiveMonitorTab(
                                activityState = activityState,
                                breakRequestsState = breakRequestsState,
                                onApproveBreak = { req -> viewModel.reviewBreakRequest(req, true) },
                                onDenyBreak = { req -> viewModel.reviewBreakRequest(req, false) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddClassDialog) {
        ClassEditorDialog(
            initial = editingClass,
            isBusy = banner.isBusy,
            errorText = banner.error,
            onDismiss = {
                showAddClassDialog = false
                editingClass = null
                viewModel.clearBanner()
            },
            onSave = { subject, division, day, start, end, room ->
                viewModel.saveClassSchedule(
                    existingClassId = editingClass?.id,
                    subject = subject,
                    division = division,
                    day = day,
                    start = start,
                    end = end,
                    room = room,
                    onSuccess = {
                        showAddClassDialog = false
                        editingClass = null
                    }
                )
            }
        )
    }

    if (showLimitsSheet) {
        SystemLimitsSheet(onDismiss = { showLimitsSheet = false })
    }
}
