package com.redmoon.oa.flow;

import cn.js.fan.db.ResultIterator;
import cn.js.fan.db.ResultRecord;
import cn.js.fan.util.*;
import cn.js.fan.web.SkinUtil;
import com.cloudweb.oa.api.*;
import com.cloudweb.oa.service.MacroCtlService;
import com.cloudweb.oa.utils.ConstUtil;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudwebsoft.framework.db.JdbcTemplate;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.kit.util.FileUpload;
import com.redmoon.oa.Config;
import com.redmoon.oa.base.IFormDAO;
import com.redmoon.oa.base.IFormMacroCtl;
import com.redmoon.oa.base.IFormValidator;
import com.redmoon.oa.flow.macroctl.*;
import com.redmoon.oa.person.UserDb;
import com.redmoon.oa.pvg.Privilege;
import com.redmoon.oa.shell.BSHShell;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.ui.LocalUtil;
import com.redmoon.oa.util.BeanShellUtil;
import com.redmoon.oa.visual.*;
import nl.bitwalker.useragentutils.DeviceType;
import nl.bitwalker.useragentutils.OperatingSystem;
import nl.bitwalker.useragentutils.UserAgent;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.*;
// import sun.org.mozilla.javascript.internal.IdFunctionObject;

/**
 * <p>Title: </p>
 *
 * <p>Description: </p>
 *
 * <p>Copyright: Copyright (c) 2005</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class FormDAOMgr {
    int flowId;
    String formCode;
    FormDb fd;
    FileUpload fu;

    public FormDAOMgr(){

    }

    public FormDAOMgr(int flowId, String formCode) {
        this.flowId = flowId;
        this.formCode = formCode;
        fd = new FormDb();
        fd = fd.getFormDb(formCode);
    }

    public FormDAOMgr(int flowId, String formCode, FileUpload fu, WorkflowActionDb action) {
    	this.flowId = flowId;
        this.formCode = formCode;
        fd = new FormDb();
        fd = fd.getFormDb(formCode);
        this.fu = fu;
        this.action = action;
    }

    public String getFieldValue(String fieldName) {
     	String val = null;
    	try {
    		val = fu.getFieldValue(fieldName, true);
    	}
    	catch (ClassCastException e) {
    		// 使表单域选择宏控件支持多选的时候，这里得到的应是数组，将其拼装成,号分隔的字符串
    		String[] ary = fu.getFieldValues(fieldName);
    		if (ary.length>0) {
    			val = "";
    		}
    		for (String str : ary) {
    			if ("".equals(val)) {
    				val = str;
    			}
    			else {
    				val += "," + str;
    			}
    		}
    	}
    	return val;         
    }

     /**
      * 根据fields获取request中所有域的值
      * @param request HttpServletRequest
      */
     public Vector<FormField> getFieldsByForm(HttpServletRequest request) throws ErrMsgException {
         Vector<FormField> fields = fd.getFields();
         getFieldsByForm(request, fields);
         return fields;
    }

    public void getFieldsByForm(HttpServletRequest request, Vector<FormField> fieldsWritable) throws ErrMsgException {
        for (FormField ff : fieldsWritable) {
            // 如果是辅助字段则跳过，因为fields来自于formdao，如果此处获取值有可能会在其后的流程事件中保存fdao，致字段值发生变化
            if (ff.isHelper()) {
                continue;
            }

            // 判断如果是文件框，则不需要取，因为getFiledValue中用fu.getFieldValue本身也取不到
            // 在其setValueForSave中会设其值，如果上传了文件或者被映射了值的话
            if ("macro_attachment".equals(ff.getMacroType())) {
                String val = StrUtil.getNullStr(fu.getFieldValue(ff.getName()));
                // 如果不为空，则说明是表单选择域宏控件映射的值，在此处赋值，可以便于验证时判断是否为空
                if (!"".equals(val)) {
                    ff.setValue(val);
                }
                continue;
            }

            // 如果为null，则有可能是不可写字段，未post值过来，则仍保留原来的值，不能赋予空字符串，否则原来的值会丢失
            if (getFieldValue(ff.getName()) == null) {
                // 如果是checkbox型，则将其值置为"0"
                if (ff.isEditable() && ff.getType().equals(FormField.TYPE_CHECKBOX)) {
                    ff.setValue("0");
                }
                continue;
            }

            if (ff.getType().equals(FormField.TYPE_DATE_TIME)) {
                UserAgent ua = UserAgent.parseUserAgentString(request.getHeader("User-Agent"));
                OperatingSystem os = ua.getOperatingSystem();
                boolean isMobile = false;
                if (DeviceType.MOBILE.equals(os.getDeviceType())) {
                    isMobile = true;
                }
                if (!isMobile) {
                    String d = getFieldValue(ff.getName()) + " " + getFieldValue(ff.getName() + "_time");
                    ff.setValue(d.trim());
                } else {
                    ff.setValue(getFieldValue(ff.getName()));
                }
            } else {
                if (ff.getFormat().equals(FormField.FORMAT_THOUSANDTH)) {
                    ff.setValue(getFieldValue(ff.getName()).replaceAll(",", ""));
                }
                else {
                    ff.setValue(getFieldValue(ff.getName()));
                }
            }
        }
    }

    /**
     * 当流程创建时添加一条记录至表单对应的表
     * @param request HttpServletRequest
     * @return boolean
     */
    public boolean create(ServletContext application, HttpServletRequest request) throws ErrMsgException {
        Vector<FormField> fields = getFieldsByForm(request);
        FormDAO fdao = new FormDAO(flowId, fd);
        fdao.setFields(fields);
        Privilege privilege = new Privilege();
        fdao.setUnitCode(privilege.getUserUnitCode(request));
        return fdao.create();
    }

    public Vector<FormField> getFields() {
        FormDAO fdao = new FormDAO();
        fdao = fdao.getFormDAOByCache(flowId, fd);
        return fdao.getFields();
    }
    
    public static void checkFieldNum(FormField ff, ParamChecker pck, double value, double mt) {
    	if (">".equals(ff.getMorethanMode())) {
        	if (value <= mt) {
        		pck.addMsg("err_need_more", new String[] { ff.getTitle(),
        				mt + "" });
        	}
    	}
    	else if (">=".equals(ff.getMorethanMode())) {
    		if (value < mt) {
        		pck.addMsg("err_need_more_equal", new String[] { ff.getTitle(),
        				mt + "" });            			
    		}
    	}
    	else if ("<".equals(ff.getMorethanMode())) {
    		if (value >= mt) {
        		pck.addMsg("err_need_less", new String[] { ff.getTitle(),
        				mt + "" });             			
    		}
    	}
    	else if ("<=".equals(ff.getMorethanMode())) {
    		if (value > mt) {
        		pck.addMsg("err_need_less_equal", new String[] { ff.getTitle(),
        				mt + "" });             			
    		}            		
    	}
    	else if ("=".equals(ff.getMorethanMode())) {
    		if (mt != value) {
        		pck.addMsg("err_need_less_equal", new String[] { ff.getTitle(),
        				mt + "" });             			
    		}                		
    	}    	
    }
    
    public static void checkFieldDate(FormField ff, ParamChecker pck, FileUpload fu, Date value, Date mt) {
    	if (value != null && mt != null) {
    		String strMt = fu.getFieldValue(ff.getMoreThan());
    		if (">".equals(ff.getMorethanMode())) {
            	if (mt.after(value) || value.equals(mt)) {
            		pck.addMsg("err_need_more", new String[] { ff.getTitle(),
            				strMt + "" });
            	}
        	}
        	else if (">=".equals(ff.getMorethanMode())) {
        		if (value.before(mt)) {
            		pck.addMsg("err_need_more_equal", new String[] { ff.getTitle(),
            				strMt + "" });            			
        		}
        	}
        	else if ("<".equals(ff.getMorethanMode())) {
        		if (value.after(mt) || value.equals(mt)) {
            		pck.addMsg("err_need_less", new String[] { ff.getTitle(),
            				strMt + "" });			
        		}
        	}
        	else if ("<=".equals(ff.getMorethanMode())) {
        		if (value.after(mt)) {
            		pck.addMsg("err_need_less_equal", new String[] { ff.getTitle(),
            				strMt + "" });
        		}
        	}
        	else if ("=".equals(ff.getMorethanMode())) {
        		if (!value.equals(mt)) {
            		pck.addMsg("err_need_less_equal", new String[] { ff.getTitle(),
            				strMt + "" });             			
        		}                		
        	}            		
    	}    	
    }
    
    /**
     * 根据表单的属性成生成规则字符串，用以进行有效性验证
     * @param ff FormField
     * @return String
     */
    public static void checkField(HttpServletRequest request, FileUpload fu, ParamChecker pck, FormField ff, String fieldName, IFormDAO ifdao) throws CheckErrException {
        String ruleStr = "";
        String cNull = "not";
        if (ff.isCanNull()) {
            cNull = "empty";
        }
        
		boolean doMoreThanCheck = true;
/*		if (com.redmoon.oa.kernel.License.getInstance().isPlatformSrc()) {
			doMoreThanCheck = true;
		}*/

        // 计算控件不检查
        if (ff.getType().equals(FormField.TYPE_CALCULATOR)) {
            return;
        }

        // 置宏控件的数据类型fieldType，实际上2.2及之前版本均为varchar
        // 由于宏控件的值有些是通过程序动态获得的，所以不宜通过ParamChecker来进行有效性验证，因此在表单设计的时候，只设置其是否必填，而不设置最大和最小值
        // 在仅设置必填项的时候也有问题，如：意见框，用户可以先保存意见，然后再转交下一步，但是因为转交时设置了必填，就要重复填写意见，这就需要管理员设置的时候要仔细
        if (ff.getType().equals(FormField.TYPE_MACRO)) {
            // LogUtil.getLog(getClass()).info("saveDAO1 " + fieldName() + " getType()=" + getType() + " getMacroType=" + this.getMacroType());
            MacroCtlMgr mm = new MacroCtlMgr();
            MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
            if (mu != null) {
                IFormMacroCtl ifmctl = mu.getIFormMacroCtl();
                if (ifmctl != null) {
                    // 用于创建时校验，此时ifdao为null，不为null则说明是编辑模块时
                    if (ifdao != null) {
                        // 20190914 AttahcmentCtl中也可能是保存了草稿，但扩展名非法的情况，还是需通过setValueForValidate检测
                        if (ifmctl instanceof IAttachmentCtl ||
                                ifmctl instanceof IAttachmentsCtl ||
                                ifmctl instanceof IImageCtl) {
                            ff.setValue(ifdao.getFieldValue(ff.getName()));
                        }
/*	                else if ( // ifmctl instanceof AttachmentCtl ||
	                		ifmctl instanceof AttachmentsCtl ||
	                		ifmctl instanceof ImageCtl
	                ) {
	                	// 如果附件、图片宏控件的值不为空，则说明已上传了数据，在此需跳过不为空的后台验证
	                	// 因为如果不跳过，当智能模块编辑数据提交时，会提示需上传，而实际之前已经上传过，无需再次上传
	                	if (!ff.isCanNull()) {
	                		String val = ifdao.getFieldValue(ff.getName());
	                		if (val!=null && !"".equals(val)) {
	                			return;
	                		}
	                	}
	                }*/
                    }

                    ff.setFieldType(ifmctl.getFieldType(ff));
                    // 在验证前获取表单域的值，用于附件、图片宏控件不能为空的检查
                    ifmctl.setValueForValidate(request, fu, ff);
                } else {
                    LogUtil.getLog(FormDAOMgr.class).error("checkField: 宏控件" + ff.getMacroType() + "不存在！");
                }
            } else {
                LogUtil.getLog(FormDAOMgr.class).error("checkField: 宏控件" + ff.getMacroType() + "不存在！");
            }
        }

        if (ff.getFieldType() == FormField.FIELD_TYPE_VARCHAR) {
            ruleStr = ParamChecker.TYPE_STRING;
            ruleStr += "," + fieldName + "," +
                    ff.getTitle() + "," + cNull;
            if (!"".equals(ff.getRule())) {
                ruleStr += "," + ff.getRule();
            }
            try {
            	pck.checkFieldString(ruleStr);
            } catch (CheckErrException e) {
				if (ff.getType().equals(FormField.TYPE_MACRO)
						&& ("macro_ckeditor".equals(ff.getMacroType())
								|| "macro_opinion".equals(ff.getMacroType())
								|| "macro_opinionex".equals(ff.getMacroType()))
						&& e.getMessage().equals(pck.LoadString("err_format", new String[] {ff.getTitle()}))) {
					pck.msgs.remove(e.getMessage());
				} else {
					throw e;
				}
            }
        } else if (ff.getFieldType() == FormField.FIELD_TYPE_TEXT || ff.getFieldType() == FormField.FIELD_TYPE_LONGTEXT) {
            ruleStr = ParamChecker.TYPE_STRING;

            ruleStr += "," + fieldName + "," +
                    ff.getTitle() + "," + cNull;
            if (!"".equals(ff.getRule())) {
                ruleStr += "," + ff.getRule();
            }
            pck.checkFieldString(ruleStr);
        }
        else if (ff.getFieldType() == FormField.FIELD_TYPE_INT) {
            if (ff.isCanNull()) {
                cNull = "allow";
            }
            ruleStr = ParamChecker.TYPE_INT;

            ruleStr += "," + fieldName + "," +
                    ff.getTitle() + "," + cNull;
            if (!"".equals(ff.getRule())) {
                ruleStr += "," + ff.getRule();
            }
            pck.checkFieldInt(ruleStr);
            
            if (doMoreThanCheck && !"".equals(StrUtil.getNullStr(ff.getMoreThan()))) {
            	int value = StrUtil.toInt(fu.getFieldValue(fieldName));
            	int mt = StrUtil.toInt(fu.getFieldValue(getMoreThanFieldName(ff, fieldName)));
            	
            	checkFieldNum(ff, pck, value, mt);
            }
        }
        else if (ff.getFieldType() == FormField.FIELD_TYPE_LONG) {
            if (ff.isCanNull()) {
                cNull = "allow";
            }
            ruleStr = ParamChecker.TYPE_LONG;

            ruleStr += "," + fieldName + "," + ff.getTitle() + "," + cNull;
            if (!"".equals(ff.getRule())) {
                ruleStr += "," + ff.getRule();
            }
            pck.checkFieldLong(ruleStr);
            
            if (doMoreThanCheck && !"".equals(StrUtil.getNullStr(ff.getMoreThan()))) {
            	long value = StrUtil.toLong(fu.getFieldValue(fieldName));
            	long mt = StrUtil.toLong(fu.getFieldValue(getMoreThanFieldName(ff, fieldName)));
            	checkFieldNum(ff, pck, value, mt);
            }
        }
        else if (ff.getFieldType() == FormField.FIELD_TYPE_BOOLEAN) {
            ruleStr = ParamChecker.TYPE_BOOLEAN;

            ruleStr += "," + fieldName + "," +
                    ff.getTitle() + "," + cNull;
            if (!"".equals(ff.getRule())) {
                ruleStr += "," + ff.getRule();
            }
            pck.checkFieldBoolean(ruleStr);
        }
        else if (ff.getFieldType() == FormField.FIELD_TYPE_FLOAT) {
            if (ff.isCanNull()) {
                cNull = "allow";
            }
            ruleStr = ParamChecker.TYPE_FLOAT;

            ruleStr += "," + fieldName + "," + ff.getTitle() + "," + cNull;
            if (!"".equals(ff.getRule())) {
                ruleStr += "," + ff.getRule();
            }
            pck.checkFieldFloat(ruleStr);
            
            if (doMoreThanCheck && !"".equals(StrUtil.getNullStr(ff.getMoreThan()))) {
            	float value = StrUtil.toFloat(fu.getFieldValue(fieldName));
            	float mt = StrUtil.toFloat(fu.getFieldValue(getMoreThanFieldName(ff, fieldName)));
            	checkFieldNum(ff, pck, value, mt);
            }
        }
        else if (ff.getFieldType() == FormField.FIELD_TYPE_DOUBLE) {
            if (ff.isCanNull()) {
                cNull = "allow";
            }
            ruleStr = ParamChecker.TYPE_DOUBLE;

            ruleStr += "," + fieldName + "," +
                    ff.getTitle() + "," + cNull;
            if (!"".equals(ff.getRule())) {
                ruleStr += "," + ff.getRule();
            }
            pck.checkFieldDouble(ruleStr);
            
            if (doMoreThanCheck && !"".equals(StrUtil.getNullStr(ff.getMoreThan()))) {
            	double value = StrUtil.toDouble(fu.getFieldValue(fieldName));
            	double mt = StrUtil.toDouble(fu.getFieldValue(getMoreThanFieldName(ff, fieldName)));
            	checkFieldNum(ff, pck, value, mt);
            }
        }
        else if (ff.getFieldType() == FormField.FIELD_TYPE_DATE) {
            if (ff.isCanNull()) {
                cNull = "allow";
            }
            ruleStr = ParamChecker.TYPE_DATE;

            ruleStr += "," + fieldName + "," +
                    ff.getTitle() + "," + cNull;
            if (!"".equals(ff.getRule())) {
                ruleStr += "," + ff.getRule();
            }
            ruleStr += ",format=yyyy-MM-dd";
            pck.checkFieldDate(ruleStr);
            
            if (doMoreThanCheck && !"".equals(StrUtil.getNullStr(ff.getMoreThan()))) {
            	Date value = DateUtil.parse(fu.getFieldValue(fieldName), FormField.FORMAT_DATE);
            	Date mt = DateUtil.parse(fu.getFieldValue(getMoreThanFieldName(ff, fieldName)), FormField.FORMAT_DATE);
            	checkFieldDate(ff, pck, fu, value, mt);
            }
        }
        else if (ff.getFieldType() == FormField.FIELD_TYPE_DATETIME) {
            if (ff.isCanNull()) {
                cNull = "allow";
            }
            ruleStr = ParamChecker.TYPE_DATE;

            ruleStr += "," + fieldName + "," +
                    ff.getTitle() + "," + cNull;
            if (!"".equals(ff.getRule())) {
                ruleStr += "," + ff.getRule();
            }
            // 因为pck是根据规则从fu从取出ff的值，故只能取出日期部分，而不能取出时间部分，所以不能用format=yyyy-MM-dd HH:mm:ss，而应用format=yyyy-MM-dd
            // 20220130 老版的时间控件有此限制，而新版的却没有，故应采用format=yyyy-MM-dd HH:mm:ss
            ruleStr += ",format=yyyy-MM-dd HH:mm:ss";
            // ruleStr += ",format=yyyy-MM-dd";
            pck.checkFieldDate(ruleStr);
            
            if (doMoreThanCheck && !"".equals(StrUtil.getNullStr(ff.getMoreThan()))) {
            	Date value = DateUtil.parse(fu.getFieldValue(fieldName), FormField.FORMAT_DATE_TIME);
            	Date mt = DateUtil.parse(fu.getFieldValue(getMoreThanFieldName(ff, fieldName)), FormField.FORMAT_DATE_TIME);
            	checkFieldDate(ff, pck, fu, value, mt);
            }
        }
        else if (ff.getFieldType() == FormField.FIELD_TYPE_PRICE) {
            if (ff.isCanNull()) {
                cNull = "allow";
            }
            ruleStr = ParamChecker.TYPE_DOUBLE;

            ruleStr += "," + fieldName + "," +
                    ff.getTitle() + "," + cNull;
            if (!"".equals(ff.getRule())) {
                ruleStr += "," + ff.getRule();
            }
            pck.checkFieldPrice(ruleStr);
            
            if (doMoreThanCheck && !"".equals(StrUtil.getNullStr(ff.getMoreThan()))) {
            	double value = StrUtil.toDouble(fu.getFieldValue(fieldName));
            	double mt = StrUtil.toDouble(fu.getFieldValue(getMoreThanFieldName(ff, fieldName)));
            	checkFieldNum(ff, pck, value, mt);
            }
        }
        else {
            throw new IllegalArgumentException("表单域 " + ff.getTitle() + " " + fieldName + " 的类型不正确：" + ff.getFieldType());
        }
    }

    public static String getMoreThanFieldName(FormField ff, String fieldName) {
        // 当来自于嵌套表格时
        if (!fieldName.startsWith("nest_field_")) {
            return ff.getMoreThan();
        } else {
            String rowId = fieldName.substring(fieldName.lastIndexOf("_"));
            return "nest_field_" + ff.getMoreThan() + rowId;
        }
    }

    /**
     * 判断是否存在重复记录
     * @param fields
     * @param fu
     * @param ifdao
     */
    public static void checkFieldIsUnique(Vector<FormField> fields, FileUpload fu, IFormDAO ifdao) throws ResKeyException {
        String formCode = "";
        String sql = "select id from ft_%s where %s and cws_status=1";
        StringBuffer sb = new StringBuffer();
        StringBuffer sbFields = new StringBuffer();
        int c = 0;
        for (FormField ff : fields) {
            if (ff.isUnique()) {
                if ("".equals(formCode)) {
                    formCode = ff.getFormCode();
                }
                StrUtil.concat(sb, " and ", ff.getName() + "=?");
                StrUtil.concat(sbFields, "+", ff.getTitle());
                c++;
            }
        }
        sql = String.format(sql, formCode, sb.toString());
        if (c>0) {
            String[] ary = new String[c];
            int i = 0;
            for (FormField ff : fields) {
                if (ff.isUnique()) {
                    ary[i] = fu.getFieldValue(ff.getName());
                    i++;
                }
            }

            JdbcTemplate jt = new JdbcTemplate();
            try {
                ResultIterator ri = jt.executeQuery(sql, ary);
                if (ifdao==null) {
                    if (ri.size() > 0) {
                        // 当智能模块创建记录时，如果库中记录数大于0，则存在重复记录
                        throw new ResKeyException("res.module.flow", "err_is_not_unique", new Object[]{sbFields.toString()});
                    }
                }
                else {
                    if (ri.size() > 1) {
                        // 存在重复记录
                        throw new ResKeyException("res.module.flow", "err_is_not_unique", new Object[]{sbFields.toString()});
                    }
                    else if (ri.size()==1) {
                        if (ri.hasNext()) {
                            ResultRecord rr = (ResultRecord)ri.next();
                            if (rr.getLong(1) != ifdao.getId()) {
                                // 如果不是记录本身，则存在重复记录
                                throw new ResKeyException("res.module.flow", "err_is_not_unique", new Object[]{sbFields.toString()});
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                DebugUtil.log(FormDAOMgr.class, "checkFieldIsUnique", sql);
                LogUtil.getLog(FormDAOMgr.class).error(e);
            }
        }
    }

    /**
     * 判断嵌套表格宏控件全局是否存在重复记录
     * @param fields
     * @param fu
     */
    public static void checkFieldIsUniqueInNestTable(Vector<FormField> fields, FileUpload fu, String[] rowIds) throws ResKeyException {
        String formCode = "";
        String sql = "select id from ft_%s where %s";
        StringBuffer sb = new StringBuffer();
        StringBuffer sbFields = new StringBuffer();
        int c = 0;
        for (FormField ff : fields) {
            if (ff.isUnique()) {
                if ("".equals(formCode)) {
                    formCode = ff.getFormCode();
                }
                StrUtil.concat(sb, " and ", ff.getName() + "=?");
                StrUtil.concat(sbFields, "+", ff.getTitle());
                c++;
            }
        }
        sql = String.format(sql, formCode, sb.toString());
        if (c>0) {
            for (String strRowId : rowIds) {
                int rowId = StrUtil.toInt(strRowId, -1);
                String[] ary = new String[c];
                int i = 0;
                for (FormField ff : fields) {
                    if (ff.isUnique()) {
                        ary[i] = fu.getFieldValue("nest_field_" + ff.getName() + "_" + rowId);
                        i++;
                    }
                }

                JdbcTemplate jt = new JdbcTemplate();
                try {
                    ResultIterator ri = jt.executeQuery(sql, ary);
                    if (ri.size() > 0) {
                        throw new ResKeyException("res.module.flow", "err_is_not_unique", new Object[]{sbFields.toString()});
                    }
                } catch (SQLException e) {
                    DebugUtil.log(FormDAOMgr.class, "checkFieldIsUnique", sql);
                    LogUtil.getLog(FormDAOMgr.class).error(e);
                }
            }
        }
    }

    /**
     * 判断是否存在重复记录
     * @param fields
     * @param fu
     * @param ifdao
     */
    public static void checkFieldIsUniqueNest(HttpServletRequest request, Vector<FormField> fields, FileUpload fu, IFormDAO ifdao) throws ResKeyException {
        String parentFormCode = com.redmoon.oa.visual.FormDAOMgr.getParentFormCode(request);
        String cwsId = StrUtil.getNullStr(fu.getFieldValue("cws_id"));

        String formCode = "";
        String sql = "select id from ft_%s where %s and cws_id=? and cws_parent_form=?";
        StringBuffer sb = new StringBuffer();
        StringBuffer sbFields = new StringBuffer();
        int c = 0;
        for (FormField ff : fields) {
            if (ff.isUniqueNest()) {
                if ("".equals(formCode)) {
                    formCode = ff.getFormCode();
                }
                StrUtil.concat(sb, " and ", ff.getName() + "=?");
                StrUtil.concat(sbFields, "+", ff.getTitle());
                c++;
            }
        }
        sql = String.format(sql, formCode, sb.toString());
        if (c>0) {
            String[] ary = new String[c + 2];
            int i = 0;
            for (FormField ff : fields) {
                if (ff.isUniqueNest()) {
                    ary[i] = fu.getFieldValue(ff.getName());
                    i++;
                }
            }
            ary[i] = cwsId;
            ary[i+1] = parentFormCode;

            JdbcTemplate jt = new JdbcTemplate();
            try {
                ResultIterator ri = jt.executeQuery(sql, ary);
                if (ifdao==null) {
                    // 如果嵌套唯一，在添加时，数据库中查的是cws_id=-2的临时记录，如果在嵌套表中添了以后删掉，然后再添加时就会报重复
                    if (ri.size() > 0) {
                        // 当智能模块创建记录时，如果库中记录数大于0，则存在重复记录
                        throw new ResKeyException("res.module.flow", "err_is_not_unique", new Object[]{sbFields.toString()});
                    }
                }
                else {
                    if (ri.size() > 1) {
                        // 存在重复记录
                        throw new ResKeyException("res.module.flow", "err_is_not_unique", new Object[]{sbFields.toString()});
                    }
                    else if (ri.size()==1) {
                        if (ri.hasNext()) {
                            ResultRecord rr = (ResultRecord)ri.next();
                            if (rr.getLong(1) != ifdao.getId()) {
                                // 如果不是记录本身，则存在重复记录
                                throw new ResKeyException("res.module.flow", "err_is_not_unique", new Object[]{sbFields.toString()});
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                DebugUtil.log(FormDAOMgr.class, "checkFieldIsUniqueNest", sql);
                LogUtil.getLog(FormDAOMgr.class).error(e);
            }
        }
    }

    /**
     * 当拉单时检查字段是否有重复，注意并不检查ids中是否有重复的记录
     * @param request 请求
     */
    public static boolean checkFieldIsUniqueNestOnPull(HttpServletRequest request, long parentId, String parentFormCode, FormDb sourceFormDb, FormDb nestFormDb, String[] ids) {
        com.redmoon.oa.visual.FormDAO sourceFormDao = new com.redmoon.oa.visual.FormDAO();
        StringBuffer sb = new StringBuffer();
        StringBuffer sbFields = new StringBuffer();
        int c = 0;
        for (FormField ff : nestFormDb.getFields()) {
            if (ff.isUniqueNest()) {
                StrUtil.concat(sb, " and ", ff.getName() + "=?");
                StrUtil.concat(sbFields, "+", ff.getTitle());
                c++;
            }
        }
        // 检查ids中将要拉取的记录中，是否与嵌套表中记录中的字段有重复
        String sql = "select id from ft_" + nestFormDb.getCode() + " where %s and cws_id=? and cws_parent_form=?";
        sql = String.format(sql, sb.toString());

        for (String strId : ids) {
            long id = StrUtil.toLong(strId, -1);
            sourceFormDao = sourceFormDao.getFormDAOByCache(id, sourceFormDb);

            if (c>0) {
                String[] ary = new String[c + 2];
                int i = 0;
                for (FormField ff : nestFormDb.getFields()) {
                    if (ff.isUniqueNest()) {
                        ary[i] = sourceFormDao.getFieldValue(ff.getName());
                        i++;
                    }
                }
                ary[i] = String.valueOf(parentId);
                ary[i+1] = parentFormCode;

                JdbcTemplate jt = new JdbcTemplate();
                try {
                    ResultIterator ri = jt.executeQuery(sql, ary);
                    if (ri.size() >= 1) {
                        // 存在重复记录
                        return false;
                    }
                } catch (SQLException e) {
                    DebugUtil.log(FormDAOMgr.class, "checkFieldIsUniqueNest", sql);
                    LogUtil.getLog(FormDAOMgr.class).error(e);
                }
            }
        }
        return true;
    }

    /**
     * 根据表单设计时定义的规则对表单域进行有效性验证
     * @param request HttpServletRequest
     * @param fu FileUpload
     * @param fields Vector 可写表单域
     * @param ifdao IFormDAO 当在模块中create时为null，其它时候存在值
     * @param isFlow boolean true-流程中 false-模块中
     * @throws ErrMsgException
     */
    public static void validateFields(HttpServletRequest request, FileUpload fu, Vector<FormField> fields, IFormDAO ifdao, boolean isFlow) throws ErrMsgException {
        ParamChecker pck = new ParamChecker(request, fu);
        MacroCtlMgr mm = new MacroCtlMgr();

        for (FormField ff : fields) {
            if (ff.getHide() != FormField.HIDE_NONE) {
                continue;
            }

            if (ff.isFunc()) {
                continue;
            }

            try {
                checkField(request, fu, pck, ff, ff.getName(), ifdao);
            } catch (CheckErrException e) {
                // 如果onError=exit，则会抛出异常
                throw new ErrMsgException(e.getMessage());
            }

            try {
                // 判断是否存在重复记录
                // ifdao为null表示当不在流程中，而是通过智能模块创建记录时
                checkFieldIsUnique(fields, fu, ifdao);
            } catch (ResKeyException e) {
                throw new ErrMsgException(e.getMessage(request));
            }

            try {
                // 判断作为嵌套表是否存在重复记录
                // ifdao为null表示当不在流程中，而是通过智能模块创建记录时
                checkFieldIsUniqueNest(request, fields, fu, ifdao);
            } catch (ResKeyException e) {
                throw new ErrMsgException(e.getMessage(request));
            }

            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                // 验证宏控件的值，如文号控件中需检查是否有重复，基础数据宏控件中所选的是否为树形结构末级的节点
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu != null) {
                    IFormMacroCtl iFormMacroCtl = mu.getIFormMacroCtl();
                    if (iFormMacroCtl!=null) {
                        mu.getIFormMacroCtl().validate(request, ifdao, ff, fu);
                    }
                    else {
                        DebugUtil.i(FormDAOMgr.class, "validateFields", "MacroCtl is not exist, macroType=" + ff.getMacroType());
                    }
                }
            }

            /*
             * 20180904 fgf 注释，本段代码移至NestSheetCtl的setValueForValidate中
            // 检查嵌套表格2是否存在数据
            if (!ff.isCanNull() && ff.getType().equals(FormField.TYPE_MACRO)) {
            	MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
	            if (mu!=null && mu.getNestType() == MacroCtlUnit.NEST_TYPE_NORMAIL) {            
            		// 为了向下兼容
            		String nestFormCode = ff.getDescription();
            		String nestFieldName = ff.getName();
            		try {
            			// 20131123 fgf 添加
            			String defaultVal = StrUtil.decodeJSON(ff.getDescription());
            			JSONObject json = new JSONObject(defaultVal);
            			nestFormCode = json.getString("destForm");
            		} catch (JSONException e) {
            			// LogUtil.getLog(getClass()).error(e);
            			LogUtil.getLog(FormDAOMgr.class).info(nestFormCode + " is old version before 20131123. ff.getDefaultValueRaw()=" + ff.getDefaultValueRaw());
            		}	
            		
            		if (ifdao!=null) {
	            		// 检查是否存在数据
	            		String sql = "select id from ft_" + nestFormCode + " where cws_id=?";
	            		JdbcTemplate jt = new JdbcTemplate();
	            		try {
							ResultIterator ri = jt.executeQuery(sql, new Object[]{ifdao.getId()});
							if (ri.size()==0) {
								FormDb nestFd = new FormDb();
								nestFd = nestFd.getFormDb(nestFormCode);
				                throw new ErrMsgException(nestFd.getName() + "的数据不能为空！");
							}
						} catch (SQLException e) {
							LogUtil.getLog(getClass()).error(e);
						}
            		}
            		else {
            			// 当在模块添加的时候，ifdao为null
                    	// 检查从模块是否有加入表单的临时temp_cws_ids_formCode
                        String fName = com.redmoon.oa.visual.FormDAO.NAME_TEMP_CWS_IDS + "_" + nestFormCode;
                        String[] ary = fu.getFieldValues(fName);
                        if (ary!=null && ary.length>0) {
                        	;
                        }    
                        else {
							FormDb nestFd = new FormDb();
							nestFd = nestFd.getFormDb(nestFormCode);	                        	
			                throw new ErrMsgException(nestFd.getName() + "的数据不能为空！");
                        }
            		}
	            }
	        }*/
        }

        if (pck.getMsgs().size()!=0) {
            throw new ErrMsgException(pck.getMessage(false));
        }
    }

    /**
     * 校验 验证规则 中的嵌套表格字段
     * @param request
     * @param flowId
     * @param fdsWritable
     * @param fdao
     * @throws ErrMsgException
     */
    public void validateNestFields(HttpServletRequest request, int flowId, String[] fdsWritable, FormDAO fdao) throws ErrMsgException {
        boolean isUseFilter = false;
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);
        WorkflowPredefineDb wpd = new WorkflowPredefineDb();
        wpd = wpd.getDefaultPredefineFlow(wf.getTypeCode());
        if (wpd.isModuleFilter()) {
            isUseFilter = true;
        }
        String userName = SpringUtil.getUserName();
        ModuleSetupDb msd = new ModuleSetupDb();

        if (fdsWritable!=null) {
            for (String fieldName : fdsWritable) {
                if (!fieldName.startsWith("nest.")) {
                    continue;
                }

                fieldName = fieldName.substring("nest.".length());

                // 取得嵌套表
                Vector<FormField> v = fdao.getFormDb().getFields();
                for (FormField ff : v) {
                    if (ff.getType().equals(FormField.TYPE_MACRO)) {
                        // LogUtil.getLog(getClass()).info(getClass() + " ff.getMacroType()=" + ff.getMacroType());
                        // 此处对nest_table不做检查，因为此时nest_table还没有得到保存，且其在保存时本身也会做检查
                        if (ConstUtil.NEST_SHEET.equals(ff.getMacroType())) {
                            String nestFormCode = ff.getDescription();
                            try {
                                String defaultVal = StrUtil.decodeJSON(ff.getDescription());
                                JSONObject json = new JSONObject(defaultVal);
                                nestFormCode = json.getString("destForm");
                            } catch (JSONException e) {
                                LogUtil.getLog(getClass()).error(e);
                            }

                            msd = msd.getModuleSetupDb(nestFormCode);
                            FormDb nestfd = new FormDb();
                            nestfd = nestfd.getFormDb(msd.getString("form_code"));
                            // 取得嵌套表中的数据
                            String sql = "select id from " + nestfd.getTableNameByForm() + " where cws_id='" + fdao.getId() + "'";
                            sql += " and cws_parent_form=" + StrUtil.sqlstr(ff.getFormCode());

                            if (isUseFilter) {
                                // 因为在提交流程验证时，request中没有flowId（在fileUpload中），当parseFilter时直接从request中取flowId是取不到的，故此处通过request.setAttribute来传递flowId
                                request.setAttribute("flowId", flowId);

                                /*String[] ary = ModuleUtil.parseFilter(request, msd.getString("form_code"), StrUtil.getNullStr(msd.getFilter(userName)));
                                if (ary != null) {
                                    String filter = ary[0];
                                    if (filter != null && !"".equals(filter)) {
                                        if (filter.toLowerCase().startsWith("select ")) {
                                            sql = filter;
                                        } else {
                                            sql += " and " + filter;
                                        }
                                    }
                                }*/
                            }

                            DebugUtil.i(getClass(), "validateNestFields sql", sql);

                            com.redmoon.oa.visual.FormDAO fdaoNest = new com.redmoon.oa.visual.FormDAO();
                            Vector<com.redmoon.oa.visual.FormDAO> vNest = fdaoNest.list(nestFormCode, sql);
                            for (com.redmoon.oa.visual.FormDAO formDAO : vNest) {
                                fdaoNest = formDAO;

                                Vector<FormField> fields = fdaoNest.getFields();
                                for (FormField nestFf : fields) {
                                    if (nestFf.getName().equals(fieldName)) {
                                        // 如果不能为空，则检查
                                        if (!nestFf.isCanNull()) {
                                            if (StringUtils.isEmpty(nestFf.getValue())) {
                                                throw new ErrMsgException(String.format(SkinUtil.LoadString(request, "res.checker", "err_blank_nest"), nestFf.getTitle()));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void checkFieldIsUniqueNestAll(HttpServletRequest request, FormDAO fdao) throws ResKeyException {
        // 取得嵌套表
        String parentFormCode = fdao.getFormCode();
        long parentId = fdao.getId();
        Vector<FormField> v = fdao.getFormDb().getFields();
        for (FormField ffPar : v) {
            if (ffPar.getType().equals(FormField.TYPE_MACRO)) {
                // LogUtil.getLog(getClass()).info(getClass() + " ff.getMacroType()=" + ff.getMacroType());
                // 此处对nest_table不做检查，因为此时nest_table还没有得到保存，且其在保存时本身也会做检查
                if (ConstUtil.NEST_SHEET.equals(ffPar.getMacroType())) {
                    String nestFormCode = ffPar.getDescription();
                    try {
                        String defaultVal = StrUtil.decodeJSON(ffPar.getDescription());
                        JSONObject json = new JSONObject(defaultVal);
                        nestFormCode = json.getString("destForm");
                    } catch (JSONException e) {
                        LogUtil.getLog(FormDAOMgr.class).error(e);
                    }

                    FormDb fdNest = new FormDb();
                    fdNest = fdNest.getFormDb(nestFormCode);

                    Vector<FormField> fields = fdNest.getFields();
                    String sql = "select %s from ft_%s where cws_id=? and cws_parent_form=?";
                    List<FormField> nestList = new ArrayList<>();
                    StringBuffer sb = new StringBuffer();
                    int c = 0;
                    for (FormField ff : fields) {
                        if (ff.isUniqueNest()) {
                            nestList.add(ff);
                            StrUtil.concat(sb, " and ", ff.getName());
                            c++;
                        }
                    }

                    if (c > 0) {
                        sql = String.format(sql, sb.toString(), nestFormCode);
                        Map<String, List<String>> map = new HashMap<>();
                        JdbcTemplate jt = new JdbcTemplate();
                        try {
                            ResultIterator ri = jt.executeQuery(sql, new Object[]{String.valueOf(parentId), parentFormCode});
                            while (ri.hasNext()) {
                                ResultRecord rr = ri.next();
                                for (FormField formField : nestList) {
                                    String fName = formField.getName();
                                    if (map.containsKey(fName)) {
                                        List<String> fieldValList = map.get(fName);
                                        if (fieldValList.contains(rr.getString(fName))) {
                                            throw new ResKeyException("res.module.flow", "err_is_not_unique", new Object[]{formField.getTitle() + ": " + rr.getString(fName)});
                                        } else {
                                            fieldValList.add(rr.getString(fName));
                                        }
                                    } else {
                                        List<String> fieldValList = new ArrayList<>();
                                        fieldValList.add(rr.getString(fName));
                                        map.put(fName, fieldValList);
                                    }
                                }
                            }
                        } catch (SQLException e) {
                            DebugUtil.log(FormDAOMgr.class, "checkFieldIsUniqueNestOnSave", sql);
                            LogUtil.getLog(FormDAOMgr.class).error(e);
                        }
                    }
                }
            }
        }
    }

    /**
     * 保存表单域的值，因为android上控件因不能写被删除替换时，表单域不会再post过来，注释本方法20130329 fgf
     * @param request HttpServletRequest
     * @return boolean
     * @throws ErrMsgException
     */
    /*
    public boolean update(HttpServletRequest request) throws ErrMsgException {
        Vector fields = fd.getFields();

        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);
        Directory dir = new Directory();
        Leaf lf = dir.getLeaf(wf.getTypeCode());

        Vector fieldsWritable = new Vector();

        if (lf.getType() == Leaf.TYPE_FREE) {
            // 自由流程根据用户所属的角色，得到可写表单域
            Privilege pvg = new Privilege();
            String userName = pvg.getUser(request);
            WorkflowPredefineDb wfpd = new WorkflowPredefineDb();
            wfpd = wfpd.getPredefineFlowOfFree(wf.getTypeCode());

            String[] fds = wfpd.getFieldsWriteOfUser(wf, userName);
            int len = fds.length;

            Iterator ir = fields.iterator();
            // 将可写的域筛选出
            while (ir.hasNext()) {
                FormField ff = (FormField) ir.next();
                for (int i = 0; i < len; i++) {
                    if (ff.getName().equals(fds[i])) {
                        fieldsWritable.addElement(ff);
                        break;
                    }
                }
            }
        }
        else {
            // 预设流程根据动作中的设定得到可写表单域
            String fieldWrite = StrUtil.getNullString(action.getFieldWrite()).trim();
            String[] fds = fieldWrite.split(",");
            int len = fds.length;
            Iterator ir = fields.iterator();
            // 将可写的域筛选出
            while (ir.hasNext()) {
                FormField ff = (FormField) ir.next();
                for (int i = 0; i < len; i++) {
                    if (ff.getName().equals(fds[i])) {
                        fieldsWritable.addElement(ff);
                        break;
                    }
                }
            }
        }

        // 对可写表单域进行有效性验证
        validateFields(request, fu, fieldsWritable);

        // 从request中获取表单中域的值
        fields = getFieldsByForm(request);

        // 对fields中的值进行有效性判断
        FormValidatorConfig fvc = new FormValidatorConfig();
        IFormValidator ifv = fvc.getIFormValidatorOfForm(formCode);
        // logger.info("ifv=" + ifv + " formCode=" + formCode);
        boolean re = true;
        if (ifv!=null)
            re = ifv.validate(request, fu, flowId, fields);
        if (!re)
            throw new ErrMsgException("表单验证非法！");
        FormDAO fdao = new FormDAO(flowId, fd);
        fdao.setFields(fields);

        // 用于在NestTableCtl的saveForNestCtl中
        request.setAttribute("action", action);
        return fdao.save(request, fu);
    }
    */

    public boolean update(HttpServletRequest request) throws ErrMsgException {
    	return update(request, false);
    }
    
   /**
    * 保存表单域的值，因为android WAP上控件因不能写被删除替换时，表单域不会再post过来，修改了本方法20130329 fgf
    * 在本方法中，只获取writable的控件的值并保存
    * @param request HttpServletRequest
    * @return boolean
    * @throws ErrMsgException
     */
   public boolean update(HttpServletRequest request, boolean isSaveDraft) throws ErrMsgException {
       FormDAO fdao = new FormDAO();
       fdao = fdao.getFormDAOByCache(flowId, fd);
       if (fdao == null || !fdao.isLoaded()) {
           throw new ErrMsgException("流程：" + flowId + " 对应的记录已不存在");
       }

       Vector<FormField> fields = fdao.getFields();

       WorkflowDb wf = new WorkflowDb();
       wf = wf.getWorkflowDb(flowId);
       Directory dir = new Directory();
       Leaf lf = dir.getLeaf(wf.getTypeCode());

       Vector<FormField> fieldsWritable = new Vector<>();

       Privilege pvg = new Privilege();
       String userName = pvg.getUser(request);
       String[] fds;

       if (lf.getType() == Leaf.TYPE_FREE) {
           // 自由流程根据用户所属的角色，得到可写表单域
           WorkflowPredefineDb wfpd = new WorkflowPredefineDb();
           wfpd = wfpd.getPredefineFlowOfFree(wf.getTypeCode());

           fds = wfpd.getFieldsWriteOfUser(wf, userName);
           int len = fds.length;

           // 将可写的域筛选出
           for (FormField ff : fields) {
               ff.setEditable(false);
               for (String s : fds) {
                   if (ff.getName().equals(s)) {
                       ff.setEditable(true);
                       fieldsWritable.addElement(ff);
                       break;
                   }
               }
           }
       }
       else {
           // 预设流程根据动作中的设定得到可写表单域
           String fieldWrite = StrUtil.getNullString(action.getFieldWrite()).trim();
           fds = fieldWrite.split(",");
           // 将可写的域筛选出
           for (FormField ff : fields) {
               ff.setEditable(false);
               // fgf 20160928 部门选择框控件也加入进去，否则当不可写时，虽然默认为本部门，看似有数据，其实没能保存下来
               if ("macro_dept_select".equals(ff.getMacroType())) {
                   // fgf 20170905 发现当用admin发起流程的时候，因为其所在部门为空，结果当字段不允许为空时会报提示，所以在此排除掉admin发起的情况
                   if (!userName.equals(ConstUtil.USER_ADMIN)) {
                       ff.setEditable(true);
                       fieldsWritable.addElement(ff);
                       continue;
                   }
               }

               for (String s : fds) {
                   if (ff.getName().equals(s)) {
                       ff.setEditable(true);
                       fieldsWritable.addElement(ff);
                       break;
                   }
               }
           }
       }
       
       /*
       	2018-1-1 fgf 注释掉，从request中获取可写表单中域的值，但是这样会导致手机端传过来的不可写字段disabled原来有值的会变为空值
       	所以在getFieldsByForm中判断，如果得到的值为null，则说明是不可写且被disabled的字段，则不应对其赋值，仍保留原来的值
        因为当disabled的时候，表单域的值不会被post至服务器
        因为表单域选择宏控件会映射字段，例如：当映射的是文本框型字段不可写时，在手机端时文本框是readonly的，那么提交后这个字段应该要保存，所以不能仅保存可写字段，
        不可写的字段如果有值也应保存，如果为null则说明可能是disabled，仍保留其原来的值
       */
       // getFieldsByForm(request, fieldsWritable);
       getFieldsByForm(request, fields);

       // 如果是保存草稿，则不进行有效性验证
       if (!isSaveDraft) {
    	   // 对可写表单域进行有效性验证
    	   validateFields(request, fu, fieldsWritable, fdao, true);
    	   // 20230531 改为对不可写字段也验证，以免可能有字段在浏览器中因为卡住未能显示而致丢失，但不能这么改，比如：认定结果是不可写的，可能为空，但却是必填项
           // validateFields(request, fu, fields, fdao, true);

           // 对嵌套表单的可写表单域进行有效性验证，因为拉单后，可能有必填项未填
    	   validateNestFields(request, flowId, fds, fdao);

           // 因为手机端不能checkFieldIsUniqueNestOnPull，故在此再次检查唯一性
           try {
               checkFieldIsUniqueNestAll(request, fdao);
           } catch (ResKeyException e) {
               throw new ErrMsgException(e.getMessage(request));
           }

           // 对fields中的值进行有效性判断
	       FormValidatorConfig fvc = new FormValidatorConfig();
	       IFormValidator ifv = fvc.getIFormValidatorOfForm(formCode);
	       // logger.info("ifv=" + ifv + " formCode=" + formCode);
	
	       // 调用脚本，置FileUpload，以便于在form_js_***.jsp中提取数据
	       // setAttrivue无效，接收不到
	       // request.setAttribute("FileUpload", fu);
	       // request.setAttribute("fields", fields);
	
	       boolean re = true;
	       
	       if (ifv!=null && ifv.isUsed()) {
	           re = ifv.validate(request, fu, flowId, fields);
	       }
	       
	       if (!re) {
               throw new ErrMsgException(LocalUtil.LoadString(request,"res.flow", "formValidError"));//"表单验证非法！");
           }
	       
  	       // 注意如果被重复选择的话，要到流程结束时才会把标志位置为1，中间可能会有延误的情况，所以在发起时就应该判断
           int actionId = StrUtil.toInt(fu.getFieldValue("actionId"), -1);
           WorkflowActionDb wa = new WorkflowActionDb();
           wa = wa.getWorkflowActionDb(actionId);
           // if ("1".equals(wa.getItem1())) { 	       // 根据是否结束节点，判断是否已被冲抵
	        	// 对嵌套表格2中的冲抵进行检查，检查是否存在已经被冲抵的记录
               MacroCtlMgr mm = new MacroCtlMgr();
               for (FormField macroField : fdao.getFields()) {
                   if (macroField.getType().equals(FormField.TYPE_MACRO)) {
                       MacroCtlUnit mu = mm.getMacroCtlUnit(macroField.getMacroType());
                       if (mu == null) {
                           DebugUtil.e(getClass(), macroField.getTitle() + "宏控件类型" , macroField.getMacroType() + "不存在");
                           continue;
                       }
                       if ("module_field_select".equals(mu.getCode())) {
                           MacroCtlService macroCtlService = SpringUtil.getBean(MacroCtlService.class);
                           IModuleFieldSelectCtl moduleFieldSelectCtl = macroCtlService.getModuleFieldSelectCtl();
                           com.alibaba.fastjson.JSONObject jsonDesc = moduleFieldSelectCtl.getCtlDescription(macroField);
                           boolean isAgainst = false;
                           String sourceFormCode="", idField="";
                           isAgainst = jsonDesc.getBoolean("isAgainst");
                           sourceFormCode = jsonDesc.getString("sourceFormCode");
                           idField = jsonDesc.getString("idField");
                           if (isAgainst && !StrUtil.isEmpty(macroField.getValue())) {
                               // 判断是否已被冲抵
                               String sql = "select id,cws_flag from ft_" + sourceFormCode + " where cws_flag=1 and " + idField + "=?";
                               try {
                                   JdbcTemplate jt = new JdbcTemplate();
                                   ResultIterator ri = jt.executeQuery(sql, new Object[]{macroField.getValue()});
                                   if (ri.size() > 0) {
                                       StringBuffer ids = new StringBuffer();
                                       while (ri.hasNext()) {
                                           ResultRecord rr = ri.next();
                                           StrUtil.concat(ids, "，", String.valueOf(rr.getLong(1)));
                                       }
                                       throw new ErrMsgException(sourceFormCode + " 中的记录已被冲抵，源记录ID为" + ids + "！");
                                   }
                               } catch (SQLException e) {
                                   LogUtil.getLog(getClass()).error(e);
                               }

                               // 此处进一步判断被选择的数据在其它已发起的流程中也被选择，则判断为存在冲突
                               sql = "select flowId from " + FormDb.getTableName(formCode) + " where flowId<>" + flowId + " and flowid<>-1 and " + macroField.getName() + "=?";
                               try {
                                   JdbcTemplate jt = new JdbcTemplate();
                                   ResultIterator ri = jt.executeQuery(sql, new Object[]{macroField.getValue()});
                                   if (ri.size() > 0) {
                                       while (ri.hasNext()) {
                                           ResultRecord rr = ri.next();
                                           int flowId = rr.getInt(1);
                                           WorkflowDb wfd = new WorkflowDb();
                                           wfd = wfd.getWorkflowDb(flowId);
                                           if (wfd.getStatus() == WorkflowDb.STATUS_STARTED) {
                                               throw new ErrMsgException("其它流程中存在已被引用的记录，流程ID为" + flowId + "！");
                                           }
                                       }
                                   }
                               } catch (SQLException e) {
                                   LogUtil.getLog(getClass()).error(e);
                               }
                           }
                       }
                       else if (mu.getNestType() == MacroCtlUnit.NEST_TYPE_NORMAIL) {
                           String formCode = macroField.getDescription();
                           boolean isAgainst = false;
                           String sourceForm = "";
                           try {
                               String defaultVal = StrUtil.decodeJSON(formCode);
                               JSONObject json = new JSONObject(defaultVal);
                               formCode = json.getString("destForm");
                               sourceForm = json.getString("sourceForm");
                               isAgainst = "1".equals(json.getString("isAgainst"));
                               if (isAgainst) {
                                   // 判断是否已被冲抵
                                   ModuleSetupDb msd = new ModuleSetupDb();
                                   msd = msd.getModuleSetupDb(sourceForm);
                                   String sql = "select a.id from " + FormDb.getTableName(msd.getString("form_code")) + " a, " + FormDb.getTableName(formCode) + " b where a.id=b.cws_quote_id and b.cws_id='" + fdao.getId() + "' and a.cws_flag=1";
                                   try {
                                       JdbcTemplate jt = new JdbcTemplate();
                                       ResultIterator ri = jt.executeQuery(sql);
                                       if (ri.size() > 0) {
                                           StringBuffer ids = new StringBuffer();
                                           while (ri.hasNext()) {
                                               ResultRecord rr = ri.next();
                                               StrUtil.concat(ids, "，", String.valueOf(rr.getLong(1)));
                                           }
                                           throw new ErrMsgException(sourceForm + " 中的记录已被冲抵，源记录ID为" + ids + "！");
                                       }
                                   } catch (SQLException e) {
                                       LogUtil.getLog(getClass()).error(e);
                                   }

                                   // 此处进一步判断被引用的数据在其它已发起的流程中也被引用，则判断为存在冲突
                                   sql = "select flowId from " + FormDb.getTableName(formCode) + " where cws_flag=0 and flowid<>-1 and cws_id<>" + fdao.getId() + " and cws_quote_id in (select cws_quote_id from " + FormDb.getTableName(formCode) + " where cws_id=" + StrUtil.sqlstr(String.valueOf(fdao.getId())) + ")";
                                   try {
                                       JdbcTemplate jt = new JdbcTemplate();
                                       ResultIterator ri = jt.executeQuery(sql);
                                       if (ri.size() > 0) {
                                           while (ri.hasNext()) {
                                               ResultRecord rr = ri.next();
                                               int flowId = rr.getInt(1);
                                               WorkflowDb wfd = new WorkflowDb();
                                               wfd = wfd.getWorkflowDb(flowId);
                                               if (wfd.getStatus() == WorkflowDb.STATUS_STARTED) {
                                                   throw new ErrMsgException("其它流程中存在已被引用的记录，流程ID为" + flowId + "！");
                                               }
                                           }
                                       }
                                   } catch (SQLException e) {
                                       LogUtil.getLog(getClass()).error(e);
                                   }
                               }
                           } catch (JSONException e) {
                               // LogUtil.getLog(getClass()).error(e);
                               LogUtil.getLog(getClass()).info("update:" + formCode + " is old version before 20131123. ff.getDefaultValueRaw()=" + macroField.getDefaultValueRaw());
                           }
                       }
                   }
               }
           // }	       
           /* 20190922 去掉验证，因为已被“验证规则”取代
	       // 根据模块中的验证配置进行验证
	       ModuleSetupDb msd = new ModuleSetupDb();
	       msd = msd.getModuleSetupDbOrInit(fd.getCode());
	       String validateProp = StrUtil.getNullStr(msd.getString("validate_prop"));
	       if (!"".equals(validateProp)) {
	       		String cond = ModuleUtil.parseValidate(request, fu, fdao, validateProp);
	       		ScriptEngineManager manager = new ScriptEngineManager();
	       		ScriptEngine engine = manager.getEngineByName("javascript");
	       		try {
	       			Boolean ret = (Boolean)engine.eval(cond);
	       			if (!ret.booleanValue()) {
                        String msg = msd.getString("validate_msg");
                        if ("".equals(msg)) {
                            msg = LocalUtil.LoadString(request,"res.flow.Flow","validError");
                        }
	       				throw new ErrMsgException(StrUtil.getNullStr(msg));
	       			}
	       		}
	       		catch (ScriptException ex) {
	       			LogUtil.getLog(getClass()).error(ex);
	       		}        	
	       }
	       */
       }
       
       if (!isSaveDraft) {
         runValidateScript(request, pvg, wf, fdao, action, false);
       }
       
       // 对函数型的表单域赋值
       for (FormField ff : fields) {
           if (ff.isFunc()) {
               ff.setValue(String.valueOf(FuncUtil.render(ff, ff.getDefaultValueRaw(), fdao)));
           }
       }       
       
       // 用于在NestTableCtl的saveForNestCtl中
       request.setAttribute("action", action);
	   // 用于在fdao.save中判断，如果此时流程为未发起状态，则应修改表单状态为STATUS_NOT
       request.setAttribute("isSaveDraft", isSaveDraft);
       boolean re = fdao.save(request, fu);
       if (re) {
           // 如果不是保存草稿（即为提交流程），且需要记录历史
           if (!isSaveDraft && fd.isLog()) {
               FormDAO.log(userName, FormDAOLog.LOG_TYPE_EDIT, fdao);
           }
       }
       
       // 如果表单中含有“表单域选择宏控件”，且当前模块是该宏控件对应的表单的从模块，则自动关联
       // 例如：单独添加合同付款，而不是先打开合同，再添加付款记录，此时则自动关联合同
       
       // 宏控件为智能模块表单型，如其formCode不为空，则表单编码为其formCode，如为空，则由控件自行去解析
       // 相应地需要在宏控件中增加虚方法getFormCode()
 	
       ModuleRelateDb mrd = new ModuleRelateDb();
       MacroCtlMgr mm = new MacroCtlMgr();
       for (FormField ff : fields) {
           if (ff.getType().equals(FormField.TYPE_MACRO)) {
               if (ff.getValue() == null || "".equals(ff.getValue())) {
                   continue;
               }

               // 如果不是long类型的，则说明字段中存储的不是ID，字段对应的不可能是主模块
               long mainFormId = StrUtil.toLong(ff.getValue(), -1);
               if (mainFormId == -1) {
                   continue;
               }
               MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
               if (mu.isForm()) {
                   String mainFormCode = mu.getFormCode();
                   if ("".equals(mainFormCode)) {
                       mainFormCode = StrUtil.getNullStr(mu.getIFormMacroCtl()
                               .getFormCode(request, ff));
                   }
                   if ("".equals(mainFormCode)) {
                       continue;
                   }

                   boolean isRelateToMainForm = false;
                   // 检查该mainFormCode是否为当前表单的主表单
                   for (Object o : mrd.getModulesRelated(mainFormCode)) {
                       mrd = (ModuleRelateDb) o;
                       if (mrd.getString("relate_code").equals(formCode)) {
                           isRelateToMainForm = true;
                           break;
                       }
                   }
                   if (isRelateToMainForm) {
                       com.redmoon.oa.visual.FormDAOMgr fdmMain = new com.redmoon.oa.visual.FormDAOMgr(mainFormCode);
                       String relateId = fdmMain.getRelateFieldValue(mainFormId, mrd.getString("relate_code"));
                       fdao.setCwsId(relateId);
                       fdao.save();
                   }
               }
           }
       }
       
       return re;
    }
   
    /**
     * 用于调试
     * @param request
     * @param flowId
     * @param actionId
     * @return
     * @throws ErrMsgException
     */
  	public BSHShell runValidateScript(HttpServletRequest request, int flowId, int actionId) throws ErrMsgException {
  		WorkflowDb wf = new WorkflowDb();
  		wf = wf.getWorkflowDb(flowId);
  		
  		Leaf lf = new Leaf();
  		lf = lf.getLeaf(wf.getTypeCode());
  		
  		FormDb fd = new FormDb();
  		fd = fd.getFormDb(lf.getFormCode());
  		
  		FormDAO fdao = new FormDAO();
  		fdao = fdao.getFormDAOByCache(flowId, fd);
  		
  		WorkflowActionDb action = new WorkflowActionDb();
  		action = action.getWorkflowActionDb(actionId);
  		
  		return runValidateScript(request, new Privilege(), wf, fdao, action, true);
  	}

    /**
     * 运行验证脚本
     *
     * @param request
     * @param pvg
     * @param wf
     * @param fdao
     * @throws ErrMsgException
     */
    public BSHShell runValidateScript(HttpServletRequest request, Privilege pvg, WorkflowDb wf, FormDAO fdao, WorkflowActionDb action, boolean isTest) throws ErrMsgException {
        IWorkflowScriptUtil workflowScriptUtil = SpringUtil.getBean(IWorkflowScriptUtil.class);
        return workflowScriptUtil.runValidateScript(request, pvg, wf, fdao, action, isTest, fu);
    }

    /**
     * 运行删除验证脚本
     * @param request
     * @param pvg
     * @param wf
     * @param fdao
     * @throws ErrMsgException
     */
    public BSHShell runDeleteValidateScript(HttpServletRequest request, Privilege pvg, WorkflowDb wf, FormDAO fdao, WorkflowActionDb action, boolean isTest) throws ErrMsgException {
        IWorkflowScriptUtil workflowScriptUtil = SpringUtil.getBean(IWorkflowScriptUtil.class);
        return workflowScriptUtil.runDeleteValidateScript(request, pvg, wf, fdao, action, isTest);
    }

    public boolean del(HttpServletRequest request) {
        FormDAO fdao = new FormDAO(flowId, fd);
        return fdao.del();
    }

    public void setAction(WorkflowActionDb action) {
        this.action = action;
    }

    public WorkflowActionDb getAction() {
        return action;
    }

    private WorkflowActionDb action;
}
