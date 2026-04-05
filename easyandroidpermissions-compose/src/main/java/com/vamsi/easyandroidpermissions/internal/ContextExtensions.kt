package com.vamsi.easyandroidpermissions.internal

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity

internal tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

