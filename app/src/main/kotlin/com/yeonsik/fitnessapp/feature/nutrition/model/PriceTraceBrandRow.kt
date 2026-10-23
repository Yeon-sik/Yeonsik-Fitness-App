package com.yeonsik.fitnessapp.feature.nutrition.model

/** 서버 순서를 유지하는 검색 결과. 별칭은 매칭 근거이며 별도 Brand identity가 아니다. */
data class PriceTraceBrandRow(
    val selection: PriceTraceBrandSelection,
    val matchedName: String,
    val matchKind: String,
    val logoUrl: String?,
    val officialSiteUrl: String?
)
