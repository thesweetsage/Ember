package app.ember.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun EmberTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = EmberColors.Bg),
    )
}

@Composable
fun Avatar(path: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(EmberColors.Field),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(name.take(1).uppercase(), color = EmberColors.Muted, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
}

@Composable
fun SwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = EmberColors.Red, checkedThumbColor = Color.White,
    uncheckedTrackColor = EmberColors.Outline, uncheckedThumbColor = EmberColors.Muted,
    uncheckedBorderColor = EmberColors.Outline,
)

/** A grouped settings card like Tipsy's: rows separated by thin lines. */
@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, fontSize = 14.sp, color = EmberColors.Muted, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
        Column(
            Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp)
        ) { content() }
    }
}

@Composable
fun SettingRow(label: String, value: String = "", sub: String? = null, divider: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, color = EmberColors.Text)
            if (sub != null) Text(sub, fontSize = 12.5.sp, color = EmberColors.Muted)
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text(value, fontSize = 15.sp, color = EmberColors.Muted, maxLines = 1, modifier = Modifier.widthIn(max = 180.dp))
        }
        if (onClick != null) Text("  ›", fontSize = 16.sp, color = EmberColors.Muted)
    }
    if (divider) HorizontalDivider(color = EmberColors.Line)
}

@Composable
fun ToggleRow(label: String, checked: Boolean, sub: String? = null, divider: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, color = EmberColors.Text)
            if (sub != null) Text(sub, fontSize = 12.5.sp, color = EmberColors.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchColors())
    }
    if (divider) HorizontalDivider(color = EmberColors.Line)
}

/** A simple list picker in a dialog. [noneLabel] adds a first "none" option that picks null. */
@Composable
fun <T> PickerDialog(
    title: String,
    items: List<T>,
    label: (T) -> String,
    isSelected: (T) -> Boolean,
    onPick: (T?) -> Unit,
    onDismiss: () -> Unit,
    noneLabel: String? = null,
    noneSelected: Boolean = false,
    sub: ((T) -> String?)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (noneLabel != null) {
                    item { PickerRow(noneLabel, null, noneSelected) { onPick(null) } }
                }
                items(items) { t -> PickerRow(label(t), sub?.invoke(t), isSelected(t)) { onPick(t) } }
                if (items.isEmpty() && noneLabel == null) {
                    item { Text("Nothing here yet.", color = EmberColors.Muted) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = EmberColors.Surface,
    )
}

@Composable
private fun PickerRow(label: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) EmberColors.Field else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, color = EmberColors.Text)
            if (!sub.isNullOrBlank()) Text(sub, fontSize = 12.5.sp, color = EmberColors.Muted, maxLines = 2)
        }
        if (selected) Icon(Icons.Filled.Check, "Selected", tint = EmberColors.RedSoft)
    }
}

/** Text-entry dialog used for scene direction, pins, rewrites and similar. */
@Composable
fun TextDialog(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    hint: String = "",
    saveLabel: String = "Save",
    extra: (@Composable () -> Unit)? = null,
) {
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (hint.isNotEmpty()) {
                    Text(hint, fontSize = 13.sp, color = EmberColors.Muted)
                    Spacer(Modifier.size(10.dp))
                }
                androidx.compose.material3.TextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp),
                    colors = fieldColors(), shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(saveLabel) } },
        dismissButton = {
            Row {
                extra?.invoke()
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        containerColor = EmberColors.Surface,
    )
}
