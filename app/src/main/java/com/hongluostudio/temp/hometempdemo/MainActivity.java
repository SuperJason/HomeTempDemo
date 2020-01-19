package com.hongluostudio.temp.hometempdemo;

import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
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
    protected final int SET_TEMP_DEBOUNCE_CNT_MAX = 3;
    protected final float SET_TEMPERATURE_MAX  = 30;
    protected final float SET_TEMPERATURE_MIN  = 5;
    protected final double SET_TEMPERATURE_STEP = 0.5;

    protected final int SET_TEMP_MODE_COMPLETE = 0;
    protected final int SET_TEMP_MODE_ONGOING = 1;

    private final Timer mTimer = new Timer();
    private StringBuffer mTempShowStrBuf, mLogShowStrBuf, mSetTempStrBuf, mStatusStrBuf;
    private TextView  tvTempShow, tvLogShow, tvSetTemp, tvStatus;
    private String mTemperatureStr, mHumidityStr;
    private int mUpdateLogCnt = 0;
    private Button btnPlus, btnMinus;
    private float mSetTempValueFloat = 18;
    private float mShowedTempValueFloat = 0;
    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private boolean mIsHeaterEnable = false;
    private boolean mIsHeaterOnline = true;

    private int mSetBrightnessInt = 120;
    private int mOldSetBrightnessInt = 120;
    private int mCurrentBrightnessInt = 120;
    private int mBrightnessDebounceCnt = 0;

    private float mModifiedSetTempFloat = 18;
    private float mOldModifiedSetTempFloat = 18;
    private float mCurrentSetTempFloat = 18;
    private int mSetTempDebounceCnt = 0;

    private int mScreenOnCnt = 0;
    private int mLogLineLength = 36;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLogShowStrBuf = new StringBuffer();
        mTempShowStrBuf = new StringBuffer();
        mSetTempStrBuf = new StringBuffer();
        mStatusStrBuf = new StringBuffer();
        tvLogShow = (TextView) findViewById(R.id.tvLogShowId);
        tvTempShow = (TextView) findViewById(R.id.tvTempShowId);
        tvSetTemp = (TextView) findViewById(R.id.tvSetTempId);
        tvStatus = (TextView) findViewById(R.id.tvStatusId);

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

        btnPlus = (Button) findViewById(R.id.buttonUp);
        btnMinus = (Button) findViewById(R.id.buttonDown);

        btnPlus.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                mSetTempValueFloat += SET_TEMPERATURE_STEP;
                if(mSetTempValueFloat > SET_TEMPERATURE_MAX)
                    mSetTempValueFloat = SET_TEMPERATURE_MAX;
                mModifiedSetTempFloat = mSetTempValueFloat;
                updateSetTempTextView(SET_TEMP_MODE_ONGOING);
            }
        });

        btnMinus.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                mSetTempValueFloat -= SET_TEMPERATURE_STEP;
                if (mSetTempValueFloat < SET_TEMPERATURE_MIN)
                    mSetTempValueFloat = SET_TEMPERATURE_MIN;
                mModifiedSetTempFloat = mSetTempValueFloat;
                updateSetTempTextView(SET_TEMP_MODE_ONGOING);
            }
        });

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (mLightSensor != null)
            mSensorManager.registerListener(new LightSensorListener(), mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        else
            Log.e(TAG, "mLightSensor is null!\n");

        updateSetTempTextView(SET_TEMP_MODE_COMPLETE);
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
            if (lux < 20) {
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
            if (mUpdateLogCnt > 600 || mUpdateLogCnt == 0) { // 5分钟更新一次数据
                updateLogData();
                mUpdateLogCnt = 1;
            }
            
            mUpdateLogCnt++;
            updateShow();

            debounceBrightnessSetting();
            debounceTemperatureSetting();
            updateHeaterStatus();

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
            mShowedTempValueFloat = tempValue;
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
        SimpleDateFormat ft = new SimpleDateFormat("HH时mm分ss秒 \n yyyy年MM月dd日 EEEE", Locale.CHINESE);

        mTempShowStrBuf.delete(0, mTempShowStrBuf.length());
        mTempShowStrBuf.append(" 温度: " + mTemperatureStr + "℃ \n 湿度: " + mHumidityStr + " \n\n" + ft.format(date));
        SpannableString ss = new SpannableString(mTempShowStrBuf.toString());
        ss.setSpan(new TypefaceSpan("sans"), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new AbsoluteSizeSpan(36,true), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //设置字体大小（相对值,单位：像素） 参数表示为默认字体大小的多少倍
        ss.setSpan(new RelativeSizeSpan(2.0f), 0, 24, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);  //2.0f表示默认字体大小的两倍
        ss.setSpan(new ForegroundColorSpan(Color.BLUE), 0, 24, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new BackgroundColorSpan(Color.YELLOW), 0, 24, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(Color.parseColor("#663366")), ss.length()-28, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new BackgroundColorSpan(Color.parseColor("#CCCC99")), ss.length()-28, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);  //粗体

        tvTempShow.setText(ss);

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
        mLogShowStrBuf.append(" " + mCurrentSetTempFloat);
        if (mIsHeaterOnline)
            mLogShowStrBuf.append("@");
        else
            mLogShowStrBuf.append(" ");
        if (mIsHeaterEnable)
            mLogShowStrBuf.append("+");
        else
            mLogShowStrBuf.append("-");
        mLogShowStrBuf.append("\n");
        mLogLineLength = mLogShowStrBuf.length();
        //Log.d(TAG, "mLogShowStrBuf length: " + mLogShowStrBuf.length() + "\n");

        SpannableString ss = new SpannableString(mLogShowStrBuf.toString());
        // https://blog.csdn.net/pcaxb/article/details/47341249
        //设置字体(default,default-bold,monospace,serif,sans-serif)
        ss.setSpan(new TypefaceSpan("serif"), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //设置字体大小（绝对值,单位：像素）
        ss.setSpan(new AbsoluteSizeSpan(16,true), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //设置字体前景色
        ss.setSpan(new ForegroundColorSpan(Color.BLACK), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //设置字体背景色
        ss.setSpan(new BackgroundColorSpan(Color.LTGRAY), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvLogShow.setText(ss);

        // Update ScrollView
        ScrollView mScrollView = (ScrollView) findViewById(R.id.svLogShow);
        mScrollView.smoothScrollTo(0, tvLogShow.getBottom());

        return 0;
    }

    private int updateSetTempTextView(int mode) {
        mSetTempStrBuf.delete(0, mSetTempStrBuf.length());
        mSetTempStrBuf.append(" 设置温度: " + mSetTempValueFloat + "℃\n");
        SpannableString ss = new SpannableString(mSetTempStrBuf.toString());
        ss.setSpan(new TypefaceSpan("sans"), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new AbsoluteSizeSpan(22,true), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (mode == SET_TEMP_MODE_ONGOING)
            ss.setSpan(new BackgroundColorSpan(Color.RED), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ss.setSpan(new ForegroundColorSpan(Color.BLUE), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvSetTemp.setText(ss);

        return 0;
    }

    private int updateHeaterStatus() {
        if (mShowedTempValueFloat < mCurrentSetTempFloat - SET_TEMPERATURE_STEP) {
            // 开始加热
            mIsHeaterEnable = true;
        } else if (mShowedTempValueFloat > mCurrentSetTempFloat + SET_TEMPERATURE_STEP) {
            // 停止加热
            mIsHeaterEnable = false;
        }
        updateStatusTextView();
        return 0;
    }

    private int updateStatusTextView() {
        mStatusStrBuf.delete(0, mStatusStrBuf.length());
        if (mIsHeaterOnline) {
            mStatusStrBuf.append("在线");
            if (mIsHeaterEnable) {
                mStatusStrBuf.append(", 加热中");
            }
        } else {
            mStatusStrBuf.append("加热器不在线");
        }

        SpannableString ss = new SpannableString(mStatusStrBuf.toString());
        ss.setSpan(new TypefaceSpan("sans"), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new AbsoluteSizeSpan(22,true), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ss.setSpan(new ForegroundColorSpan(Color.BLUE), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvStatus.setText(ss);

        return 0;
    }

    private int debounceTemperatureSetting() {
        if (mModifiedSetTempFloat != mOldModifiedSetTempFloat) {
            mOldModifiedSetTempFloat = mModifiedSetTempFloat;
            mSetTempDebounceCnt = 0;
        } else if (mSetTempDebounceCnt < SET_TEMP_DEBOUNCE_CNT_MAX) {
            mSetTempDebounceCnt++;
        } else if (mCurrentSetTempFloat != mModifiedSetTempFloat) {
            mCurrentSetTempFloat = mModifiedSetTempFloat;
            // SettingTemperature updated
            updateSetTempTextView(SET_TEMP_MODE_COMPLETE);
        }

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