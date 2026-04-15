package com.rfidwms;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * PDA WebSocket 客户端
 * 用于接收后端实时通知
 */
public class PDAWebSocketClient {
    private static final String TAG = "PDAWebSocket";
    private static PDAWebSocketClient instance;

    private WebSocket webSocket;
    private OkHttpClient client;
    private Handler mainHandler;
    private boolean isConnected = false;
    private OnConnectionStatusListener statusListener;

    private PDAWebSocketClient() {
        mainHandler = new Handler(Looper.getMainLooper());
        client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MINUTES)  // WebSocket 不超时
            .pingInterval(30, TimeUnit.SECONDS)  // 心跳保活
            .build();
    }

    public static synchronized PDAWebSocketClient getInstance() {
        if (instance == null) {
            instance = new PDAWebSocketClient();
        }
        return instance;
    }

    /**
     * 连接 WebSocket 服务器
     * @param serverUrl 如 http://192.168.0.104:3000
     */
    public void connect(String serverUrl) {
        if (isConnected && webSocket != null) {
            disconnect();
        }

        // 从 http:// 转为 ws://
        String wsUrl = serverUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .replaceFirst("/api$", "");

        Log.d(TAG, "连接 WebSocket: " + wsUrl);

        Request request = new Request.Builder()
            .url(wsUrl)
            .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                isConnected = true;
                Log.d(TAG, "WebSocket 已连接");
                notifyStatusChanged(true);

                // 注册为 PDA 客户端
                String model = android.os.Build.MODEL;
                String serial = android.os.Build.SERIAL != null ? android.os.Build.SERIAL : "unknown";
                String clientId = model + "_" + serial;

                JSONObject msg = new JSONObject();
                try {
                    msg.put("type", "register");
                    msg.put("clientType", "pda");
                    msg.put("clientId", clientId);
                    msg.put("clientLabel", model);
                    ws.send(msg.toString());
                } catch (JSONException e) {
                    Log.e(TAG, "注册消息构建失败: " + e.getMessage());
                }
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                // v3.17: 不再处理写入指令
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                ws.close(1000, null);
                isConnected = false;
                notifyStatusChanged(false);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                isConnected = false;
                notifyStatusChanged(false);
                Log.d(TAG, "WebSocket 已断开");
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                isConnected = false;
                notifyStatusChanged(false);
                Log.e(TAG, "WebSocket 连接失败: " + t.getMessage());
                // 10 秒后自动重连
                mainHandler.postDelayed(() -> {
                    if (!isConnected) {
                        Log.d(TAG, "尝试重连...");
                        connect(serverUrl);
                    }
                }, 10000);
            }
        });
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "主动断开");
            webSocket = null;
        }
        isConnected = false;
        notifyStatusChanged(false);
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnectionStatusListener(OnConnectionStatusListener listener) {
        this.statusListener = listener;
    }

    private void notifyStatusChanged(boolean connected) {
        if (statusListener != null) {
            mainHandler.post(() -> statusListener.onStatusChanged(connected));
        }
    }

    /**
     * 连接状态监听器
     */
    public interface OnConnectionStatusListener {
        void onStatusChanged(boolean connected);
    }
}
