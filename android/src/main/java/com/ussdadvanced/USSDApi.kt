package com.ussdadvanced

import android.content.Context
import android.view.accessibility.AccessibilityEvent

interface USSDApi {
    fun send(text: String, callbackMessage: (AccessibilityEvent) -> Unit)
    fun send2(text: String, event: AccessibilityEvent, callbackMessage: (AccessibilityEvent) -> Unit)
    fun cancel()
    fun cancel2(event: AccessibilityEvent)
    fun callUSSDInvoke(context: Context, ussdPhoneNumber: String, callbackInvoke: USSDController.CallbackInvoke)
    fun callUSSDInvoke(context: Context, ussdPhoneNumber: String, simSlot: Int, callbackInvoke: USSDController.CallbackInvoke)
    fun verifyAccessibilityAccess(context: Context): Boolean
}
