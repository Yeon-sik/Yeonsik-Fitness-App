package com.yeonsik.fitnessapp.ui;

import android.graphics.Color;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolylineOptions;
import com.yeonsik.fitnessapp.BuildConfig;
import com.yeonsik.fitnessapp.cardio.CardioActivityType;
import com.yeonsik.fitnessapp.cardio.CardioMetrics;
import com.yeonsik.fitnessapp.cardio.CardioRepository;
import com.yeonsik.fitnessapp.cardio.CardioRouteProjection;
import com.yeonsik.fitnessapp.state.FitnessScreen;

/** 완료된 GPS 유산소의 로컬 거리·시간·측정 품질과 이동 경로 요약. */
public final class CardioSummaryScreen extends BaseScreen implements OnMapReadyCallback {
    private static final int MAP_HEIGHT_DP = 280;
    private static final int ROUTE_COLOR = Color.rgb(0, 122, 255);

    private MapView mapView;
    private GoogleMap googleMap;
    private boolean activityResumed;
    private boolean mapResumed;
    private String routeRecordId;
    private boolean routeLoading;
    private boolean routeLoadError;
    private CardioRouteProjection routeProjection;
    private CardioRouteProjection renderedProjection;

    public CardioSummaryScreen(ScreenHost host) {
        super(host);
    }

    @Override
    public void render() {
        String recordId = host.sessionState().activeRecordId();
        CardioRepository.SessionSnapshot snapshot = host.cardioRepository().session(recordId);

        screenHeader("완료 기록", snapshot == null ? "유산소 요약" : snapshot.activityType.labelKo());
        if (snapshot == null) {
            releaseMap();
            emptyState("이 기기에서 GPS 세부 기록을 찾지 못했습니다.",
                    "공유된 운동 요약은 일반 운동 기록에서 확인할 수 있습니다.");
            add(ui().button("기록으로 돌아가기", false,
                    v -> host.navigate(FitnessScreen.RECORDS)), ui().fullWidthParams(0));
            return;
        }

        prepareRoute(recordId);

        int elapsedSeconds = snapshot.elapsedSeconds(System.currentTimeMillis());
        LinearLayout firstRow = ui().tileRow();
        firstRow.addView(ui().statTile(
                "이동 거리",
                CardioMetrics.formatDistanceKilometers(snapshot.distanceMeters),
                "km",
                true,
                null
        ), ui().tileParams(true));
        firstRow.addView(ui().statTile(
                "운동 시간",
                CardioMetrics.formatElapsed(elapsedSeconds),
                "일시정지 제외",
                false,
                null
        ), ui().tileParams(false));
        add(firstRow, ui().fullWidthParams(0));

        LinearLayout secondRow = ui().tileRow();
        boolean cycling = snapshot.activityType == CardioActivityType.CYCLING;
        secondRow.addView(ui().statTile(
                "평균 심박수",
                CardioMetrics.formatAverageHeartRate(snapshot.averageHeartRateBpm),
                CardioMetrics.hasAverageHeartRate(snapshot.averageHeartRateBpm)
                        ? "bpm · 수동 입력" : "수동 입력 없음",
                false,
                null
        ), ui().tileParams(true));
        secondRow.addView(ui().statTile(
                cycling ? "평균 속도" : "평균 페이스",
                cycling
                        ? CardioMetrics.formatAverageSpeed(elapsedSeconds, snapshot.distanceMeters)
                        : CardioMetrics.formatAveragePace(elapsedSeconds, snapshot.distanceMeters),
                cycling ? "km/h" : "분/km",
                false,
                null
        ), ui().tileParams(false));
        add(secondRow, ui().fullWidthParams(ui().dp(10)));

        section("이동 경로");
        renderRoute(recordId);

        section("데이터 범위");
        emptyState(
                "GPS 위치 " + snapshot.acceptedPointCount
                        + "개를 반영했고 거리·시간"
                        + (CardioMetrics.hasAverageHeartRate(snapshot.averageHeartRateBpm)
                        ? "·평균 심박수" : "")
                        + " 요약을 운동 기록으로 저장했습니다.",
                "원시 위·경도 경로는 이 기기에만 남고 Supabase 동기화 대상에서 제외됩니다."
        );

        add(ui().button(
                        CardioMetrics.hasAverageHeartRate(snapshot.averageHeartRateBpm)
                                ? "평균 심박수 수정" : "평균 심박수 입력",
                        true,
                        v -> host.editCardioAverageHeartRate()),
                ui().fullWidthParams(0));
        buttonRow(
                ui().button("기록 보기", false, v -> host.navigate(FitnessScreen.RECORDS)),
                ui().button("유산소", false, v -> host.navigate(FitnessScreen.CARDIO)),
                ui().dp(8)
        );
        add(ui().button("메인", false, v -> host.navigate(FitnessScreen.HOME)),
                ui().fullWidthParams(ui().dp(8)));
    }

    private void prepareRoute(String recordId) {
        if (recordId.equals(routeRecordId)) {
            return;
        }
        releaseMap();
        routeRecordId = recordId;
        routeLoading = false;
        routeLoadError = false;
        routeProjection = null;
        renderedProjection = null;
    }

