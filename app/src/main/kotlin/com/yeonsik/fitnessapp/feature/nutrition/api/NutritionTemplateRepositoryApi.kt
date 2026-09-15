package com.yeonsik.fitnessapp.feature.nutrition.api

import com.yeonsik.fitnessapp.data.CompositionTemplate

/** Public owner-scoped boundary for reusable nutrition/menu definitions. */
interface NutritionTemplateRepositoryApi {
    fun findTemplate(templateId: String): CompositionTemplate?

    fun listTemplates(kind: String? = null): List<CompositionTemplate>

    fun saveTemplate(template: CompositionTemplate): String

    /** Removes only the current owner's mutable definition; consumed snapshots are untouched. */
    fun deleteTemplate(templateId: String)

    fun setUserId(userId: String) {}
}
