package eben.ezer.lecteurmultimedias

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import eben.ezer.lecteurmultimedias.ui.theme.LecteurMultimediasTheme

class MainActivity : ComponentActivity() {

    private val dbHelper by lazy { DatabaseHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Créer un super-admin par défaut
        createDefaultSuperAdmin()

        setContent {
            LecteurMultimediasTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MediaUserApp(dbHelper = dbHelper)
                }
            }
        }
    }

    private fun createDefaultSuperAdmin() {
        if (!dbHelper.userExists("admin")) {
            dbHelper.createUser("admin", "admin123", "super-admin")
        }
    }
}

@Composable
fun MediaUserApp(dbHelper: DatabaseHelper) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Login) }
    var currentUser by remember { mutableStateOf<User?>(null) }

    when (currentScreen) {
        is Screen.Login -> {
            LoginScreen(
                dbHelper = dbHelper,
                onLoginSuccess = { user ->
                    currentUser = user
                    currentScreen = when (user.role) {
                        "super-admin" -> Screen.AdminDashboard
                        "user" -> Screen.MediaPlayer
                        else -> Screen.Login
                    }
                }
            )
        }

        is Screen.AdminDashboard -> {
            AdminDashboardScreen(
                dbHelper = dbHelper,
                currentUser = currentUser,
                onLogout = {
                    currentUser = null
                    currentScreen = Screen.Login
                }
            )
        }

        is Screen.MediaPlayer -> {
            MediaPlayerScreen(
                currentUser = currentUser,
                onLogout = {
                    currentUser = null
                    currentScreen = Screen.Login
                }
            )
        }
    }
}

sealed class Screen {
    object Login : Screen()
    object AdminDashboard : Screen()
    object MediaPlayer : Screen()
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    LecteurMultimediasTheme {
    }
}