package com.example.ui.student

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.FocusMonitorService
import com.example.service.FocusPermissionHelper
import com.studyshield.shared.model.BreakRequest
import com.studyshield.shared.model.BreakStatus
import com.studyshield.shared.model.ClassSchedule
import com.studyshield.shared.model.FocusScheduleUtils
import com.studyshield.shared.model.UiState
import com.studyshield.shared.repository.FocusGuardRepository
import com.studyshield.shared.theme.AmberBreak
import com.studyshield.shared.theme.DeepSlate
import com.studyshield.shared.theme.EmeraldFocus
import com.studyshield.shared.theme.EmeraldVibrant
import com.studyshield.shared.theme.JetBrainsMonoFontFamily
import com.studyshield.shared.theme.Slate800
import com.studyshield.shared.ui.SystemLimitsSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudentHomeScreen(
    currentUserId: String,
    usn: String,
    studentName: String,
    division: String,
    repository: FocusGuardRepository,
    onSignOutStudent: () -> Unit
) {
    val viewModel: StudentViewModel = viewModel(
        key = "${currentUserId}_$usn",
        factory = androidx.lifecycle.viewmodel.viewModelFactory {
            addInitializer(StudentViewModel::class) {
                StudentViewModel(repository, currentUserId, usn, studentName, division)
            }
        }
    )

    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val runtime by viewModel.runtime.collectAsStateWithLifecycle()
    val classesState by viewModel.divisionClassesState.collectAsStateWithLifecycle()
    val breakRequestsState by viewModel.breakRequestsState.collectAsStateWithLifecycle()

    var hasUsageAccess by remember { mutableStateOf(FocusPermissionHelper.hasUsageStatsPermission(context)) }
    var hasOverlay by remember { mutableStateOf(FocusPermissionHelper.hasOverlayPermission(context)) }
    var isPinned by remember { mutableStateOf(FocusPermissionHelper.isInLockTaskMode(context)) }
    var autoPinEnabled by remember { mutableStateOf(false) }

    var breakCodeInput by remember { mutableStateOf("") }
    var breakReasonInput by remember { mutableStateOf("") }
    var showLimitsSheet by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = FocusPermissionHelper.hasUsageStatsPermission(context)
                hasOverlay = FocusPermissionHelper.hasOverlayPermission(context)
                isPinned = FocusPermissionHelper.isInLockTaskMode(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(runtime.inClassMode, hasUsageAccess, hasOverlay, autoPinEnabled) {
        if (runtime.inClassMode) {
            if (hasUsageAccess && hasOverlay) {
                FocusMonitorService.start(
                    context = context,
                    subject = runtime.activeClass?.subject ?: "Lecture Session"
                )
            }
            if (autoPinEnabled && !FocusPermissionHelper.isInLockTaskMode(context)) {
                isPinned = FocusPermissionHelper.setScreenPinning(activity, true)
            }
        } else {
            FocusMonitorService.stop(context)
            if (FocusPermissionHelper.isInLockTaskMode(context)) {
                FocusPermissionHelper.setScreenPinning(activity, false)
                isPinned = false
            }
        }
    }

    BackHandler {
        onSignOutStudent()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "StudyShield",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "$studentName • $usn • Div $division",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showLimitsSheet = true },
                        modifier = Modifier.testTag("student_limits_button")
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = "Platform Limits")
                    }
                    IconButton(
                        onClick = onSignOutStudent,
                        modifier = Modifier.testTag("student_logout_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Log out Student")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    AnimatedVisibility(visible = runtime.actionError != null || runtime.actionSuccess != null) {
                        val isError = runtime.actionError != null
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = runtime.actionError ?: runtime.actionSuccess ?: "",
                                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = viewModel::clearBanner) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }

                item {
                    ClassModeHeroCard(
                        runtime = runtime,
                        onToggleManual = viewModel::toggleManualClassMode
                    )
                }

                item {
                    DeviceShieldCard(
                        hasUsageAccess = hasUsageAccess,
                        hasOverlay = hasOverlay,
                        isPinned = isPinned,
                        autoPinEnabled = autoPinEnabled,
                        onGrantUsage = { FocusPermissionHelper.openUsageAccessSettings(context) },
                        onGrantOverlay = { FocusPermissionHelper.openOverlaySettings(context) },
                        onTogglePinNow = {
                            val next = !isPinned
                            FocusPermissionHelper.setScreenPinning(activity, next)
                            isPinned = FocusPermissionHelper.isInLockTaskMode(context)
                        },
                        onToggleAutoPin = { autoPinEnabled = it }
                    )
                }

                item {
                    BreakPassCard(
                        breakCodeInput = breakCodeInput,
                        onBreakCodeChange = { if (it.length <= 4) breakCodeInput = it.filter(Char::isDigit) },
                        onRedeemCode = {
                            viewModel.redeemBreakCode(breakCodeInput)
                            breakCodeInput = ""
                        },
                        breakReasonInput = breakReasonInput,
                        onBreakReasonChange = { breakReasonInput = it },
                        onSendBreakRequest = {
                            viewModel.submitBreakRequest(breakReasonInput)
                            breakReasonInput = ""
                        },
                        breakRequestsState = breakRequestsState,
                        onApplyApprovedCode = { code ->
                            viewModel.redeemBreakCode(code)
                        }
                    )
                }

                item {
                    Text(
                        text = "Division $division Timetable (${FocusScheduleUtils.currentDayCode()})",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                when (val state = classesState) {
                    UiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is UiState.Error -> {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Text(
                                            text = "No scheduled classes for Division $division yet",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Classes added by your teacher in TeacherFocus will automatically activate Class Mode here.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(state.data, key = { it.id }) { schedule ->
                                TimetableSlotCard(schedule = schedule)
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    if (showLimitsSheet) {
        SystemLimitsSheet(onDismiss = { showLimitsSheet = false })
    }
}

@Composable
private fun ClassModeHeroCard(
    runtime: StudentRuntimeState,
    onToggleManual: () -> Unit
) {
    val isBreak = runtime.breakCountdownSeconds > 0L
    val isOn = runtime.inClassMode
    val infiniteTransition = rememberInfiniteTransition(label = "shield_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isOn) 1.07f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val gradient = when {
        isBreak -> Brush.linearGradient(listOf(Color(0xFF78350F), Color(0xFFB45309)))
        isOn -> Brush.linearGradient(listOf(DeepSlate, Color(0xFF065F46)))
        else -> Brush.linearGradient(listOf(DeepSlate, Slate800))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient, RoundedCornerShape(24.dp))
            .padding(22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = when {
                        isBreak -> AmberBreak.copy(alpha = 0.25f)
                        isOn -> EmeraldVibrant.copy(alpha = 0.22f)
                        else -> Color.White.copy(alpha = 0.12f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = when {
                                        isBreak -> Color(0xFFFBBF24)
                                        isOn -> Color(0xFF34D399)
                                        else -> Color(0xFF94A3B8)
                                    },
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = when {
                                isBreak -> "10-MIN TEACHER BREAK"
                                isOn && runtime.autoTriggered -> "AUTO CLASS MODE ON"
                                isOn -> "CLASS MODE ON"
                                else -> "CLASS MODE OFF"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                Icon(
                    imageVector = if (isBreak) Icons.Rounded.Coffee else Icons.Rounded.Shield,
                    contentDescription = "Focus Shield Status",
                    tint = if (isOn) Color(0xFF34D399) else Color(0xFF94A3B8),
                    modifier = Modifier
                        .size(30.dp)
                        .scale(pulseScale)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = when {
                        isBreak -> FocusScheduleUtils.formatCountdown(runtime.breakCountdownSeconds)
                        isOn -> FocusScheduleUtils.formatCountdown(runtime.classCountdownSeconds)
                        else -> "00:00"
                    },
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontFamily = JetBrainsMonoFontFamily
                )
                Text(
                    text = when {
                        isBreak -> "Break active — Class Mode resumes automatically when timer ends"
                        runtime.activeClass != null -> "${runtime.activeClass.subject} • ${runtime.activeClass.teacherName} (${runtime.activeClass.start}–${runtime.activeClass.end})"
                        isOn -> "Manual focus session active • Safe list enabled"
                        else -> "Waiting for next scheduled lecture or manual focus start"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCBD5E1)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = runtime.syncStatusMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onToggleManual,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOn) Color.White.copy(alpha = 0.18f) else EmeraldFocus,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("toggle_class_mode_button")
                ) {
                    Text(if (isOn) "Turn Off" else "Start Class Mode")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceShieldCard(
    hasUsageAccess: Boolean,
    hasOverlay: Boolean,
    isPinned: Boolean,
    autoPinEnabled: Boolean,
    onGrantUsage: () -> Unit,
    onGrantOverlay: () -> Unit,
    onTogglePinNow: () -> Unit,
    onToggleAutoPin: (Boolean) -> Unit
) {
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
                text = "App Blocking & Screen Pinning",
                style = MaterialTheme.typography.titleLarge
            )

            PermissionStatusRow(
                icon = Icons.Rounded.AccessTime,
                title = "Usage Access",
                granted = hasUsageAccess,
                actionLabel = "Grant",
                onAction = onGrantUsage,
                testTag = "grant_usage_access_button"
            )

            PermissionStatusRow(
                icon = Icons.Rounded.Layers,
                title = "Display Over Other Apps",
                granted = hasOverlay,
                actionLabel = "Grant",
                onAction = onGrantOverlay,
                testTag = "grant_overlay_button"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text("Auto Screen Pinning", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (isPinned) "Lock task mode active" else "Lock screen during Class Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onTogglePinNow,
                        modifier = Modifier.testTag("pin_now_button")
                    ) {
                        Text(if (isPinned) "Unpin" else "Pin Now")
                    }
                    Switch(
                        checked = autoPinEnabled,
                        onCheckedChange = onToggleAutoPin,
                        modifier = Modifier.testTag("auto_pin_switch")
                    )
                }
            }

            Text(
                text = "Safe-listed during Class Mode:",
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("FocusGuard", "WhatsApp", "Phone", "Messages", "Settings", "Launcher").forEach { app ->
                    AssistChip(
                        onClick = {},
                        label = { Text(app, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = EmeraldFocus
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = if (granted) EmeraldFocus else AmberBreak
            )
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (granted) "Granted & ready" else "Required for overlay blocking",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!granted) {
            FilledTonalButton(
                onClick = onAction,
                modifier = Modifier.testTag(testTag)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun BreakPassCard(
    breakCodeInput: String,
    onBreakCodeChange: (String) -> Unit,
    onRedeemCode: () -> Unit,
    breakReasonInput: String,
    onBreakReasonChange: (String) -> Unit,
    onSendBreakRequest: () -> Unit,
    breakRequestsState: UiState<List<BreakRequest>>,
    onApplyApprovedCode: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "10-Minute Teacher Break",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = breakCodeInput,
                    onValueChange = onBreakCodeChange,
                    label = { Text("4-digit code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("break_code_input")
                )
                Button(
                    onClick = onRedeemCode,
                    enabled = breakCodeInput.length == 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("redeem_break_code_button")
                ) {
                    Text("Unlock")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = breakReasonInput,
                    onValueChange = onBreakReasonChange,
                    label = { Text("Request break reason") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("break_reason_input")
                )
                OutlinedButton(
                    onClick = onSendBreakRequest,
                    enabled = breakReasonInput.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("send_break_request_button")
                ) {
                    Text("Request")
                }
            }

            val recentRequests = (breakRequestsState as? UiState.Success)?.data.orEmpty().take(2)
            recentRequests.forEach { req ->
                val status = BreakStatus.fromWire(req.status)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${req.subject} • ${status.wireValue}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = req.reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (status == BreakStatus.APPROVED && req.breakCode.isNotBlank()) {
                            FilledTonalButton(
                                onClick = { onApplyApprovedCode(req.breakCode) },
                                modifier = Modifier.testTag("apply_approved_code_${req.id}")
                            ) {
                                Text("Use ${req.breakCode}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimetableSlotCard(schedule: ClassSchedule) {
    val activeNow = FocusScheduleUtils.isClassActiveNow(schedule)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (activeNow) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${schedule.day} ${schedule.start}–${schedule.end}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (activeNow) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = EmeraldFocus
                        ) {
                            Text(
                                text = "LIVE NOW",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(text = schedule.subject, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${schedule.teacherName} (${schedule.teacherId})" +
                        if (schedule.room.isNotBlank()) " • ${schedule.room}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (activeNow) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = "Active lecture",
                    tint = EmeraldFocus
                )
            }
        }
    }
}
