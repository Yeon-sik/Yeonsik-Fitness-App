package com.yeonsik.fitnessapp.feature.information.model

import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.time.*

enum class InformationProjectionKind(val label: String) { WEIGHT("체중"), MEAL("식사"), WORKOUT("완료 운동") }
data class ProjectionField(val key: String, val label: String, val optional: Boolean = false)
data class InformationProjectionDraft(val kind: InformationProjectionKind, val values: Map<String, String>)
data class InformationProjectionPreview(
    val kind: InformationProjectionKind, val values: Map<String, String>, val summary: String, val mappingJson: String
) {
    fun text(key: String) = values[key].orEmpty()
    fun number(key: String) = text(key).toDouble()
    fun optionalNumber(key: String) = text(key).takeIf { it.isNotBlank() }?.toDouble()
}

/** Explicit user mapping, independent of the source record_type. Unknown values remain empty. */
object InformationProjection {
    private val decimal = Regex("(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?")
    private val common = listOf(ProjectionField("date", "기록 날짜 · YYYY-MM-DD"))
    fun fields(kind: InformationProjectionKind, values: Map<String, String> = emptyMap()): List<ProjectionField> = common + when (kind) {
        InformationProjectionKind.WEIGHT -> listOf(ProjectionField("weight_kg", "체중 · kg"))
        InformationProjectionKind.MEAL -> listOf(ProjectionField("time", "섭취 시각 · HH:mm"), ProjectionField("name", "식사 이름"),
            ProjectionField("calories_kcal", "총 열량 · kcal · 정수"), ProjectionField("protein_grams", "총 단백질 · g"),
            ProjectionField("carbs_grams", "총 탄수화물 · g"), ProjectionField("fat_grams", "총 지방 · g"),
            ProjectionField("sodium_mg", "총 나트륨 · mg", true), ProjectionField("sugars_grams", "총 당류 · g", true),
            ProjectionField("saturated_fat_grams", "총 포화지방 · g", true))
        InformationProjectionKind.WORKOUT -> listOf(ProjectionField("activity_name", "운동 이름"),
            ProjectionField("start_time", "시작 시각 · HH:mm"), ProjectionField("duration_seconds", "총 운동 시간 · 초")) +
            if (values["mode"] == "resistance") listOf(ProjectionField("sets", "같은 구성의 완료 세트 수"),
                ProjectionField("reps", "세트당 횟수"), ProjectionField("load_kg", "세트당 총 중량 · kg")) else emptyList()
    } + if (kind == InformationProjectionKind.MEAL) emptyList() else listOf(ProjectionField("memo", "메모", true))

    fun draft(record: InformationRecord, kind: InformationProjectionKind, zone: ZoneId = ZoneId.systemDefault()): InformationProjectionDraft {
        require(record.owner == InformationOwner.FITNESS)
        // Alias collisions must not silently choose one source assertion over another.
        val fields = record.payload.getValue("fields").jsonArray.map { it.jsonObject }
            .groupBy { it.getValue("key").jsonPrimitive.content.removePrefix("fitness.") }
            .filterValues { it.size == 1 }.mapValues { it.value.single() }
        val values = mutableMapOf<String, String>()
        val units = mapOf("weight_kg" to "kg", "calories_kcal" to "kcal", "protein_grams" to "g", "carbs_grams" to "g",
            "fat_grams" to "g", "sodium_mg" to "mg", "sugars_grams" to "g", "saturated_fat_grams" to "g",
            "load_kg" to "kg", "duration_seconds" to "s")
        for ((key, field) in fields) {
            val raw = field["value"] as? JsonPrimitive ?: continue
            if (raw == JsonNull) continue
            val unit = field["unit"]?.jsonPrimitive?.content
            if (units[key] != unit) continue
            values[key] = raw.content
        }
        fun sourceTime(text: String?): ZonedDateTime? = text?.let {
            runCatching { OffsetDateTime.parse(it).atZoneSameInstant(zone) }.getOrNull()
        }
        fun suggestTime(time: ZonedDateTime, vararg keys: String) {
            val date = time.toLocalDate().toString()
            // Never combine an explicit conflicting calendar date with a converted instant's time.
            values.putIfAbsent("date", date)
            if (values["date"] == date) keys.forEach {
                values.putIfAbsent(it, time.toLocalTime().toString().take(5))
            }
        }
        val remoteMeal = fields["meal_snapshot"]?.get("value") as? JsonObject
        if (kind == InformationProjectionKind.MEAL && remoteMeal != null) {
            mapOf("menu" to "name", "calories" to "calories_kcal", "protein_grams" to "protein_grams",
                "carbs_grams" to "carbs_grams", "fat_grams" to "fat_grams").forEach { (source, target) ->
                (remoteMeal[source] as? JsonPrimitive)?.takeIf { it != JsonNull }?.let { values.putIfAbsent(target, it.content) }
            }
            val eaten = sourceTime((remoteMeal["eaten_at"] as? JsonPrimitive)?.contentOrNull)
            if (eaten != null) suggestTime(eaten, "time")
            else (remoteMeal["date"] as? JsonPrimitive)?.contentOrNull?.let { values.putIfAbsent("date", it) }
        }
        sourceTime(record.payload["occurred_at"]?.jsonPrimitive?.content)?.let { suggestTime(it, "time", "start_time") }
        if (kind == InformationProjectionKind.WORKOUT) values["mode"] = "timed"
        return InformationProjectionDraft(kind, values)
    }

