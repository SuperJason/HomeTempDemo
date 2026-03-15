package com.hongluostudio.temp.hometempdemo;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * 温湿度历史数据持久化存储
 *
 * 采用 CSV 格式存储在应用内部存储（无需任何额外权限），文件位置：
 *   /data/data/{packageName}/files/temp_humi_history.csv
 *
 * CSV 格式（每行一条记录）：
 *   时间戳(ms),温度,湿度
 *   例：1700000000000,25.3,60.5
 *
 * 使用方法（在 MainActivity 中）：
 *   // 启动时加载历史数据（替换 thData = new ArrayList<>()）：
 *   thData = TempHumiStore.load(this, CACHED_DATA_COUNT_FOR_SHOW);
 *
 *   // 每次 thData 有新数据追加后，保存到本地（在 getTemperatureHumidity 末尾）：
 *   TempHumiStore.save(this, thData);
 */
public class TempHumiStore {

    private static final String TAG       = "TempHumiStore";
    private static final String FILE_NAME = "temp_humi_history.csv";

    // -------------------------------------------------------------------------
    // 加载历史数据（启动时调用）
    // 只保留最近 maxCount 条，超出部分自动裁剪
    // -------------------------------------------------------------------------
    public static ArrayList<TempHumiData> load(Context context, int maxCount) {
        ArrayList<TempHumiData> list = new ArrayList<>();
        File file = getFile(context);

        if (!file.exists()) {
            Log.d(TAG, "No history file found, starting fresh.");
            return list;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(",");
                if (parts.length < 3) continue;

                TempHumiData data = new TempHumiData();
                data.setDate(new Date(Long.parseLong(parts[0].trim())));
                data.setTemp(Float.parseFloat(parts[1].trim()));
                data.setHumi(Float.parseFloat(parts[2].trim()));
                list.add(data);
            }
            Log.d(TAG, "Loaded " + list.size() + " records.");
        } catch (Exception e) {
            Log.e(TAG, "Load failed: " + e.getMessage());
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignored) {}
        }

        // 裁剪超出上限的旧数据（保留最新的 maxCount 条）
        while (list.size() > maxCount) list.remove(0);

        return list;
    }

    // -------------------------------------------------------------------------
    // 保存全量数据（每次有新数据时调用）
    // 采用全量覆盖写入，保证文件与内存数据一致
    // -------------------------------------------------------------------------
    public static void save(Context context, ArrayList<TempHumiData> list) {
        if (list == null || list.isEmpty()) return;

        File file = getFile(context);
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(file, false)); // false = 覆盖
            writer.write("# timestamp_ms,temperature,humidity");
            writer.newLine();
            for (TempHumiData data : list) {
                writer.write(data.getDate().getTime()
                        + "," + data.getTemp()
                        + "," + data.getHumi());
                writer.newLine();
            }
            writer.flush();
            Log.d(TAG, "-x-1-Saved " + list.size() + " records.");
        } catch (IOException e) {
            Log.e(TAG, "Save failed: " + e.getMessage());
        } finally {
            if (writer != null) try { writer.close(); } catch (IOException ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // 追加单条数据（性能更好，适合频繁写入时使用）
    // 注意：追加模式不做裁剪，需配合 trimIfNeeded() 定期维护
    // -------------------------------------------------------------------------
    public static void append(Context context, TempHumiData data) {
        File file = getFile(context);
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(file, true)); // true = 追加
            writer.write(data.getDate().getTime()
                    + "," + data.getTemp()
                    + "," + data.getHumi());
            writer.newLine();
        } catch (IOException e) {
            Log.e(TAG, "Append failed: " + e.getMessage());
        } finally {
            if (writer != null) try { writer.close(); } catch (IOException ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // 删除本地历史文件
    // -------------------------------------------------------------------------
    public static void clear(Context context) {
        File file = getFile(context);
        if (file.exists()) file.delete();
        Log.d(TAG, "History cleared.");
    }

    // -------------------------------------------------------------------------
    // 获取文件大小（KB），便于调试
    // -------------------------------------------------------------------------
    public static long getFileSizeKB(Context context) {
        File file = getFile(context);
        return file.exists() ? file.length() / 1024 : 0;
    }

    private static File getFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }
}
