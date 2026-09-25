package com.ubad.academy

import com.ubad.academy.core.BlogHtml
import com.ubad.academy.core.BlogHtml.Block
import com.ubad.academy.data.remote.BlogApi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlogHtmlTest {
    private val blogger = """
        <style>.post{color:red} body{margin:0}</style>
        <script>alert('x')</script>
        <div class="post" onclick="evil()">
          <h2>مرحبا بالعالم</h2>
          <p>Hello <b>bold</b> and <a href="javascript:alert(1)">bad</a> and <a href="https://ubad34.blogspot.com/p/x.html">good</a>.</p>
          <a href="http://blogger.googleusercontent.com/big.jpg"><img src="http://blogger.googleusercontent.com/img.jpg" alt="pic"></a>
          <ul><li>one</li><li>two</li></ul>
          <ol start="3"><li>three</li></ol>
          <iframe src="https://www.youtube.com/embed/abc123"></iframe>
          <form><input value="x"></form>
        </div>
    """.trimIndent()

    @Test fun textDropsStyleAndScript() {
        val t = BlogHtml.text(blogger)
        assertFalse(t.contains("color:red"))
        assertFalse(t.contains("alert"))
        assertTrue(t.startsWith("مرحبا بالعالم Hello bold"))
    }

    @Test fun titleFallsBackToFirstHeading() {
        assertEquals("Real", BlogHtml.title(" Real ", blogger))
        assertEquals("مرحبا بالعالم", BlogHtml.title("", blogger))
        assertEquals(98, BlogHtml.title(null, "<h1>${"x".repeat(150)}</h1>").length)
    }

    @Test fun excerptIsCapped() {
        val e = BlogHtml.excerpt("<p>${"word ".repeat(100)}</p>")
        assertEquals(188, e.length)
        assertTrue(e.endsWith("…"))
    }

    @Test fun firstImageUpgradesToHttps() =
        assertEquals("https://blogger.googleusercontent.com/img.jpg", BlogHtml.firstImage(blogger))

    @Test fun unsafeUrlsAreRejected() {
        assertNull(BlogHtml.absUrl("javascript:alert(1)"))
        assertNull(BlogHtml.absUrl("data:text/html,x"))
        assertNull(BlogHtml.absUrl("/relative"))
        assertEquals("https://x.com/a", BlogHtml.absUrl("//x.com/a"))
    }

    @Test fun blocksAreWhitelisted() {
        val b = BlogHtml.blocks(blogger)
        assertEquals(Block.Heading(2, listOf(BlogHtml.Span("مرحبا بالعالم"))), b[0])
        val p = b[1] as Block.Paragraph
        assertEquals("Hello bold and bad and good.", p.spans.joinToString("") { it.text })
        assertTrue(p.spans.first { it.text == "bold" }.bold)
        assertNull(p.spans.first { it.text.contains("bad") }.href) // javascript: link stripped
        assertEquals("https://ubad34.blogspot.com/p/x.html", p.spans.first { it.text == "good" }.href)
        val img = b[2] as Block.Image
        assertEquals("pic", img.alt)
        assertEquals("https://blogger.googleusercontent.com/big.jpg", img.href)
        assertEquals(Block.ListItem(1, null, listOf(BlogHtml.Span("one"))), b[3])
        assertEquals(Block.ListItem(1, 3, listOf(BlogHtml.Span("three"))), b[5])
        assertEquals(Block.Embed("https://www.youtube.com/embed/abc123"), b[6])
        assertEquals(7, b.size) // form/input/script/style produce nothing
    }

    @Test fun brAndWhitespace() {
        val b = BlogHtml.blocks("<p>  a   b<br>  c  </p><hr><hr>")
        assertEquals(listOf(Block.Paragraph(listOf(BlogHtml.Span("a b\nc")))), b)
    }

    @Test fun workerPageParsesAndSendsToken() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"blog":{"id":"1"},"posts":[{"id":"9","title":"T","content":"<p>x</p>","published":"2026-01-02T10:00:00-08:00","images":[{"url":"https://i/x.jpg"}],"author":{"displayName":"a"}}],"nextPageToken":"NEXT"}"""))
            server.enqueue(MockResponse().setResponseCode(503))
            val api = BlogApi(OkHttpClient(), server.url("/").toString())
            val page = api.page()
            assertEquals("9", page.posts!!.single().id)
            assertEquals("https://i/x.jpg", page.posts!!.single().images!!.single().url)
            assertEquals("NEXT", page.nextPageToken)
            val failed = runCatching { api.page("NEXT") }.exceptionOrNull()
            assertTrue(failed is BlogApi.HttpException)
            assertEquals("/", server.takeRequest().path)
            assertEquals("/?pageToken=NEXT", server.takeRequest().path)
        }
    }
}
