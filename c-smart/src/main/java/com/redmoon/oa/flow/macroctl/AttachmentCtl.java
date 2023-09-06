package com.redmoon.oa.flow.macroctl;

import javax.servlet.http.HttpServletRequest;

import cn.js.fan.util.CheckErrException;
import cn.js.fan.util.ParamUtil;
import com.alibaba.fastjson.JSONObject;
import com.cloudweb.oa.api.IAttachmentCtl;
import com.cloudweb.oa.utils.ConstUtil;
import com.cloudweb.oa.utils.FileUtil;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudweb.oa.utils.SysUtil;
import com.redmoon.oa.Config;
import com.redmoon.oa.flow.FormField;
import com.redmoon.oa.flow.WorkflowDb;
import com.redmoon.oa.flow.FormDb;
import com.redmoon.kit.util.FileUpload;

import java.util.*;

import com.redmoon.kit.util.FileInfo;
import com.redmoon.oa.flow.FormDAO;

import cn.js.fan.util.StrUtil;
import cn.js.fan.web.Global;

import com.redmoon.oa.flow.Document;
import com.redmoon.oa.flow.Attachment;
import com.redmoon.oa.flow.DocContentCacheMgr;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.base.IFormDAO;
import com.redmoon.oa.security.SecurityUtil;
import com.redmoon.oa.sys.DebugUtil;

