package com.redmoon.oa.flow.macroctl;

import cn.js.fan.util.ParamUtil;
import cn.js.fan.util.StrUtil;
import com.redmoon.oa.flow.FormField;
import com.redmoon.oa.flow.WorkflowDb;

import javax.servlet.http.HttpServletRequest;

/**
 * <p>Title: 流程宏控件</p>
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
public class WorkflowCtl extends AbstractMacroCtl {
    public WorkflowCtl() {
    }

    @Override
    public String convertToHTMLCtl(HttpServletRequest request, FormField ff) {
        String style = "";
        if (!"".equals(ff.getCssWidth())) {
            style = " style='width:" + ff.getCssWidth() + "'";
        }
        else {
            style = " style='width:150px'";
        }

        int flowId;
        String val = ff.getValue();
        // 如果没有值，则说明以前未赋过值，从参数中获取
        if (val==null) {
	        flowId = ParamUtil.getInt(request, "flowId", -1);
	        if (flowId==-1) {
	        	flowId = StrUtil.toInt(ff.getValue(), -1);
	            if (flowId==-1) {
                    String str = "<input name='" + ff.getName() + "' value='" + flowId + "'";
                    if (ff.isReadonly()) {
                        str += " readonly='readonly' " + " readOnlyType='" + ff.getReadOnlyType() + "'";
                    }
                    str += style + " />";
                    return str;
                }
	        }
	        // 如果与当前流程ID一致（在flow_modify.jsp中查看流程时），说明流程控件中保存的值是空的
        	int curFlowId = StrUtil.toInt((String)request.getAttribute("cwsId"), -1);
	        if (curFlowId==flowId) {
	        	return "";
	        }
        }
        else {
        	flowId = StrUtil.toInt(val, -1);
            if (flowId==-1) {
                String str = "<input name='" + ff.getName() + "' value='" + flowId + "'";
                if (ff.isReadonly()) {
                    str += " readonly='readonly' " + " readOnlyType='" + ff.getReadOnlyType() + "'";
                }
                str += style + " />";
                return str;
            }
        }
        
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);
        if (wf==null) {
            return "流程" + flowId + "不存在";
        }
        
        com.redmoon.oa.sso.Config config = new com.redmoon.oa.sso.Config();
        String desKey = config.get("key");
        
        // 以flowId作为值加密
        String visitKey = cn.js.fan.security.ThreeDesUtil.encrypt2hex(desKey, String.valueOf(flowId));
        
        String str = "<a href='javascript:;' title='流程号: " + wf.getId() + "' onclick=\"openWinFlowShow(" + wf.getId() + ", '" + visitKey + "')\">" + wf.getTitle() + "</a>";
        str += "<input name='" + ff.getName() + "' value='" + flowId + "' type='hidden' />";
        return str;
    }
    
    /**
     * 用于列表中显示宏控件的值
     * @param request HttpServletRequest
     * @param ff FormField
     * @param fieldValue String
     * @return String
     */
    @Override
    public String converToHtml(HttpServletRequest request, FormField ff, String fieldValue) {
        String val = StrUtil.getNullStr(fieldValue);
        if (!"".equals(val)) {
        	int flowId = StrUtil.toInt(val);
            WorkflowDb wf = new WorkflowDb();
            wf = wf.getWorkflowDb(flowId);
            
	        com.redmoon.oa.sso.Config config = new com.redmoon.oa.sso.Config();
	        String desKey = config.get("key");			
	        // 以flowId作为值加密
	        String visitKey = cn.js.fan.security.ThreeDesUtil.encrypt2hex(desKey, String.valueOf(wf.getId()));
            return "<a href='javascript:;' title='流程号: " + wf.getId() + "' onclick=\"openWinFlowShow(" + wf.getId() + ", '" + visitKey + "')\">" + wf.getTitle() + "</a>";
        }
        else {
            return "";
        }
    }    

    @Override
    public String getDisableCtlScript(FormField ff, String formElementId) {
        String str = "if (o('" + ff.getName() + "'))\r\n";
        str += "fo('" + ff.getName() + "').style.display='none';\r\n";
        return str;
    }

    /**
     * 当report时，取得用来替换控件的脚本
     * @param ff FormField
     * @return String
     */
    @Override
    public String getReplaceCtlWithValueScript(FormField ff) {
        return "ReplaceCtlWithValue('" + ff.getName() +"', '" + ff.getType() + "', '');\n";
    }

    /**
     * 必须重载此方法，否则setCtlValue('task', 'macro', flowForm.cws_textarea_task.value)会将控件值置为空，当为必填项时通不过
     * @param request HttpServletRequest
     * @param ff FormField
     * @return String
     */
    @Override
    public String getOuterHTMLOfElementsWithRAWValueAndHTMLValue(
            HttpServletRequest request, FormField ff) {
        FormField ffNew = new FormField();
        ffNew.setName(ff.getName());
        ffNew.setValue(ff.getValue());
        ffNew.setType(ff.getType());
        ffNew.setFieldType(ff.getFieldType());
        ffNew.setValue("");

        long flowId = ParamUtil.getLong(request, "flowId", -1);
        if (flowId!=-1) {
            ffNew.setValue("" + flowId);
        }
        else {
        	flowId = StrUtil.toLong(ff.getValue(), -1);
            if (flowId!=-1) {
                ffNew.setValue("" + flowId);
            }
        }

        return super.getOuterHTMLOfElementsWithRAWValueAndHTMLValue(request, ffNew);
    }

	@Override
	public String getControlOptions(String userName, FormField ff) {
		return "";
	}

	@Override
	public String getControlText(String userName, FormField ff) {
        String val = StrUtil.getNullStr(ff.getValue());
        if (!"".equals(val)) {
            int flowId = StrUtil.toInt(val);
            WorkflowDb wf = new WorkflowDb();
            wf = wf.getWorkflowDb(flowId);
            return wf.getTitle();
        }
		return "";
	}

	@Override
	public String getControlType() {
		return "text";
	}

	@Override
	public String getControlValue(String userName, FormField ff) {
		return StrUtil.getNullStr(ff.getValue());
	}
}

