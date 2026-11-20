package com.ejemplo.smartbus.data

data class SmartBusStatistics(
    val boardingCount: Int,
    val rewardsRedeemed: Int,
    val stopsUsed: Int
)

class StatisticsRepository(
    private val pointsRepository: PointsRepository
) {

    fun getStatistics(): SmartBusStatistics {

        val history =
            pointsRepository.getHistory()

        val historyBoardings =
            history.count {
                it.points > 0 || it.reason.contains("Abordaje", ignoreCase = true)
            }

        val recordedTrips =
            pointsRepository.getRecordedRouteTripsCount()

        val boardingCount =
            maxOf(historyBoardings, recordedTrips)

        val historyRedeems =
            history.count {
                it.points < 0 || it.reason.contains("Canje", ignoreCase = true)
            }

        val savedRedeemedCount =
            pointsRepository.getRedeemedRewards().size

        val rewardsRedeemed =
            maxOf(historyRedeems, savedRedeemedCount)

        val historyStopKeys =
            history
                .filter {
                    it.points > 0 || it.reason.contains("Abordaje", ignoreCase = true)
                }
                .mapNotNull { tx ->
                    when {
                        tx.stopId != 0 -> "id_${tx.stopId}"
                        tx.stopName.isNotBlank() -> "name_${tx.stopName.trim().lowercase()}"
                        else -> null
                    }
                }
                .toSet()

        val visitedStopKeys =
            pointsRepository.getVisitedStopKeys()

        val combinedStopsCount =
            (historyStopKeys + visitedStopKeys).size

        val distinctRoutesUsed =
            pointsRepository.getRecordedDistinctRoutesCount()

        val stopsUsed =
            maxOf(
                combinedStopsCount,
                distinctRoutesUsed,
                if (boardingCount > 0) 1 else 0
            )

        return SmartBusStatistics(
            boardingCount = boardingCount,
            rewardsRedeemed = rewardsRedeemed,
            stopsUsed = stopsUsed
        )
    }
}