package com.cloudweb.oa.service.impl;

import cn.js.fan.db.ResultIterator;
import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.StrUtil;
import com.cloudweb.oa.api.IFormulaUtil;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudwebsoft.framework.db.JdbcTemplate;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.base.ISQLGenerator;
import com.redmoon.oa.flow.FormField;
import com.redmoon.oa.flow.macroctl.MacroCtlMgr;
import com.redmoon.oa.flow.macroctl.MacroCtlUnit;
import com.redmoon.oa.visual.Formula;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.Vector;

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
@Component("MySQLSQLGenerator")
public class MySQLSQLGenerator implements ISQLGenerator {
    public static final String[] keywords;

    // rank是MySQL版本8.0.2中定义的MySQL保留字
    static {
        keywords = new String[]{
                /*"SUM", */"ADDDATE", "SUBDATE", // 计算控件
                "ADD", "ALL", "ALTER",
                "ANALYZE", "AND", "AS",
                "ASC", "AUTO_INCREMENT", "BDB",
                "BEFORE", "BERKELEYDB", "BETWEEN",
                "BIGINT", "BINARY", "BLOB",
                "BOTH", "BTREE", "BY",
                "CASCADE", "CASE", "CHANGE",
                "CHAR", "CHARACTER", "CHECK",
                "COLLATE", "COLUMN", "COLUMNS",
                "CONSTRAINT", "CREATE", "CROSS",
                "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP",
                "DATABASE", "DATABASES", "DAY_HOUR",
                "DAY_MINUTE", "DAY_SECOND", "DEC",
                "DECIMAL", "DEFAULT", "DELAYED",
                "DELETE", "DESC", "DESCRIBE",
                "DISTINCT", "DISTINCTROW", "DIV",
                "DOUBLE", "DROP", "ELSE",
                "ENCLOSED", "ERRORS", "ESCAPED",
                "EXISTS", "EXPLAIN", "FALSE",
                "FIELDS", "FLOAT", "FOR",
                "FORCE", "FOREIGN", "FROM",
                "FULLTEXT", "FUNCTION", "GRANT",
                "GROUP", "HASH", "HAVING",
                "HIGH_PRIORITY", "HOUR_MINUTE", "HOUR_SECOND",
                "IF", "IGNORE", "IN",
                "INDEX", "INFILE", "INNER",
                "INNODB", "INSERT", "INT",
                "INTEGER", "INTERVAL", "INTO",
                "IS", "JOIN", "KEY",
                "KEYS", "KILL", "LEADING",
                "LEFT", "LIKE", "LIMIT",
                "LINES", "LOAD", "LOCALTIME",
                "LOCALTIMESTAMP", "LOCK", "LONG",
                "LONGBLOB", "LONGTEXT", "LOW_PRIORITY",
                "MASTER_SERVER_ID", "MATCH", "MEDIUMBLOB",
                "MEDIUMINT", "MEDIUMTEXT", "MIDDLEINT ",
                "MINUTE_SECOND", "MOD", "MRG_MYISAM",
                "NATURAL", "NOT", "NULL",
                "NUMERIC", "ON", "OPTIMIZE",
                "OPTION", "OPTIONALLY", "OR",
                "ORDER", "OUTER", "OUTFILE",
                "PRECISION", "PRIMARY", "PRIVILEGES",
                "PROCEDURE", "PURGE", "READ",
                "REAL", "REFERENCES", "REGEXP",
                "RENAME", "REPLACE", "REQUIRE",
                "RESTRICT", "RETURNS", "REVOKE",
                "RIGHT", "RLIKE", "RTREE",
                "SELECT", "SET", "SHOW",
                "SMALLINT", "SOME", "SONAME",
                "SPATIAL", "SQL_BIG_RESULT", "SQL_CALC_FOUND_ROWS ",
                "SQL_SMALL_RESULT", "SSL", "STARTING ",
                "STRAIGHT_JOIN", "STRIPED", "TABLE ",
                "TABLES", "TERMINATED", "THEN",
                "TINYBLOB", "TINYINT", "TINYTEXT ",
                "TO", "TRAILING", "TRUE",
                "TYPES", "UNION", "UNIQUE",
                "UNLOCK", "UNSIGNED", "UPDATE",
                "USAGE", "USE", "USER_RESOURCES ",
                "USING", "VALUES", "VARBINARY",
                "VARCHAR", "VARCHARACTER", "VARYING ",
                "WARNINGS", "WHEN", "WHERE",
                "WITH", "WRITE", "XOR",
                "YEAR_MONTH", "ZEROFILL", "OTHER", "RANK"
        };
    }

