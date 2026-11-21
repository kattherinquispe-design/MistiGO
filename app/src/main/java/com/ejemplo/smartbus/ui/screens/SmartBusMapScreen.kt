package com.ejemplo.smartbus.ui.screens

import android.provider.Settings
import android.net.Uri
import android.content.ContextWrapper
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.activity.result.IntentSenderRequest
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ejemplo.smartbus.data.BusLiveRepository
import com.ejemplo.smartbus.data.FirebaseParaderoRepository
import com.ejemplo.smartbus.data.model.Bus
import com.ejemplo.smartbus.data.model.BusRoute
import com.ejemplo.smartbus.data.model.BusStop
import com.ejemplo.smartbus.data.model.GeoPoint
import com.ejemplo.smartbus.location.BusStopDetector
import com.ejemplo.smartbus.location.FixQuality
import com.ejemplo.smartbus.location.LocationFixEvaluator
import com.ejemplo.smartbus.ui.theme.AppPalette
import com.ejemplo.smartbus.ui.theme.SmartBusDisplay
import com.google.android.gms.maps.model.LatLngBounds
import com.ejemplo.smartbus.ui.theme.SmartBusBlue
import com.ejemplo.smartbus.ui.theme.SmartBusDivider
import com.ejemplo.smartbus.ui.theme.SmartBusGreen
import com.ejemplo.smartbus.ui.theme.SmartBusText
import com.ejemplo.smartbus.ui.theme.SmartBusTextSecondary
import com.ejemplo.smartbus.ui.theme.SmartBusTextLight
import com.ejemplo.smartbus.ui.theme.SmartBusYellow
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.location.Geocoder
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flag
import com.ejemplo.smartbus.data.RouteRepository
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val REQUEST_ENABLE_LOCATION = 4201

/**
 * Recuerda si ya le preguntamos al usuario por el permiso de ubicacion.
 * Sin esto no hay forma de distinguir "todavia no le hemos preguntado"
 * de "lo rechazo para siempre".
 */
