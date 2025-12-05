package com.tsd.ascanner.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.AspectRatio
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@Composable
fun CameraScannerOverlay(
	visible: Boolean,
	onResult: (String) -> Unit,
	onClose: () -> Unit
) {
	if (!visible) return

	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val scope = rememberCoroutineScope()

	val hasPermission = remember {
		mutableStateOf(
			ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
		)
	}
	val requestPermission = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.RequestPermission(),
		onResult = { granted ->
			hasPermission.value = granted
		}
	)

	LaunchedEffect(Unit) {
		if (!hasPermission.value) {
			requestPermission.launch(Manifest.permission.CAMERA)
		}
	}

	val torchEnabled = remember { mutableStateOf(false) }
	val cameraRef = remember { mutableStateOf<Camera?>(null) }
	val foundCode = remember { mutableStateOf(false) }
	val pendingCode = remember { mutableStateOf<String?>(null) }
	var debounceJob: Job? = remember { null }

	Box(
		modifier = Modifier
			.fillMaxSize()
	) {
		// Mini window container (bottom-right)
		Box(
			modifier = Modifier
				.align(Alignment.BottomEnd)
				.padding(16.dp)
				.size(width = 300.dp, height = 225.dp)
				.clip(RoundedCornerShape(12.dp))
				.background(Color(0xFF000000))
		) {
			if (hasPermission.value) {
				androidx.compose.ui.viewinterop.AndroidView(
					factory = { ctx ->
						val previewView = PreviewView(ctx).apply {
							this.scaleType = PreviewView.ScaleType.FIT_CENTER
							this.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
						}

						val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
						cameraProviderFuture.addListener({
							if (foundCode.value) return@addListener
							val cameraProvider = cameraProviderFuture.get()

							val previewUseCase = Preview.Builder()
								.setTargetAspectRatio(AspectRatio.RATIO_4_3)
								.build()
								.also { it.setSurfaceProvider(previewView.surfaceProvider) }

							val analysisExecutor = Executors.newSingleThreadExecutor()
							val analysisUseCase = ImageAnalysis.Builder()
								.setTargetAspectRatio(AspectRatio.RATIO_4_3)
								.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
								.build()

							val options = BarcodeScannerOptions.Builder()
								.setBarcodeFormats(
									Barcode.FORMAT_DATA_MATRIX,
									Barcode.FORMAT_QR_CODE,
									Barcode.FORMAT_CODE_128,
									Barcode.FORMAT_CODE_39,
									Barcode.FORMAT_EAN_13,
									Barcode.FORMAT_EAN_8,
									Barcode.FORMAT_ITF,
									Barcode.FORMAT_UPC_A,
									Barcode.FORMAT_UPC_E,
									Barcode.FORMAT_PDF417,
									Barcode.FORMAT_AZTEC
								)
								.build()
							val scanner = BarcodeScanning.getClient(options)

							analysisUseCase.setAnalyzer(analysisExecutor) { imageProxy ->
								val mediaImage = imageProxy.image
								if (mediaImage == null) {
									imageProxy.close()
									return@setAnalyzer
								}
								val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
								scanner.process(image)
									.addOnSuccessListener { barcodes ->
										if (!foundCode.value) {
											val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
											if (!value.isNullOrBlank()) {
												if (pendingCode.value != value) {
													pendingCode.value = value
													debounceJob?.cancel()
													debounceJob = scope.launch {
														delay(200)
														// Commit only if value stayed the same during debounce window
														if (!foundCode.value && pendingCode.value == value) {
															foundCode.value = true
															onClose()
															onResult(value)
														}
													}
												}
											}
										}
									}
									.addOnFailureListener {
										// ignore
									}
									.addOnCompleteListener {
										imageProxy.close()
									}
							}

							val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
							try {
								cameraProvider.unbindAll()
								val camera = cameraProvider.bindToLifecycle(
									lifecycleOwner, cameraSelector, previewUseCase, analysisUseCase
								)
								cameraRef.value = camera
							} catch (_: Exception) {
							}
						}, ContextCompat.getMainExecutor(ctx))

						previewView
					},
					modifier = Modifier.fillMaxSize()
				)
			} else {
				Column(
					modifier = Modifier
						.align(Alignment.Center)
						.padding(12.dp),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					Icon(imageVector = Icons.Outlined.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
					Spacer(Modifier.size(8.dp))
					Text(text = "Нужно разрешение камеры", color = Color.White)
					Spacer(Modifier.size(8.dp))
					FloatingActionButton(onClick = { requestPermission.launch(Manifest.permission.CAMERA) }) {
						Icon(imageVector = Icons.Outlined.PhotoCamera, contentDescription = "Разрешить")
					}
				}
			}

			Row(
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(6.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				IconButton(onClick = {
					val cam = cameraRef.value
					val hasTorch = cam?.cameraInfo?.hasFlashUnit() == true
					if (hasTorch) {
						val newState = !torchEnabled.value
						cam?.cameraControl?.enableTorch(newState)
						torchEnabled.value = newState
					}
				}) {
					val torch = torchEnabled.value
					Icon(
						imageVector = if (torch) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
						contentDescription = "Фонарик",
						tint = Color.White
					)
				}
				Icon(
					imageVector = Icons.Outlined.Close,
					contentDescription = "Закрыть",
					tint = Color.White,
					modifier = Modifier
						.clickable { onClose() }
						.padding(8.dp)
				)
			}
		}
	}

	DisposableEffect(Unit) {
		onDispose {
			runCatching {
				val cam = cameraRef.value
				cam?.cameraControl?.enableTorch(false)
			}
			runCatching { debounceJob?.cancel() }
		}
	}
}


