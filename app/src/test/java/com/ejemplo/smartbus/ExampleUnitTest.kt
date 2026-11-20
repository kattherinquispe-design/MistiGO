package com.ejemplo.smartbus

import com.ejemplo.smartbus.data.RewardsCatalog
import com.ejemplo.smartbus.location.TripCoPresenceDetector
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun pointsDetectorRules_verify7mRadiusAnd5MinAndStopBoarding() {
        assertEquals(7f, TripCoPresenceDetector.MAX_TOGETHER_DISTANCE_M, 0.01f)
        assertEquals(5 * 60 * 1000L, TripCoPresenceDetector.MIN_TOGETHER_TIME_MS)
        assertEquals(8f, TripCoPresenceDetector.MIN_MOVING_SPEED_KMH, 0.01f)

        val detector = TripCoPresenceDetector()
        // Si no abordó en un paradero, no es válido para puntos
        assertFalse(detector.hasValidStopBoarding())

        // Al entrar al radio de un paradero oficial, queda habilitado el abordaje
        detector.recordStopVisit(stopId = 1)
        assertTrue(detector.hasValidStopBoarding())
    }

    @Test
    fun rewardsCatalog_matchesRequiredRewardsAndPoints() {
        val rewards = RewardsCatalog.rewards
        assertEquals(6, rewards.size)
        assertEquals("Café Latte" to 40, rewards[0].name to rewards[0].points)
        assertEquals("Empanada" to 60, rewards[1].name to rewards[1].points)
        assertEquals("Pasaje Gratis" to 20, rewards[2].name to rewards[2].points)
        assertEquals("Descuento 30%" to 30, rewards[3].name to rewards[3].points)
        assertEquals("Jugo Natural" to 50, rewards[4].name to rewards[4].points)
        assertEquals("Café Americano" to 40, rewards[5].name to rewards[5].points)
    }
}