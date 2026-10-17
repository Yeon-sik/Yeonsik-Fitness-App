package com.yeonsik.fitnessapp.feature.development.application

import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentAdvice
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentAdviceAction
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionAnalysisReport
import java.util.Locale

/** Reads intake snapshots only. A complete nutrient value never proves a complete day of eating. */
internal object DevelopmentNutritionAdvisor {
    fun advise(
        report: NutritionAnalysisReport,
        recentWeightKg: Double?,
        resistanceSessions: Int
    ): List<DevelopmentAdvice> {
        val evidence = listOf(
            "${report.startDate}–${report.endDate} · 기록일 ${report.recordedDays}일 · 식사 ${report.recordedMealCount}건",
            "기록일 평균은 기록된 식사만 합산한 값이에요. 하루 식사가 모두 입력되었다는 뜻은 아니에요.",
            "추정 영양값과 미확인 영양값을 구분하며, 입력하지 않은 식사는 0으로 계산하지 않아요."
        )
        if (report.recordedMealCount == 0) return listOf(DevelopmentAdvice(
            "nutrition-start", "식사 기록에서 보완점을 찾아드려요",
            "식사를 기록하면 단백질과 식사 구성을 함께 살펴볼 수 있어요.", evidence,
            DevelopmentAdviceAction.MEALS, "식사 기록하기"))

        val advice = mutableListOf<DevelopmentAdvice>()
        val protein = report.metric(NutritionProfile.PROTEIN_GRAMS)
        val estimated = protein?.estimatedCount?.let { it > 0 } == true
        val quality = if (estimated) "추정값 포함 · " else ""
        if (protein == null || protein.knownCount == 0 || protein.missingCount > 0 ||
            protein.unknownProvenanceCount > 0) {
            advice += DevelopmentAdvice("nutrition-protein-values", "단백질 영양값을 먼저 확인해 주세요",
                "영양값이 비어 있거나 출처가 불명확한 식사가 있어요. 현재 기록으로 섭취 부족을 판단하지 않아요.",
                evidence + listOf("단백질 값 확인 ${protein?.knownCount ?: 0}건 · 미확인 ${protein?.missingCount ?: report.recordedMealCount}건 · 출처 미상 ${protein?.unknownProvenanceCount ?: 0}건"),
                DevelopmentAdviceAction.MEALS, "식사 기록 보기", 10)
        } else {
            val average = protein.knownSum / report.recordedDays.coerceAtLeast(1)
            val savedReference = report.target?.dailyValue(NutritionProfile.PROTEIN_GRAMS)
            // A general sports-nutrition reference, not a new saved target or a medical minimum.
            // ISSN healthy exercising adults: https://doi.org/10.1186/s12970-017-0177-8
            val useWeightReference = savedReference == null && recentWeightKg != null && resistanceSessions >= 2
            val lower = savedReference ?: recentWeightKg?.takeIf { useWeightReference }?.times(1.4)
            val upper = savedReference ?: recentWeightKg?.takeIf { useWeightReference }?.times(2.0)
            val referenceLabel = when {
                savedReference != null -> "저장된 단백질 기준 ${number(savedReference)}g/일"
                lower != null && upper != null -> "체중·운동을 참고한 일반 범위 ${number(lower)}–${number(upper)}g/일"
                else -> "별도의 단백질 기준을 적용하지 않았어요."
            }
            val proteinEvidence = evidence + listOf(
                "${quality}기록된 단백질 합계 ${number(protein.knownSum)}g · 기록일 평균 ${number(average)}g",
                referenceLabel
            ) + if (useWeightReference) listOf(
                "최근 체중 ${number(requireNotNull(recentWeightKg))}kg · 최근 14일 저항운동 ${resistanceSessions}회",
                "1.4–2.0g/kg은 건강한 운동 성인의 일반 참고 범위예요. 개인의 적정량이나 부족 여부를 확정하지 않아요."
            ) else emptyList()
            val title: String
            val summary: String
            val priority: Int
            when {
                report.recordedDays < 3 -> {
                    title = "식사 기록을 조금 더 모아볼게요"
                    summary = "${quality}기록일 평균 단백질 ${number(average)}g. 며칠의 식사만으로 섭취 방향을 바꾸지 않아요."
                    priority = 0
                }
                lower == null -> {
                    title = "단백질이 포함된 식사를 확인해 보세요"
                    summary = "${quality}기록일 평균 ${number(average)}g이에요. 체중·운동 기록이 쌓이면 참고 범위도 함께 안내해요."
                    priority = 0
                }
                average < lower -> {
                    title = "기록한 식단에 단백질 보완을 검토해 보세요"
                    summary = "${quality}기록일 평균 ${number(average)}g이에요. 빠진 식사가 없다면 단백질 식품을 보완할 여지가 있어요."
                    priority = 20
                }
                upper != null && average > upper && savedReference == null -> {
                    title = "단백질을 더하기 전 전체 식단을 살펴보세요"
                    summary = "${quality}기록된 양이 일반 참고 범위 이상이에요. 탄수화물·지방과 식사 구성을 함께 확인하세요."
                    priority = 5
                }
                else -> {
                    title = "기록된 단백질은 참고 기준 이상이에요"
                    summary = "${quality}기록일 평균 ${number(average)}g이에요. 추가 보충에 앞서 현재 식사 구성을 살펴보세요."
                    priority = 5
                }
            }
            advice += DevelopmentAdvice("nutrition-protein", title, summary, proteinEvidence,
                DevelopmentAdviceAction.MEALS, "식사별로 보기", priority)
        }

        // Exceeding a configured sodium reference is observable even from a partial-day subtotal.
        val sodium = report.metric(NutritionProfile.SODIUM_MG)
        val sodiumReference = report.target?.dailyValue(NutritionProfile.SODIUM_MG)
        if (sodium != null && sodium.knownCount > 0 && sodium.unknownProvenanceCount == 0 && sodiumReference != null) {
            val highDays = report.meals.groupBy { it.date }.values.count { meals ->
                meals.sumOf { it.metric(NutritionProfile.SODIUM_MG)?.knownSum ?: 0.0 } > sodiumReference
            }
            if (highDays >= 2) advice += DevelopmentAdvice("nutrition-sodium", "나트륨이 적은 식사 선택을 검토해 보세요",
                "기록된 양만으로도 저장된 나트륨 기준을 넘은 날이 ${highDays}일 있어요.",
                evidence + listOf("저장된 나트륨 기준 ${number(sodiumReference)}mg/일",
                    "${sodium.provenanceLabel()} · 알려진 양의 합계 기준이며, 미확인 양은 제외했어요."),
                DevelopmentAdviceAction.MEALS, "식사 구성 보기", 25)
        }
        val fiber = report.metric(NutritionProfile.FIBER_GRAMS)
        val fiberReference = report.target?.dailyValue(NutritionProfile.FIBER_GRAMS)
        if (fiber != null && fiber.isComplete && fiber.unknownProvenanceCount == 0 &&
            fiberReference != null && report.recordedDays >= 3) {
            val average = fiber.knownSum / report.recordedDays
            if (average < fiberReference) advice += DevelopmentAdvice("nutrition-fiber", "채소·통곡물 등 식이섬유 식품을 확인해 보세요",
                "기록일 평균 식이섬유 ${number(average)}g이에요. 빠진 식사가 없다면 식품 구성을 보완해 보세요.",
                evidence + listOf("저장된 식이섬유 기준 ${number(fiberReference)}g/일", fiber.provenanceLabel()),
                DevelopmentAdviceAction.MEALS, "식사 구성 보기", 15)
        }
        val calories = report.metric(NutritionProfile.CALORIES_KCAL)
        val calorieReference = report.target?.dailyValue(NutritionProfile.CALORIES_KCAL)
        if (calories != null && calories.isComplete && calories.unknownProvenanceCount == 0 &&
            calorieReference != null && report.recordedDays >= 3) {
            val average = calories.knownSum / report.recordedDays
            advice += DevelopmentAdvice("nutrition-energy", "섭취 열량은 식사 누락과 함께 확인하세요",
                "기록일 평균 ${number(average)}kcal · 저장된 기준 ${number(calorieReference)}kcal이에요.",
                evidence + listOf(calories.provenanceLabel(),
                    "식사 기록의 완전성을 확인할 수 없어 자동으로 열량 증감 폭을 정하지 않아요."),
                DevelopmentAdviceAction.MEALS, "식사 기록 보기", 0)
        }
        return advice.sortedByDescending { it.priority }
    }

    private fun number(value: Double): String = String.format(Locale.KOREA, "%.0f", value)
}
