package com.yeonsik.fitness.shared.core.ui

/** A bottom-tab tap selects its root. Deep destinations belong to the selected host. */
enum class ProductTab(val id: String, val label: String) {
    HOME("home", "메인"),
    FITNESS("fitness", "피트니스"),
    RECORDS("records", "기록"),
    SETTINGS("settings", "설정");

    fun adjacent(forward: Boolean): ProductTab? = entries.getOrNull(ordinal + if (forward) 1 else -1)
    fun needsRootChange(currentRoot: ProductTab?): Boolean = this != currentRoot

    companion object {
        val ordered: List<ProductTab> = entries
        fun fromId(id: String): ProductTab? = entries.firstOrNull { it.id == id }
    }
}
