package com.yeonsik.fitnessapp.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.LruCache;
import android.widget.ImageView;

import com.yeonsik.fitnessapp.exercise.ExerciseIllustrationLookup;

/**
 * 운동 목록용 정적 대표 이미지 생성기.
 * 목록에서는 애니메이션 전체를 읽지 않고 카탈로그의 첫 프레임만 작은 비트맵으로 읽는다.
 */
public final class ExerciseIllustrationPreview {
    public static final int SIZE_DP = 64;

    private static final int CACHE_KB = 8 * 1024;
    private static final int DECODE_OVERSAMPLE = 2;

    private final Activity activity;
    private final FitnessUi ui;
    private final LruCache<String, Bitmap> bitmapCache = new LruCache<String, Bitmap>(CACHE_KB) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return Math.max(1, bitmap.getByteCount() / 1024);
        }
    };

    public ExerciseIllustrationPreview(Activity activity, FitnessUi ui) {
        this.activity = activity;
        this.ui = ui;
    }

    /** 이미지가 등록된 운동이면 대표 프레임 ImageView를, 아니면 null을 반환한다. */
    public ImageView create(String exerciseId) {
        int drawableId = ExerciseIllustrationLookup.listPreviewDrawableFor(activity, exerciseId);
        if (drawableId == 0) {
            return null;
        }

        int targetPx = ui.dp(SIZE_DP);
        Bitmap bitmap = previewBitmap(drawableId, targetPx);
        if (bitmap == null) {
            return null;
        }

        ImageView imageView = new ImageView(activity);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setAdjustViewBounds(false);
        imageView.setImageDrawable(new BitmapDrawable(activity.getResources(), bitmap));
        imageView.setImportantForAccessibility(ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO);
        imageView.setFocusable(false);
        return imageView;
    }

    private Bitmap previewBitmap(int drawableId, int targetPx) {
        String cacheKey = drawableId + "@" + targetPx;
        Bitmap cached = bitmapCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(activity.getResources(), drawableId, boundsOptions);
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        decodeOptions.inScaled = false;
        decodeOptions.inSampleSize = sampleSize(
                Math.max(boundsOptions.outWidth, boundsOptions.outHeight),
                targetPx * DECODE_OVERSAMPLE
        );
        Bitmap decoded = BitmapFactory.decodeResource(
                activity.getResources(), drawableId, decodeOptions);
        if (decoded == null) {
            return null;
        }

        Bitmap cropped = cropTransparentMargins(decoded);
        bitmapCache.put(cacheKey, cropped);
        return cropped;
    }

    private int sampleSize(int sourceDimension, int targetDimension) {
        int sample = 1;
        while (sourceDimension / (sample * 2) >= targetDimension) {
            sample *= 2;
        }
        return sample;
    }

    private Bitmap cropTransparentMargins(Bitmap bitmap) {
        Rect bounds = alphaBounds(bitmap);
        if (bounds == null
                || (bounds.left == 0 && bounds.top == 0
                && bounds.right == bitmap.getWidth()
                && bounds.bottom == bitmap.getHeight())) {
            return bitmap;
        }

        try {
            return Bitmap.createBitmap(
                    bitmap,
                    bounds.left,
                    bounds.top,
                    bounds.width(),
                    bounds.height()
            );
        } catch (IllegalArgumentException ignored) {
            return bitmap;
        }
    }

    private Rect alphaBounds(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                if ((row[x] >>> 24) == 0) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return maxX < 0 ? null : new Rect(minX, minY, maxX + 1, maxY + 1);
    }
}
