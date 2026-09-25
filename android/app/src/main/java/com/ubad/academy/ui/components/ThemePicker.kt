package com.ubad.academy.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ubad.academy.R
import com.ubad.academy.domain.model.ThemeId
import com.ubad.academy.ui.theme.swatch

@StringRes
fun ThemeId.labelRes(): Int = when (this) {
    ThemeId.DARK -> R.string.set_th_dark
    ThemeId.OLED -> R.string.set_th_oled
    ThemeId.LIGHT -> R.string.set_th_light
    ThemeId.PAPER -> R.string.set_th_paper
    ThemeId.SAGE -> R.string.set_th_sage
    ThemeId.ROSE -> R.string.set_th_rose
}

/** The six theme swatches (web `.onboard-theme` / theme grid). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePicker(selected: ThemeId, onSelect: (ThemeId) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeId.entries.forEach { th ->
            val on = th == selected
            Surface(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.widthIn(min = 92.dp).clip(MaterialTheme.shapes.medium)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(th) },
            ) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(th.swatch())
                            .border(1.dp, if (th.isDark) Color.White.copy(alpha = .25f) else Color.Black.copy(alpha = .15f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(Icons.Filled.Check, null, tint = if (th.isDark) Color.White else Color.Black, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(th.labelRes()), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
