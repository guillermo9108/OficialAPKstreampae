package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.DownloadItem
import com.example.data.pref.ServerConfig
import com.example.data.repository.DownloadRepository
import com.example.downloader.CustomDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

enum class Screen {
    WebView,
    Downloads,
    Config
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val serverConfig = ServerConfig(application)
    private val downloadDao = AppDatabase.getDatabase(application).downloadDao()
    private val repository = DownloadRepository(downloadDao)
    private val downloader = CustomDownloader(application, repository)

    // Navigation and Active screen states
    private val _currentScreen = MutableStateFlow(Screen.Config)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Web view refresh trigger
    private val _refreshWebViewTrigger = MutableStateFlow(0)
    val refreshWebViewTrigger = _refreshWebViewTrigger.asStateFlow()

    fun refreshWebView() {
        _refreshWebViewTrigger.value += 1
    }

    // Config states
    private val _ipAddressState = MutableStateFlow(serverConfig.ipAddress)
    val ipAddressState = _ipAddressState.asStateFlow()

    private val _portState = MutableStateFlow(serverConfig.port)
    val portState = _portState.asStateFlow()

    private val _downloadLocationState = MutableStateFlow(serverConfig.downloadLocation)
    val downloadLocationState = _downloadLocationState.asStateFlow()

    private val _keepCacheState = MutableStateFlow(serverConfig.keepCache)
    val keepCacheState = _keepCacheState.asStateFlow()

    private val _cacheCleanIntervalState = MutableStateFlow(serverConfig.cacheCleanInterval)
    val cacheCleanIntervalState = _cacheCleanIntervalState.asStateFlow()

    private val _userIdState = MutableStateFlow(serverConfig.lastSavedUserId)
    val userIdState = _userIdState.asStateFlow()

    // Dynamic WebView URL mapping
    val webViewUrlFlow = ipAddressState.map { ip ->
        if (ip.isBlank()) return@map ""
        val port = portState.value.trim()
        val base = if (port.isBlank()) ip else "$ip:$port"
        
        if (base.startsWith("http://") || base.startsWith("https://")) {
            base
        } else {
            "http://$base"
        }
    }

    // Database flow for all downloads
    val downloadsFlow = repository.allDownloads

    val activeDownloadsFlow = downloadsFlow.map { list ->
        list.filter { it.status == "DOWNLOADING" || it.status == "PENDING" }
    }

    init {
        // Automatically switch to WebView if config is already present
        if (serverConfig.isConfigured && serverConfig.ipAddress.isNotBlank()) {
            _currentScreen.value = Screen.WebView
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun saveConfig(ip: String, port: String) {
        serverConfig.ipAddress = ip
        serverConfig.port = port
        serverConfig.isConfigured = ip.isNotBlank()
        
        _ipAddressState.value = ip
        _portState.value = port

        // Start / update background synchronization service safely
        if (serverConfig.isConfigured && serverConfig.lastSavedUserId.isNotBlank()) {
            try {
                com.example.service.BackgroundWebSocketService.startService(getApplication())
            } catch (ignored: Exception) {}
        }

        // Jump directly to stream WebView!
        _currentScreen.value = Screen.WebView
    }

    fun onIpAddressChanged(ip: String) {
        _ipAddressState.value = ip
    }

    fun onPortChanged(port: String) {
        _portState.value = port
    }

    fun onUserIdChanged(userId: String) {
        _userIdState.value = userId
    }

    fun resetConfig() {
        serverConfig.resetConfig()
        _ipAddressState.value = serverConfig.ipAddress
        _portState.value = serverConfig.port
        _downloadLocationState.value = ServerConfig.VAL_LOCATION_INTERNAL
        _keepCacheState.value = true
        _cacheCleanIntervalState.value = "NUNCA"
        _userIdState.value = ""
        
        _currentScreen.value = Screen.Config
    }

    fun saveDownloadLocation(location: String) {
        serverConfig.downloadLocation = location
        _downloadLocationState.value = location
    }

    fun saveKeepCache(keep: Boolean) {
        serverConfig.keepCache = keep
        _keepCacheState.value = keep
    }

    fun saveCacheCleanInterval(interval: String) {
        serverConfig.cacheCleanInterval = interval
        _cacheCleanIntervalState.value = interval
    }

    fun updateUserId(userId: String) {
        serverConfig.lastSavedUserId = userId
        _userIdState.value = userId
    }

    fun triggerDownload(title: String, url: String, videoId: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            downloader.startDownload(title, url, videoId)
        }
    }

    fun deleteDownload(item: DownloadItem) {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete DB entry
            repository.delete(item)

            // Delete physical file from device memory safely
            if (item.filePath.isNotBlank()) {
                try {
                    val file = File(item.filePath)
                    if (file.exists()) {
                        file.delete()
                        Log.d("AppViewModel", "Physical file deleted successfully: ${item.filePath}")
                    }
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed to delete physical file: ${e.message}")
                }
            }
        }
    }

    fun clearWebCache() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val context = getApplication<Application>()
                // Force WebView cache clearance
                val testWebView = WebView(context)
                testWebView.clearCache(true)
                
                // Clear user cookies as well
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies(null)
                cookieManager.flush()

                Log.d("AppViewModel", "WebView data cache cleared successfully.")
            } catch (e: Exception) {
                Log.e("AppViewModel", "Error wiping cookies or cache states: ${e.message}")
            }
        }
    }
}
