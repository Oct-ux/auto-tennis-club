package com.autotennisclub.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import com.autotennisclub.app.pusun.PusunBleConfig

class BleScanner(private val adapter: BluetoothAdapter) {
    private val scanner get() = adapter.bluetoothLeScanner
    private var callback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun start(onDevice: (BluetoothDevice) -> Unit) {
        stop()
        val filters = listOf(
            android.bluetooth.le.ScanFilter.Builder()
                .setServiceUuid(
                    android.os.ParcelUuid(PusunBleConfig.SERVICE_UUID)
                )
                .build()
        )
        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onDevice(result.device)
            }
        }
        scanner.startScan(
            filters,
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(),
            callback
        )
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        callback?.let { scanner.stopScan(it) }
        callback = null
    }
}