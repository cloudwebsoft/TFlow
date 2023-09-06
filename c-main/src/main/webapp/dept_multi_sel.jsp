<%@ page contentType="text/html;charset=utf-8" %>
<%@ page import="com.redmoon.oa.dept.*" %>
<%@ page import="com.redmoon.oa.ui.*" %>
<%@ page import="cn.js.fan.util.*" %>
<%@ taglib uri="/WEB-INF/tlds/i18nTag.tld" prefix="lt" %>
<jsp:useBean id="privilege" scope="page" class="com.redmoon.oa.pvg.Privilege"/>
<%
    // String unitCode = DeptDb.ROOTCODE; // privilege.getUserUnitCode(request);

    String unitCode = ParamUtil.get(request, "unitCode");
    if (unitCode.equals("")) {
        unitCode = DeptDb.ROOTCODE;
    }

    String openType = ParamUtil.get(request, "openType");

    boolean isOnlyUnitCheckable = ParamUtil.get(request, "isOnlyUnitCheckable").equals("true");
%>
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
    <title><lt:Label res="res.flow.Flow" key="selectDepartment"/></title>
    <link type="text/css" rel="stylesheet" href="<%=SkinMgr.getSkinPath(request)%>/css.css"/>
    <jsp:useBean id="fchar" scope="page" class="cn.js.fan.util.StrUtil"/>
    <style>
        .unit {
            font-weight: bold;
        }
    </style>
    <script src="inc/common.js"></script>
    <script src="js/jquery-1.9.1.min.js"></script>
    <script src="js/jquery-migrate-1.2.1.min.js"></script>
    <script>
        var errFunc = function (response) {
            window.status = 'Error ' + response.status + ' - ' + response.statusText;
            // alert(response.responseText);
        }

        var curDeptCode;

        function func(code) {
            curDeptCode = code;
            var obj = document.getElementById("chk_" + curDeptCode);
            if (!obj.disabled)
                obj.checked = !obj.checked;
            var str = "code=" + code;
            var myAjax = new cwAjax.Request(
                "dept_multi_sel_allchild_ajax.jsp",
                {
                    method: "post",
                    parameters: str,
                    onComplete: doFunc,
                    onError: errFunc
                }
            );
        }

        function doFunc(response) {
            var rsp = response.responseText.trim();
            if (rsp != "") {
                var curObj = document.getElementById("chk_" + curDeptCode);
                var temp = rsp.split(",");
                var length = temp.length;
                for (var i = 0; i < length; i++) {
                    var obj = document.getElementById("chk_" + temp[i]);
                    if (obj) {
                        /*
                        if (curObj.checked) {
                            if (!obj.disabled)
                                  obj.checked = true;
                        }
                        else {
                            obj.checked = false;
                        }
                        */
                        // 如果只选子单位，则当前点击部门有可能被禁用
                        if (curObj.disabled) {
                            if (!obj.disabled)
                                obj.checked = !obj.checked;
                        } else {
                            if (!obj.disabled)
                                obj.checked = curObj.checked;
                        }
                    }
                }
            }
        }

        function getAllChildren(codes) {
            var str = "codes=" + codes;
            var myAjax = new cwAjax.Request(
                "dept_multi_sel_allchild_ajax.jsp",
                {
                    method: "post",
                    parameters: str,
                    onComplete: doGetAllChildren,
                    onError: errFunc
                }
            );
        }

        function doGetAllChildren(response) {
            var depts = response.responseText.trim();
            var ary = depts.split(",");
            var r = new Array();
            for (var i = 0; i < ary.length; i++) {
                var ary2 = ary[i].split(":");
                r[i] = ary2;
            }
            if (window.opener)
                window.opener.setDepts(r);
            else
                window.returnValue = r;
            window.close();
        }

        function selDepts() {
            if (document.getElementById("isIncludeChild").checked) {
                var deptCodes = "";
                for (var i = 0; i < form1.elements.length; i++) {
                    if (form1.elements[i].type == "checkbox") {
                        if (form1.elements[i].checked) {
                            if (form1.elements[i].name == "isIncludeChild") {
                                continue;
                            }
                            if (deptCodes == "") {
                                deptCodes = form1.elements[i].name;
                            } else {
                                deptCodes += "," + form1.elements[i].name;
                            }
                        }
                    }
                }
                if (deptCodes == "") {
                    if (window.opener) {
                        window.opener.setDepts(getDepts());
                    } else {
                        window.returnValue = getDepts();
                    }
                    window.close();
                } else {
                    getAllChildren(deptCodes);
                }
            } else {
                if (window.opener) {
                    window.opener.setDepts(getDepts());
                } else {
                    window.returnValue = getDepts();
                }
                window.close();
            }
        }

        function ShowChild(imgobj, name) {
            var tableobj = o("childof" + name);
            if (tableobj == null) {
                var iframeObj = o("ifrmGetChildren");
                if (iframeObj) {
                    if (iframeObj.contentDocument) {
                        iframeObj.contentDocument.location.href = "admin/dept_ajax_getchildren.jsp?op=funcCheckbox&func=func&target=_self&isOnlyUnitCheckable=<%=isOnlyUnitCheckable%>&parentCode=" + name;
                    } else {
                        document.frames["ifrmGetChildren"].location.href = "admin/dept_ajax_getchildren.jsp?op=funcCheckbox&func=func&target=_self&isOnlyUnitCheckable=<%=isOnlyUnitCheckable%>&parentCode=" + name;
                    }
                }

                // document.frames["ifrmGetChildren"].location.href = "admin/dept_ajax_getchildren.jsp?op=funcCheckbox&func=func&target=_self&isOnlyUnitCheckable=<%=isOnlyUnitCheckable%>&parentCode=" + name;
                if (imgobj.src.indexOf("i_puls-root-1.gif") != -1)
                    imgobj.src = "images/i_puls-root.gif";
                if (imgobj.src.indexOf("i_plus.gif") != -1) {
                    imgobj.src = "images/i_minus.gif";
                } else
                    imgobj.src = "images/i_plus.gif";
                return;
            }
            if (tableobj.style.display == "none") {
                tableobj.style.display = "";
                if (imgobj.src.indexOf("i_puls-root-1.gif") != -1)
                    imgobj.src = "images/i_puls-root.gif";
                if (imgobj.src.indexOf("i_plus.gif") != -1)
                    imgobj.src = "images/i_minus.gif";
                else
                    imgobj.src = "images/i_plus.gif";
            } else {
                tableobj.style.display = "none";
                if (imgobj.src.indexOf("i_plus.gif") != -1)
                    imgobj.src = "images/i_minus.gif";
                else
                    imgobj.src = "images/i_plus.gif";
            }
        }

        function insertAdjacentHTML(objId, code, isStart) {
            var obj = document.getElementById(objId);
            if (isIE())
                obj.insertAdjacentHTML(isStart ? "afterbegin" : "afterEnd", code);
            else {
                var range = obj.ownerDocument.createRange();
                range.setStartBefore(obj);
                var fragment = range.createContextualFragment(code);
                if (isStart)
                    obj.insertBefore(fragment, obj.firstChild);
                else
                    obj.appendChild(fragment);
            }
        }

        // 在onload及ajax取孩子节点时被调用，onload调用时，不带parentCode参数
        function setDepts(parentCode) {
            var depts;
            if (window.opener)
                depts = window.opener.getDepts();
            else
                depts = dialogArguments.getDepts();
            var ary = depts.split(",");
            for (var i = 0; i < form1.elements.length; i++) {
                if (form1.elements[i].type == "checkbox") {
                    for (var j = 0; j < ary.length; j++) {
                        if (form1.elements[i].name == ary[j]) {
                            // 如果指定了父节点，则只有此父节点下的子节点原来被选中的，才会在ajax后被选中
                            if (parentCode) {
                                if (form1.elements[i].getAttribute("parentCode") == parentCode)
                                    form1.elements[i].checked = true;
                            } else {
                                form1.elements[i].checked = true;
                            }
                            break;
                        }
                    }
                }
            }
        }

        function getDepts() {
            var ary = new Array();
            var j = 0;
            for (var i = 0; i < form1.elements.length; i++) {
                if (form1.elements[i].type == "checkbox") {
                    if (form1.elements[i].name == "isIncludeChild")
                        continue;
                    if (form1.elements[i].checked) {
                        ary[j] = new Array();
                        ary[j][0] = form1.elements[i].name;
                        ary[j][1] = form1.elements[i].getAttribute("fullName");
                        j++;
                    }
                }
            }
            return ary;
        }

        function checkAll(isChecked) {
            var ary = new Array();
            var j = 0;
            for (var i = 0; i < form1.elements.length; i++) {
                if (form1.elements[i].type == "checkbox") {
                    if (!form1.elements[i].disabled)
                        form1.elements[i].checked = isChecked;
                }
            }
            return ary;
        }

        function handlerOnClick() {
            var obj = window.event.srcElement;
            if (obj.type == "checkbox") {
                ;
            }
        }

        function window_onload() {
            setDepts();
        }
    </script>