    public MySQLSQLGenerator() {
        super();
    }

    /**
     * 创建增加表单的SQL语句
     *
     * @param fields Vector
     * @return String
     */
    @Override
    public Vector<String> generateCreateStr(String tableName, Vector<FormField> fields) {
        Vector<String> v = new Vector<>();
        if (fields == null) {
            return v;
        }
        Iterator<FormField> ir = fields.iterator();
        StringBuilder str = new StringBuilder();
        str.append("CREATE TABLE `").append(tableName).append("` (");
        str.append("`flowId` int(11) NOT NULL default '-1',");
        str.append("`flowTypeCode` varchar(20) NOT NULL,");
        str.append("`id` bigint(20) unsigned NOT NULL auto_increment,");
        str.append("`cws_creator` varchar(20),");
        str.append("`cws_id` varchar(20) NOT NULL default '0',");
        str.append("`cws_order` int(11) NOT NULL default '0',");
        str.append("`cws_parent_form` varchar(50),");
        str.append("`unit_code` varchar(20),");
        // -1表示临时流程，0表示流程进行中，1表示流程结束或智能模块创建
        str.append("`cws_status` TINYINT(1) NOT NULL default '-1',");

        // 20161030 fgf 添加
        // 创建拉单时所关联的源表单的ID
        str.append("`cws_quote_id` bigint(20) unsigned,");
        // 创建拉单后自动冲抵标志位
        str.append("`cws_flag` TINYINT(1) NOT NULL default '0',");
        // 创建进度字段
        str.append("`cws_progress` INTEGER UNSIGNED NOT NULL DEFAULT 0,");
        // 创建创建时间字段
        str.append("`cws_create_date` datetime,");
        // 创建修改时间字段
        str.append("`cws_modify_date` datetime,");
        // 创建流程结束时间字段
        str.append("`cws_finish_date` datetime,");
        // 创建引用记录的表单编码字段
        str.append("`cws_quote_form` varchar(20),");
        str.append("`cws_visited` TINYINT(1) NOT NULL default '0',");

        while (ir.hasNext()) {
            FormField ff = ir.next();
            str.append(toStrForCreate(ff)).append(",");
        }
        str.append("KEY (`flowId`),"); // 2006.6.25 考虑到visual智能设计的时候flowId为-1可以重复的问题，flowId因此不能作为主键
        str.append("KEY `flowTypeCode` (`flowTypeCode`),");
        str.append("KEY `cwsCreator` (`cws_creator`),");
        str.append("KEY `cws_parent_form` (`cws_parent_form`),");
        str.append("KEY `unit_code` (`unit_code`),");
        str.append("KEY `cwsIdOrder` (`cws_id`, `cws_order`),");
        str.append("KEY `cwsQuoteId` (`cws_quote_id`),");
        // str.append("KEY `cwsFlag` (`cws_flag`),");
        str.append("KEY `cwsStatus` (`cws_status`),");
        str.append("KEY `cws_id` (`cws_id`),");
        str.append("PRIMARY KEY `id` (`id`)");
        str.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8;");
        v.addElement(str.toString());
        return v;
    }

    public String getTableNameForLog(String tableName) {
        return tableName + "_log";
    }

    @Override
    public Vector<String> generateCreateStrForLog(String tableName, Vector<FormField> fields) {
        Vector<String> v = new Vector<>();
        if (fields == null) {
            return v;
        }
        Iterator<FormField> ir = fields.iterator();
        StringBuilder str = new StringBuilder();
        str.append("CREATE TABLE `").append(getTableNameForLog(tableName)).append("` (");
        str.append("`flowId` int(11) NOT NULL default '0',");
        str.append("`flowTypeCode` varchar(20) NOT NULL,");
        str.append("`id` bigint(20) unsigned NOT NULL auto_increment,");

        str.append("`cws_log_user` varchar(20),");
        str.append("`cws_log_type` TINYINT(1) unsigned NOT NULL default '0',");
        str.append("`cws_log_date` DATETIME NOT NULL,");
        str.append("`cws_log_id` int(10) unsigned NOT NULL default '0',");

        str.append("`cws_creator` varchar(20),");
        str.append("`cws_id` varchar(20) NOT NULL default '0',");
        str.append("`cws_order` int(11) NOT NULL default '0',");
        str.append("`unit_code` varchar(20),");

        while (ir.hasNext()) {
            FormField ff = ir.next();
            str.append(toStrForCreate(ff)).append(",");
        }
        str.append("KEY (`flowId`),"); // 2006.6.25 考虑到visual智能设计的时候flowId为-1可以重复的问题，flowId因此不能作为主键
        str.append("KEY `flowTypeCode` (`flowTypeCode`),");
        str.append("KEY `cwsCreator` (`cws_creator`),");
        str.append("KEY `unit_code` (`unit_code`),");
        str.append("KEY `cwsIdOrder` (`cws_id`, `cws_order`),");
        str.append("KEY `cws_log_type` (`cws_log_type`),");
        str.append("KEY `cws_log_id` (`cws_log_id`),");
        str.append("PRIMARY KEY `id` (`id`)");
        str.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8;");
        v.addElement(str.toString());
        return v;
    }

