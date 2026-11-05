package app.ember.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Turns chats into an EPUB book: cover from the character's photo, a chapter per chat, time skip or scene change. */
object BookExport {

    private data class Chapter(val title: String, val paragraphs: List<String>)

    fun export(context: Context, uri: Uri, character: Character, chats: List<Chat>) {
        val persona = chats.firstNotNullOfOrNull { Store.personaFor(it, character) }
        val charName = character.name.ifBlank { "Untitled" }
        val userName = persona?.name?.ifBlank { null } ?: "You"
        val fill = { s: String ->
            s.replace("{{char}}", charName).replace("{{user}}", userName)
                .replace("{{character}}", charName).replace("{{persona}}", userName)
        }

        // Build chapters: every chat starts one; messages marked as a chapter (time skips etc.) start another.
        val chapters = ArrayList<Chapter>()
        for (chat in chats.sortedBy { it.createdAt }) {
            var current = ArrayList<String>()
            fun close() {
                if (current.isNotEmpty()) chapters.add(Chapter("Chapter ${chapters.size + 1}", current))
                current = ArrayList()
            }
            for (m in chat.messages) {
                if (m.chapter) close()
                val paras = fill(m.text).replace("\r", "").split(Regex("\n\\s*\n|\n")).map { it.trim() }.filter { it.isNotEmpty() }
                current.addAll(paras)
            }
            close()
        }
        require(chapters.isNotEmpty()) { "There's nothing to export yet." }

        val cover = character.avatar?.let { coverJpeg(it) }
        val coverType = cover?.let { "image/jpeg" }

        context.contentResolver.openOutputStream(uri)!!.use { out ->
            ZipOutputStream(out).use { zip ->
                // The mimetype must come first and uncompressed.
                val mime = "application/epub+zip".toByteArray()
                zip.putNextEntry(ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = mime.size.toLong()
                    crc = CRC32().apply { update(mime) }.value
                })
                zip.write(mime); zip.closeEntry()

                fun put(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }

                put("META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>""")

                put("OEBPS/style.css", """
body { font-family: serif; line-height: 1.5; margin: 0 1em; }
h1 { text-align: center; margin: 2em 0 1.5em; font-weight: normal; }
p { margin: 0; text-indent: 1.4em; }
p.first { text-indent: 0; }
.title { text-align: center; margin-top: 30%; }
.title h1 { font-size: 2em; margin-bottom: .3em; }
.title p { text-indent: 0; text-align: center; font-style: italic; }
.cover { text-align: center; margin: 0; padding: 0; }
.cover img { max-width: 100%; max-height: 100%; }
""".trim())

                val ext = if (coverType == "image/png") "png" else "jpg"
                if (cover != null && coverType != null) {
                    zip.putNextEntry(ZipEntry("OEBPS/cover.$ext")); zip.write(cover); zip.closeEntry()
                    put("OEBPS/cover.xhtml", page("Cover", """<div class="cover"><img src="cover.$ext" alt="Cover"/></div>"""))
                }
                val subtitle = character.description.trim().lines().firstOrNull().orEmpty()
                put("OEBPS/title.xhtml", page(charName, """<div class="title"><h1>${esc(charName)}</h1>""" +
                    (if (subtitle.isNotEmpty()) "<p>${esc(fill(subtitle))}</p>" else "") +
                    "<p>${esc(userName)} &amp; ${esc(charName)}</p></div>"))

                chapters.forEachIndexed { i, ch ->
                    val body = StringBuilder("<h1>${esc(ch.title)}</h1>\n")
                    ch.paragraphs.forEachIndexed { j, p ->
                        body.append(if (j == 0) "<p class=\"first\">" else "<p>").append(inline(p)).append("</p>\n")
                    }
                    put("OEBPS/ch${i + 1}.xhtml", page(ch.title, body.toString()))
                }

                val navItems = chapters.indices.joinToString("\n") { "<li><a href=\"ch${it + 1}.xhtml\">Chapter ${it + 1}</a></li>" }
                put("OEBPS/nav.xhtml", """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Contents</title></head>
<body><nav epub:type="toc" id="toc"><h1>Contents</h1><ol>
$navItems
</ol></nav></body></html>""")

                val uid = "ember-" + character.id
                val ncxPoints = chapters.indices.joinToString("\n") {
                    "<navPoint id=\"n${it + 1}\" playOrder=\"${it + 1}\"><navLabel><text>Chapter ${it + 1}</text></navLabel><content src=\"ch${it + 1}.xhtml\"/></navPoint>"
                }
                put("OEBPS/toc.ncx", """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
<head><meta name="dtb:uid" content="$uid"/></head>
<docTitle><text>${esc(charName)}</text></docTitle>
<navMap>
$ncxPoints
</navMap></ncx>""")

                val modified = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
                val hasCover = cover != null && coverType != null
                val manifest = buildString {
                    append("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
                    append("<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n")
                    append("<item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>\n")
                    if (hasCover) {
                        append("<item id=\"cover-image\" href=\"cover.$ext\" media-type=\"$coverType\" properties=\"cover-image\"/>\n")
                        append("<item id=\"cover\" href=\"cover.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
                    }
                    append("<item id=\"title\" href=\"title.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
                    chapters.indices.forEach { append("<item id=\"ch${it + 1}\" href=\"ch${it + 1}.xhtml\" media-type=\"application/xhtml+xml\"/>\n") }
                }
                val spine = buildString {
                    if (hasCover) append("<itemref idref=\"cover\"/>\n")
                    append("<itemref idref=\"title\"/>\n")
                    chapters.indices.forEach { append("<itemref idref=\"ch${it + 1}\"/>\n") }
                }
                put("OEBPS/content.opf", """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="uid">$uid</dc:identifier>
<dc:title>${esc(charName)}</dc:title>
<dc:creator>${esc(userName)} &amp; ${esc(charName)}</dc:creator>
<dc:language>en</dc:language>
<meta property="dcterms:modified">$modified</meta>
${if (hasCover) "<meta name=\"cover\" content=\"cover-image\"/>" else ""}
</metadata>
<manifest>
$manifest</manifest>
<spine toc="ncx">
$spine</spine>
</package>""")
            }
        }
    }

    private fun page(title: String, body: String) = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>${esc(title)}</title><link rel="stylesheet" type="text/css" href="style.css"/></head>
<body>
$body
</body></html>"""

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Escapes a paragraph and turns _thoughts_ / *thoughts* into italics; drops ** bold markers. */
    private fun inline(p: String): String {
        val e = esc(p.replace("**", ""))
        return e.replace(Regex("(?<![\\w])[_*]([^_*\\n]+?)[_*](?![\\w])")) { "<em>${it.groupValues[1]}</em>" }
    }

    /** The photo as a JPEG no bigger than e-readers need (photos can be in any format). */
    private fun coverJpeg(path: String): ByteArray? = runCatching {
        if (!File(path).exists()) return null
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= 1600) sample *= 2
        val bmp = android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
    }.getOrNull()
}
