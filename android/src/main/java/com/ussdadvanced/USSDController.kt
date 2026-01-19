package com.ussdadvanced

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi

val mapM = hashMapOf(
    "KEY_LOGIN" to listOf("espere", "waiting", "loading", "esperando"),
    "KEY_ERROR" to listOf("problema", "problem", "error", "null")
)

@SuppressLint("StaticFieldLeak")
object USSDController : USSDInterface, USSDApi {

    internal const val KEY_LOGIN = "KEY_LOGIN"
    internal const val KEY_ERROR = "KEY_ERROR"

    private val simSlotName = arrayOf(
        "extra_asus_dial_use_dualsim",
        "com.android.phone.extra.slot", "slot", "simslot", "sim_slot", "subscription",
        "Subscription", "phone", "com.android.phone.DialingMode", "simSlot", "slot_id",
        "simId", "simnum", "phone_type", "slotId", "slotIdx"
    )

    lateinit var context: Context
        private set

    var map: HashMap<String, List<String>> = mapM
        private set

    lateinit var callbackInvoke: CallbackInvoke

    var callbackMessage: ((AccessibilityEvent) -> Unit)? = null
        private set

    var isRunning: Boolean? = false
        private set

    var sendType: Boolean? = false
        private set

    private var ussdInterface: USSDInterface? = null

    init {
        ussdInterface = this
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun callUSSDInvoke(
        context: Context, ussdPhoneNumber: String,
        callbackInvoke: CallbackInvoke
    ) {
        this.context = context
        callUSSDInvoke(this.context, ussdPhoneNumber, 0, callbackInvoke)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("MissingPermission")
    override fun callUSSDInvoke(
        context: Context, ussdPhoneNumber: String, simSlot: Int,
        callbackInvoke: CallbackInvoke
    ) {
        sendType = false
        this.context = context
        this.callbackInvoke = callbackInvoke
        if (verifyAccessibilityAccess(this.context)) {
            dialUp(ussdPhoneNumber, simSlot)
        } else {
            this.callbackInvoke.over("Check your accessibility")
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun dialUp(ussdPhoneNumber: String, simSlot: Int) {
        when {
            !map.containsKey(KEY_LOGIN) || !map.containsKey(KEY_ERROR) ->
                callbackInvoke.over("Bad Mapping structure")
            ussdPhoneNumber.isEmpty() -> callbackInvoke.over("Bad ussd number")
            else -> {
                val phone = Uri.encode("#")?.let {
                    ussdPhoneNumber.replace("#", it)
                }
                isRunning = true
                context.startActivity(getActionCallIntent(Uri.parse("tel:$phone"), simSlot))
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("MissingPermission")
    private fun getActionCallIntent(uri: Uri?, simSlot: Int): Intent {
        val telcomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        return Intent(Intent.ACTION_CALL, uri).apply {
            simSlotName.map { sim -> putExtra(sim, simSlot) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("com.android.phone.force.slot", true)
            putExtra("Cdma_Supp", true)
            telcomManager?.callCapablePhoneAccounts?.let { handles ->
                if (handles.size > simSlot)
                    putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", handles[simSlot])
            }
        }
    }

    override fun sendData(text: String) = USSDService.send(text)
    override fun sendData2(text: String, event: AccessibilityEvent) = USSDService.send2(text, event)

    override fun stopRunning() {
        isRunning = false
    }

    override fun send(text: String, callbackMessage: (AccessibilityEvent) -> Unit) {
        this.callbackMessage = callbackMessage
        sendType = true
        ussdInterface?.sendData(text)
    }

    override fun send2(
        text: String,
        event: AccessibilityEvent,
        callbackMessage: (AccessibilityEvent) -> Unit
    ) {
        this.callbackMessage = callbackMessage
        sendType = true
        ussdInterface?.sendData2(text, event)
    }

    override fun cancel() = USSDService.cancel()
    override fun cancel2(event: AccessibilityEvent) = USSDService.cancel2(event)

    interface CallbackInvoke {
        fun responseInvoke(event: AccessibilityEvent)
        fun over(message: String)
    }

    override fun verifyAccessibilityAccess(context: Context): Boolean =
        isAccessibilityServicesEnable(context).also {
            if (!it) openSettingsAccessibility(context as Activity)
        }

    private fun openSettingsAccessibility(activity: Activity) {
        activity.startActivityForResult(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), 1)
    }

    private fun isAccessibilityServicesEnable(context: Context): Boolean {
        try {
            val accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1

            if (!accessibilityEnabled) return false

            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return enabledServices.contains(context.packageName) &&
                   enabledServices.contains("USSDService")
        } catch (e: Exception) {
            return false
        }
    }
}