private const val KEY_ASKED_LOCATION = "asked_location"
@Composable
fun SmartBusMapScreen(
    destination: LatLng? = null,
    destinationName: String? = null,
    onSearchClick: () -> Unit = {},
    onClearDestination: () -> Unit = {},
    stops: List<BusStop> = emptyList(),
    routes: List<BusRoute> = emptyList(),
    selectedRoute: BusRoute? = null,
    selectedStop: BusStop? = null,
    onStopClick: (BusStop) -> Unit = {},
    onRouteClick: (BusRoute) -> Unit = {},
    vehicles: List<Bus>? = null,
    onUserLocationChange: (LatLng) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var locationPermissionGranted by remember { mutableStateOf(false) }
    var hasFinePermission by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var isWaitingForFix by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var userLocationAccuracy by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var isGpsAccurate by remember { mutableStateOf(false) }
    var gpsSignalState by remember { mutableStateOf("Buscando ubicacion...") }
    var lastFixAt by remember { mutableLongStateOf(0L) }
    var lastFixElapsed by remember { mutableLongStateOf(0L) }
    var fixQuality by remember { mutableStateOf(FixQuality.STALE) }
    var isLocationServiceOn by remember { mutableStateOf(true) }
    var isPermanentlyDenied by remember { mutableStateOf(false) }
    var nearestStop by remember { mutableStateOf<BusStop?>(null) }
    var nearestDistance by remember { mutableStateOf<Float?>(null) }

    var isFollowingUser by remember { mutableStateOf(true) }
    var hasMovedManually by remember { mutableStateOf(false) }
    var hasCenteredOnce by remember { mutableStateOf(false) }
    var isProgrammaticMove by remember { mutableStateOf(false) }
    var tripFilter by remember { mutableStateOf<String>("SUBIDA") }
    var locationFrozen by remember { mutableStateOf(false) }
    var showFarBusNotice by remember { mutableStateOf(false) }
    var farBusDistance by remember { mutableStateOf<Float?>(null) }

    val firebaseStops by FirebaseParaderoRepository.stops.collectAsState()
    LaunchedEffect(Unit) {
        FirebaseParaderoRepository.loadStops(context)
    }
    val allStops = remember(stops, firebaseStops) {
        (firebaseStops + stops).distinctBy { it.id }
    }

    val availableRoutes = remember(routes) {
        routes.ifEmpty { RouteRepository.routes }
    }
    var activeRoute by remember(selectedRoute, availableRoutes) {
        mutableStateOf(selectedRoute ?: availableRoutes.firstOrNull())
    }
    var activeStop by remember(selectedStop) {
        mutableStateOf(selectedStop)
    }

    /**
     * Se combinan unicamente los buses reales en vivo desde Firestore
     * ('vehiculos_en_vivo') y los de conductores activos en Room, sin
     * ninguna unidad simulada para no confundir al pasajero.
     */
    val liveBuses by BusLiveRepository.buses.collectAsState(initial = emptyList<Bus>())

    val rawBuses = remember(vehicles, liveBuses) {
        val now = System.currentTimeMillis()
        (liveBuses + (vehicles.orEmpty()))
            .filter { bus ->
                !bus.isMock && (bus.lastUpdate <= 0L || (now - bus.lastUpdate) <= 10 * 60 * 1000L)
            }
            .distinctBy { it.id }
    }
    // Mostrar únicamente los conductores en tiempo real vinculados con la ruta escogida
    val buses = remember(rawBuses, activeRoute) {
        val route = activeRoute ?: return@remember emptyList<Bus>()
        rawBuses.filter { bus ->
            bus.routeId == route.id ||
                (route.code.isNotBlank() && bus.code.equals("Bus ${route.code}", ignoreCase = true))
        }
    }

    // Ruta activa para dibujar en el mapa: la seleccionada o la primera disponible
    val currentRoute = activeRoute ?: if (tripFilter == "BAJADA") {
        availableRoutes.firstOrNull { it.bajadaPoints.isNotEmpty() }
    } else {
        availableRoutes.firstOrNull { it.subidaPoints.isNotEmpty() }
    }

    val rawPointsToShow = when (tripFilter) {
        "BAJADA" -> currentRoute?.bajadaPoints
            ?.ifEmpty { currentRoute.points }
            ?.ifEmpty { currentRoute.subidaPoints }
        "SUBIDA" -> currentRoute?.subidaPoints
            ?.ifEmpty { currentRoute.points }
            ?.ifEmpty { currentRoute.bajadaPoints }
        else -> currentRoute?.points
            ?.ifEmpty { currentRoute.subidaPoints }
            ?.ifEmpty { currentRoute.bajadaPoints }
    } ?: emptyList()

    var snappedRoutePoints by remember(currentRoute?.id, tripFilter, rawPointsToShow.size) {
        mutableStateOf(rawPointsToShow)
    }
    LaunchedEffect(currentRoute?.id, tripFilter, rawPointsToShow) {
        if (rawPointsToShow.size in 2..24) {
            snappedRoutePoints = withContext(Dispatchers.IO) {
                CustomMapMarkers.snapRouteToRealStreets(rawPointsToShow)
            }
        } else {
            snappedRoutePoints = rawPointsToShow
        }
    }
    val pointsToShow = snappedRoutePoints

    // Al escoger una ruta (y mientras el bus activo de esa ruta se acerca), notificar al pasajero
    // el tiempo y distancia del bus activo más cercano, aviso de 5 minutos y emergencia de 3 minutos.
    var lastNotifiedRouteId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(activeRoute?.id, buses, userLocation, pointsToShow) {
        val route = activeRoute ?: return@LaunchedEffect
        if (buses.isEmpty()) return@LaunchedEffect
        val firstRoutePoint = pointsToShow.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
        val refLoc = userLocation ?: firstRoutePoint ?: return@LaunchedEffect

        var closestBus: Bus? = null
        var closestDist = Float.MAX_VALUE
        val d = FloatArray(1)
        for (b in buses) {
            android.location.Location.distanceBetween(refLoc.latitude, refLoc.longitude, b.latitude, b.longitude, d)
            if (d[0] < closestDist) {
                closestDist = d[0]
                closestBus = b
            }
        }

        val nearest = closestBus ?: return@LaunchedEffect
        val speedKmh = if (nearest.speedKmh > 5f) nearest.speedKmh else 22f
        val etaMinutes = kotlin.math.ceil((closestDist / 1000f) / speedKmh * 60f).toInt().coerceAtLeast(1)
        val notifManager = com.ejemplo.smartbus.data.SmartBusNotificationManager(context)

        if (lastNotifiedRouteId != route.id) {
            lastNotifiedRouteId = route.id
            notifManager.showRouteSelectedNearestBus(
                routeName = route.name.ifBlank { route.code },
                busCode = nearest.code,
                etaMinutes = etaMinutes,
                distanceMeters = closestDist.toInt()
            )
        } else {
            when {
                etaMinutes <= 3 -> notifManager.showBusEmergency3Min(
                    busCode = nearest.code,
                    routeName = route.name.ifBlank { route.code },
                    distanceMeters = closestDist.toInt(),
                    etaMinutes = etaMinutes
                )
                etaMinutes in 4..5 -> notifManager.showBusApproaching5Min(
                    busCode = nearest.code,
                    routeName = route.name.ifBlank { route.code },
                    distanceMeters = closestDist.toInt()
                )
            }
        }
    }

    // Sincronizar sentido al cambiar de ruta
    LaunchedEffect(selectedRoute) {
        if (selectedRoute != null) {
            activeRoute = selectedRoute
            if (selectedRoute.tripType.equals("BAJADA", ignoreCase = true)) {
                tripFilter = "BAJADA"
            } else if (selectedRoute.tripType.equals("SUBIDA", ignoreCase = true)) {
                tripFilter = "SUBIDA"
            }
        }
    }




    LaunchedEffect(userLocation) {
        userLocation?.let { onUserLocationChange(it) }
    }

    val activity = context.findActivity()

    val permissionPrefs =
        remember(context) {
            context.getSharedPreferences("smartbus_permission", Context.MODE_PRIVATE)
        }

    /**
     * Android no dice si un permiso esta "para siempre" denegado:
     * shouldShowRequestPermissionRationale tambien devuelve false
     * antes de la primera pregunta. Por eso guardamos si ya se
     * pregunto, para no abrir Ajustes cuando el usuario todavia no
     * ha visto el dialogo (que era lo que impidia pedir el permiso).
     */
    fun updatePermissionState() {

        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        hasFinePermission = fine
        locationPermissionGranted = fine || coarse

        if (!locationPermissionGranted) {
            val asked = permissionPrefs.getBoolean(KEY_ASKED_LOCATION, false)
            val rationale = activity != null &&
                activity.shouldShowRequestPermissionRationale(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )

            isPermanentlyDenied = asked && !rationale
            showPermissionRationale = asked && rationale
        } else {
            isPermanentlyDenied = false
            showPermissionRationale = false
        }
    }

    /**
     * El GPS del telefono puede estar apagado. Con el permiso concedido
     * pero la ubicacion del sistema desactivada, Android no entrega
     * ninguna posicion: por eso lo comprobamos y lo pedimos explicitamente.
     */
    fun updateLocationServiceState() {

        val systemLocationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        isLocationServiceOn =
            systemLocationManager?.isLocationEnabled() ?: true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionPrefs.edit().putBoolean(KEY_ASKED_LOCATION, true).apply()
        updatePermissionState()
    }

    fun requestLocationPermission() {
        permissionPrefs.edit().putBoolean(KEY_ASKED_LOCATION, true).apply()
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    /**
     * Abre los ajustes del sistema cuando el usuario ya denies el
     * permiso y Android deja de mostrar el dialogo.
     */
    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + context.packageName)
        )
        runCatching { context.startActivity(intent) }
    }

    LaunchedEffect(Unit) {
        updatePermissionState()
        updateLocationServiceState()

        if (!locationPermissionGranted) {
            requestLocationPermission()
        }
    }

    /**
     * Al volver de Ajustes o al desbloquear el telefono, el permiso y el
     * GPS pueden haber cambiado. Sin esto, la app se quedaba mostrando
     * "sin permiso" o "sin senal" aunque ya estuviera todo activado.
     */
    DisposableEffect(Unit) {

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    updatePermissionState()
                    updateLocationServiceState()
                }
                else -> Unit
            }
        }

        (activity as? LifecycleOwner)?.lifecycle?.addObserver(observer)

        onDispose {
            (activity as? LifecycleOwner)?.lifecycle?.removeObserver(observer)
        }
    }

    val fusedClient: FusedLocationProviderClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val settingsResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { }

    fun enableLocationSettings(context: Context, onReady: () -> Unit) {
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).build()
            )
            .setAlwaysShow(true)
            .build()

        LocationServices.getSettingsClient(context)
            .checkLocationSettings(settingsRequest)
            .addOnSuccessListener {
                updateLocationServiceState()
                onReady()
            }
            .addOnFailureListener { error ->
                if (error is ResolvableApiException) {
                    try {
                        settingsResolutionLauncher.launch(
                            IntentSenderRequest.Builder(error.resolution).build()
                        )
                    } catch (_: Exception) {
                    }
                }
                onReady()
            }
    }

    val initialLocation: LatLng = when {
        userLocation != null -> userLocation!!
        destination != null -> destination
        selectedRoute != null && selectedRoute.points.isNotEmpty() ->
            LatLng(selectedRoute.points.first().latitude, selectedRoute.points.first().longitude)
        selectedStop != null -> LatLng(selectedStop.latitude, selectedStop.longitude)
        else -> LatLng(0.0, 0.0)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            initialLocation,
            if (destination != null) 16f else 2f
        )
    }

    LaunchedEffect(pointsToShow, tripFilter) {
        if (pointsToShow.isNotEmpty()) {
            isProgrammaticMove = true
            isFollowingUser = false
            hasMovedManually = false
            if (pointsToShow.size >= 2) {
                try {
                    val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.builder()
                    pointsToShow.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
                    val bounds = boundsBuilder.build()
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                } catch (_: Exception) {
                    val mid = pointsToShow[pointsToShow.size / 2]
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(mid.latitude, mid.longitude), 14f))
                }
            } else {
                val first = pointsToShow.first()
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 15f))
            }
            isProgrammaticMove = false
        }
    }

    var activeCallback: LocationCallback? by remember { mutableStateOf(null) }

    /**
     * Aplica una lectura de GPS ya validada: centra la camara si toca,
     * actualiza la precision y busca el paradero mas cercano.
     */
    fun applyFix(location: Location, quality: FixQuality, nowElapsed: Long) {

        lastFixElapsed = nowElapsed
        lastFixAt = System.currentTimeMillis()
        userLocationAccuracy = location.accuracy
        fixQuality = quality
        isGpsAccurate =
            quality == FixQuality.EXCELLENT ||
                quality == FixQuality.GOOD
        isWaitingForFix = false
        gpsSignalState = LocationFixEvaluator.labelOf(quality)

        val currentLatLng = LatLng(location.latitude, location.longitude)
        userLocation = currentLatLng

        if (!hasCenteredOnce || (isFollowingUser && !hasMovedManually)) {
            val isFirstFix = !hasCenteredOnce
            hasCenteredOnce = true
            isFollowingUser = true
            hasMovedManually = false
            isProgrammaticMove = true
            scope.launch {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f),
                    if (isFirstFix) 700 else 250
                )
                isProgrammaticMove = false
            }
        }

        val activeRouteStops = CustomMapMarkers.resolveStopsForRoute(
            route = currentRoute,
            allStops = allStops,
            explicitPoints = pointsToShow
        )
        var closest: BusStop? = null
        var closestDistance = Float.MAX_VALUE
        activeRouteStops.forEach { stop ->
            val distance = BusStopDetector.calculateDistance(location, stop)
            if (distance < closestDistance) {
                closestDistance = distance
                closest = stop
            }
        }
        nearestStop = closest
        nearestDistance =
            if (closestDistance != Float.MAX_VALUE) closestDistance else null

        val matchedStop = closest
        if (matchedStop != null && closestDistance <= matchedStop.radiusMeters) {
            com.ejemplo.smartbus.data.SmartBusNotificationManager(context)
                .showArrivalAtStop(matchedStop.name)
        }
    }

    /**
     * Evalua una lectura de GPS y la aplica si esUtil.
     *
     * Las lecturas en vivo siempre se evaluan con [isCachedHint] = false,
     * de modo que nunca se descartan por estar lejos de una posicion
     * anterior. Eso era lo que dejaba al usuario clavado en un barrio
     * donde nunca estuvo.
     */
    fun acceptFix(location: Location, isCachedHint: Boolean): Boolean {

        val nowElapsed = SystemClock.elapsedRealtime()

        val decision = LocationFixEvaluator.evaluate(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            elapsedRealtimeMs = nowElapsed,
            fixElapsedRealtimeMs =
                if (location.elapsedRealtimeNanos > 0L) {
                    location.elapsedRealtimeNanos / 1_000_000L
                } else {
                    0L
                },
            currentLatitude = userLocation?.latitude,
            currentLongitude = userLocation?.longitude,
            currentAcceptedAtElapsedMs = lastFixElapsed,
            isCachedHint = isCachedHint
        )

        if (!decision.accept) {
            if (!hasCenteredOnce) {
                isWaitingForFix = true
                gpsSignalState = LocationFixEvaluator.labelOf(decision.quality)
            }
            return false
        }

        applyFix(location, decision.quality, nowElapsed)
        return true
    }

    val startLocation = {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
            .setMinUpdateIntervalMillis(750L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {

                var accepted = 0

                result.locations.forEach { location ->
                    if (acceptFix(location, isCachedHint = false)) {
                        accepted++
                    }
                }

                if (accepted == 0 && !hasCenteredOnce) {
                    isWaitingForFix = true
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            activeCallback = callback

            isWaitingForFix = true

            // La ultima posicion que el sistema tiene guardada solo se usa
            // para arrancar. Si es mas vieja de 30 segundos se descarta:
            // puede ser de otro barrio, y por eso la app mostraba un
            // lugar donde el usuario nunca estuvo.
            fusedClient.lastLocation
                .addOnSuccessListener { last ->
                    if (last != null) {
                        acceptFix(last, isCachedHint = true)
                    }
                }

        } catch (_: SecurityException) {
        }
    }


    LaunchedEffect(destination, selectedRoute) {
        if (destination != null && selectedRoute == null) {
            hasMovedManually = false
            isFollowingUser = false
            isProgrammaticMove = true
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(destination, 16f),
                700
            )
            isProgrammaticMove = false
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCenteredOnce) {
            if (userLocation != null) {
                isProgrammaticMove = true
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(initialLocation, 16f),
                    600
                )
                isProgrammaticMove = false
            } else {
                isProgrammaticMove = true
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
                    ),
                    400
                )
                isProgrammaticMove = false
            }
        }
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && !isProgrammaticMove) {
            hasMovedManually = true
            isFollowingUser = false
        }
    }

    DisposableEffect(locationPermissionGranted, isLocationServiceOn) {
        if (!locationPermissionGranted || !isLocationServiceOn) {
            onDispose { }
        } else {
            enableLocationSettings(context) {
                updateLocationServiceState()
                startLocation()
            }
            onDispose {
                activeCallback?.let { callback ->
                    try {
                        fusedClient.removeLocationUpdates(callback)
                    } catch (_: Exception) {
                    }
                }
                activeCallback = null
            }
        }
    }

    fun centerOnUser() {
        val target = userLocation
        if (target == null) {
            requestLocationPermission()
            return
        }
        isFollowingUser = true
        hasMovedManually = false
        scope.launch {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(target, 17f),
                550
            )
        }
    }

    val baseRelevantStops = remember(allStops, currentRoute, pointsToShow) {
        CustomMapMarkers.resolveStopsForRoute(
            route = currentRoute,
            allStops = allStops,
            explicitPoints = pointsToShow
        )
    }

    var relevantStops by remember(baseRelevantStops) {
        mutableStateOf(baseRelevantStops)
    }

    // Resolver nombres reales de calles/avenidas por coordenadas (Geocoder con caché en memoria y timeout anti-bloqueo)
    LaunchedEffect(currentRoute?.id, tripFilter, baseRelevantStops) {
        if (baseRelevantStops.isEmpty()) return@LaunchedEffect
        val enriched = withContext(Dispatchers.IO) {
            val geocoder = runCatching { Geocoder(context, Locale("es", "PE")) }.getOrNull()
            baseRelevantStops.map { stop ->
                if (!stop.name.startsWith("Paradero ") || !stop.name.contains("·")) {
                    stop
                } else {
                    val cacheKey = "${(stop.latitude * 1000).toInt()}_${(stop.longitude * 1000).toInt()}"
                    val cached = CustomMapMarkers.geocoderStreetCache[cacheKey]
                    val streetName = cached ?: runCatching {
                        kotlinx.coroutines.withTimeoutOrNull(1800L) {
                            val addr = geocoder?.getFromLocation(stop.latitude, stop.longitude, 1)?.firstOrNull()
                            val road = addr?.thoroughfare?.takeIf { it.isNotBlank() && !it.equals("Unnamed Road", ignoreCase = true) }
                            val area = addr?.subLocality ?: addr?.featureName?.takeIf { it.any { c -> c.isLetter() } }
                            when {
                                road != null && area != null && !road.contains(area, ignoreCase = true) -> "$road ($area)"
                                road != null -> road
                                area != null -> area
                                else -> null
                            }
                        }
                    }.getOrNull()?.also { resolved ->
                        CustomMapMarkers.geocoderStreetCache[cacheKey] = resolved
                    }

                    if (!streetName.isNullOrBlank()) {
                        stop.copy(name = "Paradero $streetName")
                    } else {
                        stop
                    }
                }
            }
        }
        relevantStops = enriched
        activeStop?.let { currentActive ->
            enriched.firstOrNull { it.id == currentActive.id }?.let { updated ->
                activeStop = updated
            }
        }
    }

    // Actualizar siempre el paradero más cercano únicamente dentro de los paraderos que pasan por la ruta escogida
    LaunchedEffect(userLocation, relevantStops) {
        val loc = userLocation
        if (loc != null && relevantStops.isNotEmpty()) {
            var closest: BusStop? = null
            var closestDist = Float.MAX_VALUE
            val d = FloatArray(1)
            relevantStops.forEach { s ->
                android.location.Location.distanceBetween(loc.latitude, loc.longitude, s.latitude, s.longitude, d)
                if (d[0] < closestDist) {
                    closestDist = d[0]
                    closest = s
                }
            }
            nearestStop = closest
            nearestDistance = if (closestDist != Float.MAX_VALUE) closestDist else null
        } else if (relevantStops.isNotEmpty()) {
            nearestStop = relevantStops.firstOrNull()
            nearestDistance = null
        }
    }

    // Solo se marca el recorrido al paradero cuando el usuario presiona la bandera del paradero (o lo elige en el buscador)
    val targetStop = activeStop
    val walkingOrigin = userLocation ?: pointsToShow.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
    var realWalkingDistance by remember(targetStop) { mutableStateOf<Float?>(null) }
    val walkingDistanceMeters = realWalkingDistance ?: remember(walkingOrigin, targetStop) {
        if (walkingOrigin != null && targetStop != null) {
            val d = FloatArray(1)
            android.location.Location.distanceBetween(
                walkingOrigin.latitude, walkingOrigin.longitude,
                targetStop.latitude, targetStop.longitude,
                d
            )
            d[0]
        } else null
    }
    var walkingPathPoints by remember(targetStop) {
        mutableStateOf<List<LatLng>>(emptyList())
    }

    LaunchedEffect(walkingOrigin, targetStop, pointsToShow) {
        val from = walkingOrigin
        val stop = targetStop
        if (from == null || stop == null) {
            walkingPathPoints = emptyList()
            realWalkingDistance = null
            return@LaunchedEffect
        }
        val to = LatLng(stop.latitude, stop.longitude)
        // 1. Inmediato (0 ms): mostrar de inmediato el trazado vial y distancia para fluidez total en cualquier celular
        if (walkingPathPoints.isEmpty()) {
            val instant = CustomMapMarkers.buildStreetGridFallback(from, to, pointsToShow)
            walkingPathPoints = instant.first
            if (realWalkingDistance == null && instant.second > 0f) {
                realWalkingDistance = instant.second
            }
        }
        // 2. Refinamiento en segundo plano sin bloquear jamás la interfaz
        val routeResult = withContext(Dispatchers.IO) {
            CustomMapMarkers.fetchRealStreetWalkingRoute(
                context = context,
                from = from,
                to = to,
                routePoints = pointsToShow
            )
        }
        if (routeResult.first.size >= 2) {
            walkingPathPoints = routeResult.first
            if (routeResult.second > 0f) {
                realWalkingDistance = routeResult.second
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasFinePermission,
                mapType = MapType.NORMAL,
                minZoomPreference = 12f,
                maxZoomPreference = 20f
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = hasFinePermission,
                zoomControlsEnabled = false,
                compassEnabled = true,
                mapToolbarEnabled = false
            ),
            contentPadding = PaddingValues(bottom = 110.dp)
        ) {
            val driverCarIcon = remember { CustomMapMarkers.createDriverCarMarker() }
            val stopFlagNormalIcon = remember { CustomMapMarkers.createStopFlagMarker(isSelected = false) }
            val stopFlagSelectedIcon = remember { CustomMapMarkers.createStopFlagMarker(isSelected = true) }
            val startRouteIcon = remember { CustomMapMarkers.createEndpointMarker(isStart = true) }
            val userPinIcon = remember { CustomMapMarkers.createUserMarker() }

            if (pointsToShow.size >= 2) {
                val routeLatLngs = pointsToShow.map { LatLng(it.latitude, it.longitude) }
                val routeBlueColor = Color(0xFF2563EB)
                val haloColor = if (tripFilter == "BAJADA") Color(0x440284C7) else Color(0x442563EB)
                val coreColor = if (tripFilter == "BAJADA") Color(0xFF0284C7) else routeBlueColor

                // 1. Halo exterior azul translúcido (sombra estética de alto contraste)
                Polyline(
                    points = routeLatLngs,
                    color = haloColor,
                    width = 15f
                )

                // 2. Línea principal azul sólida estética de la ruta del bus
                Polyline(
                    points = routeLatLngs,
                    color = coreColor,
                    width = 8f
                )

                routeLatLngs.firstOrNull()?.let { start ->
                    Marker(
                        state = MarkerState(position = start),
                        title = "Inicio (" + (if (tripFilter == "BAJADA") "Bajada" else "Subida") + "): " + (currentRoute?.origin ?: "Origen"),
                        snippet = currentRoute?.name,
                        icon = startRouteIcon
                    )
                }
            }

            // 3. Recorrido de cómo llegar hasta el paradero: SOLO aparece al presionar la bandera del paradero
            if (targetStop != null && walkingPathPoints.size >= 2) {
                Polyline(
                    points = walkingPathPoints,
                    color = Color(0x4410B981),
                    width = 16f
                )
                Polyline(
                    points = walkingPathPoints,
                    color = Color(0xFF059669),
                    width = 8.5f,
                    pattern = listOf(Dash(28f), Gap(16f))
                )
            }

            // 4. Paraderos que pasan por la ruta escogida mostrados como BANDERA en el mapa
            relevantStops.forEach { stop ->
                val isSelectedStop = targetStop?.id == stop.id
                Marker(
                    state = MarkerState(
                        position = LatLng(stop.latitude, stop.longitude)
                    ),
                    title = "🚩 " + stop.name,
                    snippet = if (isSelectedStop && walkingDistanceMeters != null) {
                        "Cómo llegar: a ${walkingDistanceMeters.roundToInt()} m (~${(walkingDistanceMeters / 75f).roundToInt().coerceAtLeast(1)} min a pie)"
                    } else {
                        "Presiona esta bandera para ver información y cómo llegar"
                    },
                    icon = if (isSelectedStop) stopFlagSelectedIcon else stopFlagNormalIcon,
                    onClick = {
                        activeStop = stop
                        val from = walkingOrigin
                        if (from != null) {
                            val instant = CustomMapMarkers.buildStreetGridFallback(
                                from = from,
                                to = LatLng(stop.latitude, stop.longitude),
                                routePoints = pointsToShow
                            )
                            walkingPathPoints = instant.first
                            realWalkingDistance = instant.second
                        }
                        onStopClick(stop)
                        false
                    }
                )
            }

            // 5. Conductores / Buses en vivo con marcador personalizado de CARRITO
            buses.forEach { bus ->
                val dist = userLocation?.let { ul ->
                    val res = FloatArray(1)
                    android.location.Location.distanceBetween(
                        ul.latitude,
                        ul.longitude,
                        bus.latitude,
                        bus.longitude,
                        res
                    )
                    res[0]
                }

                val etaMinutes = dist?.let { d ->
                    val speed = if (bus.speedKmh > 5f) bus.speedKmh else 22f
                    kotlin.math.ceil((d / 1000f) / speed * 60f).toInt().coerceAtLeast(1)
                }

                val etaText = if (etaMinutes != null) {
                    if (etaMinutes <= 1) "🚗 ¡Llegando a tu posición!" else "🚗 Llegada estimada: ~$etaMinutes min (${dist?.toInt()} m) • ${bus.status}"
                } else {
                    vehicleSnippet(bus)
                }

                Marker(
                    state = MarkerState(
                        position = LatLng(bus.latitude, bus.longitude)
                    ),
                    title = vehicleTitle(bus),
                    snippet = etaText,
                    icon = driverCarIcon
                )
            }

            destination?.let { target ->
                Marker(
                    state = MarkerState(position = target),
                    title = destinationName ?: "Destino",
                    snippet = "Destino seleccionado",
                    icon = stopFlagSelectedIcon
                )
            }

            // Posicion real del usuario con marcador personalizado
            userLocation?.let { here ->
                Marker(
                    state = MarkerState(position = here),
                    title = "Mi ubicacion",
                    snippet = if (userLocationAccuracy != Float.MAX_VALUE) {
                        "Precision de ${userLocationAccuracy.roundToInt()} m"
                    } else {
                        "Ubicacion en tiempo real"
                    },
                    icon = userPinIcon
                )
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSearchClick() }
                    .padding(start = 16.dp, top = 14.dp, end = 6.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar destino",
                    tint = SmartBusBlue
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = destinationName ?: "A donde quieres ir?",
                        color = if (destinationName != null) SmartBusText else SmartBusTextLight,
                        fontWeight = if (destinationName != null) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (destinationName != null) {
                        Text(
                            text = "Toca para cambiar el destino",
                            color = SmartBusTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                if (destination != null) {
                    IconButton(onClick = { onClearDestination() }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Limpiar destino",
                            tint = SmartBusTextSecondary
                        )
                    }
                }

                IconButton(onClick = { centerOnUser() }) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Centrar en mi ubicacion",
                        tint = if (locationPermissionGranted) SmartBusBlue else SmartBusTextLight
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 92.dp, end = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (isGpsAccurate) SmartBusGreen else SmartBusYellow)
                )
                Column {
                    Text(
                        text = gpsSignalState,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartBusText
                    )
                    if (userLocationAccuracy != Float.MAX_VALUE) {
                        Text(
                            text = "±${userLocationAccuracy.roundToInt()} m",
                            fontSize = 10.sp,
                            color = SmartBusTextSecondary
                        )
                    }
                }
            }
        }

        if (!locationPermissionGranted) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = SmartBusBlue,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "Activa tu ubicacion",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SmartBusText
                    )
                    Text(
                        text = if (isPermanentlyDenied) {
                            "El permiso de ubicacion quedo bloqueado. Activalo en Ajustes para que SmartBus pueda mostrarte donde estas y los buses mas cercanos."
                        } else {
                            "SmartBus necesita tu ubicacion para mostrarte los buses mas cercanos y calcular tu ETA."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartBusTextSecondary
                    )
                    androidx.compose.material3.Button(
                        onClick = {
                            if (isPermanentlyDenied) {
                                openAppSettings()
                            } else {
                                requestLocationPermission()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isPermanentlyDenied) {
                                "Abrir ajustes"
                            } else {
                                "Permitir ubicacion"
                            }
                        )
                    }
                }
            }
        }

        // GPS del telefono apagado: con el permiso concedido pero sin
        // ubicacion del sistema, Android no entrega ninguna posicion.
        if (locationPermissionGranted && !isLocationServiceOn) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = SmartBusBlue,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "GPS apagado",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SmartBusText
                    )
                    Text(
                        text = "La ubicacion del telefono esta desactivada. Enciendela para que SmartBus te localize de verdad.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartBusTextSecondary
                    )
                    androidx.compose.material3.Button(
                        onClick = {
                            enableLocationSettings(context) {
                                updateLocationServiceState()
                                startLocation()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Encender GPS")
                    }
                }
            }
        }

        if (locationPermissionGranted && !hasFinePermission) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        text = "Ubicacion aproximada",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SmartBusText
                    )
                    Text(
                        text = "Tu ubicacion precisa esta desactivada, por eso el mapa puede ser inexacto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartBusTextSecondary
                    )
                    androidx.compose.material3.Button(
                        onClick = { requestLocationPermission() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Activar ubicacion precisa")
                    }
                }
            }
        }

        if (locationPermissionGranted && hasFinePermission && isWaitingForFix) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp, start = 28.dp, end = 28.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Obteniendo tu ubicacion real",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SmartBusText
                    )
                    Text(
                        text = gpsSignalState,
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartBusTextSecondary
                    )
                    Text(
                        text = "El mapa se movera a tu posicion en cuanto el GPS responda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartBusTextLight
                    )
                }
            }
        }

        if (showPermissionRationale) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showPermissionRationale = false },
                title = { Text("Permiso de ubicacion") },
                text = {
                    Text(
                        "SmartBus usa tu ubicacion en tiempo real para " +
                            "mostrarte el bus mas cercano, tu ETA y validar " +
                            "los abordajes. Sin este permiso el mapa no " +
                            "puede mostrar tu posicion."
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showPermissionRationale = false
                            requestLocationPermission()
                        }
                    ) {
                        Text("Permitir")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showPermissionRationale = false }
                    ) {
                        Text("Ahora no")
                    }
                }
            )
        }

        val stop = targetStop ?: nearestStop
        val distance = walkingDistanceMeters ?: nearestDistance
        if (stop != null) {
            val walkMins = distance?.let { (it / 75f).roundToInt().coerceAtLeast(1) }
            val isStopSelected = targetStop != null
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 104.dp, end = 76.dp)
                    .clickable {
                        activeStop = stop
                        onStopClick(stop)
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isStopSelected) Color(0xFF059669) else SmartBusBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Paradero",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "🚩 ${stop.name}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SmartBusText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                isStopSelected && distance != null ->
                                    "📍 Ruta ${currentRoute?.code ?: ""} · A ${distance.roundToInt()} m (~$walkMins min a pie)"
                                distance != null ->
                                    "📍 Paradero más cercano a ${distance.roundToInt()} m · Toca una bandera para ir"
                                else ->
                                    "📍 En ruta ${currentRoute?.name ?: ""} · Toca una bandera en el mapa"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isStopSelected) Color(0xFF059669) else SmartBusBlue
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 78.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (availableRoutes.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableRoutes, key = { it.id }) { routeItem ->
                        val isSelected = currentRoute?.id == routeItem.id
                        Card(
                            modifier = Modifier.clickable {
                                activeRoute = routeItem
                                activeStop = null
                                onRouteClick(routeItem)
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) AppPalette.primary else Color.White.copy(alpha = 0.96f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsBus,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else AppPalette.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = routeItem.code.ifBlank { routeItem.name },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else SmartBusText
                                )
                            }
                        }
                    }
                }
            }

            if (currentRoute != null && pointsToShow.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (tripFilter == "BAJADA") Color(0xFFEA580C) else AppPalette.primary)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = (currentRoute?.name ?: "") + " • " + (if (tripFilter == "BAJADA") "BAJADA (Vuelta)" else "SUBIDA (Ida)"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = SmartBusText
                            )
                            Text(
                                text = (currentRoute?.origin ?: "") + " → " + (currentRoute?.destination ?: ""),
                                fontSize = 11.sp,
                                color = SmartBusTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { tripFilter = "SUBIDA" },
                containerColor = if (tripFilter == "SUBIDA") AppPalette.primary else Color.White,
                contentColor = if (tripFilter == "SUBIDA") Color.White else AppPalette.primary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
            ) {
                Text("Subida", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = SmartBusDisplay)
            }
            FloatingActionButton(
                onClick = { tripFilter = "BAJADA" },
                containerColor = if (tripFilter == "BAJADA") Color(0xFFEA580C) else Color.White,
                contentColor = if (tripFilter == "BAJADA") Color.White else Color(0xFFEA580C),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
            ) {
                Text("Bajada", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = SmartBusDisplay)
            }
            FloatingActionButton(
                onClick = { centerOnUser() },
                containerColor = Color.White,
                contentColor = SmartBusBlue,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Centrar en mi ubicacion"
                )
            }
        }
    }
}

