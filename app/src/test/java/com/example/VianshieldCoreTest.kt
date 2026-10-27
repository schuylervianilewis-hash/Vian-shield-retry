package com.example

import com.example.config.WireGuardConfigParser
import com.example.engine.SingBoxAdapter
import com.example.engine.SingBoxPlatformCallback
import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VianshieldCoreTest {

    private val privateKey = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa="
    private val peerPublicKey = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb="

    private fun validConfig(): String = """
        [Interface]
        PrivateKey = $privateKey
        Address = 10.0.0.2/24
        DNS = 1.1.1.1, 8.8.8.8
        MTU = 1380

        [Peer]
        PublicKey = $peerPublicKey
        Endpoint = 198.51.100.1:51820
        AllowedIPs = 0.0.0.0/0
        PersistentKeepalive = 25
    """.trimIndent()

    @Before
    fun resetLogs() {
        OperationalLogCatcher.setLoggingEnabled(false)
        OperationalLogCatcher.clear()
        OperationalLogCatcher.setLoggingEnabled(false)
    }

    @Test
    fun parserBuildsProfileAndNormalizesFields() {
        val result = WireGuardConfigParser.parse(validConfig(), "test-profile")
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals("test-profile", profile.name)
        assertEquals("10.0.0.2/24", profile.address)
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), profile.dnsServers)
        assertEquals(1380, profile.mtu)
        assertEquals("198.51.100.1", profile.endpointHost)
        assertEquals(51820, profile.endpointPort)
        assertEquals(25, profile.persistentKeepalive)
    }

    @Test
    fun parserRejectsMissingPrivateKeyAndInvalidPort() {
        val missingKey = validConfig().replace("PrivateKey = $privateKey\n", "")
        assertTrue(WireGuardConfigParser.parse(missingKey).isFailure)

        val invalidPort = validConfig().replace("198.51.100.1:51820", "198.51.100.1:70000")
        assertTrue(WireGuardConfigParser.parse(invalidPort).isFailure)
    }

    @Test
    fun configConversionCreatesExpectedSingBoxSectionsAndBothAddresses() {
        val profile = WireGuardConfigParser.parse(validConfig()).getOrThrow()
        val json = JSONObject(WireGuardConfigParser.toSingBoxJson(profile))

        assertTrue(json.has("log"))
        assertTrue(json.has("dns"))
        assertTrue(json.has("inbounds"))
        assertTrue(json.has("outbounds"))
        assertTrue(json.has("route"))
        assertEquals("gvisor", json.getJSONArray("inbounds").getJSONObject(0).getString("stack"))
        assertEquals("wireguard", json.getJSONArray("outbounds").getJSONObject(0).getString("type"))
        assertEquals("wg-out", json.getJSONObject("route").getString("final"))
    }

    @Test
    fun displaySummariesDoNotLeakPrivateOrPresharedKey() {
        val config = validConfig().replace(
            "PublicKey = $peerPublicKey",
            "PublicKey = $peerPublicKey\nPresharedKey = $privateKey"
        )
        val profile = WireGuardConfigParser.parse(config).getOrThrow()
        val summary = profile.toRedactedSummary()
        val display = profile.toDisplaySummary()

        assertFalse(summary.contains(privateKey))
        assertFalse(display.values.any { it.contains(privateKey) })
    }

    @Test
    fun diagnosticRedactorRemovesNamedSecrets() {
        val redacted = OperationalLogCatcher.redactSecrets(
            "private_key=$privateKey password=not-for-logs token=secret-token"
        )
        assertFalse(redacted.contains(privateKey))
        assertFalse(redacted.contains("not-for-logs"))
        assertFalse(redacted.contains("secret-token"))
        assertTrue(redacted.contains("REDACTED"))
    }

    @Test
    fun singBoxAdapterSimulationLifecycleIsIdempotentlyStopped() {
        val adapter = SingBoxAdapter(allowSimulationForTesting = true)
        val profile = WireGuardConfigParser.parse(validConfig()).getOrThrow()
        val callback = object : SingBoxPlatformCallback {
            override fun openTun(options: Any?): Int = -1
            override fun autoDetectInterfaceControl(fd: Int): Boolean = false
            override fun onLog(level: String, message: String) = Unit
            override fun onError(code: String, message: String) = Unit
        }

        assertTrue(adapter.initialize(WireGuardConfigParser.toSingBoxJson(profile), callback).isSuccess)
        assertTrue(adapter.start().isSuccess)
        assertTrue(adapter.isRunning())
        assertTrue(adapter.stop().isSuccess)
        assertFalse(adapter.isRunning())
        assertTrue(adapter.stop().isSuccess)
    }
}
