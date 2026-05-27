package com.toshiba.modnet

import android.util.Log
import kotlin.math.max
import kotlin.math.min

object IsNetMaskCleaner {
    private const val TAG = "IsNetMaskCleaner"

    fun clean(mask: Mask): Mask {
        val width = mask.width
        val height = mask.height
        val data = mask.data.copyOf()
        val size = width * height
        nextQueueOffset = 0

        val strong = BooleanArray(size)
        for (i in 0 until size) {
            strong[i] = data[i] >= 0.42f
        }

        val visited = BooleanArray(size)
        val queue = IntArray(size)
        val kept = BooleanArray(size)
        val components = ArrayList<ComponentStats>()

        for (start in 0 until size) {
            if (!strong[start] || visited[start]) continue
            val stats = collectComponent(
                start = start,
                strong = strong,
                visited = visited,
                queue = queue,
                width = width,
                height = height
            )
            components.add(stats)
        }

        if (components.isEmpty()) return mask

        val largest = components.maxBy { it.count }
        val mainMinX = largest.minX
        val mainMaxX = largest.maxX
        val mainMinY = largest.minY
        val mainMaxY = largest.maxY
        val maxSmallIsland = max(24, (size * 0.004f).toInt())
        val maxArtifact = max(96, (size * 0.035f).toInt())

        var removedComponents = 0
        var removedPixels = 0

        for (component in components) {
            val overlapsMainBand = component.maxX >= mainMinX - width * 0.04f &&
                component.minX <= mainMaxX + width * 0.04f &&
                component.maxY >= mainMinY - height * 0.04f &&
                component.minY <= mainMaxY + height * 0.04f
            val nearMain = overlapsMainBand || distanceToBox(component, largest, width, height) < 0.075f
            val componentWidth = component.maxX - component.minX + 1
            val componentHeight = component.maxY - component.minY + 1
            val centerX = ((component.minX + component.maxX) * 0.5f) / width.toFloat()
            val centerY = ((component.minY + component.maxY) * 0.5f) / height.toFloat()
            val aspectTall = componentHeight > height * 0.12f && componentWidth < width * 0.055f
            val aspectWide = componentWidth > componentHeight * 4 && componentHeight < height * 0.055f
            val touchesBorder = component.minX <= 1 ||
                component.maxX >= width - 2 ||
                component.minY <= 1 ||
                component.maxY >= height - 2
            val bottomRight = centerX > 0.70f && centerY > 0.68f
            val aboveHead = component.maxY < mainMinY + height * 0.03f && component.count <= maxArtifact

            val shouldRemove = component !== largest && (
                component.count <= maxSmallIsland ||
                    (component.count <= maxArtifact && !nearMain) ||
                    (component.count <= maxArtifact && touchesBorder) ||
                    (component.count <= maxArtifact && bottomRight) ||
                    (component.count <= maxArtifact && aboveHead) ||
                    (component.count <= maxArtifact && aspectTall && centerY > 0.24f) ||
                    (component.count <= maxArtifact && aspectWide && centerY > 0.42f)
                )

            if (shouldRemove) {
                for (i in 0 until component.count) {
                    val idx = queue[component.queueOffset + i]
                    data[idx] = 0f
                }
                removedComponents++
                removedPixels += component.count
            } else {
                for (i in 0 until component.count) {
                    kept[queue[component.queueOffset + i]] = true
                }
            }
        }

        removeWeakHaloAwayFromKept(data, kept, width, height)

        if (removedComponents > 0) {
            Log.d(
                TAG,
                "clean: removedComponents=$removedComponents, removedPixelsRatio=${removedPixels.toFloat() / size.toFloat()}"
            )
        }

        return Mask(width, height, data).blurBoxSeparable(1)
    }

    private fun collectComponent(
        start: Int,
        strong: BooleanArray,
        visited: BooleanArray,
        queue: IntArray,
        width: Int,
        height: Int
    ): ComponentStats {
        val queueOffset = nextQueueOffset
        var head = queueOffset
        var tail = queueOffset

        fun push(index: Int) {
            if (index < 0 || index >= strong.size) return
            if (!strong[index] || visited[index]) return
            visited[index] = true
            queue[tail++] = index
        }

        push(start)

        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0

        while (head < tail) {
            val idx = queue[head++]
            val y = idx / width
            val x = idx - y * width
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)

            if (x > 0) push(idx - 1)
            if (x < width - 1) push(idx + 1)
            if (y > 0) push(idx - width)
            if (y < height - 1) push(idx + width)
        }

        val count = tail - queueOffset
        nextQueueOffset = tail
        return ComponentStats(queueOffset, count, minX, maxX, minY, maxY)
    }

    private fun removeWeakHaloAwayFromKept(
        data: FloatArray,
        kept: BooleanArray,
        width: Int,
        height: Int
    ) {
        val support = BooleanArray(data.size)
        val radius = 2

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                if (!kept[idx]) continue

                val minY = max(0, y - radius)
                val maxY = min(height - 1, y + radius)
                val minX = max(0, x - radius)
                val maxX = min(width - 1, x + radius)
                for (ny in minY..maxY) {
                    val nRow = ny * width
                    for (nx in minX..maxX) {
                        support[nRow + nx] = true
                    }
                }
            }
        }

        for (i in data.indices) {
            if (!support[i] && data[i] < 0.55f) {
                data[i] = 0f
            }
        }
    }

    private fun distanceToBox(
        a: ComponentStats,
        b: ComponentStats,
        width: Int,
        height: Int
    ): Float {
        val dx = when {
            a.maxX < b.minX -> b.minX - a.maxX
            b.maxX < a.minX -> a.minX - b.maxX
            else -> 0
        }
        val dy = when {
            a.maxY < b.minY -> b.minY - a.maxY
            b.maxY < a.minY -> a.minY - b.maxY
            else -> 0
        }
        return max(dx.toFloat() / width.toFloat(), dy.toFloat() / height.toFloat())
    }

    private data class ComponentStats(
        val queueOffset: Int,
        val count: Int,
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int
    )

    private var nextQueueOffset: Int = 0
}
