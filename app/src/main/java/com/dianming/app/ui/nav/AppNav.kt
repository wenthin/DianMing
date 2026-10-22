package com.dianming.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dianming.app.ui.detail.ClassDetailScreen
import com.dianming.app.ui.detail.SessionDetailScreen
import com.dianming.app.ui.home.HomeScreen
import com.dianming.app.ui.import.ImportScreen
import com.dianming.app.ui.rollcall.RollCallScreen

object Routes {
    const val HOME = "home"
    const val IMPORT = "import"
    const val CLASS_DETAIL = "class/{classId}"
    const val SESSION_DETAIL = "session/{sessionId}"
    const val ROLL_CALL = "rollcall/{classId}/{sessionId}"

    fun classDetail(classId: String) = "class/$classId"
    fun sessionDetail(sessionId: String) = "session/$sessionId"
    fun rollCall(classId: String, sessionId: String) = "rollcall/$classId/$sessionId"
}

@Composable
fun AppNav(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onAddClass = { navController.navigate(Routes.IMPORT) },
                onOpenClass = { classId -> navController.navigate(Routes.classDetail(classId)) }
            )
        }

        composable(Routes.IMPORT) {
            ImportScreen(
                onBack = { navController.popBackStack() },
                onSaved = { classId ->
                    navController.navigate(Routes.classDetail(classId)) {
                        popUpTo(Routes.IMPORT) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.CLASS_DETAIL,
            arguments = listOf(navArgument("classId") { type = NavType.StringType })
        ) { entry ->
            val classId = entry.arguments?.getString("classId").orEmpty()
            ClassDetailScreen(
                classId = classId,
                onBack = { navController.popBackStack() },
                onStartRollCall = { sessionId ->
                    navController.navigate(Routes.rollCall(classId, sessionId))
                },
                onOpenSession = { sessionId ->
                    navController.navigate(Routes.sessionDetail(sessionId))
                }
            )
        }

        composable(
            route = Routes.SESSION_DETAIL,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            SessionDetailScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onContinueRollCall = { classId, sid ->
                    navController.navigate(Routes.rollCall(classId, sid))
                }
            )
        }

        composable(
            route = Routes.ROLL_CALL,
            arguments = listOf(
                navArgument("classId") { type = NavType.StringType },
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { entry ->
            val classId = entry.arguments?.getString("classId").orEmpty()
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            RollCallScreen(
                classId = classId,
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onFinish = {
                    navController.popBackStack(Routes.CLASS_DETAIL, inclusive = false)
                }
            )
        }
    }
}
