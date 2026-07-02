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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.app_name
import spotlight.shared.generated.resources.logo
import java.awt.Taskbar
import java.awt.Toolkit

fun main() {
    AppLogger.setupGlobalExceptionHandler()

    initKoin()
    setDockIcon()

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
            icon = painterResource(Res.drawable.logo),
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

/**
 * Sets the macOS Dock icon. Compose Desktop's `Window(icon = ...)` covers the
 * Windows/Linux taskbar icon, but macOS ignores it for the Dock while the app
 * is running - that needs the AWT Taskbar API instead.
 */
private fun setDockIcon() {
    if (!Taskbar.isTaskbarSupported()) return

    val taskbar = Taskbar.getTaskbar()
    if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return

    val iconUrl = object {}.javaClass.getResource("/icons/spotlight.png") ?: return
    taskbar.iconImage = Toolkit.getDefaultToolkit().getImage(iconUrl)
}