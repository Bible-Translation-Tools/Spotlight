package org.bibletranslationtools.glossary

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.bibletranslationtools.glossary.di.initKoin
import org.bibletranslationtools.glossary.ui.DefaultRootComponent
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.app_name

fun main() {
    AppLogger.setupGlobalExceptionHandler()
    
    initKoin()

    val backDispatcher = BackDispatcher()
    val lifecycle = LifecycleRegistry()

    application {
        val windowState = rememberWindowState()
        LifecycleController(lifecycle, windowState)

        val root = DefaultRootComponent(
            DefaultComponentContext(
                lifecycle = lifecycle,
                backHandler = backDispatcher
            ),
            onFinished = ::exitApplication
        )

        Window(
            onCloseRequest = ::exitApplication,
            title = stringResource(Res.string.app_name),
            state = windowState,
            onKeyEvent = { event ->
                if ((event.key == Key.Escape) && (event.type == KeyEventType.KeyUp)) {
                    backDispatcher.back()
                } else {
                    false
                }
            }
        ) {
            App(root)
        }
    }
}