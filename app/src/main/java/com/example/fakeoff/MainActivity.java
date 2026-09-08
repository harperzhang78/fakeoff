package com.example.fakeoff;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.app.NotificationManager;
import android.app.NotificationManager.Policy;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final long UNLOCK_TIMEOUT_MILLIS = 10_000;
    private static final String WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS";
    private static final String POWER_GESTURE_SETTING = "camera_double_tap_power_gesture_disabled";
    private static final String WIRELESS_EMERGENCY_ALERTS_SETTINGS =
            "android.settings.WIRELESS_EMERGENCY_ALERTS";
    private static final String PREFS = "turn_off_state";
    private static final String PREF_POWER_GESTURE_SAVED = "power_gesture_saved";
    private static final String PREF_POWER_GESTURE_PREVIOUS = "power_gesture_previous";
    private final UnlockSequence unlockSequence = new UnlockSequence(UNLOCK_TIMEOUT_MILLIS);
    private NotificationManager notificationManager;
    private int previousInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
    private Policy previousNotificationPolicy;
    private boolean changedInterruptionFilter;
    private boolean changedNotificationPolicy;
    private boolean fakeOff;
    private boolean lockTaskRequested;
    private TextView dndStatus;
    private TextView powerGestureStatus;
    private final Runnable restoreImmersiveMode = () -> {
        if (fakeOff && !isFinishing() && !isDestroyed()) {
            applyImmersiveMode();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        configureSystemBarAppearance();
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (fakeOff) {
            applyMinimumBrightness();
            applyImmersiveMode();
        } else {
            updateDndStatus();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        keepFakeOffInFront();
    }

    @Override
    protected void onStop() {
        super.onStop();
        keepFakeOffInFront();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            keepFakeOffInFront();
        } else if (fakeOff) {
            applyMinimumBrightness();
            applyImmersiveMode();
        }
    }

    private void keepFakeOffInFront() {
        if (!fakeOff || isFinishing() || isDestroyed()) {
            return;
        }
        ActivityManager activityManager =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            getWindow().getDecorView().postDelayed(() -> {
                if (fakeOff && !isFinishing() && !isDestroyed()) {
                    activityManager.moveTaskToFront(getTaskId(), 0);
                    applyImmersiveMode();
                }
            }, 100);
        }
    }

    private void showHome() {
        stopAppPinning();
        fakeOff = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        setShowOverLockScreen(false);
        restoreSoundPolicy();
        restorePowerGesture();
        unlockSequence.reset();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(attributes);
        showSystemBars();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(48), dp(24), dp(24));
        content.setBackgroundColor(Color.rgb(18, 18, 18));

        TextView title = text(getString(R.string.title), 30);
        title.setGravity(Gravity.CENTER);
        content.addView(title, matchWrap());

        TextView description = text(getString(R.string.description), 17);
        description.setPadding(0, dp(24), 0, dp(20));
        content.addView(description, matchWrap());

        TextView limitation = text(getString(R.string.android_limit), 14);
        limitation.setTextColor(Color.LTGRAY);
        limitation.setPadding(0, 0, 0, dp(24));
        content.addView(limitation, matchWrap());

        dndStatus = text("", 14);
        content.addView(dndStatus, matchWrap());

        powerGestureStatus = text("", 14);
        powerGestureStatus.setPadding(0, dp(8), 0, 0);
        content.addView(powerGestureStatus, matchWrap());

        Button grantDnd = new Button(this);
        grantDnd.setText(R.string.grant_dnd);
        grantDnd.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        content.addView(grantDnd, matchWrap());

        Button displaySettings = new Button(this);
        displaySettings.setText(R.string.open_display_settings);
        displaySettings.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS)));
        content.addView(displaySettings, matchWrap());

        Button emergencyAlertSettings = new Button(this);
        emergencyAlertSettings.setText(R.string.open_emergency_alert_settings);
        emergencyAlertSettings.setOnClickListener(view -> openEmergencyAlertSettings());
        content.addView(emergencyAlertSettings, matchWrap());

        Button gestureSettings = new Button(this);
        gestureSettings.setText(R.string.open_gesture_settings);
        gestureSettings.setOnClickListener(view -> openGestureSettings());
        content.addView(gestureSettings, matchWrap());

        Button activate = new Button(this);
        activate.setText(R.string.activate);
        activate.setTextColor(Color.WHITE);
        activate.setTextSize(20);
        activate.setGravity(Gravity.CENTER);
        activate.setBackgroundTintList(null);
        activate.setBackgroundResource(R.drawable.activate_button_background);
        activate.setElevation(dp(8));
        activate.setOnClickListener(view -> enterFakeOff(false));
        LinearLayout.LayoutParams activateParams =
                new LinearLayout.LayoutParams(dp(200), dp(200));
        activateParams.topMargin = dp(24);
        content.addView(activate, activateParams);

        Button activatePinned = new Button(this);
        activatePinned.setText(R.string.activate_pinned);
        activatePinned.setOnClickListener(view -> enterFakeOff(true));
        LinearLayout.LayoutParams pinnedParams = matchWrap();
        pinnedParams.topMargin = dp(16);
        content.addView(activatePinned, pinnedParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(content);
        setContentView(scrollView);
        updateDndStatus();
        updatePowerGestureStatus();
    }

    private void enterFakeOff(boolean allowScreenPinning) {
        fakeOff = true;
        // An explicit app orientation prevents Android from offering its rotation
        // suggestion button while the device is in turn-off mode.
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setShowOverLockScreen(true);
        unlockSequence.reset();
        disablePowerGesture();
        if (notificationManager.isNotificationPolicyAccessGranted()) {
            previousInterruptionFilter = notificationManager.getCurrentInterruptionFilter();
            previousNotificationPolicy = notificationManager.getNotificationPolicy();
            notificationManager.setNotificationPolicy(hiddenNotificationPolicy(
                    previousNotificationPolicy));
            changedNotificationPolicy = true;
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
            changedInterruptionFilter = true;
        }

        View blackScreen = new View(this);
        blackScreen.setBackgroundColor(Color.BLACK);
        blackScreen.setFocusableInTouchMode(true);
        blackScreen.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                unlockSequence.tap(SystemClock.elapsedRealtime());
            }
            return true;
        });
        setContentView(blackScreen);
        blackScreen.requestFocus();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Match game-style full screen at the window level as well as through
        // immersive mode. This keeps the status bar, navigation bar, and the
        // large-screen taskbar out of the black surface.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        applyMinimumBrightness();
        applyImmersiveMode();
        startLockTaskMode(allowScreenPinning);
    }

    private void startLockTaskMode(boolean allowScreenPinning) {
        // An allowlisted device-owner installation enters kiosk mode without a
        // prompt. On an ordinary device, call startLockTask() only after the user
        // explicitly chooses the pinned option because Android owns and displays
        // the screen-pinning confirmation.
        DevicePolicyManager devicePolicyManager =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        boolean kioskPermitted = devicePolicyManager != null
                && devicePolicyManager.isLockTaskPermitted(getPackageName());
        if (!kioskPermitted && !allowScreenPinning) {
            lockTaskRequested = false;
            return;
        }
        try {
            startLockTask();
            lockTaskRequested = true;
        } catch (IllegalArgumentException | SecurityException ignored) {
            lockTaskRequested = false;
        }
    }

    private void stopAppPinning() {
        if (!lockTaskRequested) {
            return;
        }
        ActivityManager activityManager =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null
                && activityManager.getLockTaskModeState()
                != ActivityManager.LOCK_TASK_MODE_NONE) {
            try {
                stopLockTask();
            } catch (IllegalArgumentException | SecurityException ignored) {
                // The system may already have ended pinning with its escape gesture.
            }
        }
        lockTaskRequested = false;
    }

    private void setShowOverLockScreen(boolean enabled) {
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(enabled);
            // Never wake the phone: only cover the lock screen after the user wakes it.
            setTurnScreenOn(false);
            return;
        }

        if (enabled) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!fakeOff) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            long now = SystemClock.elapsedRealtime();
            if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN
                    && event.getRepeatCount() == 0) {
                if (unlockSequence.volumeDown(now)) {
                    showHome();
                }
            } else {
                unlockSequence.otherInput(now);
            }
        }
        // Consume all keys delivered to the activity, including volume and Back.
        return true;
    }

    @Override
    public void onBackPressed() {
        if (!fakeOff) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        stopAppPinning();
        restoreSoundPolicy();
        restorePowerGesture();
        super.onDestroy();
    }

    private boolean canWriteSecureSettings() {
        return checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    private void disablePowerGesture() {
        if (!canWriteSecureSettings()) {
            return;
        }
        try {
            int previous = Settings.Secure.getInt(getContentResolver(), POWER_GESTURE_SETTING, 0);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putInt(PREF_POWER_GESTURE_PREVIOUS, previous)
                    .putBoolean(PREF_POWER_GESTURE_SAVED, true)
                    .commit();
            if (!Settings.Secure.putInt(getContentResolver(), POWER_GESTURE_SETTING, 1)) {
                clearSavedPowerGesture();
            }
        } catch (SecurityException ignored) {
            // The development permission may have been revoked since the check.
            clearSavedPowerGesture();
        }
    }

    private void restorePowerGesture() {
        android.content.SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!preferences.getBoolean(PREF_POWER_GESTURE_SAVED, false) || !canWriteSecureSettings()) {
            return;
        }
        try {
            int previous = preferences.getInt(PREF_POWER_GESTURE_PREVIOUS, 0);
            if (Settings.Secure.putInt(getContentResolver(), POWER_GESTURE_SETTING, previous)) {
                clearSavedPowerGesture();
            }
        } catch (SecurityException ignored) {
            // Keep the saved value so a later launch can restore it after access returns.
        }
    }

    private void clearSavedPowerGesture() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .remove(PREF_POWER_GESTURE_PREVIOUS)
                .remove(PREF_POWER_GESTURE_SAVED)
                .apply();
    }

    private void openGestureSettings() {
        Intent gestureSettings = new Intent("android.settings.GESTURE_SETTINGS");
        if (gestureSettings.resolveActivity(getPackageManager()) == null) {
            gestureSettings = new Intent(Settings.ACTION_SETTINGS);
        }
        startActivity(gestureSettings);
    }

    private void openEmergencyAlertSettings() {
        Intent emergencyAlertSettings = new Intent(WIRELESS_EMERGENCY_ALERTS_SETTINGS);
        if (emergencyAlertSettings.resolveActivity(getPackageManager()) == null) {
            emergencyAlertSettings = new Intent(Settings.ACTION_SETTINGS);
        }
        startActivity(emergencyAlertSettings);
    }

    private void restoreSoundPolicy() {
        if (changedNotificationPolicy
                && notificationManager != null
                && notificationManager.isNotificationPolicyAccessGranted()
                && previousNotificationPolicy != null) {
            notificationManager.setNotificationPolicy(previousNotificationPolicy);
            changedNotificationPolicy = false;
            previousNotificationPolicy = null;
        }
        if (changedInterruptionFilter
                && notificationManager != null
                && notificationManager.isNotificationPolicyAccessGranted()) {
            notificationManager.setInterruptionFilter(previousInterruptionFilter);
            changedInterruptionFilter = false;
        }
    }

    private Policy hiddenNotificationPolicy(Policy currentPolicy) {
        int hiddenEffects;
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            hiddenEffects = Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT
                    | Policy.SUPPRESSED_EFFECT_LIGHTS
                    | Policy.SUPPRESSED_EFFECT_PEEK
                    | Policy.SUPPRESSED_EFFECT_STATUS_BAR
                    | Policy.SUPPRESSED_EFFECT_BADGE
                    | Policy.SUPPRESSED_EFFECT_AMBIENT
                    | Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST;
        } else {
            hiddenEffects = Policy.SUPPRESSED_EFFECT_SCREEN_OFF
                    | Policy.SUPPRESSED_EFFECT_SCREEN_ON;
        }
        int suppressedVisualEffects = currentPolicy.suppressedVisualEffects | hiddenEffects;
        return new Policy(
                currentPolicy.priorityCategories,
                currentPolicy.priorityCallSenders,
                currentPolicy.priorityMessageSenders,
                suppressedVisualEffects);
    }

    private void applyImmersiveMode() {
        Window window = getWindow();
        View decorView = window.getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    private void applyMinimumBrightness() {
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        // Android does not permit an app that is still receiving touch input to
        // physically power down the panel. Zero is the lowest per-window
        // backlight override; together with the solid black surface it also
        // turns OLED pixels fully black.
        attributes.screenBrightness = 0.0f;
        getWindow().setAttributes(attributes);
    }

    private void configureSystemBarAppearance() {
        Window window = getWindow();
        window.setNavigationBarColor(Color.BLACK);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }

        View decorView = window.getDecorView();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            decorView.setOnApplyWindowInsetsListener((view, insets) -> {
                if (fakeOff && insets.isVisible(WindowInsets.Type.systemBars())) {
                    // A bottom-edge swipe may briefly reveal Android's gesture handle.
                    // Re-hide it as soon as the system finishes dispatching the insets.
                    view.removeCallbacks(restoreImmersiveMode);
                    applyImmersiveMode();
                    view.postOnAnimation(restoreImmersiveMode);
                }
                return insets;
            });
        } else {
            decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
                if (fakeOff) {
                    decorView.removeCallbacks(restoreImmersiveMode);
                    decorView.post(restoreImmersiveMode);
                }
            });
        }
    }

    private void showSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(true);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void updateDndStatus() {
        if (dndStatus != null) {
            dndStatus.setText(notificationManager.isNotificationPolicyAccessGranted()
                    ? R.string.dnd_status_on : R.string.dnd_status_off);
        }
    }

    private void updatePowerGestureStatus() {
        if (powerGestureStatus != null) {
            powerGestureStatus.setText(canWriteSecureSettings()
                    ? R.string.power_gesture_status_on : R.string.power_gesture_status_off);
        }
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
