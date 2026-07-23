package com.openai.penguingirl.pet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Random;

public class PetService extends Service {
    private static final String CHANNEL_ID = "desktop_pet_running";
    private static final int NOTIFICATION_ID = 20200;

    // Position is updated at approximately the display refresh rate. Each
    // authored walk frame is one complete character drawing: body, feet,
    // flippers and tail are never assembled as separate runtime layers.
    private static final long TICK_MS = 16L;
    private static final long WALK_FRAME_MS = 62L;
    private static final long LIE_FRAME_MS = 115L;
    private static final long BREATH_FRAME_MS = 680L;
    private static final long HAPPY_DURATION_MS = 1850L;
    private static final long LONG_IDLE_BEFORE_LIE_MS = 9000L;
    private static final int TRIPS_BEFORE_REST = 3;
    private static final int WALK_FRAME_COUNT = 25;
    private static final long FEED_BUTTON_TIMEOUT_MS = 5000L;

    private enum Mode { IDLE, WALK, HAPPY, LIE_DOWN, SLEEP, WAKE_UP, DOCKED }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Bitmap[] walkRightFrames = new Bitmap[WALK_FRAME_COUNT];
    private final Bitmap[] walkLeftFrames = new Bitmap[WALK_FRAME_COUNT];
    private final Bitmap[] lieFrames = new Bitmap[8];

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private FrameLayout overlay;
    private ImageView character;
    private ImageView feedingItem;
    private Button feedButton;
    private Button closeButton;
    private Bitmap idleFrame;
    private Bitmap happyFrame;
    private Bitmap foodFrame;

