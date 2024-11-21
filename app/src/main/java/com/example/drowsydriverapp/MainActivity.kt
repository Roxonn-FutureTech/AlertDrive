package com.example.drowsydriverapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drowsydriverapp.data.models.AlertLevel
import com.example.drowsydriverapp.data.models.DrowsinessState
import com.example.drowsydriverapp.ml.FaceAnalyzer
import com.example.drowsydriverapp.ui.DrowsinessViewModel
import com.example.drowsydriverapp.ui.theme.DrowsyDriverAppTheme
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        
        setContent {
            DrowsyDriverAppTheme {
                val drowsinessViewModel: DrowsinessViewModel = viewModel()
                val drowsinessState by drowsinessViewModel.drowsinessState.collectAsState()
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainContent(
                        preview = preview,
                        imageAnalyzer = imageAnalyzer,
                        cameraProvider = cameraProvider,
                        drowsinessState = drowsinessState
                    )
                }
            }
        }

        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val drowsinessViewModel: DrowsinessViewModel by viewModels()

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            preview = Preview.Builder().build()
            
            val faceAnalyzer = FaceAnalyzer(drowsinessViewModel)
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, faceAnalyzer)
                }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview!!,
                    imageAnalyzer!!
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}

@Composable
fun MainContent(
    preview: Preview?,
    imageAnalyzer: ImageAnalysis?,
    cameraProvider: ProcessCameraProvider?,
    drowsinessState: DrowsinessState
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    Column(modifier = Modifier.fillMaxSize()) {
        val previewView = remember { mutableStateOf<PreviewView?>(null) }
        
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }.also { previewView.value = it }
            },
            modifier = Modifier.weight(1f)
        ) { view ->
            preview?.setSurfaceProvider(view.surfaceProvider)
        }
        
        LaunchedEffect(previewView.value) {
            try {
                cameraProvider?.let { provider ->
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview!!,
                        imageAnalyzer!!
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Alert UI based on drowsiness state
        when (drowsinessState.alertLevel) {
            AlertLevel.WARNING -> {
                AlertCard(
                    title = "Warning",
                    message = "You appear to be getting drowsy. Consider taking a break.",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            AlertLevel.SEVERE -> {
                AlertCard(
                    title = "Severe Warning",
                    message = "High drowsiness detected! Please pull over safely.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            AlertLevel.CRITICAL -> {
                AlertCard(
                    title = "Critical Alert",
                    message = "Immediate action required! Pull over now!",
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {}
        }
    }
}

@Composable
fun AlertCard(
    title: String,
    message: String,
    color: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
}