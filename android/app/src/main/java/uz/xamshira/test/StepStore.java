package uz.xamshira.test;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Qadamlar hisobini telefonning o'zida saqlaydi (hech qayerga yuborilmaydi).
 * Android'dagi TYPE_STEP_COUNTER sensori telefon qayta yoqilgandan beri jami qadamlarni beradi;
 * kunlik qadam = joriy qiymat - kun boshidagi qiymat (base).
 * Yarim tunda StepAlarmReceiver kunni yopadi; ilova ochilganda ham hisob yangilanadi.
 */
public class StepStore {
    static final String PREFS = "step_store";

    static String dayKey(Date d) {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(d);
    }

    static String today() { return dayKey(new Date()); }

    /** Sensor qiymati bilan hisobni yangilaydi va bugungi qadamlarni qaytaradi. */
    static synchronized long update(Context ctx, float value, boolean midnightClose) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = p.edit();
        String today = today();
        String day = p.getString("day", null);
        float base = p.getFloat("base", value);
        float last = p.getFloat("last", value);

        // Telefon qayta yoqilgan bo'lsa, sensor 0 dan boshlanadi: oldingi qadamlarni saqlab qolish uchun
        // base'ni siljitamiz va "last" ni yangi shkalada 0 deb olamiz.
        if (value < last) {
            base = base - last;
            last = 0f;
        }

        if (day == null) {
            day = today;
            base = value;
        } else if (!day.equals(today)) {
            // Oldingi kun yopiladi. Yarim tundagi signalda — joriy qiymat bo'yicha,
            // ilova keyinroq ochilganda esa — oxirgi o'qilgan qiymat bo'yicha.
            float closeAt = midnightClose ? value : last;
            long prev = Math.max(0, Math.round(closeAt - base));
            long stored = p.getLong("h_" + day, 0);
            e.putLong("h_" + day, Math.max(prev, stored));
            base = closeAt;
            day = today;
        }

        long steps = Math.max(0, Math.round(value - base));
        e.putString("day", day);
        e.putFloat("base", base);
        e.putFloat("last", value);
        e.putLong("h_" + today, steps);
        e.apply();
        return steps;
    }

    static long todaySteps(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!today().equals(p.getString("day", ""))) return 0;
        return p.getLong("h_" + today(), 0);
    }

    static long stepsFor(Context ctx, String day) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("h_" + day, -1);
    }

    /** Har kecha yarim tundan keyin kunni yopish uchun signal qo'yadi. */
    static void scheduleMidnight(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(ctx, StepAlarmReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 7301, i, flags);
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 30);
        c.set(Calendar.MILLISECOND, 0);
        if (Build.VERSION.SDK_INT >= 23) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
        }
    }

    static final String ACTION_REPORT = "uz.xamshira.test.STEP_REPORT";

    /** Kunlik qadamlar xabari sozlamalari (matnlar ilova tilida JS tomonidan beriladi). */
    static void saveReport(Context ctx, boolean enabled, int hour, int minute, String title, String body, long goal) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("r_on", enabled).putInt("r_h", hour).putInt("r_m", minute)
            .putString("r_title", title).putString("r_body", body).putLong("r_goal", goal).apply();
        if (enabled) scheduleReport(ctx); else cancelReport(ctx);
    }

    private static PendingIntent reportIntent(Context ctx) {
        Intent i = new Intent(ctx, StepAlarmReceiver.class);
        i.setAction(ACTION_REPORT);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(ctx, 7302, i, flags);
    }

    static void scheduleReport(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("r_on", false)) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, p.getInt("r_h", 21));
        c.set(Calendar.MINUTE, p.getInt("r_m", 0));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis() + 1000) c.add(Calendar.DAY_OF_YEAR, 1);
        if (Build.VERSION.SDK_INT >= 23) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), reportIntent(ctx));
        } else {
            am.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), reportIntent(ctx));
        }
    }

    static void cancelReport(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(reportIntent(ctx));
    }

    /** Bugungi qadamlar haqida bildirishnoma ko'rsatadi. */
    static void postReport(Context ctx, long steps) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String title = p.getString("r_title", "Tibbiy Yordamchi");
        String body = p.getString("r_body", "{steps}");
        long goal = p.getLong("r_goal", 8000);
        String text = body.replace("{steps}", String.valueOf(steps)).replace("{goal}", String.valueOf(goal));
        android.app.NotificationManager nm = (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel ch = new android.app.NotificationChannel("steps_daily", "Qadamlar", android.app.NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }
        Intent open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content = open != null ? PendingIntent.getActivity(ctx, 7303, open, flags) : null;
        androidx.core.app.NotificationCompat.Builder b = new androidx.core.app.NotificationCompat.Builder(ctx, "steps_daily")
            .setSmallIcon(ctx.getApplicationInfo().icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true);
        if (content != null) b.setContentIntent(content);
        try { nm.notify(7304, b.build()); } catch (SecurityException ignored) {}
    }
}
