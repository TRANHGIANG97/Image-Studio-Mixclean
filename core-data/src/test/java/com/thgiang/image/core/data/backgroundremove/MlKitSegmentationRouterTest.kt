package com.thgiang.image.core.data.backgroundremove

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitSegmentationRouterTest {

    @Test
    fun maxWorkSideForPlan_blocked_returnsNull() {
        assertNull(MlKitSegmentationRouter.maxWorkSideForPlan(MlKitSegmentationRouter.Plan.BLOCKED, 1536))
    }

    @Test
    fun maxWorkSideForPlan_subject_usesSubjectBudget() {
        val side = MlKitSegmentationRouter.maxWorkSideForPlan(MlKitSegmentationRouter.Plan.SUBJECT, 1536)
        assertNotNull(side)
        assertTrue(side!! <= 1536)
    }

    @Test
    fun maxWorkSideForPlan_selfie_usesSelfieBudget() {
        val subject = MlKitSegmentationRouter.maxWorkSideForPlan(MlKitSegmentationRouter.Plan.SUBJECT, 1536)
        val selfie = MlKitSegmentationRouter.maxWorkSideForPlan(MlKitSegmentationRouter.Plan.SELFIE_PRIMARY, 1536)
        assertNotNull(subject)
        assertNotNull(selfie)
        assertTrue(selfie!! >= subject!!)
    }

    @Test
    fun selfieAllowsLargerWorkSizeThanSubject() {
        val side = 1536
        val subject = MlKitMemoryBudget.canRunSubjectSegmentation(side, side)
        val selfie = MlKitMemoryBudget.canRunSelfieSegmentation(side, side)
        if (!subject) {
            assertTrue(selfie)
        }
    }
}
