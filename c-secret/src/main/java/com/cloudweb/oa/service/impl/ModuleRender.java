package com.cloudweb.oa.service.impl;

import cn.js.fan.db.ResultIterator;
import cn.js.fan.db.ResultRecord;
import cn.js.fan.util.*;
import cn.js.fan.web.Global;
import cn.js.fan.web.SkinUtil;
import com.cloudweb.oa.api.*;
import com.cloudweb.oa.utils.ConfigUtil;
import com.cloudweb.oa.utils.ConstUtil;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudwebsoft.framework.db.JdbcTemplate;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.base.IFormDAO;
import com.redmoon.oa.base.IFormMacroCtl;
import com.redmoon.oa.flow.FormDb;
import com.redmoon.oa.flow.FormField;
import com.redmoon.oa.flow.FormParser;
import com.redmoon.oa.flow.WorkflowActionDb;
import com.redmoon.oa.flow.macroctl.*;
import com.redmoon.oa.kernel.License;
import com.redmoon.oa.person.UserDb;
import com.redmoon.oa.pvg.Privilege;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.util.RequestUtil;
import com.redmoon.oa.util.Word2PDF;
import com.redmoon.oa.visual.*;
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

@Component("moduleRender")
public class ModuleRender implements IModuleRender {

    public static final String FORM_FLEMENT_ID = "visualForm";

    @Autowired
    HttpServletRequest request;


    @Override
    public String rendForAdd(ModuleSetupDb msd, FormDb fd, String content, Vector<FormField> fields) {
        return null;
    }

    @Override
    public String rend(ModuleSetupDb msd, FormDAO fdao, String formElementId, String content, Vector<FormField> fields, Vector<FormField> vdisable) {
        return null;
    }

    @Override
    public String report(FormDAO fdao, String content, boolean isNest) {
        return null;
    }

    @Override
    public String reportForArchive(IFormDAO fdao, String content) {
        return null;
    }


    /**
     * 替换国际化字符串
     * @param content String
     * @return String
     */
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


    public String replaceDefaultStr(FormDb fd, IFormDAO fdao, String content) {
        content = content.replaceFirst("#\\[表单\\]", fd.getName());

        String pat = "\\{\\$rootPath\\}";
        content = content.replaceAll(pat, request.getContextPath());

        content = replaceResStr(content);

        Privilege pvg = new Privilege();
        Pattern p = Pattern.compile(
                "\\{\\$([@A-Z0-9a-z-_\\u4e00-\\u9fa5\\xa1-\\xff\\.]+)\\}", // 前为utf8中文范围，后为gb2312中文范围
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String fieldName = m.group(1);

            String val = "";
            if (fieldName.startsWith("request.")) {
                String key = fieldName.substring("request.".length());
                val = ParamUtil.get(request, key);
            }
            else if ("id".equalsIgnoreCase(fieldName)) {
                if (fdao!=null) {
                    val = String.valueOf(fdao.getId());
                }
            }
            else if ("curUser".equalsIgnoreCase(fieldName)) {
                val = pvg.getUser(request);
            }
            else if ("realName".equalsIgnoreCase(fieldName)) {
                UserDb user = new UserDb();
                user = user.getUserDb(pvg.getUser(request));
                val = user.getRealName();
            }
            else if ("curDate".equalsIgnoreCase(fieldName)) {
                val = DateUtil.format(new java.util.Date(), "yyyy-MM-dd");
            }
            else {
                if (fdao!=null) {
                    val = fdao.getFieldValue(fieldName);
                }
            }
            m.appendReplacement(sb, StrUtil.getNullStr(val));
        }
        m.appendTail(sb);

        return sb.toString();
    }

