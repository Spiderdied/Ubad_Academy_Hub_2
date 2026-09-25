package com.ubad.academy.ui.screens.blog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ubad.academy.R
import com.ubad.academy.core.BlogHtml
import com.ubad.academy.core.External
import com.ubad.academy.core.Web
import com.ubad.academy.ui.theme.UbadThemeExt

/** Opens a post link natively: YouTube → app/browser, everything else → Custom Tab. */
fun openBlogLink(context: android.content.Context, url: String, toolbar: Color) {
    if (Web.youtubeVideoId(url) != null) External.openYoutube(context, url, toolbar) else External.openInTab(context, url, toolbar)
}

/** Renders sanitized post blocks with Compose (no WebView). */
@Composable
fun BlogBlocks(blocks: List<BlogHtml.Block>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface
    val link = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val open: (String) -> Unit = { openBlogLink(context, it, toolbar) }
    fun annotate(spans: List<BlogHtml.Span>): AnnotatedString = spansToAnnotated(spans, link, codeBg, open)
    val body = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.15f)

    Column(modifier) {
        blocks.forEach { b ->
            when (b) {
                is BlogHtml.Block.Paragraph -> Text(remember(b) { annotate(b.spans) }, style = body, modifier = Modifier.padding(vertical = 6.dp))
                is BlogHtml.Block.Heading -> Text(
                    remember(b) { annotate(b.spans) },
                    style = when (b.level) { 1 -> MaterialTheme.typography.headlineSmall; 2 -> MaterialTheme.typography.titleLarge; 3 -> MaterialTheme.typography.titleMedium; else -> MaterialTheme.typography.titleSmall },
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                )
                is BlogHtml.Block.ListItem -> Row(Modifier.padding(start = (12 * b.depth).dp, top = 3.dp, bottom = 3.dp)) {
                    Text(if (b.index != null) "${b.index}." else "•", style = body, modifier = Modifier.width(26.dp))
                    Text(remember(b) { annotate(b.spans) }, style = body)
                }
                is BlogHtml.Block.Quote -> Row(Modifier.padding(vertical = 8.dp).height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(12.dp))
                    Text(remember(b) { annotate(b.spans) }, style = body.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is BlogHtml.Block.Preformatted -> Text(
                    b.text, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(MaterialTheme.shapes.small).background(codeBg)
                        .horizontalScroll(rememberScrollState()).padding(12.dp),
                )
                is BlogHtml.Block.Image -> AsyncImage(
                    model = b.src, contentDescription = b.alt.ifEmpty { null }, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth().heightIn(min = 40.dp).clip(MaterialTheme.shapes.medium)
                        .then(if (b.href != null) Modifier.clickable(role = Role.Button) { open(b.href) } else Modifier),
                )
                is BlogHtml.Block.Embed -> OutlinedButton(onClick = { open(b.url) }, modifier = Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.blog_openEmbed))
                }
                BlogHtml.Block.Rule -> HorizontalDivider(Modifier.padding(vertical = 12.dp), color = UbadThemeExt.colors.line)
            }
        }
    }
}

private fun spansToAnnotated(spans: List<BlogHtml.Span>, link: Color, codeBg: Color, open: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    spans.forEach { s ->
        val style = SpanStyle(
            fontWeight = if (s.bold) FontWeight.Bold else null,
            fontStyle = if (s.italic) FontStyle.Italic else null,
            textDecoration = if (s.underline) TextDecoration.Underline else null,
            fontFamily = if (s.code) FontFamily.Monospace else null,
            background = if (s.code) codeBg else Color.Unspecified,
        )
        val href = s.href
        if (href != null) {
            withLink(LinkAnnotation.Url(href, TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline))) { open(href) }) {
                withStyle(style) { append(s.text) }
            }
        } else withStyle(style) { append(s.text) }
    }
}

