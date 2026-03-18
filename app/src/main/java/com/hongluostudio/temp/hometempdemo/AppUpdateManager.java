package com.hongluostudio.temp.hometempdemo;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.content.FileProvider;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 应用自动更新管理器（支持按设备编号静默自动更新）
 *
 * 服务器端 version.json 格式：
 * {
 *   "versionCode": 2,
 *   "versionName": "1.1",
 *   "apkUrl": "http://hongluostudio.com/update/hometempdemo.apk",
 *   "updateDesc": "1. 新增MQTT上报\n2. 温湿度历史持久化",
 *   "silentDevices": ["device_android_id_1", "device_android_id_2"]
 * }
 *
 * silentDevices 列表中的设备：发现新版本直接静默下载安装，无任何弹窗。
 * 不在列表中的设备：弹窗提示10秒，无操作自动关闭。
 */
public class AppUpdateManager {

    private static final String TAG = "AppUpdateManager";

    // ★★★ 修改为实际的版本检查接口地址 ★★★
    private static final String VERSION_CHECK_URL =
            "http://hongluostudio.com/dev/update/version.json";

    // 弹窗无操作自动关闭的秒数
    private static final int AUTO_DISMISS_SECONDS = 10;

    // -------------------------------------------------------------------------
    // 获取设备唯一编号（Android ID，稳定且无需额外权限）
    // -------------------------------------------------------------------------
    public static String getDeviceId(Context context) {
        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return (androidId != null && !androidId.isEmpty()) ? androidId : "unknown";
    }

    // -------------------------------------------------------------------------
    // 公开入口：在主线程调用，自动切到后台线程
    // -------------------------------------------------------------------------
    public static void checkUpdate(Context context) {
        String deviceId = getDeviceId(context);
        Log.d(TAG, "Device ID: " + deviceId);
        new CheckVersionTask(context, deviceId).execute(VERSION_CHECK_URL);
    }

    // -------------------------------------------------------------------------
    // Step 1: 后台请求 version.json
    // -------------------------------------------------------------------------
    private static class CheckVersionTask extends AsyncTask<String, Void, JSONObject> {

        private final Context mContext;
        private final String  mDeviceId;

        CheckVersionTask(Context context, String deviceId) {
            mContext  = context;
            mDeviceId = deviceId;
        }

        @Override
        protected JSONObject doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    conn.disconnect();
                    return new JSONObject(sb.toString());
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "checkUpdate failed: " + e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONObject result) {
            if (result == null) { Log.d(TAG, "No version info."); return; }
            try {
                int    serverCode = result.getInt("versionCode");
                String serverName = result.getString("versionName");
                String apkUrl     = result.getString("apkUrl");
                String updateDesc = result.optString("updateDesc", "有新版本可用");

                // 判断本设备是否在静默更新名单内
                boolean silent = false;
                if (result.has("silentDevices")) {
                    JSONArray list = result.getJSONArray("silentDevices");
                    for (int i = 0; i < list.length(); i++) {
                        if (mDeviceId.equals(list.getString(i))) { silent = true; break; }
                    }
                }

                int localCode = getLocalVersionCode(mContext);
                Log.d(TAG, "local=" + localCode + " server=" + serverCode + " silent=" + silent);

                if (serverCode > localCode) {
                    if (silent) {
                        Log.d(TAG, "Silent update for device: " + mDeviceId);
                        new DownloadApkTask(mContext, true).execute(apkUrl);
                    } else {
                        showUpdateDialog(mContext, serverName, updateDesc, apkUrl);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Parse failed: " + e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // 弹窗（非静默设备）：10秒无操作自动关闭，标题实时显示倒计时
    // -------------------------------------------------------------------------
    private static void showUpdateDialog(final Context ctx,
                                         final String name,
                                         final String desc,
                                         final String url) {

        final AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle("发现新版本 v" + name + "（" + AUTO_DISMISS_SECONDS + "秒后自动关闭）")
                .setMessage(desc)
                .setCancelable(false)
                .setPositiveButton("立即更新", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        d.dismiss();
                        new DownloadApkTask(ctx, false).execute(url);
                    }
                })
                .setNegativeButton("稍后", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        d.dismiss();
                    }
                })
                .create();
        dialog.show();

        // 倒计时 Runnable：每秒更新标题，到0时自动 dismiss
        final Handler handler = new Handler();
        final int[] secondsLeft = {AUTO_DISMISS_SECONDS};
        final Runnable[] countdown = {null};  // 数组包装，让匿名类内部能引用自身
        countdown[0] = new Runnable() {
            @Override
            public void run() {
                secondsLeft[0]--;
                if (!dialog.isShowing()) return;  // 用户已手动点击，停止倒计时
                if (secondsLeft[0] <= 0) {
                    dialog.dismiss();
                } else {
                    dialog.setTitle("发现新版本 v" + name
                            + "（" + secondsLeft[0] + "秒后自动关闭）");
                    handler.postDelayed(countdown[0], 1000);
                }
            }
        };
        handler.postDelayed(countdown[0], 1000);
    }

    // -------------------------------------------------------------------------
    // Step 2: 下载 APK（静默模式无进度条）
    // -------------------------------------------------------------------------
    private static class DownloadApkTask extends AsyncTask<String, Integer, File> {

        private final Context        mCtx;
        private final boolean        mSilent;
        private       ProgressDialog mProgress;

        DownloadApkTask(Context ctx, boolean silent) {
            mCtx    = ctx;
            mSilent = silent;
            if (!silent) {
                mProgress = new ProgressDialog(ctx);
                mProgress.setTitle("正在下载更新...");
                mProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgress.setCancelable(false);
                mProgress.setMax(100);
                mProgress.show();
            }
        }

        @Override
        protected File doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(60000);
                conn.connect();

                int total = conn.getContentLength();
                File dir  = mCtx.getExternalCacheDir();
                if (dir == null) dir = mCtx.getCacheDir();
                File apk = new File(dir, "update.apk");

                InputStream is  = conn.getInputStream();
                FileOutputStream os = new FileOutputStream(apk);
                byte[] buf = new byte[4096];
                int down = 0, len;
                while ((len = is.read(buf)) != -1) {
                    os.write(buf, 0, len);
                    down += len;
                    if (!mSilent && total > 0) publishProgress(down * 100 / total);
                }
                os.flush(); os.close(); is.close(); conn.disconnect();
                return apk;
            } catch (Exception e) {
                Log.e(TAG, "Download failed: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... v) {
            if (mProgress != null) mProgress.setProgress(v[0]);
        }

        @Override
        protected void onPostExecute(File apk) {
            if (!mSilent && mProgress != null) mProgress.dismiss();
            if (apk != null && apk.exists()) {
                installApk(mCtx, apk);
            } else if (!mSilent) {
                new AlertDialog.Builder(mCtx)
                        .setTitle("下载失败").setMessage("请检查网络后重试")
                        .setPositiveButton("确定", null).show();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Step 3: 安装（兼容 Android 7.0+ FileProvider）
    // -------------------------------------------------------------------------
    private static void installApk(Context ctx, File apk) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", apk);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            uri = Uri.fromFile(apk);
        }
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        ctx.startActivity(intent);
    }

    private static int getLocalVersionCode(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) { return 0; }
    }
}
