package org.bibletranslationtools.glossary.ui.drawer.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import dev.burnoo.compose.remembersetting.rememberStringSetting
import kotlinx.coroutines.launch
import org.bibletranslationtools.glossary.data.api.UserRole
import org.bibletranslationtools.glossary.domain.Settings
import org.bibletranslationtools.glossary.domain.Theme
import org.bibletranslationtools.glossary.localize
import org.bibletranslationtools.glossary.ui.components.FloatingSegmentedSelector
import org.bibletranslationtools.glossary.ui.components.SettingsClickableItem
import org.bibletranslationtools.glossary.ui.components.SettingsSection
import org.bibletranslationtools.glossary.ui.components.SettingsSwitchItem
import org.bibletranslationtools.glossary.ui.components.TopDrawerBar
import org.bibletranslationtools.glossary.ui.data.FontFamilySetting
import org.bibletranslationtools.glossary.ui.data.FontSizeSetting
import org.bibletranslationtools.glossary.ui.data.LineHeightSetting
import org.bibletranslationtools.glossary.ui.dialogs.ProgressDialog
import org.bibletranslationtools.glossary.ui.navigation.LocalSnackBarHostState
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.about_app
import spotlight.shared.generated.resources.dark_mode
import spotlight.shared.generated.resources.data_privacy
import spotlight.shared.generated.resources.edit_account
import spotlight.shared.generated.resources.edit_permissions
import spotlight.shared.generated.resources.format_list_bulleted
import spotlight.shared.generated.resources.format_list_bulleted_add
import spotlight.shared.generated.resources.guest
import spotlight.shared.generated.resources.interface_settings
import spotlight.shared.generated.resources.language
import spotlight.shared.generated.resources.line_height
import spotlight.shared.generated.resources.login
import spotlight.shared.generated.resources.logout
import spotlight.shared.generated.resources.new_glossary
import spotlight.shared.generated.resources.other_settings
import spotlight.shared.generated.resources.person_edit
import spotlight.shared.generated.resources.review_changes
import spotlight.shared.generated.resources.search_check
import spotlight.shared.generated.resources.settings
import spotlight.shared.generated.resources.shield_lock
import spotlight.shared.generated.resources.size
import spotlight.shared.generated.resources.source_text_settings
import spotlight.shared.generated.resources.style
import spotlight.shared.generated.resources.terms_and_conditions
import spotlight.shared.generated.resources.user_settings
import spotlight.shared.generated.resources.view_glossaries

