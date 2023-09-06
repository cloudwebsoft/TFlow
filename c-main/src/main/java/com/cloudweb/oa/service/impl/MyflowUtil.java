package com.cloudweb.oa.service.impl;

import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.RandomSecquenceCreator;
import cn.js.fan.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cloudweb.oa.api.IMyflowUtil;
import com.cloudweb.oa.bean.Action;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.flow.WorkflowActionDb;
import com.redmoon.oa.flow.WorkflowDb;
import com.redmoon.oa.flow.WorkflowLinkDb;
import com.redmoon.oa.flow.WorkflowUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MyflowUtil implements IMyflowUtil {
    public static final int ACTION_WIDTH = 100;
    public static final int ACTION_HEIGHT = 50;
    public static final String ACTION_TYPE_START = "start";
    public static final String ACTION_TYPE_TASK = "task";
    public static final String ACTION_TYPE_END = "end";

    public static final int ACTION_SPACE_X = 200;
    public static final int ACTION_SPACE_Y = 50;

    public MyflowUtil() {

    }

    /**
     * 为自由流程生成流程图
     * @param flowId
     * @return
     */
    @Override
    public boolean generateMyflowForFree(int flowId) throws ErrMsgException {
        Map<Integer, Action> map = initCoordinate(flowId);

        JSONObject actions = new JSONObject();
        JSONObject links = new JSONObject();

        WorkflowActionDb wad = new WorkflowActionDb();
        Vector<WorkflowActionDb> v = wad.getActionsOfFlow(flowId);

        JSONObject json = new JSONObject();
        for (WorkflowActionDb wa : v) {
            JSONObject jsonAction = tranWorkflowActionDbToMyFlow(map.get(wa.getId()), wa);
            actions.put(jsonAction.getString("ID"), jsonAction);
        }

        WorkflowLinkDb wld = new WorkflowLinkDb();
        Vector<WorkflowLinkDb> vLink = wld.getLinksOfFlow(flowId);
        for (WorkflowLinkDb wl : vLink) {
            JSONObject jsonLink = tranWorkflowLinkDbToMyflow(wl);
            links.put(jsonLink.getString("lineID"), jsonLink);
        }

        // 初始化inDegree
        Set<String>keySetLink = links.keySet();
        for (String keyRect : actions.keySet()) {
            JSONObject rect = actions.getJSONObject(keyRect);
            String rectId = rect.getString("ID");

            int inDegree = 0;
            for (String keyLink : keySetLink) {
                JSONObject path = links.getJSONObject(keyLink);
                // 便于开发时调试，实际不应出现_path为null的情况
                if (path == null) {
                    throw new ErrMsgException("连接线：" + keyLink + " 不存在");
                }

                // console.log("_path.getType()=" + _path.getType() + " _path.to().getId()=" + _path.to().getId() + " to.getId()=" + to.getId());
                if (path.getIntValue("type") == WorkflowLinkDb.TYPE_BOTH || path.getIntValue("type") == WorkflowLinkDb.TYPE_TOWARD) {
                    String to = path.getString("to");
                    JSONObject toRect = actions.getJSONObject(to);
                    if (toRect != null && rectId.equals(toRect.getString("ID"))) {
                        inDegree++;
                    }
                }
            }

            if (inDegree > 0 && rect.getString("type").equals(ACTION_TYPE_START)) {
                rect.put("type", ACTION_TYPE_TASK);
            }

            rect.put("inDegree", inDegree);
        }

        json.put("states", actions);
        json.put("paths", links);

        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);
        wf.setFlowJson(json.toString());
        return wf.save();
    }

    public Map<Integer, Action> initCoordinate(int flowId) {
        WorkflowActionDb waStart = new WorkflowActionDb();
        waStart = waStart.getStartAction(flowId);
        Action action = new Action();
        action.setX(112);
        action.setY(230);
        action.setId(waStart.getId());
        Map<Integer, Action> mapAction = new HashMap<>();
        mapAction.put(action.getId(), action);

        Vector<WorkflowActionDb> vTo = waStart.getLinkToActions();
        for (int i = 0; i<vTo.size(); i++) {
            initCoordForAction(mapAction, action, vTo.get(i), i);
        }
        return mapAction;
    }

    public void initCoordForAction(Map<Integer, Action> mapAction, Action preAction, WorkflowActionDb toWa, int index) {
        Action action = new Action();
        action.setX(preAction.getX() + ACTION_SPACE_X);
        action.setY(preAction.getY() + (preAction.getY() + ACTION_SPACE_Y) * (index));
        action.setId(toWa.getId());
        mapAction.put(action.getId(), action);
        Vector<WorkflowActionDb> vTo = toWa.getLinkToActions();
        for (int i = 0; i<vTo.size(); i++) {
            initCoordForAction(mapAction, action, vTo.get(i), i);
        }
    }

    public JSONObject tranWorkflowActionDbToMyFlow(Action action, WorkflowActionDb wa) {
        JSONObject json = new JSONObject();
        try {
            int x = action.getX();
            int y = action.getY();
            String title = wa.getTitle();
            String internalName = wa.getInternalName();
            String userName = wa.getUserName(); // 当节点上定义的为“用户”型处理者时，表示动作处理者，当节点处理完后，也是存储动作处理者，两种情况都可能有多个
            int status = wa.getStatus();
            String userRealName = wa.getUserRealName(); // 用户真实姓名
            String jobCode = wa.getJobCode(); // 角色编码
            String jobName = wa.getJobName(); // 角色名称
            String ActionProxyJobCode = "";
            String ActionProxyJobName = "";
            String ActionProxyUserName = "";
            String ActionProxyUserRealName = "";
            String ActionFieldWrite = wa.getFieldWrite();
            String ActionColorIndex = String.valueOf(wa.getOfficeColorIndex());
            String ActionDept = wa.getDept();
            String ActionFlag = wa.getFlag();
            String ActionDeptMode = String.valueOf(wa.getNodeMode());
            String ActionStrategy = wa.getStrategy();
            String ActionItem1 = wa.getItem1();
            String ActionItem2 = wa.getItem2();
            String ActionIsMsg = wa.isMsg()?"1":"0";

            JSONObject jsonProps = new JSONObject();
            jsonProps.put("ActionTitle", JSONObject.parseObject("{'name':'ActionTitle', 'value':'" + title + "'}"));
            jsonProps.put("ID", JSONObject.parseObject("{'name':'ID', 'value':'" + internalName + "'}"));
            jsonProps.put("ActionUser", JSONObject.parseObject("{'name':'ActionUser', 'value':'" + userName + "'}"));
            jsonProps.put("ActionCheckState", JSONObject.parseObject("{'name':'ActionCheckState', 'value':'" + status + "'}"));
            jsonProps.put("ActionUserRealName", JSONObject.parseObject("{'name':'ActionUserRealName', 'value':'" + userRealName + "'}"));
            jsonProps.put("ActionJobCode", JSONObject.parseObject("{'name':'ActionJobCode', 'value':'" + jobCode + "'}"));
            jsonProps.put("ActionJobName", JSONObject.parseObject("{'name':'ActionJobName', 'value':'" + jobName + "'}"));
            jsonProps.put("ActionProxyJobCode", JSONObject.parseObject("{'name':'ActionProxyJobCode', 'value':'" + ActionProxyJobCode + "'}"));
            jsonProps.put("ActionProxyJobName", JSONObject.parseObject("{'name':'ActionProxyJobName', 'value':'" + ActionProxyJobName + "'}"));
            jsonProps.put("ActionProxyUserName", JSONObject.parseObject("{'name':'ActionProxyUserName', 'value':'" + ActionProxyUserName + "'}"));
            jsonProps.put("ActionProxyUserRealName", JSONObject.parseObject("{'name':'ActionProxyUserRealName', 'value':'" + ActionProxyUserRealName + "'}"));
            jsonProps.put("ActionFieldWrite", JSONObject.parseObject("{'name':'ActionFieldWrite', 'value':'" + ActionFieldWrite + "'}"));
            jsonProps.put("ActionColorIndex", JSONObject.parseObject("{'name':'ActionColorIndex', 'value':'" + ActionColorIndex + "'}"));
            jsonProps.put("ActionDept", JSONObject.parseObject("{'name':'ActionDept', 'value':'" + ActionDept + "'}"));
            jsonProps.put("ActionFlag", JSONObject.parseObject("{'name':'ActionFlag', 'value':'" + ActionFlag + "'}"));
            jsonProps.put("ActionDeptMode", JSONObject.parseObject("{'name':'ActionDeptMode', 'value':'" + ActionDeptMode + "'}"));
            jsonProps.put("ActionStrategy", JSONObject.parseObject("{'name':'ActionStrategy', 'value':'" + ActionStrategy + "'}"));
            jsonProps.put("ActionItem1", JSONObject.parseObject("{'name':'ActionItem1', 'value':'" + ActionItem1 + "'}"));
            jsonProps.put("ActionItem2", JSONObject.parseObject("{'name':'ActionItem2', 'value':'" + ActionItem2 + "'}"));
            jsonProps.put("ActionIsMsg", JSONObject.parseObject("{'name':'ActionIsMsg', 'value':'" + ActionIsMsg + "'}"));

            json.put("props", jsonProps);

            json.put("ID", internalName);
            if (wa.getIsStart()==0) {
                json.put("type", ACTION_TYPE_TASK);
            }
            else {
                json.put("type", ACTION_TYPE_START);
            }

            JSONObject jsonText = new JSONObject();
            // jsonText.put("text", jobName + ":" + title);
            jsonText.put("text", userRealName);
            json.put("text", jsonText);

            JSONObject jsonAttr = new JSONObject();
            jsonAttr.put("x", x);
            jsonAttr.put("y", y);
            jsonAttr.put("width", ACTION_WIDTH);
            jsonAttr.put("height", ACTION_HEIGHT);

            json.put("attr", jsonAttr);
        } catch (Exception e) {
            LogUtil.getLog(getClass()).error(e);
        }
        return json;
    }

    public JSONObject actionToMyflow(String str) {
        int isStart;
        if (str.startsWith("workflow_start")) {
            isStart = 1;
        } else if (str.startsWith("workflow_action")) {
            isStart = 0;
        } else {
            return null;
        }
        JSONObject json = new JSONObject();
        int p = str.indexOf(":");
        int q = str.indexOf(";");
        if (p == -1 || q == -1) {
            LogUtil.getLog(getClass()).info("fromString:格式错误！");
            return null;
        }

        try {
            str = str.substring(p + 1, q);
            // 注意当a,b,,,这样的字符串在split时的长度只为2，后面的,号分隔的并不被计入，而当a,b,,,s时，长度为5
            String[] ary = str.split(",");
            if (ary.length < WorkflowActionDb.STRING_ARRAY_LENGTH) {
                LogUtil.getLog(getClass()).info("fromString:数组长度小于" + WorkflowActionDb.STRING_ARRAY_LENGTH + "！");
                return null;
            }

            int x = (int) StrUtil.toDouble(ary[0]);
            int y = (int) StrUtil.toDouble(ary[1]);
            String title = WorkflowActionDb.tran(ary[4]);
            String internalName = ary[5];
            String userName = WorkflowActionDb.tran(ary[7]); // 当节点上定义的为“用户”型处理者时，表示动作处理者，当节点处理完后，也是存储动作处理者，两种情况都可能有多个
            String strstatus = ary[11];
            int status = StrUtil.toInt(strstatus, WorkflowActionDb.STATE_NOTDO);
            String userRealName = WorkflowActionDb.tran(ary[13]); // 用户真实姓名
            String jobCode = WorkflowActionDb.tran(ary[14]); // 角色编码
            String jobName = WorkflowActionDb.tran(ary[15]); // 角色名称
            String ActionProxyJobCode = WorkflowActionDb.tran(ary[16]);
            String ActionProxyJobName = WorkflowActionDb.tran(ary[17]);
            String ActionProxyUserName = WorkflowActionDb.tran(ary[18]);
            String ActionProxyUserRealName = WorkflowActionDb.tran(ary[19]);
            String ActionFieldWrite = WorkflowActionDb.tran(ary[21]);
            String ActionColorIndex = WorkflowActionDb.tran(ary[22]);
            String ActionDept = WorkflowActionDb.tran(ary[23]);
            String ActionFlag = WorkflowActionDb.tran(ary[24]);
            String ActionDeptMode = WorkflowActionDb.tran(ary[25]);
            String ActionStrategy = WorkflowActionDb.tran(ary[26]);
            String ActionItem1 = WorkflowActionDb.tran(ary[27]);
            String ActionItem2 = WorkflowActionDb.tran(ary[28]);
            String ActionIsMsg = WorkflowActionDb.tran(ary[32]);

            JSONObject jsonProps = new JSONObject();
            jsonProps.put("ActionTitle", JSONObject.parseObject("{'name':'ActionTitle', 'value':'" + title + "'}"));
            jsonProps.put("ID", JSONObject.parseObject("{'name':'ID', 'value':'" + internalName + "'}"));
            jsonProps.put("ActionUser", JSONObject.parseObject("{'name':'ActionUser', 'value':'" + userName + "'}"));
            jsonProps.put("ActionCheckState", JSONObject.parseObject("{'name':'ActionCheckState', 'value':'" + status + "'}"));
            jsonProps.put("ActionUserRealName", JSONObject.parseObject("{'name':'ActionUserRealName', 'value':'" + userRealName + "'}"));
            jsonProps.put("ActionJobCode", JSONObject.parseObject("{'name':'ActionJobCode', 'value':'" + jobCode + "'}"));
            jsonProps.put("ActionJobName", JSONObject.parseObject("{'name':'ActionJobName', 'value':'" + jobName + "'}"));
            jsonProps.put("ActionProxyJobCode", JSONObject.parseObject("{'name':'ActionProxyJobCode', 'value':'" + ActionProxyJobCode + "'}"));
            jsonProps.put("ActionProxyJobName", JSONObject.parseObject("{'name':'ActionProxyJobName', 'value':'" + ActionProxyJobName + "'}"));
            jsonProps.put("ActionProxyUserName", JSONObject.parseObject("{'name':'ActionProxyUserName', 'value':'" + ActionProxyUserName + "'}"));
            jsonProps.put("ActionProxyUserRealName", JSONObject.parseObject("{'name':'ActionProxyUserRealName', 'value':'" + ActionProxyUserRealName + "'}"));
            jsonProps.put("ActionFieldWrite", JSONObject.parseObject("{'name':'ActionFieldWrite', 'value':'" + ActionFieldWrite + "'}"));
            jsonProps.put("ActionColorIndex", JSONObject.parseObject("{'name':'ActionColorIndex', 'value':'" + ActionColorIndex + "'}"));
            jsonProps.put("ActionDept", JSONObject.parseObject("{'name':'ActionDept', 'value':'" + ActionDept + "'}"));
            jsonProps.put("ActionFlag", JSONObject.parseObject("{'name':'ActionFlag', 'value':'" + ActionFlag + "'}"));
            jsonProps.put("ActionDeptMode", JSONObject.parseObject("{'name':'ActionDeptMode', 'value':'" + ActionDeptMode + "'}"));
            jsonProps.put("ActionStrategy", JSONObject.parseObject("{'name':'ActionStrategy', 'value':'" + ActionStrategy + "'}"));
            jsonProps.put("ActionItem1", JSONObject.parseObject("{'name':'ActionItem1', 'value':'" + ActionItem1 + "'}"));
            jsonProps.put("ActionItem2", JSONObject.parseObject("{'name':'ActionItem2', 'value':'" + ActionItem2 + "'}"));
            jsonProps.put("ActionIsMsg", JSONObject.parseObject("{'name':'ActionIsMsg', 'value':'" + ActionIsMsg + "'}"));

            json.put("props", jsonProps);

            json.put("ID", internalName);
            if (isStart==0) {
                json.put("type", ACTION_TYPE_TASK);
            }
            else {
                json.put("type", ACTION_TYPE_START);
            }

            int isEnd = StrUtil.toInt(WorkflowActionDb.tran(ary[27]), 0); // 是否为结束型节点
            if (isEnd==1) {
                json.put("type", ACTION_TYPE_END);
            }

            JSONObject jsonText = new JSONObject();
            jsonText.put("text", jobName + ":" + title);
            json.put("text", jsonText);

            JSONObject jsonAttr = new JSONObject();
            jsonAttr.put("x", x);
            jsonAttr.put("y", y);
            jsonAttr.put("width", ACTION_WIDTH);
            jsonAttr.put("height", ACTION_HEIGHT);

            json.put("attr", jsonAttr);
        } catch (Exception e) {
            LogUtil.getLog(getClass()).error(e);
        }
        return json;
    }

    public JSONObject tranWorkflowLinkDbToMyflow(WorkflowLinkDb wld) {
        JSONObject json = new JSONObject();

        String from = WorkflowLinkDb.tran(wld.getFrom());
        String to = WorkflowLinkDb.tran(wld.getTo());
        int type = wld.getType();
        json.put("from", from);
        json.put("to", to);
        json.put("type", type);

        String title = WorkflowLinkDb.tran(wld.getTitle());
        String condDesc = WorkflowLinkDb.tran(wld.getCondDesc());
        String conditionType = WorkflowLinkDb.tran(wld.getCondType());
        String expireHour = String.valueOf(wld.getExpireHour());
        String expireAction = wld.getExpireAction();

        JSONObject jsonProps = new JSONObject();
        jsonProps.put("title", JSONObject.parseObject("{'name':'title', 'value':'" + title + "'}"));
        jsonProps.put("desc", JSONObject.parseObject("{'name':'desc', 'value':'" + condDesc + "'}"));
        jsonProps.put("conditionType", JSONObject.parseObject("{'name':'conditionType', 'value':'" + conditionType + "'}"));
        jsonProps.put("expireAction", JSONObject.parseObject("{'name':'expireAction', 'value':'" + expireHour + "'}"));
        jsonProps.put("expireHour", JSONObject.parseObject("{'name':'expireHour', 'value':'" + expireAction + "'}"));

        String lineId = "path" + RandomSecquenceCreator.getId(10);
        json.put("lineID", lineId);
        json.put("from", from);
        json.put("to", to);
        json.put("type", type);

        JSONArray dots = new JSONArray();
        json.put("dots", dots);

        JSONObject jsonText = new JSONObject();
        jsonText.put("text", condDesc);

        JSONObject jsonPos = new JSONObject();
        jsonPos.put("x", 0);
        jsonPos.put("y", -10);
        jsonText.put("textPos", jsonPos);

        json.put("text", jsonText);

        jsonProps.put("text", JSONObject.parseObject("{'name':'text', 'value':''}"));
        json.put("props", jsonProps);

        return json;
    }

    public JSONObject linkToMyflow(String str, JSONObject actions) {
        JSONObject json = new JSONObject();
        if (!str.startsWith("workflow_link")) {
            return null;
        }

        int p = str.indexOf(":");
        int q = str.indexOf(";");
        if (p == -1 || q == -1) {
            LogUtil.getLog(getClass()).info("格式错误！");
            return null;
        }
        str = str.substring(p + 1, q);
        String[] ary = str.split(",");
        if (ary.length < 18) {
            LogUtil.getLog(getClass()).info("数组长度小于18！");
            return null;
        }

        String from = WorkflowLinkDb.tran(ary[3]);
        String to = WorkflowLinkDb.tran(ary[4]);
        int type = Integer.parseInt(ary[17]);
        json.put("from", from);
        json.put("to", to);
        json.put("type", type);

        String title = WorkflowLinkDb.tran(ary[2]);
        String condDesc = WorkflowLinkDb.tran(ary[18]);
        String conditionType = WorkflowLinkDb.tran(ary[19]);
        String expireHour = ary[20];
        String expireAction = ary[21];

        JSONObject jsonProps = new JSONObject();
        jsonProps.put("title", JSONObject.parseObject("{'name':'title', 'value':'" + title + "'}"));
        jsonProps.put("desc", JSONObject.parseObject("{'name':'desc', 'value':'" + condDesc + "'}"));
        jsonProps.put("conditionType", JSONObject.parseObject("{'name':'conditionType', 'value':'" + conditionType + "'}"));
        jsonProps.put("expireAction", JSONObject.parseObject("{'name':'expireAction', 'value':'" + expireHour + "'}"));
        jsonProps.put("expireHour", JSONObject.parseObject("{'name':'expireHour', 'value':'" + expireAction + "'}"));

        String lineID = "path" + RandomSecquenceCreator.getId(10);
        json.put("lineID", lineID);
        json.put("from", from);
        json.put("to", to);
        json.put("type", type);

        JSONArray dots = new JSONArray();

        // 124,239,168,239,145,239,145,239 在一条水平线上的4个点
        // start.x=ary[5] start.y=ary[6] end.x=ary[7] end.y=ary[8] join1.x=ary[9] join1.y=ary[10] join2.x=ary[11] join2.y=ary[12];
        int startX = StrUtil.toInt(ary[5]);
        int startY = StrUtil.toInt(ary[6]);
        int endX = StrUtil.toInt(ary[7]);
        int endY = StrUtil.toInt(ary[8]);
        int join1X = StrUtil.toInt(ary[9]);
        int join1Y = StrUtil.toInt(ary[10]);
        int join2X = StrUtil.toInt(ary[11]);
        int join2Y = StrUtil.toInt(ary[12]);

        // 是否为直线
        boolean isStraightLine = false;
        // 判断中间点是否相等，如相等则只需记录一个中间点
        boolean isJoin1EqJoin2 = false;
        if (join1X == join2X && join1Y == join2Y) {
            isJoin1EqJoin2 = true;
        }

        if (isJoin1EqJoin2) {
            // 如果4个点的Y坐标或X坐标点均相等，则说明在水平或垂直一条线上，无需记录点
            if (startX == endX && join1X == startX) {
                isStraightLine = true;
            } else if (startY == endY && join1Y == startY) {
                isStraightLine = true;
            }
        }
        else {
            if (join1X == join2X) {
                // 详见：生成myflow流程图.docx
                // myflow.js 当会签时，如果到聚合节点的分支线的第一个join点的x过于靠左，就会在恢复时出现半截线带箭头指向半空的bug
                // 将join点的x坐标加以修正，将连接点修正到居中，能修复此问题，但会带来新问题，导致跨越节点的连接线会穿过被跨越的节点
                if (actions!=null) {
                    JSONObject actionFrom = actions.getJSONObject(from);
                    if (actionFrom != null) {
                        JSONObject actionFromAttr = actionFrom.getJSONObject("attr");
                        if (actionFromAttr != null) {
                            int fromRight = actionFrom.getJSONObject("attr").getIntValue("x") + ACTION_WIDTH;
                            JSONObject actionTo = actions.getJSONObject(to);
                            if (actionTo != null) {
                                JSONObject actionToAttr = actionTo.getJSONObject("attr");
                                if (actionToAttr != null) {
                                    int toLeft = actionToAttr.getIntValue("x");
                                    // 改为判断连线线是否过于靠左，即如果join1x距离formRight比toLeft近，则将连接点调整为居中，否则不变
                                    if (join1X - fromRight < toLeft - join1X) {
                                        join1X = fromRight + (toLeft - fromRight) / 2;
                                        join2X = join1X;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!isStraightLine) {
            if (!isJoin1EqJoin2) {
                JSONObject jsonDot = new JSONObject();
                jsonDot.put("x", join1X);
                jsonDot.put("y", join1Y);
                dots.add(jsonDot);
                jsonDot = new JSONObject();
                jsonDot.put("x", join2X);
                jsonDot.put("y", join2Y);
                dots.add(jsonDot);
            }
        }

        json.put("dots", dots);

        JSONObject jsonText = new JSONObject();
        jsonText.put("text", condDesc);

        JSONObject jsonPos = new JSONObject();
        jsonPos.put("x", 0);
        jsonPos.put("y", -10);
        jsonText.put("textPos", jsonPos);

        json.put("text", jsonText);

        jsonProps.put("text", JSONObject.parseObject("{'name':'text', 'value':''}"));
        json.put("props", jsonProps);

        return json;
    }

    /**
     * 将flowString转换为flowJson，以便于显示流程图，兼容非IE浏览器
     * @param flowString
     * @return
     */
    @Override
    public String toMyflow(String flowString) throws ErrMsgException {
        if (!flowString.startsWith("paper")) {
            return "";
        }

        JSONObject actions = new JSONObject();
        JSONObject links = new JSONObject();

        String[] ary = flowString.split("\\r\\n");
        int len = ary.length;

        JSONObject json = new JSONObject();

        for (int i = 1; i < len; i++) {
            JSONObject jsonAction = actionToMyflow(ary[i]);
            if (jsonAction != null) {
                actions.put(jsonAction.getString("ID"), jsonAction);
            }
        }

        for (int i = 1; i < len; i++) {
            JSONObject jsonLink = linkToMyflow(ary[i], actions);
            if (jsonLink != null) {
                links.put(jsonLink.getString("lineID"), jsonLink);
            }
        }

        // 初始化inDegree
        Set<String>keySetLink = links.keySet();
        for (String keyRect : actions.keySet()) {
            JSONObject rect = actions.getJSONObject(keyRect);
            String rectId = rect.getString("ID");

            int inDegree = 0;
            for (String keyLink : keySetLink) {
                JSONObject path = links.getJSONObject(keyLink);
                // 便于开发时调试，实际不应出现_path为null的情况
                if (path == null) {
                    throw new ErrMsgException("连接线：" + keyLink + " 不存在");
                }

                // console.log("_path.getType()=" + _path.getType() + " _path.to().getId()=" + _path.to().getId() + " to.getId()=" + to.getId());
                if (path.getIntValue("type") == WorkflowLinkDb.TYPE_BOTH || path.getIntValue("type") == WorkflowLinkDb.TYPE_TOWARD) {
                    String to = path.getString("to");
                    JSONObject toRect = actions.getJSONObject(to);
                    if (toRect != null && rectId.equals(toRect.getString("ID"))) {
                        inDegree++;
                    }
                }
            }

            if (inDegree > 0 && rect.getString("type").equals(ACTION_TYPE_START)) {
                rect.put("type", ACTION_TYPE_TASK);
            }

            rect.put("inDegree", inDegree);
        }

        json.put("states", actions);
        json.put("paths", links);
        return json.toString();
    }
}

