package com.vamsi.easyandroidpermissions

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

internal class TestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    init {
        registry.currentState = Lifecycle.State.STARTED
    }

    override val lifecycle: Lifecycle
        get() = registry

    fun handleDestroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

