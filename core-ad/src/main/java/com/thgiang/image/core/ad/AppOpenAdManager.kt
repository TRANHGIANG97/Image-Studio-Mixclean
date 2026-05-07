package com.thgiang.image.core.ad

import android.app.Activity

/** Interface cho App Open Ad — cho phép mock trong test. */
interface AppOpenAdManager {
    var isPremiumUser: Boolean
    fun showAdIfAvailable(activity: Activity)
    fun wasAdDismissedRecently(): Boolean
}
