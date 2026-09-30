package org.zektopic.zekals;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
/** Native foundation; the next PR supplies communication controls. */
public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        double[] sample = new double[4];
        NativeCore.smooth(sample, .5, .5, 0, 70, true);
        TextView text = new TextView(this); text.setText("zekALS · Native input ready"); text.setTextSize(28);
        text.setPadding(24, 40, 24, 24); setContentView(text);
    }
}
