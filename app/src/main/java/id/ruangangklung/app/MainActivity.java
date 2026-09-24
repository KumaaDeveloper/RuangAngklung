package id.ruangangklung.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A fully native Java activity; no sign-in, external service, or UI dependency. */
public final class MainActivity extends Activity {
    private enum Screen { HOME, ABOUT, PICK_SOLO, LOBBY, CONNECTING, SOLO, ROOM }

    private static final String[] LEARNING_NOTES = {"C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5"};
    private final Handler homeHandler = new Handler(Looper.getMainLooper());
    private final Runnable noteTicker = () -> updateHomeNote(true);
    private AngklungView homeInstrument;
    private TextView homeCaption;
    private String homeCurrentNote = "C4";

    private FrameLayout root;
    private SharedPreferences preferences;
    private SoundEngine sounds;
    private ShakeDetector shake;
    private Screen screen = Screen.HOME;
    private boolean resumed;
    private boolean settingsOpen;
    private final List<String> soloNotes = new ArrayList<>();
    private final List<String> groupNotes = new ArrayList<>();
    private List<LanRoom.Member> members = new ArrayList<>();
    private volatile LanRoom room;
    private volatile int roomGeneration;
    private String activeNote = "C4";
    private AngklungView instrument;
    private TextView feedback;
    private LinearLayout activeRow;
    private LinearLayout membersColumn;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.CREAM);
        getWindow().setNavigationBarColor(Ui.CREAM);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.CREAM);
        setContentView(root);
        preferences = getSharedPreferences("ruang_angklung", MODE_PRIVATE);
        soloNotes.addAll(restore("solo"));
        groupNotes.addAll(restore("group"));
        sounds = ((RuangAngklungApp) getApplication()).soundEngine();
        sounds.setVolume(preferences.getInt("volume", 80));
        shake = new ShakeDetector(this, this::playActiveNote, preferences.getInt("sensitivity", 55));
        showHome();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        registerMotion();
        refreshMotionCue();
        if (screen == Screen.HOME) updateHomeNote(false);
    }

    @Override protected void onPause() {
        resumed = false;
        homeHandler.removeCallbacks(noteTicker);
        shake.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        leaveRoom();
        super.onDestroy();
    }

    private void registerMotion() {
        shake.stop();
        if (resumed && !settingsOpen && (screen == Screen.SOLO
                || (screen == Screen.ROOM && room != null && !room.isHost()))) shake.start();
    }

    private void install(Screen next, View page) {
        homeHandler.removeCallbacks(noteTicker);
        homeInstrument = null;
        homeCaption = null;
        screen = next;
        shake.stop();
        instrument = null;
        feedback = null;
        activeRow = null;
        membersColumn = null;
        root.removeAllViews();
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        registerMotion();
    }

    private List<String> restore(String key) {
        String raw = preferences.getString(key, "C4");
        List<String> saved = new ArrayList<>(Arrays.asList(raw.split(",")));
        return NoteCatalog.validSelection(saved, key.equals("solo") ? 10 : 3)
                ? NoteCatalog.order(saved) : NoteCatalog.defaults();
    }

    private void save(String key, List<String> notes) {
        if (NoteCatalog.validSelection(notes, key.equals("solo") ? 10 : 3))
            preferences.edit().putString(key, android.text.TextUtils.join(",", notes)).apply();
    }

    private ScrollView scroll(LinearLayout content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        return scroll;
    }

    private void pad(LinearLayout view, int all) {
        view.setPadding(dp(all), dp(all), dp(all), dp(all));
    }

    private int dp(int size) { return Ui.dp(this, size); }

    private TextView title(String copy, int size) {
        TextView text = Ui.text(this, copy, size, Ui.INK, true);
        text.setTypeface(Typeface.create("sans-serif-rounded", Typeface.BOLD));
        text.setLineSpacing(dp(2), 1f);
        return text;
    }

    private TextView body(String copy) {
        TextView text = Ui.text(this, copy, 14, Ui.MUTED, false);
        text.setLineSpacing(dp(3), 1f);
        return text;
    }

    private TextView tag(String copy) {
        TextView tag = Ui.text(this, copy, 11, Ui.GREEN, true);
        tag.setLetterSpacing(.12f);
        return tag;
    }

    private LinearLayout header(String heading, String subtitle, Runnable onBack) {
        LinearLayout view = Ui.column(this);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "‹", 34, Ui.INK, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Kembali");
        back.setBackground(Ui.ripple(this, Ui.rounded(this, Ui.PAPER, 16)));
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        back.setOnClickListener(v -> onBack.run());
        TextView brand = Ui.text(this, "RUANG ANGKLUNG", 11, Ui.GREEN, true);
        brand.setLetterSpacing(.15f);
        top.addView(brand, Ui.margins(this, 0, dp(48), 10, 0, 0, 0));
        ((LinearLayout.LayoutParams) brand.getLayoutParams()).weight = 1f;
        TextView settings = Ui.text(this, "⚙", 23, Ui.INK, false);
        settings.setGravity(Gravity.CENTER);
        settings.setContentDescription("Atur gerakan dan volume");
        settings.setBackground(Ui.ripple(this, Ui.rounded(this, Ui.PAPER, 16)));
        top.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        settings.setOnClickListener(v -> showSettings());
        view.addView(top);
        view.addView(new BambooMotifView(this), new LinearLayout.LayoutParams(-1, dp(20)));
        view.addView(Ui.gap(this, 9));
        view.addView(title(heading, 28));
        view.addView(Ui.gap(this, 6));
        view.addView(body(subtitle));
        return view;
    }

    private void showHome() {
        LinearLayout content = Ui.column(this);
        content.setPadding(dp(18), dp(18), dp(18), dp(32));
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = tag("♪  RUANG ANGKLUNG");
        navigation.addView(brand, new LinearLayout.LayoutParams(0, dp(46), 1f));
        TextView settingsButton = Ui.text(this, "⚙", 23, Ui.INK, false);
        settingsButton.setGravity(Gravity.CENTER);
        settingsButton.setContentDescription("Pengaturan volume dan gerakan");
        settingsButton.setOnClickListener(v -> showSettings());
        navigation.addView(settingsButton, new LinearLayout.LayoutParams(dp(46), dp(46)));
        content.addView(navigation);
        content.addView(Ui.gap(this, 12));
        LinearLayout hero = Ui.column(this);
        hero.setPadding(dp(22), dp(21), dp(22), dp(20));
        hero.setBackground(Ui.rounded(this, Ui.PEACH, 30));
        TextView eyebrow = Ui.text(this, "✦  PETUALANGAN MUSIK NUSANTARA", 11, Ui.GREEN, true);
        eyebrow.setLetterSpacing(.16f);
        hero.addView(eyebrow);
        hero.addView(Ui.gap(this, 12));
        TextView head = title("Jelajahi suara\nangklung Nusantara.", 27);
        head.setTextColor(Ui.FOREST);
        hero.addView(head);
        hero.addView(Ui.gap(this, 7));
        TextView intro = Ui.text(this, "Ketuk gambar angklung untuk mendengar nada yang tampil.", 15, Ui.INK, false);
        intro.setLineSpacing(dp(3), 1f);
        hero.addView(intro);
        hero.addView(new BambooMotifView(this), Ui.margins(this, -1, dp(24), 0, 14, 0, 8));
        AngklungView art = new AngklungView(this);
        homeCurrentNote = learningNote();
        art.setNote(homeCurrentNote);
        art.setShowNoteLabel(false);
        art.setContentDescription("Ketuk angklung untuk mendengarkan " + NoteCatalog.label(homeCurrentNote));
        art.setClickable(true);
        Ui.touchMotion(art);
        art.setOnClickListener(v -> playHomeNote());
        hero.addView(art, new LinearLayout.LayoutParams(-1, dp(165)));
        TextView noteCaption = Ui.text(this, "NADA  " + NoteCatalog.label(homeCurrentNote), 16, Ui.FOREST, true);
        noteCaption.setGravity(Gravity.CENTER);
        noteCaption.setPadding(dp(10), dp(12), dp(10), dp(12));
        noteCaption.setBackground(Ui.rounded(this, Ui.PAPER, 17));
        noteCaption.setContentDescription("Nada beranda: " + NoteCatalog.label(homeCurrentNote));
        hero.addView(noteCaption, Ui.margins(this, -1, -2, 0, 12, 0, 0));
        content.addView(hero);

        TextView section = title("Mau main yang mana?", 22);
        content.addView(section, Ui.margins(this, -1, -2, 4, 22, 0, 12));
        View solo = modeCard("♪", "Main sendiri", "Pilih 1–10 nada dan susun melodi pilihanmu.", "MULAI BELAJAR", this::showPicker, Ui.MINT);
        content.addView(solo);
        content.addView(Ui.gap(this, 11));
        View together = modeCard("♫", "Main bersama", "Bermain dengan teman; satu ponsel menjadi speaker.",
                "MAIN BARENG TEMAN", this::showLobby, Ui.LIGHT_GOLD);
        content.addView(together);
        content.addView(Ui.gap(this, 24));
        LinearLayout tip = Ui.column(this);
        pad(tip, 18);
        tip.setBackground(Ui.rounded(this, Ui.LIGHT_GOLD, 20));
        tip.addView(tag("COBA MAIN ANGKLUNG  ✨"));
        tip.addView(Ui.gap(this, 7));
        TextView how = Ui.text(this, "Di beranda, nada berganti setiap menit. Ketuk gambar angklung untuk mendengarnya. Di layar bermain, kamu juga bisa menggoyangkan ponsel.", 14, Ui.INK, false);
        how.setLineSpacing(dp(3), 1f);
        tip.addView(how);
        content.addView(tip);
        TextView aboutButton = Ui.button(this, "Tentang Kami  →", false);
        aboutButton.setContentDescription("Buka halaman Tentang Kami");
        aboutButton.setOnClickListener(v -> showAbout());
        content.addView(aboutButton, Ui.margins(this, -1, -2, 0, 20, 0, 0));
        install(Screen.HOME, scroll(content));
        homeInstrument = art;
        homeCaption = noteCaption;
        scheduleHomeNote();
        animateIn(hero);
        solo.setAlpha(0f);
        solo.setTranslationY(dp(14));
        solo.animate().alpha(1f).translationY(0).setStartDelay(160).setDuration(440).start();
        together.setAlpha(0f);
        together.setTranslationY(dp(14));
        together.animate().alpha(1f).translationY(0).setStartDelay(280).setDuration(440).start();
    }

    private String learningNote() {
        return LEARNING_NOTES[(int) ((System.currentTimeMillis() / 60_000L) % LEARNING_NOTES.length)];
    }

    private void scheduleHomeNote() {
        homeHandler.removeCallbacks(noteTicker);
        if (resumed && screen == Screen.HOME && homeInstrument != null) {
            long untilNextMinute = 60_000L - System.currentTimeMillis() % 60_000L + 20L;
            homeHandler.postDelayed(noteTicker, untilNextMinute);
        }
    }

    private void updateHomeNote(boolean animate) {
        if (screen != Screen.HOME || homeInstrument == null) return;
        String next = learningNote();
        if (!next.equals(homeCurrentNote)) {
            homeCurrentNote = next;
            homeInstrument.setNote(next);
            homeInstrument.setContentDescription("Ketuk angklung untuk mendengarkan " + NoteCatalog.label(next));
            if (homeCaption != null) {
                TextView target = homeCaption;
                String label = "NADA  " + NoteCatalog.label(next);
                target.animate().cancel();
                if (animate) {
                    target.animate().alpha(0f).translationY(-dp(5)).setDuration(150)
                            .withEndAction(() -> {
                                if (homeCaption != target || screen != Screen.HOME
                                        || !homeCurrentNote.equals(next)) return;
                                target.setText(label);
                                target.setContentDescription("Nada beranda: " + NoteCatalog.label(next));
                                target.setTranslationY(dp(5));
                                target.animate().alpha(1f).translationY(0f).setDuration(230).start();
                            }).start();
                } else {
                    target.setAlpha(1f);
                    target.setTranslationY(0f);
                    target.setText(label);
                    target.setContentDescription("Nada beranda: " + NoteCatalog.label(next));
                }
            }
            if (animate) homeInstrument.ring();
        }
        scheduleHomeNote();
    }

    private void playHomeNote() {
        if (screen != Screen.HOME || homeInstrument == null) return;
        if (sounds.play(homeCurrentNote)) homeInstrument.ring();
    }

    private void showAbout() {
        LinearLayout page = Ui.column(this);
        page.addView(header("Tentang Kami", "Musik bambu yang bisa dijelajahi bersama.", this::showHome));
        LinearLayout content = Ui.column(this);
        content.setPadding(dp(20), dp(6), dp(20), dp(32));

        LinearLayout mission = Ui.column(this);
        pad(mission, 18);
        mission.setBackground(Ui.rounded(this, Ui.MINT, 22));
        mission.addView(tag("MENGENAL RUANG ANGKLUNG"));
        mission.addView(title("Angklung dalam genggaman", 21), Ui.margins(this, -1, -2, 0, 9, 0, 7));
        mission.addView(body("Ruang Angklung adalah aplikasi untuk mengenal nada dan memainkan suara angklung. Pilih Do–Re–Mi, goyangkan ponsel atau ketuk gambar angklung, lalu dengarkan hasilnya."));
        content.addView(mission);

        LinearLayout features = Ui.column(this);
        pad(features, 17);
        features.setBackground(Ui.rounded(this, Ui.LIGHT_GOLD, 20));
        features.addView(tag("CARA BERMAIN"));
        features.addView(body("Main sendiri: susun hingga 10 nada. Main bersama: 1–3 nada per pemain, dengan satu ponsel sebagai speaker di Wi-Fi yang sama."),
                Ui.margins(this, -1, -2, 0, 8, 0, 0));
        content.addView(features, Ui.margins(this, -1, -2, 0, 14, 0, 0));

        content.addView(title("Tentang saya", 21), Ui.margins(this, -1, -2, 0, 26, 0, 10));
        LinearLayout profile = Ui.column(this);
        pad(profile, 17);
        profile.setBackground(Ui.bordered(this, Ui.PAPER, Ui.BORDER, 22));
        LinearLayout identity = new LinearLayout(this);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        ImageView photo = new ImageView(this);
        photo.setImageResource(R.drawable.developer_profile);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setBackground(Ui.rounded(this, Ui.LIGHT_GOLD, 18));
        photo.setClipToOutline(true);
        photo.setContentDescription("Foto Ardra Priya Abyudaya");
        identity.addView(photo, new LinearLayout.LayoutParams(dp(82), dp(82)));
        LinearLayout identityText = Ui.column(this);
        identityText.addView(tag("PENGEMBANG APLIKASI"));
        identityText.addView(title("Ardra Priya\nAbyudaya", 19), Ui.margins(this, -1, -2, 0, 6, 0, 0));
        LinearLayout.LayoutParams nameSpace = Ui.margins(this, 0, -2, 12, 0, 0, 0);
        nameSpace.weight = 1f;
        identity.addView(identityText, nameSpace);
        profile.addView(identity);
        profile.addView(body("Saya mengembangkan Ruang Angklung agar lebih banyak orang dapat mengenal bunyi angklung dan menikmati musiknya bersama melalui ponsel."),
                Ui.margins(this, -1, -2, 0, 12, 0, 0));
        profile.addView(tag("TEMUKAN SAYA DI"), Ui.margins(this, -1, -2, 0, 20, 0, 0));
        addProfileLink(profile, "YouTube  ·  @FunkaRevon  ↗", "https://www.youtube.com/@FunkaRevon", 10);
        addProfileLink(profile, "TikTok  ·  @funkarevon  ↗", "https://www.tiktok.com/@funkarevon", 8);
        addProfileLink(profile, "GitHub  ·  KumaaDeveloper  ↗", "https://github.com/KumaaDeveloper", 8);
        content.addView(profile);
        page.addView(scroll(content), new LinearLayout.LayoutParams(-1, 0, 1f));
        install(Screen.ABOUT, page);
        animateIn(mission);
        animateIn(profile);
    }

    private void addProfileLink(LinearLayout profile, String label, String address, int topMargin) {
        TextView link = Ui.button(this, label, false);
        link.setContentDescription("Buka " + label.replace("  ↗", "") + " di browser");
        link.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(address)));
            } catch (ActivityNotFoundException exception) {
                toast("Tidak ada aplikasi untuk membuka tautan ini.");
            }
        });
        profile.addView(link, Ui.margins(this, -1, -2, 0, topMargin, 0, 0));
    }

    private View modeCard(String symbol, String heading, String description, String footer, Runnable click, int color) {
        LinearLayout card = Ui.column(this);
        pad(card, 18);
        card.setBackground(Ui.ripple(this, Ui.rounded(this, color, 24)));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(heading + ". " + description);
        Ui.touchMotion(card);
        card.setOnClickListener(v -> click.run());
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView numberView = Ui.text(this, symbol, 30, Ui.GREEN, true);
        numberView.setGravity(Gravity.CENTER);
        numberView.setBackground(Ui.rounded(this, Ui.PAPER, 17));
        top.addView(numberView, new LinearLayout.LayoutParams(dp(50), dp(50)));
        TextView arrow = Ui.text(this, "↗", 26, Ui.GREEN, false);
        arrow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(arrow, new LinearLayout.LayoutParams(0, dp(50), 1f));
        card.addView(top);
        card.addView(Ui.gap(this, 8));
        card.addView(title(heading, 21));
        card.addView(Ui.gap(this, 3));
        card.addView(body(description));
        card.addView(Ui.gap(this, 9));
        card.addView(tag(footer.toUpperCase(java.util.Locale.ROOT)));
        return card;
    }

    private void showPicker() {
        List<String> selected = soloNotes;
        int max = 10;
        LinearLayout page = Ui.column(this);
        page.addView(header("Pilih nada", "Simpan 1–10 nada berbeda untuk permainan solo.", this::showHome));
        LinearLayout inner = Ui.column(this);
        inner.setPadding(dp(20), dp(12), dp(20), dp(24));
        LinearLayout counterCard = Ui.column(this);
        pad(counterCard, 17);
        counterCard.setBackground(Ui.rounded(this, Ui.MINT, 22));
        TextView chosen = Ui.text(this, "", 20, Ui.FOREST, true);
        counterCard.addView(chosen);
        counterCard.addView(Ui.gap(this, 4));
        TextView chosenDetail = Ui.text(this, "", 13, Ui.INK, false);
        counterCard.addView(chosenDetail);
        inner.addView(counterCard);
        inner.addView(title("Kenali dan pilih nada", 20), Ui.margins(this, -1, -2, 0, 19, 0, 5));
        inner.addView(body("Ketuk kotak untuk memilih nada. Dengarkan lewat gambar angklung di layar bermain. Do = C; ↓ rendah, ↑ tinggi."));
        inner.addView(Ui.gap(this, 13));
        LinearLayout grid = Ui.column(this);
        inner.addView(grid);
        List<TextView> chips = new ArrayList<>();
        TextView next = Ui.button(this, "Mulai main sendiri  →", true);
        Runnable refresh = () -> {
            chosen.setText(selected.size() + " / " + max + " nada dipilih");
            chosenDetail.setText(selected.isEmpty() ? "Pilih minimal satu nada untuk melanjutkan."
                    : NoteCatalog.describeList(selected));
            next.setEnabled(!selected.isEmpty());
            next.setAlpha(selected.isEmpty() ? .45f : 1f);
            for (int i = 0; i < chips.size(); i++) {
                boolean picked = selected.contains(NoteCatalog.NOTES[i]);
                TextView chip = chips.get(i);
                chip.setTextColor(picked ? Color.WHITE : Ui.INK);
                chip.setBackground(Ui.ripple(this, Ui.bordered(this, picked ? Ui.GREEN : Ui.PAPER,
                        picked ? Ui.GREEN : Ui.BORDER, 15)));
                chip.setContentDescription(NoteCatalog.label(NoteCatalog.NOTES[i]) + (picked ? ", dipilih" : ", belum dipilih"));
            }
        };
        for (int start = 0; start < NoteCatalog.NOTES.length; start += 3) {
            LinearLayout row = new LinearLayout(this);
            for (int j = 0; j < 3 && start + j < NoteCatalog.NOTES.length; j++) {
                String note = NoteCatalog.NOTES[start + j];
                TextView chip = Ui.text(this, NoteCatalog.chipLabel(note), 15, Ui.INK, true);
                chip.setGravity(Gravity.CENTER);
                chip.setMinHeight(dp(82));
                chip.setClickable(true);
                chip.setFocusable(true);
                Ui.touchMotion(chip);
                chip.setOnClickListener(v -> {
                    if (selected.contains(note)) selected.remove(note);
                    else if (selected.size() < max) selected.add(note);
                    else { toast("Batas " + max + " nada sudah tercapai."); return; }
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    save("solo", selected);
                    refresh.run();
                });
                row.addView(chip, Ui.margins(this, 0, -2, 0, 0, 6, 0));
                ((LinearLayout.LayoutParams) chip.getLayoutParams()).weight = 1f;
                chips.add(chip);
            }
            grid.addView(row, Ui.margins(this, -1, -2, 0, 0, 0, 7));
        }
        refresh.run();
        page.addView(scroll(inner), new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout bottom = Ui.column(this);
        bottom.setPadding(dp(20), dp(13), dp(20), dp(20));
        bottom.setBackground(Ui.rounded(this, Ui.LIGHT_GOLD, 20));
        next.setOnClickListener(v -> {
            if (selected.isEmpty()) return;
            save("solo", selected);
            activeNote = selected.contains(activeNote) ? activeNote : selected.get(0);
            showSolo();
        });
        bottom.addView(next);
        page.addView(bottom);
        install(Screen.PICK_SOLO, page);
    }

    private EditText edit(String placeholder, String value) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setTextSize(16);
        field.setTextColor(Ui.INK);
        field.setHintTextColor(Ui.MUTED);
        field.setHint(placeholder);
        field.setText(value);
        field.setPadding(dp(17), 0, dp(17), 0);
        field.setBackground(Ui.bordered(this, Color.WHITE, Ui.BORDER, 16));
        return field;
    }

    private void showLobby() {
        LinearLayout page = Ui.column(this);
        page.addView(header("Main bersama", "Main dengan teman lewat Wi-Fi yang sama.",
                this::showHome));
        LinearLayout inner = Ui.column(this);
        inner.setPadding(dp(20), dp(4), dp(20), dp(27));
        LinearLayout tip = Ui.column(this);
        pad(tip, 15);
        tip.setBackground(Ui.rounded(this, Ui.LIGHT_GOLD, 19));
        tip.addView(tag("SEBELUM BERMAIN"));
        tip.addView(body("Sambungkan semua ponsel ke Wi-Fi yang sama. Satu ponsel akan menjadi speaker, yang lain menjadi pemain."),
                Ui.margins(this, -1, -2, 0, 6, 0, 0));
        inner.addView(tip);
        inner.addView(title("Nama perangkat", 18), Ui.margins(this, -1, -2, 0, 21, 0, 9));
        EditText name = edit("Nama speaker atau pemain", preferences.getString("name", ""));
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});
        inner.addView(name, new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout speakerCard = Ui.column(this);
        pad(speakerCard, 17);
        speakerCard.setBackground(Ui.rounded(this, Ui.MINT, 22));
        speakerCard.addView(tag("01 · PONSEL SPEAKER"));
        speakerCard.addView(title("Buat ruang", 21), Ui.margins(this, -1, -2, 0, 8, 0, 5));
        speakerCard.addView(body("Ponsel ini memutar semua nada pemain. Tidak perlu memilih nada."));
        TextView create = Ui.button(this, "Buat ruang speaker  →", true);
        speakerCard.addView(create, Ui.margins(this, -1, -2, 0, 12, 0, 0));
        inner.addView(speakerCard, Ui.margins(this, -1, -2, 0, 20, 0, 0));

        LinearLayout playerCard = Ui.column(this);
        pad(playerCard, 17);
        playerCard.setBackground(Ui.rounded(this, Ui.PEACH, 22));
        playerCard.addView(tag("02 · PONSEL PEMAIN"));
        playerCard.addView(title("Gabung ke ruang", 21), Ui.margins(this, -1, -2, 0, 8, 0, 5));
        playerCard.addView(body("Pilih 1–3 nada, lalu masukkan IP dari ponsel speaker."));
        TextView selectedLabel = body("");
        selectedLabel.setTextColor(Ui.FOREST);
        selectedLabel.setText(groupSelectionSummary());
        playerCard.addView(selectedLabel, Ui.margins(this, -1, -2, 0, 11, 0, 7));
        TextView choose = Ui.button(this, "Pilih nada pemain  ♪", false);
        choose.setOnClickListener(v -> chooseGroupNotes(() -> selectedLabel.setText(groupSelectionSummary())));
        playerCard.addView(choose);
        EditText ip = edit("Contoh: 192.168.1.12", preferences.getString("last_ip", ""));
        ip.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        playerCard.addView(ip, Ui.margins(this, -1, dp(56), 0, 12, 0, 0));
        TextView join = Ui.button(this, "Gabung sebagai pemain  →", true);
        playerCard.addView(join, Ui.margins(this, -1, -2, 0, 12, 0, 0));
        inner.addView(playerCard, Ui.margins(this, -1, -2, 0, 13, 0, 0));
        page.addView(scroll(inner), new LinearLayout.LayoutParams(-1, 0, 1f));
        install(Screen.LOBBY, page);
        create.setOnClickListener(v -> {
            hideKeyboard();
            String nickname = name.getText().toString().trim();
            confirmRoom(true, "", nickname.isEmpty() ? "Speaker" : nickname);
        });
        join.setOnClickListener(v -> {
            hideKeyboard();
            String nickname = name.getText().toString().trim();
            String address = ip.getText().toString().trim();
            if (!LanRoom.validIpv4(address)) { ip.setError("Alamat IP tidak valid"); return; }
            if (!NoteCatalog.validSelection(groupNotes, 3)) {
                chooseGroupNotes(() -> selectedLabel.setText(groupSelectionSummary()));
                return;
            }
            confirmRoom(false, address, nickname.isEmpty() ? "Pemain" : nickname);
        });
    }

    private String groupSelectionSummary() {
        return groupNotes.isEmpty() ? "Belum ada nada pemain" : "Nada: " + NoteCatalog.describeList(groupNotes);
    }

    private void confirmRoom(boolean hosting, String address, String nickname) {
        String summary = hosting
                ? "Nama: " + nickname + "\nPonsel ini akan menjadi speaker. Pemain dapat bergabung lewat Wi-Fi yang sama."
                : "Nama: " + nickname + "\nIP speaker: " + address + "\n" + groupSelectionSummary();
        new AlertDialog.Builder(this)
                .setTitle(hosting ? "Buat ruang speaker?" : "Gabung sebagai pemain?")
                .setMessage(summary)
                .setNegativeButton("Batal", null)
                .setPositiveButton(hosting ? "Buat ruang" : "Gabung", (dialog, which) -> {
                    SharedPreferences.Editor edit = preferences.edit().putString("name", nickname);
                    if (!hosting) edit.putString("last_ip", address);
                    edit.apply();
                    startRoom(hosting, address, nickname);
                }).show();
    }

    private void hideKeyboard() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(), 0);
        root.clearFocus();
    }

    private void startRoom(boolean hosting, String address, String name) {
        leaveRoom();
        int generation = ++roomGeneration;
        members = new ArrayList<>();
        LanRoom.Listener callback = new LanRoom.Listener() {
            @Override public void onReady() {
                runOnUiThread(() -> {
                    if (generation != roomGeneration || room == null) return;
                    if (screen == Screen.ROOM && !room.isHost()) {
                        if (feedback != null) feedback.setText("Terhubung kembali. Siap bermain  ✦");
                        refreshMotionCue();
                    } else showRoom();
                });
            }
            @Override public void onStatus(String status) {
                runOnUiThread(() -> {
                    if (generation != roomGeneration) return;
                    TextView connecting = (TextView) findTag(root, "connecting_status");
                    if (connecting != null) connecting.setText(status);
                    else if (screen == Screen.ROOM && feedback != null) feedback.setText(status);
                });
            }
            @Override public void onSnapshot(List<LanRoom.Member> people) {
                runOnUiThread(() -> {
                    if (generation != roomGeneration) return;
                    members = people;
                    refreshMembers();
                });
            }
            @Override public void onRemotePlay(String id, String note) {
                if (generation != roomGeneration || room == null || !room.isHost()) return;
                // SoundPool can play immediately on this network worker; drawing waits for the UI thread.
                sounds.play(note);
                runOnUiThread(() -> {
                    if (generation != roomGeneration || screen != Screen.ROOM || room == null || !room.isHost()) return;
                    String who = "Teman";
                    for (LanRoom.Member member : members) if (member.id.equals(id)) { who = member.name; break; }
                    if (instrument != null) { instrument.setNote(note); instrument.ring(); }
                    if (feedback != null) feedback.setText(who + " membunyikan " + NoteCatalog.label(note) + "  ✦");
                });
            }
            @Override public void onClosed(String reason) {
                runOnUiThread(() -> {
                    if (generation != roomGeneration) return;
                    boolean hostEndedRoom = reason.contains("menutup ruang");
                    leaveRoom();
                    showLobby();
                    AlertDialog.Builder notice = new AlertDialog.Builder(MainActivity.this)
                            .setTitle(hostEndedRoom ? "Ruang telah ditutup" : "Koneksi ruang terputus")
                            .setMessage(reason + (hostEndedRoom
                                    ? "\n\nMinta alamat IP ruang baru kepada penyedia ruang."
                                    : "\n\nKamu bisa mencoba sambung lagi atau memeriksa Wi-Fi dan IP speaker."))
                            .setNegativeButton("Kembali ke menu", null);
                    if (!hostEndedRoom) notice.setPositiveButton(hosting ? "Buat ruang lagi" : "Sambung ulang",
                            (dialog, which) -> startRoom(hosting, address, name));
                    notice.show();
                });
            }
        };
        try {
            room = hosting ? LanRoom.host(name, callback)
                    : LanRoom.join(address, name, new ArrayList<>(groupNotes), callback);
        } catch (IllegalArgumentException exception) {
            toast(exception.getMessage());
            return;
        }
        LinearLayout waiting = Ui.column(this);
        waiting.setGravity(Gravity.CENTER);
        pad(waiting, 28);
        ProgressBar progress = new ProgressBar(this);
        waiting.addView(progress, new LinearLayout.LayoutParams(dp(52), dp(52)));
        waiting.addView(title(hosting ? "Menyiapkan speaker…" : "Menghubungkan pemain…", 25), Ui.margins(this, -2, -2, 0, 24, 0, 8));
        TextView connecting = body("Pastikan setiap perangkat berada pada jaringan Wi-Fi yang sama.");
        connecting.setTag("connecting_status");
        waiting.addView(connecting);
        TextView cancel = Ui.button(this, "Batal", false);
        waiting.addView(cancel, Ui.margins(this, -1, -2, 0, 25, 0, 0));
        cancel.setOnClickListener(v -> { leaveRoom(); showLobby(); });
        install(Screen.CONNECTING, waiting);
    }

    private void showSolo() {
        if (!soloNotes.contains(activeNote)) activeNote = soloNotes.get(0);
        LinearLayout page = Ui.column(this);
        page.addView(header("Main sendiri", "Pilih nada, dengarkan suaranya, lalu ikuti iramamu!", this::showPicker));
        LinearLayout content = Ui.column(this);
        content.setPadding(dp(20), dp(8), dp(20), dp(18));
        LinearLayout notePad = addPlayArea(content, soloNotes);
        LinearLayout callout = Ui.column(this);
        pad(callout, 17);
        callout.setBackground(Ui.rounded(this, Ui.LIGHT_GOLD, 19));
        callout.addView(tag("YUK, KENALI NADANYA  ✨"));
        callout.addView(Ui.gap(this, 6));
        callout.addView(body("Coba pilih Do, Re, Mi di panel bawah. Goyangkan ponsel atau ketuk gambar angklung untuk membunyikan nada yang aktif."));
        content.addView(callout, Ui.margins(this, -1, -2, 0, 23, 0, 0));
        page.addView(scroll(content), new LinearLayout.LayoutParams(-1, 0, 1f));
        page.addView(notePad);
        install(Screen.SOLO, page);
        // install clears only references; rebind the play controls built in addPlayArea.
        bindPlayArea(content);
    }

    private void showRoom() {
        boolean hosting = room != null && room.isHost();
        if (!hosting && !groupNotes.contains(activeNote)) activeNote = groupNotes.get(0);
        LinearLayout page = Ui.column(this);
        page.addView(header(hosting ? "Speaker ruang" : "Pemain angklung",
                hosting ? "Semua nada pemain disatukan dan dibunyikan dari ponsel ini."
                        : "Gerakkan ponsel untuk mengirim nada; suara keluar dari speaker ruang.",
                () -> new AlertDialog.Builder(this).setTitle("Keluar dari ruang?")
                        .setMessage(room != null && room.isHost() ? "Ruang akan ditutup untuk semua pemain." : "Kamu akan meninggalkan permainan.")
                        .setNegativeButton("Tetap main", null)
                        .setPositiveButton("Keluar", (dialog, which) -> { leaveRoom(); showLobby(); }).show()));
        LinearLayout content = Ui.column(this);
        content.setPadding(dp(20), dp(8), dp(20), dp(28));
        LinearLayout notePad = null;
        if (hosting) {
            LinearLayout invite = Ui.column(this);
            pad(invite, 17);
            invite.setBackground(Ui.rounded(this, Ui.FOREST, 20));
            TextView inviteLabel = Ui.text(this, "ALAMAT RUANG · PORT " + LanRoom.PORT, 11, Ui.GOLD, true);
            inviteLabel.setLetterSpacing(.12f);
            invite.addView(inviteLabel);
            List<String> addresses = LanRoom.localAddresses();
            String ip = addresses.isEmpty() ? "Sambungkan ke Wi-Fi" : addresses.get(0);
            TextView ipText = Ui.text(this, ip, 24, Color.WHITE, true);
            invite.addView(ipText, Ui.margins(this, -1, -2, 0, 6, 0, 3));
            if (addresses.size() > 1) invite.addView(Ui.text(this,
                    "Alamat lain: " + android.text.TextUtils.join(" · ", addresses.subList(1, addresses.size())),
                    12, 0xFFDAE5DB, false));
            invite.addView(Ui.text(this, "Minta pemain memasukkan IP ini. Semua suara akan keluar di sini.", 13, 0xFFDAE5DB, false));
            TextView share = Ui.text(this, "Salin & bagikan  ↗", 14, Ui.GOLD, true);
            share.setPadding(0, dp(12), 0, dp(3));
            share.setClickable(true);
            share.setOnClickListener(v -> {
                if (addresses.isEmpty()) { toast("Alamat IP belum tersedia."); return; }
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("IP Ruang Angklung", ip));
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, "Gabung sebagai pemain di Ruang Angklung! Sambungkan ke Wi-Fi yang sama lalu masukkan IP speaker: " + ip);
                startActivity(Intent.createChooser(send, "Bagikan alamat ruang"));
            });
            invite.addView(share);
            content.addView(invite);
            content.addView(Ui.gap(this, 18));
        }
        if (hosting) {
            content.addView(tag("SUARA ANSAMBEL · LIVE"));
            content.addView(Ui.gap(this, 10));
            AngklungView display = new AngklungView(this);
            display.setNote("♪");
            display.setTag("instrument");
            content.addView(display, new LinearLayout.LayoutParams(-1, dp(240)));
            TextView status = Ui.text(this, "Menunggu pemain membunyikan angklung…", 14, Ui.GREEN, true);
            status.setTag("feedback");
            content.addView(status, Ui.margins(this, -1, -2, 0, 14, 0, 0));
        } else {
            notePad = addPlayArea(content, groupNotes);
            TextView change = Ui.button(this, "Ganti nada pemain  ✎", false);
            change.setOnClickListener(v -> changeGroupNotes());
            content.addView(change, Ui.margins(this, -1, -2, 0, 15, 0, 0));
            LinearLayout info = Ui.column(this);
            pad(info, 15);
            info.setBackground(Ui.rounded(this, Ui.LIGHT_GOLD, 18));
            info.addView(body("Ponsel pemain mengirim nada tanpa mengeluarkan suara. Atur volume pada ponsel speaker."));
            content.addView(info, Ui.margins(this, -1, -2, 0, 17, 0, 0));
        }
        TextView players = title("Pemain di ruang", 21);
        content.addView(players, Ui.margins(this, -1, -2, 0, 24, 0, 8));
        membersColumn = Ui.column(this);
        content.addView(membersColumn);
        page.addView(scroll(content), new LinearLayout.LayoutParams(-1, 0, 1f));
        if (notePad != null) page.addView(notePad);
        install(Screen.ROOM, page);
        if (hosting) {
            instrument = (AngklungView) findTag(content, "instrument");
            feedback = (TextView) findTag(content, "feedback");
        } else bindPlayArea(content);
        membersColumn = findMembersColumn(content);
        refreshMembers();
    }

    /* The page is created before install(), so bind tagged controls after install resets references. */
    private LinearLayout addPlayArea(LinearLayout content, List<String> selection) {
        AngklungView art = new AngklungView(this);
        art.setNote(activeNote);
        art.setTag("instrument");
        art.setClickable(true);
        art.setFocusable(true);
        Ui.touchMotion(art);
        art.setContentDescription("Ketuk untuk membunyikan angklung nada " + NoteCatalog.label(activeNote));
        content.addView(art, new LinearLayout.LayoutParams(-1, dp(205)));
        content.addView(Ui.gap(this, 10));
        TextView cue = body(motionCue(selection == groupNotes));
        cue.setTag("motion_cue");
        content.addView(cue);

        LinearLayout pad = Ui.column(this);
        pad.setPadding(dp(16), dp(10), dp(16), dp(14));
        pad.setBackground(Ui.bordered(this, Ui.PAPER, Ui.BORDER, 22));
        pad.setElevation(dp(9));
        pad.addView(tag("PILIH NADA ANGKLUNGMU"));
        TextView current = Ui.text(this, "Nada aktif: " + NoteCatalog.label(activeNote), 14, Ui.FOREST, true);
        current.setTag("active_label");
        pad.addView(current, Ui.margins(this, -1, -2, 0, 4, 0, 2));
        TextView hint = body(padCue(selection == groupNotes));
        hint.setTag("pad_cue");
        pad.addView(hint);
        LinearLayout grid = Ui.column(this);
        grid.setTag("active_row");
        pad.addView(grid, Ui.margins(this, -1, -2, 0, 8, 0, 0));
        TextView status = Ui.text(this, "Siap! Pilih nada dulu  ♪", 12, Ui.GREEN, true);
        status.setTag("feedback");
        status.setMaxLines(1);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pad.addView(status, Ui.margins(this, -1, -2, 0, 6, 0, 0));
        return pad;
    }

    private String padCue(boolean group) {
        String destination = group ? " · bunyi di speaker" : "";
        if (!shake.available()) return "Pilih nada, lalu ketuk angklung · tanpa sensor" + destination;
        return shake.enabled() ? "Pilih nada, lalu goyang atau ketuk angklung" + destination
                : "Pilih nada, lalu ketuk gambar angklung · sensor mati" + destination;
    }

    private String motionCue(boolean group) {
        String destination = group ? " Suara keluar di ponsel speaker." : "";
        if (!shake.available())
            return "Sensor gerak tidak tersedia. Pilih nada di panel bawah, lalu ketuk gambar angklung." + destination;
        if (!shake.enabled())
            return "Gerakan nonaktif pada sensitivitas 0%. Pilih nada, lalu ketuk gambar angklung." + destination;
        return (shake.usingGyroscope()
                ? "Goyangkan/putar ponsel untuk membunyikan nada. Atur sensitivitas dan tempo di pengaturan."
                : "Goyangkan ponsel untuk membunyikan nada. Atur sensitivitas dan tempo di pengaturan.")
                + destination;
    }

    private void refreshMotionCue() {
        TextView cue = (TextView) findTag(root, "motion_cue");
        if (cue != null) cue.setText(motionCue(screen == Screen.ROOM));
        TextView padHint = (TextView) findTag(root, "pad_cue");
        if (padHint != null) padHint.setText(padCue(screen == Screen.ROOM));
    }

    private void bindPlayArea(LinearLayout content) {
        instrument = (AngklungView) findTag(content, "instrument");
        feedback = (TextView) findTag(root, "feedback");
        activeRow = (LinearLayout) findTag(root, "active_row");
        if (instrument != null) instrument.setOnClickListener(v -> playActiveNote());
        renderActiveNotes();
        refreshMotionCue();
    }

    private LinearLayout findMembersColumn(ViewGroup content) {
        // The participant container is the only column after the heading.
        for (int i = content.getChildCount() - 1; i >= 0; i--)
            if (content.getChildAt(i) instanceof LinearLayout) return (LinearLayout) content.getChildAt(i);
        return null;
    }

    private View findTag(ViewGroup parent, String key) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (key.equals(child.getTag())) return child;
            if (child instanceof ViewGroup) {
                View found = findTag((ViewGroup) child, key);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void renderActiveNotes() {
        if (activeRow == null) return;
        activeRow.removeAllViews();
        List<String> selection = screen == Screen.ROOM ? groupNotes : soloNotes;
        List<String> ordered = NoteCatalog.order(selection);
        float widthDp = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density;
        float fontScale = getResources().getConfiguration().fontScale;
        int columns = Math.min(ordered.size(), widthDp >= 300 && fontScale <= 1.3f ? 5
                : widthDp >= 260 ? 4 : 3);
        for (int start = 0; start < ordered.size(); start += columns) {
            LinearLayout row = new LinearLayout(this);
            for (int j = start; j < Math.min(start + columns, ordered.size()); j++) {
                String note = ordered.get(j);
                boolean chosen = note.equals(activeNote);
                TextView chip = Ui.text(this, NoteCatalog.chipLabel(note), 12,
                        chosen ? Color.WHITE : Ui.INK, true);
                chip.setGravity(Gravity.CENTER);
                chip.setMinHeight(dp(62));
                chip.setPadding(dp(2), dp(3), dp(2), dp(3));
                chip.setBackground(Ui.ripple(this, Ui.bordered(this,
                        chosen ? Ui.GREEN : Ui.LIGHT_GOLD, chosen ? Ui.GREEN : Ui.BORDER, 14)));
                Ui.touchMotion(chip);
                chip.setContentDescription(NoteCatalog.label(note)
                        + ", ketuk untuk memilih tanpa membunyikan"
                        + (chosen ? ", nada aktif" : ""));
                chip.setOnClickListener(v -> {
                    activeNote = note;
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (instrument != null) {
                        instrument.setNote(note);
                        instrument.setContentDescription("Ketuk untuk membunyikan angklung nada " + NoteCatalog.label(note));
                    }
                    TextView label = (TextView) findTag(root, "active_label");
                    if (label != null) label.setText("Nada aktif: " + NoteCatalog.label(note));
                    if (feedback != null) {
                        feedback.setText("Siap: " + NoteCatalog.solfegeFor(note) + "  ✦");
                        feedback.setContentDescription("Nada " + NoteCatalog.label(note)
                                + " dipilih. Goyangkan ponsel atau ketuk gambar angklung untuk membunyikan.");
                    }
                    renderActiveNotes();
                });
                row.addView(chip, Ui.margins(this, 0, -2, j == start ? 0 : 5, 0, 0, 0));
                ((LinearLayout.LayoutParams) chip.getLayoutParams()).weight = 1f;
            }
            activeRow.addView(row, Ui.margins(this, -1, -2, 0, start == 0 ? 0 : 5, 0, 0));
        }
    }

    private void playActiveNote() {
        if (screen != Screen.SOLO && screen != Screen.ROOM) return;
        if (!(screen == Screen.ROOM ? groupNotes : soloNotes).contains(activeNote)) return;
        if (screen == Screen.ROOM) {
            if (room == null || room.isHost() || !room.isReady()) return;
            room.play(activeNote);
        } else if (!sounds.play(activeNote)) {
            toast("Suara sedang disiapkan, coba sebentar lagi.");
            return;
        }
        if (instrument != null) {
            instrument.ring();
            instrument.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        if (feedback != null) {
            feedback.setText(screen == Screen.ROOM
                    ? "Terkirim: " + NoteCatalog.solfegeFor(activeNote) + " → speaker  ✦"
                    : "Berbunyi: " + NoteCatalog.solfegeFor(activeNote) + "  ✦");
            feedback.setContentDescription(screen == Screen.ROOM
                    ? "Nada " + NoteCatalog.label(activeNote) + " dikirim ke speaker"
                    : "Nada " + NoteCatalog.label(activeNote) + " dibunyikan");
        }
    }

    private void refreshMembers() {
        if (screen != Screen.ROOM || membersColumn == null) return;
        membersColumn.removeAllViews();
        for (LanRoom.Member member : members) {
            LinearLayout item = Ui.column(this);
            pad(item, 15);
            item.setBackground(Ui.bordered(this, Color.WHITE, Ui.BORDER, 16));
            String who = room != null && member.id.equals(room.ownId()) ? "Kamu" : member.name;
            item.addView(Ui.text(this, "●  " + who + (member.speaker ? " · SPEAKER" : " · PEMAIN"), 15, Ui.INK, true));
            item.addView(Ui.text(this, member.speaker ? "Semua nada terdengar di perangkat ini"
                    : NoteCatalog.describeList(member.notes), 13, Ui.GREEN, false),
                    Ui.margins(this, -1, -2, 20, 5, 0, 0));
            membersColumn.addView(item, Ui.margins(this, -1, -2, 0, 0, 0, 9));
        }
    }

    private void changeGroupNotes() {
        if (room == null || room.isHost() || !room.isReady()) return;
        chooseGroupNotes(() -> {
            if (room == null || room.isHost() || screen != Screen.ROOM) return;
            if (!groupNotes.contains(activeNote)) activeNote = groupNotes.get(0);
            room.updateNotes(new ArrayList<>(groupNotes));
            if (instrument != null) instrument.setNote(activeNote);
            TextView label = (TextView) findTag(root, "active_label");
            if (label != null) label.setText("Nada aktif: " + NoteCatalog.label(activeNote));
            renderActiveNotes();
        });
    }

    private void chooseGroupNotes(Runnable onSaved) {
        boolean[] checked = new boolean[NoteCatalog.NOTES.length];
        for (int i = 0; i < checked.length; i++) checked[i] = groupNotes.contains(NoteCatalog.NOTES[i]);
        String[] labels = new String[NoteCatalog.NOTES.length];
        for (int i = 0; i < labels.length; i++) labels[i] = NoteCatalog.label(NoteCatalog.NOTES[i]);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Pilih nada pemain (1–3)")
                .setMultiChoiceItems(labels, checked, (box, which, isChecked) -> {
                    if (isChecked && count(checked) > 3) {
                        checked[which] = false;
                        ((AlertDialog) box).getListView().setItemChecked(which, false);
                        toast("Maksimal tiga nada per pemain.");
                    }
                })
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            List<String> changed = new ArrayList<>();
            for (int i = 0; i < checked.length; i++) if (checked[i]) changed.add(NoteCatalog.NOTES[i]);
            if (!NoteCatalog.validSelection(changed, 3)) { toast("Pilih minimal satu nada."); return; }
            groupNotes.clear();
            groupNotes.addAll(changed);
            save("group", groupNotes);
            onSaved.run();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private int count(boolean[] values) {
        int count = 0;
        for (boolean value : values) if (value) count++;
        return count;
    }

    private void addVolumeControl(LinearLayout parent) {
        LinearLayout box = Ui.column(this);
        pad(box, 16);
        box.setBackground(Ui.rounded(this, Ui.LIGHT_GOLD, 18));
        TextView label = Ui.text(this, "Volume angklung: " + preferences.getInt("volume", 80) + "%", 15, Ui.INK, true);
        box.addView(label);
        SeekBar volume = new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(preferences.getInt("volume", 80));
        volume.setContentDescription("Volume angklung");
        box.addView(volume);
        box.addView(body("Berlaku untuk solo dan ponsel speaker; atur juga volume media perangkat."));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText("Volume angklung: " + progress + "%");
                sounds.setVolume(progress);
                if (fromUser) preferences.edit().putInt("volume", progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        parent.addView(box, Ui.margins(this, -1, -2, 0, 18, 0, 0));
    }

    private void showSettings() {
        settingsOpen = true;
        shake.stop();
        LinearLayout layout = Ui.column(this);
        pad(layout, 20);
        addVolumeControl(layout);
        layout.addView(Ui.gap(this, 18));
        TextView label = body(MotionTuning.settingLabel(preferences.getInt("sensitivity", 55)));
        layout.addView(label);
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(preferences.getInt("sensitivity", 55));
        layout.addView(bar);
        layout.addView(body("0% mematikan bunyi otomatis dari gerakan; gambar angklung tetap bisa diketuk. Nilai rendah memerlukan goyangan lebih tegas dan memberi jeda lebih panjang."));
        TextView credit = body("Rekaman angklung Bandung oleh Parking Sun (Freesound). Sampel diproses menjadi WAV mono dan levelnya diseragamkan. Ketuk untuk melihat sumber audio.");
        credit.setTextColor(Ui.GREEN);
        credit.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://freesound.org/people/Parking%20Sun/packs/4799/"))));
        layout.addView(credit, Ui.margins(this, -1, -2, 0, 16, 0, 0));
        TextView license = body("Lisensi CC BY 3.0 · Lihat syarat atribusi");
        license.setTextColor(Ui.GREEN);
        license.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://creativecommons.org/licenses/by/3.0/"))));
        layout.addView(license, Ui.margins(this, -1, -2, 0, 6, 0, 0));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText(MotionTuning.settingLabel(progress));
                shake.setSensitivity(progress);
                preferences.edit().putInt("sensitivity", progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Suara dan gerakan").setView(layout)
                .setPositiveButton("Selesai", null).create();
        dialog.setOnDismissListener(ignored -> {
            settingsOpen = false;
            registerMotion();
            refreshMotionCue();
        });
        dialog.show();
    }

    private void animateIn(View view) {
        view.setAlpha(0f);
        view.setTranslationY(dp(16));
        view.animate().alpha(1f).translationY(0f).setDuration(450).start();
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    private void leaveRoom() {
        roomGeneration++;
        if (room != null) room.close();
        room = null;
        members = Collections.emptyList();
    }

    @Override public void onBackPressed() {
        switch (screen) {
            case HOME: super.onBackPressed(); break;
            case ABOUT: showHome(); break;
            case PICK_SOLO: showHome(); break;
            case LOBBY: showHome(); break;
            case CONNECTING: leaveRoom(); showLobby(); break;
            case SOLO: showPicker(); break;
            case ROOM:
                new AlertDialog.Builder(this).setTitle("Keluar dari ruang?")
                        .setMessage(room != null && room.isHost() ? "Ruang akan ditutup untuk semua pemain."
                                : "Kamu akan meninggalkan permainan.")
                        .setNegativeButton("Tetap main", null)
                        .setPositiveButton("Keluar", (dialog, which) -> { leaveRoom(); showLobby(); }).show();
                break;
        }
    }
}
