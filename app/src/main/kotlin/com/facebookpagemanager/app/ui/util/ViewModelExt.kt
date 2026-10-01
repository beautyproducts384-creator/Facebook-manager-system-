package com.facebookpagemanager.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.facebookpagemanager.app.FpmApplication
import com.facebookpagemanager.app.di.AppContainer

/** ViewModel factory wired to the manual DI container. */
@Composable
inline fun <reified VM : ViewModel> fpmViewModel(
    crossinline creator: (AppContainer) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as FpmApplication
    val container = app.container
    return viewModel(
        modelClass = VM::class.java,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                creator(container) as T
        }
    )
}

/** Reads the DI container directly (for non-ViewModel use). */
@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as FpmApplication).container
