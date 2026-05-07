package com.thgiang.image.app.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object SingleImagePicker : Screen("image_picker")
    data object BatchPicker : Screen("batch_picker")
    data object BatchRemove : Screen("batch_remove")
    data object Settings : Screen("settings")
    data object Premium : Screen("premium")
    data object Drafts : Screen("drafts")
}
