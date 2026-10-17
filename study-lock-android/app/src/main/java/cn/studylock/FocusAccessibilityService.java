package cn.studylock;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
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
    private static FocusAccessibilityService instance;
    private static boolean appVisible;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private ScrollView overlay;
    private TextView countdown;
    private Set<String> permitted = Collections.emptySet();
    private String foreground = "";
    private long observedEnd = -1;
    private boolean attached;
    private boolean renderingFailed;

    static boolean consented(Context context) { return Store.prefs(context).getBoolean("accessibility_consent_v3", false); }
    static boolean connected() { return instance != null; }
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
        windows = getSystemService(WindowManager.class);
        permitted = Policy.permittedPackages(this);
        Controller.reconcile(this, false);
        refreshSoon();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String name = event.getPackageName().toString();
        // Our overlay also emits window events; never mistake it for an app switch.
        if (!name.equals(getPackageName())) foreground = name;
        refreshSoon();
    }

    private void render(long end) {
        try {
            boolean phone = Policy.phonePermission(this);
            boolean calling = phone && getSystemService(TelecomManager.class).isInCall();
            boolean cover = BlockingRules.cover(end, System.currentTimeMillis(), consented(this), phone,
                    getSystemService(KeyguardManager.class).isKeyguardLocked(), calling, appVisible,
                    permitted.contains(foreground));
            if (!cover || renderingFailed) { hide(); return; }
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
            hide();
            Store.fail(this, "系统无法显示学习遮罩或读取通话状态，已停止计划。请重新打开无障碍服务后试锁。");
            Controller.reconcile(this, false);
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
        Ui.button(this, content, "打开学习界面", true, view -> openFocus(false));
        Ui.button(this, content, "离线急救解锁", false, view -> openFocus(true));
        Ui.button(this, content, "拨打电话", false, view -> {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:")).setPackage(Policy.dialer(this))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException error) {
                Store.fail(this, "电话界面未能打开，已解除限制。请检查默认电话应用后重新试锁。");
                Controller.reconcile(this, false);
            }
        });
        Ui.button(this, content, "系统授权设置", false, view -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (RuntimeException error) { openFocus(false); }
        });
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
        if (instance == this) instance = null;
        // A disrupted rehearsal cannot certify the consumer lock.
        if (Store.trialEnd(this) > 0) Store.fail(this, "试运行被中断，请重新开启无障碍服务后再试。");
        super.onDestroy();
    }
}
