package com.rfidwms;

import android.util.Log;
import com.xlzn.hcpda.uhf.UHFReader;
import com.xlzn.hcpda.uhf.entity.UHFReaderResult;
import com.xlzn.hcpda.uhf.entity.UHFTagEntity;
import com.xlzn.hcpda.uhf.enums.InventoryModeForPower;
import com.xlzn.hcpda.uhf.interfaces.OnInventoryDataListener;

import java.util.List;

/**
 * RFID 管理类 - 单例模式
 * 负责与 ES-UH8600 设备的所有交互
 */
public class RFIDManager {
    private static final String TAG = "RFIDManager";
    private static RFIDManager instance;
    
    private UHFReader reader;
    private boolean isScanning = false;
    private OnScanListener scanListener;
    
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
     * 销毁单例实例
     */
    public static synchronized void destroyInstance() {
        if (instance != null) {
            try {
                instance.reader.disConnect();
            } catch (Exception e) {}
            instance.isScanning = false;
            instance.scanListener = null;
            instance = null;
            Log.e(TAG, "RFIDManager 单例已销毁");
        }
    }
    
    /**
     * 连接设备
     */
    public boolean connect() {
        try {
            UHFReaderResult<Boolean> result = reader.connect();
            if (result.getResultCode() == UHFReaderResult.ResultCode.CODE_SUCCESS) {
                reader.setPower(30);
                reader.setInventoryModeForPower(InventoryModeForPower.POWER_SAVING_MODE);
                reader.setDynamicTarget(0);
                Log.e(TAG, "设备连接成功（省电模式, 功率30, 动态Target）");
                return true;
            } else {
                Log.e(TAG, "设备连接失败: " + result.getMessage());
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "设备连接异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        if (isScanning) {
            stopScan();
        }
        reader.disConnect();
        Log.e(TAG, "设备已断开");
    }
    
    /**
     * 启动连续扫描
     */
    public void startScan(OnScanListener listener) {
        if (isScanning) {
            Log.e(TAG, "扫描已在进行中");
            return;
        }
        
        this.scanListener = listener;
        
        reader.setOnInventoryDataListener(new OnInventoryDataListener() {
            @Override
            public void onInventoryData(List<UHFTagEntity> tagEntityList) {
                if (tagEntityList != null && tagEntityList.size() > 0) {
                    for (UHFTagEntity tag : tagEntityList) {
                        String epc = tag.getEcpHex();
                        String tid = tag.getTidHex();
                        int rssi = tag.getRssi();
                        int count = tag.getCount();
                        
                        if (scanListener != null) {
                            scanListener.onTagScanned(epc, tid, rssi, count);
                        }
                    }
                }
            }
        });
        
        UHFReaderResult<Boolean> result = reader.startInventory();
        if (result.getResultCode() == UHFReaderResult.ResultCode.CODE_SUCCESS) {
            isScanning = true;
            Log.e(TAG, "扫描已启动");
        } else {
            Log.e(TAG, "启动扫描失败: " + result.getMessage());
        }
    }
    
    /**
     * 停止扫描
     */
    public void stopScan() {
        if (isScanning) {
            reader.stopInventory();
            isScanning = false;
            Log.e(TAG, "扫描已停止");
        }
    }
    
    /**
     * 单次扫描一个标签
     */
    public String singleScan() {
        try {
            UHFReaderResult<UHFTagEntity> result = reader.singleTagInventory();
            if (result.getResultCode() == UHFReaderResult.ResultCode.CODE_SUCCESS) {
                UHFTagEntity tag = result.getData();
                if (tag != null) {
                    return tag.getEcpHex();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "单次扫描异常: " + e.getMessage());
        }
        return null;
    }

    /**
     * 检查是否正在扫描
     */
    public boolean isScanning() {
        return isScanning;
    }

    /**
     * 获取设备连接状态
     */
    public com.xlzn.hcpda.uhf.enums.ConnectState getConnectState() {
        return reader.getConnectState();
    }

    /**
     * 强制重置扫描标志位（不调用 SDK 方法）
     */
    public void resetScanningFlag() {
        isScanning = false;
        Log.e(TAG, "扫描标志位已重置");
    }

    /**
     * 重新设置扫描数据监听器（不启动/停止扫描，只替换回调）
     */
    public void setScanListener(OnScanListener listener) {
        this.scanListener = listener;
        if (listener != null && isScanning) {
            reader.setOnInventoryDataListener(new OnInventoryDataListener() {
                @Override
                public void onInventoryData(List<UHFTagEntity> tagEntityList) {
                    if (tagEntityList != null && tagEntityList.size() > 0) {
                        for (UHFTagEntity tag : tagEntityList) {
                            String epc = tag.getEcpHex();
                            String tid = tag.getTidHex();
                            int rssi = tag.getRssi();
                            int count = tag.getCount();
                            if (scanListener != null) {
                                scanListener.onTagScanned(epc, tid, rssi, count);
                            }
                        }
                    }
                }
            });
        }
    }

    /**
     * 扫描回调接口
     */
    public interface OnScanListener {
        void onTagScanned(String epc, String tid, int rssi, int count);
    }
}
