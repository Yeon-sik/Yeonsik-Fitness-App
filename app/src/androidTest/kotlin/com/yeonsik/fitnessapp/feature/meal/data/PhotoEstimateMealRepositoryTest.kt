package com.yeonsik.fitnessapp.feature.meal.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.feature.meal.model.PhotoEstimateMealInput
import com.yeonsik.fitnessapp.feature.nutrition.data.NutritionCatalogRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class PhotoEstimateMealRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "photo-estimate-test-${UUID.randomUUID()}.db"
    private lateinit var room: FitnessRoomDatabase
    private lateinit var repository: MealRecordRepository
    private lateinit var read: MealReadRepository
    private val scope = AccountScope("photo-test-owner")
    private val date = LocalDate.now().minusDays(1).toString()

    @Before fun open() {
        room = Room.databaseBuilder(context, FitnessRoomDatabase::class.java, name).build()
        repository = MealRecordRepository(room, NutritionCatalogRepository(room, scope.ownerId), scope.ownerId)
        read = MealReadRepository(room)
    }
    @After fun close() { room.close(); context.deleteDatabase(name) }

    @Test fun caloriesOnlyRoundTripsWithoutCatalogItemsAndMissingMacrosStayMissing() {
        val id = repository.savePhotoEstimate(scope, date, "12:30", PhotoEstimateMealInput(2400.0), null, null)
        room.close()
        open()
        val meal = read.meals(scope, date).single()
        assertEquals(id, meal.id)
        assertEquals(date.replace("-", ""), meal.previewTitle)
        assertTrue(meal.isPhotoEstimate)
        assertTrue(meal.subtitle.contains("사진 기반 추정"))
        assertNull(meal.recordedProteinGrams)
        assertNull(meal.recordedCarbsGrams)
        assertNull(meal.recordedFatGrams)
        assertEquals("estimated", meal.nutritionStatus)
        val snapshot = read.mealSnapshot(scope, id)!!
        assertTrue(snapshot.items.isEmpty())
        assertNull(snapshot.nutrition.proteinGrams)
        val totals = read.mealTotals(scope, date)
        assertEquals(2400.0, totals.totals.getValue("calories_kcal").sum, 0.0)
        listOf("protein_grams", "carbs_grams", "fat_grams").forEach { key ->
            assertEquals(0, totals.totals.getValue(key).known)
            assertEquals(1, totals.totals.getValue(key).missing)
        }
        assertTrue(read.meals(AccountScope("other-owner"), date).isEmpty())
        assertNull(read.mealSnapshot(AccountScope("other-owner"), id))
        val report = read.nutritionSummary(scope, date, date)
        assertEquals(1, report.estimatedMealCount)
        assertNull(report.proteinGrams)
    }

    @Test fun editingMovingDeletingAndKnownZeroKeepTheOriginalIdentityAndProvenance() {
        val id = repository.savePhotoEstimate(scope, date, "12:30", PhotoEstimateMealInput(2400.0), null, null)
        val original = repository.loadMealForEditing(scope, id)!!
        assertNotNull(original.photoEstimate)
        assertEquals(id, repository.savePhotoEstimate(scope, date, "13:00", PhotoEstimateMealInput(2200.0, 0.0), id, original.updatedAt))
        assertThrows(IllegalStateException::class.java) {
            repository.savePhotoEstimate(scope, date, "13:00", PhotoEstimateMealInput(999.0), id, original.updatedAt)
        }
        val nextDate = LocalDate.parse(date).minusDays(1).toString()
        assertTrue(repository.updateMealDateTime(scope, id, nextDate, "13:00"))
        assertTrue(read.meals(scope, date).isEmpty())
        val moved = read.meals(scope, nextDate).single()
        assertEquals(id, moved.id)
        assertEquals(nextDate.replace("-", ""), moved.previewTitle)
        assertTrue(moved.isPhotoEstimate)
        assertEquals(0.0, moved.recordedProteinGrams!!, 0.0)
        assertEquals(2200, moved.calories)
        assertTrue(repository.deleteMeal(scope, id))
        assertTrue(read.meals(scope, nextDate).isEmpty())
    }

    @Test fun mixedTotalsCountUnknownAndKnownMealsSeparatelyAndRejectAnotherOwner() {
        repository.savePhotoEstimate(scope, date, "12:30", PhotoEstimateMealInput(2400.0), null, null)
        repository.savePhotoEstimate(scope, date, "18:30", PhotoEstimateMealInput(600.0, 30.0, 70.0, 20.0), null, null)
        val totals = read.mealTotals(scope, date)
        assertEquals(3000.0, totals.totals.getValue("calories_kcal").sum, 0.0)
        val protein = totals.totals.getValue("protein_grams")
        assertEquals(30.0, protein.sum, 0.0)
        assertEquals(1, protein.known)
        assertEquals(1, protein.missing)
        assertThrows(IllegalStateException::class.java) {
            repository.savePhotoEstimate(AccountScope("other-owner"), date, "12:30", PhotoEstimateMealInput(10.0), null, null)
        }
    }
}
