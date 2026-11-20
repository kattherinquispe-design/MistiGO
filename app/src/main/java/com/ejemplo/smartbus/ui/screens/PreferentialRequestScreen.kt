package com.ejemplo.smartbus.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.PregnantWoman
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejemplo.smartbus.data.PreferentialManager
import com.ejemplo.smartbus.ui.theme.AppPalette
import com.ejemplo.smartbus.ui.theme.SmartBusBackground
import com.ejemplo.smartbus.ui.theme.SmartBusDisplay
import com.ejemplo.smartbus.ui.theme.SmartBusText
import com.ejemplo.smartbus.ui.theme.SmartBusTextSecondary

private data class PreferentialOption(
    val label: String,
    val description: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferentialRequestScreen(
    onBack: () -> Unit,
    preselectedBusId: String?,
    passengerName: String,
    onRequestSent: () -> Unit
) {
    val context = LocalContext.current
    var busCode by remember(preselectedBusId) {
        mutableStateOf(preselectedBusId.orEmpty())
    }
    var selectedOption by remember { mutableStateOf("Adulto mayor") }
    var note by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    val options = remember {
        listOf(
            PreferentialOption(
                label = "Adulto mayor",
                description = "Asiento reservado y tiempo prudente para subir y bajar",
                icon = Icons.Default.Elderly
            ),
            PreferentialOption(
                label = "Mujer embarazada",
                description = "Prioridad de asiento reservado en zona delantera",
                icon = Icons.Default.PregnantWoman
            ),
            PreferentialOption(
                label = "Persona con discapacidad",
                description = "Asistencia en abordaje, rampa o aviso en paradero",
                icon = Icons.Default.Accessible
            ),
            PreferentialOption(
                label = "Con niños o bebés en brazos",
                description = "Asiento preferencial para viajar seguro con menores",
                icon = Icons.Default.ChildCare
            )
        )
    }

    Scaffold(
        containerColor = SmartBusBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Atención Preferencial",
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
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Identificación del Bus",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SmartBusText
                    )
                    OutlinedTextField(
                        value = busCode,
                        onValueChange = { busCode = it.uppercase() },
                        label = { Text("Placa o código del bus (ej. BUS-01)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Text(
                text = "Selecciona el tipo de atención preferencial",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = SmartBusText
            )

            options.forEach { option ->
                val isSelected = selectedOption == option.label
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedOption = option.label },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFFEFF6FF) else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) Color(0xFF2563EB) else AppPalette.primarySoft
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else AppPalette.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = SmartBusText
                            )
                            Text(
                                text = option.description,
                                fontSize = 12.sp,
                                color = SmartBusTextSecondary
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2563EB)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Nota adicional para el conductor (opcional)") },
                placeholder = { Text("Ej: Necesito rampa o bajo en Plaza de Armas") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    val cleanBus = busCode.trim()
                    if (cleanBus.isBlank()) {
                        Toast.makeText(
                            context,
                            "Ingresa o escanea la placa del bus",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }
                    isSending = true
                    PreferentialManager.sendPreferentialRequest(
                        busId = cleanBus,
                        passengerName = passengerName,
                        categoryLabel = selectedOption,
                        note = note
                    ) { success ->
                        isSending = false
                        if (success) {
                            onRequestSent()
                        } else {
                            Toast.makeText(
                                context,
                                "No se pudo enviar la alerta. Revisa tu conexión.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                enabled = !isSending,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Enviar alerta sonora al conductor",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}