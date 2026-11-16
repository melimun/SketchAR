package com.tracecam.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tracecam.ui.home.HomeScreen
import com.tracecam.ui.trace.TraceScreen

object Routes {
    const val HOME = "home"
    const val TRACE = "trace"
    const val ARG_IMAGE_URI = "imageUri"
    const val TRACE_PATTERN = "$TRACE?$ARG_IMAGE_URI={$ARG_IMAGE_URI}"

    fun trace(imageUri: Uri): String = "$TRACE?$ARG_IMAGE_URI=${Uri.encode(imageUri.toString())}"
}

@Composable
fun SketchArApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onImageReady = { uri -> navController.navigate(Routes.trace(uri)) })
        }
        composable(
            route = Routes.TRACE_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_IMAGE_URI) {
                    type = NavType.StringType
                    nullable = true
                },
            ),
        ) { entry ->
            val imageUri = entry.arguments?.getString(Routes.ARG_IMAGE_URI)?.let(Uri::parse)
            TraceScreen(imageUri = imageUri, onBack = { navController.popBackStack() })
        }
    }
}
