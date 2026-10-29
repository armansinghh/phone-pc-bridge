package com.example.phoneaudio

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val projection = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == RESULT_OK) {
            startForegroundService(
                Intent(this, AudioService::class.java)
                    .putExtra("code", r.resultCode)
                    .putExtra("data", r.data)
            )
            finish()
        }
    }

    private val perms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection.launch(mpm.createScreenCaptureIntent())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        perms.launch(needed.toTypedArray())
    }
}