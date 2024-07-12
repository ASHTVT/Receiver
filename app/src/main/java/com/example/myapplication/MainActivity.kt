package com.example.myapplication

import android.os.Bundle
import com.example.myapplication.databinding.ActivityMainBinding
import androidx.activity.ComponentActivity
import android.Manifest
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.graphics.ImageFormat
import android.hardware.camera2.params.OutputConfiguration
import android.os.HandlerThread
import android.os.Handler
import java.nio.ByteBuffer
import android.util.Range
import java.util.concurrent.Executors
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity


class MainActivity : ComponentActivity() {
    private var isStart = true
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private var cameraCaptureSession: CameraCaptureSession? = null
    private lateinit var imageReader: ImageReader
    private lateinit var backgroundHandler: Handler
    private lateinit var backgroundThread: HandlerThread
    private val imageProcessingExecutor = Executors.newSingleThreadExecutor()

    private val REQUEST_CAMERA_PERMISSION = 200

    private var isSurfaceViewRunning = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        binding.button.setOnClickListener {
            if(isStart) {

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.CAMERA),
                        REQUEST_CAMERA_PERMISSION
                    )
                } else {
                    binding.surfaceView.visibility = View.VISIBLE
                    startCamera()
                }
                binding.button.setText("Stop")
                isStart = false
            } else {
                binding.surfaceView.visibility = View.INVISIBLE
                binding.button.setText("Start")
                isStart = true
            }
        }
    }

    private fun startCamera() {
        binding.surfaceView.visibility = View.VISIBLE
        val cameraId = cameraManager.cameraIdList[0]
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, stateCallback, null)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            setupImageReader()
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
        }
    }

    private fun setupImageReader() {
        imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.YUV_420_888, 2)
        imageReader.setOnImageAvailableListener(imageAvailableListener, null)
    }

    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image: Image = reader.acquireLatestImage()
        processImage(image)
        image.close()
    }

    private fun processImage(image: Image) {
        imageProcessingExecutor.execute {
            // Efficient image processing code here
            val buffer: ByteBuffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            // TODO: Add optimized image processing code here
        }
    }


    private fun createCameraPreviewSession() {
        try {
            val surface = binding.surfaceView.holder.surface
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)

            cameraDevice!!.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    val captureRequest = captureRequestBuilder.build()
                    cameraCaptureSession?.setRepeatingRequest(captureRequest, null, null)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    // Handle configuration failure
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            // Surface created, start camera preview
            startCamera()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            // Surface changed, handle any necessary changes
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            // Surface destroyed, release camera
            cameraDevice?.close()
            cameraDevice = null

        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                startCamera()
            } else {
                // Permission denied, show a message to the user
            }
        }
    }


    @Composable
    fun Greeting(name: String, modifier: Modifier = Modifier) {
        Text(
            text = "Hello $name!",
            modifier = modifier
        )
    }
    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        MyApplicationTheme {
            Greeting("Android")
        }
    }
}