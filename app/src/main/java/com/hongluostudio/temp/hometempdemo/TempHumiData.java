package com.hongluostudio.temp.hometempdemo;

import java.io.Serializable;
import java.util.Date;

public class TempHumiData  implements Serializable {
    public float temp;
    public float humi;
    public Date date;
    /*
    public Date getDate() {
        return date;
    }

    public float getHumi() {
        return humi;
    }

    public float getTemp() {
        return temp;
    }
    */
}
