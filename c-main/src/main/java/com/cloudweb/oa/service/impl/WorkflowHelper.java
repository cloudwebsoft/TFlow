package com.cloudweb.oa.service.impl;

import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.StrUtil;
import com.cloudweb.oa.api.IWorkflowHelper;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.flow.*;
import com.redmoon.oa.sys.DebugUtil;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Iterator;

@Service
public class WorkflowHelper implements IWorkflowHelper {
    @Override
    public boolean renewWorkflowString(WorkflowDb wf, WorkflowActionDb wad, boolean isSaveFlow) throws ErrMsgException {
        // 当在调试模式中时，点保存（如保存可写字段），会调用此方法，而此时wf.getTypeCode()为null
        if (!StrUtil.isEmpty(wf.getTypeCode())) {
            Leaf lf = new Leaf();
            lf = lf.getLeaf(wf.getTypeCode());
            if (lf.getType() == Leaf.TYPE_FREE) {
                return true;
            }
        }

        StringBuilder flowString = new StringBuilder(wf.getFlowString());
        String flowJson = wf.getFlowJson();

        if (StringUtils.isEmpty(flowString.toString()) && StringUtils.isEmpty(flowJson)) {
            return true;
        }
        if (!StringUtils.isEmpty(flowString.toString()) && !flowString.toString().startsWith("paper")) {
            return false;
        }

        String[] flowary = flowString.toString().split("\\r\\n");
        int len = flowary.length;
        boolean isFound = false;
        for (int i = 1; i < len; i++) {
            String str = flowary[i];
            boolean isAction = false;
            int isStart = 0;
            if (str.startsWith("workflow_start")) {
                isAction = true;
                isStart = 1;
            } else if (str.startsWith("workflow_action")) {
                isAction = true;
                isStart = 0;
            }
            if (isAction) {
                int p = str.indexOf(":");
                int q = str.indexOf(";");
                if (p == -1 || q == -1) {
                    LogUtil.getLog(getClass()).info("regenerateWorkflowString action格式错误！");
                    return false;
                }

                str = str.substring(p + 1, q);
                // 注意当a,b,,,这样的字符串在split时的长度可能只为2，后面的,号分隔的不被计入
                String[] ary = str.split("\\,");
                if (ary.length < WorkflowActionDb.STRING_ARRAY_LENGTH) {
                    LogUtil.getLog(getClass()).info("renewWorkflowString: id=" + wf.getId() + " actionId=" + wad.getId() + " 数组长度为" + ary.length + " 小于" +
                            WorkflowActionDb.STRING_ARRAY_LENGTH +
                            "！");
                    return false;
                }

                String internalName = ary[5];
                if (internalName.equals(wad.getInternalName())) {
                    isFound = true;
                    ary[4] = wad.tranReverse(wad.getTitle());
                    ary[7] = wad.tranReverse(wad.getUserName());
                    ary[11] = String.valueOf(wad.getStatus());
                    ary[12] = wad.tranReverse(wad.getReason());
                    ary[13] = wad.tranReverse(wad.getUserRealName());
                    ary[14] = wad.tranReverse(wad.getJobCode());
                    ary[15] = wad.tranReverse(wad.getJobName());
                    ary[16] = wad.tranReverse("" + wad.getDirection()); // wad.getProxyJobCode());
                    ary[17] = wad.tranReverse(wad.getRankCode()); // .getProxyJobName());
                    ary[18] = wad.tranReverse(wad.getRankName()); // proxyUserName
                    ary[19] = wad.tranReverse(wad.isRelateRoleToOrganization()?"1":"0"); // proxyUserRealName
                    ary[20] = wad.tranReverse(wad.getResult()); // 处理时间
                    ary[21] = wad.tranReverse(wad.getFieldWrite());
                    ary[22] = String.valueOf(wad.getOfficeColorIndex());
                    ary[23] = wad.tranReverse(wad.getDept()); // dept的信息只存储于设计器flowString的节点中，在数据库中并不存储
                    ary[24] = wad.tranReverse(wad.getFlag()); // flag的信息只存储于设计器flowString的节点中，在数据库中并不存储
                    ary[25] = String.valueOf(wad.getNodeMode());
                    if (ary.length>=27) {
                        ary[26] = wad.tranReverse(wad.getStrategy());
                    }
                    if (ary.length>=28) {
                        ary[27] = wad.tranReverse(wad.getItem1()); // 是否为结束型节点
                    }
                    if (ary.length>=29) {
                        ary[28] = wad.tranReverse(wad.getItem2()); // item2
                    }
                    if (ary.length>=30) {
                        ary[29] = ""; // item3
                    }
                    if (ary.length>=31) {
                        ary[30] = ""; // item4
                    }
                    if (ary.length>=32) {
                        ary[31] = ""; // item5
                    }
                    if (ary.length>=33) {
                        ary[32] = wad.isMsg() ? "1" : "0";
                    }

                    String s = "";
                    for (String value : ary) {
                        if ("".equals(s)) {
                            s = value;
                        } else {
                            s += "," + value;
                        }
                    }
                    // LogUtil.getLog(getClass()).info("WorkflowDb.java renewWorkflowString: id=" + id + " actionId=" + wad.getId() + " isSaveAction=" + isSaveAction + " wad status=" + wad.getStatusName());

                    // 重建action的字符串
                    if (isStart==1) {
                        flowary[i] = "workflow_start:" + s + ";";
                    } else {
                        flowary[i] = "workflow_action:" + s + ";";
                    }
                    // LogUtil.getLog(getClass()).info("renewWorkflowString flowary[i]=" + flowary[i]);
                }
            }
        }
        if (isFound) {
            // 重建flowString
            flowString = new StringBuilder();
            for (String s : flowary) {
                flowString.append(s).append("\r\n");
            }
            wf.setFlowString(flowString.toString());
        }
        if (!StringUtils.isEmpty(flowJson)) {
            JSONObject flowJsonObject;
            try {
                // DebugUtil.i(getClass(), "renewWorkflowString", flowJson);
                // alibaba的JSONObject不能parse非正规的key不带引号的json字符串

                // ActionFieldWrite及ActionItem2中可能含有\comma这样的字符，会导致new JSONObject(flowJson)出错，所以要先tran转换成逗号
                // flowJsonObject = new JSONObject(flowJson);
                flowJsonObject = new JSONObject(WorkflowActionDb.tran(flowJson));

                JSONObject stateJsonObject = flowJsonObject.getJSONObject("states");
                Iterator ir = stateJsonObject.keys();
                while (ir.hasNext()) {
                    String key = (String)ir.next();
                    JSONObject state = stateJsonObject.getJSONObject(key);
                    if (state.getString("ID").equals(wad.getInternalName())) {
                        // 如果是调试模式，则getUserRealName为空
                        if ("".equals(wad.getUserRealName())) {
                            state.getJSONObject("text").put("text", wad.tranReverseForFlowJson(wad.getJobName()) + "：" + wad.getTitle());
                        }
                        else {
                            // 不能tranReverseForFlowJson，否则当一个节点上有多个处理时，流程图的节点上会显示 张三comma李四
                            state.getJSONObject("text").put("text", wad.getUserRealName() + "：" + wad.getTitle());
                        }

                        JSONObject props = state.getJSONObject("props");
                        props.getJSONObject("ActionTitle").put("value", wad.tranReverseForFlowJson(wad.getTitle()));
                        props.getJSONObject("ActionUser").put("value", wad.tranReverseForFlowJson(wad.getUserName()));
                        props.getJSONObject("ActionCheckState").put("value",  String.valueOf(wad.getStatus()));
                        props.getJSONObject("ActionUserRealName").put("value", wad.tranReverseForFlowJson(wad.getUserRealName()));
                        props.getJSONObject("ActionJobCode").put("value", wad.tranReverseForFlowJson(wad.getJobCode()));
                        props.getJSONObject("ActionJobName").put("value", wad.tranReverseForFlowJson(wad.getJobName()));
                        props.getJSONObject("ActionProxyJobCode").put("value",  String.valueOf(wad.getDirection()));
                        props.getJSONObject("ActionProxyJobName").put("value", wad.tranReverseForFlowJson(wad.getRankCode()));
                        props.getJSONObject("ActionProxyUserName").put("value", wad.tranReverseForFlowJson(wad.getRankName()));
                        props.getJSONObject("ActionProxyUserRealName").put("value", wad.isRelateRoleToOrganization()?"1":"0");
                        // ary[12] = wad.tranReverseForFlowJson(wad.getReason());
                        // ary[20] = wad.tranReverseForFlowJson(wad.getResult()); // 处理时间
                        props.getJSONObject("ActionFieldWrite").put("value", wad.tranReverseForFlowJson(wad.getFieldWrite()));
                        props.getJSONObject("ActionColorIndex").put("value", String.valueOf(wad.getOfficeColorIndex()));
                        props.getJSONObject("ActionDept").put("value", wad.tranReverseForFlowJson(wad.getDept()));
                        props.getJSONObject("ActionFlag").put("value", wad.tranReverseForFlowJson(wad.getFlag()));
                        props.getJSONObject("ActionDeptMode").put("value", String.valueOf(wad.getNodeMode()));
                        props.getJSONObject("ActionStrategy").put("value", wad.tranReverseForFlowJson(wad.getStrategy()));
                        props.getJSONObject("ActionItem1").put("value", wad.tranReverseForFlowJson(wad.getItem1()));   // 是否为结束型节点
                        props.getJSONObject("ActionItem2").put("value", wad.tranReverseForFlowJson(wad.getItem2()));
                        props.getJSONObject("ActionIsMsg").put("value", wad.isMsg() ? "1" : "0");
                    }
                }

                flowJson = flowJsonObject.toString();
                flowJson = wad.tranForFlowJson(flowJson);
                wf.setFlowJson(flowJson);
            } catch (JSONException e) {
                LogUtil.getLog(getClass()).error(e);
                DebugUtil.e(getClass(), "renewWorkflowString", "格式非法：" + flowJson);
            }
        }

        if (isFound) {
            if (isSaveFlow) {
                wf.setRenewed(true);
                wf.save();
            }
        }
        return true;
    }

