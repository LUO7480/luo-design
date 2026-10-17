package cn.studylock;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public final class FocusActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView remaining;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long end = Store.end(FocusActivity.this);
            if (end <= 0) { leave(); return; }
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
        layout.setOnTouchListener((view, event) -> true);
        boolean trial = Store.prefs(this).getLong("trial_end", 0) > 0;
        TextView title = Ui.text(this, layout, trial ? "正在试锁" : "现在是学习时间", 26, true);
        title.setGravity(Gravity.CENTER);
        remaining = Ui.text(this, layout, "", 46, true);
        remaining.setGravity(Gravity.CENTER);
        TextView hint = Ui.text(this, layout, trial ? "2 分钟结束后自动恢复\n请测试接听和拨打电话" : "23:00 自动恢复\n除电话外，屏幕操作已限制", 16, false);
        hint.setGravity(Gravity.CENTER);
        Ui.button(this, layout, "拨打电话", true, view -> {
            try {
                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).setPackage(Policy.dialer(this));
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLockTaskEnabled(true);
                startActivity(intent, options.toBundle());
            } catch (RuntimeException error) {
                Store.fail(this, "电话界面未能打开，已解除锁定。请勿启用正式计划。");
                leave();
                Toast.makeText(this, "电话界面未能打开，已解除锁定", Toast.LENGTH_LONG).show();
            }
        });
        setContentView(layout);
    }

    @Override public void onResume() {
        super.onResume();
        if (Controller.reconcile(this, false) <= 0) { leave(); return; }
        try {
            // Starting lock task while the system keyguard is locked can trap the user.
            if (!getSystemService(KeyguardManager.class).isKeyguardLocked()) startLockTask();
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            handler.removeCallbacks(tick);
            tick.run();
        } catch (RuntimeException error) {
            Store.fail(this, "系统拒绝进入锁定模式，已解除锁定。");
            leave();
        }
    }

    private void leave() {
        handler.removeCallbacks(tick);
        Controller.reconcile(this, false);
        try { stopLockTask(); } catch (RuntimeException ignored) {}
        finish();
    }

    @Override public void onBackPressed() {}
    @Override public void onDestroy() { handler.removeCallbacks(tick); super.onDestroy(); }
}
