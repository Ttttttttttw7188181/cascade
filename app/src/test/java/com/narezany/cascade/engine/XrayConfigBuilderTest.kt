package com.narezany.cascade.engine

import com.narezany.cascade.data.AppSettings
import com.narezany.cascade.data.Profile
import com.narezany.cascade.data.ProxyProtocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigBuilderTest {
    private val server = Profile(
        id = "main",
        name = "main",
        protocol = ProxyProtocol.VLESS,
        server = "example.com",
        port = 443,
        password = "11111111-1111-1111-1111-111111111111",
        security = "tls",
        sni = "example.com",
    )

    private val hop = server.copy(id = "hop", name = "hop", server = "hop.example.com")

    private fun outbounds(json: String): JSONArray = JSONObject(json).getJSONArray("outbounds")

    private fun outbound(json: String, tag: String): JSONObject? {
        val list = outbounds(json)
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            if (item.optString("tag") == tag) return item
        }
        return null
    }

    private fun dialer(outbound: JSONObject?): String =
        outbound?.optJSONObject("streamSettings")?.optJSONObject("sockopt")?.optString("dialerProxy").orEmpty()

    @Test
    fun aggregationAddsSocksOutboundAndRoutesServerThroughIt() {
        val settings = AppSettings(aggregationEnabled = true, aggregationHost = "127.0.0.1", aggregationPort = 1088)
        val json = XrayConfigBuilder(settings, listOf(server)).build("main").json

        val socks = outbound(json, settings.aggregationTag)
        assertNotNull(socks)
        assertEquals("socks", socks!!.getString("protocol"))
        val target = socks.getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertEquals("127.0.0.1", target.getString("address"))
        assertEquals(1088, target.getInt("port"))

        assertEquals(settings.aggregationTag, dialer(outbound(json, "proxy")))
    }

    @Test
    fun withoutAggregationThereIsNoSocksExit() {
        val json = XrayConfigBuilder(AppSettings(), listOf(server)).build("main").json
        assertNull(outbound(json, "aggregation"))
        assertEquals("", dialer(outbound(json, "proxy")))
    }

    @Test
    fun chainSendsOnlyLastHopToAggregation() {
        val chained = server.copy(chainIds = listOf("hop"))
        val settings = AppSettings(aggregationEnabled = true)
        val json = XrayConfigBuilder(settings, listOf(chained, hop)).build("main").json

        assertEquals("proxy-c1", dialer(outbound(json, "proxy")))
        assertEquals(settings.aggregationTag, dialer(outbound(json, "proxy-c1")))
    }

    @Test(expected = IllegalStateException::class)
    fun aggregationWithBlankHostFails() {
        val settings = AppSettings(aggregationEnabled = true, aggregationHost = " ")
        XrayConfigBuilder(settings, listOf(server)).build("main")
    }

    @Test
    fun portIsClampedByNormalization() {
        assertEquals(65535, AppSettings(aggregationPort = 70000).normalized().aggregationPort)
        assertTrue(AppSettings(aggregationPort = 0).normalized().aggregationPort >= 1)
    }
}
