package com.thgiang.image.core.data.backgroundremove

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Conservative post-processing for portrait masks.
 *
 * SAFE is the production default. AGGRESSIVE is opt-in for hard lower-body
 * background leaks after hybrid/ML Kit routes.
 */
object MaskPostProcessor {

    private const val TAG = "MaskPostProcessor"

    enum class Mode {
        SAFE,
        AGGRESSIVE
    }

    data class Options(
        val mode: Mode = Mode.SAFE,
        val maxProcessSide: Int = 1280,
        val guidedFilterRadius: Int = 4,
        val guidedFilterEps: Float = 0.006f,
        val guidedMix: Float = 0.28f,
        val hardBackgroundThreshold: Float = 0.025f,
        val hardForegroundThreshold: Float = 0.985f,
        val dehaloLowAlpha: Float = 0.045f,
        val dehaloHighAlpha: Float = 0.965f,
        val edgeConnectedWeakAlpha: Float = 0.38f,
        val backgroundColorDistance: Float = 0.18f,
        val greenBackgroundColorDistance: Float = 0.24f,
        val weakBackgroundAlpha: Float = 0.52f,
        val mediumBackgroundAlpha: Float = 0.72f,
        val maxInternalLeakAreaRatio: Float = 0.010f,
        val internalLeakMeanAlpha: Float = 0.62f,
        val internalLeakBgRatio: Float = 0.48f,
        val mlKitGuideMask: PortraitConfidenceMask? = null,
        val enablePersonPriorPrune: Boolean = false,
        val personPriorStartYRatio: Float = 0.32f,
        val personPriorCandidateAlpha: Float = 0.24f,
        val personPriorRejectAlpha: Float = 0.18f,
        val personPriorProtectAlpha: Float = 0.24f,
        val personPriorDilationRadius: Int = 8,
        val maxPersonPriorComponentRatio: Float = 0.18f,
        val enableResidualArtifactPrune: Boolean = true,
        val residualArtifactCandidateAlpha: Float = 0.18f,
        val residualArtifactRejectMlKitAlpha: Float = 0.28f,
        val maxGreenIslandAreaRatio: Float = 0.010f,
        val maxLowerArtifactAreaRatio: Float = 0.060f,
        val enableAggressiveGapPrune: Boolean = true,
        val enableThinColorResiduePrune: Boolean = true,
        val enableGreenLeakAssist: Boolean = false
    )

    fun postProcess(
        bitmap: Bitmap,
        mask: PortraitConfidenceMask,
        mode: Mode = Mode.SAFE
    ): PortraitConfidenceMask {
        return postProcess(
            bitmap = bitmap,
            mask = mask,
            options = Options(mode = mode)
        )
    }

    fun postProcess(
        bitmap: Bitmap,
        mask: PortraitConfidenceMask,
        options: Options = Options()
    ): PortraitConfidenceMask {
        require(!bitmap.isRecycled) { "Bitmap is recycled" }

        val normalized = MaskMath.ensureMaskSize(mask, bitmap.width, bitmap.height)
        val processInput = createProcessInputIfNeeded(
            bitmap = bitmap,
            mask = normalized,
            maxSide = options.maxProcessSide
        )

        return try {
            val guide = processInput.bitmap
            val workingMask = processInput.mask
            val width = workingMask.width
            val height = workingMask.height

            val pixels = IntArray(width * height)
            guide.getPixels(pixels, 0, width, 0, 0, width, height)

            val alpha = workingMask.values.copyOf()
            val mlKitGuide = options.mlKitGuideMask?.let {
                MaskMath.ensureMaskSize(it, width, height).values
            }

            snapAlpha(alpha, options)

            val guided = runCatching {
                FastGuidedFilter.refineAlpha(
                    guide = guide,
                    alpha = alpha,
                    w = width,
                    h = height,
                    radius = options.guidedFilterRadius,
                    eps = options.guidedFilterEps
                )
            }.onFailure {
                Log.w(TAG, "Guided filter failed, keeping original alpha", it)
            }.getOrElse {
                alpha.copyOf()
            }

            val refined = blendGuidedOnlyTransition(
                original = alpha,
                guided = guided,
                mix = options.guidedMix
            )

            cleanupEdgeConnectedWeakBackground(
                alpha = refined,
                width = width,
                height = height,
                weakAlpha = options.edgeConnectedWeakAlpha
            )

            if (options.mode == Mode.AGGRESSIVE) {
                removeBackgroundLikeLeaks(
                    pixels = pixels,
                    alpha = refined,
                    width = width,
                    height = height,
                    options = options
                )

                removeInternalBackgroundComponents(
                    pixels = pixels,
                    alpha = refined,
                    width = width,
                    height = height,
                    options = options
                )

                if (options.enablePersonPriorPrune && mlKitGuide != null) {
                    pruneByPersonPrior(
                        pixels = pixels,
                        alpha = refined,
                        mlKit = mlKitGuide,
                        width = width,
                        height = height,
                        options = options
                    )
                }

                if (options.enableResidualArtifactPrune && mlKitGuide != null) {
                    pruneResidualArtifacts(
                        pixels = pixels,
                        alpha = refined,
                        mlKit = mlKitGuide,
                        width = width,
                        height = height,
                        options = options
                    )
                }
            }

            dehaloAlpha(refined, options)

            val processed = PortraitConfidenceMask(width, height, refined)
            if (processInput.scaled) {
                MaskMath.resizeBilinear(processed, bitmap.width, bitmap.height)
            } else {
                processed
            }
        } finally {
            if (processInput.scaled && processInput.bitmap !== bitmap && !processInput.bitmap.isRecycled) {
                processInput.bitmap.recycle()
            }
        }
    }