    /**
     * workflow_action:410.000000,98.000000,490.000000,138.000000,,179,0,新用户16,2,174,,2,;
     * @param str String
     * @param isCheck boolean
     */
    @Override
    public boolean fromString(WorkflowActionDb wa, String str, boolean isCheck) throws ErrMsgException {
        if (str.startsWith("workflow_start")) {
            wa.setIsStart(1);
        }
        else if (str.startsWith("workflow_action")) {
            wa.setIsStart(0);
        } else {
            return false;
        }

        int p = str.indexOf(":");
        int q = str.indexOf(";");
        if (p==-1 || q==-1) {
            LogUtil.getLog(getClass()).info("fromString:格式错误！");
            return false;
        }

        try {
            str = str.substring(p + 1, q);
            // 注意当a,b,,,这样的字符串在split时的长度只为2，后面的,号分隔的并不被计入，而当a,b,,,s时，长度为5
            String[] ary = str.split("\\,");
            if (ary.length < WorkflowActionDb.STRING_ARRAY_LENGTH) {
                LogUtil.getLog(getClass()).info("fromString:数组长度小于" + WorkflowActionDb.STRING_ARRAY_LENGTH + "！");
                return false;
            }
            wa.setTitle(tran(ary[4]));
            wa.setInternalName(ary[5]);
            wa.setUserName(tran(ary[7])); // userName当节点上定义的为“用户”型处理者时，表示动作处理者，当节点处理完后，也是存储动作处理者，两种情况都可能有多个
            // ary[6] GetGroup()始终为0
            String strstatus = ary[11];
            wa.setStatus(StrUtil.toInt(strstatus, WorkflowActionDb.STATE_NOTDO));
            wa.setReason(tran(ary[12])); // reason
            wa.setUserRealName(tran(ary[13])); // userRealName 用户真实姓名
            wa.setJobCode(tran(ary[14])); // jobCode 角色编码
            wa.setJobName(tran(ary[15])); // jobName 角色名称
            wa.setDirection(WorkflowActionDb.DIRECTION_UP); // 行文方向，字段proxyJobCode
            if (StrUtil.isNumeric(ary[16])) {
                wa.setDirection(Integer.parseInt(ary[16]));
            }
            wa.setRankCode(tran(ary[17])); // 职级编码，字段proxyJobName
            wa.setRankName(tran(ary[18])); // 职级名称，字段proxyUserName
            String strRelateRoleToOrganization = tran(ary[19]); // relateRoleToOrganization 角色与组织机构(行文方向)、职级、部门相关联，字段proxyUserRealName
            wa.setRelateRoleToOrganization(strRelateRoleToOrganization.equals("1"));
            wa.setResult(tran(ary[20])); // result
            wa.setFieldWrite(tran(ary[21])); // fieldWrite

            int officeColorIndex = 6; // red
            if (StrUtil.isNumeric(ary[22])) {
                officeColorIndex = Integer.parseInt(ary[22]); // officeColorIndex
            }
            wa.setOfficeColorIndex(officeColorIndex);

            wa.setDept(tran(ary[23])); // dept
            wa.setFlag(tran(ary[24]));  // flag
            wa.setNodeMode(WorkflowActionDb.NODE_MODE_ROLE);
            String strDeptMode = tran(ary[25]);
            if (StrUtil.isNumeric(strDeptMode)) {
                wa.setNodeMode(StrUtil.toInt(strDeptMode));
            }
            if (ary.length>=27) {
                wa.setStrategy(tran(ary[26]));
            }
            if (ary.length>=28) {
                wa.setItem1(tran(ary[27])); // 是否为结束型节点
            }
            // 格式为 { relateToAction , ignoreType , kind , fieldHide , isDelayed , timeDelayedValue , timeDelayedUnit , canPrivUserModifyDelayDate, formView, relateDeptManager };
            if (ary.length >= 29) {
                wa.setItem2(tran(ary[28]));
            }
            boolean msg;
            if (ary.length >= 33) {
                String strIsMsg = tran(ary[32]);
                if ("1".equals(strIsMsg)) {
                    msg = true;
                }
                else {
                    msg = false;
                }
            }
            else {
                msg = true;
                LogUtil.getLog(getClass()).error("取是否发送消息提醒失败，客户端版本低，需安装1, 3, 0, 0以上版本！");
            }
            wa.setMsg(msg);

            // LogUtil.getLog(getClass()).info("fromString:dept=" + dept);

            // 检查action是否合法
            if (isCheck) {
                WorkflowActionChecker wack = new WorkflowActionChecker();
                if (!wack.check(wa)) {
                    throw new ErrMsgException(wack.getErrMsg());
                }
            }
        }
        catch (Exception e) {
            LogUtil.getLog(getClass()).error(e);
            throw new ErrMsgException(e.getMessage());
        }
        return true;
    }

