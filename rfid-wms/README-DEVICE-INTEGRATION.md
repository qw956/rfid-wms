# RFID 仓储管理系统 - ES-UH8600 设备对接指南

## 📋 目录
1. [SDK 集成](#sdk-集成)
2. [核心功能实现](#核心功能实现)
3. [与后端 API 对接](#与后端-api-对接)
4. [常见问题](#常见问题)

---

## SDK 集成

### 1.1 导入 SDK 库

将 `/Users/pky/Desktop/HCUHF/ModuleAPI` 目录复制到你的 Android 项目中：

```gradle
// settings.gradle
include ':ModuleAPI'

// app/build.gradle
implementation project(':ModuleAPI')
```

### 1.2 添加权限

在 `AndroidManifest.xml` 中添加：

```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
```

---

## 核心功能实现

### 2.1 初始化 RFID 读头

```java
import com.xlzn.hcpda.uhf.UHFReader;
import com.xlzn.hcpda.uhf.entity.UHFReaderResult;
import com.xlzn.hcpda.uhf.entity.UHFTagEntity;
import com.xlzn.hcpda.uhf.interfaces.OnInventoryDataListener;

public class RFIDManager {
    private static RFIDManager instance;
    private UHFReader reader;
    private boolean isScanning = false;
    
    // 服务器地址（你的后端 API）
    private static final String SERVER_URL = "http://192.168.1.100:3000/api";

    private RFIDManager() {
        reader = UHFReader.getInstance();
    }

    public static synchronized RFIDManager getInstance() {
        if (instance == null) {
            instance = new RFIDManager();
        }
        return instance;
    }

    /**
     * 连接设备
     */
    public boolean connect() {
        UHFReaderResult<Boolean> result = reader.connect();
        if (result.getResultCode() == UHFReaderResult.ResultCode.CODE_SUCCESS) {
            // 设置扫描功率（0-30）
            reader.setPower(30);
            return true;
        }
        return false;
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (isScanning) {
            stopScan();
        }
        reader.disConnect();
    }

    /**
     * 启动扫描
     * @param listener 扫描回调
     */
    public void startScan(OnScanListener listener) {
        if (isScanning) {
            return;
        }

        // 设置扫描监听器
        reader.setOnInventoryDataListener(new OnInventoryDataListener() {
            @Override
            public void onInventoryData(List<UHFTagEntity> tagEntityList) {
                if (tagEntityList != null && tagEntityList.size() > 0) {
                    for (UHFTagEntity tag : tagEntityList) {
                        String epc = tag.getEcpHex();
                        String tid = tag.getTidHex();
                        int rssi = tag.getRssi();
                        int count = tag.getCount();

                        // 回调给 UI 层
                        if (listener != null) {
                            listener.onTagScanned(epc, tid, rssi, count);
                        }

                        // 上传到服务器
                        uploadTagToServer(epc, tid, rssi);
                    }
                }
            }
        });

        // 开始扫描
        UHFReaderResult<Boolean> result = reader.startInventory();
        if (result.getResultCode() == UHFReaderResult.ResultCode.CODE_SUCCESS) {
            isScanning = true;
        }
    }

    /**
     * 停止扫描
     */
    public void stopScan() {
        if (isScanning) {
            reader.stopInventory();
            isScanning = false;
        }
    }

    /**
     * 单次扫描一个标签
     */
    public String singleScan() {
        UHFReaderResult<UHFTagEntity> result = reader.singleTagInventory();
        if (result.getResultCode() == UHFReaderResult.ResultCode.CODE_SUCCESS) {
            return result.getData().getEcpHex();
        }
        return null;
    }

    /**
     * 扫描回调接口
     */
    public interface OnScanListener {
        void onTagScanned(String epc, String tid, int rssi, int count);
    }
}
```

---

## 与后端 API 对接

### 3.1 后端 API 接口设计

```javascript
// 后端 API（Node.js + Express 示例）
const express = require('express');
const bodyParser = require('body-parser');
const cors = require('cors');
const app = express();

app.use(cors());
app.use(bodyParser.json());

// 标签数据库（实际使用 MySQL/SQLite）
let tags = [];

// 扫描上传标签
app.post('/api/tags/scan', (req, res) => {
    const { epc, tid, rssi } = req.body;
    
    // 检查标签是否已存在
    const existingTag = tags.find(t => t.epc === epc);
    
    if (existingTag) {
        // 更新扫描次数和信号强度
        existingTag.rssi = rssi;
        existingTag.lastScan = new Date().toISOString();
        existingTag.scanCount = (existingTag.scanCount || 0) + 1;
    } else {
        // 新增标签
        tags.push({
            epc,
            tid,
            rssi,
            name: '未命名标签',
            category: '未分类',
            qty: 1,
            location: '未知位置',
            lastScan: new Date().toISOString(),
            scanCount: 1
        });
    }
    
    res.json({ success: true, tag: existingTag || tags[tags.length - 1] });
});

// 获取所有标签
app.get('/api/tags', (req, res) => {
    res.json({ success: true, tags });
});

// 更新标签信息
app.put('/api/tags/:epc', (req, res) => {
    const { epc } = req.params;
    const { name, category, qty, location } = req.body;
    
    const tag = tags.find(t => t.epc === epc);
    if (tag) {
        tag.name = name || tag.name;
        tag.category = category || tag.category;
        tag.qty = qty !== undefined ? qty : tag.qty;
        tag.location = location || tag.location;
        tag.updated = new Date().toISOString();
        
        res.json({ success: true, tag });
    } else {
        res.json({ success: false, message: '标签不存在' });
    }
});

// 删除标签
app.delete('/api/tags/:epc', (req, res) => {
    const { epc } = req.params;
    tags = tags.filter(t => t.epc !== epc);
    res.json({ success: true });
});

// 导出 Excel
app.get('/api/tags/export', (req, res) => {
    const { createExcel } = require('./excel-export');
    const filePath = createExcel(tags);
    res.download(filePath);
});

app.listen(3000, () => {
    console.log('服务器运行在 http://192.168.1.100:3000');
});
```

### 3.2 Android 端上传数据

```java
import okhttp3.*;
import org.json.JSONObject;

public class ApiService {
    private static final String BASE_URL = "http://192.168.1.100:3000/api";
    private OkHttpClient client = new OkHttpClient();

    /**
     * 上传扫描到的标签
     */
    public void uploadTag(String epc, String tid, int rssi, Callback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("epc", epc);
            json.put("tid", tid != null ? tid : "");
            json.put("rssi", rssi);

            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                json.toString()
            );

            Request request = new Request.Builder()
                .url(BASE_URL + "/tags/scan")
                .post(body)
                .build();

            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取所有标签
     */
    public void getTags(Callback callback) {
        Request request = new Request.Builder()
            .url(BASE_URL + "/tags")
            .get()
            .build();

        client.newCall(request).enqueue(callback);
    }

    /**
     * 更新标签信息
     */
    public void updateTag(String epc, String name, String category, 
                        int qty, String location, Callback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("category", category);
            json.put("qty", qty);
            json.put("location", location);

            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                json.toString()
            );

            Request request = new Request.Builder()
                .url(BASE_URL + "/tags/" + epc)
                .put(body)
                .build();

            client.newCall(request).enqueue(callback);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

---

## 常见问题

### Q1: 设备连接失败？
**A:** 检查以下几点：
1. USB 数据线是否连接正常
2. 是否已授予 USB 权限
3. 设备是否被其他应用占用
4. 查看 Logcat 日志：`adb logcat | grep UHF`

### Q2: 扫描不到标签？
**A:** 
1. 检查扫描功率：`reader.setPower(30)` 设置为最大
2. 确认标签是超高频（UHF 860-960MHz）
3. 标签距离设备太近（< 5cm）会导致信号过强
4. 尝试单次扫描 `singleTagInventory()` 测试

### Q3: 如何批量上传标签？
**A:** 监听扫描回调，实时上传到服务器：

```java
reader.setOnInventoryDataListener(new OnInventoryDataListener() {
    @Override
    public void onInventoryData(List<UHFTagEntity> tagEntityList) {
        for (UHFTagEntity tag : tagEntityList) {
            ApiService.getInstance().uploadTag(
                tag.getEcpHex(),
                tag.getTidHex(),
                tag.getRssi(),
                new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e("TAG", "上传失败: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) {
                        Log.e("TAG", "上传成功");
                    }
                }
            );
        }
    }
});
```

### Q4: 如何导出 Excel？
**A:** 使用 Apache POI 库：

```gradle
implementation 'org.apache.poi:poi:5.2.3'
implementation 'org.apache.poi:poi-ooxml:5.2.3'
```

```java
public void exportExcel(List<Tag> tags, String filePath) {
    try {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("标签数据");

        // 表头
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("EPC");
        headerRow.createCell(1).setCellValue("TID");
        headerRow.createCell(2).setCellValue("名称");
        headerRow.createCell(3).setCellValue("类别");
        headerRow.createCell(4).setCellValue("数量");
        headerRow.createCell(5).setCellValue("存放位置");
        headerRow.createCell(6).setCellValue("信号强度");
        headerRow.createCell(7).setCellValue("最后扫描时间");

        // 数据
        for (int i = 0; i < tags.size(); i++) {
            Tag tag = tags.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(tag.getEpc());
            row.createCell(1).setCellValue(tag.getTid());
            row.createCell(2).setCellValue(tag.getName());
            row.createCell(3).setCellValue(tag.getCategory());
            row.createCell(4).setCellValue(tag.getQty());
            row.createCell(5).setCellValue(tag.getLocation());
            row.createCell(6).setCellValue(tag.getRssi());
            row.createCell(7).setCellValue(tag.getLastScan());
        }

        // 保存
        FileOutputStream fos = new FileOutputStream(filePath);
        workbook.write(fos);
        workbook.close();
        fos.close();

        Log.e("TAG", "Excel 导出成功: " + filePath);
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

---

## 📞 技术支持

- SDK 位置：`/Users/pky/Desktop/HCUHF/ModuleAPI`
- Demo 代码：`/Users/pky/Desktop/HCUHF/uhfDemo`
- 开发文档：`/Users/pky/Desktop/HCUHF/超高频开发文档.docx`

---

## ✅ 快速开始

1. **导入 SDK**：将 ModuleAPI 复制到你的 Android 项目
2. **初始化连接**：`RFIDManager.getInstance().connect()`
3. **启动扫描**：`RFIDManager.getInstance().startScan(listener)`
4. **上传数据**：`ApiService.getInstance().uploadTag(epc, tid, rssi)`
5. **查看结果**：打开 Web 管理后台 `http://localhost:7890`

---

**完成！🎉**
