package com.example.transport_app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.transport_app.ui.departures.BusDetailScreen
import com.example.transport_app.ui.departures.DeparturesScreen
import com.example.transport_app.ui.departures.ServiceDetailScreen
import com.example.transport_app.ui.groups.CreateEditGroupScreen
import com.example.transport_app.ui.groups.GroupsListScreen
import java.net.URLEncoder

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "groups") {

        composable("groups") {
            GroupsListScreen(
                onGroupClick = { groupId -> navController.navigate("departures/$groupId") },
                onCreateGroup = { navController.navigate("create_group") },
                onEditGroup = { groupId -> navController.navigate("edit_group/$groupId") }
            )
        }

        composable("create_group") {
            CreateEditGroupScreen(
                onBack = { navController.popBackStack() },
                onSaveAndView = { groupId ->
                    navController.popBackStack()
                    navController.navigate("departures/$groupId")
                }
            )
        }

        composable(
            route = "edit_group/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.LongType })
        ) {
            CreateEditGroupScreen(
                onBack = { navController.popBackStack() },
                onSaveAndView = { groupId ->
                    navController.popBackStack()
                    navController.navigate("departures/$groupId")
                }
            )
        }

        composable(
            route = "departures/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.LongType })
        ) {
            DeparturesScreen(
                onBack = { navController.popBackStack() },
                onServiceClick = { serviceId ->
                    val encoded = URLEncoder.encode(serviceId, "UTF-8")
                    navController.navigate("service_detail/$encoded")
                },
                onBusClick = { vehicleId ->
                    val encoded = URLEncoder.encode(vehicleId, "UTF-8")
                    navController.navigate("bus_detail/$encoded")
                }
            )
        }

        composable(
            route = "service_detail/{serviceId}",
            arguments = listOf(navArgument("serviceId") { type = NavType.StringType })
        ) {
            ServiceDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "bus_detail/{vehicleId}",
            arguments = listOf(navArgument("vehicleId") { type = NavType.StringType })
        ) {
            BusDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