    /**
     * workflow_link:2,1,,172,174,100,118,150,118,125,118,125,118,0,2005,05,02,新用户13,蓝风;
     * @param str String
     */
    @Override
    public boolean fromString(WorkflowLinkDb workflowLinkDb, String str) throws ErrMsgException {
        if (!str.startsWith("workflow_link")) {
            return false;
        }
        int p = str.indexOf(":");
        int q = str.indexOf(";");
        if (p==-1 || q==-1) {
            LogUtil.getLog(getClass()).info("格式错误！");
            return false;
        }
        str = str.substring(p+1, q);
        String[] ary = str.split("\\,");
        if (ary.length<18) {
            LogUtil.getLog(getClass()).info("数组长度小于18！");
            return false;
        }
        int isSpeedup = Integer.parseInt(ary[13]);
        workflowLinkDb.setIsSpeedup(isSpeedup);
        if (isSpeedup==1) {
            int y = Integer.parseInt(ary[14]);
            int m = Integer.parseInt(ary[15]);
            int d = Integer.parseInt(ary[16]);
            Calendar speedupDate = Calendar.getInstance();
            speedupDate.set(y, m - 1, d);
            workflowLinkDb.setSpeedupDate(speedupDate);
        }
        workflowLinkDb.setTitle(tran(ary[2]));
        workflowLinkDb.setFrom(tran(ary[3]));
        workflowLinkDb.setTo(tran(ary[4]));
        workflowLinkDb.setType(Integer.parseInt(ary[17]));

        if (ary.length>=19) {
            workflowLinkDb.setCondDesc(ary[18]);
        }
        if (ary.length>=20) {
            workflowLinkDb.setCondType(ary[19]); // m_item1
        }
        if (ary.length>=21) {
            workflowLinkDb.setExpireHour(StrUtil.toDouble(ary[20], 0)); // m_item2
        }
        if (ary.length>=22) {
            workflowLinkDb.setExpireAction(ary[21]); // m_item3;
        }
        return true;
    }

    @Override
    public String tran(String str) {
        if (str==null) {
            return "";
        }
        str = str.replaceAll("\\\\quot", "\"");
        str = str.replaceAll("\\\\colon", ":");
        str = str.replaceAll("\\\\semicolon", ";");
        str = str.replaceAll("\\\\comma", ",");
        str = str.replaceAll("\\\\newline","\r\n");

        // 从老版转换过来的时候，存在item2中有comma的情况
        str = str.replaceAll("comma", ",");
        return str;
    }
}
