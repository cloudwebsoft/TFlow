<%@ page contentType="text/html;charset=utf-8" %>
<%@ page import="java.util.*" %>
<%@ page import="cn.js.fan.util.*" %>
<%@ page import="org.json.*" %>
<%@ page import="cn.js.fan.web.*" %>
<%@ page import="com.cloudwebsoft.framework.db.*" %>
<%@ page import="com.redmoon.oa.util.*" %>
<%@ page import="com.redmoon.oa.basic.*" %>
<%@ page import="com.redmoon.oa.pvg.*" %>
<%@ page import="com.redmoon.oa.person.*" %>
<%@ page import="com.redmoon.oa.ui.*" %>
<%@ page import="com.redmoon.oa.flow.*" %>
<%@ page import="com.redmoon.oa.visual.*" %>
<%@ page import="com.redmoon.oa.flow.macroctl.*" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.json.JSONArray" %>
<%@ page import="com.redmoon.oa.Config" %>
<%@ page import="org.apache.http.client.utils.URIBuilder" %>
<%@ page import="com.cloudweb.oa.utils.ConstUtil" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="com.redmoon.oa.sys.DebugUtil" %>
<%@ page import="com.cloudweb.oa.utils.SpringUtil" %>
<%@ page import="com.cloudweb.oa.api.ICloudUtil" %>
<%@ page import="com.cloudwebsoft.framework.util.IPUtil" %>
<%@ page import="com.cloudweb.oa.api.IBasicSelectCtl" %>
<%@ page import="com.cloudweb.oa.service.MacroCtlService" %>
<%@ page import="com.cloudweb.oa.utils.JarFileUtil" %>
<%
	String op = ParamUtil.get(request, "op");
	String code = ParamUtil.get(request, "moduleCode"); // 模块编码
	if ("".equals(code)) {
		code = ParamUtil.get(request, "code");
	}
	String formCode = ParamUtil.get(request, "formCode");
	Config cfg = new Config();
	boolean isServerConnectWithCloud = cfg.getBooleanProperty("isServerConnectWithCloud");
	String url = cfg.get("cloudUrl");
	URIBuilder uriBuilder = new URIBuilder(url);
	String host = uriBuilder.getHost();
	int port = uriBuilder.getPort();
	if (port == -1) {
		port = 80;
	}
	String path = uriBuilder.getPath();
	if (path.startsWith("/")) {
		path = path.substring(1);
	}

	ICloudUtil cloudUtil = SpringUtil.getBean(ICloudUtil.class);
	String userSecret = cloudUtil.getUserSecret();
	String ip = IPUtil.getRemoteAddr(request);
