package com.yeonsik.fitnessapp.ui;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.FitnessRecordContract;
import com.yeonsik.fitnessapp.exercise.ExerciseIllustrationCatalog;
import com.yeonsik.fitnessapp.state.FitnessScreen;
import com.yeonsik.fitnessapp.state.WorkoutSessionState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 종목 세부 화면: 종목 칩 탭 + 운동 자세 이미지 + 세트 편집 그리드(이전 값 참조) + 기록 분석.
 * 세트 입력이 최우선이므로 그리드가 위, 분석(이전 기록/개인 기록/볼륨 추이)이 아래다.
 */
public final class WorkoutExerciseDetailScreen extends BaseScreen {
    private static final int DEFAULT_REST_SECONDS = 90;
    private static final int REST_STEP_SECONDS = 15;
    private static final int ILLUSTRATION_DISPLAY_SCALE_PERCENT = 120;

    /** 이번 종목의 기본 휴식(초). 스탬프 시 타이머와 세트 기록에 쓰인다. */
    private final int[] defaultRestSeconds = {DEFAULT_REST_SECONDS};

    public WorkoutExerciseDetailScreen(ScreenHost host) {
        super(host);
    }

    @Override
    public void render() {
        String recordId = host.sessionState().activeRecordId();
        if (recordId == null) {
            host.navigate(FitnessScreen.WORKOUT_SESSION);
            return;
        }

        List<FitnessRepository.SessionExerciseEntry> exercises = repository().sessionExerciseEntries(recordId);
        if (exercises.isEmpty()) {
            host.navigate(FitnessScreen.WORKOUT_SESSION);
            return;
        }

        FitnessUi ui = ui();
        FitnessRepository.SessionExerciseEntry activeExercise =
                WorkoutSessionState.findActiveExercise(exercises, host.sessionState().activeExerciseId());
        host.sessionState().setActiveExerciseId(activeExercise.id);
        List<FitnessRepository.SessionSetEntry> sets = repository().setsForExercise(activeExercise.id);
        if (sets.isEmpty()) {
            repository().addTypedSet(
                    recordId,
                    activeExercise.id,
                    1,
                    emptySetInput(false, null)
            );
            sets = repository().setsForExercise(activeExercise.id);
        }
        boolean allCompleted = WorkoutSessionState.allSetsCompleted(sets);
        defaultRestSeconds[0] = resolveDefaultRest(sets);

        FitnessRepository.ExerciseHistory lastHistory = repository().lastExerciseHistory(
                activeExercise.exerciseId, activeExercise.name, recordId);
        FitnessRepository.ExerciseBests bests = repository().exerciseBests(
                activeExercise.exerciseId, activeExercise.name, recordId);

        LinearLayout topRow = new LinearLayout(host.activity());
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView backAction = ui.textAction("‹ 세션으로", FitnessUi.COLOR_MUTED,
                () -> host.navigate(FitnessScreen.WORKOUT_SESSION));
        topRow.addView(backAction, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        topRow.addView(ui.textAction("종목 삭제", FitnessUi.COLOR_MUTED,
                () -> confirmDeleteExercise(recordId, activeExercise)));
        add(topRow, ui.fullWidthParams(0));

        add(ui.titleView(activeExercise.name));

        renderExerciseTabs(exercises, activeExercise, allCompleted);
        renderExerciseIllustration(activeExercise);

        section("세트 기록");
        renderExerciseSetEditorCard(recordId, activeExercise, sets, lastHistory);

        section("기록 분석");
        if (supportsLoadRepAnalytics(activeExercise.recordType)) {
            renderPersonalRecordCard(bests, sets);
            add(volumeTrendCard("볼륨 추이", "최근 8회 + 현재",
                    repository().recentExerciseVolumes(
                            activeExercise.exerciseId,
                            activeExercise.name,
                            recordId,
                            8
                    ),
                    currentExerciseVolume(sets)));
        }
        renderLastHistoryCard(activeExercise.recordType, lastHistory);
    }

    // ── 운동 자세 이미지 ───────────────────────────────────────────────

    private void renderExerciseIllustration(FitnessRepository.SessionExerciseEntry exercise) {
        int[] drawableIds = ExerciseIllustrationCatalog.detailDrawablesFor(exercise.exerciseId);
        if (drawableIds.length == 0) {
            return;
        }

        FitnessUi ui = ui();
        ImageView illustration = new ImageView(host.activity());
        int[] durationsMs = ExerciseIllustrationCatalog.frameDurationsMsFor(exercise.exerciseId);
        String illustrationDescription = exercise.name
                + (drawableIds.length > 1 ? " 운동 자세 애니메이션" : " 운동 자세");
        illustration.setContentDescription(illustrationDescription);
        setIllustrationFrames(illustration, drawableIds, durationsMs, illustrationDescription);
        illustration.setScaleType(ImageView.ScaleType.FIT_CENTER);
        illustration.setAdjustViewBounds(false);
        illustration.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        illustration.setPadding(0, ui.dp(4), 0, ui.dp(4));

        int preferredHeightDp = ExerciseIllustrationCatalog.preferredHeightDp(exercise.exerciseId);
        int height = ui.dp(Math.round(preferredHeightDp * ILLUSTRATION_DISPLAY_SCALE_PERCENT / 100f));

        LinearLayout.LayoutParams imageParams = ui.fullWidthParams(ui.dp(8));
        imageParams.height = height;
        add(illustration, imageParams);
    }

    private void setIllustrationFrames(
            ImageView illustration,
            int[] drawableIds,
            int[] durationsMs,
            String baseContentDescription
    ) {
        if (drawableIds.length != durationsMs.length) {
            throw new IllegalArgumentException("운동 자세 프레임과 duration 수가 일치하지 않습니다.");
        }

        Drawable[] frames = displayFramesWithoutTransparentMargins(drawableIds);
        if (frames.length == 1) {
            illustration.setImageDrawable(frames[0]);
            return;
        }

        AnimationDrawable animation = new AnimationDrawable();
        animation.setOneShot(false);
        final boolean[] pausedByUser = {!ValueAnimator.areAnimatorsEnabled()};
        for (int index = 0; index < frames.length; index++) {
            Drawable frame = frames[index];
            if (frame != null) {
                animation.addFrame(frame, durationsMs[index]);
            }
        }
        illustration.setImageDrawable(animation);
        illustration.setClickable(true);
        illustration.setFocusable(true);
        illustration.setContentDescription(
                ValueAnimator.areAnimatorsEnabled()
                        ? baseContentDescription + " 탭하여 일시정지하거나 재생할 수 있습니다."
                        : baseContentDescription + " 시스템 애니메이션이 꺼져 있어 정지되어 있습니다."
        );
        illustration.setOnClickListener(view -> {
            if (!ValueAnimator.areAnimatorsEnabled()) {
                pausedByUser[0] = true;
                animation.stop();
                illustration.setContentDescription(
                        baseContentDescription + " 시스템 애니메이션이 꺼져 있어 정지되어 있습니다."
                );
                return;
            }
            pausedByUser[0] = !pausedByUser[0];
            if (pausedByUser[0]) {
                animation.stop();
                illustration.setContentDescription(
                        baseContentDescription + " 일시정지되었습니다. 탭하여 재생할 수 있습니다."
                );
            } else {
                animation.start();
                illustration.setContentDescription(
                        baseContentDescription + " 재생 중입니다. 탭하여 일시정지할 수 있습니다."
                );
            }
        });
        illustration.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                if (ValueAnimator.areAnimatorsEnabled() && !pausedByUser[0]) {
                    animation.start();
                }
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                animation.stop();
            }
        });
        illustration.post(() -> {
            if (illustration.isAttachedToWindow()
                    && ValueAnimator.areAnimatorsEnabled()
                    && !pausedByUser[0]) {
                animation.start();
            }
        });
    }

    /**
     * 배포 PNG의 투명 캔버스는 그대로 보존하되, 화면 표시 시 실제 그림 영역만 사용한다.
     * Bitmap을 재인코딩하거나 리사이즈하지 않으므로 APK 용량과 원본 픽셀은 변하지 않는다.
     */
    private Drawable[] displayFramesWithoutTransparentMargins(int[] drawableIds) {
        Drawable[] sourceFrames = new Drawable[drawableIds.length];
        Rect union = null;
        for (int index = 0; index < drawableIds.length; index++) {
            Drawable frame = host.activity().getDrawable(drawableIds[index]);
            sourceFrames[index] = frame;
            Rect frameBounds = alphaBounds(frame);
            if (frameBounds == null) {
                return sourceFrames;
            }
            if (union == null) {
                union = new Rect(frameBounds);
            } else {
                union.union(frameBounds);
            }
        }
        if (union == null) {
            return sourceFrames;
        }

        for (int index = 0; index < sourceFrames.length; index++) {
            Drawable frame = sourceFrames[index];
            if (!(frame instanceof BitmapDrawable)) {
                return sourceFrames;
            }
            Bitmap bitmap = ((BitmapDrawable) frame).getBitmap();
            Rect frameCrop = new Rect(
                    Math.max(0, union.left),
                    Math.max(0, union.top),
                    Math.min(bitmap.getWidth(), union.right),
                    Math.min(bitmap.getHeight(), union.bottom)
            );
            if (frameCrop.width() <= 0 || frameCrop.height() <= 0) {
                return sourceFrames;
            }
            sourceFrames[index] = cropBitmapDrawable((BitmapDrawable) frame, frameCrop);
        }
        return sourceFrames;
    }

    private Rect alphaBounds(Drawable drawable) {
        if (!(drawable instanceof BitmapDrawable)) {
            return null;
        }
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
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
                maxY = y;
            }
        }
        return maxX < 0
                ? null
                : new Rect(minX, minY, maxX + 1, maxY + 1);
    }

    private Drawable cropBitmapDrawable(BitmapDrawable source, Rect crop) {
        Bitmap sourceBitmap = source.getBitmap();
        if (crop.left == 0 && crop.top == 0
                && crop.right == sourceBitmap.getWidth()
                && crop.bottom == sourceBitmap.getHeight()) {
            return source;
        }
        Bitmap croppedBitmap;
        try {
            croppedBitmap = Bitmap.createBitmap(
                    sourceBitmap,
                    crop.left,
                    crop.top,
                    crop.width(),
                    crop.height()
            );
        } catch (IllegalArgumentException exception) {
            return source;
        }
        croppedBitmap.setDensity(sourceBitmap.getDensity());
        BitmapDrawable cropped = new BitmapDrawable(host.activity().getResources(), croppedBitmap);
        cropped.setFilterBitmap(true);
        cropped.setDither(true);
        return cropped;
    }

    // ── 종목 탭 ───────────────────────────────────────────────────────

    private void renderExerciseTabs(
            List<FitnessRepository.SessionExerciseEntry> exercises,
            FitnessRepository.SessionExerciseEntry activeExercise,
            boolean allowForwardMove
    ) {
        FitnessUi ui = ui();
        HorizontalScrollView scroller = new HorizontalScrollView(host.activity());
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(host.activity());
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (FitnessRepository.SessionExerciseEntry exercise : exercises) {
            Button tabButton = ui.filterButton(exercise.orderIndex + ". " + exercise.name);
            boolean isActive = exercise.id.equals(activeExercise.id);
            boolean canOpen = exercise.orderIndex <= activeExercise.orderIndex || allowForwardMove;
            ui.styleFilterButton(tabButton, isActive);
            tabButton.setEnabled(canOpen);
            if (!canOpen) {
                tabButton.setTextColor(ui.mappedTextColor(FitnessUi.COLOR_MUTED));
            }
            tabButton.setOnClickListener(v -> {
                host.sessionState().setActiveExerciseId(exercise.id);
                host.rerender();
            });
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            chipParams.setMargins(chipRow.getChildCount() == 0 ? 0 : ui.dp(8), 0, 0, 0);
            chipRow.addView(tabButton, chipParams);
        }
        scroller.addView(chipRow);
        add(scroller, ui.fullWidthParams(ui.dp(4)));
    }

    // ── 세트 편집 그리드 ──────────────────────────────────────────────

    private void renderExerciseSetEditorCard(
            String recordId,
            FitnessRepository.SessionExerciseEntry activeExercise,
            List<FitnessRepository.SessionSetEntry> sets,
            FitnessRepository.ExerciseHistory lastHistory
    ) {
        FitnessUi ui = ui();
        LinearLayout setCard = ui.card();

        // 헤더: 제목 + 기본 휴식 스테퍼 (타이머·새 세트에 적용)
        LinearLayout headerRow = new LinearLayout(host.activity());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView setTitle = ui.text("세트", 16, FitnessUi.COLOR_TEXT, true);
        headerRow.addView(setTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(restStepper());
        setCard.addView(headerRow);

        // 이전 세션의 세트 인덱스별 참조값
        Map<Integer, FitnessRepository.SessionSetEntry> previousBySetIndex = new HashMap<>();
        if (lastHistory != null) {
            for (FitnessRepository.SessionSetEntry prev : lastHistory.sets) {
                previousBySetIndex.put(prev.setIndex, prev);
            }
        }

        LinearLayout columnHeader = new LinearLayout(host.activity());
        columnHeader.setOrientation(LinearLayout.HORIZONTAL);
        columnHeader.setGravity(Gravity.CENTER_VERTICAL);
        addColumnHeader(columnHeader, "이전", new LinearLayout.LayoutParams(ui.dp(56),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addColumnHeader(columnHeader, "무게 kg", ui.fieldCellParams(false));
        addColumnHeader(columnHeader, "횟수", ui.fieldCellParams(false));
        TextView stampHeader = ui.caption("완료", FitnessUi.COLOR_MUTED);
        stampHeader.setGravity(Gravity.CENTER);
        columnHeader.addView(stampHeader, new LinearLayout.LayoutParams(ui.dp(48),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView deleteHeader = ui.caption("삭제", FitnessUi.COLOR_MUTED);
        deleteHeader.setGravity(Gravity.CENTER);
        columnHeader.addView(deleteHeader, new LinearLayout.LayoutParams(ui.dp(32),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addTypedColumnHeader(setCard, activeExercise.recordType);

        for (FitnessRepository.SessionSetEntry set : sets) {
            renderTypedSetRow(
                    setCard,
                    recordId,
                    activeExercise,
                    set,
                    previousBySetIndex.get(set.setIndex)
            );
        }
        setCard.addView(ui.button("+ 세트 추가", false, v -> addSet(recordId, activeExercise, sets)),
                ui.fullWidthParams(ui.dp(12)));
        add(setCard);
    }

    /** 기본 휴식 스테퍼: −/+ 15초. 세트 완료 시 타이머와 새 세트의 rest_seconds에 쓰인다. */
    private View restStepper() {
        FitnessUi ui = ui();
        LinearLayout stepper = new LinearLayout(host.activity());
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = ui.caption("휴식", FitnessUi.COLOR_MUTED);
        label.setPadding(0, 0, ui.dp(8), 0);
        stepper.addView(label);

        TextView minus = stepperButton("−");
        TextView valueView = ui.num(defaultRestSeconds[0] + "초", 14, FitnessUi.COLOR_TEXT, true);
        valueView.setGravity(Gravity.CENTER);
        valueView.setMinWidth(ui.dp(48));
        TextView plus = stepperButton("＋");

        minus.setOnClickListener(v -> {
            defaultRestSeconds[0] = Math.max(REST_STEP_SECONDS, defaultRestSeconds[0] - REST_STEP_SECONDS);
            valueView.setText(defaultRestSeconds[0] + "초");
        });
        plus.setOnClickListener(v -> {
            defaultRestSeconds[0] = Math.min(600, defaultRestSeconds[0] + REST_STEP_SECONDS);
            valueView.setText(defaultRestSeconds[0] + "초");
        });

        stepper.addView(minus);
        stepper.addView(valueView);
        stepper.addView(plus);
        return stepper;
    }

    private TextView stepperButton(String glyph) {
        FitnessUi ui = ui();
        TextView button = ui.text(glyph, 16, FitnessUi.COLOR_TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(ui.borderDrawable(ui.subtle(), ui.subtle(), ui.dp(999)));
        button.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));
        button.setClickable(true);
        button.setFocusable(true);
        ui.applyDepth(button, 3);
        ui.pressFeedback(button);
        return button;
    }

    private static LinearLayout.LayoutParams compactSetFieldParams(
            FitnessUi ui,
            boolean first
    ) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ui.dp(58),
                ui.dp(40)
        );
        params.setMargins(first ? 0 : ui.dp(6), 0, 0, 0);
        return params;
    }

    private void addColumnHeader(LinearLayout row, String label, LinearLayout.LayoutParams params) {
        TextView header = ui().caption(label, FitnessUi.COLOR_MUTED);
        header.setGravity(Gravity.CENTER);
        row.addView(header, params);
    }

    private void addTypedColumnHeader(LinearLayout card, String recordType) {
        FitnessUi ui = ui();
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addColumnHeader(row, "이전", new LinearLayout.LayoutParams(
                ui.dp(52),
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addColumnHeader(row, primaryLabel(recordType), compactSetFieldParams(ui, true));
        addColumnHeader(row, secondaryLabel(recordType), compactSetFieldParams(ui, false));
        if (FitnessRecordContract.supportsRir(recordType)) {
            addColumnHeader(row, "RIR", compactSetFieldParams(ui, false));
        }
        addColumnHeader(row, "완료", new LinearLayout.LayoutParams(
                ui.dp(44),
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addColumnHeader(row, "삭제", new LinearLayout.LayoutParams(
                ui.dp(30),
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        card.addView(row, ui.fullWidthParams(ui.dp(12)));
    }

    private void renderTypedSetRow(
            LinearLayout card,
            String recordId,
            FitnessRepository.SessionExerciseEntry exercise,
            FitnessRepository.SessionSetEntry set,
            FitnessRepository.SessionSetEntry previousSet
    ) {
        FitnessUi ui = ui();
        LinearLayout setBox = new LinearLayout(host.activity());
        setBox.setOrientation(LinearLayout.VERTICAL);
        if (set.isCompleted) {
            setBox.setBackground(ui.borderDrawable(ui.subtle(), ui.subtle(), ui.dp(12)));
            ui.applyDepth(setBox, 2);
        }

        TextView volumeDelta = volumeDeltaLabel(ui, exercise.recordType, previousSet);
        if (volumeDelta != null) {
            setBox.addView(volumeDelta, ui.fullWidthParams(ui.dp(2)));
        }

        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(2), ui.dp(3), ui.dp(2), ui.dp(3));

        TextView previous = ui.num(
                previousSet == null ? "--" : setSummary(exercise.recordType, previousSet),
                10,
                FitnessUi.COLOR_TERTIARY,
                true
        );
        previous.setGravity(Gravity.CENTER);
        previous.setMaxLines(2);
        row.addView(previous, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(44)));

        EditText primary = typedPrimaryInput(exercise.recordType, set);
        EditText secondary = typedSecondaryInput(exercise.recordType, set);
        EditText rir = FitnessRecordContract.supportsRir(exercise.recordType)
                ? ui.numberInput("", set.rir == null ? "" : String.valueOf(set.rir))
                : null;
        EditText[] effortInputs = rir == null
                ? new EditText[]{primary, secondary}
                : new EditText[]{primary, secondary, rir};
        for (EditText input : effortInputs) {
            input.setGravity(Gravity.CENTER);
            input.setTextSize(14);
            input.setMinHeight(ui.dp(38));
            input.setMinimumHeight(ui.dp(38));
            input.setPadding(ui.dp(4), ui.dp(3), ui.dp(4), ui.dp(3));
        }
        secondary.setEnabled(!secondaryLabel(exercise.recordType).isEmpty());
        row.addView(primary, compactSetFieldParams(ui, true));
        row.addView(secondary, compactSetFieldParams(ui, false));
        if (rir != null) {
            row.addView(rir, compactSetFieldParams(ui, false));
        }

        if (volumeDelta != null) {
            TextWatcher volumeDeltaWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                    // 입력 중간값은 afterTextChanged에서 다시 계산한다.
                }

                @Override
                public void onTextChanged(CharSequence text, int start, int before, int count) {
                    // 입력 중간값은 afterTextChanged에서 다시 계산한다.
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    updateVolumeDeltaLabel(volumeDelta, primary, secondary, previousSet);
                }
            };
            primary.addTextChangedListener(volumeDeltaWatcher);
            secondary.addTextChangedListener(volumeDeltaWatcher);
            updateVolumeDeltaLabel(volumeDelta, primary, secondary, previousSet);
        }

        if (previousSet != null) {
            previous.setClickable(true);
            previous.setFocusable(true);
            previous.setOnClickListener(view -> {
                applyPrevious(exercise.recordType, previousSet, primary, secondary, rir);
                try {
                    repository().updateTypedSet(
                            recordId,
                            set.id,
                            typedSetInput(
                                    exercise.recordType,
                                    primary,
                                    secondary,
                                    rir,
                                    set.restSeconds,
                                    set.isCompleted
                            )
                    );
                } catch (IllegalArgumentException error) {
                    host.toast(error.getMessage());
                }
            });
        }

        LinearLayout stampCell = new LinearLayout(host.activity());
        stampCell.setGravity(Gravity.CENTER);
        TextView stamp = ui.num("✓", 16, FitnessUi.COLOR_TEXT, true);
        stamp.setGravity(Gravity.CENTER);
        styleStamp(stamp, set.isCompleted);
        stamp.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(38), ui.dp(38)));
        stampCell.addView(stamp);
        stampCell.setClickable(true);
        stampCell.setFocusable(true);
        stampCell.setOnClickListener(view -> {
            boolean completed = !set.isCompleted;
            try {
                repository().updateTypedSet(
                        recordId,
                        set.id,
                        typedSetInput(
                                exercise.recordType,
                                primary,
                                secondary,
                                rir,
                                completed ? defaultRestSeconds[0] : set.restSeconds,
                                completed
                        )
                );
                styleStamp(stamp, completed);
                ui.stampPop(stamp);
                if (completed) {
                    host.startRestTimer(defaultRestSeconds[0]);
                }
                host.content().postDelayed(host::rerender, 220);
            } catch (IllegalArgumentException error) {
                host.toast(error.getMessage());
                styleStamp(stamp, set.isCompleted);
            }
        });
        row.addView(stampCell, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)));

        if (set.setIndex > 1) {
            TextView delete = ui.num("×", 18, FitnessUi.COLOR_MUTED, true);
            delete.setGravity(Gravity.CENTER);
            delete.setClickable(true);
            delete.setFocusable(true);
            delete.setOnClickListener(view -> {
                repository().deleteSet(recordId, set.id);
                host.rerender();
            });
            row.addView(delete, new LinearLayout.LayoutParams(ui.dp(30), ui.dp(44)));
        } else {
            row.addView(new TextView(host.activity()), new LinearLayout.LayoutParams(
                    ui.dp(30),
                    ui.dp(44)
            ));
        }
        setBox.addView(row, ui.fullWidthParams(0));
        card.addView(setBox, ui.fullWidthParams(ui.dp(6)));
    }

    private EditText typedPrimaryInput(
            String recordType,
            FitnessRepository.SessionSetEntry set
    ) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.REPS_ONLY.equals(type)) {
            return ui().numberInput("", zeroToBlank(set.actualReps));
        }
        if (FitnessRecordContract.TIME.equals(type)) {
            return ui().numberInput("", zeroToBlank(set.durationSeconds));
        }
        if (FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(type)) {
            return ui().decimalInput("", zeroToBlank(set.assistedWeightKg));
        }
        if (FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(type)) {
            return ui().decimalInput("", zeroToBlank(set.addedWeightKg));
        }
        return ui().decimalInput("", zeroToBlank(set.weightKg));
    }

    private EditText typedSecondaryInput(
            String recordType,
            FitnessRepository.SessionSetEntry set
    ) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.WEIGHT_TIME.equals(type)) {
            return ui().numberInput("", zeroToBlank(set.durationSeconds));
        }
        if (FitnessRecordContract.WEIGHT_REPS.equals(type)
                || FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(type)
                || FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(type)) {
            return ui().numberInput("", zeroToBlank(set.actualReps));
        }
        return ui().numberInput("", "");
    }

    private FitnessRepository.SetInput typedSetInput(
            String recordType,
            EditText primary,
            EditText secondary,
            EditText rir,
            Integer restSeconds,
            boolean completed
    ) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        Double weight = null;
        Integer reps = null;
        Integer duration = null;
        Double assisted = null;
        Double added = null;

        if (FitnessRecordContract.REPS_ONLY.equals(type)) {
            reps = FitnessUi.optionalInt(primary);
        } else if (FitnessRecordContract.TIME.equals(type)) {
            duration = FitnessUi.optionalInt(primary);
        } else if (FitnessRecordContract.WEIGHT_TIME.equals(type)) {
            weight = FitnessUi.optionalDouble(primary);
            duration = FitnessUi.optionalInt(secondary);
        } else if (FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(type)) {
            assisted = FitnessUi.optionalDouble(primary);
            reps = FitnessUi.optionalInt(secondary);
        } else if (FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(type)) {
            added = FitnessUi.optionalDouble(primary);
            reps = FitnessUi.optionalInt(secondary);
        } else {
            weight = FitnessUi.optionalDouble(primary);
            reps = FitnessUi.optionalInt(secondary);
        }

        return new FitnessRepository.SetInput(
                weight,
                reps,
                duration,
                assisted,
                added,
                rir == null ? null : FitnessUi.optionalInt(rir),
                restSeconds,
                completed
        );
    }

    private void applyPrevious(
            String recordType,
            FitnessRepository.SessionSetEntry previous,
            EditText primary,
            EditText secondary,
            EditText rir
    ) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.REPS_ONLY.equals(type)) {
            primary.setText(zeroToBlank(previous.actualReps));
        } else if (FitnessRecordContract.TIME.equals(type)) {
            primary.setText(zeroToBlank(previous.durationSeconds));
        } else if (FitnessRecordContract.WEIGHT_TIME.equals(type)) {
            primary.setText(zeroToBlank(previous.weightKg));
            secondary.setText(zeroToBlank(previous.durationSeconds));
        } else if (FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(type)) {
            primary.setText(zeroToBlank(previous.assistedWeightKg));
            secondary.setText(zeroToBlank(previous.actualReps));
        } else if (FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(type)) {
            primary.setText(zeroToBlank(previous.addedWeightKg));
            secondary.setText(zeroToBlank(previous.actualReps));
        } else {
            primary.setText(zeroToBlank(previous.weightKg));
            secondary.setText(zeroToBlank(previous.actualReps));
        }
        if (rir != null) {
            rir.setText(previous.rir == null ? "" : String.valueOf(previous.rir));
        }
    }

    private static String primaryLabel(String recordType) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.REPS_ONLY.equals(type)) {
            return "횟수";
        }
        if (FitnessRecordContract.TIME.equals(type)) {
            return "초";
        }
        if (FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(type)) {
            return "보조 kg";
        }
        if (FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(type)) {
            return "추가 kg";
        }
        return "중량 kg";
    }

    private static String secondaryLabel(String recordType) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.WEIGHT_TIME.equals(type)) {
            return "초";
        }
        if (FitnessRecordContract.WEIGHT_REPS.equals(type)
                || FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(type)
                || FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(type)) {
            return "횟수";
        }
        return "";
    }

    private static boolean supportsLoadRepAnalytics(String recordType) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        return FitnessRecordContract.WEIGHT_REPS.equals(type);
    }

    private static String setSummary(
            String recordType,
            FitnessRepository.SessionSetEntry set
    ) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.REPS_ONLY.equals(type)) {
            return set.actualReps + "회";
        }
        if (FitnessRecordContract.TIME.equals(type)) {
            return set.durationSeconds + "초";
        }
        if (FitnessRecordContract.WEIGHT_TIME.equals(type)) {
            return FitnessUi.trimDouble(set.weightKg) + "kg\n" + set.durationSeconds + "초";
        }
        if (FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(type)) {
            return "보조 " + FitnessUi.trimDouble(set.assistedWeightKg) + "kg\n" + set.actualReps + "회";
        }
        if (FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(type)) {
            return "추가 " + FitnessUi.trimDouble(set.addedWeightKg) + "kg\n" + set.actualReps + "회";
        }
        return FitnessUi.trimDouble(set.weightKg) + "kg\n" + set.actualReps + "회";
    }

    /** 한 세트 박스에 하나씩 표시할 중량×횟수 비교 라벨을 만든다. */
    private static TextView volumeDeltaLabel(
            FitnessUi ui,
            String recordType,
            FitnessRepository.SessionSetEntry previousSet
    ) {
        if (!FitnessRecordContract.WEIGHT_REPS.equals(
                FitnessRecordContract.normalizeRecordType(recordType))
                || previousSet == null) {
            return null;
        }

        TextView label = ui.num("", 11, FitnessUi.COLOR_MUTED, true);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, 0, 0, ui.dp(2));
        label.setVisibility(View.GONE);
        return label;
    }

    /** 중량과 횟수가 입력될 때마다 한 세트의 총 중량 차이를 갱신한다. */
    private static void updateVolumeDeltaLabel(
            TextView label,
            EditText weightInput,
            EditText repsInput,
            FitnessRepository.SessionSetEntry previousSet
    ) {
        Double currentWeight = FitnessUi.optionalDouble(weightInput);
        Integer currentReps = FitnessUi.optionalInt(repsInput);
        if (currentWeight == null
                || currentReps == null
                || currentWeight < 0
                || currentReps <= 0) {
            label.setVisibility(View.GONE);
            return;
        }

        double currentVolume = currentWeight * currentReps;
        double previousVolume = previousSet.weightKg * previousSet.actualReps;
        double delta = currentVolume - previousVolume;
        String direction = delta < 0 ? "덜" : "더";
        int color = delta > 0
                ? FitnessUi.COLOR_POSITIVE
                : delta < 0 ? FitnessUi.COLOR_NEGATIVE : FitnessUi.COLOR_MUTED;
        label.setText("지난 세트보다 " + FitnessUi.formatVolume(Math.abs(delta))
                + " KG " + direction + " 들었어요");
        label.setTextColor(color);
        label.setVisibility(View.VISIBLE);
    }

    private static FitnessRepository.SetInput emptySetInput(
            boolean completed,
            Integer restSeconds
    ) {
        return new FitnessRepository.SetInput(
                null,
                null,
                null,
                null,
                null,
                null,
                restSeconds,
                completed
        );
    }

    private static String zeroToBlank(double value) {
        return value == 0 ? "" : FitnessUi.trimDouble(value);
    }

    private static String zeroToBlank(int value) {
        return value == 0 ? "" : String.valueOf(value);
    }

    private void renderSetRow(LinearLayout setCard, String recordId,
                              FitnessRepository.SessionSetEntry set,
                              FitnessRepository.SessionSetEntry previousSet) {
        FitnessUi ui = ui();
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(6));
        if (set.isCompleted) {
            row.setBackground(ui.borderDrawable(ui.subtle(), ui.subtle(), ui.dp(12)));
            ui.applyDepth(row, 2);
        }

        EditText weightInput = ui.decimalInput("", set.weightKg == 0 ? "" : FitnessUi.trimDouble(set.weightKg));
        EditText repsInput = ui.numberInput("", set.actualReps == 0 ? "" : String.valueOf(set.actualReps));
        weightInput.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
        repsInput.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
        weightInput.setGravity(Gravity.CENTER);
        repsInput.setGravity(Gravity.CENTER);

        // "이전" 참조 셀: 지난 세션 같은 세트의 값. 탭하면 이 세트에 즉시 적용된다.
        TextView prevCell = ui.num(previousSet == null
                        ? "—"
                        : FitnessUi.trimDouble(previousSet.weightKg) + "×" + previousSet.actualReps,
                11, FitnessUi.COLOR_TERTIARY, true);
        prevCell.setGravity(Gravity.CENTER);
        prevCell.setMaxLines(1);
        if (previousSet != null) {
            final FitnessRepository.SessionSetEntry prev = previousSet;
            prevCell.setClickable(true);
            prevCell.setFocusable(true);
            prevCell.setOnClickListener(v -> {
                weightInput.setText(prev.weightKg == 0 ? "" : FitnessUi.trimDouble(prev.weightKg));
                repsInput.setText(prev.actualReps == 0 ? "" : String.valueOf(prev.actualReps));
                repository().updateSet(recordId, set.id, prev.weightKg, prev.actualReps,
                        null, set.restSeconds, set.isCompleted);
            });
        }
        row.addView(prevCell, new LinearLayout.LayoutParams(ui.dp(56), ui.dp(48)));

        row.addView(weightInput, ui.fieldCellParams(false));
        row.addView(repsInput, ui.fieldCellParams(false));

        // 완료 스탬프: 탭 1회 = 완료(반전 채움 + 팝), 재탭 = 해제. 시그니처 인터랙션.
        LinearLayout stampCell = new LinearLayout(host.activity());
        stampCell.setGravity(Gravity.CENTER);
        TextView stamp = ui.num("✓", 16, FitnessUi.COLOR_TEXT, true);
        stamp.setGravity(Gravity.CENTER);
        styleStamp(stamp, set.isCompleted);
        stamp.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
        stampCell.addView(stamp);
        stampCell.setClickable(true);
        stampCell.setFocusable(true);
        stampCell.setOnClickListener(v -> {
            boolean nowCompleted = !set.isCompleted;
            styleStamp(stamp, nowCompleted);
            ui.stampPop(stamp);
            saveSet(recordId, set, weightInput, repsInput, nowCompleted);
        });
        row.addView(stampCell, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(52)));

        if (set.setIndex > 1) {
            TextView delete = ui.num("−", 18, FitnessUi.COLOR_MUTED, true);
            delete.setGravity(Gravity.CENTER);
            delete.setClickable(true);
            delete.setFocusable(true);
            delete.setOnClickListener(v -> {
                repository().deleteSet(recordId, set.id);
                host.rerender();
            });
            row.addView(delete, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(52)));
        } else {
            TextView spacer = new TextView(host.activity());
            row.addView(spacer, new LinearLayout.LayoutParams(ui.dp(32), LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        setCard.addView(row, ui.fullWidthParams(ui.dp(6)));
    }

    /** 완료 = 히어로 팔레트 채움, 미완료 = 옅은 팔레트 링. */
    private void styleStamp(TextView stamp, boolean completed) {
        FitnessUi ui = ui();
        if (completed) {
            stamp.setTextColor(ui.onVibrant());
            stamp.setBackground(ui.vibrantBackground(0, ui.dp(999)));
        } else {
            stamp.setTextColor(ui.inkTertiary());
            stamp.setBackground(ui.flatSurfaceDrawable(ui.dp(999)));
        }
        ui.applyDepth(stamp, completed ? 5 : 2);
    }

    private void saveSet(String recordId, FitnessRepository.SessionSetEntry set,
                         EditText weightInput, EditText repsInput,
                         boolean completed) {
        repository().updateSet(recordId, set.id,
                FitnessUi.parseDouble(weightInput, 0),
                Math.max(0, FitnessUi.parseInt(repsInput, 0)),
                null,
                completed ? defaultRestSeconds[0] : set.restSeconds,
                completed);
        if (completed) {
            host.startRestTimer(defaultRestSeconds[0]);
        }
        if (completed && WorkoutSessionState.canMoveToNextExercise(repository(), recordId,
                host.sessionState().activeExerciseId())) {
            FitnessRepository.SessionExerciseEntry next = WorkoutSessionState.nextExercise(
                    repository().sessionExerciseEntries(recordId), host.sessionState().activeExerciseId());
            if (next != null) {
                host.sessionState().setActiveExerciseId(next.id);
            }
        }
        // 스탬프 팝 모션이 보이도록 rerender를 한 박자 늦춘다.
        host.content().postDelayed(host::rerender, 220);
    }

    // ── 기록 분석 ─────────────────────────────────────────────────────

    /** 개인 기록 카드: 역대 최고 무게 / 추정 1RM / 최고 세션 볼륨. 오늘 갱신 시 PR 뱃지. */
    private void renderPersonalRecordCard(FitnessRepository.ExerciseBests bests,
                                          List<FitnessRepository.SessionSetEntry> sets) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();

        double todayMaxWeight = 0;
        double todayBestE1rm = 0;
        for (FitnessRepository.SessionSetEntry set : sets) {
            if (!set.isCompleted) {
                continue;
            }
            todayMaxWeight = Math.max(todayMaxWeight, set.weightKg);
            todayBestE1rm = Math.max(todayBestE1rm, FitnessRepository.epleyE1rm(set.weightKg, set.actualReps));
        }
        double todayVolume = currentExerciseVolume(sets);
        boolean todayPr = bests.sessionCount > 0
                && (todayMaxWeight > bests.maxWeightKg
                || todayBestE1rm > bests.bestE1rmKg
                || todayVolume > bests.bestSessionVolumeKg);

        LinearLayout header = new LinearLayout(host.activity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = ui.text("개인 기록", 16, FitnessUi.COLOR_TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (todayPr) {
            header.addView(ui.statusDotBadge("오늘 PR", FitnessUi.COLOR_POSITIVE, false));
        } else if (bests.sessionCount > 0) {
            header.addView(ui.text(bests.sessionCount + "회 수행", 12, FitnessUi.COLOR_TERTIARY, false));
        }
        card.addView(header);

        if (bests.sessionCount == 0) {
            TextView empty = ui.text("이전 수행 기록이 없습니다. 오늘 기록이 기준이 됩니다.", 13, FitnessUi.COLOR_MUTED, false);
            empty.setPadding(0, ui.dp(10), 0, 0);
            card.addView(empty);
            add(card);
            return;
        }

        LinearLayout statRow = new LinearLayout(host.activity());
        statRow.setOrientation(LinearLayout.HORIZONTAL);
        statRow.setPadding(0, ui.dp(12), 0, 0);
        statRow.addView(ui.inlineStat("최고 무게",
                FitnessUi.trimDouble(bests.maxWeightKg) + "kg × " + bests.repsAtMaxWeight + "회", false),
                ui.metaCellParams(true));
        statRow.addView(ui.inlineStat("추정 1RM",
                FitnessUi.formatVolume(round1(bests.bestE1rmKg)) + "kg", false),
                ui.metaCellParams(false));
        statRow.addView(ui.inlineStat("최고 볼륨",
                FitnessUi.formatVolume(bests.bestSessionVolumeKg) + "kg", false),
                ui.metaCellParams(false));
        card.addView(statRow);

        if (todayBestE1rm > 0) {
            TextView todayLine = ui.num("오늘 추정 1RM " + FitnessUi.formatVolume(round1(todayBestE1rm)) + "kg",
                    12, FitnessUi.COLOR_MUTED, false);
            todayLine.setPadding(0, ui.dp(10), 0, 0);
            card.addView(todayLine);
        }
        add(card);
    }

    /** 직전 세션의 같은 종목 수행 내역. 프로그레시브 오버로드의 기준점. */
    private void renderLastHistoryCard(
            String recordType,
            FitnessRepository.ExerciseHistory lastHistory
    ) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "이전 기록", lastHistory == null ? null : formatDate(lastHistory.date));

        if (lastHistory == null) {
            TextView empty = ui.text("이 종목의 이전 기록이 없습니다.", 13, FitnessUi.COLOR_MUTED, false);
            empty.setPadding(0, ui.dp(10), 0, 0);
            card.addView(empty);
            add(card);
            return;
        }

        for (FitnessRepository.SessionSetEntry set : lastHistory.sets) {
            card.addView(ui.keyValue(set.setIndex + "세트",
                    setSummary(recordType, set)));
        }
        if (supportsLoadRepAnalytics(recordType)) {
            View line = ui.hairline(ui.border());
            LinearLayout.LayoutParams lineParams = ui.fullWidthParams(ui.dp(10));
            lineParams.height = ui.dp(1);
            card.addView(line, lineParams);
            card.addView(ui.keyValue(
                    "외부 중량 볼륨",
                    FitnessUi.formatVolume(lastHistory.totalVolumeKg) + "kg"
            ));
        }
        add(card);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────

    private double currentExerciseVolume(List<FitnessRepository.SessionSetEntry> sets) {
        double volume = 0;
        for (FitnessRepository.SessionSetEntry set : sets) {
            if (set.isCompleted) {
                volume += set.weightKg * set.actualReps;
            }
        }
        return volume;
    }

    private int resolveDefaultRest(List<FitnessRepository.SessionSetEntry> sets) {
        for (int i = sets.size() - 1; i >= 0; i--) {
            Integer rest = sets.get(i).restSeconds;
            if (rest != null && rest > 0) {
                return rest;
            }
        }
        return DEFAULT_REST_SECONDS;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String formatDate(String date) {
        return date == null ? "" : date.replace("-", ". ");
    }

    private void addSet(String recordId, FitnessRepository.SessionExerciseEntry exercise,
                        List<FitnessRepository.SessionSetEntry> sets) {
        FitnessRepository.SessionSetEntry last = sets.isEmpty() ? null : sets.get(sets.size() - 1);
        int nextIndex = last == null ? 1 : last.setIndex + 1;
        repository().addTypedSet(
                recordId,
                exercise.id,
                nextIndex,
                new FitnessRepository.SetInput(
                        last == null || last.weightKg == 0 ? null : last.weightKg,
                        last == null || last.actualReps == 0 ? null : last.actualReps,
                        last == null || last.durationSeconds == 0 ? null : last.durationSeconds,
                        last == null || last.assistedWeightKg == 0 ? null : last.assistedWeightKg,
                        last == null || last.addedWeightKg == 0 ? null : last.addedWeightKg,
                        last == null ? null : last.rir,
                        defaultRestSeconds[0],
                        false
                )
        );
        host.rerender();
    }

    private void confirmDeleteExercise(String recordId, FitnessRepository.SessionExerciseEntry exercise) {
        ui().confirmSheet("종목 삭제",
                "\"" + exercise.name + "\" 종목과 해당 세트를 삭제 표시합니다.",
                null,
                "삭제", () -> {
                    repository().deleteExercise(recordId, exercise.id);
                    host.rerender();
                });
    }
}
