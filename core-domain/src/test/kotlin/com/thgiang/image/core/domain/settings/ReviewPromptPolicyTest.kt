package com.thgiang.image.core.domain.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptPolicyTest {

    @Test
    fun `shows on every fifth successful save`() {
        assertFalse(shouldShowReviewPromptAfterSave(1, false, false))
        assertFalse(shouldShowReviewPromptAfterSave(4, false, false))
        assertTrue(shouldShowReviewPromptAfterSave(5, false, false))
        assertFalse(shouldShowReviewPromptAfterSave(9, false, false))
        assertTrue(shouldShowReviewPromptAfterSave(10, false, false))
    }

    @Test
    fun `never shows when disabled or already reviewed`() {
        assertFalse(shouldShowReviewPromptAfterSave(5, promptDisabled = true, markedAsReviewed = false))
        assertFalse(shouldShowReviewPromptAfterSave(5, promptDisabled = false, markedAsReviewed = true))
    }
}
