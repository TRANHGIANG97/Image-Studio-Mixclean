package com.thgiang.image.core.ad

import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.thgiang.image.core.ad.R

@Composable
fun SmallNativeAdView(
    nativeAd: NativeAd,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            val adView = LayoutInflater.from(context)
                .inflate(R.layout.layout_native_ad_small, null) as NativeAdView
            
            // Map views
            adView.headlineView = adView.findViewById(R.id.ad_headline)
            adView.bodyView = adView.findViewById(R.id.ad_body)
            adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
            adView.iconView = adView.findViewById(R.id.ad_app_icon)
            
            // Set data
            (adView.headlineView as? TextView)?.text = nativeAd.headline
            (adView.bodyView as? TextView)?.text = nativeAd.body
            (adView.callToActionView as? Button)?.text = nativeAd.callToAction
            
            if (nativeAd.icon != null) {
                (adView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
                adView.iconView?.visibility = android.view.View.VISIBLE
            } else {
                adView.iconView?.visibility = android.view.View.GONE
            }

            // Assign native ad object to the native ad view.
            adView.setNativeAd(nativeAd)
            
            adView
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
        update = { adView ->
            // Update content if ad object changes
            (adView.headlineView as? TextView)?.text = nativeAd.headline
            (adView.bodyView as? TextView)?.text = nativeAd.body
            (adView.callToActionView as? Button)?.text = nativeAd.callToAction
            if (nativeAd.icon != null) {
                (adView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
                adView.iconView?.visibility = android.view.View.VISIBLE
            }
            adView.setNativeAd(nativeAd)
        }
    )
}
