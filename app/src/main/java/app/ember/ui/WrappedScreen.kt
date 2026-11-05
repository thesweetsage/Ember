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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ember.data.Store
import app.ember.data.Tags
import java.util.Calendar
import java.util.Locale

private fun big(n: Long) = String.format(Locale.US, "%,d", n)

/** Ember Wrapped: your stories in numbers, for this year or all time. */
@Composable
fun WrappedScreen(onBack: () -> Unit) {
    var allTime by remember { mutableStateOf(false) }
    val year = Calendar.getInstance().get(Calendar.YEAR)
    val yearStart = Calendar.getInstance().apply { set(year, 0, 1, 0, 0, 0) }.timeInMillis
    // Messages have no dates, so "this year" means chats you were active in this year.
    val chats = Store.chats.filter { allTime || it.updatedAt >= yearStart }
    val characters = Store.characters

    fun words(t: String) = wordCount(t).toLong()
    val mine = chats.sumOf { c -> c.messages.filter { it.fromUser }.sumOf { words(it.text) } }
    val theirs = chats.sumOf { c -> c.messages.filter { !it.fromUser }.sumOf { words(it.text) } }
    val messages = chats.sumOf { it.messages.size }.toLong()
    val byCharacter = chats.groupBy { it.characterId }
        .mapValues { (_, cs) -> cs.sumOf { c -> c.messages.sumOf { words(it.text) } } }
        .filterKeys { Store.character(it) != null }
        .toList().sortedByDescending { it.second }
    val longest = chats.maxByOrNull { c -> c.messages.sumOf { words(it.text) } }
    val longestWords = longest?.messages?.sumOf { words(it.text) } ?: 0
    val tagWeights = HashMap<String, Long>()
    byCharacter.forEach { (id, w) -> Store.character(id)?.tags?.forEach { t -> tagWeights[Tags.name(t)] = (tagWeights[Tags.name(t)] ?: 0) + w } }
    val topTags = tagWeights.toList().sortedByDescending { it.second }.take(5).map { it.first }
    val rerolls = chats.sumOf { c -> c.messages.filter { !it.fromUser }.sumOf { it.versions.size - 1 } }.toLong()
    val mostRerolled = chats.flatMap { it.messages }.filter { !it.fromUser }.maxOfOrNull { it.versions.size } ?: 1
    val newChars = characters.count { allTime || it.createdAt >= yearStart }
    val chapters = chats.sumOf { c -> c.messages.count { it.chapter } }
    val spent = Store.usage.filter { allTime || it.month.startsWith("$year") }.sumOf { it.cost }

    Scaffold(containerColor = EmberColors.BgDeep, topBar = { EmberTopBar("Ember Wrapped", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LengthChip("$year", !allTime) { allTime = false }
                LengthChip("All time", allTime) { allTime = true }
            }
            if (messages == 0L) {
                Text("No stories yet for this period. Go write some!", color = EmberColors.Muted)
                return@Column
            }

            WrapCard(Color(0xFF7A1E2C), Color(0xFF3A1016)) {
                Label("Words written together")
                Big(big(mine + theirs))
                Text(
                    "You wrote ${big(mine)}, they wrote ${big(theirs)}. " +
                        novels(mine + theirs),
                    color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp,
                )
            }

            byCharacter.firstOrNull()?.let { (id, w) ->
                val c = Store.character(id)!!
                WrapCard(Color(0xFF5B2333), Color(0xFF221016)) {
                    Label("Your favourite")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.avatar, c.name, 64.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(c.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("${big(w)} words together", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                        }
                    }
                    if (byCharacter.size > 1) {
                        Spacer(Modifier.height(12.dp))
                        byCharacter.drop(1).take(4).forEachIndexed { i, (cid, cw) ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text("${i + 2}.  ${Store.character(cid)?.name.orEmpty()}", color = Color.White, fontSize = 15.sp,
                                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(big(cw), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            longest?.let { l ->
                WrapCard(Color(0xFF8A3B2A), Color(0xFF2E1410)) {
                    Label("Your longest story")
                    Big(big(longestWords) + " words")
                    Text(
                        "With ${Store.character(l.characterId)?.name ?: "someone"}, over ${l.messages.size} messages. ${pages(longestWords)}",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp,
                    )
                }
            }

            if (topTags.isNotEmpty()) {
                WrapCard(Color(0xFF4A1F3D), Color(0xFF1E0D19)) {
                    Label("Your type")
                    topTags.forEachIndexed { i, t ->
                        Text(t, color = Color.White, fontSize = (26 - i * 3).sp, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.SemiBold)
                    }
                }
            }

            WrapCard(Color(0xFF3E2A22), Color(0xFF1A110E)) {
                Label("The rest")
                StatLine("Messages", big(messages))
                StatLine("Characters you talked to", "${byCharacter.size}")
                StatLine(if (allTime) "Characters created" else "New characters this year", "$newChars")
                StatLine("Replies rerolled", big(rerolls))
                if (mostRerolled > 2) StatLine("Most rerolls on one reply", "$mostRerolled versions")
                if (chapters > 0) StatLine("Time skips and scene changes", "$chapters")
                if (spent > 0) StatLine("Spent on models", "$" + "%.2f".format(Locale.US, spent))
            }
            Text(
                if (allTime) "Counted from every chat in the app." else "Counted from chats you were active in this year.",
                color = EmberColors.Dim, fontSize = 12.sp,
            )
        }
    }
}

private fun novels(words: Long): String = when {
    words >= 90_000 -> "That's about ${"%.1f".format(Locale.US, words / 90_000.0)} novels."
    words >= 20_000 -> "That's about ${words * 100 / 90_000}% of a novel."
    else -> "That's about ${(words / 300).coerceAtLeast(1)} book pages."
}

private fun pages(words: Long) = "About ${(words / 300).coerceAtLeast(1)} pages in a paperback."

@Composable
private fun WrapCard(top: Color, bottom: Color, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(top, bottom)), RoundedCornerShape(22.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable private fun Label(t: String) = Text(t.uppercase(), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
@Composable private fun Big(t: String) = Text(t, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp)

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