@Composable
fun SettingsListScreen(component: SettingsListComponent) {
    val model by component.model.subscribeAsState()

    val appStateStore = koinInject<AppStateStore>()
    val userState by appStateStore.userStateHolder.state
        .collectAsStateWithLifecycle()
    val glossaryState by appStateStore.glossaryStateHolder.state
        .collectAsStateWithLifecycle()

    var theme by rememberStringSetting(
        Settings.THEME,
        Theme.SYSTEM
    )
    val isSystemInDarkTheme = isSystemInDarkTheme()
    var isDarkModeEnabled by remember {
        mutableStateOf(darkModeEnabled(theme, isSystemInDarkTheme))
    }

    var savedFontFamily by rememberStringSetting(
        Settings.FONT_FAMILY,
        "SansSerif"
    )
    val fontFamilies = listOf(
        FontFamilySetting.SERIF,
        FontFamilySetting.SANS_SERIF,
        FontFamilySetting.MONOSPACE
    )
    var selectedFontFamily by remember {
        mutableStateOf(FontFamilySetting.of(savedFontFamily))
    }

    var savedFontSize by rememberStringSetting(
        Settings.FONT_SIZE,
        "medium"
    )
    val fontSizes = listOf(
        FontSizeSetting.SMALL,
        FontSizeSetting.MEDIUM,
        FontSizeSetting.LARGE
    )
    var selectedFontSize by remember {
        mutableStateOf(FontSizeSetting.of(savedFontSize))
    }

    var savedLineHeight by rememberStringSetting(
        Settings.LINE_HEIGHT,
        "default"
    )
    val lineHeights = listOf(
        LineHeightSetting.SMALL,
        LineHeightSetting.DEFAULT,
        LineHeightSetting.LARGE
    )
    var selectedLineHeight by remember {
        mutableStateOf(LineHeightSetting.of(savedLineHeight))
    }

    val scrollState = rememberScrollState()
    val snackBar = LocalSnackBarHostState.current
    val scope = rememberCoroutineScope()

    val isAdmin by remember(glossaryState.users) {
        mutableStateOf(
            glossaryState.users
                .filter { it.role == UserRole.OWNER || it.role == UserRole.ADMIN }
                .map { it.user.username }
                .contains(userState.user?.username)
        )
    }

    val role by remember(glossaryState.users) {
        mutableStateOf(
            glossaryState.users
                .firstOrNull { it.user.username == userState.user?.username }
                ?.role ?: UserRole.VIEWER
        )
    }

    LaunchedEffect(isDarkModeEnabled) {
        theme = if (isDarkModeEnabled) {
            Theme.DARK
        } else {
            Theme.LIGHT
        }
    }

    LaunchedEffect(glossaryState.glossary) {
        glossaryState.glossary?.let { glossary ->
            component.loadPendingPhrases(glossary)
        }
    }

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
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    TopDrawerBar(
                        title = stringResource(Res.string.settings),
                        onDismiss = component::dismiss,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        userState.user?.let { user ->
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = user.emoji,
                                    fontSize = 40.sp,
                                    modifier = Modifier
                                        .clickable { component.navigateChangeEmoji() }
                                )
                                Text(
                                    text = user.username,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "(${role.localize()})",
                                    fontSize = 16.sp
                                )
                                TextButton(
                                    onClick = component::logout,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text(
                                        text = stringResource(Res.string.logout),
                                        textDecoration = TextDecoration.Underline
                                    )
                                }
                            }
                        } ?: run {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = "guest",
                                    modifier = Modifier.size(70.dp)
                                )
                                Text(
                                    text = stringResource(Res.string.guest),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "(${role.localize()})",
                                    fontSize = 16.sp
                                )
                                TextButton(
                                    onClick = component::navigateLogin
                                ) {
                                    Text(
                                        text = stringResource(Res.string.login),
                                        textDecoration = TextDecoration.Underline
                                    )
                                }
                            }

                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        SettingsSection(
                            title = stringResource(Res.string.source_text_settings)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FontDownload,
                                    contentDescription = "font style"
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(Res.string.style),
                                    fontWeight = FontWeight.W500
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = selectedFontFamily.name,
                                    fontWeight = FontWeight.W500,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FloatingSegmentedSelector(
                                options = fontFamilies,
                                selectedOption = selectedFontFamily,
                                onOptionSelected = {
                                    selectedFontFamily = it
                                    savedFontFamily = it.name
                                }
                            ) { option, isSelected ->
                                Text(
                                    text = option.localize(),
                                    fontWeight = if (isSelected) {
                                        FontWeight.Bold
                                    } else FontWeight.SemiBold,
                                    fontFamily = option.value,
                                    fontSize = 28.sp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TextFields,
                                    contentDescription = "font size"
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(Res.string.size),
                                    fontWeight = FontWeight.W500
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = selectedFontSize.localize(),
                                    fontWeight = FontWeight.W500,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FloatingSegmentedSelector(
                                options = fontSizes,
                                selectedOption = selectedFontSize,
                                onOptionSelected = {
                                    selectedFontSize = it
                                    savedFontSize = it.name
                                }
                            ) { option, isSelected ->
                                Text(
                                    text = option.localize(),
                                    fontWeight = if (isSelected) {
                                        FontWeight.Bold
                                    } else FontWeight.W400,
                                    fontSize = option.value,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FormatLineSpacing,
                                    contentDescription = "line height"
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(Res.string.line_height),
                                    fontWeight = FontWeight.W500
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = selectedLineHeight.localize(),
                                    fontWeight = FontWeight.W500,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FloatingSegmentedSelector(
                                options = lineHeights,
                                selectedOption = selectedLineHeight,
                                onOptionSelected = {
                                    selectedLineHeight = it
                                    savedLineHeight = it.name
                                }
                            ) { option, isSelected ->
                                Text(
                                    text = option.localize(),
                                    fontWeight = if (isSelected) {
                                        FontWeight.Bold
                                    } else FontWeight.W400,
                                    fontSize = 16.sp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            DividerDefaults.color
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        SettingsSection(
                            title = stringResource(Res.string.interface_settings)
                        ) {
                            SettingsClickableItem(
                                icon = Icons.Default.Translate,
                                text = stringResource(Res.string.language),
                                actionText = "English",
                                onClick = {}
                            )
                            SettingsSwitchItem(
                                icon = Icons.Outlined.DarkMode,
                                text = stringResource(Res.string.dark_mode),
                                checked = isDarkModeEnabled,
                                onCheckedChange = { isDarkModeEnabled = it }
                            )
                        }

                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            DividerDefaults.color
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        SettingsSection(
                            title = stringResource(Res.string.user_settings)
                        ) {
                            if (isAdmin) {
                                SettingsClickableItem(
                                    icon = painterResource(Res.drawable.person_edit),
                                    text = stringResource(Res.string.edit_account),
                                    onClick = {},
                                    modifier = Modifier.fillMaxWidth()
                                )
                                SettingsClickableItem(
                                    icon = painterResource(Res.drawable.person_edit),
                                    text = stringResource(Res.string.edit_permissions),
                                    onClick = component::editPermissions,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            SettingsClickableItem(
                                icon = painterResource(Res.drawable.format_list_bulleted_add),
                                text = stringResource(Res.string.new_glossary),
                                onClick = component::createGlossary,
                                modifier = Modifier.fillMaxWidth()
                            )
                            SettingsClickableItem(
                                icon = painterResource(Res.drawable.format_list_bulleted),
                                text = stringResource(Res.string.view_glossaries),
                                onClick = component::viewGlossaries
                            )
                            if (isAdmin) {
                                SettingsClickableItem(
                                    icon = painterResource(Res.drawable.search_check),
                                    text = stringResource(Res.string.review_changes),
                                    actionText = if (model.pendingPhrases.isNotEmpty()) {
                                        "(${model.pendingPhrases.size})"
                                    } else "",
                                    inProgress = model.pendingPhrasesLoading,
                                    onClick = component::reviewChanges
                                )
                            }
                        }

                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            DividerDefaults.color
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        SettingsSection(
                            title = stringResource(Res.string.other_settings)
                        ) {
                            SettingsClickableItem(
                                icon = Icons.Default.Info,
                                text = stringResource(Res.string.about_app),
                                onClick = {}
                            )
                            SettingsClickableItem(
                                icon = Icons.Outlined.Description,
                                text = stringResource(Res.string.terms_and_conditions),
                                onClick = {}
                            )
                            SettingsClickableItem(
                                icon = painterResource(Res.drawable.shield_lock),
                                text = stringResource(Res.string.data_privacy),
                                onClick = {}
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    model.progress?.let { progress ->
        ProgressDialog(progress)
    }
}

private fun darkModeEnabled(
    theme: String,
    isSystemInDarkTheme: Boolean
): Boolean {
    return when (theme) {
        Theme.SYSTEM if isSystemInDarkTheme -> true
        Theme.LIGHT -> false
        Theme.DARK -> true
        else -> false
    }
}