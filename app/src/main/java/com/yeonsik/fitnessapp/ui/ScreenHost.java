package com.yeonsik.fitnessapp.ui;

import android.app.Activity;
import android.widget.LinearLayout;

import com.yeonsik.fitnessapp.cardio.CardioActivityType;
import com.yeonsik.fitnessapp.cardio.CardioRouteProjection;
import com.yeonsik.fitnessapp.cardio.CardioRepository;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.data.NutritionCatalogRepository;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.ProductReadV1;
import com.yeonsik.fitnessapp.data.RestaurantMenuReadV1Client;
import com.yeonsik.fitnessapp.development.DevelopmentInsight;
import com.yeonsik.fitnessapp.development.DevelopmentRepository;
import com.yeonsik.fitnessapp.exercise.ExerciseMasterRepository;
import com.yeonsik.fitnessapp.routine.RoutineRepository;
import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.state.FitnessScreen;
import com.yeonsik.fitnessapp.state.WorkoutSessionState;
import com.yeonsik.fitnessapp.supplement.SupplementRepository;

import java.util.List;

/**
 * 화면 렌더러가 MainActivity에 접근하는 유일한 통로.
 * 화면은 "어떻게 그릴지"만 담당하고, 내비게이션·의존성·앱 수준 상태는 host가 소유한다.
 */
public interface ScreenHost {
    Activity activity();

    FitnessUi ui();

    LinearLayout content();

    FitnessRepository repository();

    NutritionCatalogRepository nutritionCatalogRepository();

    CardioRepository cardioRepository();

    RoutineRepository routineRepository();

    SupplementRepository supplementRepository();

    ExerciseMasterRepository exerciseMasterRepository();

    WorkoutSessionState sessionState();

    String today();

    FitnessScreen currentScreen();

    void navigate(FitnessScreen screen);

    /** Pops the current screen, returning true when a previous history entry was shown. */
    default boolean back() {
        return false;
    }

    /** Replaces the current history entry for flow transitions such as session → summary. */
    default void replace(FitnessScreen screen) {
        navigate(screen);
    }

    void rerender();

    void toast(String message);

    // ── 화면 간 공유 액션 ─────────────────────────────────────────────

    void openWorkoutSession(String recordId);

    void openWorkoutExercisePicker();

    void finishActiveWorkout();

    void continueWorkoutIfAvailable();

    void startRoutineWorkout(List<RoutineExerciseInstance> exercises);

    String currentWorkoutRecordId();

    void confirmDeleteSession(String recordId);

    void startEmptyWorkout();

    void showPastWorkoutDialog();

    void startCardioWorkout(CardioActivityType activityType);

    void openCardioSummary(String recordId);

    void loadCardioRoute(String recordId, CardioRouteCallback callback);

    void pauseCardioWorkout();

    void resumeCardioWorkout();

    void finishCardioWorkout();

    void editCardioAverageHeartRate();

    void cancelCardioWorkout();

    void showBodyMetricDialog();

    void showBodyMetricDialog(String date, String recordId);

    void openMealManagement();

    void openMealManagement(String date);

    void openMealManagement(String date, FitnessScreen returnScreen);

    void openSettingsConnections();

    DevelopmentRepository developmentRepository();

    void showDevelopmentBodyProfileDialog();

    void showDevelopmentGoalDialog();

    void openDevelopmentInsightAction(DevelopmentInsight insight);

    /** 세트 완료 시 휴식 타이머를 시작한다. null 또는 0 이하이면 기본 90초. */
    void startRestTimer(Integer restSeconds);

    // ── 테마 ─────────────────────────────────────────────────────────

    /** 현재 테마 모드: "light" | "dark" | "system". 기본은 light. */
    String themeMode();

    void setThemeMode(String mode);

    // ── 설정 / 동기화 ─────────────────────────────────────────────────

    void openFleekDataImport();

    boolean isDataImporting();

    String dataImportDetail();

    void createLocalBackup();

    void restoreLocalBackup();

    void exportRecordsCsv();

    boolean isDataTransferInProgress();

    String dataTransferDetail();

    SupabaseConfig supabaseConfig();

    boolean isSharedSupabaseConnectionManaged();

    void saveSupabaseConfig(String url, String anonKey);

    void signInToSupabase(String email, String password);

    void signUpToSupabase(String email, String password);

    void signOutFromSupabase();

    SupabaseConfig nutritionSupabaseConfig();

    boolean isNutritionSupabaseConnectionManaged();

    void saveNutritionSupabaseConfig(String url, String anonKey);

    void signInToNutritionSupabase(String email, String password);

    void signUpToNutritionSupabase(String email, String password);

    void signOutFromNutritionSupabase();

    SupabaseConfig priceTraceSupabaseConfig();

    boolean isPriceTraceSupabaseConnectionManaged();

    void savePriceTraceSupabaseConfig(String url, String anonKey);

    void signInToPriceTraceSupabase(String email, String password);

    void signUpToPriceTraceSupabase(String email, String password);

    void signOutFromPriceTraceSupabase();

    void searchPriceTraceProducts(String query, ProductSearchCallback callback);

    void loadPriceTraceProduct(String catalogProductId, ProductLoadCallback callback);

    void searchPriceTraceRestaurants(String query, RestaurantSearchCallback callback);

    void loadPriceTraceRestaurant(String restaurantId, RestaurantLoadCallback callback);

    void loadPublicProductNutrition(
            String catalogProductId,
            PublicNutritionCallback callback
    );

    void syncNutritionCatalog(NutritionCatalogRepository.SyncCallback callback);

    void setNutritionFoodPublication(
            String nutritionFoodId,
            String catalogProductId,
            boolean publish,
            NutritionCatalogRepository.PublicationCallback callback
    );

    void setDiningOutMenuPublication(
            String nutritionFoodId,
            boolean publish,
            NutritionCatalogRepository.PublicationCallback callback
    );

    void runManualSync();

    boolean isManualSyncing();

    String syncLabel();

    String syncDetail();

    String repositoryUserLabel();

    interface CardioRouteCallback {
        void onComplete(CardioRouteProjection projection);

        void onError(Exception error);
    }

    interface ProductSearchCallback {
        void onComplete(List<ProductReadV1> products);

        void onError(Exception error);
    }

    interface ProductLoadCallback {
        void onComplete(ProductReadV1 product);

        void onError(Exception error);
    }

    interface RestaurantSearchCallback {
        void onComplete(List<RestaurantMenuReadV1Client.RestaurantSummary> restaurants);

        void onError(Exception error);
    }

    interface RestaurantLoadCallback {
        void onComplete(RestaurantMenuReadV1Client.RestaurantDetail restaurant);

        void onError(Exception error);
    }

    interface PublicNutritionCallback {
        void onComplete(NutritionCatalogRepository.PublicProductNutrition nutrition);

        void onError(Exception error);
    }
}