%>
<!DOCTYPE html>
<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
	<title>模块设置</title>
	<link type="text/css" rel="stylesheet" href="<%=SkinMgr.getSkinPath(request)%>/css.css"/>
	<link href="../lte/css/font-awesome.min.css?v=4.4.0" rel="stylesheet"/>
	<link rel="stylesheet" href="../js/layui/css/layui.css" media="all">
	<style>
		.role-sel-btn {
			vertical-align: baseline;
			width: 24px;
		}
	</style>
	<script src="../inc/common.js"></script>
	<script src="../inc/livevalidation_standalone.js"></script>
	<script src="<%=request.getContextPath()%>/js/jquery-1.9.1.min.js"></script>
	<script src="<%=request.getContextPath()%>/js/jquery-migrate-1.2.1.min.js"></script>
	<script src="../inc/map.js"></script>

	<link rel="stylesheet" href="<%=request.getContextPath()%>/js/bootstrap/css/bootstrap.min.css"/>
	<script src="<%=request.getContextPath()%>/js/bootstrap/js/bootstrap.min.js"></script>

	<script src="../js/select2/select2.js"></script>
	<link href="../js/select2/select2.css" rel="stylesheet"/>

	<script src="../js/jquery.toaster.js"></script>

	<script src="../js/jquery-alerts/jquery.alerts.js" type="text/javascript"></script>
	<script src="../js/jquery-alerts/cws.alerts.js" type="text/javascript"></script>
	<link href="../js/jquery-alerts/jquery.alerts.css" rel="stylesheet" type="text/css" media="screen"/>

	<link type="text/css" rel="stylesheet" href="<%=SkinMgr.getSkinPath(request)%>/flexbox/flexbox.css"/>
	<script type="text/javascript" src="../js/jquery.flexbox.js"></script>

	<link href="../js/jquery-showLoading/showLoading.css" rel="stylesheet" media="screen"/>
	<script type="text/javascript" src="../js/jquery-showLoading/jquery.showLoading.js"></script>
	<script type="text/javascript" src="../inc/livevalidation_standalone.js"></script>

	<script type="text/javascript" src="../js/formpost.js"></script>
	<script src="../js/json2.js"></script>
	<script type="text/javascript" src="../js/activebar2.js"></script>
	<script src="../js/layui/layui.js" charset="utf-8"></script>

	<script>
		function window_onload() {
			getFieldOfForm($('#otherFormCode').val());
		}

		var errFunc = function (response) {
			window.status = response.responseText;
		}

		function doGetField(response) {
			var rsp = response.responseText.trim();
			$('#spanField').html(rsp);
			$('#otherShowField').append("<option value='id'>ID</option>");
			$('#otherField').append("<option value='cws_id'>cws_id</option>");
		}

		function getFieldOfForm(formCode) {
			var str = "formCode=" + formCode;
			var myAjax = new cwAjax.Request(
					"module_field_ajax.jsp",
					{
						method: "post",
						parameters: str,
						onComplete: doGetField,
						onError: errFunc
					}
			);
		}
	</script>
</head>
<body onload="window_onload()">
<jsp:useBean id="privilege" scope="page" class="com.redmoon.oa.pvg.Privilege"/>
<%
if (!privilege.isUserPrivValid(request, "admin.flow")) {
	out.print(cn.js.fan.web.SkinUtil.makeErrMsg(request, cn.js.fan.web.SkinUtil.LoadString(request, "pvg_invalid")));
	return;
}

String tabIdOpener = ParamUtil.get(request, "tabIdOpener");

ModuleSetupDb vsd = new ModuleSetupDb();
vsd = vsd.getModuleSetupDb(code);
if (vsd==null) {
	out.print(SkinUtil.makeErrMsg(request, "模块：" + code + "不存在"));
	return;
}
else {
	formCode = vsd.getString("form_code");
}

FormMgr fm = new FormMgr();
FormDb fd = fm.getFormDb(formCode);
if (!fd.isLoaded()) {
	out.print(StrUtil.jAlert_Back("表单不存在！","提示"));
	return;
}

int work_log = vsd.getInt("is_workLog");

String strProps = vsd.getString("props");
com.alibaba.fastjson.JSONObject props = com.alibaba.fastjson.JSONObject.parseObject(strProps);
if (props == null) {
	props = new com.alibaba.fastjson.JSONObject();
}%>
<%@ include file="module_setup_inc_menu_top.jsp"%>
<script>
o("menu1").className="current";
</script>
<div class="spacerH"></div>
<%
String listField = StrUtil.getNullStr(vsd.getString("list_field"));
String[] fields = vsd.getColAry(true, "list_field");

String listFieldWidth = StrUtil.getNullStr(vsd.getString("list_field_width"));
String[] fieldsWidth = vsd.getColAry(true, "list_field_width");

String listFieldOrder = StrUtil.getNullStr(vsd.getString("list_field_order"));
String[] fieldOrder = vsd.getColAry(true, "list_field_order");

String listFieldLink = StrUtil.getNullStr(vsd.getString("list_field_link"));
String[] fieldsLink = vsd.getColAry(true, "list_field_link");

String listFieldShow = StrUtil.getNullStr(vsd.getString("list_field_show"));
String[] fieldsShow = vsd.getColAry(true, "list_field_show");

