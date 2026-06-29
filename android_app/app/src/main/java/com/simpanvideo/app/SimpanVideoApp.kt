package com.simpanvideo.app

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg

class SimpanVideoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            Log.d("SimpanVideoApp", "YTDLnis (YoutubeDL & FFmpeg) initialized successfully.")
        } catch (e: Exception) {
            Log.e("SimpanVideoApp", "Failed to initialize YoutubeDL: ${e.message}")
        }
    }
}