    /**
     * 判断log表格是否存在
     *
     * @param tableName
     * @return
     */
    @Override
    public boolean isTableForLogExist(String tableName) {
        String sql = "show tables like '" + getTableNameForLog(tableName) + "'";
        JdbcTemplate jt = new JdbcTemplate();
        ResultIterator ri;
        try {
            ri = jt.executeQuery(sql);
            if (ri.hasNext()) {
                return true;
            }
        } catch (SQLException e) {
            LogUtil.getLog(getClass()).error(e);
        }

        return false;
    }

    /**
     * 创建修改表单中字段SQL语句，注意只能增加和删除字段，不能对字段的名称、类型、默认值等作修改
     * 20171209 fgf 改为支持对名称及默认值的修改，数据类型在JSP中已经限制了不可修改，所以不用考虑
     *
     * @param vt Vector[]
     * @return String
     */
    @Override
    public Vector<String> generateModifyStr(String tableName, Vector[] vt) {
        Vector<String> v = new Vector<>();

        String str = "ALTER TABLE `" + tableName + "`";
        Vector delv = vt[0];
        Iterator ir = delv.iterator();
        StringBuilder delstr = new StringBuilder();
        while (ir.hasNext()) {
            FormField delff = (FormField) ir.next();
            if ("".equals(delstr.toString())) {
                delstr.append(" DROP COLUMN `").append(delff.getName()).append("`");
            } else {
                delstr.append(",DROP COLUMN `").append(delff.getName()).append("`");
            }
        }
        Vector addv = vt[1];
        ir = addv.iterator();
        StringBuilder addstr = new StringBuilder();
        while (ir.hasNext()) {
            FormField addff = (FormField) ir.next();
            if ("".equals(addstr.toString())) {
                addstr.append(" ADD COLUMN ").append(toStrForCreate(addff));
            } else {
                addstr.append(",ADD COLUMN ").append(toStrForCreate(addff));
            }
        }
        if (!"".equals(delstr.toString())) {
            if (!"".equals(addstr.toString())) {
                addstr.insert(0, ",");
            }
        }

        Vector remainV = vt[2];
        ir = remainV.iterator();
        String remainStr = "";
        while (ir.hasNext()) {
            FormField ff = (FormField) ir.next();
            if ("".equals(remainStr)) {
                remainStr = " MODIFY COLUMN " + toStrForCreate(ff);
            } else {
                remainStr += ", MODIFY COLUMN " + toStrForCreate(ff);
            }
            // ALTER TABLE `sip`.`ft_bmyssp` MODIFY COLUMN `status2` VARCHAR(100) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT 123445 COMMENT '状态2567';
        }
        if (!"".equals(delstr.toString()) || !"".equals(addstr.toString())) {
            if (!"".equals(remainStr)) {
                remainStr = "," + remainStr;
            }
        }

        str = str + delstr + addstr + remainStr;

        // DebugUtil.i(getClass(), "generateModifyStr", str);
        v.addElement(str);
        return v;
    }