String listFieldTitle = StrUtil.getNullStr(vsd.getString("list_field_title"));
String[] fieldsTitle = vsd.getColAry(true, "list_field_title");

String listFieldAlign = StrUtil.getNullStr(vsd.getString("list_field_align"));
String[] fieldsAlign = vsd.getColAry(true, "list_field_align");

int len = 0;
if (fields!=null) {
	len = fields.length;
}

int i;
%>
<table cellSpacing="0" class="tabStyle_1 percent98" cellPadding="3" width="95%" align="center">
	<tr>
		<td>
			<jsp:include page="module_field_inc_preview.jsp">
				<jsp:param name="code" value="<%=code%>"/>
				<jsp:param name="formCode" value="<%=formCode%>"/>
				<jsp:param name="from" value="module_field_list"/>
			</jsp:include>
		</td>
	</tr>
</table>

<form id="formModuleFilter" method="post" name="frmFilter" onsubmit="return frmFilter_onsbumit()">
<table cellspacing="0" class="tabStyle_1 percent98" cellpadding="3" width="95%" align="center">
    <tr>
      <td align="center"  class="tabStyle_1_title">过滤条件</td>
    </tr>
    <tr>
      <td width="91%" align="left" >
<%
String filter = StrUtil.getNullStr(vsd.getString("filter")).trim();
boolean isComb = filter.startsWith("<items>") || filter.equals("");
String cssComb = "", cssScript = "";
String kind;
if (isComb) {
	cssComb = "in active";
	kind = "comb";
}
else {
	cssScript = "in active";
	kind = "script";
	%>
	<script>
	$(function() {
		$('#trOrderBy').hide();
	});
	</script>
	<%
}
%>
<ul id="myTab" class="nav nav-tabs">
   <li class="dropdown active">
      <a href="#" id="myTabDrop1" class="dropdown-toggle" data-toggle="dropdown">
         	条件<b class="caret"></b></a>
      <ul class="dropdown-menu" role="menu" aria-labelledby="myTabDrop1">
         <li><a href="#comb" kind="comb" tabindex="-1" data-toggle="tab">组合条件</a></li>
         <li><a href="#script" kind="script" tabindex="-1" data-toggle="tab">脚本条件</a></li>
      </ul>
   </li>
</ul>
<div id="myTabContent" class="tab-content">
   <div class="tab-pane fade <%=cssComb %>" id="comb">
   		<div style="margin:10px">
      		<img src="../admin/images/combination.png" style="margin-bottom:-5px;"/>&nbsp;<a href="javascript:;" onclick="openCondition(o('condition'), o('imgId'))">配置条件</a>&nbsp;
      		<img src="../admin/images/gou.png" style="margin-bottom:-5px;width:20px;height:20px;display:<%=(isComb && !filter.equals(""))?"":"none" %>;" id="imgId"/>
      		<textarea id="condition" name="condition" style="display:none" cols="80" rows="5"><%=filter %></textarea>
		</div>
   </div>
   <div class="tab-pane fade <%=cssScript %>" id="script">
      <textarea id="filter" name="filter" style="width:98%; height:200px"><%=StrUtil.HtmlEncode(filter)%></textarea>
      <br />
		字段：
        <select id="filterField" name="filterField" onchange="if (o('filterField').value!='') o('filter').value += o('filterField').value">
        <option value="">请选择字段</option>
		<%
		Vector v = fd.getFields();
        Iterator ir = v.iterator();
        while (ir.hasNext()) {
            FormField ff = (FormField) ir.next();
        %>
            <option value="<%=ff.getName()%>"><%=ff.getTitle()%></option>
        <%}%>
        </select>
        &nbsp;&nbsp;
      	<a href="javascript:;" onclick="o('filter').value += '{$request.key}';" title="从request请求中获取参数">request参数</a>
        &nbsp;&nbsp;
      	<a href="javascript:;" onclick="o('filter').value += ' {$curDate}';" title="当前日期">当前日期</a>
        &nbsp;&nbsp;
      	<a href="javascript:;" onclick="o('filter').value += ' ={$curUser}';" title="当前用户">当前用户</a>
        &nbsp;&nbsp;
      	<a href="javascript:;" onclick="o('filter').value += ' in ({$curUserDept})';" title="当前用户">当前用户所在的部门</a>
        &nbsp;&nbsp;        
      	<a href="javascript:;" onclick="o('filter').value += ' in ({$curUserRole})';" title="当前用户的角色">当前用户的角色</a>
        &nbsp;&nbsp;        
      	<a href="javascript:;" onclick="o('filter').value += ' in ({$admin.dept})';" title="用户可以管理的部门">当前用户管理的部门</a>
        &nbsp;&nbsp; 
        <span style="text-align:center">
      	<input type="button" value="设计器" class="btn btn-default" onclick="openIdeWin()" />
      	<br />
        (注：条件不能以and开头，可以直接输入条件，也可以使用脚本，脚本中必须返回ret)
      	</span>      
   </div>
