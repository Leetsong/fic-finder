package com.example.ficfinder.models.api;

import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.annotation.JSONType;

@JSONType(typeName = "field")
public class ApiField extends Api {

    public static final String TAG = "com.example.ficfinder.models.api.ApiField";

    @JSONField(name = "iface")
    private String iface;

    @JSONField(name = "type")
    private Type type;

    @JSONField(name = "field")
    private String field;

    public String getIface() {
        return iface;
    }

    public void setIface(String iface) {
        this.iface = iface;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    @Override
    public String getSiganiture() {
        // TODO maynot be compatible with soot
        return "<" + this.pkg + '.' + this.iface + ": " + this.type + " " + this.field + ">";
    }

}