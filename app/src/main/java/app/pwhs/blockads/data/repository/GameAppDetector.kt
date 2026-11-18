package app.pwhs.blockads.data.repository

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Finds installed apps that declare themselves as games, so Game Mode can be
 * suggested for them. Uses the Play Store category (Android 8+) and the older
 * isGame manifest flag.
 */
class GameAppDetector(
    private val installedApps: () -> List<ApplicationInfo>
) {
    constructor(packageManager: PackageManager) : this({ packageManager.getInstalledApplications(0) })

    fun installedGamePackages(): List<String> =
        installedApps()
            .filter { isGame(it) }
            .map { it.packageName }
            .distinct()
            .sorted()

    companion object {
        fun isGame(info: ApplicationInfo): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                info.category == ApplicationInfo.CATEGORY_GAME
            ) return true
            @Suppress("DEPRECATION")
            return info.flags and ApplicationInfo.FLAG_IS_GAME != 0
        }
    }
}
