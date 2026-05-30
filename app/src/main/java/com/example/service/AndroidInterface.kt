package com.example.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.example.ui.viewmodel.AppViewModel

class AndroidInterface(
    private val context: Context,
    private val viewModel: AppViewModel
) {
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun triggerDownload(title: String, url: String, videoId: String) {
        Log.d("AndroidInterface", "JS Triggered Download. Title: $title, Url: $url, VideoId: $videoId")
        
        viewModel.triggerDownload(title, url, videoId)

        handler.post {
            Toast.makeText(context, "Iniciando descarga: $title", Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun updateUserId(userId: String) {
        Log.d("AndroidInterface", "JS updated UserId: $userId")
        viewModel.updateUserId(userId)
        
        // Restart websocket service with the new userId context
        handler.post {
            BackgroundWebSocketService.startService(context)
        }
    }
}
