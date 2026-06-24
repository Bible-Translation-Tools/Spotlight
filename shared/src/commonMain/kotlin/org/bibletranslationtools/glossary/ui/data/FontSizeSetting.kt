package org.bibletranslationtools.glossary.ui.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.large
import spotlight.shared.generated.resources.medium
import spotlight.shared.generated.resources.small

class FontSizeSetting(
    override val name: String,
    override val value: TextUnit,
) : FontSetting<TextUnit> {
    @Composable
    override fun localize(): String {
        return when (this.name) {
            "small" -> stringResource(Res.string.small)
            "large" -> stringResource(Res.string.large)
            else -> stringResource(Res.string.medium)
        }
    }

    companion object {
        val SMALL = LineHeightSetting("small", 12.8.sp)
        val MEDIUM = LineHeightSetting("medium", 16.sp)
        val LARGE = LineHeightSetting("large", 20.sp)

        fun of(string: String): FontSetting<TextUnit> {
            return when (string) {
                "small" -> SMALL
                "large" -> LARGE
                else -> MEDIUM
            }
        }
    }
}

