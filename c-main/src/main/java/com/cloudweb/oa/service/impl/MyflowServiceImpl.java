package com.cloudweb.oa.service.impl;

import cn.js.fan.security.SecurityUtil;
import cn.js.fan.util.ParamUtil;
import cn.js.fan.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.cloudweb.oa.service.MyflowService;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.flow.WorkflowPredefineMgr;
import com.redmoon.oa.sys.DebugUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Iterator;

@Service
public class MyflowServiceImpl implements MyflowService {

    @Autowired
    private HttpServletRequest request;

    @Override
    public String replaceSubstitution(String val) {
        val = val.replaceAll(WorkflowPredefineMgr.SUBSTITUTION + "colon", "\\\\colon");
        val = val.replaceAll(WorkflowPredefineMgr.SUBSTITUTION + "semicolon", "\\\\semicolon");
        val = val.replaceAll(WorkflowPredefineMgr.SUBSTITUTION + "comma", "\\\\comma");
        val = val.replaceAll(WorkflowPredefineMgr.SUBSTITUTION + "newline", "\\\\newline");
        val = val.replaceAll(WorkflowPredefineMgr.SUBSTITUTION + "quot", "\\\\quot");
        return val;
    }

    public String getJSONObjectVal(org.json.JSONObject json, String key) throws JSONException {
        if (json.has(key)) {
            org.json.JSONObject jsonObject = json.getJSONObject(key);
            // DebugUtil.i(getClass(), "key", key + " jsonObject:" + jsonObject.toString());
            String val = jsonObject.getString("value");

            /*val = val.replaceAll(WorkflowPredefineMgr.SUBSTITUTION + "colon", "\\\\colon");
            val = val.replaceAll(WorkflowPredefineMgr.SUBSTITUTION + "semicolon", "\\\\semicolon");
            val = val.replaceAll(WorkflowPredefineMgr.SUBSTITUTION + "comma", "\\\\comma");
            val = val.replaceAll(WorkflowPredefineMgr.SUBSTITUTION + "newline", "\\\\newline");*/
            val = replaceSubstitution(val);

            // 将,号等转义
            val = tranReverse(val);

            return val;

            // 如果字符串中含有多个\，可能会引发java.lang.StringIndexOutOfBoundsException，replaceAll上的javadoc包含对反斜杠使用的警告，并建议使用Matcher.replaceAll或Matcher.quoteReplacement
            // return val.replaceAll(WorkflowPredefineMgr.SUBSTITUTION, "\\\\");
            // 用\\\\也不行
            // return val.replaceAll(WorkflowPredefineMgr.SUBSTITUTION, "\\");

            // quoteReplacement 用来取消字符的特殊含义，会被替换为\\，而不是\
            // return val.replaceAll(WorkflowPredefineMgr.SUBSTITUTION, Matcher.quoteReplacement("\\"));

            // 使用matcher.replaceAll也不行
            // Pattern p = Pattern.compile(WorkflowPredefineMgr.SUBSTITUTION, Pattern.CASE_INSENSITIVE);
            // Matcher m = p.matcher(val);
            // return m.replaceAll("\\");

            // 用appendReplacement也不行
            /*StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, "\\");
            }
            m.appendTail(sb);
            return sb.toString();*/
        }
        else {
            return "";
        }
    }

    public String tranReverse(String str) {
        if (str==null) {
            return "";
        }
        str = str.replaceAll(":", "\\\\colon");
        str = str.replaceAll(";", "\\\\semicolon");
        str = str.replaceAll(",", "\\\\comma");
        str = str.replaceAll("\r\n","\\\\newline");
        return str;
    }

