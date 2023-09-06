package com.redmoon.oa.flow.macroctl;

import cn.js.fan.util.StrUtil;
import com.redmoon.oa.flow.FormField;

import javax.servlet.http.HttpServletRequest;


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
public class ColorPickerWinProCtl extends AbstractMacroCtl {
    public ColorPickerWinProCtl() {
    }

    @Override
	public String convertToHTMLCtl(HttpServletRequest request, FormField ff) {
		String pageType = (String)request.getAttribute("pageType");
		String val = StrUtil.getNullStr(ff.getValue());
    	/*String bgclr = "";
    	if (!"".equals(val)) {
    		bgclr = "background-color:" + val;
    	}*/
		String style = "";
		if (!"".equals(ff.getCssWidth())) {
			style = "style='width:" + ff.getCssWidth() + "'";
		} else {
			style = "style='width:130px'";
		}
    	String str = "";   	
    	str += "<div id='" + ff.getName() + "_show' name='" + ff.getName() + "_show' style='display:inline-block;vertical-align:middle; width:16px; height:16px; border:0px solid #cccccc;cursor:pointer;margin-right:5px;'></div>";
        str += "<input id='" + ff.getName() + "' name='" + ff.getName() + "' " + style + " value='" + val + "'/>";
    	if ((pageType!=null && !pageType.contains("show")) && ff.isEditable()) {
			str += "<script>\n";
			str += "var colorPicker = new ewPlugins('colorpicker', {\n";
			str += "	el: \"#" + ff.getName() + "_show\",\n";
			str += "	alpha: true,//是否开启透明度\n";
			str += "	size: 'mini',//颜色box类型 normal,medium,small,mini\n";
			str += "	predefineColor: ['#223456', 'rgba(122,35,77,.5)','rgba(255,255,255,1)'],//预定义颜色\n";
			str += "	disabled: false,//是否禁止打开颜色选择器\n";
			str += "	openPickerAni: 'opacity',//打开颜色选择器动画\n";
			str += "	defaultColor:'" + val + "',//默认颜色\n";
			str += "	sure: function (color) {\n";
			// str += "		fo('" + ff.getName() + "_show').style.background = color;\n";
			str += "		fo('" + ff.getName() + "').value = color;\n";
			str += "	},//点击确认按钮事件回调\n";
			str += "	clear: function () {\n";
			// str += "		fo('" + ff.getName() + "_show').style.background = this.defaultColor;\n";
			str += "		fo('" + ff.getName() + "').value = this.defaultColor;\n";
			str += "	}//点击清空按钮事件回调\n";
			str += "});\n";
			str += "</script>\n";
		}
        return str;
    }
    
    @Override
	public String getDisableCtlScript(FormField ff, String formElementId) {
    	String displayVal = "";
/*    	if (!"".equals(ff.getValue())) {
        	String bgclr = "background-color:" + ff.getValue();
        	displayVal = "<div style='width:20px; height: 20px; " + bgclr + "'></div>";
    	}   */
		return "DisableCtl('" + ff.getName() + "', '" + ff.getType() +
							"','" + displayVal + "','" + ff.getValue() + "');\n";
    }    
    
	@Override
	public String getReplaceCtlWithValueScript(FormField ff) {
		String displayVal = "";
		return "ReplaceCtlWithValue('" + ff.getName() + "', '" + ff.getType()
				+ "','" + displayVal + "');\n";
	}

    @Override
	public String getControlType() {
        return "text";
    }

    @Override
	public String getControlValue(String userName, FormField ff) {
        return ff.getValue();
    }

    @Override
	public String getControlText(String userName, FormField ff) {
        return ff.getValue();
    }

    @Override
	public String getControlOptions(String userName, FormField ff) {
        return "";
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
    	return fieldValue;
    }

    @Override
	public String getValueForExport(HttpServletRequest request, FormField ff, String fieldValue) {
    	return fieldValue;
	}
    
}

