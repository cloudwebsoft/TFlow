package com.redmoon.oa.flow.macroctl;

import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.pvg.Privilege;
import cn.js.fan.util.StrUtil;
import java.util.Iterator;
import javax.servlet.http.HttpServletRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.redmoon.oa.flow.FormField;
import com.redmoon.oa.person.UserDb;
import com.redmoon.oa.pvg.RoleDb;

/**
 * <p>
 * Title: 角色中的用户选择
 * </p>
 * 
 * <p>
 * Description: 支持多个角色，角色编码可以逗号分隔
 * </p>
 * 
 * <p>
 * Copyright: Copyright (c) 2007
 * </p>
 * 
 * <p>
 * Company:
 * </p>
 * 
 * @author not attributable
 * @version 1.0
 */
public class RoleUserSelectCtl extends AbstractMacroCtl {
	public RoleUserSelectCtl() {
	}

	@Override
	public String convertToHTMLCtl(HttpServletRequest request, FormField ff) {
		String str = "";
		str += "<select id='" + ff.getName() + "' name='" + ff.getName() + "'>";
		str += "<option value=''>请选择</option>";

		Privilege pvg = new Privilege();
		String userName = pvg.getUser(request);

		RoleDb rd = new RoleDb();
		String[] ary = StrUtil.split(ff.getDescription(), ",");
		if (ary == null) {
			return "控件中未设角色编码！";
		}
		for (String s : ary) {
			rd = rd.getRoleDb(s);
			if (!rd.isLoaded()) {
				str += "<option value=''>角色已不存在</option>";
			} else {
				// 置为当前用户
				for (UserDb ud : rd.getAllUserOfRole()) {
					if (userName.equals(ud.getName())) {
						str += "<option selected value='" + ud.getName() + "'>"
								+ ud.getRealName() + "</option>";
					} else {
						str += "<option value='" + ud.getName() + "'>"
								+ ud.getRealName() + "</option>";
					}
				}
			}
		}
		str += "</select>";

		return str;
	}

	@Override
    public String convertToHTMLCtlForQuery(HttpServletRequest request,
                                           FormField ff) {
		String str = "";
		str += "<select id='" + ff.getName() + "' name='" + ff.getName() + "'>";
		str += "<option value=''>无</option>";

		Privilege pvg = new Privilege();
		String userName = pvg.getUser(request);

		RoleDb rd = new RoleDb();
		String[] ary = StrUtil.split(ff.getDescription(), ",");
		for (int i = 0; i < ary.length; i++) {
			rd = rd.getRoleDb(ary[i]);
			if (!rd.isLoaded()) {
				str += "<option value=''>角色已不存在</option>";
			} else {
				Iterator ir = rd.getAllUserOfRole().iterator();
				// 置为当前用户
				while (ir.hasNext()) {
					UserDb ud = (UserDb) ir.next();
					if (userName.equals(ud.getName())) {
						str += "<option selected value='" + ud.getName() + "'>"
								+ ud.getRealName() + "</option>";
					} else {
						str += "<option value='" + ud.getName() + "'>"
								+ ud.getRealName() + "</option>";
					}
				}
			}
		}
		str += "</select>";
		return str;
	}

	/**
	 * 获取用来保存宏控件原始值的表单中的HTML元素，通常为textarea
	 * 
	 * @return String
	 */
	@Override
    public String getOuterHTMLOfElementsWithRAWValueAndHTMLValue(
			HttpServletRequest request, FormField ff) {
		// 检查如果没有赋值就赋予其当前用户名称
		/*if (StrUtil.getNullStr(ff.getValue()).equals("")) {
			//Privilege privilege = new Privilege();
			//UserDb ud = new UserDb();
			//ud = ud.getUserDb(privilege.getUser(request));
			// ff.setValue(ud.getRealName());
			//ff.setValue(privilege.getUser(request));
		}*/

		return super
				.getOuterHTMLOfElementsWithRAWValueAndHTMLValue(request, ff);
	}

	/**
	 * 获取用来保存宏控件toHtml后的值的表单中的HTML元素中保存的值，生成用以禁用控件的脚本
	 * 
	 * @return String
	 */
	public String getDisableCtlScript(FormField ff, String formElementId) {
		String realName = "";
		if (ff.getValue() != null) {
			UserDb ud = new UserDb();
			ud = ud.getUserDb(ff.getValue());
			if (ud.isLoaded())
				realName = ud.getRealName();
		}
		String str = "DisableCtl('" + ff.getName() + "', '" + ff.getType()
				+ "','" + realName + "','" + StrUtil.getNullStr(ff.getValue()) + "');\n";

		return str;
	}

	/**
	 * 当report时，取得用来替换控件的脚本
	 * 
	 * @param ff
	 *            FormField
	 * @return String
	 */
	public String getReplaceCtlWithValueScript(FormField ff) {
		String realName = "";
		if (ff.getValue() != null) {
			UserDb ud = new UserDb();
			ud = ud.getUserDb(ff.getValue());
			if (ud.isLoaded())
				realName = ud.getRealName();
		}
		return "ReplaceCtlWithValue('" + ff.getName() + "', '" + ff.getType()
				+ "','" + realName + "');\n";
	}
	
    @Override
	public Object getValueForCreate(int flowId, FormField ff) {
        return ""; // ff.getDefaultValue();
    }	

	/**
	 * 用于模块列表中显示宏控件的值
	 * 
	 * @param request
	 *            HttpServletRequest
	 * @param ff
	 *            FormField 表单域的描述，其中的value值为空
	 * @param fieldValue
	 *            String 表单域的值
	 * @return String
	 */
	public String converToHtml(HttpServletRequest request, FormField ff,
			String fieldValue) {
		String realName = "";
		if (fieldValue != null && !fieldValue.equals("")) {
			UserDb ud = new UserDb();
			ud = ud.getUserDb(fieldValue);
			if (ud.isLoaded())
				realName = ud.getRealName();
		}

		return realName;
	}

	public String getControlType() {
		return "select";
	}

	public String getControlValue(String userName, FormField ff) {
		String value = StrUtil.getNullStr(ff.getValue());
		if (value.trim().equals("")) {
			return "";
		}
		return value;

	}

	public String getControlText(String userName, FormField ff) {
		String v = "";
		if (!StrUtil.getNullStr(ff.getValue()).equals("")) {
			UserDb userDb = new UserDb(ff.getValue());
			v = userDb.getRealName();
		}
		return v;
	}

	public String getControlOptions(String userName, FormField ff) {
		RoleDb rd = new RoleDb();
		String[] ary = StrUtil.split(ff.getDescription(), ",");
		JSONArray jsonArr = new JSONArray();
		try {
			if (ary != null && ary.length > 0) {
				for (int i = 0; i < ary.length; i++) {
					rd = rd.getRoleDb(ary[i]);
					if (rd.isLoaded()) {
						Iterator ir = rd.getAllUserOfRole().iterator();
						// 置为当前用户
						while (ir.hasNext()) {
							UserDb ud = (UserDb) ir.next();
							JSONObject userObj = new JSONObject();
							userObj.put("value", ud.getName());
							userObj.put("name", ud.getRealName());// 客户名称
							jsonArr.put(userObj);
						}
					}
				}

			}
		} catch (JSONException e) {
			LogUtil.getLog(getClass()).error(e.getMessage());
		}

		return jsonArr.toString();
	}

	/**
	 * 根据名称取值，用于导入Excel数据
	 * 
	 * @return
	 */
	@Override
    public String getValueByName(FormField ff, String name) {
		UserDb user = new UserDb();
		user = user.getUserDbByRealName(name);
		return user.getName();
	}

}
