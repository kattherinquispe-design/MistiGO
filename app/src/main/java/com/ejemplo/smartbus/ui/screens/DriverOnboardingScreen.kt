package com.ejemplo.smartbus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejemplo.smartbus.data.SessionManager
import com.ejemplo.smartbus.data.driver.DriverRepository
import com.ejemplo.smartbus.data.driver.DriverRequirements
import com.ejemplo.smartbus.data.model.DriverProfile
import com.ejemplo.smartbus.data.model.DriverVerificationStatus
import com.ejemplo.smartbus.ui.theme.SmartBusBackground
import com.ejemplo.smartbus.ui.theme.SmartBusBlue
import com.ejemplo.smartbus.ui.theme.SmartBusGreen
import com.ejemplo.smartbus.ui.theme.SmartBusRed
import com.ejemplo.smartbus.ui.theme.SmartBusText
import com.ejemplo.smartbus.ui.theme.SmartBusTextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DriverOnboardingScreen(
    accountId: String,
    repository: DriverRepository,
    existing: DriverProfile?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { SessionManager.get(context).session.value }
    val companies = remember { DriverRequirements.companies }
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    var fullName by remember(existing) {
        mutableStateOf(existing?.fullName?.ifBlank { session?.name.orEmpty() } ?: session?.name.orEmpty())
    }
    var email by remember(existing) {
        mutableStateOf(existing?.email?.ifBlank { session?.email.orEmpty() } ?: session?.email.orEmpty())
    }
    var driverPassword by remember { mutableStateOf("") }
    var documentId by remember(existing) {
        mutableStateOf(existing?.documentId.orEmpty())
    }
    var phone by remember(existing) {
        mutableStateOf(existing?.phone.orEmpty())
    }
    var companyName by remember(existing) {
        mutableStateOf(existing?.companyName.orEmpty())
    }
    var companyCode by remember(existing) {
        mutableStateOf(existing?.companyCode.orEmpty())
    }
    var licenseNumber by remember(existing) {
        mutableStateOf(existing?.licenseNumber.orEmpty())
    }
    var licenseClass by remember(existing) {
        mutableStateOf(existing?.licenseClass?.ifBlank { "A-IIIc" } ?: "A-IIIc")
    }
    var licenseExpiry by remember(existing) {
        mutableStateOf(
            existing?.licenseExpiresAt?.takeIf { it > 0L }?.let { dateFormatter.format(Date(it)) }
                ?: "2030-12-31"
        )
    }
    var emergencyContactName by remember(existing) {
        mutableStateOf(existing?.emergencyContactName.orEmpty())
    }
    var emergencyContactPhone by remember(existing) {
        mutableStateOf(existing?.emergencyContactPhone.orEmpty())
    }

    var showCompanyPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SmartBusBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        TextButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Volver al mapa")
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Badge,
                contentDescription = null,
                tint = SmartBusBlue,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Datos del Conductor",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = SmartBusText
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Completa tus datos oficiales para habilitar tu vehículo y ruta en MistiGo.",
            style = MaterialTheme.typography.bodySmall,
            color = SmartBusTextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Identidad
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "1. Identidad",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SmartBusText
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nombres y apellidos completos") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = documentId,
                    onValueChange = { input ->
                        documentId = input.filter { it.isDigit() }.take(8)
                        error = null
                    },
                    label = { Text("DNI (8 dígitos)") },
                    placeholder = { Text("71234567") },
                    singleLine = true,
                    supportingText = {
                        Text("${documentId.length}/8 dígitos")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { input ->
                        phone = input.filter { it.isDigit() }.take(9)
                    },
                    label = { Text("Celular de contacto") },
                    placeholder = { Text("987654321") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it.trim()
                        error = null
                    },
                    label = { Text("Correo electrónico (obligatorio)") },
                    placeholder = { Text("conductor@correo.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = driverPassword,
                    onValueChange = {
                        driverPassword = it
                        error = null
                    },
                    label = { Text("Contraseña de confirmación (obligatoria)") },
                    placeholder = { Text("Mínimo 6 caracteres") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Empresa con convenio y Licencia
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "2. Empresa Autorizada y Licencia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SmartBusText
                )

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FA)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCompanyPicker = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBus,
                            contentDescription = null,
                            tint = if (DriverRequirements.findCompany(companyCode) == null) {
                                SmartBusBlue
                            } else {
                                SmartBusGreen
                            },
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Empresa con convenio",
                                style = MaterialTheme.typography.labelMedium,
                                color = SmartBusTextSecondary
                            )
                            Text(
                                text = if (companyName.isNotBlank() && companyCode.isNotBlank()) {
                                    "$companyCode · $companyName"
                                } else {
                                    "Toca aquí para elegir una empresa autorizada"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = SmartBusText
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = licenseNumber,
                        onValueChange = { licenseNumber = it.uppercase().take(12) },
                        label = { Text("N° Licencia") },
                        placeholder = { Text("Q71234567") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )

                    OutlinedTextField(
                        value = licenseClass,
                        onValueChange = { licenseClass = it.uppercase().take(8) },
                        label = { Text("Categoría") },
                        placeholder = { Text("A-IIIc") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = licenseExpiry,
                    onValueChange = { licenseExpiry = it.trim() },
                    label = { Text("Vencimiento de licencia (AAAA-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Contacto de emergencia
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "3. Contacto de emergencia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SmartBusText
                )

                OutlinedTextField(
                    value = emergencyContactName,
                    onValueChange = { emergencyContactName = it },
                    label = { Text("Nombre del familiar o contacto") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = emergencyContactPhone,
                    onValueChange = { input ->
                        emergencyContactPhone = input.filter { it.isDigit() }.take(9)
                    },
                    label = { Text("Teléfono de emergencia") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SmartBusRed.copy(alpha = 0.10f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = SmartBusRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartBusRed
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                error = null
                val parsedLicenseExpiry = parseDate(licenseExpiry)
                when {
                    fullName.trim().length < 3 ->
                        error = "Ingresa tu nombre y apellido completo."

                    documentId.length != 8 ->
                        error = "El DNI debe tener exactamente 8 dígitos."

                    phone.length < 9 ->
                        error = "Ingresa tu número de celular de 9 dígitos."

                    email.trim().isBlank() || !email.contains("@") || !email.contains(".") ->
                        error = "Debes ingresar un correo electrónico válido."

                    driverPassword.trim().length < 6 ->
                        error = "Debes ingresar tu contraseña (mínimo 6 caracteres) para confirmar tu cuenta de conductor."

                    DriverRequirements.findCompany(companyCode) == null ->
                        error = "Elige una de las empresas autorizadas de la lista."

                    licenseNumber.trim().length < 6 ->
                        error = "Ingresa tu número de licencia de conducir."

                    parsedLicenseExpiry == null || parsedLicenseExpiry <= System.currentTimeMillis() ->
                        error = "La fecha de vencimiento de la licencia debe ser futura (AAAA-MM-DD)."

                    emergencyContactName.trim().length < 3 ->
                        error = "Ingresa el nombre de tu contacto de emergencia."

                    emergencyContactPhone.length < 9 ->
                        error = "Ingresa el teléfono de emergencia (9 dígitos)."

                    else -> {
                        saving = true
                        scope.launch {
                            try {
                                repository.saveProfile(
                                    DriverProfile(
                                        accountId = accountId,
                                        email = email.trim(),
                                        fullName = fullName.trim(),
                                        documentId = documentId.trim(),
                                        documentType = "DNI",
                                        companyName = companyName.trim(),
                                        companyCode = companyCode.trim(),
                                        birthDate = existing?.birthDate?.takeIf { it.isNotBlank() } ?: "1990-05-15",
                                        phone = phone.trim(),
                                        phoneVerified = true,
                                        licenseNumber = licenseNumber.trim().uppercase(),
                                        licenseClass = licenseClass.trim().uppercase().ifBlank { "A-IIIc" },
                                        licenseExpiresAt = parsedLicenseExpiry,
                                        emergencyContactName = emergencyContactName.trim(),
                                        emergencyContactPhone = emergencyContactPhone.trim(),
                                        status = existing?.status ?: DriverVerificationStatus.VERIFIED,
                                        statusReason = existing?.statusReason.orEmpty(),
                                        rating = existing?.rating ?: 5.0f,
                                        totalTrips = existing?.totalTrips ?: 0,
                                        createdAt = existing?.createdAt ?: System.currentTimeMillis()
                                    )
                                )
                                saving = false
                                onSaved()
                            } catch (e: Exception) {
                                saving = false
                                error = "No se pudo guardar el perfil: ${e.localizedMessage ?: "intenta de nuevo"}"
                            }
                        }
                    }
                }
            },
            enabled = !saving,
            colors = ButtonDefaults.buttonColors(containerColor = SmartBusBlue),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Guardar y continuar al vehículo",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showCompanyPicker) {
        AlertDialog(
            onDismissRequest = { showCompanyPicker = false },
            title = {
                Text(
                    text = "Elige tu empresa con convenio",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    companies.forEach { option ->
                        val selected = option.code.equals(companyCode, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    companyCode = option.code
                                    companyName = option.name
                                    showCompanyPicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    companyCode = option.code
                                    companyName = option.name
                                    showCompanyPicker = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = option.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SmartBusText
                                )
                                Text(
                                    text = "Código: ${option.code} · Operador: ${option.operatorCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SmartBusTextSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCompanyPicker = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}