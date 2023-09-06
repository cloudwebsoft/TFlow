package com.cloudweb.oa.utils;

import cn.js.fan.db.ResultIterator;
import cn.js.fan.db.ResultRecord;
import cn.js.fan.util.DateUtil;
import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.NumberUtil;
import cn.js.fan.util.StrUtil;
import com.cloudweb.oa.api.IFormulaUtil;
import com.cloudwebsoft.framework.db.JdbcTemplate;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.flow.FormField;
import com.redmoon.oa.shell.BSHShell;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.visual.Formula;
import com.redmoon.oa.visual.FormulaResult;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FormulaUtil implements IFormulaUtil {
    public static final String KIND_SQL = "sql";
    public static final String KIND_SCRIPT = "script";

    /**
     * 调用函数，如：#selBookPerson(bookId, userNames)
     * @param formula
     * @return
     */
    @Override
    public FormulaResult render(String formula) throws ErrMsgException {
        String sql = "select params,content,kind,field_type from ft_formula where code=?";

        int fieldType = FormField.FIELD_TYPE_INT;
        int decimals = 2;

        JdbcTemplate jt = new JdbcTemplate();
        jt.setAutoClose(false);
        boolean isMatched = false;
        try {
            Pattern p = Pattern.compile(
                    "#([A-Z0-9a-z-_]+)\\(([^\\$]*?)\\)",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            do {
                isMatched = false;
                // 找到原子函数，因为函数有可能是嵌套的
                Matcher m = p.matcher(formula);
                StringBuffer sb = new StringBuffer();
                while (m.find()) {
                    isMatched = true;

                    String formulaCode = m.group(1);
                    // 实参字符串，以逗号分隔，如：#selBookPerson(bookId, userNames)的实参为：12, "张三,李四,王五"
                    String strArgs = m.group(2);

                    // 从ft_formula中取得内容
                    String strParams = "", content = "", kind = "";
                    ResultIterator ri = jt.executeQuery(sql, new Object[]{formulaCode});
                    if (ri.hasNext()) {
                        ResultRecord rr = ri.next();
                        strParams = rr.getString(1).trim();
                        content = rr.getString(2).trim();
                        kind = rr.getString(3);
                        fieldType = rr.getInt(4);
                    }
                    else {
                        throw new ErrMsgException("函数 " + formulaCode + " 不存在！");
                    }

                    // 检查参数长度及类型是否一致
                    String[] args = parseArgs(strArgs); // 实参
                    String[] params = StrUtil.split(strParams, ","); // 形参

                    if (args==null && params==null) {
                        ;
                    }
                    else {
                        int argsLen = 0, paramsLen = 0;
                        if (args!=null) {
                            argsLen = args.length;
                        }
                        if (params!=null) {
                            paramsLen = params.length;
                        }
                        // 实参args的数量也可能会小于形参params的数量，如：在module_add.jsp页面中添加工资帐套中的人员时，pos(personId)，因为此时personId为空，所以实参数量解析得到的数组长度为0
                        // 所以此处，如果 argsLen < paramsLen，则说明传入的实参中有可能仅有1个空字符串，或者少传了实参
                        // 在macro_formula_ctl_js.jsp中，出现上述情况，仅有可能是：只有第1个参数为空字符串，且只有1个形参（此时因为是自动拼装的参数，所以不会出现少传的情况）
                        // 因此，如果argsLen=0，并且paramsLen=1，则自动补第1个参数为空字符串，但这样仍会有问题，函数pos中是sql语句：select pos from ft_personbasic where id={$personId}
                        // 会使得报SQL运行错误，故不能补为空字符串，而是只能返回空值
                        if (argsLen==0 && paramsLen==1) {
                            DebugUtil.i(getClass(), "render", formula + " 实参为空，形参为" + params[0] + "，返回空值");
                            FormulaResult fr = new FormulaResult(fieldType, decimals);
                            fr.setResult("");
                            return fr;
                        }

                        // 注意：实参的数量可以大于等于形参的数量，大于的部分将会被忽略
                        if (argsLen < paramsLen) {
                            throw new ErrMsgException("实参：" + strArgs + " 的数量小于形参：" + strParams + " 的数量");
                        }
                    }

                    // DebugUtil.i(FormulaUtil.class, "render", formulaCode + " 形参：" + strParams + " 实参：" + strArgs);
                    String val = render(jt, content, params, args, kind, fieldType);

                    m.appendReplacement(sb, val);
                }
                m.appendTail(sb);

                formula = sb.toString();
            }
            while (isMatched);
        } catch (SQLException e) {
            LogUtil.getLog(getClass()).error(e);
        }
        finally {
            jt.close();
        }

        FormulaResult fr = new FormulaResult(fieldType, decimals);
        fr.setResult(formula);
        // formulaStr 此处可能是一个四则运算的算式
        // 判断是否为数字，如果不是，则再做一次四则运算
        if (!"".equals(formula) && !StrUtil.isDouble(formula)) {
            if (fieldType==FormField.FIELD_TYPE_DOUBLE
                    || fieldType==FormField.FIELD_TYPE_INT
                    || fieldType==FormField.FIELD_TYPE_FLOAT
                    || fieldType==FormField.FIELD_TYPE_LONG
                    || fieldType==FormField.FIELD_TYPE_PRICE
            ) {
                ScriptEngineManager manager = new ScriptEngineManager();
                ScriptEngine engine = manager.getEngineByName("javascript");
                try {
                    Double obj = (Double)engine.eval(formula);
                    fr.setResult(String.valueOf(obj.doubleValue()));
                }
                catch (ScriptException ex) {
                    LogUtil.getLog(getClass()).info("render中四则运算 formula=" + formula);
                    LogUtil.getLog(getClass()).error(ex);
                }
            }
        }

        return fr;
    }

    /**
     *
     * @param content
     * @param params 形参，例：user,dept
     * @param args 实参，例：张三,0001
     * @param kind
     * @param fieldType
     * @return
     * @throws ErrMsgException
     */
    public String render(JdbcTemplate jt, String content, String[] params, String[] args, String kind, int fieldType) throws ErrMsgException {
        // 以实参替换函数内容中的形参
        if (params!=null) {
            // 实参的数量可以大于等于形参的数量，大于的部分将会被忽略
            for (int i=0; i<params.length; i++) {
                // LogUtil.getLog(getClass()).info("FormulaUtil.java param=" + params[i]);
                content = content.replaceAll("\\{\\$" + params[i].trim() + "\\}", args[i]);
            }
        }

        // DebugUtil.log(FormulaUtil.class.getName(), "render", content);

        // 检查是否含有函数，如：select pv from ft_salary_set where position=’#rank({$userName})’
        Pattern p = Pattern.compile("#([A-Z0-9a-z-_]+)\\(([[a-zA-Z0-9-_,\"]]*?)\\)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String formulaCode = m.group(1);

            // 从ft_formula中取得内容
            Formula fma = getFormula(jt, formulaCode);

            String strArgs = m.group(2);
            // 检查参数长度及类型是否一致
            String[] argsAry = parseArgs(strArgs); // 实参
            String[] paramsAry = StrUtil.split(fma.getParams(), ","); // 形参

            if (args!=null && params!=null) {
                // 实参的数量可以大于等于形参的数量，大于的部分将会被忽略
                if (args.length < params.length) {
                    throw new ErrMsgException("函数：" + formulaCode + " 实参：" + strArgs + " 的数量小于形参：" + fma.getParams() + " 的数量");
                }
            }

            String ret = render(jt, fma.getContent(), paramsAry, argsAry, fma.getKind(), fma.getFieldType());

            m.appendReplacement(sb, ret);
        }
        m.appendTail(sb);

        content = sb.toString();

        String ret = "";

        if (kind.equals(KIND_SQL)) {
            try {
                ResultIterator ri = jt.executeQuery(content);
                if (ri.hasNext()) {
                    ResultRecord rr = ri.next();
                    if (fieldType == FormField.FIELD_TYPE_DATE) {
                        Date d = rr.getDate(1);
                        ret = DateUtil.format(d, "yyyy-MM-dd");
                    } else if (fieldType == FormField.FIELD_TYPE_DATETIME) {
                        Timestamp ts = rr.getTimestamp(1);
                        Date d = new java.util.Date(ts.getTime());
                        ret = DateUtil.format(d, "yyyy-MM-dd HH:mm:ss");
                    }
                    else if (fieldType == FormField.FIELD_TYPE_DOUBLE) {
                        ret = String.valueOf(rr.getDouble(1));
                    }
                    else if (fieldType==FormField.FIELD_TYPE_FLOAT) {
                        ret = String.valueOf(rr.getFloat(1));
                    }
                    else if (fieldType==FormField.FIELD_TYPE_PRICE) {
                        ret = NumberUtil.round(rr.getDouble(1), 2);
                    }
                    else {
                        ret = StrUtil.getNullStr(rr.getString(1));
                    }
                }
            } catch (SQLException e) {
                LogUtil.getLog(getClass()).error(e);
            }
        }
        else {
            BSHShell bs = new BSHShell();
            // 赋值用户
/*				StringBuffer sb = new StringBuffer();
			sb.append("userName=\"" + userName + "\";");
			bsh.eval(BeanShellUtil.escape(sb.toString()));*/

            // bsh.set("ids", ids);

            bs.eval(content);

            String errMsg = (String)bs.get("errMsg");
            if (errMsg!=null && !"".equals(errMsg)) {
                throw new ErrMsgException(errMsg);
            }

            ret = (String)bs.get("ret");
            if (ret == null) {
                throw new ErrMsgException("ret未定义");
            }
        }
        return ret;
    }

    /**
     * 解析实参为数组
     * @param args
     * @return
     */
    public String[] parseArgs(String args) {
        // 实参字符串，以逗号分隔，如：#selBookPerson(bookId, userNames)的实参为：12, "张三,李四,王五"
        // 解析为实参后，字符串类型的实参数需去掉引号
        // 字符串型的实参当其中不含有逗号时，可以不加引号，即实参可为：12、张三
        Pattern p = Pattern.compile("\"(.*?)\"",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(args);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String str = m.group(1);

            // 将其中的逗号改为%co
            String patternStr = ","; //
            String replacementStr = "%co";
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(str);
            str = matcher.replaceAll(replacementStr);

            m.appendReplacement(sb, str);
        }
        m.appendTail(sb);

        // 将字符串中的逗号还原
        String[] ary = StrUtil.split(sb.toString(), ",");
        if (ary!=null) {
            for (int i = 0; i < ary.length; i++) {
                ary[i] = ary[i].replaceAll("%co", ",").trim();
            }
        }
        return ary;
    }

    /**
     * 取得函数内容
     * @param formulaCode
     * @return
     * @throws ErrMsgException
     */
    @Override
    public Formula getFormula(JdbcTemplate jt, String formulaCode) throws ErrMsgException {
        String sql = "select params,content,kind,field_type,decimals,code,name from ft_formula where code=?";
        // 从ft_formula中取得内容
        Formula fma = new Formula();
        ResultIterator ri;
        try {
            ri = jt.executeQuery(sql, new Object[]{formulaCode});
            if (ri.hasNext()) {
                ResultRecord rr = ri.next();
                fma.setParams(rr.getString(1).trim());
                fma.setContent(rr.getString(2).trim());
                fma.setKind(rr.getString(3));
                fma.setFieldType(rr.getInt(4));
                fma.setDecimals(rr.getInt(5));
                fma.setCode(rr.getString(6));
                fma.setName(rr.getString(7));
            }
            else {
                throw new ErrMsgException("函数 " + formulaCode + " 不存在！");
            }
        } catch (SQLException e) {
            LogUtil.getLog(getClass()).error(e);
        }
        return fma;
    }

    @Override
    public Formula getFormulaById(long id) throws ErrMsgException {
        String sql = "select params,content,kind,field_type,decimals,code,name from ft_formula where id=?";
        // 从ft_formula中取得内容
        Formula fma = new Formula();
        ResultIterator ri;
        try {
            JdbcTemplate jt = new JdbcTemplate();
            ri = jt.executeQuery(sql, new Object[]{id});
            if (ri.hasNext()) {
                ResultRecord rr = ri.next();
                fma.setParams(rr.getString(1).trim());
                fma.setContent(rr.getString(2).trim());
                fma.setKind(rr.getString(3));
                fma.setFieldType(rr.getInt(4));
                fma.setDecimals(rr.getInt(5));
                fma.setCode(rr.getString(6));
                fma.setName(rr.getString(7));
            }
            else {
                throw new ErrMsgException("函数 id: " + id + " 不存在！");
            }
        } catch (SQLException e) {
            LogUtil.getLog(getClass()).error(e);
        }
        return fma;
    }
}
