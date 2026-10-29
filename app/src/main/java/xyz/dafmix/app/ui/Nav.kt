package xyz.dafmix.app.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import xyz.dafmix.app.Svc

suspend fun doLogout() {
    try { Svc.api.logout() } catch (_: Exception) { }
    Svc.cookies.clear()
    Svc.prefs.clearSession()
}

private fun NavHostController.resetTo(route: String) {
    navigate(route) { popUpTo(graph.id) { inclusive = true } }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val start = remember {
        when {
            !Svc.prefs.loggedIn -> "welcome"
            Svc.prefs.profileId == 0 -> "profiles"
            else -> "main"
        }
    }
    val logout: () -> Unit = { scope.launch { doLogout(); nav.resetTo("welcome") } }

    // Davet bağlantısı (https://…/oda/KOD) ile açıldıysa doğrudan odaya git
    val pendingRoom by Deep.room.collectAsState()
    val ctx = LocalContext.current
    LaunchedEffect(pendingRoom) {
        val code = pendingRoom ?: return@LaunchedEffect
        Deep.room.value = null
        if (Svc.prefs.loggedIn && Svc.prefs.profileId != 0) nav.navigate("room/$code")
        else Toast.makeText(ctx, "Odaya katılmak için önce giriş yapıp profil seç", Toast.LENGTH_LONG).show()
    }

    NavHost(navController = nav, startDestination = start) {
        composable("welcome") {
            WelcomeScreen(onLogin = { nav.navigate("login") })
        }
        composable("login") {
            LoginScreen(onDone = { nav.resetTo("profiles") }, onBack = { nav.popBackStack() })
        }
        composable("profiles") {
            ProfilesScreen(onPicked = { nav.resetTo("main") }, onLogout = logout)
        }
        composable("main") {
            MainScreen(
                openTitle = { nav.navigate("detail/$it") },
                openPlayer = { nav.navigate("player") },
                changeProfile = { nav.navigate("profiles") },
                logout = logout,
                openBirlikte = { nav.navigate("birlikte") },
                openSeries = { nav.navigate("series/$it") },
            )
        }
        composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
            DetailScreen(
                id = entry.arguments?.getInt("id") ?: 0,
                back = { nav.popBackStack() },
                openPlayer = { nav.navigate("player") },
                openTitle = { nav.navigate("detail/$it") },
            )
        }
        composable("series/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
            SeriesScreen(id = entry.arguments?.getInt("id") ?: 0, back = { nav.popBackStack() }, openTitle = { nav.navigate("detail/$it") })
        }
        composable("birlikte") {
            BirlikteScreen(onBack = { nav.popBackStack() }, openRoom = { nav.navigate("room/$it") })
        }
        composable("room/{code}", arguments = listOf(navArgument("code") { type = NavType.StringType })) { entry ->
            RoomScreen(code = entry.arguments?.getString("code").orEmpty(), onExit = { nav.popBackStack() })
        }
        composable("player") {
            PlayerScreen(onExit = { nav.popBackStack() })
        }
    }
}

/** Dışarıdan (davet bağlantısı) gelen oda kodu */
object Deep {
    val room = MutableStateFlow<String?>(null)
}
