package com.example.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher

class VpnLifecycleController(private val context: Context) {

    fun checkVpnPermission(): Intent? {
        return VpnService.prepare(context)
    }

    fun requestStartVpn(
        configContent: String,
        permissionLauncher: ActivityResultLauncher<Intent>? = null,
        activity: Activity? = null
    ) {
        val prepareIntent = checkVpnPermission()
        if (prepareIntent != null) {
            OperationalLogCatcher.info(
                LogCategory.VPN_LIFECYCLE,
                "LifecycleController",
                "Android VPN permission required. Prompting user consent dialog."
            )
            if (permissionLauncher != null) {
                permissionLauncher.launch(prepareIntent)
            } else {
                activity?.startActivityForResult(prepareIntent, 101)
            }
            return
        }

        startVpnService(configContent)
    }

    fun startVpnService(configContent: String) {
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "LifecycleController",
            "Starting VianshieldVpnService..."
        )
        val intent = VianshieldVpnService.startIntent(context, configContent)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            OperationalLogCatcher.error(
                LogCategory.VPN_LIFECYCLE,
                "LifecycleController",
                "Failed to start foreground VPN service: ${e.message}"
            )
        }
    }

    fun requestStopVpn() {
        OperationalLogCatcher.info(
            LogCategory.VPN_LIFECYCLE,
            "LifecycleController",
            "Requesting VPN stop..."
        )
        val intent = VianshieldVpnService.stopIntent(context)
        context.startService(intent)
    }
}
