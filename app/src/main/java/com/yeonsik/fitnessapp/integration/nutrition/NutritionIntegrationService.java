package com.yeonsik.fitnessapp.integration.nutrition;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.ProductReadV1;
import com.yeonsik.fitnessapp.data.ProductReadV1Client;
import com.yeonsik.fitnessapp.data.RestaurantMenuReadV1Client;
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogRepositoryApi;
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogSyncStore;
import com.yeonsik.fitnessapp.sync.SupabaseAuthManager;

import java.io.IOException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/**
 * Integration boundary for Nutrition DB and PriceTrace read/publication contracts.
 *
 * It owns token refresh, remote contract clients, and the local catalog cache write that must
 * accompany a PriceTrace read. MainActivity receives contract models, but never coordinates the
 * repository/client sequence itself.
 */
public final class NutritionIntegrationService {
    private final NutritionCatalogSyncStore nutritionCatalogSync;
    private final NutritionCatalogRepositoryApi nutritionCatalog;
    private final ProductReadV1Client productReadClient;
    private final RestaurantMenuReadV1Client restaurantReadClient;
    private final SupabaseAuthManager nutritionAuth;
    private final SupabaseAuthManager priceTraceAuth;
    private final NutritionPublicNutritionClient publicNutritionClient;
    private final NutritionPublicationClient publicationClient;
    private final NutritionCatalogSyncClient syncClient;
    private volatile SupabaseConfig nutritionConfig;

    public NutritionIntegrationService(
            NutritionCatalogRepositoryApi nutritionCatalog,
            NutritionCatalogSyncStore nutritionCatalogSync,
            ProductReadV1Client productReadClient,
            RestaurantMenuReadV1Client restaurantReadClient,
            SupabaseAuthManager nutritionAuth,
            SupabaseAuthManager priceTraceAuth
    ) {
        this(
                nutritionCatalog,
                nutritionCatalogSync,
                productReadClient,
                restaurantReadClient,
                nutritionAuth,
                priceTraceAuth,
                SupabaseConfig.empty()
        );
    }

    public NutritionIntegrationService(
            NutritionCatalogRepositoryApi nutritionCatalog,
            NutritionCatalogSyncStore nutritionCatalogSync,
            ProductReadV1Client productReadClient,
            RestaurantMenuReadV1Client restaurantReadClient,
            SupabaseAuthManager nutritionAuth,
            SupabaseAuthManager priceTraceAuth,
            SupabaseConfig nutritionConfig
    ) {
        if (nutritionCatalog == null || nutritionCatalogSync == null
                || productReadClient == null || restaurantReadClient == null
                || nutritionAuth == null || priceTraceAuth == null) {
            throw new IllegalArgumentException("영양 통합 서비스 의존성이 없습니다.");
        }
        this.nutritionCatalogSync = nutritionCatalogSync;
        this.nutritionCatalog = nutritionCatalog;
        this.productReadClient = productReadClient;
        this.restaurantReadClient = restaurantReadClient;
        this.nutritionAuth = nutritionAuth;
        this.priceTraceAuth = priceTraceAuth;
        this.publicNutritionClient = new NutritionPublicNutritionClient();
        this.publicationClient = new NutritionPublicationClient();
        this.syncClient = new NutritionCatalogSyncClient();
        this.nutritionConfig = nutritionConfig == null
                ? SupabaseConfig.empty()
                : nutritionConfig;
    }

    public void setNutritionConfig(SupabaseConfig nutritionConfig) {
        this.nutritionConfig = nutritionConfig == null
                ? SupabaseConfig.empty()
                : nutritionConfig;
    }

    public List<ProductReadV1> searchProducts(String query) throws Exception {
        List<ProductReadV1> products = productReadClient.searchProducts(query);
        nutritionCatalog.cachePriceTraceProducts(products);
        return products;
    }

