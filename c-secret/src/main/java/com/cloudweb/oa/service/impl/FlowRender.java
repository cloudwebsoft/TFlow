package com.cloudweb.oa.service.impl;

import cn.js.fan.db.ResultIterator;
import cn.js.fan.db.ResultRecord;
import cn.js.fan.util.DateUtil;
import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.NumberUtil;
import cn.js.fan.util.StrUtil;
import cn.js.fan.web.Global;
import cn.js.fan.web.SkinUtil;
import com.cloudweb.oa.api.IFlowRender;
import com.cloudweb.oa.cache.FormArchiveCache;
import com.cloudweb.oa.utils.ConfigUtil;
import com.cloudweb.oa.utils.ConstUtil;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudweb.oa.utils.WorkflowProUtil;
import com.cloudwebsoft.framework.db.JdbcTemplate;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.base.IFormDAO;
import com.redmoon.oa.base.IFormMacroCtl;
import com.redmoon.oa.flow.*;
import com.redmoon.oa.flow.macroctl.MacroCtlMgr;
import com.redmoon.oa.flow.macroctl.MacroCtlUnit;
import com.redmoon.oa.kernel.License;
import com.redmoon.oa.pvg.Privilege;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.util.RequestUtil;
import com.redmoon.oa.visual.FormUtil;
import com.redmoon.oa.visual.FuncUtil;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component("flowRender")
public class FlowRender implements IFlowRender {

    public final static String FORM_FLEMENT_ID = "flowForm";

    @Autowired
    HttpServletRequest request;

    @Autowired
    WorkflowProUtil workflowProUtil;

    public String replaceDefaultStr(WorkflowDb wf, FormDb fd, IFormDAO fdao, String content) {
        content = content.replaceFirst("#\\[表单\\]", fd.getName());
        // content = content.replaceFirst("#\\[文号\\]", wf.getTitle());
        if (wf!=null) {
            content = content.replaceFirst("#\\[标题\\]", wf.getTitle());
            content = content.replaceFirst("#\\[时间\\]", DateUtil.format(wf.getMydate(), FormField.FORMAT_DATE));
            // content = content.replaceFirst("#\\[title\\]", "");

            content = content.replaceFirst("#\\[id\\]", String.valueOf(wf.getId()));
        }

        String pat = "\\{\\$rootPath\\}";
        content = content.replaceAll(pat, request.getContextPath());

        pat = "\\{\\$id\\}";
        String id = String.valueOf(fdao.getId());
        content = content.replaceAll(pat, id);

        // 替换国际化字符串
        content = replaceResStr(content);

        return content;
    }

    public String replaceDefaultStr(WorkflowDb wf, FormDb fd, FormDAO fdao, WorkflowActionDb wfa, String content) {
        content = content.replaceFirst("#\\[title\\]", wfa.getTitle());
        content = replaceDefaultStr(wf, fd, fdao, content);
        return content;
    }

