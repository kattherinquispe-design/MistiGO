package com.ejemplo.smartbus.data

import android.location.Location
import android.util.Log
import com.ejemplo.smartbus.data.model.BusRoute
import com.ejemplo.smartbus.data.model.GeoPoint
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * Repositorio de rutas conectadas en tiempo real a Firebase Firestore.
 * Lee la colección "rutas" donde se encuentran los recorridos reales de MistiGo,
 * organizando cada ruta con su trazado de SUBIDA (Ida) y BAJADA (Vuelta / Retorno).
 */
object FirebaseRouteRepository {

    private const val TAG = "MistiGoFirebase"
    private const val COLLECTION_RUTAS = "rutas"

    private val _routes = MutableStateFlow<List<BusRoute>>(emptyList())
    val routes: StateFlow<List<BusRoute>> = _routes.asStateFlow()

    private var hasLoaded = false

    /**
     * Espera asíncrona segura para tareas de Google Play Services / Firebase.
     */
    private suspend fun <T> Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            addOnFailureListener { exception ->
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }
        }

    /**
     * Carga todas las rutas desde Firebase Firestore de la colección "rutas".
     */
    suspend fun loadRoutes(forceRefresh: Boolean = false): List<BusRoute> =
        withContext(Dispatchers.IO) {
            if (hasLoaded && !forceRefresh && _routes.value.isNotEmpty()) {
                return@withContext _routes.value
            }

            try {
                val firestore = FirebaseFirestore.getInstance()
                Log.d(TAG, "Conectando a Firebase Firestore coleccion '$COLLECTION_RUTAS'...")

                val snapshot: QuerySnapshot = firestore
                    .collection(COLLECTION_RUTAS)
                    .get()
                    .awaitTask()

                Log.d(TAG, "Firebase respondió con ${snapshot.documents.size} documentos de rutas.")

                val consolidated = parseAndConsolidateRoutes(snapshot)
                if (consolidated.isNotEmpty()) {
                    _routes.value = consolidated
                    hasLoaded = true
                    Log.i(TAG, "Rutas consolidadas con éxito: ${consolidated.size} rutas reales cargadas.")
                    return@withContext consolidated
                } else {
                    Log.w(TAG, "No se encontraron rutas validas en Firestore, usando fallback local.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al conectar con Firebase Firestore: ${e.message}", e)
            }

            // Fallback a las rutas de muestra si no hay conexion
            if (_routes.value.isEmpty()) {
                val fallback = RouteRepository.routes.map { r ->
                    r.copy(
                        subidaPoints = r.points,
                        bajadaPoints = r.points.reversed()
                    )
                }
                _routes.value = fallback
            }

            return@withContext _routes.value
        }

    /**
     * Busca rutas por texto (código de ruta, nombre, origen o destino).
     */
    suspend fun searchRoutes(query: String): List<BusRoute> {
        val currentRoutes = if (_routes.value.isEmpty()) loadRoutes() else _routes.value
        val cleanQuery = query.trim().lowercase()

        if (cleanQuery.isBlank()) {
            return currentRoutes
        }

        return currentRoutes.filter { route ->
            route.code.lowercase().contains(cleanQuery) ||
                route.name.lowercase().contains(cleanQuery) ||
                route.origin.lowercase().contains(cleanQuery) ||
                route.destination.lowercase().contains(cleanQuery)
        }
    }

    /**
     * Encuentra rutas cuyo recorrido pase cerca de unas coordenadas dadas.
     */
    fun findRoutesNear(
        lat: Double,
        lng: Double,
        radiusMeters: Float = 1500f
    ): List<BusRoute> {
        val current = _routes.value
        if (current.isEmpty()) return emptyList()

        val results = FloatArray(1)
        return current.filter { route ->
            val allPoints = (route.subidaPoints + route.bajadaPoints).ifEmpty { route.points }
            allPoints.any { pt ->
                Location.distanceBetween(lat, lng, pt.latitude, pt.longitude, results)
                results[0] <= radiusMeters
            }
        }
    }

    // Límites geográficos estrictos de Arequipa Metropolitana (evita rutas de otros países o ciudades)
    private const val AREQUIPA_MIN_LAT = -16.65
    private const val AREQUIPA_MAX_LAT = -16.15
    private const val AREQUIPA_MIN_LNG = -71.85
    private const val AREQUIPA_MAX_LNG = -71.30

    fun isPointInArequipa(lat: Double, lng: Double): Boolean {
        return !lat.isNaN() && !lng.isNaN() &&
            lat in AREQUIPA_MIN_LAT..AREQUIPA_MAX_LAT &&
            lng in AREQUIPA_MIN_LNG..AREQUIPA_MAX_LNG
    }

    private data class ArequipaSector(
        val name: String,
        val lat: Double,
        val lng: Double
    )

    private val arequipaSectors2026 = listOf(
        ArequipaSector("Centro Histórico (Cercado)", -16.3989, -71.5369),
        ArequipaSector("Av. Goyeneche / UNSA", -16.4025, -71.5265),
        ArequipaSector("Terminal / Av. Avelino Cáceres", -16.4255, -71.5375),
        ArequipaSector("Mall Porongoche / Lambramani", -16.4165, -71.5190),
        ArequipaSector("Paucarpata (Plaza / Ciudad Blanca)", -16.4210, -71.5030),
        ArequipaSector("Miguel Grau (Paucarpata)", -16.4045, -71.4780),
        ArequipaSector("Mariano Melgar (Av. Lima)", -16.4020, -71.5035),
        ArequipaSector("Miraflores (Plaza Mayta Cápac)", -16.3910, -71.5180),
        ArequipaSector("Alto Selva Alegre", -16.3775, -71.5250),
        ArequipaSector("Yanahuara / Av. Ejército", -16.3920, -71.5490),
        ArequipaSector("Cayma (Plaza / Bolognesi)", -16.3650, -71.5470),
        ArequipaSector("Cerro Colorado (Las Américas)", -16.3760, -71.5610),
        ArequipaSector("Zamácola / Av. Aviación", -16.3520, -71.5680),
        ArequipaSector("Cono Norte / Yura (Cerro Colorado)", -16.3150, -71.5850),
        ArequipaSector("Sachaca (Av. Fernandini)", -16.4180, -71.5640),
        ArequipaSector("Tiabaya (Pampas Nuevas)", -16.4495, -71.5950),
        ArequipaSector("Hunter (Av. Viña del Mar)", -16.4450, -71.5580),
        ArequipaSector("Socabaya (Av. Salaverry / Lara)", -16.4520, -71.5270),
        ArequipaSector("Characato / Sabandía", -16.4580, -71.4880),
        ArequipaSector("Uchumayo / Congata", -16.4380, -71.6250)
    )

    private fun resolveArequipaSectorName(point: GeoPoint): String {
        val dist = FloatArray(1)
        var bestName = "Arequipa Cercado"
        var bestDist = Float.MAX_VALUE
        for (sector in arequipaSectors2026) {
            Location.distanceBetween(point.latitude, point.longitude, sector.lat, sector.lng, dist)
            if (dist[0] < bestDist) {
                bestDist = dist[0]
                bestName = sector.name
            }
        }
        return bestName
    }

    /**
     * Parsea la cadena JSON de coordenadas en formato [[lng, lat, alt], ...]
     * filtrando estrictamente que todos los puntos pertenezcan a Arequipa.
     */
    fun parseCoordinates(rawJson: String?): List<GeoPoint> {
        if (rawJson.isNullOrBlank()) return emptyList()
        val points = ArrayList<GeoPoint>()
        try {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                val pointArray = jsonArray.optJSONArray(i) ?: continue
                val lng = pointArray.optDouble(0)
                val lat = pointArray.optDouble(1)
                if (isPointInArequipa(lat, lng)) {
                    points.add(GeoPoint(latitude = lat, longitude = lng))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando coordenadas: ${e.message}")
        }
        return points
    }

    private class RawRouteDoc(
        val docId: String,
        val rawName: String,
        val description: String,
        val points: List<GeoPoint>,
        val isBajada: Boolean
    )

    private fun parseAndConsolidateRoutes(snapshot: QuerySnapshot): List<BusRoute> {
        val rawDocs = mutableListOf<RawRouteDoc>()

        for (doc in snapshot.documents) {
            val rawName = doc.getString("nombreRuta") ?: ""
            val props = doc.get("propiedades") as? Map<*, *>
            val description = (props?.get("description") as? String) ?: ""
            val coordStr = doc.getString("coordenadas")
            val points = parseCoordinates(coordStr)

            // Solo aceptar rutas con coordenadas válidas dentro de Arequipa
            if (points.size < 2) continue

            val isBajada = rawName.contains("vuelta", ignoreCase = true) ||
                rawName.contains("retorno", ignoreCase = true) ||
                rawName.contains("bajada", ignoreCase = true) ||
                description.contains("vuelta", ignoreCase = true) ||
                description.contains("retorno", ignoreCase = true) ||
                description.contains("bajada", ignoreCase = true)

            rawDocs.add(
                RawRouteDoc(
                    docId = doc.id,
                    rawName = rawName,
                    description = description,
                    points = points,
                    isBajada = isBajada
                )
            )
        }

        // Agrupamos por nombre base limpio o por sector real de Arequipa si el documento solo decía "IDA"/"VUELTA"
        val groups = rawDocs.groupBy { doc ->
            val base = cleanRouteBase(doc.rawName, doc.description)
            if (base.isBlank() || base.equals("Ruta Urbana", ignoreCase = true)) {
                val firstPt = if (!doc.isBajada) doc.points.first() else doc.points.last()
                val lastPt = if (!doc.isBajada) doc.points.last() else doc.points.first()
                val origSector = resolveArequipaSectorName(firstPt)
                val destSector = resolveArequipaSectorName(lastPt)
                if (origSector == destSector) {
                    val midSector = resolveArequipaSectorName(doc.points[doc.points.size / 2])
                    "SIT $origSector - $midSector"
                } else {
                    "SIT $origSector - $destSector"
                }
            } else {
                base
            }
        }
        val consolidated = mutableListOf<BusRoute>()

        // 1. Incluir todas las rutas oficiales actualizadas SIT Arequipa 2026
        RouteRepository.routes.forEach { sitRoute ->
            val validPoints = sitRoute.points.filter { isPointInArequipa(it.latitude, it.longitude) }
            if (validPoints.size >= 2) {
                consolidated.add(
                    sitRoute.copy(
                        points = validPoints,
                        subidaPoints = sitRoute.subidaPoints.ifEmpty { validPoints },
                        bajadaPoints = sitRoute.bajadaPoints.ifEmpty { validPoints.reversed() }
                    )
                )
            }
        }

        // 2. Procesar y agregar las rutas reales de Arequipa
        var index = 1
        for ((baseName, docs) in groups) {
            // Seleccionar los mejores trazados para Subida y Bajada
            val subidaDocs = docs.filter { !it.isBajada }.sortedByDescending { it.points.size }
            val bajadaDocs = docs.filter { it.isBajada }.sortedByDescending { it.points.size }

            val bestSubidaPoints = subidaDocs.firstOrNull()?.points ?: emptyList()
            val bestBajadaPoints = bajadaDocs.firstOrNull()?.points ?: emptyList()

            val finalSubida = bestSubidaPoints.ifEmpty { bestBajadaPoints.reversed() }
            val finalBajada = bestBajadaPoints.ifEmpty { bestSubidaPoints.reversed() }

            if (finalSubida.size < 2) continue

            val representativeDoc = docs.first()
            val (rawOrigin, rawDestination) = extractOriginDestination(representativeDoc.rawName, representativeDoc.description)
            val origin = if (isValidSector(rawOrigin)) rawOrigin else resolveArequipaSectorName(finalSubida.first())
            var destination = if (isValidSector(rawDestination)) rawDestination else resolveArequipaSectorName(finalSubida.last())

            if (origin == destination && finalSubida.size >= 3) {
                val start = finalSubida.first()
                val dist = FloatArray(1)
                val farthest = finalSubida.maxByOrNull { pt ->
                    Location.distanceBetween(start.latitude, start.longitude, pt.latitude, pt.longitude, dist)
                    dist[0]
                }
                if (farthest != null) {
                    val resolvedFar = resolveArequipaSectorName(farthest)
                    if (resolvedFar != origin) {
                        destination = resolvedFar
                    }
                }
            }
            val code = extractRouteCode(baseName, index)

            val cleanBase = baseName.replace("(?i)\\s*\\(?\\s*(ida|vuelta|retorno|subida|bajada)\\s*\\)?\\s*".toRegex(), "").trim()

            // Asignar nombres claros y descriptivos con lugares importantes para que el pasajero identifique a dónde va
            val descriptiveName = when {
                cleanBase.contains(" - ") || cleanBase.contains(" → ") || cleanBase.contains(" a ") -> cleanBase
                origin.isNotBlank() && destination.isNotBlank() && origin != destination ->
                    "$cleanBase: $origin → $destination"
                origin.isNotBlank() ->
                    "$cleanBase: $origin"
                else -> cleanBase
            }

            val routeId = Math.abs(baseName.hashCode())
            consolidated.add(
                BusRoute(
                    id = if (routeId != 0) routeId else (1000 + index),
                    code = code,
                    name = descriptiveName,
                    origin = origin,
                    destination = destination,
                    points = finalSubida,
                    subidaPoints = finalSubida,
                    bajadaPoints = finalBajada,
                    frequencyMinutes = 10,
                    isActive = true,
                    tripType = "SUBIDA"
                )
            )
            index++
        }

        return consolidated.distinctBy { it.id }.sortedBy { it.code }
    }

    private fun cleanRouteBase(rawName: String, desc: String): String {
        val trimmed = rawName.trim()
        if (trimmed.equals("IDA", ignoreCase = true) ||
            trimmed.equals("VUELTA", ignoreCase = true) ||
            trimmed.equals("RETORNO", ignoreCase = true) ||
            trimmed.isBlank()
        ) {
            if (desc.isNotBlank()) {
                val desdeHasta = desc.replace("(?i)desde:".toRegex(), "").split("(?i)hasta:".toRegex())
                if (desdeHasta.size == 2) {
                    val o = desdeHasta[0].trim().replace("\n", " ").replace("\\s+".toRegex(), " ")
                    val d = desdeHasta[1].trim().replace("\n", " ").replace("\\s+".toRegex(), " ")
                    return "$o - $d"
                }
                return desc.lines().firstOrNull()?.trim() ?: "Ruta Urbana"
            }
            return "Ruta Urbana"
        }

        return trimmed
            .replace("(?i)\\s*\\(?\\s*(ida|vuelta|retorno|subida|bajada)\\s*\\)?\\s*".toRegex(), " ")
            .replace("[-\\s]+".toRegex(), " ")
            .trim()
    }

    private fun extractRouteCode(baseName: String, fallbackIndex: Int): String {
        val match = "(?i)ruta\\s+([a-z0-9]+)".toRegex().find(baseName)
        if (match != null) {
            return match.groupValues[1].uppercase()
        }
        val firstWord = baseName.split(" ").firstOrNull()
        if (!firstWord.isNullOrBlank() && firstWord.length <= 6) {
            return firstWord.uppercase()
        }
        return "R$fallbackIndex"
    }

    private fun isValidSector(name: String): Boolean {
        val clean = name.trim().lowercase()
        if (clean.isBlank() || clean.length < 3) return false
        if (clean == "ida" || clean == "vuelta" || clean == "retorno" || clean == "subida" || clean == "bajada") return false
        if (clean == "terminal origen" || clean == "terminal destino") return false
        if (clean.startsWith("ruta ") || clean.startsWith("ruta-") || clean.matches("^r\\d+.*".toRegex())) return false
        return true
    }

    private fun extractOriginDestination(rawName: String, desc: String): Pair<String, String> {
        val cleanDesc = desc
            .replace("(?i)empresa\\s+.*".toRegex(), "")
            .replace("(?i)corporacion\\s+.*".toRegex(), "")
            .trim()

        if (cleanDesc.isNotBlank()) {
            val desdeHasta = cleanDesc.replace("(?i)desde:".toRegex(), "").split("(?i)hasta:".toRegex())
            if (desdeHasta.size >= 2) {
                val orig = desdeHasta[0].trim().replace("\n", " ").take(30)
                val dest = desdeHasta[1].trim().replace("\n", " ").take(30)
                if (isValidSector(orig) && isValidSector(dest)) {
                    return Pair(orig, dest)
                }
            }
            val dashSplit = cleanDesc.split("-")
            if (dashSplit.size >= 2) {
                val orig = dashSplit[0].trim().replace("\n", " ").take(30)
                val dest = dashSplit[1].trim().replace("\n", " ").take(30)
                if (isValidSector(orig) && isValidSector(dest)) {
                    return Pair(orig, dest)
                }
            }
        }
        val cleanRaw = rawName
            .replace("(?i)\\s*\\(?\\s*(ida|vuelta|retorno|subida|bajada)\\s*\\)?\\s*".toRegex(), " ")
            .replace("(?i)empresa\\s+.*".toRegex(), "")
            .replace("(?i)translampa.*".toRegex(), "")
            .trim()
        val nameSplit = cleanRaw.split("-")
        if (nameSplit.size >= 2) {
            val orig = nameSplit[0].trim().take(30)
            val dest = nameSplit[1].trim().take(30)
            if (isValidSector(orig) && isValidSector(dest)) {
                return Pair(orig, dest)
            }
        }
        return Pair("Terminal Origen", "Terminal Destino")
    }
}
