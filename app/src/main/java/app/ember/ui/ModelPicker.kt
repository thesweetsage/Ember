package app.ember.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/** One model from OpenRouter's list; prices are dollars per million tokens. */
data class OrModel(val id: String, val name: String, val priceIn: Double?, val priceOut: Double?)

private data class QuickPick(val id: String, val label: String, val note: String)

private val QUICK_PICKS = listOf(
    QuickPick("moonshotai/kimi-k2.6", "Kimi K2.6", "Excellent prose · thinks before writing"),
    QuickPick("minimax/minimax-m2-her", "MiniMax M2-her", "Built for roleplay · cheap"),
    QuickPick("qwen/qwen3-235b-a22b-2507", "Qwen3 235B Instruct", "Very cheap · no thinking"),
    QuickPick("mistralai/mistral-small-creative", "Mistral Small Creative", "Cheapest · small model"),
    QuickPick("z-ai/glm-5.2", "GLM 5.2", "Strongest open writer · pricier"),
    QuickPick("anthropic/claude-sonnet-4.6", "Claude Sonnet 4.6", "Best prose · most expensive · strict on explicit scenes"),
)

private var cachedModels: List<OrModel>? = null

/** OpenRouter's public model list (no key needed). */
private suspend fun loadOpenRouterModels(): List<OrModel> = withContext(Dispatchers.IO) {
    cachedModels ?: run {
        val text = URL("https://openrouter.ai/api/v1/models").openStream().bufferedReader().use { it.readText() }
        val arr = JSONObject(text).getJSONArray("data")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val p = o.optJSONObject("pricing")
            fun perM(key: String) = p?.optString(key)?.toDoubleOrNull()?.let { it * 1_000_000 }
            OrModel(o.optString("id"), o.optString("name").ifBlank { o.optString("id") }, perM("prompt"), perM("completion"))
        }.filter { it.id.isNotBlank() }.sortedBy { it.name.lowercase() }.also { cachedModels = it }
    }
}

private fun price(m: OrModel?): String {
    if (m?.priceIn == null || m.priceOut == null) return ""
    if (m.priceIn == 0.0 && m.priceOut == 0.0) return "free"
    return "$" + "%.2f".format(m.priceIn) + " in · $" + "%.2f".format(m.priceOut) + " out per 1M"
}

/**
 * Full-screen model chooser: a few recommended models, plus every model on OpenRouter with search.
 * [onPick] gets the model id and, when known, its prices (for the cost counter).
 */
@Composable
fun ModelPicker(current: String, onPick: (id: String, priceIn: Double?, priceOut: Double?) -> Unit, onDismiss: () -> Unit) {
    var all by remember { mutableStateOf(cachedModels) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var browsing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (all == null) {
            try { all = loadOpenRouterModels() } catch (e: Exception) { loadError = "Couldn't load OpenRouter's model list." }
        }
    }
    fun find(id: String) = all?.firstOrNull { it.id == id }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = EmberColors.Bg) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Choose a model", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text("Now using: $current", fontSize = 13.sp, color = EmberColors.Muted)
                Spacer(Modifier.height(12.dp))
                Row {
                    LengthChip("Recommended", !browsing) { browsing = false }
                    Spacer(Modifier.padding(4.dp))
                    LengthChip("All OpenRouter models", browsing) { browsing = true }
                }
                Spacer(Modifier.height(12.dp))
                if (browsing) {
                    TextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text("Search, e.g. kimi, qwen, roleplay", color = EmberColors.Dim) },
                        modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(24.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                loadError?.let { Text(it, color = EmberColors.RedSoft, fontSize = 13.sp) }
                LazyColumn(contentPadding = PaddingValues(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!browsing) {
                        items(QUICK_PICKS) { q ->
                            ModelRow(q.label, q.id, listOf(q.note, price(find(q.id))).filter { it.isNotBlank() }.joinToString("\n"), q.id == current) {
                                val m = find(q.id); onPick(q.id, m?.priceIn, m?.priceOut)
                            }
                        }
                        item {
                            Text(
                                "Switching keeps your chat. Regenerate a reply after switching and both versions stay as swipes, so you can compare.",
                                fontSize = 12.5.sp, color = EmberColors.Dim, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    } else {
                        val list = all.orEmpty().filter {
                            query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true)
                        }
                        if (all == null && loadError == null) item { Text("Loading…", color = EmberColors.Muted) }
                        items(list, key = { it.id }) { m ->
                            ModelRow(m.name, m.id, price(m), m.id == current) { onPick(m.id, m.priceIn, m.priceOut) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(title: String, id: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(14.dp))
            .border(1.5.dp, if (selected) EmberColors.Red else EmberColors.Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Text(title + if (selected) "  ✓" else "", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(id, fontSize = 12.sp, color = EmberColors.Muted)
        if (sub.isNotBlank()) Text(sub, fontSize = 12.5.sp, color = EmberColors.TextSoft, lineHeight = 17.sp)
    }
}
