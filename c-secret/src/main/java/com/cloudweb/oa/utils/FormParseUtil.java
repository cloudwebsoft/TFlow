package com.cloudweb.oa.utils;

import cn.js.fan.util.StrUtil;
import com.cloudweb.oa.bean.Field;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.flow.FormField;
import com.redmoon.oa.flow.macroctl.MacroCtlMgr;
import com.redmoon.oa.flow.macroctl.MacroCtlUnit;
import com.redmoon.oa.visual.FuncUtil;
import org.apache.commons.lang3.StringEscapeUtils;
import org.htmlparser.Node;
import org.htmlparser.Parser;
import org.htmlparser.filters.AndFilter;
import org.htmlparser.filters.HasAttributeFilter;
import org.htmlparser.filters.TagNameFilter;
import org.htmlparser.nodes.TagNode;
import org.htmlparser.tags.*;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;

import java.util.Iterator;
import java.util.Vector;

public class FormParseUtil {
    public final String DEFAULTVALUE = "default";

    String content;

    public Vector<Field> getFields() {
        return fields;
    }

    public void setFields(Vector<Field> fields) {
        this.fields = fields;
    }

    Vector<Field> fields;

    public FormParseUtil(String content) {
        this.content = content;
        fields = new Vector<Field>();
        parseTextfield();
        parseTextArea();
        parseSelect();
    }

    public void parseSelect() {
        Parser parser;
        try {
            parser = new Parser(content);
            parser.setEncoding("utf-8");//
            TagNameFilter filter = new TagNameFilter("select");
            NodeList nodeList = parser.parse(filter);
            int len = nodeList.size();
            for (int i=0; i<len; i++) {
                SelectTag node = (SelectTag)nodeList.elementAt(i);
                Field ff = new Field();

                String style = node.getAttribute("style");
                if (style!=null) {
                    style = style.toLowerCase();
                    String[] ary = StrUtil.split(style, ";");
                    for (int k=0; k<ary.length; k++) {
                        String[] attAry = StrUtil.split(ary[k], ":");
                        if (attAry!=null) {
                            if (attAry.length==2) {
                                String att = attAry[0].trim();
                                if ("width".equals(att)) {
                                    String w = attAry[1].trim();
                                    ff.setCssWidth(w);
                                }
                            }
                        }
                    }
                }

                String name = node.getAttribute("name");
                if (name==null) {
                    // 在AbstractMacroCtl的doReplaceMacroCtlWithHTMLCtl中需要调用FormParser
                    // 而此时有些宏控件已经展开，比如出现了"选择"按钮，这时就需要跳过没有name的控件
                    continue;
                }
                ff.setName(name);
                String title = node.getAttribute("title");
                if (title==null) {
                    continue;
                }
                ff.setTitle(title);

                // 查找canNull
                String canNull, minT, minV, maxT, maxV, description;
                canNull = StrUtil.getNullStr(node.getAttribute("canNull"));
                ff.setCanNull(!"0".equals(canNull));

                minT = StrUtil.getNullStr(node.getAttribute("minT"));
                minV = StrUtil.getNullStr(node.getAttribute("minV"));
                maxT = StrUtil.getNullStr(node.getAttribute("maxT"));
                maxV = StrUtil.getNullStr(node.getAttribute("maxV"));

                String rule = "";
                if (!"".equals(minV)) {
                    minT = minT.replaceAll("d", ">");
                    rule = "min" + minT + minV;
                }
                if (!"".equals(maxV)) {
                    maxT = maxT.replaceAll("x", "<");
                    if ("".equals(rule)) {
                        rule = "max" + maxT + maxV;
                    } else {
                        rule += ",max" + maxT + maxV;
                    }
                }
                ff.setRule(rule);

                int size = StrUtil.toInt(StrUtil.getNullStr(node.getAttribute("size")), 1);
                if (size>1) {
                    ff.setType(FormField.TYPE_LIST);
                }
                else {
                    ff.setType(FormField.TYPE_SELECT);
                }

                String fieldType = node.getAttribute("fieldType");
                ff.setFieldType(StrUtil.toInt(fieldType, FormField.FIELD_TYPE_VARCHAR));

                String defaultValue = null;
                OptionTag[] opts = node.getOptionTags();
                for (OptionTag tag : opts) {
                    if (tag.getAttribute("selected") != null) {
                        defaultValue = tag.getValue();
                    }
                }
                if (defaultValue!=null) {
                    ff.setDefaultValue(defaultValue);
                }

                ff.setReadOnlyType(StrUtil.getNullStr(node.getAttribute("readOnlyType")));
                ff.setFormat(StrUtil.getNullStr(node.getAttribute("format")));

                fields.addElement(ff);
            }
        } catch (ParserException e) {
            LogUtil.getLog(getClass()).error(e);
        }
    }

