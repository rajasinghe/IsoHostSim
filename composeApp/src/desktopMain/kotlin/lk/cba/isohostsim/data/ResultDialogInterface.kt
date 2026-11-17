package lk.cba.isohostsim.data

import androidx.compose.runtime.Composable

interface ResultDialogInterface {
    fun onError(item: @Composable () -> Unit)
    fun onSuccess(item: @Composable () -> Unit)
}