    /**
     * 创建修改表单中字段SQL语句，注意只能增加和删除字段，不能对字段的名称、类型、默认值等作修改
     *
     * @param vt Vector[]
     * @return String
     */
    @Override
    public Vector<String> generateModifyStrForLog(String tableName, Vector[] vt) {
        Vector<String> v = new Vector<>();

        String str = "ALTER TABLE `" + getTableNameForLog(tableName) + "`";
        Vector delv = vt[0];
        Iterator ir = delv.iterator();
        StringBuilder delstr = new StringBuilder();
        while (ir.hasNext()) {
            FormField delff = (FormField) ir.next();
            if (delstr.toString().equals("")) {
                delstr.append(" DROP COLUMN `").append(delff.getName()).append("`");
            } else {
                delstr.append(",DROP COLUMN `").append(delff.getName()).append("`");
            }
        }
        Vector addv = vt[1];
        ir = addv.iterator();
        StringBuilder addstr = new StringBuilder();
        while (ir.hasNext()) {
            FormField addff = (FormField) ir.next();
            if ("".equals(addstr.toString())) {
                addstr.append(" ADD COLUMN ").append(toStrForCreate(addff));
            } else {
                addstr.append(",ADD COLUMN ").append(toStrForCreate(addff));
            }
        }
        if (!"".equals(delstr.toString())) {
            if (!"".equals(addstr.toString())) {
                addstr.insert(0, ",");
            }
        }

        Vector remainV = vt[2];
        ir = remainV.iterator();
        String remainStr = "";
        while (ir.hasNext()) {
            FormField ff = (FormField) ir.next();
            if ("".equals(remainStr)) {
                remainStr = " MODIFY COLUMN " + toStrForCreate(ff);
            } else {
                remainStr += ", MODIFY COLUMN " + toStrForCreate(ff);
            }
            // ALTER TABLE `sip`.`ft_bmyssp` MODIFY COLUMN `status2` VARCHAR(100) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT 123445 COMMENT '状态2567';
        }
        if (!"".equals(delstr.toString()) || !"".equals(addstr.toString())) {
            if (!"".equals(remainStr)) {
                remainStr = "," + remainStr;
            }
        }

        // ALTER TABLE `test`.`ff` DROP COLUMN `name`, ADD COLUMN `con` VARCHAR(45) NOT NULL AFTER `title`;
        if ("".equals(addstr.toString()) && "".equals(delstr.toString()) && "".equals(remainStr)) {
            return v;
        }
        str = str + delstr + addstr + remainStr;
        LogUtil.getLog(getClass()).info("generateModifyStr:" + str);
        v.addElement(str);
        return v;
    }

    @Override
    public Vector<String> generateDropTable(String tableName) {
        Vector<String> v = new Vector<>();
        v.addElement("DROP TABLE IF EXISTS " + "`" +
                tableName + "`");
        return v;
    }

    @Override
    public Vector<String> generateDropTableForLog(String tableName) {
        Vector<String> v = new Vector<>();
        v.addElement("DROP TABLE IF EXISTS " + "`" +
                getTableNameForLog(tableName) + "`");
        return v;
    }

