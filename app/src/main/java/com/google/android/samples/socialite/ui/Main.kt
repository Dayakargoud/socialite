/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.samples.socialite.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Matrix
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.RgbFilter
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.google.android.samples.socialite.model.extractChatId
import com.google.android.samples.socialite.ui.camera.Camera
import com.google.android.samples.socialite.ui.camera.CameraViewModel
import com.google.android.samples.socialite.ui.camera.Media
import com.google.android.samples.socialite.ui.camera.MediaType
import com.google.android.samples.socialite.ui.chat.ChatScreen
import com.google.android.samples.socialite.ui.home.Home
import com.google.android.samples.socialite.ui.photopicker.navigation.navigateToPhotoPicker
import com.google.android.samples.socialite.ui.photopicker.navigation.photoPickerScreen
import com.google.android.samples.socialite.ui.player.VideoPlayerScreen
import com.google.android.samples.socialite.ui.videoedit.VideoEditScreen
import com.google.android.samples.socialite.ui.videoedit.transformVideo

@Composable
fun Main(
    shortcutParams: ShortcutParams?,
) {
    val modifier = Modifier.fillMaxSize()
    SocialTheme {
        MainNavigation(modifier, shortcutParams)
    }
}

var transformedVideoFilePath = ""

@Composable
fun MainNavigation(
    modifier: Modifier,
    shortcutParams: ShortcutParams?,
) {
    val activity = LocalContext.current as Activity
    val navController = rememberNavController()

    navController.addOnDestinationChangedListener { _: NavController, navDestination: NavDestination, _: Bundle? ->
        // Lock the layout of the Camera screen to portrait so that the UI layout remains
        // constant, even on orientation changes. Note that the camera is still aware of
        // orientation, and will assign the correct edge as the bottom of the photo or video.
        if (navDestination.route?.contains("camera") == true) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    NavHost(
        navController = navController,
        startDestination = "home",
        popEnterTransition = {
            scaleIn(initialScale = 1.1F) + fadeIn()
        },
        popExitTransition = {
            scaleOut(targetScale = 0.9F) + fadeOut()
        },
        modifier = modifier,
    ) {
        composable(
            route = "home",
        ) {
            Home(
                modifier = Modifier.fillMaxSize(),
                onChatClicked = { chatId -> navController.navigate("chat/$chatId") },
            )
        }
        composable(
            route = "chat/{chatId}?text={text}",
            arguments = listOf(
                navArgument("chatId") { type = NavType.LongType },
                navArgument("text") { defaultValue = "" },
            ),
            deepLinks = listOf(
                navDeepLink {
                    action = Intent.ACTION_VIEW
                    uriPattern = "https://socialite.google.com/chat/{chatId}"
                },
            ),
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getLong("chatId") ?: 0L
            val text = backStackEntry.arguments?.getString("text")
            ChatScreen(
                chatId = chatId,
                foreground = true,
                onBackPressed = { navController.popBackStack() },
                onCameraClick = { navController.navigate("chat/$chatId/camera") },
                onPhotoPickerClick = { navController.navigateToPhotoPicker(chatId) },
                onVideoClick = { uri -> navController.navigate("videoPlayer?uri=$uri") },
                prefilledText = text,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(
            route = "chat/{chatId}/camera",
            arguments = listOf(
                navArgument("chatId") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getLong("chatId") ?: 0L

            Camera(
                onMediaCaptured = { capturedMedia: Media? ->
                    when (capturedMedia?.mediaType) {
                        MediaType.PHOTO -> {
                            navController.popBackStack()
                        }

                        MediaType.VIDEO -> {
                            // Show loading icon

                            transformVideo(
                                context = navController.context,
                                originalVideoUri = capturedMedia.uri.toString(),
                                @UnstableApi object : Transformer.Listener {
                                    override fun onCompleted(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                    ) {
                                        navController.navigate("videoPlayer?uri=${transformedVideoFilePath}")
                                    }

                                    override fun onError(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                        exportException: ExportException,
                                    ) {
                                        exportException.printStackTrace()
                                    }
                                },
                            )
                        }

                        else -> {
                            navController.popBackStack()
                        }
                    }
                },
                chatId = chatId,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Invoke PhotoPicker to select photo or video from device gallery
        photoPickerScreen(
            onPhotoPicked = navController::popBackStack,
        )

        composable(
            route = "videoEdit?uri={videoUri}&chatId={chatId}",
            arguments = listOf(
                navArgument("videoUri") { type = NavType.StringType },
                navArgument("chatId") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getLong("chatId") ?: 0L
            val videoUri = backStackEntry.arguments?.getString("videoUri") ?: ""
            VideoEditScreen(
                chatId = chatId,
                uri = videoUri,
                onCloseButtonClicked = { navController.popBackStack() },
                navController = navController,
            )
        }
        composable(
            route = "videoPlayer?uri={videoUri}",
            arguments = listOf(
                navArgument("videoUri") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val videoUri = backStackEntry.arguments?.getString("videoUri") ?: ""
            VideoPlayerScreen(
                uri = videoUri,
                onCloseButtonClicked = { navController.popBackStack() },
            )
        }
    }

    if (shortcutParams != null) {
        val chatId = extractChatId(shortcutParams.shortcutId)
        val text = shortcutParams.text
        navController.navigate("chat/$chatId?text=$text")
    }
}

data class ShortcutParams(
    val shortcutId: String,
    val text: String,
)

object AnimationConstants {
    private const val ENTER_MILLIS = 250
    private const val EXIT_MILLIS = 250

    val enterTransition = fadeIn(
        animationSpec = tween(
            durationMillis = ENTER_MILLIS,
            easing = FastOutLinearInEasing,
        ),
    )

    val exitTransition = fadeOut(
        animationSpec = tween(
            durationMillis = EXIT_MILLIS,
            easing = FastOutSlowInEasing,
        ),
    )
}
