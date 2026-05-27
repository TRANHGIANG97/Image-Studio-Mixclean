package com.toshiba.modnet

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object NativeMaskFusion {
    private const val TAG = "NativeMaskFusion"
    private const val MAX_FUSION_SIDE = 1536

    private external fun nativeFuseMasks(
        modnetMask: FloatArray,
        coreMask: FloatArray,
        width: Int,
        height: Int,
        erodeRadius: Int,
        coreThreshold: Float,
        modnetWeakThreshold: Float,
    ): FloatArray?

    private external fun nativeRefineCoreStrict(
        sourcePixels: IntArray,
        detailMask: FloatArray,
        coreMask: FloatArray,
        width: Int,
        height: Int,
        closeRadius: Int,
        greenSuppress: Boolean,
    ): FloatArray?

    private external fun nativeGuidedFilterRefine(
        sourcePixels: IntArray,
        mask: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
        epsilon: Float,
    ): FloatArray?

    fun fuse(
        modNetMask: Mask,
        coreMask: Mask,
        erodeRadius: Int = 2,
        coreThreshold: Float = 0.72f,
        modnetWeakThreshold: Float = 0.48f,
    ): Mask {
        val smallModNet = if (modNetMask.width > MAX_FUSION_SIDE || modNetMask.height > MAX_FUSION_SIDE) {
            resizeToMaxSide(modNetMask, MAX_FUSION_SIDE)
        } else {
            modNetMask
        }
        val smallCore = coreMask.resizeBilinear(smallModNet.width, smallModNet.height)

        val fused = runCatching {
            nativeFuseMasks(
                smallModNet.data,
                smallCore.data,
                smallModNet.width,
                smallModNet.height,
                erodeRadius,
                coreThreshold,
                modnetWeakThreshold,
            )
        }.onFailure {
            Log.w(TAG, "Native fusion failed, using Kotlin fallback", it)
        }.getOrNull()

        val fusedSmall = if (fused != null && fused.size == smallModNet.data.size) {
            Mask(smallModNet.width, smallModNet.height, fused)
        } else {
            kotlinFuse(smallModNet, smallCore, coreThreshold, modnetWeakThreshold)
        }

        return if (fusedSmall.width == modNetMask.width && fusedSmall.height == modNetMask.height) {
            fusedSmall
        } else {
            fusedSmall.resizeBilinear(modNetMask.width, modNetMask.height)
        }
    }

    fun fuseCoreFirstDetail(
        source: Bitmap,
        detailMask: Mask,
        coreMask: Mask,
    ): Mask {
        val smallDetail = if (detailMask.width > MAX_FUSION_SIDE || detailMask.height > MAX_FUSION_SIDE) {
            resizeToMaxSide(detailMask, MAX_FUSION_SIDE)
        } else {
            detailMask
        }
        val smallCore = coreMask.resizeBilinear(smallDetail.width, smallDetail.height)
        val sourceForFusion = source.scaledForFusion(smallDetail.width, smallDetail.height)
        val pixels = IntArray(smallDetail.width * smallDetail.height)
        sourceForFusion.getPixels(pixels, 0, smallDetail.width, 0, 0, smallDetail.width, smallDetail.height)
        val rawCoverage = smallCore.foregroundCoverage(0.5f)

        val nativeValues = runCatching {
            nativeRefineCoreStrict(
                sourcePixels = pixels,
                detailMask = smallDetail.data,
                coreMask = smallCore.data,
                width = smallDetail.width,
                height = smallDetail.height,
                closeRadius = 2,
                greenSuppress = true,
            )
        }.onFailure {
            Log.w(TAG, "Native core-first refine failed, using Kotlin fallback", it)
        }.getOrNull()

        val fusedSmall = if (nativeValues != null && nativeValues.size == smallDetail.data.size) {
            val refined = Mask(smallDetail.width, smallDetail.height, nativeValues)
            val refinedCoverage = refined.foregroundCoverage(0.5f)
            val coverageDelta = kotlin.math.abs(refinedCoverage - rawCoverage)
            if (coverageDelta > 0.03f || refinedCoverage < rawCoverage * 0.90f || refinedCoverage > rawCoverage * 1.06f) {
                Log.w(
                    TAG,
                    "Native refinement diverged from ML Kit core: raw=$rawCoverage refined=$refinedCoverage, fallback to core"
                )
                smallCore
            } else {
                Log.d(TAG, "Native core-first refine used — size=${smallDetail.width}x${smallDetail.height}")
                refined
            }
        } else {
            if (nativeValues != null) {
                Log.w(TAG, "Native returned wrong size: ${nativeValues.size} vs ${smallDetail.data.size}")
            }
            kotlinFuseCoreFirstDetail(
                detailMask = smallDetail,
                coreMask = smallCore,
                pixels = pixels,
            )
        }
        if (sourceForFusion !== source && !sourceForFusion.isRecycled) {
            sourceForFusion.recycle()
        }

        // Tầng 2: Guided Filter — edge-aware mask refinement
        // radius=7 @ 1536px (~0.5% width) — đủ anti-alias mà không lan qua vùng áo trắng/nền trắng
        // epsilon=1.5e-3 — bảo thủ hơn trước (5e-4): ít bị lừa bởi vùng ít gradient như áo trắng
        val sourceForGuided = source.scaledForFusion(fusedSmall.width, fusedSmall.height)
        val guidedPixels = IntArray(fusedSmall.width * fusedSmall.height)
        sourceForGuided.getPixels(guidedPixels, 0, fusedSmall.width, 0, 0, fusedSmall.width, fusedSmall.height)

        val guidedValues = runCatching {
            nativeGuidedFilterRefine(
                sourcePixels = guidedPixels,
                mask = fusedSmall.data,
                width = fusedSmall.width,
                height = fusedSmall.height,
                radius = 7,
                epsilon = 1.5e-3f,
            )
        }.onFailure {
            Log.w(TAG, "Guided filter failed, using raw fusion mask", it)
        }.getOrNull()

        if (sourceForGuided !== source && !sourceForGuided.isRecycled) {
            sourceForGuided.recycle()
        }

        // Fix Bug 1: Safety check — nếu GF làm thay đổi coverage > 5%, fallback về fusedSmall
        // GF không nên tự ý xoá/thêm foreground, chỉ được làm mượt viền sẵn có
        val refinedSmall = if (guidedValues != null && guidedValues.size == fusedSmall.data.size) {
            val guidedMask = Mask(fusedSmall.width, fusedSmall.height, guidedValues)
            val origCoverage = fusedSmall.foregroundCoverage(0.5f)
            val guidedCoverage = guidedMask.foregroundCoverage(0.5f)
            val coverageDelta = kotlin.math.abs(guidedCoverage - origCoverage)
            if (coverageDelta > 0.05f
                || guidedCoverage < origCoverage * 0.88f
                || guidedCoverage > origCoverage * 1.10f
            ) {
                Log.w(
                    TAG,
                    "Guided filter diverged: orig=$origCoverage guided=$guidedCoverage " +
                        "delta=$coverageDelta — fallback to fusedSmall"
                )
                fusedSmall
            } else {
                Log.d(TAG, "Guided filter OK — orig=$origCoverage guided=$guidedCoverage size=${fusedSmall.width}x${fusedSmall.height}")
                guidedMask
            }
        } else {
            fusedSmall
        }

        return if (refinedSmall.width == detailMask.width && refinedSmall.height == detailMask.height) {
            refinedSmall
        } else {
            refinedSmall.resizeBilinear(detailMask.width, detailMask.height)
        }
    }


    private fun kotlinFuse(
        modNetMask: Mask,
        coreMask: Mask,
        coreThreshold: Float,
        modnetWeakThreshold: Float,
    ): Mask {
        val out = FloatArray(modNetMask.data.size)
        for (i in out.indices) {
            val mod = modNetMask.data[i].coerceIn(0f, 1f)
            val core = coreMask.data[i].coerceIn(0f, 1f)
            out[i] = if (core < coreThreshold || mod >= modnetWeakThreshold) {
                mod
            } else {
                maxOf(mod, core)
            }
        }
        return Mask(modNetMask.width, modNetMask.height, out)
    }

    private fun kotlinFuseCoreFirstDetail(
        detailMask: Mask,
        coreMask: Mask,
        pixels: IntArray,
    ): Mask {
        val width = detailMask.width
        val height = detailMask.height
        val size = width * height

        val hardCore = BooleanArray(size)
        val softCore = BooleanArray(size)
        for (i in 0 until size) {
            val core = coreMask.data[i].coerceIn(0f, 1f)
            hardCore[i] = core > 0.28f
            softCore[i] = core > 0.08f
        }

        val allowed = dilate(softCore, width, height, 2)
        val alpha = FloatArray(size)
        var edgePixels = 0
        var greenSuppressed = 0

        for (i in 0 until size) {
            val core = coreMask.data[i].coerceIn(0f, 1f)
            val detail = detailMask.data[i].coerceIn(0f, 1f)

            alpha[i] = when {
                core <= 0.025f || !allowed[i] -> 0f
                core >= 0.70f -> maxOf(core, detail * 0.94f)
                core >= 0.30f -> minOf(maxOf(core * 0.94f, detail * 0.82f), core + 0.05f)
                core >= 0.10f -> minOf(maxOf(core * 0.88f, detail * 0.72f), core + 0.03f)
                else -> core * 0.70f
            }

            if (alpha[i] > 0f && core < 0.55f && allowed[i] && isGreenFringe(pixels[i])) {
                alpha[i] = if (core < 0.16f) 0f else minOf(alpha[i], core * 0.28f)
                greenSuppressed++
            }

            if (allowed[i] && hardCore[i].not()) {
                edgePixels++
            }
        }

        val blurred = boxBlurInAllowed(alpha, allowed, width, height)
        for (i in 0 until size) {
            val core = coreMask.data[i].coerceIn(0f, 1f)
            blurred[i] = when {
                !allowed[i] -> 0f
                core >= 0.65f -> maxOf(blurred[i], core)
                core < 0.22f -> minOf(blurred[i], core + 0.03f)
                else -> minOf(blurred[i], core + 0.05f)
            }.coerceIn(0f, 1f)
        }

        Log.d(
            TAG,
            "Core-first Kotlin soft: edgeR=${edgePixels.toFloat() / size.toFloat()} " +
                "greenSupp=${greenSuppressed.toFloat() / size.toFloat()}"
        )

        return Mask(width, height, blurred)
    }

    /**
     * Fill small holes within closedHard silhouette. Allows thin border-touching components.
     */
    private fun fillHolesStrictPlus(
        closedHard: BooleanArray,
        hardCore: BooleanArray,
        allowed: BooleanArray,
        width: Int,
        height: Int,
    ): BooleanArray {
        val size = width * height
        val candidate = BooleanArray(size) { closedHard[it] && !hardCore[it] && allowed[it] }
        val visited = BooleanArray(size)
        val queue = IntArray(size)
        val fill = BooleanArray(size)
        val maxArea = maxOf(64, (size * 0.025f).toInt())

        for (start in 0 until size) {
            if (!candidate[start] || visited[start]) continue

            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true

            var minX = width; var maxX = 0
            var minY = height; var maxY = 0
            var touchesBorder = false
            var borderContact = 0

            while (head < tail) {
                val idx = queue[head++]
                val y = idx / width
                val x = idx - y * width
                minX = minOf(minX, x); maxX = maxOf(maxX, x)
                minY = minOf(minY, y); maxY = maxOf(maxY, y)
                if (x <= 1 || y <= 1 || x >= width - 2 || y >= height - 2) {
                    touchesBorder = true
                    borderContact++
                }

                fun push(n: Int) {
                    if (n in 0 until size && candidate[n] && !visited[n]) {
                        visited[n] = true
                        queue[tail++] = n
                    }
                }
                if (x > 0) push(idx - 1)
                if (x < width - 1) push(idx + 1)
                if (y > 0) push(idx - width)
                if (y < height - 1) push(idx + width)
            }

            val compW = maxX - minX + 1
            val compH = maxY - minY + 1
            val centerY = ((minY + maxY) * 0.5f) / height.toFloat()
            val borderThin = touchesBorder &&
                (compW < 8 || compH < 8 || borderContact < maxOf(5, tail / 3))
            val largeGap = compW > width * 0.18f && compH > height * 0.24f
            val tooBig = tail > maxArea &&
                !(compW < width * 0.10f || compH < height * 0.22f)

            if ((!touchesBorder || borderThin) && centerY > 0.25f && !largeGap && !tooBig) {
                for (i in 0 until tail) fill[queue[i]] = true
            }
        }

        return fill
    }

    private fun boxBlurInAllowed(
        alpha: FloatArray,
        allowed: BooleanArray,
        width: Int,
        height: Int,
    ): FloatArray {
        val out = FloatArray(alpha.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (!allowed[idx]) { out[idx] = 0f; continue }

                var sum = 0f
                var count = 0
                for (yy in maxOf(0, y - 1)..minOf(height - 1, y + 1)) {
                    val row = yy * width
                    for (xx in maxOf(0, x - 1)..minOf(width - 1, x + 1)) {
                        val n = row + xx
                        if (allowed[n]) { sum += alpha[n]; count++ }
                    }
                }
                out[idx] = if (count > 0) (sum / count).coerceIn(0f, 1f) else 0f
            }
        }
        return out
    }

    private fun isGreenFringe(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return g > r + 18 &&
            g > b + 12 &&
            g > 72 &&
            r < 190
    }

    private fun suppressOutsideCore(
        alpha: FloatArray,
        coreMask: Mask,
    ) {
        for (i in alpha.indices) {
            val core = coreMask.data[i].coerceIn(0f, 1f)
            if (core <= 0.025f) {
                alpha[i] = 0f
            } else if (core < 0.22f) {
                alpha[i] = min(alpha[i], core + 0.08f)
            }
        }
    }

    private fun isHairOrEdgeDetail(
        index: Int,
        pixels: IntArray,
        width: Int,
        height: Int,
        nearCore: Boolean,
    ): Boolean {
        val color = pixels[index]
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        val brightness = (r + g + b) / 3
        val saturation = if (maxChannel == 0) 0f else (maxChannel - minChannel).toFloat() / maxChannel.toFloat()
        val contrast = localContrast(index, pixels, width, height)

        val darkHair = brightness < 118 && saturation < 0.72f
        val blondeHair = brightness in 105..205 && r > b + 14 && g > b + 8 && saturation > 0.10f && contrast > 18
        val crispEdge = nearCore && contrast > 26 && brightness < 225

        return darkHair || blondeHair || crispEdge
    }

    private fun localContrast(
        index: Int,
        pixels: IntArray,
        width: Int,
        height: Int,
    ): Int {
        val y = index / width
        val x = index - y * width
        val center = luma(pixels[index])
        var best = 0

        fun check(nx: Int, ny: Int) {
            if (nx !in 0 until width || ny !in 0 until height) return
            best = max(best, abs(center - luma(pixels[ny * width + nx])))
        }

        check(x - 1, y)
        check(x + 1, y)
        check(x, y - 1)
        check(x, y + 1)
        return best
    }

    private fun luma(color: Int): Int {
        return ((Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000)
    }

    private fun suppressDetachedDetail(
        alpha: FloatArray,
        coreSoft: BooleanArray,
        allowedArea: BooleanArray,
        width: Int,
        height: Int,
    ) {
        val size = alpha.size
        val visited = BooleanArray(size)
        val queue = IntArray(size)

        for (start in 0 until size) {
            if (visited[start] || alpha[start] < 0.18f) continue

            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var touchesCore = coreSoft[start]
            var outsideAllowed = !allowedArea[start]

            while (head < tail) {
                val idx = queue[head++]
                val y = idx / width
                val x = idx - y * width
                touchesCore = touchesCore || coreSoft[idx]
                outsideAllowed = outsideAllowed || !allowedArea[idx]

                fun push(n: Int) {
                    if (n < 0 || n >= size || visited[n] || alpha[n] < 0.18f) return
                    visited[n] = true
                    queue[tail++] = n
                }

                if (x > 0) push(idx - 1)
                if (x < width - 1) push(idx + 1)
                if (y > 0) push(idx - width)
                if (y < height - 1) push(idx + width)
            }

            if (!touchesCore || outsideAllowed) {
                for (i in 0 until tail) {
                    alpha[queue[i]] = 0f
                }
            }
        }
    }

    private fun dilate(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
        if (radius <= 0) return mask.copyOf()
        val out = BooleanArray(mask.size)
        val r2 = radius * radius
        for (y in 0 until height) {
            for (x in 0 until width) {
                var found = false
                val minY = max(0, y - radius)
                val maxY = min(height - 1, y + radius)
                val minX = max(0, x - radius)
                val maxX = min(width - 1, x + radius)
                for (ny in minY..maxY) {
                    if (found) break
                    val dy = ny - y
                    val row = ny * width
                    for (nx in minX..maxX) {
                        val dx = nx - x
                        if (dx * dx + dy * dy <= r2 && mask[row + nx]) {
                            found = true
                            break
                        }
                    }
                }
                out[y * width + x] = found
            }
        }
        return out
    }

    private fun erode(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
        if (radius <= 0) return mask.copyOf()
        val out = BooleanArray(mask.size)
        val r2 = radius * radius
        for (y in 0 until height) {
            for (x in 0 until width) {
                var all = true
                val minY = max(0, y - radius)
                val maxY = min(height - 1, y + radius)
                val minX = max(0, x - radius)
                val maxX = min(width - 1, x + radius)
                for (ny in minY..maxY) {
                    if (!all) break
                    val dy = ny - y
                    val row = ny * width
                    for (nx in minX..maxX) {
                        val dx = nx - x
                        if (dx * dx + dy * dy <= r2 && !mask[row + nx]) {
                            all = false
                            break
                        }
                    }
                }
                out[y * width + x] = all
            }
        }
        return out
    }

    private fun resizeToMaxSide(mask: Mask, maxSide: Int): Mask {
        val srcMax = maxOf(mask.width, mask.height)
        if (srcMax <= maxSide) return mask
        val scale = maxSide.toFloat() / srcMax
        return mask.resizeBilinear(
            targetWidth = (mask.width * scale).toInt().coerceAtLeast(1),
            targetHeight = (mask.height * scale).toInt().coerceAtLeast(1),
        )
    }

    private fun Bitmap.scaledForFusion(targetWidth: Int, targetHeight: Int): Bitmap {
        if (width == targetWidth && height == targetHeight) return this
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true).also {
            it.setHasAlpha(hasAlpha())
        }
    }

}
