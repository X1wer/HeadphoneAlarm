package hu.ulpifex.fulesebreszto.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import hu.ulpifex.fulesebreszto.AppContainer
import hu.ulpifex.fulesebreszto.ui.edit.AlarmEditScreen
import hu.ulpifex.fulesebreszto.ui.list.AlarmListScreen
import hu.ulpifex.fulesebreszto.ui.settings.SettingsScreen
import hu.ulpifex.fulesebreszto.ui.sounds.SoundPickerScreen

private const val KEY_MESSAGE = "message"
private const val KEY_PICKED_REF = "picked_sound_ref"
private const val KEY_PICKED_NAME = "picked_sound_name"

@Composable
fun AppNavigation(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "alarms") {
        composable("alarms") { entry ->
            val message by entry.savedStateHandle.getStateFlow<String?>(KEY_MESSAGE, null).collectAsStateWithLifecycle()
            AlarmListScreen(
                container = container,
                incomingMessage = message,
                onMessageShown = { entry.savedStateHandle[KEY_MESSAGE] = null },
                onEdit = { id -> nav.navigate("edit/$id") },
                onNew = { nav.navigate("edit/0") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable(
            "edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val pickedRef by entry.savedStateHandle.getStateFlow<String?>(KEY_PICKED_REF, null).collectAsStateWithLifecycle()
            AlarmEditScreen(
                container = container,
                alarmId = id,
                pickedSound = pickedRef?.let { it to (entry.savedStateHandle.get<String>(KEY_PICKED_NAME) ?: "") },
                onPickedSoundConsumed = {
                    entry.savedStateHandle[KEY_PICKED_REF] = null
                    entry.savedStateHandle[KEY_PICKED_NAME] = null
                },
                onPickSound = { current -> nav.navigate("sounds?current=${Uri.encode(current)}") },
                onDone = { message ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(KEY_MESSAGE, message)
                    nav.popBackStack()
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "sounds?current={current}",
            arguments = listOf(navArgument("current") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { entry ->
            SoundPickerScreen(
                container = container,
                currentRef = entry.arguments?.getString("current"),
                onSelected = { ref, name ->
                    nav.previousBackStackEntry?.savedStateHandle?.apply {
                        set(KEY_PICKED_NAME, name)
                        set(KEY_PICKED_REF, ref)
                    }
                    nav.popBackStack()
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(container = container, onBack = { nav.popBackStack() })
        }
    }
}