    private data class ProcessInput(
        val bitmap: Bitmap,
        val mask: PortraitConfidenceMask,
        val scaled: Boolean
    )

    private fun createProcessInputIfNeeded(
        bitmap: Bitmap,
        mask: PortraitConfidenceMask,
        maxSide: Int
    ): ProcessInput {
        val srcMax = max(bitmap.width, bitmap.height)
        if (srcMax <= maxSide) {
            return ProcessInput(bitmap, mask, scaled = false)
        }

        val scale = maxSide.toFloat() / srcMax.toFloat()
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also {
            it.setHasAlpha(bitmap.hasAlpha())
        }

        val scaledMask = MaskMath.resizeBilinear(mask, targetWidth, targetHeight)
        return ProcessInput(scaledBitmap, scaledMask, scaled = true)
    }

    private fun snapAlpha(alpha: FloatArray, options: Options) {
        for (i in alpha.indices) {
            val a = alpha[i].coerceIn(0f, 1f)
            alpha[i] = when {
                a <= options.hardBackgroundThreshold -> 0f
                a >= options.hardForegroundThreshold -> 1f
                else -> a
            }
        }
    }

    private fun blendGuidedOnlyTransition(
        original: FloatArray,
        guided: FloatArray,
        mix: Float
    ): FloatArray {
        require(original.size == guided.size)

        val safeMix = mix.coerceIn(0f, 1f)
        val out = FloatArray(original.size)

        for (i in original.indices) {
            val a = original[i].coerceIn(0f, 1f)
            val g = guided[i].coerceIn(0f, 1f)

            out[i] = when {
                a >= 0.92f -> a
                a <= 0.05f -> 0f
                else -> (a * (1f - safeMix) + g * safeMix).coerceIn(0f, 1f)
            }
        }

        return out
    }

    private fun cleanupEdgeConnectedWeakBackground(
        alpha: FloatArray,
        width: Int,
        height: Int,
        weakAlpha: Float
    ) {
        val size = width * height
        val candidate = BooleanArray(size)

        for (i in 0 until size) {
            candidate[i] = alpha[i] < weakAlpha
        }

        val connected = floodFillEdgeConnected(candidate, width, height)

        for (i in 0 until size) {
            if (connected[i] && alpha[i] < weakAlpha) {
                alpha[i] = 0f
            }
        }
    }

    private fun removeBackgroundLikeLeaks(
        pixels: IntArray,
        alpha: FloatArray,
        width: Int,
        height: Int,
        options: Options
    ) {
        val samples = collectBorderBackgroundSamples(
            pixels = pixels,
            alpha = alpha,
            width = width,
            height = height
        )

        if (samples.isEmpty()) return

        for (y in 0 until height) {
            val row = y * width

            for (x in 0 until width) {
                val index = row + x
                val a = alpha[index]

                if (a <= 0f || a >= 0.90f) continue

                val bgLike = isSimilarToBackgroundSamples(
                    color = pixels[index],
                    samples = samples,
                    threshold = options.backgroundColorDistance
                )

                val greenAssist = options.enableGreenLeakAssist &&
                    y > height * 0.40f &&
                    a < 0.64f &&
                    !isProtectedCenterRegion(x, y, width, height) &&
                    isGreenDominant(pixels[index]) &&
                    isSimilarToBackgroundSamples(
                        color = pixels[index],
                        samples = samples,
                        threshold = options.greenBackgroundColorDistance
                    )

                if (!bgLike && !greenAssist) continue

                alpha[index] = when {
                    a < options.weakBackgroundAlpha -> 0f
                    a < options.mediumBackgroundAlpha -> (a * 0.42f).coerceIn(0f, 1f)
                    else -> a
                }
            }
        }
    }

