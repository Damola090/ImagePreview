package com.example.image_preview.enrichment

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.DisplayMetrics
import com.example.image_preview.domain.model.DeviceInfo
import java.util.Locale

/**
 * Automatically enriches every event with device + app context.
 * The developer using your SDK never has to think about this.
 */
class DeviceInfoProvider(private val context: Context) {

    private val appContext = context.applicationContext

    // Cache this since it never changes during the app session
    private val cachedDeviceInfo: DeviceInfo by lazy { buildDeviceInfo() }

    fun get(): DeviceInfo = cachedDeviceInfo.copy(
        // Network type CAN change so we fetch it fresh each time
        networkType = getNetworkType()
    )

    private fun buildDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            appVersion = getAppVersion(),
            appPackage = appContext.packageName,
            networkType = getNetworkType(),
            screenDensity = getScreenDensity(),
            locale = Locale.getDefault().toLanguageTag()
        )
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }
    }

    private fun getNetworkType(): String {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "none"
        val capabilities = cm.getNetworkCapabilities(network) ?: return "none"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }

    private fun getScreenDensity(): String {
        return when (appContext.resources.displayMetrics.densityDpi) {
            DisplayMetrics.DENSITY_LOW    -> "ldpi"
            DisplayMetrics.DENSITY_MEDIUM -> "mdpi"
            DisplayMetrics.DENSITY_HIGH   -> "hdpi"
            DisplayMetrics.DENSITY_XHIGH  -> "xhdpi"
            DisplayMetrics.DENSITY_XXHIGH -> "xxhdpi"
            DisplayMetrics.DENSITY_XXXHIGH -> "xxxhdpi"
            else -> "unknown"
        }
    }
}
