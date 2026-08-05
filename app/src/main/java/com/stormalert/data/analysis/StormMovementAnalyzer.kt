package com.stormalert.data.analysis

import com.stormalert.data.network.RadarFrame
import kotlin.math.*

/**
 * Analyzes storm movement from historical radar frames
 * Ported from Python script with advanced forecasting methods
 */
class StormMovementAnalyzer {
    
    data class StormCentroid(
        val timestamp: Long,
        val x: Double,
        val y: Double,
        val pixelCount: Int
    )
    
    data class MovementVector(
        val speedX: Double,
        val speedY: Double,
        val timeDiff: Double
    )
    
    data class ForecastData(
        val avgSpeedX: Double,
        val avgSpeedY: Double,
        val forecast1h: Pair<Double, Double>,
        val forecast5h: Pair<Double, Double>,
        val stormCentroids: List<StormCentroid>,
        val movements: List<MovementVector>,
        val acceleration: Pair<Double, Double>
    )
    
    /**
     * Calculate storm movement from historical radar frames
     */
    fun calculateStormMovement(historicalFrames: List<RadarFrame>): ForecastData? {
        if (historicalFrames.size < 2) {
            return null
        }
        
        // Note: In a real implementation, you would need to actually process the radar images
        // to extract storm centroids. For now, we'll create placeholder data
        // This would require image processing libraries or API calls to get pixel data
        
        val stormCentroids = generatePlaceholderCentroids(historicalFrames)
        
        if (stormCentroids.size < 2) {
            return null
        }
        
        // Calculate movement vectors between consecutive frames
        val movements = calculateMovements(stormCentroids)
        
        if (movements.isEmpty()) {
            return null
        }
        
        // Method 1: Weighted averaging (recent frames get higher weight)
        val weightedSpeed = calculateWeightedAverage(movements)
        
        // Method 2: Polynomial regression for acceleration detection
        val regressionResult = performPolynomialRegression(stormCentroids)
        
        // Method 3: Directional trend analysis
        val directionalCorrection = calculateDirectionalTrend(movements)
        
        // Select final forecast velocity
        val finalSpeed = selectForecastVelocity(weightedSpeed, regressionResult, directionalCorrection)
        
        // Calculate forecast positions
        val forecast1h = Pair(finalSpeed.first * 60.0, finalSpeed.second * 60.0) // 1 hour = 60 minutes
        val forecast5h = if (regressionResult != null) {
            calculateRegressionForecast(regressionResult, stormCentroids, 300.0) // 5 hours = 300 minutes
        } else {
            Pair(finalSpeed.first * 300.0, finalSpeed.second * 300.0)
        }
        
        return ForecastData(
            avgSpeedX = finalSpeed.first,
            avgSpeedY = finalSpeed.second,
            forecast1h = forecast1h,
            forecast5h = forecast5h,
            stormCentroids = stormCentroids,
            movements = movements,
            acceleration = regressionResult?.acceleration ?: Pair(0.0, 0.0)
        )
    }
    
    /**
     * Generate placeholder centroids (in real implementation, this would process actual radar images)
     */
    private fun generatePlaceholderCentroids(frames: List<RadarFrame>): List<StormCentroid> {
        // This is a placeholder - in production, you would:
        // 1. Download the radar images for each frame
        // 2. Process them to find high-intensity pixels
        // 3. Calculate centroids from those pixels
        
        return frames.mapIndexed { index, frame ->
            StormCentroid(
                timestamp = frame.time,
                x = 200.0 + index * 10.0, // Placeholder X position
                y = 100.0 + index * 2.0,  // Placeholder Y position
                pixelCount = 50 + index * 5 // Placeholder pixel count
            )
        }
    }
    
