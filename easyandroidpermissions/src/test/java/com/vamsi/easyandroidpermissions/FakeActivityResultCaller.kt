package com.vamsi.easyandroidpermissions

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat

internal class FakeActivityResultCaller : ActivityResultCaller {

    private var singleCallback: ((Boolean) -> Unit)? = null
    private var multipleCallback: ((Map<String, Boolean>) -> Unit)? = null
    private var activityResultCallback: ((ActivityResult) -> Unit)? = null

    var lastSinglePermission: String? = null
        private set
    var lastMultiplePermissions: List<String> = emptyList()
        private set
    var lastLaunchedIntent: Intent? = null
        private set
    val unsupportedIntentActions = mutableSetOf<String>()

    override fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        callback: androidx.activity.result.ActivityResultCallback<O>
    ): ActivityResultLauncher<I> {
        return registerInternal(contract, callback)
    }

    override fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        registry: androidx.activity.result.ActivityResultRegistry,
        callback: androidx.activity.result.ActivityResultCallback<O>
    ): ActivityResultLauncher<I> {
        return registerInternal(contract, callback)
    }

    fun dispatchSingleResult(granted: Boolean) {
        singleCallback?.invoke(granted)
    }

    fun dispatchMultipleResult(results: Map<String, Boolean>) {
        multipleCallback?.invoke(results)
    }

    fun dispatchActivityResult(resultCode: Int = Activity.RESULT_OK, data: Intent? = null) {
        activityResultCallback?.invoke(ActivityResult(resultCode, data))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <I, O> registerInternal(
        contract: ActivityResultContract<I, O>,
        callback: androidx.activity.result.ActivityResultCallback<O>
    ): ActivityResultLauncher<I> {
        return when (contract) {
            is ActivityResultContracts.RequestPermission -> SingleLauncher(contract, callback as androidx.activity.result.ActivityResultCallback<Boolean>) as ActivityResultLauncher<I>
            is ActivityResultContracts.RequestMultiplePermissions -> MultiLauncher(contract, callback as androidx.activity.result.ActivityResultCallback<Map<String, Boolean>>) as ActivityResultLauncher<I>
            is ActivityResultContracts.StartActivityForResult -> IntentLauncher(contract, callback as androidx.activity.result.ActivityResultCallback<ActivityResult>) as ActivityResultLauncher<I>
            else -> error("Unsupported contract $contract")
        }
    }

    private inner class SingleLauncher(
        override val contract: ActivityResultContracts.RequestPermission,
        private val callback: androidx.activity.result.ActivityResultCallback<Boolean>
    ) : ActivityResultLauncher<String>() {

        override fun launch(input: String, options: ActivityOptionsCompat?) {
            lastSinglePermission = input
            singleCallback = { result -> callback.onActivityResult(result) }
        }

        override fun unregister() {
            singleCallback = null
        }
    }

    private inner class MultiLauncher(
        override val contract: ActivityResultContracts.RequestMultiplePermissions,
        private val callback: androidx.activity.result.ActivityResultCallback<Map<String, Boolean>>
    ) : ActivityResultLauncher<Array<String>>() {

        override fun launch(input: Array<String>, options: ActivityOptionsCompat?) {
            lastMultiplePermissions = input.toList()
            multipleCallback = { results -> callback.onActivityResult(results) }
        }

        override fun unregister() {
            multipleCallback = null
        }
    }

    private inner class IntentLauncher(
        override val contract: ActivityResultContracts.StartActivityForResult,
        private val callback: androidx.activity.result.ActivityResultCallback<ActivityResult>
    ) : ActivityResultLauncher<Intent>() {

        override fun launch(input: Intent, options: ActivityOptionsCompat?) {
            if (input.action in unsupportedIntentActions) {
                throw android.content.ActivityNotFoundException("No Activity found to handle ${input.action}")
            }
            lastLaunchedIntent = input
            activityResultCallback = { result -> callback.onActivityResult(result) }
        }

        override fun unregister() {
            activityResultCallback = null
        }
    }
}

