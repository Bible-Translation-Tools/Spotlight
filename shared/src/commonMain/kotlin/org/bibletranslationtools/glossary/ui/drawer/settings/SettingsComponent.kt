package org.bibletranslationtools.glossary.ui.drawer.settings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.instancekeeper.getOrCreate
import kotlinx.serialization.Serializable
import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.data.api.User
import org.bibletranslationtools.glossary.ui.ParentContext
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
import org.bibletranslationtools.glossary.ui.main.SettingsIntent

interface SettingsComponent: DrawerContext {
    val childStack: Value<ChildStack<*, Child>>

    sealed interface Child {
        data class Settings(val component: SettingsListComponent) : Child
        data class CreateGlossary(val component: CreateGlossaryComponent) : Child
        data class SelectLanguage(val component: SelectLanguageComponent) : Child
        data class ViewGlossaries(val component: GlossaryListComponent) : Child
        data class ImportGlossary(val component: ImportGlossaryComponent) : Child
        data class EditPermissions(val component: EditPermissionsComponent) : Child
        data class ReviewChanges(val component: ReviewChangesComponent) : Child
        data class AdvancedSettings(val component: AdvancedSettingsComponent) : Child
        data class Login(val component: LoginComponent) : Child
        data class ChangeEmoji(val component: ChangeEmojiComponent) : Child
    }
}

class DefaultSettingsComponent(
    componentContext: ComponentContext,
    private val parentContext: ParentContext,
    intent: SettingsIntent,
    private val onSelectResource: (resource: Resource) -> Unit,
    private val onSelectGlossary: (glossary: Glossary, openKeyTerms: Boolean) -> Unit,
    private val onFullscreen: (Boolean) -> Unit,
    private val onImportFinished: () -> Unit,
    private val onUserUpdated: (User) -> Unit,
    private val onLogout: () -> Unit
) : SettingsComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()
    private val createGlossaryState = instanceKeeper.getOrCreate { CreateGlossaryStateKeeper() }

    override val childStack: Value<ChildStack<*, SettingsComponent.Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = when (intent) {
                SettingsIntent.Index -> Config.Settings
                SettingsIntent.ImportGlossary -> Config.ImportGlossary()
                SettingsIntent.CreateGlossary -> Config.CreateGlossary
            },
            handleBackButton = false,
            childFactory = ::createChild
        )


    init {
        backHandler.register(
            BackCallback {
                navigateBack()
            }
        )
    }

    private fun createChild(
        config: Config,
        context: ComponentContext
    ) : SettingsComponent.Child {
        return when (config) {
            is Config.Settings -> SettingsComponent.Child.Settings(
                DefaultSettingsListComponent(
                    componentContext = context,
                    parentContext = this,
                    onCreateGlossary = {
                        navigation.bringToFront(Config.CreateGlossary)
                    },
                    onViewGlossaries = {
                        navigation.bringToFront(Config.ViewGlossaries)
                    },
                    onNavigateLogin = {
                        navigation.bringToFront(Config.Login)
                    },
                    onNavigateChangeEmoji = {
                        navigation.bringToFront(Config.ChangeEmoji)
                    },
                    onLogout = onLogout,
                    onEditPermissions = {
                        navigation.bringToFront(Config.EditPermissions)
                    },
                    onReviewChanges = {
                        navigation.bringToFront(Config.ReviewChanges)
                    },
                    onAdvancedSettings = {
                        navigation.bringToFront(Config.AdvancedSettings)
                    }
                )
            )
            is Config.CreateGlossary -> SettingsComponent.Child.CreateGlossary(
                DefaultCreateGlossaryComponent(
                    componentContext = context,
                    parentContext = this,
                    sharedState = createGlossaryState,
                    onResourceDownloaded = onSelectResource,
                    onGlossaryCreated = { resource, glossary ->
                        onSelectResource(resource)
                        onSelectGlossary(glossary, true)
                        navigation.replaceAll(Config.Settings)
                    },
                    onSelectLanguage = { type ->
                        navigation.bringToFront(Config.SelectLanguage(type))
                    }
                )
            )
            is Config.SelectLanguage -> SettingsComponent.Child.SelectLanguage(
                DefaultSelectLanguageComponent(
                    componentContext = context,
                    parentContext = this,
                    type = config.type,
                    sharedState = createGlossaryState
                )
            )
            is Config.ViewGlossaries -> SettingsComponent.Child.ViewGlossaries(
                DefaultGlossaryListComponent(
                    componentContext = context,
                    parentContext = this,
                    onNavigateImportGlossary = {
                        navigation.bringToFront(Config.ImportGlossary())
                    },
                    onNavigateCreateGlossary = {
                        navigation.bringToFront(Config.CreateGlossary)
                    },
                    onSelectResource = onSelectResource,
                    onSelectGlossary = onSelectGlossary,
                    onImportManually = {
                        navigation.bringToFront(Config.ImportGlossary(true))
                    }
                )
            )
            is Config.ImportGlossary -> SettingsComponent.Child.ImportGlossary(
                DefaultImportGlossaryComponent(
                    componentContext = context,
                    parentContext = this,
                    autoImportManually = config.autoImportManually,
                    onSelectResource = onSelectResource,
                    onSelectGlossary = onSelectGlossary,
                    onImportFinished = { onImportFinished() }
                )
            )
            is Config.EditPermissions -> SettingsComponent.Child.EditPermissions(
                DefaultEditPermissionsComponent(
                    componentContext = context,
                    parentContext = this
                )
            )
            is Config.ReviewChanges -> SettingsComponent.Child.ReviewChanges(
                DefaultReviewChangesComponent(
                    componentContext = context,
                    parentContext = this
                )
            )
            is Config.AdvancedSettings -> SettingsComponent.Child.AdvancedSettings(
                DefaultAdvancedSettingsComponent(
                    componentContext = context,
                    parentContext = this
                )
            )
            is Config.Login -> SettingsComponent.Child.Login(
                DefaultLoginComponent(
                    componentContext = context,
                    parentContext = this,
                    onUserUpdated = onUserUpdated
                )
            )
            is Config.ChangeEmoji -> SettingsComponent.Child.ChangeEmoji(
                DefaultChangeEmojiComponent(
                    componentContext = context,
                    parentContext = this,
                    onUserUpdated = onUserUpdated
                )
            )
        }
    }

    override fun dismiss() {
        setFullscreen(false)
        parentContext.dismissDrawer()
    }

    override fun navigateBack() {
        setFullscreen(false)
        if (childStack.value.backStack.isNotEmpty()) {
            navigation.pop()
        } else {
            dismiss()
        }
    }

    override fun setFullscreen(fullscreen: Boolean) {
        onFullscreen(fullscreen)
    }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Settings : Config
        @Serializable
        data object CreateGlossary : Config
        @Serializable
        data object ViewGlossaries : Config
        @Serializable
        data class SelectLanguage(val type: LanguageType) : Config
        @Serializable
        data class ImportGlossary(val autoImportManually: Boolean = false) : Config
        @Serializable
        data object EditPermissions : Config
        @Serializable
        data object ReviewChanges : Config
        @Serializable
        data object AdvancedSettings : Config
        @Serializable
        data object Login : Config
        @Serializable
        data object ChangeEmoji : Config
    }
}