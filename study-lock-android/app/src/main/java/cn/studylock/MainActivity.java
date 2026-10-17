package cn.studylock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) { super.onCreate(state); }

    @Override public void onResume() {
        super.onResume();
        if (Controller.reconcile(this, true) > 0) return;
        showHome();
    }

    private void showHome() {
        SharedPreferences prefs = Store.prefs(this);
        boolean owner = Policy.owner(this);
        boolean ready = owner && Scheduler.exact(this) && Policy.dialer(this) != null;
        boolean enabled = prefs.getBoolean("enabled", false);
        boolean tested = prefs.getBoolean("trial_completed", false)
                && Policy.validationKey(this).equals(prefs.getString("trial_for", ""));
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = Ui.column(this);
        scroll.addView(layout);
        Ui.text(this, layout, "学习锁屏", 30, true);
        Ui.text(this, layout, "每天 19:00—23:00", 27, true);
        Ui.text(this, layout, "到点限制触摸和系统切换。\n电话保留，23:00 自动解除。", 16, false);
        Ui.text(this, layout, enabled ? "每日锁定已启用" : "每日锁定尚未启用", 18, true);
        String error = prefs.getString("error", "");
        if (!error.isEmpty()) Ui.text(this, layout, error, 15, false);

        if (!owner) {
            Ui.text(this, layout, "需要先配置设备管理权限，才能限制返回桌面和系统手势。安装本身不会锁住手机。", 15, false);
            Ui.button(this, layout, "查看配置说明", false, view -> new AlertDialog.Builder(this)
                    .setTitle("设备管理配置")
                    .setMessage("此版本需要连接电脑配置设备所有者，普通“设备管理员”权限不够。\n\n命令：\nadb shell dpm set-device-owner cn.studylock/.AdminReceiver\n\n手机已有账户或受系统限制时可能失败。失败后停止；不要为此直接清空手机。\n\n配置后先进行 2 分钟试锁。")
                    .setPositiveButton("知道了", null).show());
        }
        if (!Scheduler.exact(this)) {
            Ui.button(this, layout, "允许到点启动和解除", false, view -> {
                try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); }
                catch (RuntimeException errorOpen) { Toast.makeText(this, "请在系统设置中允许本应用设置闹钟和提醒", Toast.LENGTH_LONG).show(); }
            });
        }

        Ui.button(this, layout, "先试锁 2 分钟", false, view -> new AlertDialog.Builder(this)
                .setTitle("开始 2 分钟试锁")
                .setMessage("试锁期间仅电话可操作，到点自动恢复。请准备另一部电话，验证接听、拨打以及通话结束后的屏幕。\n\n请确认电源菜单的紧急呼叫入口仍可打开，无需实际拨打紧急号码。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开始试锁", (dialog, which) -> {
                    prefs.edit().putBoolean("enabled", false).putBoolean("trial_completed", false)
                            .putString("error", "").putLong("trial_end", System.currentTimeMillis() + 120000)
                            .putLong("trial_elapsed", SystemClock.elapsedRealtime() + 120000)
                            .putInt("trial_boot", Store.boot(this)).commit();
                    Controller.reconcile(this, true);
                }).show()).setEnabled(ready && !enabled);

        CheckBox calls = new CheckBox(this);
        calls.setText("试锁已结束，接听、拨打和紧急呼叫入口均正常");
        calls.setTextColor(Ui.INK);
        calls.setEnabled(tested && !enabled);
        layout.addView(calls);
        android.widget.Button arm = Ui.button(this, layout, enabled ? "关闭每日锁定" : "启用每日锁定", true, view -> {
            if (enabled) {
                Store.fail(this, "");
                Controller.reconcile(this, false);
                showHome();
            } else {
                new AlertDialog.Builder(this).setTitle("启用每日锁定")
                        .setMessage("每天 19:00 开始，23:00 自动结束。锁定期间不能返回桌面、切换应用或修改计划。\n\n若当前已在此时段，确认后立即开始。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("启用", (dialog, which) -> {
                            prefs.edit().putBoolean("enabled", true).putString("error", "")
                                    .putString("verified_for", Policy.validationKey(this)).commit();
                            if (Controller.reconcile(this, true) == 0) showHome();
                        }).show();
            }
        });
        arm.setEnabled(enabled || (ready && tested && calls.isChecked()));
        calls.setOnCheckedChangeListener((button, checked) -> arm.setEnabled(ready && tested && checked));

        if (owner) {
            Ui.button(this, layout, "移除设备管理", false, view -> new AlertDialog.Builder(this)
                    .setTitle("移除设备管理")
                    .setMessage("关闭每日计划，移除本应用的设备管理身份，然后可以正常卸载。本操作不会清空手机。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("移除", (dialog, which) -> {
                        try {
                            Store.fail(this, "");
                            Controller.reconcile(this, false);
                            Policy.manager(this).clearDeviceOwnerApp(getPackageName());
                            showHome();
                        } catch (RuntimeException removeError) { Toast.makeText(this, "系统拒绝移除，请保留错误信息以便排查", Toast.LENGTH_LONG).show(); }
                    }).show());
        }
        Ui.text(this, layout, "本地运行，无联网、账号或广告。\n学习时段内无手动退出密码。", 13, false);
        setContentView(scroll);
    }
}