    fun preview(draft: InformationProjectionDraft, now: ZonedDateTime = ZonedDateTime.now()): InformationProjectionPreview {
        val values = draft.values.mapValues { it.value.trim() }.toMutableMap()
        fun required(key: String): String = values[key].orEmpty().also { require(it.isNotBlank()) { "${fields(draft.kind, values).find { f -> f.key == key }?.label ?: key} 값을 입력하세요. 모르는 값은 0으로 채우지 마세요." } }
        fun quantity(key: String, positive: Boolean = false, optional: Boolean = false): BigDecimal? {
            val raw = values[key].orEmpty()
            if (optional && raw.isBlank()) return null
            required(key)
            require(raw.length <= 128 && decimal.matches(raw)) { "$key 값은 단위에 맞는 0 이상의 소수여야 합니다." }
            val number = BigDecimal(raw)
            require(!positive || number > BigDecimal.ZERO) { "$key 값은 0보다 커야 합니다." }
            val converted = number.toDouble()
            require(converted.isFinite() && BigDecimal.valueOf(converted).compareTo(number) == 0) { "$key 값은 실제 기록의 숫자 범위에서 정확하게 표현할 수 없습니다. 원문 검토함에 보관하세요." }
            return number
        }
        fun integer(key: String, maximum: Int, positive: Boolean = true): Int {
            val value = quantity(key, positive)!!
            val result = runCatching { value.intValueExact() }.getOrElse { error("$key 값은 정수여야 합니다.") }
            require(result <= maximum) { "$key 값이 지원 범위를 넘습니다 ($maximum)." }
            values[key] = result.toString()
            return result
        }
        val date = runCatching { LocalDate.parse(required("date")) }.getOrElse { error("날짜는 YYYY-MM-DD 형식이어야 합니다.") }
        require(!date.isAfter(now.toLocalDate())) { "미래 기록은 완료된 사실로 반영할 수 없습니다." }
        require(values["memo"].orEmpty().length <= 1000) { "메모는 1000자 이하여야 합니다." }
        fun time(key: String): LocalTime {
            val text = required(key)
            require(Regex("[0-2][0-9]:[0-5][0-9]").matches(text)) { "시각은 HH:mm 형식이어야 합니다." }
            return runCatching { LocalTime.parse(text) }.getOrElse { error("올바른 시각을 입력하세요.") }
        }
        val summary = when (draft.kind) {
            InformationProjectionKind.WEIGHT -> {
                quantity("weight_kg", positive = true)
                "$date 체중 ${values["weight_kg"]} kg · 기존 날짜 기록이 있으면 추가하지 않습니다."
            }
            InformationProjectionKind.MEAL -> {
                require(required("name").length <= 200) { "식사 이름은 200자 이하여야 합니다." }
                val eaten = date.atTime(time("time")).atZone(now.zone)
                require(!eaten.isAfter(now)) { "미래 식사는 실제 섭취 기록으로 반영할 수 없습니다." }
                integer("calories_kcal", Int.MAX_VALUE, positive = false)
                listOf("protein_grams", "carbs_grams", "fat_grams").forEach { quantity(it) }
                listOf("sodium_mg", "sugars_grams", "saturated_fat_grams").forEach { quantity(it, optional = true) }
                "$date ${values["time"]} · ${values["name"]}\n한 끼 전체: ${values["calories_kcal"]} kcal / 단백질 ${values["protein_grams"]} g / 탄수화물 ${values["carbs_grams"]} g / 지방 ${values["fat_grams"]} g\n미입력 선택 영양값은 모름으로 유지합니다."
            }
            InformationProjectionKind.WORKOUT -> {
                require(required("activity_name").length <= 120) { "운동 이름은 120자 이하여야 합니다." }
                val duration = integer("duration_seconds", 86400)
                val started = date.atTime(time("start_time")).atZone(now.zone)
                require(!started.plusSeconds(duration.toLong()).isAfter(now)) { "운동 종료 시각이 미래입니다." }
                values["started_at"] = started.toOffsetDateTime().toString()
                values["ended_at"] = started.plusSeconds(duration.toLong()).toOffsetDateTime().toString()
                require(values["mode"] in setOf("timed", "resistance")) { "운동 기록 방식을 선택하세요." }
                if (values["mode"] == "resistance") {
                    integer("sets", 100); integer("reps", 10000); quantity("load_kg", positive = true)
                    require(values.getValue("load_kg").toDouble() * values.getValue("reps").toInt() * values.getValue("sets").toInt() < Double.MAX_VALUE)
                }
                "$date ${values["start_time"]} · ${values["activity_name"]} · $duration 초 완료\n" +
                    if (values["mode"] == "resistance") "${values["sets"]} 세트 × ${values["reps"]} 회 × ${values["load_kg"]} kg (모든 세트가 같은 구성)" else "시간 기록 1세트 · 중량/횟수/심박/GPS를 만들어 넣지 않습니다."
            }
        }
        val allowed = fields(draft.kind, values).map { it.key }.toSet() + if (draft.kind == InformationProjectionKind.WORKOUT) setOf("mode", "started_at", "ended_at") else emptySet()
        val selected = values.filterKeys { it in allowed }
        val mapping = buildJsonObject {
            put("schema_version", "fitness-native-projection.v1"); put("kind", draft.kind.name.lowercase())
            put("time_zone", now.zone.id); put("values", JsonObject(selected.mapValues { JsonPrimitive(it.value) }))
        }
        return InformationProjectionPreview(draft.kind, selected, summary, InformationRecordCodec.canonical(mapping))
    }
}
