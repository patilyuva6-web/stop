package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.studyshield.shared.auth.attemptAutoSignIn
import com.studyshield.shared.auth.signOutUser
import com.studyshield.shared.model.TeacherSession
import com.studyshield.shared.repository.FocusGuardRepository
import com.studyshield.shared.teacher.TeacherHomeScreen
import com.studyshield.shared.teacher.TeacherLoginScreen
import com.studyshield.shared.theme.FocusGuardTheme

/**
 * Secondary launcher entry point inside :app so both StudyShield (Student)
 * and TeacherFocus (Teacher) can be launched independently on the Streaming Emulator.
 */
class TeacherEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusGuardTheme {
                TeacherFocusPortalApp()
            }
        }
    }
}

@Composable
private fun TeacherFocusPortalApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val webClientId = remember(context) { context.getString(R.string.default_web_client_id) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(webClientId) {
        attemptAutoSignIn(
            context = context,
            webClientId = webClientId,
            credentialManager = credentialManager,
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
            onUnauthenticated = {},
            scope = scope
        )
    }

    val repository = remember {
        val db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
        FocusGuardRepository(db)
    }

    var loggedTeacherId by rememberSaveable { mutableStateOf<String?>(null) }
    var loggedTeacherName by rememberSaveable { mutableStateOf("") }

    val session: TeacherSession? = loggedTeacherId?.let { TeacherSession(it, loggedTeacherName) }
    val activeUserId = currentUser?.uid ?: "rtdb_session"

    if (session == null) {
        TeacherLoginScreen(
            webClientId = webClientId,
            googleEmail = currentUser?.email.orEmpty(),
            onVerifyTeacher = { tid, password ->
                repository.verifyAndLoginTeacher(tid, password)
            },
            onAuthenticated = { verified ->
                loggedTeacherId = verified.teacherId
                loggedTeacherName = verified.name
            },
            onGoogleAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            },
            onSignOutGoogle = {
                signOutUser(credentialManager, scope)
            },
            onOpenStudentApp = {
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        )
    } else {
        TeacherHomeScreen(
            currentUserId = activeUserId,
            teacherId = session.teacherId,
            teacherName = session.name,
            repository = repository,
            onSignOutTeacher = {
                loggedTeacherId = null
            }
        )
    }
}
