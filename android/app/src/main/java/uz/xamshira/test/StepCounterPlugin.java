package uz.xamshira.test;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.Calendar;

/**
 * Qadam hisoblagich: telefonning qadam sensori (TYPE_STEP_COUNTER) dan foydalanadi.
 * Ma'lumot faqat telefonda saqlanadi.
 */
@CapacitorPlugin(
    name = "StepCounter",
    permissions = {
        @Permission(strings = { Manifest.permission.ACTIVITY_RECOGNITION }, alias = "activity")
    }
)
public class StepCounterPlugin extends Plugin {

    static boolean hasPermission(Context ctx) {
        if (Build.VERSION.SDK_INT < 29) return true;
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    private Sensor sensor() {
        SensorManager sm = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        return sm != null ? sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject r = new JSObject();
        r.put("available", sensor() != null);
        r.put("granted", hasPermission(getContext()));
        call.resolve(r);
    }

    @PluginMethod
    public void requestAccess(PluginCall call) {
        if (Build.VERSION.SDK_INT < 29 || getPermissionState("activity") == PermissionState.GRANTED) {
            JSObject r = new JSObject();
            r.put("granted", true);
            call.resolve(r);
            return;
        }
        requestPermissionForAlias("activity", call, "accessCallback");
    }

    @PermissionCallback
    private void accessCallback(PluginCall call) {
        JSObject r = new JSObject();
        r.put("granted", hasPermission(getContext()));
        call.resolve(r);
    }

    @PluginMethod
    public void getSteps(final PluginCall call) {
        final Context ctx = getContext();
        final Sensor s = sensor();
        if (s == null || !hasPermission(ctx)) {
            call.resolve(result(ctx, StepStore.todaySteps(ctx)));
            return;
        }
        StepStore.scheduleMidnight(ctx);
        final SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        final Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] done = {false};
        final SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (done[0]) return;
                done[0] = true;
                try { sm.unregisterListener(this); } catch (Exception ignored) {}
                long steps = StepStore.update(ctx, event.values[0], false);
                call.resolve(result(ctx, steps));
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
        sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL, handler);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (done[0]) return;
                done[0] = true;
                try { sm.unregisterListener(listener); } catch (Exception ignored) {}
                call.resolve(result(ctx, StepStore.todaySteps(ctx)));
            }
        }, 4000);
    }

    /** Bugungi qadamlar va oxirgi 7 kun tarixi. */
    private JSObject result(Context ctx, long today) {
        JSObject r = new JSObject();
        r.put("today", today);
        JSArray hist = new JSArray();
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -6);
        for (int i = 0; i < 7; i++) {
            String key = StepStore.dayKey(c.getTime());
            JSObject d = new JSObject();
            d.put("day", key);
            d.put("steps", i == 6 ? today : StepStore.stepsFor(ctx, key));
            hist.put(d);
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        r.put("history", hist);
        return r;
    }
}