    /**
     * Calculate movement vectors between consecutive centroids
     */
    private fun calculateMovements(centroids: List<StormCentroid>): List<MovementVector> {
        val movements = mutableListOf<MovementVector>()
        
        for (i in 1 until centroids.size) {
            val prev = centroids[i - 1]
            val curr = centroids[i]
            
            val timeDiff = (curr.timestamp - prev.timestamp) / 60.0 // Convert seconds to minutes
            val dx = curr.x - prev.x
            val dy = curr.y - prev.y
            
            if (timeDiff > 0) {
                movements.add(MovementVector(
                    speedX = dx / timeDiff,
                    speedY = dy / timeDiff,
                    timeDiff = timeDiff
                ))
            }
        }
        
        return movements
    }
    
    /**
     * Calculate weighted average of movements (recent frames get higher weight)
     */
    private fun calculateWeightedAverage(movements: List<MovementVector>): Pair<Double, Double> {
        val weights = movements.indices.map { it + 1 } // Linear weighting
        val totalWeight = weights.sum()
        
        val weightedSpeedX = movements.mapIndexed { i, m -> m.speedX * weights[i] }.sum() / totalWeight
        val weightedSpeedY = movements.mapIndexed { i, m -> m.speedY * weights[i] }.sum() / totalWeight
        
        return Pair(weightedSpeedX, weightedSpeedY)
    }
    
