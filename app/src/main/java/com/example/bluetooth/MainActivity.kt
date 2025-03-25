package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import com.example.bluetooth.R
import java.util.UUID


class MainActivity : ComponentActivity() {

    companion object {
        const val NAME: String = "MyBluetoothService"//An arbitrary name
        val MY_UUID: UUID =
            UUID.fromString("3c8ffb84-f3d3-4b6b-a0d9-0f1d9b0bb010")//An Arbitrary random UUID
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    val devices: ArrayList<BluetoothDevice> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.main_layout)
        bluetoothAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val scanButton = findViewById<Button>(R.id.scan_button)

        packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HEART_RATE_ECG)

        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            scanButton.isEnabled = false
            scanButton.text = getString(R.string.not_supported)
            Toast.makeText(this, "Your device doesn't support bluetooth", Toast.LENGTH_SHORT).show()

        } else {

            // Device supports Bluetooth
            scanButton.isEnabled = true
            scanButton.setOnClickListener { onClickScanButton() }

            null.setDiscoverable()
        }

    }

    private fun Int?.setDiscoverable() {
        val discoverableIntent: Intent =
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, this@setDiscoverable ?: 300)
            }

        setDiscoverable.launch(discoverableIntent)
    }

    private val setDiscoverable = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    private fun onClickScanButton() {
        val oldSize = devices.size
        devices.clear()

        // Different permission management depending on android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestMultiplePermissions.launch(
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                )
            )
        } else {
            requestEnableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    //Multiple requests for android 12+
    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions["android.permission.BLUETOOTH_SCAN"] == true && permissions["android.permission.BLUETOOTH_CONNECT"] == true) {
                //Bluetooth permission granted
                scanIfEnabled()

            } else {
                //Bluetooth permission still not granted
                Toast.makeText(this, "Can't scan until permission is granted", Toast.LENGTH_LONG)
                    .show()
            }
        }

    //Request for old android versions
    private val requestEnableBluetooth =
        this.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                //Bluetooth permission granted
                scanIfEnabled()
            } else {
                //Bluetooth permission still not granted
                Toast.makeText(this, "Can't scan until permission is granted", Toast.LENGTH_LONG)
                    .show()
            }
        }

    private fun scanIfEnabled() {
        if (bluetoothAdapter!!.isEnabled) { //Check if bluetooth is enabled by user (always true on Android 6)
            //Permission granted and Bluetooth enabled
            scan()
        } else {
            //Permission granted but Bluetooth disabled
            Toast.makeText(this, "Can't scan until bluetooth is enabled", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun scan() {

        val discoveryStarted = bluetoothAdapter!!.startDiscovery()
        Log.d("Scan", "Discovery started -> $discoveryStarted")

        if (discoveryStarted) {
            registerReceiver(scanReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
            registerReceiver(scanReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED))
            registerReceiver(scanReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
        } else {
            //Depending on the android version, we might need location permission
            //This is because scanning for bluetooth devices might give information of your own location
            requestLocationPermission()
        }

    }

    private fun requestLocationPermission() {
        requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission granted
                Toast.makeText(
                    this, "Location permission granted, make sure it is enabled", Toast.LENGTH_SHORT
                ).show()
            } else {
                // Permission denied
                Toast.makeText(
                    this,
                    "Unfortunately, you need location permission to scan bluetooth devices",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }


    // Create a BroadcastReceiver for ACTION_FOUND.
    private val scanReceiver = object : BroadcastReceiver() {

        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (val action: String? = intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.

                    val device: BluetoothDevice? = BundleCompat.getParcelable(
                        intent.extras!!, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                    )

                    device?.let {

                        device.name?.let { Log.d("scanReceiver", device.name) }
                        device.address?.let { Log.d("scanReceiver", device.address) }
                        if (!devices.any { it.address == device.address }) {
                            devices.add(device)
                        }
                    }

                }

                else -> {
                    action?.let { Log.d("scanReceiver", action) }
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()

        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(scanReceiver)
    }

}
