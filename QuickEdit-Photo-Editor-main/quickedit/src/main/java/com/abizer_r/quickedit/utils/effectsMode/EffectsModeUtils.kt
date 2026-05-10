package com.abizer_r.quickedit.utils.effectsMode

import android.content.Context
import android.graphics.Bitmap
import com.abizer_r.quickedit.ui.effectsMode.effectsPreview.EffectItem
import com.abizer_r.quickedit.utils.AppUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object EffectsModeUtils {

    private const val MAX_CONCURRENT_FILTERS = 4
    private val concurrencyLimit = Semaphore(MAX_CONCURRENT_FILTERS)

    fun getEffectsPreviewList(
        context: Context,
        bitmap: Bitmap,
    ) = flow<ArrayList<EffectItem>> {
        val effectList = arrayListOf<EffectItem>()

        effectList.add(
            EffectItem(
                ogBitmap = bitmap,
                previewBitmap = getScaledPreviewBitmap(context, bitmap),
                label = "original"
            )
        )

        val filterDefs = mutableListOf<suspend () -> EffectItem?>()

        // Grayscale
        filterDefs.add {
            try {
                val grayBitmap = BitmapGrayscaleFilter.apply(bitmap)
                EffectItem(
                    ogBitmap = grayBitmap,
                    previewBitmap = getScaledPreviewBitmap(context, grayBitmap),
                    label = "grayscale"
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        // Blur
        filterDefs.add {
            try {
                val blurBitmap = BitmapBlurFilter.apply(context, bitmap)
                EffectItem(
                    ogBitmap = blurBitmap,
                    previewBitmap = getScaledPreviewBitmap(context, blurBitmap),
                    label = "blur"
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        // ACV filters
        val filterFiles = listOf(
            "acv/Fade.acv",
            "acv/Pistol.acv",
            "acv/Cinnamon_darkness.acv",
            "acv/Blue_Poppies.acv",
            "acv/Brighten.acv",
            "acv/Darken.acv",
            "acv/Contrast.acv",
            "acv/Matte.acv",
            "acv/Softness.acv",
            "acv/Carousel.acv",
            "acv/Electric.acv",
            "acv/Peacock_Feathers.acv",
            "acv/Good_Luck_Charm.acv",
            "acv/Lullabye.acv",
            "acv/Moth_Wings.acv",
            "acv/Old_Postcards_1.acv",
            "acv/Old_Postcards_2.acv",
            "acv/Toes_In_The_Ocean.acv",
            "acv/Mark_Galer_Grading.acv",
            "acv/Curve_1.acv",
            "acv/Curve_2.acv",
            "acv/Curve_3.acv",
            "acv/Curve_Le_Fabuleux_Coleur_de_Amelie.acv",
            "acv/Tropical_Beach.acv"
        )

        filterFiles.forEach { fileName ->
            filterDefs.add {
                try {
                    concurrencyLimit.withPermit {
                        context.assets.open(fileName).use { stream ->
                            val curve = AcvToneCurveParser.parse(stream)
                            val filtered = BitmapToneCurveFilter.apply(bitmap, curve)
                            EffectItem(
                                ogBitmap = filtered,
                                previewBitmap = getScaledPreviewBitmap(context, filtered),
                                label = fileName.drop(4).dropLast(4).replace("_", " ")
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }

        // Apply all filters in parallel with concurrency limit
        coroutineScope {
            val deferredItems = filterDefs.map { filterDef ->
                async(Dispatchers.Default) { filterDef() }
            }
            var batchCount = 0
            for (deferred in deferredItems) {
                val item = deferred.await()
                if (item != null) {
                    effectList.add(item)
                    batchCount++
                    if (batchCount >= 10) {
                        emit(ArrayList(effectList))
                        effectList.clear()
                        batchCount = 0
                    }
                }
            }
            if (effectList.isNotEmpty()) {
                emit(effectList)
            }
        }
    }

    fun getScaledPreviewBitmap(context: Context, originalBitmap: Bitmap): Bitmap {
        val screenWidth = AppUtils.getScreenWidth(context)
        val aspectRatio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
        val reqWidth = screenWidth / 3
        val reqHeight = (reqWidth / aspectRatio).toInt()
        return Bitmap.createScaledBitmap(originalBitmap, reqWidth, reqHeight, false)
    }
}
