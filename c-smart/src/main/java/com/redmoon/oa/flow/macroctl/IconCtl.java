package com.redmoon.oa.flow.macroctl;

import cn.js.fan.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudweb.oa.utils.SysUtil;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.base.IFormDAO;
import com.redmoon.oa.flow.FormField;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.visual.SQLBuilder;

import javax.servlet.http.HttpServletRequest;

/**
 * <p>Title: 利用JQuery实现的图标控件</p>
 *
 * <p>
 * </p>
 *
 * <p>Copyright: Copyright (c) 2007</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class IconCtl extends AbstractMacroCtl {

    public IconCtl() {
    }

    @Override
    public String getValueForExport(HttpServletRequest request, FormField ff, String fieldValue) {
        return fieldValue;
    }

    /**
     * 用于列表中显示宏控件的值
     *
     * @param request    HttpServletRequest
     * @param ff         FormField
     * @param fieldValue String
     * @return String
     */
    @Override
    public String converToHtml(HttpServletRequest request, FormField ff, String fieldValue) {
        String v = StrUtil.getNullStr(fieldValue);

        String props = ff.getDescription();

        JSONObject jsonProps = null;
        try {
            jsonProps = JSONObject.parseObject(props);
        }
        catch (JSONException e) {
            return "控件描述格式非法";
        }

        JSONArray jsonArr = jsonProps.getJSONArray("options");
        if (jsonArr == null) {
            return "控件描述中选项格式非法";
        }

        boolean isOnlyIcon = true;
        if (jsonProps.containsKey("isOnlyIcon")) {
            isOnlyIcon = jsonProps.getBoolean("isOnlyIcon");
        }

        String iconUrl = "";
        JSONObject json = getByVal(jsonProps, v);
        if (json != null) {
            String name = json.getString("name");
            // String icon = json.getString("icon");

            if (request!=null && !"true".equals(request.getAttribute("isForExport"))) {
                // 此处因为在ModuleService中setRowField时用了getAbstract，会导致为空，因为img标签会被过滤掉
                /*SysUtil sysUtil = SpringUtil.getBean(SysUtil.class);
                iconUrl = "<img class='icon-ctl' src='" + sysUtil.getRootPath() + "/showImgInJar.do?path=/static/images/symbol/" + icon + "'/>";
                if (!isOnlyIcon) {
                    iconUrl += name;
                }*/
                iconUrl = name;
            }
            else {
                iconUrl = name;
            }
        }

        return iconUrl;
    }

    @Override
    public String getMetaData(FormField formField) {
        JSONObject json = new JSONObject();
        String v = StrUtil.getNullStr(formField.getValue());

        String props = formField.getDescription();

        JSONObject jsonProps = null;
        try {
            jsonProps = JSONObject.parseObject(props);
        }
        catch (JSONException e) {
            LogUtil.getLog(getClass()).error("控件描述格式非法");
            return json.toString();
        }

        JSONArray jsonArr = jsonProps.getJSONArray("options");
        if (jsonArr == null) {
            LogUtil.getLog(getClass()).error("控件描述中选项格式非法");
            return json.toString();
        }

        JSONObject jsonObject = getByVal(jsonProps, v);
        if (jsonObject != null) {
            String icon = jsonObject.getString("icon");
            SysUtil sysUtil = SpringUtil.getBean(SysUtil.class);
            json.put("url", sysUtil.getRootPath() + "/showImgInJar.do?path=/static/images/symbol/" + icon);
        }
        return json.toString();
    }

    /**
     * @param request HttpServletRequest
     * @param ff      FormField
     * @return String
     */
    @Override
    public String convertToHTMLCtl(HttpServletRequest request, FormField ff) {
        // 控件默认值：0,5（0表示默认不选，5表示5级）
        String props = ff.getDescription();
        JSONObject jsonProps = null;
        try {
            jsonProps = JSONObject.parseObject(props);
        }
        catch (JSONException e) {
            return "控件描述格式非法";
        }

        JSONArray jsonArr = jsonProps.getJSONArray("options");
        if (jsonArr == null) {
            return "控件描述中选项格式非法";
        }

        String style = "";
        if (!"".equals(ff.getCssWidth())) {
            style = "style='width:" + ff.getCssWidth() + "'";
        }

        StringBuilder sb = new StringBuilder();
        String strReadOnly = "";
        if (ff.isReadonly()) {
            strReadOnly = " readonly='readonly' ";
        }

        SysUtil sysUtil = SpringUtil.getBean(SysUtil.class);

        String defaultVal = null;
        sb.append("<span id='" + ff.getName() + "_wrapper'>");
        sb.append("<select id='" + ff.getName() + "' name='" + ff.getName() + "' " + style + strReadOnly + ">");
        sb.append("<option value=''>无</option>");
        for (int i = 0; i < jsonArr.size(); i++) {
            JSONObject json = jsonArr.getJSONObject(i);
            boolean selected = false;
            if (json.containsKey("selected")) {
                selected = json.getBoolean("selected");
                if (selected) {
                    defaultVal = json.getString("value");
                }
            }
            String icon = json.getString("icon");
            sb.append("<option value=\"" + json.getString("value") + "\" " + (selected ? "selected" : "") + " style=\"background-image: url('" + sysUtil.getRootPath() + "/static/images/symbol/" + icon + "');\">" + json.getString("name") + "</option>");
        }
        sb.append("</select>");

        sb.append("<script>\n");
        sb.append("async function ctlFunc_" + ff.getName() + "() {\n");
        if (request.getAttribute("isIconCtlJS_" + ff.getName()) == null) {
            String pageType = (String) request.getAttribute("pageType");
            sb.append("await ajaxGetJS(\"/flow/macro/macro_js_icon_ctl.jsp?pageType=" + pageType
                    + "&formCode=" + StrUtil.UrlEncode(ff.getFormCode())
                    + "&fieldName=" + ff.getName() + "&isHidden=" + ff.isHidden() + "&editable=" + ff.isEditable()
                    + "\", {});\n");
            request.setAttribute("isIconCtlJS_" + ff.getName(), "y");
        }

        String pageType = StrUtil.getNullStr((String) request.getAttribute("pageType"));
        if (!pageType.contains("show")) {
            sb.append("$(function() {\n");
            if (ff.isReadonly()) {
                // 顺序得在formatStatePrompt之前，否则会丢失图标
                sb.append("$(fo('" + ff.getName() + "')).select2({'disabled':'readonly'});\n"); // 会被禁用
                // 注意select2的disabled同时会将隐藏的实际字段变为disabled，需将其置为非disabled，否则提交表单验证时可能会报错
                // sb.append("$('select[name=" + ff.getName() + "]').attr('disabled', false);\n"); // 无效，会取消只读
                // sb.append("o('" + ff.getName() + "').removeAttribute('disabled');\n"); // 也无效，同样会取消只读
                // sb.append("$('#" + ff.getName() + "').select2('readonly', true);\n"); // 会被禁用，且看不到图标
            }
            sb.append("     $(fo('" + ff.getName() + "')).select2({templateResult: formatStatePrompt,templateSelection: formatStatePrompt});\n");
            sb.append("});\n");
            // 设置默认值
            if (null != defaultVal) {
                sb.append("$(fo('" + ff.getName() + "')).val(['" + defaultVal +"']).trigger('change');\n");
            }
        }

        sb.append("}\n");
        sb.append("ctlFunc_" + ff.getName() + "();\n");
        sb.append("</script>");

        sb.append("</span>");
        return sb.toString();
    }

    @Override
    public String convertToHTMLCtlForQuery(HttpServletRequest request, FormField ff) {
        if (ff.getCondType().equals(SQLBuilder.COND_TYPE_FUZZY)) {
            return super.convertToHTMLCtlForQuery(request, ff);
        }
        String props = ff.getDescription();
        JSONObject jsonProps = null;
        try {
            jsonProps = JSONObject.parseObject(props);
        }
        catch (JSONException e) {
            return "控件描述格式非法";
        }

        JSONArray jsonArr = jsonProps.getJSONArray("options");
        if (jsonArr == null) {
            return "控件描述中选项格式非法";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<select id='" + ff.getName() + "' name='" + ff.getName() + "'>");
        sb.append("<option value=''></option>");
        for (int i = 0; i < jsonArr.size(); i++) {
            JSONObject json = jsonArr.getJSONObject(i);
            boolean selected = json.getBoolean("selected");
            String icon = json.getString("icon");
            SysUtil sysUtil = SpringUtil.getBean(SysUtil.class);
            sb.append("<option value=\"" + json.getString("value") + "\"" + (selected ? "selected" : "") + " style=\"background-image: url('" + sysUtil.getRootPath() + "/static/images/symbol/" + icon + "');\">" + json.getString("name") + "</option>");
        }
        sb.append("</select>\n");
        sb.append("<script>\n");
        sb.append("async function ctlFunc_" + ff.getName() + "() {\n");
        if (request.getAttribute("isIconCtlJS_" + ff.getName()) == null) {
            String pageType = (String) request.getAttribute("pageType");
            sb.append("await ajaxGetJS(\"/flow/macro/macro_js_icon_ctl.jsp?pageType=" + pageType
                    + "&formCode=" + StrUtil.UrlEncode(ff.getFormCode())
                    + "&fieldName=" + ff.getName() + "&isHidden=" + ff.isHidden() + "&editable=" + ff.isEditable()
                    + "\", {});\n");
            request.setAttribute("isIconCtlJS_" + ff.getName(), "y");
        }
        sb.append("console.log('icon select2 start');\n");
        sb.append("$(fo('" + ff.getName() + "')).select2({templateResult: formatStatePrompt,templateSelection: formatStatePrompt});\n");
        sb.append("console.log('icon select2 end');\n");
        sb.append("}\n");
        sb.append("ctlFunc_" + ff.getName() + "();\n");
        sb.append("</script>\n");
        return sb.toString();
    }

    public JSONObject getByVal(JSONObject jsonProps, String fieldValue) {
        String v = StrUtil.getNullStr(fieldValue);
        JSONArray jsonArr = jsonProps.getJSONArray("options");
        if (jsonArr == null) {
            DebugUtil.e(getClass(), "getByVal", "控件描述中选项格式非法");
        }

        for (int i = 0; i < jsonArr.size(); i++) {
            JSONObject json = jsonArr.getJSONObject(i);
            String value = json.getString("value");
            if (value.equals(v)) {
                return json;
            }
        }
        return null;
    }

    @Override
    public String getReplaceCtlWithValueScript(IFormDAO ifdao, FormField ff) {
        if (ff.getValue() == null) {
            return "";
        }

        String v = converToHtml(null, ff, ff.getValue());
        return "ReplaceCtlWithValue('" + ff.getName() +"', '" + ff.getType() + "',\"" + v + "\");\n";
    }

    /**
     * 获取用来保存宏控件原始值的表单中的HTML元素中保存的值，生成用以给控件赋值的脚本
     *
     * @return String
     */
    @Override
    public String getSetCtlValueScript(HttpServletRequest request, IFormDAO IFormDao, FormField ff, String formElementId) {
        String pageType = StrUtil.getNullStr((String) request.getAttribute("pageType"));
        if ("edit".equals(pageType)) {
            if (ff.getValue() != null && !"".equals(ff.getValue())) {
                return "$(fo('" + ff.getName() + "')).val('" + ff.getValue() + "').trigger('change');\n";
            } else {
                return super.getSetCtlValueScript(request, IFormDao, ff, formElementId);
            }
        } else {
            return super.getSetCtlValueScript(request, IFormDao, ff, formElementId);
        }
    }

    @Override
    public String getControlType() {
        return "select";
    }

    @Override
    public String getControlValue(String userName, FormField ff) {
        return "";
    }

    @Override
    public String getControlText(String userName, FormField ff) {
        return "";
    }

    @Override
    public String getControlOptions(String userName, FormField ff) {
        org.json.JSONArray selects = new org.json.JSONArray();

        String props = ff.getDescription();
        JSONObject jsonProps = null;
        try {
            jsonProps = JSONObject.parseObject(props);
        }
        catch (JSONException e) {
            LogUtil.getLog(getClass()).error(e);
            return selects.toString();
        }

        JSONArray jsonArr = jsonProps.getJSONArray("options");
        if (jsonArr == null) {
            return selects.toString();
        }
        for (int i = 0; i < jsonArr.size(); i++) {
            org.json.JSONObject select = new org.json.JSONObject();
            JSONObject json = jsonArr.getJSONObject(i);
            try {
                select.put("name", json.getString("name"));
                select.put("value", json.getString("value"));
                selects.put(select);
            } catch (org.json.JSONException ex) {
                LogUtil.getLog(getClass()).error(ex);
            }
        }
        return selects.toString();
    }

}