    private Mode mode = Mode.IDLE;
    private long modeUntil;
    private long lastFrameAt;
    private long lastTickAt;
    private long walkStartedAt;
    private int frameIndex;
    private int screenWidth;
    private int screenHeight;
    private int petSize;
    private float preciseX;
    private float preciseY;
    private float targetX;
    private float targetY;
    private float speedPxPerSecond;
    private int tripsSinceRest;
    private boolean restPending;
    private boolean dragging;
    private boolean facingRight = true;
    private boolean dockedLeft;
    private boolean edgeAnimating;
    private ValueAnimator edgeAnimator;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updatePet();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private final Runnable hideFeedButtonRunnable = new Runnable() {
        @Override
        public void run() {
            hideFeedButton();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForegroundService();
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        loadFrames();
        createOverlay();
        setMode(Mode.IDLE, randomIdleDuration());
        lastTickAt = SystemClock.uptimeMillis();
        handler.post(ticker);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startAsForegroundService() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "桌宠运行状态", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("保持企鹅女孩桌宠在桌面上运行");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("企鹅女孩正在巡游")
                .setContentText("点击进入桌宠控制页面")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void loadFrames() {
        idleFrame = loadBitmap("idle.png");
        happyFrame = loadBitmap("happy.png");
        foodFrame = loadBitmap("food.png");
        for (int i = 0; i < WALK_FRAME_COUNT; i++) {
            String fileName = String.format(Locale.US, "walk_%02d.png", i + 1);
            walkRightFrames[i] = loadBitmap("walk_right/" + fileName);
            walkLeftFrames[i] = loadBitmap("walk_left/" + fileName);
        }
        for (int i = 0; i < lieFrames.length; i++) {
            lieFrames[i] = loadBitmap(String.format(
                    Locale.US, "lie/lie_%02d.png", i + 1));
        }
    }

    private Bitmap loadBitmap(String path) {
        try (InputStream input = getAssets().open(path)) {
            return BitmapFactory.decodeStream(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load asset: " + path, exception);
        }
    }

    private void createOverlay() {
        updateScreenBounds();
        petSize = dp(210);
        speedPxPerSecond = dp(68);

        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.TRANSPARENT);

        character = new ImageView(this);
        character.setBackgroundColor(Color.TRANSPARENT);
        character.setScaleType(ImageView.ScaleType.FIT_CENTER);
        character.setImageBitmap(idleFrame);
        overlay.addView(character, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        feedingItem = new ImageView(this);
        feedingItem.setBackgroundColor(Color.TRANSPARENT);
        feedingItem.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        feedingItem.setImageBitmap(foodFrame);
        feedingItem.setVisibility(View.GONE);
        FrameLayout.LayoutParams foodParams = new FrameLayout.LayoutParams(dp(58), dp(58));
        foodParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        foodParams.bottomMargin = dp(13);
        overlay.addView(feedingItem, foodParams);

        feedButton = new Button(this);
        feedButton.setText("投喂");
        feedButton.setTextColor(Color.rgb(38, 29, 12));
        feedButton.setTextSize(14);
        feedButton.setGravity(Gravity.CENTER);
        feedButton.setAllCaps(false);
        feedButton.setPadding(dp(8), 0, dp(8), 0);
        feedButton.setMinWidth(0);
        feedButton.setMinHeight(0);
        GradientDrawable feedBackground = new GradientDrawable();
        feedBackground.setColor(Color.rgb(255, 194, 48));
        feedBackground.setStroke(dp(1), Color.rgb(151, 98, 8));
        feedBackground.setCornerRadius(dp(17));
        feedButton.setBackground(feedBackground);
        feedButton.setVisibility(View.GONE);
        FrameLayout.LayoutParams feedParams = new FrameLayout.LayoutParams(dp(78), dp(38));
        feedParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        feedParams.bottomMargin = dp(7);
        overlay.addView(feedButton, feedParams);
        feedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                feedPet();
            }
        });

        closeButton = new Button(this);
        closeButton.setText("×");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTextSize(15);
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setPadding(0, 0, 0, 0);
        closeButton.setMinWidth(0);
        closeButton.setMinHeight(0);
        GradientDrawable closeBackground = new GradientDrawable();
        closeBackground.setShape(GradientDrawable.OVAL);
        closeBackground.setColor(Color.argb(125, 20, 22, 26));
        closeButton.setBackground(closeBackground);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(30), dp(30));
        closeParams.gravity = Gravity.TOP | Gravity.END;
        closeParams.setMargins(0, dp(3), dp(3), 0);
        overlay.addView(closeButton, closeParams);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopSelf();
            }
        });

        int overlayType = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        windowParams = new WindowManager.LayoutParams(
                petSize,
                petSize,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.TOP | Gravity.START;
        windowParams.x = dp(18);
        windowParams.y = Math.max(dp(24), screenHeight - petSize - dp(72));
        windowParams.alpha = 1f;
        preciseX = windowParams.x;
        preciseY = windowParams.y;
        windowManager.addView(overlay, windowParams);
        character.setOnTouchListener(new PetTouchListener());
    }

    private void updatePet() {
        if (overlay == null) return;
        long now = SystemClock.uptimeMillis();
        float deltaSeconds = Math.min(0.05f, Math.max(0f, (now - lastTickAt) / 1000f));
        lastTickAt = now;
        if (dragging) return;
        if (mode == Mode.DOCKED || edgeAnimating) return;

        if (mode == Mode.WALK) {
            updateWalkFrame(now);
            moveTowardTarget(deltaSeconds);
            return;
        }
        if (mode == Mode.LIE_DOWN) {
            advanceLieDown(now);
            return;
        }
        if (mode == Mode.WAKE_UP) {
            advanceWakeUp(now);
            return;
        }
        if (mode == Mode.SLEEP) {
            updateBreathing(now);
            if (now >= modeUntil) setMode(Mode.WAKE_UP, Long.MAX_VALUE);
            return;
        }
        if (now < modeUntil) return;

        if (mode == Mode.HAPPY) {
            restPending = false;
            tripsSinceRest = 0;
            setMode(Mode.IDLE, 1100L);
        } else if (mode == Mode.IDLE && restPending) {
            setMode(Mode.LIE_DOWN, Long.MAX_VALUE);
        } else if (mode == Mode.IDLE) {
            startWalkToRandomTarget();
        }
    }

    private void updateWalkFrame(long now) {
        int nextFrame = (int) (((now - walkStartedAt) / WALK_FRAME_MS)
                % WALK_FRAME_COUNT);
        if (nextFrame == frameIndex) return;
        frameIndex = nextFrame;
        character.setImageBitmap(facingRight
                ? walkRightFrames[frameIndex]
                : walkLeftFrames[frameIndex]);
    }

    private void moveTowardTarget(float deltaSeconds) {
        float dx = targetX - preciseX;
        float dy = targetY - preciseY;
        float distance = (float) Math.hypot(dx, dy);
        float step = speedPxPerSecond * deltaSeconds;
        if (distance <= Math.max(2f, step)) {
            preciseX = targetX;
            preciseY = targetY;
            applyPrecisePosition();
            tripsSinceRest++;
            restPending = tripsSinceRest >= TRIPS_BEFORE_REST;
            setMode(Mode.IDLE,
                    restPending ? LONG_IDLE_BEFORE_LIE_MS : randomIdleDuration());
            return;
        }
        preciseX += dx / distance * step;
        preciseY += dy / distance * step;
        applyPrecisePosition();
    }

    private void startWalkToRandomTarget() {
        updateScreenBounds();
        preciseX = windowParams.x;
        preciseY = windowParams.y;
        int minX = 0;
        int maxX = Math.max(0, screenWidth - petSize);
        int minY = dp(24);
        int maxY = Math.max(minY, screenHeight - petSize - dp(48));
        float minimumDistance = dp(135);

        targetX = preciseX;
        targetY = preciseY;
        for (int attempt = 0; attempt < 24; attempt++) {
            float candidateX = minX + random.nextFloat() * Math.max(1, maxX - minX);
            float candidateY = minY + random.nextFloat() * Math.max(1, maxY - minY);
            if (Math.hypot(candidateX - preciseX, candidateY - preciseY) >= minimumDistance) {
                targetX = candidateX;
                targetY = candidateY;
                break;
            }
        }
        if (targetX == preciseX && targetY == preciseY) {
            targetX = Math.abs(preciseX - minX) > Math.abs(preciseX - maxX) ? minX : maxX;
            targetY = minY + random.nextFloat() * Math.max(1, maxY - minY);
        }
        if (Math.abs(targetX - preciseX) > dp(6)) {
            facingRight = targetX > preciseX;
        }
        setMode(Mode.WALK, Long.MAX_VALUE);
    }

    private void advanceLieDown(long now) {
        if (now - lastFrameAt < LIE_FRAME_MS) return;
        if (frameIndex < 6) {
            frameIndex++;
            character.setImageBitmap(lieFrames[frameIndex]);
            lastFrameAt = now;
        } else {
            setMode(Mode.SLEEP, 14000L + random.nextInt(11001));
        }
    }

    private void updateBreathing(long now) {
        if (now - lastFrameAt < BREATH_FRAME_MS) return;
        frameIndex = frameIndex == 6 ? 7 : 6;
        character.setImageBitmap(lieFrames[frameIndex]);
        lastFrameAt = now;
    }

    private void advanceWakeUp(long now) {
        if (now - lastFrameAt < LIE_FRAME_MS) return;
        if (frameIndex > 0) {
            frameIndex--;
            character.setImageBitmap(lieFrames[frameIndex]);
            lastFrameAt = now;
        } else {
            restPending = false;
            tripsSinceRest = 0;
            setMode(Mode.IDLE, 900L);
        }
    }

    private void setMode(Mode newMode, long duration) {
        mode = newMode;
        long now = SystemClock.uptimeMillis();
        modeUntil = duration == Long.MAX_VALUE ? Long.MAX_VALUE : now + duration;
        frameIndex = 0;
        lastFrameAt = now;
        character.animate().cancel();
        character.setTranslationY(0f);
        character.setScaleX(1f);
        character.setScaleY(1f);
        if (newMode != Mode.DOCKED) {
            character.setClipBounds(null);
            if (closeButton != null) closeButton.setVisibility(View.VISIBLE);
        }

        if (newMode == Mode.IDLE) {
            character.setImageBitmap(idleFrame);
        } else if (newMode == Mode.WALK) {
            walkStartedAt = now;
            character.setImageBitmap(facingRight
                    ? walkRightFrames[0]
                    : walkLeftFrames[0]);
        } else if (newMode == Mode.HAPPY) {
            character.setImageBitmap(happyFrame);
            ObjectAnimator bounce = ObjectAnimator.ofFloat(
                    character, View.TRANSLATION_Y, 0f, -dp(9), 0f);
            bounce.setDuration(420L);
            bounce.setRepeatCount(3);
            bounce.start();
        } else if (newMode == Mode.LIE_DOWN) {
            character.setImageBitmap(lieFrames[0]);
        } else if (newMode == Mode.SLEEP) {
            frameIndex = 6;
            character.setImageBitmap(lieFrames[frameIndex]);
        } else if (newMode == Mode.WAKE_UP) {
            frameIndex = 6;
            character.setImageBitmap(lieFrames[frameIndex]);
        }
    }

    private boolean isNearScreenEdge() {
        int maxX = Math.max(0, screenWidth - petSize);
        int threshold = dp(30);
        return windowParams.x <= threshold || windowParams.x >= maxX - threshold;
    }

    private void dockToEdge(boolean left) {
        if (overlay == null || edgeAnimating) return;
        updateScreenBounds();
        handler.removeCallbacks(hideFeedButtonRunnable);
        hideFeedButton();
        character.animate().cancel();
        if (edgeAnimator != null) edgeAnimator.cancel();

        dockedLeft = left;
        edgeAnimating = true;
        mode = Mode.DOCKED;
        modeUntil = Long.MAX_VALUE;
        restPending = false;
        tripsSinceRest = 0;
        if (closeButton != null) closeButton.setVisibility(View.GONE);

        character.setImageBitmap(left
                ? walkRightFrames[WALK_FRAME_COUNT - 1]
                : walkLeftFrames[WALK_FRAME_COUNT - 1]);
        character.setClipBounds(new Rect(0, 0, petSize, petSize));

        final int startX = windowParams.x;
        // In the 420 px artwork the inward-facing head centers are near x=160
        // on the right-facing frame and x=260 on its full-frame mirror.
        final int endX = left
                ? -Math.round(petSize * 160f / 420f)
                : screenWidth - Math.round(petSize * 260f / 420f);
        final int startBottom = petSize;
        final int headBottom = Math.round(petSize * 205f / 420f);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        edgeAnimator = animator;
        animator.setDuration(430L);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float progress = (Float) valueAnimator.getAnimatedValue();
                windowParams.x = Math.round(startX + (endX - startX) * progress);
                preciseX = windowParams.x;
                int bottom = Math.round(
                        startBottom + (headBottom - startBottom) * progress);
                character.setClipBounds(new Rect(0, 0, petSize, bottom));
                safelyUpdateOverlay();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (edgeAnimator != animation) return;
                edgeAnimator = null;
                edgeAnimating = false;
                windowParams.x = endX;
                preciseX = endX;
                character.setClipBounds(new Rect(0, 0, petSize, headBottom));
                safelyUpdateOverlay();
            }
        });
        animator.start();
    }

    private void revealFromEdge() {
        if (mode != Mode.DOCKED || edgeAnimating || overlay == null) return;
        updateScreenBounds();
        if (edgeAnimator != null) edgeAnimator.cancel();
        edgeAnimating = true;

        final int startX = windowParams.x;
        final int endX = dockedLeft
                ? dp(8)
                : Math.max(0, screenWidth - petSize - dp(8));
        Rect currentClip = character.getClipBounds();
        final int startBottom = currentClip == null
                ? Math.round(petSize * 205f / 420f)
                : currentClip.bottom;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        edgeAnimator = animator;
        animator.setDuration(390L);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float progress = (Float) valueAnimator.getAnimatedValue();
                windowParams.x = Math.round(startX + (endX - startX) * progress);
                preciseX = windowParams.x;
                int bottom = Math.round(
                        startBottom + (petSize - startBottom) * progress);
                character.setClipBounds(new Rect(0, 0, petSize, bottom));
                safelyUpdateOverlay();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (edgeAnimator != animation) return;
                edgeAnimator = null;
                edgeAnimating = false;
                windowParams.x = endX;
                preciseX = endX;
                character.setClipBounds(null);
                if (closeButton != null) closeButton.setVisibility(View.VISIBLE);
                safelyUpdateOverlay();
                lastTickAt = SystemClock.uptimeMillis();
                setMode(Mode.IDLE, 900L);
            }
        });
        animator.start();
    }

    private void showHappy() {
        restPending = false;
        tripsSinceRest = 0;
        setMode(Mode.HAPPY, HAPPY_DURATION_MS);
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(
                        28, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(28);
            }
        }
    }

    private void showFeedButton() {
        restPending = false;
        tripsSinceRest = 0;
        setMode(Mode.IDLE, FEED_BUTTON_TIMEOUT_MS + 600L);
        handler.removeCallbacks(hideFeedButtonRunnable);
        feedButton.animate().cancel();
        feedButton.setAlpha(0f);
        feedButton.setScaleX(0.82f);
        feedButton.setScaleY(0.82f);
        feedButton.setVisibility(View.VISIBLE);
        feedButton.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(160L)
                .start();
        handler.postDelayed(hideFeedButtonRunnable, FEED_BUTTON_TIMEOUT_MS);
    }

    private void hideFeedButton() {
        if (feedButton == null || feedButton.getVisibility() != View.VISIBLE) return;
        feedButton.animate().cancel();
        feedButton.setVisibility(View.GONE);
    }

    private void feedPet() {
        handler.removeCallbacks(hideFeedButtonRunnable);
        hideFeedButton();
        restPending = false;
        tripsSinceRest = 0;
        setMode(Mode.IDLE, 900L);

        feedingItem.animate().cancel();
        feedingItem.setVisibility(View.VISIBLE);
        feedingItem.setAlpha(1f);
        feedingItem.setScaleX(0.82f);
        feedingItem.setScaleY(0.82f);
        feedingItem.setTranslationY(dp(18));
        feedingItem.animate()
                .translationY(-dp(82))
                .scaleX(0.34f)
                .scaleY(0.34f)
                .alpha(0f)
                .setDuration(680L)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        feedingItem.setVisibility(View.GONE);
                        feedingItem.setTranslationY(0f);
                        feedingItem.setAlpha(1f);
                    }
                })
                .start();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                showHappy();
            }
        }, 300L);
    }

    private long randomIdleDuration() {
        return 2600L + random.nextInt(3001);
    }

    private void updateScreenBounds() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
    }

    private void applyPrecisePosition() {
        windowParams.x = Math.round(preciseX);
        windowParams.y = Math.round(preciseY);
        safelyUpdateOverlay();
    }

    private void safelyUpdateOverlay() {
        try {
            windowManager.updateViewLayout(overlay, windowParams);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private class PetTouchListener implements View.OnTouchListener {
        private final int touchSlop = ViewConfiguration.get(PetService.this).getScaledTouchSlop();
        private float downRawX;
        private float downRawY;
        private int startX;
        private int startY;
        private long downAt;
        private boolean moved;
        private boolean longPressTriggered;
        private final Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                if (dragging && !moved) {
                    longPressTriggered = true;
                    showFeedButton();
                }
            }
        };

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (mode == Mode.DOCKED || edgeAnimating) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                        && mode == Mode.DOCKED && !edgeAnimating) {
                    revealFromEdge();
                }
                return true;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = true;
                    moved = false;
                    longPressTriggered = false;
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startX = windowParams.x;
                    startY = windowParams.y;
                    downAt = SystemClock.uptimeMillis();
                    handler.postDelayed(longPressRunnable,
                            ViewConfiguration.getLongPressTimeout());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(event.getRawX() - downRawX);
                    int dy = Math.round(event.getRawY() - downRawY);
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        moved = true;
                        handler.removeCallbacks(longPressRunnable);
                        if (!longPressTriggered) hideFeedButton();
                    }
                    if (longPressTriggered) return true;
                    updateScreenBounds();
                    windowParams.x = clamp(startX + dx, 0, Math.max(0, screenWidth - petSize));
                    windowParams.y = clamp(startY + dy, dp(24),
                            Math.max(dp(24), screenHeight - petSize - dp(48)));
                    preciseX = windowParams.x;
                    preciseY = windowParams.y;
                    safelyUpdateOverlay();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPressRunnable);
                    dragging = false;
                    lastTickAt = SystemClock.uptimeMillis();
                    if (longPressTriggered) {
                        longPressTriggered = false;
                        return true;
                    }
                    if (moved
                            && event.getActionMasked() == MotionEvent.ACTION_UP
                            && isNearScreenEdge()) {
                        int maxX = Math.max(0, screenWidth - petSize);
                        dockToEdge(windowParams.x <= maxX / 2);
                        return true;
                    }
                    long duration = SystemClock.uptimeMillis() - downAt;
                    if (!moved && duration < 520
                            && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        showHappy();
                    } else {
                        restPending = false;
                        tripsSinceRest = 0;
                        setMode(Mode.IDLE, 1100L);
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (edgeAnimator != null) {
            edgeAnimator.cancel();
            edgeAnimator = null;
        }
        if (overlay != null && windowManager != null) {
            try {
                windowManager.removeView(overlay);
            } catch (IllegalArgumentException ignored) {
            }
            overlay = null;
        }
        stopForeground(true);
        super.onDestroy();
    }
}
