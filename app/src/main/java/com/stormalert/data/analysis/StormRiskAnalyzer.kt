package com.stormalert.data.analysis

import com.stormalert.data.analysis.StormMovementAnalyzer.ForecastData
import kotlin.math.*

/**
 * Analyzes storm risk at multiple radii from user location
 * Ported from Python script with approach detection
 */
class StormRiskAnalyzer {
    
    data class RiskAnalysis(
        val distance: Double, // km
        val stormSpeed: Double, // km/min
        val movementAlignment: Double, // -1=away, 1=toward
        val timeToImpact: Double, // minutes
        val riskLevel: RiskLevel,
        val isApproaching: Boolean
    )
    
    data class MultiRadiusAnalysis(
        val current: RiskAnalysis,
        val radius20km: RiskAnalysis,
        val radius100km: RiskAnalysis
    )
    
    enum class RiskLevel {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }
    
    companion object {
        private const val PIXELS_TO_KM = 2.0 // Approximate conversion at zoom 7
        private const val APPROACH_ALIGNMENT_THRESHOLD = 0.3
    }
    
    /**
     * Analyze storm risk at multiple radii from user location
     */
    fun analyzeStormAtRadii(
        userLat: Double,
        userLon: Double,
        forecastData: ForecastData
    ): MultiRadiusAnalysis? {
        val centroids = forecastData.stormCentroids
        if (centroids.isEmpty()) return null
        
        val latestCentroid = centroids.last()
        val stormX = latestCentroid.x
        val stormY = latestCentroid.y
        
        // User position (center of 512x512 image)
        val userX = 256.0
        val userY = 256.0
        
        // Current location analysis
        val currentAnalysis = analyzeRiskAtRadius(
            stormX, stormY, userX, userY, forecastData, 0.0
        )
        
        // 20km radius analysis
        val radius20kmAnalysis = analyzeRiskAtRadius(
            stormX, stormY, userX, userY, forecastData, 20.0
        )
        
        // 100km radius analysis
        val radius100kmAnalysis = analyzeRiskAtRadius(
            stormX, stormY, userX, userY, forecastData, 100.0
        )
        
        return MultiRadiusAnalysis(
            current = currentAnalysis,
            radius20km = radius20kmAnalysis,
            radius100km = radius100kmAnalysis
        )
    }
    
    /**
     * Analyze risk at a specific radius
     */
    private fun analyzeRiskAtRadius(
        stormX: Double,
        stormY: Double,
        userX: Double,
        userY: Double,
        forecastData: ForecastData,
        radiusKm: Double
    ): RiskAnalysis {
        // Calculate distance from storm to user
        val distancePixels = sqrt((stormX - userX).pow(2) + (stormY - userY).pow(2))
        val distanceKm = distancePixels * PIXELS_TO_KM
        
        // Calculate storm speed
        val speedPixelsPerMin = sqrt(
            forecastData.avgSpeedX.pow(2) + forecastData.avgSpeedY.pow(2)
        )
        val stormSpeedKmPerMin = speedPixelsPerMin * PIXELS_TO_KM
        
        // Calculate movement alignment (dot product)
        val stormToUserX = userX - stormX
        val stormToUserY = userY - stormY
        val movementX = forecastData.avgSpeedX
        val movementY = forecastData.avgSpeedY
        
        val dotProduct = stormToUserX * movementX + stormToUserY * movementY
        val magnitudeStormToUser = sqrt(stormToUserX.pow(2) + stormToUserY.pow(2))
        val magnitudeMovement = sqrt(movementX.pow(2) + movementY.pow(2))
        
        val alignment = if (magnitudeStormToUser > 0 && magnitudeMovement > 0) {
            dotProduct / (magnitudeStormToUser * magnitudeMovement)
        } else {
            0.0
        }
        
        // Calculate time to impact
        val timeToImpact = if (alignment > APPROACH_ALIGNMENT_THRESHOLD && stormSpeedKmPerMin > 0) {
            distanceKm / stormSpeedKmPerMin
        } else {
            Double.POSITIVE_INFINITY
        }
        
        // Determine risk level
        val riskLevel = determineRiskLevel(alignment, timeToImpact, distanceKm)
        
        // Determine if approaching
        val isApproaching = alignment > APPROACH_ALIGNMENT_THRESHOLD
        
        return RiskAnalysis(
            distance = distanceKm,
            stormSpeed = stormSpeedKmPerMin,
            movementAlignment = alignment,
            timeToImpact = timeToImpact,
            riskLevel = riskLevel,
            isApproaching = isApproaching
        )
    }
    
    /**
     * Determine risk level based on alignment, time to impact, and distance
     */
    private fun determineRiskLevel(
        alignment: Double,
        timeToImpact: Double,
        distanceKm: Double
    ): RiskLevel {
        if (alignment > APPROACH_ALIGNMENT_THRESHOLD) {
            return when {
                timeToImpact < 60 -> RiskLevel.CRITICAL
                timeToImpact < 120 -> RiskLevel.HIGH
                timeToImpact < 300 -> RiskLevel.MEDIUM
                else -> RiskLevel.LOW
            }
        } else {
            return when {
                distanceKm < 20 -> RiskLevel.MEDIUM
                distanceKm < 100 -> RiskLevel.LOW
                else -> RiskLevel.LOW
            }
        }
    }
    
    /**
     * Get overall risk assessment from multi-radius analysis
     */
    fun getOverallRisk(analysis: MultiRadiusAnalysis): RiskLevel {
        return when {
            analysis.current.riskLevel == RiskLevel.CRITICAL -> RiskLevel.CRITICAL
            analysis.current.riskLevel == RiskLevel.HIGH -> RiskLevel.HIGH
            analysis.radius20km.riskLevel == RiskLevel.HIGH -> RiskLevel.HIGH
            analysis.radius20km.riskLevel == RiskLevel.MEDIUM -> RiskLevel.MEDIUM
            analysis.radius100km.riskLevel == RiskLevel.MEDIUM -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }
}
