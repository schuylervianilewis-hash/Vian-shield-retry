package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.config.WireGuardConfigParser
import com.example.config.WireGuardProfile
import com.example.engine.SingBoxAdapter
import com.example.engine.SingBoxPlatformCallback
import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class VianshieldVpnService : VpnService(), SingBoxPlatformCallback {

    companion object {
        const val ACTION_START_VPN = "com.example.vpn.ACTION_START_VPN"
        const val ACTION_STOP_VPN = "com.example.vpn.ACTION_STOP_VPN"
        const val EXTRA_CONFIG_STRING = "com.example.vpn.EXTRA_CONFIG_STRING"

        private const val NOTIFICATION_CHANNEL_ID = "vianshield_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        private val _vpnState = MutableStateFlow<VpnState>(VpnState.Stopped)
        val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

        fun startIntent(context: Context, configString: String): Intent {
            return Intent(context, VianshieldVpnService::class.java).apply {
                action = ACTION_START_VPN
                putExtra(EXTRA_CONFIG_STRING, configString)
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, VianshieldVpnService::class.java).apply {
                action = ACTION_STOP_VPN
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val lifecycleMutex = Mutex()
    private var currentStartJob: Job? = null
    private val singBoxEngine = SingBoxAdapter()
    private var activeProfile: WireGuardProfile? = null
    private var activeTunFd: Int = -1
    private var tunFdTransferredToEngine = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "VpnService",
            "VianshieldVpnService created"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_VPN -> {
                val configStr = intent.getStringExtra(EXTRA_CONFIG_STRING)
                if (configStr.isNullOrBlank()) {
                    val message = "No WireGuard profile supplied; import a .conf file before connecting."
                    OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "VpnService", message)
                    _vpnState.value = VpnState.Error("ERR_CONFIG_MISSING", message)
                    stopForegroundSafely()
                    stopSelfResult(startId)
                } else {
                    handleStartVpn(configStr, startId)
                }
            }
            ACTION_STOP_VPN -> {
                handleStopVpn(startId)
            }
        }

        return START_NOT_STICKY
    }

    private fun handleStartVpn(rawConfig: String, startId: Int) {
        if (_vpnState.value is VpnState.Connected) {
            OperationalLogCatcher.warn(
                LogCategory.VPN_LIFECYCLE,
                "VpnService",
                "Start requested while VPN is already connected (duplicate start suppressed)"
            )
            return
        }

        currentStartJob?.cancel()
        currentStartJob = serviceScope.launch {
            lifecycleMutex.withLock {
                _vpnState.value = VpnState.Starting("STAGE 1: Validating WireGuard profile")
                OperationalLogCatcher.info(
                    LogCategory.VPN_LIFECYCLE,
                    "VpnService",
                    "STAGE 1: Initiating serialized VPN start sequence (startId=$startId)"
                )

                // 1. Parse and validate WireGuard configuration
                val parseResult = WireGuardConfigParser.parse(rawConfig)
                if (parseResult.isFailure) {
                    val err = parseResult.exceptionOrNull()?.message ?: "Configuration parse failed"
                    OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "VpnService", "STAGE 1 FAILED: $err")
                    _vpnState.value = VpnState.Error("ERR_CONFIG_INVALID", err)
                    stopForegroundSafely()
                    stopSelfResult(startId)
                    return@withLock
                }

                val profile = parseResult.getOrThrow()
                activeProfile = profile

                // 2. Transform to sing-box v1.11 JSON
                _vpnState.value = VpnState.Starting("STAGE 2: Generating sing-box configuration")
                val singBoxJson = WireGuardConfigParser.toSingBoxJson(profile)
                OperationalLogCatcher.info(
                    LogCategory.CONFIG_VALIDATION,
                    "VpnService",
                    "STAGE 2: Generated sing-box configuration (gvisor stack, MTU=${profile.mtu}, route auto_detect=true, addresses=${profile.addresses.joinToString(",")})"
                )

                // 3. Verify native binary availability before claiming connection
                if (!singBoxEngine.isNativeBinaryAvailable()) {
                    val missingMsg = "sing-box native engine (libbox.aar) has not been compiled yet. Run the GitHub Actions workflow (.github/workflows/build-vianshield.yml) to generate libbox.aar."
                    OperationalLogCatcher.error(LogCategory.ENGINE_INIT, "VpnService", "STAGE 2 FAILED: $missingMsg")
                    _vpnState.value = VpnState.Error("ERR_NATIVE_AAR_PENDING", missingMsg)
                    stopForegroundSafely()
                    stopSelfResult(startId)
                    return@withLock
                }

                // 4. Start foreground notification
                startForeground(NOTIFICATION_ID, buildNotification("Starting Vianshield Tunnel…"))

                // 5. Initialize engine adapter
                _vpnState.value = VpnState.Starting("STAGE 3: Initializing native BoxService core")
                val initResult = singBoxEngine.initialize(singBoxJson, this@VianshieldVpnService)
                if (initResult.isFailure) {
                    val err = initResult.exceptionOrNull()?.message ?: "Engine initialization failed"
                    OperationalLogCatcher.error(LogCategory.ENGINE_INIT, "VpnService", "STAGE 3 FAILED: $err")
                    _vpnState.value = VpnState.Error("ERR_ENGINE_INIT", err)
                    stopForegroundSafely()
                    stopSelfResult(startId)
                    return@withLock
                }

                // 6. Start sing-box engine with watchdog timeout (prevents hanging on deadlocks - BitProxy patch 0034)
                _vpnState.value = VpnState.Starting("STAGE 4: Starting sing-box routing core")
                val engineStartResult = withTimeoutOrNull(10_000L) {
                    singBoxEngine.start()
                } ?: Result.failure(IllegalStateException("sing-box BoxService start timed out after 10 seconds"))

                if (engineStartResult.isFailure) {
                    val err = engineStartResult.exceptionOrNull()?.message ?: "Engine start failed"
                    OperationalLogCatcher.error(LogCategory.ENGINE_INIT, "VpnService", "STAGE 4 FAILED: $err")
                    _vpnState.value = VpnState.Error("ERR_ENGINE_START", err)
                    singBoxEngine.stop()
                    releaseTunFd()
                    stopForegroundSafely()
                    stopSelfResult(startId)
                    return@withLock
                }

                // 7. Transition to Connected ONLY after engine and TUN are verified running
                _vpnState.value = VpnState.Connected(
                    endpoint = "${profile.endpointHost}:${profile.endpointPort}",
                    localAddress = profile.address,
                    mtu = profile.mtu
                )

                updateNotification("Connected to ${profile.endpointHost}:${profile.endpointPort}")
                OperationalLogCatcher.info(
                    LogCategory.VPN_LIFECYCLE,
                    "VpnService",
                    "STAGE 5: VPN tunnel and sing-box engine successfully running"
                )
            }
        }
    }

    private fun handleStopVpn(startId: Int? = null) {
        if (_vpnState.value is VpnState.Stopped || _vpnState.value is VpnState.Stopping) {
            return
        }

        _vpnState.value = VpnState.Stopping
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "VpnService",
            "Stopping VPN service and releasing resources"
        )

        currentStartJob?.cancel()
        serviceScope.launch {
            lifecycleMutex.withLock {
                // Teardown order: stop engine first so it releases TUN fd cleanly
                singBoxEngine.stop()
                releaseTunFd()
                stopForegroundSafely()
                _vpnState.value = VpnState.Stopped
                OperationalLogCatcher.info(
                    LogCategory.VPN_LIFECYCLE,
                    "VpnService",
                    "VPN service stopped cleanly"
                )
                if (startId != null) {
                    stopSelfResult(startId)
                } else {
                    stopSelf()
                }
            }
        }
    }

    // SingBoxPlatformCallback implementations (Bridge to libbox PlatformInterface)

    override fun openTun(options: Any?): Int {
        val profile = activeProfile
        if (profile == null) {
            OperationalLogCatcher.error(LogCategory.TUN_INTERFACE, "VpnService", "openTun requested without active profile")
            return -1
        }

        return try {
            OperationalLogCatcher.info(
                LogCategory.TUN_INTERFACE,
                "VpnService",
                "STAGE 4A: openTun requested by native engine (options: ${options?.javaClass?.name ?: "null"})"
            )

            val builder = Builder()
                .setSession("Vianshield VPN")
                .setMtu(profile.mtu)

            for (addr in profile.addresses) {
                val addressParts = addr.split("/")
                val ip = addressParts[0]
                val isIpv6 = ip.contains(":")
                val defaultPrefix = if (isIpv6) 128 else 32
                val prefix = if (addressParts.size > 1) addressParts[1].toIntOrNull() ?: defaultPrefix else defaultPrefix
                builder.addAddress(ip, prefix)
                if (isIpv6) {
                    builder.addRoute("::", 0)
                } else {
                    builder.addRoute("0.0.0.0", 0)
                }
            }

            for (dns in profile.dnsServers) {
                builder.addDnsServer(dns)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            val pfd: ParcelFileDescriptor? = builder.establish()
            if (pfd == null) {
                OperationalLogCatcher.error(LogCategory.TUN_INTERFACE, "VpnService", "Builder.establish() returned null (VPN permission or configuration issue)")
                return -1
            }

            // Detach descriptor to transfer exclusive ownership to Go / sing-box runtime
            val rawFd = pfd.detachFd()
            activeTunFd = rawFd
            tunFdTransferredToEngine = true

            OperationalLogCatcher.info(
                LogCategory.TUN_INTERFACE,
                "VpnService",
                "STAGE 4B: TUN interface established and detached: fd=$rawFd, addresses=${profile.addresses.joinToString(",")}, dns=${profile.dnsServers.joinToString(",")}, mtu=${profile.mtu}"
            )
            rawFd
        } catch (e: Exception) {
            OperationalLogCatcher.error(LogCategory.TUN_INTERFACE, "VpnService", "Exception in openTun: ${e.message}")
            -1
        }
    }

    override fun autoDetectInterfaceControl(fd: Int): Boolean {
        return try {
            val success = protect(fd)
            if (success) {
                OperationalLogCatcher.info(
                    LogCategory.NETWORK_STATE,
                    "VpnService",
                    "Outbound socket fd=$fd protected from VPN routing loop"
                )
            } else {
                OperationalLogCatcher.warn(
                    LogCategory.NETWORK_STATE,
                    "VpnService",
                    "protect(fd=$fd) returned false"
                )
            }
            success
        } catch (e: Exception) {
            OperationalLogCatcher.error(
                LogCategory.NETWORK_STATE,
                "VpnService",
                "Exception protecting socket fd=$fd: ${e.message}"
            )
            false
        }
    }

    override fun onLog(level: String, message: String) {
        OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxCore", "[$level] $message")
    }

    override fun onError(code: String, message: String) {
        OperationalLogCatcher.error(LogCategory.ERROR_DIAGNOSTIC, "SingBoxCore", "Error ($code): $message")
        _vpnState.value = VpnState.Error(code, message)
    }

    private fun releaseTunFd() {
        if (activeTunFd >= 0) {
            val fdToClose = activeTunFd
            val wasTransferred = tunFdTransferredToEngine
            activeTunFd = -1
            tunFdTransferredToEngine = false
            if (!wasTransferred) {
                try {
                    ParcelFileDescriptor.adoptFd(fdToClose).close()
                    OperationalLogCatcher.info(LogCategory.TUN_INTERFACE, "VpnService", "Active TUN descriptor closed by Android: fd=$fdToClose")
                } catch (_: Exception) {}
            } else {
                OperationalLogCatcher.info(LogCategory.TUN_INTERFACE, "VpnService", "Active TUN descriptor released to sing-box Go core: fd=$fdToClose")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.channel_vpn_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_vpn_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, VianshieldVpnService::class.java).apply {
            action = ACTION_STOP_VPN
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_title))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.btn_stop_vpn), stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun stopForegroundSafely() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        serviceScope.cancel()
        singBoxEngine.stop()
        releaseTunFd()
        if (_vpnState.value !is VpnState.Error) {
            _vpnState.value = VpnState.Stopped
        }
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "VpnService",
            "VianshieldVpnService destroyed"
        )
        super.onDestroy()
    }

    override fun onRevoke() {
        OperationalLogCatcher.warn(
            LogCategory.VPN_LIFECYCLE,
            "VpnService",
            "VPN permission revoked by system or another VPN application started"
        )
        handleStopVpn()
        super.onRevoke()
    }
}
