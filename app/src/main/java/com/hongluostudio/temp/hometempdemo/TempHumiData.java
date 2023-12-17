package com.hongluostudio.temp.hometempdemo;

import java.io.Serializable;
import java.util.Date;

public class TempHumiData  implements Serializable {
    private float temp;
    private float humi;
    private Date date;

    public Date getDate() {
        return date;
    }

    public float getHumi() {
        return humi;
    }

    public float getTemp() {
        return temp;
    }

    public void setTemp(float t) {
        temp = t;
    }

    public void setHumi(float h) {
        humi = h;
    }

    public void setDate(Date d) {
        date = d;
    }
}
