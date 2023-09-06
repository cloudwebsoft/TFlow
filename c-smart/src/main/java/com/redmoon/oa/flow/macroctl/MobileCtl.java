package com.redmoon.oa.flow.macroctl;

import cn.js.fan.util.CheckErrException;
import cn.js.fan.util.StrUtil;
import com.redmoon.kit.util.FileUpload;
import com.redmoon.oa.flow.FormField;
import javax.servlet.http.HttpServletRequest;
import java.util.Vector;

public class MobileCtl extends AbstractMacroCtl {
    @Override
    public String convertToHTMLCtl(HttpServletRequest request, FormField ff) {
        String str = "<input id='" + ff.getName() + "' name='" + ff.getName() + "' title='" + ff.getTitle() + "' value='" + StrUtil.getNullStr(ff.getValue()) + "' style='width:" + ff.getCssWidth() + "'";
        if (ff.isReadonly()) {
            str += " readonly='readonly' " + " readOnlyType='" + ff.getReadOnlyType() + "'";
        }
        str += " />";
        if (request.getAttribute("isMobileCtlJS_" + ff.getName()) == null) {
            String pageType = (String) request.getAttribute("pageType");
            /*str += "<script src='" + request.getContextPath()
                    + "/flow/macro/macro_js_mobile_ctl.jsp?pageType=" + pageType
                    + "&formCode=" + StrUtil.UrlEncode(ff.getFormCode())
                    + "&fieldName=" + ff.getName() + "&isHidden=" + ff.isHidden() + "&editable=" + ff.isEditable()
                    + "'></script>\n";*/
            str += "<script>ajaxGetJS(\"/flow/macro/macro_js_mobile_ctl.jsp?pageType=" + pageType +"&formCode=" + StrUtil.UrlEncode(ff.getFormCode())
                    + "&fieldName=" + ff.getName() + "&isHidden=" + ff.isHidden() + "&editable=" + ff.isEditable() + "\", {})</script>\n";
            request.setAttribute("isMobileCtlJS_" + ff.getName(), "y");
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
            if (!com.redmoon.oa.sms.Config.isValidMobile(val)) {
                Vector msgs = new Vector();
                msgs.addElement(ff.getTitle() + " 格式非法");
                throw new CheckErrException(msgs);
            }
        }
    }
}