package com.redmoon.oa.flow.macroctl;

import cn.js.fan.util.ParamUtil;
import cn.js.fan.util.RandomSecquenceCreator;
import cn.js.fan.util.StrUtil;
import com.redmoon.oa.flow.FormField;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by fgf on 2018/12/4.
 */
public class UEditorCtl extends AbstractMacroCtl {

    @Override
    public String convertToHTMLCtl(HttpServletRequest request, FormField ff) {
        String str = "";

        int flowId = StrUtil.toInt((String)request.getAttribute("cwsId"), -1);
        String pageType = StrUtil.getNullStr((String)request.getAttribute("pageType"));
        // 如果是在智能模块的编辑页面
        if ("edit".equals(pageType)) {
            flowId = -1;
        }

        String pageKind = (String)request.getAttribute("pageKind");
        if ("nest_sheet_relate".equals(pageKind)) {
            flowId = -1; // 如果用于嵌套表编辑，则强置flowId为-1，以免记录到主表的流程文档中去
        }

        long id = StrUtil.toLong((String)request.getAttribute("cwsId"), -1);

        // str += "<div style='clear:both;margin:0px;padding:0px'>";
        // str += "<span id=\"upload_box_" + ff.getName() + "\" class=\"upload-box\"><span class=\"upload-label\">上传图片</span><input name=\"uploader\" id=\"uploader\" type=\"file\" onchange=\"submitFile('" + ff.getName() + "', '" + ff.getFormCode() + "', '" + RandomSecquenceCreator.getId(20) + "')\" /></span>";
        str += "<textarea id=\"" + ff.getName() + "\" class=\"ueditor\" name=\"" + ff.getName() + "\">" + StrUtil.getNullString(ff.getValue()) + "</textarea>";
        // str += "</div>";

        if (!"show".equals(pageType) && ff.isEditable()) {
            if (request.getAttribute("isUEditorJS") == null) {
                request.setAttribute("isUEditorJS", "y");
            }
            str += "<script>ajaxGetJS(\"/flow/macro/macro_ueditor_ctl_js.jsp?pageType=" + pageType + "&flowId=" + flowId + "&id=" + id + "&formCode=" + ff.getFormCode() + "&fieldName=" + StrUtil.UrlEncode(ff.getName()) + "\",{})</script>";
        }
        return str;
    }

    /**
     * 当report时，取得用来替换控件的脚本
     * @param ff FormField
     * @return String
     */
    @Override
    public String getReplaceCtlWithValueScript(FormField ff) {
        // 用下行效果是一样的，但是因为考虑到原来为textarea，后来又在表单中直接改为ckeditor宏控件，为保证兼容性所以不采用下行的方式
        // return "ReplaceCtlWithValue('" + ff.getName() +"', '" + ff.getType() + "', o('" + ff.getName() + "').value);\n";

        // 如果value为空，则表示可能原来是textarea，后来改为ckeditor控件
        String str = "if (findObj('" + ff.getName() + "') && findObj('" + ff.getName() + "').value=='') ReplaceCtlWithValue('" + ff.getName() +"', '" + ff.getType() + "', o('cws_span_" + ff.getName() + "').innerHTML);\n";
        str += "else ReplaceCtlWithValue('" + ff.getName() +"', '" + ff.getType() + "', $(findObj('" + ff.getName() + "')).val());\n";
        str += "$('.upload-box').remove();\n";
        return str;
    }

    /**
     * 获取用来保存宏控件toHtml后的值的表单中的HTML元素中保存的值，生成用以禁用控件的脚本
     * @return String
     */
    @Override
    public String getDisableCtlScript(FormField ff, String formElementId) {
        String str = "DisableCtl('" + ff.getName() + "', '" + ff.getType() +
                "', findObj('" + ff.getName() + "').value, findObj('" + ff.getName() + "').value);\n";
        // str += "findObj('upload_box_" + ff.getName() + "').style.display='none';\n";
        return str;
    }

    @Override
    public String getControlType() {
        return "text";
    }

    @Override
    public String getControlValue(String userName, FormField ff) {
        return StrUtil.getAbstract(null, ff.getValue(), 2000, "\r\n");
    }

    @Override
    public String getControlText(String userName, FormField ff) {
        return StrUtil.getAbstract(null, ff.getValue(), 2000, "\r\n");
    }

    @Override
    public String getControlOptions(String userName, FormField ff) {
        return "";
    }

}
