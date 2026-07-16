package com.thgiang.image.core.ad

/**
 * Cấu hình quảng cáo tập trung — dễ thay đổi theo BuildVariant (Free/Premium).
 */
data class AdConfig(
    val rewardedAdsEnabled: Boolean = false,
    val interstitialAdUnitId: String = "ca-app-pub-5559226591525834/3123553059",
    val appOpenAdUnitId: String = "ca-app-pub-5559226591525834/5558144705",
    val rewardedAdUnitId: String = "ca-app-pub-5559226591525834/1096155511",
    val bannerAdUnitId: String = "ca-app-pub-5559226591525834/2792367253",
    val nativeAdUnitId: String = "ca-app-pub-5559226591525834/2635678123",
    val interstitialCooldownMs: Long = 10_000L,
    val appOpenCooldownMs: Long = 180_000L,
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 2000L
)
