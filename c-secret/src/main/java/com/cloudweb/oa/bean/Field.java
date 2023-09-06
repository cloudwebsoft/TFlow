package com.cloudweb.oa.bean;

import com.redmoon.oa.flow.FormField;

public class Field {
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPresent() {
        return present;
    }

    public void setPresent(String present) {
        this.present = present;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getFieldType() {
        return fieldType;
    }

    public void setFieldType(int fieldType) {
        this.fieldType = fieldType;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getCssWidth() {
        return cssWidth;
    }

    public void setCssWidth(String cssWidth) {
        this.cssWidth = cssWidth;
    }

    public boolean isFunc() {
        return func;
    }

    public void setFunc(boolean func) {
        this.func = func;
    }

    public String getMacroType() {
        return macroType;
    }

    public void setMacroType(String macroType) {
        this.macroType = macroType;
    }

    public boolean isCanNull() {
        return canNull;
    }

    public void setCanNull(boolean canNull) {
        this.canNull = canNull;
    }

    String name = "";
    String title = "";
    String type = FormField.TYPE_TEXTFIELD;
    String present = ""; // 如果为null，则fastjson.JSON.toJSON时，不会被序列化
    String value = "";
    int fieldType = FormField.FIELD_TYPE_VARCHAR;
    boolean readOnly = false;
    String description = "";
    String rule = "";
    String defaultValue = "";
    String cssWidth = "";
    boolean func = false;
    boolean canNull = true;
    String macroType = FormField.MACRO_NOT;

    public String getReadOnlyType() {
        return readOnlyType;
    }

    public void setReadOnlyType(String readOnlyType) {
        this.readOnlyType = readOnlyType;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    String readOnlyType;
    String format;
}