    private fun removeInternalBackgroundComponents(
        pixels: IntArray,
        alpha: FloatArray,
        width: Int,
        height: Int,
        options: Options
    ) {
        val size = width * height

        val samples = collectBorderBackgroundSamples(pixels, alpha, width, height)
        if (samples.isEmpty()) return

        val candidate = BooleanArray(size)
        for (i in 0 until size) {
            val a = alpha[i]
            val bgLike = isSimilarToBackgroundSamples(
                color = pixels[i],
                samples = samples,
                threshold = options.backgroundColorDistance
            )

            candidate[i] = a < options.weakBackgroundAlpha ||
                (a < options.mediumBackgroundAlpha && bgLike)
        }

        val edgeConnected = floodFillEdgeConnected(candidate, width, height)
        val visited = BooleanArray(size)
        val queue = IntArray(size)

        val maxComponentArea = max(64, (size * options.maxInternalLeakAreaRatio).toInt())

        for (start in 0 until size) {
            if (!candidate[start] || edgeConnected[start] || visited[start]) continue

            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true

            var count = 0
            var alphaSum = 0f
            var bgLikeCount = 0
            var minY = height
            var maxY = 0

            while (head < tail) {
                val idx = queue[head++]
                val y = idx / width
                val x = idx - y * width

                count++
                alphaSum += alpha[idx]
                minY = min(minY, y)
                maxY = max(maxY, y)

                if (isSimilarToBackgroundSamples(
                        color = pixels[idx],
                        samples = samples,
                        threshold = options.backgroundColorDistance
                    )
                ) {
                    bgLikeCount++
                }

                fun push(n: Int) {
                    if (n < 0 || n >= size) return
                    if (!candidate[n] || edgeConnected[n] || visited[n]) return
                    visited[n] = true
                    queue[tail++] = n
                }

                if (x > 0) push(idx - 1)
                if (x < width - 1) push(idx + 1)
                if (y > 0) push(idx - width)
                if (y < height - 1) push(idx + width)
            }

            if (count <= 0) continue

            val meanAlpha = alphaSum / count
            val bgRatio = bgLikeCount.toFloat() / count.toFloat()
            val centerY = ((minY + maxY) * 0.5f) / height.toFloat()
            val isMiddleOrLower = centerY > 0.30f

            val shouldRemove = count <= maxComponentArea &&
                meanAlpha < options.internalLeakMeanAlpha &&
                bgRatio > options.internalLeakBgRatio &&
                isMiddleOrLower

            if (shouldRemove) {
                for (i in 0 until tail) {
                    alpha[queue[i]] = 0f
                }
            }
        }
    }

    private fun pruneByPersonPrior(
        pixels: IntArray,
        alpha: FloatArray,
        mlKit: FloatArray,
        width: Int,
        height: Int,
        options: Options
    ) {
        if (mlKit.size != alpha.size) return

        val size = width * height
        val protected = dilateMask(
            values = mlKit,
            width = width,
            height = height,
            threshold = options.personPriorProtectAlpha,
            radius = options.personPriorDilationRadius.coerceAtLeast(1)
        )

        val startY = (height * options.personPriorStartYRatio).toInt().coerceIn(0, height - 1)
        val candidate = BooleanArray(size)

        for (y in startY until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                if (protected[idx]) continue
                if (alpha[idx] <= options.personPriorCandidateAlpha) continue
                if (mlKit[idx] >= options.personPriorRejectAlpha) continue
                if (isSkinLike(pixels[idx])) continue

                candidate[idx] = true
            }
        }

        val visited = BooleanArray(size)
        val queue = IntArray(size)
        val maxComponentArea = max(96, (size * options.maxPersonPriorComponentRatio).toInt())
        var prunedComponents = 0
        var prunedPixels = 0

