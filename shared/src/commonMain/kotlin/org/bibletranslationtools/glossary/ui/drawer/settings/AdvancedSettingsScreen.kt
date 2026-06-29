package org.bibletranslationtools.glossary.ui.drawer.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.launch
import org.bibletranslationtools.glossary.ui.components.SettingsClickableItem
import org.bibletranslationtools.glossary.ui.components.SettingsSection
import org.bibletranslationtools.glossary.ui.components.TopDrawerBar
import org.bibletranslationtools.glossary.ui.dialogs.ProgressDialog
import org.bibletranslationtools.glossary.ui.navigation.LocalSnackBarHostState
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.advanced_settings
import spotlight.shared.generated.resources.download_langnames
import spotlight.shared.generated.resources.import_langnames
import spotlight.shared.generated.resources.languages_settings

@Composable
fun AdvancedSettingsScreen(component: AdvancedSettingsComponent) {
    val model by component.model.subscribeAsState()

    val snackBar = LocalSnackBarHostState.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(model.snackBarMessage) {
        model.snackBarMessage?.let { message ->
            scope.launch {
                component.clearSnackBarMessage()
                snackBar?.showSnackbar(message)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp)
    ) {
        TopDrawerBar(
            title = stringResource(Res.string.advanced_settings),
            onBackClick = component::navigateBack,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSection(
            title = stringResource(Res.string.languages_settings)
        ) {
            SettingsClickableItem(
                icon = Icons.Default.Download,
                text = stringResource(Res.string.download_langnames),
                onClick = component::downloadLanguages
            )
            SettingsClickableItem(
                icon = Icons.Default.UploadFile,
                text = stringResource(Res.string.import_langnames),
                onClick = {
                    scope.launch {
                        FileKit.openFilePicker()?.let { file ->
                            component.importLanguages(file)
                        }
                    }
                }
            )
        }
    }

    model.progress?.let { progress ->
        ProgressDialog(progress)
    }
}
