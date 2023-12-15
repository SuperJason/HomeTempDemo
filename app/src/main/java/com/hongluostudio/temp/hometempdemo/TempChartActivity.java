package com.hongluostudio.temp.hometempdemo;

import android.app.Activity;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.util.ArrayList;

// https://blog.51cto.com/u_16175437/6757965
// https://zhuanlan.zhihu.com/p/22134046
public class TempChartActivity extends Activity implements
        OnChartValueSelectedListener {

    protected final String TAG = getClass().getSimpleName();
    private BarChart mChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_temp_chart);

        // 隐藏虚拟按键及状态栏
        /* https://blog.csdn.net/liuyuejinqiu/article/details/70230963 */
        /* https://developer.android.com/training/system-ui/navigation#java */
        View v = getWindow().getDecorView();
        int opt = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        v.setSystemUiVisibility(opt);

        mChart = (BarChart) findViewById(R.id.chart);
        mChart.setOnChartValueSelectedListener(this);
        mChart.setDescription("市场拓展表");
        mChart.setNoDataTextDescription("You need to provide data for the chart.");
        mChart.setDrawValueAboveBar(true);//将Y数据显示在点的上方

        // mChart.setDrawBorders(true);

        // scaling can now only be done on x- and y-axis separately
        mChart.setPinchZoom(true);//挤压缩放

        mChart.setDrawBarShadow(false);
        mChart.setDrawGridBackground(false);
        mChart.setScaleYEnabled(false);
        mChart.setDoubleTapToZoomEnabled(false);//双击缩放
        mChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);//x轴位置
        // create a custom MarkerView (extend MarkerView) and specify the layout
        // to use for it
        //自定义   MarkerView
        MyMarkerView mv = new MyMarkerView(this, R.layout.custom_marker_view);

        // define an offset to change the original position of the marker
        // (optional)
        // mv.setOffsets(-mv.getMeasuredWidth() / 2, -mv.getMeasuredHeight());

        // set the marker to the chart
        mChart.setMarkerView(mv);
        Legend l = mChart.getLegend();//图例
        l.setPosition(Legend.LegendPosition.RIGHT_OF_CHART_INSIDE);
        l.setTextSize(10f);
        l.setFormSize(10f); // set the size of the legend forms/shapes
        l.setForm(Legend.LegendForm.CIRCLE);
        l.setWordWrapEnabled(true);
        l.setPosition(Legend.LegendPosition.BELOW_CHART_CENTER);

        XAxis xl = mChart.getXAxis();
        xl.setLabelRotationAngle(-20);//设置x轴字体显示角度
        //xl.setPosition(XAxisPosition.BOTTOM);


        YAxis leftAxis = mChart.getAxisLeft();
        //leftAxis.setValueFormatter(new LargeValueFormatter());//
        leftAxis.setValueFormatter(new MyYValueFormatter());//自定义y数据格式化方式
        leftAxis.setDrawGridLines(false);//是否画线
        leftAxis.setSpaceTop(30f);
        leftAxis.setAxisMinValue(0f); // this replaces setStartAtZero(true)
        mChart.getAxisRight().setEnabled(false);
        setData(10);
    }

    public void setData(int num) {

        ArrayList<String> xVals = new ArrayList<String>();
        for (int i = 0; i < num; i++) {
            xVals.add( "小谢"+ i);
        }

        ArrayList<BarEntry> yVals1 = new ArrayList<BarEntry>();
        ArrayList<BarEntry> yVals2 = new ArrayList<BarEntry>();
        ArrayList<BarEntry> yVals3 = new ArrayList<BarEntry>();


        for (int i = 0; i < num; i++) {
            float val = (float) (Math.random() * num);
            yVals1.add(new BarEntry(val, i));
        }

        for (int i = 0; i < num; i++) {
            float val = (float) (Math.random() * num);;
            yVals2.add(new BarEntry(val, i));
        }

        for (int i = 0; i < num; i++) {
            float val = (float) (Math.random() * num);
            yVals3.add(new BarEntry(val, i));
        }

        // create 3 datasets with different types
        BarDataSet set1 = new BarDataSet(yVals1, "一季度");
        // set1.setColors(ColorTemplate.createColors(getApplicationContext(),
        // ColorTemplate.FRESH_COLORS));
        set1.setColor(Color.rgb(104, 241, 175));
        BarDataSet set2 = new BarDataSet(yVals2, "二季度");
        set2.setColor(Color.rgb(164, 228, 251));
        BarDataSet set3 = new BarDataSet(yVals3, "三季度");
        set3.setColor(Color.rgb(242, 247, 158));

        ArrayList<IBarDataSet> dataSets = new ArrayList<IBarDataSet>();
        dataSets.add(set1);
        dataSets.add(set2);
        dataSets.add(set3);

        BarData data = new BarData(xVals, dataSets);
        // data.setValueFormatter(new LargeValueFormatter());

        // add space between the dataset groups in percent of bar-width
        data.setValueFormatter(new CustomerValueFormatter());
        data.setDrawValues(true);
        data.setValueTextColor(Color.BLACK);
        data.setValueTextSize(13);
        data.setGroupSpace(80f);//设置组数据间距
        //data.setValueTypeface(tf);

        mChart.setData(data);
        mChart.animateXY(800,800);//图表数据显示动画
        mChart.setVisibleXRangeMaximum(15);//设置屏幕显示条数
        mChart.invalidate();
    }

    @Override
    public void onValueSelected(Entry e, int dataSetIndex, Highlight h) {
        Log.i("Activity", "Selected: " + e.toString() + ", dataSet: "
                + dataSetIndex);
        Toast.makeText(TempChartActivity.this, e.getXIndex()+"valu"+e.getVal()+e.getData(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNothingSelected() {
        Log.i("Activity", "Nothing selected.");
    }
}
