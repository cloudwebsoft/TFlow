package com.cloudweb.oa.service.impl;

import bsh.EvalError;
import bsh.Interpreter;
import cn.js.fan.db.ResultIterator;
import cn.js.fan.db.SQLFilter;
import cn.js.fan.util.*;
import cn.js.fan.web.Global;
import com.alibaba.fastjson.JSONObject;
import com.cloudweb.oa.api.*;
import com.cloudweb.oa.cache.FormShowRuleCache;
import com.cloudweb.oa.cache.RoleCache;
import com.cloudweb.oa.cache.UserCache;
import com.cloudweb.oa.entity.Role;
import com.cloudweb.oa.entity.UserOfRole;
import com.cloudweb.oa.security.AuthUtil;
import com.cloudweb.oa.service.IUserOfRoleService;
import com.cloudweb.oa.utils.ConstUtil;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudweb.oa.utils.WorkflowProUtil;
import com.cloudwebsoft.framework.db.JdbcTemplate;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.kit.util.FileUpload;
import com.redmoon.oa.base.IFormDAO;
import com.redmoon.oa.base.IFormMacroCtl;
import com.redmoon.oa.dept.DeptDb;
import com.redmoon.oa.dept.DeptUserDb;
import com.redmoon.oa.flow.*;
import com.redmoon.oa.flow.macroctl.MacroCtlMgr;
import com.redmoon.oa.flow.macroctl.MacroCtlUnit;
import com.redmoon.oa.person.UserDb;
import com.redmoon.oa.pvg.Privilege;
import com.redmoon.oa.pvg.RoleDb;
import com.redmoon.oa.shell.BSHShell;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.visual.FormDAO;
import com.redmoon.oa.visual.ModuleSetupDb;
import com.redmoon.oa.visual.ModuleUtil;
import nl.bitwalker.useragentutils.Browser;
import nl.bitwalker.useragentutils.DeviceType;
import nl.bitwalker.useragentutils.OperatingSystem;
import nl.bitwalker.useragentutils.UserAgent;
import org.apache.commons.lang3.StringUtils;
import org.htmlparser.Node;
import org.htmlparser.Parser;
import org.htmlparser.filters.HasAttributeFilter;
import org.htmlparser.nodes.TagNode;
import org.htmlparser.tags.InputTag;
import org.htmlparser.tags.SelectTag;
import org.htmlparser.tags.TextareaTag;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component("moduleUtilService")
public class ModuleUtilService implements IModuleUtil {

    @Autowired
    IUserOfRoleService userOfRoleService;

    @Autowired
    AuthUtil authUtil;

    @Autowired
    IWorkflowUtil workflowProUtil;

    @Autowired
    FormShowRuleCache formShowRuleCache;