    public ProductReadV1 loadProduct(String catalogProductId) throws Exception {
        ProductReadV1 product = productReadClient.findProduct(catalogProductId);
        if (product != null) {
            nutritionCatalog.cachePriceTraceProducts(Collections.singletonList(product));
        }
        return product;
    }

    public List<RestaurantSummary> searchRestaurants(String query)
            throws Exception {
        List<RestaurantMenuReadV1Client.RestaurantSummary> results =
                restaurantReadClient.searchRestaurants(query);
        List<RestaurantSummary> mapped = new ArrayList<>();
        for (RestaurantMenuReadV1Client.RestaurantSummary result : results) {
            mapped.add(RestaurantSummary.from(result));
        }
        return Collections.unmodifiableList(mapped);
    }

    public RestaurantDetail loadRestaurant(String restaurantId)
            throws Exception {
        return RestaurantDetail.from(restaurantReadClient.loadRestaurant(restaurantId));
    }

    public PublicProductNutrition loadPublicProductNutrition(String catalogProductId)
            throws Exception {
        NutritionPublicNutritionClient.PublicProductNutrition nutrition =
                publicNutritionClient.fetch(nutritionConfig, catalogProductId);
        if (nutrition == null) {
            return null;
        }
        return new PublicProductNutrition(
                nutrition.catalogProductId,
                nutrition.nutritionFoodId,
                nutrition.name,
                nutrition.basisUnit,
                nutrition.basisAmount,
                nutrition.profile,
                nutrition.catalogProductRevision,
                nutrition.catalogContentAmount,
                nutrition.catalogContentUnit,
                nutrition.catalogPackageCount
        );
    }

    public SyncResult syncCatalog(SupabaseConfig configuredNutrition) throws Exception {
        SupabaseConfig active = refreshNutrition(configuredNutrition);
        NutritionCatalogSyncClient.SyncResult result = syncClient.sync(
                active,
                nutritionCatalogSync
        );
        return new SyncResult(active, result.pushedRows, result.pulledRows);
    }

    public PublicationResult publishNutrition(
            SupabaseConfig configuredNutrition,
            String nutritionFoodId,
            String catalogProductId,
            boolean publish
    ) throws Exception {
        SupabaseConfig active = requireNutritionAccount(configuredNutrition);
        NutritionPublicationClient.PublicationState state = publicationClient.publishProductNutrition(
                active,
                nutritionFoodId,
                catalogProductId,
                publish
        );
        applyLocalPublication(active, state);
        return new PublicationResult(active, null, state);
    }

    public PublicationResult publishDiningOut(
            SupabaseConfig configuredNutrition,
            SupabaseConfig configuredPriceTrace,
            String nutritionFoodId,
            boolean publish
    ) throws Exception {
        SupabaseConfig activeNutrition = requireNutritionAccount(configuredNutrition);
        SupabaseConfig activePriceTrace = configuredPriceTrace;
        if (publish) {
            activePriceTrace = requirePriceTraceAccount(configuredPriceTrace);
        }
        NutritionFood food = nutritionCatalog.findFoodById(nutritionFoodId);
        NutritionPublicationClient.PublicationState state = publicationClient.publishDiningOut(
                activeNutrition,
                activePriceTrace,
                food,
                publish
        );
        applyLocalPublication(activeNutrition, state);
        return new PublicationResult(activeNutrition, activePriceTrace, state);
    }

    private void applyLocalPublication(
            SupabaseConfig activeNutrition,
            NutritionPublicationClient.PublicationState state
    ) throws IOException {
        String ownerId = activeNutrition.effectiveUserId();
        if (state.sourceReference != null
                && !nutritionCatalog.applySourceReference(
                state.nutritionFoodId,
                ownerId,
                state.sourceReference,
                state.sourceReferenceUpdatedAt == null
                        ? state.updatedAt
                        : state.sourceReferenceUpdatedAt
        )) {
            throw new IOException("FT 식당 메뉴 identity를 기기 카탈로그에 반영하지 못했습니다.");
        }
        if (!nutritionCatalog.applyPublicationVisibility(
                state.nutritionFoodId,
                ownerId,
                state.isPublic ? "public" : "private",
                state.updatedAt
        )) {
            throw new IOException("공개된 영양정보를 기기 카탈로그에 반영하지 못했습니다.");
        }
    }