    /**
     * 生成流程图的描述字符串
     * @param flowJson
     * @param serverName
     * @return
     */
    @Override
    public JSONObject generateFlowString(String flowJson, String serverName) {
        // String flowJson = ParamUtil.get(request, "flowJson");
        // 注释掉，防止手工存的ac.dat文件，其中有空格或者换行
        // String activateCode = request.getParameter("activateCode");
        String activateCode = ParamUtil.get(request, "activateCode");
        JSONObject jsonObject = new JSONObject();
        if (activateCode == null) {
            jsonObject.put("ret", 0);
            jsonObject.put("msg", "错误：0x003，未上传激活码");
            return jsonObject;
        }

        String flowString = "paper:2592,2160;\r\n";

        try {
            // 直接转因为其中的\字符，导致不认，故先将\转为#@#
            flowJson = flowJson.replaceAll("\\\\", WorkflowPredefineMgr.SUBSTITUTION);
            org.json.JSONObject flowJsonObject = new org.json.JSONObject(flowJson);
            org.json.JSONObject stateJsonObjects = flowJsonObject.getJSONObject("states");
            Iterator ir = stateJsonObjects.keys();
            while (ir.hasNext()) {
                String key = (String) ir.next();
                org.json.JSONObject stateJson = stateJsonObjects.getJSONObject(key);
                if (stateJson == null) {
                    // @task: 删除节点时，可能未从states中清掉
                    continue;
                }

                org.json.JSONObject props = stateJson.getJSONObject("props");

                String stateStr;
                // 流程图json中type均为task
                // if (MyflowUtil.ACTION_TYPE_START.equals(stateJson.getString("type"))) {
                // 根据入度来判断是否为开始节点
                if (MyflowUtil.ACTION_TYPE_START.equals(stateJson.getString("type"))) { // || stateJson.getInt("inDegree") == 0) {
                    stateStr = "workflow_start:";
                }
                else {
                    stateStr = "workflow_action:";
                }

                stateStr += stateJson.getJSONObject("attr").getInt("x"); // 0、left
                stateStr += "," + stateJson.getJSONObject("attr").getInt("y"); // 1、top
                stateStr += "," + (stateJson.getJSONObject("attr").getInt("x") + stateJson.getJSONObject("attr").getInt("width")); // 2、right
                stateStr += "," + (stateJson.getJSONObject("attr").getInt("y") + stateJson.getJSONObject("attr").getInt("height")); // 3、bottom
                stateStr += "," + getJSONObjectVal(props, "ActionTitle"); // 4、title
                stateStr += "," + stateJson.getString("ID"); // 5、internalname
                stateStr += "," + 0; // 6、GetGroup()始终为0
                stateStr += "," + getJSONObjectVal(props, "ActionUser"); // 7、userName
                stateStr += "," + 0; // 8、GetKind()=0
                stateStr += ","; // 9、radiate=''
                stateStr += ","; // 10、aggregate=''
                stateStr += "," + getJSONObjectVal(props, "ActionCheckState"); // 11、status
                stateStr += ","; // 12、reason=''
                stateStr += "," + getJSONObjectVal(props, "ActionUserRealName"); // 13、userRealName
                stateStr += "," + getJSONObjectVal(props, "ActionJobCode"); // 14、jobCode
                stateStr += "," + getJSONObjectVal(props, "ActionJobName"); // 15、jobName
                stateStr += "," + getJSONObjectVal(props, "ActionProxyJobCode"); // 16、direction(proxyJobCode)
                stateStr += "," + getJSONObjectVal(props, "ActionProxyJobName"); // 17、rankCode(proxyJobName)
                stateStr += "," + getJSONObjectVal(props, "ActionProxyUserName"); // 18、rankName(proxyUserName)
                stateStr += "," + getJSONObjectVal(props, "ActionProxyUserRealName"); // 19、relateRoleToOrganization(1或0, proxyUserRealName)
                stateStr += ","; // 20、result=''
                stateStr += "," + getJSONObjectVal(props, "ActionFieldWrite"); // 21、fieldWrite
                stateStr += "," + getJSONObjectVal(props, "ActionColorIndex"); // 22、officeColorIndex
                stateStr += "," + getJSONObjectVal(props, "ActionDept"); // 23、dept
                stateStr += "," + getJSONObjectVal(props, "ActionFlag"); // 24、flag
                stateStr += "," + getJSONObjectVal(props, "ActionDeptMode"); // 25、nodeMode(m_deptMode) 0表示NODE_MODE_ROLE
                stateStr += "," + getJSONObjectVal(props, "ActionStrategy"); // 26、strategy
                stateStr += "," + getJSONObjectVal(props, "ActionItem1"); // 27、item1，是否为结束型节点
                stateStr += "," + getJSONObjectVal(props, "ActionItem2"); // 28、item2
                stateStr += ","; // item3='' 冗余
                stateStr += ","; // item4='' 冗余
                stateStr += ","; // item5='' 冗余
                stateStr += "," + getJSONObjectVal(props, "ActionIsMsg"); // 32、msg(1|0)
                stateStr += ",A_END;\r\n";

                flowString += stateStr;
            }

            org.json.JSONObject pathJsonObjects = flowJsonObject.getJSONObject("paths");
            Iterator irPath = pathJsonObjects.keys();
            while (irPath.hasNext()) {
                String key = (String) irPath.next();
                org.json.JSONObject pathJson = pathJsonObjects.getJSONObject(key);
                if (pathJson == null) {
                    // @task: 删除节点时，可能未从states中清掉
                    continue;
                }

                org.json.JSONObject props = pathJson.getJSONObject("props");

                String pathStr = "workflow_link:";
                pathStr += "2"; // fromtype(连接位置)
                pathStr += ",1";    // totype(连接位置)
                pathStr += "," + getJSONObjectVal(props, "title");  // 2、条件

                pathStr += "," + pathJson.getString("from"); // 3、from
                pathStr += "," + pathJson.getString("to"); // 4、to

                org.json.JSONObject fromState = getState(pathJson.getString("from"), stateJsonObjects);
                int fromX = fromState.getJSONObject("attr").getInt("x");
                int fromW = fromState.getJSONObject("attr").getInt("width");
                int fromY = fromState.getJSONObject("attr").getInt("y");
                int fromH = fromState.getJSONObject("attr").getInt("height");

                org.json.JSONObject toState = getState(pathJson.getString("to"), stateJsonObjects);

                int toX = toState.getJSONObject("attr").getInt("x");
                int toW = toState.getJSONObject("attr").getInt("width");
                int toY = toState.getJSONObject("attr").getInt("y");
                int toH = toState.getJSONObject("attr").getInt("height");

                int startX = fromX + fromW;
                int startY = fromY + fromH / 2; // 开始点位置于from节点的右侧中间点
                pathStr += "," + startX; // start.x
                pathStr += "," + startY; // start.y

                int endX = toX;
                int endY = toY + toH / 2;
                pathStr += "," + endX; // end.x
                pathStr += "," + endY; // end.y

                int joint1X = 0, joint1Y = 0, joint2X = 0, joint2Y = 0;
                // console.log(pathJson);
                JSONArray dots = pathJson.getJSONArray("dots");
                // console.log("getWorkflow", "dots", dots);
                if (dots == null || dots.length() == 0) {
                    // 如果没有dots，则取开始与结束节点之间的中间点
                    joint1X = (startX + endX) / 2;
                    joint1Y = startY;
                } else {
                    joint1X = dots.getJSONObject(0).getInt("x");
                    joint1Y = dots.getJSONObject(0).getInt("y");
                }
                if (dots != null && dots.length() > 1) {
                    joint2X = dots.getJSONObject(1).getInt("x");
                    joint2Y = dots.getJSONObject(1).getInt("y");
                } else {
                    joint2X = joint1X;
                    joint2Y = endY;
                }
                // console.log("joint1X=" + joint1X + " joint1Y=" + joint1Y + " joint2X=" + joint2X + " joint2Y=" + joint2Y);
                pathStr += "," + joint1X;
                pathStr += "," + joint1Y;
                pathStr += "," + joint2X;
                pathStr += "," + joint2Y;
                pathStr += ",0";    // 13、isSpeedup(1|0)
                pathStr += ",2020"; // 14、speedupDate_y
                pathStr += ",12";   // 15、speedupDate_m
                pathStr += ",03";   // 16、speedupDate_d
                pathStr += "," + pathJson.getInt("type"); // 17、TYPE_TOWARD等
                pathStr += "," + getJSONObjectVal(props, "desc");    // 18、condDesc
                pathStr += "," + getJSONObjectVal(props, "conditionType"); // 19、condType(item1)
                pathStr += "," + getJSONObjectVal(props, "expireHour");   // 20、expireAction(item3)
                pathStr += "," + getJSONObjectVal(props, "expireAction");   // 21、expireHour(item2)
                pathStr += ","; // item4 冗余
                pathStr += ","; // item5 冗余
                pathStr += ",L_END;\r\n";

                flowString += pathStr;
            }

            flowString += "version:1, 3, 0, 0;";
            jsonObject.put("ret", 1);
            jsonObject.put("flowString", flowString);

            DebugUtil.i(getClass(), "flowString", flowString);

        } catch (JSONException e) {
            LogUtil.getLog(getClass()).error(e);
            jsonObject.put("ret", 0);
            jsonObject.put("msg", e.getMessage());
        }
        return jsonObject;
    }

    public org.json.JSONObject getState(String id, org.json.JSONObject states) {
        try {
            Iterator ir = states.keys();
            while (ir.hasNext()) {
                String key = (String) ir.next();
                org.json.JSONObject state = null;
                state = states.getJSONObject(key);
                if (state.getString("ID").equals(id)) {
                    return state;
                }
            }
        } catch (JSONException e) {
            LogUtil.getLog(getClass()).error(e);
        }
        return null;
    }

}
