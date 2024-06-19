package org.samo_lego.canta.util

import android.content.pm.PackageManager
import android.content.pm.PackageInfo

fun PackageManager.getInfoForPackage(packageName: String): PackageInfo {
    return getPackageInfo(packageName, 0)
}
