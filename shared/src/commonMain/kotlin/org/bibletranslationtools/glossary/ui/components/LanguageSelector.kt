package org.bibletranslationtools.glossary.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bibletranslationtools.glossary.data.Language
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.edit
import spotlight.shared.generated.resources.required

@Composable
fun LanguageSelector(
    title: String,
    language: Language?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectorTitle by rememberUpdatedState(title)
    val selectorLanguage by rememberUpdatedState(language)

    Surface(
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = selectorTitle,
                    fontWeight = FontWeight.W500,
                    fontSize = 16.sp
                )
                selectorLanguage?.let { language ->
                    Text(
                        text = language.name
                    )
                } ?: Text(
                    text = stringResource(Res.string.required),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
            TextButton(
                onClick = onClick
            ) {
                Text(
                    text = stringResource(Res.string.edit),
                    textDecoration = TextDecoration.Underline
                )
            }
        }
    }
}