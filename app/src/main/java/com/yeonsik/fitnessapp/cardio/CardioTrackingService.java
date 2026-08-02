package com.yeonsik.fitnessapp.cardio;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.yeonsik.fitnessapp.MainActivity;
import com.yeonsik.fitnessapp.R;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.config.SupabaseConfigStore;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.FitnessRepository;

import java.util.Locale;

/** 화면이 꺼지거나 앱이 백그라운드로 이동해도 GPS 유산소를 계속 추적한다. */
public final class CardioTrackingService extends Service {
    public static final String ACTION_START = "com.yeonsik.fitnessapp.cardio.START";
    public static final String ACTION_PAUSE = "com.yeonsik.fitnessapp.cardio.PAUSE";
    public static final String ACTION_RESUME = "com.yeonsik.fitnessapp.cardio.RESUME";
    public static final String ACTION_STOP = "com.yeonsik.fitnessapp.cardio.STOP";
    public static final String ACTION_CANCEL = "com.yeonsik.fitnessapp.cardio.CANCEL";
    public static final String EXTRA_RECORD_ID = "cardio_record_id";

    private static final String TAG = "CardioTracking";
    private static final String CHANNEL_ID = "cardio_tracking";
    private static final int NOTIFICATION_ID = 2401;
    private static final long LOCATION_INTERVAL_MS = 3_000L;
    private static final long MIN_LOCATION_INTERVAL_MS = 1_500L;
    private static final long NOTIFICATION_REFRESH_MS = 5_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable notificationTicker = new Runnable() {
        @Override
        public void run() {
            if (currentRecordId == null) {
                return;
            }
            CardioRepository.SessionSnapshot snapshot = cardioRepository.session(currentRecordId);
            if (snapshot == null || CardioRepository.STATUS_COMPLETED.equals(snapshot.status)) {
                stopTrackingService();
                return;
            }
            updateNotification(snapshot);
            handler.postDelayed(this, NOTIFICATION_REFRESH_MS);
        }
    };

