package org.zektopic.zekals;

import android.app.Activity;
import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.widget.ProgressBar;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.icu.text.BreakIterator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/** Native views work with TalkBack, Android Switch Access, touch and keyboard. */
// UI text comes from the language packs in assets, not Android string resources.
@android.annotation.SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private static final String TAG = "zekALS";
    private static final int BG = Color.rgb(12,23,36), SURFACE = Color.rgb(20,35,53), INK = Color.rgb(245,248,252), ACCENT = Color.rgb(188,235,207), SCAN = Color.rgb(255,221,133);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Button> buttons = new ArrayList<>();
    /** Each on-screen row of buttons is one group for row/column scanning. */
    private final List<List<Button>> groups = new ArrayList<>();
    private final List<LanguagePack> packs = new ArrayList<>();
    private final ArrayDeque<String> undo = new ArrayDeque<>();
    private final long[] dwellState = {-1,0,0};
    private SharedPreferences preferences;
    private LanguagePack pack;
    private EditText message;
    private TextView status, cameraStatus, messagePreview, voiceValue;
    private String cameraText;
    private Rect gazeReentryBlocked;
    private String currentUtterance = "";
    private long utteranceSequence;
    private LinearLayout content, settingsPanel;
    private ScrollView scroll, settingsScroll;
    private ImageView cameraPreview, calibrationPreview;
    private Bitmap latestPreview;
    private TextView calibrationText, calibrationStatus;
    private CalibrationTarget calibrationTarget;
    private boolean calibrationPositioning;
    private long calibrationTracked, calibrationMissing, calibrationTick;
    /** The button the pointer is locked onto, and the one it is settling on before locking. */
    private Button lockedButton, lockCandidate;
    private long lockCandidateSince, lockLeftSince;
    private static final long LOCK_DELAY_MS = 120, UNLOCK_MS = 150;
    /** Head pose held when the face was first found; the uncalibrated pointer moves relative to it. */
    private final double[] neutralHead = new double[2];
    private int neutralSamples, restarts;
    /** Blink to select: a deliberate closure between blinkMillis and BLINK_MAX_MS presses what the pointer is on. */
    private boolean blinkSelect = true;
    private long blinkMillis = 600, closedSince;
    private int blinkTarget = -1;
    private static final long BLINK_MAX_MS = 2500;
    private long restartWindow;
    private static final double HEAD_GAIN_X = 5, HEAD_GAIN_Y = 6;
    private final Runnable restartCamera = this::restartCameraIfWanted;
    private Button pauseButton, modeButton, selected;
    private List<Button> scanGroup, scanItems;
    private TextToSpeech tts;
    private boolean speechReady, paused, large, contrast, changing, running, cameraWanted, requestingCamera, sizeWarned, gazeHinted;
    private String lastMessage = "";
    private int languageIndex, keyboardPage, mode, pointerTarget = -1, blockedTarget = -1, scanIndex = -1, scanEntry, renders;
    private long dwellMillis = 1200, scanMillis = 1400, lastScan;
    private float speechRate = .9f;
    private final List<Voice> voices = new ArrayList<>();
    private int voiceIndex;
    private FrameLayout surface, calibrationOverlay;
    private ProgressBar selectionProgress;
    private CameraTracker cameraTracker;
    private String requestedProvider = "CPU";
    private volatile long cameraGeneration;
    private long lastGazeTime;
    private static final class CameraSample {
        final double[] point; final long captured, generation; final String provider; final double blink;
        CameraSample(double[] p,long c,String name,long g,double b){point=p;captured=c;provider=name;generation=g;blink=b;}
    }
    /** Survives rotation in-process only; messages are never written to storage. */
    private static final class Retained {
        final String message; final boolean paused;
        Retained(String m,boolean p){message=m;paused=p;}
    }
    private volatile CameraSample pendingCameraSample;
    private final Runnable deliverGaze = this::deliverGazeSample;
    private final double[] gazeState = new double[4];
    private Calibration calibration;
    private int calibrationWidth, calibrationHeight, calibrationRotation, cameraRotation;
    private GazePointer pointer;
    private int calibrationIndex = -1;
    private final List<double[]> calibrationPoints = new ArrayList<>();
    private final List<double[]> calibrationSamples = new ArrayList<>();
    /** Nine points, centre first, then around the edges so the eyes cannot anticipate the next one. */
    private final double[][] calibrationTargets = {{.5,.5},{.1,.12},{.9,.12},{.9,.88},{.1,.88},{.5,.12},{.9,.5},{.5,.88},{.1,.5}};
    /** The targets in whole-surface fractions (the gaze mapping's coordinates), laid out inside the system bars. */
    private double[][] calibrationPositions;
    private Button calibrationCancel;
    /** MediaPipe features (eyeX, eyeY, headX, headY, lookX, lookY, open): x and y use different cues. */
    private static final int[] GAZE_X = {0, 2, 4}, GAZE_Y = {1, 3, 5, 6};
    private static final long TARGET_MS = 2500, SETTLE_MS = 800, FACE_LOST_MS = 10000;
    /** Fits worse than this (as a fraction of the screen) are not usable even for large buttons. */
    private static final double CALIBRATION_CEILING = .25;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("preferences", MODE_PRIVATE);
        large = preferences.getBoolean("large", false); contrast = preferences.getBoolean("contrast", false);
        dwellMillis = preferences.getLong("dwell", 1200); scanMillis = preferences.getLong("scan", 1400);
        speechRate = preferences.getFloat("rate", .9f);
        blinkSelect = preferences.getBoolean("blink", true); blinkMillis = preferences.getLong("blinkTime", 600);
        mode = preferences.getInt("mode", 0); if (mode < 0 || mode > 3 || (mode == 3 && !BuildConfig.CAMERA_AVAILABLE)) mode = 0;
        requestedProvider = preferences.getString("provider", "CPU"); if (!Arrays.asList("CPU","GPU","NNAPI").contains(requestedProvider)) requestedProvider = "CPU";
        cameraWanted = BuildConfig.CAMERA_AVAILABLE && preferences.getBoolean("camera", false);
        restoreCalibration();
        Object previous = getLastNonConfigurationInstance();
        if (previous instanceof Retained) { lastMessage = ((Retained) previous).message; paused = ((Retained) previous).paused; }
        try {
            String[] files = getAssets().list("languages");
            if (files == null) throw new IllegalStateException("Missing languages");
            java.util.Arrays.sort(files);
            for (String file : files) if (file.endsWith(".json")) packs.add(new LanguagePack(getAssets(), file));
            String code = preferences.getString("language", "en");
            for (int i = 0; i < packs.size(); i++) if (packs.get(i).code.equals(code)) languageIndex = i;
            pack = packs.get(languageIndex); render();
            // Entering touch mode clears focus from buttons; the root must hold it to see switch keys.
            getWindow().getDecorView().getViewTreeObserver().addOnTouchModeChangeListener(inTouchMode -> { if (surface != null && !surface.hasFocus()) surface.requestFocus(); });
        } catch (Exception error) {
            Log.e(TAG, "Language assets could not be loaded", error);
            pack = null; TextView failure = new TextView(this); failure.setText(R.string.languages_failed); setContentView(failure); return;
        }
        tts = new TextToSpeech(this, result -> runOnUiThread(() -> {
            speechReady = result == TextToSpeech.SUCCESS; refreshVoices();
            if (!speechReady) notice(t("installVoice", "Install an offline voice in device settings."));
        }));
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
            @Override public void onStart(String id){}
            @Override public void onDone(String id){runOnUiThread(()->{if(id.equals(currentUtterance))notice(t("ready","Ready when you are."));});}
            @Override public void onError(String id){runOnUiThread(()->{if(id.equals(currentUtterance))notice(t("speechFailed","Speech failed. Check the installed voice."));});}
        });
    }
    @Override public Object onRetainNonConfigurationInstance() { return new Retained(lastMessage, paused); }
    private String t(String key, String fallback) { return pack == null ? fallback : pack.text(key, fallback); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void save() {
        preferences.edit().putString("language", pack.code).putBoolean("large", large).putBoolean("contrast", contrast)
            .putLong("dwell", dwellMillis).putLong("scan", scanMillis).putFloat("rate", speechRate).putInt("mode", mode)
            .putString("provider", requestedProvider).putBoolean("camera", cameraWanted).putBoolean("blink", blinkSelect).putLong("blinkTime", blinkMillis).apply();
    }
    private void notice(String text) { if (status != null && !status.getText().toString().equals(text)) status.setText(text); }
    private void cameraText(String text) { cameraText = text; if (cameraStatus != null && !cameraStatus.getText().toString().equals(text)) cameraStatus.setText(text); }
    private TextView label(String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextColor(INK); view.setTextSize(size + (large ? 4 : 0));
        view.setTextLocale(pack.locale); view.setPadding(dp(8),dp(12),dp(8),dp(8)); return view;
    }
    private GradientDrawable background(int color) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(12)); shape.setStroke(dp(1), contrast ? Color.WHITE : Color.rgb(82,104,126)); return shape;
    }
    private Button button(String text, Runnable action) {
        Button view = new Button(this); view.setId(View.generateViewId()); view.setText(text); view.setAllCaps(false);
        view.setTextSize(large ? 23 : 19); view.setTextLocale(pack.locale); view.setTextColor(INK); view.setMinHeight(dp(64)); view.setMinimumWidth(dp(48));
        view.setBackground(background(contrast ? Color.BLACK : SURFACE)); view.setPadding(dp(10),dp(10),dp(10),dp(10));
        view.setOnClickListener(v -> { if(mode==3){Rect bounds=new Rect();if(view.getGlobalVisibleRect(bounds))gazeReentryBlocked=new Rect(bounds);} blockedTarget = view.getId(); resetDwell(); action.run(); });
        view.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) pointerTarget = -1;
            else pointerTarget = view.getId(); return false;
        });
        buttons.add(view); return view;
    }
    private LinearLayout row(LinearLayout parent, Button... entries) {
        // Baseline alignment shifts buttons whose labels wrap onto two lines.
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setBaselineAligned(false);
        for (Button entry : entries) { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1); params.setMargins(dp(4),dp(4),dp(4),dp(4)); row.addView(entry, params); }
        parent.addView(row); groups.add(Arrays.asList(entries)); return row;
    }
    private void grid(LinearLayout parent, List<Button> entries, int columns) {
        for (int i=0; i<entries.size(); i+=columns) row(parent, entries.subList(i, Math.min(entries.size(), i+columns)).toArray(new Button[0]));
    }
    /** A character key; combining signs show on a dotted circle and the joiner gets a word label. */
    private Button keyButton(String key) {
        return button(key.equals("\u200d") ? t("joinLetters","Join letters") : key.matches("\\p{M}+") ? "◌"+key : key, () -> append(key));
    }
    /** A labelled value with − and + buttons; the value stays visible in Settings. */
    private void stepper(LinearLayout panel, String name, Supplier<String> value, Runnable down, Runnable up) {
        TextView shown = label(name + ": " + value.get(), 18); panel.addView(shown);
        Runnable update = () -> { save(); shown.setText(name + ": " + value.get()); notice(shown.getText().toString()); };
        row(panel, button(name + " −", () -> { down.run(); update.run(); }), button(name + " +", () -> { up.run(); update.run(); }));
    }
    private void render() {
        // Rebuilding the views must not throw the user back to the top or close Settings.
        int keepScroll = scroll == null ? 0 : scroll.getScrollY();
        boolean keepSettings = settingsScroll != null && settingsScroll.getVisibility() == View.VISIBLE;
        renders++; resetDwell(); clearScan(); buttons.clear(); groups.clear(); selected = null; lockedButton = null; lockCandidate = null; pointerTarget = -1; scanIndex = -1; scanGroup = null; scanItems = null;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(8),dp(6),dp(8),dp(4)); root.setBackgroundColor(contrast ? Color.BLACK : BG);root.setLayoutDirection(pack.rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(dp(8)+insets.getSystemWindowInsetLeft(), dp(6)+insets.getSystemWindowInsetTop(), dp(8)+insets.getSystemWindowInsetRight(), dp(4)+insets.getSystemWindowInsetBottom()); return insets; });
        android.content.res.Configuration config = getResources().getConfiguration();
        // Wide landscape screens show the message and phrases beside the keyboard, so the whole
        // board fits without scrolling (scrolling by gaze or switch is slow).
        boolean wide = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && config.screenWidthDp >= 900;
        int columnDp = wide ? config.screenWidthDp / 2 : config.screenWidthDp;
        TextView title = label("zekALS · " + pack.name, 25); if(android.os.Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true);
        cameraStatus=label(cameraText == null ? t("cameraOff","Camera off") : cameraText,16);
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setBaselineAligned(false);
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        // Short landscape phones need the height for content; the language name stays on its button.
        if (config.screenHeightDp >= 480) titles.addView(title);
        if (BuildConfig.CAMERA_AVAILABLE) titles.addView(cameraStatus);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        // What the camera sees, so the user can tell whether their face and eyes are found.
        cameraPreview = new ImageView(this); cameraPreview.setScaleType(ImageView.ScaleType.FIT_CENTER); cameraPreview.setImageBitmap(latestPreview);
        cameraPreview.setContentDescription(t("cameraPreview","Camera view")); cameraPreview.setVisibility(cameraTracker != null ? View.VISIBLE : View.GONE);
        header.addView(cameraPreview, new LinearLayout.LayoutParams(dp(120), dp(90)));
        root.addView(header);
        pauseButton = button(paused ? t("resume","Resume") : t("pause","Pause"), () -> setPaused(!paused));
        LinearLayout nav = row(wide ? header : root, pauseButton, button(t("scrollUp","Scroll up"), () -> { visibleScroll().smoothScrollBy(0,-visibleScroll().getHeight()/2); resetDwell(); }), button(t("scrollDown","Scroll down"), () -> { visibleScroll().smoothScrollBy(0,visibleScroll().getHeight()/2); resetDwell(); }));
        // Wide screens fold the navigation row into the header to leave the height for the board.
        if (wide) { nav.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 2)); ((LinearLayout.LayoutParams) cameraPreview.getLayoutParams()).setMargins(dp(8), 0, dp(8), 0); }
        messagePreview = label(lastMessage, 18); messagePreview.setMaxLines(2); messagePreview.setEllipsize(TextUtils.TruncateAt.START);
        messagePreview.setBackground(background(contrast ? Color.BLACK : SURFACE)); messagePreview.setVisibility(View.GONE);
        messagePreview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); root.addView(messagePreview);
        scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> updatePreview());
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content);
        // On wide screens the keyboard rows share all the height left over, so keys are as large as possible.
        scroll.setFillViewport(wide);
        message = new EditText(this); message.setId(View.generateViewId()); message.setText(lastMessage); message.setSelection(message.length());
        message.setSaveEnabled(false); message.setTextColor(INK); message.setHintTextColor(Color.LTGRAY); message.setTextSize(large ? 28 : 24);
        message.setHint(t("textPlaceholder","Write a message")); message.setContentDescription(t("messageLabel","Your message")); message.setMinLines(wide ? 2 : 3);
        message.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        message.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)}); message.setBackground(background(contrast ? Color.BLACK : SURFACE));
        message.setPadding(dp(14),dp(14),dp(14),dp(14)); message.setTextLocale(pack.locale);
        message.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {}
            public void afterTextChanged(Editable text) {
                if (!changing && !text.toString().equals(lastMessage)) { if (undo.size() >= 30) undo.removeFirst(); undo.addLast(lastMessage); }
                lastMessage = text.toString(); messagePreview.setText(lastMessage); updatePreview();
            }
        });
        Button speak = button(t("speak","Speak"), this::speak); speak.setBackground(background(ACCENT)); speak.setTextColor(BG); speak.setTag(true);
        Button stop = button(t("stop","Stop"), () -> { stopSpeech(); notice(t("stopped","Speech stopped.")); });
        Button undoButton = button(t("undo","Undo"), () -> { if (!undo.isEmpty()) { changing=true; message.setText(undo.removeLast()); message.setSelection(message.length()); changing=false; } });
        Button clear = button(t("clear","Clear"), () -> message.setText(""));
        Button space = button(t("space","Space"), () -> append(" ")), delete = button(t("backspace","Delete"), this::backspace);
        Button settings = button(t("settings","Settings"), () -> showSettings(true));
        if (wide) {
            // Message beside its actions, so the height goes to the keyboard.
            LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setBaselineAligned(false); content.addView(top);
            LinearLayout.LayoutParams box = new LinearLayout.LayoutParams(0, -1, 2); box.setMargins(dp(4),dp(4),dp(4),dp(4)); top.addView(message, box);
            LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.VERTICAL); top.addView(actions, new LinearLayout.LayoutParams(0, -2, 1));
            row(actions, speak, stop); row(actions, undoButton, clear);
        } else {
            TextView heading = label(t("messageTitle","What would you like to say?"),24); if(android.os.Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true); content.addView(heading);
            content.addView(message); row(content, speak, stop); row(content, undoButton, clear);
        }
        boolean keyboard = false;
        try {
            List<Button> phraseButtons = new ArrayList<>();
            for (String phrase : pack.phrases()) phraseButtons.add(button(phrase, () -> append((message.length()>0 ? " " : "") + phrase)));
            if (!wide) content.addView(label(t("phrases","Everyday phrases"),22));
            grid(content, phraseButtons, wide ? Math.min(8, phraseButtons.size()) : columnDp >= 600 ? 4 : 2);
            List<List<String>> pages = pack.pages(); keyboardPage %= pages.size();
            Button more = button(t("moreKeys","More characters") + " " + (keyboardPage+1) + "/" + pages.size(), () -> { keyboardPage++; render(); });
            Button language = button(pack.name + " ›", () -> { stopSpeech(); languageIndex=(languageIndex+1)%packs.size(); pack=packs.get(languageIndex); keyboardPage=0; save(); refreshVoices(); render(); });
            if (wide) {
                LinearLayout board = new LinearLayout(this); board.setOrientation(LinearLayout.VERTICAL); content.addView(board, new LinearLayout.LayoutParams(-1, 0, 1));
                List<List<String>> rows = pack.rows(keyboardPage);
                // Rows stretch to fill the height; busy pages (e.g. 42 Sinhala signs in four rows) get a
                // slightly smaller glyph and padding so every row still fits without scrolling.
                int glyph = (rows.size() >= 4 ? 26 : 30) + (large ? 6 : 0);
                for (List<String> keys : rows) {
                    List<Button> line = new ArrayList<>();
                    for (String key : keys) {
                        Button view = keyButton(key); view.setMinHeight(dp(52));
                        if (rows.size() >= 4) view.setPadding(dp(6),dp(2),dp(6),dp(2));
                        if (key.codePointCount(0, key.length()) <= 2) view.setTextSize(glyph); else if (rows.size() >= 4) view.setTextSize(large ? 20 : 16);
                        line.add(view);
                    }
                    row(board, line.toArray(new Button[0])).setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
                }
                row(board, more, space, delete, language, settings).setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
            } else {
                row(content, more, language);
                List<Button> keys = new ArrayList<>(); for (String key : pages.get(keyboardPage)) keys.add(keyButton(key));
                grid(content, keys, columnDp >= 600 ? 10 : 5);
            }
            keyboard = wide;
        } catch (Exception error) { Log.w(TAG, "Keyboard pack is invalid: " + pack.code, error); notice(t("keyboardInvalid","This keyboard could not be loaded.")); }
        if (!keyboard) { row(content, space, delete); row(content, settings); }
        // Settings is a separate page: the board is hidden so scanning and gaze only reach visible buttons.
        settingsScroll = new ScrollView(this); root.addView(settingsScroll, new LinearLayout.LayoutParams(-1,0,1));
        settingsPanel = new LinearLayout(this); settingsPanel.setOrientation(LinearLayout.VERTICAL); settingsScroll.addView(settingsPanel);
        row(settingsPanel, button("‹ " + t("settingsBack","Back to board"), () -> showSettings(false)));
        LinearLayout columns = new LinearLayout(this); columns.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL); columns.setBaselineAligned(false); settingsPanel.addView(columns);
        LinearLayout access = columns, output = columns;
        if (wide) {
            access = new LinearLayout(this); access.setOrientation(LinearLayout.VERTICAL); columns.addView(access, new LinearLayout.LayoutParams(0, -2, 1));
            output = new LinearLayout(this); output.setOrientation(LinearLayout.VERTICAL); columns.addView(output, new LinearLayout.LayoutParams(0, -2, 1));
        }
        modeButton = button(modeName(), () -> { mode=(mode+1)%(BuildConfig.CAMERA_AVAILABLE?4:3); lastScan=0; clearScan(); scanGroup=null; scanItems=null; scanIndex=-1; resetDwell(); save(); modeButton.setText(modeName()); updateProgressVisibility(); }); row(access,modeButton);
        stepper(access, t("dwellTime","Dwell time"), () -> dwellMillis + " ms", () -> dwellMillis=Math.max(500,dwellMillis-100), () -> dwellMillis=Math.min(3000,dwellMillis+100));
        stepper(access, t("scanTime","Scan interval"), () -> scanMillis + " ms", () -> scanMillis=Math.max(600,scanMillis-200), () -> scanMillis=Math.min(4000,scanMillis+200));
        row(access,button(t("textSize","Text size"), () -> { large=!large; save(); render(); }), button(t("contrast","Contrast"), () -> { contrast=!contrast; save(); render(); }));
        Button blinkButton = button(blinkSelect ? t("blinkSelectOn","Blink to select: on") : t("blinkSelectOff","Blink to select: off"), () -> {});
        blinkButton.setOnClickListener(v -> { blinkSelect = !blinkSelect; save(); blinkButton.setText(blinkSelect ? t("blinkSelectOn","Blink to select: on") : t("blinkSelectOff","Blink to select: off")); notice(blinkButton.getText().toString()); });
        row(access, blinkButton);
        stepper(access, t("blinkTime","Blink length"), () -> blinkMillis + " ms", () -> blinkMillis=Math.max(300,blinkMillis-100), () -> blinkMillis=Math.min(2000,blinkMillis+100));
        access.addView(label(t("scanHelp","Press Space to choose the highlighted button. Escape pauses."),18));
        stepper(output, t("speechSpeed","Speech speed"), () -> String.format(pack.locale,"%.1f×",speechRate), () -> speechRate=Math.max(.5f,Math.round((speechRate-.1f)*10)/10f), () -> speechRate=Math.min(1.5f,Math.round((speechRate+.1f)*10)/10f));
        voiceValue = label(t("voice","Voice") + ": " + (voices.isEmpty() ? "—" : voiceLabel(voiceIndex)), 18); output.addView(voiceValue);
        row(output,button(t("voice","Voice") + " ›", this::nextVoice), button(t("voiceSettings","Install voices"), this::openVoiceSettings));
        addCameraControls(output);
        status=label(t("ready","Ready when you are."),18); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); root.addView(status);
        selectionProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        selectionProgress.setMax(100); selectionProgress.setContentDescription(t("selectionProgress","Selection progress")); root.addView(selectionProgress); updateProgressVisibility();
        surface = new SwitchSurface(); surface.addView(root); pointer = new GazePointer(); surface.addView(pointer, new FrameLayout.LayoutParams(dp(56), dp(56)));
        setContentView(surface); surface.requestFocus();
        // Calibration maps the viewport, so a keyboard-page rebuild preserves it.
        calibrationIndex = -1;
        showSettings(keepSettings);
        ScrollView kept = visibleScroll(); if (keepScroll > 0) kept.post(() -> kept.scrollTo(0, keepScroll));
    }
    private ScrollView visibleScroll() { return settingsScroll != null && settingsScroll.getVisibility() == View.VISIBLE ? settingsScroll : scroll; }
    private void showSettings(boolean open) {
        clearScan(); scanGroup = null; scanItems = null; scanIndex = -1; resetDwell();
        scroll.setVisibility(open ? View.GONE : View.VISIBLE); settingsScroll.setVisibility(open ? View.VISIBLE : View.GONE);
        if (open) settingsScroll.scrollTo(0, 0);
        if (messagePreview != null && open) messagePreview.setVisibility(View.GONE);
    }
    /** Pins a copy of the message above the scroll area whenever the message box is out of view. */
    private void updatePreview() {
        if (messagePreview == null || message == null) return;
        Rect visible = new Rect(); boolean shown = message.getLocalVisibleRect(visible) && visible.height() >= message.getHeight() / 2;
        int wanted = !shown && message.length() > 0 ? View.VISIBLE : View.GONE;
        if (messagePreview.getVisibility() != wanted) messagePreview.setVisibility(wanted);
    }
    private void updateProgressVisibility() { if (selectionProgress != null) selectionProgress.setVisibility(mode == 1 || mode == 3 ? View.VISIBLE : View.GONE); }
    private void addCameraControls(LinearLayout panel) {
        if(!BuildConfig.CAMERA_AVAILABLE)return;
        row(panel,button(t("cameraControl","Camera: start / stop"),()->{
            if(cameraTracker!=null){cameraWanted=false;save();stopCamera(true);notice(t("cameraStopped","Camera stopped. Other input still works."));}
            else if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){cameraWanted=true;save();startCamera();}
            else if(preferences.getBoolean("cameraAsked",false)&&!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
                // Android stops showing the prompt after repeated denials; only App info can grant it now.
                notice(t("cameraSettings","Allow the camera in App info › Permissions."));
                try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",getPackageName(),null)));}catch(ActivityNotFoundException error){Log.w(TAG,"App info unavailable",error);}
            }
            else{preferences.edit().putBoolean("cameraAsked",true).apply();requestingCamera=true;requestPermissions(new String[]{Manifest.permission.CAMERA},42);}
        }));
        row(panel,button(t("inference","Inference")+": " + requestedProvider,()->{
            requestedProvider=requestedProvider.equals("CPU")?"GPU":requestedProvider.equals("GPU")?"NNAPI":"CPU";
            // Another model family can change coordinates, so the calibration is discarded.
            boolean restart=cameraTracker!=null;save();stopCamera(true);render();
            if(restart)startCamera();else notice(t("inferenceRequest","Inference: {provider}. Start the camera to apply.").replace("{provider}",requestedProvider));
        }),button(t("calibrate","Calibrate"),this::startCalibration));
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request!=42)return;
        requestingCamera=false;
        if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){cameraWanted=true;save();startCamera();}
        else notice(t("cameraDenied","Camera permission denied. Other input still works."));
    }
    private void deliverGazeSample() {
        CameraSample sample=pendingCameraSample;if(sample==null||sample.generation!=cameraGeneration||!running)return;
        long now=SystemClock.uptimeMillis();boolean valid=sample.point!=null&&now-sample.captured<=500;
        if(!Double.isNaN(sample.blink))blink(sample.blink>.5,sample.captured);
        // A blink or a dropped frame keeps the last gaze; recency (500 ms) decides when it is stale.
        long previousGaze=lastGazeTime;if(valid)lastGazeTime=sample.captured;
        // Calibration learns from the raw eye features; the pointer is smoothed after mapping,
        // because the native filter only accepts screen fractions in [0,1].
        if(valid&&calibration!=null&&calibration.dimensions()!=sample.point.length){
            // A calibration from another tracker version uses different features.
            Log.i(TAG,"Stored calibration expects "+calibration.dimensions()+" features, tracker gives "+sample.point.length);calibration=null;preferences.edit().remove("calibration").apply();
        }
        // Re-centre the head pointer when the face returns after a gap (the user may have moved).
        if(valid&&sample.captured-previousGaze>3000)neutralSamples=0;
        double[] mapped=!valid?null:calibration!=null?calibration.map(sample.point):headPointer(sample.point);
        if(mapped!=null)NativeCore.smooth(gazeState,mapped[0],mapped[1],sample.captured,120,true);
        if(!valid){if(now-lastGazeTime>500)cameraText(t("cameraNoFace","No face detected")+" · "+sample.provider);return;}
        if(calibrationIndex>=0&&!calibrationPositioning&&calibrationTracked>=SETTLE_MS)calibrationPoints.add(sample.point.clone());
        cameraText(t("cameraTracking","Eye tracking on")+" · "+sample.provider);
    }
    /**
     * Before calibration the pointer follows head turns from the pose held when the face was first
     * found, so the user sees it move straight away. Custom ONNX models already give screen fractions.
     */
    private double[] headPointer(double[] features){
        if(features.length<4)return new double[]{Math.max(0,Math.min(1,features[0])),Math.max(0,Math.min(1,features[1]))};
        if(neutralSamples<10){
            if(neutralSamples==0){neutralHead[0]=0;neutralHead[1]=0;}
            neutralHead[0]+=features[2]/10;neutralHead[1]+=features[3]/10;
            if(++neutralSamples==10)Log.i(TAG,String.format(java.util.Locale.ROOT,"Head pointer centred at (%.3f, %.3f)",neutralHead[0],neutralHead[1]));
            return null;
        }
        return new double[]{Math.max(0,Math.min(1,.5+(features[2]-neutralHead[0])*HEAD_GAIN_X)),Math.max(0,Math.min(1,.5+(features[3]-neutralHead[1])*HEAD_GAIN_Y))};
    }
    /** Tracks eye closure; a deliberate blink presses what the pointer is on, or the scanned row/button. */
    private void blink(boolean closed,long time){
        if(!blinkSelect||calibrationIndex>=0){closedSince=0;return;}
        // The eyes stop being tracked while closed, so remember the locked button from the moment they close.
        if(closed){if(closedSince==0){closedSince=time;blinkTarget=lockedButton==null?-1:lockedButton.getId();}return;}
        if(closedSince==0)return;
        long held=time-closedSince;closedSince=0;
        // Natural blinks are shorter; closing the eyes to rest is longer. Neither presses anything.
        if(held<blinkMillis||held>BLINK_MAX_MS)return;
        Log.i(TAG,"Blink select after "+held+" ms");
        if(mode==2){if(paused)setPaused(false);else scanSelect();return;}
        int target=blinkTarget;
        if(target<0||(paused&&target!=pauseButton.getId()))return;
        for(Button button:buttons)if(button.getId()==target&&button.isShown()){button.playSoundEffect(SoundEffectConstants.CLICK);blockedTarget=target;button.performClick();break;}
    }
    private void restartCameraIfWanted(){if(running&&cameraWanted&&cameraTracker==null&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();}
    private void startCamera(){
        stopCamera(false);long generation=++cameraGeneration;cameraRotation=displayRotation();
        // A gaze-only user cannot wake the screen, so it stays on while the camera runs.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        cameraText(t("cameraStarting","Starting camera…"));if(cameraPreview!=null)cameraPreview.setVisibility(View.VISIBLE);
        cameraTracker=new CameraTracker(this,requestedProvider,cameraRotation,new CameraTracker.Listener(){
            public void sample(double[] point,long captured,String provider,double blink){
                pendingCameraSample=new CameraSample(point,captured,provider,generation,blink);
                handler.removeCallbacks(deliverGaze);handler.post(deliverGaze);
            }
            public void preview(Bitmap frame){runOnUiThread(()->{
                if(generation!=cameraGeneration)return;latestPreview=frame;
                if(cameraPreview!=null)cameraPreview.setImageBitmap(frame);if(calibrationPreview!=null)calibrationPreview.setImageBitmap(frame);
            });}
            public void unavailable(String key,String fallback){runOnUiThread(()->{
                if(generation!=cameraGeneration)return;
                // The tracker already closed itself; forget it so the next press retries.
                stopCamera(false);notice(t(key,fallback));
                // A crashed camera service usually comes back; retry a few times so a gaze-only user is not stranded.
                long now=SystemClock.uptimeMillis();if(now-restartWindow>60000){restartWindow=now;restarts=0;}
                if(cameraWanted&&running&&(key.equals("cameraFailed")||key.equals("trackingLost"))&&restarts<3){restarts++;handler.postDelayed(restartCamera,2000);}
            });}
        });
    }
    /** Lifecycle stops keep the calibration; a user stop or provider change discards it. */
    private void stopCamera(boolean discardCalibration){
        cameraGeneration++;handler.removeCallbacks(deliverGaze);handler.removeCallbacks(restartCamera);pendingCameraSample=null;neutralSamples=0;if(cameraTracker!=null){cameraTracker.close();cameraTracker=null;}
        lastGazeTime=0;gazeState[3]=0;cancelCalibration();resetDwell();
        if(discardCalibration){calibration=null;preferences.edit().remove("calibration").apply();}
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        cameraText(t("cameraOff","Camera off"));latestPreview=null;if(cameraPreview!=null){cameraPreview.setImageBitmap(null);cameraPreview.setVisibility(View.GONE);}
    }
    private void restoreCalibration(){
        String stored=preferences.getString("calibration",null);if(stored==null)return;
        try{
            // The calibration's own text, then width, height and rotation; older formats are discarded.
            String[] parts=stored.split("\\|");String[] screen=parts[1].split(",");
            calibration=Calibration.decode(parts[0]);calibrationWidth=Integer.parseInt(screen[0]);calibrationHeight=Integer.parseInt(screen[1]);calibrationRotation=Integer.parseInt(screen[2]);
        }catch(RuntimeException error){Log.w(TAG,"Discarding stored calibration",error);calibration=null;preferences.edit().remove("calibration").apply();}
    }
    private void startCalibration(){
        if(cameraTracker==null){notice(t("needCamera","Start the camera before calibration."));return;}
        resetDwell();clearScan();calibrationSamples.clear();calibrationIndex=0;calibrationPositioning=true;calibrationTracked=0;calibrationMissing=0;calibrationTick=SystemClock.uptimeMillis();
        if(calibrationOverlay!=null)surface.removeView(calibrationOverlay);
        calibrationOverlay=new FrameLayout(this);calibrationOverlay.setBackgroundColor(BG);calibrationOverlay.setClickable(true);surface.addView(calibrationOverlay,new FrameLayout.LayoutParams(-1,-1));
        // Keep text and Cancel clear of the status bar and taskbar; targets use whole-surface coordinates like the gaze mapping.
        calibrationOverlay.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});calibrationOverlay.requestApplyInsets();
        calibrationText=label(t("facePosition","Center your face in the frame. Calibration starts when your face is found."),24);calibrationText.setGravity(android.view.Gravity.CENTER);
        FrameLayout.LayoutParams heading=new FrameLayout.LayoutParams(-1,-2,android.view.Gravity.TOP);heading.topMargin=dp(48);calibrationOverlay.addView(calibrationText,heading);
        calibrationStatus=label("",20);calibrationStatus.setGravity(android.view.Gravity.CENTER);calibrationStatus.setTextColor(SCAN);
        FrameLayout.LayoutParams statusPosition=new FrameLayout.LayoutParams(-1,-2,android.view.Gravity.BOTTOM);statusPosition.bottomMargin=dp(110);calibrationOverlay.addView(calibrationStatus,statusPosition);
        calibrationPreview=new ImageView(this);calibrationPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);calibrationPreview.setImageBitmap(latestPreview);
        calibrationOverlay.addView(calibrationPreview,new FrameLayout.LayoutParams(dp(360),dp(270),android.view.Gravity.CENTER));
        calibrationTarget=new CalibrationTarget();calibrationTarget.setVisibility(View.GONE);calibrationOverlay.addView(calibrationTarget,new FrameLayout.LayoutParams(dp(72),dp(72)));
        Button cancel=new Button(this);cancel.setText(t("cancelCalibration","Cancel calibration"));cancel.setAllCaps(false);cancel.setTextSize(19);cancel.setTextColor(INK);cancel.setBackground(background(SURFACE));cancel.setMinHeight(dp(64));cancel.setPadding(dp(24),0,dp(24),0);cancel.setOnClickListener(v->{cancelCalibration();notice(t("ready","Ready when you are."));});
        FrameLayout.LayoutParams cancelPosition=new FrameLayout.LayoutParams(-2,dp(64),android.view.Gravity.BOTTOM|android.view.Gravity.CENTER_HORIZONTAL);cancelPosition.bottomMargin=dp(30);calibrationOverlay.addView(cancel,cancelPosition);calibrationCancel=cancel;
    }
    private void cancelCalibration(){
        calibrationIndex=-1;calibrationPositioning=false;calibrationPoints.clear();calibrationPreview=null;calibrationTarget=null;calibrationCancel=null;
        if(calibrationOverlay!=null&&surface!=null)surface.removeView(calibrationOverlay);calibrationOverlay=null;
    }
    /** Runs every tick during calibration: time on a target only counts while the face is tracked. */
    private void calibrationStep(long now){
        long delta=Math.min(200,now-calibrationTick);calibrationTick=now;
        boolean tracked=now-lastGazeTime<=400;
        calibrationStatus.setText(tracked?"":t("cameraNoFace","No face detected"));
        if(calibrationPositioning){
            calibrationTracked=tracked?calibrationTracked+delta:0;
            if(calibrationTracked>=1000){calibrationPositioning=false;calibrationPreview.setVisibility(View.GONE);calibrationPreview=null;calibrationTarget.setVisibility(View.VISIBLE);layoutCalibration();nextCalibration();}
            return;
        }
        if(tracked)calibrationTracked+=delta;else calibrationMissing+=delta;
        calibrationTarget.setProgress(Math.min(1f,calibrationTracked/(float)TARGET_MS));
        if(calibrationMissing>FACE_LOST_MS){Log.i(TAG,"Calibration cancelled: face lost on target "+(calibrationIndex+1));cancelCalibration();notice(t("calibrationLost","Tracking lost. Please retry."));return;}
        if(calibrationTracked<TARGET_MS||calibrationPoints.size()<6)return;
        // The median ignores blinks and glances away better than the mean.
        int features=calibrationPoints.get(0).length;double[] median=new double[features],spread=new double[features];
        for(int axis=0;axis<features;axis++){
            double[] values=new double[calibrationPoints.size()];for(int i=0;i<values.length;i++)values[i]=calibrationPoints.get(i)[axis];
            Arrays.sort(values);median[axis]=values[values.length/2];spread[axis]=values[values.length*3/4]-values[values.length/4];
        }
        Log.i(TAG,String.format(java.util.Locale.ROOT,"Calibration target %d at (%.3f,%.3f): %d samples, median %s, IQR %s",calibrationIndex+1,calibrationPositions[calibrationIndex][0],calibrationPositions[calibrationIndex][1],calibrationPoints.size(),Arrays.toString(median),Arrays.toString(spread)));
        calibrationSamples.add(median);calibrationIndex++;nextCalibration();
    }
    /** A calibration dot with a ring that fills while the eyes are measured on it. */
    private final class CalibrationTarget extends View {
        private final Paint dot=new Paint(Paint.ANTI_ALIAS_FLAG),ring=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds=new RectF();
        private float progress;
        CalibrationTarget(){
            super(MainActivity.this);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            dot.setColor(SCAN);ring.setStyle(Paint.Style.STROKE);ring.setStrokeWidth(dp(6));ring.setStrokeCap(Paint.Cap.ROUND);ring.setColor(ACCENT);
        }
        void setProgress(float value){if(value!=progress){progress=value;invalidate();}}
        @Override protected void onDraw(Canvas canvas){
            float cx=getWidth()/2f,cy=getHeight()/2f,inset=dp(4);
            canvas.drawCircle(cx,cy,dp(10),dot);
            bounds.set(inset,inset,getWidth()-inset,getHeight()-inset);canvas.drawArc(bounds,-90,360*progress,false,ring);
        }
    }
    /**
     * Places the nine targets inside the system bars and moves the instructions and Cancel between the
     * target rows, so no target hides behind the taskbar or under text.
     */
    private void layoutCalibration(){
        float width=surface.getWidth(),height=surface.getHeight();
        float left=calibrationOverlay.getPaddingLeft(),top=calibrationOverlay.getPaddingTop(),safeWidth=width-left-calibrationOverlay.getPaddingRight(),safeHeight=height-top-calibrationOverlay.getPaddingBottom();
        calibrationPositions=new double[calibrationTargets.length][];
        for(int i=0;i<calibrationTargets.length;i++)calibrationPositions[i]=new double[]{(left+calibrationTargets[i][0]*safeWidth)/width,(top+calibrationTargets[i][1]*safeHeight)/height};
        FrameLayout.LayoutParams text=new FrameLayout.LayoutParams(-1,-2,android.view.Gravity.CENTER);calibrationText.setLayoutParams(text);calibrationText.setTranslationY(-.19f*safeHeight);
        FrameLayout.LayoutParams cancel=new FrameLayout.LayoutParams(-2,dp(64),android.view.Gravity.CENTER);calibrationCancel.setLayoutParams(cancel);calibrationCancel.setTranslationY(.19f*safeHeight);
        FrameLayout.LayoutParams status=new FrameLayout.LayoutParams(-1,-2,android.view.Gravity.CENTER);calibrationStatus.setLayoutParams(status);calibrationStatus.setTranslationY(-.19f*safeHeight+dp(48));
    }
    private void nextCalibration(){
        if(calibrationIndex==calibrationTargets.length){
            try{
                double[][] samples=calibrationSamples.toArray(new double[0][]);
                Calibration fitted=samples[0].length==7?Calibration.fit(samples,calibrationPositions,GAZE_X,GAZE_Y):Calibration.fit(samples,calibrationPositions);
                double[] axes=fitted.axisErrors(samples,calibrationPositions);
                Log.i(TAG,String.format(java.util.Locale.ROOT,"Calibration fit: RMS error %.3f of the screen (x %.3f, y %.3f; ceiling %.2f)",fitted.error(),axes[0],axes[1],CALIBRATION_CEILING));
                if(!(fitted.error()<=CALIBRATION_CEILING))throw new IllegalArgumentException("Calibration error too large");
                calibration=fitted;calibrationWidth=surface.getWidth();calibrationHeight=surface.getHeight();calibrationRotation=displayRotation();gazeState[3]=0;
                preferences.edit().putString("calibration",calibration.encode()+"|"+calibrationWidth+","+calibrationHeight+","+calibrationRotation).apply();
                mode=3;save();modeButton.setText(modeName());updateProgressVisibility();gazeHinted=false;
                notice(t("calibrationQuality","Calibration saved. Typical error: {error}% of the screen.").replace("{error}",String.valueOf(Math.round(fitted.error()*100))));
            }
            catch(IllegalArgumentException error){
                Log.i(TAG,"Calibration rejected: "+error.getMessage());calibration=null;
                notice(String.valueOf(error.getMessage()).contains("movement")?t("calibrationMovement","Not enough eye movement between targets. Please retry."):t("calibrationError","Calibration was not accurate enough. Please retry."));
            }
            cancelCalibration();return;
        }
        calibrationText.setText(t("lookTarget","Look at the target and hold still")+" · "+(calibrationIndex+1)+"/"+calibrationTargets.length);
        calibrationTarget.setTranslationX((float)(surface.getWidth()*calibrationPositions[calibrationIndex][0])-dp(36));
        calibrationTarget.setTranslationY((float)(surface.getHeight()*calibrationPositions[calibrationIndex][1])-dp(36));
        calibrationTarget.setProgress(0);calibrationPoints.clear();calibrationTracked=0;calibrationMissing=0;
    }
    private String modeName() { return new String[]{t("manual","Touch / keyboard"),t("dwell","Pointer dwell"),t("scan","Single switch"),t("gaze","Eye tracking")}[mode]; }
    private void append(String text) {
        int start=Math.max(0,message.getSelectionStart()), end=Math.max(start,message.getSelectionEnd());
        if(message.length()-end+start+text.length()>2000){notice(t("messageFull","Message is full."));return;}
        message.getText().replace(start,end,text);
    }
    private void backspace() {
        int start=Math.max(0,message.getSelectionStart()), end=Math.max(start,message.getSelectionEnd());
        if(start==end && start>0){
            String text=message.getText().toString();int last=text.codePointBefore(start),type=Character.getType(last);
            // Combining signs and joiners are typed as separate keys, so Delete removes them one at a time.
            boolean sign=(type==Character.NON_SPACING_MARK||type==Character.COMBINING_SPACING_MARK||type==Character.ENCLOSING_MARK||last==0x200D||last==0x200C)&&!(last>=0xFE00&&last<=0xFE0F);
            if(sign)start-=Character.charCount(last);
            else{BreakIterator iterator=BreakIterator.getCharacterInstance(pack.locale);iterator.setText(text);start=iterator.preceding(start);}
        }
        if(start>=0)message.getText().delete(start,end);
    }
    private void refreshVoices() {
        voices.clear(); if(!speechReady || tts==null || pack==null)return;
        if(tts.getVoices()!=null)for(Voice voice:tts.getVoices())if(!voice.isNetworkConnectionRequired() && (voice.getFeatures()==null || !voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) && voice.getLocale().getLanguage().equals(pack.locale.getLanguage()))voices.add(voice);
        voices.sort(Comparator.comparingInt(Voice::getQuality).reversed().thenComparing(Voice::getName)); voiceIndex=0;
        String preferred=preferences.getString("voice",""); for(int i=0;i<voices.size();i++)if(voices.get(i).getName().equals(preferred))voiceIndex=i;
        if(voiceValue!=null)voiceValue.setText(t("voice","Voice") + ": " + (voices.isEmpty() ? "—" : voiceLabel(voiceIndex)));
    }
    /** Engine voice IDs mean nothing to users; show the accent and position instead. */
    private String voiceLabel(int index){return voices.get(index).getLocale().getDisplayName(pack.locale)+" · "+(index+1)+"/"+voices.size();}
    private void nextVoice(){
        refreshVoices();
        if(voices.isEmpty()){notice(t("installVoice","Install a voice."));return;}
        voiceIndex=(voiceIndex+1)%voices.size(); preferences.edit().putString("voice",voices.get(voiceIndex).getName()).apply();
        voiceValue.setText(t("voice","Voice") + ": " + voiceLabel(voiceIndex)); notice(voiceValue.getText().toString());
        // Preview with the language name: a phrase such as "Yes" could be mistaken for an answer.
        stopSpeech(); if(tts.setVoice(voices.get(voiceIndex))==TextToSpeech.SUCCESS){tts.setSpeechRate(speechRate);tts.speak(pack.name,TextToSpeech.QUEUE_FLUSH,null,currentUtterance="zekals-"+(++utteranceSequence));}
    }
    private void openVoiceSettings(){
        for(String action:new String[]{TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA,"com.android.settings.TTS_SETTINGS"}){
            try{startActivity(new Intent(action));return;}catch(ActivityNotFoundException error){Log.i(TAG,"No activity for "+action);}
        }
        notice(t("installVoice","Install an offline voice for this language in device settings."));
    }
    private void stopSpeech(){currentUtterance="";utteranceSequence++;if(tts!=null)tts.stop();}
    private void speak() {
        if(message.getText().toString().trim().isEmpty()){notice(t("emptyMessage","Write a message first."));return;}
        refreshVoices(); if(!speechReady || voices.isEmpty()){notice(t("installVoice","Install a local voice for this language."));return;}
        stopSpeech(); if(tts.setVoice(voices.get(voiceIndex))!=TextToSpeech.SUCCESS){notice(t("installVoice","Install a local voice for this language."));return;} tts.setSpeechRate(speechRate);
        if(tts.speak(message.getText().toString(),TextToSpeech.QUEUE_FLUSH,null,currentUtterance="zekals-"+(++utteranceSequence)) == TextToSpeech.ERROR){notice(t("speechFailed","Speech failed."));return;}
        // Speech plays on the media stream; a muted stream silences the user's voice.
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        notice(audio!=null&&audio.getStreamVolume(AudioManager.STREAM_MUSIC)==0?t("mediaMuted","Media volume is muted, so speech cannot be heard."):t("speaking","Speaking…"));
    }
    private void highlight(Button view, boolean active) { if(view==null)return; boolean primary=Boolean.TRUE.equals(view.getTag()); view.setBackground(background(active?SCAN:primary?ACCENT:contrast?Color.BLACK:SURFACE));view.setTextColor(active||primary?BG:INK); }
    private void resetDwell(){dwellState[0]=-1;dwellState[1]=0;dwellState[2]=0; highlight(selected,false);}
    private void setPaused(boolean value){paused=value;resetDwell();clearScan();pauseButton.setText(paused?t("resume","Resume"):t("pause","Pause"));notice(paused?t("paused","Paused"):t("ready","Ready"));}
    private List<Button> shown(List<Button> group){List<Button> result=new ArrayList<>();for(Button b:group)if(b.isShown()&&b.isEnabled())result.add(b);return result;}
    private void clearScan(){if(scanGroup!=null)for(Button b:scanGroup)highlight(b,false);if(mode==2)highlight(selected,false);}
    private void reveal(Button view){
        View row=(View)view.getParent();row.requestRectangleOnScreen(new Rect(0,0,row.getWidth(),row.getHeight()),true);
        view.requestFocus();view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }
    /** Row/column scanning: rows are highlighted first, then the buttons inside the chosen row. */
    private void scanStep(){
        clearScan();
        if(scanItems==null){
            List<List<Button>> visible=new ArrayList<>();for(List<Button> group:groups)if(!shown(group).isEmpty())visible.add(group);
            if(visible.isEmpty())return;
            scanIndex=(scanIndex+1)%visible.size();scanGroup=visible.get(scanIndex);selected=null;
            List<Button> items=shown(scanGroup);for(Button b:items)highlight(b,true);reveal(items.get(0));
        }else{
            List<Button> items=shown(scanItems);scanIndex++;
            // One silent pass through a row returns to row scanning on the same row.
            if(scanIndex>=items.size()){scanItems=null;scanIndex=scanEntry-1;scanStep();return;}
            selected=items.get(scanIndex);highlight(selected,true);reveal(selected);
        }
    }
    private void scanSelect(){
        if(scanItems==null){
            if(scanGroup==null)return;List<Button> items=shown(scanGroup);if(items.isEmpty())return;
            if(items.size()==1){activate(items.get(0));return;}
            clearScan();scanItems=scanGroup;scanEntry=scanIndex;scanIndex=-1;lastScan=0;
        }else if(selected!=null&&selected.isShown())activate(selected);
    }
    private void activate(Button view){
        int entry=scanItems==null?scanIndex:scanEntry,before=renders;clearScan();view.performClick();
        if(renders!=before)return;
        // Stay on the same row so repeated letters are quick.
        scanItems=null;scanGroup=null;selected=null;scanIndex=entry-1;lastScan=0;
    }
    private final Runnable tick = new Runnable(){ public void run(){
        if(!running)return;
        long now=SystemClock.uptimeMillis();
        if(calibrationIndex>=0)calibrationStep(now);
        if(mode==2 && !paused && calibrationIndex<0 && now-lastScan>=scanMillis){scanStep();lastScan=now;}
        float[] looked=cameraTracker!=null?gazePoint(now):null;
        Button lock=updateLock(looked,now);
        if((mode==1 || mode==3) && calibrationIndex<0){
            // Gaze selects the locked button, so jitter inside or just outside it cannot reset the dwell.
            int target=mode==1?pointerTarget:lock==null?-1:lock.getId();
            if(target!=blockedTarget)blockedTarget=-1;
            if(target==blockedTarget || (paused && target!=pauseButton.getId()))target=-1;
            Button next=null;for(Button b:buttons)if(b.getId()==target&&b.isShown()){next=b;break;}
            if(next!=selected){highlight(selected,false);selected=next;highlight(selected,true);}
            if(NativeCore.dwell(dwellState,target,now,dwellMillis)){
                for(Button b:buttons)if(b.getId()==target&&b.isShown()){blockedTarget=target;b.performClick();break;}
            }
        }
        int progress=dwellState[0]<0?0:(int)Math.min(100,Math.max(0,(now-dwellState[1])*100/dwellMillis));
        if(selectionProgress!=null)selectionProgress.setProgress(progress);
        if(pointer!=null){if(looked==null)pointer.hide();else{float[] aim=lock!=null?centre(lock):looked;pointer.aim(aim[0],aim[1],lock!=null,mode==3?progress/100f:0);}}
        handler.postDelayed(this,mode==0&&calibrationIndex<0&&cameraTracker==null?250:50);
    }};
    private int displayRotation(){return getWindowManager().getDefaultDisplay().getRotation();}
    /** Where the user is looking, in surface pixels, or null when uncalibrated or not tracking. */
    private float[] gazePoint(long now){
        if(gazeState[3]==0||now-lastGazeTime>500||calibrationIndex>=0)return null;
        if(calibration==null&&!gazeHinted){notice(t("gazeHint","The pointer follows your head. Calibrate in Settings so it follows your eyes too."));gazeHinted=true;}
        // Calibration maps this viewport from this camera position; keep it for when they return.
        if(calibration!=null&&(surface.getWidth()!=calibrationWidth||surface.getHeight()!=calibrationHeight||displayRotation()!=calibrationRotation)){resetDwell();if(!sizeWarned)notice(t("recalibrate","The screen size changed. Calibrate again."));sizeWarned=true;return null;}
        sizeWarned=false;
        // gazeState already holds the calibrated, smoothed screen fraction.
        return new float[]{(float)(gazeState[0]*surface.getWidth()),(float)(gazeState[1]*surface.getHeight())};
    }
    /**
     * Locks onto the button the pointer settles on (or just beside) for LOCK_DELAY_MS, and holds it until
     * the gaze has been clearly outside it, past a margin, for UNLOCK_MS.
     */
    private Button updateLock(float[] looked,long now){
        if(looked==null){lockedButton=null;lockCandidate=null;return null;}
        if(lockedButton!=null&&(!lockedButton.isShown()||!near(lockedButton,looked))){
            if(lockLeftSince==0)lockLeftSince=now;else if(now-lockLeftSince>=UNLOCK_MS)lockedButton=null;
        }else lockLeftSince=0;
        if(lockedButton!=null)return lockedButton;
        Button over=nearestButton(looked);
        if(over!=lockCandidate){lockCandidate=over;lockCandidateSince=now;}
        else if(over!=null&&now-lockCandidateSince>=LOCK_DELAY_MS){lockedButton=over;lockLeftSince=0;}
        return lockedButton;
    }
    private final Rect lockBounds=new Rect();
    private final int[] surfaceOrigin=new int[2];
    /** The button under the point, or the nearest one within a small snap radius (gaps between keys). */
    private Button nearestButton(float[] point){
        surface.getLocationOnScreen(surfaceOrigin);float x=point[0]+surfaceOrigin[0],y=point[1]+surfaceOrigin[1];
        Button best=null;float bestDistance=dp(28);
        for(Button button:buttons){
            if(!button.isShown()||!button.getGlobalVisibleRect(lockBounds))continue;
            float dx=Math.max(0,Math.max(lockBounds.left-x,x-lockBounds.right)),dy=Math.max(0,Math.max(lockBounds.top-y,y-lockBounds.bottom));
            float distance=(float)Math.hypot(dx,dy);if(distance==0)return button;if(distance<bestDistance){bestDistance=distance;best=button;}
        }
        return best;
    }
    /** Inside the button grown by a quarter of its smaller side (at least 16 dp): leaving that unlocks. */
    private boolean near(Button button,float[] point){
        if(!button.getGlobalVisibleRect(lockBounds))return false;surface.getLocationOnScreen(surfaceOrigin);
        int margin=Math.max(dp(16),Math.min(lockBounds.width(),lockBounds.height())/4);lockBounds.inset(-margin,-margin);
        return lockBounds.contains((int)(point[0]+surfaceOrigin[0]),(int)(point[1]+surfaceOrigin[1]));
    }
    private float[] centre(Button button){
        button.getGlobalVisibleRect(lockBounds);surface.getLocationOnScreen(surfaceOrigin);
        return new float[]{lockBounds.exactCenterX()-surfaceOrigin[0],lockBounds.exactCenterY()-surfaceOrigin[1]};
    }
    private int gazeTarget(float[] gaze){
        if(gaze==null)return -1;
        int[] origin=new int[2];surface.getLocationOnScreen(origin);
        int x=origin[0]+(int)gaze[0],y=origin[1]+(int)gaze[1];
        if(gazeReentryBlocked!=null){if(gazeReentryBlocked.contains(x,y))return -1;gazeReentryBlocked=null;}
        Rect bounds=new Rect();for(Button button:buttons)if(button.isShown()&&button.getGlobalVisibleRect(bounds)&&bounds.contains(x,y))return button.getId();
        return -1;
    }
    /**
     * Switch keys are handled before the view hierarchy: scanning gives buttons keyboard focus,
     * and a focused button would otherwise consume Space/Enter as a click and swallow Escape.
     */
    @Override public boolean dispatchKeyEvent(KeyEvent event){ return switchKey(event) || super.dispatchKeyEvent(event); }
    private boolean switchKey(KeyEvent event){
        int key=event.getKeyCode();
        boolean escape=key==KeyEvent.KEYCODE_ESCAPE;
        boolean select=mode==2&&(key==KeyEvent.KEYCODE_SPACE||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER);
        if(pack==null||!(escape||select))return false;
        if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
            if(escape){cancelCalibration();setPaused(true);stopSpeech();}
            else if(paused)setPaused(false);
            else scanSelect();
        }
        return true;
    }
    /**
     * In touch mode Android consumes the first Space/Enter just to leave touch mode, before the
     * activity sees it. Pre-IME dispatch runs earlier, so the root stays focusable to receive it.
     */
    private final class SwitchSurface extends FrameLayout {
        SwitchSurface(){
            super(MainActivity.this);setFocusable(true);setFocusableInTouchMode(true);setDefaultFocusHighlightEnabled(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override public boolean dispatchKeyEventPreIme(KeyEvent event){ return switchKey(event) || super.dispatchKeyEventPreIme(event); }
    }
    /**
     * The eye-tracking pointer. It moves as a damped spring toward where the user looks (or the centre of
     * the locked button), so it has momentum: it accelerates, glides and settles instead of jumping.
     */
    private final class GazePointer extends View implements Choreographer.FrameCallback {
        private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG), ring=new Paint(Paint.ANTI_ALIAS_FLAG), arc=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds=new RectF();
        private float progress,x,y,vx,vy,aimX,aimY;
        private boolean placed,animating,locked;
        private long lastFrame;
        GazePointer(){
            super(MainActivity.this);setVisibility(View.GONE);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            fill.setColor(Color.argb(90,255,221,133));
            ring.setStyle(Paint.Style.STROKE);ring.setStrokeWidth(dp(3));ring.setColor(SCAN);
            arc.setStyle(Paint.Style.STROKE);arc.setStrokeWidth(dp(5));arc.setStrokeCap(Paint.Cap.ROUND);arc.setColor(ACCENT);
        }
        void aim(float targetX,float targetY,boolean lock,float dwell){
            aimX=targetX;aimY=targetY;
            if(!placed){x=targetX;y=targetY;vx=vy=0;placed=true;place();}
            if(getVisibility()!=View.VISIBLE)setVisibility(View.VISIBLE);
            if(lock!=locked||dwell!=progress){locked=lock;progress=dwell;ring.setColor(lock?ACCENT:SCAN);fill.setColor(lock?Color.argb(120,188,235,207):Color.argb(90,255,221,133));invalidate();}
            if(!animating){animating=true;lastFrame=0;Choreographer.getInstance().postFrameCallback(this);}
        }
        void hide(){
            if(getVisibility()!=View.GONE)setVisibility(View.GONE);
            placed=false;animating=false;Choreographer.getInstance().removeFrameCallback(this);
        }
        @Override public void doFrame(long nanos){
            if(!animating)return;
            float dt=lastFrame==0?.016f:Math.min(.05f,(nanos-lastFrame)/1e9f);lastFrame=nanos;
            // Slightly under-damped spring; stiffer when locked so it snaps to the button centre.
            float stiffness=locked?260:140,damping=2*.8f*(float)Math.sqrt(stiffness);
            vx+=(stiffness*(aimX-x)-damping*vx)*dt;vy+=(stiffness*(aimY-y)-damping*vy)*dt;x+=vx*dt;y+=vy*dt;
            place();Choreographer.getInstance().postFrameCallback(this);
        }
        private void place(){setTranslationX(x-getWidth()/2f);setTranslationY(y-getHeight()/2f);}
        @Override protected void onDetachedFromWindow(){animating=false;Choreographer.getInstance().removeFrameCallback(this);super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas canvas){
            float inset=dp(5),radius=getWidth()/2f-inset,cx=getWidth()/2f,cy=getHeight()/2f;
            canvas.drawCircle(cx,cy,radius,fill);canvas.drawCircle(cx,cy,radius,ring);canvas.drawCircle(cx,cy,dp(3),ring);
            if(progress>0){bounds.set(inset,inset,getWidth()-inset,getHeight()-inset);canvas.drawArc(bounds,-90,360*progress,false,arc);}
        }
    }
    /** Flipping a landscape device turns the camera image upside down without recreating the activity. */
    private final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){
        @Override public void onDisplayAdded(int id){}
        @Override public void onDisplayRemoved(int id){}
        @Override public void onDisplayChanged(int id){if(cameraTracker!=null&&displayRotation()!=cameraRotation)startCamera();}
    };
    @Override protected void onResume(){
        super.onResume();
        if(pack==null)return;
        running=true;handler.post(tick);registerDebugGaze();
        ((DisplayManager)getSystemService(DISPLAY_SERVICE)).registerDisplayListener(displayListener,handler);
        if(cameraWanted&&cameraTracker==null&&!requestingCamera&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();
    }
    @Override protected void onPause(){
        running=false;handler.removeCallbacks(tick);if(pointer!=null)pointer.hide();lockedButton=null;lockCandidate=null;
        if(pack!=null)((DisplayManager)getSystemService(DISPLAY_SERVICE)).unregisterDisplayListener(displayListener);
        // Rotation and our own permission prompt are not the user leaving the app.
        if(pack!=null){if(!requestingCamera&&!isChangingConfigurations())setPaused(true);stopCamera(false);}
        stopSpeech();super.onPause();
    }
    /**
     * Debug builds only: lets a developer drive the pointer, dwell and blink over adb without a face, e.g.
     * adb shell am broadcast -a org.zektopic.zekals.DEBUG_GAZE --ef x 0.3 --ef y 0.6 [--el hold 2000] [--el blink 700]
     */
    private android.content.BroadcastReceiver debugGaze;
    @android.annotation.SuppressLint("InlinedApi") // RECEIVER_EXPORTED is a constant older versions ignore.
    private void registerDebugGaze(){
        if(!BuildConfig.DEBUG||debugGaze!=null)return;
        debugGaze=new android.content.BroadcastReceiver(){@Override public void onReceive(android.content.Context context,Intent intent){
            long now=SystemClock.uptimeMillis();
            if(intent.hasExtra("x")){lastGazeTime=now;NativeCore.smooth(gazeState,intent.getFloatExtra("x",.5f),intent.getFloatExtra("y",.5f),now,1,true);}
            long hold=intent.getLongExtra("hold",0),end=now+hold;
            if(hold>0)handler.post(new Runnable(){public void run(){long time=SystemClock.uptimeMillis();if(time>end)return;lastGazeTime=time;handler.postDelayed(this,100);}});
            long held=intent.getLongExtra("blink",0);if(held>0){blink(true,now-held);blink(false,now);}
            Log.i(TAG,"Debug gaze "+intent.getExtras());
        }};
        android.content.IntentFilter filter=new android.content.IntentFilter("org.zektopic.zekals.DEBUG_GAZE");
        // Exported so adb can reach it; this receiver only exists in debug builds.
        registerReceiver(debugGaze,filter,android.content.Context.RECEIVER_EXPORTED);
    }
    @Override protected void onDestroy(){if(debugGaze!=null)unregisterReceiver(debugGaze);if(tts!=null){stopSpeech();tts.shutdown();}super.onDestroy();}
}
