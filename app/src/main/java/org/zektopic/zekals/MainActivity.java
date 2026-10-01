package org.zektopic.zekals;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.icu.text.BreakIterator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Native views work with TalkBack, Android Switch Access, touch and keyboard. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(12,23,36), SURFACE = Color.rgb(20,35,53), INK = Color.rgb(245,248,252), ACCENT = Color.rgb(188,235,207);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Button> buttons = new ArrayList<>();
    private final List<LanguagePack> packs = new ArrayList<>();
    private final ArrayDeque<String> undo = new ArrayDeque<>();
    private final long[] dwellState = {-1,0,0};
    private SharedPreferences preferences;
    private LanguagePack pack;
    private EditText message;
    private TextView status, cameraStatus;
    private Rect gazeReentryBlocked;
    private String currentUtterance = "";
    private long utteranceSequence;
    private LinearLayout content, settingsPanel;
    private ScrollView scroll;
    private Button pauseButton, modeButton, selected;
    private TextToSpeech tts;
    private boolean speechReady, paused, large, contrast, changing, running;
    private String lastMessage = "";
    private int languageIndex, keyboardPage, mode, pointerTarget = -1, blockedTarget = -1, scanIndex = -1;
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
        final double[] point; final long captured, generation; final String provider;
        CameraSample(double[] p,long c,String name,long g){point=p;captured=c;provider=name;generation=g;}
    }
    private volatile CameraSample pendingCameraSample;
    private final Runnable deliverGaze = this::deliverGazeSample;
    private final double[] gazeState = new double[4];
    private Calibration calibration;
    private int calibrationWidth, calibrationHeight;
    private int calibrationIndex = -1;
    private long calibrationStarted;
    private final List<double[]> calibrationPoints = new ArrayList<>();
    private final List<double[]> calibrationSamples = new ArrayList<>();
    private final double[][] calibrationTargets = {{.15,.18},{.85,.18},{.5,.5},{.15,.82},{.85,.82}};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("preferences", MODE_PRIVATE);
        large = preferences.getBoolean("large", false); contrast = preferences.getBoolean("contrast", false);
        dwellMillis = preferences.getLong("dwell", 1200); scanMillis = preferences.getLong("scan", 1400);
        speechRate = preferences.getFloat("rate", .9f);
        Object previous = getLastNonConfigurationInstance(); if (previous instanceof String) lastMessage = (String) previous;
        try {
            String[] files = getAssets().list("languages");
            if (files == null) throw new IllegalStateException("Missing languages");
            java.util.Arrays.sort(files);
            for (String file : files) if (file.endsWith(".json")) packs.add(new LanguagePack(getAssets(), file));
            String code = preferences.getString("language", "en");
            for (int i = 0; i < packs.size(); i++) if (packs.get(i).code.equals(code)) languageIndex = i;
            pack = packs.get(languageIndex); render();
        } catch (Exception error) {
            pack = null; TextView failure = new TextView(this); failure.setText("Language assets could not be loaded. Please reinstall the app."); setContentView(failure); return;
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
    @Override public Object onRetainNonConfigurationInstance() { return lastMessage; }
    private String t(String key, String fallback) { return pack == null ? fallback : pack.text(key, fallback); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void save() {
        preferences.edit().putString("language", pack.code).putBoolean("large", large).putBoolean("contrast", contrast)
            .putLong("dwell", dwellMillis).putLong("scan", scanMillis).putFloat("rate", speechRate).apply();
    }
    private void notice(String text) { if (status != null && !status.getText().toString().equals(text)) status.setText(text); }
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
    private void row(LinearLayout parent, Button... entries) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        for (Button entry : entries) { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1); params.setMargins(dp(4),dp(4),dp(4),dp(4)); row.addView(entry, params); }
        parent.addView(row);
    }
    private void grid(LinearLayout parent, List<Button> entries, int columns) {
        for (int i=0; i<entries.size(); i+=columns) row(parent, entries.subList(i, Math.min(entries.size(), i+columns)).toArray(new Button[0]));
    }
    private void render() {
        resetDwell(); buttons.clear(); selected = null; pointerTarget = -1; scanIndex = -1;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(8),dp(6),dp(8),dp(4)); root.setBackgroundColor(contrast ? Color.BLACK : BG);root.setLayoutDirection(pack.rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(dp(8)+insets.getSystemWindowInsetLeft(), dp(6)+insets.getSystemWindowInsetTop(), dp(8)+insets.getSystemWindowInsetRight(), dp(4)+insets.getSystemWindowInsetBottom()); return insets; });
        TextView title = label("zekALS · " + pack.name, 25); if(android.os.Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true); root.addView(title);
        cameraStatus=label(t("pointerReady","Camera unavailable"),16);if(BuildConfig.CAMERA_AVAILABLE)root.addView(cameraStatus);
        pauseButton = button(paused ? t("resume","Resume") : t("pause","Pause"), () -> setPaused(!paused));
        row(root, pauseButton, button(t("scrollUp","Scroll up"), () -> { scroll.smoothScrollBy(0,-scroll.getHeight()/2); resetDwell(); }), button(t("scrollDown","Scroll down"), () -> { scroll.smoothScrollBy(0,scroll.getHeight()/2); resetDwell(); }));
        scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content);
        TextView heading = label(t("messageTitle","What would you like to say?"),24); if(android.os.Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true); content.addView(heading);
        message = new EditText(this); message.setId(View.generateViewId()); message.setText(lastMessage); message.setSelection(message.length());
        message.setSaveEnabled(false); message.setTextColor(INK); message.setHintTextColor(Color.LTGRAY); message.setTextSize(large ? 28 : 24);
        message.setHint(t("textPlaceholder","Write a message")); message.setContentDescription(t("messageLabel","Your message")); message.setMinLines(3);
        message.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        message.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)}); message.setBackground(background(contrast ? Color.BLACK : SURFACE));
        message.setPadding(dp(14),dp(14),dp(14),dp(14)); message.setTextLocale(pack.locale); content.addView(message);
        message.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {}
            public void afterTextChanged(Editable text) {
                if (!changing && !text.toString().equals(lastMessage)) { if (undo.size() >= 30) undo.removeFirst(); undo.addLast(lastMessage); }
                lastMessage = text.toString();
            }
        });
        Button speak = button(t("speak","Speak"), this::speak); speak.setBackground(background(ACCENT)); speak.setTextColor(BG); speak.setTag(true);
        row(content, speak, button(t("stop","Stop"), () -> { stopSpeech(); notice(t("stopped","Speech stopped.")); }));
        row(content, button(t("undo","Undo"), () -> { if (!undo.isEmpty()) { changing=true; message.setText(undo.removeLast()); message.setSelection(message.length()); changing=false; } }), button(t("clear","Clear"), () -> message.setText("")));
        try {
            content.addView(label(t("phrases","Everyday phrases"),22));
            List<Button> phraseButtons = new ArrayList<>();
            for (String phrase : pack.phrases()) phraseButtons.add(button(phrase, () -> append((message.length()>0 ? " " : "") + phrase)));
            grid(content, phraseButtons, getResources().getConfiguration().screenWidthDp >= 700 ? 4 : 2);
            List<List<String>> pages = pack.pages(); keyboardPage %= pages.size();
            row(content, button(t("moreKeys","More characters") + " " + (keyboardPage+1) + "/" + pages.size(), () -> { keyboardPage++; render(); }),
                button(pack.name + " ›", () -> { stopSpeech(); languageIndex=(languageIndex+1)%packs.size(); pack=packs.get(languageIndex); keyboardPage=0; save(); refreshVoices(); render(); }));
            List<Button> keys = new ArrayList<>();
            for (String key : pages.get(keyboardPage)) keys.add(button(key.equals("\u200d") ? t("joinLetters","Join letters") : key.matches("\\p{M}+") ? "◌"+key : key, () -> append(key)));
            grid(content, keys, getResources().getConfiguration().screenWidthDp >= 700 ? 10 : 5);
        } catch (Exception error) { notice("Keyboard pack is invalid."); }
        row(content, button(t("space","Space"), () -> append(" ")), button(t("backspace","Delete"), this::backspace));
        settingsPanel = new LinearLayout(this); settingsPanel.setOrientation(LinearLayout.VERTICAL); settingsPanel.setVisibility(View.GONE);
        content.addView(button(t("settings","Settings"), () -> { settingsPanel.setVisibility(settingsPanel.getVisibility()==View.VISIBLE ? View.GONE : View.VISIBLE); resetDwell(); }));
        content.addView(settingsPanel);
        modeButton = button(modeName(), () -> { mode=(mode+1)%(BuildConfig.CAMERA_AVAILABLE?4:3); lastScan=0; resetDwell(); modeButton.setText(modeName()); }); row(settingsPanel,modeButton);
        row(settingsPanel,button(t("dwellTime","Dwell time") + " −", () -> { dwellMillis=Math.max(500,dwellMillis-100); save(); notice(dwellMillis + " ms"); }),button(t("dwellTime","Dwell time") + " +", () -> { dwellMillis=Math.min(3000,dwellMillis+100); save(); notice(dwellMillis + " ms"); }));
        row(settingsPanel,button(t("scanTime","Scan interval") + " −", () -> { scanMillis=Math.max(600,scanMillis-200); save(); notice(scanMillis + " ms"); }),button(t("scanTime","Scan interval") + " +", () -> { scanMillis=Math.min(4000,scanMillis+200); save(); notice(scanMillis + " ms"); }));
        row(settingsPanel,button(t("textSize","Text size"), () -> { large=!large; save(); render(); }), button(t("contrast","Contrast"), () -> { contrast=!contrast; save(); render(); }));
        row(settingsPanel,button(t("speechSpeed","Speech speed") + " −", () -> { speechRate=Math.max(.5f,speechRate-.1f); save(); notice(String.format(pack.locale,"%.1f×",speechRate)); }),button(t("speechSpeed","Speech speed") + " +", () -> { speechRate=Math.min(1.5f,speechRate+.1f); save(); notice(String.format(pack.locale,"%.1f×",speechRate)); }));
        row(settingsPanel,button(t("voice","Voice") + " ›", () -> { if(!voices.isEmpty()){voiceIndex=(voiceIndex+1)%voices.size(); preferences.edit().putString("voice",voices.get(voiceIndex).getName()).apply(); notice(voices.get(voiceIndex).getName());}else notice(t("installVoice","Install a voice.")); }));
        addCameraControls(settingsPanel);
        settingsPanel.addView(label(t("scanHelp","Press Space to choose the highlighted button. Escape pauses."),18));
        status=label(t("ready","Ready when you are."),18); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); root.addView(status);
        selectionProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        selectionProgress.setMax(100); selectionProgress.setContentDescription(t("dwellTime","Selection progress")); root.addView(selectionProgress);
        surface = new FrameLayout(this); surface.addView(root); setContentView(surface);
        // Calibration maps the viewport, so a keyboard-page rebuild preserves it.
        calibrationIndex = -1;
    }
    private void addCameraControls(LinearLayout panel) {
        if(!BuildConfig.CAMERA_AVAILABLE)return;
        row(panel,button(t("cameraControl","Camera: start / stop"),()->{
            if(cameraTracker!=null){stopCamera();notice(t("pointerReady","Camera stopped"));}
            else if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},42);
            else startCamera();
        }));
        row(panel,button(t("inference","Inference")+": " + requestedProvider,()->{
            requestedProvider=requestedProvider.equals("CPU")?"GPU":requestedProvider.equals("GPU")?"NNAPI":"CPU";
            stopCamera();render();notice("Inference request: "+requestedProvider+". Start the camera to apply.");
        }),button(t("calibrate","Calibrate"),this::startCalibration));
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==42&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)startCamera();
        else if(request==42)notice("Camera permission denied. Other input remains available.");
    }
    private void deliverGazeSample() {
        CameraSample sample=pendingCameraSample;if(sample==null||sample.generation!=cameraGeneration||!running)return;
        long now=SystemClock.uptimeMillis();boolean valid=sample.point!=null&&now-sample.captured<=500;
        NativeCore.smooth(gazeState,valid?sample.point[0]:0,valid?sample.point[1]:0,sample.captured,70,valid);
        lastGazeTime=valid?sample.captured:0;
        if(!valid){if(mode==3)resetDwell();if(cameraStatus!=null)cameraStatus.setText(t("pointerReady","Tracking unavailable"));return;}
        if(calibrationIndex>=0&&now-calibrationStarted>1500)calibrationPoints.add(new double[]{gazeState[0],gazeState[1]});
        if(cameraStatus!=null&&!cameraStatus.getText().toString().equals(sample.provider))cameraStatus.setText(sample.provider);
    }
    private void startCamera(){
        stopCamera();long generation=++cameraGeneration;
        cameraTracker=new CameraTracker(this,requestedProvider,getWindowManager().getDefaultDisplay().getRotation(),new CameraTracker.Listener(){
            public void sample(double[] point,long captured,String provider){
                pendingCameraSample=new CameraSample(point,captured,provider,generation);
                handler.removeCallbacks(deliverGaze);handler.post(deliverGaze);
            }
            public void unavailable(String reason){runOnUiThread(()->{
                if(generation!=cameraGeneration)return;lastGazeTime=0;gazeState[3]=0;resetDwell();notice(reason);
            });}
        });
    }
    private void stopCamera(){
        cameraGeneration++;handler.removeCallbacks(deliverGaze);pendingCameraSample=null;if(cameraTracker!=null){cameraTracker.close();cameraTracker=null;}
        lastGazeTime=0;gazeState[3]=0;calibration=null;cancelCalibration();resetDwell();
    }
    private void startCalibration(){
        if(SystemClock.uptimeMillis()-lastGazeTime>500||gazeState[3]==0){notice(t("needCamera","Connect the camera first."));return;}
        resetDwell();calibrationSamples.clear();calibrationIndex=0;nextCalibration();
    }
    private void cancelCalibration(){
        calibrationIndex=-1;calibrationPoints.clear();
        if(calibrationOverlay!=null&&surface!=null)surface.removeView(calibrationOverlay);calibrationOverlay=null;
    }
    private void nextCalibration(){
        if(calibrationIndex==5){
            try{calibration=Calibration.fit(calibrationSamples.toArray(new double[0][]),calibrationTargets);calibrationWidth=surface.getWidth();calibrationHeight=surface.getHeight();mode=3;modeButton.setText(modeName());notice(t("calibrated","Calibration complete."));}
            catch(IllegalArgumentException error){calibration=null;notice(error.getMessage());}
            cancelCalibration();return;
        }
        if(calibrationOverlay!=null)surface.removeView(calibrationOverlay);
        calibrationOverlay=new FrameLayout(this);calibrationOverlay.setBackgroundColor(BG);surface.addView(calibrationOverlay,new FrameLayout.LayoutParams(-1,-1));
        TextView instructions=label(t("lookTarget","Look at the target and hold still")+" · "+(calibrationIndex+1)+"/5",22);
        FrameLayout.LayoutParams heading=new FrameLayout.LayoutParams(-1,dp(100));calibrationOverlay.addView(instructions,heading);
        Button cancel=new Button(this);cancel.setText(t("cancelCalibration","Cancel calibration"));cancel.setMinHeight(dp(64));cancel.setOnClickListener(v->cancelCalibration());
        FrameLayout.LayoutParams cancelPosition=new FrameLayout.LayoutParams(-2,dp(64),android.view.Gravity.BOTTOM|android.view.Gravity.CENTER_HORIZONTAL);cancelPosition.bottomMargin=dp(30);calibrationOverlay.addView(cancel,cancelPosition);
        View target=new View(this);GradientDrawable circle=new GradientDrawable();circle.setShape(GradientDrawable.OVAL);circle.setColor(Color.rgb(255,221,133));target.setBackground(circle);
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(dp(48),dp(48));
        position.leftMargin=(int)(surface.getWidth()*calibrationTargets[calibrationIndex][0])-dp(24);
        position.topMargin=(int)(surface.getHeight()*calibrationTargets[calibrationIndex][1])-dp(24);
        calibrationOverlay.addView(target,position);calibrationPoints.clear();calibrationStarted=SystemClock.uptimeMillis();
    }
    private String modeName() { return new String[]{t("manual","Touch / keyboard"),t("dwell","Pointer dwell"),t("scan","Single switch"),t("gaze","Eye tracking")}[mode]; }
    private void append(String text) {
        int start=Math.max(0,message.getSelectionStart()), end=Math.max(start,message.getSelectionEnd());
        if(message.length()-end+start+text.length()>2000){notice(t("messageFull","Message is full."));return;}
        message.getText().replace(start,end,text);
    }
    private void backspace() {
        int start=Math.max(0,message.getSelectionStart()), end=Math.max(start,message.getSelectionEnd());
        if(start==end && start>0){BreakIterator iterator=BreakIterator.getCharacterInstance(pack.locale);iterator.setText(message.getText().toString());start=iterator.preceding(start);}
        if(start>=0)message.getText().delete(start,end);
    }
    private void refreshVoices() {
        voices.clear(); if(!speechReady || tts==null || pack==null)return;
        if(tts.getVoices()!=null)for(Voice voice:tts.getVoices())if(!voice.isNetworkConnectionRequired() && (voice.getFeatures()==null || !voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) && voice.getLocale().getLanguage().equals(pack.locale.getLanguage()))voices.add(voice);
        voices.sort(Comparator.comparingInt(Voice::getQuality).reversed().thenComparing(Voice::getName)); voiceIndex=0;
        String preferred=preferences.getString("voice",""); for(int i=0;i<voices.size();i++)if(voices.get(i).getName().equals(preferred))voiceIndex=i;
    }
    private void stopSpeech(){currentUtterance="";utteranceSequence++;if(tts!=null)tts.stop();}
    private void speak() {
        if(message.getText().toString().trim().isEmpty()){notice(t("emptyMessage","Write a message first."));return;}
        refreshVoices(); if(!speechReady || voices.isEmpty()){notice(t("installVoice","Install a local voice for this language."));return;}
        stopSpeech(); if(tts.setVoice(voices.get(voiceIndex))!=TextToSpeech.SUCCESS){notice(t("installVoice","Install a local voice for this language."));return;} tts.setSpeechRate(speechRate);
        if(tts.speak(message.getText().toString(),TextToSpeech.QUEUE_FLUSH,null,currentUtterance="zekals-"+(++utteranceSequence)) == TextToSpeech.ERROR)notice(t("speechFailed","Speech failed."));
        else notice(t("speaking","Speaking…"));
    }
    private void highlight(Button view, boolean active) { if(view==null)return; boolean primary=Boolean.TRUE.equals(view.getTag()); view.setBackground(background(active?Color.rgb(255,221,133):primary?ACCENT:contrast?Color.BLACK:SURFACE));view.setTextColor(active||primary?BG:INK); }
    private void resetDwell(){dwellState[0]=-1;dwellState[1]=0;dwellState[2]=0; highlight(selected,false);}
    private void setPaused(boolean value){paused=value;resetDwell();pauseButton.setText(paused?t("resume","Resume"):t("pause","Pause"));notice(paused?t("paused","Paused"):t("ready","Ready"));}
    private final Runnable tick = new Runnable(){ public void run(){
        if(!running)return;
        long now=SystemClock.uptimeMillis();
        if(calibrationIndex>=0 && now-calibrationStarted>=3000){
            if(now-lastGazeTime>500||calibrationPoints.size()<5){cancelCalibration();notice(t("calibrationLost","Tracking lost. Please retry."));}
            else{double x=0,y=0;for(double[] sample:calibrationPoints){x+=sample[0];y+=sample[1];}calibrationSamples.add(new double[]{x/calibrationPoints.size(),y/calibrationPoints.size()});calibrationIndex++;nextCalibration();}
        }
        if(mode==2 && !paused && calibrationIndex<0 && now-lastScan>=scanMillis){
            List<Button> available=new ArrayList<>();for(Button b:buttons)if(b.isShown()&&b.isEnabled())available.add(b);
            highlight(selected,false);
            if(!available.isEmpty()){scanIndex=(scanIndex+1)%available.size();selected=available.get(scanIndex);highlight(selected,true);selected.requestFocus();selected.requestRectangleOnScreen(new Rect(0,0,selected.getWidth(),selected.getHeight()),true);selected.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);}
            lastScan=now;
        }
        if((mode==1 || mode==3) && calibrationIndex<0){
            int target=mode==1?pointerTarget:gazeTarget(now);
            if(target!=blockedTarget)blockedTarget=-1;
            if(target==blockedTarget || (paused && target!=pauseButton.getId()))target=-1;
            Button next=null;for(Button b:buttons)if(b.getId()==target&&b.isShown()){next=b;break;}
            if(next!=selected){highlight(selected,false);selected=next;highlight(selected,true);}
            if(NativeCore.dwell(dwellState,target,now,dwellMillis)){
                for(Button b:buttons)if(b.getId()==target&&b.isShown()){blockedTarget=target;b.performClick();break;}
            }
        }
        if(selectionProgress!=null)selectionProgress.setProgress(dwellState[0]<0?0:(int)Math.min(100,Math.max(0,(now-dwellState[1])*100/dwellMillis)));
        handler.postDelayed(this,mode==0&&calibrationIndex<0?250:50);
    }};
    private int gazeTarget(long now){
        if(calibration==null||gazeState[3]==0||now-lastGazeTime>500||calibrationIndex>=0)return -1;
        if(surface.getWidth()!=calibrationWidth||surface.getHeight()!=calibrationHeight){calibration=null;resetDwell();notice(t("gazeHint","Recalibrate after resizing."));return -1;}
        double[] mapped=calibration.map(gazeState[0],gazeState[1]);int[] origin=new int[2];surface.getLocationOnScreen(origin);
        int x=origin[0]+(int)(mapped[0]*surface.getWidth()),y=origin[1]+(int)(mapped[1]*surface.getHeight());
        if(gazeReentryBlocked!=null){if(gazeReentryBlocked.contains(x,y))return -1;gazeReentryBlocked=null;}
        Rect bounds=new Rect();for(Button button:buttons)if(button.isShown()&&button.getGlobalVisibleRect(bounds)&&bounds.contains(x,y))return button.getId();
        return -1;
    }
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_ESCAPE){cancelCalibration();setPaused(true);stopSpeech();return true;}
        if(mode==2 && (key==KeyEvent.KEYCODE_SPACE || key==KeyEvent.KEYCODE_ENTER)){
            if(event.getRepeatCount()==0){if(paused)setPaused(false);else if(selected!=null&&selected.isShown()){selected.performClick();lastScan=SystemClock.uptimeMillis();}}return true;
        }
        return super.onKeyDown(key,event);
    }
    @Override protected void onResume(){super.onResume();if(pack!=null){running=true;handler.post(tick);}}
    @Override protected void onPause(){running=false;handler.removeCallbacks(tick);if(pack!=null){setPaused(true);stopCamera();}stopSpeech();super.onPause();}
    @Override protected void onDestroy(){if(tts!=null){stopSpeech();tts.shutdown();}super.onDestroy();}
}
