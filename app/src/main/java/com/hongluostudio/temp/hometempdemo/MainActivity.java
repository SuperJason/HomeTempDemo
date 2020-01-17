package com.hongluostudio.temp.hometempdemo;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.MotionEventCompat;
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
    private final Timer timer = new Timer();
    private StringBuffer showBuf, strBuf, tempBuf, statusBuf;
    private TextView tvHello, tvShow, setTempText, setStatusText;
    private FileInputStream fInSys;
    private String temperature, humidity;
    private SpannableString msp;
    private int count = 0;
    private Button mButtonUp, mButtonDown;
    private float setTempValue = 18;
    private float currentTemperatureValue = 0;
    private SensorManager sm;
    private Sensor ligthSensor;
    private int heaterStatus = 0;
    private int ifHeaterOnline = 1;

    private int iSettingBrightness = 120;
    private int iOldSettingBrightness = 120;
    private int iCurrentBrightness = 120;
    private int iBrightnessDebounceCnt = 0;

    private float iModifiedSettingTemperature = 18;
    private float iOldModifiedSettingTemperature = 18;
    private float iCurrentSettingTemperature = 18;
    private int iSettingTemperatureDebounceCnt = 0;

    private int iScreenOnCnt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        strBuf = new StringBuffer();
        showBuf = new StringBuffer();
        tempBuf = new StringBuffer();
        statusBuf = new StringBuffer();
        tvHello = (TextView) findViewById(R.id.HelloText);
        tvShow = (TextView) findViewById(R.id.ShowText);
        setTempText = (TextView) findViewById(R.id.setTempText);
        setStatusText = (TextView) findViewById(R.id.setStatusText);

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Message message = new Message();
                message.what = 1;
                handler.sendMessage(message);
            }
        },1000,1000);

        /* https://blog.csdn.net/liuyuejinqiu/article/details/70230963 */
        /* https://developer.android.com/training/system-ui/navigation#java */
        View decorView = getWindow().getDecorView();
        /*int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;*/
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        decorView.setSystemUiVisibility(uiOptions);

        mButtonUp = (Button) findViewById(R.id.buttonUp);
        mButtonDown = (Button) findViewById(R.id.buttonDown);

        mButtonUp.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                setTempValue += 0.5;
                if(setTempValue > 30)
                    setTempValue = 30;
                iModifiedSettingTemperature = setTempValue;
                setTempTextUpdate(0);
            }
        });

        mButtonDown.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                setTempValue -= 0.5;
                if (setTempValue < 5)
                    setTempValue = 5;
                iModifiedSettingTemperature = setTempValue;
                setTempTextUpdate(0);
            }
        });

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        ligthSensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (ligthSensor != null)
            sm.registerListener(new LightSensorListener(), ligthSensor, SensorManager.SENSOR_DELAY_NORMAL);
        else
            Log.e(TAG, "lightSensor is null!!!\n");

        setTempTextUpdate(1);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        Log.e(TAG, "onTouchEvent: " + action + "\n");

        switch(action) {
            case (MotionEvent.ACTION_DOWN) :
                Log.d(TAG,"Action was DOWN");
            case (MotionEvent.ACTION_MOVE) :
                Log.d(TAG,"Action was MOVE");
            case (MotionEvent.ACTION_UP) :
                Log.d(TAG,"Action was UP");
                iScreenOnCnt = 8;
                return true;
            default :
                return super.onTouchEvent(event);
        }
    }

    public class LightSensorListener implements SensorEventListener {

        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
        public void onSensorChanged(SensorEvent event) {
            float acc = event.accuracy;
            float lux = event.values[0];

            Log.d(TAG, "lux: " + lux + "\n");

            /* 获取系统亮度，但是只有在设定亮度为手动修改的情况下有效 */
            /*ContentResolver contentResolver = getContentResolver();
            int defVal = 200, brVal;
            brVal = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, defVal);
            Log.d(TAG, "System Screen Brightness: " + brVal + "\n");*/

            int brightness;
            if (lux < 20) {
                    brightness = 0;
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

            iSettingBrightness = brightness;
        }
    }

    private int LCDBrightnessWrite(int brightness) {
        BufferedWriter bufWriter = null;
        FileWriter fileWriter = null;

        try {
            fileWriter = new FileWriter("/sys/class/leds/lcd-backlight/brightness");
            bufWriter = new BufferedWriter(fileWriter);
            bufWriter.write(String.valueOf(brightness));
            bufWriter.close();
            Log.d(TAG, "LCDBrightnessWrite: " + brightness + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return 0;
    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (count > 3600 || count == 0) { // 每小时更新一次数据
                updateData();
                count = 0;
            }
            count++;
            updateShow();
            super.handleMessage(msg);

            BrightnessDebounceSetting();
            SettingTempDebounceSetting();
            heaterStatusUpdate();

            if (iScreenOnCnt > 0)
                iScreenOnCnt -= 1;
            //Log.d(TAG, "Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC: " + Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC + "\n");
            //Log.d(TAG, "Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL: " + Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL + "\n");
        }
    };

    private int BrightnessDebounceSetting() {
        if (iSettingBrightness != iOldSettingBrightness) {
            iOldSettingBrightness = iSettingBrightness;
            iBrightnessDebounceCnt = 0;
        } else if (iBrightnessDebounceCnt < 3) {
            iBrightnessDebounceCnt++;
        } else if (iCurrentBrightness != iSettingBrightness) {
            if (iScreenOnCnt > 0 && iSettingBrightness == 0 && iCurrentBrightness != 11) {
                iCurrentBrightness = 11;
                LCDBrightnessWrite(iCurrentBrightness);
            } else if (iScreenOnCnt <=0) {
                iCurrentBrightness = iSettingBrightness;
                LCDBrightnessWrite(iCurrentBrightness);
            }

        } else if (iScreenOnCnt > 0 ) {
            if (iCurrentBrightness == 0) {
                iCurrentBrightness = 11;
                LCDBrightnessWrite(iCurrentBrightness);
            }
        }

        return 0;
    }

    private int updateShow() {
        Date date = new Date();
        SimpleDateFormat ft = new SimpleDateFormat("HH时mm分ss秒 \n yyyy年MM月dd日 EEEE", Locale.CHINESE);

        try {
            fInSys = new FileInputStream("/sys/class/hwmon/hwmon3/temp1_input");
            byte[] bytes = new byte[16];
            int n = 0;
            n = fInSys.read(bytes);
            float tempValue = Float.parseFloat(new String(bytes).substring(0, n - 1));
            tempValue = new BigDecimal(tempValue / 1000).setScale(1, BigDecimal.ROUND_UP).floatValue();
            currentTemperatureValue = tempValue;
            temperature = String.format("%2.1f", tempValue);
            //Log.d(TAG, "n: " + n + ", temperature: " + temperature + "\n");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fInSys.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            fInSys = new FileInputStream("/sys/class/hwmon/hwmon3/humidity1_input");
            byte[] bytes = new byte[16];
            int n = 0;
            n = fInSys.read(bytes);
            float humiValue = Float.parseFloat(new String(bytes).substring(0, n - 1));
            humiValue = new BigDecimal(humiValue / 1000).setScale(1, BigDecimal.ROUND_UP).floatValue();
            humidity = String.format("%2.1f%%", humiValue);
            //Log.d(TAG, "n: " + n + ", humidity: " + humidity + "\n");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fInSys.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        showBuf.delete(0, showBuf.length());
        showBuf.append(" 温度: " + temperature + "℃ \n 湿度: " + humidity + " \n\n" + ft.format(date));
        msp = new SpannableString(showBuf.toString());

        msp.setSpan(new TypefaceSpan("sans"), 0, msp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        msp.setSpan(new AbsoluteSizeSpan(36,true), 0, msp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        //设置字体大小（相对值,单位：像素） 参数表示为默认字体大小的多少倍
        msp.setSpan(new RelativeSizeSpan(2.0f), 0, 24, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);  //2.0f表示默认字体大小的两倍

        msp.setSpan(new ForegroundColorSpan(Color.BLUE), 0, 24, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        msp.setSpan(new BackgroundColorSpan(Color.YELLOW), 0, 24, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        msp.setSpan(new ForegroundColorSpan(Color.parseColor("#663366")), msp.length()-28, msp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        msp.setSpan(new BackgroundColorSpan(Color.parseColor("#CCCC99")), msp.length()-28, msp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        msp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, msp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);  //粗体

        tvShow.setText(msp);

        return 0;
    }

    private int updateData() {
        Date date = new Date();
        SimpleDateFormat ft = new SimpleDateFormat("MM-dd HH:mm");

        try {
            fInSys = new FileInputStream("/sys/class/hwmon/hwmon3/temp1_input");
            byte[] bytes = new byte[16];
            int n = 0;
            n = fInSys.read(bytes);
            float tempValue = Float.parseFloat(new String(bytes).substring(0, n - 1));
            tempValue = new BigDecimal(tempValue / 1000).setScale(1, BigDecimal.ROUND_UP).floatValue();
            temperature = String.format("%2.1f℃", tempValue);
            //Log.d(TAG, "n: " + n + ", temperature: " + temperature + "\n");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fInSys.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            fInSys = new FileInputStream("/sys/class/hwmon/hwmon3/humidity1_input");
            byte[] bytes = new byte[16];
            int n = 0;
            n = fInSys.read(bytes);
            float humiValue = Float.parseFloat(new String(bytes).substring(0, n - 1));
            humiValue = new BigDecimal(humiValue / 1000).setScale(1, BigDecimal.ROUND_UP).floatValue();
            humidity = String.format("%2.1f%%", humiValue);
            //Log.d(TAG, "n: " + n + ", humidity: " + humidity + "\n");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fInSys.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (strBuf.length() >= 36 * 72) {// 每小时更新一次数据的情况下，保存3天的数据
            String str = strBuf.substring(36 * 1, strBuf.length());
            strBuf.delete(0, strBuf.length());
            strBuf.append(str);
        }
        strBuf.append(ft.format(date) + " -- T: " + temperature + " -- H: " + humidity + "\n");
        msp = new SpannableString(strBuf.toString());

        // https://blog.csdn.net/pcaxb/article/details/47341249
        //设置字体(default,default-bold,monospace,serif,sans-serif)
        msp.setSpan(new TypefaceSpan("serif"), 0, msp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //设置字体大小（绝对值,单位：像素）
        msp.setSpan(new AbsoluteSizeSpan(16,true), 0, msp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        //设置字体前景色
        msp.setSpan(new ForegroundColorSpan(Color.BLACK), 0, msp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        //设置字体背景色
        msp.setSpan(new BackgroundColorSpan(Color.LTGRAY), 0, msp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        tvHello.setText(msp);
        Log.d(TAG, "strBuf length: " + strBuf.length() + "\n");

        // Update ScrollView
        ScrollView mScrollView = (ScrollView) findViewById(R.id.HelloScrollView);
        mScrollView.smoothScrollTo(0, tvHello.getBottom());

        return 0;
    }

    // 亮度调节参考 https://blog.csdn.net/wzy_1988/article/details/49472611
    private int setTempTextUpdate(int mode) {
        /*Window window = getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.screenBrightness = setTempValue/255.0f;
        window.setAttributes(lp);*/

        tempBuf.delete(0, tempBuf.length());
        tempBuf.append(" 设置温度: " + setTempValue + "℃\n");
        msp = new SpannableString(tempBuf.toString());
        msp.setSpan(new TypefaceSpan("sans"), 0, msp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        msp.setSpan(new AbsoluteSizeSpan(22,true), 0, msp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (mode == 0)
            msp.setSpan(new BackgroundColorSpan(Color.RED), 0, msp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        msp.setSpan(new ForegroundColorSpan(Color.BLUE), 0, msp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        setTempText.setText(msp);

        return 0;
    }

    private int SettingTempDebounceSetting() {
        if (iModifiedSettingTemperature != iOldModifiedSettingTemperature) {
            iOldModifiedSettingTemperature = iModifiedSettingTemperature;
            iSettingTemperatureDebounceCnt = 0;
        } else if (iSettingTemperatureDebounceCnt < 3) {
            iSettingTemperatureDebounceCnt++;
        } else if (iCurrentSettingTemperature != iModifiedSettingTemperature) {
            iCurrentSettingTemperature = iModifiedSettingTemperature;
            // SettingTemperature updated
            setTempTextUpdate(1);
        }

        return 0;
    }

    private int heaterStatusUpdate() {
        if (currentTemperatureValue < iCurrentSettingTemperature - 0.5) {
            // 开始加热
            heaterStatus = 1;
        } else if (currentTemperatureValue > iCurrentSettingTemperature + 0.5) {
            // 停止加热
            heaterStatus = 0;
        }
        setStatusTextUpdate();
        return 0;
    }

    private int setStatusTextUpdate() {
        statusBuf.delete(0, statusBuf.length());
        if (ifHeaterOnline != 0) {
            statusBuf.append("在线");
            if (heaterStatus != 0) {
                statusBuf.append(", 加热中");
            }
        } else {
            statusBuf.append("加热器不在线");
        }

        msp = new SpannableString(statusBuf.toString());
        msp.setSpan(new TypefaceSpan("sans"), 0, msp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        msp.setSpan(new AbsoluteSizeSpan(22,true), 0, msp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        msp.setSpan(new ForegroundColorSpan(Color.BLUE), 0, msp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        setStatusText.setText(msp);

        return 0;
    }



}