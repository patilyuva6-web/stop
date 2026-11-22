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
import com.example.service.FocusMonitorService
import com.example.ui.student.StudentHomeScreen
import com.example.ui.student.StudentLoginScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.studyshield.shared.auth.attemptAutoSignIn
import com.studyshield.shared.auth.signOutUser
import com.studyshield.shared.model.StudentSession
import com.studyshield.shared.repository.FocusGuardRepository
import com.studyshield.shared.theme.FocusGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusGuardTheme {
                StudyShieldStudentApp()
            }
        }
    }
}

@Composable
fun StudyShieldStudentApp() {
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

    var loggedUsn by rememberSaveable { mutableStateOf<String?>(null) }
    var loggedName by rememberSaveable { mutableStateOf("") }
    var loggedDiv by rememberSaveable { mutableStateOf("A") }

    val session: StudentSession? = loggedUsn?.let { StudentSession(it, loggedName, loggedDiv) }
    val activeUserId = currentUser?.uid ?: "rtdb_session"

    if (session == null) {
        StudentLoginScreen(
            webClientId = webClientId,
            googleEmail = currentUser?.email.orEmpty(),
            onVerifyStudent = { usn, password, division ->
                repository.verifyAndLoginStudent(usn, password, division)
            },
            onAuthenticated = { verified ->
                loggedUsn = verified.usn
                loggedName = verified.name
                loggedDiv = verified.division
            },
            onGoogleAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            },
            onSignOutGoogle = {
                FocusMonitorService.stop(context)
                signOutUser(credentialManager, scope)
            },
            onOpenTeacherApp = {
                context.startActivity(
                    Intent(context, TeacherEntryActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        )
    } else {
        StudentHomeScreen(
            currentUserId = activeUserId,
            usn = session.usn,
            studentName = session.name,
            division = session.division,
            repository = repository,
            webClientId = webClientId,
            isFirestoreConnected = currentUser != null,
            onGoogleAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            },
            onSignOutStudent = {
                FocusMonitorService.stop(context)
                loggedUsn = null
            }
        )
    }
}
