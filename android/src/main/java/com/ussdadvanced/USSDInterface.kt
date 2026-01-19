package com.ussdadvanced

import android.view.accessibility.AccessibilityEvent

interface USSDInterface {
    fun sendData(text: String)
    fun sendData2(text: String, event: AccessibilityEvent)
    fun stopRunning()
}
