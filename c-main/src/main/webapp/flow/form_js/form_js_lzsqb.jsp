<%@ page contentType="text/html; charset=utf-8" language="java" errorPage="" %>
<%@ page import = "cn.js.fan.util.ParamUtil"%>
<%@ page import = "com.redmoon.oa.person.UserDb"%>
<%@ page import = "org.json.JSONObject"%>
<%
    /*
    - 功能描述：离职申请表
    - 访问规则：从flow_dispose.jsp中通过include script访问
    - 过程描述：
    - 注意事项：
    - 创建者：fgf
    - 创建时间：2013-05-12
    ==================
    - 修改者：
    - 修改时间：
    - 修改原因:
    - 修改点:
    */
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Content-Security-Policy", "default-src 'self' http: https:; script-src 'self'; frame-ancestors 'self'");

    String op = ParamUtil.get(request, "op");
    if (op.equals("getPersonInfo")) {
        response.setContentType("text/html;charset=utf-8");
        String userName = ParamUtil.get(request, "userName");
        UserDb ud = new UserDb(userName);
        JSONObject json = new JSONObject();
        json.put("ret", "1");
        json.put("personNo", ud.getPersonNo());
        json.put("sex", ud.getGender());
        json.put("idCard", ud.getIDCard());
        json.put("duty", ud.getDuty());
        json.put("mobile", ud.getMobile());
        out.print(json);
        return;
    }
    response.setContentType("text/javascript;charset=utf-8");
%>
<script>
    var ajaxData = {
        op: "getPersonInfo",
        userName: fo('xm').value
    }
    ajaxPost('/flow/form_js/form_js_lzsqb.jsp', ajaxData).then((data) => {
        console.log('data', data);
        if (data.ret=="1") {
            // o("sfzh").value = data.idCard;
            fo("xb").selectedIndex = 1;
            // o("person_no").value = data.personNo;
            // o("zw").value = data.duty;
            // o("sj").value = data.mobile;
        } else {
            myMsg(data.msg, 'error');
        }
    });
</script>