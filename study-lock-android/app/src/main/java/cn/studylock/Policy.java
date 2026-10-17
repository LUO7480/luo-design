package cn.studylock;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.UserManager;
import android.telecom.TelecomManager;
import java.util.LinkedHashSet;

final class Policy {
    static DevicePolicyManager manager(Context context) {
        return context.getSystemService(DevicePolicyManager.class);
    }

    static ComponentName admin(Context context) { return new ComponentName(context, AdminReceiver.class); }
    static boolean owner(Context context) { return manager(context).isDeviceOwnerApp(context.getPackageName()); }

    static String dialer(Context context) {
        String name = context.getSystemService(TelecomManager.class).getDefaultDialerPackage();
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"));
        if (name != null) intent.setPackage(name);
        ResolveInfo result = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return result != null && result.activityInfo != null ? result.activityInfo.packageName : null;
    }

    static String validationKey(Context context) { return Build.FINGERPRINT + "|" + dialer(context); }

    static void prepare(Context context) {
        if (!owner(context)) throw new IllegalStateException("尚未配置设备管理，不能强制锁定。");
        String phone = dialer(context);
        if (phone == null) throw new IllegalStateException("没有找到系统拨号界面，已停止锁定。");
        DevicePolicyManager manager = manager(context);
        ComponentName admin = admin(context);
        SharedPreferences prefs = Store.prefs(context);
        if (!prefs.getBoolean("policy_saved", false)) {
            prefs.edit().putInt("old_features", manager.getLockTaskFeatures(admin))
                    .putStringSet("old_packages", new LinkedHashSet<>(java.util.Arrays.asList(manager.getLockTaskPackages(admin))))
                    .putBoolean("old_date_restriction", manager.getUserRestrictions(admin).getBoolean(UserManager.DISALLOW_CONFIG_DATE_TIME))
                    .putBoolean("old_window_restriction", manager.getUserRestrictions(admin).getBoolean(UserManager.DISALLOW_CREATE_WINDOWS))
                    .putBoolean("policy_saved", true).commit();
        }
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        allowed.add(context.getPackageName());
        allowed.add(phone);
        for (String name : new String[]{"com.android.phone", "com.android.incallui", "com.android.server.telecom", "com.android.emergency"}) {
            try {
                if ((context.getPackageManager().getApplicationInfo(name, 0).flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) allowed.add(name);
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        for (ResolveInfo result : context.getPackageManager().queryIntentServices(new Intent("android.telecom.InCallService"), 0)) {
            if (result.serviceInfo != null && "android.permission.BIND_INCALL_SERVICE".equals(result.serviceInfo.permission)
                    && (result.serviceInfo.packageName.equals(phone)
                    || (result.serviceInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)) {
                allowed.add(result.serviceInfo.packageName);
            }
        }
        manager.setLockTaskPackages(admin, allowed.toArray(new String[0]));
        manager.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                | DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
                | DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                | DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK);
        manager.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME);
        manager.addUserRestriction(admin, UserManager.DISALLOW_CREATE_WINDOWS);
        if (!manager.isLockTaskPermitted(context.getPackageName())) throw new IllegalStateException("系统未允许锁定任务，已停止锁定。");
    }

    static void release(Context context) {
        SharedPreferences prefs = Store.prefs(context);
        if (!owner(context) || !prefs.getBoolean("policy_saved", false)) return;
        DevicePolicyManager manager = manager(context);
        ComponentName admin = admin(context);
        // Removing our package also exits lock task mode when the phone app is in front.
        manager.setLockTaskPackages(admin, new String[0]);
        manager.setLockTaskFeatures(admin, prefs.getInt("old_features", DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS));
        if (!prefs.getBoolean("old_date_restriction", false)) manager.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME);
        if (!prefs.getBoolean("old_window_restriction", false)) manager.clearUserRestriction(admin, UserManager.DISALLOW_CREATE_WINDOWS);
        java.util.Set<String> old = prefs.getStringSet("old_packages", java.util.Collections.emptySet());
        manager.setLockTaskPackages(admin, old.toArray(new String[0]));
        prefs.edit().putBoolean("policy_saved", false).remove("deadline_wall").remove("deadline_elapsed").remove("deadline_boot").commit();
    }
}