</head>
<body onload="window_onload()">
<%
    String priv = "read";
    if (!privilege.isUserPrivValid(request, priv)) {
        out.println(cn.js.fan.web.SkinUtil.makeErrMsg(request, cn.js.fan.web.SkinUtil.LoadString(request, "pvg_invalid")));
        return;
    }
%>
<form id="form1" name="form1" method="post">
    <table align="center" cellpadding="0" cellspacing="0" style="width:100%; margin:0px">
        <tr>
            <td height="24" colspan="2" align="center" class="tdStyle_1"><lt:Label res="res.flow.Flow" key="organizations"/></td>
        </tr>
        <tr>
            <td height="24" colspan="2" align="center">
                <%
                    String isDisplay = "";
                    if (ParamUtil.get(request, "isIncludeChild").equals("false")) {
                        isDisplay = "display:none";
                    }
                %>
                <div style="border-bottom:1px dashed #cccccc;text-align:left;<%=isDisplay%>">
                    <input id="isIncludeChild" name="isIncludeChild" type="checkbox"/>
                    <lt:Label res="res.flow.Flow" key="selectSub"/>
                </div>
            </td>
        </tr>
        <tr>
            <td width="24" height="87">&nbsp;</td>
            <td width="249" valign="top">
                <%
                    DeptMgr dm = new DeptMgr();
                    DeptDb dd = dm.getDeptDb(unitCode);
                    DeptView tv = new DeptView(dd);
                    tv.ListFuncWithCheckboxAjax(request, out, "", "func", "", "", true, isOnlyUnitCheckable);
                %></td>
        </tr>
        <tr align="center">
            <td height="28" colspan="2">
                <input class="btn" type="button" value="<lt:Label res='res.flow.Flow' key='selectAll'/>" onclick="checkAll(true)"/>
                &nbsp;&nbsp;&nbsp;&nbsp;
                <input class="btn" type="button" value="<lt:Label res='res.flow.Flow' key='clearSelection'/>" onclick="checkAll(false)"/>
                &nbsp;&nbsp;&nbsp;&nbsp;
                <input class="btn" type="button" value="<lt:Label res='res.flow.Flow' key='sure'/>" onclick="selDepts()"/>
                &nbsp;&nbsp;&nbsp;&nbsp;
                <input class="btn" type="button" value="<lt:Label res='res.flow.Flow' key='cancel'/>" onclick="window.close()"/>
            </td>
        </tr>
    </table>
</form>
<iframe id="ifrmGetChildren" style="display:none" width="300" height="300" src=""></iframe>
</body>
</html>
