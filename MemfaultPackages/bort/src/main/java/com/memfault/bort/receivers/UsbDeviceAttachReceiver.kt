package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.memfault.bort.reporting.Reporting
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UsbDeviceAttachReceiver : BortEnabledFilteringReceiver(
    actions = setOf(
        UsbManager.ACTION_USB_DEVICE_ATTACHED,
        UsbManager.ACTION_USB_DEVICE_DETACHED,
        UsbManager.ACTION_USB_ACCESSORY_ATTACHED,
        UsbManager.ACTION_USB_ACCESSORY_DETACHED,
    ),
) {
    override fun onReceivedAndEnabled(context: Context, intent: Intent, action: String) {
        when (action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED,
            UsbManager.ACTION_USB_DEVICE_DETACHED,
            -> reportUsbDevices(context)
            UsbManager.ACTION_USB_ACCESSORY_ATTACHED,
            UsbManager.ACTION_USB_ACCESSORY_DETACHED,
            -> reportUsbAccessories(context)
        }
    }

    private fun reportUsbDevices(context: Context) {
        val devices = (context.getSystemService(Context.USB_SERVICE) as UsbManager?)
            ?.deviceList
            ?.values
            ?: listOf()
        Reporting.report()
            .event(
                name = "usb.devices",
                latestInReport = true,
            )
            .add(devices.toString())
    }

    private fun reportUsbAccessories(context: Context) {
        val accessories = (context.getSystemService(Context.USB_SERVICE) as UsbManager?)
            ?.accessoryList // accessoryList is actually an array
            ?.toList()
            ?: listOf()
        Reporting.report()
            .event(
                name = "usb.accessories",
                latestInReport = true,
            )
            .add(accessories.toString())
    }
}
