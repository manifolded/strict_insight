package com.example.strict_insight


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import java.lang.Math.*
import java.util.concurrent.Executors

// This is an arbitrary number we are using to keep track of the permission
// request. Where an app has multiple context for requesting permission,
// this can help differentiate the different contexts.
private const val REQUEST_CODE_PERMISSIONS = 10

// This is an array of all the permission specified in the manifest.
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

private const val PHOTO_EXTENSION = ".jpg"
private const val RATIO_4_3_VALUE = 4.0 / 3.0
private const val RATIO_16_9_VALUE = 16.0 / 9.0


class MainActivity : AppCompatActivity() {


    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var viewFinder: PreviewView
    private lateinit var cameraProvider: ListenableFuture<ProcessCameraProvider>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.view_finder)

        // Request camera permissions
        if(allPermissionsGranted()) {
            // setup camera capture
            viewFinder.post { setupCameraCapture() }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }


    private fun setupCameraCapture() {

        val setupRunnable = Runnable {
            val provider = cameraProvider.get()
            bindPreview(provider)
        }
        cameraProvider = ProcessCameraProvider.getInstance(this)
        cameraProvider.addListener(setupRunnable, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreview(provider: ProcessCameraProvider) {
        val metric = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }

        val aspectRatio = aspectRatio(metric.widthPixels, metric.heightPixels)
        val rotation = viewFinder.display.rotation


        // Build the viewfinder use case
        val preview = Preview.Builder()
            .setTargetAspectRatio(aspectRatio)
            .setTargetRotation(rotation)
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()


        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(aspectRatio)
            .setTargetRotation(rotation)
            .build()


        // ImageAnalysis
        val imageAnalyzer = ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(aspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()
            // The analyzer can then be assigned to the instance
            .also {
                it.setAnalyzer(executor, ImageAnalysis.Analyzer { image ->

                    Log.d("MainActivity", "Average luminosity: $image")
                })
            }

        val orientationEventListener = object : OrientationEventListener(this as Context) {
            override fun onOrientationChanged(orientation : Int) {
                // Monitors orientation values to determine the target rotation value
                val rotation : Int = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                imageCapture.targetRotation = rotation
            }
        }
        orientationEventListener.enable()


        // unbind all use-cases before re-binding them
        provider.unbindAll()

        try {
            // varianle returned, 'camera', prvides access to CameraControl and CameraInfo
            val camera = provider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            // attach the surface viewFinder to  preview's use case
            preview.setSurfaceProvider(viewFinder.createSurfaceProvider(camera.cameraInfo))
        } catch (ex: Exception) {
            Log.d("MainActivity", "An exception occurred binding to camera")
        }

    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if(requestCode == REQUEST_CODE_PERMISSIONS) {
            if(allPermissionsGranted()) {
                viewFinder.post { setupCameraCapture() }
            } else {
                Toast.makeText(this,
                    "Permissions not granted by user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}
