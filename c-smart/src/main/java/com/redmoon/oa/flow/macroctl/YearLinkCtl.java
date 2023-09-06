package com.redmoon.oa.flow.macroctl;

import java.util.Calendar;
import java.util.Date;


import javax.servlet.http.HttpServletRequest;

import cn.js.fan.util.DateUtil;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.visual.SQLBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import cn.js.fan.util.StrUtil;

import com.redmoon.oa.flow.FormField;
import com.redmoon.oa.pvg.Privilege;

public class YearLinkCtl extends AbstractMacroCtl {

    @Override
    public String convertToHTMLCtl(HttpServletRequest request, FormField ff) {
        StringBuilder sb = new StringBuilder();
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR); // 当前年份

        String style = "";
        if (!"".equals(ff.getCssWidth())) {
            style = "style='width:" + ff.getCssWidth() + "'";
        }
        else {
            style = "style='width:150px'";
        }

        if (ff.isReadonly()) {
            sb.append("<select id='" + ff.getName() + "' name='" + ff.getName() + "' title='" + ff.getTitle() + "' readonly " + style + " onfocus='this.defaultIndex=this.selectedIndex;' onchange='this.selectedIndex=this.defaultIndex;'>");
        } else {
            sb.append("<select id= '").append(ff.getName()).append("' name='").append(ff.getName()).append("' ").append(style).append(" >");
        }
        sb.append("<option value=''></option>");
        // 前30年
        for (int i = 30; i >= 1; i--) {
            sb.append("<option value='").append(currentYear - i).append("' >").append(currentYear - i).append("</option>");
        }
        sb.append("<option value='").append(currentYear).append("' selected >").append(currentYear).append("</option>");
        // 后30年
        for (int i = 1; i <= 30; i++) {
            sb.append("<option value='").append(currentYear + i).append("' >").append(currentYear + i).append("</option>");
        }
        sb.append("</select>");

        return sb.toString();
    }

    /**
     * 将宏控件展开为用于查询的HTML字符串
     *
     * @param request HttpServletRequest
     * @param ff      FormField
     * @return String
     */
    @Override
    public String convertToHTMLCtlForQuery(HttpServletRequest request, FormField ff) {
        if (ff.getCondType().equals(SQLBuilder.COND_TYPE_FUZZY)) {
            return super.convertToHTMLCtlForQuery(request, ff);
        }
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);//当前年份
        StringBuilder sb = new StringBuilder();
        sb.append("<select id= '").append(ff.getName()).append("' name='").append(ff.getName()).append("' >");
        sb.append("<option value=''>无</option>");
        //前30年
        for (int i = 30; i >= 1; i--) {
            sb.append("<option value='").append(currentYear - i).append("' >").append(currentYear - i).append("</option>");
        }
        // sb.append("<option value='").append(currentYear).append("' selected>").append(currentYear).append("</option>");
        sb.append("<option value='").append(currentYear).append("'>").append(currentYear).append("</option>");
        //后30年
        for (int i = 1; i <= 30; i++) {
            sb.append("<option value='").append(currentYear + i).append("' >").append(currentYear + i).append("</option>");
        }
        sb.append("</select>");
        return sb.toString();
    }

    @Override
    public String getControlOptions(String arg0, FormField arg1) {
        JSONArray arr = new JSONArray();
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);//当前年份
        try {
            for (int i = 30; i >= 1; i--) {
                JSONObject obj = new JSONObject();
                obj.put("name", currentYear - i);
                obj.put("value", currentYear - i);
                arr.put(obj);
            }
            JSONObject obj = new JSONObject();
            obj.put("name", currentYear);
            obj.put("value", currentYear);
            arr.put(obj);
            for (int i = 1; i <= 30; i++) {
                obj = new JSONObject();
                obj.put("name", currentYear + i);
                obj.put("value", currentYear + i);
                arr.put(obj);
            }
        } catch (JSONException e) {
            LogUtil.getLog(getClass()).error(e);
        }
        return arr.toString();
    }

    @Override
    public String getControlText(String arg0, FormField ff) {
        return StrUtil.getNullStr(ff.getValue());
    }

    @Override
    public String getControlType() {
        return FormField.TYPE_SELECT;
    }

    @Override
    public String getControlValue(String arg0, FormField ff) {
		return StrUtil.getNullStr(ff.getValue());
    }

    /**
     * 取得用来保存宏控件原始值及toHtml后的值的表单中的HTML元素，通常前者为textarea，后者为span
     * 如果不继承此方法，则该控件的默认值会为空，而不是当前时间，设了selected也没用，会被setCtlValue重置为空
     *
     * @return String
     */
    @Override
    public String getOuterHTMLOfElementsWithRAWValueAndHTMLValue(
            HttpServletRequest request, FormField ff) {
        // 则检查如果没有赋值就赋予其当前年份
        FormField ffNew = new FormField();
        ffNew.setName(ff.getName());
        ffNew.setValue(ff.getValue());
        ffNew.setType(ff.getType());
        ffNew.setFieldType(ff.getFieldType());

        // 如果是当前用户宏控件，则检查如果没有赋值就赋予其当前用户名称
        if ("".equals(StrUtil.getNullStr(ff.getValue()))) {
            Calendar calendar = Calendar.getInstance();
            int currentYear = calendar.get(Calendar.YEAR);//当前年份
            ffNew.setValue(String.valueOf(currentYear));
        }
        return super.getOuterHTMLOfElementsWithRAWValueAndHTMLValue(request, ffNew);
    }

    /**
     * 取得表单域的类型
     *
     * @return int
     */
    @Override
    public int getFieldType(FormField ff) {
        return FormField.FIELD_TYPE_INT;
    }

    /**
     * 用于流程处理时，生成表单默认值，如基础数据宏控件，取其默认值
     *
     * @param ff FormField
     * @return Object
     */
    @Override
    public Object getValueForCreate(int flowId, FormField ff) {
		return DateUtil.getYear(new Date());
    }
}
