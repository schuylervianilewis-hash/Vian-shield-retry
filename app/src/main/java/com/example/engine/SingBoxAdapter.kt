package com.example.engine

import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher
import org.json.JSONObject
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SingBoxAdapter:
 * Bridges Android VpnService to sing-box v1.11.0 native libbox.
 *
 * Implements truthful engine lifecycle:
 * - Dynamically binds to io.nekohasekai.libbox.Libbox when libbox.aar is present.
 * - Bridges PlatformInterface.openTun to VpnService.establishTunInterface with pfd.detachFd().
 * - Bridges PlatformInterface.autoDetectInterfaceControl to VpnService.protect(fd) to prevent routing loops.
 * - Truthfully reports failure when libbox.aar has not yet been built by the CI pipeline.
 */
class SingBoxAdapter(
    private val allowSimulationForTesting: Boolean = false
) : SingBoxEngine {

    companion object {
        const val ENGINE_PINNED_VERSION = "sing-box v1.11.0"
        private const val LIBBOX_CLASS = "io.nekohasekai.libbox.Libbox"
        private const val PLATFORM_INTERFACE_CLASS = "io.nekohasekai.libbox.PlatformInterface"
    }

    private val isEngineRunning = AtomicBoolean(false)
    private var nativeServiceInstance: Any? = null
    private var platformCallback: SingBoxPlatformCallback? = null

    override fun getEngineVersion(): String = ENGINE_PINNED_VERSION

    override fun isRunning(): Boolean = isEngineRunning.get()

    override fun isNativeBinaryAvailable(): Boolean {
        return try {
            Class.forName(LIBBOX_CLASS)
            Class.forName(PLATFORM_INTERFACE_CLASS)
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    override fun initialize(configJson: String, platformCallback: SingBoxPlatformCallback): Result<Unit> {
        this.platformCallback = platformCallback

        OperationalLogCatcher.info(
            LogCategory.ENGINE_INIT,
            "SingBoxAdapter",
            "Initializing sing-box adapter targeting $ENGINE_PINNED_VERSION"
        )

        // 1. Validate sing-box JSON syntax
        try {
            val json = JSONObject(configJson)
            if (!json.has("inbounds") || !json.has("outbounds")) {
                val err = "Invalid sing-box configuration: missing required inbounds or outbounds definitions"
                OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "SingBoxAdapter", err)
                return Result.failure(IllegalArgumentException(err))
            }
        } catch (e: Exception) {
            val err = "Malformed sing-box JSON configuration: ${e.message}"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "SingBoxAdapter", err)
            return Result.failure(IllegalArgumentException(err, e))
        }

        // 2. Check for native libbox presence
        if (isNativeBinaryAvailable()) {
            return initializeNativeService(configJson, platformCallback)
        }

        if (allowSimulationForTesting) {
            OperationalLogCatcher.info(
                LogCategory.ENGINE_INIT,
                "SingBoxAdapter",
                "Operating in verified test simulation mode (allowSimulationForTesting=true)"
            )
            return Result.success(Unit)
        }

        val missingMsg = "sing-box native engine (libbox.aar) is not present in app/libs/. Run GitHub Actions workflow (.github/workflows/build-vianshield.yml) to compile upstream sing-box v1.11.0."
        OperationalLogCatcher.error(LogCategory.ENGINE_INIT, "SingBoxAdapter", missingMsg)
        return Result.failure(IllegalStateException(missingMsg))
    }

    private fun initializeNativeService(configJson: String, callback: SingBoxPlatformCallback): Result<Unit> {
        return try {
            OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 2A: Resolving Libbox and PlatformInterface classes...")
            val libboxCls = Class.forName(LIBBOX_CLASS)
            val platformInterfaceCls = Class.forName(PLATFORM_INTERFACE_CLASS)

            // Dynamic Proxy implementing io.nekohasekai.libbox.PlatformInterface
            val proxyHandler = InvocationHandler { _, method, args ->
                handlePlatformInterfaceCall(method, args, callback)
            }

            val proxy = Proxy.newProxyInstance(
                platformInterfaceCls.classLoader,
                arrayOf(platformInterfaceCls),
                proxyHandler
            )

            OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 2B: Calling Libbox.newService()...")
            // Libbox.newService(configJson, platformInterface)
            val newServiceMethod = libboxCls.getMethod("newService", String::class.java, platformInterfaceCls)
            nativeServiceInstance = newServiceMethod.invoke(null, configJson, proxy)

            OperationalLogCatcher.info(
                LogCategory.ENGINE_INIT,
                "SingBoxAdapter",
                "STAGE 2C: Native BoxService instantiated with dynamic PlatformInterface bridge"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            val root = unwrapReflectionException(e)
            val err = "Failed to initialize native sing-box BoxService [${root.javaClass.name}]: ${root.message ?: root.toString()}"
            OperationalLogCatcher.error(LogCategory.ERROR_DIAGNOSTIC, "SingBoxAdapter", "$err\n${root.stackTraceToString().lines().take(10).joinToString("\n")}")
            Result.failure(root)
        }
    }

    private fun handlePlatformInterfaceCall(method: Method, args: Array<Any>?, callback: SingBoxPlatformCallback): Any? {
        val methodName = method.name
        val argSummary = args?.joinToString { it?.javaClass?.simpleName ?: "null" } ?: "none"
        OperationalLogCatcher.info(
            LogCategory.ENGINE_INIT,
            "SingBoxAdapter",
            "STAGE 4 [PlatformCallback]: $methodName($argSummary) -> returnType=${method.returnType.simpleName}"
        )

        return when {
            methodName.equals("openTun", ignoreCase = true) -> {
                val options = args?.firstOrNull()
                OperationalLogCatcher.info(LogCategory.TUN_INTERFACE, "SingBoxAdapter", "STAGE 4A: Native engine requested OpenTun")
                val fd = callback.openTun(options)
                OperationalLogCatcher.info(LogCategory.TUN_INTERFACE, "SingBoxAdapter", "STAGE 4B: OpenTun returned fd=$fd to native engine")
                fd
            }
            methodName.equals("usePlatformAutoDetectInterfaceControl", ignoreCase = true) -> {
                true // Instruct sing-box to call autoDetectInterfaceControl for outbound sockets
            }
            methodName.equals("autoDetectInterfaceControl", ignoreCase = true) -> {
                val fd = (args?.firstOrNull() as? Number)?.toInt() ?: -1
                if (fd >= 0) {
                    val success = callback.autoDetectInterfaceControl(fd)
                    if (!success) {
                        throw IllegalStateException("VpnService.protect(fd=$fd) failed; cannot protect outbound socket from VPN loop")
                    }
                }
                null // Go error returns null on success
            }
            methodName.equals("writeLog", ignoreCase = true) -> {
                val msg = args?.firstOrNull()?.toString() ?: ""
                callback.onLog("INFO", msg)
                null
            }
            methodName == "toString" -> "SingBoxPlatformInterfaceProxy"
            methodName == "hashCode" -> System.identityHashCode(this)
            methodName == "equals" -> args?.firstOrNull() === this
            else -> {
                // Return safe defaults for optional callbacks
                when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    else -> null
                }
            }
        }
    }

    override fun start(): Result<Unit> {
        if (isEngineRunning.get()) {
            OperationalLogCatcher.warn(LogCategory.ENGINE_INIT, "SingBoxAdapter", "Sing-box engine is already running (duplicate start suppressed)")
            return Result.success(Unit)
        }

        val service = nativeServiceInstance
        if (service != null) {
            return try {
                OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 3A: Locating start() on ${service.javaClass.name}...")
                val startMethod = service.javaClass.getMethod("start")
                OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 3B: Invoking BoxService.start()...")
                startMethod.invoke(service)
                isEngineRunning.set(true)
                OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 3C: Native sing-box BoxService started successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                val root = unwrapReflectionException(e)
                val rootName = root.javaClass.name
                val rootMsg = root.message ?: root.localizedMessage ?: root.toString()
                val stackPreview = root.stackTraceToString().lines().take(12).joinToString("\n")
                val err = "Native sing-box BoxService failed to start [$rootName]: $rootMsg"
                OperationalLogCatcher.error(
                    LogCategory.ERROR_DIAGNOSTIC,
                    "SingBoxAdapter",
                    "$err\nCause chain: ${describeCauseChain(root)}\nStack trace:\n$stackPreview"
                )
                platformCallback?.onError("ERR_START_FAILED", err)
                try {
                    val closeMethod = service.javaClass.methods.firstOrNull { it.name.equals("close", ignoreCase = true) }
                    closeMethod?.invoke(service)
                } catch (_: Exception) {}
                nativeServiceInstance = null
                Result.failure(root)
            }
        }

        if (allowSimulationForTesting) {
            isEngineRunning.set(true)
            OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "Simulated engine core started for testing")
            return Result.success(Unit)
        }

        val err = "Cannot start sing-box: Native service instance is null (libbox.aar not linked)"
        OperationalLogCatcher.error(LogCategory.ENGINE_INIT, "SingBoxAdapter", err)
        return Result.failure(IllegalStateException(err))
    }

    override fun stop(): Result<Unit> {
        if (!isEngineRunning.get() && nativeServiceInstance == null) {
            OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "Sing-box engine is already stopped (duplicate stop suppressed)")
            return Result.success(Unit)
        }

        val service = nativeServiceInstance
        if (service != null) {
            try {
                val closeMethod = service.javaClass.methods.firstOrNull { it.name.equals("close", ignoreCase = true) }
                closeMethod?.invoke(service)
                nativeServiceInstance = null
            } catch (e: Exception) {
                val root = unwrapReflectionException(e)
                val err = "Exception while closing native BoxService: ${root.message ?: root.toString()}"
                OperationalLogCatcher.warn(LogCategory.ERROR_DIAGNOSTIC, "SingBoxAdapter", err)
                nativeServiceInstance = null
            }
        }

        isEngineRunning.set(false)
        OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "Sing-box engine stopped and native resources released cleanly")
        return Result.success(Unit)
    }

    private fun unwrapReflectionException(e: Throwable): Throwable {
        var current = e
        while (current is InvocationTargetException && current.targetException != null) {
            current = current.targetException
        }
        return current
    }

    private fun describeCauseChain(throwable: Throwable): String {
        val sb = StringBuilder()
        var curr: Throwable? = throwable
        var depth = 0
        while (curr != null && depth < 6) {
            if (depth > 0) sb.append(" -> ")
            sb.append("[${curr.javaClass.simpleName}]: ${curr.message}")
            curr = curr.cause
            depth++
        }
        return sb.toString()
    }
}