    @Override
    public String[] rendForNestTable(FormDb fd, String content, Vector<FormField> fields, String FORM_FLEMENT_ID, boolean isAdd, IFormDAO fdao) {
        String[] r = new String[3];
        content = replaceDefaultStr(fd, null, content);
        r[0] = content;

        // 置默认值
        StringBuilder str = new StringBuilder();

        MacroCtlMgr mm = new MacroCtlMgr();
        Iterator<FormField> ir = fields.iterator();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu != null) {
                    // 传入fdao，引用字段宏控件计算时需要用到
                    IFormMacroCtl ifc = mu.getIFormMacroCtl();
                    ifc.setIFormDAO(fdao);
                    str.append(ifc.getOuterHTMLOfElementsWithRAWValueAndHTMLValue(request, ff));
                }
            }
            else {
                if (isAdd) {
                    str.append(FormField.getOuterHTMLOfElementsWithRAWValueAndHTMLValueForVisualAdd(request, ff));
                }
                else {
                    str.append(FormField.getOuterHTMLOfElementsWithRAWValueAndHTMLValue(request, ff));
                }
            }
        }
        r[1] = str.toString();

        str = new StringBuilder("\n<script>\n");
        ir = fields.iterator();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu != null) {
                    str.append(mu.getIFormMacroCtl().getSetCtlValueScript(request, fdao, ff, FORM_FLEMENT_ID));
                }
            } else {
                str.append(FormField.getSetCtlValueScript(request, fdao, ff, FORM_FLEMENT_ID));
            }
        }

        str.append("</script>\n");

        // str += FormUtil.doGetCheckJS(request, fields);

        // 取得函数的参数中所关联的表单域当值发生改变时，重新获取值的脚本
        str.append(FuncUtil.doGetFieldsRelatedOnChangeJS(fd));

        // str += FormUtil.doGetCheckJSUnique(-1, fields);

        r[2] = str.toString();

        return r;
    }

    /**
     *
     * @param fdao
     * @param content
     * @param fields
     * @param vdisable 在visual/Render.java中传入，用于记录不可写的表单域，以提高效率
     * @return
     */
    @Override
    public String getContentMacroReplaced(ModuleSetupDb msd, FormDAO fdao, String content, Vector<FormField> fields, Vector<FormField> vdisable) {
        if (msd!=null) {
            long t = System.currentTimeMillis();
            // 置editable及hidden，因为在宏控件convertToHTML时需用到
            String userName = new Privilege().getUser(request);
            // 取得当前用户的可写字段
            String moduleCode = msd.getString("code");
            ModulePrivDb mpd = new ModulePrivDb(moduleCode);
            Iterator<FormField> ir;
            String fieldWrite = mpd.getUserFieldsHasPriv(userName, "write");
            if (fieldWrite!=null && !"".equals(fieldWrite)) {
                String[] fds = StrUtil.split(fieldWrite, ",");
                if (fds!=null) {
                    // 将不可写的域筛选出
                    ir = fields.iterator();
                    while (ir.hasNext()) {
                        FormField ff = ir.next();
                        // 虽然FormField的editable默认为true，但是有可以会出现象流程FlowRender中一样，
                        // 当从缓存中取出FormDAO后，其中的fields中的editable在有的地方改变了，所以此处仍需再次设置一下editable
                        ff.setEditable(true);

                        boolean finded = false;
                        for (String s : fds) {
                            if (ff.getName().equals(s)) {
                                finded = true;
                                break;
                            }
                        }

                        if (!finded) {
                            // 置为不能编辑，以使得CKEditorCtl初始化时，不转变为编辑器
                            vdisable.addElement(ff);
                            ff.setEditable(false);
                        }
                    }
                }
            }

            String fieldHide = mpd.getUserFieldsHasPriv(userName, "hide");
            String[] fdsHide = StrUtil.split(fieldHide, ",");
            if (fdsHide!=null) {
                ir = fields.iterator();
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

            // 测试发现需隐藏时，必须得可写，所以要把隐藏字段从不可写字段中去掉
            // 从不可写字段中去掉隐藏字段
            if (fdsHide!=null) {
                ir = vdisable.iterator();
                while(ir.hasNext()) {
                    FormField ff = ir.next();
                    for (String s : fdsHide) {
                        if (ff.getName().equals(s)) {
                            ir.remove();
                        }
                    }
                }
            }

            if (Global.getInstance().isDebug()) {
                LogUtil.getLog(getClass()).info("replaceMacroCtlWithHTMLCtl getUserFieldsHasPriv take " + (System.currentTimeMillis() - t) + " ms");
            }
        }

        org.jsoup.nodes.Document doc = Jsoup.parse(content);
        Iterator<FormField> ir = fields.iterator();
        MacroCtlMgr mm = new MacroCtlMgr();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            if (!ff.getMacroType().equals(FormField.MACRO_NOT)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu == null) {
                    throw new IllegalArgumentException("Macro ctl " + ff.getTitle() + " is not exist.");
                } else {
                    IFormMacroCtl ifmc = mu.getIFormMacroCtl();
                    if (ifmc==null) {
                        throw new IllegalArgumentException("Macro ctl " + ff.getMacroType() + "'s IFormMacroCtl is not found.");
                    }
                    else {
                        // 传入fdao备用
                        ifmc.setIFormDAO(fdao);
                        long t = System.currentTimeMillis();
                        ifmc.replaceMacroCtlWithHTMLCtl(request, ff, doc);
                        if (Global.getInstance().isDebug()) {
                            LogUtil.getLog(getClass()).info("replaceMacroCtlWithHTMLCtl " + ff.getTitle() + " " + ff.getName() + " take " + (System.currentTimeMillis() - t) + " ms");
                        }
                    }
                }
            }
        }
        return doc.html();
    }

    @Override
    public String rendNestSheetCtlRelated(FormDAO fdao, FormDb fd, WorkflowActionDb wfa, Vector<FormField> vNestFormField) throws ErrMsgException {
        return null;
    }

}