    private void renderRoute(String recordId) {
        if (!BuildConfig.MAPS_API_KEY_CONFIGURED) {
            emptyState(
                    "Google Maps API 키가 설정되지 않아 이동 경로를 표시할 수 없습니다.",
                    "local.properties에 MAPS_API_KEY를 추가한 뒤 다시 빌드하세요."
            );
            return;
        }
        if (routeLoadError) {
            emptyState(
                    "이동 경로를 불러오지 못했습니다.",
                    "GPS 거리·시간 기록은 유지되며 지도 표시만 건너뜁니다."
            );
            return;
        }
        if (routeProjection == null) {
            emptyState(
                    "이동 경로를 불러오는 중입니다.",
                    "저장된 GPS 좌표를 지도 표시용으로 변환합니다."
            );
            requestRoute(recordId);
            return;
        }
        if (!routeProjection.hasRenderablePath()) {
            emptyState(
                    "지도에 표시할 이동 경로가 없습니다.",
                    "반영된 GPS 위치가 2개 미만입니다."
            );
            return;
        }
        if (!addRouteMap()) {
            emptyState(
                    "Google 지도를 초기화하지 못했습니다.",
                    "경로 데이터는 이 기기에 남아 있으며 지도만 표시하지 않습니다."
            );
        }
    }

    private void requestRoute(String recordId) {
        if (routeLoading) {
            return;
        }
        routeLoading = true;
        host.loadCardioRoute(recordId, new ScreenHost.CardioRouteCallback() {
            @Override
            public void onComplete(CardioRouteProjection projection) {
                if (!isCurrentRoute(recordId)) {
                    return;
                }
                routeLoading = false;
                routeProjection = projection;
                routeLoadError = false;
                host.rerender();
            }

            @Override
            public void onError(Exception error) {
                if (!isCurrentRoute(recordId)) {
                    return;
                }
                routeLoading = false;
                routeLoadError = true;
                host.rerender();
            }
        });
    }

    private boolean isCurrentRoute(String recordId) {
        return host.currentScreen() == FitnessScreen.CARDIO_SUMMARY
                && recordId.equals(routeRecordId)
                && recordId.equals(host.sessionState().activeRecordId());
    }

    private boolean addRouteMap() {
        if (!ensureMapView()) {
            return false;
        }
        detachMapView();
        LinearLayout mapCard = ui().card();
        mapCard.setPadding(ui().dp(8), ui().dp(8), ui().dp(8), ui().dp(8));
        mapCard.addView(mapView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ui().dp(MAP_HEIGHT_DP)
        ));
        add(mapCard, ui().fullWidthParams(ui().dp(10)));
        renderProjectionOnMap();
        return true;
    }

    private boolean ensureMapView() {
        if (mapView != null) {
            return true;
        }
        try {
            mapView = new MapView(host.activity());
            mapView.onCreate(null);
            mapView.getMapAsync(this);
            return true;
        } catch (RuntimeException error) {
            mapView = null;
            googleMap = null;
            return false;
        }
    }

    private void detachMapView() {
        if (mapView == null) {
            return;
        }
        ViewParent parent = mapView.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(mapView);
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        if (mapView == null) {
            return;
        }
        googleMap.getUiSettings().setMapToolbarEnabled(false);
        googleMap.getUiSettings().setCompassEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        renderProjectionOnMap();
    }

    private void renderProjectionOnMap() {
        if (googleMap == null || mapView == null || routeProjection == null
                || routeProjection == renderedProjection) {
            return;
        }

        googleMap.clear();
        LatLngBounds.Builder boundsBuilder = LatLngBounds.builder();
        LatLng firstPoint = null;
        int pointCount = 0;
        for (java.util.List<CardioRouteProjection.RoutePoint> segment
                : routeProjection.segments()) {
            PolylineOptions line = new PolylineOptions()
                    .color(ROUTE_COLOR)
                    .width(7f)
                    .geodesic(false);
            for (CardioRouteProjection.RoutePoint point : segment) {
                LatLng latLng = new LatLng(point.latitude, point.longitude);
                line.add(latLng);
                boundsBuilder.include(latLng);
                if (firstPoint == null) {
                    firstPoint = latLng;
                }
                pointCount++;
            }
            if (segment.size() >= 2) {
                googleMap.addPolyline(line);
            }
        }
        renderedProjection = routeProjection;
        LatLng finalFirstPoint = firstPoint;
        int renderedPointCount = pointCount;
        LatLngBounds bounds = boundsBuilder.build();
        mapView.post(() -> {
            if (mapView == null || mapView.getWidth() <= 0 || mapView.getHeight() <= 0) {
                return;
            }
            if (renderedPointCount == 1 && finalFirstPoint != null) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(finalFirstPoint, 16f));
            } else {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(
                        bounds,
                        ui().dp(24)
                ));
            }
        });
    }

    @Override
    public void onVisible() {
        resumeMapIfNeeded();
    }

    @Override
    public void onResume() {
        activityResumed = true;
        resumeMapIfNeeded();
    }

    @Override
    public void onPause() {
        activityResumed = false;
        pauseMapIfNeeded();
    }

    @Override
    public void onHidden() {
        releaseMap();
        routeRecordId = null;
        routeLoading = false;
        routeProjection = null;
        renderedProjection = null;
    }

    @Override
    public void onDestroy() {
        releaseMap();
    }

    @Override
    public void onLowMemory() {
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    private void resumeMapIfNeeded() {
        if (activityResumed && mapView != null && !mapResumed) {
            mapView.onResume();
            mapResumed = true;
        }
    }

    private void pauseMapIfNeeded() {
        if (mapView != null && mapResumed) {
            mapView.onPause();
            mapResumed = false;
        }
    }

    private void releaseMap() {
        pauseMapIfNeeded();
        if (mapView != null) {
            mapView.onDestroy();
        }
        mapView = null;
        googleMap = null;
        renderedProjection = null;
    }
}
