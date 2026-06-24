package org.bibletranslationtools.glossary.ui.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.default
import spotlight.shared.generated.resources.large
import spotlight.shared.generated.resources.small

class LineHeightSetting(
    override val name: String,
    override val value: TextUnit
) : FontSetting<TextUnit> {
    @Composable
    override fun localize(): String {
        return when (this.name) {
            SMALL_NAME -> stringResource(Res.string.small)
            LARGE_NAME -> stringResource(Res.string.large)
            else -> stringResource(Res.string.default)
        }
    }

    companion object {
        private const val SMALL_NAME = "small"
        private const val DEFAULT_NAME = "default"
        private const val LARGE_NAME = "large"

        val SMALL = LineHeightSetting(SMALL_NAME, 16.sp)
        val DEFAULT = LineHeightSetting(DEFAULT_NAME, 32.sp)
        val LARGE = LineHeightSetting(LARGE_NAME, 48.sp)

        fun of(string: String): FontSetting<TextUnit> {
            return when (string) {
                SMALL_NAME -> SMALL
                LARGE_NAME -> LARGE
                else -> DEFAULT
            }
        }
    }
}
