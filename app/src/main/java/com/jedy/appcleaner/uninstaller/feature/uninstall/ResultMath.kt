package com.jedy.appcleaner.uninstaller.feature.uninstall

import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.model.UnusedApp
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The Result screen's gauge: how full the phone was before this batch and how full it is now.
 * Fractions of total storage, 0..1.
 */
data class StorageDrop(val beforeFraction: Float, val afterFraction: Float) {
    /** "Your phone is now 68% full". */
    val afterPercent: Int get() = ResultMath.percent(afterFraction)
}

/**
 * Pure numbers behind the celebration, kept out of Compose so they are unit-tested. The brief's
 * rule is "never invent numbers": every value here derives from the device's own storage reading
 * and the verified freed total.
 */
object ResultMath {

    /**
     * "Before" is reconstructed as the current used space plus what this batch freed, because by
     * the time the Result screen exists the space is already back. Null when the volume reports
     * no total (a broken StatFs), so the screen shows no gauge rather than a made-up one.
     */
    fun storageDrop(device: DeviceStorage, freedBytes: Long): StorageDrop? {
        val total = device.totalBytes
        if (total <= 0) return null
        val after = device.usedBytes.coerceIn(0, total)
        val before = (after + freedBytes.coerceAtLeast(0)).coerceAtMost(total)
        return StorageDrop(
            beforeFraction = (before.toDouble() / total).toFloat(),
            afterFraction = (after.toDouble() / total).toFloat(),
        )
    }

    fun percent(fraction: Float): Int = (fraction * 100).roundToInt().coerceIn(0, 100)

    /**
     * Android's short size formatter moves to the next unit above 900, so 970 MB prints as
     * "0.97 GB" (design review §6). For sizes it would show as "0.9x GB", this returns the whole
     * megabytes to print instead; null means the platform formatter's output is fine. A size that
     * rounds to 1,000 MB stays "1.0 GB".
     */
    fun subGigabyteMegabytes(bytes: Long): Long? {
        if (bytes <= 900_000_000L) return null
        val mb = (bytes / 1_000_000.0).roundToLong()
        return mb.takeIf { it < 1_000 }
    }

    /**
     * The loss-framed teaser ("9 more apps you haven't opened are still using 1.3 GB"). Uses the
     * measured size when [fullSizes] is available (premium with Usage Access — the same rule as the
     * Confirm Sheet), else the APK size, and then says "about".
     */
    fun unusedTeaser(unused: List<UnusedApp>, fullSizes: Map<String, AppSize>?, thresholdDays: Int): UnusedTeaser {
        var estimate = false
        val bytes = unused.sumOf { entry ->
            val full = fullSizes?.get(entry.app.packageName)?.totalBytes
            if (full == null) estimate = true
            full ?: entry.app.apkBytes
        }
        return UnusedTeaser(
            count = unused.size,
            thresholdDays = thresholdDays,
            bytes = bytes,
            isEstimate = estimate || unused.isEmpty(),
        )
    }
}