/**
 * Titulo del marcador de un vehiculo.
 *
 * Si el vehiculo tiene conductor verificado se muestra su nombre y
 * los ultimos 4 de la placa, que es como lo identifica el pasajero.
 * Si viene del simulador se dice explicitamente: una demostracion no
 * debe parecer un auto real en servicio.
 */
private fun vehicleTitle(bus: Bus): String {

    val plate = if (bus.plateCount > 0) {
        " · placa ${bus.plateCount}"
    } else {
        ""
    }

    return when {
        bus.isMock -> "Bus ${bus.code} (demo)"
        bus.driverName.isNotBlank() ->
            "${bus.driverName.substringBefore(' ')} · ${bus.code}$plate"

        else -> "Bus ${bus.code}$plate"
    }
}

private fun vehicleSnippet(bus: Bus): String {

    val speed = "${bus.speedKmh.roundToInt()} km/h"

    return when {
        bus.isMock ->
            "$speed · posicion simulada, no hay conductor real"

        bus.capacity > 0 ->
            "$speed · ${bus.capacity} lugares · ${bus.status}"

        else ->
            "$speed · ${bus.status}"
    }
}

/**
 * Obtiene la Activity actual para usar
 * shouldShowRequestPermissionRationale.
 */
internal fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return null
}

