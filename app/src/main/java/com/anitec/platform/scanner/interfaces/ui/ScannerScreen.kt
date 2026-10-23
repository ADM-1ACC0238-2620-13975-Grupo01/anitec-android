package com.anitec.platform.scanner.interfaces.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anitec.platform.R
import com.anitec.platform.core.designsystem.component.AniTecPanel
import com.anitec.platform.core.designsystem.component.AniTecTextField
import com.anitec.platform.core.designsystem.component.PrimaryButton
import com.anitec.platform.core.designsystem.component.SecondaryButton
import com.anitec.platform.core.designsystem.component.StatusTag
import com.anitec.platform.livestock.interfaces.ui.statusLabel
import com.anitec.platform.livestock.domain.AnimalStatus
import com.anitec.platform.livestock.interfaces.ui.severity
import com.anitec.platform.scanner.interfaces.viewmodel.ScanResult
import com.anitec.platform.scanner.interfaces.viewmodel.ScannerViewModel
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun analyzeImage(proxy: ImageProxy, scanner: BarcodeScanner, onCode: (String) -> Unit) {
    val media = proxy.image
    if (media == null) {
        proxy.close()
        return
    }
    scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
        .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onCode) }
        .addOnCompleteListener { proxy.close() }
}

/** Camera preview that reports every code ML Kit reads. The preview is dropped, and the camera released, with the composable. */
@Composable
private fun CameraScanner(onCode: (String) -> Unit, onUnavailable: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE, Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_DATA_MATRIX,
                )
                .build(),
        )
        val providerFuture = ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor) { proxy -> analyzeImage(proxy, scanner) { code -> currentOnCode(code) } } }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) {
                currentOnUnavailable()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onOpenAnimal: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasCameraHardware = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    // The user opened the scanner on purpose, so ask right away; typing the code stays possible either way.
    LaunchedEffect(Unit) { if (!granted && hasCameraHardware) requestCamera.launch(Manifest.permission.CAMERA) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scanner_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val result = state.result
            when {
                result is ScanResult.Found -> AniTecPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.scanner_found), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(result.animal.name, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${result.animal.tag} · ${result.animal.species} · ${result.animal.breed}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        StatusTag(statusLabel(result.animal.status), AnimalStatus.fromApi(result.animal.status).severity())
                        PrimaryButton(
                            text = stringResource(R.string.scanner_open),
                            onClick = { onOpenAnimal(result.animal.id) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                result is ScanResult.NotFound -> AniTecPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.scanner_not_found, result.code), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                granted && hasCameraHardware && !cameraFailed -> {
                    Text(stringResource(R.string.scanner_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CameraScanner(
                        onCode = viewModel::onCodeDetected,
                        onUnavailable = { cameraFailed = true },
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
                    )
                }
                else -> AniTecPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(
                                when {
                                    !hasCameraHardware || cameraFailed -> R.string.scanner_unavailable
                                    denied -> R.string.scanner_denied
                                    else -> R.string.scanner_permission
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (hasCameraHardware && !cameraFailed) {
                            SecondaryButton(
                                text = stringResource(R.string.scanner_grant),
                                onClick = { requestCamera.launch(Manifest.permission.CAMERA) },
                            )
                        }
                    }
                }
            }
            if (result != null) {
                SecondaryButton(
                    text = stringResource(R.string.scanner_again),
                    onClick = viewModel::scanAgain,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(stringResource(R.string.scanner_manual_title), style = MaterialTheme.typography.titleSmall)
            AniTecTextField(
                value = state.manualCode,
                onValueChange = viewModel::onManualCodeChange,
                label = stringResource(R.string.scanner_manual_label),
                modifier = Modifier.fillMaxWidth(),
                imeAction = ImeAction.Search,
                onImeAction = viewModel::lookUpManualCode,
            )
            PrimaryButton(
                text = stringResource(R.string.scanner_lookup),
                onClick = viewModel::lookUpManualCode,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
