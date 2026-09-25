package com.ubad.academy.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Native replacement for the web blog helpers (`blogTextFromHtml`, `blogTitle`,
 * `blogImage`, `blogExcerpt`, `sanitizeBlogHtml`). Posts are never shown in a WebView:
 * the HTML is parsed with Jsoup into a small whitelist of blocks and rendered with Compose.
 * Scripts, styles, forms and event handlers can't survive because only text, links
 * (http/https) and images (http/https) are carried over.
 */
object BlogHtml {

    // ── model ──
    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val code: Boolean = false,
        val href: String? = null,
    )

    sealed interface Block {
        data class Paragraph(val spans: List<Span>) : Block
        data class Heading(val level: Int, val spans: List<Span>) : Block
        /** [index] is the 1-based number for ordered lists, null for bullets. */
        data class ListItem(val depth: Int, val index: Int?, val spans: List<Span>) : Block
        data class Quote(val spans: List<Span>) : Block
        data class Preformatted(val text: String) : Block
        data class Image(val src: String, val alt: String, val href: String?) : Block
        /** An `<iframe>` (e.g. a YouTube embed) — opened externally, never embedded. */
        data class Embed(val url: String) : Block
        data object Rule : Block
    }

    private val DROP = setOf("script", "style", "link", "meta", "object", "embed", "form", "button", "input", "textarea", "select", "noscript", "template", "svg", "head", "title")
    private val WS = Regex("\\s+")

    private fun parse(html: String?): Document = Jsoup.parse(html.orEmpty()).also { d ->
        d.select(DROP.joinToString(",")).remove()
    }

    /** `blogTextFromHtml` */
    fun text(html: String?): String = parse(html).body().text().replace(WS, " ").trim()

    /** `blogTitle` — the post title, or the first heading/strong text of the content. */
    fun title(rawTitle: String?, html: String?): String {
        val direct = rawTitle.orEmpty().trim()
        if (direct.isNotEmpty()) return direct
        val h = parse(html).selectFirst("h1,h2,h3,h4,strong")?.text()?.replace(WS, " ")?.trim().orEmpty()
        return if (h.length > 100) h.take(97) + "…" else h
    }

    /** `blogImage` — the first `<img src>` (the Worker's `images[0].url` is preferred by the caller). */
    fun firstImage(html: String?): String? =
        parse(html).select("img[src]").firstNotNullOfOrNull { absUrl(it.attr("src")) }

    /** `blogExcerpt` */
    fun excerpt(html: String?): String {
        val t = text(html)
        return if (t.length > 190) t.take(187) + "…" else t
    }

    /** Only absolute http(s) (protocol-relative becomes https); everything else is dropped. */
    fun absUrl(raw: String?): String? {
        var s = raw?.trim().orEmpty()
        if (s.startsWith("//")) s = "https:$s"
        val lower = s.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
        if (s.any { it.isWhitespace() || it == '"' || it == '<' || it == '>' }) return null
        // Blogger/Google image hosts serve https; cleartext is blocked on modern Android.
        return if (lower.startsWith("http://")) "https://" + s.substring(7) else s
    }

    // ── blocks ──
    fun blocks(html: String?): List<Block> = Builder().apply { walk(parse(html).body()) ; flush() }.out.let(::tidy)

    private data class Style(val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false, val code: Boolean = false, val href: String? = null)

    private sealed interface Kind {
        data object Para : Kind
        data class Head(val level: Int) : Kind
        data class Item(val depth: Int, val index: Int?) : Kind
        data object Quote : Kind
    }

    private class Builder {
        val out = mutableListOf<Block>()
        private var spans = mutableListOf<Span>()
        private var kind: Kind = Kind.Para
        private var listDepth = 0

        fun flush() {
            val merged = normalize(spans)
            spans = mutableListOf()
            if (merged.isEmpty()) return
            out += when (val k = kind) {
                Kind.Para -> Block.Paragraph(merged)
                is Kind.Head -> Block.Heading(k.level, merged)
                is Kind.Item -> Block.ListItem(k.depth, k.index, merged)
                Kind.Quote -> Block.Quote(merged)
            }
        }

        private fun block(k: Kind, body: () -> Unit) {
            flush()
            val prev = kind
            kind = k
            body()
            flush()
            kind = prev
        }

        fun walk(node: Node, style: Style = Style()) {
            when (node) {
                is TextNode -> {
                    val t = node.wholeText.replace(WS, " ")
                    if (t.isNotEmpty()) spans += Span(t, style.bold, style.italic, style.underline, style.code, style.href)
                }
                is Element -> element(node, style)
            }
        }

        private fun children(e: Element, style: Style) = e.childNodes().toList().forEach { walk(it, style) }

        private fun element(e: Element, s: Style) {
            when (val tag = e.normalName()) {
                "br" -> spans += Span("\n", href = s.href)
                "hr" -> { flush(); out += Block.Rule }
                "img" -> {
                    val src = absUrl(e.attr("src")) ?: return
                    flush(); out += Block.Image(src, e.attr("alt").trim(), s.href)
                }
                "iframe" -> { absUrl(e.attr("src"))?.let { flush(); out += Block.Embed(it) } }
                "h1", "h2", "h3", "h4", "h5", "h6" -> block(Kind.Head(tag[1].digitToInt())) { children(e, s) }
                "blockquote" -> block(Kind.Quote) { children(e, s) }
                "pre" -> { flush(); e.wholeText().trimEnd().takeIf { it.isNotBlank() }?.let { out += Block.Preformatted(it) } }
                "ul", "ol" -> {
                    flush()
                    listDepth++
                    var n = e.attr("start").toIntOrNull() ?: 1
                    e.children().forEach { li ->
                        if (li.normalName() == "li") block(Kind.Item(listDepth, if (tag == "ol") n++ else null)) { children(li, s) }
                        else walk(li, s)
                    }
                    listDepth--
                }
                "tr" -> block(kind) {
                    e.children().forEachIndexed { i, cell -> if (i > 0) spans += Span(" | "); children(cell, s) }
                }
                "p", "div", "section", "article", "figure", "figcaption", "table", "tbody", "thead", "header", "footer", "main", "aside", "center", "dl", "dt", "dd", "li" ->
                    block(if (kind is Kind.Para) Kind.Para else kind) { children(e, s) }
                "b", "strong" -> children(e, s.copy(bold = true))
                "i", "em", "cite" -> children(e, s.copy(italic = true))
                "u", "ins" -> children(e, s.copy(underline = true))
                "code", "kbd", "samp", "tt" -> children(e, s.copy(code = true))
                "a" -> children(e, s.copy(href = absUrl(e.attr("href")) ?: s.href))
                "span", "font" -> {
                    val css = e.attr("style").lowercase()
                    children(e, s.copy(
                        bold = s.bold || Regex("font-weight\\s*:\\s*(bold|[6-9]00)").containsMatchIn(css),
                        italic = s.italic || css.contains("font-style: italic") || css.contains("font-style:italic"),
                    ))
                }
                else -> children(e, s)
            }
        }

    }

    /** Collapse whitespace across span boundaries, trim the block, and merge equal neighbours. */
    private fun normalize(spans: List<Span>): List<Span> {
        val res = mutableListOf<Span>()
        var lastSpace = true // leading whitespace is dropped
        for (sp in spans) {
            val sb = StringBuilder()
            for (ch in sp.text) {
                when {
                    ch == '\n' -> { while (sb.endsWith(" ")) sb.setLength(sb.length - 1); sb.append('\n'); lastSpace = true }
                    ch == ' ' -> if (!lastSpace) { sb.append(' '); lastSpace = true }
                    else -> { sb.append(ch); lastSpace = false }
                }
            }
            if (sb.isEmpty()) continue
            val prev = res.lastOrNull()
            if (prev != null && prev.copy(text = "") == sp.copy(text = "")) res[res.size - 1] = prev.copy(text = prev.text + sb)
            else res += sp.copy(text = sb.toString())
        }
        // Trim trailing whitespace/newlines and leading newlines.
        while (res.isNotEmpty()) {
            val l = res.last(); val t = l.text.trimEnd()
            if (t.isEmpty()) res.removeAt(res.size - 1) else { res[res.size - 1] = l.copy(text = t); break }
        }
        while (res.isNotEmpty()) {
            val f = res.first(); val t = f.text.trimStart()
            if (t.isEmpty()) res.removeAt(0) else { res[0] = f.copy(text = t); break }
        }
        return res
    }

    /** Drop consecutive rules and rules at the edges. */
    private fun tidy(list: List<Block>): List<Block> {
        val out = mutableListOf<Block>()
        list.forEach { b -> if (b is Block.Rule && (out.isEmpty() || out.last() is Block.Rule)) return@forEach; out += b }
        while (out.lastOrNull() is Block.Rule) out.removeAt(out.size - 1)
        return out
    }
}
