package com.xlzn.hcpda.utils;


import android.util.Log;

public class Test {
    /**
     * 字符串按照长度补零
     */
    public static String AddZero(String str,int length){
        Log.e("TAG", "AddZero-----------:str  " + str );
        Log.e("TAG", "AddZero-----------:length  " + length );
        if (str != null) {
            if(str.length()<length){
                String addzreo = "0";
                for(int i=0;i<length-str.length() - 1;i++){
                    addzreo += "0";
                }
                str = addzreo + str;
            }
        }

        return str;
    }

    /**
     * 将十六进制转换为二进制
     *
     * @param hexString
     * @return
     */
    public static String HexString2binaryString(String hexString) {
        if(hexString.length() == 24){
            //16进制转10进制
            int hexString1int = Integer.valueOf(hexString.substring(0, 6),16);
            int hexString2int = Integer.valueOf(hexString.substring(6, 12),16);
            int hexString3int = Integer.valueOf(hexString.substring(12,18),16);
            int hexString4int = Integer.valueOf(hexString.substring(18,24),16);
            //10进制转2进制
            String bin1=AddZero(Integer.toBinaryString(hexString1int),24);
            String bin2=AddZero(Integer.toBinaryString(hexString2int),24);
            String bin3=AddZero(Integer.toBinaryString(hexString3int),24);
            String bin4=AddZero(Integer.toBinaryString(hexString4int),24);
            //字符串反转
            String AllBin = bin1 + bin2 + bin3 + bin4;
            return AllBin;
        }else
        {
            return null;
        }
    }
    /*
     * 二进制装换成十进制
     */
    public static String BinaryToDec(String Binarys){
        long sum = 0;
        for(int i=Binarys.length()-1;i>=0;i--){
            sum += Integer.parseInt(Binarys.charAt(i)+"")*Math.pow(2,Binarys.length()-i-1);
        }
        return sum+"";
    }
    /*
     * 条码校验位生成
     */
    public static String CheckBit(String str){
        int js_sum = 0;  //奇数和
        int os_sum = 0;  //偶数和
        int all_sum = 0;  //总数和
        int parity_bit = 0; //校验位
        int k = 1; //判断位数
        if(str.length() == 11){
            for(int i = 0;i<str.length();i++){
                if(k<12){
                    if(k%2 ==0 && k!=12){
                        //print('偶数相加%d' % int(i))
                        os_sum += Integer.parseInt(str.charAt(i)+"");
                    }else{
                        //print('奇数相加%d' % int(i))
                        js_sum += Integer.parseInt(str.charAt(i)+"");
                    }
                    k +=1;
                }
            }
            all_sum = js_sum * 3 + os_sum;
            parity_bit = 10 - all_sum % 10;
        }
        return parity_bit + "";
    }
    /**
     * 获取UPC
     * @param EPC
     */
    public static String getUPC(String EPC){
        String EPCBits = AddZero(HexString2binaryString(EPC), 96);
        String Header = Integer.parseInt((String) EPCBits.subSequence(0, 8),2) + "";
        String Filter = Integer.parseInt((String) EPCBits.subSequence(8, 11),2) + "";
        String Partition = Integer.parseInt((String) EPCBits.subSequence(11,14),2) + "";
        int Digits = 0; //公司前缀长度
        int CutoffPoint = 0; //截点
        String UPCPro;
        if(Partition == "0"){
            CutoffPoint = 40;
            Digits = 12;
        }else if(Partition.equals("1")){
            CutoffPoint = 37;
            Digits = 11;
        }else if(Partition.equals("2")){
            CutoffPoint = 34;
            Digits = 10;
        }else if(Partition.equals("3")){
            CutoffPoint = 30;
            Digits = 9;
        }else if(Partition.equals("4")){
            CutoffPoint = 27;
            Digits = 8;
        }else if(Partition.equals("5")){
            CutoffPoint = 24;
            Digits = 7;
        }else if(Partition.equals("6")){
            CutoffPoint = 20;
            Digits = 6;
        }
        String GS1CompanyPrefix = AddZero(BinaryToDec(EPCBits.substring(14, CutoffPoint+14)),Digits);//公司前缀
        String IdIrn = AddZero(BinaryToDec(EPCBits.substring(CutoffPoint+14,58)),12-Digits);//项目参考号码
        if(GS1CompanyPrefix.length()+IdIrn.length() ==13){
            UPCPro = AddZero(IdIrn.substring(0, 1) + GS1CompanyPrefix + IdIrn.substring(1),13);
        }else{
            UPCPro = AddZero(GS1CompanyPrefix + IdIrn,13);
        }
        String UPC = UPCPro + CheckBit(UPCPro.substring(2));
        return UPC;
    }
//    /**
//     * 获取序列号
//     * @param EPC
//     */
//    public String getSerialNumber(String EPC){
//        String EPCBits = AddZero(HexString2binaryString(EPC), 96);
//        return BinaryToDec(EPCBits.substring(58));
//    }
//    public static void main(){
//        Test test = new Test();
//        System.out.println(test.getUPC("30394DC4021BA61B02238F50"));
//        System.out.println(test.getSerialNumber("302C4D3F92C08C4E6F7CEC0C"));
//    }
}