</div>
      </td>
    </tr>
    <tr id="trOrderBy">
      <td align="left" >
      	排序字段
        <select id="orderby" name="orderby">
        <option value="">请选择字段</option>
			<option value="id">ID</option>
		<%
        ir = v.iterator();
        while (ir.hasNext()) {
            FormField ff = (FormField) ir.next();
        %>
            <option value="<%=ff.getName()%>"><%=ff.getTitle()%></option>
        <%}%>
        </select>      
        顺序
        <select id="sort" name="sort">
        <option value="desc">降序</option>
        <option value="asc">升序</option>
        </select>
        &nbsp;&nbsp;
		记录状态
        <select id="cws_status" name='cws_status' title="如果条件中已含有cws_status，则不生效">
        <option value='<%=SQLBuilder.CWS_STATUS_NOT_LIMITED%>'>不限</option>
        <option value='<%=com.redmoon.oa.flow.FormDAO.STATUS_DRAFT%>'><%=com.redmoon.oa.flow.FormDAO.getStatusDesc(com.redmoon.oa.flow.FormDAO.STATUS_DRAFT)%></option>
        <option value='<%=com.redmoon.oa.flow.FormDAO.STATUS_NOT%>'><%=com.redmoon.oa.flow.FormDAO.getStatusDesc(com.redmoon.oa.flow.FormDAO.STATUS_NOT)%></option>
        <option value='<%=com.redmoon.oa.flow.FormDAO.STATUS_DONE%>' selected><%=com.redmoon.oa.flow.FormDAO.getStatusDesc(com.redmoon.oa.flow.FormDAO.STATUS_DONE)%></option>
        <option value='<%=com.redmoon.oa.flow.FormDAO.STATUS_REFUSED%>'><%=com.redmoon.oa.flow.FormDAO.getStatusDesc(com.redmoon.oa.flow.FormDAO.STATUS_REFUSED)%></option>
        <option value='<%=com.redmoon.oa.flow.FormDAO.STATUS_DISCARD%>'><%=com.redmoon.oa.flow.FormDAO.getStatusDesc(com.redmoon.oa.flow.FormDAO.STATUS_DISCARD)%></option>
        </select>
      </td>
    </tr>
	<tr>
		<td>
			单位
			<select id="isUnitShow" name="isUnitShow" title="模块列表过滤条件中的单位下拉框">
				<option value="0">隐藏</option>
				<option value="1">显示</option>
			</select>
			&nbsp;&nbsp;默认
			<select id="unitCode" name="unitCode" title="默认显示本单位的记录">
				<option value="-1">不限</option>
				<option value="0">本单位</option>
			</select>
			&nbsp;&nbsp;注：此处配置在表单域选择宏控件、嵌套表格及嵌套表格2宏控件的过滤条件中也生效。
			<script>
				$(function() {
					$('#orderby').val("<%=vsd.getString("orderby")%>");
					$('#sort').val("<%=vsd.getString("sort")%>");
					$('#cws_status').val("<%=vsd.getInt("cws_status")%>");
					$('#isUnitShow').val("<%=vsd.getInt("is_unit_show")%>");
					$('#unitCode').val("<%=vsd.getInt("unit_code")%>");
				});
			</script>
		</td>
	</tr>
    <tr>
      <td align="center" ><input class="btn btn-default" type="submit" value="确定" />
        <input name="code" value="<%=code%>" type="hidden" />
        <input name="formCode" value="<%=formCode%>" type="hidden" />
      </td>
    </tr>
  </table>
