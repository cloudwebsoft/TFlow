<%@ page contentType="text/html;charset=utf-8" %>
<%@ page import="cn.js.fan.util.*" %>
<%@ page import="com.redmoon.oa.flow.*" %>
<%@ page import="com.redmoon.oa.kernel.License" %>
<%
    String formCodeTop = ParamUtil.get(request, "code");
    FormDb fdTop = new FormDb();
    fdTop = fdTop.getFormDb(formCodeTop);
%>
<div id="tabs1">
    <ul>
        <li id="menu1"><a href="<%=request.getContextPath()%>/admin/form_edit.jsp?code=<%=StrUtil.UrlEncode(formCodeTop)%>"><span>表单编辑</span></a></li>
        <li id="menu2"><a href="<%=request.getContextPath()%>/admin/form_field_m.jsp?code=<%=StrUtil.UrlEncode(formCodeTop)%>"><span>字段管理</span></a></li>
        <%
            if (com.redmoon.oa.kernel.License.getInstance().isSrc()) {
        %>
        <li id="menu7"><a
                href="<%=request.getContextPath()%>/visual/module_scripts_iframe.jsp?code=<%=formCodeTop%>&formCode=<%=formCodeTop%>"><span>事件脚本</span></a>
        </li>
        <%
            }
        %>
    </ul>
</div>