    private SupabaseConfig refreshNutrition(SupabaseConfig configured) throws Exception {
        if (configured == null) {
            throw new IllegalStateException("영양 DB 연결 설정이 없습니다.");
        }
        SupabaseConfig active = configured;
        if (active.isConfigured()) {
            active = nutritionAuth.refresh(active);
            nutritionCatalog.setUserId(active.effectiveUserId());
        }
        nutritionConfig = active;
        return active;
    }

    private SupabaseConfig requireNutritionAccount(SupabaseConfig configured) throws Exception {
        SupabaseConfig active = refreshNutrition(configured);
        if (!active.isConfigured()) {
            throw new IllegalStateException("영양 DB 계정 로그인이 필요합니다.");
        }
        return active;
    }

    private SupabaseConfig requirePriceTraceAccount(SupabaseConfig configured) throws Exception {
        if (configured == null || !configured.isConfigured()) {
            throw new IllegalStateException("PT 관리자 계정 로그인이 필요합니다.");
        }
        SupabaseConfig active = priceTraceAuth.refresh(configured);
        productReadClient.setConfig(active);
        restaurantReadClient.setConfig(active);
        return active;
    }

    public static final class SyncResult {
        public final SupabaseConfig nutritionConfig;
        public final int pushedRows;
        public final int pulledRows;

        private SyncResult(SupabaseConfig nutritionConfig, int pushedRows, int pulledRows) {
            this.nutritionConfig = nutritionConfig;
            this.pushedRows = pushedRows;
            this.pulledRows = pulledRows;
        }
    }

    public static final class PublicationResult {
        public final SupabaseConfig nutritionConfig;
        public final SupabaseConfig priceTraceConfig;
        public final PublicationState state;

        private PublicationResult(
                SupabaseConfig nutritionConfig,
                SupabaseConfig priceTraceConfig,
                NutritionPublicationClient.PublicationState state
        ) {
            this.nutritionConfig = nutritionConfig;
            this.priceTraceConfig = priceTraceConfig;
            this.state = new PublicationState(
                    state.nutritionFoodId,
                    state.catalogProductId,
                    state.isPublic,
                    state.publicationRevision,
                    state.publishedAt
            );
        }
    }

    public static final class PublicationState {
        public final String nutritionFoodId;
        public final String catalogProductId;
        public final boolean isPublic;
        public final int publicationRevision;
        public final String publishedAt;

        private PublicationState(
                String nutritionFoodId,
                String catalogProductId,
                boolean isPublic,
                int publicationRevision,
                String publishedAt
        ) {
            this.nutritionFoodId = nutritionFoodId;
            this.catalogProductId = catalogProductId;
            this.isPublic = isPublic;
            this.publicationRevision = publicationRevision;
            this.publishedAt = publishedAt;
        }
    }

    public static final class PublicProductNutrition {
        public final String catalogProductId;
        public final String nutritionFoodId;
        public final String name;
        public final String basisUnit;
        public final double basisAmount;
        public final NutritionProfile profile;
        public final String catalogProductRevision;
        public final double catalogContentAmount;
        public final String catalogContentUnit;
        public final int catalogPackageCount;

        private PublicProductNutrition(
                String catalogProductId,
                String nutritionFoodId,
                String name,
                String basisUnit,
                double basisAmount,
                NutritionProfile profile,
                String catalogProductRevision,
                double catalogContentAmount,
                String catalogContentUnit,
                int catalogPackageCount
        ) {
            this.catalogProductId = catalogProductId;
            this.nutritionFoodId = nutritionFoodId;
            this.name = name;
            this.basisUnit = basisUnit;
            this.basisAmount = basisAmount;
            this.profile = profile;
            this.catalogProductRevision = catalogProductRevision;
            this.catalogContentAmount = catalogContentAmount;
            this.catalogContentUnit = catalogContentUnit;
            this.catalogPackageCount = catalogPackageCount;
        }

