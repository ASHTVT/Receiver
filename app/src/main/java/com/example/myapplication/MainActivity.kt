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
import android.widget.Toast
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.ExecutorService
import android.content.Intent
import android.net.Uri


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var backgroundHandler: Handler
    private lateinit var backgroundThread: HandlerThread
    private var isStart = true
    private var cameraDevice: CameraDevice? = null
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var captureSession: CameraCaptureSession
    private val luminosityValues = mutableListOf<Int>()
    private val bitValues = mutableListOf<Int>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startBackgroundThread()

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        binding.button.setOnClickListener {
            if (isStart) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.CAMERA),
                        REQUEST_CAMERA_PERMISSION
                    )
                } else {
                    binding.surfaceView.visibility = View.VISIBLE
                    startCamera()
                }
                binding.button.text = "Stop"
                isStart = false
            } else {
                binding.surfaceView.visibility = View.INVISIBLE
                binding.button.text = "Start"
                stopCamera()
                isStart = true
            }
        }

        binding.surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (!isStart) {
                    startCamera()
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                stopCamera()
            }
        })
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread.looper)

    }

    private fun stopBackgroundThread() {
        backgroundThread.quitSafely()
        try {
            backgroundThread.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun startCamera() {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreviewSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraDevice?.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cameraDevice?.close()
                    cameraDevice = null
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }


    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
        }
    }

    private fun createCameraPreviewSession() {
        try {
            val surface = binding.surfaceView.holder.surface
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)

            cameraDevice!!.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = session
                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
                    startLuminosityAnalysis()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(this@MainActivity, "Failed", Toast.LENGTH_SHORT).show()
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun startLuminosityAnalysis() {
        val reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2)
        reader.setOnImageAvailableListener({ reader ->
            val image: Image = reader.acquireNextImage()
            var bri = 0
            val buffer: ByteBuffer = image.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val luma = calculateCenterLuminosity(data, 640, 480, 160, 120)
            if(luma >= 150) {bri = 1}
            else {bri = 0}
            handleLuminosityValue(bri)
            image.close()
        }, backgroundHandler)

        captureRequestBuilder.addTarget(reader.surface)
        captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
    }

    private fun handleLuminosityValue(luma: Int) {
        var head  = 0
        if(luminosityValues.size == 1 && luminosityValues.last() == luma) {
            luminosityValues.clear()
            luminosityValues.add(luma)
        }
        if (luminosityValues.isNotEmpty() && luminosityValues.last() == 2) {
            luminosityValues.clear()
            bitValues.clear()
        }
        else {
            luminosityValues.add(luma)
        }

        if (luminosityValues.size > 3200) {
            luminosityValues.subList(0, luminosityValues.size - 3200).clear()
        }


        if (checkTail()) {
            stopCamera()
            head = checkHead()
            luminosityValues.subList(head,luminosityValues.size - 16)
            manchesterDecoding(head)
            val url = byteToURL()
            openWebpage(url)
            luminosityValues.add(2)
            finish()
        }
    }

    private fun stopCamera() {
        runOnUiThread {
            binding.surfaceView.visibility = View.INVISIBLE
            binding.button.text = "Start"
        }
        try {
            captureSession.stopRepeating()
            captureSession.close()
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
        cameraDevice?.close()
        isStart = true
    }

    private fun checkTail(): Boolean {
        if (luminosityValues.size < 16) return false

        val lastSix = luminosityValues.takeLast(16)
        val pattern = listOf(1,0,0,1,1,0,0,1,1,0,1,0,1,0,1,0)
        return lastSix == pattern
    }

    private fun checkHead():Int {
        val pattern = listOf(1,0,0,1,1,0,0,1,1,0,0,1,1,0,1,0)
        val patternSize = pattern.size

        for (i in luminosityValues.size - patternSize downTo 0) {
            if (luminosityValues.subList(i, i + patternSize) == pattern) {
                return i + 16
            }
        }
        return 0
    }

    private fun manchesterDecoding(startIndex: Int) {
        bitValues.clear()
        for (i in startIndex until luminosityValues.size - 1 step 2) {
            val first = luminosityValues[i]
            val second = luminosityValues[i + 1]
            if (first == 1 && second == 0) {
                bitValues.add(1)
            } else if (first == 0 && second == 1) {
                bitValues.add(0)
            }
        }

    }

    private fun byteToURL(): String{
        val byteArray = ByteArray(bitValues.size / 8) { index ->
            val byteStartIndex = index * 8
            bitValues.subList(byteStartIndex, byteStartIndex + 8).fold(0) { acc, bit ->
                (acc shl 1) + bit
            }.toByte()
        }
        return byteArray.toString(Charsets.UTF_8)
    }

    fun openWebpage(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun calculateCenterLuminosity(data: ByteArray, width: Int, height: Int, centerWidth: Int, centerHeight: Int): Double {
        val startX = (width - centerWidth) / 2
        val startY = (height - centerHeight) / 2
        var sum: Long = 0
        var count = 0

        for (y in startY until startY + centerHeight) {
            for (x in startX until startX + centerWidth) {
                val index = y * width + x
                sum += data[index].toInt() and 0xFF
                count++
            }
        }
        return sum.toDouble() / count
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBackgroundThread()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val REQUEST_CAMERA_PERMISSION = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}

typealias LumaListener = (luma: Double) -> Unit