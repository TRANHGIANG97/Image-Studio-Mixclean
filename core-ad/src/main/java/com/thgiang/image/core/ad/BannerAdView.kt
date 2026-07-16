package com.thgiang.image.core.ad

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Hiển thị Banner Ad bằng AndroidView trong Compose.
 * Không load khi user mới / chưa chọn ảnh / integrity fail / session đáng ngờ.
 */
@Composable
fun BannerAdView(
    modifier: Modifier = Modifier,
    adUnitId: String = "ca-app-pub-5559226591525834/2792367253" // Banner Home ID chính thức
) {
    val context = LocalContext.current
    if (NewUserAdPolicy.shouldBypassBanner(context)) {
        NewUserAdPolicy.reportBypassIfNeeded(context, "banner")
        return
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                setAdUnitId(adUnitId)
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
