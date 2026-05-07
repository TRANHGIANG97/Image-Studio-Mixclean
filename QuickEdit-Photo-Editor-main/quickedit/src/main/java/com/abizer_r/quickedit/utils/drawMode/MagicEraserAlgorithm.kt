package com.abizer_r.quickedit.utils.drawMode

import android.graphics.Bitmap
import android.graphics.Color
import java.util.LinkedList
import java.util.Queue
import kotlin.math.pow
import kotlin.math.sqrt

object MagicEraserAlgorithm {

    /**
     * Performs a smart flood fill erasure on a bitmap within a specific radius and color tolerance.
     * @param bitmap The source bitmap to modify.
     * @param startX Initial X coordinate.
     * @param startY Initial Y coordinate.
     * @param radius The maximum distance from start point to erase.
     * @param tolerance Color similarity threshold (0-255).
     * @return A modified bitmap.
     */
    fun erase(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        radius: Float,
        tolerance: Int
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (startX !in 0 until width || startY !in 0 until height) return bitmap

        // Work on a mutable copy if not already mutable
        val mutableBitmap = if (bitmap.isMutable) bitmap else bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        
        // Get all pixels into an array for high-performance processing
        val pixels = IntArray(width * height)
        mutableBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val targetColor = pixels[startY * width + startX]
        if (targetColor == Color.TRANSPARENT) return mutableBitmap

        val targetR = Color.red(targetColor)
        val targetG = Color.green(targetColor)
        val targetB = Color.blue(targetColor)

        // Queue for BFS loang màu
        val queue: Queue<Int> = LinkedList()
        queue.add(startY * width + startX)

        // Keep track of visited pixels to avoid infinite loops
        val visited = BooleanArray(width * height)
        visited[startY * width + startX] = true

        val radiusSq = radius * radius

        while (queue.isNotEmpty()) {
            val index = queue.poll() ?: continue
            val currX = index % width
            val currY = index / width

            // Erase the pixel
            pixels[index] = Color.TRANSPARENT

            // Check 4-directional neighbors
            val neighbors = arrayOf(
                currX + 1 to currY,
                currX - 1 to currY,
                currX to currY + 1,
                currX to currY - 1
            )

            for ((nx, ny) in neighbors) {
                if (nx in 0 until width && ny in 0 until height) {
                    val nIndex = ny * width + nx
                    
                    if (!visited[nIndex]) {
                        visited[nIndex] = true

                        // Check 1: Is it within the brush radius?
                        val distSq = (nx - startX).toDouble().pow(2) + (ny - startY).toDouble().pow(2)
                        if (distSq <= radiusSq) {
                            
                            // Check 2: Is the color similar enough? (Edge-Aware logic)
                            val nColor = pixels[nIndex]
                            if (nColor != Color.TRANSPARENT) {
                                val nR = Color.red(nColor)
                                val nG = Color.green(nColor)
                                val nB = Color.blue(nColor)

                                val colorDiff = sqrt(
                                    (nR - targetR).toDouble().pow(2) +
                                    (nG - targetG).toDouble().pow(2) +
                                    (nB - targetB).toDouble().pow(2)
                                )

                                if (colorDiff <= tolerance) {
                                    queue.add(nIndex)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Copy modified pixels back to bitmap
        mutableBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return mutableBitmap
    }
}
