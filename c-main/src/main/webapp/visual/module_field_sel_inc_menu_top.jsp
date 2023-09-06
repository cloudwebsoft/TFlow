<%@ page contentType="text/html; charset=utf-8"%>
<%@ page import = "cn.js.fan.util.*"%>
<%@ page import = "com.redmoon.oa.visual.*"%>
<%@ page import = "com.redmoon.oa.flow.*"%>
<%
String openerFormCodeTop = ParamUtil.get(request, "openerFormCode");
String nestTypeTop = ParamUtil.get(request, "nestType");
%>
<div id="tabs1">
  <ul>
    <li id="menu1"><a href="module_field_sel_nest1.jsp?openerFormCode=<%=openerFormCodeTop%>&nestType=<%=nestTypeTop%>"><span>嵌套表</span></a></li>
  </ul>
</div>
