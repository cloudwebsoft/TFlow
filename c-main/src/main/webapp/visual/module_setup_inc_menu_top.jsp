<%@ page contentType="text/html;charset=utf-8" %>
<%@ page import="cn.js.fan.util.*" %>
<%@ page import="com.redmoon.oa.kernel.*" %>
<%@ page import="com.redmoon.oa.visual.ModuleSetupDb" %>
<%@page import="com.redmoon.oa.flow.FormDb" %>
<%
    String codeTop = ParamUtil.get(request, "code");
    if ("".equals(codeTop)) {
        // 从前端界面上直接点击管理进入
        codeTop = ParamUtil.get(request, "moduleCode");
    }
    String formCodeTop = ParamUtil.get(request, "formCode");
    if ("".equals(codeTop)) {
        // module_relate.jsp中添加关联模块时，code因为是关联表单的编码
        codeTop = formCodeTop;
    }
    if ("".equals(formCodeTop)) {
      ModuleSetupDb msdTop = new ModuleSetupDb();
      msdTop = msdTop.getModuleSetupDb(codeTop);
      formCodeTop = msdTop.getString("form_code");
    }
%>
<div id="tabs1">
    <ul>
        <li id="menu1"><a
                href="<%=request.getContextPath()%>/visual/module_field_list.jsp?code=<%=codeTop%>&formCode=<%=formCodeTop%>"><span>列表设置</span></a>
        </li>
    </ul>
</div>
