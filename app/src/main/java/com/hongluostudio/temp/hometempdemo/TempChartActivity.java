package com.hongluostudio.temp.hometempdemo;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.charts.CombinedChart.DrawOrder;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.XAxis.XAxisPosition;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

// MPChartExample/src/com/xxmassdeveloper/mpchartexample/CombinedChartActivity.java
public class TempChartActivity extends FragmentActivity {

    protected final String TAG = getClass().getSimpleName();
    private CombinedChart mChart;
    ArrayList<TempHumiData> thData;
    public ArrayList<String> timeStamp;
    private Timer mTimer;
    private int mNoTouchCnt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        //        WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_combined);

        mChart = findViewById(R.id.temp_humi_chart);
        mChart.setDescription("温度湿度显示");
        mChart.setBackgroundColor(Color.parseColor("#CFE3D7")); // 颜色名称：湖绿
        mChart.setDrawGridBackground(false);
        mChart.setDrawBarShadow(false);
        mChart.setDoubleTapToZoomEnabled(false);
        mChart.setScaleYEnabled(false);
        mChart.setPinchZoom(true);//挤压缩放

        //mChart.getXAxis().setLabelRotationAngle(-20);//设置x轴字体显示角度
        mChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);

        // draw bars behind lines
        mChart.setDrawOrder(new DrawOrder[] {
                DrawOrder.BAR, DrawOrder.LINE,
        });

        // 温度
        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setAxisLineColor(this.getResources().getColor(R.color.colorTemperature));
        leftAxis.setGridColor(this.getResources().getColor(R.color.colorTemperature));
        leftAxis.setTextColor(this.getResources().getColor(R.color.colorTempLabel));
        leftAxis.setAxisMinValue(-20.0f);
        leftAxis.setAxisMaxValue(60.0f);

        // 湿度
        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setDrawGridLines(true);
        rightAxis.setTextColor(this.getResources().getColor(R.color.colorHumility));
        rightAxis.setGridColor(this.getResources().getColor(R.color.colorHumility));
        rightAxis.setTextColor(this.getResources().getColor(R.color.colorHumiLabel));
        rightAxis.setAxisMinValue(0.0f);
        rightAxis.setAxisMaxValue(100.0f);

        XAxis xAxis = mChart.getXAxis();
        xAxis.setPosition(XAxisPosition.BOTH_SIDED);

        thData = (ArrayList<TempHumiData>) getIntent().getSerializableExtra("key");

        timeStamp = new ArrayList<>();
        for (int index = 0; index < thData.size(); index++) {
            Date date = thData.get(index).getDate();
            SimpleDateFormat dateTag = new SimpleDateFormat("yyyy-M-d HH:mm", Locale.CHINESE);
            timeStamp.add(dateTag.format(date));
        }

        CombinedData data = new CombinedData(timeStamp.toArray(new String[0]));
        //CombinedData data = new CombinedData(new String[] {"Jan", "Feb", "Mar"});

        data.setData(generateLineData());
        data.setData(generateBarData());

        mChart.setData(data);
        mChart.animateXY(800,800); //图表数据显示动画
        mChart.setVisibleXRangeMaximum(48); //设置初始屏幕显示条数
        mChart.moveViewToX(thData.size());  //显示最新数据条目
        mChart.invalidate(); //刷新显示

        mChart.setVisibleXRangeMaximum(thData.size()); //双指缩放可以显示更多条数
    }

    @Override
    protected void onStart() {
        super.onStart();
        //Log.e(TAG, "onStart()");
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        mTimer.cancel();
        overridePendingTransition(R.anim.move_left_in_activity, R.anim.move_right_out_activity);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Log.e(TAG, "onResume()");

        // 隐藏虚拟按键及状态栏
        View v = getWindow().getDecorView();
        int opt = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        v.setSystemUiVisibility(opt);

        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mNoTouchCnt++;
                //Log.e(TAG, "timer schedule, mNoTouchCnt: " + mNoTouchCnt);
                if (mNoTouchCnt > 30) { // 30秒无操作返回时钟主页面
                    finish();
                    mTimer.cancel();
                }
            }
        },1000,1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Log.e(TAG, "onPause()");
        mTimer.cancel();
    }

    @Override
    protected void onStop() {
        super.onStop();
        //Log.e(TAG, "onStop()");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //Log.e(TAG, "onDestroy()");
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
/*
        int action = event.getAction();
        Log.e(TAG, "dispatchTouchEvent() " + action);
*/
        mNoTouchCnt = 0; // 有触摸操作阻止定时返回
        return super.dispatchTouchEvent(event);
    }

    // 温度
    private LineData generateLineData() {

        LineData d = new LineData();

        ArrayList<Entry> entries = new ArrayList<Entry>();

        for (int index = 0; index < thData.size(); index++) {
            entries.add(new Entry(thData.get(index).getTemp(), index));
        }

        LineDataSet set = new LineDataSet(entries, "温度（℃）");
        set.setColor(this.getResources().getColor(R.color.colorTemperature));
        set.setLineWidth(2.5f);
        set.setCircleColor(this.getResources().getColor(R.color.colorTemperature));
        set.setCircleRadius(5f);
        set.setFillColor(this.getResources().getColor(R.color.colorTemperature));
        set.setDrawCubic(true);
        set.setDrawValues(true);
        set.setValueTextSize(10f);
        set.setValueTextColor(this.getResources().getColor(R.color.colorTempLabel));

        set.setAxisDependency(YAxis.AxisDependency.LEFT);

        d.addDataSet(set);

        return d;
    }

    // 湿度
    private BarData generateBarData() {

        BarData d = new BarData();

        ArrayList<BarEntry> entries = new ArrayList<BarEntry>();

        for (int index = 0; index < thData.size(); index++)
            entries.add(new BarEntry(thData.get(index).getHumi(), index));

        BarDataSet set = new BarDataSet(entries, "湿度（%）");
        set.setColor(this.getResources().getColor(R.color.colorHumility));
        set.setValueTextColor(this.getResources().getColor(R.color.colorHumiLabel));
        set.setValueTextSize(10f);
        d.addDataSet(set);

        set.setAxisDependency(YAxis.AxisDependency.RIGHT);

        return d;
    }
}
