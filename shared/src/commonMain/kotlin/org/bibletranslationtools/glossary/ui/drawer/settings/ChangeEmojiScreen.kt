package org.bibletranslationtools.glossary.ui.drawer.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.launch
import org.bibletranslationtools.glossary.ui.components.EmojiGridItem
import org.bibletranslationtools.glossary.ui.components.TopDrawerBar
import org.bibletranslationtools.glossary.ui.dialogs.ProgressDialog
import org.bibletranslationtools.glossary.ui.navigation.LocalSnackBarHostState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.change_emoji
import spotlight.shared.generated.resources.emoji_activity
import spotlight.shared.generated.resources.emoji_food
import spotlight.shared.generated.resources.emoji_gestures
import spotlight.shared.generated.resources.emoji_nature
import spotlight.shared.generated.resources.emoji_objects
import spotlight.shared.generated.resources.emoji_smileys
import spotlight.shared.generated.resources.emoji_travel

private data class EmojiCategory(
    val title: StringResource,
    val icon: String,
    val emojis: List<String>
)

private val allEmojiCategories = listOf(
    EmojiCategory(
        title = Res.string.emoji_smileys,
        icon = "😀",
        emojis = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃",
            "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😭",
            "😙", "🥲", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭",
            "🤫", "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄",
            "😬", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕",
            "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳",
            "😎", "🤓", "🧐", "😕", "😟", "🙁", "😮", "😯", "😲", "😱",
            "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢"
        )
    ),
    EmojiCategory(
        title = Res.string.emoji_gestures,
        icon = "👍",
        emojis = listOf(
            "👋", "🤚", "🖐", "✋", "🖖", "👌", "🤏", "✌️", "🤞", "🤟",
            "🤘", "🤙", "👈", "👉", "👆", "👇", "👍", "👎", "✊", "💪",
            "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "👀",
            "👄", "💋"
        )
    ),
    EmojiCategory(
        title = Res.string.emoji_nature,
        icon = "🐻",
        emojis = listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
            "🦁", "🐮", "🐷", "🐽", "🐸", "🐵", "🙈", "🙉", "🙊", "🐒",
            "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇",
            "🐺", "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜",
            "🦟", "🦗", "🕷", "🕸", "🦂", "🐢", "🐍", "🦎", "🦖", "🦕",
            "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳",
            "🌲", "🌳", "🌴", "🌵", "🌷", "🌸", "🌹", "🌺", "🌻", "🌼"
        )
    ),
    EmojiCategory(
        title = Res.string.emoji_food,
        icon = "🍔",
        emojis = listOf(
            "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍈",
            "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑", "🥦",
            "🥬", "🥒", "🌶", "🌽", "🥕", "🥔", "🍠", "🥐", "🥯", "🍞",
            "🥖", "🥨", "🧀", "🥚", "🍳", "🥞", "🥓", "🥩", "🍗", "🍖",
            "🌭", "🍔", "🍟", "🍕", "🥪", "🥙", "🌮", "🌯", "🥗", "🥘",
            "🥫", "🍝", "🍜", "🍲", "🍛", "🍣", "🍱", "🥟", "🍤", "🍙",
            "🍚", "🍘", "🍥", "🥠", "🍦", "🍧", "🍨", "🍩", "🍪", "🎂",
            "🍺", "🍻", "🥂", "🍷", "🥃", "🍸", "🍹", "🍾", "☕", "🍵"
        )
    ),
    EmojiCategory(
        title = Res.string.emoji_activity,
        icon = "⚽",
        emojis = listOf(
            "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱",
            "🪀", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🥅", "⛳", "🪁",
            "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸",
            "🥌", "🎿", "⛷", "🏂", "🪂", "🏋️", "🤼", "🤸", "⛹️", "🤺",
            "🎮", "🕹", "🎰", "🎲", "🧩", "🧸", "♠️", "♥️", "♦️", "♣️",
            "♟", "🃏", "🀄", "🎨", "🧵", "🧶", "🎹", "🎷", "🎺", "🎸"
        )
    ),
    EmojiCategory(
        title = Res.string.emoji_travel,
        icon = "🚗",
        emojis = listOf(
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐",
            "🛻", "🚚", "🚛", "🚜", "🏍️", "🛵", "🚲", "🦼", "🦽", "🦺",
            "🚨", "🚔", "🚍", "🚘", "🚖", "🚡", "🚠", "🚟", "🚃", "🚋",
            "🚞", "🚝", "🚄", "🚅", "🚈", "🚂", "🚆", "🚇", "🚊", "🚉",
            "✈️", "🛫", "🛬", "🛩️", "💺", "🛰️", "🚀", "🛸", "🚁", "🛶",
            "⛵", "🚤", "🛥️", "🛳️", "⛴️", "🚢", "⚓", "🚧", "⛽", "🚏"
        )
    ),
    EmojiCategory(
        title = Res.string.emoji_objects,
        icon = "💡",
        emojis = listOf(
            "⌚", "📱", "📲", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "🖲️", "🕹️",
            "🗜️", "💽", "💾", "💿", "📀", "📼", "📷", "📸", "📹", "🎥",
            "📽️", "🎞️", "📞", "☎️", "📟", "📠", "📺", "📻", "🎙️", "🎚️",
            "🎛️", "🧭", "⏱️", "⏲️", "⏰", "🕰️", "⌛", "⏳", "📡", "🔋",
            "🔌", "💡", "🔦", "🕯️", "🪔", "🧯", "🛢️", "💸", "💵", "💴",
            "💶", "💷", "💰", "💳", "💎", "⚖️", "🧰", "🔧", "🔨", "⚒️",
            "🛠️", "⛏️", "🔩", "⚙️", "🧱", "⛓️", "🧲", "🔫", "💣", "🧨"
        )
    )
)

@Composable
fun ChangeEmojiScreen(component: ChangeEmojiComponent) {
    val model by component.model.subscribeAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyGridState()

    val snackBar = LocalSnackBarHostState.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedTabIndex) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(model.snackBarMessage) {
        model.snackBarMessage?.let { message ->
            scope.launch {
                component.clearSnackBarMessage()
                snackBar?.showSnackbar(message)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopDrawerBar(
            title = stringResource(Res.string.change_emoji),
            subTitle = "",
            onBackClick = component::navigateBack,
            modifier = Modifier.fillMaxWidth()
                .background(color = MaterialTheme.colorScheme.surfaceVariant)
        )

        Column(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                        .padding(24.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    SecondaryScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        allEmojiCategories.forEachIndexed { index, category ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { Text(stringResource(category.title)) },
                                icon = { Text(category.icon) }
                            )
                        }
                    }

                    val currentEmojis = allEmojiCategories[selectedTabIndex].emojis

                    Box(modifier = Modifier.weight(1f).padding(8.dp)) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 45.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            state = listState
                        ) {
                            items(currentEmojis) { emoji ->
                                EmojiGridItem(
                                    emoji = emoji,
                                    onClick = {
                                        component.changeEmoji(emoji)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    model.progress?.let { progress ->
        ProgressDialog(progress)
    }
}