</form>    
<br />

</body>
<script>
var work_log = "<%=work_log%>";
if(work_log==1) {
	$("#is_workLog").attr({"checked":"checked"});
}else{
	$("#is_workLog").removeAttr("checked");
}

function changeWorkLog(){
	//alert($("#is_workLog:checked").parent().html());
	if($("#is_workLog:checked").parent().html() != null){
		$(".is_workLog").val(1);
	}else{
		$(".is_workLog").val(0);
	}
}

function formAddMulti_onsubmit() {
	if (formAddMulti.fieldName.value=="") {
		layer.msg("字段不能为空！", {
			offset: '6px'
		});
		return false;
	}
}

function getScript() {
	return $('#filter').val();
}

function setScript(script) {
	$('#filter').val(script);
}

function openWin(url,width,height) {
	var newwin=window.open(url,"fieldWin","toolbar=no,location=no,directories=no,status=no,menubar=no,scrollbars=yes,resizable=yes,top=50,left=120,width="+width+",height="+height);
	return newwin;
}

var curCondsObj, curImgObj;
function openCondition(condsObj, imgObj){
	curCondsObj = condsObj;
	curImgObj = imgObj
	
    openWin("",1024,568);

	var url = "module_combination_condition.jsp";
	var tempForm = document.createElement("form");
	tempForm.id="tempForm1";  
	tempForm.method="post";
	tempForm.action=url;  

	var hideInput = document.createElement("input");
	hideInput.type="hidden";
	hideInput.name= "condition";
	hideInput.value = curCondsObj.value;
	tempForm.appendChild(hideInput);   
	    
	hideInput = document.createElement("input");  
	hideInput.type="hidden";  
	hideInput.name= "fromValue";
	hideInput.value=  "" ;
	tempForm.appendChild(hideInput);   
			  
	hideInput = document.createElement("input");  
	hideInput.type="hidden";  
	hideInput.name= "toValue";
	hideInput.value=  ""
	tempForm.appendChild(hideInput);   
	    
	hideInput = document.createElement("input");  
	hideInput.type="hidden";  
	hideInput.name= "moduleCode";
	hideInput.value=  "<%=code %>";
	tempForm.appendChild(hideInput);   
	    
	hideInput = document.createElement("input");  
	hideInput.type="hidden";  
	hideInput.name= "operate";
	hideInput.value=  "";
	tempForm.appendChild(hideInput);   
	
	document.body.appendChild(tempForm);
	tempForm.target="fieldWin";
	tempForm.submit();
	document.body.removeChild(tempForm);
}

function setCondition(val) {
	curCondsObj.value = val;
	if (val=="") {
		$(curImgObj).hide();
	}
	else {
		$(curImgObj).show();
	}		
}

function openMsgPropDlg(){
  openWin("module_msg_prop.jsp?moduleCode=<%=code%>", 700, 600);
}		 

function getMsgProp() {
	return o("msgProp").value;
}

function setMsgProp(msgProp) {
	o("msgProp").value = msgProp;
	$.ajax({
		url: "setMsgProp",
		type: "post",
		data: {
			code: "<%=code%>",
			msgProp: msgProp
		},
		dataType: "json",
		beforeSend: function(XMLHttpRequest){
		},
		success: function(data, status) {
			layer.msg(data.msg, {
				offset: '6px'
			});
		},
		complete: function(XMLHttpRequest, status){
		},
		error: function(XMLHttpRequest, textStatus){
		}
	});	
}

