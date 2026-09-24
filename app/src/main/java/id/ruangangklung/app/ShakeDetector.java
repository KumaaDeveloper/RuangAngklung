package id.ruangangklung.app;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/** Gyroscope and acceleration cooperate so fast rotations and straight shakes both register. */
public final class ShakeDetector implements SensorEventListener {
    private final SensorManager manager;
    private final Sensor gyroscope;
    private final Sensor motion;
    private final Runnable onShake;
    private final ShakeLogic gyroLogic;
    private final ShakeLogic motionLogic;
    private final float[] gravity = new float[3];
    private boolean gravityReady;
    private boolean registrationFailed;
    private long lastFiredNs;
    private int sensitivity;

    public ShakeDetector(Context context, Runnable onShake, int sensitivity) {
        manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor acceleration = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if (acceleration == null) acceleration = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        motion = acceleration;
        this.onShake = onShake;
        this.sensitivity = Math.max(0, Math.min(100, sensitivity));
        gyroLogic = new ShakeLogic(MotionTuning.gyroThreshold(this.sensitivity));
        motionLogic = new ShakeLogic(MotionTuning.accelerationThreshold(this.sensitivity));
    }

    public void setSensitivity(int value) {
        sensitivity = Math.max(0, Math.min(100, value));
        gyroLogic.setThreshold(MotionTuning.gyroThreshold(sensitivity));
        motionLogic.setThreshold(MotionTuning.accelerationThreshold(sensitivity));
        gyroLogic.reset();
        motionLogic.reset();
        lastFiredNs = 0L;
    }
    public boolean available() { return (gyroscope != null || motion != null) && !registrationFailed; }
    public boolean usingGyroscope() { return gyroscope != null; }
    public boolean enabled() { return MotionTuning.enabled(sensitivity); }

    public void start() {
        gyroLogic.reset();
        motionLogic.reset();
        lastFiredNs = 0L;
        gravityReady = false;
        registrationFailed = false;
        if (!enabled()) return;
        // 100 Hz is responsive without requesting a >200 Hz sensor stream, which
        // requires an extra Android permission on recent phones. Some vendor
        // sensors may still refuse registration; keep touch playback available.
        boolean gyroReady = registerSafely(gyroscope);
        boolean motionReady = registerSafely(motion);
        registrationFailed = (gyroscope != null || motion != null) && !gyroReady && !motionReady;
    }

    private boolean registerSafely(Sensor sensor) {
        if (sensor == null) return false;
        try {
            return manager.registerListener(this, sensor, 10_000);
        } catch (RuntimeException ignored) {
            // Sensor access differs across devices; the angklung is still tappable.
            return false;
        }
    }

    public void stop() {
        manager.unregisterListener(this);
        gyroLogic.reset();
        motionLogic.reset();
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (!enabled() || (event.sensor != gyroscope && event.sensor != motion)) return;
        float x = event.values[0], y = event.values[1], z = event.values[2];
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (!gravityReady) {
                gravity[0] = x; gravity[1] = y; gravity[2] = z;
                gravityReady = true;
                return;
            }
            gravity[0] = gravity[0] * .88f + x * .12f;
            gravity[1] = gravity[1] * .88f + y * .12f;
            gravity[2] = gravity[2] * .88f + z * .12f;
            x -= gravity[0]; y -= gravity[1]; z -= gravity[2];
        }
        ShakeLogic detector = event.sensor == gyroscope ? gyroLogic : motionLogic;
        boolean canTrigger = event.timestamp - lastFiredNs >= MotionTuning.minimumGapNs(sensitivity);
        if (detector.accept(x, y, z, event.timestamp, canTrigger)) {
            lastFiredNs = event.timestamp;
            onShake.run();
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
