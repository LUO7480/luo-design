package cn.studylock;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.UserManager;
import android.provider.Settings;
import android.telecom.TelecomManager;
import java.util.LinkedHashSet;
import java.util.Set;

final class Policy {
    static DevicePolicyManager manager(Context context) { return context.getSystemService(DevicePolicyManager.class); }
    static ComponentName admin(Context context) { return new ComponentName(context, AdminReceiver.class); }
    static boolean owner(Context context) { return manager(context).isDeviceOwnerApp(context.getPackageName()); }

    static String dialer(Context context) {
        String name = context.getSystemService(TelecomManager.class).getDefaultDialerPackage();
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"));
        if (name != null) intent.setPackage(name);
        ResolveInfo result = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return result != null && result.activityInfo != null ? result.activityInfo.packageName : null;
    }

    static boolean phonePermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    static String validationKey(Context context) { return "consumer-overlay-v3|" + Build.FINGERPRINT + "|" + dialer(context); }

    private static boolean systemPackage(Context context, String name) {
        try {
            return (context.getPackageManager().getApplicationInfo(name, 0).flags
                    & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        } catch (PackageManager.NameNotFoundException ignored) { return false; }
    }

    static Set<String> permittedPackages(Context context) {
        Set<String> allowed = new LinkedHashSet<>();
        allowed.add(context.getPackageName());
        String phone = dialer(context);
        if (phone != null) allowed.add(phone);
        // System permission and uninstall surfaces remain usable by design.
        for (String action : new String[]{Settings.ACTION_SETTINGS, Settings.ACTION_USAGE_ACCESS_SETTINGS, Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM}) {
            Intent intent = new Intent(action);
            if (action.equals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    || action.equals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    || action.equals(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    || action.equals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) intent.setData(Uri.parse("package:" + context.getPackageName()));
            ResolveInfo result = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (result != null && result.activityInfo != null && systemPackage(context, result.activityInfo.packageName)) {
                allowed.add(result.activityInfo.packageName);
            }
        }
        for (String name : new String[]{"com.android.settings", "com.android.phone",
                "com.android.incallui", "com.android.server.telecom", "com.android.emergency",
                "com.android.permissioncontroller", "com.google.android.permissioncontroller",
                "com.android.packageinstaller", "com.google.android.packageinstaller", "com.miui.packageinstaller"}) {
            if (systemPackage(context, name)) allowed.add(name);
        }
        for (ResolveInfo result : context.getPackageManager().queryIntentServices(new Intent("android.telecom.InCallService"), 0)) {
            if (result.serviceInfo != null && "android.permission.BIND_INCALL_SERVICE".equals(result.serviceInfo.permission)
                    && (result.serviceInfo.packageName.equals(phone) || systemPackage(context, result.serviceInfo.packageName))) {
                allowed.add(result.serviceInfo.packageName);
            }
        }
        return allowed;
    }

    /** Upgrade cleanup only: consumer mode never installs a device-owner policy. */
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
        Set<String> old = prefs.getStringSet("old_packages", java.util.Collections.emptySet());
        manager.setLockTaskPackages(admin, old.toArray(new String[0]));
        prefs.edit().putBoolean("policy_saved", false).remove("deadline_wall").remove("deadline_elapsed").remove("deadline_boot").commit();
    }
}
