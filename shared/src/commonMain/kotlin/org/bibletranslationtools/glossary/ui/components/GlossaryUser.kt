package org.bibletranslationtools.glossary.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bibletranslationtools.glossary.data.api.GlossaryUser
import org.bibletranslationtools.glossary.data.api.UserRole
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.edit
import spotlight.shared.generated.resources.user_you

@Composable
fun GlossaryUser(
    glossaryUser: GlossaryUser,
    isMe: Boolean,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = if (isMe) {
        stringResource(Res.string.user_you, glossaryUser.user.username)
    } else glossaryUser.user.username

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(16.dp)
    ) {
        Text(
            text = glossaryUser.user.emoji,
            fontSize = 25.sp
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = name,
                fontWeight = FontWeight.W600
            )
            Text(
                text = glossaryUser.role.localizedName()
            )
        }

        if (!isMe && glossaryUser.role != UserRole.OWNER) {
            TextButton(
                onClick = onEdit
            ) {
                Text(text = stringResource(Res.string.edit))
            }
        }
    }
}