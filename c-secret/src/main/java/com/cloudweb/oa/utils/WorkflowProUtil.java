package com.cloudweb.oa.utils;

import cn.js.fan.db.ResultIterator;
import cn.js.fan.db.SQLFilter;
import cn.js.fan.util.*;
import com.alibaba.fastjson.JSONArray;
import com.cloudweb.oa.api.IWorkflowUtil;
import com.cloudweb.oa.cache.RoleCache;
import com.cloudweb.oa.cache.UserCache;
import com.cloudweb.oa.entity.Role;
import com.cloudwebsoft.framework.db.JdbcTemplate;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.flow.*;
import com.redmoon.oa.pvg.Privilege;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.visual.ModuleUtil;
import org.htmlparser.Node;
import org.htmlparser.Parser;
import org.htmlparser.filters.HasAttributeFilter;
import org.htmlparser.tags.InputTag;
import org.htmlparser.tags.SelectTag;
import org.htmlparser.tags.TextareaTag;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.StringReader;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WorkflowProUtil implements IWorkflowUtil {

    /**
     * 取得用来控制是否显示的脚本
     * @param request HttpServletRequest
     * @param fd FormDb
     * @param fdao FormDAO
     * @param userName String
     * @param isForReport 是否用于查看流程时
     * @return String
     */
    @Override
    public String doGetViewJSMobile(HttpServletRequest request, FormDb fd, FormDAO fdao, String userName, boolean isForReport) {
        return "console.info('本版本没有显示规则功能');";
    }

    /**
     * 取得用来控制是否显示的脚本（显示规则）
     * @param request HttpServletRequest
     * @param wa WorkflowActionDb
     * @param fd FormDb
     * @param fdao FormDAO
     * @param userName String
     * @param isForReport 是否用于查看流程时
     * @return String
     */
    public String doGetViewJS(HttpServletRequest request, WorkflowActionDb wa, FormDb fd, FormDAO fdao, String userName, boolean isForReport) {
        return "<script>console.info('本版本没有显示规则功能')</script>";
    }

    /**
     * 回写至表单
     * @param wf
     * @param lf
     * @param nodeFinish
     * @throws SQLException
     * @throws JSONException
     */
    @Override
    public void writeBack(WorkflowDb wf, Leaf lf, Element nodeFinish) throws SQLException, JSONException  {
        String writeBackForm = nodeFinish.getChildText("writeBackForm");
        int writeBackType = StrUtil.toInt(StrUtil.getNullStr(nodeFinish.getChildText("writeBackType")), WorkflowPredefineDb.WRITE_BACK_UPDATE);

        // 更新字段
        if (writeBackType==WorkflowPredefineDb.WRITE_BACK_UPDATE || writeBackType==WorkflowPredefineDb.WRITE_BACK_UPDATE_INSERT) {
            FormDb fd = new FormDb();
            fd = fd.getFormDb(writeBackForm);

            StringBuilder sb = new StringBuilder("");
            StringBuilder cond = new StringBuilder("");
            List writeBackFieldList = nodeFinish.getChildren("writeBackField");
            Element condition = nodeFinish.getChild("condition");
            List conditionFieldList = null;
            Iterator conditionIr = null;
            if (condition!=null) {
                conditionFieldList = condition.getChildren("conditionField");
            }
            if (conditionFieldList!=null) {
                conditionIr = conditionFieldList.iterator();
            }
            // 拼接条件字段
            while (conditionIr!=null&&conditionIr.hasNext()) {
                Element conditionField = (Element)conditionIr.next();
                String conditionFieldName = conditionField.getAttributeValue("fieldName");
                FormField ff = fd.getFormField(conditionFieldName);
                String beginBracket = conditionField.getAttributeValue("beginBracket");
                String endBracket = conditionField.getAttributeValue("endBracket");
                String compare = conditionField.getChildText("compare");
                String compareVal = conditionField.getChildText("compareVal");
                if (ff!=null) {
                    int fieldType = ff.getFieldType();
                    if (fieldType==FormField.FIELD_TYPE_TEXT||fieldType==FormField.FIELD_TYPE_VARCHAR||fieldType==FormField.FIELD_TYPE_DATE) {
                        compareVal = SQLFilter.sqlstr(compareVal);
                    }
                }
                String logical = conditionField.getChildText("logical");
                cond.append(" ").append(beginBracket).append(" ").append(conditionFieldName).append(" ").append(compare).append(" ").append(compareVal).append(" ").append(endBracket).append(" ").append(logical);
            }
            String condString = cond.toString();

            // 对所设置的回写字段赋值
            Iterator writeBackFieldIr = null;
            if (writeBackFieldList!=null) {
                writeBackFieldIr= writeBackFieldList.iterator();
            }
            while (writeBackFieldIr!=null && writeBackFieldIr.hasNext()) {        // 拼接回写字段
                Element writeBackField = (Element)writeBackFieldIr.next();
                String writeBackFieldStr = writeBackField.getAttributeValue("fieldName");
                String writeBackMathStr = writeBackField.getChildText("writeBackMath");
                sb.append(writeBackFieldStr).append(" = ").append(writeBackMathStr).append(",");
            }
            String setField = sb.toString();
            if (!"".equals(setField)){
                WorkflowMgr wm = new WorkflowMgr();
                setField = setField.substring(0, setField.lastIndexOf(","));
                String sql = "update ft_" + writeBackForm + " set " + setField;
                if (!"".equals(condString)){
                    condString = condString.substring(0, condString.lastIndexOf(" "));
                    sql += " where "+condString;
                }
                sql = wm.parseAndSetFieldValue(writeBackForm, sql);            //过滤{}，用于回写值
                // 如果条件中带有嵌套表中的字段
                if (!condString.contains("{$nest.")) {
                    sql = wm.parseAndSetMainFieldValue(lf.getFormCode(), sql, wf.getId());     //过滤{$}，用于条件值
                    JdbcTemplate jt = new JdbcTemplate();
                    jt.executeUpdate(sql);
                }
                else {
                    // 取得当前主表中的子表中对应字段的值，并赋予
                    List ary = wm.parseAndSetNestFieldValue(lf.getFormCode(), sql, wf.getId());     //过滤{$}，用于条件值
                    if (ary.size()==0) {
                        LogUtil.getLog(getClass()).error("parseAndSetNestFieldValue error");
                    }
                    else {
                        JdbcTemplate jt = new JdbcTemplate();
                        for (Object o : ary) {
                            sql = wm.parseAndSetMainFieldValue(lf.getFormCode(), (String) o, wf.getId());     //过滤{$}，用于条件值
                            jt.addBatch(sql);
                        }
                        jt.executeBatch();
                    }
                }
            }
        }

        // 插入数据处理
        if (writeBackType==WorkflowPredefineDb.WRITE_BACK_INSERT || writeBackType==WorkflowPredefineDb.WRITE_BACK_UPDATE_INSERT) {
            WorkflowMgr wMgr = new WorkflowMgr();

            String primaryKey = nodeFinish.getChildText("primaryKey");
            boolean hasPrimaryKey = !"".equals(primaryKey) && !"empty".equals(primaryKey);
            String primaryKeyVal = "";

            String insertFields = nodeFinish.getChildText("insertFields");
            if (insertFields!=null) {
                boolean hasNest = false; // 是否取自嵌套表，如果是，则需插入多条数据
                JSONObject insFields = new JSONObject(insertFields);
                StringBuffer fields = new StringBuffer();
                StringBuffer values = new StringBuffer();
                Iterator irKeys = insFields.keys();
                while (irKeys.hasNext()) {
                    String fieldName = (String)irKeys.next();

                    StrUtil.concat(fields, ",", fieldName);
                    String val = insFields.getString(fieldName);
                    if (val.contains("{$nest.")) {
                        hasNest = true;
                    }

                    // 得到主键字段被赋予的值
                    if (hasPrimaryKey) {
                        if (primaryKey.equals(fieldName)) {
                            primaryKeyVal = val;
                        }
                    }

                    if ("".equals(val)) {
                        // 日期型如果预置为空字符串将变为null
                        StrUtil.concat(values, ",", "null");
                    }
                    else {
                        StrUtil.concat(values, ",", val);
                    }
                }

                if (!hasNest) {
                    // 判断主键字段是否存在
                    boolean isPrimaryKeyExist = false;
                    if (hasPrimaryKey) {
                        String sql = "select id from ft_" + writeBackForm + " where " + primaryKey + "=" + primaryKeyVal;
                        JdbcTemplate jt = new JdbcTemplate();
                        ResultIterator ri = jt.executeQuery(sql, 1, 1);
                        if (ri.size()>0) {
                            isPrimaryKeyExist = true;
                        }
                    }
                    // 如果主键字段未设置，或者主键字段不存在则插入数据
                    if (!isPrimaryKeyExist) {
                        String flowTypeCode = String.valueOf(System.currentTimeMillis());
                        String sql = "insert into ft_" + writeBackForm;
                        sql += " (unit_code, cws_status, flowTypeCode, " + fields + ") values ('root', 1, " + StrUtil.sqlstr(flowTypeCode) + "," + values + ")";
                        sql = wMgr.parseAndSetMainFieldValue(lf.getFormCode(), sql, wf.getId());     //过滤{$}，用于条件值
                        JdbcTemplate jt = new JdbcTemplate();
                        jt.executeUpdate(sql);
                    }
                }
                else {
                    // 如果字段中有嵌套表
                    boolean isPrimaryKeyValHasNest = false;
                    if (hasPrimaryKey) {
                        if (primaryKeyVal.contains("{$nest.")) {
                            isPrimaryKeyValHasNest = true;
                        }
                    }
                    List aryVal = null;
                    if (isPrimaryKeyValHasNest) {
                        aryVal = wMgr.parseAndSetNestFieldValue(lf.getFormCode(), primaryKeyVal, wf.getId());
                    }
                    List ary = wMgr.parseAndSetNestFieldValue(lf.getFormCode(), values.toString(), wf.getId());     //过滤{$}，用于条件值
                    if (ary.size()==0) {
                        LogUtil.getLog(getClass()).error("changeStatus3:parseAndSetNestFieldValue error");
                    }
                    else {
                        JdbcTemplate jtBatch = new JdbcTemplate();
                        JdbcTemplate jt = new JdbcTemplate();
                        jt.setAutoClose(false);
                        try {
                            boolean isAddBatch = false;
                            for (int i=0; i<ary.size(); i++) {
                                // 判断主键字段是否存在
                                boolean isPrimaryKeyExist = false;
                                if (hasPrimaryKey) {
                                    String sql;
                                    if (isPrimaryKeyValHasNest) {
                                        sql = "select id from ft_" + writeBackForm + " where " + primaryKey + "=" + aryVal.get(i);
                                    }
                                    else {
                                        sql = "select id from ft_" + writeBackForm + " where " + primaryKey + "=" + primaryKeyVal;
                                    }
                                    sql = wMgr.parseAndSetMainFieldValue(lf.getFormCode(), sql, wf.getId());
                                    ResultIterator ri = jt.executeQuery(sql, 1, 1);
                                    if (ri.size()>0) {
                                        isPrimaryKeyExist = true;
                                    }
                                }

                                if (!isPrimaryKeyExist) {
                                    String flowTypeCode = String.valueOf(System.currentTimeMillis());
                                    String sql = "insert into ft_" + writeBackForm;
                                    sql += " (unit_code, cws_status, flowTypeCode, " + fields + ") values ('root', 1, " + StrUtil.sqlstr(flowTypeCode) + "," + ary.get(i) + ")";
                                    sql = wMgr.parseAndSetMainFieldValue(lf.getFormCode(), sql, wf.getId());
                                    jtBatch.addBatch(sql);

                                    isAddBatch = true;
                                }
                            }

                            if (isAddBatch) {
                                jtBatch.executeBatch();
                            }
                        }
                        finally {
                            jt.close();
                            jtBatch.close();
                        }
                    }
                }
            }
        }
    }

    /**
     * 回写至数据库
     * @param wf
     * @param lf
     * @param nodeFinish
     * @throws SQLException
     */
    @Override
    public void writeBackDb(WorkflowDb wf, Leaf lf, Element nodeFinish) throws SQLException {
        String dbSource = nodeFinish.getChildText("dbSource");
        String writeBackTable = nodeFinish.getChildText("writeBackForm");
        StringBuilder sb = new StringBuilder ("");                       				 //用于拼接回写字段
        StringBuilder cond = new StringBuilder("");                       			//用于拼接条件字段
        List writeBackFieldList = nodeFinish.getChildren("writeBackField");
        Element condition = nodeFinish.getChild("condition");
        List conditionFieldList = null;
        Iterator conditionIr = null;
        if (condition!=null) {
            conditionFieldList = condition.getChildren("conditionField");
        }
        if (conditionFieldList!=null) {
            conditionIr = conditionFieldList.iterator();
        }
        while (conditionIr!=null&&conditionIr.hasNext()) {                         //解析条件字段
            Element conditionField = (Element)conditionIr.next();
            String conditionFieldName = conditionField.getAttributeValue("fieldName");

            int fieldType = WorkflowUtil.getColumnType(dbSource, writeBackTable, conditionFieldName);

            String beginBracket = conditionField.getAttributeValue("beginBracket");
            String endBracket = conditionField.getAttributeValue("endBracket");
            String compare = conditionField.getChildText("compare");
            String compareVal = conditionField.getChildText("compareVal");

            if (fieldType==FormField.FIELD_TYPE_TEXT||fieldType==FormField.FIELD_TYPE_VARCHAR||fieldType==FormField.FIELD_TYPE_DATE) {
                compareVal = SQLFilter.sqlstr(compareVal);
            }

            String logical = conditionField.getChildText("logical");
            cond.append(" ").append(beginBracket).append(" ").append(conditionFieldName).append(" ").append(compare).append(" ").append(compareVal).append(" ").append(endBracket).append(" ").append(logical);
        }
        String condString = cond.toString();
        Iterator writeBackFieldIr = null;
        if (writeBackFieldList!=null) {
            writeBackFieldIr= writeBackFieldList.iterator();
        }
        while (writeBackFieldIr!=null&&writeBackFieldIr.hasNext()) {            //解析回写字段
            Element writeBackField = (Element)writeBackFieldIr.next();
            String writeBackFieldStr = writeBackField.getAttributeValue("fieldName");
            String writeBackMathStr = writeBackField.getChildText("writeBackMath");
            sb.append(writeBackFieldStr).append(" = ").append(writeBackMathStr).append(",");
        }
        String setField = sb.toString();
        if (!"".equals(setField)) {
            WorkflowMgr wMgr = new WorkflowMgr();
            setField = setField.substring(0, setField.lastIndexOf(","));
            String sql = "update " + writeBackTable + " set " + setField;
            if (!"".equals(condString)) {
                condString = condString.substring(0, condString.lastIndexOf(" "));
                sql += " where "+condString;
            }
            sql = wMgr.parseWriteBackDbField(sql);            //过滤{}，用于回写值
            if (!condString.contains("{$nest.")) {
                sql = wMgr.parseAndSetMainFieldValue(lf.getFormCode(), sql, wf.getId());     //过滤{$}，用于条件值
                JdbcTemplate jt = new JdbcTemplate(dbSource);
                jt.executeUpdate(sql);
            }
            else {
                // 取得当前主表中的子表中对应字段的值，并赋予
                List ary = wMgr.parseAndSetNestFieldValue(lf.getFormCode(), sql, wf.getId());     //过滤{$}，用于条件值
                if (ary.size()==0) {
                    LogUtil.getLog(getClass()).error("parseAndSetNestFieldValue error");
                }
                else {
                    JdbcTemplate jt = new JdbcTemplate(dbSource);
                    for (Object o : ary) {
                        sql = wMgr.parseAndSetMainFieldValue(lf.getFormCode(), (String) o, wf.getId());     //过滤{$}，用于条件值
                        jt.addBatch(sql);
                    }
                    jt.executeBatch();
                }
            }
        }
    }

    @Override
    public String makeViewJSMobile(FormDb fd, JSONArray ifArr, com.alibaba.fastjson.JSONObject json, Map<String, List<String>> fieldChangeFuncMap, int k) throws JSONException {
        return null;
    }

    @Override
    public String makeViewBindChangeEvent(FormDb fd, Map<String, List<String>> fieldChangeFuncMap) {
        return null;
    }

    @Override
    public String makeViewBindChangeEventMobile(FormDb fd, Map<String, List<String>> fieldChangeFuncMap) {
        return null;
    }

}
