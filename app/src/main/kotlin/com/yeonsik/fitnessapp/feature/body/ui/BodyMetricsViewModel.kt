package com.yeonsik.fitnessapp.feature.body.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.body.application.BodyMetricsApplicationService
import com.yeonsik.fitness.shared.feature.body.ui.BodyMetricsEditorController

class BodyMetricsViewModel(service: BodyMetricsApplicationService) : ViewModel() {
    val editor = BodyMetricsEditorController(service, viewModelScope)

    fun open(scope: AccountScope, date: String, recordId: String?) = editor.open(scope, date, recordId)

    fun dismissEditor() = editor.dismiss()
}
