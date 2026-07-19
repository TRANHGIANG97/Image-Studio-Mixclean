package com.thgiang.image.core.data.backgroundremove

import android.app.ActivityManager
import android.content.Context
import kotlin.math.min

/**
 * Picks segmentation backend **before** invoking ML models.
 *
 * - [Plan.SUBJECT] — Play-services Subject Segmentation (best quality); default on real devices.
 * - [Plan.SELFIE_PRIMARY] — bundled Selfie (x86 emulator debug, or heap cannot run Subject on mid-tier).
 * - [Plan.BLOCKED] — heap too low for Subject on weak Go/low-RAM devices (no Selfie — quality policy).
 */
internal object MlKitSegmentationRouter {

    enum class Plan {
        SUBJECT,
        SELFIE_PRIMARY,
        BLOCKED,
    }

    fun choosePlan(context: Context, sourceWidth: Int, sourceHeight: Int, policyCap: Int): Plan {
        MlKitMemoryBudget.prepareForMlKit()

        if (MlKitDeviceSupport.isSegmentationUnsupportedOnRelease(context)) {
            return Plan.BLOCKED
        }

        if (!MlKitMemoryBudget.canRunAnySegmentationAtAll()) {
            return Plan.BLOCKED
        }

        val workSide = min(
            policyCap,
            maxOf(sourceWidth, sourceHeight).coerceAtLeast(1),
        ).coerceAtLeast(MlKitMemoryBudget.MIN_SEGMENTATION_SIDE_PUBLIC)

        if (MlKitDeviceSupport.shouldForceSelfiePrimary(context)) {
            if (!SelfieFallbackSegmenter.isNativeLibraryAvailable(context)) {
                return Plan.BLOCKED
            }
            return if (MlKitMemoryBudget.canRunSelfieSegmentation(workSide, workSide)) {
                Plan.SELFIE_PRIMARY
            } else {
                Plan.BLOCKED
            }
        }

        // Android Go / low-RAM: Subject only — no Selfie (quality).
        if (MlKitDeviceSupport.shouldAvoidSelfieForQuality(context)) {
            return if (MlKitMemoryBudget.largestRunnableSubjectSide(policyCap) != null) {
                Plan.SUBJECT
            } else {
                Plan.BLOCKED
            }
        }

        val subjectRunnable = MlKitMemoryBudget.largestRunnableSubjectSide(policyCap)
        val selfieRunnable = MlKitMemoryBudget.largestRunnableSelfieSide(policyCap)
        return when {
            subjectRunnable != null -> Plan.SUBJECT
            !MlKitMemoryBudget.canRunSubjectSegmentation(workSide, workSide) &&
                MlKitMemoryBudget.canRunSelfieSegmentation(workSide, workSide) &&
                selfieRunnable != null -> Plan.SELFIE_PRIMARY
            else -> Plan.BLOCKED
        }
    }

    fun maxWorkSideForPlan(plan: Plan, policyCap: Int): Int? = when (plan) {
        Plan.BLOCKED -> null
        Plan.SUBJECT -> MlKitMemoryBudget.largestRunnableSubjectSide(policyCap)
        Plan.SELFIE_PRIMARY -> MlKitMemoryBudget.largestRunnableSelfieSide(policyCap)
    }
}
