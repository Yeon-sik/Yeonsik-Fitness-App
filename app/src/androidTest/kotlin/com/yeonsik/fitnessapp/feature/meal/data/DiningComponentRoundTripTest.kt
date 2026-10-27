package com.yeonsik.fitnessapp.feature.meal.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.meal.model.*
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogRepositoryApi
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Every database in this suite is isolated in memory; no user's database or remote service is opened. */
class DiningComponentRoundTripTest {
    @Test fun acceptanceMenusPersistReadAndEditWithoutFlatteningOrDuplicatingComponents() = fixture {
        val burger = intake("통새우와퍼", 600, 0.5, listOf(
            component("통새우 추가", "add_on", "paid", 3.0, 1.0, 20.0),
            component("너겟킹", "side", "included", 1.0, 1.0, 100.0),
            component("제로콜라", "beverage", "included", 1.0, 1.0, 0.0)))
        val soup = intake("감자탕", 900, 1.0 / 3, listOf(
            component("뼈 추가", "add_on", "paid", 2.0, 1.0 / 3, 300.0),
            component("김치", "banchan", "included", 1.0, 1.0, 10.0),
            component("단무지", "banchan", "included", 1.0, 1.0, 10.0),
            component("미니만두", "side", "paid", 1.0, 1.0, 100.0),
            component("콜라", "beverage", "review_event", 1.0, 1.0, 100.0)),
            DiningOutConsumption.equalByDiners(3))
        val id = repository.saveDiningOutMenuItems(scope, date, "12:30", listOf(burger, soup))
        val snapshot = read.mealSnapshot(scope, id)!!
        assertEquals(listOf("통새우와퍼", "감자탕"), snapshot.items.map { it.foodName })
        assertEquals(listOf(3, 5), snapshot.items.map { it.components.size })
        assertEquals(1180.0, snapshot.nutrition.calories!!, 0.001)
        val bone = snapshot.items[1].components.first()
        assertEquals(2.0, bone.quantity, 0.0)
        assertEquals(1.0 / 3, bone.consumedFraction!!, 0.000001)
        assertEquals(600.0, bone.nutrition.calories!!, 0.001) // Full ordered snapshot, before fraction.
        assertNull(bone.nutrition.sodiumMg)
        assertEquals("member-뼈 추가", bone.memberId)
        assertEquals("food-뼈 추가", bone.foodId)
        assertEquals("beverage", snapshot.items[1].components.last().groupType)
        assertEquals("review_event", snapshot.items[1].components.last().provisionType)

        val edit = repository.loadMealForEditing(scope, id)!!
        assertEquals(300.0, edit.items[1].components.first().profile.calories(), 0.001)
        val changed = edit.items.map(::fromSnapshot).toMutableList()
        changed[0] = changed[0].copy(components = changed[0].components!!.mapIndexed { index, component ->
            if (index == 0) component.withQuantity(4.0) else component
        })
        assertTrue(repository.updateMealItems(scope, request(edit), "13:00", emptyList(), changed))
        val updated = read.mealSnapshot(scope, id)!!
        assertEquals(listOf(3, 5), updated.items.map { it.components.size })
        assertEquals(1200.0, updated.nutrition.calories!!, 0.001)
        assertEquals(4.0, updated.items[0].components.first().quantity, 0.0)
        assertEquals(3L, updated.items[1].consumption!!.dinerCount)
        assertEquals("equal_by_diners", updated.items[1].consumption!!.shareMethod)
        assertEquals("review_event", updated.items[1].components.last().provisionType)
        assertEquals("restaurant", org.json.JSONObject(updated.items[0].sourceReference!!).getString("restaurant_id"))
        assertEquals("location", org.json.JSONObject(updated.items[0].sourceReference!!).getString("restaurant_location_id"))
        assertEquals("product-통새우와퍼", org.json.JSONObject(updated.items[0].sourceReference!!).getString("catalog_product_id"))

        val secondEdit = repository.loadMealForEditing(scope, id)!!
        repository.updateMealItems(scope, request(secondEdit), "13:30", emptyList(),
            secondEdit.items.map(::fromSnapshot).map { it.copy(components = emptyList()) })
        assertTrue(read.mealSnapshot(scope, id)!!.items.all { it.components.isEmpty() })
        assertEquals(600.0, read.mealSnapshot(scope, id)!!.nutrition.calories!!, 0.001)
        assertNull(read.mealSnapshot(AccountScope("other-owner"), id))
    }

