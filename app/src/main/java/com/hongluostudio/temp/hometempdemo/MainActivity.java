package com.hongluostudio.temp.hometempdemo;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    protected final String TAG = getClass().getSimpleName();
    protected final int BRIGHTNESS_CHANGE_DELAY_CNT_MAX = 9;
    protected final int AFTER_TOUCH_SCREENON_CNT_MAX = 9;
    private static final int MIN_DISTANCE = 100;
    final int DATE_TIME_TO_MINUTE = 60 * 1000;
    final int DATE_TIME_TO_SECOND = 1000;
    final int CACHED_DATA_COUNT_FOR_SHOW = 30 * 24; // 最多保存30天的数据

    private final Timer mTimer = new Timer();
    private String mTemperatureStr, mHumidityStr;
    private int mUpdateLogCnt = 0;
    private SensorManager mSensorManager;
    private Sensor mLightSensor;

    private GestureDetector gestureDetector;
    private DemoGestureDetector demoGestureDetector;

    private int mSetBrightnessInt = 120;
    private int mOldSetBrightnessInt = 120;
    private int mCurrentBrightnessInt = 120;
    private int mBrightnessDebounceCnt = 0;

    private int mScreenOnCnt = 0;

    private Typeface dateTypeface;
    private Typeface timeTypeface;

    private int colorDateBg = 0;
    private int colorTimeBg = 0;
    private int colorTimeFg = -1;
    private int colorDateFg = -1;

    private int colorSolutionIndex = 0;
    private String[][] colorSolutionArry = {
            /* Date Backgroud,    Time Backgroud,    Time Frontgroud,    Date Frontgroud,    Name  */
            {  "#779649",         "#D3CBC5",         "#779649",          "#D3CBC5",          "白羊座|绿色"}, /* 白羊座|绿色 */
            /*  碧山,               藕丝秋半  */
            {  "#DA4268",         "#F3BCA7",         "#DA4268",          "#F3BCA7",          "金牛座|桃红"}, /* 金牛座|桃红 */
            /*  桃红,               豆沙  */
            {  "#06436F",         "#DDB078",         "#06436F",          "#DDB078",          "双子座|蓝色"}, /* 双子座|蓝色 */
            /*  蓝采和,             九斤黄  */
            {  "#F2C867",         "#6C945C",         "#F2C867",          "#6C945C",          "巨蟹座|黄色"}, /* 巨蟹座|黄色 */
            /*  嫩鹅黄,             庭芜绿  */
            {  "#EA5514",         "#B2B6B6",         "#EA5514",          "#B2B6B6",          "狮子座|橘色"}, /* 狮子座|橘色 */
            /*  黄丹,               月魄  */
            {  "#ABD5E1",         "#CFE3D7",         "#000000",          "#000000",          "处女座|水蓝"}, /* 处女座|水蓝 */
            /*  碧落,               湖绿  */
            {  "#A2191B",         "#0A2456",         "#FFFFFF",          "#FFFFFF",          "天秤座|红色"}, /* 天秤座|红色 */
            /*  朱樱,               骐驎  */
            {  "#F9D3E3",         "#88ABDA",         "#F9D3E3",          "#88ABDA",          "天蝎座|粉色"}, /* 天蝎座|粉色 */
            /*  盈盈,               窃蓝  */
            {  "#BC836B",         "#007175",         "#BC836B",          "#007175",          "射手座|咖色"}, /* 射手座|咖色 */
            /*  紫磨金,             青雘  */
            {  "#FFEE6F",         "#5AA4AE",         "#FFEE6F",          "#5AA4AE",          "摩羯座|黄色"}, /* 摩羯座|黄色  */
            /*  黄栗留,             天水碧  */
            {  "#7C5B3E",         "#DDBB99",         "#7C5B3E",          "#DDBB99",          "水瓶座|咖色"}, /* 水瓶座|咖色 */
            /*  骆驼褐,             如梦令  */
            {  "#422256",         "#B81A35",         "#FFFFFF",          "#FFFFFF",          "双鱼座|蓝紫"}, /* 双鱼座|蓝紫 */
            /*  凝夜紫,             朱孔阳  */
    };

    ArrayList<TempHumiData> thData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        thData = new ArrayList<TempHumiData>();

        colorDateBg = this.getResources().getColor(R.color.colorDateBg);
        colorTimeBg = this.getResources().getColor(R.color.colorTimeBg);
        colorTimeFg = this.getResources().getColor(R.color.colorTimeFg);
        colorDateFg = this.getResources().getColor(R.color.colorDateFg);

        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Message message = new Message();
                message.what = 1;
                mTimeHandler.sendMessage(message);
            }
        },1000,1000);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (mLightSensor != null)
            mSensorManager.registerListener(new LightSensorListener(), mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        else
            Log.e(TAG, "mLightSensor is null!\n");

        demoGestureDetector = new DemoGestureDetector();
        gestureDetector=new GestureDetector(this,demoGestureDetector);

        dateTypeface = Typeface.create(ResourcesCompat.getFont(this, R.font.google_font_zcool_kuai_le_regular), Typeface.NORMAL);
        //dateTypeface = Typeface.create(ResourcesCompat.getFont(this, R.font.google_font_long_cang_regular), Typeface.NORMAL);
        timeTypeface = Typeface.create(ResourcesCompat.getFont(this, R.font.google_font_fugaz_one_regular), Typeface.NORMAL);
        //timeTypeface = Typeface.create(ResourcesCompat.getFont(this, R.font.google_font_russo_one_regular), Typeface.NORMAL);
        //timeTypeface = Typeface.create(ResourcesCompat.getFont(this, R.font.google_font_stick_no_bills_bold), Typeface.NORMAL);

        //timeTypeface = Typeface.create(ResourcesCompat.getFont(this, R.font.google_font_black_ops_one_regular), Typeface.NORMAL);
        //timeTypeface = Typeface.create(ResourcesCompat.getFont(this, R.font.google_font_quantico_bold_italic), Typeface.NORMAL);
        //timeTypeface = Typeface.create(ResourcesCompat.getFont(this, R.font.google_font_racing_sans_one_regular), Typeface.NORMAL);
        //timeTypeface = Typeface.create(ResourcesCompat.getFont(this, R.font.google_font_rozha_one_regular), Typeface.NORMAL);
        //timeTypeface = Typeface.create(ResourcesCompat.getFont(this, R.font.google_font_squada_one_regular), Typeface.NORMAL);
        //timeTypeface = Typeface.create(ResourcesCompat.getFont(this, R.font.google_font_vt323_regular), Typeface.NORMAL);

        //Log.e(TAG, "onCreate()\n");
    }

    @Override
    protected void onStart() {
        super.onStart();

        // 隐藏虚拟按键及状态栏
        /* https://blog.csdn.net/liuyuejinqiu/article/details/70230963 */
        /* https://developer.android.com/training/system-ui/navigation#java */
        View v = getWindow().getDecorView();
        int opt = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        v.setSystemUiVisibility(opt);

        //Log.e(TAG, "onStart()\n");
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Log.e(TAG, "onResume()\n");
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Log.e(TAG, "onPause()\n");
    }

    @Override
    protected void onStop() {
        super.onStop();
        //Log.e(TAG, "onStop()\n");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //Log.e(TAG, "onDestroy()\n");
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        //Log.d(TAG, "onTouchEvent: " + action + "\n");

        gestureDetector.onTouchEvent(event);

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

            //Log.d(TAG, "original lux: " + lux + "\n");
            if (lux > 5)
                lux = lux * 6;
            //Log.d(TAG, "manipulated lux: " + lux + "\n");

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
            if (mUpdateLogCnt > 720/*3600*/ || mUpdateLogCnt == 0) { // 1小时更新一次数据
                colorSolutionIndex++;
                if (colorSolutionIndex >= colorSolutionArry.length) {
                    colorSolutionIndex = 0;
                }
                updateColorSolution();
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
        float tempValue = 0.0f;
        float humiValue = 0.0f;

        try {
            inputStream = new FileInputStream("/sys/class/hwmon/hwmon3/temp1_input");
            byte[] bytes = new byte[16];
            int n = 0;
            n = inputStream.read(bytes);
            tempValue = Float.parseFloat(new String(bytes).substring(0, n - 1));
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
            humiValue = Float.parseFloat(new String(bytes).substring(0, n - 1));
            humiValue = new BigDecimal(humiValue / 1000).setScale(1, BigDecimal.ROUND_UP).floatValue();
            mHumidityStr = String.format("%2.1f%%", humiValue);
            //Log.d(TAG, "n: " + n + ", mHumidityStr: " + mHumidityStr + "\n");

        } catch (Exception e) {
            e.printStackTrace();
        }

        Date nowDate = new Date();
/*
        SimpleDateFormat dateTag = new SimpleDateFormat("yyyy年M月d日 HH:mm:ss", Locale.CHINESE);
        if (thData.size() == 0) {
            Log.d(TAG, "nowDate: " + dateTag.format(nowDate) + ", thData.size(): " + thData.size());
        } else {
            Log.d(TAG, "nowDate: " + dateTag.format(nowDate) + ", thData.size(): " + thData.size() + ", diff: " + (nowDate.getTime() - thData.get(thData.size() - 1).getDate().getTime()) / 1000);
        }
*/
        long timeDelta = 0;
        if (thData.size() > 0) {
            timeDelta = nowDate.getTime() - thData.get(thData.size() - 1).getDate().getTime();
        }
        if (thData.size() == 0 || timeDelta > 30 * DATE_TIME_TO_MINUTE) {  // 数据采样间隔为30分钟，一天采样24
            TempHumiData t = new TempHumiData();
            t.setDate(nowDate);
            t.setTemp(tempValue);
            t.setHumi(humiValue);
            thData.add(t);
            if (thData.size() > CACHED_DATA_COUNT_FOR_SHOW) {
                thData.remove(0);
            }
        }

        return 0;
    }

    private int updateShow() {
        SpannableString ss;
        TextView tv;
        StringBuffer strBuf = new StringBuffer();
        Date date = new Date();

        // 公历
        SimpleDateFormat sunarDate = new SimpleDateFormat("yyyy年M月\nd日", Locale.CHINESE);
        strBuf.delete(0, strBuf.length());
        strBuf.append(sunarDate.format(date));
        ss = new SpannableString(strBuf.toString());
        ss.setSpan(new CustomTypefaceSpan("sans", dateTypeface), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new AbsoluteSizeSpan(24,true), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // 通过增大加粗强调日期
        ss.setSpan(new RelativeSizeSpan(3.5f), ss.length() - 3, ss.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new StyleSpan(Typeface.BOLD), ss.length() - 3, ss.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(colorDateFg), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv = (TextView) findViewById(R.id.tvSunarDataShow);
        tv.setText(ss);

        // 星期
        SimpleDateFormat weekDate = new SimpleDateFormat("EEEE", Locale.CHINESE);
        strBuf.delete(0, strBuf.length());
        strBuf.append("星期" + weekDate.format(date).substring(2));
        ss = new SpannableString(strBuf.toString());
        ss.setSpan(new CustomTypefaceSpan("sans", dateTypeface), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new AbsoluteSizeSpan(24,true), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // 通过增大加粗强调星期
        ss.setSpan(new RelativeSizeSpan(3.0f), ss.length() - 1, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new StyleSpan(Typeface.BOLD), ss.length() - 1, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(colorDateFg), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
        ss.setSpan(new CustomTypefaceSpan("sans", dateTypeface), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new AbsoluteSizeSpan(24,true), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // 通过增大加粗强调日期
        ss.setSpan(new RelativeSizeSpan(3.0f), ss.length() - 2, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new StyleSpan(Typeface.BOLD), ss.length() - 2, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(colorDateFg), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv = (TextView) findViewById(R.id.tvLunarDataShow);
        tv.setText(ss);

        // 温湿度
        strBuf.delete(0, strBuf.length());
        strBuf.append(" 温度: " + mTemperatureStr + "\n湿度: " + mHumidityStr);
        ss = new SpannableString(strBuf.toString());
        ss.setSpan(new CustomTypefaceSpan("sans", dateTypeface), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new AbsoluteSizeSpan(24,true), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new StyleSpan(Typeface.BOLD), ss.length() - 15, ss.length() - 11 , Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new StyleSpan(Typeface.BOLD), ss.length() - 5, ss.length() - 1 , Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(colorDateFg), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv = (TextView) findViewById(R.id.tvTemHumiDateShow);
        tv.setText(ss);

        // 时间
        SimpleDateFormat tTime = new SimpleDateFormat("HH:mm\ns", Locale.CHINESE);
        strBuf.delete(0, strBuf.length());
        strBuf.append(tTime.format(date));
        ss = new SpannableString(strBuf.toString());
        ss.setSpan(new CustomTypefaceSpan("sans", timeTypeface), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new AbsoluteSizeSpan(54,true), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //设置字体大小（相对值,单位：像素） 参数表示为默认字体大小的多少倍
        ss.setSpan(new RelativeSizeSpan(4.0f), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);  //2.0f表示默认字体大小的两倍
        ss.setSpan(new ForegroundColorSpan(colorTimeFg), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //ss.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);  //粗体
        tv = (TextView) findViewById(R.id.tvTimeShowId);
        tv.setText(ss);

        //ssTime.setSpan(new BackgroundColorSpan(Color.YELLOW), 0, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //ssTime.setSpan(new ForegroundColorSpan(Color.WHITE), ssTime.length()-15, ssTime.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        //ssTime.setSpan(new BackgroundColorSpan(Color.parseColor("#CCCC99")), ssTime.length()-15, ssTime.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return 0;
    }

    private void updateColorSolution() {
        //Log.d(TAG, "colorSolutionArry[" + colorSolutionIndex + "][0]: " + colorSolutionArry[colorSolutionIndex][0]);
        colorDateBg = Color.parseColor(colorSolutionArry[colorSolutionIndex][0]);
        //Log.d(TAG, "colorSolutionArry[" + colorSolutionIndex + "][1]: " + colorSolutionArry[colorSolutionIndex][1]);
        colorTimeBg = Color.parseColor(colorSolutionArry[colorSolutionIndex][1]);
        //Log.d(TAG, "colorSolutionArry[" + colorSolutionIndex + "][2]: " + colorSolutionArry[colorSolutionIndex][2]);
        colorTimeFg = Color.parseColor(colorSolutionArry[colorSolutionIndex][2]);
        //Log.d(TAG, "colorSolutionArry[" + colorSolutionIndex + "][3]: " + colorSolutionArry[colorSolutionIndex][3]);
        colorDateFg = Color.parseColor(colorSolutionArry[colorSolutionIndex][3]);

        ConstraintLayout layoutDate = (ConstraintLayout)findViewById(R.id.ConstraintLayoutLeft);
        ConstraintLayout layoutTime = (ConstraintLayout)findViewById(R.id.ConstraintLayoutRight);

        layoutDate.setBackgroundColor(colorDateBg);
        layoutTime.setBackgroundColor(colorTimeBg);
        updateShow();
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

    class DemoGestureDetector extends GestureDetector.SimpleOnGestureListener{

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if(e1.getX()-e2.getX()>MIN_DISTANCE){

/*
                thData.removeAll(thData);
                // 填充数据用于测试
                int cntData = 30 * 24;
                for (int i = 0; i < cntData; i++) {
                    Date nowDate = new Date();
                    TempHumiData data = new TempHumiData();
                    data.setDate(new Date(nowDate.getTime() + (long)i * 30 * 60 * 1000)); // 30分钟递进
                    SimpleDateFormat dateTag = new SimpleDateFormat("yyyy年M月d日 HH:mm:ss", Locale.CHINESE);
                    Log.d(TAG, "i: " + i + ", date: " + dateTag.format(data.getDate()));
                    data.setTemp((float) (i * 80)/cntData - 20);
                    data.setHumi((float) cntData - i);
                    thData.add(data);
                    if (thData.size() > cntData) {
                        thData.remove(0);
                    }
                }
*/

                Intent intent = new Intent();
                intent.putExtra("key", thData);
                intent.setClass(MainActivity.this, TempChartActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.move_right_in_activity, R.anim.move_left_out_activity);
                //Toast.makeText(MainActivity.this,"左滑", Toast.LENGTH_SHORT).show();
            }else if(e2.getX()-e1.getX()>MIN_DISTANCE){
                Toast.makeText(MainActivity.this,"右滑",Toast.LENGTH_SHORT).show();
            }else if(e1.getY()-e2.getY()>MIN_DISTANCE){
                colorSolutionIndex--;
                if (colorSolutionIndex < 0) {
                    colorSolutionIndex = colorSolutionArry.length - 1;
                }
                Toast.makeText(MainActivity.this,"上滑 " + colorSolutionArry[colorSolutionIndex][4],Toast.LENGTH_SHORT).show();
                updateColorSolution();
            }else if(e2.getY()-e1.getY()>MIN_DISTANCE){
                Toast.makeText(MainActivity.this,"下滑 " + colorSolutionArry[colorSolutionIndex][4],Toast.LENGTH_SHORT).show();
                updateColorSolution();
                colorSolutionIndex++;
                if (colorSolutionIndex >= colorSolutionArry.length) {
                    colorSolutionIndex = 0;
                }
                Toast.makeText(MainActivity.this,"下滑 " + colorSolutionArry[colorSolutionIndex][4],Toast.LENGTH_SHORT).show();
                updateColorSolution();
            }
            return true;
        }
    }

    public class CustomTypefaceSpan extends TypefaceSpan {
        private final Typeface newType;

        public CustomTypefaceSpan(String family, Typeface type) {
            super(family);
            newType = type;
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            applyCustomTypeFace(ds, newType);
        }

        @Override
        public void updateMeasureState(TextPaint paint) {
            applyCustomTypeFace(paint, newType);
        }

        private void applyCustomTypeFace(Paint paint, Typeface tf) {
            paint.setTypeface(tf);
        }
    }
}