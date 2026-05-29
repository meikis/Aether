package com.zhousl.aether.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebToolsClientTest {
    @Test
    fun fetchUrlAsMarkdownConvertsHtmlIntoMarkdown() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(
                    """
                    <html>
                      <head>
                        <title>Example Page</title>
                      </head>
                      <body>
                        <header>Top nav</header>
                        <main>
                          <article>
                            <h1>Hello from Aether</h1>
                            <p>Read the <a href="/docs">relative link</a>.</p>
                          </article>
                        </main>
                      </body>
                    </html>
                    """.trimIndent(),
                ),
        )
        server.start()

        try {
            val client = WebToolsClient()
            val result = client.fetchUrlAsMarkdown(server.url("/page").toString()).getOrThrow()

            assertEquals("Example Page", result.title)
            assertEquals(server.url("/page").toString(), result.finalUrl)
            assertTrue(result.markdown.contains("Example Page"))
            assertTrue(result.markdown.contains("Hello from Aether"))
            assertTrue(result.markdown.contains("relative link"))
            assertTrue(result.markdown.contains(server.url("/docs").toString()))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun tavilySearchUsesBearerAuthAndSearchEndpoint() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "query": "android agent",
                      "answer": "summary",
                      "results": [
                        {
                          "title": "Result One",
                          "url": "https://example.com/result",
                          "content": "Snippet"
                        }
                      ],
                      "response_time": "0.42",
                      "usage": { "credits": 1 },
                      "request_id": "req-123"
                    }
                    """.trimIndent(),
                ),
        )
        server.start()

        try {
            val client = WebToolsClient(tavilyBaseUrl = server.url("/").toString())
            val response = client.searchTavily(
                apiKey = "tvly-test",
                request = TavilySearchRequest(
                    query = "android agent",
                    searchDepth = "advanced",
                    includeRawContent = true,
                    includeDomains = listOf("example.com"),
                ),
            ).getOrThrow()

            assertEquals("summary", response.getString("answer"))

            val request = server.takeRequest()
            assertEquals("/search", request.path)
            assertEquals("Bearer tvly-test", request.getHeader("Authorization"))

            val payload = JSONObject(request.body.readUtf8())
            assertEquals("android agent", payload.getString("query"))
            assertEquals("advanced", payload.getString("search_depth"))
            assertEquals("markdown", payload.getString("include_raw_content"))
            assertEquals("basic", payload.getString("include_answer"))
            assertEquals(true, payload.getBoolean("include_favicon"))
            assertEquals("example.com", payload.getJSONArray("include_domains").getString(0))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun tavilySearchAcceptsFullSearchEndpoint() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""{"query":"android agent","results":[]}"""),
        )
        server.start()

        try {
            val client = WebToolsClient()
            client.searchTavily(
                apiKey = "tvly-test",
                baseUrl = server.url("/proxy/tavily/search").toString(),
                request = TavilySearchRequest(query = "android agent"),
            ).getOrThrow()

            val request = server.takeRequest()
            assertEquals("/proxy/tavily/search", request.path)
        } finally {
            server.shutdown()
        }
    }
}