/**
 * Generador de marcadores personalizados (Carrito para conductor, Bandera para paradero,
 * Pines de inicio/fin y usuario) y trazo peatonal hacia el paradero.
 */
internal object CustomMapMarkers {

    val geocoderStreetCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val walkingRouteCache = java.util.concurrent.ConcurrentHashMap<String, Pair<List<LatLng>, Float>>()
    private val snappedCorridorCache = java.util.concurrent.ConcurrentHashMap<String, List<GeoPoint>>()

    /**
     * Si una ruta tiene pocos puntos de referencia (< 25), los ajusta automáticamente a las pistas
     * y avenidas reales de Arequipa para que jamás pase por encima de las casas.
     */
    fun snapRouteToRealStreets(waypoints: List<GeoPoint>): List<GeoPoint> {
        if (waypoints.size < 2 || waypoints.size >= 25) return waypoints
        val key = waypoints.joinToString(";") { "${(it.longitude * 10000).toInt()},${(it.latitude * 10000).toInt()}" }
        snappedCorridorCache[key]?.let { return it }

        val coordsParam = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
        val urls = listOf(
            "https://routing.openstreetmap.de/routed-car/route/v1/driving/$coordsParam?overview=full&geometries=geojson",
            "https://router.project-osrm.org/route/v1/driving/$coordsParam?overview=full&geometries=geojson"
        )
        for (u in urls) {
            val res = requestOsrmWalking(u)
            if (res != null && res.first.size >= waypoints.size) {
                val snapped = res.first.map { GeoPoint(latitude = it.latitude, longitude = it.longitude) }
                snappedCorridorCache[key] = snapped
                return snapped
            }
        }
        return waypoints
    }

