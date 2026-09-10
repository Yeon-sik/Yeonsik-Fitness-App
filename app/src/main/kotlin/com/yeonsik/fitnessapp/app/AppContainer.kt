package com.yeonsik.fitnessapp.app

import android.content.Context
import com.yeonsik.fitnessapp.feature.cardio.data.CardioRepository
import com.yeonsik.fitnessapp.config.NutritionSupabaseConfigStore
import com.yeonsik.fitnessapp.config.PriceTraceSupabaseConfigStore
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.config.SupabaseConfigStore
import com.yeonsik.fitnessapp.core.account.AccountOwnershipService
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider
import com.yeonsik.fitnessapp.core.database.RoomTransactionRunner
import com.yeonsik.fitnessapp.core.database.backup.RoomBackupDatabaseStorage
import com.yeonsik.fitnessapp.feature.body.data.BodyMetricsRepository
import com.yeonsik.fitnessapp.feature.nutrition.data.NutritionCatalogRepository
import com.yeonsik.fitnessapp.integration.pricetrace.ProductReadV1Client
import com.yeonsik.fitnessapp.integration.pricetrace.RestaurantMenuReadV1Client
import com.yeonsik.fitnessapp.feature.development.data.DevelopmentRepository
import com.yeonsik.fitnessapp.exercise.ExerciseMasterRepository
import com.yeonsik.fitnessapp.feature.cardio.api.CardioRepositoryApi
import com.yeonsik.fitnessapp.feature.cardio.application.CardioSessionApplicationService
import com.yeonsik.fitnessapp.feature.home.api.HomeRepositoryApi
import com.yeonsik.fitnessapp.feature.home.data.FeatureHomeReadSources
import com.yeonsik.fitnessapp.feature.home.data.HomeReadRepository
import com.yeonsik.fitnessapp.feature.body.data.BodyMetricsReadRepository
import com.yeonsik.fitnessapp.feature.body.application.BodyMetricsApplicationService
import com.yeonsik.fitnessapp.feature.development.data.DevelopmentReadRepository
import com.yeonsik.fitnessapp.feature.development.api.DevelopmentReportApi
import com.yeonsik.fitnessapp.feature.development.application.DevelopmentApplicationService
import com.yeonsik.fitnessapp.feature.development.application.DevelopmentReportService
import com.yeonsik.fitnessapp.feature.exercise.api.ExerciseMasterRepositoryApi
import com.yeonsik.fitnessapp.feature.meal.data.MealRecordRepository
import com.yeonsik.fitnessapp.feature.meal.data.MealReadRepository
import com.yeonsik.fitnessapp.feature.meal.api.MealRecordRepositoryApi
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogRepositoryApi
import com.yeonsik.fitnessapp.feature.routine.api.RoutineRepositoryApi
import com.yeonsik.fitnessapp.feature.supplement.api.SupplementRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.application.CompleteWorkout
import com.yeonsik.fitnessapp.feature.workout.application.InitializeWorkoutExercise
import com.yeonsik.fitnessapp.feature.workout.application.WorkoutSessionApplicationService
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutRepositoryImplementation
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutReadRepository
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutSummaryApi
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutSummaryRepository
import com.yeonsik.fitnessapp.integration.nutrition.NutritionIntegrationService
import com.yeonsik.fitnessapp.integration.sync.SyncApplicationService
import com.yeonsik.fitnessapp.integration.transfer.LocalDataTransferApplicationService
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutInterchangeRepository
import com.yeonsik.fitnessapp.feature.routine.data.RoutineRepository
import com.yeonsik.fitnessapp.feature.supplement.data.SupplementRepository
import com.yeonsik.fitnessapp.sync.SupabaseAuthManager
import com.yeonsik.fitnessapp.integration.personalos.SupabaseSyncManager

