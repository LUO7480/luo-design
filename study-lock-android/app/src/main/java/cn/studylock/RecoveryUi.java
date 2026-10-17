package cn.studylock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.os.PersistableBundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.time.ZoneId;

final class RecoveryUi {
    static void setup(Activity activity, Runnable saved) {
        if (Store.committed(activity) || Store.prefs(activity).getBoolean("enabled", false)) return;
        String master = RecoveryCrypto.digits(64);
        LinearLayout layout = Ui.column(activity);
        Ui.text(activity, layout, "请把长码交给妻子保管，并让她保存离线急救网页。此长码仅在本次设置时显示。", 15, false);
        TextView code = Ui.text(activity, layout, RecoveryCrypto.grouped(master), 19, true);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextIsSelectable(false);
        final boolean[] copied = {false};
        Ui.button(activity, layout, "一键复制 64 位保管码", true, view -> {
            ClipData clip = ClipData.newPlainText("学习锁屏急救保管码", master);
            PersistableBundle flags = new PersistableBundle();
            flags.putBoolean("android.content.extra.IS_SENSITIVE", true);
            clip.getDescription().setExtras(flags);
            activity.getSystemService(ClipboardManager.class).setPrimaryClip(clip);
            copied[0] = true;
            Toast.makeText(activity, "请粘贴给妻子；确认保存后会清除本应用复制的长码", Toast.LENGTH_LONG).show();
        });
        Ui.text(activity, layout, "妻子可电话口述 8 位一次性码，你无需打开微信。请清理自己聊天中含长码的发送记录，否则休息时仍可找回长码。完全失联时，可用事先封存的纸质长码直接输入。请勿将长码上传 GitHub。", 14, false);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(layout);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("托管离线急救码")
                .setView(scroll).setNegativeButton("取消", null).setPositiveButton("妻子已保存，完成托管", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (!copied[0]) { Toast.makeText(activity, "请先复制并交给妻子保存", Toast.LENGTH_SHORT).show(); return; }
                if (Store.committed(activity) || Store.prefs(activity).getBoolean("enabled", false)) { dialog.dismiss(); return; }
                try {
                    RecoveryVault.install(activity, master);
                    ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
                    ClipData current = clipboard.getPrimaryClip();
                    if (current != null && current.getItemCount() > 0 && master.equals(String.valueOf(current.getItemAt(0).coerceToText(activity)))) clipboard.clearPrimaryClip();
                    dialog.dismiss();
                    saved.run();
                } catch (Exception error) { Toast.makeText(activity, "本机密钥保存失败，请勿启用锁定", Toast.LENGTH_LONG).show(); }
            });
        });
        dialog.show();
    }

    static void unlock(Activity activity, Runnable unlocked) {
        if (!RecoveryVault.ready(activity)) {
            new AlertDialog.Builder(activity).setMessage("尚未托管急救码。请等待休息或到点自动解除，之后先完成托管。").setPositiveButton("知道了", null).show();
            return;
        }
        String challenge = RecoveryVault.challenge(activity);
        boolean practice = !Store.prefs(activity).getBoolean("enabled", false) && Store.trialEnd(activity) == 0;
        LinearLayout layout = Ui.column(activity);
        TextView challengeView = Ui.text(activity, layout, "把挑战号告诉妻子：\n" + RecoveryCrypto.grouped(challenge), 20, true);
        TextView info = Ui.text(activity, layout, "她在离线网页输入保管码和挑战号，读出 8 位码。挑战号 15 分钟有效。", 14, false);
        TextView entered = Ui.text(activity, layout, "请输入 8 位一次性码", 21, true);
        entered.setTypeface(Typeface.MONOSPACE);
        entered.setGravity(Gravity.CENTER);
        StringBuilder input = new StringBuilder();
        boolean[] longCode = {false};
        Runnable refresh = () -> entered.setText(input.length() == 0 ? (longCode[0] ? "请输入 64 位纸质长码" : "请输入 8 位一次性码")
                : RecoveryCrypto.grouped(input.toString()) + "\n" + input.length() + "/" + (longCode[0] ? 64 : 8));
        for (String[] keys : new String[][]{{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"清空", "0", "退格"}}) {
            LinearLayout row = new LinearLayout(activity);
            layout.addView(row, new LinearLayout.LayoutParams(-1, -2));
            for (String key : keys) {
                Button button = new Button(activity);
                button.setText(key);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(activity, 48), 1);
                row.addView(button, params);
                button.setOnClickListener(view -> {
                    if (key.equals("清空")) input.setLength(0);
                    else if (key.equals("退格")) { if (input.length() > 0) input.deleteCharAt(input.length() - 1); }
                    else if (input.length() < (longCode[0] ? 64 : 8)) input.append(key);
                    refresh.run();
                });
            }
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(layout);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(practice ? "验证急救码，不改变计划" : "离线急救：当晚解除")
                .setView(scroll).setNegativeButton("取消", null).setNeutralButton("改用纸质长码", null).setPositiveButton(practice ? "验证急救码" : "验证并解除", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                longCode[0] = !longCode[0];
                input.setLength(0);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText(longCode[0] ? "改用 8 位短码" : "改用纸质长码");
                challengeView.setVisibility(longCode[0] ? android.view.View.GONE : android.view.View.VISIBLE);
                info.setText(longCode[0] ? "仅在完全失联且持有封存纸质备份时使用。\n按每组 4 位输入，无需微信、剪贴板或联网。"
                        : "挑战号：" + RecoveryCrypto.grouped(challenge) + "\n请妻子读出对应的 8 位码，成功后此挑战号立即作废。");
                refresh.run();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (input.length() != (longCode[0] ? 64 : 8)) { info.setText("位数不足，请继续输入；可以用退格修改。"); return; }
                String error = RecoveryVault.verify(activity, input.toString(), longCode[0]);
                input.setLength(0);
                refresh.run();
                if (error != null) { info.setText(error); return; }
                boolean scheduled = Store.prefs(activity).getBoolean("enabled", false);
                boolean rehearsing = Store.trialEnd(activity) > 0;
                android.content.SharedPreferences.Editor edit = Store.prefs(activity).edit()
                        .putLong("trial_end", 0).putLong("trial_elapsed", 0)
                        .putString("error", scheduled ? "当晚限制已解除，每日计划将在次日 19:00 恢复。"
                                : rehearsing ? "试运行已解除，请重新完整试运行后再启用计划。" : "急救码验证成功，未改变学习计划。");
                if (scheduled) edit.putLong("skip_until", RecoveryRules.tonightEnd(System.currentTimeMillis(), ZoneId.systemDefault()));
                if (rehearsing) edit.putBoolean("trial_completed", false);
                edit.commit();
                Controller.reconcile(activity, false);
                dialog.dismiss();
                unlocked.run();
            });
        });
        dialog.show();
    }
}
