package com.ussdadvanced

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

class UssdModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val TAG = "UssdModule"
        private var isSessionActive: Boolean = false
    }

    private val ussdApi: USSDApi = USSDController

    override fun getName(): String = "UssdModule"

    private fun sendEvent(eventName: String, params: WritableMap?) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    @ReactMethod
    fun addListener(eventName: String) {
    }

    @ReactMethod
    fun removeListeners(count: Int) {
    }

    @ReactMethod
    fun isAccessibilityEnabled(promise: Promise) {
        try {
            promise.resolve(isAccessibilityServiceEnabled(reactContext))
        } catch (e: Exception) {
            promise.reject("ERROR", e.message)
        }
    }

    @ReactMethod
    fun getDeviceInfo(promise: Promise) {
        try {
            val deviceInfo = Arguments.createMap().apply {
                putString("manufacturer", Build.MANUFACTURER)
                putString("brand", Build.BRAND)
                putString("model", Build.MODEL)
                val manufacturer = Build.MANUFACTURER.lowercase()
                val isMIUI = manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")
                putBoolean("isMIUI", isMIUI)
            }
            promise.resolve(deviceInfo)
        } catch (e: Exception) {
            promise.reject("ERROR", e.message)
        }
    }

    @ReactMethod
    fun openAccessibilitySettings(promise: Promise) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            reactContext.startActivity(intent)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("ERROR", e.message)
        }
    }

    @ReactMethod
    fun sendUssd(code: String, subscriptionId: Int, promise: Promise) {
        try {
            defaultUssdService(code, subscriptionId)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("ERROR", e.message)
        }
    }

    @ReactMethod
    fun sendAdvancedUssd(code: String, subscriptionId: Int, promise: Promise) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            singleSessionUssd(code, subscriptionId, promise)
        } else {
            sendUssd(code, subscriptionId, promise)
        }
    }

    @ReactMethod
    fun multisessionUssd(code: String, subscriptionId: Int, promise: Promise) {
        val activity = reactContext.currentActivity
        if (activity == null) {
            promise.reject("ERROR", "Activity is null")
            return
        }

        if (!isAccessibilityServiceEnabled(reactContext)) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            reactContext.startActivity(intent)
            promise.reject("ACCESSIBILITY_NOT_ENABLED", "Please enable accessibility service")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val slot = if (subscriptionId == -1) 0 else subscriptionId

            ussdApi.callUSSDInvoke(activity, code, slot, object : USSDController.CallbackInvoke {
                override fun responseInvoke(ev: AccessibilityEvent) {
                    isSessionActive = true

                    try {
                        if (ev.text.isNotEmpty()) {
                            val response = ev.text.joinToString("\n")

                            val params = Arguments.createMap().apply {
                                putString("message", response)
                                putBoolean("sessionActive", true)
                            }
                            sendEvent("UssdResponse", params)

                            promise.resolve(response)
                        } else {
                            promise.resolve(null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in responseInvoke", e)
                    }
                }

                override fun over(message: String) {
                    isSessionActive = false
                    try {
                        val params = Arguments.createMap().apply {
                            putString("message", message)
                            putBoolean("sessionActive", false)
                        }
                        sendEvent("UssdSessionEnd", params)
                        promise.resolve(message)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in over", e)
                    }
                }
            })
        } else {
            sendUssd(code, subscriptionId, promise)
        }
    }

    @ReactMethod
    fun sendUssdResponse(text: String, promise: Promise) {
        if (!isSessionActive) {
            promise.reject("ERROR", "No active USSD session")
            return
        }

        try {
            var responseReceived = false

            Handler(Looper.getMainLooper()).postDelayed({
                if (!responseReceived) {
                    Log.w(TAG, "USSD response timeout")
                    promise.reject("TIMEOUT", "USSD response timeout")
                }
            }, 5000)

            USSDController.send(text) { ev ->
                responseReceived = true
                try {
                    if (ev.text.isNotEmpty()) {
                        val response = ev.text.joinToString("\n")

                        val params = Arguments.createMap().apply {
                            putString("message", response)
                            putBoolean("sessionActive", true)
                        }
                        sendEvent("UssdResponse", params)

                        promise.resolve(response)
                    } else {
                        promise.resolve(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing USSD response", e)
                    promise.reject("ERROR", e.message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending USSD response", e)
            promise.reject("ERROR", e.message)
        }
    }

    @ReactMethod
    fun cancelUssdSession(promise: Promise) {
        try {
            if (isSessionActive) {
                ussdApi.cancel()
                isSessionActive = false
            }
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("ERROR", e.message)
        }
    }

    private fun singleSessionUssd(ussdCode: String, subscriptionId: Int, promise: Promise) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val activity = reactContext.currentActivity
            if (activity == null) {
                promise.reject("ERROR", "Activity is null")
                return
            }

            if (ContextCompat.checkSelfPermission(
                    reactContext,
                    android.Manifest.permission.CALL_PHONE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.CALL_PHONE),
                    2
                )
                promise.reject("PERMISSION_DENIED", "Call phone permission required")
                return
            }

            val tm = reactContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val useDefault = subscriptionId == -1
            val simManager = if (useDefault) tm else tm.createForSubscriptionId(subscriptionId)

            val callback = object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(
                    telephonyManager: TelephonyManager,
                    request: String,
                    response: CharSequence
                ) {
                    promise.resolve(response.toString())
                }

                override fun onReceiveUssdResponseFailed(
                    telephonyManager: TelephonyManager,
                    request: String,
                    failureCode: Int
                ) {
                    val errorMsg = when (failureCode) {
                        TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> "USSD_ERROR_SERVICE_UNAVAIL"
                        TelephonyManager.USSD_RETURN_FAILURE -> "USSD_RETURN_FAILURE"
                        else -> "Unknown error: $failureCode"
                    }
                    promise.reject("USSD_ERROR", errorMsg)
                }
            }

            try {
                if (useDefault) {
                    tm.sendUssdRequest(ussdCode, callback, Handler(Looper.getMainLooper()))
                } else {
                    simManager.sendUssdRequest(ussdCode, callback, Handler(Looper.getMainLooper()))
                }
            } catch (e: SecurityException) {
                promise.reject("SECURITY_ERROR", e.message)
            }
        } else {
            defaultUssdService(ussdCode, subscriptionId)
            promise.resolve(null)
        }
    }

    private val simSlotName = arrayOf(
        "extra_asus_dial_use_dualsim",
        "com.android.phone.extra.slot",
        "slot",
        "simslot",
        "sim_slot",
        "subscription",
        "Subscription",
        "phone",
        "com.android.phone.DialingMode",
        "simSlot",
        "slot_id",
        "simId",
        "simnum",
        "phone_type",
        "slotId",
        "slotIdx"
    )

    private fun defaultUssdService(ussdCode: String, subscriptionId: Int) {
        val activity = reactContext.currentActivity ?: return

        if (ContextCompat.checkSelfPermission(
                reactContext,
                android.Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(android.Manifest.permission.CALL_PHONE),
                2
            )
            return
        }

        val useDefault = subscriptionId == -1
        val sim = subscriptionId - 1
        var number = ussdCode.replace("#", "%23")
        if (!number.startsWith("tel:")) {
            number = "tel:$number"
        }

        val intent = Intent(
            if (isTelephonyEnabled()) Intent.ACTION_CALL else Intent.ACTION_VIEW
        )
        intent.data = Uri.parse(number)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (!useDefault) {
            intent.putExtra("com.android.phone.force.slot", true)
            intent.putExtra("Cdma_Supp", true)

            for (s in simSlotName) {
                intent.putExtra(s, sim)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(
                        reactContext,
                        android.Manifest.permission.READ_PHONE_STATE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(android.Manifest.permission.READ_PHONE_STATE),
                        2
                    )
                }
                val telecomManager =
                    reactContext.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                val phoneAccountHandleList = telecomManager.callCapablePhoneAccounts
                if (phoneAccountHandleList != null && phoneAccountHandleList.isNotEmpty() && sim >= 0 && sim < phoneAccountHandleList.size) {
                    intent.putExtra(
                        "android.telecom.extra.PHONE_ACCOUNT_HANDLE",
                        phoneAccountHandleList[sim]
                    )
                }
            }
        }

        reactContext.startActivity(intent)
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        try {
            val packageName = context.packageName
            val serviceName = "USSDService"
            
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            var foundInSettings = false
            if (enabledServices != null) {
                if (enabledServices.contains(packageName) && enabledServices.contains(serviceName)) {
                    foundInSettings = true
                }
            }

            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val services = am.getEnabledAccessibilityServiceList(AccessibilityEvent.TYPES_ALL_MASK)

            var foundInManager = false
            for (service in services) {
                val id = service.id
                if (id.contains(packageName) && id.contains(serviceName)) {
                    foundInManager = true
                    break
                }
            }

            val accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1

            val manufacturer = Build.MANUFACTURER.lowercase()
            val isMIUI = manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")
            
            if (isMIUI) {
                return (foundInSettings || foundInManager) && accessibilityEnabled
            }

            return foundInSettings && foundInManager && accessibilityEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service", e)
            return false
        }
    }

    private fun isTelephonyEnabled(): Boolean {
        val tm = reactContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return tm.phoneType != TelephonyManager.PHONE_TYPE_NONE
    }
}
