package org.bibletranslationtools.glossary.ui.drawer.settings

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import org.bibletranslationtools.glossary.Utils

@Composable
fun SettingsScreen(component: SettingsComponent) {
    Children(
        stack = component.childStack,
        animation = stackAnimation(Utils.slideHorizontally())
    ) {
        when (val child = it.instance) {
            is SettingsComponent.Child.Settings -> SettingsListScreen(child.component)
            is SettingsComponent.Child.CreateGlossary -> CreateGlossaryScreen(child.component)
            is SettingsComponent.Child.ViewGlossaries -> GlossaryListScreen(child.component)
            is SettingsComponent.Child.SelectLanguage -> SelectLanguageScreen(child.component)
            is SettingsComponent.Child.ImportGlossary -> ImportGlossaryScreen(child.component)
            is SettingsComponent.Child.EditPermissions -> EditPermissionsScreen(child.component)
            is SettingsComponent.Child.ReviewChanges -> ReviewChangesScreen(child.component)
            is SettingsComponent.Child.AdvancedSettings -> AdvancedSettingsScreen(child.component)
            is SettingsComponent.Child.Login -> LoginScreen(child.component)
            is SettingsComponent.Child.ChangeEmoji -> ChangeEmojiScreen(child.component)
        }
    }
}