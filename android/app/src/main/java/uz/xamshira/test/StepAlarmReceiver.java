package uz.xamshira.test;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

/** Yarim tunda (va telefon yoqilganda) qadamlar kunini yopadi va keyingi signalni qo'yadi. */
public class StepAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        final Context ctx = context.getApplicationContext();
        final String action = intent != null ? intent.getAction() : null;
        final boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(action);
        StepStore.scheduleMidnight(ctx);
        final SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        final Sensor sensor = sm != null ? sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;
        if (sensor == null || !StepCounterPlugin.hasPermission(ctx)) return;

        final PendingResult pending = goAsync();
        final Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] done = {false};
        final SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (done[0]) return;
                done[0] = true;
                try { sm.unregisterListener(this); } catch (Exception ignored) {}
                try { StepStore.update(ctx, event.values[0], !boot); } catch (Exception ignored) {}
                pending.finish();
            }
            @Override
            public void onAccuracyChanged(Sensor s, int accuracy) {}
        };
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (done[0]) return;
                done[0] = true;
                try { sm.unregisterListener(listener); } catch (Exception ignored) {}
                pending.finish();
            }
        }, 8000);
    }
}