    public void parseTextArea() {
        Parser parser;
        try {
            parser = new Parser(content);
            parser.setEncoding("utf-8");//
            TagNameFilter filter = new TagNameFilter("textarea");
            NodeList nodeList = parser.parse(filter);
            int len = nodeList.size();
            for (int i = 0; i < len; i++) {
                TextareaTag node = (TextareaTag) nodeList.elementAt(i);

                Field ff = new Field();

                String style = node.getAttribute("style");
                if (style!=null) {
                    style = style.toLowerCase();
                    String[] ary = StrUtil.split(style, ";");
                    for (String s : ary) {
                        String[] attAry = StrUtil.split(s, ":");
                        if (attAry != null) {
                            if (attAry.length == 2) {
                                String att = attAry[0].trim();
                                if ("width".equals(att)) {
                                    String w = attAry[1].trim();
                                    ff.setCssWidth(w);
                                }
                            }
                        }
                    }
                }

                String name = node.getAttribute("name");
                if (name==null) {
                    // 在AbstractMacroCtl的doReplaceMacroCtlWithHTMLCtl中需要调用FormParser
                    // 而此时有些宏控件已经展开，比如出现了"选择"按钮，这时就需要跳过没有name的控件
                    continue;
                }
                ff.setName(name);
                String title = node.getAttribute("title");
                if (title==null) {
                    continue;
                }
                ff.setTitle(title);

                ff.setValue(node.getValue());
                ff.setDefaultValue(node.getValue());
                ff.setType(FormField.TYPE_TEXTAREA);

                // 查找canNull
                String canNull, minT, minV, maxT, maxV, description;
                canNull = StrUtil.getNullStr(node.getAttribute("canNull"));
                ff.setCanNull(!"0".equals(canNull));

                minT = StrUtil.getNullStr(node.getAttribute("minT"));
                minV = StrUtil.getNullStr(node.getAttribute("minV"));
                maxT = StrUtil.getNullStr(node.getAttribute("maxT"));
                maxV = StrUtil.getNullStr(node.getAttribute("maxV"));

                String rule = "";
                if (!"".equals(minV)) {
                    minT = minT.replaceAll("d", ">");
                    rule = "min" + minT + minV;
                }
                if (!"".equals(maxV)) {
                    maxT = maxT.replaceAll("x", "<");
                    if ("".equals(rule)) {
                        rule = "max" + maxT + maxV;
                    } else {
                        rule += ",max" + maxT + maxV;
                    }
                }
                ff.setRule(rule);

                ff.setReadOnlyType(StrUtil.getNullStr(node.getAttribute("readOnlyType")));
                ff.setFormat(StrUtil.getNullStr(node.getAttribute("format")));

                fields.addElement(ff);
            }
        }
        catch (ParserException e) {
            LogUtil.getLog(getClass()).error(e);
        }
    }