<%
	com.redmoon.oa.Config oaCfg = new com.redmoon.oa.Config();
	com.redmoon.oa.SpConfig spCfg = new com.redmoon.oa.SpConfig();
	String version = StrUtil.getNullStr(oaCfg.get("version"));
	String spVersion = StrUtil.getNullStr(spCfg.get("version"));
%>
var ideUrl = "../admin/script_frame.jsp";
var ideWin;
var cwsToken = "";

function openIdeWin() {
	ideWin = openWinMax(ideUrl);
}

var onMessage = function(e) {
	var d = e.data;
	var data = d.data;
	var type = d.type;
	if (type=="setScript") {
		setScript(data);
		if (d.cwsToken!=null) {
			cwsToken = d.cwsToken;
			ideUrl = "../admin/script_frame.jsp?cwsToken=" + cwsToken;
		}
	}
	else if (type=="getScript") {
		var data={
		    "type":"openerScript",
		    "version":"<%=version%>",
		    "spVersion":"<%=spVersion%>",
		    "scene":"module.filter",	    
		    "data":getScript()
	    }
		ideWin.leftFrame.postMessage(data, '*');
	}
	else if (type == "setCwsToken") {
		cwsToken = d.cwsToken;
		ideUrl = "../admin/script_frame.jsp?cwsToken=" + cwsToken;
	}
};

