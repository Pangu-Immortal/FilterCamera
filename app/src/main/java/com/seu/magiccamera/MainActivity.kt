package com.seu.magiccamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import com.seu.magiccamera.activity.CameraActivity

class MainActivity : AppCompatActivity() {



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.button_camera).setOnClickListener { v ->
            if (PermissionChecker.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                == PermissionChecker.PERMISSION_DENIED
            ) {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.CAMERA),
                    v.id
                )
            } else {
                startActivity(Intent(this, CameraActivity::class.java))
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (grantResults.size != 1 || grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, CameraActivity::class.java))
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

}