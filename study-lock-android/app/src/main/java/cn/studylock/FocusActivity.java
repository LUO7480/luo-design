package cn.studylock;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public final class FocusActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView remaining;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long end = Store.end(FocusActivity.this);
            if (end <= 0 || !FocusAccessibilityService.connected()) { leave(); return; }
            long seconds = Math.max(0, (end - System.currentTimeMillis() + 999) / 1000);
            remaining.setText(String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60));
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, () -> {});
        LinearLayout layout = Ui.column(this);
        layout.setGravity(Gravity.CENTER);
        layout.setClickable(true);
        boolean trial = Store.prefs(this).getLong("trial_end", 0) > 0;
        TextView title = Ui.text(this, layout, trial ? "正在试锁" : "现在是学习时间", 26, true);
        title.setGravity(Gravity.CENTER);
        remaining = Ui.text(this, layout, "", 42, true);
        remaining.setGravity(Gravity.CENTER);
        TextView hint = Ui.text(this, layout, trial ? "试运行：锁 60 秒，休息 30 秒，再锁 30 秒\n请测试电话、提醒和离线急救入口" : "倒计时结束后休息 10 分钟\n到整点自动锁回，23:00 全部解除", 16, false);
        hint.setGravity(Gravity.CENTER);
        Ui.button(this, layout, "拨打电话", true, view -> {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).setPackage(Policy.dialer(this)));
            } catch (RuntimeException error) {
                Store.fail(this, "电话界面未能打开，已解除限制。请勿启用正式计划。");
                leave();
                Toast.makeText(this, "电话界面未能打开，已解除限制", Toast.LENGTH_LONG).show();
            }
        });
        Ui.button(this, layout, "离线急救解锁", false, view -> RecoveryUi.unlock(this, this::leave));
        Ui.button(this, layout, "系统授权设置", false, view -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (RuntimeException error) { Toast.makeText(this, "请从手机设置打开无障碍服务", Toast.LENGTH_LONG).show(); }
        });
        Ui.text(this, layout, "免 USB 版：系统设置可关闭权限。通话期间暂停遮罩，挂断后恢复。", 13, false);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(layout);
        setContentView(scroll);
    }

    @Override public void onResume() {
        super.onResume();
        FocusAccessibilityService.appVisible(true);
        if (Controller.reconcile(this, false) <= 0) { leave(); return; }
        handler.removeCallbacks(tick);
        tick.run();
        openRecoveryIfRequested();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openRecoveryIfRequested();
    }

    private void openRecoveryIfRequested() {
        if (getIntent().getBooleanExtra("recovery", false) && Store.end(this) > 0) {
            getIntent().removeExtra("recovery");
            RecoveryUi.unlock(this, this::leave);
        }
    }

    @Override public void onPause() { FocusAccessibilityService.appVisible(false); super.onPause(); }

    private void leave() {
        handler.removeCallbacks(tick);
        Controller.reconcile(this, false);
        finish();
    }

    @Override public void onBackPressed() {}
    @Override public void onDestroy() { handler.removeCallbacks(tick); super.onDestroy(); }
}