    /**
     * 2017/3/15 fgf
     * 将parseTextfield改为了通过htmlparser解析，因为默认值获取不准确，因为正则里面含有了不允许>，而func型的字段里面可能会出现布尔表达式
     */
    public void parseTextfield() {
        Parser parser;
        try {
            MacroCtlMgr mm = new MacroCtlMgr();
            parser = new Parser(content);
            parser.setEncoding("utf-8");
            TagNameFilter filter = new TagNameFilter("input");
            NodeList nodeList = parser.parse(filter);
            int len = nodeList.size();
            for (int i=0; i<len; i++) {
                InputTag node = (InputTag)nodeList.elementAt(i);
                Field ff = new Field();

                String style = node.getAttribute("style");
                if (!StrUtil.isEmpty(style)) {
                    style = style.toLowerCase();
                    String[] ary = StrUtil.split(style, ";");
                    for (String s : ary) {
                        String[] attAry = StrUtil.split(s, ":");
                        if (attAry != null) {
                            if (attAry.length == 2) {
                                String att = attAry[0].trim();
                                if ("width".equals(att)) {
                                    String w = attAry[1].trim();
                                    ff.setCssWidth(w);
                                }
                            }
                        }
                    }
                }

                String name = node.getAttribute("name");
                if (name==null) {
                    // 在AbstractMacroCtl的doReplaceMacroCtlWithHTMLCtl中需要调用FormParser
                    // 而此时有些宏控件已经展开，比如出现了"选择"按钮，这时就需要跳过没有name的控件
                    continue;
                }
                ff.setName(name);
                String title = node.getAttribute("title");
                if (title==null) {
                    continue;
                }
                ff.setTitle(title);
                String value = StringEscapeUtils.unescapeHtml3(node.getAttribute("value"));
                ff.setValue(value);

                // 记录checkbox的组名
                String desc = node.getAttribute("description");
                if (desc!=null && !"".equals(desc)) {
                    ff.setDescription(desc);
                }

                String present = node.getAttribute("present");
                if (present!=null && !"".equals(present)) {
                    ff.setPresent(present);
                }

                String kind = "";
                String macroType = "";
                String macroDefaultValue = "";
                String fieldType = "";

                // 查找fieldType
                fieldType = node.getAttribute("fieldType");
                ff.setFieldType(StrUtil.toInt(fieldType, FormField.FIELD_TYPE_VARCHAR));

                // 置宏控件的readonly属性
                String readonly = node.getAttribute("readonly");
                if (readonly!=null) {
                    ff.setReadOnly(true);
                }

                ff.setReadOnlyType(StrUtil.getNullStr(node.getAttribute("readOnlyType")));
                ff.setFormat(StrUtil.getNullStr(node.getAttribute("format")));

                kind = StrUtil.getNullStr(node.getAttribute("kind"));
                macroType = node.getAttribute("macroType");
                macroDefaultValue = node.getAttribute("macroDefaultValue");

                // 查找canNull
                String canNull, minT, minV, maxT, maxV, description;
                canNull = StrUtil.getNullStr(node.getAttribute("canNull"));
                ff.setCanNull(!"0".equals(canNull));

                minT = StrUtil.getNullStr(node.getAttribute("minT"));
                minV = StrUtil.getNullStr(node.getAttribute("minV"));
                maxT = StrUtil.getNullStr(node.getAttribute("maxT"));
                maxV = StrUtil.getNullStr(node.getAttribute("maxV"));

                String rule = "";
                if (!"".equals(minV)) {
                    minT = minT.replaceAll("d", ">");
                    rule = "min" + minT + minV;
                }
                if (!"".equals(maxV)) {
                    maxT = maxT.replaceAll("x", "<");
                    if ("".equals(rule)) {
                        rule = "max" + maxT + maxV;
                    } else {
                        rule += ",max" + maxT + maxV;
                    }
                }
                ff.setRule(rule);

                // 判断是否含有
                if (FuncUtil.hasFunc(value) && !kind.equals(FormField.TYPE_CALCULATOR)) {
                    ff.setFunc(true);
                }

                // 如果是计算控件，如果控件为sum型，则取出其formCode属性，即对应的嵌套表的表单编码
                if (kind.equals(FormField.TYPE_CALCULATOR)) {
                    String formCode = node.getAttribute("formCode");
                    if (formCode != null) {
                        ff.setPresent(formCode);
                    }
                }

                if (value == null) {
                    value = "";
                }
                if (value.equals(DEFAULTVALUE)) {
                    value = "";
                }
                if (kind.equals(FormField.TYPE_MACRO) && !"".equals(macroType)) {
                    // chrome中对公式宏控件中的描述字符串会进行html转义，而IE中则因为用单引号括起来，所以不会转义
                    // 如：
                    // description='{"code":"ndnzeNotChecked","params":"id,xmmc,hj","name":"实际利用内资额未经审核","isAutoWhenList":false}'
                    // description="{&quot;code&quot;:&quot;ndnzeNotChecked&quot;,&quot;params&quot;:&quot;id,xmmc,hj&quot;,&quot;name&quot;:&quot;实际利用内资额未经审核&quot;,&quot;isAutoWhenList&quot;:false}"
                    value = StringEscapeUtils.unescapeHtml4(macroDefaultValue);
                    ff.setMacroType(macroType);
                    ff.setDefaultValue(value);

                    description = StringEscapeUtils.unescapeHtml4(StrUtil.getNullStr(node.getAttribute("description")));
                    MacroCtlUnit mu = mm.getMacroCtlUnit(macroType);
                    // 有可能是客户自定义的宏控件
                    if (mu==null) {
                        // 向下兼容
                        if ("".equals(description)) {
                            // 向下兼容
                            ff.setDescription(value);
                        }
                        else {
                            ff.setDescription(description);
                        }
                    }
                    else {
                        if (mu.getVersion() >= 2) {
                            ff.setDescription(description);
                        } else {
                            // 向下兼容
                            if ("".equals(description)) {
                                // 向下兼容
                                ff.setDescription(value);
                            } else {
                                ff.setDescription(description);
                            }
                        }
                    }
                } else if (kind.equals(FormField.TYPE_DATE)
                        || kind.equals(FormField.TYPE_DATE_TIME)) {
                    if (kind.equals(FormField.TYPE_DATE)) {
                        ff.setFieldType(FormField.FIELD_TYPE_DATE);
                    }
                    else {
                        ff.setFieldType(FormField.FIELD_TYPE_DATETIME);
                    }
                    if (!"".equals(value) && "CURRENT".equals(value)) {
                        value = FormField.DATE_CURRENT;
                        ff.setDefaultValue(value);
                    } else {
                        ff.setDefaultValue(value);
                    }
                } else {
                    ff.setDefaultValue(value);
                }

                String attrType = node.getAttribute("type");
                if ("".equals(kind)) {
                    if ("checkbox".equals(attrType)) {
                        ff.setType(FormField.TYPE_CHECKBOX);
                        String checked = node.getAttribute("checked");
                        if (checked!=null) {
                            ff.setDefaultValue("1");
                        } else {
                            ff.setDefaultValue("0");
                        }
                    } else if ("radio".equals(attrType)) {
                        ff.setType(FormField.TYPE_RADIO);
                        String checked = node.getAttribute("checked");
                        if (checked!=null) {
                            ff.setDefaultValue(value);
                        } else {
                            ff.setDefaultValue("");
                        }
                    } else {
                        ff.setType(FormField.TYPE_TEXTFIELD);
                    }
                } else {
                    ff.setType(kind);
                    if (kind.equals(FormField.TYPE_BUTTON)) {
                        // 按钮型则置默认值为脚本值
                        String script = node.getAttribute("script");

                        // 对脚本解码
                        script = script.replaceAll("&amp;gt;", "&");
                        script = script.replaceAll("&amp;", "&");
                        script = script.replaceAll("&#br;", ";");

                        // "openWin(&amp;#39;visual/module_list_relate.jsp?parentId={$id}&amp;gt;formCodeRelated=dzycl&amp;gt;formCode=qyjcbb&amp;#39;, 800, 600)"

                        LogUtil.getLog(getClass()).info("script1=" + script);

                        script = StringEscapeUtils.unescapeHtml3(script);
                        LogUtil.getLog(getClass()).info("script2=" + script);

                        // script = script.replaceAll("&quot;", "\"");
                        // script = script.replaceAll("&#39;", "'");

                        // 20200419 注释，改用description
                        // ff.setDefaultValue(script);

                        ff.setDefaultValue("");
                        ff.setDescription(script);
                    }
                }

                // IE9下需判定type=\"radio\"
                if ("radio".equals(attrType)) {
                    // 检查fields中是否已有同名的控件，如果有，则判断是否需更新默认值，如果没有则加入
                    Iterator ir = fields.iterator();
                    boolean isFound = false;
                    while (ir.hasNext()) {
                        Field f = (Field) ir.next();
                        if (f.getName().equals(ff.getName())) {
                            if (!"".equals(ff.getDefaultValue())
                                    && "".equals(f.getDefaultValue())) {
                                f.setDefaultValue(ff.getDefaultValue());
                            }
                            isFound = true;
                            break;
                        }
                    }
                    if (!isFound) {
                        Parser parserRadioSpan = new Parser(content);
                        parserRadioSpan.setEncoding("utf-8");
                        AndFilter filterRadioSpan = new AndFilter(new TagNameFilter("span"),
                                new HasAttributeFilter("orgname", ff.getName()));
                        NodeList nodes = parserRadioSpan.parse(filterRadioSpan);
                        if (nodes == null || nodes.size() == 0) {
                            LogUtil.getLog(getClass()).error("单选框: " + ff.getTitle() + " " + ff.getName() + " 格式错误");
                        } else {
                            Span nodeSpan = (Span)nodes.elementAt(0);
                            minT = StrUtil.getNullStr(nodeSpan.getAttribute("minT"));
                            minV = StrUtil.getNullStr(nodeSpan.getAttribute("minV"));
                            maxT = StrUtil.getNullStr(nodeSpan.getAttribute("maxT"));
                            maxV = StrUtil.getNullStr(nodeSpan.getAttribute("maxV"));

                            rule = "";
                            if (!"".equals(minV)) {
                                minT = minT.replaceAll("d", ">");
                                rule = "min" + minT + minV;
                            }
                            if (!"".equals(maxV)) {
                                maxT = maxT.replaceAll("x", "<");
                                if ("".equals(rule)) {
                                    rule = "max" + maxT + maxV;
                                } else {
                                    rule += ",max" + maxT + maxV;
                                }
                            }
                            ff.setRule(rule);
                        }

                        fields.addElement(ff);
                    }
                } else {
                    fields.addElement(ff);
                }
            }
        } catch (ParserException e) {
            LogUtil.getLog(getClass()).error(e);
        }
    }
}
