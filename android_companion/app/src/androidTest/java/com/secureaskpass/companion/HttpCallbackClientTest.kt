package com.secureaskpass.companion

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HttpCallbackClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testSendAuthResponse_success() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))

        val result = HttpCallbackClient.sendAuthResponse(
            host = server.hostName,
            port = server.port,
            nonce = "test-nonce",
            signature = "dGVzdC1zaWc="
        )
        assertTrue("Should return true on 200", result)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/response", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("test-nonce", body.getString("nonce"))
        assertEquals("dGVzdC1zaWc=", body.getString("signature"))
    }

    @Test
    fun testSendAuthResponse_serverError() {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = HttpCallbackClient.sendAuthResponse(
            host = server.hostName,
            port = server.port,
            nonce = "test-nonce",
            signature = "sig"
        )
        assertFalse("Should return false on 500", result)
    }

    @Test
    fun testSendAuthResponse_unreachable() {
        // Use a port that nothing is listening on
        val result = HttpCallbackClient.sendAuthResponse(
            host = "127.0.0.1",
            port = 1, // unlikely to be open
            nonce = "test-nonce",
            signature = "sig"
        )
        assertFalse("Should return false when unreachable", result)
    }
}
