package com.xlzn.hcpda.jxl;

import android.annotation.SuppressLint;
import android.os.Environment;
import android.util.Log;

import com.xlzn.hcpda.uhf.entity.UHFTagEntity;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jxl.Workbook;
import jxl.read.biff.BiffException;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import jxl.write.biff.RowsExceededException;

/**
 * Created by Administrator on 2021/6/22.
 */

public class FileImport {
    //    static String xlsFilePath = Environment.getExternalStorageDirectory() + "/Android/";
    @SuppressLint("SdCardPath")
//    static String xlsFilePath = "/storage/emulated/0/Download/";
    static String xlsFilePath = Environment.getExternalStorageDirectory().getPath() + "/scanData/";

    public static boolean daochu(String tmpname, List<UHFTagEntity> lists2) {
        try {
            String file = xlsFilePath + tmpname;
//            if (tmpname.isEmpty())
//                file = xlsFilePath + "xls"
//                        + GetTimesyyyymmddhhmmss() + ".xls";
//            else
//                file = xlsFilePath + tmpname;
            File path2 = new File(xlsFilePath);
            File path3 = new File(file);
//            @SuppressLint("SdCardPath") File path2 = new File("/sdcard/Android/data/com.example.uhf/");
            if (path2.mkdirs()) {
                Log.e("TAG", "创建成功: ");
            } else {
                Log.e("TAG", "创建失败: ");
            }

            if (!path3.exists()) {
                path2.createNewFile();
            }
            Log.e("TAG", "导出d d 路径: " + file);
            List<Object> al22 = new ArrayList<Object>();
            List<String> al2 = new ArrayList<String>();

            al2.add("编号测试");


            // al2.add("筛选栏");

            al22.add(al2);
            FileXls.writeXLS(file, al22);
            List<Object> ac = new ArrayList<Object>();
            for (int i = 0; i < lists2.size(); i++) {
                List<String> al = new ArrayList<String>();
                al.add(lists2.get(i).getEcpHex());
                Log.e("TAG", "EEE: " + lists2.get(i).getEcpHex());
                ac.add(al);
            }

            return FileXls.writeXLS(file, ac);
        } catch (Exception ex) {

            Log.e("TAG", "导出异常 = : " + ex.getMessage());
            return false;
        }
    }

    public static boolean daochuStringList(String tmpname, List<String> lists2) {
        try {
            String file = xlsFilePath + tmpname;
//            if (tmpname.isEmpty())
//                file = xlsFilePath + "xls"
//                        + GetTimesyyyymmddhhmmss() + ".xls";
//            else
//                file = xlsFilePath + tmpname;
            File path2 = new File(xlsFilePath);
//            @SuppressLint("SdCardPath") File path2 = new File("/sdcard/Android/data/com.example.uhf/");
//            if (path2.mkdirs()) {
//                Log.e("TAG", "创建成功: ");
//            } else {
//                Log.e("TAG", "创建失败: ");
//            }

            if (!path2.exists()) {
                path2.createNewFile();
            }
            Log.e("TAG", "导出路径: " +path2.getPath());
//            Log.e("TAG", "导出路径:3 " + file);
            List<Object> al22 = new ArrayList<Object>();
            List<String> al2 = new ArrayList<String>();

            al2.add("编号");

            // al2.add("筛选栏");

            al22.add(al2);
            FileXls.writeXLS(file, al22);
            List<Object> ac = new ArrayList<Object>();
            for (int i = 0; i < lists2.size(); i++) {
                List<String> al = new ArrayList<String>();
                al.add(lists2.get(i));
                // al.add(sxl);
                ac.add(al);
            }

            return FileXls.writeXLS(file, ac);
        } catch (Exception ex) {

            Log.e("TAG", "导出异常 = : " + ex.getMessage());
            return false;
        }
    }


    public static String GetTimesyyyymmdd() {

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date curDate = new Date(System.currentTimeMillis());// 获取当前时间
        String dt = formatter.format(curDate);

        return dt;

    }

    public static String GetTimesddMMyy() {

        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yy");
        Date curDate = new Date(System.currentTimeMillis());// 获取当前时间
        String dt = formatter.format(curDate);

        return dt;

    }

    public static String GetTimesyyyymmddhhmmss() {

        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        Date curDate = new Date(System.currentTimeMillis());// 获取当前时间
        String dt = formatter.format(curDate);

        return dt;

    }


    public static boolean writeJXL(String tmpname, List<SMRBean> smrBeans) throws IOException, RowsExceededException, WriteException {
        Workbook wb = null;
        String file2 = xlsFilePath + tmpname;
//            if (tmpname.isEmpty())
//                file = xlsFilePath + "xls"
//                        + GetTimesyyyymmddhhmmss() + ".xls";
//            else
//                file = xlsFilePath + tmpname;
        File path2 = new File(xlsFilePath);
        File path3 = new File(file2);
//            @SuppressLint("SdCardPath") File path2 = new File("/sdcard/Android/data/com.example.uhf/");
        if (path2.mkdirs()) {
            Log.e("TAG", "创建成功: ");
        } else {
            Log.e("TAG", "创建失败: ");
        }

        if (!path3.exists()) {
            path2.createNewFile();
        }
        try {
            wb = Workbook.getWorkbook(path3);
        } catch (BiffException var10) {
            var10.printStackTrace();
        }

        WritableWorkbook book = Workbook.createWorkbook(path3, wb);
        WritableSheet sheet = book.getSheet(0);
        int i = sheet.getRows();

        Label labels = new Label(0,0,"表头一的数据");
        sheet.addCell(labels);

        book.write();
        book.close();
        wb.close();
        return true;
    }
}
