package org.bibletranslationtools.glossary.ui.components

import androidx.compose.runtime.Composable
import org.bibletranslationtools.glossary.data.api.UserRole
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.user_role_admin
import spotlight.shared.generated.resources.user_role_admin_desc
import spotlight.shared.generated.resources.user_role_editor
import spotlight.shared.generated.resources.user_role_editor_desc
import spotlight.shared.generated.resources.user_role_owner
import spotlight.shared.generated.resources.user_role_viewer
import spotlight.shared.generated.resources.user_role_viewer_desc

@Composable
fun UserRole.localizedName(): String {
    return when (this) {
        UserRole.OWNER -> stringResource(Res.string.user_role_owner)
        UserRole.ADMIN -> stringResource(Res.string.user_role_admin)
        UserRole.EDITOR -> stringResource(Res.string.user_role_editor)
        UserRole.VIEWER -> stringResource(Res.string.user_role_viewer)
    }
}

@Composable
fun UserRole.localizedDescription(): String {
    return when (this) {
        UserRole.OWNER -> ""
        UserRole.ADMIN -> stringResource(Res.string.user_role_admin_desc)
        UserRole.EDITOR -> stringResource(Res.string.user_role_editor_desc)
        UserRole.VIEWER -> stringResource(Res.string.user_role_viewer_desc)
    }
}
