package app.ember.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ember.data.Store
import app.ember.data.UsageRow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun money(v: Double) = "$" + when {
    v == 0.0 -> "0.00"
    v < 0.01 -> "%.3f".format(Locale.US, v)
    else -> "%.2f".format(Locale.US, v)
}

private fun tokens(n: Long) = when {
    n >= 1_000_000 -> "%.1fM".format(Locale.US, n / 1e6)
    n >= 1_000 -> "%.0fk".format(Locale.US, n / 1e3)
    else -> "$n"
}

/** Spending per month: total, by model and by character, plus an optional monthly budget warning. */
@Composable
fun CostScreen(onBack: () -> Unit) {
    var offset by remember { mutableStateOf(0) }
    val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -offset) }
    val month = Store.monthKey(cal.timeInMillis)
    val label = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(cal.time)
    val rows = Store.usage.filter { it.month == month }
    val total = rows.sumOf { it.cost }
    val budget = Store.settings.monthlyBudget
    var budgetText by remember { mutableStateOf(if (budget > 0) "%.2f".format(Locale.US, budget) else "") }

    Scaffold(containerColor = EmberColors.Bg, topBar = { EmberTopBar("Spending", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset += 1 }) { Icon(Icons.Filled.ChevronLeft, "Previous month") }
                Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { offset -= 1 }, enabled = offset > 0) { Icon(Icons.Filled.ChevronRight, "Next month") }
            }
            Column(
                Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(16.dp)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(money(total), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${tokens(rows.sumOf { it.input })} tokens sent · ${tokens(rows.sumOf { it.output })} written",
                    fontSize = 13.sp, color = EmberColors.Muted,
                )
                if (budget > 0 && offset == 0) {
                    Spacer(Modifier.height(10.dp))
                    val frac = (total / budget).toFloat().coerceIn(0f, 1f)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { frac }, modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = if (total >= budget) EmberColors.RedSoft else EmberColors.Red, trackColor = EmberColors.Line,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${money(total)} of your ${money(budget)} budget", fontSize = 12.5.sp, color = EmberColors.Muted)
                }
            }

            if (rows.isEmpty()) {
                Text("Nothing spent this month yet.", color = EmberColors.Muted, fontSize = 14.sp)
            } else {
                Breakdown("By model", rows.groupBy { it.model }.mapKeys { it.key.substringAfter('/').ifBlank { "Unknown" } })
                Breakdown("By character", rows.groupBy {
                    if (it.tag.isBlank()) "Ideas, builder and other" else Store.character(it.tag)?.name ?: "Deleted character"
                })
            }

            SettingsGroup("Monthly budget") {
                Column(Modifier.padding(vertical = 10.dp)) {
                    TextField(
                        value = budgetText,
                        onValueChange = { v ->
                            budgetText = v.filter { it.isDigit() || it == '.' || it == ',' }.take(7)
                            Store.updateSettings(Store.settings.copy(monthlyBudget = budgetText.replace(',', '.').toDoubleOrNull() ?: 0.0))
                        },
                        singleLine = true, placeholder = { Text("e.g. 10", color = EmberColors.Dim) },
                        prefix = { Text("$ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = fieldColors(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Chats show a warning once you pass it. Leave empty for no warning.", fontSize = 12.5.sp, color = EmberColors.Muted)
                }
            }
            Text(
                "OpenRouter reports the exact cost of every reply. For your backup model (DeepSeek) and anything else, the prices in Settings " +
                    "are used. Counting started with this version, so earlier spending isn't included.",
                fontSize = 12.5.sp, color = EmberColors.Dim,
            )
        }
    }
}

@Composable
private fun Breakdown(title: String, groups: Map<String, List<UsageRow>>) {
    SettingsGroup(title) {
        val sorted = groups.map { (k, v) -> Triple(k, v.sumOf { it.cost }, v.sumOf { it.input + it.output }) }.sortedByDescending { it.second }
        sorted.forEachIndexed { i, (name, cost, tok) ->
            SettingRow(name, value = money(cost), sub = "${tokens(tok)} tokens", divider = i < sorted.lastIndex)
        }
    }
}