        public boolean hasRequiredNutrition() {
            return profile != null && profile.hasAllRequired();
        }
    }

    public static final class RestaurantSummary {
        public final String restaurantId;
        public final String restaurantName;
        public final List<RestaurantLocation> locations;

        private RestaurantSummary(
                String restaurantId,
                String restaurantName,
                List<RestaurantLocation> locations
        ) {
            this.restaurantId = restaurantId;
            this.restaurantName = restaurantName;
            this.locations = Collections.unmodifiableList(locations);
        }

        private static RestaurantSummary from(RestaurantMenuReadV1Client.RestaurantSummary value) {
            List<RestaurantLocation> mapped = new ArrayList<>();
            for (RestaurantMenuReadV1Client.RestaurantLocation location : value.locations) {
                mapped.add(RestaurantLocation.from(location));
            }
            return new RestaurantSummary(value.restaurantId, value.restaurantName, mapped);
        }
    }

    public static final class RestaurantDetail {
        public final String restaurantId;
        public final String restaurantName;
        public final List<RestaurantLocation> locations;
        public final List<RestaurantMenu> menus;

        private RestaurantDetail(
                String restaurantId,
                String restaurantName,
                List<RestaurantLocation> locations,
                List<RestaurantMenu> menus
        ) {
            this.restaurantId = restaurantId;
            this.restaurantName = restaurantName;
            this.locations = Collections.unmodifiableList(locations);
            this.menus = Collections.unmodifiableList(menus);
        }

        private static RestaurantDetail from(RestaurantMenuReadV1Client.RestaurantDetail value) {
            List<RestaurantLocation> mappedLocations = new ArrayList<>();
            for (RestaurantMenuReadV1Client.RestaurantLocation location : value.locations) {
                mappedLocations.add(RestaurantLocation.from(location));
            }
            List<RestaurantMenu> mappedMenus = new ArrayList<>();
            for (RestaurantMenuReadV1Client.RestaurantMenu menu : value.menus) {
                mappedMenus.add(RestaurantMenu.from(menu));
            }
            return new RestaurantDetail(
                    value.restaurantId,
                    value.restaurantName,
                    mappedLocations,
                    mappedMenus
            );
        }
    }

    public static final class RestaurantLocation {
        public final String restaurantLocationId;
        public final String branchName;
        public final String locationSourceNamespace;
        public final String sourceNamespace;
        public final String sourceLocationCode;

        private RestaurantLocation(
                String restaurantLocationId,
                String branchName,
                String locationSourceNamespace,
                String sourceLocationCode
        ) {
            this.restaurantLocationId = restaurantLocationId;
            this.branchName = branchName;
            this.locationSourceNamespace = locationSourceNamespace;
            this.sourceNamespace = locationSourceNamespace;
            this.sourceLocationCode = sourceLocationCode;
        }

        private static RestaurantLocation from(RestaurantMenuReadV1Client.RestaurantLocation value) {
            return new RestaurantLocation(
                    value.restaurantLocationId,
                    value.branchName,
                    value.locationSourceNamespace,
                    value.sourceLocationCode
            );
        }
    }

    public static final class RestaurantMenu {
        public final String restaurantMenuId;
        public final String catalogProductId;
        public final String menuName;

        private RestaurantMenu(
                String restaurantMenuId,
                String catalogProductId,
                String menuName
        ) {
            this.restaurantMenuId = restaurantMenuId;
            this.catalogProductId = catalogProductId;
            this.menuName = menuName;
        }

        private static RestaurantMenu from(RestaurantMenuReadV1Client.RestaurantMenu value) {
            return new RestaurantMenu(
                    value.restaurantMenuId,
                    value.catalogProductId,
                    value.menuName
            );
        }
    }
}