    /**
     * Obtiene el camino real calle por calle (como Google Maps) desde la ubicación del usuario
     * hasta el paradero seleccionado:
     * 1. Intenta Google Maps Directions API (mode=walking) con la API Key del proyecto.
     * 2. Si la clave no tiene Directions API habilitada en Google Cloud, consulta el motor de
     *    ruteo vial/peatonal real de calles (OSRM / OpenStreetMap Foot & Street Router en formato GeoJSON)
     *    que devuelve cada esquina, cuadra y avenida real de Arequipa.
     * 3. Si el teléfono está sin internet, sigue el corredor vial más cercano de la ruta.
     */
    fun fetchRealStreetWalkingRoute(
        context: Context,
        from: LatLng,
        to: LatLng,
        routePoints: List<GeoPoint> = emptyList()
    ): Pair<List<LatLng>, Float> {
        val cacheKey = "${(from.latitude * 2000).toInt()},${(from.longitude * 2000).toInt()}_" +
            "${(to.latitude * 2000).toInt()},${(to.longitude * 2000).toInt()}"
        walkingRouteCache[cacheKey]?.let { return it }

        // 1. Intentar con Google Maps Directions API oficial
        val apiKey = runCatching {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
        }.getOrDefault("")

        if (apiKey.isNotBlank()) {
            val googleResult = requestGoogleDirectionsWalking(from, to, apiKey)
            if (googleResult != null && googleResult.first.size >= 2) {
                walkingRouteCache[cacheKey] = googleResult
                return googleResult
            }
        }

        // 2. Motor de ruteo peatonal real por calles (OpenStreetMap / OSRM GeoJSON & Polyline)
        val osrmUrls = listOf(
            "https://routing.openstreetmap.de/routed-foot/route/v1/driving/${from.longitude},${from.latitude};${to.longitude},${to.latitude}?overview=full&geometries=geojson",
            "https://router.project-osrm.org/route/v1/foot/${from.longitude},${from.latitude};${to.longitude},${to.latitude}?overview=full&geometries=geojson",
            "https://routing.openstreetmap.de/routed-car/route/v1/driving/${from.longitude},${from.latitude};${to.longitude},${to.latitude}?overview=full&geometries=geojson"
        )
        for (urlStr in osrmUrls) {
            val osrmResult = requestOsrmWalking(urlStr)
            if (osrmResult != null && osrmResult.first.size >= 2) {
                walkingRouteCache[cacheKey] = osrmResult
                return osrmResult
            }
        }

        // 3. Respaldo offline siguiendo el corredor vial de la ruta y cuadras ortogonales
        val fallback = buildStreetGridFallback(from, to, routePoints)
        return fallback
    }

