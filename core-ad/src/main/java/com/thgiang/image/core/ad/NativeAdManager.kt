package com.thgiang.image.core.ad

import com.google.android.gms.ads.nativead.NativeAd

interface NativeAdManager {
    var isPremiumUser: Boolean
    fun loadNativeAd(context: android.content.Context, onLoaded: (com.google.android.gms.ads.nativead.NativeAd) -> Unit, onFailed: (String) -> Unit)
}