$(function() {
     if (window.addEventListener) { // all browsers except IE before version 9
         window.addEventListener("message", onMessage, false);
     } else {
         if (window.attachEvent) { // IE before version 9
             window.attachEvent("onmessage", onMessage);
         }
     }
});

  <%
      if (!isServerConnectWithCloud) {
  %>
  function checkWebEditInstalled() {
	  var bCtlLoaded = false;
	  try {
		  if (typeof(o("webedit").AddField)=="undefined")
			  bCtlLoaded = false;
		  if (typeof(o("webedit").AddField)=="unknown") {
			  bCtlLoaded = true;
		  }
	  }
	  catch (ex) {
	  }
	  if (!bCtlLoaded) {
		  $('<div></div>').html('您还没有安装客户端控件，请点击确定此处下载安装！').activebar({
			  'icon': 'images/alert.gif',
			  'highlight': '#FBFBB3',
			  'url': 'activex/oa_client.exe',
			  'button': 'images/bar_close.gif'
		  });
	  }
  }

  $(function() {
	  checkWebEditInstalled();
  })
  <%
  }
  %>

	$.fn.outerHTML = function () {
		return $("<p></p>").append(this.clone()).html();
	};

	function addCalcuField() {
		if (o("divCalcuField0")) {
			$("#divCalcuField").append($("#divCalcuField0").outerHTML());
		} else {
			initDivCalcuField();
		}
	}

	function initDivCalcuField() {
		$.ajax({
			type: "POST",
			url: "module_field_calcu_field_ajax.jsp",
			data: {
				formCode: "<%=formCode%>"
			},
			success: function (html) {
				$("#divCalcuField").html(html);
			},
			error: function (XMLHttpRequest, textStatus) {
				// 请求出错处理
				jAlert(XMLHttpRequest.responseText, "提示");
			}
		});
	}

	$(function () {
		$('#btnPropStat').click(function (e) {
			e.preventDefault();

			// 字段合计描述字符串处理
			var calcCodesStr = "";
			var calcFuncs = $("select[name='calcFunc']");

			var map = new Map();
			var isFound = false;
			$("select[name='calcFieldCode']").each(function (i) {
				if ($(this).val() != "") {
					if (!map.containsKey($(this).val()))
						map.put($(this).val(), $(this).val());
					else {
						isFound = true;
						layer.msg($(this).find("option:selected").text() + "存在重复！", {
							offset: '6px'
						});
						return false;
					}

					if (calcCodesStr == "")
						calcCodesStr = "\"" + $(this).val() + "\":\"" + calcFuncs.eq(i).val() + "\"";
					else
						calcCodesStr += "," + "\"" + $(this).val() + "\":\"" + calcFuncs.eq(i).val() + "\"";
				}
			})
			if (isFound)
				return;

			calcCodesStr = "{" + calcCodesStr + "}";

			$.ajax({
				type: "post",
				url: "updatePropStat",
				data: {
					code: "<%=code%>",
					propStat: calcCodesStr
				},
				dataType: "html",
				beforeSend: function (XMLHttpRequest) {
					$('body').showLoading();
				},
				success: function (data, status) {
					data = $.parseJSON(data);
					layer.msg(data.msg, {
						offset: '6px'
					});
				},
				complete: function (XMLHttpRequest, status) {
					$('body').hideLoading();
				},
				error: function (XMLHttpRequest, textStatus) {
					// 请求出错处理
					jAlert(XMLHttpRequest.responseText, "提示");
				}
			});
		});

		$('#btnModuleProp').click(function (e) {
			e.preventDefault();
			$.ajax({
				type: "post",
				url: "setModuleProps",
				data: $('#formModuleProps').serialize(),
				dataType: "html",
				beforeSend: function (XMLHttpRequest) {
					$('body').showLoading();
				},
				success: function (data, status) {
					data = $.parseJSON(data);
					layer.msg(data.msg, {
						offset: '6px'
					});
					if (data.ret == 1) {
						reloadTab("<%=tabIdOpener%>");
					}
				},
				complete: function (XMLHttpRequest, status) {
					$('body').hideLoading();
				},
				error: function (XMLHttpRequest, textStatus) {
					// 请求出错处理
					jAlert(XMLHttpRequest.responseText, "提示");
				}
			});
		})
	})

	function openWinMax(url) {
		return window.open(url, '', 'scrollbars=yes,resizable=yes,channelmode'); // 开启一个被F11化后的窗口起作用的是最后那个特效
	}

	var kind = "<%=kind%>";

	function frmFilter_onsbumit() {
		if (kind == "comb") {
			o("filter").value = o("condition").value;
		}

		$.ajax({
			type: "post",
			url: "setModuleFilter",
			data: $('#formModuleFilter').serialize(),
			dataType: "html",
			beforeSend: function (XMLHttpRequest) {
				$('body').showLoading();
			},
			success: function (data, status) {
				data = $.parseJSON(data);
				// $.toaster({priority: 'info', message: data.msg});
				layer.msg(data.msg, {
					offset: '6px'
				});
			},
			complete: function (XMLHttpRequest, status) {
				$('body').hideLoading();
			},
			error: function (XMLHttpRequest, textStatus) {
				// 请求出错处理
				jAlert(XMLHttpRequest.responseText, "提示");
			}
		});
		return false;
	}

	$(function () {
		$('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
			kind = $(e.target).attr("kind");
			if (kind == "script") {
				if (o("filter").value.indexOf("<items>") == 0) {
					o("filter").value = "";
				}
				$('#trOrderBy').hide();
			} else {
				$('#trOrderBy').show();
			}
		});

		$("#mainTable td").mouseout(function () {
			if ($(this).parent().parent().get(0).tagName != "THEAD")
				$(this).parent().find("td").each(function (i) {
					$(this).removeClass("tdOver");
				});
		});

		$("#mainTable td").mouseover(function () {
			if ($(this).parent().parent().get(0).tagName != "THEAD")
				$(this).parent().find("td").each(function (i) {
					$(this).addClass("tdOver");
				});
		});

		$('input, select, textarea').each(function() {
			if (!$('body').hasClass('form-inline')) {
				$('body').addClass('form-inline');
			}
			if (!$(this).hasClass('ueditor') && !$(this).hasClass('btnSearch') && !$(this).hasClass('tSearch') && $(this).attr('type') != 'hidden' && $(this).attr('type') != 'file') {
				$(this).addClass('form-control');
				$(this).attr('autocomplete', 'off');
			}
		});
	});
</script>
</html>