package app.ember.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Filled text field in the burgundy style, with label above and an optional counter. */
@Composable
fun EmberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    required: Boolean = false,
    minLines: Int = 1,
    singleLine: Boolean = false,
    trailingInfo: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                buildAnnotatedString {
                    append(label)
                    if (required) withStyle(SpanStyle(color = Color(0xFFE5655E))) { append(" *") }
                },
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = EmberColors.Text,
            )
            Spacer(Modifier.weight(1f))
            if (trailingInfo != null) Text(trailingInfo, fontSize = 12.sp, color = EmberColors.Dim)
        }
        Spacer(Modifier.height(8.dp))
        TextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, color = EmberColors.Dim) },
            minLines = minLines,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors(),
        )
    }
}

@Composable
fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = EmberColors.Field,
    unfocusedContainerColor = EmberColors.Field,
    disabledContainerColor = EmberColors.Field,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    focusedTextColor = EmberColors.Text,
    unfocusedTextColor = EmberColors.Text,
    cursorColor = EmberColors.RedSoft,
)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
        fontSize = 12.sp, letterSpacing = 1.sp, color = EmberColors.Dim,
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .background(EmberColors.Surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) { content() }
}

/**
 * Novel-style formatting: "dialogue" in bold, _thoughts_ (or *thoughts*) in italics,
 * everything else as plain narration.
 */
fun formatStory(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    var bold = false
    var italic = false
    var italicMark = ' '
    val sb = StringBuilder()

    fun flush() {
        if (sb.isEmpty()) return
        val style = SpanStyle(
            fontWeight = if (bold) FontWeight.Bold else null,
            fontStyle = if (italic) FontStyle.Italic else null,
        )
        withStyle(style) { append(sb.toString()) }
        sb.clear()
    }

    fun isWordChar(c: Char?) = c != null && c.isLetterOrDigit()

    while (i < text.length) {
        val c = text[i]
        val prev = if (i > 0) text[i - 1] else null
        val next = if (i + 1 < text.length) text[i + 1] else null
        when {
            c == '"' || c == '“' || c == '”' -> {
                if (!bold) { flush(); bold = true; sb.append(c) }
                else { sb.append(c); flush(); bold = false }
            }
            (c == '_' || c == '*') && !italic && !isWordChar(prev) && next != null && !next.isWhitespace() -> {
                flush(); italic = true; italicMark = c
            }
            italic && c == italicMark && !isWordChar(next) -> {
                flush(); italic = false
            }
            c == '\n' -> {
                // A paragraph break closes any open dialogue or thought.
                flush(); bold = false; italic = false; sb.append(c)
            }
            else -> sb.append(c)
        }
        i++
    }
    flush()
}

fun wordCount(s: String): Int = s.split(Regex("\\s+")).count { it.isNotBlank() }
