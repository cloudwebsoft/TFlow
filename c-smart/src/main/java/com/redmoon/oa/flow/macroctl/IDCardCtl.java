package com.redmoon.oa.flow.macroctl;

import cn.js.fan.util.CheckErrException;
import cn.js.fan.util.DateUtil;
import cn.js.fan.util.StrUtil;
import com.cloudwebsoft.framework.util.IDCardUtil;
import com.redmoon.kit.util.FileUpload;
import com.redmoon.oa.flow.FormField;

import javax.servlet.http.HttpServletRequest;
import java.util.Vector;

/**
 * 身份证宏控件，在描述中输入生日的字段名，可以同时解析出生日并赋值
 */
public class IDCardCtl extends AbstractMacroCtl {
    @Override
    public String convertToHTMLCtl(HttpServletRequest request, FormField ff) {
        String style = "";
        if (!"".equals(ff.getCssWidth())) {
            style = " style='width:" + ff.getCssWidth() + "'";
        }
        else {
            style = " style='width:150px'";
        }
        String str = "<input id='" + ff.getName() + "' name='" + ff.getName() + "' title='" + ff.getTitle() + "' value='" + StrUtil.getNullStr(ff.getValue()) + "' style='width:" + ff.getCssWidth() + "'";
        if (ff.isReadonly()) {
            str += " readonly='readonly' " + " readOnlyType='" + ff.getReadOnlyType() + "'";
        }
        str += style + "'/>";

        if (request.getAttribute("isIDCardCtlJS_" + ff.getName()) == null) {
            String pageType = (String) request.getAttribute("pageType");
            /*str += "<script src='" + request.getContextPath()
                    + "/flow/macro/macro_js_idcard_ctl.jsp?pageType=" + pageType
                    + "&formCode=" + StrUtil.UrlEncode(ff.getFormCode())
                    + "&fieldName=" + ff.getName() + "&isHidden=" + ff.isHidden() + "&editable=" + ff.isEditable()
                    + "'></script>\n";*/
            str += "<script>ajaxGetJS(\"/flow/macro/macro_js_idcard_ctl.jsp?pageType=" + pageType +"&formCode=" + StrUtil.UrlEncode(ff.getFormCode())
                    + "&fieldName=" + ff.getName() + "&isHidden=" + ff.isHidden() + "&editable=" + ff.isEditable() + "\", {})</script>\n";
            request.setAttribute("isIDCardCtlJS_" + ff.getName(), "y");
        }
        return str;
    }

    @Override
    public String getControlType() {
        return "text";
    }

    @Override
    public String getControlValue(String userName, FormField ff) {
        return StrUtil.getNullStr(ff.getValue());
    }

    @Override
    public String getControlText(String userName, FormField ff) {
        return StrUtil.getNullStr(ff.getValue());
    }

    @Override
    public String getControlOptions(String userName, FormField ff) {
        return "";
    }

    @Override
    public void setValueForValidate(HttpServletRequest request, FileUpload fu, FormField ff) throws CheckErrException {
        String val = fu.getFieldValue(ff.getName());
        if (val!=null && !"".equals(val)) {
            IDCardUtil icUtil = new IDCardUtil();
            String str = icUtil.validate(val);
            if (!"1".equals(str)) {
                Vector msgs = new Vector();
                msgs.addElement(str);
                throw new CheckErrException(msgs);
            }
        }
    }
}