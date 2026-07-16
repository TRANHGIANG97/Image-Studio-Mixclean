package com.abizer_r.quickedit.utils.effectsMode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.abizer_r.quickedit.ui.effectsMode.effectsPreview.EffectItem
import com.abizer_r.quickedit.ui.effectsMode.effectsPreview.EffectRecipe
import com.abizer_r.quickedit.utils.AppUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

object EffectsModeUtils {

    private const val MAX_CONCURRENT_FILTERS = 4
    private val concurrencyLimit = Semaphore(MAX_CONCURRENT_FILTERS)
    private const val EFFECT_PREVIEW_SAMPLE_ASSET = "image_sample/img_0001.jpg"

    fun getEffectsPreviewList(
        context: Context,
        bitmap: Bitmap,
    ) = flow<ArrayList<EffectItem>> {
        val effectList = arrayListOf<EffectItem>()
        // Only generate small previews for the strip + main stage to avoid holding ~25 full-res bitmaps.
        val previewSourceScaledBitmap = getScaledPreviewBitmap(context, bitmap)
        val thumbSource = loadEffectPreviewSample(context)?.let {
            getScaledPreviewBitmap(context, it)
        } ?: previewSourceScaledBitmap

        effectList.add(
            EffectItem(
                ogBitmap = previewSourceScaledBitmap,
                previewBitmap = thumbSource,
                label = context.getString(com.abizer_r.quickedit.R.string.effect_original),
                recipe = EffectRecipe.Original,
            )
        )

        val filterDefs = mutableListOf<suspend () -> EffectItem?>()

        filterDefs.add {
            try {
                EffectItem(
                    ogBitmap = BitmapGrayscaleFilter.apply(previewSourceScaledBitmap),
                    previewBitmap = BitmapGrayscaleFilter.apply(thumbSource),
                    label = context.getString(com.abizer_r.quickedit.R.string.effect_grayscale),
                    recipe = EffectRecipe.Grayscale,
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        filterDefs.add {
            try {
                EffectItem(
                    ogBitmap = BitmapBlurFilter.apply(context, previewSourceScaledBitmap),
                    previewBitmap = BitmapBlurFilter.apply(context, thumbSource),
                    label = context.getString(com.abizer_r.quickedit.R.string.effect_blur),
                    recipe = EffectRecipe.Blur,
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

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
                            EffectItem(
                                ogBitmap = BitmapToneCurveFilter.apply(previewSourceScaledBitmap, curve),
                                previewBitmap = BitmapToneCurveFilter.apply(thumbSource, curve),
                                label = fileName.drop(4).dropLast(4).replace("_", " "),
                                recipe = EffectRecipe.Acv(fileName),
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }

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

    /** Apply selected recipe at full resolution when user taps Done. */
    suspend fun applyFullResolution(
        context: Context,
        source: Bitmap,
        recipe: EffectRecipe,
    ): Bitmap = withContext(Dispatchers.Default) {
        when (recipe) {
            EffectRecipe.Original -> source
            EffectRecipe.Grayscale -> BitmapGrayscaleFilter.apply(source)
            EffectRecipe.Blur -> BitmapBlurFilter.apply(context, source)
            is EffectRecipe.Acv -> {
                context.assets.open(recipe.assetPath).use { stream ->
                    val curve = AcvToneCurveParser.parse(stream)
                    BitmapToneCurveFilter.apply(source, curve)
                }
            }
        }
    }

    private fun loadEffectPreviewSample(context: Context): Bitmap? {
        return try {
            context.assets.open(EFFECT_PREVIEW_SAMPLE_ASSET).use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getScaledPreviewBitmap(context: Context, originalBitmap: Bitmap): Bitmap {
        val screenWidth = AppUtils.getScreenWidth(context)
        val aspectRatio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
        val reqWidth = (screenWidth / 3).coerceAtLeast(120)
        val reqHeight = (reqWidth / aspectRatio).toInt().coerceAtLeast(120)
        return Bitmap.createScaledBitmap(originalBitmap, reqWidth, reqHeight, true)
    }
}