        for (start in 0 until size) {
            if (!candidate[start] || visited[start]) continue

            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true

            var count = 0
            var alphaSum = 0f
            var mlKitSum = 0f
            var skinCount = 0
            var lightGarmentCount = 0
            var minX = width
            var maxX = 0
            var minY = height
            var maxY = 0

            while (head < tail) {
                val idx = queue[head++]
                val y = idx / width
                val x = idx - y * width
                val color = pixels[idx]

                count++
                alphaSum += alpha[idx]
                mlKitSum += mlKit[idx]
                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)

                if (isSkinLike(color)) skinCount++
                if (isLightGarmentLike(color)) lightGarmentCount++

                fun push(n: Int) {
                    if (n < 0 || n >= size) return
                    if (!candidate[n] || visited[n]) return
                    visited[n] = true
                    queue[tail++] = n
                }

                if (x > 0) push(idx - 1)
                if (x < width - 1) push(idx + 1)
                if (y > startY) push(idx - width)
                if (y < height - 1) push(idx + width)
            }

            if (count <= 0 || count > maxComponentArea) continue

            val meanAlpha = alphaSum / count
            val meanMlKit = mlKitSum / count
            val skinRatio = skinCount.toFloat() / count.toFloat()
            val lightGarmentRatio = lightGarmentCount.toFloat() / count.toFloat()
            val centerX = ((minX + maxX) * 0.5f) / width.toFloat()
            val centerY = ((minY + maxY) * 0.5f) / height.toFloat()
            val touchesBorder = minX <= 1 || maxX >= width - 2 || maxY >= height - 2
            val tallThin = (maxY - minY) > height * 0.12f && (maxX - minX) < width * 0.07f
            val outsideMainSubject = centerX < 0.10f || centerX > 0.90f || touchesBorder
            val likelyGarment = lightGarmentRatio > 0.64f && centerX in 0.12f..0.88f && !touchesBorder

            val shouldRemove = meanMlKit < 0.10f &&
                skinRatio < 0.08f &&
                !likelyGarment &&
                (
                    touchesBorder ||
                        outsideMainSubject ||
                        tallThin ||
                        centerY > 0.46f ||
                        meanAlpha < 0.70f
                    )

