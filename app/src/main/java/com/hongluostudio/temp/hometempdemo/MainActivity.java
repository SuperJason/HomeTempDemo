package com.hongluostudio.temp.hometempdemo;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    protected final String TAG = getClass().getSimpleName();
    protected final int BRIGHTNESS_CHANGE_DELAY_CNT_MAX = 9;
    protected final int AFTER_TOUCH_SCREENON_CNT_MAX = 9;

    private final Timer mTimer = new Timer();
    private String mTemperatureStr, mHumidityStr;
    private int mUpdateLogCnt = 0;
    private SensorManager mSensorManager;
    private Sensor mLightSensor;

    private int mSetBrightnessInt = 120;
    private int mOldSetBrightnessInt = 120;
    private int mCurrentBrightnessInt = 120;
    private int mBrightnessDebounceCnt = 0;

    private int mScreenOnCnt = 0;

    private HandlerThread mHttpHandlerThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Message message = new Message();
                message.what = 1;
                mTimeHandler.sendMessage(message);
            }
        },1000,1000);

        /* https://blog.csdn.net/liuyuejinqiu/article/details/70230963 */
        /* https://developer.android.com/training/system-ui/navigation#java */
        View v = getWindow().getDecorView();
        int opt = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        v.setSystemUiVisibility(opt);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (mLightSensor != null)
            mSensorManager.registerListener(new LightSensorListener(), mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        else
            Log.e(TAG, "mLightSensor is null!\n");

        mHttpHandlerThread = new HandlerThread("http");
        mHttpHandlerThread.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        Log.d(TAG, "onTouchEvent: " + action + "\n");
        switch(action) {
            case (MotionEvent.ACTION_DOWN) :
                //Log.d(TAG,"Action was DOWN");
            case (MotionEvent.ACTION_MOVE) :
                //Log.d(TAG,"Action was MOVE");
            case (MotionEvent.ACTION_UP) :
                //Log.d(TAG,"Action was UP");
                mScreenOnCnt = AFTER_TOUCH_SCREENON_CNT_MAX;
                return true;
            default :
                return super.onTouchEvent(event);
        }
    }

    public class LightSensorListener implements SensorEventListener {

        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }

        public void onSensorChanged(SensorEvent event) {
            float lux = event.values[0];

            Log.d(TAG, "lux: " + lux + "\n");

            int brightness;
            if (lux < 5) {
                brightness = 0; // 不考虑关闭屏幕的场景（如，此时有touch事件），该值应为11
            } else if (lux < 40) {
                brightness = 22;
            } else if (lux < 100) {
                brightness = 47;
            } else if (lux < 325) {
                brightness = 61;
            } else if (lux < 600) {
                brightness = 84;
            } else if (lux < 1250) {
                brightness = 107;
            } else if (lux < 2200) {
                brightness = 154;
            } else if (lux < 4000) {
                brightness = 212;
            } else if (lux < 10000){
                brightness = 245;
            } else {
                brightness = 255;
            }

            mSetBrightnessInt = brightness;
        }
    }

    private int writeBrightness(int brightness) {
        BufferedWriter bufWriter = null;
        FileWriter fileWriter = null;

        try {
            fileWriter = new FileWriter("/sys/class/leds/lcd-backlight/brightness");
            bufWriter = new BufferedWriter(fileWriter);
            bufWriter.write(String.valueOf(brightness));
            bufWriter.close();
            //Log.d(TAG, "writeBrightness: " + brightness + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return 0;
    }

    Handler mTimeHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            getTemperatureHumidity();
            if (mUpdateLogCnt > 3/*3600*/ || mUpdateLogCnt == 0) { // 1小时更新一次数据
                updateLogData();
                mUpdateLogCnt = 1;
            }
            
            mUpdateLogCnt++;
            updateShow();

            debounceBrightnessSetting();

            if (mScreenOnCnt > 0)
                mScreenOnCnt -= 1;

            super.handleMessage(msg);
        }
    };

    private int getTemperatureHumidity() {
        FileInputStream inputStream;

        try {
            inputStream = new FileInputStream("/sys/class/hwmon/hwmon3/temp1_input");
            byte[] bytes = new byte[16];
            int n = 0;
            n = inputStream.read(bytes);
            float tempValue = Float.parseFloat(new String(bytes).substring(0, n - 1));
            tempValue = new BigDecimal(tempValue / 1000).setScale(1, BigDecimal.ROUND_UP).floatValue();
            mTemperatureStr = String.format("%2.1f℃", tempValue);
            //Log.d(TAG, "n: " + n + ", mTemperatureStr: " + mTemperatureStr + "\n");

        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            inputStream = new FileInputStream("/sys/class/hwmon/hwmon3/humidity1_input");
            byte[] bytes = new byte[16];
            int n = 0;
            n = inputStream.read(bytes);
            float humiValue = Float.parseFloat(new String(bytes).substring(0, n - 1));
            humiValue = new BigDecimal(humiValue / 1000).setScale(1, BigDecimal.ROUND_UP).floatValue();
            mHumidityStr = String.format("%2.1f%%", humiValue);
            //Log.d(TAG, "n: " + n + ", mHumidityStr: " + mHumidityStr + "\n");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    private int updateShow() {
        SpannableString ss;
        TextView tv;
        StringBuffer strBuf = new StringBuffer();
        Date date = new Date();

        // 公历
        SimpleDateFormat sunarDate = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINESE);
        strBuf.delete(0, strBuf.length());
        strBuf.append(sunarDate.format(date));
        ss = new SpannableString(strBuf.toString());
        ss.setSpan(new TypefaceSpan("sans"), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new AbsoluteSizeSpan(24,true), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // 通过变大的手法强调日期
        ss.setSpan(new RelativeSizeSpan(3.0f), ss.length() - 3, ss.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(this.getResources().getColor(R.color.colorDateFg)), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv = (TextView) findViewById(R.id.tvSunarDataShow);
        tv.setText(ss);

        // 星期
        SimpleDateFormat weekDate = new SimpleDateFormat("EEEE", Locale.CHINESE);
        strBuf.delete(0, strBuf.length());
        strBuf.append("星期" + weekDate.format(date).substring(2));
        ss = new SpannableString(strBuf.toString());
        ss.setSpan(new TypefaceSpan("sans"), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new AbsoluteSizeSpan(24,true), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // 通过变大的手法强调星期
        ss.setSpan(new RelativeSizeSpan(3.0f), ss.length() - 1, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(this.getResources().getColor(R.color.colorDateFg)), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //ss.setSpan(new BackgroundColorSpan(this.getResources().getColor(R.color.colorTimeBg)), ss.length() - 1, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv = (TextView) findViewById(R.id.tvWeekDataShow);
        tv.setText(ss);

        // 农历
        strBuf.delete(0, strBuf.length());
        int yyyy = Integer.valueOf(new SimpleDateFormat("yyyy", Locale.CHINESE).format(date)).intValue();
        int MM = Integer.valueOf(new SimpleDateFormat("MM", Locale.CHINESE).format(date)).intValue();
        int dd = Integer.valueOf(new SimpleDateFormat("dd", Locale.CHINESE).format(date)).intValue();
        strBuf.append(LunarUtils.getLunar(yyyy, MM, dd));
        ss = new SpannableString(strBuf.toString());
        ss.setSpan(new TypefaceSpan("sans"), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new AbsoluteSizeSpan(24,true), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // 通过变大的手法强调日期
        ss.setSpan(new RelativeSizeSpan(3.0f), ss.length() - 2, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(this.getResources().getColor(R.color.colorDateFg)), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv = (TextView) findViewById(R.id.tvLunarDataShow);
        tv.setText(ss);

        // 温湿度
        strBuf.delete(0, strBuf.length());
        strBuf.append(" 温度: " + mTemperatureStr + "\n湿度: " + mHumidityStr);
        ss = new SpannableString(strBuf.toString());
        ss.setSpan(new TypefaceSpan("sans"), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new AbsoluteSizeSpan(24,true), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(this.getResources().getColor(R.color.colorDateFg)), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv = (TextView) findViewById(R.id.tvTemHumiDateShow);
        tv.setText(ss);

        // 时间
        SimpleDateFormat tTime = new SimpleDateFormat("HH:mm s", Locale.CHINESE);
        strBuf.delete(0, strBuf.length());
        strBuf.append(tTime.format(date));
        ss = new SpannableString(strBuf.toString());
        ss.setSpan(new TypefaceSpan("sans"), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new AbsoluteSizeSpan(60,true), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //设置字体大小（相对值,单位：像素） 参数表示为默认字体大小的多少倍
        ss.setSpan(new RelativeSizeSpan(4.0f), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);  //2.0f表示默认字体大小的两倍
        ss.setSpan(new ForegroundColorSpan(this.getResources().getColor(R.color.colorTimeFg)), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);  //粗体
        tv = (TextView) findViewById(R.id.tvTimeShowId);
        tv.setText(ss);

        //ssTime.setSpan(new BackgroundColorSpan(Color.YELLOW), 0, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //ssTime.setSpan(new ForegroundColorSpan(Color.WHITE), ssTime.length()-15, ssTime.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //ssTime.setSpan(new BackgroundColorSpan(Color.parseColor("#CCCC99")), ssTime.length()-15, ssTime.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return 0;
    }

    private int updateLogData() {
        return 0;
    }

    private int debounceBrightnessSetting() {
        if (mSetBrightnessInt != mOldSetBrightnessInt) {
            mOldSetBrightnessInt = mSetBrightnessInt;
            mBrightnessDebounceCnt = 0;
        } else if (mBrightnessDebounceCnt < BRIGHTNESS_CHANGE_DELAY_CNT_MAX) {
            mBrightnessDebounceCnt++;
        } else if (mCurrentBrightnessInt != mSetBrightnessInt) {
            if (mScreenOnCnt > 0 && mSetBrightnessInt == 0 && mCurrentBrightnessInt != 11) {
                mCurrentBrightnessInt = 11;
                writeBrightness(mCurrentBrightnessInt);
            } else if (mScreenOnCnt <= 0) {
                mCurrentBrightnessInt = mSetBrightnessInt;
                writeBrightness(mCurrentBrightnessInt);
            }
        } else if (mScreenOnCnt > 0 ) {
            if (mCurrentBrightnessInt == 0) {
                mCurrentBrightnessInt = 11;
                writeBrightness(mCurrentBrightnessInt);
            }
        }

        return 0;
    }
}