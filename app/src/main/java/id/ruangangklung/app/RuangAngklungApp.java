package id.ruangangklung.app;

import android.app.Application;

/** Load the angklung samples during the splash, before anyone starts playing. */
public final class RuangAngklungApp extends Application {
    private SoundEngine sounds;

    @Override public void onCreate() {
        super.onCreate();
        sounds = new SoundEngine(this);
    }

    public SoundEngine soundEngine() { return sounds; }

    @Override public void onTerminate() {
        if (sounds != null) sounds.close();
        super.onTerminate();
    }
}