    /**
     * 解析出过滤条件中的字段，以便于表单域选择宏控件在调用onSelect***方法时，能够post相关字段的值，以便于实时映射字段值的时候在sql中加入filter
     * @param request
     * @param formCode filter中字段{$field}所属的表单编码
     * @param filter
     * @return
     */
    @Override
    public List<String> parseFieldNameInFilter(HttpServletRequest request, String formCode, String filter) {
        List<String> list = new ArrayList<>();
        FormDb fd = new FormDb();
        fd = fd.getFormDb(formCode);

        // 将filter中的%n转换为换行符
        filter = ModuleUtil.decodeFilter(filter);

        if (filter.startsWith("<items>")) {
            List<String> filedList = new ArrayList<>();
            for (FormField ff : fd.getFields()) {
                filedList.add(ff.getName());
            }

            SAXBuilder parser = new SAXBuilder();
            org.jdom.Document doc;
            try {
                doc = parser.build(new InputSource(new StringReader(filter)));

                Element root = doc.getRootElement();
                List<Element> vroot = root.getChildren();
                boolean formFlag = true;
                if (vroot != null) {
                    for (Element e : vroot) {
                        String name = e.getChildText("name");
                        String fieldName = e.getChildText("fieldName");
                        String value = e.getChildText("value");

                        formFlag = filedList.contains(fieldName);
                        if(!formFlag && !"cws_id".equals(fieldName) && !"cws_status".equals(fieldName) && "!cws_flag".equals(fieldName)) {
                            break;
                        }

                        if (name.equals(WorkflowPredefineDb.COMB_COND_TYPE_FIELD)) {
                            if (value.startsWith("{$")) {
                                // 表单域选择或拉单时取主表中字段的值
                                String val = value.substring(2);
                                val = val.substring(0, val.length() - 1);
                                if (!list.contains(val)) {
                                    list.add(val);
                                }
                            }
                        }
                    }
                }
            } catch (JDOMException | IOException e1) {
                e1.printStackTrace();
            }
        }
        else {
            Pattern p = Pattern.compile(
                    "\\{\\$([@A-Z0-9a-z-_\\u4e00-\\u9fa5\\xa1-\\xff\\.]+)\\}", // 前为utf8中文范围，后为gb2312中文范围
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(filter);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String fieldName = m.group(1);
                // 脚本型条件，当拉单时，取主表单中的字段值
                boolean isLike = fieldName.startsWith("@"); // 包含
                if (isLike) {
                    fieldName = fieldName.substring(1);
                }

                if (fieldName.startsWith("request.") || "admin.dept".equalsIgnoreCase(fieldName) || "curUser".equalsIgnoreCase(fieldName)
                        || "curDate".equalsIgnoreCase(fieldName) || "curUserDept".equalsIgnoreCase(fieldName) || "curUserDeptAndChildren".equals(fieldName)
                        || "curUserRole".equalsIgnoreCase(fieldName) || "mainId".equalsIgnoreCase(fieldName)) {
                } else {
                    if (!list.contains(fieldName)) {
                        list.add(fieldName);
                    }
                }
            }

            p = Pattern.compile(
                    "\\{#([@A-Z0-9a-z-_\\u4e00-\\u9fa5\\xa1-\\xff\\.]+)\\}", // 前为utf8中文范围，后为gb2312中文范围
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            m = p.matcher(sb.toString());
            sb = new StringBuffer();
            while (m.find()) {
                String fieldName = m.group(1);
                // 脚本型条件，当拉单时，取主表单中的字段值
                boolean isLike = fieldName.startsWith("@"); // 包含
                if (isLike) {
                    fieldName = fieldName.substring(1);
                }

                if (fieldName.startsWith("request.") || "admin.dept".equalsIgnoreCase(fieldName) || "curUser".equalsIgnoreCase(fieldName)
                        || "curDate".equalsIgnoreCase(fieldName) || "curUserDept".equalsIgnoreCase(fieldName) || "curUserDeptAndChildren".equalsIgnoreCase(fieldName)
                        || "curUserRole".equalsIgnoreCase(fieldName) || "mainId".equalsIgnoreCase(fieldName)) {
                } else {
                    if (!list.contains(fieldName)) {
                        list.add(fieldName);
                    }
                }
            }
        }
        return list;
    }

    @Override
    public String[] parseFilter(HttpServletRequest request, String formCode, String filter) {
        String[] array = new String[2];

        FormDb fd = new FormDb();
        fd = fd.getFormDb(formCode);

        // 当打开module_list_sel.jsp页面时，会带入openerFormCode，如：表单域选择宏控件
        String openerFormCode = ParamUtil.get(request, "openerFormCode");
        FormDb fdOpener = null;
        if (!"".equals(openerFormCode)) {
            fdOpener = fd.getFormDb(openerFormCode);
        }

        Privilege pvg = new Privilege();

        // 将filter中的%n转换为换行符
        filter = ModuleUtil.decodeFilter(filter);

        if (filter.startsWith("<items>")) {
            List<String> filedList = new ArrayList<>();
            for (FormField ff : fd.getFields()) {
                filedList.add(ff.getName());
            }

            SAXBuilder parser = new SAXBuilder();
            org.jdom.Document doc;
            try {
                doc = parser.build(new InputSource(new StringReader(filter)));

                StringBuilder sb = new StringBuilder();

                Element root = doc.getRootElement();
                List<Element> vroot = root.getChildren();
                boolean formFlag = true;
                int i = 0;
                if (vroot != null) {
                    String lastLogical = "";
                    for (Element e : vroot) {
                        String name = e.getChildText("name");
                        String fieldName = e.getChildText("fieldName");
                        String fieldNameRaw = fieldName;
                        String op = e.getChildText("operator");

                        op = op.replaceAll("&lt;", "<");
                        op = op.replaceAll("&gt;", ">");

                        String logical = e.getChildText("logical");
                        String value = e.getChildText("value");
                        String firstBracket = e.getChildText("firstBracket");
                        String twoBracket = e.getChildText("twoBracket");

                        formFlag  = filedList.contains(fieldName);
                        if(!formFlag && !"cws_id".equals(fieldName) && !"cws_status".equals(fieldName)
                                && "!cws_flag".equals(fieldName) && !"cws_role".equals(fieldName) && !"cws_cur_user".equals(fieldName)) {
                            break;
                        }

                        // 加入别名，以免当以嵌套表为模块时，映射主表字段，出现cws_id ambiguous的问题
                        // 如果以{$开始，则可能为curUser或curUserDept
                        if ("cws_cur_user".equals(fieldName)) {
                            fieldName = StrUtil.sqlstr(SpringUtil.getUserName());
                        } else if (!"cws_role".equals(fieldName)){
                            fieldName = "t1." + fieldName;
                        }

                        if (null == firstBracket || "".equals(firstBracket)) {
                            firstBracket = "";
                        }
                        if (null == twoBracket || "".equals(twoBracket)) {
                            twoBracket = "";
                        }
                        if (name.equals(WorkflowPredefineDb.COMB_COND_TYPE_FIELD)) {
                            if (value.equals(ModuleUtil.FILTER_CUR_USER)) {
                                sb.append(firstBracket);
                                sb.append(fieldName);

                                value = pvg.getUser(request);

                                // 此处把组合条件中的{$curUser}处理掉，因为有like的情况
                                if ("like".equals(op)) {
                                    value = "%" + value + "%";
                                    sb.append(" " + op + " ");
                                }
                                else {
                                    sb.append(op);
                                }

                                sb.append(StrUtil.sqlstr(value));
                                sb.append(twoBracket);
                            }
                            else if (value.equals(ModuleUtil.FILTER_CUR_DATE)) {
                                sb.append(firstBracket);
                                sb.append(fieldName);

                                sb.append(op);
                                value = SQLFilter.getDateStr(DateUtil.format(new java.util.Date(), "yyyy-MM-dd"), "yyyy-MM-dd");

                                sb.append(value);
                                sb.append(twoBracket);
                            }
                            else if (value.equals(ModuleUtil.FILTER_CUR_USER_DEPT) || value.equals(ModuleUtil.FILTER_CUR_USER_DEPT_AND_CHILDREN) || value.equals(ModuleUtil.FILTER_CUR_USER_ROLE) || value.equals(ModuleUtil.FILTER_ADMIN_DEPT)) {
                                if ("=".equals(op)) {
                                    sb.append(firstBracket);
                                    sb.append(fieldName);
                                    sb.append(" in (" + value + ")");
                                    sb.append(twoBracket);
                                }
                                else {
                                    sb.append(firstBracket);
                                    sb.append(fieldName);
                                    sb.append(" not in (" + value + ")");
                                    sb.append(twoBracket);
                                }
                            }
                            else if (value.startsWith("{$")) {
                                // 拉单时取主表中字段的值
                                sb.append(firstBracket);
                                sb.append(fieldName);

                                String val = ParamUtil.get(request, fieldName);
                                if ("like".equals(op)) {
                                    val = "%" + val + "%";
                                    sb.append(" " + op + " ");
                                }
                                else {
                                    sb.append(op);
                                }

                                sb.append(value);
                                sb.append(twoBracket);
                            }
                            else {
                                sb.append(firstBracket);

                                if ("cws_role".equals(fieldName)) {
                                    String condStr = "";
                                    String val = StrUtil.sqlstr(value);
                                    UserCache userCache = SpringUtil.getBean(UserCache.class);
                                    List<Role> list = userCache.getRoles(SpringUtil.getUserName());
                                    RoleCache roleCache = SpringUtil.getBean(RoleCache.class);
                                    list.add(roleCache.getRole(ConstUtil.ROLE_MEMBER));
                                    if ("=".equals(op)) {
                                        StringBuilder stringBuilder = new StringBuilder();
                                        for (Role role : list) {
                                            StrUtil.concat(stringBuilder, " or ", val + "=" + StrUtil.sqlstr(role.getCode()) + ")");
                                        }
                                        condStr = stringBuilder.toString();
                                    } else if ("<>".equals(op)) {
                                        StringBuilder stringBuilder = new StringBuilder();
                                        for (Role role : list) {
                                            StrUtil.concat(stringBuilder, "and", val + "<>" + StrUtil.sqlstr(role.getCode()) + ")");
                                        }
                                        condStr = stringBuilder.toString();
                                    } else {
                                        Role roleVal = roleCache.getRole(value);
                                        if (roleVal == null) {
                                            DebugUtil.e(ModuleUtilService.class, "parseFilter 角色", value + " 不存在");
                                            continue;
                                        } else {
                                            StringBuilder stringBuilder = new StringBuilder();
                                            int order = roleVal.getOrders();
                                            for (Role role : list) {
                                                StrUtil.concat(stringBuilder, " or ", role.getOrders() + op + order);
                                            }
                                            condStr = stringBuilder.toString();
                                        }
                                    }

                                    sb.append(condStr);
                                } else {
                                    sb.append(fieldName);
                                    sb.append(op);
                                    if ("cws_status".equals(fieldNameRaw) || "cws_id".equals(fieldNameRaw) || "cws_flag".equals(fieldNameRaw)) {
                                        sb.append(value);
                                    } else {
                                        sb.append(StrUtil.sqlstr(value));
                                    }
                                }

                                sb.append(twoBracket);
                            }
                        }

                        // 去除最后一个逻辑判断
                        if ( i!=vroot.size()-1 ) {
                            sb.append(" " + logical + " ");
                            lastLogical = logical;
                        }

                        i++;
                    }
                    String tempCond = sb.toString();
                    //校验括弧对称性
                    //boolean flag = checkComCond(tempCond);

                    // 如果配置了条件
                    if (!"".equals(tempCond)) {
                        String script = sb.toString();
                        int p = script.lastIndexOf(" " + lastLogical + " ");

                        // LogUtil.getLog(ModuleUtil.class).info("filter script=" + script);
                        if (p!=-1) {
                            script = script.substring(0, p);
                        }
                        // LogUtil.getLog(ModuleUtil.class).info("filter script2=" + script);

                        filter = tempCond;
                    }
                }
            } catch (JDOMException | IOException e1) {
                e1.printStackTrace();
            }
        }

        // 判断是否来自手机端
        boolean isMobile = false;
        UserAgent ua = null;
        // 异步执行的线程中可能会为null
        if (request.getHeader("User-Agent") != null) {
            ua = UserAgent.parseUserAgentString(request.getHeader("User-Agent"));
            OperatingSystem os = ua.getOperatingSystem();
            if (DeviceType.MOBILE.equals(os.getDeviceType())) {
                isMobile = true;
            }
        }

        // 如果是来自于流程中通过nest_sheet_sel.jsp打开嵌套表，则参数是通过macro.js中openNestSheet生成json放在parentFields中
        boolean isParentFields = false;
        JSONObject parentJson = null;
        String parentFieldsVal = "";
        try {
            parentFieldsVal = new String(StrUtil.getNullStr(request.getParameter("parentFields")).getBytes("ISO-8859-1"),"UTF-8");
        } catch (UnsupportedEncodingException e) {
            LogUtil.getLog(getClass()).error(e);
        }
        if (!"".equals(parentFieldsVal)) {
            isParentFields = true;
            parentJson = JSONObject.parseObject(parentFieldsVal);
        }
        // 从request取得过滤条件中的参数值，组装为url字符串，以便于分页
        StringBuffer urlStrBuf = new StringBuffer();

        Pattern p = Pattern.compile(
                "\\{\\$([@A-Z0-9a-z-_\\u4e00-\\u9fa5\\xa1-\\xff\\.]+)\\}", // 前为utf8中文范围，后为gb2312中文范围
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(filter);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String fieldName = m.group(1);
            // 脚本型条件，当拉单时，取主表单中的字段值
            boolean isLike = fieldName.startsWith("@"); // 包含
            if (isLike) {
                fieldName = fieldName.substring(1);
            }

            String val = "";
            if (fieldName.startsWith("request.")) {
                String key = fieldName.substring("request.".length());
                val = ParamUtil.get(request, key);
                if (!isLike) {
                    if (!"id".equalsIgnoreCase(key)) {
                        val = StrUtil.sqlstr(val);
                    }
                }
                else {
                    val = StrUtil.sqlstr("%" + val + "%");
                }
            }
            else if ("admin.dept".equalsIgnoreCase(fieldName)) {
                try {
                    Iterator ir = pvg.getUserAdminDepts(request).iterator();
                    while (ir.hasNext()) {
                        DeptDb dd = (DeptDb)ir.next();
                        if ("".equals(val)) {
                            val = StrUtil.sqlstr(dd.getCode());
                        }
                        else {
                            val += "," + StrUtil.sqlstr(dd.getCode());
                        }
                    }
                } catch (ErrMsgException e) {
                    LogUtil.getLog(getClass()).error(e);
                }
            }
            else if ("curUser".equalsIgnoreCase(fieldName)) {
                if (!isLike) {
                    val = StrUtil.sqlstr(pvg.getUser(request));
                }
                else {
                    val = StrUtil.sqlstr("%" + pvg.getUser(request) + "%");
                }
            }
            else if ("curDate".equalsIgnoreCase(fieldName)) {
                val = SQLFilter.getDateStr(DateUtil.format(new java.util.Date(), "yyyy-MM-dd"), "yyyy-MM-dd");
            }
            else if ("curYear".equalsIgnoreCase(fieldName)) {
                val = "'" + DateUtil.getYear(new Date()) + "'";
            }
            else if ("curMonth".equalsIgnoreCase(fieldName)) {
                val = "'" + DateUtil.getMonth(new Date()) + "'";
            }
            else if ("curUserDept".equalsIgnoreCase(fieldName)) { // 当前用户所在的部门
                DeptUserDb dud = new DeptUserDb();
                Vector<DeptDb> v = dud.getDeptsOfUser(pvg.getUser(request));
                if (v.size()>0) {
                    for (DeptDb dd : v) {
                        if ("".equals(val)) {
                            val = StrUtil.sqlstr(dd.getCode());
                        } else {
                            val += "," + StrUtil.sqlstr(dd.getCode());
                        }
                    }
                }
                else {
                    val = "";
                }
            }
            else if ("curUserDeptAndChildren".equalsIgnoreCase(fieldName)) { // 当前用户所在的部门及其子部门
                DeptUserDb dud = new DeptUserDb();
                Vector<DeptDb> v = dud.getDeptsOfUser(pvg.getUser(request));
                if (v.size()>0) {
                    for (DeptDb dd : v) {
                        if ("".equals(val)) {
                            val = StrUtil.sqlstr(dd.getCode());
                        } else {
                            val += "," + StrUtil.sqlstr(dd.getCode());
                        }

                        Vector<DeptDb> children = new Vector<>();
                        try {
                            dd.getAllChild(children, dd);
                        } catch (ErrMsgException e) {
                            LogUtil.getLog(getClass()).error(e);
                        }
                        for (DeptDb child : children) {
                            val += "," + StrUtil.sqlstr(child.getCode());
                        }
                    }
                }
                else {
                    val = "";
                }
            }
            else if ("curUserRole".equalsIgnoreCase(fieldName)) {
                UserDb ud = new UserDb();
                ud = ud.getUserDb(pvg.getUser(request));
                RoleDb[] ary = ud.getRoles();
                if (ary!=null && ary.length>0) {
                    for (RoleDb roleDb : ary) {
                        if ("".equals(val)) {
                            val = StrUtil.sqlstr(roleDb.getCode());
                        } else {
                            val += "," + StrUtil.sqlstr(roleDb.getCode());
                        }
                    }
                }
                else {
                    val = "";
                }
            }
            else if ("mainId".equalsIgnoreCase(fieldName)) {
                val = ParamUtil.get(request, "mainId");
                if ("".equals(val)) {
                    // 手机端传过来的是parentId
                    val = ParamUtil.get(request, "parentId");
                }
            }
            else {
                if (isMobile) {
                    try {
                        // 如果是来自于流程中通过nest_sheet_sel.jsp打开嵌套表，则参数是通过macro.js中openNestSheet生成json放在parentFields中
                        if (isParentFields) {
                            if (parentJson.containsKey(fieldName)) {
                                val = parentJson.getString(fieldName);
                            }
                            else {
                                val = "";
                            }
                        }
                        else {
                            val = new String(StrUtil.getNullStr(request.getParameter(fieldName)).getBytes("ISO-8859-1"), "UTF-8");
                        }
                    } catch (UnsupportedEncodingException e) {
                        LogUtil.getLog(getClass()).error(e);
                    }
                }
                else {
                    if (ua != null && ua.getBrowser().equals(Browser.CHROME)) {
                        try {
                            val = new String(StrUtil.getNullStr(request.getParameter(fieldName)).getBytes("ISO-8859-1"),"UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            LogUtil.getLog(getClass()).error(e);
                        }
                    }
                    else {
                        val = ParamUtil.get(request, fieldName);
                    }
                }

                urlStrBuf = StrUtil.concat(urlStrBuf, "&", fieldName + "=" + val);

                if (!isLike) {
                    if (fdOpener!=null && fdOpener.isLoaded()) {
                        FormField ff = fdOpener.getFormField(fieldName);
                        if (ff != null) {
                            if (!FormField.isNumeric(ff.getFieldType())) {
                                val = StrUtil.sqlstr(val);
                            }
                        }
                        else {
                            val = StrUtil.sqlstr(val);
                        }
                    }
                    else {
                        val = StrUtil.sqlstr(val);
                    }
                }
                else {
                    val = StrUtil.sqlstr("%" + val + "%");
                }
            }

            m.appendReplacement(sb, val);
        }
        m.appendTail(sb);

        // {#fieldName}，展开后不带有单引号
        p = Pattern.compile(
                "\\{#([@A-Z0-9a-z-_\\u4e00-\\u9fa5\\xa1-\\xff\\.]+)\\}", // 前为utf8中文范围，后为gb2312中文范围
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        m = p.matcher(sb.toString());
        sb = new StringBuffer();
        while (m.find()) {
            String fieldName = m.group(1);
            // 脚本型条件，当拉单时，取主表单中的字段值
            boolean isLike = fieldName.startsWith("@"); // 包含
            if (isLike) {
                fieldName = fieldName.substring(1);
            }

            String val = "";
            if (fieldName.startsWith("request.")) {
                String key = fieldName.substring("request.".length());
                val = ParamUtil.get(request, key);
                if (!isLike) {
                    val = "%" + val + "%";
                }
            }
            else if ("admin.dept".equalsIgnoreCase(fieldName)) {
                try {
                    Iterator ir = pvg.getUserAdminDepts(request).iterator();
                    while (ir.hasNext()) {
                        DeptDb dd = (DeptDb)ir.next();
                        if ("".equals(val)) {
                            val = StrUtil.sqlstr(dd.getCode());
                        }
                        else {
                            val += "," + StrUtil.sqlstr(dd.getCode());
                        }
                    }
                } catch (ErrMsgException e) {
                    LogUtil.getLog(getClass()).error(e);
                }
            }
            else if ("curUser".equalsIgnoreCase(fieldName)) {
                if (!isLike) {
                    val = pvg.getUser(request);
                }
                else {
                    val = "%" + pvg.getUser(request) + "%";
                }
            }
            else if ("curDate".equalsIgnoreCase(fieldName)) {
                val = SQLFilter.getDateStr(DateUtil.format(new java.util.Date(), "yyyy-MM-dd"), "yyyy-MM-dd");
            }
            else if ("curYear".equalsIgnoreCase(fieldName)) {
                val = String.valueOf(DateUtil.getYear(new Date()));
            }
            else if ("curMonth".equalsIgnoreCase(fieldName)) {
                val = String.valueOf(DateUtil.getMonth(new Date()) + 1);
            }
            else if ("curUserDept".equalsIgnoreCase(fieldName)) { // 当前用户所在的部门
                DeptUserDb dud = new DeptUserDb();
                Vector v = dud.getDeptsOfUser(pvg.getUser(request));
                if (v.size()>0) {
                    Iterator ir = v.iterator();
                    while (ir.hasNext()) {
                        DeptDb dd = (DeptDb)ir.next();
                        if ("".equals(val)) {
                            val = StrUtil.sqlstr(dd.getCode());
                        }
                        else {
                            val += "," + StrUtil.sqlstr(dd.getCode());
                        }
                    }
                } else {
                    val = "";
                }
            }
            else if ("curUserDeptAndChildren".equalsIgnoreCase(fieldName)) { // 当前用户所在的部门及其子部门
                DeptUserDb dud = new DeptUserDb();
                Vector<DeptDb> v = dud.getDeptsOfUser(pvg.getUser(request));
                if (v.size()>0) {
                    for (DeptDb dd : v) {
                        if ("".equals(val)) {
                            val = StrUtil.sqlstr(dd.getCode());
                        } else {
                            val += "," + StrUtil.sqlstr(dd.getCode());
                        }

                        Vector<DeptDb> children = new Vector<>();
                        try {
                            dd.getAllChild(children, dd);
                        } catch (ErrMsgException e) {
                            LogUtil.getLog(getClass()).error(e);
                        }
                        for (DeptDb child : children) {
                            val += "," + StrUtil.sqlstr(child.getCode());
                        }
                    }
                } else {
                    val = "";
                }
            }
            else if ("curUserRole".equalsIgnoreCase(fieldName)) {
                UserDb ud = new UserDb();
                ud = ud.getUserDb(pvg.getUser(request));
                RoleDb[] ary = ud.getRoles();
                if (ary!=null && ary.length>0) {
                    for (int i=0; i<ary.length; i++) {
                        if ("".equals(val)) {
                            val = StrUtil.sqlstr(ary[i].getCode());
                        } else {
                            val += "," + StrUtil.sqlstr(ary[i].getCode());
                        }
                    }
                } else {
                    val = "";
                }
            }
            else if ("mainId".equalsIgnoreCase(fieldName)) {
                val = ParamUtil.get(request, "mainId");
            }
            else {
                if (isMobile) {
                    try {
                        // 如果是来自于流程中通过nest_sheet_sel.jsp打开嵌套表，则参数是通过macro.js中openNestSheet生成json放在parentFields中
                        if (isParentFields) {
                            if (parentJson.containsKey(fieldName)) {
                                val = parentJson.getString(fieldName);
                            }
                            else {
                                val = "";
                            }
                        }
                        else {
                            val = new String(StrUtil.getNullStr(request.getParameter(fieldName)).getBytes("ISO-8859-1"), "UTF-8");
                        }
                    } catch (UnsupportedEncodingException e) {
                        LogUtil.getLog(getClass()).error(e);
                    }
                }
                else {
                    if (ua !=null && ua.getBrowser().equals(Browser.CHROME)) {
                        try {
                            val = new String(StrUtil.getNullStr(request.getParameter(fieldName)).getBytes("ISO-8859-1"),"UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            LogUtil.getLog(getClass()).error(e);
                        }
                    }
                    else {
                        val = ParamUtil.get(request, fieldName);
                    }
                }

                urlStrBuf = StrUtil.concat(urlStrBuf, "&", fieldName + "=" + val);

                if (isLike) {
                    val = "%" + val + "%";
                }
            }

            m.appendReplacement(sb, val);
        }
        m.appendTail(sb);

        String ret = sb.toString();

        boolean isScript = filter.contains("ret=") || filter.contains("ret ");
        if (isScript) {
            BSHShell bs = new BSHShell();
            try {
                sb = new StringBuffer();
                // BeanShellUtil.setFieldsValue(fdao, sb);

                // 赋值当前用户
                sb.append("userName=\"" + pvg.getUser(request) + "\";");
                // sb.append("fdao=\"" + fdao + "\";");

                bs.set("request", request);
                // bsh.set("fileUpload", fu);

                bs.eval(sb.toString());

                bs.eval(ret);

                Object obj = bs.get("ret");
                if (obj != null) {
                    ret = (String) obj;
                } else {
                    ret = "1=1";
                    String errMsg = (String) bs.get("errMsg");
                    DebugUtil.e(getClass(), "parseFilter bsh errMsg", errMsg);
                }
            } catch (ErrMsgException e) {
                LogUtil.getLog(getClass()).error(e);
            }
        }

        array[0] = ret;
        array[1] = urlStrBuf.toString();

        return array;
    }

    @Override
    public String parseConds(HttpServletRequest request, IFormDAO ifdao, String conds) {
        return null;
    }

    @Override
    public String doGetViewJS(HttpServletRequest request, FormDb fd, IFormDAO fdao, String userName, boolean isForReport) {
        return null;
    }

    @Override
    public String doGetViewJSMobile(HttpServletRequest request, FormDb fd, FormDAO fdao, String userName, boolean isForReport) {
        return null;
    }

    @Override
    public boolean evalCheckSetupRule(HttpServletRequest request, String userName, JSONArray ary, IFormDAO fdao, List filedList, FileUpload fu) throws JSONException, ErrMsgException {
        return false;
    }

    @Override
    public com.alibaba.fastjson.JSONArray getConditions(HttpServletRequest request, ModuleSetupDb msd, ArrayList<String> dateFieldNamelist) {
        return null;
    }
}
