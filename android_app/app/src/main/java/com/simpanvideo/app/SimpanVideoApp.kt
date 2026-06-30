package com.simpanvideo.app

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg

import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.MutableStateFlow

enum class EngineStatus { INITIALIZING, READY, ERROR }

object EngineState {
    val status = MutableStateFlow(EngineStatus.INITIALIZING)
    var errorMessage = ""
}

class SimpanVideoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                EngineState.status.value = EngineStatus.INITIALIZING
                YoutubeDL.getInstance().init(this@SimpanVideoApp)
                FFmpeg.getInstance().init(this@SimpanVideoApp)
                
                // Menyamakan dengan YTDLnis: Melakukan update engine yt-dlp secara otomatis
                try {
                    YoutubeDL.getInstance().update(this@SimpanVideoApp)
                    Log.d("SimpanVideoApp", "yt-dlp berhasil di-update ke versi terbaru.")
                } catch (e: Exception) {
                    Log.e("SimpanVideoApp", "Gagal update yt-dlp (mungkin offline): ${e.message}")
                }

                Log.d("SimpanVideoApp", "YTDLnis (YoutubeDL & FFmpeg) initialized successfully.")
                EngineState.status.value = EngineStatus.READY
            } catch (e: Exception) {
                Log.e("SimpanVideoApp", "Failed to initialize YoutubeDL: ${e.message}")
                e.printStackTrace()
                EngineState.errorMessage = e.message ?: "Kesalahan tak dikenal"
                EngineState.status.value = EngineStatus.ERROR
            }
        }
    }
}
