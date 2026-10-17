package cn.studylock;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

public final class FocusAccessibilityService extends AccessibilityService {
    private static volatile FocusAccessibilityService instance;
    private static boolean appVisible;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private ScrollView overlay;
    private TextView countdown;
    private Set<String> permitted = Collections.emptySet();
    private String foreground = "";
    private String foregroundClass = "";
    private long observedEnd = -1;
    private boolean attached;
    private volatile boolean renderingFailed;
    private long retryAt;
    private boolean receiverRegistered;
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            foreground = "";
            foregroundClass = "";
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) hide();
            refreshSoon();
        }
    };

    static boolean consented(Context context) { return Store.prefs(context).getBoolean("accessibility_consent_v3", false); }
    static boolean connected() { return instance != null && !instance.renderingFailed; }
    static boolean enabled(Context context) {
        String list = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName ours = new ComponentName(context, FocusAccessibilityService.class);
        if (list != null) for (String name : list.split(":")) if (ours.equals(ComponentName.unflattenFromString(name))) return true;
        return false;
    }

    static void appVisible(boolean visible) {
        appVisible = visible;
        refreshSoon();
    }

    static void refreshSoon() {
        FocusAccessibilityService service = instance;
        if (service != null) {
            service.handler.removeCallbacks(service.tick);
            service.handler.post(service.tick);
        }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long end = Store.end(FocusAccessibilityService.this);
            if (observedEnd != end) {
                observedEnd = end;
                Controller.reconcile(FocusAccessibilityService.this, false);
                end = Store.end(FocusAccessibilityService.this);
                observedEnd = end;
            }
            render(end);
            handler.removeCallbacks(this);
            handler.postDelayed(this, end > 0 ? 500 : 15000);
        }
    };

    @Override protected void onServiceConnected() {
        instance = this;
        renderingFailed = false;
        Store.prefs(this).edit().putString("service_status", "已连接").commit();
        windows = getSystemService(WindowManager.class);
        permitted = Policy.permittedPackages(this);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
        Controller.reconcile(this, false);
        refreshSoon();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String name = event.getPackageName().toString();
        // Our overlay also emits window events; never mistake it for an app switch.
        if (!name.equals(getPackageName())) {
            foreground = name;
            foregroundClass = event.getClassName() == null ? "" : event.getClassName().toString();
        }
        refreshSoon();
    }

    private void render(long end) {
        if (renderingFailed && SystemClock.elapsedRealtime() < retryAt) { hide(); return; }
        renderingFailed = false;
        try {
            boolean phone = Policy.phonePermission(this);
            boolean calling = phone && getSystemService(TelecomManager.class).isInCall();
            boolean callSurface = CallRules.allowed(foreground, foregroundClass, calling);
            boolean cover = BlockingRules.cover(end, System.currentTimeMillis(), consented(this), phone,
                    getSystemService(KeyguardManager.class).isKeyguardLocked()
                            || !getSystemService(PowerManager.class).isInteractive(), callSurface, appVisible,
                    permitted.contains(foreground));
            if (!cover) { hide(); return; }
            if (overlay == null) createOverlay();
            long seconds = Math.max(0, (end - System.currentTimeMillis() + 999) / 1000);
            countdown.setText(String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60));
            if (!attached) {
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.OPAQUE);
                params.gravity = Gravity.TOP | Gravity.START;
                params.setTitle("学习锁屏 · 专注遮罩");
                windows.addView(overlay, params);
                attached = true;
            }
        } catch (RuntimeException error) {
            renderingFailed = true;
            retryAt = SystemClock.elapsedRealtime() + 2000;
            hide();
            overlay = null;
            countdown = null;
            Store.prefs(this).edit().putString("error", "遮罩暂不可用，计时保留。正在重试；可从系统设置恢复无障碍服务。").commit();
        }
    }

    private void createOverlay() {
        overlay = new ScrollView(this);
        overlay.setFillViewport(true);
        overlay.setBackgroundColor(Ui.PAPER);
        LinearLayout content = Ui.column(this);
        content.setGravity(Gravity.CENTER);
        content.setClickable(true);
        overlay.addView(content);
        TextView title = Ui.text(this, content, "现在是学习时间", 26, true);
        title.setGravity(Gravity.CENTER);
        countdown = Ui.text(this, content, "", 42, true);
        countdown.setGravity(Gravity.CENTER);
        TextView hint = Ui.text(this, content, "到点自动休息；电话与离线急救仍可用", 16, false);
        hint.setGravity(Gravity.CENTER);
        Ui.button(this, content, "拨打电话", true, view -> {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:")).setPackage(Policy.dialer(this))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException error) {
                Store.fail(this, "电话界面未能打开，已解除限制。请检查默认电话应用后重新试锁。");
                Controller.reconcile(this, false);
            }
        });
        Ui.button(this, content, "离线急救解锁", false, view -> openFocus(true));
        Ui.text(this, content, "可正常息屏。微信仅放行已识别的通话页，聊天与桌面仍被拦截。", 13, false);
    }

    private void openFocus(boolean recovery) {
        try {
            startActivity(new Intent(this, FocusActivity.class).putExtra("recovery", recovery)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (RuntimeException error) {
            Store.fail(this, "系统拒绝打开学习或急救界面，已停止限制。");
            Controller.reconcile(this, false);
        }
    }

    private void hide() {
        if (!attached) return;
        try { windows.removeViewImmediate(overlay); } catch (RuntimeException ignored) {}
        attached = false;
    }

    @Override public void onInterrupt() { hide(); }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        hide();
        if (receiverRegistered) unregisterReceiver(screen);
        if (instance == this) instance = null;
        // A disrupted rehearsal cannot certify the consumer lock.
        if (Store.trialEnd(this) > 0) Store.prefs(this).edit().putBoolean("trial_interrupted", true).commit();
        Store.prefs(this).edit().putString("service_status", "服务已中断；计划保留，重连后继续。").commit();
        super.onDestroy();
    }
}
