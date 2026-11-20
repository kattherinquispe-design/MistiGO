package com.ejemplo.smartbus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejemplo.smartbus.data.PreferentialManager
import com.ejemplo.smartbus.ui.theme.AppPalette
import com.ejemplo.smartbus.ui.theme.SmartBusBackground
import com.ejemplo.smartbus.ui.theme.SmartBusDisplay
import com.ejemplo.smartbus.ui.theme.SmartBusText
import com.ejemplo.smartbus.ui.theme.SmartBusTextSecondary
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onBack: () -> Unit,
    onBusScanned: (String) -> Unit
) {
    val context = LocalContext.current
    var manualBusCode by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun launchGoogleQrScanner() {
        statusMessage = null
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()

            val scanner = GmsBarcodeScanning.getClient(context, options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val raw = barcode.rawValue.orEmpty()
                    val parsedBusId = PreferentialManager.parseBusIdFromQr(raw)
                    if (!parsedBusId.isNullOrBlank()) {
                        onBusScanned(parsedBusId)
                    } else {
                        statusMessage = "El código QR escaneado no pertenece a un bus SmartBus."
                    }
                }
                .addOnFailureListener { e ->
                    statusMessage = "No se pudo abrir el escáner (${e.localizedMessage ?: "intenta escribir la placa"})."
                }
        } catch (e: Exception) {
            statusMessage = "Escáner no disponible en este dispositivo. Ingresa el código del bus abajo."
        }
    }

    Scaffold(
        containerColor = SmartBusBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Escanear QR del Bus",
                        fontFamily = SmartBusDisplay,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(AppPalette.primarySoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = AppPalette.primary,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Text(
                        text = "Escanea el QR dentro o fuera del bus",
                        fontFamily = SmartBusDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = SmartBusText,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Al escanear el código del conductor se abrirá automáticamente el formulario de atención preferencial para ese vehículo.",
                        fontSize = 13.sp,
                        color = SmartBusTextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = { launchGoogleQrScanner() },
                        colors = ButtonDefaults.buttonColors(containerColor = AppPalette.primary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Abrir cámara para escanear QR",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (statusMessage != null) {
                        Text(
                            text = statusMessage.orEmpty(),
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "O ingresa la placa / código del bus",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SmartBusText
                    )

                    OutlinedTextField(
                        value = manualBusCode,
                        onValueChange = { manualBusCode = it.uppercase() },
                        label = { Text("Ej: V1A-234 o BUS-01") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(
                        onClick = {
                            if (manualBusCode.isNotBlank()) {
                                onBusScanned(manualBusCode.trim().uppercase())
                            }
                        },
                        enabled = manualBusCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continuar con este bus", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}