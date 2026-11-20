package com.ejemplo.smartbus.ui.screens

import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejemplo.smartbus.data.PreferentialManager
import com.ejemplo.smartbus.data.PreferentialRequest
import com.ejemplo.smartbus.data.driver.DriverBlocker
import com.ejemplo.smartbus.data.model.BusRoute
import com.ejemplo.smartbus.data.model.DriverShift
import com.ejemplo.smartbus.data.model.DriverTrip
import com.ejemplo.smartbus.data.model.DriverVehicle
import com.ejemplo.smartbus.ui.theme.SmartBusBlue
import com.ejemplo.smartbus.ui.theme.SmartBusGreen
import com.ejemplo.smartbus.ui.theme.SmartBusRed
import com.ejemplo.smartbus.ui.theme.SmartBusText
import com.ejemplo.smartbus.ui.theme.SmartBusTextSecondary
import com.ejemplo.smartbus.ui.theme.SmartBusYellow
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DriverHomeScreen(
    vehicle: DriverVehicle?,
    shift: DriverShift?,
    blockers: List<DriverBlocker>,
    isWorking: Boolean,
    route: BusRoute?,
    availableRoutes: List<BusRoute> = emptyList(),
    onSelectRoute: (BusRoute) -> Unit = {},
    onToggleOnline: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenVehicle: () -> Unit,
    onOpenEarnings: () -> Unit,
    onSwitchToPassenger: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showRouteSelector by remember { mutableStateOf(false) }
    var showBusQrDialog by remember { mutableStateOf(false) }
    var showPreferentialListDialog by remember { mutableStateOf(false) }

    val effectiveBusId = remember(vehicle?.plate, shift?.accountId) {
        vehicle?.plate?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            ?: shift?.accountId?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            ?: "BUS-01"
    }

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale("es", "PE")
            }
        }
        ttsEngine = engine
        onDispose {
            try {
                engine?.stop()
                engine?.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    fun playPreferentialAlert(request: PreferentialRequest) {
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notificationUri)
            ringtone?.play()
        } catch (_: Exception) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
            } catch (_: Exception) {
            }
        }

        try {
            val spoken = "Atención conductor. Solicitud de atención preferencial: ${request.categoryLabel}."
            ttsEngine?.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "pref_${request.id}")
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(effectiveBusId) {
        PreferentialManager.startListeningForBus(effectiveBusId)
    }

    val pendingRequests by PreferentialManager.pendingRequests.collectAsState()
    val allBusRequests by PreferentialManager.allRequestsForBus.collectAsState()
    val attendedCount = remember(allBusRequests) {
        allBusRequests.count { it.status == "ATTENDED" }
    }
    val activeRequest = pendingRequests.firstOrNull()
    var lastAlertedRequestId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeRequest?.id) {
        val currentReq = activeRequest
        if (currentReq != null && currentReq.id != lastAlertedRequestId) {
            lastAlertedRequestId = currentReq.id
            delay(250)
            playPreferentialAlert(currentReq)
        }
    }

    val routeSubida = remember(route) { route?.subidaPoints.orEmpty() }
    val routeBajada = remember(route) { route?.bajadaPoints.orEmpty() }
    val routeAllPoints = remember(route) {
        (route?.subidaPoints.orEmpty() + route?.bajadaPoints.orEmpty())
            .ifEmpty { route?.points.orEmpty() }
    }

    val routeStart = remember(routeAllPoints) {
        routeAllPoints.firstOrNull()?.let {
            LatLng(it.latitude, it.longitude)
        }
    }

    val driverPosition: LatLng? = remember(shift, isWorking, routeStart) {
        val lat = shift?.lastLatitude
        val lng = shift?.lastLongitude
        if (lat != null && lng != null) {
            LatLng(lat, lng)
        } else {
            routeStart
        }
    }

    val initial = driverPosition ?: routeStart ?: LatLng(-16.4090, -71.5375)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initial, 13f)
    }

    val scope = rememberCoroutineScope()
    var centeredOnDriver by remember { mutableStateOf(false) }

    LaunchedEffect(driverPosition) {
        val position = driverPosition
        if (!centeredOnDriver && position != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(position, 15f),
                600
            )
            centeredOnDriver = true
        }
    }

    LaunchedEffect(route?.id) {
        val start = routeStart
        if (start != null && driverPosition == null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(start, 13.5f),
                600
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = false,
                mapType = MapType.NORMAL,
                minZoomPreference = 11f,
                maxZoomPreference = 20f
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                compassEnabled = true,
                mapToolbarEnabled = false
            ),
            contentPadding = PaddingValues(top = 150.dp, bottom = 210.dp)
        ) {
            val routeColor = SmartBusBlue

            if (routeSubida.size >= 2 || routeBajada.size >= 2) {
                if (routeSubida.size >= 2) {
                    Polyline(
                        points = routeSubida.map { LatLng(it.latitude, it.longitude) },
                        color = routeColor,
                        width = 11f
                    )
                }
                if (routeBajada.size >= 2) {
                    Polyline(
                        points = routeBajada.map { LatLng(it.latitude, it.longitude) },
                        color = routeColor.copy(alpha = 0.78f),
                        width = 9f
                    )
                }
            } else if (routeAllPoints.size >= 2) {
                Polyline(
                    points = routeAllPoints.map { LatLng(it.latitude, it.longitude) },
                    color = routeColor,
                    width = 10f
                )
            }

            val driverCarIcon = remember { CustomMapMarkers.createDriverCarMarker() }
            val startIcon = remember { CustomMapMarkers.createEndpointMarker(isStart = true) }

            if (route != null) {
                routeAllPoints.lastOrNull()?.let { end ->
                    Marker(
                        state = MarkerState(LatLng(end.latitude, end.longitude)),
                        title = route.destination.ifBlank { route.name },
                        snippet = "Fin de ruta (${route.code})",
                        icon = startIcon
                    )
                }
            }

            driverPosition?.let { position ->
                Marker(
                    state = MarkerState(position),
                    title = "🚗 Tu vehículo (Conductor)",
                    snippet = vehicle?.plate ?: "Ubicación del conductor",
                    icon = driverCarIcon
                )
            }
        }

        // ---- Barra superior organizada sin solapamientos ----
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showRouteSelector = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        if (route != null) {
                            Text(
                                text = "${route.code} · ${route.name}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = SmartBusText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "Sin ruta seleccionada",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = SmartBusRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isWorking) SmartBusGreen else SmartBusYellow)
                            )
                            Text(
                                text = if (isWorking) "En servicio" else "Desconectado",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isWorking) SmartBusGreen else SmartBusTextSecondary,
                                maxLines = 1
                            )
                            Text(
                                text = "· Cambiar ruta",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SmartBusBlue,
                                maxLines = 1
                            )
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        IconButton(
                            onClick = { showBusQrDialog = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = "Mostrar QR del bus",
                                tint = SmartBusBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onOpenProfile,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Mis datos",
                                tint = SmartBusBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onOpenDocuments,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = "Mis documentos",
                                tint = SmartBusBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onOpenVehicle,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = "Mi vehiculo",
                                tint = SmartBusBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onOpenEarnings,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Payments,
                                contentDescription = "Mis ganancias",
                                tint = SmartBusBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ---- Contador en tiempo real de Reportes Preferenciales / Discapacidad ----
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPreferentialListDialog = true },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (pendingRequests.isNotEmpty()) Color(0xFF1E3A8A) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Accessible,
                        contentDescription = null,
                        tint = if (pendingRequests.isNotEmpty()) Color.White else SmartBusBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Reportes preferenciales ($effectiveBusId): ${pendingRequests.size} pendientes · $attendedCount atendidos",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pendingRequests.isNotEmpty()) Color.White else SmartBusText,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Ver (${allBusRequests.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (pendingRequests.isNotEmpty()) Color(0xFF93C5FD) else SmartBusBlue
                    )
                }
            }

            // ---- Alerta prioritaria cuando hay una solicitud pendiente ----
            if (activeRequest != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A8A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3B82F6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Accessible,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SOLICITUD DE ATENCIÓN PREFERENCIAL",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF93C5FD)
                                )
                                Text(
                                    text = "${activeRequest.categoryLabel} · ${activeRequest.passengerName}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (activeRequest.note.isNotBlank()) {
                                    Text(
                                        text = "Nota: ${activeRequest.note}",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.9f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(onClick = { playPreferentialAlert(activeRequest) }) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Repetir sonido",
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                PreferentialManager.acknowledgeRequest(activeRequest.id)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SmartBusGreen,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Confirmar atención al pasajero",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // ---- Boton principal y avisos, apilados abajo sin solaparse ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isWorking && blockers.isNotEmpty()) {
                val primaryBlocker = blockers.first()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when {
                                primaryBlocker.title.contains("documento", ignoreCase = true) ||
                                        primaryBlocker.title.contains("rechazado", ignoreCase = true) ->
                                    onOpenDocuments()
                                primaryBlocker.title.contains("vehiculo", ignoreCase = true) ||
                                        primaryBlocker.title.contains("SOAT", ignoreCase = true) ||
                                        primaryBlocker.title.contains("Inspeccion", ignoreCase = true) ->
                                    onOpenVehicle()
                                primaryBlocker.title.contains("ruta", ignoreCase = true) ->
                                    if (vehicle != null && availableRoutes.isNotEmpty()) {
                                        showRouteSelector = true
                                    } else {
                                        onOpenVehicle()
                                    }
                                else -> onOpenProfile()
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF8E1)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = SmartBusRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🔒 Seguridad activa · Pendientes (${blockers.size}): ${primaryBlocker.title}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SmartBusText
                            )
                            Text(
                                text = "${primaryBlocker.detail} (Toca aquí para completar)",
                                style = MaterialTheme.typography.bodySmall,
                                color = SmartBusTextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                isWorking ->
                                    "✅ En línea: tu bus y ruta se transmiten en vivo."

                                blockers.isEmpty() ->
                                    "✅ Verificado y seguro. Listo para salir en ruta."

                                else ->
                                    "Completa tus requisitos y ruta para conectarte con pasajeros."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (blockers.isEmpty() || isWorking) SmartBusGreen else SmartBusTextSecondary,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedButton(
                            onClick = { showRouteSelector = true },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Route,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (route != null) "Ruta: ${route.code}" else "Elegir ruta",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onToggleOnline,
                        enabled = isWorking || blockers.isEmpty(),
                        colors = if (isWorking) {
                            ButtonDefaults.buttonColors(
                                containerColor = SmartBusRed,
                                contentColor = Color.White
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = SmartBusBlue,
                                contentColor = Color.White
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (isWorking) {
                                "Terminar servicio"
                            } else if (blockers.isEmpty()) {
                                "Empezar a conducir"
                            } else {
                                "🔒 Completa requisitos para conectarte"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onSwitchToPassenger,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SmartBusYellow,
                                contentColor = SmartBusText
                            ),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Modo pasajero",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = { showBusQrDialog = true },
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = null,
                                tint = SmartBusBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "QR Bus",
                                fontWeight = FontWeight.Bold,
                                color = SmartBusBlue
                            )
                        }
                    }
                }
            }
        }

        // ---- Volver a centrarse en el conductor ----
        FloatingActionButton(
            onClick = {
                val target = driverPosition ?: routeStart
                if (target != null) {
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(target, 15f),
                            500
                        )
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 230.dp),
            containerColor = Color.White,
            contentColor = SmartBusBlue,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
        ) {
            Icon(
                Icons.Default.MyLocation,
                contentDescription = "Centrar en mi posicion"
            )
        }

        // ---- Dialogo con la cantidad y lista completa de reportes preferenciales ----
        if (showPreferentialListDialog) {
            val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

            AlertDialog(
                onDismissRequest = { showPreferentialListDialog = false },
                title = {
                    Text(
                        text = "♿ Reportes de Atención Preferencial",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${allBusRequests.size}",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SmartBusBlue
                                    )
                                    Text("Total", fontSize = 11.sp, color = SmartBusTextSecondary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${pendingRequests.size}",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SmartBusRed
                                    )
                                    Text("Pendientes", fontSize = 11.sp, color = SmartBusTextSecondary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$attendedCount",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SmartBusGreen
                                    )
                                    Text("Atendidos", fontSize = 11.sp, color = SmartBusTextSecondary)
                                }
                            }
                        }

                        if (allBusRequests.isEmpty()) {
                            Text(
                                text = "Aún no hay reportes registrados para el bus $effectiveBusId.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SmartBusTextSecondary
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(allBusRequests, key = { it.id }) { req ->
                                    val isPending = req.status == "PENDING"
                                    val timeStr = if (req.timestamp > 0L) {
                                        timeFormatter.format(Date(req.timestamp))
                                    } else {
                                        ""
                                    }
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isPending) Color(0xFFFEF2F2) else Color(0xFFF5F7FA)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = req.categoryLabel,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = SmartBusText
                                                )
                                                Text(
                                                    text = if (isPending) "PENDIENTE · $timeStr" else "ATENDIDO · $timeStr",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = if (isPending) SmartBusRed else SmartBusGreen
                                                )
                                            }
                                            Text(
                                                text = "Pasajero: ${req.passengerName}",
                                                fontSize = 12.sp,
                                                color = SmartBusTextSecondary
                                            )
                                            if (req.note.isNotBlank()) {
                                                Text(
                                                    text = "Nota: ${req.note}",
                                                    fontSize = 12.sp,
                                                    color = SmartBusText
                                                )
                                            }
                                            if (isPending) {
                                                TextButton(
                                                    onClick = { PreferentialManager.acknowledgeRequest(req.id) }
                                                ) {
                                                    Text("Marcar como atendido", color = SmartBusGreen, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPreferentialListDialog = false }) {
                        Text("Cerrar")
                    }
                }
            )
        }

        // ---- Dialogo para mostrar el Código QR del Bus ----
        if (showBusQrDialog) {
            val qrPayload = remember(effectiveBusId) {
                PreferentialManager.buildQrDeepLink(effectiveBusId)
            }
            val qrBitmap = remember(qrPayload) {
                PreferentialManager.generateQrBitmap(qrPayload, 520)
            }

            AlertDialog(
                onDismissRequest = { showBusQrDialog = false },
                title = {
                    Text(
                        text = "Código QR del Bus · $effectiveBusId",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Muestra este QR a los pasajeros para que soliciten asiento preferencial o rampa:",
                            style = MaterialTheme.typography.bodySmall,
                            color = SmartBusTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        if (qrBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(220.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR Atención Preferencial",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Text(
                            text = "Enlace: $qrPayload",
                            style = MaterialTheme.typography.labelSmall,
                            color = SmartBusBlue,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showBusQrDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = SmartBusBlue)
                    ) {
                        Text("Listo", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // ---- Dialogo para elegir la ruta donde trabaja el conductor ----
        if (showRouteSelector) {
            AlertDialog(
                onDismissRequest = { showRouteSelector = false },
                title = {
                    Text(
                        text = "🛣️ Escoge la ruta donde trabajas",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Se marcará únicamente tu ruta seleccionada en el mapa y los pasajeros te verán en esta línea:",
                            style = MaterialTheme.typography.bodySmall,
                            color = SmartBusTextSecondary
                        )
                        if (availableRoutes.isEmpty()) {
                            Text(
                                text = "Cargando rutas disponibles...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SmartBusText
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 340.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(availableRoutes, key = { it.id }) { itemRoute ->
                                    val selected = route?.id == itemRoute.id
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSelectRoute(itemRoute)
                                                showRouteSelector = false
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selected) {
                                                SmartBusBlue.copy(alpha = 0.12f)
                                            } else {
                                                Color(0xFFF5F7FA)
                                            }
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (selected) SmartBusGreen else SmartBusBlue
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${itemRoute.code} · ${itemRoute.name}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SmartBusText
                                                )
                                                if (itemRoute.origin.isNotBlank() || itemRoute.destination.isNotBlank()) {
                                                    Text(
                                                        text = "${itemRoute.origin} → ${itemRoute.destination}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = SmartBusTextSecondary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            if (selected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Seleccionada",
                                                    tint = SmartBusGreen,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRouteSelector = false }) {
                        Text("Cerrar")
                    }
                }
            )
        }
    }
}

internal fun todayTrips(trips: List<DriverTrip>): List<DriverTrip> {
    val formatter = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val today = formatter.format(Date())
    return trips.filter { trip ->
        formatter.format(Date(trip.startedAt)) == today
    }
}