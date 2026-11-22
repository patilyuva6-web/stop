package com.studyshield.shared.teacher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.studyshield.shared.model.ClassSchedule
import com.studyshield.shared.model.FocusScheduleUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassEditorDialog(
    initial: ClassSchedule?,
    isBusy: Boolean,
    errorText: String?,
    onDismiss: () -> Unit,
    onSave: (subject: String, division: String, day: String, start: String, end: String, room: String) -> Unit
) {
    var subject by remember(initial) { mutableStateOf(initial?.subject ?: "") }
    var division by remember(initial) { mutableStateOf(initial?.division ?: "A") }
    var day by remember(initial) { mutableStateOf(initial?.day ?: FocusScheduleUtils.currentDayCode()) }
    var start by remember(initial) { mutableStateOf(initial?.start ?: "09:00") }
    var end by remember(initial) { mutableStateOf(initial?.end ?: "10:00") }
    var room by remember(initial) { mutableStateOf(initial?.room ?: "LH-201") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "Add Timetable Class" else "Edit Class",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("class_subject_input")
                )

                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Room / Hall") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("class_room_input")
                )

                Text("Division", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FocusScheduleUtils.DIVISIONS.forEach { div ->
                        FilterChip(
                            selected = division == div,
                            onClick = { division = div },
                            label = { Text(div) },
                            modifier = Modifier.testTag("class_div_${div}_chip")
                        )
                    }
                }

                Text("Day", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FocusScheduleUtils.DAYS.forEach { d ->
                        FilterChip(
                            selected = day == d,
                            onClick = { day = d },
                            label = { Text(d) },
                            modifier = Modifier.testTag("class_day_${d}_chip")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it },
                        label = { Text("Start (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("class_start_input")
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it },
                        label = { Text("End (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("class_end_input")
                    )
                }

                val combinedError = localError ?: errorText
                if (!combinedError.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = combinedError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = FocusScheduleUtils.parseMinutes(start)
                    val e = FocusScheduleUtils.parseMinutes(end)
                    if (subject.isBlank()) {
                        localError = "Enter a subject name."
                        return@Button
                    }
                    if (s == null || e == null) {
                        localError = "Use 24-hour HH:mm format (e.g. 09:00)."
                        return@Button
                    }
                    if (s >= e) {
                        localError = "End time must be after start time."
                        return@Button
                    }
                    localError = null
                    onSave(subject, division, day, start.trim(), end.trim(), room)
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("save_class_button")
            ) {
                Text(if (isBusy) "Checking..." else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
