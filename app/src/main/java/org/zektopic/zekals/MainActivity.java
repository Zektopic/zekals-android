package org.zektopic.zekals;

import android.app.Activity;
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
    private TextView status;
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
            TextView failure = new TextView(this); failure.setText("Language assets could not be loaded. Please reinstall the app."); setContentView(failure); return;
        }
        tts = new TextToSpeech(this, result -> runOnUiThread(() -> {
            speechReady = result == TextToSpeech.SUCCESS; refreshVoices();
            if (!speechReady) notice(t("installVoice", "Install an offline voice in device settings."));
        }));
    }
    @Override public Object onRetainNonConfigurationInstance() { return lastMessage; }
    private String t(String key, String fallback) { return pack == null ? fallback : pack.text(key, fallback); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void save() {
        preferences.edit().putString("language", pack.code).putBoolean("large", large).putBoolean("contrast", contrast)
            .putLong("dwell", dwellMillis).putLong("scan", scanMillis).putFloat("rate", speechRate).apply();
    }
    private void notice(String text) { if (status != null) status.setText(text); }
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
        view.setOnClickListener(v -> { blockedTarget = view.getId(); resetDwell(); action.run(); });
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
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(8),dp(6),dp(8),dp(4)); root.setBackgroundColor(contrast ? Color.BLACK : BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(dp(8)+insets.getSystemWindowInsetLeft(), dp(6)+insets.getSystemWindowInsetTop(), dp(8)+insets.getSystemWindowInsetRight(), dp(4)+insets.getSystemWindowInsetBottom()); return insets; });
        TextView title = label("zekALS · " + pack.name, 25); if(android.os.Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true); root.addView(title);
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
        row(content, speak, button(t("stop","Stop"), () -> { if (tts != null) tts.stop(); notice(t("stopped","Speech stopped.")); }));
        row(content, button(t("undo","Undo"), () -> { if (!undo.isEmpty()) { changing=true; message.setText(undo.removeLast()); message.setSelection(message.length()); changing=false; } }), button(t("clear","Clear"), () -> message.setText("")));
        try {
            content.addView(label(t("phrases","Everyday phrases"),22));
            List<Button> phraseButtons = new ArrayList<>();
            for (String phrase : pack.phrases()) phraseButtons.add(button(phrase, () -> append((message.length()>0 ? " " : "") + phrase)));
            grid(content, phraseButtons, getResources().getConfiguration().screenWidthDp >= 700 ? 4 : 2);
            List<List<String>> pages = pack.pages(); keyboardPage %= pages.size();
            row(content, button(t("moreKeys","More characters") + " " + (keyboardPage+1) + "/" + pages.size(), () -> { keyboardPage++; render(); }),
                button(pack.name + " ›", () -> { if(tts!=null)tts.stop(); languageIndex=(languageIndex+1)%packs.size(); pack=packs.get(languageIndex); keyboardPage=0; save(); refreshVoices(); render(); }));
            List<Button> keys = new ArrayList<>();
            for (String key : pages.get(keyboardPage)) keys.add(button(key.equals("\u200d") ? t("joinLetters","Join letters") : key, () -> append(key)));
            grid(content, keys, getResources().getConfiguration().screenWidthDp >= 700 ? 10 : 5);
        } catch (Exception error) { notice("Keyboard pack is invalid."); }
        row(content, button(t("space","Space"), () -> append(" ")), button(t("backspace","Delete"), this::backspace));
        settingsPanel = new LinearLayout(this); settingsPanel.setOrientation(LinearLayout.VERTICAL); settingsPanel.setVisibility(View.GONE);
        content.addView(button(t("settings","Settings"), () -> { settingsPanel.setVisibility(settingsPanel.getVisibility()==View.VISIBLE ? View.GONE : View.VISIBLE); resetDwell(); }));
        content.addView(settingsPanel);
        modeButton = button(modeName(), () -> { mode=(mode+1)%4; lastScan=0; resetDwell(); modeButton.setText(modeName()); }); row(settingsPanel,modeButton);
        row(settingsPanel,button(t("dwellTime","Dwell time") + " −", () -> { dwellMillis=Math.max(500,dwellMillis-100); save(); notice(dwellMillis + " ms"); }),button(t("dwellTime","Dwell time") + " +", () -> { dwellMillis=Math.min(3000,dwellMillis+100); save(); notice(dwellMillis + " ms"); }));
        row(settingsPanel,button(t("scanTime","Scan interval") + " −", () -> { scanMillis=Math.max(600,scanMillis-200); save(); notice(scanMillis + " ms"); }),button(t("scanTime","Scan interval") + " +", () -> { scanMillis=Math.min(4000,scanMillis+200); save(); notice(scanMillis + " ms"); }));
        row(settingsPanel,button(t("textSize","Text size"), () -> { large=!large; save(); render(); }), button(t("contrast","Contrast"), () -> { contrast=!contrast; save(); render(); }));
        row(settingsPanel,button(t("speechSpeed","Speech speed") + " −", () -> { speechRate=Math.max(.5f,speechRate-.1f); save(); notice(String.format(pack.locale,"%.1f×",speechRate)); }),button(t("speechSpeed","Speech speed") + " +", () -> { speechRate=Math.min(1.5f,speechRate+.1f); save(); notice(String.format(pack.locale,"%.1f×",speechRate)); }));
        row(settingsPanel,button(t("voice","Voice") + " ›", () -> { if(!voices.isEmpty()){voiceIndex=(voiceIndex+1)%voices.size(); preferences.edit().putString("voice",voices.get(voiceIndex).getName()).apply(); notice(voices.get(voiceIndex).getName());}else notice(t("installVoice","Install a voice.")); }));
        addCameraControls(settingsPanel);
        settingsPanel.addView(label(t("scanHelp","Press Space to choose the highlighted button. Escape pauses."),18));
        status=label(t("ready","Ready when you are."),18); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); root.addView(status);
        setContentView(root);
    }
    private void addCameraControls(LinearLayout panel) { /* Camera adapter is added in the next PR. */ }
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
        if(tts.getVoices()!=null)for(Voice voice:tts.getVoices())if(!voice.isNetworkConnectionRequired() && voice.getLocale().getLanguage().equals(pack.locale.getLanguage()))voices.add(voice);
        voices.sort(Comparator.comparingInt(Voice::getQuality).reversed().thenComparing(Voice::getName)); voiceIndex=0;
        String preferred=preferences.getString("voice",""); for(int i=0;i<voices.size();i++)if(voices.get(i).getName().equals(preferred))voiceIndex=i;
    }
    private void speak() {
        if(message.length()==0){notice(t("emptyMessage","Write a message first."));return;}
        refreshVoices(); if(!speechReady || voices.isEmpty()){notice(t("installVoice","Install a local voice for this language."));return;}
        tts.stop(); tts.setVoice(voices.get(voiceIndex)); tts.setSpeechRate(speechRate);
        if(tts.speak(message.getText().toString(),TextToSpeech.QUEUE_FLUSH,null,"zekals") == TextToSpeech.ERROR)notice(t("speechFailed","Speech failed."));
        else notice(t("speaking","Speaking…"));
    }
    private void highlight(Button view, boolean active) { if(view==null)return; boolean primary=Boolean.TRUE.equals(view.getTag()); view.setBackground(background(active?Color.rgb(255,221,133):primary?ACCENT:contrast?Color.BLACK:SURFACE));view.setTextColor(active||primary?BG:INK); }
    private void resetDwell(){dwellState[0]=-1;dwellState[1]=0;dwellState[2]=0; highlight(selected,false);}
    private void setPaused(boolean value){paused=value;resetDwell();pauseButton.setText(paused?t("resume","Resume"):t("pause","Pause"));notice(paused?t("paused","Paused"):t("ready","Ready"));}
    private final Runnable tick = new Runnable(){ public void run(){
        if(!running)return;
        long now=SystemClock.uptimeMillis();
        if(mode==2 && !paused && now-lastScan>=scanMillis){
            List<Button> available=new ArrayList<>();for(Button b:buttons)if(b.isShown()&&b.isEnabled())available.add(b);
            highlight(selected,false);
            if(!available.isEmpty()){scanIndex=(scanIndex+1)%available.size();selected=available.get(scanIndex);highlight(selected,true);selected.requestFocus();selected.requestRectangleOnScreen(new Rect(0,0,selected.getWidth(),selected.getHeight()),true);selected.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);}
            lastScan=now;
        }
        if(mode==1 || mode==3){
            int target=mode==1?pointerTarget:gazeTarget(now);
            if(target!=blockedTarget)blockedTarget=-1;
            if(target==blockedTarget || (paused && target!=pauseButton.getId()))target=-1;
            if(NativeCore.dwell(dwellState,target,now,dwellMillis)){
                for(Button b:buttons)if(b.getId()==target&&b.isShown()){blockedTarget=target;b.performClick();break;}
            }
        }
        handler.postDelayed(this,mode==0?250:50);
    }};
    private int gazeTarget(long now){return -1;}
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_ESCAPE){setPaused(true);if(tts!=null)tts.stop();return true;}
        if(mode==2 && (key==KeyEvent.KEYCODE_SPACE || key==KeyEvent.KEYCODE_ENTER)){
            if(event.getRepeatCount()==0){if(paused)setPaused(false);else if(selected!=null&&selected.isShown()){selected.performClick();lastScan=SystemClock.uptimeMillis();}}return true;
        }
        return super.onKeyDown(key,event);
    }
    @Override protected void onResume(){super.onResume();if(pack!=null){running=true;handler.post(tick);}}
    @Override protected void onPause(){running=false;handler.removeCallbacks(tick);if(pack!=null)setPaused(true);if(tts!=null)tts.stop();super.onPause();}
    @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
