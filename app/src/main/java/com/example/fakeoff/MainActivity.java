package com.example.fakeoff;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
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
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final long UNLOCK_TIMEOUT_MILLIS = 10_000;
    private final UnlockSequence unlockSequence = new UnlockSequence(UNLOCK_TIMEOUT_MILLIS);
    private NotificationManager notificationManager;
    private int previousInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
    private boolean changedInterruptionFilter;
    private boolean fakeOff;
    private TextView dndStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (fakeOff) {
            applyImmersiveMode();
        } else {
            updateDndStatus();
        }
    }

    private void showHome() {
        fakeOff = false;
        restoreSoundPolicy();
        unlockSequence.reset();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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

        Button grantDnd = new Button(this);
        grantDnd.setText(R.string.grant_dnd);
        grantDnd.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        content.addView(grantDnd, matchWrap());

        Button activate = new Button(this);
        activate.setText(R.string.activate);
        activate.setOnClickListener(view -> enterFakeOff());
        LinearLayout.LayoutParams activateParams = matchWrap();
        activateParams.topMargin = dp(16);
        content.addView(activate, activateParams);

        setContentView(content);
        updateDndStatus();
    }

    private void enterFakeOff() {
        fakeOff = true;
        unlockSequence.reset();
        if (notificationManager.isNotificationPolicyAccessGranted()) {
            previousInterruptionFilter = notificationManager.getCurrentInterruptionFilter();
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
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = 0.0f;
        getWindow().setAttributes(attributes);
        applyImmersiveMode();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!fakeOff) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN
                && event.getRepeatCount() == 0
                && unlockSequence.volumeDown(SystemClock.elapsedRealtime())) {
            showHome();
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
        restoreSoundPolicy();
        super.onDestroy();
    }

    private void restoreSoundPolicy() {
        if (changedInterruptionFilter
                && notificationManager != null
                && notificationManager.isNotificationPolicyAccessGranted()) {
            notificationManager.setInterruptionFilter(previousInterruptionFilter);
            changedInterruptionFilter = false;
        }
    }

    private void applyImmersiveMode() {
        Window window = getWindow();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void showSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
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
