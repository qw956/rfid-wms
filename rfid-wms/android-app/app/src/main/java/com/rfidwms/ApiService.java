package com.rfidwms;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * API 服务类 - 负责与后端服务器通信
 */
public class ApiService {
    private static final String TAG = "ApiService";
    
    // 服务器地址（根据你的局域网 IP 修改）
    private static final String BASE_URL = "http://192.168.1.55:3000/api";
    
    private OkHttpClient client;
    private Gson gson;
    
    private static ApiService instance;
    
    private ApiService() {
        client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();
        gson = new Gson();
    }
    
    public static synchronized ApiService getInstance() {
        if (instance == null) {
            instance = new ApiService();
        }
        return instance;
    }
    
    /**
     * 上传扫描到的标签
     */
    public void uploadTag(String epc, String tid, int rssi, final ApiCallback callback) {
        uploadTag(epc, tid, rssi, null, callback);
    }

    /**
     * 上传扫描到的标签（带 User Memory 数据）
     */
    public void uploadTag(String epc, String tid, int rssi, String userData, final ApiCallback callback) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("epc", epc);
            json.addProperty("tid", tid != null ? tid : "");
            json.addProperty("rssi", rssi);
            if (userData != null && !userData.isEmpty()) {
                json.addProperty("user_data", userData);
            }

            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                gson.toJson(json)
            );

            Request request = new Request.Builder()
                .url(BASE_URL + "/tags/scan")
                .post(body)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "上传标签失败: " + e.getMessage());
                    if (callback != null) {
                        callback.onError("上传失败: " + e.getMessage());
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        Log.e(TAG, "上传标签成功: " + responseBody);
                        if (callback != null) {
                            callback.onSuccess(responseBody);
                        }
                    } else {
                        Log.e(TAG, "上传标签失败: " + response.code());
                        if (callback != null) {
                            callback.onError("上传失败: " + response.code());
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "上传标签异常: " + e.getMessage());
            if (callback != null) {
                callback.onError("上传异常: " + e.getMessage());
            }
        }
    }
    
    /**
     * 获取所有类别
     */
    public void getCategories(final ApiCallback callback) {
        Request request = new Request.Builder()
            .url(BASE_URL + "/categories")
            .get()
            .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取类别失败: " + e.getMessage());
                if (callback != null) callback.onError(e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    Log.i(TAG, "获取类别成功");
                    if (callback != null) callback.onSuccess(body);
                } else {
                    if (callback != null) callback.onError("HTTP " + response.code());
                }
            }
        });
    }

    /**
     * 获取所有标签
     */
    public void getTags(final ApiCallback callback) {
        Request request = new Request.Builder()
            .url(BASE_URL + "/tags")
            .get()
            .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取标签失败: " + e.getMessage());
                if (callback != null) {
                    callback.onError("获取失败: " + e.getMessage());
                }
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    Log.e(TAG, "获取标签成功");
                    if (callback != null) {
                        callback.onSuccess(responseBody);
                    }
                } else {
                    if (callback != null) {
                        callback.onError("获取失败: " + response.code());
                    }
                }
            }
        });
    }
    
    /**
     * 更新标签信息
     */
    public void updateTag(String epc, String name, String category,
                         int qty, String location, String department, String userName,
                         final ApiCallback callback) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("name", name);
            json.addProperty("category", category);
            json.addProperty("qty", qty);
            json.addProperty("location", location);
            json.addProperty("department", department);
            json.addProperty("user_name", userName);
            
            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                gson.toJson(json)
            );
            
            Request request = new Request.Builder()
                .url(BASE_URL + "/tags/" + epc)
                .put(body)
                .build();
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "更新标签失败: " + e.getMessage());
                    if (callback != null) {
                        callback.onError("更新失败: " + e.getMessage());
                    }
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        Log.e(TAG, "更新标签成功");
                        if (callback != null) {
                            callback.onSuccess(responseBody);
                        }
                    } else {
                        if (callback != null) {
                            callback.onError("更新失败: " + response.code());
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "更新标签异常: " + e.getMessage());
            if (callback != null) {
                callback.onError("更新异常: " + e.getMessage());
            }
        }
    }
    
    /**
     * 删除标签
     */
    public void deleteTag(String epc, final ApiCallback callback) {
        Request request = new Request.Builder()
            .url(BASE_URL + "/tags/" + epc)
            .delete()
            .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "删除标签失败: " + e.getMessage());
                if (callback != null) {
                    callback.onError("删除失败: " + e.getMessage());
                }
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    Log.e(TAG, "删除标签成功");
                    if (callback != null) {
                        callback.onSuccess(responseBody);
                    }
                } else {
                    if (callback != null) {
                        callback.onError("删除失败: " + response.code());
                    }
                }
            }
        });
    }
    
    /**
     * API 回调接口
     */
    public interface ApiCallback {
        void onSuccess(String response);
        void onError(String error);
    }
}
