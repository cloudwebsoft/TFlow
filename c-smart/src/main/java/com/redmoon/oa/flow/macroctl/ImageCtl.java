package com.redmoon.oa.flow.macroctl;

import java.io.File;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

import cn.js.fan.util.CheckErrException;
import cn.js.fan.util.NumberUtil;
import cn.js.fan.util.StrUtil;
import cn.js.fan.web.Global;

import com.cloudweb.oa.api.IImageCtl;
import com.cloudweb.oa.utils.ConstUtil;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudweb.oa.utils.SysUtil;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.kit.util.FileInfo;
import com.redmoon.kit.util.FileUpload;
import com.redmoon.oa.base.IAttachment;
import com.redmoon.oa.flow.*;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * <p>Title: </p>
 *
 * <p>Description: 
 * 如果默认值为w,h，则图片以宽w和高h自适应，如果仅有w，则固定宽度为w，如果无默认值，则为原始尺寸
 * 默认值也可能为w,h;300或w;300，300表示限制文件大小，单位为KB
 * </p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class ImageCtl extends AbstractMacroCtl implements IImageCtl {

    public ImageCtl() {
    }

    @Override
    public String convertToHTMLCtl(HttpServletRequest request, FormField ff) {
        String str = "";
        String w = "", h = "";
        // 如果附件中已存在赋值，则显示图片
        if (ff.getValue() != null && !"".equals(ff.getValue()) && !ff.getValue().equals(ff.getDefaultValueRaw())) {
            String desc = ff.getDescription();
            if (desc.startsWith("{")) {
                try {
                    JSONObject json = new JSONObject(desc);
                    w = json.getString("w");
                    h = json.getString("h");
                } catch (JSONException e) {
                    LogUtil.getLog(getClass()).error(e);
                }
            }
            else {
                String defaultStr = ff.getDescription().replaceAll("，", ",");
                defaultStr = defaultStr.replaceAll("；", ";");

                String[] strAry = StrUtil.split(defaultStr, ";");
                String strStyle = "";
                if (strAry != null) {
                    strStyle = strAry[0];
                }
                String[] ary = StrUtil.split(strStyle, ",");
                if (ary!=null) {
                    if (ary.length==2) {
                        w = ary[0];
                        h = ary[1];
                    }
                    else {
                        w = ary[0];
                    }
                }
            }

            SysUtil sysUtil = SpringUtil.getBean(SysUtil.class);
            str += "<div id=\"" + ff.getName() + "Img\" style=\"margin-bottom: 5px\">";
            if (!"".equals(w) && !"".equals(h)) {
                str += "<img title='点击在新窗口中打开' onclick='window.open(\"" + sysUtil.getRootPath() + "/showImg.do?path=" + ff.getValue() + "\")' src='" + sysUtil.getRootPath() + "/showImg.do?path=" + ff.getValue() +
                        "' style='cursor:pointer; width:" + w + "px; height:" + h + "px'></a><BR>";
            } else if (!"".equals(w)) {
                str += "<a title='点击在新窗口中打开' href='" + sysUtil.getRootPath() + "/showImg.do?path=" + ff.getValue() + "' target='_blank'><img src='" + sysUtil.getRootPath() + "/showImg.do?path=" + ff.getValue() +
                        "' style=\"width:" + w + "px\"></a><BR>";
            } else {
                str += "<a title='点击在新窗口中打开' href='" + sysUtil.getRootPath() + "/showImg.do?path=" + ff.getValue() + "' target='_blank'><img src='" + sysUtil.getRootPath() + "/showImg.do?path=" + ff.getValue() + "'></a><BR>";
            }
            str += "</div>";
        }
        else {
            str += "<div id=\"" + ff.getName() + "Img\" style=\"margin-bottom: 5px\"></div>";
        }
        str += "<input id='" + ff.getName() + "' name='" + ff.getName() + "' title='" + ff.getTitle() + "' class='image-ctl' type='file' accept='image/gif,image/jpeg,image/jpg,image/png,image/bmp,image/svg' size=15>";

        if (request.getAttribute("isImageCtlJS_" + ff.getName()) == null) {
            String pageType = (String) request.getAttribute("pageType");

            /*str += "<script src='" + request.getContextPath()
                    + "/flow/macro/macro_js_image_ctl.jsp?formCode=" + StrUtil.UrlEncode(ff.getFormCode())
                    + "&fieldName=" + ff.getName() + "&w=" + w + "&h=" + h
                    + "'></script>\n";*/

            str += "<script>ajaxGetJS(\"/flow/macro/macro_js_idcard_ctl.jsp?pageType=" + pageType +"&formCode=" + StrUtil.UrlEncode(ff.getFormCode())
                    + "&w=" + w + "&h=" + h + "&fieldName=" + ff.getName() + "&isHidden=" + ff.isHidden() + "&editable=" + ff.isEditable() + "\", {})</script>\n";
            request.setAttribute("isImageCtlJS__" + ff.getName(), "y");
        }
        return str;
    }
    
    @Override
    public String converToHtml(HttpServletRequest request, FormField ff, String fieldValue) {
    	String str = "";
        // 如果附件中已存在赋值，则显示图片
        if (!"".equals(StrUtil.getNullStr(fieldValue))) {
        	if (fieldValue != null) {
        	    // 如果含有逗号，说明是默认值：宽度,高度
        	    if (fieldValue.contains(",")) {
        	        return "";
                }
        	    // 默认值可能只设了宽度
                if (NumberUtil.isNumeric(fieldValue)) {
                    return "";
                }

                com.redmoon.oa.sso.Config ssoCfg = new com.redmoon.oa.sso.Config();
                String desKey = ssoCfg.get("key");
                String visitKey = cn.js.fan.security.ThreeDesUtil.encrypt2hex(desKey, fieldValue);

                if (request == null) {
                    request = SpringUtil.getRequest();
                }
        	    String pageType = null;
                SysUtil sysUtil = SpringUtil.getBean(SysUtil.class);
                if (request != null) {
                    pageType = StrUtil.getNullStr((String) request.getAttribute("pageType"));
                }

                if (pageType != null && pageType.contains(ConstUtil.PAGE_TYPE_WORD)) {
                    String defaultStr = ff.getDefaultValue().replaceAll("，", ",");
                    String[] ary = StrUtil.split(defaultStr, ",");
                    if (ary!=null) {
                        if (ary.length==2) {
                            str += "<img src='" + sysUtil.getRootPath() +"/showImg.do?visitKey=" + visitKey + "&path="+ StrUtil.UrlEncode(fieldValue) +
                                "' width='" + ary[0] + "' height='" + ary[1] + "'><BR>";
                        }
                        else if (ary.length==1) {
                            str += "<img src='" + sysUtil.getRootPath() +"/showImg.do?visitKey=" + visitKey + "&path="+ StrUtil.UrlEncode(fieldValue) +
                            "' width='" + ary[0] + "'><BR>";
                        }
                    }
                    else {
                        str += "<img src='" + sysUtil.getRootPath() +"/showImg.do?visitKey=" + visitKey + "&path="+ StrUtil.UrlEncode(fieldValue) + "'><BR>";
                    }
                }
        	    else {
                    // 在流程中发邮件取摘要信息时，传入的request为null
                    str += "<a title='点击在新窗口中打开' href='" + sysUtil.getRootPath() + "/showImg.do?visitKey=" + visitKey + "&path=" + StrUtil.UrlEncode(fieldValue) + "' target='_blank'>查看图片</a>";
                }
        	}
        }
        return str;
    }

    /**
     * 获取用来保存宏控件toHtml后的值的表单中的HTML元素中保存的值，生成用以禁用控件的脚本
     * @return String
     */
    @Override
    public String getDisableCtlScript(FormField ff, String formElementId) {
        String str = "DisableCtl('" + ff.getName() + "', '" + ff.getType() +
                     "', '', " + "o('cws_textarea_" + ff.getName() +
                     "').value);\n";
        return str;
    }

    /**
     * 当report时，取得用来替换控件的脚本
     * @param ff FormField
     * @return String
     */
    @Override
    public String getReplaceCtlWithValueScript(FormField ff) {
        return "ReplaceCtlWithValue('" + ff.getName() + "', '" + ff.getType() +
                "','');\n";
    }
    
    /**
     * 在验证前获取表单域的值，用于附件、图片宏控件不能为空的检查
     * @param request
     * @param fu
     * @param ff
     * @throws CheckErrException 
     */
    @Override
    public void setValueForValidate(HttpServletRequest request, FileUpload fu, FormField ff) throws CheckErrException {
        boolean isUploaded = false;
        List<String> list = new ArrayList<String>();
        list.add("gif");
        list.add("jpeg");
        list.add("jpg");
        list.add("png");
        list.add("bmp");
        list.add("svg");

        Vector<String> vt = new Vector<String>();
        Vector<FileInfo> v = fu.getFiles();
        for (FileInfo fi : v) {
            LogUtil.getLog(getClass()).info("setValueForValidate: ff.getName()=" +
                    ff.getName() + " fi.fieldName=" +
                    fi.getFieldName());
            if (fi.getFieldName().equals(ff.getName())) {
                isUploaded = true;
                fu.setFieldValue(ff.getName(), fi.getName());

                if (!list.contains(StrUtil.getFileExt(fi.getName()).toLowerCase())) {
                    vt.addElement(fi.getName() + " 文件非法，格式只能为：gif、jpeg、jpg、png、bmp、svg");
                }

                long maxSize = -1;
                String defaultStr = ff.getDescription();
                if (defaultStr.startsWith("{")) {
                    try {
                        JSONObject json = new JSONObject(defaultStr);
                        maxSize = StrUtil.toLong(json.getString("maxSize"), -1);
                    } catch (JSONException e) {
                        LogUtil.getLog(getClass()).error(e);
                    }
                } else {
                    defaultStr = defaultStr.replaceAll("；", ";");
                    String[] strAry = StrUtil.split(defaultStr, ";");
                    if (strAry != null && strAry.length == 2) {
                        maxSize = StrUtil.toLong(strAry[1], -1);
                    }
                }
                if (maxSize != -1) {
                    File f = new File(fi.getTmpFilePath());
                    if (f.length() > maxSize * 1024) {
                        String msg = "图片不能大于" + maxSize + "KB";
                        vt.addElement(msg);
                    }
                }
                break;
            }
        }

        // 如果未上传文件，而字段中有值，说明原来可能保存过文件（如：流程中保存草稿），需检查文件格式是否合法
        if (!isUploaded) {
            String fieldValue = ff.getValue();
            if (ff.getValue()!=null && !"".equals(ff.getValue()) && !(ff.getValue().equals(ff.getDescription()))) {
                String[] aryVal = fieldValue.split(",");
                String ext = "";
                // 如果有两个元素，则说明是流程中所存
                if (aryVal.length==2) {
                    Attachment att = new Attachment(Integer.parseInt(aryVal[1]));
                    if (att.isLoaded()) {
                        ext = StrUtil.getFileExt(att.getDiskName());
                        fu.setFieldValue(ff.getName(), att.getDiskName());
                    }
                }
                // 如果只有一个元素，则说明是visual模块所存，字段中存的是diskName
                else if (aryVal.length==1) {
                    fu.setFieldValue(ff.getName(), aryVal[0]);
                    ext = StrUtil.getFileExt(aryVal[0]);
                }
                if (!"".equals(ext)) {
                    if (!list.contains(ext)) {
                        vt.addElement(ff.getTitle() + " 文件非法，格式只能为：gif、jpeg、jpg、png、bmp、svg");
                    }
                }
            }
        }

        if (vt.size()>0) {
            throw new CheckErrException(vt);
        }
    }        

    @Override
    public Object getValueForSave(FormField ff, int flowId, FormDb fd, FileUpload fu) {
        // ff参数来自于FormDAO中的doUpload，并不是来自于数据库，所以需从数据库中提取
        FormDAO fdao = new FormDAO(flowId, fd);
        fdao.load();
        FormField dbff = null;
        Vector<FormField> vts = fdao.getFields();
        Iterator<FormField> ir = vts.iterator();
        while (ir.hasNext()) {
            dbff = (FormField) ir.next();
            LogUtil.getLog(getClass()).info("getValueForSave1:" + dbff.getName() +
                                            ":" + ff.getName());
            if (dbff.getName().equals(ff.getName())) {
                break;
            }
        }

        String re = StrUtil.getNullStr(ff.getValue());
        // 加getNullStr是为了防止修改表单新增图像控件字段，致历史记录中该字段值为null，如果不处理，保存后在数据库中就会变null字符串
        if (dbff != null) {
            re = StrUtil.getNullStr(dbff.getValue());
        }

        Vector<FileInfo> v = fu.getFiles();
        Iterator<FileInfo> irFu = v.iterator();
        boolean isUploaded = false;
        while (irFu.hasNext()) {
            FileInfo fi = irFu.next();
            LogUtil.getLog(getClass()).info("getValueForSave: ff.getName()=" +
                                            ff.getName() + " fi.fieldName=" +
                                            fi.getFieldName());
            if (fi.getFieldName().equals(ff.getName())) {
                re = fdao.getVisualPath() + "/" + fi.getDiskName();
                isUploaded = true;
            }
        }

        LogUtil.getLog(getClass()).info("getValueForSave:" + ff.getName() + "=" +
                                        ff.getValue());
        if (isUploaded && dbff != null &&
            !"".equals(StrUtil.getNullStr(dbff.getValue()))) {
            // 如果formfield原来的值不为空，且上传的文件中存在有对应fieldName的，则获取对应的图片附件，将其删除
            WorkflowDb wf = new WorkflowDb();
            wf = wf.getWorkflowDb(flowId);
            Document doc = new Document();
            doc = doc.getDocument(wf.getDocId());
            LogUtil.getLog(getClass()).info("getValueForSave:" + ff.getName() +
                                            " docId=" + wf.getDocId());
            // LogUtil.getLog(getClass()).info("getValueForSave:" + ff.getName() + " doc.isLoaded=" + doc.isLoaded());

            int pageNum = 1;
            // 取得ID为最小的attach，并将其删除
            int count = 0;
            long minId = Long.MAX_VALUE;
            IAttachment lastAtt = null;
            Vector<IAttachment> vt = doc.getAttachments(pageNum);
            for (IAttachment att : vt) {
                // LogUtil.getLog(getClass()).info("getValueForSave:" + att.getFieldName() + " ff.getName()=" + ff.getName());

                if (att.getFieldName().equals(ff.getName())) {
                    if (minId > att.getId()) {
                        minId = att.getId();
                        lastAtt = att;
                    }
                    count++;
                }
            }
            // LogUtil.getLog(getClass()).info("getValueForSave:" + lastAtt + " count=" + count);

            // 如果相同fieldName的存在两个附件，则删除较早的一个
            if (lastAtt != null && count > 1) {
                lastAtt.del();
                DocContentCacheMgr dcm = new DocContentCacheMgr();
                dcm.refreshUpdate(doc.getID(), pageNum);
            }
        }
        return re;
    }

    @Override
    public Object getValueForCreate(FormField ff, FileUpload fu, FormDb fd) {
        // 当在流程中创建初始表单时，fu的值为null
        if (fu == null) {
            return ff.getDefaultValue();
        }
        Vector<FileInfo> v = fu.getFiles();
        for (FileInfo fi : v) {
            if (fi.getFieldName().equals(ff.getName())) {
                com.redmoon.oa.visual.FormDAO fdao = new com.redmoon.oa.visual.FormDAO(fd);
                return fdao.getVisualPath() + "/" + fi.getDiskName();
            }
        }
        return ff.getDefaultValue();
    }

    @Override
    public Object getValueForSave(FormField ff, FormDb fd, long formDAOId, FileUpload fu) {
        // ff参数来自于FormDAO中的doUpload，并不是来自于数据库，所以需从数据库中提取
        com.redmoon.oa.visual.FormDAO fdao = new com.redmoon.oa.visual.FormDAO(formDAOId, fd);
        FormField dbff = null;
        Vector<FormField> vts = fdao.getFields();
        Iterator<FormField> ir = vts.iterator();
        while (ir.hasNext()) {
            dbff = (FormField) ir.next();
            LogUtil.getLog(getClass()).info("getValueForSave1:" + dbff.getName() + ":" + ff.getName());
            if (dbff.getName().equals(ff.getName())) {
                break;
            }
        }

        String re = StrUtil.getNullStr(ff.getValue());
        // 加getNullStr是为了防止修改表单新增图像控件字段，致历史记录中该字段值为null，如果不处理，保存后在数据库中就会变null字符串        
        if (dbff != null)
            re = StrUtil.getNullStr(dbff.getValue());

        boolean isUploaded = false;
        Vector<FileInfo> v = fu.getFiles();
        for (FileInfo fi : v) {
            LogUtil.getLog(getClass()).info("getValueForSave: ff.getName()=" +
                    ff.getName() + " fi.fieldName=" +
                    fi.getFieldName());
            if (fi.getFieldName().equals(ff.getName())) {
                re = fdao.getVisualPath() + "/" + fi.getDiskName();
                isUploaded = true;
            }
        }

        LogUtil.getLog(getClass()).info("getValueForSave:" + ff.getName() + "=" + ff.getValue());
        if (isUploaded && dbff != null &&
            !"".equals(StrUtil.getNullStr(dbff.getValue()))) {
            // 如果formfield原来的值不为空，且上传的文件中存在有对应fieldName的，则获取对应的图片附件，将其删除
            // 取得ID为最小的attach，并将其删除
            int count = 0;
            long minId = Long.MAX_VALUE;
            IAttachment lastAtt = null;
            Vector<IAttachment> vt = fdao.getAttachments();
            for (IAttachment att : vt) {
                LogUtil.getLog(getClass()).info("getValueForSave:att.getFieldName()=" + att.getFieldName() + " ff.getName()=" + ff.getName());
                if (att.getFieldName().equals(ff.getName())) {
                    if (minId > att.getId()) {
                        minId = att.getId();
                        lastAtt = att;
                    }
                    count++;
                }
            }
            LogUtil.getLog(getClass()).info("getValueForSave:" + lastAtt + " count=" + count);
            // 如果相同fieldName的存在两个附件，则删除较早的一个
            if (lastAtt != null && count > 1) {
                lastAtt.del();
            }
        }
        return re;
    }

    @Override
    public String getControlType() {
        return "img";
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
}