/**
 * <p>Title: </p>
 *
 * <p>Description: </p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class AttachmentCtl extends AbstractMacroCtl implements IAttachmentCtl {

    public AttachmentCtl() {
    }

    @Override
    public String convertToHTMLCtl(HttpServletRequest request, FormField ff) {
        long t = System.currentTimeMillis();

        boolean isEditableRaw = ff.isEditable();
        // 如果是来自于表单域选择控件的映射，则置ff为不可编辑状态
        // 是否正在module_list_sel.jsp中作映射处理
        boolean isMapping = false;
        // 是否被映射了值
        boolean isMapped = false;
        FormField sourceField = (FormField)request.getAttribute("cwsMapSourceFormField");
        if (sourceField!=null) {
            isMapping = true;
        }
        if (sourceField!=null && !"".equals(StrUtil.getNullStr(sourceField.getValue()))) {
            isMapped = true;
            ff.setEditable(false);
        }
        String pageUrl = request.getRequestURI();

        String str = "";
        // 如果附件中已存在赋值，则显示图片
        if (!"".equals(StrUtil.getNullStr(ff.getValue()))) {
            // str += "<img src='" + Global.getRootPath() + "/" + ff.getValue() + "'><BR>";
            String[] ary = ff.getValue().split(",");
            if (Global.getInstance().isDebug()) {
                LogUtil.getLog(getClass()).info("ary.length=" + ary.length);
            }
            // 如果有两个元素，则说明是流程中所存
            if (ary.length==2) {
                int flowId = StrUtil.toInt(ary[0], -1);
                int attId = StrUtil.toInt(ary[1], -1);

                if (flowId!=-1 && attId!=-1) {
                    WorkflowDb wf = new WorkflowDb();
                    wf = wf.getWorkflowDb(flowId);
                    int docId = wf.getDocId();
                    if (Global.getInstance().isDebug()) {
                        LogUtil.getLog(getClass()).info("before Attachment take " + (System.currentTimeMillis() - t) + " ms");
                    }
                    Attachment att = new Attachment(attId);
                    if (Global.getInstance().isDebug()) {
                        LogUtil.getLog(getClass()).info("after Attachment take " + (System.currentTimeMillis() - t) + " ms");
                    }
                    if (att.isLoaded()) {
                        str += "<div id='helper_" + ff.getName() + "'>";
                        if (!pageUrl.toLowerCase().contains("show")) {
                            str += "<a href='javascript:;' onclick=\"downloadFile('" + att.getName() + "', {attachId:" + ary[1] + ", flowId:" + ary[0] + "})\">" + att.getName() + "</a>";
                            if (StrUtil.isImage(StrUtil.getFileExt(att.getDiskName()))) {
                                SysUtil sysUtil = SpringUtil.getBean(SysUtil.class);
                                str += "&nbsp;&nbsp;<a href='javascript:;' onclick=\"window.open('" + sysUtil.getRootPath() + "/showImg.do?path=" + att.getVisualPath() + "/" + att.getDiskName() + "')\">查看</a>";
                            }
                            if (ff.isEditable()) {
                                str += "&nbsp;&nbsp;<a href='javascript:;' onclick=\"delAtt(" + att.getId() + ", '" + ff.getName() + "', " + flowId + "," + docId + ")\">删除</a>";
                            }
                        } else {
                            if (StrUtil.isImage(StrUtil.getFileExt(att.getDiskName()))) {
                                SysUtil sysUtil = SpringUtil.getBean(SysUtil.class);
                                str += "<a href='javascript:;' onclick=\"window.open('" + sysUtil.getRootPath() + "/showImg.do?path=" + att.getVisualPath() + "/" + att.getDiskName() + "')\">" + att.getName() + "</a>";
                            } else {
                                str += "<a href='javascript:;' onclick=\"downloadFile('" + att.getName() + "', {attachId:" + ary[1] + ", flowId:" + ary[0] + "})\">" + att.getName() + "</a>";
                            }
                        }

                        Config cfg = Config.getInstance();
                        boolean isPreview = false;
                        // 判断是否为wps、word文件
                        if (FileUtil.isPdfFile(att.getDiskName())) {
                            if (cfg.getBooleanProperty("canPdfFilePreview")) {
                                isPreview = true;
                            }
                        } else if (FileUtil.isOfficeFile(att.getDiskName())) {
                            if (cfg.getBooleanProperty("canOfficeFilePreview")) {
                                isPreview = true;
                            }
                        }

                        if (isPreview) {
                            // 网络环境下，Tomcat方式部署时有时会比较耗时，故在集群时不判断文件是否存在
                            /*boolean isFileExist = true;
                            if (Global.isCluster()) {
                                String s = Global.getRealPath() + att.getVisualPath() + "/" + att.getDiskName();
                                String htmlfile = s.substring(0, s.lastIndexOf(".")) + ".html";
                                java.io.File fileExist = new java.io.File(htmlfile);
                                isFileExist = fileExist.exists();
                                if (Global.getInstance().isDebug()) {
                                    LogUtil.getLog(getClass()).info("after exists " + htmlfile + " take " + (System.currentTimeMillis() - t) + " ms");
                                }
                            }
                            if (isFileExist) {*/
                                SysUtil sysUtil = SpringUtil.getBean(SysUtil.class);
                                str += "&nbsp;&nbsp;<a href='javascript:;' onclick=\"window.open('" + sysUtil.getRootPath() + "/" + att.getVisualPath() + "/" + att.getDiskName().substring(0, att.getDiskName().lastIndexOf(".")) + ".html')\">预览</a>";
                            // }
                        }

                        str += "</div>";
                    }
                }
                else {
                    str += "值 " + ff.getValue() + " 格式错误";
                }
            }
            // 如果只有一个元素，则说明是visual模块所存，字段中存的是diskName
            else if (ary.length==1) {
            	com.redmoon.oa.visual.Attachment att = new com.redmoon.oa.visual.Attachment();
            	att = att.getAttachment(ary[0]);
            	if (att != null && att.isLoaded()) {
            		str += "<div id='helper_" + ff.getName() + "'>";

                    if (!pageUrl.toLowerCase().contains("show")) {
                        str += "<a href='javascript:;' onclick=\"downloadFileVisual('" + att.getName() + "', {attachId:" + att.getId() + ", visitKey:'" + SecurityUtil.makeVisitKey(att.getId()) + "'})\">" + att.getName() + "</a>";
                        if (StrUtil.isImage(StrUtil.getFileExt(att.getDiskName()))) {
                            SysUtil sysUtil = SpringUtil.getBean(SysUtil.class);
                            str += "&nbsp;&nbsp;<a href='javascript:;' onclick=\"window.open('" + sysUtil.getRootPath() +"/showImg.do?path="+ att.getVisualPath() + "/" + att.getDiskName() + "')\">查看</a>";
                        }
                        if (ff.isEditable()) {
                            str += "&nbsp;&nbsp;<a href='javascript:;' onclick=\"delAtt(" + att.getId() + ", '" + ff.getName() + "')\">删除</a>";
                        }
                    } else {
                        if (StrUtil.isImage(StrUtil.getFileExt(att.getDiskName()))) {
                            SysUtil sysUtil = SpringUtil.getBean(SysUtil.class);
                            str += "<a href='javascript:;' onclick=\"window.open('" + sysUtil.getRootPath() +"/showImg.do?path="+ att.getVisualPath() + "/" + att.getDiskName() + "')\">" + att.getName() + "</a>";
                        } else {
                            str += "<a href='javascript:;' onclick=\"downloadFileVisual('" + att.getName() + "', {attachId:" + att.getId() + ", visitKey:'" + SecurityUtil.makeVisitKey(att.getId()) + "'})\">" + att.getName() + "</a>";
                        }
                    }

                    Config cfg = Config.getInstance();
                    boolean isPreview = false;
                    // 判断是否为wps、word文件
                    if (FileUtil.isPdfFile(att.getDiskName())) {
                        if (cfg.getBooleanProperty("canPdfFilePreview")) {
                            isPreview = true;
                        }
                    } else if (FileUtil.isOfficeFile(att.getDiskName())) {
                        if (cfg.getBooleanProperty("canOfficeFilePreview")) {
                            isPreview = true;
                        }
                    }

                    if (isPreview) {
                        // 网络环境下，Tomcat方式部署时有时会比较耗时，故在集群时不判断文件是否存在
                        /*boolean isFileExist = true;
                        if (Global.isCluster()) {
                            String s = Global.getRealPath() + att.getVisualPath() + "/" + att.getDiskName();
                            String htmlfile = s.substring(0, s.lastIndexOf(".")) + ".html";
                            java.io.File fileExist = new java.io.File(htmlfile);
                            isFileExist = fileExist.exists();
                            if (Global.getInstance().isDebug()) {
                                LogUtil.getLog(getClass()).info("after exists " + htmlfile + " take " + (System.currentTimeMillis() - t) + " ms");
                            }
                        }
                        if (isFileExist) {*/
                            SysUtil sysUtil = SpringUtil.getBean(SysUtil.class);
                            str += "&nbsp;&nbsp;<a href='javascript:;' onclick=\"window.open('" + sysUtil.getRootPath() + "/" + att.getVisualPath() + "/" + att.getDiskName().substring(0, att.getDiskName().lastIndexOf(".")) + ".html')\">预览</a>";
                        // }
                    }
            		str += "</div>";
            	}
            }
        }

        if (request.getAttribute("isAttachmentCtl") == null) {
            // str += "<script src='" + request.getContextPath() + "/flow/macro/macro_js_attachment_ctl.jsp?editable=" + ff.isEditable() + "'></script>";
            str += "<script>ajaxGetJS(\"/flow/macro/macro_js_attachment_ctl.jsp?formCode=" + StrUtil.UrlEncode(ff.getFormCode())
                    + "&fieldName=" + ff.getName() + "&isHidden=" + ff.isHidden() + "&editable=" + ff.isEditable() + "\", {})</script>\n";
            request.setAttribute("isAttachmentCtl", "y");
        }

        if (Global.getInstance().isDebug()) {
            LogUtil.getLog(getClass()).info("after ajaxGetJS take " + (System.currentTimeMillis() - t) + " ms");
        }

        String desc = ff.getDescription();
        StringBuffer accept = new StringBuffer();
        String[] ary = StrUtil.split(desc, ",");
        if (ary!=null) {
            for (String s : ary) {
                StrUtil.concat(accept, ",", "." + s.trim());
            }
        }

        DebugUtil.i(getClass(), "convertToHTMLCtl", ff.getName() + " " + ff.getTitle() + " isEditableRaw=" + isEditableRaw + " isMapped=" + isMapped);
        if (Global.getInstance().isDebug()) {
            LogUtil.getLog(getClass()).info("after accept take " + (System.currentTimeMillis() - t) + " ms");
        }

        if (isEditableRaw && !isMapping) {
            String width = "";
            if (!"".equals(ff.getCssWidth())) {
                width = "width:" + ff.getCssWidth();
            }
            else {
                width = "width:150px";
            }
            // 为防止被forms.less中的input[type=file] block覆盖，而使得必填的*号折行
            str += "<input id='" + ff.getName() + "' name='" + ff.getName() + "' title='" + ff.getTitle() + "' type='file' style='display:inline;" + width + "'";
            if (accept.length()>0) {
                str += " accept='" + accept.toString() + "'";
            }
            str += " size='15'/>";
        }

        if (Global.getInstance().isDebug()) {
            LogUtil.getLog(getClass()).info("after getCssWidth take " + (System.currentTimeMillis() - t) + " ms");
        }

        // 注意映射时不能返回file控件，否则当在表单域选择控件中映射时，浏览器会报安全问题
        if (!ff.isEditable()){
            if (isMapped) {
                str += "<input id='" + ff.getName() + "_mapped' name='" + ff.getName() + "_mapped' value='" + ff.getValue() + "' type='hidden' size='15'/>";
            } else {

            }
        }

        if (Global.getInstance().isDebug()) {
            LogUtil.getLog(getClass()).info("convertToHTMLCtl end " + (System.currentTimeMillis() - t) + " ms");
        }
        return str;
    }

    @Override
    public String getMetaData(FormField ff) {
        JSONObject json = new JSONObject();
        if (!"".equals(StrUtil.getNullStr(ff.getValue()))) {
            // str += "<img src='" + Global.getRootPath() + "/" + ff.getValue() + "'><BR>";
            String[] ary = ff.getValue().split(",");
            // 如果有两个元素，则说明是流程中所存
            if (ary.length==2) {
                int flowId = StrUtil.toInt(ary[0], -1);
                int attId = StrUtil.toInt(ary[1], -1);

                if (flowId!=-1 && attId!=-1) {
                    WorkflowDb wf = new WorkflowDb();
                    wf = wf.getWorkflowDb(flowId);
                    int docId = wf.getDocId();

                    Attachment att = new Attachment(attId);
                    if (att.isLoaded()) {
                        json.put("attachId", att.getId());
                        json.put("attachName", att.getName());
                        json.put("flowId", flowId);
                        json.put("docId", docId);

                        if (StrUtil.isImage(StrUtil.getFileExt(att.getDiskName()))) {
                            SysUtil sysUtil = SpringUtil.getBean(SysUtil.class);
                            json.put("previewUrl", sysUtil.getRootPath() + "/" + att.getVisualPath() + "/" + att.getDiskName());
                        } else {
                            Config cfg = Config.getInstance();
                            boolean isPreview = false;
                            // 判断是否为wps、word文件
                            if (FileUtil.isPdfFile(att.getDiskName())) {
                                if (cfg.getBooleanProperty("canPdfFilePreview")) {
                                    isPreview = true;
                                }
                            } else if (FileUtil.isOfficeFile(att.getDiskName())) {
                                if (cfg.getBooleanProperty("canOfficeFilePreview")) {
                                    isPreview = true;
                                }
                            }
                            if (isPreview) {
                                SysUtil sysUtil = SpringUtil.getBean(SysUtil.class);
                                json.put("previewUrl", sysUtil.getRootPath() + "/" + att.getVisualPath() + "/" + att.getDiskName().substring(0, att.getDiskName().lastIndexOf(".")) + ".html");
                            }
                        }
                    }
                }
            }
            // 如果只有一个元素，则说明是visual模块所存，字段中存的是diskName
            else if (ary.length==1) {
                com.redmoon.oa.visual.Attachment att = new com.redmoon.oa.visual.Attachment();
                att = att.getAttachment(ary[0]);
                if (att != null && att.isLoaded()) {
                    json.put("attachId", att.getId());
                    json.put("visitKey", SecurityUtil.makeVisitKey(att.getId()));
                    json.put("attachName", att.getName());

                    if (StrUtil.isImage(StrUtil.getFileExt(att.getDiskName()))) {
                        SysUtil sysUtil = SpringUtil.getBean(SysUtil.class);
                        json.put("previewUrl", sysUtil.getRootPath() + "/" + att.getVisualPath() + "/" + att.getDiskName());
                    } else {
                        Config cfg = Config.getInstance();
                        boolean isPreview = false;
                        // 判断是否为wps、word文件
                        if (FileUtil.isPdfFile(att.getDiskName())) {
                            if (cfg.getBooleanProperty("canPdfFilePreview")) {
                                isPreview = true;
                            }
                        } else if (FileUtil.isOfficeFile(att.getDiskName())) {
                            if (cfg.getBooleanProperty("canOfficeFilePreview")) {
                                isPreview = true;
                            }
                        }
                        if (isPreview) {
                            SysUtil sysUtil = SpringUtil.getBean(SysUtil.class);
                            json.put("previewUrl", sysUtil.getRootPath() + "/" + att.getVisualPath() + "/" + att.getDiskName().substring(0, att.getDiskName().lastIndexOf(".")) + ".html");
                        }
                    }
                }
            }
        }
        return json.toString();
    }
    
    @Override
    public String converToHtml(HttpServletRequest request, FormField ff, String fieldValue) {
    	String str = "";
        // 如果附件中已存在赋值，则显示图片
        if (!StrUtil.getNullStr(fieldValue).equals("")) {
        	String[] ary = fieldValue.split(",");
            // 如果有两个元素，则说明是流程中所存
            if (ary.length==2) {
                Attachment att = new Attachment(Integer.parseInt(ary[1]));
                if (att.isLoaded()) {
                    // 导出时不需要生成链接
                    if (request!=null && "true".equals(request.getAttribute(ConstUtil.IS_FOR_EXPORT))) {
                        return att.getName();
                    }
	                /*str += "<a href=\"" + Global.getRootPath() + "/flow/download.do?attachId=" + ary[1] +
	                        "&flowId=" + ary[0] + "\" target=\"_blank\">" + att.getName() + "</a><br />";*/
                    str +=  att.getName();
                }
            }
            // 如果只有一个元素，则说明是visual模块所存，字段中存的是diskName
            else if (ary.length==1) {
            	com.redmoon.oa.visual.Attachment att = new com.redmoon.oa.visual.Attachment();
            	att = att.getAttachment(ary[0]);
            	if (att != null && att.isLoaded()) {
                    if (request!=null && "true".equals(request.getAttribute(ConstUtil.IS_FOR_EXPORT))) {
                        return att.getName();
                    }
                    // str += "<a href=\"" + Global.getRootPath() + "/visual/download.do?attachId=" + att.getId() + "&visitKey=" + SecurityUtil.makeVisitKey(att.getId()) + "\" target=\"_blank\">" + att.getName() + "</a><br />";
                    str += att.getName();
            	}
            }
        }
        return str;
    }

    /**
     * 获取用来保存宏控件原始值的表单中的HTML元素中保存的值，生成用以给控件赋值的脚本
     * @return String
     */
    @Override
    public String getSetCtlValueScript(HttpServletRequest request, IFormDAO IFormDao, FormField ff, String formElementId) {
        return super.getSetCtlValueScript(request, IFormDao, ff, formElementId);
    }

    /**
     * 获取用来保存宏控件toHtml后的值的表单中的HTML元素中保存的值，生成用以禁用控件的脚本
     * @return String
     */
    @Override
    public String getDisableCtlScript(FormField ff, String formElementId) {
    	/*
    	String str = "";
    	String value = StrUtil.getNullStr(ff.getValue());
        if(!value.equals("")){
        	String[] ary = value.split(",");
            // 如果有两个元素，则说明是流程中所存
            if (ary.length==2) {
                Attachment att = new Attachment(Integer.valueOf(ary[1]).intValue());
                str += "var " + ff.getName() + "temp = \"<a href='" + "flow_getfile.jsp?attachId=" + ary[1] +
                        "&flowId=" + ary[0] + "' target='_blank'>" + att.getName() + "</a>\";\n";
                str += "document.getElementById('"+ff.getName() + "File" + "').innerHTML = " + ff.getName() + "temp;\n";
            }
            // 如果只有一个元素，则说明是visual模块所存，字段中存的是diskName
            else if (ary.length==1) {
            	com.redmoon.oa.visual.Attachment att = new com.redmoon.oa.visual.Attachment();
            	att = att.getAttachment(ary[0]);
            	str += "var "+ff.getName() + "temp = \"<a href='" + Global.getFullRootPath() + "/visual/download.do?attachId=" + att.getId() + "' target='_blank'>" + att.getName() + "</a>\";\n";
                str += "document.getElementById('"+ff.getName() + "File" + "').innerHTML = " + ff.getName() + "temp;\n";
            }
        }
        return str;
        */
    	// 有可能在老版本的表单中，没有这个字段
    	return "if (o('" + ff.getName() + "')) {o('" + ff.getName() + "').style.display = 'none';}\n";
    }

    /**
     * 当report时，取得用来替换控件的脚本
     * @param ff FormField
     * @return String
     */
    @Override
    public String getReplaceCtlWithValueScript(FormField ff) {
    	/*
    	String str = "";
    	String value = StrUtil.getNullStr(ff.getValue());
        if(!value.equals("")){
        	//String strTemp = "";
        	String[] items = value.split(";");
        	for(int i = 0; i < items.length; i ++){
	            String[] ary = items[i].split(",");
	            if (ary.length==2) {
	                Attachment att = new Attachment(Integer.valueOf(ary[1]).intValue());
	                str += "var "+ff.getName()+i+"temp = \"<a href='" + "flow_getfile.jsp?attachId=" + ary[1] +"&flowId=" + ary[0] + "' target='_blank'>" + att.getName() + "</a>\";\n";
	                str += "document.getElementById(\'"+ff.getName()+"File"+i+"\').innerHTML = "+ff.getName()+i+"temp;\n";
	            }
	        }
        }
        return str;
        */
    	return "if (o('" + ff.getName() + "')) { o('" + ff.getName() + "').style.display = 'none'; }";
    }

    /**
     * 在验证前获取表单域的值，用于附件、图片宏控件不能为空的检查，该值同时会被保存下来
     * @param request
     * @param fu
     * @param ff
     */
    @Override
    public void setValueForValidate(HttpServletRequest request, FileUpload fu, FormField ff) throws CheckErrException {
        String desc = ff.getDescription();
        String[] ary = StrUtil.split(desc.toLowerCase(), ",");
        List<String> list = null;
        if (ary!=null) {
            list = Arrays.asList(ary);
        }
        boolean isUploaded = false;
        Vector<String> vMsg = new Vector<>();
        Vector<FileInfo> v = fu.getFiles();
        for (FileInfo fi : v) {
            LogUtil.getLog(getClass()).info("setValueForValidate: ff.getName()=" +
                    ff.getName() + " fi.fieldName=" +
                    fi.getFieldName());
            if (fi.getFieldName()!=null && fi.getFieldName().equals(ff.getName())) {
                isUploaded = true;
                fu.setFieldValue(ff.getName(), fi.getName());
                if (list != null && !list.contains(StrUtil.getFileExt(fi.getName()).toLowerCase())) {
                    vMsg.addElement(fi.getName() + " 文件非法，格式只能为：" + desc);
                }
                break;
            }
        }
        // 如果未上传文件，而字段中有值，说明原来可能保存过文件（如：流程中保存草稿），需检查文件格式是否合法
        if (!isUploaded) {
            String fieldValue = ff.getValue();
            if (ff.getValue()!=null && !"".equals(ff.getValue())) {
                String[] aryVal = fieldValue.split(",");
                String ext = "", fileName = "";
                // 如果有两个元素，则说明是流程中所存
                if (aryVal.length==2) {
                    Attachment att = new Attachment(StrUtil.toInt(aryVal[1], -1));
                    if (att.isLoaded()) {
                        fileName = att.getDiskName();
                        ext = StrUtil.getFileExt(fileName);
                        fu.setFieldValue(ff.getName(), att.getDiskName());
                    }
                }
                // 如果只有一个元素，则说明是visual模块所存，字段中存的是diskName
                else if (aryVal.length==1) {
                    fu.setFieldValue(ff.getName(), aryVal[0]);
                    fileName = aryVal[0];
                    ext = StrUtil.getFileExt(fileName);
                }
                if (!"".equals(ext)) {
                    if (list!=null && !list.contains(ext)) {
                        vMsg.addElement(ff.getTitle() + " 文件 " + fileName + " 非法，格式只能为：" + desc);
                    }
                }
            }
            else {
                // 如果是来自于表单域选择宏控件的映射，则在converToHtmlCtl中，将原来为file类型的控件改为了hidden，故fileUpload中会传入映射的值
                String r = StrUtil.getNullStr(fu.getFieldValue(ff.getName() + "_mapped"));
                if (!"".equals(r)) {
                    fu.setFieldValue(ff.getName(), r);
                }
            }
        }
        if (vMsg.size()>0) {
            throw new CheckErrException(vMsg);
        }
    }    

    @Override
    public Object getValueForSave(FormField ff, int flowId, FormDb fd, FileUpload fu) {
        // ff参数来自于FormDAO中的doUpload，并不是来自于数据库，所以需从数据库中提取(已更改为取自数据库)
        // 在数据库中存储其附件ID|文件名
        String fieldTitle = ff.getTitle();
        FormDAO fdao = new FormDAO(flowId, fd);
        fdao.load();
        Vector vts = fdao.getFields();
        Iterator ir = vts.iterator();
        FormField dbff = null;
        while (ir.hasNext()) {
            dbff = (FormField) ir.next();
            // LogUtil.getLog(getClass()).info("getValueForSave1:" + dbff.getName() + ":" + ff.getName());
            if (dbff.getName().equals(ff.getName())) {
                break;
            }
        }

        String re = ff.getValue();
        if (dbff != null) {
            re = dbff.getValue();
        }

        Vector v = fu.getFiles();
        ir = v.iterator();
        boolean isUploaded = false;
        while (ir.hasNext()) {
            FileInfo fi = (FileInfo) ir.next();
            // LogUtil.getLog(getClass()).info("getValueForSave: ff.getName()=" + ff.getName() + " fi.fieldName=" + fi.getFieldName());
            if (fi.getFieldName()!=null && fi.getFieldName().equals(ff.getName())) {
                isUploaded = true;
                break;
            }
        }

        LogUtil.getLog(getClass()).info("getValueForSave:" + ff.getName() + "=" + ff.getValue());
        if (isUploaded && dbff != null) {
            // 如果formfield原来的值不为空，且上传的文件中存在有对应fieldName的，则获取对应的图片附件，将其删除
            WorkflowDb wf = new WorkflowDb();
            wf = wf.getWorkflowDb(flowId);
            Document doc = new Document();
            doc = doc.getDocument(wf.getDocId());
            LogUtil.getLog(getClass()).info("getValueForSave:" + ff.getName() + " docId=" + wf.getDocId());

            Vector vt = doc.getAttachments(1);
            ir = vt.iterator();
            // 取得ID为最小的对应的attach
            int count = 0;
            long minId = Long.MAX_VALUE;
            Attachment lastAtt = null;
            while (ir.hasNext()) {
                Attachment att = (Attachment) ir.next();
                // LogUtil.getLog(getClass()).info("getValueForSave:" + att.getFieldName() + " ff.getName()=" + ff.getName());

                if (att.getFieldName()!=null && att.getFieldName().equals(ff.getName())) {
                    if (minId==Long.MAX_VALUE) {
                        re = flowId + "," +att.getId();
                    }
                    if (minId > att.getId()) {
                        minId = att.getId();
                        lastAtt = att;
                    }
                    else {
                        re = flowId + "," + att.getId();
                    }
                    count++;
                }
            }

            // LogUtil.getLog(getClass()).info("getValueForSave:" + lastAtt + " count=" + count);

            // 如果相同fieldName的存在两个附件，则删除较早的一个
            if (lastAtt != null && count > 1) {
                lastAtt.del();
                DocContentCacheMgr dcm = new DocContentCacheMgr();
                dcm.refreshUpdate(doc.getID(), 1);
            }
        }
        else {
            // 如果ff的值不为空，则说明原来已有值（如流程中退回时）
            String val = StrUtil.getNullStr(ff.getValue());
            if ("".equals(val)) {
                // 如果是来自于表单域选择宏控件的映射，则在converToHtmlCtl中，将原来为file类型的控件改为了hidden，故fileUpload中会传入映射的值
                String r = StrUtil.getNullStr(fu.getFieldValue(ff.getName() + "_mapped"));
                if (!"".equals(r)) {
                    re = r;
                }
            }
            else {
                re = val;
            }
        }
        return re;
    }

    @Override
    public Object getValueForCreate(FormField ff, FileUpload fu, FormDb fd) {
        // 当在流程中时，fu的值为null，而当在visual模块中时，fu参数才可用
        if (fu == null) {
            return ff.getDefaultValue();
        }
        Vector v = fu.getFiles();
        Iterator ir = v.iterator();
        while (ir.hasNext()) {
            FileInfo fi = (FileInfo) ir.next();
            if (fi.getFieldName()!=null && fi.getFieldName().equals(ff.getName())) {
                //com.redmoon.oa.visual.FormDAO fdao = new com.redmoon.oa.visual.
                //        FormDAO(fd);
                // return fdao.getVisualPath() + "/" + fi.getDiskName();
                return fi.getDiskName();
            }
        }
        // 如果是来自于表单域选择宏控件的映射，则在converToHtmlCtl中，将原来为file类型的控件改为了hidden，故fileUpload中会传入映射的值
        String r = StrUtil.getNullStr(fu.getFieldValue(ff.getName()));
        if (!"".equals(r)) {
            return r;
        }
        return ff.getDefaultValue();
    }

    @Override
    public Object getValueForSave(FormField ff, FormDb fd, long formDAOId, FileUpload fu) {
        // ff参数来自于FormDAO中的doUpload，并不是来自于数据库，所以需从数据库中提取
        com.redmoon.oa.visual.FormDAO fdao = new com.redmoon.oa.visual.FormDAO(
                formDAOId, fd);
        Vector vts = fdao.getFields();
        Iterator ir = vts.iterator();
        FormField dbff = null;
        while (ir.hasNext()) {
            dbff = (FormField) ir.next();
//            LogUtil.getLog(getClass()).info("getValueForSave1:" + dbff.getName() +
//                                            ":" + ff.getName());
            if (dbff.getName().equals(ff.getName())) {
                break;
            }
        }

        String re = ff.getValue();
        if (dbff != null)
            re = dbff.getValue();

        Vector v = fu.getFiles();
        ir = v.iterator();
        boolean isUploaded = false;
        while (ir.hasNext()) {
            FileInfo fi = (FileInfo) ir.next();
//            LogUtil.getLog(getClass()).info("getValueForSave: ff.getName()=" +
//                                            ff.getName() + " fi.fieldName=" +
//                                            fi.getFieldName());
            if (fi.getFieldName()!=null && fi.getFieldName().equals(ff.getName())) {
                // re = fdao.getVisualPath() + "/" + fi.getDiskName();
                re = fi.getDiskName();
                isUploaded = true;
            }
        }

        LogUtil.getLog(getClass()).info("getValueForSave:" + ff.getName() + "=" + ff.getValue());
        if (isUploaded && dbff != null && !StrUtil.getNullStr(dbff.getValue()).equals("")) {
            // 如果formfield原来的值不为空，且上传的文件中存在有对应fieldName的，则获取对应的附件，将其删除
            Vector vt = fdao.getAttachments();
            ir = vt.iterator();
            // 取得ID为最小的attach，并将其删除
            int count = 0;
            long minId = Long.MAX_VALUE;
            com.redmoon.oa.visual.Attachment lastAtt = null;
            while (ir.hasNext()) {
                com.redmoon.oa.visual.Attachment att = (com.redmoon.oa.visual.Attachment) ir.next();
                // LogUtil.getLog(getClass()).info("getValueForSave:att.getFieldName()=" + att.getFieldName() + " ff.getName()=" + ff.getName());
                if (att.getFieldName()!=null && att.getFieldName().equals(ff.getName())) {
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
        else {
            // 如果ff的值不为空，则说明原来已有值（如流程中退回时）
            String val = StrUtil.getNullStr(ff.getValue());
            if ("".equals(val)) {
                // 如果是来自于表单域选择宏控件的映射，则在converToHtmlCtl中，将原来为file类型的控件改为了hidden，故fileUpload中会传入映射的值
                String r = StrUtil.getNullStr(fu.getFieldValue(ff.getName() + "_mapped"));
                if (!"".equals(r)) {
                    re = r;
                }
            }
            else {
                re = val;
            }
        }
        return re;
    }

    @Override
    public String getControlType() {
        return "";
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
        return "";
    }

    /**
     * 取得根据名称（而不是值）查询时需用到的SQL语句，如果没有特定的SQL语句，则返回空字符串
     * @param request
     * @param ff 当前被查询的字段
     * @param value
     * @param isBlur 是否模糊查询
     * @return
     */
    @Override
    public String getSqlForQuery(HttpServletRequest request, FormField ff, String value, boolean isBlur) {
        if (isBlur) {
            return "select f." + ff.getName() + " from ft_" + ff.getFormCode() + " f, visual_attach a where f.id=a.visualId and a.name like " +
                    StrUtil.sqlstr("%" + value + "%");
        }
        else {
            return "select f." + ff.getName() + " from ft_" + ff.getFormCode() + " f, visual_attach a where f.id=a.visualId and a.name=" +
                    StrUtil.sqlstr(value);
        }
    }

}
