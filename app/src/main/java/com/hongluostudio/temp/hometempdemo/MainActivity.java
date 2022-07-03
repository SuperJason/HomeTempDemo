package com.hongluostudio.temp.hometempdemo;

import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
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
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
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
    private StringBuffer mTempShowStrBuf, mTimeShowStrBuf, mDataShowStrBuf, mLogShowStrBuf;
    private TextView tvTimeShow, tvLogShow;
    private String mTemperatureStr, mHumidityStr;
    private int mUpdateLogCnt = 0;
    private SensorManager mSensorManager;
    private Sensor mLightSensor;

    private int mSetBrightnessInt = 120;
    private int mOldSetBrightnessInt = 120;
    private int mCurrentBrightnessInt = 120;
    private int mBrightnessDebounceCnt = 0;

    private int mScreenOnCnt = 0;
    private int mLogLineLength = 36;

    private HandlerThread mHttpHandlerThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLogShowStrBuf = new StringBuffer();
        mTempShowStrBuf = new StringBuffer();
        mTimeShowStrBuf = new StringBuffer();
        mDataShowStrBuf = new StringBuffer();
        tvLogShow = (TextView) findViewById(R.id.tvLogShowId);
        //tvTempShow = (TextView) findViewById(R.id.tvTempShowId);
        tvTimeShow = (TextView) findViewById(R.id.tvTimeShowId);
        //tvDataShow = (TextView) findViewById(R.id.tvDataShowId);

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
        Date date = new Date();
        SimpleDateFormat fTime = new SimpleDateFormat("HH:mm ss", Locale.CHINESE);
        SimpleDateFormat fDate = new SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINESE);

        mTempShowStrBuf.delete(0, mTempShowStrBuf.length());
        mTempShowStrBuf.append(" 温度: " + mTemperatureStr + " 湿度: " + mHumidityStr);

        mTimeShowStrBuf.delete(0,mTimeShowStrBuf.length());
        mTimeShowStrBuf.append(fTime.format(date));

        mDataShowStrBuf.delete(0,mDataShowStrBuf.length());
        mDataShowStrBuf.append(fDate.format(date));

        SpannableString ssTime = new SpannableString(mTimeShowStrBuf.toString());
        ssTime.setSpan(new TypefaceSpan("sans"), 0, ssTime.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssTime.setSpan(new AbsoluteSizeSpan(40,true), 0, ssTime.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //设置字体大小（相对值,单位：像素） 参数表示为默认字体大小的多少倍
        ssTime.setSpan(new RelativeSizeSpan(6.0f), 0, ssTime.length() - 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);  //2.0f表示默认字体大小的两倍
        ssTime.setSpan(new ForegroundColorSpan(this.getResources().getColor(R.color.colorTimeFg)), 0, ssTime.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //ssTime.setSpan(new BackgroundColorSpan(Color.YELLOW), 0, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //ssTime.setSpan(new ForegroundColorSpan(Color.WHITE), ssTime.length()-15, ssTime.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //ssTime.setSpan(new BackgroundColorSpan(Color.parseColor("#CCCC99")), ssTime.length()-15, ssTime.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssTime.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, ssTime.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);  //粗体

        tvTimeShow.setText(ssTime);

        return 0;
    }

    private int updateLogData() {
        Date date = new Date();
        SimpleDateFormat ft = new SimpleDateFormat("MM-dd HH:mm");

        if (mLogShowStrBuf.length() >= mLogLineLength * 3 * 24 * 12) {// 5分钟一次数据的情况下，保存3天的数据
            String str = mLogShowStrBuf.substring(mLogLineLength, mLogShowStrBuf.length());
            mLogShowStrBuf.delete(0, mLogShowStrBuf.length());
            mLogShowStrBuf.append(str);
        }
        mLogShowStrBuf.append(ft.format(date));
        mLogShowStrBuf.append(" " + mTemperatureStr);
        mLogShowStrBuf.append(" " + mHumidityStr);

        mLogShowStrBuf.delete(0, mLogShowStrBuf.length());
        // 公历
        //mLogShowStrBuf.append("公历: \n");
        //SimpleDateFormat fDate = new SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINESE);
        SimpleDateFormat fDate = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINESE);
        mLogShowStrBuf.append(fDate.format(date) + "\n");
        fDate = new SimpleDateFormat("EEEE", Locale.CHINESE);
        mLogShowStrBuf.append(fDate.format(date));
        mLogShowStrBuf.append("\n\n");

        // 农历
        //mLogShowStrBuf.append("农历: \n");
        mLogShowStrBuf.append(LunarUtils.getLunar(2022, 7, 3));

        // 温湿度
        mLogShowStrBuf.append("\n\n");
        mLogShowStrBuf.append(" 温度: " + mTemperatureStr + "\n");
        mLogShowStrBuf.append(" 湿度: " + mHumidityStr);

        mLogShowStrBuf.append("\n");
        mLogLineLength = mLogShowStrBuf.length();
        //Log.d(TAG, "mLogShowStrBuf length: " + mLogShowStrBuf.length() + "\n");

        SpannableString ss = new SpannableString(mLogShowStrBuf.toString());
        // https://blog.csdn.net/pcaxb/article/details/47341249
        //设置字体(default,default-bold,monospace,serif,sans-serif)
        ss.setSpan(new TypefaceSpan("sans"), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //设置字体大小（绝对值,单位：像素）
        ss.setSpan(new AbsoluteSizeSpan(24,true), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //设置字体前景色
        ss.setSpan(new ForegroundColorSpan(this.getResources().getColor(R.color.colorDateFg)), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //设置字体背景色
        //ss.setSpan(new BackgroundColorSpan(Color.LTGRAY), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvLogShow.setText(ss);

        // Update ScrollView
        ScrollView mScrollView = (ScrollView) findViewById(R.id.svLogShow);
        mScrollView.smoothScrollTo(0, tvLogShow.getBottom());

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