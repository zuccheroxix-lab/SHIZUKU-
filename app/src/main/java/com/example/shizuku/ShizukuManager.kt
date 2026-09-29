package com.example.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class ShizukuManager(private val context: Context) {

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE_SHIZUKU_PERMISSION = 7001
        private const val TAG = "ShizukuManager"
    }

    private val _statusState = MutableStateFlow(
        ShizukuStatusInfo(
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            sdkVersion = Build.VERSION.SDK_INT
        )
    )
    val statusState: StateFlow<ShizukuStatusInfo> = _statusState.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
        refreshStatus()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE_SHIZUKU_PERMISSION) {
                Log.d(TAG, "Permission result received: $grantResult")
                refreshStatus()
            }
        }

    private var isListenerRegistered = false

    fun startObserving() {
        if (!isListenerRegistered) {
            try {
                Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
                Shizuku.addBinderDeadListener(binderDeadListener)
                Shizuku.addRequestPermissionResultListener(permissionResultListener)
                isListenerRegistered = true
            } catch (e: Throwable) {
                Log.e(TAG, "Error registering Shizuku listeners", e)
            }
        }
        refreshStatus()
    }

    fun stopObserving() {
        if (isListenerRegistered) {
            try {
                Shizuku.removeBinderReceivedListener(binderReceivedListener)
                Shizuku.removeBinderDeadListener(binderDeadListener)
                Shizuku.removeRequestPermissionResultListener(permissionResultListener)
                isListenerRegistered = false
            } catch (e: Throwable) {
                Log.e(TAG, "Error unregistering Shizuku listeners", e)
            }
        }
    }

    fun isShizukuAppInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    SHIZUKU_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (e: Throwable) {
            Log.e(TAG, "Failed checking Shizuku package", e)
            false
        }
    }

    fun refreshStatus() {
        val installed = isShizukuAppInstalled()
        val androidVer = Build.VERSION.RELEASE ?: "Unknown"
        val sdkVer = Build.VERSION.SDK_INT

        if (!installed) {
            _statusState.value = ShizukuStatusInfo(
                connectionStatus = ShizukuConnectionStatus.NOT_INSTALLED,
                isInstalled = false,
                isRunning = false,
                isBinderAlive = false,
                isPermissionGranted = false,
                androidVersion = androidVer,
                sdkVersion = sdkVer,
                errorMessage = "Aplikasi Shizuku ($SHIZUKU_PACKAGE) belum terpasang di perangkat."
            )
            return
        }

        val isBinderAlive = try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            Log.e(TAG, "Error pinging binder", e)
            false
        }

        if (!isBinderAlive) {
            _statusState.value = ShizukuStatusInfo(
                connectionStatus = ShizukuConnectionStatus.NOT_RUNNING,
                isInstalled = true,
                isRunning = false,
                isBinderAlive = false,
                isPermissionGranted = false,
                androidVersion = androidVer,
                sdkVersion = sdkVer,
                errorMessage = "Shizuku terpasang namun layanan belum berjalan atau Binder belum terhubung."
            )
            return
        }

        // Binder is alive, check permission
        try {
            val isGranted = if (Shizuku.isPreV11()) {
                // Pre-v11 uses standard runtime permission
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }

            val uid = try {
                Shizuku.getUid()
            } catch (_: Throwable) {
                null
            }

            val version = try {
                Shizuku.getVersion()
            } catch (_: Throwable) {
                null
            }

            val status = if (isGranted) {
                ShizukuConnectionStatus.CONNECTED
            } else {
                ShizukuConnectionStatus.PERMISSION_REQUIRED
            }

            _statusState.value = ShizukuStatusInfo(
                connectionStatus = status,
                isInstalled = true,
                isRunning = true,
                isBinderAlive = true,
                isPermissionGranted = isGranted,
                uid = uid,
                shizukuVersion = version,
                androidVersion = androidVer,
                sdkVersion = sdkVer,
                errorMessage = if (!isGranted) "Izin Shizuku belum diberikan untuk aplikasi ini." else null
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error inspecting Shizuku permission or info", e)
            _statusState.value = ShizukuStatusInfo(
                connectionStatus = ShizukuConnectionStatus.ERROR,
                isInstalled = true,
                isRunning = true,
                isBinderAlive = isBinderAlive,
                isPermissionGranted = false,
                androidVersion = androidVer,
                sdkVersion = sdkVer,
                errorMessage = "Gagal memverifikasi Shizuku: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }
    }

    fun requestPermission(): Result<Unit> {
        return try {
            if (!Shizuku.pingBinder()) {
                return Result.failure(IllegalStateException("Shizuku belum berjalan atau Binder mati."))
            }
            if (Shizuku.isPreV11()) {
                return Result.failure(UnsupportedOperationException("Versi Shizuku lama (pre-v11) tidak didukung."))
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refreshStatus()
                return Result.success(Unit)
            }
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION)
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
            Result.failure(e)
        }
    }

    suspend fun executePrivilegedCommand(command: String): CommandExecutionResult = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return@withContext CommandExecutionResult(
                command = command,
                exitCode = -1,
                stdout = "",
                stderr = "Perintah kosong tidak dapat dieksekusi.",
                isSuccess = false
            )
        }

        if (!Shizuku.pingBinder()) {
            return@withContext CommandExecutionResult(
                command = command,
                exitCode = -1,
                stdout = "",
                stderr = "Binder Shizuku tidak tersedia. Pastikan Shizuku berjalan.",
                isSuccess = false
            )
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return@withContext CommandExecutionResult(
                command = command,
                exitCode = -1,
                stdout = "",
                stderr = "Izin Shizuku belum diberikan (PERMISSION_DENIED).",
                isSuccess = false
            )
        }

        try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", trimmed),
                null,
                null
            ) as Process

            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val stdReader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))

            stdReader.forEachLine { line ->
                if (stdoutBuilder.length < 50000) {
                    stdoutBuilder.appendLine(line)
                }
            }

            errReader.forEachLine { line ->
                if (stderrBuilder.length < 20000) {
                    stderrBuilder.appendLine(line)
                }
            }

            val exitCode = process.waitFor()

            CommandExecutionResult(
                command = trimmed,
                exitCode = exitCode,
                stdout = stdoutBuilder.toString().trimEnd(),
                stderr = stderrBuilder.toString().trimEnd(),
                isSuccess = (exitCode == 0)
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while executing: $trimmed", e)
            CommandExecutionResult(
                command = trimmed,
                exitCode = -1,
                stdout = "",
                stderr = "SecurityException: Akses ditolak oleh Shizuku/Android. ${e.message}",
                isSuccess = false
            )
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException while executing: $trimmed", e)
            CommandExecutionResult(
                command = trimmed,
                exitCode = -1,
                stdout = "",
                stderr = "IllegalStateException: Layanan Shizuku terputus saat eksekusi. ${e.message}",
                isSuccess = false
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Exception while executing: $trimmed", e)
            CommandExecutionResult(
                command = trimmed,
                exitCode = -1,
                stdout = "",
                stderr = "Kesalahan (${e.javaClass.simpleName}): ${e.localizedMessage ?: "Tidak diketahui"}",
                isSuccess = false
            )
        }
    }
}
