package com.goodgirl.accounting.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun BarcodeScannerScreen(
    onBarcodeDetected: (String, String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnBarcodeDetected by rememberUpdatedState(onBarcodeDetected)
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val alreadyDelivered = remember { AtomicBoolean(false) }
    val scanner: BarcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_CODE_128
            )
            .build()
        BarcodeScanning.getClient(options)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) cameraError = "يجب السماح باستخدام الكاميرا لقراءة الباركود"
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(scanner, executor) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }
    DisposableEffect(cameraProvider) {
        onDispose { cameraProvider?.unbindAll() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("قارئ الباركود", style = MaterialTheme.typography.headlineSmall)
        Text("وجّه الكاميرا نحو الباركود حتى تتم قراءته تلقائياً.")
        if (hasPermission) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { viewContext ->
                    PreviewView(viewContext).also { view ->
                        view.scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewView = view
                    }
                }
            )
            LaunchedEffect(previewView, hasPermission) {
                val view = previewView ?: return@LaunchedEffect
                try {
                    val providerFuture = ProcessCameraProvider.getInstance(context)
                    providerFuture.addListener({
                        try {
                            val provider = providerFuture.get()
                            cameraProvider = provider
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = view.surfaceProvider
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(executor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage == null || alreadyDelivered.get()) {
                                    imageProxy.close()
                                } else {
                                    val input = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    scanner.process(input)
                                        .addOnSuccessListener { barcodes ->
                                            val barcode = barcodes.firstOrNull {
                                                !it.rawValue.isNullOrBlank()
                                            }
                                            val value = barcode?.rawValue
                                            if (value != null && alreadyDelivered.compareAndSet(false, true)) {
                                                val format = when (barcode.format) {
                                                    Barcode.FORMAT_EAN_13 -> "EAN_13"
                                                    Barcode.FORMAT_EAN_8 -> "EAN_8"
                                                    Barcode.FORMAT_UPC_A -> "UPC_A"
                                                    Barcode.FORMAT_CODE_128 -> "CODE_128"
                                                    else -> "UNKNOWN"
                                                }
                                                latestOnBarcodeDetected(value, format)
                                            }
                                        }
                                        .addOnFailureListener {
                                            cameraError = "تعذرت قراءة الباركود، حاول تثبيت الكاميرا"
                                        }
                                        .addOnCompleteListener { imageProxy.close() }
                                }
                            }
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                            )
                        } catch (error: Exception) {
                            cameraError = "تعذر تشغيل الكاميرا: ${error.localizedMessage ?: "خطأ غير معروف"}"
                        }
                    }, ContextCompat.getMainExecutor(context))
                } catch (error: Exception) {
                    cameraError = "تعذر تهيئة الكاميرا"
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(cameraError ?: "بانتظار إذن الكاميرا")
            }
        }
        cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("إغلاق القارئ")
        }
    }
}
