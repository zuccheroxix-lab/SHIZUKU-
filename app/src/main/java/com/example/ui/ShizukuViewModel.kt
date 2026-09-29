package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.shizuku.CommandExecutionResult
import com.example.shizuku.ShizukuManager
import com.example.shizuku.ShizukuStatusInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShizukuViewModel(application: Application) : AndroidViewModel(application) {

    val shizukuManager = ShizukuManager(application.applicationContext)

    val statusState: StateFlow<ShizukuStatusInfo> = shizukuManager.statusState

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _lastExecutionResult = MutableStateFlow<CommandExecutionResult?>(null)
    val lastExecutionResult: StateFlow<CommandExecutionResult?> = _lastExecutionResult.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        shizukuManager.startObserving()
    }

    fun refreshStatus() {
        shizukuManager.refreshStatus()
    }

    fun requestPermission() {
        val result = shizukuManager.requestPermission()
        result.onFailure { error ->
            _userMessage.value = "Gagal meminta izin: ${error.localizedMessage ?: "Layanan tidak siap"}"
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun executeCommand(command: String) {
        viewModelScope.launch {
            _isExecuting.value = true
            try {
                val result = shizukuManager.executePrivilegedCommand(command)
                _lastExecutionResult.value = result
            } finally {
                _isExecuting.value = false
            }
        }
    }

    fun clearResult() {
        _lastExecutionResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        shizukuManager.stopObserving()
    }
}
