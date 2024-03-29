package com.automate123.videshorts.screen.main

import androidx.lifecycle.ViewModel
import com.automate123.videshorts.service.ShortsController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val controller: ShortsController
) : ViewModel() {

    override fun onCleared() {
        controller.clear()
    }
}