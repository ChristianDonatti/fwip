package io.github.christiandonatti.fwip.ui

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.ui.theme.Accent
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import io.github.christiandonatti.fwip.ui.theme.ThemeMode

/** Theme and accent colour, applied the moment they are tapped. */
@Composable
fun AppearancePicker(modifier: Modifier = Modifier) {
    val ui = UiSettings.get(LocalContext.current)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(str(R.string.appearance_theme), style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(FwipColors.Surface)
                .border(1.dp, FwipColors.Stroke, CircleShape)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                val on = ui.theme == mode
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(CircleShape)
                        .background(if (on) FwipColors.Text else Color.Transparent)
                        .clickable(role = Role.RadioButton) { ui.setTheme(mode) }
                        .semantics { selected = on },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(mode.label, style = MaterialTheme.typography.labelMedium, color = if (on) FwipColors.Ink else FwipColors.TextSoft, maxLines = 1)
                }
            }
        }
        Text(str(R.string.appearance_accent), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Accent.entries.forEach { accent ->
                val on = ui.accent == accent
                val color = if (FwipColors.isLight) accent.onLight else accent.onDark
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(if (on) 3.dp else 1.dp, if (on) FwipColors.Text else FwipColors.Stroke, CircleShape)
                        .padding(if (on) 6.dp else 4.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable(role = Role.RadioButton) { ui.setAccent(accent) }
                        .semantics {
                            contentDescription = accent.label
                            selected = on
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(Icons.Filled.Check, null, tint = if (FwipColors.isLight) Color.White else Color(0xFF16130C), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun AppearanceDialog(onDismiss: () -> Unit) {
    val ui = UiSettings.get(LocalContext.current)
    FwipDialog(onDismiss) {
        DialogTitle(str(R.string.appearance), onDismiss)
        AppearancePicker()
        Text(str(R.string.appearance_reader_note), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(FwipColors.Surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(str(R.string.appearance_cover_size), style = MaterialTheme.typography.labelLarge)
            Text(str(R.string.appearance_pinch_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            if (ui.coverScale !in 0.97f..1.03f) {
                Text(
                    str(R.string.appearance_cover_reset),
                    style = MaterialTheme.typography.labelMedium,
                    color = FwipColors.Amber,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) {
                            ui.scaleCovers(1f / ui.coverScale)
                            ui.saveCoverScale()
                        }
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}