    public String replaceResStr(String content) {
        // {$res.form.code}
        String patternStr = "\\{\\$res\\.(.*?)\\}";
        Pattern pattern = Pattern.compile(patternStr,
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        boolean result = matcher.find();
        while (result) {
            String key = matcher.group(1);
            int p = key.lastIndexOf(".");
            String key1 = "res." + key.substring(0, p);
            String key2 = key.substring(p+1);
            String str = SkinUtil.LoadString(request, key1, key2);
            if (str==null) {
                str = key1 + "." + key2;
            }
            matcher.appendReplacement(sb, str);
            result = matcher.find();
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public String getContentMacroReplaced(WorkflowDb wf, FormDb fd, FormDAO fdao) {
        String content;
        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());
        if (lf.isDebug()) {
            content = fd.getContent();
        }
        else {
            long formArchiveId = wf.getFormArchiveId();
            if (formArchiveId == ConstUtil.FORM_ARCHIVE_NONE) {
                int doc_id = wf.getDocId();
                DocumentMgr dm = new DocumentMgr();
                Document doc = dm.getDocument(doc_id);
                // 向下兼容
                content = doc.getContent(1);
            }
            else {
                FormArchiveCache formArchiveCache = SpringUtil.getBean(FormArchiveCache.class);
                IFormDAO fdaoFormArchive = formArchiveCache.getFormDao(formArchiveCache, ConstUtil.FORM_ARCHIVE, formArchiveId);
                content = fdaoFormArchive.getFieldValue("content");
            }
        }
        return getContentMacroReplaced(fdao, content);
    }

    /**
     * 替换宏控件
     * @param fdao 取自数据库
     * @return 替换后的字符串
     */
    public String getContentMacroReplaced(FormDAO fdao, String content) {
        Vector<FormField> fieldsDb = fdao.getFields();

        // 替换content中的宏控件
        Iterator<FormField> ir = fieldsDb.iterator();
        // 20130308，因为客户端可能会用不同版本浏览器管理后台，如果用原来保存的表单，则因为FormDb中ieVersion变了的关系，会导致解析失败
        // 20130720，恢复从doc中调用，当表单添加或编辑后，在content中加入头部<!--{ieVersion:6}-->
        // 如果是debug模式，则直接调用表单
        MacroCtlMgr mm = new MacroCtlMgr();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            if (!ff.getMacroType().equals(FormField.MACRO_NOT)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu == null) {
                    LogUtil.getLog(getClass()).error("getContentMacroReplaced: " + ff.getMacroType() + " is not found in xml config file.");
                }
                else {
                    LogUtil.getLog(getClass()).info("getContentMacroReplaced:" + mu.getName());
                    IFormMacroCtl ifc = mu.getIFormMacroCtl();
                    if (ifc == null) {
                        DebugUtil.e(getClass(), "getContentMacroReplaced", "宏控件：" + ff.getMacroType() + " 不存在");
                        continue;
                    }

                    // 传入fdao，考虑到第三方数据宏控件会更改其它字段的数据
                    ifc.setIFormDAO(fdao);
                    // 预处理数据（第三方数据宏控件）
                    ifc.preProcessData(request, ff);
                }
            }
        }

        long t = System.currentTimeMillis();
        org.jsoup.nodes.Document doc = Jsoup.parse(content);
        ir = fieldsDb.iterator();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            if (!ff.getMacroType().equals(FormField.MACRO_NOT)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu == null) {
                    LogUtil.getLog(getClass()).error("getContentMacroReplaced: " + ff.getMacroType() + " is not found in xml config file.");
                }
                else {
                    LogUtil.getLog(getClass()).info("getContentMacroReplaced:" + mu.getName());
                    IFormMacroCtl ifc = mu.getIFormMacroCtl();
                    if (ifc == null) {
                        DebugUtil.e(getClass(), "getContentMacroReplaced", "宏控件：" + ff.getMacroType() + " 不存在");
                        continue;
                    }

                    // 传入fdao备用
                    ifc.setIFormDAO(fdao);
                    ifc.replaceMacroCtlWithHTMLCtl(request, ff, doc);
                }
            }
        }
        content = doc.html();
        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "getContentMacroReplaced", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        return content;
    }

    /**
     * 详情页显示
     * @return String
     */
    @Override
    public String report(WorkflowDb wf, FormDb fd, boolean isHideField) {
        FormDAO fdao = new FormDAO();
        fdao = fdao.getFormDAOByCache(wf.getId(), fd);

        Vector<FormField> vf = fdao.getFields();
        for (FormField ff : vf) {
            // 置为不能编辑，以使得CKEditorCtl初始化时，不转变为编辑器
            ff.setEditable(false);
        }

        String content = getContentMacroReplaced(wf, fd, fdao);

        content = replaceDefaultStr(wf, fd, fdao, content);
        content = "<div id=formDiv name=formDiv>" + content + "</div>";

        // 置用户已操作的值
        StringBuilder str = new StringBuilder();
        MacroCtlMgr mm = new MacroCtlMgr();

        // FormDAOMgr fdm = new FormDAOMgr(wf.getId(), formCode);
        Vector<FormField> v = fdao.getFields();
        Iterator<FormField> ir = v.iterator();
        while (ir.hasNext()) {
            FormField formField = ir.next();

            FormField ff = null;
            // clone是为了避免当renderFieldValue后，再setValue时，因为千分位格式化的原因致原始value被更改
            try {
                ff = (FormField)formField.clone();
            } catch (CloneNotSupportedException e) {
                LogUtil.getLog(getClass()).error(e);
            }

            String val;
            switch (ff.getType()) {
                case FormField.TYPE_CHECKBOX:
                    val = ff.getValue();
                    break;
                case FormField.TYPE_SELECT:
                    val = ff.getValue();
                    break;
                case FormField.TYPE_RADIO:
                    val = ff.getValue();
                    break;
                default:
                    val = FuncUtil.renderFieldValue(fdao, ff);
                    break;
            }

            ff.setValue(val);

            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu != null) {
                    IFormMacroCtl iFormMacroCtl = mu.getIFormMacroCtl();
                    iFormMacroCtl.setIFormDAO(fdao);
                    str.append(iFormMacroCtl.getOuterHTMLOfElementWithHTMLValue(request, ff));
                }
            }
            else {
                str.append(FormField.getOuterHTMLOfElementWithHTMLValue(request, ff));
            }
        }

        str.append("\n<script>\n");
        ir = fdao.getFields().iterator();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu != null) {
                    str.append(mu.getIFormMacroCtl().getReplaceCtlWithValueScript(fdao, ff));
                }
            }
            else {
                str.append(FormField.getReplaceCtlWithValueScript(ff));
            }
        }

        if (isHideField) {
            String fieldHide = "";
            Privilege pvg = new Privilege();
            MyActionDb mad = new MyActionDb();
            mad = mad.getMyActionDbOfFlow(wf.getId(), pvg.getUser(request));
            // 管理员查看时，其本人可能并未参与流程，则mad将为null
            if (mad!=null) {
                WorkflowActionDb wad = new WorkflowActionDb();
                wad = wad.getWorkflowActionDb((int) mad.getActionId());

                fieldHide = StrUtil.getNullString(wad.getFieldHide()).trim();
            }

            // 将不显示的字段加入fieldHide
            ir = v.iterator();
            while (ir.hasNext()) {
                FormField ff = ir.next();
                if (ff.getHide()==FormField.HIDE_ALWAYS) {
                    if ("".equals(fieldHide)) {
                        fieldHide = ff.getName();
                    }
                    else {
                        fieldHide += "," + ff.getName();
                    }
                }
            }

            String[] fdsHide = StrUtil.split(fieldHide, ",");
            if (fdsHide != null) {
                // 20170731 fgf 增加hideNestCol
                str.append("function hideNestCol() {\n");
                for (String s : fdsHide) {
                    if (!s.startsWith("nest.")) {
                        FormField ff = fd.getFormField(s);
                        if (ff == null) {
                            LogUtil.getLog(getClass()).error(fd.getName() + " Field hidden " + s + " is not exist.");
                        } else {
                            str.append(FormField.getHideCtlScript(ff, FORM_FLEMENT_ID));
                        }
                    } else {
                        str.append("try{ hideNestTableCol('").append(s.substring("nest.".length())).append("'); }catch(e) {}\n");
                        str.append("try{ hideNestSheetCol('").append(s.substring("nest.".length())).append("'); }catch(e) {}\n");
                    }
                }
                str.append("}\n");

                str.append("hideNestCol();\n");

                // 屏蔽鼠标右键
                // if (!pvg.isUserPrivValid(request, "admin"))
                //    str += "$(document).bind('contextmenu', function(e){return false;});";
            }
        }

        // 清除其它辅助图片按钮等
        str.append("ClearAccessory();\n");
        str.append("</script>\n");

        return content + str;
    }

    @Override
    public String reportScript(FormDb fd, FormDAO fdao) {
        return "";
    }

    @Override
    public String reportForView(WorkflowDb wf, FormDb fd, int formViewId, boolean isHideField) {
        FormViewDb fvd = new FormViewDb();
        fvd = fvd.getFormViewDb(formViewId);
        if (fvd == null){
            return "表单视图不存在"; //LocalUtil.LoadString(request,"res.flow.Flow", "formVeiwNotExist");
        }

        FormDAO fdao = new FormDAO();
        fdao = fdao.getFormDAOByCache(wf.getId(), fd);

        Vector<FormField> vf = fdao.getFields();
        for (FormField ff : vf) {
            // 置为不能编辑，以使得CKEditorCtl初始化时，不转变为编辑器
            ff.setEditable(false);
        }

        String content = getContentMacroReplaced(fdao, fvd.getString("form"));

        content = replaceDefaultStr(wf, fd, fdao, content);
        content = "<div id=formDiv name=formDiv>" + content + "</div>";

        // 置用户已操作的值
        StringBuilder str = new StringBuilder();
        MacroCtlMgr mm = new MacroCtlMgr();

        Vector<FormField> v = fdao.getFields();
        Iterator<FormField> ir = v.iterator();
        while (ir.hasNext()) {
            FormField ff = ir.next();

            String val;
            if (ff.getType().equals(FormField.TYPE_CHECKBOX)) {
                val = ff.getValue();
            }
            else {
                val = FuncUtil.renderFieldValue(fdao, ff);
            }
            ff.setValue(val);

            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu != null) {
                    IFormMacroCtl iFormMacroCtl = mu.getIFormMacroCtl();
                    iFormMacroCtl.setIFormDAO(fdao);
                    str.append(iFormMacroCtl.getOuterHTMLOfElementWithHTMLValue(request, ff));
                }
            }
            else {
                str.append(FormField.getOuterHTMLOfElementWithHTMLValue(request, ff));
            }
        }

        str.append("\n<script>\n");
        ir = fdao.getFields().iterator();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu != null) {
                    str.append(mu.getIFormMacroCtl().getReplaceCtlWithValueScript(fdao, ff));
                }
            }
            else {
                str.append(FormField.getReplaceCtlWithValueScript(ff));
            }
        }

        if (isHideField) {
            String fieldHide = "";
            Privilege pvg = new Privilege();
            MyActionDb mad = new MyActionDb();
            mad = mad.getMyActionDbOfFlow(wf.getId(), pvg.getUser(request));
            // 管理员查看时，其本人可能并未参与流程，则mad将为null
            if (mad!=null) {
                WorkflowActionDb wad = new WorkflowActionDb();
                wad = wad.getWorkflowActionDb((int) mad.getActionId());

                fieldHide = StrUtil.getNullString(wad.getFieldHide()).trim();
            }

            // 将不显示的字段加入fieldHide
            ir = v.iterator();
            while (ir.hasNext()) {
                FormField ff = ir.next();
                if (ff.getHide()==FormField.HIDE_ALWAYS) {
                    if ("".equals(fieldHide)) {
                        fieldHide = ff.getName();
                    }
                    else {
                        fieldHide += "," + ff.getName();
                    }
                }
            }

            String[] fdsHide = StrUtil.split(fieldHide, ",");
            if (fdsHide != null) {
                // 20170731 fgf 增加hideNestCol
                str.append("function hideNestCol() {\n");

                for (String s : fdsHide) {
                    if (!s.startsWith("nest.")) {
                        FormField ff = fd.getFormField(s);
                        if (ff == null) {
                            LogUtil.getLog(getClass()).error(fd.getName() + " Field hidden " + s + " is not exist.");
                        } else {
                            str.append(FormField.getHideCtlScript(ff, FORM_FLEMENT_ID));
                        }
                    } else {
                        str.append("try{ hideNestTableCol('").append(s.substring("nest.".length())).append("'); }catch(e) {}\n");
                        str.append("try{ hideNestSheetCol('").append(s.substring("nest.".length())).append("'); }catch(e) {}\n");
                    }
                }
                str.append("}\n");

                str.append("hideNestCol();\n");

                // 屏蔽鼠标右键
                // if (!pvg.isUserPrivValid(request, "admin"))
                //    str += "$(document).bind('contextmenu', function(e){return false;});";
            }

        }

        // 清除其它辅助图片按钮等
        str.append("ClearAccessory();\n");
        str.append("</script>\n");

        return content + str;
    }

    @Override
    public String reportForArchive(WorkflowDb wf, FormDb fd, IFormDAO fdao) {
        Vector<FormField> vf = fdao.getFields();
        Iterator<FormField> fir = vf.iterator();
        ArrayList<String> list = new ArrayList<>();
        while (fir.hasNext()) {
            FormField ff = fir.next();
            // 置为不能编辑，以使得CKEditorCtl初始化时，不转变为编辑器
            ff.setEditable(false);

            if (ff.getFieldType() == FormField.FIELD_TYPE_DATETIME) {
                list.add(ff.getName());
            }
        }

        String content = fd.getContent();
        content = FormParser.replaceTextfieldWithValue(request, content, fdao, fd);
        content = FormParser.replaceTextAreaWithValue(request, content, fdao, fd);
        content = FormParser.replaceSelectWithValue(request, content, fdao, fd);

        content = replaceDefaultStr(wf, fd, fdao, content);

        // 清除其它辅助图片按钮等
        String pat = "<img([^>]*?)calendar.gif([^>]*?)>";
        Pattern pattern = Pattern.compile(pat,
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        content = matcher.replaceAll("");

        pat = "<img([^>]*?)clock.gif([^>]*?)>";
        pattern = Pattern.compile(pat,
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(content);
        content = matcher.replaceAll("");
        //替换存档表单中的日期控件时间输入框 2015-01-29
        pat = "<input([^>]*?)value=12:30:30 name=([^>]*?)_time>";
        pattern = Pattern.compile(pat,
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(content);
        content = matcher.replaceAll("");

        for (String code : list) {
            pat = "<input([^>]*?)" + code + "_time([^>]*?)>";
            pattern = Pattern.compile(pat,
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            matcher = pattern.matcher(content);
            content = matcher.replaceAll("");
        }
        return content;
    }

    /**
     * 解析表单，根据当前action用户可编辑的表单域，禁止其它表单域
     * @return String
     */
    @Override
    public String rendFree(WorkflowDb wf, FormDb fd, WorkflowActionDb wfa) {
        // 根据用户所属的角色，取出其能写入的表单域
        Privilege pvg = new Privilege();
        String userName = pvg.getUser(request);
        WorkflowPredefineDb wfpd = new WorkflowPredefineDb();
        wfpd = wfpd.getPredefineFlowOfFree(wf.getTypeCode());

        String[] fds = wfpd.getFieldsWriteOfUser(wf, userName);

        FormDAO fdao = new FormDAO();
        fdao = fdao.getFormDAOByCache(wf.getId(), fd);

        // SQL宏控件在getDisableCtlScript时，在调用convertToHtml时会用到fdao
        RequestUtil.setFormDAO(request, fdao);

        Vector<FormField> v = fdao.getFields();
        Iterator<FormField> ir = v.iterator();
        // 将不可写的域筛选出
        Vector<FormField> vdisable = new Vector<>();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            ff.setEditable(true);

            boolean finded = false;
            for (String s : fds) {
                if (ff.getName().equals(s)) {
                    finded = true;
                    break;
                }
            }
            if (!finded) {
                vdisable.addElement(ff);
                // 置为不能编辑，以使得CKEditorCtl初始化时，不转变为编辑器
                ff.setEditable(false);
            }
        }

        // 替换宏控件，替换的过程中，有的宏控件（如第三方数据宏控件）可能会修改其它字段的值，因此需要传入fdao
        String content = getContentMacroReplaced(wf, fd, fdao);
        // 再获取一次
        v = fdao.getFields();

        content = replaceDefaultStr(wf, fd, fdao, wfa, content);
        // String formId = "flowForm";

        // 置用户已操作的值
        StringBuilder str = new StringBuilder();

        MacroCtlMgr mm = new MacroCtlMgr();

        // 为了使控件中的值中有"号或者'号时，JS不出错，采用如下处理方式 20060909
        ir = fdao.getFields().iterator();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu != null) {
                    IFormMacroCtl iFormMacroCtl = mu.getIFormMacroCtl();
                    iFormMacroCtl.setIFormDAO(fdao);
                    str.append(iFormMacroCtl.getOuterHTMLOfElementsWithRAWValueAndHTMLValue(request, ff));
                }
            } else {
                str.append(FormField.getOuterHTMLOfElementsWithRAWValueAndHTMLValue(request, ff));
            }
        }

        // LogUtil.getLog(getClass()).info("pageType:" + request.getAttribute("pageType"));

        str.append("\n<script>\n");

        ir = fdao.getFields().iterator();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu != null) {
                    str.append(mu.getIFormMacroCtl().getSetCtlValueScript(request, fdao, ff, FORM_FLEMENT_ID));
                }
            }
            else {
                str.append(FormField.getSetCtlValueScript(request, fdao, ff, FORM_FLEMENT_ID));
            }
        }
        // 根据用户不可写的表单域的type，name，插入相应的JavaScript，禁止控件
        ir = vdisable.iterator();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu != null) {
                    str.append(mu.getIFormMacroCtl().getDisableCtlScript(ff, FORM_FLEMENT_ID));
                }
            }
            else {
                str.append(FormField.getDisableCtlScript(ff, FORM_FLEMENT_ID));
            }
        }

        str.append("</script>\n");

        str.append(FormUtil.doGetCheckJS(request, v));

        str.append(FormUtil.doGetCheckJSUnique(fdao.getId(), v));

        // 格式化，千分位
        str.append(FormUtil.doGetCheckJSFormat(v));
        return content + str;
    }

    /**
     * 显示嵌套表单
     * @param request HttpServletRequest
     * @param formCode String 嵌套表单的编码
     * @param wfa WorkflowActionDb 当前处理动作
     * @return String
     */
    @Override
    public String rendForNestCtl(HttpServletRequest request, String formCode, WorkflowActionDb wfa) {
        FormDb formDb = new FormDb();
        formDb = formDb.getFormDb(formCode);
        com.redmoon.oa.visual.FormDAO visualfdao = new com.redmoon.oa.visual.FormDAO(formDb);

        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(wfa.getFlowId());
        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());
        FormDb parentFd = new FormDb();
        parentFd = parentFd.getFormDb(lf.getFormCode());

        com.redmoon.oa.flow.FormDAO fdao = new com.redmoon.oa.flow.FormDAO();
        fdao = fdao.getFormDAOByCache(wf.getId(), parentFd);
        long cwsId = fdao.getId();

        // SQL宏控件在getDisableCtlScript时，在调用convertToHtml时会用到fdao
        RequestUtil.setFormDAO(request, fdao);

        int visualObjId = visualfdao.getIDByCwsId(String.valueOf(cwsId));

        visualfdao = visualfdao.getFormDAOByCache(visualObjId, formDb);

        LogUtil.getLog(getClass()).info("rendForNestCtl visualObjId=" + visualObjId);

        com.redmoon.oa.visual.Render rd = new com.redmoon.oa.visual.Render(request, visualObjId, formDb);
        String content = rd.rend(FORM_FLEMENT_ID);
        // LogUtil.getLog(getClass()).info("rendForNestCtl content=" + content);

        // 检查本节点用户是否有填写嵌套表单的权限
        String fieldWrite = StrUtil.getNullString(wfa.getFieldWrite()).trim();
        boolean canWrite = true;

        MacroCtlMgr mm = new MacroCtlMgr();

        String[] fds = StrUtil.split(fieldWrite, ",");
        int len = 0;
        if (fds!=null) {
            len = fds.length;
        }
        if (len==0) {
            canWrite = false;
        } else {
            for (FormField ff : parentFd.getFields()) {
                boolean finded = false;
                for (int i = 0; i < len; i++) {
                    if (ff.getName().equals(fds[i])) {
                        finded = true;
                        break;
                    }
                }
                if (!finded) {
                    if (ff.getType().equals(FormField.TYPE_MACRO)) {
                        MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                        if (mu.getNestType() == MacroCtlUnit.NEST_TYPE_NORMAIL) {
                            if (ff.getDefaultValueRaw().equals(formCode)) {
                                // 嵌套表单不可写
                                canWrite = false;
                                break;
                            }
                        }
                    }
                }
            }
        }

        Vector<FormField> vdisable = visualfdao.getFields();
        content = replaceDefaultStr(wf, formDb, fdao, wfa, content);

        // 置用户已操作的值
        StringBuilder str = new StringBuilder();
        str.append("\n<script>\n");
        LogUtil.getLog(getClass()).info("rendForNestCtl canWrite=" + canWrite);

        // 根据用户不可写的表单域的type，name，插入相应的JavaScript，禁止控件
        if (!canWrite) {
            for (FormField ff : vdisable) {
                if (ff.getType().equals(FormField.TYPE_MACRO)) {
                    MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                    if (mu != null) {
                        str.append(mu.getIFormMacroCtl().getDisableCtlScript(ff, FORM_FLEMENT_ID));
                    }
                } else {
                    str.append(FormField.getDisableCtlScript(ff, FORM_FLEMENT_ID));
                }
            }
        }
        str.append("</script>\n");
        return content + str;
    }

    /**
     * 20230622 原rend方法的备份
     * @param wf
     * @param fd
     * @param wfa
     * @param isForFormEdit
     * @return
     * @throws ErrMsgException
     */
    public String rendXXX(WorkflowDb wf, FormDb fd, WorkflowActionDb wfa, boolean isForFormEdit) throws ErrMsgException {
        long t = System.currentTimeMillis();
        try {
            License.getInstance().checkSolution(fd.getCode());
        } catch (ErrMsgException e) {
            return e.getMessage();
        }

        String fieldWrite = StrUtil.getNullString(wfa.getFieldWrite()).trim();
        String fieldHide = StrUtil.getNullString(wfa.getFieldHide()).trim();
        boolean canWriteAll = false;

        if (isForFormEdit) {
            canWriteAll = true;
        }

        String[] fds = fieldWrite.split(",");

        /*
         * 2008.9.17 将下面的两行改为采用fdao，因为用fdm取fields，会重复至数据库中获取，涉及render(WorkflowActionDb wfa)、rend()及rendFree
         * FormDAOMgr fdm = new FormDAOMgr(wf.getId(), fd.getCode());
         * Vector v = fdm.getFields();
         */
        FormDAO fdao = new FormDAO();
        fdao = fdao.getFormDAOByCache(wf.getId(), fd);

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend getFormDAOByCache", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }
        // SQL宏控件在getDisableCtlScript时，在调用convertToHtml时会用到fdao
        RequestUtil.setFormDAO(request, fdao);

        Vector<FormField> v = fdao.getFields();

        // 替换宏控件，替换的过程中，有的宏控件（如第三方数据宏控件）可能会修改其它字段的值，因此需要传入fdao
        String content = "";
        if (wfa.getFormView()!=WorkflowActionDb.VIEW_DEFAULT) {
            FormViewDb fvd = new FormViewDb();
            fvd = fvd.getFormViewDb(wfa.getFormView());
            if (fvd == null){
                String msg = "表单视图不存在";//LocalUtil.LoadString(request,"res.flow.Flow", "formVeiwNotExist");
                throw new  ErrMsgException(msg);
            }
            content = fvd.getString("form");

            String ieVersion = fvd.getString("ie_version");
            FormParser fp = new FormParser();
            Vector<FormField> fields = fp.parseCtlFromView(fvd.getString("content"), ieVersion, fd);

            // 将fields改为已fdao中已取得值的FormField
            Vector<FormField> vt = new Vector<>();
            for (FormField ff : fields) {
                vt.addElement(fdao.getFormField(ff.getName()));
            }
            v = vt;
        }

        // 将不可写的域筛选出
        Iterator<FormField> ir = v.iterator();
        Vector<FormField> vdisable = new Vector<>();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            // 因为从缓存中取出的时候，ff.isEditable()有可能在其它地方已经产生了变化
            // 如：当处理流程后，切换至查看详情画面，此时所有的fields在report方法被设为了false
            // 所以此处需对editable重新设置，否则SQLCtl控件因为editable为false，会致控件不能响应onSQLCtlRelateFieldChange事件
            // 还有文件宏控件会因为editable为false致不能生成控件
            ff.setEditable(true);

            boolean finded = false;
            for (String s : fds) {
                if (ff.getName().equals(s)) {
                    finded = true;
                    break;
                }
            }

            if (!finded) {
                vdisable.addElement(ff);
                // 置为不能编辑，以使得CKEditorCtl初始化时，不转变为编辑器
                ff.setEditable(false);
            }
        }

        // 将不显示的字段加入fieldHide
        ir = v.iterator();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            if (ff.getHide()==FormField.HIDE_EDIT || ff.getHide()==FormField.HIDE_ALWAYS) {
                if ("".equals(fieldHide)) {
                    fieldHide = ff.getName();
                }
                else {
                    fieldHide += "," + ff.getName();
                }
            }
        }

        String[] fdsHide = StrUtil.split(fieldHide, ",");
        if (fdsHide!=null) {
            ir = v.iterator();
            while (ir.hasNext()) {
                FormField ffHide = ir.next();
                for (String hideFieldName : fdsHide) {
                    if (ffHide.getName().equals(hideFieldName)) {
                        ffHide.setHidden(true);
                        break;
                    }
                }
            }
        }

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend before getContentMacroReplaced", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        if (wfa.getFormView()==WorkflowActionDb.VIEW_DEFAULT) {
            content = getContentMacroReplaced(wf, fd, fdao);
        }
        else {
            content = getContentMacroReplaced(fdao, content);
        }

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend after getContentMacroReplaced", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        content = replaceDefaultStr(wf, fd, fdao, wfa, content);

        // 置用户已操作的值
        StringBuilder str = new StringBuilder();

        MacroCtlMgr mm = new MacroCtlMgr();

        // 为了使控件中的值中有"号或者'号时，JS不出错，采用如下处理方式 20060909
        ir = v.iterator();
        while (ir.hasNext()) {
            FormField formField = ir.next();

            FormField ff = null;
            // clone是为了避免当renderFieldValue后，再setValue时，因为千分位格式化的原因致原始value被更改
            try {
                ff = (FormField)formField.clone();
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
            // 如果是不可写的字段，则对其值进行渲染，如价格型显示为两位小数点
            if (!ff.isEditable()) {
                if (ff.getFieldType() == FormField.FIELD_TYPE_PRICE) {
                    ff.setValue(FuncUtil.renderFieldValue(fdao, ff));
                }
            }

            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu != null) {
                    IFormMacroCtl iFormMacroCtl = mu.getIFormMacroCtl();
                    iFormMacroCtl.setIFormDAO(fdao);
                    str.append(iFormMacroCtl.getOuterHTMLOfElementsWithRAWValueAndHTMLValue(request, ff));
                }
            }
            else {
                str.append(FormField.getOuterHTMLOfElementsWithRAWValueAndHTMLValue(request, ff));
            }
        }

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend getOuterHTMLOfElementsWithRAWValueAndHTMLValue", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        str.append("\n<script>\n");

        ir = v.iterator();
        while (ir.hasNext()) {
            FormField ff = ir.next();

            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu != null) {
                    str.append(mu.getIFormMacroCtl().getSetCtlValueScript(request, fdao, ff, FORM_FLEMENT_ID));
                }
            }
            else {
                str.append(FormField.getSetCtlValueScript(request, fdao, ff, FORM_FLEMENT_ID));
            }
        }

        str.append("</script>\n");

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend getSetCtlValueScript", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        str.append("<script>\n");

        // 根据用户不可写的表单域的type，name，插入相应的JavaScript，禁止控件
        if (!canWriteAll) {
            ir = vdisable.iterator();
            while (ir.hasNext()) {
                FormField ff = ir.next();
                LogUtil.getLog(getClass()).info("不可写字段" + ff.getName() + ":" + ff.getTitle());
                if (ff.getType().equals(FormField.TYPE_MACRO)) {
                    MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                    if (mu != null) {
                        // 传入fdao，函数宏控件计算时需要用到
                        IFormMacroCtl ifc = mu.getIFormMacroCtl();
                        ifc.setIFormDAO(fdao);
                        str.append(ifc.getDisableCtlScript(ff, FORM_FLEMENT_ID));
                    }
                }
                else {
                    str.append(FormField.getDisableCtlScript(ff, FORM_FLEMENT_ID));
                }
            }
        }

        // 处理嵌套表的可写单元格
        for (String s : fds) {
            if (s.startsWith("nest.")) {
                str.append("try{ setNestTableColWritable('").append(s.substring("nest.".length())).append("'); }catch(e) {}\n");
            }
        }

        // 处理隐藏表单域
        if (fdsHide!=null) {
            // 20161222 fgf 因为嵌套表格2是通过loadNestCtl，ajax方式获得的，所以需在nest_sheet_view.jsp中调用，以隐藏列
            str.append("function hideNestCol() {\n");

            for (String s : fdsHide) {
                if (!s.startsWith("nest.")) {
                    FormField ff = fd.getFormField(s);
                    if (ff == null) {
                        LogUtil.getLog(getClass()).error(fd.getName() + " Field hidden " + s + " is not exist.");
                    } else {
                        str.append(FormField.getHideCtlScript(ff, FORM_FLEMENT_ID));
                    }
                } else {
                    str.append("try{ hideNestTableCol('").append(s.substring("nest.".length())).append("'); }catch(e) {}\n");
                    str.append("try{ hideNestSheetCol('").append(s.substring("nest.".length())).append("'); }catch(e) {}\n");
                }
            }
            str.append("}\n");

            str.append("hideNestCol();\n");

            // 如果不是管理员，则屏蔽鼠标右键，便于调试
            // Privilege pvg = new Privilege();
            // if (!pvg.isUserPrivValid(request, "admin"))
            //    str += "$(document).bind('contextmenu', function(e){return false;});";
        } else {
            // 如果之前隐藏了字段，则nest_sheet_view.jsp中调用hideNestCol()隐藏列的时候，会再次调用，所以应清除原来的方法
            // 如：街道区内流转至区，街道节点隐藏了拟流入街道字段，而切换区帐户登录后，如果不清除，则会在区节点隐藏该字段
            str.append("function hideNestCol() {\n");
            str.append("}\n");
        }

        str.append("</script>\n");

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend before doGetCheckJS", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        // livevalidation检查
        str.append(FormUtil.doGetCheckJS(request, v));

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend after doGetCheckJS", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        // 取得算式(如$Age)的参数中所关联的表单域当值发生改变时，重新获取值的脚本
        str.append(FuncUtil.doGetFieldsRelatedOnChangeJS(fd));

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend doGetFieldsRelatedOnChangeJS", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }
        // 唯一性检查
        str.append(FormUtil.doGetCheckJSUnique(fdao.getId(), v));
        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend doGetCheckJSUnique", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }
        // 格式化，千分位
        str.append(FormUtil.doGetCheckJSFormat(v));
        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend doGetCheckJSFormat", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend end", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        // 与后端配合，强制在渲染的脚本执行完后，使工具条按钮可用，以免中间有脚本错误，仍能提交
        str.append("<script>");
        str.append("try{ onRendEnd(" + wf.getId() + ", '" + fd.getCode() + "'); } catch(e) {console.warn('Function onRenderEnd is not defined')}");
        str.append("</script>");
        return content + str;
    }

    /**
     * 解析表单，根据当前action用户可编辑的表单域，禁止其它表单域
     * @param wfa WorkflowActionDb
     * @param isForFormEdit boolean 是否用于编辑表单内容（只能由流程管理员编辑）
     * @return String
     */
    @Override
    public String rend(WorkflowDb wf, FormDb fd, WorkflowActionDb wfa, boolean isForFormEdit) throws ErrMsgException {
        long t = System.currentTimeMillis();
        try {
            License.getInstance().checkSolution(fd.getCode());
        } catch (ErrMsgException e) {
            return e.getMessage();
        }

        /*
         * 2008.9.17 将下面的两行改为采用fdao，因为用fdm取fields，会重复至数据库中获取，涉及render(WorkflowActionDb wfa)、rend()及rendFree
         * FormDAOMgr fdm = new FormDAOMgr(wf.getId(), fd.getCode());
         * Vector v = fdm.getFields();
         */
        FormDAO fdao = new FormDAO();
        fdao = fdao.getFormDAOByCache(wf.getId(), fd);

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend getFormDAOByCache", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        String fieldWrite = StrUtil.getNullString(wfa.getFieldWrite()).trim();
        String fieldHide = StrUtil.getNullString(wfa.getFieldHide()).trim();
        boolean canWriteAll = false;

        if (isForFormEdit) {
            canWriteAll = true;
        }

        String[] fds = fieldWrite.split(",");

        // SQL宏控件在getDisableCtlScript时，在调用convertToHtml时会用到fdao 20230622 重构后暂不删除此行，以免有用到
        RequestUtil.setFormDAO(request, fdao);

        Vector<FormField> v = fdao.getFields();

        // 替换宏控件，替换的过程中，有的宏控件（如第三方数据宏控件）可能会修改其它字段的值，因此需要传入fdao
        String content = "";
        if (wfa.getFormView() != WorkflowActionDb.VIEW_DEFAULT) {
            FormViewDb fvd = new FormViewDb();
            fvd = fvd.getFormViewDb(wfa.getFormView());
            if (fvd == null) {
                String msg = "表单视图不存在";//LocalUtil.LoadString(request,"res.flow.Flow", "formVeiwNotExist");
                throw new ErrMsgException(msg);
            }
            content = fvd.getString("form");

            String ieVersion = fvd.getString("ie_version");
            FormParser fp = new FormParser();
            Vector<FormField> fields = fp.parseCtlFromView(fvd.getString("content"), ieVersion, fd);

            // 将fields改为已fdao中已取得值的FormField
            Vector<FormField> vt = new Vector<>();
            for (FormField ff : fields) {
                vt.addElement(fdao.getFormField(ff.getName()));
            }
            v = vt;
        }

        // 将不可写的域筛选出
        Iterator<FormField> ir = v.iterator();
        Vector<FormField> vdisable = new Vector<>();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            // 因为从缓存中取出的时候，ff.isEditable()有可能在其它地方已经产生了变化
            // 如：当处理流程后，切换至查看详情画面，此时所有的fields在report方法被设为了false
            // 所以此处需对editable重新设置，否则SQLCtl控件因为editable为false，会致控件不能响应onSQLCtlRelateFieldChange事件
            // 还有文件宏控件会因为editable为false致不能生成控件
            ff.setEditable(true);

            boolean finded = false;
            for (String s : fds) {
                if (ff.getName().equals(s)) {
                    finded = true;
                    break;
                }
            }

            if (!finded) {
                vdisable.addElement(ff);
                // 置为不能编辑，以使得CKEditorCtl初始化时，不转变为编辑器
                ff.setEditable(false);
            }
        }

        // 将不显示的字段加入fieldHide
        ir = v.iterator();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            if (ff.getHide()==FormField.HIDE_EDIT || ff.getHide()==FormField.HIDE_ALWAYS) {
                if ("".equals(fieldHide)) {
                    fieldHide = ff.getName();
                }
                else {
                    fieldHide += "," + ff.getName();
                }
            }
        }

        String[] fdsHide = StrUtil.split(fieldHide, ",");
        if (fdsHide!=null) {
            ir = v.iterator();
            while (ir.hasNext()) {
                FormField ffHide = ir.next();
                for (String hideFieldName : fdsHide) {
                    if (ffHide.getName().equals(hideFieldName)) {
                        ffHide.setHidden(true);
                        break;
                    }
                }
            }
        }

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend before getContentMacroReplaced", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        if (wfa.getFormView()==WorkflowActionDb.VIEW_DEFAULT) {
            content = getContentMacroReplaced(wf, fd, fdao);
        }
        else {
            content = getContentMacroReplaced(fdao, content);
        }

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend after getContentMacroReplaced", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        content = replaceDefaultStr(wf, fd, fdao, wfa, content);

        // 置用户已操作的值
        StringBuilder str = new StringBuilder();

        MacroCtlMgr mm = new MacroCtlMgr();

        // 为了使控件中的值中有"号或者'号时，JS不出错，采用如下处理方式 20060909
        ir = v.iterator();
        while (ir.hasNext()) {
            FormField formField = ir.next();

            FormField ff = null;
            // clone是为了避免当renderFieldValue后，再setValue时，因为千分位格式化的原因致原始value被更改
            try {
                ff = (FormField)formField.clone();
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
            // 如果是不可写的字段，则对其值进行渲染，如价格型显示为两位小数点
            if (!ff.isEditable()) {
                if (ff.getFieldType() == FormField.FIELD_TYPE_PRICE) {
                    ff.setValue(FuncUtil.renderFieldValue(fdao, ff));
                }
            }

            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu != null) {
                    IFormMacroCtl iFormMacroCtl = mu.getIFormMacroCtl();
                    iFormMacroCtl.setIFormDAO(fdao);
                    str.append(iFormMacroCtl.getOuterHTMLOfElementsWithRAWValueAndHTMLValue(request, ff));
                }
            }
            else {
                str.append(FormField.getOuterHTMLOfElementsWithRAWValueAndHTMLValue(request, ff));
            }
        }

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend getOuterHTMLOfElementsWithRAWValueAndHTMLValue", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        str.append("\n<script>\n");

        ir = v.iterator();
        while (ir.hasNext()) {
            FormField ff = ir.next();

            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu != null) {
                    str.append(mu.getIFormMacroCtl().getSetCtlValueScript(request, fdao, ff, FORM_FLEMENT_ID));
                }
            }
            else {
                str.append(FormField.getSetCtlValueScript(request, fdao, ff, FORM_FLEMENT_ID));
            }
        }

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend getSetCtlValueScript", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        // 根据用户不可写的表单域的type，name，插入相应的JavaScript，禁止控件
        if (!canWriteAll) {
            ir = vdisable.iterator();
            while (ir.hasNext()) {
                FormField ff = ir.next();
                long t2 = System.currentTimeMillis();
                LogUtil.getLog(getClass()).info("不可写字段" + ff.getName() + ":" + ff.getTitle());
                if (ff.getType().equals(FormField.TYPE_MACRO)) {
                    MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                    if (mu != null) {
                        // 传入fdao，函数宏控件计算时需要用到
                        IFormMacroCtl ifc = mu.getIFormMacroCtl();
                        ifc.setIFormDAO(fdao);
                        str.append(ifc.getDisableCtlScript(ff, FORM_FLEMENT_ID));
                        if (Global.getInstance().isDebug()) {
                            LogUtil.getLog(getClass()).info("rend getDisableCtlScript " + ff.getTitle() + ":" + ff.getName() + " " + (System.currentTimeMillis() - t2) + " ms");
                        }
                    }
                }
                else {
                    str.append(FormField.getDisableCtlScript(ff, FORM_FLEMENT_ID));
                    if (Global.getInstance().isDebug()) {
                        LogUtil.getLog(getClass()).info("rend getDisableCtlScript " + ff.getTitle() + ":" + ff.getName() + " " + (System.currentTimeMillis() - t2) + " ms");
                    }
                }
            }
        }

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend getDisableCtlScript", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        // 处理嵌套表的可写单元格
        for (String s : fds) {
            if (s.startsWith("nest.")) {
                str.append("try{ setNestTableColWritable('").append(s.substring("nest.".length())).append("'); }catch(e) {}\n");
            }
        }

        // 处理隐藏表单域，为了加快隐藏速度，不放到rendScriptByAction中
        if (fdsHide!=null) {
            // 20161222 fgf 因为嵌套表格2是通过loadNestCtl，ajax方式获得的，所以需在nest_sheet_view.jsp中调用，以隐藏列
            str.append("function hideNestCol() {\n");

            for (String s : fdsHide) {
                if (!s.startsWith("nest.")) {
                    FormField ff = fd.getFormField(s);
                    if (ff == null) {
                        LogUtil.getLog(getClass()).error(fd.getName() + " Field hidden " + s + " is not exist.");
                    } else {
                        str.append(FormField.getHideCtlScript(ff, FORM_FLEMENT_ID));
                    }
                } else {
                    str.append("try{ hideNestTableCol('").append(s.substring("nest.".length())).append("'); }catch(e) {}\n");
                    str.append("try{ hideNestSheetCol('").append(s.substring("nest.".length())).append("'); }catch(e) {}\n");
                }
            }
            str.append("}\n");

            str.append("hideNestCol();\n");

            // 如果不是管理员，则屏蔽鼠标右键，便于调试
            // Privilege pvg = new Privilege();
            // if (!pvg.isUserPrivValid(request, "admin"))
            //    str += "$(document).bind('contextmenu', function(e){return false;});";
        } else {
            // 如果之前隐藏了字段，则nest_sheet_view.jsp中调用hideNestCol()隐藏列的时候，会再次调用，所以应清除原来的方法
            // 如：街道区内流转至区，街道节点隐藏了拟流入街道字段，而切换区帐户登录后，如果不清除，则会在区节点隐藏该字段
            str.append("function C() {\n");
            str.append("}\n");
        }

        str.append("</script>\n");

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend getDisableCtlScript", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        // livevalidation检查
        str.append(FormUtil.doGetCheckJS(request, v));

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend doGetCheckJS", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        // 取得算式(如$Age)的参数中所关联的表单域当值发生改变时，重新获取值的脚本
        str.append(FuncUtil.doGetFieldsRelatedOnChangeJS(fd));

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend doGetFieldsRelatedOnChangeJS", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }
        // 唯一性检查
        str.append(FormUtil.doGetCheckJSUnique(fdao.getId(), v));
        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend doGetCheckJSUnique", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        // 格式化，千分位
        str.append(FormUtil.doGetCheckJSFormat(v));
        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend doGetCheckJSFormat", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        // 与后端配合，强制在渲染的脚本执行完后，使工具条按钮可用，以免中间有脚本错误，却仍能提交
        str.append("<script>");
        str.append("try{ onRendEnd(" + wf.getId() + ", '" + fd.getCode() + "'); } catch(e) {console.warn('Function onRenderEnd is not defined')}");
        str.append("</script>");

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rend end", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }
        return content + str;
    }

    @Override
    public String rendScriptByAction(int actionId, boolean canWriteAll) {
        long t = System.currentTimeMillis();
        WorkflowActionDb wfa = new WorkflowActionDb();
        wfa = wfa.getWorkflowActionDb(actionId);
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(wfa.getFlowId());
        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());
        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());
        FormDAO fdao = new FormDAO();
        fdao = fdao.getFormDAOByCache(wf.getId(), fd);

        // String fieldWrite = StrUtil.getNullString(wfa.getFieldWrite()).trim();

        // SQL宏控件在getDisableCtlScript时，在调用convertToHtml时会用到fdao
        RequestUtil.setFormDAO(request, fdao);

        Vector<FormField> v = fdao.getFields();

        if (wfa.getFormView() != WorkflowActionDb.VIEW_DEFAULT) {
            FormViewDb fvd = new FormViewDb();
            fvd = fvd.getFormViewDb(wfa.getFormView());
            if (fvd == null) {
                String msg = "表单视图不存在";//LocalUtil.LoadString(request,"res.flow.Flow", "formVeiwNotExist");
                throw new ErrMsgException(msg);
            }

            String ieVersion = fvd.getString("ie_version");
            FormParser fp = new FormParser();
            Vector<FormField> fields = fp.parseCtlFromView(fvd.getString("content"), ieVersion, fd);

            // 将fields改为已fdao中已取得值的FormField
            Vector<FormField> vt = new Vector<>();
            for (FormField ff : fields) {
                vt.addElement(fdao.getFormField(ff.getName()));
            }
            v = vt;
        }

        /*// 将不可写的域筛选出，还是将使不可写的脚本放在了rend中，否则因滞后体验不佳
        Iterator<FormField> ir = v.iterator();
        Vector<FormField> vdisable = new Vector<>();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            // 因为从缓存中取出的时候，ff.isEditable()有可能在其它地方已经产生了变化
            // 如：当处理流程后，切换至查看详情画面，此时所有的fields在report方法被设为了false
            // 所以此处需对editable重新设置，否则SQLCtl控件因为editable为false，会致控件不能响应onSQLCtlRelateFieldChange事件
            // 还有文件宏控件会因为editable为false致不能生成控件
            ff.setEditable(true);

            boolean finded = false;
            for (String s : fds) {
                if (ff.getName().equals(s)) {
                    finded = true;
                    break;
                }
            }

            if (!finded) {
                vdisable.addElement(ff);
                // 置为不能编辑，以使得CKEditorCtl初始化时，不转变为编辑器
                ff.setEditable(false);
            }
        }*/

        StringBuilder str = new StringBuilder();

        str.append(workflowProUtil.doGetViewJS(request, wfa, fd, fdao, new Privilege().getUser(request), false));
        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "rendScriptByAction doGetViewJS", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        return str.toString();
    }

    public String randStr(String content) {
        // 随机包裹元素
        String[] els = new String[] {"div", "span", "p", "ul", "li"};
        String el = els[NumberUtil.random(0, els.length - 1)];

        String style = "";

        // 随机背景色
        if (Math.random()>0.5) {
            String bkClr = "background-color: #" + getRandColor();
            style = "style='" + bkClr;
        }

        // 随机宽度
        int width = NumberUtil.random(80, 100);
        if ("".equals(style)) {
            style = "style='width: " + width + "px";
        }
        else {
            style += "; width: " + width + "px";
        }

        // 随机前景色
        if (Math.random()>0.5) {
            if ("".equals(style)) {
                style = "style='color: #" + getRandColor();
            }
            else {
                style += "; color: #" + getRandColor();
            }
        }

        // 随机左边距
        if (Math.random() > 0.5) {
            int marginLeft = NumberUtil.random(0, 10);
            if ("".equals(style)) {
                style = "style='margin-left: " + marginLeft + "px";
            }
            else {
                style += "; margin-left: " + marginLeft + "px";
            }
        }

        String trialHtml = "<" + el;
        trialHtml += " " + style + "' ";
        trialHtml += ">";
        trialHtml += getRandText();
        trialHtml += "</" + el + ">";

        // 随机位置
        // 在content中找到第1~10个<td>、</td>或者第N个<div>、</div>
        String[] ary = {"<td",  /*"<div", "</div>",*/ "<span", "</td>","</span>"};
        String token = ary[NumberUtil.random(0, ary.length - 1)];

        int MAX_COUNT = 10;
        int n = NumberUtil.random(0, MAX_COUNT);
        if (n == 0) {
            content = trialHtml + content;
        }
        else if (n == MAX_COUNT) {
            content += trialHtml;
        }
        else {
            // 从ueditor自动生成的class="firstRow"开始找，以免被人为地用隐藏的方式元素跳过
            int initP = content.indexOf("firstRow");
            int p = initP;
            if (p == -1) {
                p = 0;
            }

            int i = 0;
            int lastP = p;
            while (p != -1) {
                lastP = p;
                p = content.indexOf(token, p + token.length());
                i++;
                if (i > n) {
                    break;
                }
            }

            if (p == -1) {
                p = lastP;
            }
            // 如果token为span，有可能一个也找不到，此时p = initP
            if (p == initP) {
                content = trialHtml + content;
            } else {
                // 结束标签
                if (token.indexOf("</") != -1) {
                    // DebugUtil.i(getClass(), token + " n=" + n, content.substring(p - 30, p));
                    content = content.substring(0, p) + trialHtml + content.substring(p);
                } else {
                    // DebugUtil.i(getClass(), token + " n=" + n + " p=" + p, content.substring(p, p + 30));
                    // 找到 >
                    p = content.indexOf(">", p);
                    // DebugUtil.i(getClass(), "p", String.valueOf(p));
                    content = content.substring(0, p + 1) + trialHtml + content.substring(p + 1);
                }
            }
        }
        return content;
    }

    /**
     * 获取十六进制的颜色代码.例如  "#6E36B4" , For HTML ,
     * @return String
     */
    public String getRandColor(){
        String r,g,b;
        Random random = new Random();
        r = Integer.toHexString(random.nextInt(256)).toUpperCase();
        g = Integer.toHexString(random.nextInt(256)).toUpperCase();
        b = Integer.toHexString(random.nextInt(256)).toUpperCase();

        r = r.length()==1 ? "0" + r : r ;
        g = g.length()==1 ? "0" + g : g ;
        b = b.length()==1 ? "0" + b : b ;

        return r+g+b;
    }

    public String getRandText() {
        String[] chars = {" ", "　", "&nbsp;", "_", ".", "*", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"};
        int charsLen = chars.length;

        StringBuilder text = new StringBuilder();
        int len = NumberUtil.random(0, 3);
        for (int i = 0; i<len; i++) {
            text.append(chars[NumberUtil.random(0, charsLen - 1)]);
        }
        text.append("试");
        len = NumberUtil.random(0, 3);
        for (int i = 0; i<len; i++) {
            text.append(chars[NumberUtil.random(0, charsLen - 1)]);
        }
        text.append("用");
        len = NumberUtil.random(0, 2);
        for (int i = 0; i<len; i++) {
            text.append(chars[NumberUtil.random(0, charsLen - 1)]);
        }
        text.append("版");
        len = NumberUtil.random(0, 3);
        for (int i = 0; i<len; i++) {
            text.append(chars[NumberUtil.random(0, charsLen - 1)]);
        }

        return text.toString();
    }
}
