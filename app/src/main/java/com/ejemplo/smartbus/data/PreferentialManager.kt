package com.ejemplo.smartbus.data

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class PreferentialRequest(
    val id: String = "",
    val busId: String = "",
    val passengerName: String = "Viajero",
    val categoryLabel: String = "Atención preferencial",
    val note: String = "",
    val status: String = "PENDING",
    val timestamp: Long = System.currentTimeMillis()
)

object PreferentialManager {

    private const val TAG = "SmartBusPreferential"
    private const val COLLECTION_REQUESTS = "preferential_requests"

    private val _pendingDeepLinkBusId = MutableStateFlow<String?>(null)
    val pendingDeepLinkBusId: StateFlow<String?> = _pendingDeepLinkBusId.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<PreferentialRequest>>(emptyList())
    val pendingRequests: StateFlow<List<PreferentialRequest>> = _pendingRequests.asStateFlow()

    private val _allRequestsForBus = MutableStateFlow<List<PreferentialRequest>>(emptyList())
    val allRequestsForBus: StateFlow<List<PreferentialRequest>> = _allRequestsForBus.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null
    private var listeningBusId: String? = null

    fun buildQrDeepLink(busId: String): String {
        val cleanId = busId.trim().uppercase().ifBlank { "BUS-01" }
        return "smartbus://preferential?bus=${Uri.encode(cleanId)}"
    }

    fun parseBusIdFromQr(rawValue: String): String? {
        val trimmed = rawValue.trim()
        if (trimmed.isBlank()) return null

        return try {
            if (trimmed.startsWith("smartbus://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("http://", ignoreCase = true)
            ) {
                val uri = Uri.parse(trimmed)
                val fromQuery = uri.getQueryParameter("bus")
                    ?: uri.getQueryParameter("busId")
                    ?: uri.getQueryParameter("placa")
                if (!fromQuery.isNullOrBlank()) {
                    return fromQuery.trim().uppercase()
                }
                val lastSegment = uri.lastPathSegment
                if (!lastSegment.isNullOrBlank() &&
                    !lastSegment.equals("preferential", ignoreCase = true)
                ) {
                    return lastSegment.trim().uppercase()
                }
                null
            } else {
                trimmed.uppercase().takeIf { it.length in 2..20 }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun requestOpenFromDeepLink(busId: String) {
        _pendingDeepLinkBusId.value = busId.trim().uppercase()
    }

    fun consumeDeepLink() {
        _pendingDeepLinkBusId.value = null
    }

    fun generateQrBitmap(content: String, sizePx: Int = 520): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bitMatrix = MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generando QR: ${e.message}", e)
            null
        }
    }

    fun sendPreferentialRequest(
        busId: String,
        passengerName: String,
        categoryLabel: String,
        note: String,
        onComplete: (Boolean) -> Unit
    ) {
        val cleanBusId = busId.trim().uppercase().ifBlank { "BUS-01" }
        val requestId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val data = hashMapOf(
            "id" to requestId,
            "busId" to cleanBusId,
            "passengerName" to passengerName.ifBlank { "Viajero" },
            "categoryLabel" to categoryLabel,
            "note" to note.trim(),
            "status" to "PENDING",
            "timestamp" to now
        )

        try {
            FirebaseFirestore.getInstance()
                .collection(COLLECTION_REQUESTS)
                .document(requestId)
                .set(data)
                .addOnSuccessListener {
                    onComplete(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error enviando solicitud preferencial: ${e.message}", e)
                    onComplete(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al enviar solicitud: ${e.message}", e)
            onComplete(false)
        }
    }

    fun startListeningForBus(busId: String) {
        val cleanBusId = busId.trim().uppercase().ifBlank { "BUS-01" }
        if (listeningBusId == cleanBusId && listenerRegistration != null) return

        listenerRegistration?.remove()
        listeningBusId = cleanBusId

        try {
            listenerRegistration = FirebaseFirestore.getInstance()
                .collection(COLLECTION_REQUESTS)
                .whereEqualTo("busId", cleanBusId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Error escuchando solicitudes: ${error.message}")
                        return@addSnapshotListener
                    }
                    val allList = snapshot?.documents?.mapNotNull { doc ->
                        PreferentialRequest(
                            id = doc.getString("id") ?: doc.id,
                            busId = doc.getString("busId") ?: cleanBusId,
                            passengerName = doc.getString("passengerName") ?: "Viajero",
                            categoryLabel = doc.getString("categoryLabel") ?: "Atención preferencial",
                            note = doc.getString("note") ?: "",
                            status = doc.getString("status") ?: "PENDING",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    }?.sortedByDescending { it.timestamp }.orEmpty()

                    _allRequestsForBus.value = allList
                    _pendingRequests.value = allList.filter { it.status == "PENDING" }
                }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar listener de solicitudes: ${e.message}", e)
        }
    }

    fun acknowledgeRequest(requestId: String) {
        if (requestId.isBlank()) return
        _pendingRequests.value = _pendingRequests.value.filterNot { it.id == requestId }
        _allRequestsForBus.value = _allRequestsForBus.value.map {
            if (it.id == requestId) it.copy(status = "ATTENDED") else it
        }

        try {
            FirebaseFirestore.getInstance()
                .collection(COLLECTION_REQUESTS)
                .document(requestId)
                .update("status", "ATTENDED")
        } catch (e: Exception) {
            Log.w(TAG, "Error confirmando atención: ${e.message}")
        }
    }
}