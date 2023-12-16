package com.hongluostudio.temp.hometempdemo;

import android.support.v4.app.FragmentActivity;

import java.util.ArrayList;

/**
 * Baseclass of all Activities of the Demo Application.
 *
 * @author Philipp Jahoda
 */
public abstract class DemoBase extends FragmentActivity {

    public ArrayList<String> timeStamp;

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.move_left_in_activity, R.anim.move_right_out_activity);
    }
}