    private fun requestGoogleDirectionsWalking(
        from: LatLng,
        to: LatLng,
        apiKey: String
    ): Pair<List<LatLng>, Float>? {
        return try {
            val url = java.net.URL(
                "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=${from.latitude},${from.longitude}" +
                    "&destination=${to.latitude},${to.longitude}" +
                    "&mode=walking&key=$apiKey"
            )
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 1500
                readTimeout = 1500
                requestMethod = "GET"
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(body)
            if (json.optString("status") != "OK") return null
            val routes = json.optJSONArray("routes") ?: return null
            if (routes.length() == 0) return null
            val firstRoute = routes.getJSONObject(0)

            var totalDist = 0f
            val stepPoints = mutableListOf<LatLng>()
            val legs = firstRoute.optJSONArray("legs")
            if (legs != null && legs.length() > 0) {
                val leg = legs.getJSONObject(0)
                totalDist = leg.optJSONObject("distance")?.optDouble("value", 0.0)?.toFloat() ?: 0f
                val steps = leg.optJSONArray("steps")
                if (steps != null) {
                    for (i in 0 until steps.length()) {
                        val poly = steps.getJSONObject(i)
                            .optJSONObject("polyline")
                            ?.optString("points")
                            .orEmpty()
                        if (poly.isNotBlank()) {
                            stepPoints.addAll(decodePolyline(poly))
                        }
                    }
                }
            }

            val finalPoints = if (stepPoints.size >= 2) {
                stepPoints
            } else {
                val overview = firstRoute.optJSONObject("overview_polyline")?.optString("points").orEmpty()
                if (overview.isNotBlank()) decodePolyline(overview) else emptyList()
            }

            if (finalPoints.size >= 2) finalPoints to totalDist else null
        } catch (_: Exception) {
            null
        }
    }

