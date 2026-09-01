package sk.planx4.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import sk.planx4.app.PlanX4Application
import sk.planx4.app.ble.DistoBleManager
import sk.planx4.app.export.ExportHelper
import sk.planx4.app.ui.device.DeviceConnectScreen
import sk.planx4.app.ui.device.DeviceConnectViewModel
import sk.planx4.app.ui.editor.FloorPlanEditorScreen
import sk.planx4.app.ui.editor.FloorPlanEditorViewModel
import sk.planx4.app.ui.projectlist.ProjectListScreen
import sk.planx4.app.ui.projectlist.ProjectListViewModel
import sk.planx4.app.ui.projectlist.RoomListScreen
import sk.planx4.app.ui.projectlist.RoomListViewModel
import sk.planx4.core.model.RoomPlan

private object Routes {
    const val PROJECTS = "projects"
    const val ROOMS = "rooms/{projectId}"
    const val EDITOR = "editor/{projectId}/{roomId}"
    const val DEVICE = "device"
    fun rooms(projectId: String) = "rooms/$projectId"
    fun editor(projectId: String, roomId: String) = "editor/$projectId/$roomId"
}

@Composable
fun PlanX4NavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as PlanX4Application
    // One BLE manager for the whole app session — a room's editor and the device screen
    // both observe the same connection, so a device paired once stays connected while you
    // move between rooms.
    val bleManager = remember { DistoBleManager(app) }

    NavHost(navController = navController, startDestination = Routes.PROJECTS) {
        composable(Routes.PROJECTS) {
            val vm: ProjectListViewModel = viewModel(
                factory = viewModelFactory { initializer { ProjectListViewModel(app.projectRepository) } }
            )
            ProjectListScreen(viewModel = vm, onOpenProject = { navController.navigate(Routes.rooms(it)) })
        }

        composable(Routes.ROOMS) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val vm: RoomListViewModel = viewModel(
                key = "rooms-$projectId",
                factory = viewModelFactory { initializer { RoomListViewModel(projectId, app.projectRepository) } }
            )
            val project by vm.project.collectAsState()
            RoomListScreen(
                viewModel = vm,
                onOpenRoom = { roomId -> navController.navigate(Routes.editor(projectId, roomId)) },
                onExport = { project?.let { ExportHelper.exportAndShare(context, it) } }
            )
        }

        composable(Routes.EDITOR) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable

            val roomVm: RoomListViewModel = viewModel(
                key = "rooms-$projectId",
                factory = viewModelFactory { initializer { RoomListViewModel(projectId, app.projectRepository) } }
            )
            val project by roomVm.project.collectAsState()
            val room = project?.rooms?.firstOrNull { it.id == roomId } ?: RoomPlan(id = roomId, name = "Miestnosť")

            val editorVm: FloorPlanEditorViewModel = viewModel(
                key = "editor-$roomId",
                factory = viewModelFactory {
                    initializer { FloorPlanEditorViewModel(projectId, room, app.projectRepository, bleManager) }
                }
            )
            FloorPlanEditorScreen(
                viewModel = editorVm,
                roomName = room.name,
                onOpenDeviceScreen = { navController.navigate(Routes.DEVICE) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DEVICE) {
            val vm: DeviceConnectViewModel = viewModel(
                factory = viewModelFactory { initializer { DeviceConnectViewModel(bleManager) } }
            )
            DeviceConnectScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
