package com.example.vpn

sealed class VpnState {
    data object Stopped : VpnState()
    data class Starting(val stage: String) : VpnState()
    data class Connected(
        val endpoint: String,
        val localAddress: String,
        val mtu: Int,
        val connectedSince: Long = System.currentTimeMillis()
    ) : VpnState()
    data object Stopping : VpnState()
    data class Error(
        val errorCode: String,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : VpnState()
}