    private fun requestOsrmWalking(urlStr: String): Pair<List<LatLng>, Float>? {
        return try {
            val conn = (java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MistiGo-Android/1.0")
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(body)
            val routes = json.optJSONArray("routes") ?: return null
            if (routes.length() == 0) return null
            val routeObj = routes.getJSONObject(0)
            val dist = routeObj.optDouble("distance", 0.0).toFloat()

            // Soporta tanto geometries=geojson (LineString exacto de coordenadas) como geometries=polyline
            val geomObj = routeObj.optJSONObject("geometry")
            if (geomObj != null) {
                val coords = geomObj.optJSONArray("coordinates")
                if (coords != null && coords.length() >= 2) {
                    val pts = ArrayList<LatLng>(coords.length())
                    for (i in 0 until coords.length()) {
                        val pair = coords.optJSONArray(i) ?: continue
                        val lng = pair.optDouble(0)
                        val lat = pair.optDouble(1)
                        if (!lat.isNaN() && !lng.isNaN()) {
                            pts.add(LatLng(lat, lng))
                        }
                    }
                    if (pts.size >= 2) return pts to dist
                }
            }

            val geometry = routeObj.optString("geometry").orEmpty()
            if (geometry.isBlank()) return null
            val decoded = decodePolyline(geometry)
            if (decoded.size >= 2) decoded to dist else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decodificador estándar de polilíneas codificadas de Google Maps (Encoded Polyline Algorithm).
     */
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < len)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            if (index >= len) break
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < len)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            poly.add(LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
        }
        return poly
    }

    fun buildStreetGridFallback(
        from: LatLng,
        to: LatLng,
        routePoints: List<GeoPoint>
    ): Pair<List<LatLng>, Float> {
        val path = mutableListOf<LatLng>()
        path.add(from)

        if (routePoints.size >= 2) {
            val fromIdx = routePoints.indices.minByOrNull { idx ->
                val p = routePoints[idx]
                val dLat = p.latitude - from.latitude
                val dLng = p.longitude - from.longitude
                dLat * dLat + dLng * dLng
            } ?: 0
            val toIdx = routePoints.indices.minByOrNull { idx ->
                val p = routePoints[idx]
                val dLat = p.latitude - to.latitude
                val dLng = p.longitude - to.longitude
                dLat * dLat + dLng * dLng
            } ?: 0

            val entryPt = routePoints[fromIdx]
            // Esquina ortogonal desde el usuario hasta la avenida de la ruta
            path.add(LatLng(from.latitude, entryPt.longitude))
            path.add(LatLng(entryPt.latitude, entryPt.longitude))

            val step = if (fromIdx <= toIdx) 1 else -1
            var i = fromIdx
            var safety = 0
            while (i != toIdx && safety < 120) {
                val rp = routePoints[i]
                path.add(LatLng(rp.latitude, rp.longitude))
                i += step
                safety++
            }
        } else {
            val midLat = from.latitude + (to.latitude - from.latitude) * 0.5
            path.add(LatLng(midLat, from.longitude))
            path.add(LatLng(midLat, to.longitude))
        }

        path.add(to)

        var totalDist = 0f
        val d = FloatArray(1)
        for (k in 0 until path.size - 1) {
            android.location.Location.distanceBetween(
                path[k].latitude, path[k].longitude,
                path[k + 1].latitude, path[k + 1].longitude,
                d
            )
            totalDist += d[0]
        }
        return path.distinct() to totalDist
    }

    /**
     * Obtiene únicamente los paraderos que pasan o están sobre la ruta del bus escogido.
     * Si la ruta de Firebase no tiene paraderos registrados cerca en la colección general,
     * genera los paraderos oficiales directamente sobre el trazado de esa ruta para que
     * nunca muestre paraderos lejanos de otras rutas.
     */
    fun resolveStopsForRoute(
        route: BusRoute?,
        allStops: List<BusStop>,
        explicitPoints: List<GeoPoint> = emptyList()
    ): List<BusStop> {
        if (route == null) return emptyList()
        val pts = explicitPoints.ifEmpty {
            route.subidaPoints.ifEmpty { route.points }.ifEmpty { route.bajadaPoints }
        }
        if (pts.isEmpty()) return emptyList()

        val byId = if (route.stopIds.isNotEmpty()) {
            allStops.filter { it.id in route.stopIds }
        } else emptyList()

        // Filtrar paraderos que realmente pasen por el trazado de esta ruta (<= 300 m)
        val maxDegSq = 0.0027 * 0.0027
        val sampled = if (pts.size > 45) {
            pts.filterIndexed { idx, _ -> idx % 3 == 0 }
        } else {
            pts
        }
        val byProximity = allStops.filter { stop ->
            sampled.any { pt ->
                val dLat = stop.latitude - pt.latitude
                val dLng = stop.longitude - pt.longitude
                (dLat * dLat + dLng * dLng) <= maxDegSq
            }
        }

        val matched = (byId + byProximity).distinctBy { it.id }

        // Generar y conectar paraderos directamente sobre las coordenadas reales del recorrido de la ruta
        val generated = mutableListOf<BusStop>()
        val count = 8.coerceAtMost(pts.size)
        val routeLabel = route.name.ifBlank { route.code }
        for (i in 0 until count) {
            val ptIndex = if (count <= 1) 0 else ((pts.size - 1) * i / (count - 1)).coerceIn(0, pts.size - 1)
            val pt = pts[ptIndex]

            // Si ya hay un paradero conocido muy cerca de este punto (< 220m), usamos ese nombre real pero anclado a la ruta
            val nearKnown = allStops.firstOrNull { s ->
                val dLat = s.latitude - pt.latitude
                val dLng = s.longitude - pt.longitude
                (dLat * dLat + dLng * dLng) <= (0.0020 * 0.0020)
            }

            val sectorName = when {
                nearKnown != null -> nearKnown.name
                i == 0 && route.origin.isNotBlank() -> "Paradero ${route.origin}"
                i == count - 1 && route.destination.isNotBlank() -> "Paradero ${route.destination}"
                else -> "Paradero ${i + 1} · $routeLabel"
            }

            generated.add(
                BusStop(
                    id = kotlin.math.abs((route.id * 31) + i + 1000),
                    name = sectorName,
                    latitude = pt.latitude,
                    longitude = pt.longitude,
                    radiusMeters = 55f,
                    isActive = true
                )
            )
        }
        return (matched + generated).distinctBy {
            // Evitar duplicar banderas demasiado pegadas
            "${(it.latitude * 500).toInt()}_${(it.longitude * 500).toInt()}"
        }
    }

