package com.example.shizuku

enum class ShizukuConnectionStatus {
    NOT_INSTALLED,
    NOT_RUNNING,
    PERMISSION_REQUIRED,
    CONNECTED,
    ERROR
}

data class ShizukuStatusInfo(
    val connectionStatus: ShizukuConnectionStatus = ShizukuConnectionStatus.NOT_RUNNING,
    val isInstalled: Boolean = false,
    val isRunning: Boolean = false,
    val isBinderAlive: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val uid: Int? = null,
    val shizukuVersion: Int? = null,
    val androidVersion: String = "",
    val sdkVersion: Int = 0,
    val errorMessage: String? = null
)

data class CommandExecutionResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val isSuccess: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
