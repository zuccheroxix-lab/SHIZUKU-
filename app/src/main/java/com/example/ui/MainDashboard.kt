package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shizuku.CommandExecutionResult
import com.example.shizuku.ShizukuConnectionStatus
import com.example.shizuku.ShizukuStatusInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(
    viewModel: ShizukuViewModel,
    modifier: Modifier = Modifier
) {
    val statusInfo by viewModel.statusState.collectAsState()
    val isExecuting by viewModel.isExecuting.collectAsState()
    val lastResult by viewModel.lastExecutionResult.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var customCommandText by remember { mutableStateOf("whoami") }
    var showInstructions by remember { mutableStateOf(false) }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Shizuku Client",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Official IPC Binder Integration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshStatus() },
                        modifier = Modifier.testTag("refresh_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Status"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // 1. Connection Status Banner
                ConnectionStatusBanner(
                    statusInfo = statusInfo,
                    onRequestPermission = { viewModel.requestPermission() },
                    onRefresh = { viewModel.refreshStatus() }
                )
            }

            item {
                // 2. Diagnostics Grid
                DetailedDiagnosticsCard(statusInfo = statusInfo)
            }

            item {
                // 3. Quick Action Buttons
                ActionButtonsCard(
                    statusInfo = statusInfo,
                    onCheckAgain = { viewModel.refreshStatus() },
                    onRequestPermission = { viewModel.requestPermission() },
                    onToggleInstructions = { showInstructions = !showInstructions },
                    showInstructions = showInstructions
                )
            }

            if (showInstructions || statusInfo.connectionStatus == ShizukuConnectionStatus.NOT_RUNNING || statusInfo.connectionStatus == ShizukuConnectionStatus.NOT_INSTALLED) {
                item {
                    // 4. Official Setup Guide
                    SetupGuideCard(statusInfo = statusInfo)
                }
            }

            item {
                // 5. Real System Tools Section
                SystemToolsSection(
                    statusInfo = statusInfo,
                    isExecuting = isExecuting,
                    customCommandText = customCommandText,
                    onCustomCommandChange = { customCommandText = it },
                    onExecuteCommand = { cmd -> viewModel.executeCommand(cmd) }
                )
            }

            if (lastResult != null) {
                item {
                    // 6. Command Execution Terminal Output
                    TerminalOutputCard(
                        result = lastResult!!,
                        onClear = { viewModel.clearResult() },
                        context = context
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ConnectionStatusBanner(
    statusInfo: ShizukuStatusInfo,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusLabel, indicatorColor, icon, subtitle) = when (statusInfo.connectionStatus) {
        ShizukuConnectionStatus.CONNECTED -> Quadruple(
            "CONNECTED",
            Color(0xFF10B981),
            Icons.Default.CheckCircle,
            "Terhubung penuh ke Shizuku. Izin granted, siap operasi berkeistimewaan."
        )
        ShizukuConnectionStatus.PERMISSION_REQUIRED -> Quadruple(
            "PERMISSION REQUIRED",
            Color(0xFFF59E0B),
            Icons.Default.Security,
            "Layanan Shizuku berjalan, namun aplikasi belum memiliki izin."
        )
        ShizukuConnectionStatus.NOT_RUNNING -> Quadruple(
            "NOT RUNNING",
            Color(0xFF6B7280),
            Icons.Default.Warning,
            "Aplikasi Shizuku terpasang, tetapi layanan Binder belum berjalan."
        )
        ShizukuConnectionStatus.NOT_INSTALLED -> Quadruple(
            "NOT INSTALLED",
            Color(0xFFEF4444),
            Icons.Default.Error,
            "Paket moe.shizuku.privileged.api tidak ditemukan pada perangkat."
        )
        ShizukuConnectionStatus.ERROR -> Quadruple(
            "ERROR",
            Color(0xFFDC2626),
            Icons.Default.Error,
            statusInfo.errorMessage ?: "Terjadi kesalahan saat memeriksa komunikasi Binder."
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("connection_status_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = indicatorColor.copy(alpha = 0.12f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(color = indicatorColor, shape = CircleShape)
                    )
                    Text(
                        text = "● $statusLabel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = indicatorColor
                    )
                }

                Icon(
                    imageVector = icon,
                    contentDescription = statusLabel,
                    tint = indicatorColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (statusInfo.connectionStatus == ShizukuConnectionStatus.PERMISSION_REQUIRED) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = indicatorColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("banner_request_permission_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("MINTA IZIN SEKARANG")
                }
            } else if (statusInfo.connectionStatus == ShizukuConnectionStatus.NOT_RUNNING) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("banner_retry_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PERIKSA LAGI (CHECK AGAIN)")
                }
            }
        }
    }
}