    fun buildWalkingPath(from: LatLng, to: LatLng): List<LatLng> {
        val midLat = (from.latitude + to.latitude) / 2.0
        val midLng = (from.longitude + to.longitude) / 2.0
        val dLat = to.latitude - from.latitude
        val dLng = to.longitude - from.longitude
        val corner1 = LatLng(from.latitude + dLat * 0.35, from.longitude + dLng * 0.65)
        val corner2 = LatLng(midLat + dLat * 0.18, midLng + dLng * 0.18)
        return listOf(from, corner1, corner2, to)
    }

    /**
     * Marcador personalizado EN FORMA DE CARRITO para la ubicación del conductor/bus.
     */
    fun createDriverCarMarker(): BitmapDescriptor {
        val width = 148
        val height = 104
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Sombra debajo del carrito
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(75, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawOval(RectF(16f, height - 22f, width - 16f, height - 6f), shadowPaint)

        // Silueta exterior blanca (borde del carrito para que resalte en el mapa)
        val carPath = Path().apply {
            moveTo(14f, 64f)
            lineTo(14f, 44f)
            lineTo(34f, 40f)
            lineTo(50f, 18f)
            lineTo(104f, 18f)
            lineTo(122f, 40f)
            lineTo(136f, 46f)
            lineTo(136f, 64f)
            close()
        }

        val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(carPath, whiteBorderPaint)

        // Pintura principal de la carrocería del carrito (Amarillo/Ámbar taxi/bus con chasis azul oscuro)
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#F59E0B")
            style = Paint.Style.FILL
        }
        canvas.drawPath(carPath, bodyPaint)

        // Franja inferior del chasis
        val bumperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#1E3A8A")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(12f, 54f, 138f, 68f), 8f, 8f, bumperPaint)

        // Ventanas del carrito (delantera y trasera)
        val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#E0F2FE")
            style = Paint.Style.FILL
        }
        val frontWindow = Path().apply {
            moveTo(80f, 24f)
            lineTo(100f, 24f)
            lineTo(114f, 40f)
            lineTo(80f, 40f)
            close()
        }
        val backWindow = Path().apply {
            moveTo(54f, 24f)
            lineTo(74f, 24f)
            lineTo(74f, 40f)
            lineTo(40f, 40f)
            close()
        }
        canvas.drawPath(frontWindow, windowPaint)
        canvas.drawPath(backWindow, windowPaint)

        // Faro delantero y luz trasera
        val headlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#FEF08A")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(126f, 45f, 137f, 53f), 4f, 4f, headlightPaint)

        val taillightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#EF4444")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(13f, 45f, 22f, 53f), 4f, 4f, taillightPaint)

        // Llantas del carrito con aros
        val tireBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        val tirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#0F172A")
            style = Paint.Style.FILL
        }
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#CBD5E1")
            style = Paint.Style.FILL
        }

        // Rueda trasera
        canvas.drawCircle(42f, 68f, 18f, tireBorderPaint)
        canvas.drawCircle(42f, 68f, 15f, tirePaint)
        canvas.drawCircle(42f, 68f, 7f, rimPaint)

        // Rueda delantera
        canvas.drawCircle(108f, 68f, 18f, tireBorderPaint)
        canvas.drawCircle(108f, 68f, 15f, tirePaint)
        canvas.drawCircle(108f, 68f, 7f, rimPaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    /**
     * Marcador personalizado con icono de BANDERA para todos los paraderos de la ruta.
     * Al presionarlo se resalta en verde esmeralda y marca cómo llegar hasta él.
     */
    fun createStopFlagMarker(isSelected: Boolean): BitmapDescriptor {
        val width = if (isSelected) 116 else 98
        val height = if (isSelected) 132 else 112
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = width / 2f
        val cy = if (isSelected) 52f else 44f
        val radius = if (isSelected) 42f else 35f

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(60, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy + 4f, radius, shadowPaint)

        val bgColor = if (isSelected) {
            android.graphics.Color.parseColor("#059669") // Verde esmeralda para paradero seleccionado
        } else {
            android.graphics.Color.parseColor("#1D4ED8") // Azul para los paraderos de la ruta
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        val pointerPath = Path().apply {
            moveTo(cx - 15f, cy + radius * 0.68f)
            lineTo(cx, height - 6f)
            lineTo(cx + 15f, cy + radius * 0.68f)
            close()
        }
        canvas.drawPath(pointerPath, bgPaint)
        canvas.drawCircle(cx, cy, radius, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 5.5f
        }
        canvas.drawCircle(cx, cy, radius - 2.5f, borderPaint)

        // Mástil de la bandera
        val polePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
            strokeCap = Paint.Cap.ROUND
        }
        val poleX = cx - 11f
        canvas.drawLine(poleX, cy - 18f, poleX, cy + 18f, polePaint)

        // Paño ondeante de la bandera
        val flagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) android.graphics.Color.parseColor("#FEF08A") else android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        val flagPath = Path().apply {
            moveTo(poleX, cy - 18f)
            lineTo(cx + 17f, cy - 11f)
            lineTo(cx + 11f, cy - 3f)
            lineTo(cx + 17f, cy + 4f)
            lineTo(poleX, cy + 4f)
            close()
        }
        canvas.drawPath(flagPath, flagPaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    fun createEndpointMarker(isStart: Boolean): BitmapDescriptor {
        val size = 76
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isStart) android.graphics.Color.parseColor("#2563EB") else android.graphics.Color.parseColor("#0F172A")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawCircle(cx, cy, 26f, fillPaint)
        canvas.drawCircle(cx, cy, 26f, borderPaint)

        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, 9f, innerPaint)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    fun createUserMarker(): BitmapDescriptor {
        val size = 90
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(70, 37, 99, 235)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, 40f, haloPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, 22f, borderPaint)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#2563EB")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, 15f, dotPaint)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
