package com.hongluostudio.temp.hometempdemo;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import org.json.JSONObject;

/**
 * MQTT 上报管理器
 *
 * 将温度、湿度、环境光数据上报到 hongluostudio.com 的 Mosquitto 服务器。
 *
 * ★ 依赖（添加到 app/build.gradle 的 dependencies 中）：
 *   implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
 *   implementation 'org.eclipse.paho:org.eclipse.paho.android.service:1.1.1'
 *
 * ★ AndroidManifest.xml 需要额外声明：
 *   <!-- Paho Android Service -->
 *   <service android:name="org.eclipse.paho.android.service.MqttService" />
 *
 * 上报 Topic 格式：
 *   hometempdemo/{deviceId}/sensor
 *
 * 上报 JSON Payload 格式：
 *   {
 *     "deviceId": "xxxxxxxx",
 *     "temperature": 25.3,
 *     "humidity": 60.5,
 *     "light": 320.0,
 *     "timestamp": 1700000000000
 *   }
 *
 * 使用方法（在 MainActivity 中）：
 *   // onCreate():
 *   mMqttReporter = new MqttReporter(this);
 *   mMqttReporter.connect();
 *
 *   // 在读取到新数据后（getTemperatureHumidity 末尾）：
 *   mMqttReporter.publish(tempValue, humiValue, luxValue);
 *
 *   // onDestroy():
 *   mMqttReporter.disconnect();
 */

public class MqttReporter {

    private static final String TAG = "MqttReporter";

    // ★★★ Mosquitto 服务器地址和端口（默认1883，TLS用8883）★★★
    private static final String BROKER_URL  = "tcp://hongluostudio.com:1883";
    // 若服务器开启了认证，填写用户名和密码，否则留空
    private static final String MQTT_USER   = "sensors";
    private static final String MQTT_PASS   = "asdf123456";

    private final Context            mContext;
    private final String             mDeviceId;
    private final String             mTopic;
    private       MqttAndroidClient  mClient;
    private       boolean            mConnected = false;

    public MqttReporter(Context context) {
        mContext  = context.getApplicationContext();
        mDeviceId = getDeviceId(context);
        mTopic    = "hometempdemo/" + mDeviceId + "/sensor";
        Log.d(TAG, "Topic: " + mTopic);
    }

    // -------------------------------------------------------------------------
    // 连接到 Broker
    // -------------------------------------------------------------------------
    public void connect() {
        String clientId = "HomeTempDemo_" + mDeviceId;
        mClient = new MqttAndroidClient(mContext, BROKER_URL, clientId);

        mClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                mConnected = true;
                Log.d(TAG, "MQTT connected: " + serverURI + (reconnect ? " (reconnect)" : ""));
            }

            @Override
            public void connectionLost(Throwable cause) {
                mConnected = false;
                Log.e(TAG, "MQTT connection lost: " + (cause != null ? cause.getMessage() : ""));
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) { }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) { }
        });

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);  // 断线自动重连
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);
        if (!MQTT_USER.isEmpty()) {
            options.setUserName(MQTT_USER);
            options.setPassword(MQTT_PASS.toCharArray());
        }

        try {
            mClient.connect(options, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken token) {
                    mConnected = true;
                    Log.d(TAG, "MQTT connect success.");
                }
                @Override public void onFailure(IMqttToken token, Throwable ex) {
                    mConnected = false;
                    Log.e(TAG, "MQTT connect failed: " + (ex != null ? ex.getMessage() : ""));
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "connect() exception: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // 发布传感器数据
    // -------------------------------------------------------------------------
    public void publish(float temperature, float humidity, float light) {
        if (mClient == null || !mConnected) {
            Log.w(TAG, "Not connected, skip publish.");
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("deviceId",    mDeviceId);
            json.put("temperature", Math.round(temperature * 10.0) / 10.0);
            json.put("humidity",    Math.round(humidity    * 10.0) / 10.0);
            json.put("light",       (double) light);
            json.put("timestamp",   System.currentTimeMillis());

            MqttMessage msg = new MqttMessage(json.toString().getBytes("UTF-8"));
            msg.setQos(1);      // QoS 1: 至少送达一次
            msg.setRetained(true); // Retained：新订阅者可立即获取最新值

            mClient.publish(mTopic, msg, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken token) {
                    Log.d(TAG, "Published: " + json.toString());
                }
                @Override public void onFailure(IMqttToken token, Throwable ex) {
                    Log.e(TAG, "Publish failed: " + (ex != null ? ex.getMessage() : ""));
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "publish() exception: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // 断开连接（在 onDestroy 中调用）
    // -------------------------------------------------------------------------
    public void disconnect() {
        if (mClient != null && mConnected) {
            try {
                mClient.disconnect();
                mConnected = false;
                Log.d(TAG, "MQTT disconnected.");
            } catch (MqttException e) {
                Log.e(TAG, "disconnect() exception: " + e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // 工具：设备ID（复用 AppUpdateManager 的逻辑）
    // -------------------------------------------------------------------------
    private static String getDeviceId(Context context) {
        String id = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return (id != null && !id.isEmpty()) ? id : "unknown";
    }
}
