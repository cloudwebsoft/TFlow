package com.redmoon.oa.flow.macroctl;

import com.cloudweb.oa.permission.ModuleTreePermission;
import com.cloudweb.oa.utils.ConstUtil;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.basic.TreeSelectDb;
import com.redmoon.oa.basic.TreeSelectView;
import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.StrUtil;
import java.util.Vector;
import javax.servlet.http.HttpServletRequest;
import com.redmoon.oa.flow.FormField;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

/**
 * <p>Title: 文件柜目录选择</p>
 *
 * <p>Description: </p>
 *
 * <p>Copyright: Copyright (c) 2007</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class FilearkDirSelectCtl extends AbstractMacroCtl {
    public FilearkDirSelectCtl() {
    }

    @Override
    public String convertToHTMLCtl(HttpServletRequest request, FormField ff) {
		String rCode = StrUtil.getNullStr(ff.getDescription());
		if ("".equals(rCode)) {
            rCode = ConstUtil.FILEARK_DIR;
        }

        TreeSelectDb tsd = new TreeSelectDb();
        tsd = tsd.getTreeSelectDb(rCode);
		
        StringBuffer sb = new StringBuffer();
        if (!tsd.isLoaded()) {
        	sb.append("节点" + rCode + "不存在！");
        }
		sb.append("<select id='" + ff.getName() + "' name='" + ff.getName() + "' onChange=\"if(this.options[this.selectedIndex].value==''){alert(this.options[this.selectedIndex].text+' 不能被选择！'); return false;}\">");
		sb.append("<option value=''>请选择</option>");

        TreeSelectView tsv = new TreeSelectView(tsd);
        try {
            tsv.getTreeSelectAsOptions(sb, tsd, 1);
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
        }
	
		sb.append("</select>");
        return sb.toString();
    }

    @Override
    public String convertToHTMLCtlForQuery(HttpServletRequest request,
                                           FormField ff) {
		String rCode = StrUtil.getNullStr(ff.getDescription());
		if ("".equals(rCode)) {
            rCode = ConstUtil.FILEARK_DIR;
        }
        TreeSelectDb tsd = new TreeSelectDb();
        tsd = tsd.getTreeSelectDb(rCode);
		
        StringBuffer sb = new StringBuffer();
        if (!tsd.isLoaded()) {
        	sb.append("节点" + rCode + "不存在！");
        }
        else {
	        sb.append("<select id='" + ff.getName() + "' name='" + ff.getName() + "'>");
	        sb.append("<option value=''>无</option>");

            TreeSelectView tsv = new TreeSelectView(tsd);
            try {
                tsv.getTreeSelectAsOptions(sb, tsd, 1);
            } catch (ErrMsgException e) {
                LogUtil.getLog(getClass()).error(e);
            }

	        sb.append("</select>");
        }
        return sb.toString();
    }

    /**
     * 获取用来保存宏控件原始值的表单中的HTML元素，通常为textarea
     * @return String
     */
    @Override
    public String getOuterHTMLOfElementsWithRAWValueAndHTMLValue(
            HttpServletRequest request,
            FormField ff) {
        return super.getOuterHTMLOfElementsWithRAWValueAndHTMLValue(request, ff);
    }

    /**
     * 获取用来保存宏控件toHtml后的值的表单中的HTML元素中保存的值，生成用以禁用控件的脚本
     * @return String
     */
    @Override
    public String getDisableCtlScript(FormField ff, String formElementId) {
        String desc = "";
        if (!StrUtil.isEmpty(ff.getValue())) {
            TreeSelectDb tsd = new TreeSelectDb();
            tsd = tsd.getTreeSelectDb(ff.getValue());
            if (tsd.isLoaded()) {
                desc = tsd.getName();
            }
        }

        return "DisableCtl('" + ff.getName() + "', '" + ff.getType() +
                     "','" + desc + "','" + ff.getValue() + "');\n";
    }

    /**
     * 当report时，取得用来替换控件的脚本
     * @param ff FormField
     * @return String
     */
    @Override
    public String getReplaceCtlWithValueScript(FormField ff) {
        String desc = "";
        if (!StrUtil.isEmpty(ff.getValue())) {
            TreeSelectDb tsd = new TreeSelectDb();
            tsd = tsd.getTreeSelectDb(ff.getValue());
            if (tsd.isLoaded()) {
                desc = tsd.getName();
            }
        }
        return "ReplaceCtlWithValue('" + ff.getName() + "', '" + ff.getType() +
                "','" + desc + "');\n";
    }

    public Object getValueForCreate(FormField ff) {
        return ff.getValue();
    }

    /**
     * 用于模块列表中显示宏控件的值
     * @param request HttpServletRequest
     * @param ff FormField 表单域的描述，其中的value值为空
     * @param fieldValue String 表单域的值
     * @return String
     */
    @Override
    public String converToHtml(HttpServletRequest request, FormField ff, String fieldValue) {
        String desc = "";
        if (!StrUtil.isEmpty(ff.getValue())) {
            TreeSelectDb tsd = new TreeSelectDb();
            tsd = tsd.getTreeSelectDb(ff.getValue());
            if (tsd.isLoaded()) {
                desc = tsd.getName();
            }
        }

        return desc;
    }

    @Override
    public String getControlType() {
        return "select";
    }

    @Override
    public String getControlValue(String userName, FormField ff) {
        if (!"".equals(StrUtil.getNullStr(ff.getValue()))) {
            return ff.getValue();
        }else{
            return StrUtil.getNullStr(ff.getDefaultValue());
        }
    }

    @Override
    public String getControlText(String userName, FormField ff) {
        if (!StrUtil.isEmpty(ff.getValue())) {
            TreeSelectDb tsd = new TreeSelectDb();
            tsd = tsd.getTreeSelectDb(ff.getValue());
            if (tsd.isLoaded()) {
                return tsd.getName();
            }
        }
        return "";
    }

    public JSONArray getDirNameAsOptions(String dirCode, JSONArray childrens, boolean isInclude) throws ErrMsgException {
        ModuleTreePermission moduleTreePermission = SpringUtil.getBean(ModuleTreePermission.class);
        // 是否包含该目录
        if (isInclude) {
            if (moduleTreePermission.canSee(SpringUtil.getUserName(), dirCode)) {
                childrens.put(getDirNameAsOptionValue(dirCode));
            }
        }

        TreeSelectDb tsd = new TreeSelectDb();
        tsd = tsd.getTreeSelectDb(dirCode);
        Vector<TreeSelectDb> children = tsd.getChildren();
        int size = children.size();
        if (size == 0) {
            return childrens;
        }

        /*
         * 遍历获取子节点的部门
         */
        for (TreeSelectDb childlf : children) {
            if (moduleTreePermission.canSee(SpringUtil.getUserName(), childlf.getCode())) {
                getDirNameAsOptions(childlf.getCode(), childrens, true);
            }
        }
        return childrens;
    }

    public JSONObject getDirNameAsOptionValue(String dirCode) {
        TreeSelectDb tsd = new TreeSelectDb();
        tsd = tsd.getTreeSelectDb(dirCode);
        JSONObject children = new JSONObject();
        try {
            String deptName = "";
            int layer = tsd.getLayer();
            String blank = "";
            int d = layer-1;
            for (int i=0; i<d; i++) {
                blank += "　";
            }
            if (tsd.getChildCount()>0) {
                deptName = blank + "╋ " + tsd.getName();
            }
            else {
                deptName += blank + "├ " + tsd.getName();
            }

            children.put("name", deptName);
            children.put("value", tsd.getCode());
            children.put("parentCode", tsd.getParentCode());
        } catch (JSONException ex) {
            LogUtil.getLog(getClass()).error(ex);
        }
        return children;
    }

    @Override
    public String getControlOptions(String userName, FormField ff) {
        String res = "";
        JSONArray childrens = new JSONArray();
        try {
            res = getDirNameAsOptions(ConstUtil.FILEARK_DIR, childrens, true).toString();

            JSONArray ary = new JSONArray();
            JSONObject children = new JSONObject();
            children.put("name", ConstUtil.NONE);
            children.put("value", "");
            ary.put(children);

            JSONArray jsonAry = new JSONArray(res);
            for (int i=0; i<jsonAry.length(); i++) {
                ary.put(jsonAry.get(i));
            }
            res = ary.toString();
        } catch (ErrMsgException | JSONException ex1) {
            ex1.printStackTrace();
        }
        return res;
    }
}