/**
 * Activity-scoped dependency composition root for the single :app module.
 *
 * Feature code receives the narrow API it needs from here. Activities coordinate lifecycle,
 * navigation and platform callbacks without constructing repositories or integration adapters.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val configStore = SupabaseConfigStore(appContext)
    val nutritionConfigStore = NutritionSupabaseConfigStore(appContext)
    val priceTraceConfigStore = PriceTraceSupabaseConfigStore(appContext)
    val supabaseAuthManager = SupabaseAuthManager(configStore)
    val nutritionAuthManager = SupabaseAuthManager(nutritionConfigStore)
    val priceTraceAuthManager = SupabaseAuthManager(priceTraceConfigStore)

    var supabaseConfig: SupabaseConfig = configStore.load()
        private set
    var nutritionSupabaseConfig: SupabaseConfig = nutritionConfigStore.load()
        private set
    var priceTraceSupabaseConfig: SupabaseConfig = priceTraceConfigStore.load()
        private set

    val roomDatabase: FitnessRoomDatabase = FitnessRoomDatabaseProvider.get(appContext)
    val roomTransactionRunner = RoomTransactionRunner(roomDatabase)
    private val backupDatabaseStorage = RoomBackupDatabaseStorage(roomDatabase)

    private val accountOwnershipService = AccountOwnershipService(
        roomDatabase,
        roomTransactionRunner,
        supabaseConfig.effectiveUserId()
    )
    private val fitnessSummaryStore: WorkoutSummaryApi = WorkoutSummaryRepository(roomDatabase)
    private val workoutInterchangeStore = WorkoutInterchangeRepository(
        roomDatabase,
        appContext,
        roomTransactionRunner
    )
    private val bodyMetricsRepository = BodyMetricsRepository(
        roomDatabase,
        supabaseConfig.effectiveUserId()
    )
    private val nutritionCatalogRepository = NutritionCatalogRepository(
        roomDatabase,
        appContext,
        nutritionSupabaseConfig.effectiveUserId(),
        nutritionSupabaseConfig
    )
    private val mealRecordRepository = MealRecordRepository(
        roomDatabase,
        nutritionCatalogRepository,
        supabaseConfig.effectiveUserId()
    )
    private val cardioRepository = CardioRepository(
        roomDatabase,
        supabaseConfig.effectiveUserId(),
        roomTransactionRunner
    )
    private val exerciseMasterRepository = ExerciseMasterRepository(appContext)
    private val routineRepository = RoutineRepository(
        roomDatabase,
        appContext,
        supabaseConfig.effectiveUserId()
    )
    private val developmentRepository = DevelopmentRepository(
        roomDatabase,
        appContext,
        supabaseConfig.effectiveUserId()
    )
    private val supplementRepository = SupplementRepository(
        roomDatabase,
        supabaseConfig.effectiveUserId(),
        appContext
    )

    private val workoutRepositoryImplementation = WorkoutRepositoryImplementation(
        roomDatabase,
        appContext,
        roomTransactionRunner
    )
    private val workoutReadRepository = WorkoutReadRepository(roomDatabase, appContext)
    private val mealReadRepository = MealReadRepository(roomDatabase)
    private val bodyMetricsReadRepository = BodyMetricsReadRepository(roomDatabase)
    private val developmentReadRepository = DevelopmentReadRepository(roomDatabase)

    val workoutRepository: WorkoutRepositoryApi = workoutRepositoryImplementation
    val cardioRepositoryApi: CardioRepositoryApi = cardioRepository
    val exerciseMasterRepositoryApi: ExerciseMasterRepositoryApi = exerciseMasterRepository
    val developmentReportApi: DevelopmentReportApi = DevelopmentReportService(
        workoutReadRepository,
        mealReadRepository,
        bodyMetricsReadRepository,
        developmentReadRepository
    )
    val supplementRepositoryApi: SupplementRepositoryApi = supplementRepository
    val mealRecordRepositoryApi: MealRecordRepositoryApi = mealRecordRepository
    val nutritionCatalogRepositoryApi: NutritionCatalogRepositoryApi = nutritionCatalogRepository
    val routineRepositoryApi: RoutineRepositoryApi = routineRepository
    val homeRepository: HomeRepositoryApi = HomeReadRepository(
        FeatureHomeReadSources(
            workoutReadRepository,
            mealReadRepository,
            bodyMetricsReadRepository,
            developmentReadRepository
        ),
        routineRepositoryApi
    )
    val initializeWorkoutExercise = InitializeWorkoutExercise(workoutRepository)
    val completeWorkout = CompleteWorkout(workoutRepository)

    private val syncManager = SupabaseSyncManager(roomDatabase, appContext)
    private val productReadClient = ProductReadV1Client(priceTraceSupabaseConfig)
    private val restaurantMenuReadClient = RestaurantMenuReadV1Client(priceTraceSupabaseConfig)

    val bodyMetricsApplicationService = BodyMetricsApplicationService(
        bodyMetricsRepository,
        supabaseConfig.effectiveUserId()
    )
    val developmentApplicationService = DevelopmentApplicationService(
        developmentRepository,
        bodyMetricsApplicationService,
        supabaseConfig.effectiveUserId()
    )
    val cardioSessionApplicationService = CardioSessionApplicationService(
        cardioRepository,
        workoutRepository,
        roomTransactionRunner,
        supabaseConfig.effectiveUserId()
    )
    val workoutSessionApplicationService = WorkoutSessionApplicationService(
        workoutRepository,
        cardioSessionApplicationService,
        roomTransactionRunner
    )
    val nutritionIntegrationService = NutritionIntegrationService(
        nutritionCatalogRepository,
        nutritionCatalogRepository,
        productReadClient,
        restaurantMenuReadClient,
        nutritionAuthManager,
        priceTraceAuthManager,
        nutritionSupabaseConfig
    )
    val syncApplicationService = SyncApplicationService(
        supabaseAuthManager,
        syncManager,
        nutritionIntegrationService
    )
    val localDataTransferApplicationService = LocalDataTransferApplicationService(
        backupDatabaseStorage,
        nutritionCatalogRepository,
        workoutInterchangeStore,
        fitnessSummaryStore,
        exerciseMasterRepository
    )

    fun applyAuthenticatedSharedConfig(config: SupabaseConfig) {
        supabaseConfig = config
        val ownerId = config.effectiveUserId()
        accountOwnershipService.claimLocalRows(ownerId)
        bodyMetricsRepository.setUserId(ownerId)
        bodyMetricsApplicationService.setOwnerId(ownerId)
        developmentApplicationService.setOwnerId(ownerId)
        cardioSessionApplicationService.setOwnerId(ownerId)
        cardioRepository.setUserId(ownerId)
        routineRepository.setUserId(ownerId)
        developmentRepository.normalizeLocalUserId(ownerId)
        supplementRepository.normalizeLocalUserId(ownerId)
        mealRecordRepository.setUserId(ownerId)
    }

    fun applySharedSessionConfig(config: SupabaseConfig) {
        supabaseConfig = config
        val ownerId = config.effectiveUserId()
        accountOwnershipService.setOwnerId(ownerId)
        bodyMetricsRepository.setUserId(ownerId)
        bodyMetricsApplicationService.setOwnerId(ownerId)
        developmentApplicationService.setOwnerId(ownerId)
        cardioSessionApplicationService.setOwnerId(ownerId)
        cardioRepository.setUserId(ownerId)
        routineRepository.setUserId(ownerId)
        developmentRepository.setUserId(ownerId)
        supplementRepository.setUserId(ownerId)
        mealRecordRepository.setUserId(ownerId)
    }

    fun applyNutritionSessionConfig(config: SupabaseConfig) {
        nutritionSupabaseConfig = config
        nutritionIntegrationService.setNutritionConfig(config)
        nutritionCatalogRepository.setUserId(config.effectiveUserId())
    }

    fun applyAuthenticatedNutritionConfig(config: SupabaseConfig) {
        nutritionSupabaseConfig = config
        nutritionIntegrationService.setNutritionConfig(config)
        val ownerId = config.effectiveUserId()
        nutritionCatalogRepository.normalizeLocalUserId(ownerId)
    }

    fun applyPriceTraceSessionConfig(config: SupabaseConfig) {
        priceTraceSupabaseConfig = config
        productReadClient.setConfig(config)
        restaurantMenuReadClient.setConfig(config)
    }
}
