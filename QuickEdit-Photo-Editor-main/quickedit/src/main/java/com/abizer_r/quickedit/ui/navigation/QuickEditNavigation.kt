package com.abizer_r.quickedit.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.abizer_r.quickedit.ui.SharedEditorViewModel
import com.abizer_r.quickedit.ui.cropMode.CropperScreen
import com.abizer_r.quickedit.ui.drawMode.DrawModeScreen
import com.abizer_r.quickedit.ui.editorScreen.EditorScreen
import com.abizer_r.quickedit.ui.editorScreen.EditorScreenState
import com.abizer_r.quickedit.ui.effectsMode.EffectsModeScreen
import com.abizer_r.quickedit.ui.borderMode.BorderModeScreen
import com.abizer_r.quickedit.ui.mainScreen.MainScreen
import com.abizer_r.quickedit.ui.studioMode.StudioModeScreen
import com.abizer_r.quickedit.ui.textMode.TextModeScreen
import com.abizer_r.quickedit.ui.magicBrush.MagicBrushScreen
import com.abizer_r.quickedit.ui.rotateMode.RotateModeScreen
import com.abizer_r.quickedit.utils.other.anim.AnimUtils
import com.abizer_r.quickedit.utils.other.anim.enterTransition
import com.abizer_r.quickedit.utils.other.anim.exitTransition
import com.abizer_r.quickedit.utils.other.anim.popEnterTransition
import com.abizer_r.quickedit.utils.other.anim.popExitTransition
import com.abizer_r.quickedit.utils.other.bitmap.BitmapUtils
import com.abizer_r.quickedit.utils.other.bitmap.ImmutableBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
fun QuickEditNavigation(
    initialImageUri: Uri? = null,
    imagePickerScreen: @Composable (onImageSelected: (Uri) -> Unit, onCancel: () -> Unit) -> Unit
) {

    val sharedEditorViewModel = hiltViewModel<SharedEditorViewModel>()
    val navController = rememberNavController()

    val onImageSelected = remember<(Bitmap) -> Unit> {{ bitmap ->
        sharedEditorViewModel.addBitmapToStack(
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false),
            triggerRecomposition = false
        )
        sharedEditorViewModel.useTransition = true
        navController.navigate(NavDestinations.EDITOR_SCREEN)
    }}

    val goToCropModeScreenLambda = remember<(EditorScreenState) -> Unit> {{ finalEditorState ->
        sharedEditorViewModel.updateStacksFromEditorState(finalEditorState)
        navController.navigate(NavDestinations.CROPPER_SCREEN)
    }}
    val goToDrawModeScreenLambda = remember<(EditorScreenState) -> Unit> {{ finalEditorState ->
        sharedEditorViewModel.updateStacksFromEditorState(finalEditorState)
        navController.navigate(NavDestinations.DRAW_MODE_SCREEN)
    }}
    val goToTextModeScreenLambda = remember<(EditorScreenState) -> Unit> {{ finalEditorState ->
        sharedEditorViewModel.updateStacksFromEditorState(finalEditorState)
        navController.navigate(NavDestinations.TEXT_MODE_SCREEN)
    }}
    val goToEffectsModeScreenLambda = remember<(EditorScreenState) -> Unit> {{ finalEditorState ->
        sharedEditorViewModel.updateStacksFromEditorState(finalEditorState)
        navController.navigate(NavDestinations.EFFECTS_MODE_SCREEN)
    }}
    val goToBorderModeScreenLambda = remember<(EditorScreenState) -> Unit> {{ finalEditorState ->
        sharedEditorViewModel.updateStacksFromEditorState(finalEditorState)
        navController.navigate(NavDestinations.BORDER_MODE_SCREEN)
    }}
    val goToStudioModeScreenLambda = remember<(EditorScreenState) -> Unit> {{ finalEditorState ->
        sharedEditorViewModel.updateStacksFromEditorState(finalEditorState)
        navController.navigate(NavDestinations.STUDIO_MODE_SCREEN)
    }}
    val goToMagicBrushScreenLambda = remember<(EditorScreenState) -> Unit> {{ finalEditorState ->
        sharedEditorViewModel.updateStacksFromEditorState(finalEditorState)
        navController.navigate(NavDestinations.MAGIC_BRUSH_SCREEN)
    }}
    val goToRotateModeScreenLambda = remember<(EditorScreenState) -> Unit> {{ finalEditorState ->
        android.util.Log.d("RotateDebug", "goToRotateModeScreenLambda: updating stacks")
        sharedEditorViewModel.updateStacksFromEditorState(finalEditorState)
        android.util.Log.d("RotateDebug", "goToRotateModeScreenLambda: navigating to ${NavDestinations.ROTATE_SCREEN}")
        navController.navigate(NavDestinations.ROTATE_SCREEN)
    }}


    val goToMainScreenLambda = remember<() -> Unit> {{
        android.util.Log.d("RotateDebug", "goToMainScreenLambda: resetting stacks and navigating to main")
        sharedEditorViewModel.resetStacks()
        sharedEditorViewModel.useTransition = true
        navController.navigateUp()
    }}


    val onBackPressedLambda = remember<() -> Unit> {{
        android.util.Log.d("RotateDebug", "onBackPressedLambda: navigating up, current back stack: ${navController.currentBackStackEntry?.destination?.route}")
        navController.navigateUp()
    }}
    val onDoneClickedLambda = remember<(Bitmap) -> Unit> {{ resultBitmap ->
        android.util.Log.d("RotateDebug", "onDoneClickedLambda: adding bitmap to stack")
        sharedEditorViewModel.addBitmapToStack(
            bitmap = resultBitmap.copy(Bitmap.Config.ARGB_8888, false),
        )
        android.util.Log.d("RotateDebug", "onDoneClickedLambda: navigating back to EDITOR_SCREEN")
        navController.navigate(
            NavDestinations.EDITOR_SCREEN,
            navOptions = NavOptions.Builder()
                .setPopUpTo(route = NavDestinations.EDITOR_SCREEN, inclusive = true)
                .build()
        )
    }}


    NavHost(
        navController = navController,
        startDestination = NavDestinations.MAIN_SCREEN,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None }
    ) {

        composable(
            route = NavDestinations.MAIN_SCREEN,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { popEnterTransition() },
            popExitTransition = { popExitTransition() }
        ) {
            MainScreen(
                initialImageUri = initialImageUri,
                onImageSelected = onImageSelected
            )
        }

        composable(
            route = NavDestinations.EDITOR_SCREEN,
            enterTransition = {
                if (sharedEditorViewModel.useTransition) enterTransition()
                else EnterTransition.None
            },
            popEnterTransition = {
                if (sharedEditorViewModel.useTransition) popEnterTransition()
                else EnterTransition.None
            },
            exitTransition = {
                if (sharedEditorViewModel.useTransition) exitTransition()
                else ExitTransition.None
            },
            popExitTransition = {
                if (sharedEditorViewModel.useTransition) popExitTransition()
                else ExitTransition.None
            }
        ) {
            sharedEditorViewModel.useTransition = false
            val initialEditorState = EditorScreenState(
                sharedEditorViewModel.bitmapStack, sharedEditorViewModel.bitmapRedoStack
            )

            EditorScreen(
                initialEditorScreenState = initialEditorState,
                goToCropModeScreen = goToCropModeScreenLambda,
                goToDrawModeScreen = goToDrawModeScreenLambda,
                goToTextModeScreen = goToTextModeScreenLambda,
                goToEffectsModeScreen = goToEffectsModeScreenLambda,
                goToBorderModeScreen = goToBorderModeScreenLambda,
                goToStudioModeScreen = goToStudioModeScreenLambda,
                goToBackgroundModeScreen = { finalEditorState ->
                    sharedEditorViewModel.updateStacksFromEditorState(finalEditorState)
                    navController.navigate(NavDestinations.BACKGROUND_MODE_SCREEN)
                },
                goToMagicBrushScreen = goToMagicBrushScreenLambda,
                goToRotateModeScreen = goToRotateModeScreenLambda,
                goToMainScreen = goToMainScreenLambda
            )

        }

        composable(route = NavDestinations.BACKGROUND_MODE_SCREEN) { entry ->
            val pickedImageUri = entry.savedStateHandle.get<Uri>("background_image_uri")
            var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }
            val context = LocalContext.current

            LaunchedEffect(pickedImageUri) {
                pickedImageUri?.let { uri ->
                    val bitmap = com.abizer_r.quickedit.utils.other.bitmap.BitmapUtils.getBitmapFromUri(context, uri)
                    pickedBitmap = bitmap
                    entry.savedStateHandle.remove<Uri>("background_image_uri")
                }
            }

            com.abizer_r.quickedit.ui.backgroundMode.BackgroundModeScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
                onBackPressed = onBackPressedLambda,
                onDoneClicked = onDoneClickedLambda,
                onPickImageRequest = {
                    navController.navigate(NavDestinations.SINGLE_IMAGE_PICKER_SCREEN)
                },
                pickedImage = pickedBitmap
            )
        }

        composable(route = NavDestinations.SINGLE_IMAGE_PICKER_SCREEN) {
            imagePickerScreen(
                { uri ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("background_image_uri", uri)
                    navController.popBackStack()
                },
                {
                    navController.popBackStack()
                }
            )
        }

        composable(route = NavDestinations.MAGIC_BRUSH_SCREEN) {
            val context = androidx.compose.ui.platform.LocalContext.current
            MagicBrushScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
                onBackPressed = { navController.navigateUp() },
                onDoneClicked = { resultBitmap: Bitmap ->
                    sharedEditorViewModel.addBitmapToStack(
                        bitmap = resultBitmap.copy(Bitmap.Config.ARGB_8888, false),
                    )
                    navController.navigate(NavDestinations.EDITOR_SCREEN) {
                        popUpTo(NavDestinations.EDITOR_SCREEN) { inclusive = true }
                    }
                }
            )
        }

        composable(route = NavDestinations.CROPPER_SCREEN) { entry ->
            CropperScreen(
                immutableBitmap = ImmutableBitmap((sharedEditorViewModel.getCurrentBitmap())),
                onBackPressed = onBackPressedLambda,
                onDoneClicked = onDoneClickedLambda,
            )
        }

        composable(route = NavDestinations.DRAW_MODE_SCREEN) { entry ->
            DrawModeScreen(
                immutableBitmap = ImmutableBitmap((sharedEditorViewModel.getCurrentBitmap())),
                onBackPressed = onBackPressedLambda,
                onDoneClicked = onDoneClickedLambda,
            )
        }

        composable(route = NavDestinations.TEXT_MODE_SCREEN) { entry ->
            TextModeScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
                onBackPressed = onBackPressedLambda,
                onDoneClicked = onDoneClickedLambda,
            )
        }

        composable(route = NavDestinations.EFFECTS_MODE_SCREEN) { entry ->
            EffectsModeScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
                onBackPressed = onBackPressedLambda,
                onDoneClicked = onDoneClickedLambda,
            )
        }

        composable(route = NavDestinations.BORDER_MODE_SCREEN) { entry ->
            BorderModeScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
                onBackPressed = onBackPressedLambda,
                onDoneClicked = onDoneClickedLambda,
            )
        }

        composable(route = NavDestinations.STUDIO_MODE_SCREEN) { entry ->
            StudioModeScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
                onBackPressed = onBackPressedLambda,
                onDoneClicked = onDoneClickedLambda,
            )
        }

        composable(route = NavDestinations.ROTATE_SCREEN) {
            android.util.Log.d("RotateDebug", "ROTATE_SCREEN composable: rendering RotateModeScreen")
            RotateModeScreen(
                immutableBitmap = ImmutableBitmap(sharedEditorViewModel.getCurrentBitmap()),
                onBackPressed = onBackPressedLambda,
                onDoneClicked = onDoneClickedLambda,
            )
        }


    }
}