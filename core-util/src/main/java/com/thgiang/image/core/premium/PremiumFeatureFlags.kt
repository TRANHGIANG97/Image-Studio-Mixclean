package com.thgiang.image.core.premium

/**
 * Master switch for Premium / IAP monetization.
 *
 * Keep all premium/billing code in the project, but when [enabled] is false:
 * - Do not connect to Play Billing (avoids ProxyBillingActivity noise)
 * - Do not show PremiumScreen / Pro upgrade CTAs / premium-limit paywalls
 * - Do not show PRO badges on templates
 * - Premium templates open freely (no 3/day gate)
 * - Pro quality selection is unlocked without paywall
 *
 * Flip to `true` to re-enable monetization later.
 */
object PremiumFeatureFlags {
    const val enabled: Boolean = false
}
