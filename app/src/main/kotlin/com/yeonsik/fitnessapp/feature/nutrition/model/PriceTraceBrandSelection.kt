package com.yeonsik.fitnessapp.feature.nutrition.model

/** 외부 브랜드 ID만 identity다. 이름과 직접 parent는 선택 당시의 표시 snapshot이다. */
data class PriceTraceBrandSelection @JvmOverloads constructor(
    val brandId: String,
    val canonicalName: String,
    val parentBrandId: String? = null,
    val parentCanonicalName: String? = null
) {
    init {
        require(UUID_PATTERN.matches(brandId)) { "Invalid PriceTrace Brand UUID" }
        require(canonicalName.isNotBlank()) { "PriceTrace Brand name is required" }
        require(parentBrandId == null || UUID_PATTERN.matches(parentBrandId)) { "Invalid parent Brand UUID" }
        require(parentCanonicalName == null || (parentBrandId != null && parentCanonicalName.isNotBlank())) {
            "Parent name requires a parent Brand ID"
        }
    }

    companion object {
        private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        @JvmStatic
        fun fromSnapshot(id: String?, name: String?, parentId: String?, parentName: String?): PriceTraceBrandSelection? {
            if (id == null && name == null && parentId == null && parentName == null) return null
            require(id != null && name != null) { "Incomplete PriceTrace Brand snapshot" }
            return PriceTraceBrandSelection(id, name, parentId, parentName)
        }
    }
}
