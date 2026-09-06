package com.yeonsik.fitnessapp.app

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner

class SavedStateViewModelFactory<T : ViewModel>(
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle? = null,
    private val creator: (SavedStateHandle) -> T
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
    @Suppress("UNCHECKED_CAST")
    override fun <V : ViewModel> create(
        key: String,
        modelClass: Class<V>,
        handle: SavedStateHandle
    ): V = creator(handle) as V
}
