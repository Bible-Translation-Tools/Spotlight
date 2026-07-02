package org.bibletranslationtools.glossary.ui.read

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.delay
import org.bibletranslationtools.glossary.data.RefOption
import org.bibletranslationtools.glossary.ui.components.BookItem
import org.bibletranslationtools.glossary.ui.components.ChapterGrid
import org.bibletranslationtools.glossary.ui.components.SearchField
import org.bibletranslationtools.glossary.ui.components.TopAppBar
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.browse
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(component: BrowseComponent) {
    val model by component.model.subscribeAsState()

    val lazyListState = rememberLazyListState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var expandedBookIndex by rememberSaveable {
        mutableIntStateOf(model.books.indexOf(model.book))
    }

    var filteredBooks by remember { mutableStateOf(model.books) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val initialBookIndex = model.books.indexOf(model.book)
        expandedBookIndex = initialBookIndex
        delay(500.milliseconds)
        bringIntoViewRequester.bringIntoView()
    }

    LaunchedEffect(expandedBookIndex) {
        if (expandedBookIndex != -1) {
            delay(200.milliseconds)
            val visibleItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull {
                it.index == expandedBookIndex
            }
            if (visibleItem != null) {
                lazyListState.animateScrollBy(
                    value = visibleItem.offset.toFloat(),
                    animationSpec = tween(500)
                )
            } else {
                lazyListState.animateScrollToItem(expandedBookIndex)
            }
        }
    }

    LaunchedEffect(searchQuery) {
        filteredBooks = model.books.filter { book ->
            book.title.contains(searchQuery, ignoreCase = true)
                    || book.slug.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .padding(top = 36.dp, bottom = 18.dp)
    ) {
        TopAppBar(
            title = stringResource(Res.string.browse),
            actions = {
                Spacer(modifier = Modifier.weight(1f))
                SearchField(
                    searchQuery = searchQuery,
                    onValueChange = { searchQuery = it },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedIndicatorColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    borderColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(0.5f)
                )
            }
        ) {
            component.onBackClick()
        }

        Column(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState
            ) {
                itemsIndexed(filteredBooks) { index, book ->
                    BookItem(
                        book = book,
                        isExpanded = expandedBookIndex == index,
                        onToggle = {
                            expandedBookIndex = if (expandedBookIndex == index) -1 else index
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AnimatedVisibility(
                        visible = expandedBookIndex == index,
                        enter = expandVertically(
                            expandFrom = Alignment.Top,
                            animationSpec = tween(durationMillis = 500)
                        )
                    ) {
                        ChapterGrid(
                            chapters = book.chapters.size,
                            activeChapter = if (book == model.book) model.chapter?.number else null,
                            bringIntoViewRequester = bringIntoViewRequester,
                            modifier = Modifier.fillMaxWidth(),
                            onChapterClick = { chapter ->
                                component.onRefClick(
                                    RefOption(
                                        book = book.slug,
                                        chapter = chapter
                                    )
                                )
                            }
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}