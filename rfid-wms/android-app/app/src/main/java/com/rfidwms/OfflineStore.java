package com.rfidwms;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 离线数据存储
 * 使用 SharedPreferences 缓存未上传的扫描记录，上线后自动同步
 */
public class OfflineStore {
    private static final String TAG = "OfflineStore";
    private static final String PREF_NAME = "rfid_offline_store";
    private static final String KEY_PENDING_TAGS = "pending_tags";
    
    private SharedPreferences prefs;
    private Gson gson;
    private static OfflineStore instance;
    
    private OfflineStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }
    
    public static synchronized OfflineStore getInstance(Context context) {
        if (instance == null) {
            instance = new OfflineStore(context);
        }
        return instance;
    }
    
    /**
     * 扫描记录
     */
    public static class ScanRecord {
        public String epc;
        public String tid;
        public int rssi;
        public long timestamp;
        public boolean uploaded;
        
        public ScanRecord(String epc, String tid, int rssi) {
            this.epc = epc;
            this.tid = tid != null ? tid : "";
            this.rssi = rssi;
            this.timestamp = System.currentTimeMillis();
            this.uploaded = false;
        }
    }
    
    /**
     * 保存一条扫描记录到本地
     */
    public synchronized void saveRecord(ScanRecord record) {
        List<ScanRecord> records = getAllRecords();
        records.add(record);
        
        String json = gson.toJson(records);
        prefs.edit().putString(KEY_PENDING_TAGS, json).apply();
        Log.d(TAG, "已保存到本地缓存，当前未上传: " + getPendingCount());
    }
    
    /**
     * 获取所有记录
     */
    public synchronized List<ScanRecord> getAllRecords() {
        String json = prefs.getString(KEY_PENDING_TAGS, "[]");
        Type type = new TypeToken<ArrayList<ScanRecord>>() {}.getType();
        List<ScanRecord> records = gson.fromJson(json, type);
        return records != null ? records : new ArrayList<>();
    }
    
    /**
     * 获取未上传的记录
     */
    public synchronized List<ScanRecord> getPendingRecords() {
        List<ScanRecord> pending = new ArrayList<>();
        for (ScanRecord record : getAllRecords()) {
            if (!record.uploaded) {
                pending.add(record);
            }
        }
        return pending;
    }
    
    /**
     * 获取未上传记录数量
     */
    public synchronized int getPendingCount() {
        return getPendingRecords().size();
    }
    
    /**
     * 标记一条记录为已上传
     */
    public synchronized void markUploaded(ScanRecord record) {
        List<ScanRecord> records = getAllRecords();
        for (ScanRecord r : records) {
            if (r.epc.equals(record.epc) && r.timestamp == record.timestamp) {
                r.uploaded = true;
                break;
            }
        }
        String json = gson.toJson(records);
        prefs.edit().putString(KEY_PENDING_TAGS, json).apply();
    }
    
    /**
     * 批量标记已上传
     */
    public synchronized void markAllUploaded(List<ScanRecord> uploadedRecords) {
        List<ScanRecord> records = getAllRecords();
        for (ScanRecord r : records) {
            for (ScanRecord ur : uploadedRecords) {
                if (r.epc.equals(ur.epc) && r.timestamp == ur.timestamp) {
                    r.uploaded = true;
                    break;
                }
            }
        }
        String json = gson.toJson(records);
        prefs.edit().putString(KEY_PENDING_TAGS, json).apply();
    }
    
    /**
     * 清空已上传的记录（保留未上传的）
     */
    public synchronized void clearUploaded() {
        List<ScanRecord> records = new ArrayList<>();
        for (ScanRecord r : getAllRecords()) {
            if (!r.uploaded) {
                records.add(r);
            }
        }
        String json = gson.toJson(records);
        prefs.edit().putString(KEY_PENDING_TAGS, json).apply();
        Log.d(TAG, "已清理已上传记录，剩余未上传: " + records.size());
    }
    
    /**
     * 清空所有记录
     */
    public synchronized void clearAll() {
        prefs.edit().putString(KEY_PENDING_TAGS, "[]").apply();
        Log.d(TAG, "已清空所有本地缓存");
    }
}