    /**
     * Perform polynomial regression for acceleration detection
     */
    private fun performPolynomialRegression(centroids: List<StormCentroid>): RegressionResult? {
        if (centroids.size < 3) return null
        
        try {
            val times = centroids.map { (it.timestamp - centroids[0].timestamp) / 60.0 }
            val xPositions = centroids.map { it.x }
            val yPositions = centroids.map { it.y }
            
            val xCoeffs = quadraticFit(times, xPositions)
            val yCoeffs = quadraticFit(times, yPositions)
            
            if (xCoeffs == null || yCoeffs == null) return null
            
            // Calculate acceleration (2*a)
            val accelX = 2 * xCoeffs[0]
            val accelY = 2 * yCoeffs[0]
            
            // Calculate velocity at current time
            val currentTime = times.last()
            val velocityX = 2 * xCoeffs[0] * currentTime + xCoeffs[1]
            val velocityY = 2 * yCoeffs[0] * currentTime + yCoeffs[1]
            
            // Sanity checks
            val maxReasonableAccel = 10.0
            val maxReasonableVelocity = 50.0
            
            if (abs(accelX) > maxReasonableAccel || abs(accelY) > maxReasonableAccel) {
                return null // Acceleration too high
            }
            
            if (abs(velocityX) > maxReasonableVelocity || abs(velocityY) > maxReasonableVelocity) {
                return null // Velocity too high
            }
            
            return RegressionResult(
                xCoeffs = xCoeffs,
                yCoeffs = yCoeffs,
                acceleration = Pair(accelX, accelY),
                velocity = Pair(velocityX, velocityY),
                currentTime = currentTime
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Quadratic polynomial fit using least squares
     */
    private fun quadraticFit(times: List<Double>, positions: List<Double>): DoubleArray? {
        val n = times.size
        if (n < 3) return null
        
        // Calculate sums
        val sumT = times.sum()
        val sumT2 = times.map { it * it }.sum()
        val sumT3 = times.map { it * it * it }.sum()
        val sumT4 = times.map { it * it * it * it }.sum()
        val sumY = positions.sum()
        val sumTY = times.zip(positions).map { it.first * it.second }.sum()
        val sumT2Y = times.zip(positions).map { it.first * it.first * it.second }.sum()
        
        // Solve system of equations for coefficients a, b, c
        val det = n * (sumT2 * sumT4 - sumT3 * sumT3) - 
                  sumT * (sumT * sumT4 - sumT2 * sumT3) + 
                  sumT2 * (sumT * sumT3 - sumT2 * sumT2)
        
        if (abs(det) < 1e-10) return null
        
        val detA = sumY * (sumT2 * sumT4 - sumT3 * sumT3) - 
                   sumT * (sumTY * sumT4 - sumT2Y * sumT3) + 
                   sumT2 * (sumTY * sumT3 - sumT2Y * sumT2)
        
        val detB = n * (sumTY * sumT4 - sumT2Y * sumT3) - 
                   sumY * (sumT * sumT4 - sumT2 * sumT3) + 
                   sumT2 * (sumT * sumT2Y - sumT2 * sumTY)
        
        val detC = n * (sumT2 * sumT2Y - sumT3 * sumTY) - 
                   sumT * (sumT * sumT2Y - sumT2 * sumTY) + 
                   sumY * (sumT * sumT3 - sumT2 * sumT2)
        
        val a = detA / det
        val b = detB / det
        val c = detC / det
        
        return doubleArrayOf(a, b, c)
    }
    
    /**
     * Calculate directional trend from movement vectors
     */
    private fun calculateDirectionalTrend(movements: List<MovementVector>): Double {
        if (movements.size < 2) return 0.0
        
        val angles = movements.map { atan2(it.speedY, it.speedX) }
        
        // Calculate angular changes
        val angleChanges = mutableListOf<Double>()
        for (i in 1 until angles.size) {
            var angleDiff = angles[i] - angles[i - 1]
            // Normalize to [-pi, pi]
            while (angleDiff > PI) angleDiff -= 2 * PI
            while (angleDiff < -PI) angleDiff += 2 * PI
            angleChanges.add(angleDiff)
        }
        
        if (angleChanges.isEmpty()) return 0.0
        
        return angleChanges.average()
    }
    
    /**
     * Select final forecast velocity based on analysis results
     */
    private fun selectForecastVelocity(
        weightedSpeed: Pair<Double, Double>,
        regressionResult: RegressionResult?,
        directionalCorrection: Double
    ): Pair<Double, Double> {
        var speedX = weightedSpeed.first
        var speedY = weightedSpeed.second
        
        // Use regression if acceleration is significant
        if (regressionResult != null) {
            val accelSignificant = abs(regressionResult.acceleration.first) > 0.1 || 
                                   abs(regressionResult.acceleration.second) > 0.1
            if (accelSignificant) {
                speedX = regressionResult.velocity.first
                speedY = regressionResult.velocity.second
            }
        }
        
        // Apply directional correction if significant
        if (abs(directionalCorrection) > 0.1) {
            val forecastAngle = atan2(speedY, speedX)
            val forecastSpeed = sqrt(speedX * speedX + speedY * speedY)
            val correctedAngle = forecastAngle + directionalCorrection * 6
            speedX = forecastSpeed * cos(correctedAngle)
            speedY = forecastSpeed * sin(correctedAngle)
        }
        
        return Pair(speedX, speedY)
    }
    
    /**
     * Calculate forecast using regression coefficients
     */
    private fun calculateRegressionForecast(
        regressionResult: RegressionResult,
        centroids: List<StormCentroid>,
        forecastTime: Double
    ): Pair<Double, Double> {
        val forecastTimeTotal = regressionResult.currentTime + forecastTime
        val forecastX = regressionResult.xCoeffs[0] * forecastTimeTotal * forecastTimeTotal + 
                        regressionResult.xCoeffs[1] * forecastTimeTotal + 
                        regressionResult.xCoeffs[2]
        val forecastY = regressionResult.yCoeffs[0] * forecastTimeTotal * forecastTimeTotal + 
                        regressionResult.yCoeffs[1] * forecastTimeTotal + 
                        regressionResult.yCoeffs[2]
        
        // Convert to displacement from current position
        val currentX = centroids.last().x
        val currentY = centroids.last().y
        
        return Pair(forecastX - currentX, forecastY - currentY)
    }
    
    data class RegressionResult(
        val xCoeffs: DoubleArray,
        val yCoeffs: DoubleArray,
        val acceleration: Pair<Double, Double>,
        val velocity: Pair<Double, Double>,
        val currentTime: Double
    )
}