            if (shouldRemove) {
                for (i in 0 until tail) {
                    alpha[queue[i]] = 0f
                }
                prunedComponents++
                prunedPixels += count
            } else if (meanMlKit < 0.14f && skinRatio < 0.05f && centerY > 0.44f) {
                for (i in 0 until tail) {
                    val idx = queue[i]
                    alpha[idx] = (alpha[idx] * 0.28f).coerceIn(0f, 1f)
                }
                prunedComponents++
                prunedPixels += count
            }
        }

        if (prunedComponents > 0) {
            Log.d(
                TAG,
                "Person prior prune: components=$prunedComponents, pixelsRatio=${prunedPixels.toFloat() / size.toFloat()}"
            )
        }
    }

    private fun pruneResidualArtifacts(
        pixels: IntArray,
        alpha: FloatArray,
        mlKit: FloatArray,
        width: Int,
        height: Int,
        options: Options
    ) {
        if (mlKit.size != alpha.size) return

        val size = width * height
        val protected = dilateMask(
            values = mlKit,
            width = width,
            height = height,
            threshold = options.personPriorProtectAlpha,
            radius = max(2, options.personPriorDilationRadius / 3)
        )
        val candidate = BooleanArray(size)
        val startY = (height * 0.30f).toInt().coerceIn(0, height - 1)

        for (y in startY until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                if (protected[idx]) continue
                if (alpha[idx] < options.residualArtifactCandidateAlpha) continue
                if (mlKit[idx] > options.residualArtifactRejectMlKitAlpha && alpha[idx] > 0.62f) continue
                if (isSkinLike(pixels[idx])) continue

                candidate[idx] = true
            }
        }

        val visited = BooleanArray(size)
        val queue = IntArray(size)
        val maxGreenArea = max(16, (size * options.maxGreenIslandAreaRatio).toInt())
        val maxLowerArea = max(48, (size * options.maxLowerArtifactAreaRatio).toInt())

        var greenComponents = 0
        var lowerComponents = 0
        var seatComponents = 0
        var shadowComponents = 0
        var gapComponents = 0
        var affectedPixels = 0

        for (start in 0 until size) {
            if (!candidate[start] || visited[start]) continue

            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true

            var count = 0
            var alphaSum = 0f
            var mlKitSum = 0f
            var skinCount = 0
            var lightGarmentCount = 0
            var greenYellowCount = 0
            var darkCount = 0
            var grayCount = 0
            var minX = width
            var maxX = 0
            var minY = height
            var maxY = 0

            while (head < tail) {
                val idx = queue[head++]
                val y = idx / width
                val x = idx - y * width
                val color = pixels[idx]

                count++
                alphaSum += alpha[idx]
                mlKitSum += mlKit[idx]
                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)

                if (isSkinLike(color)) skinCount++
                if (isLightGarmentLike(color)) lightGarmentCount++
                if (isGreenYellowArtifactLike(color)) greenYellowCount++
                if (brightness(color) < 82) darkCount++
                if (isGrayArtifactLike(color)) grayCount++

                fun push(n: Int) {
                    if (n < 0 || n >= size) return
                    if (!candidate[n] || visited[n]) return
                    visited[n] = true
                    queue[tail++] = n
                }

                if (x > 0) push(idx - 1)
                if (x < width - 1) push(idx + 1)
                if (y > startY) push(idx - width)
                if (y < height - 1) push(idx + width)
            }

            if (count <= 0) continue

            val boxWidth = max(1, maxX - minX + 1)
            val boxHeight = max(1, maxY - minY + 1)
            val centerX = ((minX + maxX) * 0.5f) / width.toFloat()
            val centerY = ((minY + maxY) * 0.5f) / height.toFloat()
            val meanAlpha = alphaSum / count.toFloat()
            val meanMlKit = mlKitSum / count.toFloat()
            val skinRatio = skinCount.toFloat() / count.toFloat()
            val lightGarmentRatio = lightGarmentCount.toFloat() / count.toFloat()
            val greenYellowRatio = greenYellowCount.toFloat() / count.toFloat()
            val darkRatio = darkCount.toFloat() / count.toFloat()
            val grayRatio = grayCount.toFloat() / count.toFloat()
            val touchesBottom = maxY >= height - 2
            val touchesSide = minX <= 1 || maxX >= width - 2
            val touchesInnerLowerBand = minY > height * 0.42f && maxY < height * 0.90f
            val tallThin = boxHeight > height * 0.075f && boxWidth < width * 0.080f
            val veryTallThin = boxHeight > height * 0.13f && boxWidth < width * 0.060f
            val wideLowLine = boxWidth > boxHeight * 3 && boxHeight < height * 0.070f
            val likelyGarment = lightGarmentRatio > 0.70f && centerX in 0.10f..0.90f && !touchesSide
            val protectedColor = skinRatio > 0.075f || likelyGarment

            if (protectedColor || meanMlKit > 0.24f) continue

            val greenIsland = count <= maxGreenArea &&
                greenYellowRatio > 0.24f &&
                meanAlpha < 0.96f &&
                centerY > 0.30f

            val seatLine = count <= maxLowerArea &&
                centerY in 0.44f..0.76f &&
                wideLowLine &&
                (darkRatio > 0.22f || grayRatio > 0.30f)

            val gapArtifact = options.enableAggressiveGapPrune &&
                count <= maxLowerArea &&
                touchesInnerLowerBand &&
                (tallThin || veryTallThin) &&
                centerX in 0.12f..0.88f &&
                lightGarmentRatio < 0.64f &&
                (darkRatio > 0.18f || greenYellowRatio > 0.18f || grayRatio > 0.24f || meanMlKit < 0.10f)

            val lowerArtifact = count <= maxLowerArea &&
                centerY > 0.42f &&
                (
                    tallThin ||
                        touchesBottom ||
                        touchesSide ||
                        darkRatio > 0.28f ||
                        grayRatio > 0.34f ||
                        greenYellowRatio > 0.20f ||
                        meanAlpha < 0.72f
                    )

            val shoeShadow = centerY > 0.70f &&
                meanAlpha < 0.70f &&
                (grayRatio > 0.28f || darkRatio > 0.16f) &&
                skinRatio < 0.05f &&
                lightGarmentRatio < 0.58f

            when {
                greenIsland -> {
                    zeroComponent(alpha, queue, tail)
                    greenComponents++
                    affectedPixels += count
                }

                seatLine -> {
                    zeroComponent(alpha, queue, tail)
                    seatComponents++
                    affectedPixels += count
                }

                gapArtifact -> {
                    zeroComponent(alpha, queue, tail)
                    gapComponents++
                    affectedPixels += count
                }

                lowerArtifact -> {
                    zeroComponent(alpha, queue, tail)
                    lowerComponents++
                    affectedPixels += count
                }

                shoeShadow -> {
                    scaleComponentAlpha(alpha, queue, tail, 0.15f)
                    shadowComponents++
                    affectedPixels += count
                }
            }
        }

        if (affectedPixels > 0) {
            Log.d(
                TAG,
                "Residual artifact prune: green=$greenComponents, lower=$lowerComponents, " +
                    "seat=$seatComponents, gap=$gapComponents, shadow=$shadowComponents, " +
                    "pixelsRatio=${affectedPixels.toFloat() / size.toFloat()}"
            )
        }

        if (options.enableThinColorResiduePrune) {
            pruneThinColorResidue(
                pixels = pixels,
                alpha = alpha,
                mlKit = mlKit,
                width = width,
                height = height
            )
        }
    }

    private fun pruneThinColorResidue(
        pixels: IntArray,
        alpha: FloatArray,
        mlKit: FloatArray,
        width: Int,
        height: Int
    ) {
        var removed = 0
        val startY = (height * 0.36f).toInt().coerceIn(0, height - 1)

        for (y in startY until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                if (alpha[idx] < 0.12f) continue
                if (mlKit[idx] > 0.34f && alpha[idx] > 0.54f) continue

                val color = pixels[idx]
                if (!isGreenYellowArtifactLike(color)) continue
                if (isSkinLike(color) || isLightGarmentLike(color)) continue
                if (hasStrongPersonSupportNearby(mlKit, width, height, x, y)) continue

                alpha[idx] = 0f
                removed++
            }
        }

        if (removed > 0) {
            Log.d(TAG, "Thin color residue prune: pixelsRatio=${removed.toFloat() / alpha.size.toFloat()}")
        }
    }

    private fun hasStrongPersonSupportNearby(
        mlKit: FloatArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int
    ): Boolean {
        val radius = 3
        val minY = max(0, y - radius)
        val maxY = min(height - 1, y + radius)
        val minX = max(0, x - radius)
        val maxX = min(width - 1, x + radius)
        var supported = 0

        for (ny in minY..maxY) {
            val row = ny * width
            for (nx in minX..maxX) {
                if (mlKit[row + nx] > 0.56f) {
                    supported++
                    if (supported >= 6) return true
                }
            }
        }

        return false
    }

    private fun zeroComponent(
        alpha: FloatArray,
        queue: IntArray,
        count: Int
    ) {
        for (i in 0 until count) {
            alpha[queue[i]] = 0f
        }
    }

    private fun scaleComponentAlpha(
        alpha: FloatArray,
        queue: IntArray,
        count: Int,
        scale: Float
    ) {
        for (i in 0 until count) {
            val idx = queue[i]
            alpha[idx] = (alpha[idx] * scale).coerceIn(0f, 1f)
        }
    }

    private fun dilateMask(
        values: FloatArray,
        width: Int,
        height: Int,
        threshold: Float,
        radius: Int
    ): BooleanArray {
        val size = width * height
        val out = BooleanArray(size)
        val radiusSq = radius * radius

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (values[row + x] < threshold) continue

                val minY = max(0, y - radius)
                val maxY = min(height - 1, y + radius)
                val minX = max(0, x - radius)
                val maxX = min(width - 1, x + radius)

                for (ny in minY..maxY) {
                    val dy = ny - y
                    val nRow = ny * width
                    for (nx in minX..maxX) {
                        val dx = nx - x
                        if (dx * dx + dy * dy <= radiusSq) {
                            out[nRow + nx] = true
                        }
                    }
                }
            }
        }

        return out
    }

    private fun floodFillEdgeConnected(
        candidate: BooleanArray,
        width: Int,
        height: Int
    ): BooleanArray {
        val size = width * height
        val connected = BooleanArray(size)
        val queue = IntArray(size)

        var head = 0
        var tail = 0

        fun push(index: Int) {
            if (index < 0 || index >= size) return
            if (!candidate[index] || connected[index]) return

            connected[index] = true
            queue[tail++] = index
        }

        for (x in 0 until width) {
            push(x)
            push((height - 1) * width + x)
        }

        for (y in 0 until height) {
            push(y * width)
            push(y * width + width - 1)
        }

        while (head < tail) {
            val idx = queue[head++]
            val y = idx / width
            val x = idx - y * width

            if (x > 0) push(idx - 1)
            if (x < width - 1) push(idx + 1)
            if (y > 0) push(idx - width)
            if (y < height - 1) push(idx + width)
        }

        return connected
    }

    private fun dehaloAlpha(alpha: FloatArray, options: Options) {
        for (i in alpha.indices) {
            val a = alpha[i].coerceIn(0f, 1f)

            alpha[i] = when {
                a < options.dehaloLowAlpha -> 0f
                a > options.dehaloHighAlpha -> 1f
                a < 0.22f -> (a * 0.82f).coerceIn(0f, 1f)
                a > 0.78f -> (a + (1f - a) * 0.12f).coerceIn(0f, 1f)
                else -> a
            }
        }
    }

    private fun collectBorderBackgroundSamples(
        pixels: IntArray,
        alpha: FloatArray,
        width: Int,
        height: Int
    ): IntArray {
        val samples = ArrayList<Int>(256)
        val step = max(4, max(width, height) / 96)

        fun maybeAdd(x: Int, y: Int, alphaLimit: Float) {
            if (x !in 0 until width || y !in 0 until height) return

            val idx = y * width + x
            if (idx !in pixels.indices || idx !in alpha.indices) return

            if (alpha[idx] < alphaLimit) {
                samples.add(pixels[idx])
            }
        }

        var x = 0
        while (x < width) {
            maybeAdd(x, 0, 0.35f)
            maybeAdd(x, height - 1, 0.35f)
            x += step
        }

        var y = 0
        while (y < height) {
            maybeAdd(0, y, 0.35f)
            maybeAdd(width - 1, y, 0.35f)
            y += step
        }

        if (samples.size < 12) {
            x = 0
            while (x < width) {
                maybeAdd(x, 0, 0.55f)
                maybeAdd(x, height - 1, 0.55f)
                x += step
            }

            y = 0
            while (y < height) {
                maybeAdd(0, y, 0.55f)
                maybeAdd(width - 1, y, 0.55f)
                y += step
            }
        }

        if (samples.size < 8) {
            Log.w(TAG, "Only ${samples.size} background samples, skip color cleanup")
            return IntArray(0)
        }

        return samples.toIntArray()
    }

    private fun isSimilarToBackgroundSamples(
        color: Int,
        samples: IntArray,
        threshold: Float
    ): Boolean {
        if (samples.isEmpty()) return false

        var best = Float.MAX_VALUE

        for (sample in samples) {
            val distance = normalizedColorDistance(color, sample)
            if (distance < best) {
                best = distance
                if (best <= threshold) return true
            }
        }

        return false
    }

    private fun normalizedColorDistance(a: Int, b: Int): Float {
        val ar = Color.red(a)
        val ag = Color.green(a)
        val ab = Color.blue(a)

        val br = Color.red(b)
        val bg = Color.green(b)
        val bb = Color.blue(b)

        val dr = (ar - br).toFloat()
        val dg = (ag - bg).toFloat()
        val db = (ab - bb).toFloat()

        return sqrt(dr * dr + dg * dg + db * db) / 441.67295f
    }

    private fun isProtectedCenterRegion(
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): Boolean {
        val left = (width * 0.18f).toInt()
        val right = (width * 0.82f).toInt()
        val top = (height * 0.12f).toInt()
        val bottom = (height * 0.88f).toInt()

        return x in left..right && y in top..bottom
    }

    private fun isGreenDominant(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        return g > r + 22 &&
            g > b + 18 &&
            g > 65
    }

    private fun isSkinLike(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))

        return r > 82 &&
            g > 45 &&
            b > 32 &&
            r > g + 8 &&
            r > b + 18 &&
            maxChannel - minChannel > 18
    }

    private fun isLightGarmentLike(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))

        return r > 172 &&
            g > 150 &&
            b > 150 &&
            maxChannel - minChannel < 72
    }

    private fun isGreenYellowArtifactLike(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        val green = g > r + 16 && g > b + 12 && g > 70
        val yellowGreen = g > b + 22 && r > b + 18 && g > 82 && r > 72

        return green || yellowGreen
    }

    private fun isGrayArtifactLike(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        val value = brightness(color)

        return value in 48..190 && maxChannel - minChannel < 42
    }

    private fun brightness(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        return ((r * 299 + g * 587 + b * 114) / 1000).coerceIn(0, 255)
    }
}
