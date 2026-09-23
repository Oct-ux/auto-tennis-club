package com.autotennisclub.app.kiosk

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Device admin component; `dpm set-device-owner` points at it. */
class KioskAdminReceiver : DeviceAdminReceiver()

enum class KioskStatus { NOT_SET_UP, LOCKED, UNLOCKED }

/**
 * Kiosk mode for the station tablet. Only active when the app is device owner
 * (set once per tablet with adb, see docs). On a development device nothing changes.
 */
object Kiosk {
    private const val HOME_ALIAS = "com.autotennisclub.app.KioskHome"

    fun isDeviceOwner(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(context.packageName)

    fun status(context: Context): KioskStatus {
        if (!isDeviceOwner(context)) return KioskStatus.NOT_SET_UP
        val locked = context.getSystemService(ActivityManager::class.java).lockTaskModeState !=
            ActivityManager.LOCK_TASK_MODE_NONE
        return if (locked) KioskStatus.LOCKED else KioskStatus.UNLOCKED
    }

    /**
     * One-time device policy setup, safe to repeat on every launch:
     * only this app may run, it is the home screen (so it opens after a reboot or
     * crash) and Bluetooth permissions are granted without a dialog.
     */
    fun configure(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(context, KioskAdminReceiver::class.java)

        dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
        }

        val home = ComponentName(context.packageName, HOME_ALIAS)
        context.packageManager.setComponentEnabledSetting(
            home, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
        )
        val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(admin, homeFilter, home)

        // Plugged in 24/7: keep the screen on while charging.
        dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "7")

        bluetoothPermissions().forEach { permission ->
            dpm.setPermissionGrantState(
                admin, context.packageName, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )
        }
    }

    /** Locks the screen to the app. Called on every resume, so leaving Settings re-locks it. */
    fun lock(activity: Activity) {
        if (status(activity) == KioskStatus.UNLOCKED) activity.startLockTask()
    }

    /** Operator: unlock and open Android settings. Coming back to the app locks again. */
    fun openAndroidSettings(activity: Activity) {
        if (status(activity) == KioskStatus.LOCKED) activity.stopLockTask()
        activity.startActivity(Intent(Settings.ACTION_SETTINGS))
    }

    /** Development only: gives the tablet back its normal launcher and removes device owner. */
    @Suppress("DEPRECATION")
    fun remove(activity: Activity) {
        if (!isDeviceOwner(activity)) return
        val dpm = activity.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(activity, KioskAdminReceiver::class.java)
        if (status(activity) == KioskStatus.LOCKED) activity.stopLockTask()
        dpm.clearPackagePersistentPreferredActivities(admin, activity.packageName)
        activity.packageManager.setComponentEnabledSetting(
            ComponentName(activity.packageName, HOME_ALIAS),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        dpm.clearDeviceOwnerApp(activity.packageName)
    }
}

/** Runtime permissions the real MAX B connection needs. */
fun bluetoothPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