    @Test fun mixedMealTotalsIncludeComponentsAndFlatCallersKeepTheirContract() = fixture {
        val food = NutritionFood.builder().id("rice").ownerId(scope.ownerId).name("밥")
            .kind(NutritionFood.KIND_INGREDIENT).basis(1.0, NutritionUnit.SERVING)
            .profile(NutritionProfile.ofMacros(200.0, 10.0, 30.0, 4.0)).build()
        val menu = intake("메뉴", 600, 0.5,
            listOf(component("추가", "side", "review_event", 3.0, 0.5, 100.0)))
        val id = repository.saveMealItems(scope, date, "12:30", listOf(MealCompositionItem.from(food, 1.0)), listOf(menu))
        assertEquals(650.0, read.mealSnapshot(scope, id)!!.nutrition.calories!!, 0.001)
        assertEquals(listOf(0, 1), read.mealSnapshot(scope, id)!!.items.map { it.components.size })
        val flat = DiningOutMenuIntake(menu.input, 0.5)
        val flatId = repository.saveDiningOutMenuItems(scope, date, "15:00", listOf(flat))
        assertEquals(300.0, read.mealSnapshot(scope, flatId)!!.nutrition.calories!!, 0.001)
        assertTrue(read.mealSnapshot(scope, flatId)!!.items.single().components.isEmpty())
        assertNull(read.mealSnapshot(scope, flatId)!!.items.single().nutrition.sodiumMg)
    }

    @Test fun catalogAndTemplateChangesCannotRewriteHistoricalNutritionIncludingUnknownMacros() = fixture {
        val master = component("미확인 반찬", "banchan", "included", 2.0, 0.5, null)
        val menu = intake("식사", 600, 1.0, listOf(master))
        database.openHelper.writableDatabase.execSQL("INSERT INTO nutrition_foods " +
            "(id,owner_id,name,kind,basis_amount,basis_unit,source_type,created_at,updated_at) VALUES " +
            "('food-미확인 반찬','nutrition-owner','미확인 반찬','dining_out_component',1,'serving','test','now','now')")
        database.openHelper.writableDatabase.execSQL("INSERT INTO composition_templates " +
            "(id,user_id,name,template_kind,revision,created_at,updated_at,device_id) VALUES " +
            "('template','nutrition-owner','템플릿','dining_out',1,'now','now','test')")
        val id = repository.saveDiningOutMenuItems(scope, date, "12:30", listOf(menu))
        val before = read.mealSnapshot(scope, id)!!
        // Change the linked Nutrition master and template rows after recording the meal.
        database.openHelper.writableDatabase.execSQL("UPDATE nutrition_foods SET name='바뀐 이름', calories_kcal=999 WHERE id='food-미확인 반찬'")
        database.openHelper.writableDatabase.execSQL("UPDATE composition_templates SET name='바뀐 템플릿',revision=99 WHERE id='template'")
        val after = read.mealSnapshot(scope, id)!!
        assertEquals(before, after)
        assertNull(after.items.single().components.single().nutrition.calories)
        val edit = repository.loadMealForEditing(scope, id)!!
        repository.updateMealItems(scope, request(edit), "14:00", emptyList(), edit.items.map(::fromSnapshot))
        val component = read.mealSnapshot(scope, id)!!.items.single().components.single()
        assertEquals("미확인 반찬", component.foodName)
        assertNull(component.nutrition.calories)
        assertNull(component.nutrition.sodiumMg)
        assertEquals(2.0, component.quantity, 0.0)
        assertEquals("template", org.json.JSONObject(component.sourceReference!!).getString("composition_template_id"))
    }

    private class Fixture {
        val scope = AccountScope("fitness-owner")
        val date = LocalDate.now().toString()
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitnessRoomDatabase::class.java).build()
        val catalog = object : NutritionCatalogRepositoryApi {
            override fun searchFoods(query: String): List<NutritionFood> = error("Snapshots must not read masters")
            override fun findFoodById(foodId: String): NutritionFood? = error("Snapshots must not read masters")
        }
        val repository = MealRecordRepository(database, catalog, scope.ownerId)
        val read = MealReadRepository(database)
    }
    private fun fixture(work: Fixture.() -> Unit) { val fixture = Fixture(); try { fixture.work() } finally { fixture.database.close() } }
    private fun request(edit: MealRecordEditSnapshot) = MealRecordEditRequest(edit.recordId, edit.updatedAt, emptyList(), edit.items.map { it.itemId })
    private fun fromSnapshot(item: MealRecordEditItem): DiningOutMenuIntake {
        val p = item.food.profile
        return intake(item.food.name, p.calories().toInt(), item.consumedFraction, item.components, item.consumption)
            .copy(input = intake(item.food.name, p.calories().toInt(), item.consumedFraction, null).input.copy(quantity = item.quantity),
                nutritionFoodId = item.sourceFoodId, caloriesKcal = p.calories(), sourceReference = item.food.sourceReference)
    }
    private fun intake(name: String, calories: Int, fraction: Double, components: List<DiningOutComponent>?,
        consumption: DiningOutConsumption? = null) = DiningOutMenuIntake(
        DiningOutMealInput("식당", "본점", name, 1.0, calories, 20.0, 50.0, 10.0, null, null, null,
            "restaurant", "location", "menu-$name", "product-$name"), fraction, "food-$name", calories.toDouble(), components, consumption)
    private fun component(name: String, group: String, provision: String, quantity: Double, fraction: Double, calories: Double?) =
        DiningOutComponent(name, NutritionProfile.builder().value(NutritionProfile.CALORIES_KCAL, calories).build(),
            "food-$name", "{\"composition_template_id\":\"template\",\"composition_template_revision\":1}",
            "group-$group", group, CompositionGroupType.labelOf(group), "optional", "member-$name", provision, quantity, fraction)
}
