package com.xlzn.hcpda.jxl;

import java.util.List;

/**
 * ： on 2024/6/11.
 * ：630646654@qq.com
 */

public class SMRBean {


    private List<ThDTO> th;

    private List<DataDTO> data;


    public static class ThDTO {

        private String name;

        private String prop;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getProp() {
            return prop;
        }

        public void setProp(String prop) {
            this.prop = prop;
        }
    }


    public static class DataDTO {
        private String a;
        private String b;
        private String c;
        private String d;

    }
}
