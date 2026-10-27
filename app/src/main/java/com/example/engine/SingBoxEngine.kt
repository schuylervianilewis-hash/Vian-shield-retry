package com.example.engine

/**
 * SingBoxPlatformCallback:
 * Matches the native libbox PlatformInterface contract required by sing-box v1.11.0.
 */
interface SingBoxPlatformCallback {
    /**
     * Called by sing-box when the TUN inbound starts.
     * Android layer must establish the TUN interface and return the detached file descriptor (int).
     */
    fun openTun(options: Any?): Int

    /**
     * Called by sing-box to exempt outbound sockets (e.g. WireGuard UDP transport) from the VPN tunnel.
     * Android layer must invoke VpnService.protect(fd).
     */
    fun autoDetectInterfaceControl(fd: Int): Boolean

    /**
     * Diagnostic logging callback from native sing-box core.
     */
    fun onLog(level: String, message: String)

    /**
     * Structured error notification from native engine.
     */
    fun onError(code: String, message: String)
}

interface SingBoxEngine {
    fun initialize(configJson: String, platformCallback: SingBoxPlatformCallback): Result<Unit>
    fun start(): Result<Unit>
    fun stop(): Result<Unit>
    fun isRunning(): Boolean
    fun isNativeBinaryAvailable(): Boolean
    fun getEngineVersion(): String
}