    @Override
    public boolean isFieldKeywords(String fieldName) {
        for (String keyword : keywords) {
            if (fieldName.equalsIgnoreCase(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 得到创建字段的SQL语句
     * 20070425
     * MYSQL的极限长度 innodb表中的varchar()合计长度不能超过65535，因此将varchar(250)改为varchar(100)
     * ERROR http-80-Processor23 com.redmoon.oa.flow.FormDb - create:Row size too large. The maximum row size for the used table type, not counting BLOBs, is 65535. You have to change some columns to TEXT or BLOBs. Now transaction rollback
     *
     * @return String
     */
    @Override
    public String toStrForCreate(FormField ff) {
        String typeStr = "";
        boolean isMacroSpecial = false;
        MacroCtlUnit mu;
        if (ff.getType().equals(FormField.TYPE_MACRO)) {
            if (ff.getFieldType() != FormField.FIELD_TYPE_VARCHAR) {
                isMacroSpecial = true;
            }
            else if (!StringUtils.isEmpty(ff.getRule())) {
                // 如果是FIELD_TYPE_VARCHAR，且规则不为空，则说明是6.0以后的新版
                isMacroSpecial = true;
            }

            MacroCtlMgr mm = new MacroCtlMgr();
            mu = mm.getMacroCtlUnit(ff.getMacroType());
            if (mu == null) {
                throw new IllegalArgumentException("Macro ctl type=" + ff.getMacroType() + " is not exist.");
            }

            // 基础数据宏控件在macros.jsp中配置了字段类型
            if ("macro_flow_select".equals(mu.getCode())) {
                isMacroSpecial = true;
            }

            if (!isMacroSpecial) {
                // 如果是函数宏控件，则根据函数宏控件设置的为准
                if ("macro_formula_ctl".equals(mu.getCode())) {
                    String desc = ff.getDescription();
                    try {
                        com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSONObject.parseObject(desc);
                        String formulaCode = json.getString("code");
                        IFormulaUtil formulaUtil = SpringUtil.getBean(IFormulaUtil.class);
                        Formula formula = formulaUtil.getFormula(new JdbcTemplate(), formulaCode);
                        // 使字段类型为函数中所设的数据类型
                        ff.setFieldType(formula.getFieldType());

                        isMacroSpecial = true;
                    } catch (ErrMsgException ex) {
                        LogUtil.getLog(getClass()).error(ex);
                    }
                }
            }

            /*if (!isMacroSpecial) {
                typeStr = mu.getFieldType();
            }*/
            // 20230510 改为使宏控件以表单中设置的为准
            isMacroSpecial = true;
        }

        if (!ff.getType().equals(FormField.TYPE_MACRO) || isMacroSpecial) {
            if (ff.getFieldType() == FormField.FIELD_TYPE_VARCHAR) {
                typeStr = "varchar(" + ff.getLengthByRule() + ")";
            } else if (ff.getFieldType() == FormField.FIELD_TYPE_INT) {
                typeStr = "INTEGER";
            } else if (ff.getFieldType() == FormField.FIELD_TYPE_LONG) {
                typeStr = "BIGINT";
            } else if (ff.getFieldType() == FormField.FIELD_TYPE_FLOAT) {
                typeStr = "FLOAT";
            } else if (ff.getFieldType() == FormField.FIELD_TYPE_DOUBLE) {
                typeStr = "DOUBLE";
            } else if (ff.getFieldType() == FormField.FIELD_TYPE_PRICE) {
                typeStr = "DOUBLE";
            } else if (ff.getFieldType() == FormField.FIELD_TYPE_BOOLEAN) {
                typeStr = "char(1)";
            } else if (ff.getFieldType() == FormField.FIELD_TYPE_TEXT) {
                typeStr = "text";
            } else if (ff.getFieldType() == FormField.FIELD_TYPE_DATE) {
                typeStr = "date";
            } else if (ff.getFieldType() == FormField.FIELD_TYPE_DATETIME) {
                typeStr = "datetime";
            }
            else if (ff.getFieldType() == FormField.FIELD_TYPE_LONGTEXT) {
                typeStr = "longtext";
            }
            else {
                typeStr = "varchar(" + ff.getLengthByRule() + ")";
            }
        }

        String defaultStr = StrUtil.sqlstr("");
        if (ff.getDefaultValue() != null) {
            if (ff.getType().equals(FormField.TYPE_DATE)) {
                if (ff.getDefaultValue().equals(FormField.DATE_CURRENT) || "".equals(ff.getDefaultValue())) {
                    // defaultStr = StrUtil.sqlstr("0000-00-00");
                    // mysql5.7默认已不支持0000-00-00
                    defaultStr = StrUtil.sqlstr("");
                } else {
                    defaultStr = StrUtil.sqlstr(ff.getDefaultValue());
                }
            } else if (ff.getType().equals(FormField.TYPE_DATE_TIME)) {
                if (ff.getDefaultValue().equals(FormField.DATE_CURRENT) || "".equals(ff.getDefaultValue())) {
                    // defaultStr = StrUtil.sqlstr("0000-00-00 00:00:00");
                    defaultStr = StrUtil.sqlstr("");
                } else {
                    defaultStr = StrUtil.sqlstr(ff.getDefaultValue());
                }
            } else {
                defaultStr = StrUtil.sqlstr(ff.getDefaultValue());
            }
        }

        if (ff.getType().equals(FormField.TYPE_BUTTON)) {
            typeStr = "varchar(20)";
            defaultStr = StrUtil.sqlstr("");
        }

        String str = "";
        if (!"text".equals(typeStr)) {
            if ("''".equals(defaultStr)) {
                // 在MySQL5.1中，不能用 ... default '' ...的方式，而MySQL4.1可以这样用
                str = "`" + ff.getName() + "` " + typeStr +
                        " COMMENT " + StrUtil.sqlstr(ff.getTitle());
            } else {
                if (ff.getType().equals(FormField.TYPE_CALCULATOR)) {
                    str = "`" + ff.getName() + "` " + typeStr +
                            " COMMENT " + StrUtil.sqlstr(ff.getTitle());
                } else {
                    str = "`" + ff.getName() + "` " + typeStr + " default " +
                            defaultStr +
                            " COMMENT " + StrUtil.sqlstr(ff.getTitle());
                }
            }
        } else {
            str = "`" + ff.getName() + "` " + typeStr + " COMMENT " +
                    StrUtil.sqlstr(ff.getTitle());
        }

        LogUtil.getLog(getClass()).info("toStrForCreate str=" + str);
        return str;
    }

    @Override
    public String getTableColumnsFromDbSql(String tableName) {
        return "select * from " + tableName + " limit 1";
    }
}
