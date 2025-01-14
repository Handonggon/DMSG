package Koreatech.grad_project;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.widget.Chronometer;

public class PopupTimeActivity extends Activity {
    private Chronometer mChronometer;
    Long startDate;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        startDate = intent.getLongExtra("Time", 0);

        setContentView(R.layout.popup_time);
        mChronometer = findViewById(R.id.chronometer);

        //time 초기화
        mChronometer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener(){
            @Override
            public void onChronometerTick(Chronometer cArg) {
                long now = System.currentTimeMillis();
                long time = now - startDate;
                if(time >= 2*60*60*1000) {mChronometer.setTextColor(Color.parseColor("#FF0000"));}
                cArg.setText(DateFormat.format("kk:mm:ss", time-9*60*60*1000));
            }
        });
        mChronometer.start();

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);

        int width = dm.widthPixels;
        int height = dm.heightPixels;
        getWindow().setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        getWindow().setLayout((int)(width*0.8), (int)(height * 0.2));
    }
    @Override
    protected void onPause() {
        super.onPause();
        overridePendingTransition(0,0);
    }
}
