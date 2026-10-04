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
import android.os.HandlerThread;
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
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider;
import com.yeonsik.fitnessapp.feature.cardio.data.CardioRepository;

import com.yeonsik.fitness.shared.feature.cardio.model.CardioLocationSample;

import java.util.concurrent.Executor;

/** 화면이 꺼지거나 앱이 백그라운드로 이동해도 GPS 유산소를 계속 추적한다. */
public final class CardioTrackingService extends Service {
    public static final String ACTION_START = "com.yeonsik.fitnessapp.cardio.START";
    public static final String ACTION_PAUSE = "com.yeonsik.fitnessapp.cardio.PAUSE";
    public static final String ACTION_RESUME = "com.yeonsik.fitnessapp.cardio.RESUME";
    public static final String ACTION_CANCEL = "com.yeonsik.fitnessapp.cardio.CANCEL";
    public static final String EXTRA_RECORD_ID = "cardio_record_id";
    public static final String EXTRA_FINISH_REQUESTED = "cardio_finish_requested";

    private static final String TAG = "CardioTracking";
    private static final String CHANNEL_ID = "cardio_tracking";
    private static final int NOTIFICATION_ID = 2401;
    private static final long LOCATION_INTERVAL_MS = 3_000L;
    private static final long MIN_LOCATION_INTERVAL_MS = 1_500L;
    private static final long NOTIFICATION_REFRESH_MS = 5_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private HandlerThread trackingThread;
    private Handler handler;
    private Executor trackingExecutor;
    private volatile boolean destroyed;
    private volatile int latestStartId;
    // These fields and every repository call are owned by trackingThread.
    private int trackingStartId;
    private final Runnable notificationTicker = new Runnable() {
        @Override
        public void run() {
            if (destroyed || currentRecordId == null) {
                return;
            }
            CardioRepository.SessionSnapshot snapshot = cardioRepository.session(currentRecordId);
            if (snapshot == null || CardioRepository.STATUS_COMPLETED.equals(snapshot.status)) {
                stopTrackingService();
                return;
            }
            updateNotification(snapshot);
            if (!destroyed) {
                handler.postDelayed(this, NOTIFICATION_REFRESH_MS);
            }
        }
    };

