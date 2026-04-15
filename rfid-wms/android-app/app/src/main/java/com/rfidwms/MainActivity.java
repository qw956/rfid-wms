package com.rfidwms;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.xlzn.hcpda.uhf.UHFReader;
import com.xlzn.hcpda.uhf.entity.UHFReaderResult;
import com.xlzn.hcpda.uhf.entity.UHFTagEntity;

/**
 * 主界面 - RFID 标签管理
 * 功能：扫描列表显示、点击查看/编辑详情、去重、声音提示、本机存储、导出
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int PERMISSION_WRITE_STORAGE = 2;

    // UI 组件
    private TextView tvStatus;
    private TextView tvLastScan;
    private TextView tvScanCount;
    private TextView tvOfflineCount;
    private TextView tvSyncStatus;
    private TextView tvListCount;
    private Button btnConnect;
    private Button btnScan;
    private Button btnStop;
    private Button btnClear;
    private Button btnSyncNow;
    private Button btnExport;
    private EditText etServerUrl;
    private ListView lvTags;

    // 列表刷新节流：最少间隔 500ms 刷新一次
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean listDirty = false;
    private Runnable listRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (listDirty) {
                tagAdapter.updateData(new ArrayList<>(tagMap.values()));
                listDirty = false;
            }
        }
    };
    private void scheduleListRefresh() {
        listDirty = true;
        uiHandler.removeCallbacks(listRefreshRunnable);
        uiHandler.postDelayed(listRefreshRunnable, 500);
    }

    // 管理类
    private RFIDManager rfidManager;
    private ApiService apiService;
    private OfflineStore offlineStore;
    private PDAWebSocketClient wsClient;

    // 动态类别列表（从服务器加载）
    private final List<String> categoryList = new ArrayList<>();
    private static final String[] DEFAULT_CATEGORIES = {"", "紧固件", "电子元器件", "润滑油脂", "密封件", "变频器", "其他"};

    // 标签数据（去重，按 EPC 索引）
    private final LinkedHashMap<String, TagItem> tagMap = new LinkedHashMap<>();
    private TagListAdapter tagAdapter;

    // 扫描统计
    private int scanCount = 0;

    // 网络状态
    private boolean isNetworkAvailable = false;
    private boolean isSyncing = false;
    private BroadcastReceiver networkReceiver;

    // 声音提示
    private ToneGenerator toneGenerator;

    // 防休眠
    private PowerManager.WakeLock wakeLock;

    // 扫描监听器
    private RFIDManager.OnScanListener scanListener;

    // 时间格式
    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
    private final SimpleDateFormat sdfFull = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
    private final SimpleDateFormat sdfFile = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA);

    // ───── 标签数据模型 ─────
    public static class TagItem {
        public String epc;
        public String tid;
        public int rssi;
        public String name;
        public String category;
        public int qty;
        public String location;
        public String department;
        public String userName;
        public String purchaseDate;
        public String userData;   // 保留字段（兼容旧数据）
        public long scanTime;
        public boolean synced;

        public TagItem(String epc, String tid, int rssi) {
            this.epc = epc;
            this.tid = tid != null ? tid : "";
            this.rssi = rssi;
            this.name = "";
            this.category = "";
            this.qty = 1;
            this.location = "";
            this.department = "";
            this.userName = "";
            this.purchaseDate = "";
            this.userData = "";
            this.scanTime = System.currentTimeMillis();
            this.synced = false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化声音
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        } catch (Exception e) {
            Log.w(TAG, "ToneGenerator 初始化失败: " + e.getMessage());
        }

        // 初始化 UI 组件
        initViews();

        // 初始化标签列表适配器
        tagAdapter = new TagListAdapter(this, new ArrayList<>(tagMap.values()));
        lvTags.setAdapter(tagAdapter);
        lvTags.setOnItemClickListener((parent, view, position, id) -> {
            List<TagItem> items = tagAdapter.getItems();
            if (position < items.size()) {
                openTagDetailDialog(items.get(position));
            }
        });

        // 初始化管理类
        try {
            rfidManager = RFIDManager.getInstance();
            apiService = ApiService.getInstance();
            offlineStore = OfflineStore.getInstance(this);
        } catch (Exception e) {
            Log.e(TAG, "初始化管理类异常: ", e);
            Toast.makeText(this, "SDK 初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // 设置按钮事件
        btnConnect.setOnClickListener(v -> { buttonFeedback(btnConnect); connectDevice(); });
        btnScan.setOnClickListener(v -> { buttonFeedback(btnScan); startScan(); });
        btnStop.setOnClickListener(v -> { buttonFeedback(btnStop); stopScan(); });
        btnClear.setOnClickListener(v -> { buttonFeedback(btnClear); clearData(); });
        btnSyncNow.setOnClickListener(v -> { buttonFeedback(btnSyncNow); syncOfflineData(); });
        btnExport.setOnClickListener(v -> { buttonFeedback(btnExport); exportLocalData(); });

        // 初始化 WebSocket 客户端（连接后端接收实时通知）
        wsClient = PDAWebSocketClient.getInstance();
        wsClient.setConnectionStatusListener(connected -> {
            if (connected) {
                tvSyncStatus.setText("🌐 PDA在线");
                tvSyncStatus.setTextColor(0xFF38BDF8);
            } else {
                updateNetworkUI();
            }
        });

        // 请求权限
        requestStoragePermission();

        // 注册网络监听
        registerNetworkReceiver();
        checkNetworkStatus();
        updateOfflineCount();
        loadCategoriesFromServer();

        // 自动连接 RFID 模块（无需手动点击）
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (rfidManager != null && !rfidManager.isScanning()) {
                connectDevice();
            }
        }, 500);
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvLastScan = findViewById(R.id.tvLastScan);
        tvScanCount = findViewById(R.id.tvScanCount);
        tvOfflineCount = findViewById(R.id.tvOfflineCount);
        tvSyncStatus = findViewById(R.id.tvSyncStatus);
        tvListCount = findViewById(R.id.tvListCount);
        btnConnect = findViewById(R.id.btnConnect);
        btnScan = findViewById(R.id.btnScan);
        btnStop = findViewById(R.id.btnStop);
        btnClear = findViewById(R.id.btnClear);
        btnSyncNow = findViewById(R.id.btnSyncNow);
        btnExport = findViewById(R.id.btnExport);
        etServerUrl = findViewById(R.id.etServerUrl);
        lvTags = findViewById(R.id.lvTags);
    }

    // ───── 扫描声音提示 ─────
    private void playBeep() {
        try {
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
            }
        } catch (Exception e) {
            Log.w(TAG, "播放提示音失败: " + e.getMessage());
        }
    }

    /** 按钮点击反馈：声音 + 缩放动画 */
    private void buttonFeedback(View button) {
        playBeep();
        button.animate().scaleX(0.92f).scaleY(0.92f).setDuration(50).withEndAction(() ->
                button.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        ).start();
    }

    // ───── 连接设备 ─────
    private void connectDevice() {
        tvStatus.setText("连接中...");
        btnConnect.setEnabled(false);

        new Thread(() -> {
            try {
                boolean connected = rfidManager.connect();
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (connected) {
                        tvStatus.setText("✓ 已连接");
                        tvStatus.setTextColor(0xFF34D399);
                        btnScan.setEnabled(true);
                        Toast.makeText(this, "设备连接成功!", Toast.LENGTH_SHORT).show();

                        // 连接成功后自动连接 WebSocket
                        String serverUrl = etServerUrl.getText().toString().trim();
                        if (!serverUrl.isEmpty()) {
                            wsClient.connect(serverUrl);
                        }
                    } else {
                        tvStatus.setText("✗ 失败");
                        tvStatus.setTextColor(0xFFF87171);
                        btnConnect.setEnabled(true);
                        Toast.makeText(this, "连接失败", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    tvStatus.setText("✗ 异常");
                    tvStatus.setTextColor(0xFFF87171);
                    btnConnect.setEnabled(true);
                    Toast.makeText(this, "连接异常: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ───── 启动扫描 ─────
    private void startScan() {
        // 防休眠：屏幕常亮 + CPU 保持唤醒
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rfid:scan");
        }
        if (!wakeLock.isHeld()) wakeLock.acquire();

        tvStatus.setText("扫描中...");
        tvStatus.setTextColor(0xFF38BDF8);
        btnScan.setEnabled(false);
        btnStop.setEnabled(true);
        scanCount = 0;
        tvScanCount.setText("0");

        // 保存监听器到字段
        scanListener = new RFIDManager.OnScanListener() {
            @Override
            public void onTagScanned(String epc, String tid, int rssi, int count) {
                scanCount++;

                boolean isNew = !tagMap.containsKey(epc);

                TagItem item;
                if (isNew) {
                    // 新标签 —— 尝试从服务器获取已知名称
                    item = new TagItem(epc, tid, rssi);
                    tagMap.put(epc, item);

                    // 保存到本地离线存储
                    OfflineStore.ScanRecord record = new OfflineStore.ScanRecord(epc, tid, rssi);
                    offlineStore.saveRecord(record);

                    // v3.17: 不再写芯片，直接上传
                    uploadTag(epc, tid, rssi, record, item);
                } else {
                    // 已有标签：只更新 RSSI，不刷新 UI（减少内存压力）
                    item = tagMap.get(epc);
                    if (item != null) {
                        item.rssi = rssi;
                    }
                    return; // 已有标签不需要后续操作
                }

                final TagItem finalItem = item;

                new Handler(Looper.getMainLooper()).post(() -> {
                tvLastScan.setText(epc);
                tvScanCount.setText(String.valueOf(tagMap.size()));
                    tvListCount.setText(tagMap.size() + " 条");
                    Log.e(TAG, "=== 扫描新标签 EPC=" + epc + " ===");
                    updateOfflineCount();

                    // 延迟刷新列表（节流，500ms 内多次更新只执行一次）
                    scheduleListRefresh();

                    // 新标签：声音提示
                    playBeep();
                });
            }
        };

        rfidManager.startScan(scanListener);

    }

    // ───── 停止扫描 ─────
    private void stopScan() {
        // 释放防休眠
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Exception ignored) {}
        }

        if (rfidManager != null) rfidManager.stopScan();
        btnScan.setEnabled(true);
        btnStop.setEnabled(false);
        tvStatus.setText("已停止");
        tvStatus.setTextColor(0xFF94A3B8);
        Toast.makeText(this, "扫描停止，共 " + tagMap.size() + " 个不重复标签", Toast.LENGTH_SHORT).show();
    }

    // ───── 上传标签到服务器 ─────
    private void uploadTag(String epc, String tid, int rssi, OfflineStore.ScanRecord record, TagItem item) {
        apiService.uploadTag(epc, tid, rssi, null, new ApiService.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                offlineStore.markUploaded(record);
                offlineStore.clearUploaded();

                // 尝试解析服务器返回的标签信息，仅填充本地为空的字段（不覆盖已有数据）
                try {
                    JSONObject json = new JSONObject(response);
                    if (json.getBoolean("success") && json.has("tag")) {
                        JSONObject tag = json.getJSONObject("tag");
                        if (item != null) {
                            if (item.name.isEmpty()) item.name = tag.optString("name", "");
                            if (item.category.isEmpty()) item.category = tag.optString("category", "");
                            if (item.qty <= 0) item.qty = tag.optInt("qty", 1);
                            if (item.location.isEmpty()) item.location = tag.optString("location", "");
                            if (item.department.isEmpty()) item.department = tag.optString("department", "");
                            if (item.userName.isEmpty()) item.userName = tag.optString("user_name", "");
                            if (item.purchaseDate.isEmpty()) item.purchaseDate = tag.optString("purchase_date", "");
                            item.synced = true;
                        }
                    }
                } catch (Exception e) { /* 忽略解析错误 */ }

                new Handler(Looper.getMainLooper()).post(() -> {
                    updateOfflineCount();
                    tagAdapter.updateData(new ArrayList<>(tagMap.values()));
                });
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "上传失败（已缓存）: " + error);
            }
        });
    }

    // ───── 点击标签查看详情/编辑 ─────
    private void openTagDetailDialog(TagItem item) {
        if (item == null) return;

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_tag_detail, null);

        TextView tvEpc = view.findViewById(R.id.tvDetailEpc);
        EditText etName = view.findViewById(R.id.etDetailName);
        Spinner spinnerCat = view.findViewById(R.id.spinnerDetailCategory);
        EditText etQty = view.findViewById(R.id.etDetailQty);
        EditText etLocation = view.findViewById(R.id.etDetailLocation);
        EditText etDepartment = view.findViewById(R.id.etDetailDepartment);
        EditText etUserName = view.findViewById(R.id.etDetailUserName);
        EditText etPurchaseDate = view.findViewById(R.id.etDetailPurchaseDate);
        TextView tvRssi = view.findViewById(R.id.tvDetailRssi);
        TextView tvTime = view.findViewById(R.id.tvDetailTime);

        tvEpc.setText(item.epc);
        etName.setText(item.name);
        etQty.setText(String.valueOf(item.qty));
        etLocation.setText(item.location);
        etDepartment.setText(item.department);
        etUserName.setText(item.userName);
        etPurchaseDate.setText(item.purchaseDate);
        tvRssi.setText(item.rssi + " dBm");
        tvTime.setText(sdfFull.format(new Date(item.scanTime)));

        // 类别下拉（从服务器动态加载，备选默认类别）
        List<String> categories = getCategories();
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categories);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCat.setAdapter(catAdapter);
        // 选中当前类别
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).equals(item.category)) {
                spinnerCat.setSelection(i);
                break;
            }
        }

        AlertDialog detailDialog = new AlertDialog.Builder(this)
                .setTitle("🏷️ 标签详情")
                .setView(view)
                .setPositiveButton("💾 保存", (dialog, which) -> {
                    String newName = etName.getText().toString().trim();
                    String newCat = spinnerCat.getSelectedItem() != null
                            ? spinnerCat.getSelectedItem().toString() : "";
                    int newQty = 1;
                    try { newQty = Integer.parseInt(etQty.getText().toString()); } catch(Exception e) {}
                    String newLoc = etLocation.getText().toString().trim();
                    String newDept = etDepartment.getText().toString().trim();
                    String newUser = etUserName.getText().toString().trim();
                    String newPurchaseDate = etPurchaseDate.getText().toString().trim();

                    item.name = newName;
                    item.category = newCat;
                    item.qty = newQty;
                    item.location = newLoc;
                    item.department = newDept;
                    item.userName = newUser;
                    item.purchaseDate = newPurchaseDate;

                    // 更新适配器
                    tagAdapter.updateData(new ArrayList<>(tagMap.values()));

                    // 上传更新到服务器
                    if (isNetworkAvailable) {
                        updateTagOnServer(item);
                    }
                    Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .create();

        detailDialog.show();
    }

    // ───── 更新标签信息到服务器 ─────
    private void updateTagOnServer(TagItem item) {
        String serverUrl = etServerUrl.getText().toString().trim();
        if (!serverUrl.endsWith("/api")) {
            serverUrl = serverUrl.endsWith("/") ? serverUrl + "api" : serverUrl + "/api";
        }

        final String url = serverUrl + "/tags/" + item.epc;
        final String body;
        try {
            JSONObject json = new JSONObject();
            json.put("name", item.name);
            json.put("category", item.category);
            json.put("qty", item.qty);
            json.put("location", item.location);
            json.put("department", item.department);
            json.put("user_name", item.userName);
            json.put("purchase_date", item.purchaseDate);
            body = json.toString();
        } catch (Exception e) {
            return;
        }

        new Thread(() -> {
            try {
                java.net.URL u = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }
                int code = conn.getResponseCode();
                Log.d(TAG, "标签更新响应码: " + code);
                conn.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "更新标签到服务器失败: " + e.getMessage());
            }
        }).start();
    }

    // ───── 导出本地数据 ─────
    private void exportLocalData() {
        if (tagMap.isEmpty()) {
            Toast.makeText(this, "暂无扫描数据", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                // 统一导出到公共 Download 目录
                File exportDir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "RFID_WMS");
                if (!exportDir.exists()) exportDir.mkdirs();

                String fileName = "扫描数据_" + sdfFile.format(new Date()) + ".csv";
                File file = new File(exportDir, fileName);

                FileWriter fw = new FileWriter(file);
                // UTF-8 BOM（Excel 兼容）
                fw.write('\uFEFF');
                fw.write("序号,RFID 标签,名称,类别,数量,存放位置,使用部门,使用人,入库/采购时间,扫描时间\n");

                int i = 1;
                for (TagItem item : tagMap.values()) {
                    fw.write(String.format(Locale.CHINA, "%d,\"%s\",\"%s\",\"%s\",%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                            i++,
                            item.epc,
                            item.name,
                            item.category,
                            item.qty,
                            item.location,
                            item.department,
                            item.userName,
                            item.purchaseDate,
                            sdfFull.format(new Date(item.scanTime))
                    ));
                }
                fw.close();

                final String filePath = file.getAbsolutePath();
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "✅ 已导出到:\n" + filePath, Toast.LENGTH_LONG).show();
                });

            } catch (IOException e) {
                Log.e(TAG, "导出失败: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ───── 清空数据 ─────
    private void clearData() {
        new AlertDialog.Builder(this)
                .setTitle("清空数据")
                .setMessage("确定要清空所有本次扫描记录吗？\n（包括未上传的离线缓存）")
                .setPositiveButton("确定", (dialog, which) -> {
                    tagMap.clear();
                    tagAdapter.updateData(new ArrayList<>());
                    scanCount = 0;
                    tvScanCount.setText("0");
                    tvListCount.setText("0 条");
                    tvLastScan.setText("暂无");
                    offlineStore.clearAll();
                    updateOfflineCount();
                    Toast.makeText(this, "数据已清空", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ───── 网络相关 ─────
    private void registerNetworkReceiver() {
        networkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                checkNetworkStatus();
            }
        };
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    private void checkNetworkStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo net = cm.getActiveNetworkInfo();
            boolean wasAvailable = isNetworkAvailable;
            isNetworkAvailable = net != null && net.isConnected();

            if (!wasAvailable && isNetworkAvailable) {
                Toast.makeText(this, "📶 网络已恢复，同步中...", Toast.LENGTH_SHORT).show();
                // 自动重连 WebSocket + 同步离线数据
                String serverUrl = etServerUrl.getText().toString().trim();
                if (!serverUrl.isEmpty()) {
                    wsClient.connect(serverUrl);
                }
                syncOfflineData();
            }
            updateNetworkUI();
        }
    }

    private void updateNetworkUI() {
        if (tvSyncStatus == null) return;
        if (isSyncing) {
            tvSyncStatus.setText("🔄 同步中...");
            tvSyncStatus.setTextColor(0xFFFBBF24);
        } else if (isNetworkAvailable) {
            int pending = offlineStore.getPendingCount();
            if (pending > 0) {
                tvSyncStatus.setText("📡 " + pending + " 待上传");
                tvSyncStatus.setTextColor(0xFFFBBF24);
            } else {
                tvSyncStatus.setText("☁️ 已同步");
                tvSyncStatus.setTextColor(0xFF34D399);
            }
        } else {
            tvSyncStatus.setText("📴 离线");
            tvSyncStatus.setTextColor(0xFFF87171);
        }
    }

    // ───── 从服务器加载类别列表 ─────
    private void loadCategoriesFromServer() {
        apiService.getCategories(new ApiService.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    if (json.getBoolean("success")) {
                        org.json.JSONArray arr = json.getJSONArray("categories");
                        categoryList.clear();
                        categoryList.add(""); // 空选项
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject cat = arr.getJSONObject(i);
                            categoryList.add(cat.getString("name"));
                        }
                        Log.i("MainActivity", "已加载 " + categoryList.size() + " 个类别");
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "解析类别失败: " + e.getMessage());
                }
            }
            @Override
            public void onError(String error) {
                Log.w("MainActivity", "加载类别失败，使用默认: " + error);
            }
        });
    }

    private List<String> getCategories() {
        if (categoryList.isEmpty()) {
            categoryList.addAll(java.util.Arrays.asList(DEFAULT_CATEGORIES));
        }
        return categoryList;
    }

    private void updateOfflineCount() {
        if (tvOfflineCount != null) {
            int pending = offlineStore.getPendingCount();
            tvOfflineCount.setText(String.valueOf(pending));
            tvOfflineCount.setTextColor(pending > 0 ? 0xFFFBBF24 : 0xFF64748B);
        }
        updateNetworkUI();
    }

    private void syncOfflineData() {
        if (isSyncing) return;

        // 优先同步离线未上传的记录
        List<OfflineStore.ScanRecord> pending = offlineStore.getPendingRecords();

        // 同时把当前 tagMap 中的标签也重新上传（应对网页端清空会话后重新同步）

        if (pending.isEmpty() && tagMap.isEmpty()) {
            updateOfflineCount();
            return;
        }
        if (!isNetworkAvailable) {
            Toast.makeText(this, "📴 无网络，无法同步", Toast.LENGTH_SHORT).show();
            return;
        }

        isSyncing = true;
        updateNetworkUI();

        // 合并需要上传的 EPC（去重：离线记录和 tagMap 可能重叠）
        final Map<String, int[]> epcRssiMap = new LinkedHashMap<>(); // epc -> {rssi, source(0=offline,1=tagMap)}
        for (OfflineStore.ScanRecord rec : pending) {
            epcRssiMap.put(rec.epc, new int[]{rec.rssi, 0});
        }
        for (TagItem item : tagMap.values()) {
            if (!epcRssiMap.containsKey(item.epc)) {
                epcRssiMap.put(item.epc, new int[]{item.rssi, 1});
            }
        }

        final int total = epcRssiMap.size();
        final int[] success = {0}, fail = {0};
        final List<OfflineStore.ScanRecord> uploaded = new ArrayList<>();

        for (Map.Entry<String, int[]> entry : epcRssiMap.entrySet()) {
            final String epc = entry.getKey();
            final int rssi = entry.getValue()[0];
            apiService.uploadTag(epc, "", rssi, new ApiService.ApiCallback() {
                @Override
                public void onSuccess(String resp) {
                    success[0]++;

                    // 标记离线记录为已上传
                    for (OfflineStore.ScanRecord rec : pending) {
                        if (rec.epc.equals(epc)) {
                            uploaded.add(rec);
                            break;
                        }
                    }

                    // 尝试用服务器数据更新本地
                    try {
                        JSONObject json = new JSONObject(resp);
                        if (json.getBoolean("success") && json.has("tag")) {
                            JSONObject tag = json.getJSONObject("tag");
                            TagItem item = tagMap.get(epc);
                            if (item != null) {
                                if (item.name.isEmpty()) item.name = tag.optString("name", "");
                                if (item.category.isEmpty()) item.category = tag.optString("category", "");
                                if (item.department.isEmpty()) item.department = tag.optString("department", "");
                                if (item.userName.isEmpty()) item.userName = tag.optString("user_name", "");
                                if (item.purchaseDate.isEmpty()) item.purchaseDate = tag.optString("purchase_date", "");
                                item.synced = true;
                            }
                        }
                    } catch (Exception e) {}

                    checkDone();
                }

                @Override
                public void onError(String error) {
                    fail[0]++;
                    checkDone();
                }

                private void checkDone() {
                    if (success[0] + fail[0] == total) {
                        offlineStore.markAllUploaded(uploaded);
                        offlineStore.clearUploaded();
                        isSyncing = false;
                        String msg = "同步完成: " + success[0] + " 成功" + (fail[0] > 0 ? ", " + fail[0] + " 失败" : "");
                        new Handler(Looper.getMainLooper()).post(() -> {
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                            updateOfflineCount();
                            tagAdapter.updateData(new ArrayList<>(tagMap.values()));
                            loadCategoriesFromServer(); // 刷新类别列表
                        });
                    }
                }
            });
        }
    }

    // ───── 权限 ─────
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                        }, PERMISSION_WRITE_STORAGE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // ───── 硬件扫描键 ─────
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == 287 || keyCode == 0 || keyCode == 11 ||
                keyCode == 293 || keyCode == 290 || keyCode == 286) {
            if (rfidManager != null && !rfidManager.isScanning()) {
                startScan();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == 287 || keyCode == 0 || keyCode == 11 ||
                keyCode == 293 || keyCode == 290 || keyCode == 286) {
            if (rfidManager != null && rfidManager.isScanning()) {
                stopScan();
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    // ───── 生命周期 ─────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (networkReceiver != null) unregisterReceiver(networkReceiver);
            if (toneGenerator != null) { toneGenerator.release(); toneGenerator = null; }
            if (wakeLock != null && wakeLock.isHeld()) { try { wakeLock.release(); } catch (Exception ignored) {} }
            if (rfidManager != null) {
                if (rfidManager.isScanning()) rfidManager.stopScan();
                rfidManager.disconnect();
            }
            if (wsClient != null) wsClient.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "onDestroy 异常: ", e);
        }
    }
}
