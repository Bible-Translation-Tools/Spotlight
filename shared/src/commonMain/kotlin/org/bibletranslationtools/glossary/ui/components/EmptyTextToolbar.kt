package org.bibletranslationtools.glossary.ui.components

import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import kotlinx.coroutines.awaitCancellation

object EmptyTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {}

    override fun hide() {}
}

/**
 * Hides the text context menu used when ComposeFoundationFlags.isNewContextMenuEnabled is on
 * (the default on Android since Compose 1.12), which ignores LocalTextToolbar.
 * Suspends like an open menu that draws nothing, until selection code cancels it.
 */
object EmptyTextContextMenuProvider : TextContextMenuProvider {
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        awaitCancellation()
    }
}