@Composable
fun DetailedDiagnosticsCard(
    statusInfo: ShizukuStatusInfo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("diagnostics_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "DIAGNOSTIK SISTEM & SHIZUKU",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            DiagnosticRow(
                label = "Status Instalasi",
                value = if (statusInfo.isInstalled) "Terpasang (Official)" else "Tidak Ditemukan",
                isPositive = statusInfo.isInstalled
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DiagnosticRow(
                label = "Status Layanan",
                value = if (statusInfo.isRunning) "Berjalan (Active)" else "Mati (Inactive)",
                isPositive = statusInfo.isRunning
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DiagnosticRow(
                label = "Status Binder",
                value = if (statusInfo.isBinderAlive) "Alive & Responsive" else "Dead / Not Available",
                isPositive = statusInfo.isBinderAlive
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DiagnosticRow(
                label = "Status Izin (Permission)",
                value = if (statusInfo.isPermissionGranted) "Granted (PERMISSION_GRANTED)" else "Belum Diberikan (Denied)",
                isPositive = statusInfo.isPermissionGranted
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val uidText = statusInfo.uid?.let {
                when (it) {
                    0 -> "0 (Root)"
                    2000 -> "2000 (Shell / ADB)"
                    else -> "$it (Privileged)"
                }
            } ?: "N/A (Layanan Belum Tersedia)"

            DiagnosticRow(
                label = "UID Shizuku",
                value = uidText,
                isPositive = statusInfo.uid != null
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DiagnosticRow(
                label = "Versi Server Shizuku",
                value = statusInfo.shizukuVersion?.let { "v$it" } ?: "N/A",
                isPositive = statusInfo.shizukuVersion != null
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DiagnosticRow(
                label = "Versi Android / SDK",
                value = "Android ${statusInfo.androidVersion} (API ${statusInfo.sdkVersion})",
                isPositive = true
            )
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isPositive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun ActionButtonsCard(
    statusInfo: ShizukuStatusInfo,
    onCheckAgain: () -> Unit,
    onRequestPermission: () -> Unit,
    onToggleInstructions: () -> Unit,
    showInstructions: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCheckAgain,
                modifier = Modifier
                    .weight(1f)
                    .testTag("check_again_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("CHECK AGAIN")
            }

            Button(
                onClick = onRequestPermission,
                enabled = statusInfo.isRunning && !statusInfo.isPermissionGranted,
                modifier = Modifier
                    .weight(1f)
                    .testTag("request_permission_button")
            ) {
                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("REQUEST PERMISSION")
            }
        }

        OutlinedButton(
            onClick = onToggleInstructions,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("toggle_guide_button")
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (showInstructions) "Sembunyikan Panduan Shizuku" else "Lihat Panduan Menjalankan Shizuku")
        }
    }
}

@Composable
fun SetupGuideCard(
    statusInfo: ShizukuStatusInfo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("setup_guide_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PANDUAN MEMULAI SHIZUKU RESMI",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Shizuku memungkinkan aplikasi mengeksekusi API tingkat sistem dengan izin ADB atau root tanpa perlu modifikasi sistem.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Metode 1: Wireless Debugging (Android 11+ Tanpa PC & Tanpa Root)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "1. Buka Pengaturan HP -> Opsi Pengembang (Developer Options).\n" +
                        "2. Aktifkan 'Wireless Debugging'.\n" +
                        "3. Buka Shizuku Manager -> klik 'Pairing' (Pasangkan) dan masukkan kode pairing 6 angka.\n" +
                        "4. Kembali ke Shizuku Manager dan klik 'Start' (Mulai).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Metode 2: ADB via Komputer",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Metode 3: Perangkat Rooted",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Jika perangkat telah di-root (Magisk/KernelSU/APatch), cukup buka aplikasi Shizuku dan klik 'Start' dengan akses Superuser.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

@Composable
fun SystemToolsSection(
    statusInfo: ShizukuStatusInfo,
    isExecuting: Boolean,
    customCommandText: String,
    onCustomCommandChange: (String) -> Unit,
    onExecuteCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("system_tools_section"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "SYSTEM TOOLS (REAL PRIVILEGED OPERATIONS)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (statusInfo.connectionStatus == ShizukuConnectionStatus.CONNECTED)
                    "Pilih operasi nyata untuk dieksekusi melalui Shizuku Binder IPC:"
                else
                    "Fitur ini memerlukan status CONNECTED (Binder aktif & izin diberikan).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            val isToolsEnabled = statusInfo.connectionStatus == ShizukuConnectionStatus.CONNECTED && !isExecuting

            // Predefined Quick Tools
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickToolChip(
                    label = "Cek Identity (id)",
                    enabled = isToolsEnabled,
                    onClick = { onExecuteCommand("id && whoami") }
                )
                QuickToolChip(
                    label = "Dumpsys Battery",
                    enabled = isToolsEnabled,
                    onClick = { onExecuteCommand("dumpsys battery") }
                )
                QuickToolChip(
                    label = "List 3rd Party Packages",
                    enabled = isToolsEnabled,
                    onClick = { onExecuteCommand("pm list packages -3 | head -n 30") }
                )
                QuickToolChip(
                    label = "Device Props (getprop)",
                    enabled = isToolsEnabled,
                    onClick = { onExecuteCommand("getprop ro.product.model && getprop ro.build.version.release && getprop ro.build.version.security_patch") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Custom Shell Command Field
            OutlinedTextField(
                value = customCommandText,
                onValueChange = onCustomCommandChange,
                label = { Text("Perintah Shell Shizuku") },
                placeholder = { Text("Contoh: pm list packages, dumpsys meminfo, dll.") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_command_input"),
                enabled = isToolsEnabled
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onExecuteCommand(customCommandText) },
                enabled = isToolsEnabled && customCommandText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("execute_command_button")
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Menjalankan Operasi...")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("JALANKAN PERINTAH")
                }
            }
        }
    }
}

@Composable
private fun QuickToolChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun TerminalOutputCard(
    result: CommandExecutionResult,
    onClear: () -> Unit,
    context: Context,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("terminal_output_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (result.isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HASIL EKSEKUSI (Exit Code: ${result.exitCode})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Shizuku Output", result.stdout.ifEmpty { result.stderr })
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Output berhasil disalin", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Output",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onClear) {
                        Text(
                            text = "X",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$ ${result.command}",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFF93C5FD)
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.DarkGray
            )

            SelectionContainer {
                val displayText = when {
                    result.stdout.isNotEmpty() -> result.stdout
                    result.stderr.isNotEmpty() -> "[STDERR]\n${result.stderr}"
                    else -> "(Tidak ada output/perintah selesai tanpa pesan)"
                }

                Text(
                    text = displayText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (result.isSuccess) Color(0xFFE5E7EB) else Color(0xFFFCA5A5),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