    private FusedLocationProviderClient locationClient;
    private CardioRepository cardioRepository;
    private String currentRecordId;
    private LocationCallback locationCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        SupabaseConfig config = new SupabaseConfigStore(this).load();
        String ownerId = config.effectiveUserId();
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
        trackingThread = new HandlerThread("CardioTrackingWorker");
        trackingThread.start();
        handler = new Handler(trackingThread.getLooper());
        trackingExecutor = command -> handler.post(() -> {
            if (!destroyed) {
                command.run();
            }
        });
        trackingExecutor.execute(() -> {
            FitnessRoomDatabase roomDatabase = FitnessRoomDatabaseProvider.get(this);
            cardioRepository = new CardioRepository(roomDatabase, ownerId);
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        String requestedRecordId = intent == null ? null : intent.getStringExtra(EXTRA_RECORD_ID);
        latestStartId = startId;
        boolean foregroundAllowed = true;
        if (!ACTION_CANCEL.equals(action)) {
            // A Room read may need to open/migrate the database. Meet the foreground
            // service deadline before queuing any reads, including sticky recovery.
            try {
                startAsForeground(buildStartingNotification(requestedRecordId));
            } catch (SecurityException error) {
                foregroundAllowed = false;
                Log.e(TAG, "위치 권한이 없어 GPS 추적을 시작하지 못했습니다.", error);
            }
        }
        boolean canRunInForeground = foregroundAllowed;
        trackingExecutor.execute(() -> handleStartCommand(
                action, requestedRecordId, startId, canRunInForeground));
        return ACTION_CANCEL.equals(action) ? START_NOT_STICKY : START_STICKY;
    }

    private void handleStartCommand(
            String action,
            String requestedRecordId,
            int startId,
            boolean foregroundAllowed
    ) {
        trackingStartId = startId;
        if (ACTION_CANCEL.equals(action)) {
            // Cross-feature cancellation is owned by CardioSessionApplicationService;
            // this platform callback only stops GPS delivery.
            stopTrackingService();
            return;
        }
        if (requestedRecordId != null && !requestedRecordId.trim().isEmpty()) {
            if (!requestedRecordId.equals(currentRecordId)) {
                removeLocationUpdates();
            }
            currentRecordId = requestedRecordId;
        }
        if (currentRecordId == null) {
            CardioRepository.SessionSnapshot active = cardioRepository.activeSession();
            currentRecordId = active == null ? null : active.recordId;
        }
        if (currentRecordId == null) {
            stopTrackingService();
            return;
        }

        CardioRepository.SessionSnapshot requestedSession = cardioRepository.session(currentRecordId);
        if (requestedSession == null || !requestedSession.usesGps()) {
            stopTrackingService();
            return;
        }

        if (!foregroundAllowed) {
            cardioRepository.pause(currentRecordId);
            cardioRepository.setGpsStatus(currentRecordId, CardioRepository.GPS_PERMISSION_MISSING);
            stopTrackingService();
            return;
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
            return;
        }
        if (destroyed) {
            return;
        }
        updateNotification(snapshot);
        if (CardioRepository.STATUS_TRACKING.equals(snapshot.status)) {
            requestLocationUpdates();
        } else {
            removeLocationUpdates();
        }
        if (currentRecordId != null && !destroyed) {
            restartNotificationTicker();
        }
    }

    @Override
    public void onDestroy() {
        // Reject late tasks first. Cleanup remains on the same worker as location
        // registration; clearing its queue prevents a pending start from reviving GPS.
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        handler.removeCallbacksAndMessages(null);
        handler.post(() -> {
            removeLocationUpdates();
            currentRecordId = null;
            trackingThread.quitSafely();
        });
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void requestLocationUpdates() {
        if (destroyed || locationCallback != null) {
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
                .setMaxUpdateAgeMillis(LOCATION_INTERVAL_MS)
                .setWaitForAccurateLocation(false)
                .build();
        String recordId = currentRecordId;
        LocationCallback callback = createLocationCallback(recordId);
        try {
            locationCallback = callback;
            locationClient.requestLocationUpdates(request, callback, trackingThread.getLooper())
                    .addOnCompleteListener(Runnable::run, task -> {
                        if (destroyed) {
                            locationClient.removeLocationUpdates(callback);
                            return;
                        }
                        trackingExecutor.execute(() -> {
                            if (!isCurrentLocationCallback(callback, recordId)) {
                                // Registration can finish after pause, stop, or a new
                                // session. Remove the old callback again in that case.
                                locationClient.removeLocationUpdates(callback);
                                return;
                            }
                            if (!task.isSuccessful()) {
                                removeLocationUpdates();
                                cardioRepository.setGpsStatus(
                                        recordId, CardioRepository.GPS_UNAVAILABLE);
                                Log.e(TAG, "위치 업데이트를 시작하지 못했습니다.", task.getException());
                            }
                        });
                    });
        } catch (SecurityException error) {
            removeLocationUpdates();
            cardioRepository.pause(currentRecordId);
            cardioRepository.setGpsStatus(
                    currentRecordId, CardioRepository.GPS_PERMISSION_MISSING);
            Log.e(TAG, "위치 권한이 없어 GPS 추적을 중단했습니다.", error);
            stopTrackingService();
        }
    }

    private void removeLocationUpdates() {
        if (locationCallback == null || locationClient == null) {
            return;
        }
        LocationCallback callback = locationCallback;
        locationCallback = null;
        locationClient.removeLocationUpdates(callback);
    }

    private boolean isCurrentLocationCallback(LocationCallback callback, String recordId) {
        return !destroyed && callback == locationCallback && recordId.equals(currentRecordId);
    }

    private LocationCallback createLocationCallback(String recordId) {
        return new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (!isCurrentLocationCallback(this, recordId) || locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    if (destroyed) {
                        return;
                    }
                    Float speed = location.hasSpeed() ? location.getSpeed() : null;
                    cardioRepository.acceptLocation(recordId, new CardioLocationSample(
                            location.getLatitude(), location.getLongitude(),
                            location.getAccuracy(), location.getTime(), speed));
                }
                CardioRepository.SessionSnapshot snapshot = cardioRepository.session(recordId);
                if (snapshot == null || CardioRepository.STATUS_COMPLETED.equals(snapshot.status)) {
                    stopTrackingService();
                } else {
                    updateNotification(snapshot);
                }
            }

            @Override
            public void onLocationAvailability(LocationAvailability availability) {
                if (isCurrentLocationCallback(this, recordId)
                        && availability != null && !availability.isLocationAvailable()) {
                    cardioRepository.setGpsStatus(recordId, CardioRepository.GPS_UNAVAILABLE);
                }
            }
        };
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

    private void startAsForeground(Notification notification) {
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
        if (destroyed) {
            return;
        }
        int notificationStartId = trackingStartId;
        mainHandler.post(() -> {
            // PendingIntent creation updates shared extras too; keep it behind the
            // same lifecycle guard as publication, onDestroy and foreground/stop.
            if (!destroyed && latestStartId == notificationStartId) {
                getSystemService(NotificationManager.class)
                        .notify(NOTIFICATION_ID, buildNotification(snapshot));
            }
        });
    }

    private Notification buildNotification(CardioRepository.SessionSnapshot snapshot) {
        boolean paused = CardioRepository.STATUS_PAUSED.equals(snapshot.status);
        String title = snapshot.activityType.labelKo() + (paused ? " · 일시정지" : " 기록 중");
        String content = CardioMetrics.formatDistanceKilometers(snapshot.distanceMeters)
                + " km · " + CardioMetrics.formatElapsed(
                snapshot.elapsedSeconds(System.currentTimeMillis()));

        String toggleAction = paused ? ACTION_RESUME : ACTION_PAUSE;
        String toggleLabel = paused ? "재개" : "일시정지";
        return notificationBuilder(snapshot.recordId, title, content)
                .addAction(new Notification.Action.Builder(
                        null,
                        toggleLabel,
                        servicePendingIntent(toggleAction, snapshot.recordId, 1)
                ).build())
                .addAction(new Notification.Action.Builder(
                        null,
                        "완료",
                        completionPendingIntent(snapshot.recordId)
                ).build())
                .build();
    }

    private Notification buildStartingNotification(String recordId) {
        return notificationBuilder(recordId, "유산소 GPS 기록", "진행 중인 기록을 확인하고 있습니다.")
                .build();
    }

    private Notification.Builder notificationBuilder(String recordId, String title, String content) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_RECORD_ID, recordId);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String notificationCategory = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? Notification.CATEGORY_WORKOUT
                : Notification.CATEGORY_SERVICE;
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setCategory(notificationCategory);
    }

    private PendingIntent completionPendingIntent(String recordId) {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_RECORD_ID, recordId)
                .putExtra(EXTRA_FINISH_REQUESTED, true);
        return PendingIntent.getActivity(
                this,
                2,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
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
        int stoppingStartId = trackingStartId;
        mainHandler.post(() -> {
            // An older command must not stop a newer queued start/resume.
            if (!destroyed && latestStartId == stoppingStartId) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelfResult(stoppingStartId);
            }
        });
    }

}