    private FusedLocationProviderClient locationClient;
    private CardioRepository cardioRepository;
    private String currentRecordId;
    private boolean requestingLocation;

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            if (currentRecordId == null || locationResult == null) {
                return;
            }
            for (Location location : locationResult.getLocations()) {
                Float speed = location.hasSpeed() ? location.getSpeed() : null;
                cardioRepository.acceptLocation(
                        currentRecordId,
                        new CardioLocationSample(
                                location.getLatitude(),
                                location.getLongitude(),
                                location.getAccuracy(),
                                location.getTime(),
                                speed
                        )
                );
            }
            CardioRepository.SessionSnapshot snapshot = cardioRepository.session(currentRecordId);
            if (snapshot != null) {
                updateNotification(snapshot);
            }
        }

        @Override
        public void onLocationAvailability(LocationAvailability availability) {
            if (currentRecordId == null || availability == null) {
                return;
            }
            if (!availability.isLocationAvailable()) {
                cardioRepository.setGpsStatus(currentRecordId, CardioRepository.GPS_UNAVAILABLE);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        FitnessDatabaseHelper databaseHelper = new FitnessDatabaseHelper(this);
        SupabaseConfig config = new SupabaseConfigStore(this).load();
        FitnessRepository fitnessRepository = new FitnessRepository(
                databaseHelper, config.effectiveUserId());
        cardioRepository = new CardioRepository(databaseHelper, fitnessRepository);
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        String requestedRecordId = intent == null ? null : intent.getStringExtra(EXTRA_RECORD_ID);
        if (requestedRecordId != null && !requestedRecordId.trim().isEmpty()) {
            currentRecordId = requestedRecordId;
        }
        if (currentRecordId == null) {
            CardioRepository.SessionSnapshot active = cardioRepository.activeSession();
            currentRecordId = active == null ? null : active.recordId;
        }
        if (currentRecordId == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_STOP.equals(action)) {
            cardioRepository.finish(currentRecordId);
            stopTrackingService();
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(action)) {
            cardioRepository.cancel(currentRecordId);
            stopTrackingService();
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(action)) {
            cardioRepository.pause(currentRecordId);
            removeLocationUpdates();
        } else if (ACTION_RESUME.equals(action)) {
            cardioRepository.resume(currentRecordId);
        }

        CardioRepository.SessionSnapshot snapshot = cardioRepository.session(currentRecordId);
        if (snapshot == null || CardioRepository.STATUS_COMPLETED.equals(snapshot.status)) {
            stopTrackingService();
            return START_NOT_STICKY;
        }
        startAsForeground(snapshot);
        if (CardioRepository.STATUS_TRACKING.equals(snapshot.status)) {
            requestLocationUpdates();
        } else {
            removeLocationUpdates();
        }
        restartNotificationTicker();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(notificationTicker);
        removeLocationUpdates();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void requestLocationUpdates() {
        if (requestingLocation) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            cardioRepository.pause(currentRecordId);
            cardioRepository.setGpsStatus(currentRecordId, CardioRepository.GPS_PERMISSION_MISSING);
            stopTrackingService();
            return;
        }

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                LOCATION_INTERVAL_MS
        )
                .setMinUpdateIntervalMillis(MIN_LOCATION_INTERVAL_MS)
                .setMinUpdateDistanceMeters(2f)
                .setWaitForAccurateLocation(false)
                .build();
        try {
            requestingLocation = true;
            locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                    .addOnFailureListener(error -> {
                        requestingLocation = false;
                        cardioRepository.setGpsStatus(
                                currentRecordId, CardioRepository.GPS_UNAVAILABLE);
                        Log.e(TAG, "위치 업데이트를 시작하지 못했습니다.", error);
                    });
        } catch (SecurityException error) {
            requestingLocation = false;
            cardioRepository.pause(currentRecordId);
            cardioRepository.setGpsStatus(
                    currentRecordId, CardioRepository.GPS_PERMISSION_MISSING);
            Log.e(TAG, "위치 권한이 없어 GPS 추적을 중단했습니다.", error);
            stopTrackingService();
        }
    }

    private void removeLocationUpdates() {
        if (!requestingLocation || locationClient == null) {
            return;
        }
        requestingLocation = false;
        locationClient.removeLocationUpdates(locationCallback);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "유산소 GPS 추적",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("진행 중인 걷기, 달리기, 자전거 거리 추적 상태");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void startAsForeground(CardioRepository.SessionSnapshot snapshot) {
        Notification notification = buildNotification(snapshot);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(CardioRepository.SessionSnapshot snapshot) {
        getSystemService(NotificationManager.class)
                .notify(NOTIFICATION_ID, buildNotification(snapshot));
    }

    private Notification buildNotification(CardioRepository.SessionSnapshot snapshot) {
        boolean paused = CardioRepository.STATUS_PAUSED.equals(snapshot.status);
        String title = snapshot.activityType.labelKo() + (paused ? " · 일시정지" : " 기록 중");
        String content = formatDistance(snapshot.distanceMeters)
                + " · " + formatElapsed(snapshot.elapsedSeconds(System.currentTimeMillis()));

        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_RECORD_ID, snapshot.recordId);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String toggleAction = paused ? ACTION_RESUME : ACTION_PAUSE;
        String toggleLabel = paused ? "재개" : "일시정지";
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_WORKOUT)
                .addAction(new Notification.Action.Builder(
                        null,
                        toggleLabel,
                        servicePendingIntent(toggleAction, snapshot.recordId, 1)
                ).build())
                .addAction(new Notification.Action.Builder(
                        null,
                        "완료",
                        servicePendingIntent(ACTION_STOP, snapshot.recordId, 2)
                ).build());
        return builder.build();
    }

    private PendingIntent servicePendingIntent(String action, String recordId, int requestCode) {
        Intent intent = new Intent(this, CardioTrackingService.class)
                .setAction(action)
                .putExtra(EXTRA_RECORD_ID, recordId);
        return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void restartNotificationTicker() {
        handler.removeCallbacks(notificationTicker);
        handler.postDelayed(notificationTicker, NOTIFICATION_REFRESH_MS);
    }

    private void stopTrackingService() {
        handler.removeCallbacks(notificationTicker);
        removeLocationUpdates();
        currentRecordId = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private static String formatDistance(double distanceMeters) {
        return String.format(Locale.KOREA, "%.2f km", distanceMeters / 1000d);
    }

    private static String formatElapsed(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.KOREA, "%02d:%02d:%02d", hours, minutes, seconds);
    }
}
