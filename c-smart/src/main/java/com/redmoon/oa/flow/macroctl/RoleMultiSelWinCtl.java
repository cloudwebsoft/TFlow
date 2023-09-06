package com.redmoon.oa.flow.macroctl;

import cn.js.fan.util.StrUtil;
import com.cloudweb.oa.cache.RoleCache;
import com.cloudweb.oa.entity.Role;
import com.cloudweb.oa.service.IRoleService;
import com.cloudweb.oa.utils.SpringUtil;
import com.redmoon.oa.base.IFormDAO;
import com.redmoon.oa.flow.FormField;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

/**
 * @Author:
 * @Description:
 * @Date: 2017/12/6 14:59
 */
public class RoleMultiSelWinCtl extends AbstractMacroCtl {
    public RoleMultiSelWinCtl() {
    }

    /**
     * 此方法用于 添加or编辑页面时 拼成html返回到td中，此处只需拼成一个input和选择按钮
     *
     * @param request HttpServletRequest
     * @param ff      FormField
     * @return
     */
    @Override
    public String convertToHTMLCtl(HttpServletRequest request, FormField ff) {
        String str = "";
        String v = StrUtil.getNullStr(ff.getValue());
        String pageType = StrUtil.getNullStr((String)request.getAttribute("pageType"));
        if (pageType.contains("show") || !ff.isEditable()) {
            StringBuilder roleNames = new StringBuilder();
            if (!"".equals(v)) {
                com.cloudweb.oa.cache.RoleCache roleCache = SpringUtil.getBean(com.cloudweb.oa.cache.RoleCache.class);
                String[] fields = v.split(",");
                for (String roleCode : fields) {
                    Role role = roleCache.getRole(roleCode);
                    String desc = role.getDescription();
                    if (roleNames.length() == 0) {
                        roleNames.append(desc);
                    } else {
                        roleNames.append(",").append(desc);
                    }
                }
            }
            str += "<div class='user_group_box'>";
            str += "<input id='" + ff.getName() + "_realshow' name='" + ff.getName() + "_realshow" +
                    "' readonly style='float:left; width:" + ff.getCssWidth() + "' value='" + roleNames + "' />";
            str += "<input id='" + ff.getName() + "' name='" + ff.getName() + "' value='" + v + "' type='hidden' />";
            str += "</div>";
        }
        else {
            String[] fields = v.split(",");
            List<String> listRoles = Arrays.asList(fields);

            String style = "";
            if (!"".equals(ff.getCssWidth())) {
                style = "style='width:" + ff.getCssWidth() + "'";
            } else {
                style = "style='width:150px'";
            }
            str += "<select id='" + ff.getName() + "' name='" + ff.getName() + "' " + style + " multiple='multiple'>";
            IRoleService roleService = SpringUtil.getBean(IRoleService.class);
            List<Role> list = roleService.getAll();
            for (Role role : list) {
                if (listRoles.contains(role.getCode())) {
                    str += "<option value='" + role.getCode() + "' selected>" + role.getDescription() + "</option>";
                } else {
                    str += "<option value='" + role.getCode() + "'>" + role.getDescription() + "</option>";
                }
            }
            str += "</select>";
            str += "<script>";
            str += "function initSelect2" + ff.getName() + "() {\n";
            str += " var obj=findObj('" + ff.getName() + "');\n";
            str += " var $obj=$(obj);\n";
            str += " $obj.select2({multiple: true,allowClear: true});\n";
            str += "}\n";

            str += "initSelect2" + ff.getName() + "();\n";
            str += "</script>";
        }

        return str;
    }

    @Override
    public String getControlType() {
        return "text";
    }

    @Override
    public String getControlOptions(String s, FormField formField) {
        return "";
    }

    @Override
    public String getControlValue(String s, FormField formField) {
        return StrUtil.getNullStr(formField.getValue());
    }

    @Override
    public String getControlText(String s, FormField ff) {
        return "";
    }

    /**
     * 显示在列表中的值，此处参照单部门选择框，只是显示样式不同，需要对数据
     * 做处理,与多用户选择框相似
     *
     * @param request    HttpServletRequest
     * @param ff         FormField
     * @param fieldValue String 格式：a,b
     * @return
     */
    @Override
    public String converToHtml(HttpServletRequest request, FormField ff, String fieldValue) {
        String v = StrUtil.getNullStr(fieldValue);
        if (!"".equals(v)) {
            StringBuffer roleNames = new StringBuffer();
            com.cloudweb.oa.cache.RoleCache roleCache = SpringUtil.getBean(com.cloudweb.oa.cache.RoleCache.class);
            String[] fields = v.split(",");
            for (String roleCode : fields) {
                Role role = roleCache.getRole(roleCode);
                if (role == null) {
                    continue;
                }
                String deptName = role.getCode();
                StrUtil.concat(roleNames, ",", deptName);
            }
            return roleNames.toString();
        } else {
            return "";
        }
    }

    @Override
    public String getSetCtlValueScript(HttpServletRequest request, IFormDAO iFormDao, FormField ff, String formElementId) {
        String v = StrUtil.getNullStr(ff.getValue());
        String[] ary = StrUtil.split(v, ",");
        if (!StrUtil.isEmpty(v)) {
            for (int k = 0; k < ary.length; k++) {
                ary[k] = StrUtil.sqlstr(ary[k]);
            }
            v = StringUtils.join(ary, ",");
        }
        return "$(findObj('" + ff.getName() + "')).val([" + v + "]).trigger('change');\n";
    }

    /**
     * 当report时，取得用来替换控件的脚本
     *
     * @param ff FormField
     * @return String
     */
    @Override
    public String getReplaceCtlWithValueScript(FormField ff) {
        String v = "";
        if (ff.getValue() != null && !"".equals(ff.getValue())) {
            com.cloudweb.oa.cache.RoleCache roleCache = SpringUtil.getBean(com.cloudweb.oa.cache.RoleCache.class);
            String[] ary = StrUtil.split(ff.getValue(), ",");
            if (ary != null) {
                for (String roleCode : ary) {
                    Role role = roleCache.getRole(roleCode);
                    if (role == null) {
                        continue;
                    }
                    if ("".equals(v)) {
                        v = role.getDescription();
                    } else {
                        v += "," + role.getDescription();
                    }
                }
            }
        }
        String str = "$('#" + ff.getName() + "_btn').hide();\n";
        return str + "ReplaceCtlWithValue('" + ff.getName() + "_realshow', '" + ff.getType() + "','" + v + "');\n";
    }

    @Override
    public String getDisableCtlScript(FormField ff, String formElementId) {
        String realName = "";
        if (ff.getValue() != null && !"".equals(ff.getValue())) {
            String[] ary = StrUtil.split(ff.getValue(), ",");
            com.cloudweb.oa.cache.RoleCache roleCache = SpringUtil.getBean(com.cloudweb.oa.cache.RoleCache.class);
            if (ary != null) {
                for (String roleCode : ary) {
                    Role role = roleCache.getRole(roleCode);
                    if ("".equals(realName)) {
                        realName = role.getDescription();
                    } else {
                        realName += "," + role.getDescription();
                    }
                }
            }
        }

        String str = "DisableCtl('" + ff.getName() + "', '" + ff.getType() +
                "','" + realName + "','" + ff.getValue() + "');\n";
        str += "DisableCtl('" + ff.getName() + "_realshow', '" + ff.getType() +
                "','" + "" + "','" + ff.getValue() + "');\n";
        str += "if (o('" + ff.getName() + "_btn')) o('" + ff.getName() + "_btn').outerHTML='';";
        return str;
    }
}
