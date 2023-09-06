package com.redmoon.oa.flow.macroctl;

import cn.js.fan.db.ResultIterator;
import cn.js.fan.db.ResultRecord;
import cn.js.fan.util.ParamUtil;
import cn.js.fan.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cloudweb.oa.utils.ConstUtil;
import com.cloudwebsoft.framework.db.JdbcTemplate;
import com.redmoon.oa.base.IFormDAO;
import com.redmoon.oa.flow.FormField;
import com.redmoon.oa.ui.menu.DirectoryView;
import com.redmoon.oa.ui.menu.Leaf;
import com.redmoon.oa.util.CSSUtil;

import javax.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * <p>Title: 系统菜单选择</p>
 *
 * <p>Description: </p>
 *
 * <p>Copyright: Copyright (c) 2007</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class IconFontSelectCtl extends AbstractMacroCtl {
    public IconFontSelectCtl() {
    }

    @Override
    public String convertToHTMLCtl(HttpServletRequest request, FormField ff) {
        String iconOpts = "";
        ArrayList<String[]> fontAry = CSSUtil.getFontBefore();
        int fontAryLen = fontAry.size();
        for (int m = 0; m < fontAryLen; m++) {
            String[] ary = fontAry.get(m);
            iconOpts += "<option value='" + ary[0] + "'>";
            iconOpts += "<i class='fa " + ary[0] + "'></i>";
            iconOpts += ary[0];
            iconOpts += "</option>";
        }

        StringBuffer sb = new StringBuffer();
        sb.append("<select id='" + ff.getName() + "' name='" + ff.getName() + "'>");
        sb.append(iconOpts);
        sb.append("</select>");

        String pageType = (String) request.getAttribute("pageType");
        if (pageType==null) {
            pageType = ParamUtil.get(request, "pageType"); // 来自于module_list_sel.jsp
        }
        if (!pageType.contains("show")) {
            sb.append("<script>");
            sb.append("$(fo('" + ff.getName() + "')).select2({templateResult: formatStateIcon,templateSelection: formatStateIcon});\n");
            sb.append("function formatStateIcon(state) {\n");
            sb.append("if (!state.id) {\n");
            sb.append("    return state.text;\n");
            sb.append("}\n");
            sb.append("var $state = $(\n");
            sb.append("        '<span><i class=\"fa ' + state.id + '\"></i>&nbsp;&nbsp;' + state.text + '</span>'\n");
            sb.append(");\n");
            sb.append("return $state;\n");
            sb.append("}\n");
            sb.append("</script>\n");
        }
        return sb.toString();
    }

    @Override
    public String getValueForExport(HttpServletRequest request, FormField ff, String fieldValue) {
        return fieldValue;
    }

    @Override
    public String convertToHTMLCtlForQuery(HttpServletRequest request,
                                           FormField ff) {
        String iconOpts = "";
        ArrayList<String[]> fontAry = CSSUtil.getFontBefore();
        int fontAryLen = fontAry.size();
        for (int m = 0; m < fontAryLen; m++) {
            String[] ary = fontAry.get(m);
            iconOpts += "<option value='" + ary[0] + "'>";
            iconOpts += "<i class='fa " + ary[0] + "'></i>";
            iconOpts += ary[0];
            iconOpts += "</option>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<select id='" + ff.getName() + "' name='" + ff.getName() + "'>");
        sb.append("<option value=''>无</option>");
        sb.append(iconOpts);
        sb.append("</select>");
        return sb.toString();
    }

    /**
     * 获取用来保存宏控件原始值的表单中的HTML元素，通常为textarea
     *
     * @return String
     */
    @Override
    public String getOuterHTMLOfElementsWithRAWValueAndHTMLValue(
            HttpServletRequest request,
            FormField ff) {
        return super.getOuterHTMLOfElementsWithRAWValueAndHTMLValue(request, ff);
    }

    /**
     * 获取用来保存宏控件toHtml后的值的表单中的HTML元素中保存的值，生成用以禁用控件的脚本
     *
     * @return String
     */
    @Override
    public String getDisableCtlScript(FormField ff, String formElementId) {
        String desc = "<i class=\"fa " + ff.getValue() + "\"></i>";
        return "DisableCtl('" + ff.getName() + "', '" + ff.getType() +
                "','" + desc + "','" + ff.getValue() + "');\n";
    }

    /**
     * 当report时，取得用来替换控件的脚本
     *
     * @param ff FormField
     * @return String
     */
    @Override
    public String getReplaceCtlWithValueScript(FormField ff) {
        String desc = "<i class=\"fa " + ff.getValue() + "\"></i>";
        return "ReplaceCtlWithValue('" + ff.getName() + "', '" + ff.getType() +
                "','" + desc + "');\n";
    }

    public Object getValueForCreate(FormField ff) {
        return ff.getValue();
    }

    /**
     * 用于模块列表中显示宏控件的值
     *
     * @param request    HttpServletRequest
     * @param ff         FormField 表单域的描述，其中的value值为空
     * @param fieldValue String 表单域的值
     * @return String
     */
    @Override
    public String converToHtml(HttpServletRequest request, FormField ff, String fieldValue) {
        // return "<i class=\"fa " + fieldValue + "\"></i>";
        return fieldValue;
    }

    @Override
    public String getControlType() {
        return "select";
    }

    @Override
    public String getControlValue(String userName, FormField ff) {
        if (!"".equals(StrUtil.getNullStr(ff.getValue()))) {
            return ff.getValue();
        } else {
            return StrUtil.getNullStr(ff.getDefaultValueRaw());
        }
    }

    @Override
    public String getControlText(String userName, FormField ff) {
        if (!"".equals(StrUtil.getNullStr(ff.getValue()))) {
            String desc = "";
            Leaf lf = new Leaf();
            lf = lf.getLeaf(ff.getValue());
            if (lf != null) {
                desc = lf.getName();
            }

            return desc;
        } else {
            return "";
        }
    }

    @Override
    public String getControlOptions(String userName, FormField ff) {
        JSONArray selects = new JSONArray();
        ArrayList<String[]> fontAry = CSSUtil.getFontBefore();
        int fontAryLen = fontAry.size();
        for (int m = 0; m < fontAryLen; m++) {
            String[] ary = fontAry.get(m);
            JSONObject select = new JSONObject();
            select.put("name", ary[0]);
            select.put("value", ary[0]);
            selects.add(select);
        }
        return selects.toString();
    }

    @Override
    public String getSetCtlValueScript(HttpServletRequest request, IFormDAO iFormDao, FormField ff, String formElementId) {
        return "$(findObj('" + ff.getName() + "')).val('" + ff.getValue() + "').trigger('change');\n";
    }
}