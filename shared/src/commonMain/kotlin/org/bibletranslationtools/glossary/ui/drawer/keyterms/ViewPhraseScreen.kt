package org.bibletranslationtools.glossary.ui.drawer.keyterms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import dev.burnoo.compose.remembersetting.rememberStringSetting
import org.bibletranslationtools.glossary.data.api.UserRole
import org.bibletranslationtools.glossary.domain.Settings
import org.bibletranslationtools.glossary.ui.components.TopDrawerBar
import org.bibletranslationtools.glossary.ui.components.VerseReference
import org.bibletranslationtools.glossary.ui.data.FontFamilySetting
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.add_audio
import spotlight.shared.generated.resources.edit
import spotlight.shared.generated.resources.key_terms

@Composable
fun ViewPhraseScreen(component: ViewPhraseComponent) {
    val model by component.model.subscribeAsState()

    val appStateStore = koinInject<AppStateStore>()
    val resourceState by appStateStore.resourceStateHolder.state
        .collectAsStateWithLifecycle()
    val glossaryState by appStateStore.glossaryStateHolder.state
        .collectAsStateWithLifecycle()
    val userState by appStateStore.userStateHolder.state
        .collectAsStateWithLifecycle()

    var savedFontFamily by rememberStringSetting(
        Settings.FONT_FAMILY,
        "SansSerif"
    )

    val fontFamily by remember(savedFontFamily) {
        mutableStateOf(
            FontFamilySetting.of(savedFontFamily).value
        )
    }

    var canEdit by remember { mutableStateOf(false) }

    LaunchedEffect(glossaryState.users, userState.user) {
        userState.user?.let { glossaryUser ->
            canEdit = glossaryState.users
                .filter { it.role != UserRole.VIEWER }
                .map { it.user.username }
                .contains(glossaryUser.username)
        } ?: run {
            canEdit = true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    TopDrawerBar(
                        title = stringResource(Res.string.key_terms),
                        subTitle = model.phrase?.phrase,
                        onBackClick = component::navigateBack,
                        onDismiss = component::dismiss,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Column (
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = model.phrase?.spelling ?: "",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fontFamily
                            )
                            if (!model.phrase?.audio.isNullOrEmpty()) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Listen",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        BasicTextField(
                            value = model.phrase?.description ?: "",
                            onValueChange = {},
                            readOnly = true,
                            maxLines = 5,
                            textStyle = TextStyle.Default.copy(
                                fontSize = 16.sp,
                                lineHeight = 32.sp,
                                fontFamily = fontFamily,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    if (!model.isLoading) {
                        Spacer(modifier = Modifier.height(64.dp))
                    }

                    if (model.isLoading) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth()
                                .weight(1f)
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier.fillMaxSize()
                                .weight(1f)
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.medium
                                )
                        ) {
                            items(model.refs) { ref ->
                                resourceState.resource?.let { resource ->
                                    val reference = "${ref.book.uppercase()} ${ref.chapter}:${ref.verse}"
                                    val text = ref.getVerseText(resource)

                                    model.phrase?.let { phrase ->
                                        VerseReference(
                                            reference = reference,
                                            phrase = phrase.phrase,
                                            text = text,
                                            fontFamily = fontFamily,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            component.onRefClick(ref)
                                        }
                                    }
                                }
                            }
                        }

                        if (canEdit) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 16.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            model.phrase?.let { phrase ->
                                                component.onEditClick(phrase)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = "Edit",
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(Res.string.edit))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { /*TODO*/ },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        shape = MaterialTheme.shapes.medium,
                                        enabled = false,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Mic,
                                            contentDescription = "Add Audio",
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(Res.string.add_audio))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
