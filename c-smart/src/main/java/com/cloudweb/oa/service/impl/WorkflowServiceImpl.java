package com.cloudweb.oa.service.impl;

import cn.js.fan.db.ListResult;
import cn.js.fan.db.SQLFilter;
import cn.js.fan.util.*;
import cn.js.fan.web.Global;
import cn.js.fan.web.SkinUtil;
import com.alibaba.fastjson.JSONObject;
import com.cloudweb.oa.cache.UserCache;
import com.cloudweb.oa.entity.User;
import com.cloudweb.oa.security.AuthUtil;
import com.cloudweb.oa.service.WorkflowService;
import com.cloudweb.oa.utils.I18nUtil;
import com.cloudweb.oa.utils.SysUtil;
import com.cloudweb.oa.vo.Result;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.Config;
import com.redmoon.oa.android.Constant;
import com.redmoon.oa.base.IAttachment;
import com.redmoon.oa.dept.DeptDb;
import com.redmoon.oa.dept.DeptMgr;
import com.redmoon.oa.dept.DeptUserDb;
import com.redmoon.oa.flow.*;
import com.redmoon.oa.kernel.License;
import com.redmoon.oa.message.MessageDb;
import com.redmoon.oa.oacalendar.OACalendarDb;
import com.redmoon.oa.person.UserDb;
import com.redmoon.oa.person.UserMgr;
import com.redmoon.oa.pvg.Privilege;
import com.redmoon.oa.shell.BSHShell;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.ui.LocalUtil;
import com.cloudweb.oa.utils.ThreadContext;
import com.redmoon.oa.util.RequestUtil;
import com.redmoon.oa.visual.FormUtil;
import com.redmoon.oa.visual.ModulePrivDb;
import com.redmoon.oa.visual.ModuleRelateDb;
import com.redmoon.oa.visual.ModuleSetupDb;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.*;

@Service
public class WorkflowServiceImpl implements WorkflowService {
    @Autowired
    I18nUtil i18nUtil;

    @Autowired
    HttpServletRequest request;

    @Autowired
    UserCache userCache;

    @Autowired
    SysUtil sysUtil;

    @Autowired
    AuthUtil authUtil;

    // 20230522 因为上传文件时间超长，故注释掉事务
    @Override
    // @Transactional(rollbackFor = {Exception.class, RuntimeException.class})
    public JSONObject finishAction(HttpServletRequest request, Privilege privilege) throws ErrMsgException {
        boolean re;
        WorkflowMgr wfm = new WorkflowMgr();
        JSONObject json = new JSONObject();

        try {
            wfm.doUpload(request.getServletContext(), request);
        } catch (ErrMsgException e) {
            // 可能会抛出验证非法的错误信息
            LogUtil.getLog(getClass()).error(e);
            DebugUtil.e(getClass(), "doUpload", e.getMessage());

            json.put("ret", "0");
            json.put("msg", e.getMessage());
            json.put("op", "");
            return json;
        }

        request.setAttribute("workflowParams", new WorkflowParams(request, wfm.getFileUpload()));

        String op = wfm.getFieldValue("op");
        String strFlowId = wfm.getFieldValue("flowId");
        int flowId = Integer.parseInt(strFlowId);
        String strActionId = wfm.getFieldValue("actionId");
        int actionId = Integer.parseInt(strActionId);
        String strMyActionId = wfm.getFieldValue("myActionId");
        long myActionId = Long.parseLong(strMyActionId);

        WorkflowDb wf = wfm.getWorkflowDb(flowId);

        WorkflowActionDb wa = new WorkflowActionDb();
        wa = wa.getWorkflowActionDb(actionId);
        if (!wa.isLoaded()) {
            // out.print(SkinUtil.makeErrMsg(request, "没有正在办理的节点！"));
            String str = LocalUtil.LoadString(request, "res.flow.Flow", "notBeingHandle");
            json.put("ret", "0");
            json.put("op", op);
            json.put("msg", str);
            return json;
        }

        MyActionDb myActionDb = new MyActionDb();
        myActionDb = myActionDb.getMyActionDb(myActionId);
        if (myActionDb.getCheckStatus() == MyActionDb.CHECK_STATUS_PASS) {
            json.put("ret", "0");
            json.put("op", op);
            String str = LocalUtil.LoadString(request, "res.flow.Flow", "noNeedToDealWith");
            json.put("msg", str);
            return json;
        } else if (myActionDb.getCheckStatus() == MyActionDb.CHECK_STATUS_PASS_BY_RETURN) {
            json.put("ret", "0");
            json.put("op", op);
            String str = LocalUtil.LoadString(request, "res.flow.Flow", "upcomingProcess");
            json.put("msg", str);
            return json;
        }

        String result = wfm.getFieldValue("cwsWorkflowResult");
        myActionDb.setResult(result);
        /*
        // 如果FinishAction在处理时抛出了异常，则不能置状态为checked，否则回到待办记录列表后，会找不到此记录
        if(op!= null && !op.trim().equals("saveformvalue") && !op.trim().equals("saveformvalueBeforeXorCondSelect")){
             myActionDb.setChecked(true);
        }
        */

        myActionDb.save();

        // 退回
        if ("return".equals(op)) {
            re = wfm.ReturnAction(request, wf, wa, myActionId);
            if (re) {
                json.put("ret", "1");
                json.put("op", op);
                String str = LocalUtil.LoadString(request, "res.common", "info_op_success");
                json.put("msg", str);
            } else {
                json.put("ret", "0");
                String str = LocalUtil.LoadString(request, "res.common", "info_op_fail");
                json.put("msg", str);
                json.put("op", op);
            }
            return json;
        }

        if ("finish".equals(op) || "AutoSaveArchiveNodeCommit".equals(op)) {
            try {
                wfm.checkLock(request, wf);
            } catch (ErrMsgException e1) {
                myActionDb.setChecked(false);
                myActionDb.save();
                json.put("ret", "0");
                json.put("msg", e1.getMessage());
                json.put("op", op);
                return json;
            }
            re = wfm.FinishAction(request, wf, wa, myActionId);
            if (re) {
                // 不能在此处自动存档，因此时事务尚未提交，如果在结束事件中修改了字段（如活动举办的标志位），那么读取的数据仍为未修改的状态
                // 会致台帐列表中仍显示为“未举办”，故改在WorkflowController.finishAction中自动存档
                if ("AutoSaveArchiveNodeCommit".equals(op)) {
                    json.put("operate", "AutoSaveArchiveNodeCommit");
                }

                // 如果后继节点中有一个节点是由本人继续处理，且已处于激活状态，则继续处理这个节点
                MyActionDb mad = wa.getNextActionDoingWillBeCheckedByUserSelf(privilege.getUser(request));

                op = "finish";

                if (mad != null) {
                    json.put("ret", "1");
                    json.put("op", op);

                    // 用于在WorkflowController.finishAction中自动存档
                    json.put("flowId", wf.getId());
                    json.put("actionId", wa.getId());

                    json.put("nextMyActionId", "" + mad.getId());
                    String str = LocalUtil.LoadString(request, "res.flow.Flow", "clickOk");
                    json.put("msg", str);
                    return json;
                } else {
                    json.put("ret", "1");
                    json.put("op", op);

                    // 用于在WorkflowController.finishAction中自动存档
                    json.put("flowId", wf.getId());
                    json.put("actionId", wa.getId());

                    json.put("nextMyActionId", "");
                    String str = LocalUtil.LoadString(request, "res.common", "info_op_success");
                    json.put("msg", str);
                    return json;
                }
            } else {
                json.put("ret", "0");
                String str = LocalUtil.LoadString(request, "res.common", "info_op_fail");
                json.put("msg", str);
                json.put("op", op);
                return json;
            }
        }
        if ("read".equals(op)) {
            re = wfm.read(request, actionId, myActionId);
            if (re) {
                json.put("ret", "1");
                json.put("op", op);
                String str = LocalUtil.LoadString(request, "res.common", "info_op_success");
                json.put("msg", str);
            } else {
                json.put("ret", "0");
                String str = LocalUtil.LoadString(request, "res.common", "info_op_fail");
                json.put("msg", str);
                json.put("op", op);
            }
            return json;
        }
        if ("manualFinish".equals(op) || "AutoSaveArchiveNodeManualFinish".equals(op) || "manualFinishAgree".equals(op)) {
            re = wfm.saveFormValue(request, wf, wa);
            if (re) {
                re = wfm.ManualFinish(request, flowId, myActionId);
            }
            if (re) {
                json.put("ret", "1");
                json.put("op", op);
                String str = LocalUtil.LoadString(request, "res.common", "info_op_success");
                json.put("msg", str);
            } else {
                // out.print(StrUtil.Alert_Back("操作失败！"));
                json.put("ret", "0");
                String str = LocalUtil.LoadString(request, "res.common", "info_op_fail");
                json.put("msg", str);
                json.put("op", op);
            }
            return json;
        }

        // 自动存档前先保存数据，然后获取flow_displose.jsp中iframe中的report表单数据在 办理完毕 时存档
        if ("editFormValue".equals(op) || "saveformvalue".equals(op) || "saveformvalueBeforeXorCondSelect".equals(op)) {
            // 2013-06-29 fgf 注意保存草稿已经不再进行有效性验证
            re = wfm.saveFormValue(request, wf, wa);
            if (re) {
                json.put("ret", "1");
                json.put("op", op);
                String str = i18nUtil.get("info_op_success");
                json.put("msg", str);
            } else {
                json.put("ret", "0");
                String str = i18nUtil.get("info_op_fail");
                json.put("msg", str);
                json.put("op", op);
            }
        }

        return json;
    }

    @Override
    // @Transactional(rollbackFor = {Exception.class, RuntimeException.class})
    public JSONObject finishActionByMobile(HttpServletRequest request, Privilege privilege) throws ErrMsgException {
        boolean re = false;
        WorkflowMgr wfm = new WorkflowMgr();
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        try {
            wfm.doUpload(request.getServletContext(), request);
        } catch (ErrMsgException e) {
            // 可能会抛出验证非法的错误信息
            LogUtil.getLog(getClass()).error(e);
            DebugUtil.e(getClass(), "doUpload", e.getMessage());

            json.put("ret", "-1");
            json.put("msg", e.getMessage());
            json.put("op", "");
            return json;
        }

        request.setAttribute("workflowParams", new WorkflowParams(request, wfm.getFileUpload()));

        String skey = wfm.getFieldValue("skey");
        com.redmoon.oa.android.Privilege prl = new com.redmoon.oa.android.Privilege();
        prl.doLogin(request, skey);

        String op = wfm.getFieldValue("op");
        String strFlowId = wfm.getFieldValue("flowId");
        int flowId = Integer.parseInt(strFlowId);
        String strActionId = wfm.getFieldValue("actionId");
        int actionId = Integer.parseInt(strActionId);
        String strMyActionId = wfm.getFieldValue("myActionId");
        long myActionId = Long.parseLong(strMyActionId);

        WorkflowDb wf = wfm.getWorkflowDb(flowId);

        String myname = privilege.getUser(request);

        WorkflowActionDb wa = new WorkflowActionDb();
        wa = wa.getWorkflowActionDb(actionId);
        if (!wa.isLoaded()) {
            json.put("res", "-1");
            json.put("msg", "没有正在办理的节点！");
            return json;
        }

        MyActionDb myActionDb = new MyActionDb();
        myActionDb = myActionDb.getMyActionDb(myActionId);

        if (myActionDb.getCheckStatus() == MyActionDb.CHECK_STATUS_PASS) {
            json.put("ret", "-1");
            json.put("op", op);
            String str = LocalUtil.LoadString(request, "res.flow.Flow", "noNeedToDealWith");
            json.put("msg", str);
            return json;
        } else if (myActionDb.getCheckStatus() == MyActionDb.CHECK_STATUS_PASS_BY_RETURN) {
            json.put("ret", "-1");
            json.put("op", op);
            String str = LocalUtil.LoadString(request, "res.flow.Flow", "upcomingProcess");
            json.put("msg", str);
            return json;
        }

        String result = wfm.getFieldValue("cwsWorkflowResult");
        myActionDb.setResult(result);
        /*
        // 如果FinishAction在处理时抛出了异常，则不能置状态为checked，否则回到待办记录列表后，会找不到此记录
        if(op!= null && !op.trim().equals("saveformvalue") && !op.trim().equals("saveformvalueBeforeXorCondSelect")){
             myActionDb.setChecked(true);
        }
        */

        myActionDb.save();
        // lzm添加  审阅 判断
        if ("finish".equals(op)) {
            int kind = wa.getKind();
            if (kind == WorkflowActionDb.KIND_READ) {
                op = "read";
            }
        }
        // 退回
        if ("return".equals(op)) {
            re = wfm.ReturnAction(request, wf, wa, myActionId);
            if (re) {
                json.put("res", "0");
                json.put("op", op);
                json.put("msg", "操作成功！");
            } else {
                json.put("res", "-1");
                json.put("msg", "操作失败！");
                json.put("op", op);
            }
            return json;
        } else if ("del".equals(op)) {
            re = wfm.del(request, flowId);
            if (re) {
                json.put("res", "0");
                json.put("op", "del");
                json.put("msg", "操作成功!");
            } else {
                json.put("res", "-1");
                json.put("msg", "操作失败!");
                json.put("op", "del");
            }
            return json;
        } else if ("finish".equals(op)) {
            boolean flagXorRadiate = wa.isXorRadiate();
            Vector<WorkflowLinkDb> vMatched = null;
            StringBuffer condBuf = new StringBuffer();
            if (flagXorRadiate) {
                wfm.saveFormValue(request, wf, wa);
                request.setAttribute("myActionId", myActionId);
                vMatched = WorkflowRouter.matchNextBranch(wa, myname, condBuf, myActionId);
            }
            boolean isCondSatisfied = vMatched != null && vMatched.size() > 0;
            String conds = condBuf.toString();
            WorkflowRouter workflowRouter = new WorkflowRouter();
            boolean hasCond = !"".equals(conds); // 是否含有条件
            boolean isAfterSaveformvalueBeforeXorCondSelect = wfm.getFieldValue("isAfterSaveformvalueBeforeXorCondSelect").equals("true");
            if (hasCond && !isAfterSaveformvalueBeforeXorCondSelect) {
                com.alibaba.fastjson.JSONArray users = new com.alibaba.fastjson.JSONArray();
                com.redmoon.oa.android.Privilege pri = new com.redmoon.oa.android.Privilege();
                Vector<WorkflowActionDb> vto = wa.getLinkToActions();
                Iterator<WorkflowActionDb> toir = vto.iterator();
                WorkflowLinkDb wld = new WorkflowLinkDb();
                Iterator<UserDb> userir = null;
                // 如果条件不满足，则让用户选择默认条件（默认条件可能有多个）
                if (!isCondSatisfied) {
                    while (toir.hasNext()) {
                        WorkflowActionDb towa = (WorkflowActionDb) toir.next();
                        wld = wld.getWorkflowLinkDbForward(wa, towa);
                        // @task:是否该改为condType为-1（不需要，因为title中存储的是条件，cond_desc中才是描述
                        // 过滤掉非默认分支，不能单纯以title是否为空判断，因为还有组合条件，存于link_prop字段中
                        if (!"".equals(wld.getTitle().trim())) {
                            continue;
                        }
                        if (!WorkflowLinkDb.COND_TYPE_NONE.equals(wld.getCondType())) {
                            continue;
                        }

                        boolean isSelectable = towa.isStrategySelectable();
                        Vector<UserDb> vuser = null;
                        try {
                            String deptOfUserWithMultiDept = wfm.getFieldValue("deptOfUserWithMultiDept");
                            vuser = workflowRouter.matchActionUser(request, towa, wa, false, deptOfUserWithMultiDept);
                        } catch (MatchUserException e) {
                            json.put("res", "-1");
                            json.put("msg", e.getMessage());
                            json.put("op", "finish");
                            return json;
                        }

                        if (vuser != null && vuser.size() > 0) {
                            userir = vuser.iterator();
                            while (userir.hasNext()) {
                                UserDb ud = userir.next();
                                com.alibaba.fastjson.JSONObject user = new com.alibaba.fastjson.JSONObject();
                                user.put("actionTitle", towa.getTitle());
                                user.put("internalname", towa.getInternalName());
                                user.put("name", "WorkflowAction_" + towa.getId());
                                user.put("value", ud.getName());
                                user.put("realName", ud.getRealName());
                                user.put("isSelectable", isSelectable);
                                users.add(user);
                            }
                        } else {
                            com.alibaba.fastjson.JSONObject user = new com.alibaba.fastjson.JSONObject();
                            user.put("actionTitle", towa.getTitle());
                            user.put("internalname", towa.getInternalName());
                            user.put("name", "WorkflowAction_" + towa.getId());
                            user.put("value", "");
                            user.put("realName", "");
                            users.add(user);
                        }
                    }
                    json.put("res", "3");
                    json.put("users", users);
                    return json;
                } else {
                    while (toir.hasNext()) {
                        WorkflowActionDb towa = toir.next();
                        boolean isTowaMatched = false;
                        for (WorkflowLinkDb linkMatched : vMatched) {
                            if (towa.getInternalName().equals(linkMatched.getTo())) {
                                isTowaMatched = true;
                                break;
                            }
                        }
                        if (isTowaMatched) {
                            boolean isSelectable = towa.isStrategySelectable();
                            Vector<UserDb> vuser = null;
                            try {
                                String deptOfUserWithMultiDept = wfm.getFieldValue("deptOfUserWithMultiDept");
                                vuser = workflowRouter.matchActionUser(request, towa, wa, false, deptOfUserWithMultiDept);
                            } catch (MatchUserException e) {
                                // LogUtil.getLog(getClass()).error(e);
                                json.put("res", "-2");
                                json.put("msg", e.getMessage());
                                json.put("op", "finish");
                                return json;
                            }
                            if (vuser != null && vuser.size() > 0) {
                                userir = vuser.iterator();
                                while (userir.hasNext()) {
                                    UserDb ud = userir.next();
                                    com.alibaba.fastjson.JSONObject user = new com.alibaba.fastjson.JSONObject();
                                    user.put("actionTitle", towa.getTitle());
                                    user.put("internalname", towa.getInternalName());
                                    user.put("name", "WorkflowAction_" + towa.getId());
                                    user.put("value", ud.getName());
                                    user.put("realName", ud.getRealName());
                                    user.put("isSelectable", isSelectable);
                                    users.add(user);
                                }
                            } else {
                                com.alibaba.fastjson.JSONObject user = new com.alibaba.fastjson.JSONObject();
                                user.put("actionTitle", towa.getTitle());
                                user.put("internalname", towa.getInternalName());
                                user.put("name", "WorkflowAction_" + towa.getId());
                                user.put("value", "");
                                user.put("realName", "");
                                users.add(user);
                            }
                        }
                    }
                    if (users.size() > 0) {
                        json.put("res", "3");
                        json.put("users", users);
                        return json;
                    }
                }
            }

            // 检查是否有表单中指定的用户，如果有，则返回匹配到的人员，还是利用流程中匹配条件分支的方式，手机端无需改动
            if (!isAfterSaveformvalueBeforeXorCondSelect) {
                boolean isFieldUser = false;
                Vector<WorkflowActionDb> vto = wa.getLinkToActions();
                for (WorkflowActionDb towa : vto) {
                    if (towa.getJobCode().startsWith(WorkflowActionDb.PRE_TYPE_FIELD_USER)) {
                        isFieldUser = true;
                        break;
                    }
                }
                if (isFieldUser) {
                    JSONArray users = new JSONArray();
                    // 保存表单
                    wfm.saveFormValue(request, wf, wa);
                    for (WorkflowActionDb towa : vto) {
                        boolean isSelectable = towa.isStrategySelectable();
                        Vector vuser = null;
                        try {
                            vuser = workflowRouter.matchActionUser(request, towa, wa, false, null);
                        } catch (MatchUserException e) {
                            LogUtil.getLog(getClass()).error(e);
                        }
                        if (vuser != null && vuser.size() > 0) {
                            Iterator userir = vuser.iterator();
                            while (userir != null && userir.hasNext()) {
                                UserDb ud = (UserDb) userir.next();
                                com.alibaba.fastjson.JSONObject user = new com.alibaba.fastjson.JSONObject();
                                user.put("actionTitle", towa.getTitle());
                                user.put("internalname", towa.getInternalName());
                                user.put("name", "WorkflowAction_" + towa.getId());
                                user.put("value", ud.getName());
                                user.put("realName", ud.getRealName());
                                user.put("isSelectable", isSelectable);
                                users.put(user);
                            }
                        } else {
                            com.alibaba.fastjson.JSONObject user = new com.alibaba.fastjson.JSONObject();
                            user.put("actionTitle", towa.getTitle());
                            user.put("internalname", towa.getInternalName());
                            user.put("name", "WorkflowAction_" + towa.getId());
                            user.put("value", "");
                            user.put("realName", "");
                            users.put(user);
                        }
                    }
                    // 如果匹配到用户，则返回
                    if (users.length() > 0) {
                        json.put("res", "3");
                        json.put("users", users);
                        return json;
                    }
                }
            }
        }
        if ("finish".equals(op) || "AutoSaveArchiveNodeCommit".equals(op)) {
            re = wfm.FinishAction(request, wf, wa, myActionId);
            if (re) {
                // 如果后继节点中有一个节点是由本人继续处理，且已处于激活状态，则继续处理这个节点
                MyActionDb mad = wa.getNextActionDoingWillBeCheckedByUserSelf(privilege.getUser(request));
                if (mad != null) {
                    json.put("res", "0");
                    json.put("op", op);
                    json.put("nextMyActionId", "" + mad.getId());
                    json.put("msg", "操作成功！请点击确定，继续处理下一节点！");
                } else {
                    json.put("res", "0");
                    json.put("op", op);
                    json.put("nextMyActionId", "");
                    json.put("msg", "操作成功！");
                }
            } else {
                json.put("res", "-1");
                json.put("msg", "操作失败！");
                json.put("op", op);
            }
            return json;
        }
        if ("read".equals(op)) {
            re = wfm.read(request, actionId, myActionId);
            if (re) {
                json.put("res", "0");
                json.put("op", "finish");
                json.put("msg", "操作成功!");
            } else {
                json.put("res", "-1");
                json.put("msg", "操作失败!");
                json.put("op", "finish");
            }
            return json;
        }

        if ("manualFinish".equals(op) || "AutoSaveArchiveNodeManualFinish".equals(op) || "manualFinishAgree".equals(op)) {
            re = wfm.saveFormValue(request, wf, wa);
            if (re) {
                re = wfm.ManualFinish(request, flowId, myActionId);
            }
            if (re) {
                json.put("res", "0");
                json.put("op", op);
                json.put("msg", "操作成功！");
            } else {
                json.put("res", "-1");
                json.put("msg", "操作失败！");
                json.put("op", op);
            }
            return json;
        }

        if ("saveformvalue".equals(op)) {
            // 2013-06-29 fgf 注意保存草稿已经不再进行有效性验证
            re = wfm.saveFormValue(request, wf, wa);
            if (re) {
                json.put("res", "5");
                json.put("op", op);
                json.put("msg", "操作成功！");
            } else {
                json.put("res", "-1");
                json.put("msg", "操作失败！");
                json.put("op", op);
            }
        } else if ("editFormValue".equals(op) || "saveformvalueBeforeXorCondSelect".equals(op)) {
            re = wfm.saveFormValue(request, wf, wa);
            if (re) {
                json.put("res", "0");
                json.put("op", op);
                json.put("msg", "操作成功！");
            } else {
                json.put("res", "-1");
                json.put("msg", "操作失败！");
                json.put("op", op);
            }
        }
        return json;
    }

    @Override
    // @Transactional(rollbackFor = {Exception.class, RuntimeException.class})
    public JSONObject finishActionFree(HttpServletRequest request, Privilege privilege) throws ErrMsgException {
        WorkflowMgr wfm = new WorkflowMgr();
        JSONObject json = new JSONObject();
        try {
            wfm.doUpload(request.getServletContext(), request);
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
            DebugUtil.e(getClass(), "doUpload", e.getMessage());

            json.put("ret", "0");
            json.put("msg", e.getMessage());
            json.put("op", "");
            return json;
        }

        String op = wfm.getFieldValue("op");
        String strFlowId = wfm.getFieldValue("flowId");
        int flowId = Integer.parseInt(strFlowId);
        String strActionId = wfm.getFieldValue("actionId");
        int actionId = Integer.parseInt(strActionId);
        String strMyActionId = wfm.getFieldValue("myActionId");
        long myActionId = Long.parseLong(strMyActionId);

        WorkflowDb wf = wfm.getWorkflowDb(flowId);

        WorkflowActionDb wa = new WorkflowActionDb();
        wa = wa.getWorkflowActionDb(actionId);
        if (!wa.isLoaded()) {
            String str = LocalUtil.LoadString(request, "res.flow.Flow", "notBeingHandle");
            json.put("ret", "0");
            json.put("msg", str);
            json.put("op", op);
            return json;
        }

        MyActionDb myActionDb = new MyActionDb();
        myActionDb = myActionDb.getMyActionDb(myActionId);
        String result = wfm.getFieldValue("cwsWorkflowResult");
        myActionDb.setResult(result);
        if (op != null && !"saveformvalue".equals(op.trim())) {
            myActionDb.setChecked(true);
        }
        myActionDb.save();

        if ("return".equals(op)) {
            boolean re = wfm.ReturnAction(request, wf, wa, myActionId);
            if (re) {
                json.put("ret", "1");
                json.put("op", op);
                String str = LocalUtil.LoadString(request, "res.common", "info_op_success");
                json.put("msg", str);
                return json;
            } else {
                json.put("ret", "0");
                String str = LocalUtil.LoadString(request, "res.common", "info_op_fail");
                json.put("msg", str);
                json.put("op", op);
                return json;
            }
        } else if ("finish".equals(op)) {
            try {
                wfm.checkLock(request, wf);
            } catch (ErrMsgException e1) {
                myActionDb.setChecked(false);
                myActionDb.save();
                json.put("ret", "0");
                json.put("msg", e1.getMessage());
                json.put("op", op);
                return json;
            }
            boolean re = wfm.FinishActionFree(request, wf, wa, myActionId);
            if (re) {
                // 如果后继节点中有一个节点是由本人继续处理，且已处于激活状态，则继续处理这个节点
                MyActionDb mad = wa.getNextActionDoingWillBeCheckedByUserSelf(privilege.getUser(request));
                if (mad != null) {
                    // out.print(StrUtil.Alert_Redirect("操作成功！请点击确定，继续处理下一节点！", "flow_dispose_free.jsp?myActionId=" + mad.getId()));
                    json.put("ret", "1");
                    json.put("op", op);
                    json.put("nextMyActionId", "" + mad.getId());
                    String str = LocalUtil.LoadString(request, "res.flow.Flow", "clickOk");
                    json.put("msg", str);
                    return json;
                } else {
                    json.put("ret", "1");
                    json.put("op", op);
                    json.put("nextMyActionId", "");
                    String str = LocalUtil.LoadString(request, "res.common", "info_op_success");
                    json.put("msg", str);
                    return json;
                }
            } else {
                // out.print(StrUtil.Alert_Redirect("操作失败！", "flow_dispose_free.jsp?myActionId=" + myActionId));
                json.put("ret", "0");
                String str = LocalUtil.LoadString(request, "res.common", "info_op_fail");
                json.put("msg", str);
                json.put("op", op);
                return json;
            }
        } else if ("manualFinish".equals(op)) {
            boolean re = wfm.saveFormValue(request, wf, wa);
            if (re) {
                re = wfm.ManualFinish(request, flowId, myActionId);
                if (re) {
                    myActionDb.setResultValue(WorkflowActionDb.RESULT_VALUE_DISAGGREE);
                    myActionDb.save();
                }
            }

            if (re) {
                json.put("ret", "1");
                json.put("op", op);
                String str = LocalUtil.LoadString(request, "res.common", "info_op_success");
                json.put("msg", str);
                return json;
            } else {
                json.put("ret", "0");
                String str = LocalUtil.LoadString(request, "res.common", "info_op_fail");
                json.put("msg", str);
                json.put("op", op);
                return json;
            }
        }
        // 保存草稿
        else if ("saveformvalue".equals(op)) {
            boolean re = wfm.saveFormValue(request, wf, wa);
            // afterXorCondNodeCommit通知flow_dispose.jsp页面，已保存完毕，匹配条件后，自动重定向
            if (re) {
                json.put("ret", "1");
                json.put("op", op);
                String str = LocalUtil.LoadString(request, "res.common", "info_op_success");
                json.put("msg", str);
                return json;
            }
        }
        return json;
    }

    @Override
    // @Transactional(rollbackFor = {Exception.class, RuntimeException.class})
    public JSONObject createNestSheetRelated(HttpServletRequest request) throws ErrMsgException {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        String moduleCodeRelated = ParamUtil.get(request, "moduleCodeRelated");

        String moduleCode = ParamUtil.get(request, "moduleCode");
        ModuleSetupDb msd = new ModuleSetupDb();
        msd = msd.getModuleSetupDbOrInit(moduleCode);
        if (msd == null) {
            json.put("res", 1);
            json.put("msg", "模块不存在");
            return json;
        }
        String formCode = msd.getString("form_code");

        String relateFieldValue = String.valueOf(com.redmoon.oa.visual.FormDAO.TEMP_CWS_ID);
        int parentId = ParamUtil.getInt(request, "parentId", -1); // 父模块的ID
        if (parentId == -1) {
            ModuleRelateDb mrd = new ModuleRelateDb();
            mrd = mrd.getModuleRelateDb(formCode, moduleCodeRelated);
            if (mrd == null) {
                json.put("res", 1);
                json.put("msg", "请检查模块是否相关联");
                return json;
            }
        } else {
            com.redmoon.oa.visual.FormDAOMgr fdm = new com.redmoon.oa.visual.FormDAOMgr(formCode);
            relateFieldValue = fdm.getRelateFieldValue(parentId, moduleCodeRelated);
            if (relateFieldValue == null) {
                json.put("res", 1);
                json.put("msg", "请检查模块是否相关联");
                return json;
            }
        }

        ModuleSetupDb msdRelated = new ModuleSetupDb();
        msdRelated = msdRelated.getModuleSetupDbOrInit(moduleCodeRelated);
        String formCodeRelated = msdRelated.getString("form_code");
        FormMgr fm = new FormMgr();
        FormDb fdRelated = fm.getFormDb(formCodeRelated);

        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        com.redmoon.oa.Config cfg = new com.redmoon.oa.Config();
        boolean isNestSheetCheckPrivilege = cfg.getBooleanProperty("isNestSheetCheckPrivilege");
        ModulePrivDb mpd = new ModulePrivDb(moduleCodeRelated);
        if (isNestSheetCheckPrivilege && !mpd.canUserAppend(privilege.getUser(request))) {
            json.put("res", 1);
            json.put("msg", cn.js.fan.web.SkinUtil.LoadString(request, "pvg_invalid"));
            return json;
        }

        long actionId = ParamUtil.getLong(request, "actionId", -1);
        request.setAttribute("actionId", String.valueOf(actionId));

        // 用于区分嵌套表是在流程还是智能模块
        boolean isVisual = false;
        boolean re;
        com.redmoon.oa.visual.FormDAOMgr fdm = new com.redmoon.oa.visual.FormDAOMgr(fdRelated);
        try {
            re = fdm.create(request.getServletContext(), request, msdRelated);
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
            json.put("res", 1);
            json.put("msg", e.getMessage());
            return json;
        }
        if (re) {
            String[] fields = msdRelated.getColAry(false, "list_field");

            int len = 0;
            if (fields != null) {
                len = fields.length;
            }
            StringBuilder tds = new StringBuilder();
            String token = "#@#";
            // int cwsId = ParamUtil.getInt(request, "cws_id", com.redmoon.oa.visual.FormDAO.TEMP_CWS_ID);
            String cwsId = fdm.getFieldValue("cws_id");
            // CWS_ID_TO_ASSIGN表示在智能模块中添加操作时，添加嵌套表格2中的记录
            if (com.redmoon.oa.visual.FormDAO.CWS_ID_TO_ASSIGN.equals(cwsId)) {
                isVisual = true;
            }

            com.redmoon.oa.visual.FormDAO fdao = fdm.getFormDAO();
            RequestUtil.setFormDAO(request, fdao);
            for (int i = 0; i < len; i++) {
                String fieldName = fields[i];
                String v = StrUtil.getNullStr(fdao.getFieldHtml(request, fieldName));
                if (i == 0) {
                    tds = new StringBuilder(v);
                } else {
                    tds.append(token).append(v);
                }
            }

            json.put("res", 0);
            json.put("msg", "操作成功！");
            json.put("isVisual", isVisual);
            json.put("token", token);
            json.put("tds", tds.toString());
            json.put("fdaoId", fdm.getVisualObjId());
            json.put("formCodeRelated", formCodeRelated);

            FormDb pForm = new FormDb();
            pForm = pForm.getFormDb(formCode);
            json.put("sums", JSONObject.parse(FormUtil.getSums(fdRelated, pForm, String.valueOf(parentId)).toString()));
        } else {
            json.put("res", 1);
            json.put("msg", "操作失败！");
        }
        return json;
    }

    @Override
    // @Transactional(rollbackFor = {Exception.class, RuntimeException.class})
    public JSONObject updateNestSheetRelated(HttpServletRequest request) throws ErrMsgException {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();

        String moduleCode = ParamUtil.get(request, "moduleCode"); // 主模块编码
        if ("".equals(moduleCode)) {
            json.put("res", "1");
            json.put("msg", "编码不能为空！");
            return json;
        }

        String moduleCodeRelated = ParamUtil.get(request, "moduleCodeRelated"); // 从模块编码

        com.redmoon.oa.Config cfg = new com.redmoon.oa.Config();
        boolean isNestSheetCheckPrivilege = cfg.getBooleanProperty("isNestSheetCheckPrivilege");

        Privilege privilege = new Privilege();
        ModulePrivDb mpd = new ModulePrivDb(moduleCodeRelated);
        if (isNestSheetCheckPrivilege && !mpd.canUserManage(privilege.getUser(request))) {
            json.put("res", "1");
            json.put("msg", cn.js.fan.web.SkinUtil.LoadString(request, "pvg_invalid"));
            return json;
        }

        long actionId = ParamUtil.getLong(request, "actionId", -1);
        request.setAttribute("actionId", String.valueOf(actionId));

        ModuleSetupDb msdRelated = new ModuleSetupDb();
        msdRelated = msdRelated.getModuleSetupDb(moduleCodeRelated);
        String formCodeRelated = msdRelated.getString("form_code");
        FormMgr fm = new FormMgr();
        FormDb fd = fm.getFormDb(formCodeRelated);

        long id = ParamUtil.getLong(request, "id", -1);
        if (id == -1) {
            json.put("res", "1");
            json.put("msg", SkinUtil.LoadString(request, "err_id"));
            return json;
        }

        com.redmoon.oa.visual.FormDAOMgr fdm = new com.redmoon.oa.visual.FormDAOMgr(fd);
        com.redmoon.oa.visual.FormDAO fdao = fdm.getFormDAO(id);

        // 用于区分嵌套表是在流程还是智能模块
        boolean isVisual;

        boolean re;
        try {
            re = fdm.update(request.getServletContext(), request, msdRelated);
        } catch (ErrMsgException e) {
            json.put("res", "1");
            json.put("msg", e.getMessage());
            return json;
        }
        if (re) {
            StringBuilder tds = new StringBuilder();
            String token = "#@#";
            // if (fdao.getCwsId() == com.redmoon.oa.visual.FormDAO.TEMP_CWS_ID || fdao.getCwsId().equals("" + com.redmoon.oa.visual.FormDAO.NAME_TEMP_CWS_IDS)) {
            // 智能模块中
            if (fdao.getCwsId().equals(com.redmoon.oa.visual.FormDAO.TEMP_CWS_ID) || fdao.getCwsId().equals(com.redmoon.oa.visual.FormDAO.CWS_ID_TO_ASSIGN)) {
                String[] fields = msdRelated.getColAry(false, "list_field");

                int len = 0;
                if (fields != null) {
                    len = fields.length;
                }
                fdao = fdm.getFormDAO(id);

                for (int i = 0; i < len; i++) {
                    String fieldName = fields[i];
                    String v = fdao.getFieldHtml(request, fieldName); // fdao.getFieldValue(fieldName);
                    if (i == 0) {
                        tds = new StringBuilder(v);
                    } else {
                        tds.append(token).append(v);
                    }
                }
                isVisual = true;
            } else {
                isVisual = false;
            }
            json.put("res", 0);
            json.put("msg", "操作成功！");
            json.put("isVisual", isVisual);
            json.put("formCodeRelated", formCodeRelated);
            json.put("moduleCodeRelated", moduleCodeRelated);
            json.put("token", token);
            json.put("tds", tds.toString());
        } else {
            json.put("res", 1);
            json.put("msg", "操作失败！");
        }
        return json;
    }

    @Override
    @Transactional(rollbackFor = {Exception.class, RuntimeException.class})
    public String runFinishScript(HttpServletRequest request, int flowId, int actionId) throws ErrMsgException {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        BSHShell shell = null;
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);

        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());
        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());

        FormDAO fdao = new FormDAO();
        fdao = fdao.getFormDAO(flowId, fd);

        WorkflowActionDb wa = new WorkflowActionDb();
        wa = wa.getWorkflowActionDb(actionId);

        WorkflowPredefineDb wpd = new WorkflowPredefineDb();
        wpd = wpd.getDefaultPredefineFlow(wf.getTypeCode());
        WorkflowPredefineMgr wpm = new WorkflowPredefineMgr();
        String script = wpm.getOnFinishScript(wpd.getScripts());

        if (script != null && !"".equals(script.trim())) {
            shell = wf.runFinishScript(request, wf, fdao, wa, script, true);
        }

        if (shell == null) {
            return "请检查脚本是否存在";
        } else {
            String errDesc = shell.getConsole().getLogDesc();
            // json.put("msg", StrUtil.toHtml(errDesc));
            return errDesc;
        }
    }

    @Override
    // @Transactional(rollbackFor = {Exception.class, RuntimeException.class})
    public JSONObject finishActionFreeByMobile(HttpServletRequest request, Privilege privilege) throws ErrMsgException {
        WorkflowMgr wfm = new WorkflowMgr();
        JSONObject json = new JSONObject();

        wfm.doUpload(request.getServletContext(), request);

        String skey = wfm.getFieldValue("skey");
        com.redmoon.oa.android.Privilege prl = new com.redmoon.oa.android.Privilege();
        prl.doLogin(request, skey);

        String op = wfm.getFieldValue("op");
        String strFlowId = wfm.getFieldValue("flowId");
        int flowId = Integer.parseInt(strFlowId);
        String strActionId = wfm.getFieldValue("actionId");
        int actionId = Integer.parseInt(strActionId);
        String strMyActionId = wfm.getFieldValue("myActionId");
        long myActionId = Long.parseLong(strMyActionId);

        WorkflowDb wf = wfm.getWorkflowDb(flowId);
        WorkflowActionDb wa = new WorkflowActionDb();
        wa = wa.getWorkflowActionDb(actionId);
        if (!wa.isLoaded()) {
            json.put("res", "-1");
            json.put("msg", "没有正在办理的节点！");
            json.put("op", op);
            return json;
        }

        MyActionDb myActionDb = new MyActionDb();
        myActionDb = myActionDb.getMyActionDb(myActionId);

        String result = wfm.getFieldValue("cwsWorkflowResult");
        myActionDb.setResult(result);
        myActionDb.save();

        if ("return".equals(op)) {
            boolean re = wfm.ReturnAction(request, wf, wa, myActionId);
            if (re) {
                json.put("res", "0");
                json.put("op", op);
                json.put("msg", "操作成功！");
            } else {
                json.put("res", "-1");
                json.put("msg", "操作失败！");
                json.put("op", op);
            }
        } else if ("finish".equals(op)) {
            boolean re = wfm.FinishActionFree(request, wf, wa, myActionId);
            if (re) {
                // 如果后继节点中有一个节点是由本人继续处理，且已处于激活状态，则继续处理这个节点
                MyActionDb mad = wa.getNextActionDoingWillBeCheckedByUserSelf(privilege.getUser(request));
                if (mad != null) {
                    json.put("res", "0");
                    json.put("op", op);
                    json.put("nextMyActionId", "" + mad.getId());
                    json.put("msg", "操作成功！请点击确定，继续处理下一节点！");
                } else {
                    json.put("res", "0");
                    json.put("op", op);
                    json.put("nextMyActionId", "");
                    json.put("msg", "操作成功！");
                }
                return json;
            } else {
                json.put("res", "-1");
                json.put("msg", "操作失败！");
                json.put("op", op);
            }
        } else if ("manualFinish".equals(op)) {
            boolean re = wfm.saveFormValue(request, wf, wa);
            re = wfm.ManualFinish(request, flowId, myActionId);
            if (re) {
                myActionDb.setResultValue(WorkflowActionDb.RESULT_VALUE_DISAGGREE);
                myActionDb.save();
            }
            if (re) {
                json.put("res", "0");
                json.put("op", op);
                json.put("msg", "操作成功！");
            } else {
                json.put("res", "-1");
                json.put("msg", "操作失败！");
                json.put("op", op);
            }
            return json;
        }

        if ("saveformvalue".equals(op) || "AutoSaveArchiveNodeCommit".equals(op)) {
            boolean re = wfm.saveFormValue(request, wf, wa);
            if (re) {
                if ("saveformvalue".equals(op)) {
                    json.put("res", "5");
                    json.put("op", op);
                    json.put("msg", "保存草稿成功！");
                    return json;
                } else if ("AutoSaveArchiveNodeCommit".equals(op)) {
                    re = wfm.autoSaveArchive(request, wf, wa);
                    if (re) {
                        json.put("res", "0");
                        json.put("op", op);
                        json.put("msg", "保存存档成功！");
                    } else {
                        json.put("res", "-1");
                        json.put("op", op);
                        json.put("msg", "保存存档失败！");
                    }
                }
            }
        }
        return json;
    }

    /**
     * 匹配用户
     *
     * @param request
     * @param myActionId
     * @param actionId
     * @return
     */
    @Override
    public com.alibaba.fastjson.JSONObject matchBranchAndUser(HttpServletRequest request, long myActionId, int actionId) {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        StringBuilder info = new StringBuilder();
        String op = ParamUtil.get(request, "op");
        json.put("op", op);

        // 列出符合条件的用户
        Privilege privilege = new Privilege();
        UserMgr um = new UserMgr();
        com.redmoon.oa.Config cfg = new com.redmoon.oa.Config();
        WorkflowDb wf = new WorkflowDb();
        WorkflowActionDb wa = new WorkflowActionDb();
        wa = wa.getWorkflowActionDb(actionId);

        WorkflowRuler wr = new WorkflowRuler();
        MyActionDb mad = new MyActionDb();
        mad = mad.getMyActionDb(myActionId);

        if (mad.getActionStatus() == WorkflowActionDb.STATE_PLUS) {
            json.put("errCode", -2);
            json.put("actionStatus", WorkflowActionDb.STATE_PLUS);
            return json;
        }

        wf = wf.getWorkflowDb(wa.getFlowId());
        WorkflowPredefineDb wfp = new WorkflowPredefineDb();
        wfp = wfp.getPredefineFlowOfFree(wf.getTypeCode());

        Vector<WorkflowActionDb> vto = wa.getLinkToActions();
        // 条件分支
        boolean flagXorRadiate = wa.isXorRadiate();
        json.put("flagXorRadiate", flagXorRadiate);

        Iterator<WorkflowActionDb> toir = vto.iterator();

        // 如果在后继节点的连接线上存在条件，则判别是否有符合条件的分支，如果有满足条件的，则自动运行，注意条件分支中应只有一个分支满足条件
        // 鉴别模型 Discriminator Choice
        Vector<WorkflowLinkDb> vMatched = null;
        StringBuffer condBuf = new StringBuffer();
        if (flagXorRadiate) {
            try {
                request.setAttribute("myActionId", myActionId);
                vMatched = WorkflowRouter.matchNextBranch(wa, privilege.getUser(request), condBuf, myActionId);
            } catch (ErrMsgException e) {
                info.append(e.getMessage());
            }
        }

        boolean isCondSatisfied = vMatched != null && vMatched.size() > 0;
        String conds = condBuf.toString();
        boolean hasCond = !"".equals(conds); // 是否含有条件
        // 当op为 saveformvalueBeforeXorCondSelect 或 matchNextBranch 时，askType=1
        int askType = ParamUtil.getInt(request, "askType", 0);

        if (hasCond && !"matchNextBranch".equals(op)) {
            // 选择兼职部门
            if (!"matchAfterSelDept".equals(op) && !"reMatchUser".equals(op)) {
                // fgf 20161009使当有条件时，不显示条件的相关信息，因为在提交后还会再次提示选择用户，以免引起混淆
                json.put("errCode", -2);
                json.put("info", "使当有条件时，不显示条件的相关信息");
                json.put("hasCond", hasCond);
                return json;
            }
        }

        json.put("errCode", 0);
        json.put("hasCond", hasCond);

        String flowExpireUnit = cfg.get("flowExpireUnit");

        // 找出条件为空的分支，以选择用户
        com.alibaba.fastjson.JSONArray toActions = new com.alibaba.fastjson.JSONArray();
        json.put("toActions", toActions);

        boolean isBtnSelUserShow = false;
        // 如果没有满足条件的，则显示默认分支
        if (hasCond && !isCondSatisfied) {
            int q = 0;
            WorkflowLinkDb wld = new WorkflowLinkDb();
            int isMultiDeptCount = 0;
            while (askType == 1 && toir.hasNext()) {
                WorkflowActionDb towa = toir.next();

                boolean canSelUser = wr.canUserSelUser(request, towa);
                wld = wld.getWorkflowLinkDbForward(wa, towa);
                // 过滤掉非默认分支，不能单纯以title是否为空判断，因为还有组合条件，存于link_prop字段中
                /*if (!"".equals(wld.getTitle().trim())) {
                    continue;
                }*/
                if (!WorkflowLinkDb.COND_TYPE_NONE.equals(wld.getCondType())) {
                    continue;
                }
                q++;
                JSONObject jsonWa = new JSONObject();
                toActions.add(jsonWa);
                jsonWa.put("id", towa.getId());
                jsonWa.put("internalName", towa.getInternalName());
                jsonWa.put("title", towa.getTitle());
                jsonWa.put("jobCode", towa.getJobCode());
                jsonWa.put("isDisplay", true);

                JSONObject XorActionSelected = new JSONObject();
                XorActionSelected.put("checked", false);
                XorActionSelected.put("isDisplay", false);
                XorActionSelected.put("id", "XOR" + towa.getId());
                jsonWa.put("XorActionSelected", XorActionSelected);

                com.alibaba.fastjson.JSONArray checkers = new com.alibaba.fastjson.JSONArray();
                jsonWa.put("checkers", checkers);

                // 如果预设的是用户，而不是角色
                if (towa.getJobCode().equals(WorkflowActionDb.PRE_TYPE_USER_SELECT) || towa.getJobCode().equals(WorkflowActionDb.PRE_TYPE_USER_SELECT_IN_ADMIN_DEPT)) {
                    String uName = towa.getUserName();
                    if (!StrUtil.isEmpty(uName)) {
                        String[] nameAry = StrUtil.split(uName, ",");
                        for (String userName : nameAry) {
                            JSONObject jsonChecker = new JSONObject();
                            jsonChecker.put("userName", userName);
                            jsonChecker.put("realName", um.getUserDb(userName).getRealName());
                            jsonChecker.put("checked", true);
                            jsonChecker.put("type", "checkbox");
                            checkers.add(jsonChecker);
                        }
                    }

                    isBtnSelUserShow = true;
                    jsonWa.put("isBtnSelUser", true);
                    jsonWa.put("isBtnXor", true); // 与isBtnSelUser联用，表示是在分支节点上选人人
                } else {
                    Iterator userir = null;
                    int userCount = 0;
                    try {
                        String deptOfUserWithMultiDept;
                        if (cfg.getBooleanProperty("isDeptSwitchable")) {
                            deptOfUserWithMultiDept = Privilege.getCurDeptCode();
                        } else {
                            deptOfUserWithMultiDept = ParamUtil.get(request, "deptOfUserWithMultiDept");
                        }
                        json.put("deptOfUserWithMultiDept", deptOfUserWithMultiDept);
                        WorkflowRouter workflowRouter = new WorkflowRouter();
                        Vector<UserDb> vuser = workflowRouter.matchActionUser(request, towa, wa, false, deptOfUserWithMultiDept);
                        userir = vuser.iterator();
                        userCount = vuser.size();
                    } catch (MatchUserException e) {
                        json.put("isMatchUserException", true);
                        json.put("isMultiDept", true);
                        com.alibaba.fastjson.JSONArray deptAry = new com.alibaba.fastjson.JSONArray();
                        json.put("multiDepts", deptAry);

                        if (isMultiDeptCount == 0) {
                            DeptUserDb du = new DeptUserDb();
                            DeptMgr dm = new DeptMgr();
                            // 请选择您所在的部门
                            Vector<DeptDb> vu = du.getDeptsOfUser(mad.getUserName());
                            for (DeptDb mdd : vu) {
                                String deptName = "";
                                if (!mdd.getParentCode().equals(DeptDb.ROOTCODE) && !mdd.getCode().equals(DeptDb.ROOTCODE)) {
                                    deptName = dm.getDeptDb(mdd.getParentCode()).getName() + "<span style='font-family:宋体'>&nbsp;->&nbsp;</span>" + mdd.getName();
                                } else {
                                    deptName = mdd.getName();
                                }
                                JSONObject jsonDept = new JSONObject();
                                jsonDept.put("deptCode", mdd.getCode());
                                jsonDept.put("deptName", deptName);
                                deptAry.add(jsonDept);
                            }
                            isMultiDeptCount++;
                        }
                    } catch (ErrMsgException e) {
                        json.put("errCode", -1);
                        json.put("info", e.getMessage());
                        json.put("hasCond", hasCond);
                        return json;
                    }

                    if (userir != null) {
                        while (userir.hasNext()) {
                            UserDb ud = (UserDb) userir.next();
                            boolean checked = !flagXorRadiate && userCount == 1;
                            JSONObject jsonChecker = new JSONObject();
                            jsonChecker.put("userName", ud.getName());
                            jsonChecker.put("realName", ud.getRealName());
                            jsonChecker.put("checked", checked);
                            checkers.add(jsonChecker);
                        }
                    }

                    if (canSelUser) {
                        isBtnSelUserShow = true;
                        jsonWa.put("isBtnSelUser", true);
                        jsonWa.put("isBtnXor", flagXorRadiate);
                    }
                }
                WorkflowLinkDb wld2 = wld.getWorkflowLinkDbForward(wa, towa);
                if (wld2.getExpireHour() != 0) {
                    jsonWa.put("expireHour", wld2.getExpireHour());
                    jsonWa.put("expireUnit", "day".equals(flowExpireUnit) ? "天" : "小时");
                }
            }

            if (q == 0) {
                json.put("errCode", -3);
                if (askType == 1) {
                    info.append("条件 " + StrUtil.toHtml(conds) + " 不匹配，请注意填写是否正确，重新填写后请点击提交按钮！");
                } else {
                    info.append("当前节点为条件分支，尚未匹配，请正确填写表单，提交后选择下一节点的处理人！");
                }
            }
        } else {
            // 如果满足条件，或不存在條件
            boolean isMatchUserException = false;
            String deptOfUserWithMultiDept;
            if (cfg.getBooleanProperty("isDeptSwitchable")) {
                deptOfUserWithMultiDept = Privilege.getCurDeptCode();
            } else {
                deptOfUserWithMultiDept = ParamUtil.get(request, "deptOfUserWithMultiDept");
            }
            json.put("deptOfUserWithMultiDept", deptOfUserWithMultiDept);

            WorkflowLinkDb wld = new WorkflowLinkDb();
            while (toir.hasNext()) {
                WorkflowActionDb towa = toir.next();
                // 在出现节点所设用户的同时，能否选择用户
                boolean canSelUser = wr.canUserSelUser(request, towa);

                JSONObject jsonWa = new JSONObject();

                jsonWa.put("id", towa.getId());
                jsonWa.put("internalName", towa.getInternalName());
                jsonWa.put("title", towa.getTitle());
                jsonWa.put("jobCode", towa.getJobCode());
                jsonWa.put("isDisplay", true);

                // 如果本节点是异或发散节点
                if (flagXorRadiate) {
                    // 如果有节点被忽略，则说明有后继节点被选中，但要注意全部被忽略的情况
                    if (towa.getStatus() == WorkflowActionDb.STATE_IGNORED) {
                        jsonWa.put("color", "#99CCCC");
                    }

                    // 如果存在条件，且匹配结果不为空
                    if (hasCond) {
                        if (isCondSatisfied) {
                            // 找到分支上满足条件的节点
                            boolean isTowaMatched = false;
                            for (WorkflowLinkDb linkMatched : vMatched) {
                                if (towa.getInternalName().equals(linkMatched.getTo())) {
                                    isTowaMatched = true;
                                    break;
                                }
                            }

                            jsonWa.put("isMatched", isTowaMatched);
                            if (isTowaMatched) {
                                // 匹配到的分支线
                                JSONObject XorActionSelected = new JSONObject();
                                XorActionSelected.put("checked", true);
                                XorActionSelected.put("isDisplay", false);
                                XorActionSelected.put("id", "");
                                jsonWa.put("XorActionSelected", XorActionSelected);
                            } else {
                                // 20220706 直接注释掉，不返回该分支节点的信息，因前端未判断isDisplay为false时是否显示
                                // 条件不满足，则继续寻找，不显示待选节点
                                // jsonWa.put("isDisplay", false);
                                continue;
                            }
                        }
                    } else {
                        JSONObject XorActionSelected = new JSONObject();
                        XorActionSelected.put("checked", false);
                        XorActionSelected.put("isDisplay", false);
                        XorActionSelected.put("id", "XOR" + towa.getId());
                        jsonWa.put("XorActionSelected", XorActionSelected);
                    }
                }

                toActions.add(jsonWa);

                String inputType = "checkbox";
                // 如果是子流程，则只允许选择一个用户，因为流程发起人只能是一个
                if (towa.getKind() == WorkflowActionDb.KIND_SUB_FLOW) {
                    inputType = "radio";
                }

                // 自选用户
                if (towa.getJobCode().equals(WorkflowActionDb.PRE_TYPE_USER_SELECT) || towa.getJobCode().equals(WorkflowActionDb.PRE_TYPE_USER_SELECT_IN_ADMIN_DEPT)) {
                    // 如果下一节点被设为异步提交，当前节点为被退回状态，则不能重选用户
                    boolean isNextXorFinishAndReturned = false;
                    if (towa.isXorFinish() && wa.getStatus() == WorkflowActionDb.STATE_RETURN) {
                        isNextXorFinishAndReturned = true;
                    }

                    com.alibaba.fastjson.JSONArray checkers = new com.alibaba.fastjson.JSONArray();
                    jsonWa.put("checkers", checkers);

                    String uName = towa.getUserName();
                    if (!StringUtils.isEmpty(uName)) {
                        String[] nameAry = StrUtil.split(uName, ",");
                        for (String userName : nameAry) {
                            JSONObject jsonChecker = new JSONObject();
                            checkers.add(jsonChecker);
                            jsonChecker.put("userName", userName);
                            jsonChecker.put("realName", um.getUserDb(userName).getRealName());
                            jsonChecker.put("type", "checkbox");

                            if (flagXorRadiate) {
                                jsonChecker.put("checked", false);
                                jsonChecker.put("clickXor", true);
                            } else {
                                // 如果当前节点是异步提交，或下一节点是下达模式，或下一节点为异步提交并退回至当前节点，则其他人已选的用户不能被变动
                                if (wa.isXorFinish() || towa.isStrategyGoDown() || isNextXorFinishAndReturned) {
                                    jsonChecker.put("checked", true);
                                    jsonChecker.put("disabled", true);
                                } else {
                                    jsonChecker.put("checked", true);
                                }
                            }
                        }
                    }

                    if (!isNextXorFinishAndReturned) {
                        isBtnSelUserShow = true;
                        jsonWa.put("isBtnSelUser", true);
                        jsonWa.put("isBtnXor", flagXorRadiate);
                    }
                } else if ((wa.getStatus() == WorkflowActionDb.STATE_RETURN || wfp.isReactive()) && StrUtil.getNullStr(towa.getJobCode()).startsWith(WorkflowActionDb.PRE_TYPE_FIELD_USER)) {
                    continue;
                } else {
                    Iterator userir = null;
                    int userCount = 0;
                    try {
                        // 当异或发散不带有条件时，如果存在兼职且已选择了所在部门时
                        WorkflowRouter workflowRouter = new WorkflowRouter();
                        Vector vuser = workflowRouter.matchActionUser(request, towa, wa, false, deptOfUserWithMultiDept);
                        userir = vuser.iterator();
                        userCount = vuser.size();
                    } catch (MatchUserException e) {
                        isMatchUserException = true;
                    } catch (ErrMsgException e) {
                        json.put("errCode", -1);
                        json.put("info", e.getMessage());
                        json.put("hasCond", hasCond);
                        return json;
                    }

                    com.alibaba.fastjson.JSONArray checkers = new com.alibaba.fastjson.JSONArray();
                    jsonWa.put("checkers", checkers);

                    while (userir != null && userir.hasNext()) {
                        UserDb ud = (UserDb) userir.next();
                        if (ud == null || !ud.isLoaded()) {
                            userCount--;
                            continue;
                        }

                        JSONObject jsonChecker = new JSONObject();
                        checkers.add(jsonChecker);
                        jsonChecker.put("userName", ud.getName());
                        jsonChecker.put("realName", ud.getRealName());
                        jsonChecker.put("clickXor", true);

                        // 如果含条件，且分支节点上仅一个用户满足条件，则置为checked
                        // 否则仅异或发散时，需要依靠click事件去选分支
                        if (hasCond && userCount == 1) {
                            jsonChecker.put("checked", true);
                            jsonChecker.put("disabled", true);
                        } else {
                            // 如果不是异或发散，且节点上用户唯一，则选中
                            if (!flagXorRadiate) {
                                if (!towa.isStrategySelectable() || userCount == 1) {
                                    jsonChecker.put("checked", true);
                                    jsonChecker.put("disabled", true);
                                } else {
                                    String checked = towa.isStrategySelected() ? "checked" : "";
                                    jsonChecker.put("checked", checked);
                                    jsonChecker.put("type", inputType);
                                }
                            } else {
                                if (!towa.isStrategySelectable()) {
                                    jsonChecker.put("checked", true);
                                    jsonChecker.put("disabled", true);
                                    jsonChecker.put("type", inputType);
                                } else {
                                    String checked = towa.isStrategySelected() ? "checked" : "";
                                    jsonChecker.put("checked", checked);
                                    jsonChecker.put("type", inputType);
                                }
                            }
                        }
                    }

                    if (canSelUser) {
                        /*if (hasCond) {
                            JSONObject XorActionSelected = new JSONObject();
                            XorActionSelected.put("checked", false);
                            XorActionSelected.put("isDisplay", true);
                            XorActionSelected.put("id", "XOR" + towa.getId());
                            jsonWa.put("XorActionSelected", XorActionSelected);
                        }*/
                        isBtnSelUserShow = true;
                        jsonWa.put("isBtnSelUser", true);
                        jsonWa.put("isBtnXor", flagXorRadiate);
                    }
                }
                WorkflowLinkDb wld2 = wld.getWorkflowLinkDbForward(wa, towa);
                if (wld2.getExpireHour() != 0) {
                    jsonWa.put("expireHour", wld2.getExpireHour());
                    jsonWa.put("expireUnit", "day".equals(flowExpireUnit) ? "天" : "小时");
                }
            }

            json.put("isMatchUserException", isMatchUserException);
            if (isMatchUserException) {
                json.put("isMultiDept", true);
                com.alibaba.fastjson.JSONArray deptAry = new com.alibaba.fastjson.JSONArray();
                json.put("multiDepts", deptAry);

                // 请选择您所在的部门
                DeptMgr dm = new DeptMgr();
                DeptUserDb du = new DeptUserDb();
                Vector<DeptDb> vu = du.getDeptsOfUser(mad.getUserName());
                Iterator<DeptDb> irdu = vu.iterator();
                while (irdu.hasNext()) {
                    DeptDb mdd = (DeptDb) irdu.next();
                    String deptName = "";
                    if (!mdd.getParentCode().equals(DeptDb.ROOTCODE) && !mdd.getCode().equals(DeptDb.ROOTCODE)) {
                        deptName = dm.getDeptDb(mdd.getParentCode()).getName() + "<span class='match-dept-arrow'>-></span>" + mdd.getName();
                    } else {
                        deptName = mdd.getName();
                    }
                    JSONObject jsonDept = new JSONObject();
                    jsonDept.put("deptCode", mdd.getCode());
                    jsonDept.put("deptName", deptName);
                    deptAry.add(jsonDept);
                }
            }
        }

        if (vto.size() == 1) {
            // 检查是否延迟，且可以更改时间
            WorkflowActionDb nwa = (WorkflowActionDb) vto.elementAt(0);
            if (nwa.isDelayed()) {
                json.put("isDelayed", true);
                json.put("actionTitleDelayed", nwa.getTitle());
                json.put("actionJobNameDelayed", nwa.getJobName());
                json.put("isCanPrivUserModifyDelayDate", nwa.isCanPrivUserModifyDelayDate());
                json.put("timeDelayedValue", nwa.getTimeDelayedValue());
                json.put("timeDelayedUnit", nwa.getTimeDelayedUnit());
                json.put("timeDelayedUnitDesc", WorkflowActionDb.getTimeUnitDesc(nwa.getTimeDelayedValue()));
            }
        }

        // 是否存在选择用户按钮需显示
        json.put("isBtnSelUserShow", isBtnSelUserShow);
        json.put("info", info.toString());

        return json;
    }

    @Override
    public com.alibaba.fastjson.JSONArray getToolbarButtons(WorkflowPredefineDb wpd, Leaf lf, WorkflowDb wf, MyActionDb mad, WorkflowActionDb wa, Vector vreturn, String flag, String myname, boolean isBtnSaveShow, boolean isActionKindRead, boolean canUserSeeDesignerWhenDispose, boolean canUserSeeFlowChart) {
        com.redmoon.oa.flow.FlowConfig conf = new com.redmoon.oa.flow.FlowConfig();
        com.alibaba.fastjson.JSONArray aryButton = new com.alibaba.fastjson.JSONArray();
        boolean isReadOnly = isActionKindRead;
        if (!isReadOnly) {
            if (isBtnSaveShow && conf.getIsDisplay("FLOW_BUTTON_SAVE")) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("type", "button");
                json.put("text", conf.getBtnName("FLOW_BUTTON_SAVE").startsWith("#") ? i18nUtil.get("saveDraft") : conf.getBtnName("FLOW_BUTTON_SAVE"));
                json.put("title", conf.getBtnTitle("FLOW_BUTTON_SAVE").startsWith("#") ? i18nUtil.get("noSubmit") : conf.getBtnTitle("FLOW_BUTTON_SAVE"));
                json.put("name", "save");
//                json.put("useable", "T");
//                json.put("handler", "saveDraft();");
                aryButton.add(json);
            }
            if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
                // 取得节点上设置的同意按钮名称
                String textName = "";
                String btnAgreeName = WorkflowActionDb.getActionProperty(wpd, wa.getInternalName(), "btnAgreeName");
                if (wf.isStarted() && wa.getIsStart() != 1) {
                    if (conf.getIsDisplay("FLOW_BUTTON_AGREE")) {
                        if (btnAgreeName != null && !"".equals(btnAgreeName)) {
                            textName = btnAgreeName;
                        } else {
                            textName = conf.getBtnName("FLOW_BUTTON_AGREE").startsWith("#") ? i18nUtil.get("agree") : conf.getBtnName("FLOW_BUTTON_AGREE");
                        }
                    }
                } else {
                    if (conf.getIsDisplay("FLOW_BUTTON_COMMIT")) {
                        if (btnAgreeName != null && !"".equals(btnAgreeName)) {
                            textName = btnAgreeName;
                        } else {
                            textName = conf.getBtnName("FLOW_BUTTON_COMMIT").startsWith("#") ? i18nUtil.get("submit") : conf.getBtnName("FLOW_BUTTON_COMMIT");
                        }
                    }
                }

                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("type", "button");
                json.put("text", textName);
                json.put("title", conf.getBtnTitle("FLOW_BUTTON_AGREE").startsWith("#") ? i18nUtil.get("nextNode") : conf.getBtnTitle("FLOW_BUTTON_AGREE"));
                json.put("name", "commit");
//                json.put("useable", "T");
//                json.put("handler", "toolbarSubmit();");
                aryButton.add(json);
            }
        } else {
            if (conf.getIsDisplay("FLOW_BUTTON_CHECK")) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("type", "button");
                json.put("text", conf.getBtnName("FLOW_BUTTON_CHECK").startsWith("#") ? i18nUtil.get("review") : conf.getBtnName("FLOW_BUTTON_CHECK"));
                json.put("title", conf.getBtnTitle("FLOW_BUTTON_CHECK").startsWith("#") ? i18nUtil.get("review") : conf.getBtnTitle("FLOW_BUTTON_CHECK"));
                json.put("name", "commit");
//                json.put("useable", "T");
//                json.put("handler", "toolbar.setDisabled(0, true);\n" +
//                        "                    $('#bodyBox').showLoading();\n" +
//                        "                    read();");
                aryButton.add(json);
            }
        }

        if (conf.getIsDisplay("FLOW_BUTTON_PLUS") && wpd.isPlus()) {
            if (!isReadOnly && "".equals(wa.getPlus())) {
                if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
                    com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                    json.put("type", "button");
                    json.put("text", conf.getBtnName("FLOW_BUTTON_PLUS").startsWith("#") ? i18nUtil.get("plus") : conf.getBtnName("FLOW_BUTTON_PLUS"));
                    json.put("title", conf.getBtnTitle("FLOW_BUTTON_PLUS").startsWith("#") ? i18nUtil.get("plus") : conf.getBtnTitle("FLOW_BUTTON_PLUS"));
                    json.put("name", "plus");
                    aryButton.add(json);
                }
            }
        }

        if (conf.getIsDisplay("FLOW_BUTTON_RETURN")) {
            if (!isReadOnly && wa.isStart != 1) {
                // 加签时不允许返回，否则可能流程可能会进行不下去 fgf 20161007
                if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND && mad.getActionStatus() != WorkflowActionDb.STATE_PLUS) {
                    if (vreturn.size() > 0 || wpd.getReturnStyle() == WorkflowPredefineDb.RETURN_STYLE_FREE) {
                        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                        json.put("type", "button");
                        json.put("text", conf.getBtnName("FLOW_BUTTON_RETURN").startsWith("#") ? i18nUtil.get("back") : conf.getBtnName("FLOW_BUTTON_RETURN"));
                        json.put("title", conf.getBtnTitle("FLOW_BUTTON_RETURN").startsWith("#") ? i18nUtil.get("backNode") : conf.getBtnTitle("FLOW_BUTTON_RETURN"));
                        json.put("name", "return");
                        aryButton.add(json);
                    }
                }
            }
        }

        // 如果该节点是异或节点，则如果其后续相邻节点中有已完成的节点，说明该节点曾被激活过，那么用户可以选择不往下继续
        if (conf.getIsDisplay("FLOW_BUTTON_NOCOMMIT")) {
            if (!isReadOnly && wa.isXorAggregate()) {
                int accessedCount = wa.linkedFromActionsAccessedCount();
                if (accessedCount >= 2) {
                    if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
                        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                        json.put("type", "button");
                        json.put("text", conf.getBtnName("FLOW_BUTTON_NOCOMMIT").startsWith("#") ? i18nUtil.get("completed") : conf.getBtnName("FLOW_BUTTON_NOCOMMIT"));
                        json.put("title", conf.getBtnTitle("FLOW_BUTTON_NOCOMMIT").startsWith("#") ? i18nUtil.get("noNextNode") : conf.getBtnTitle("FLOW_BUTTON_NOCOMMIT"));
                        json.put("name", "finish");
                        aryButton.add(json);
                    }
                }
            }
        }

        if (conf.getIsDisplay("FLOW_BUTTON_PROCESS")) {
            if (wf.isStarted() && wa.getIsStart()!=1 && !lf.isDebug()) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("type", "button");
                json.put("text", conf.getBtnName("FLOW_BUTTON_PROCESS").startsWith("#") ? i18nUtil.get("process") : conf.getBtnName("FLOW_BUTTON_PROCESS"));
                json.put("title", conf.getBtnTitle("FLOW_BUTTON_PROCESS").startsWith("#") ? i18nUtil.get("showFlow") : conf.getBtnTitle("FLOW_BUTTON_PROCESS"));
                json.put("name", "process");
                aryButton.add(json);
            }
        }

        /*if (!"".equals(wpd.getDirCode()) && conf.getIsDisplay("FLOW_BUTTON_ALTER")) {
            if (wf.isStarted() && !lf.isDebug()) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("type", "button");
                json.put("text", conf.getBtnName("FLOW_BUTTON_ALTER").startsWith("#") ? i18nUtil.get("alter") : conf.getBtnName("FLOW_BUTTON_ALTER"));
                json.put("title", conf.getBtnTitle("FLOW_BUTTON_ALTER").startsWith("#") ? i18nUtil.get("alterTitle") : conf.getBtnTitle("FLOW_BUTTON_ALTER"));
                json.put("name", "alter");
                aryButton.add(json);
            }
        }*/

        if (conf.getIsDisplay("FLOW_BUTTON_DOC")) {
            if (!isReadOnly) {
                if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
                    com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                    json.put("type", "button");
                    json.put("text", conf.getBtnName("FLOW_BUTTON_DOC").startsWith("#") ? i18nUtil.get("proposedText") : conf.getBtnName("FLOW_BUTTON_DOC"));
                    json.put("title", conf.getBtnTitle("FLOW_BUTTON_DOC").startsWith("#") ? i18nUtil.get("work") : conf.getBtnTitle("FLOW_BUTTON_DOC"));
                    json.put("name", "doc");
                    aryButton.add(json);
                }
            }
        }

        if (conf.getIsDisplay("FLOW_BUTTON_DISAGREE")) {
            if (!isReadOnly && wf.isStarted() && flag.length() >= 9 && flag.substring(8, 9).equals("1")) {
                if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
                    String text = "";
                    String btnRefuseName = WorkflowActionDb.getActionProperty(wpd, wa.getInternalName(), "btnRefuseName");
                    if (btnRefuseName != null && !"".equals(btnRefuseName)) {
                        text = btnRefuseName;
                    } else {
                        text = conf.getBtnName("FLOW_BUTTON_DISAGREE").startsWith("#") ? i18nUtil.get("refuse") : conf.getBtnName("FLOW_BUTTON_DISAGREE");
                    }
                    String title = conf.getBtnTitle("FLOW_BUTTON_DISAGREE").startsWith("#") ? i18nUtil.get("refusedAndEndFlow") : conf.getBtnTitle("FLOW_BUTTON_DISAGREE");
                    com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                    json.put("type", "button");
                    json.put("text", text);
                    json.put("title", title);
                    json.put("name", "disagree");
//                    json.put("useable", "T");
//                    json.put("handler", "disagree();");
                    aryButton.add(json);
                }
            }
        }

        if (conf.getIsDisplay("FLOW_BUTTON_FINISH")) {
            if (!isReadOnly && wf.isStarted() && flag.length() >= 12 && "1".equals(flag.substring(11, 12))) {
                if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
                    com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                    json.put("type", "button");
                    json.put("text", conf.getBtnName("FLOW_BUTTON_FINISH").startsWith("#") ? i18nUtil.get("finish") : conf.getBtnName("FLOW_BUTTON_FINISH"));
                    json.put("title", conf.getBtnTitle("FLOW_BUTTON_FINISH").startsWith("#") ? i18nUtil.get("finishAndAgree") : conf.getBtnTitle("FLOW_BUTTON_FINISH"));
                    json.put("name", "finishAgree");
//                    json.put("useable", "T");
//                    json.put("handler", "manualFinishAgree();");
                    aryButton.add(json);
                }
            }
        }

        if (!isReadOnly && wf.isStarted()) {
            if (mad.getCheckStatus() == MyActionDb.CHECK_STATUS_SUSPEND) {
                if (conf.getIsDisplay("FLOW_BUTTON_RESUME")) {
                    com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                    json.put("type", "button");
                    json.put("text", conf.getBtnName("FLOW_BUTTON_RESUME").startsWith("#") ? i18nUtil.get("recovery") : conf.getBtnName("FLOW_BUTTON_RESUME"));
                    json.put("title", conf.getBtnTitle("FLOW_BUTTON_RESUME").startsWith("#") ? i18nUtil.get("recoveryProcess") : conf.getBtnTitle("FLOW_BUTTON_RESUME"));
                    json.put("name", "resume");
//                    json.put("useable", "T");
//                    json.put("handler", "resume();");
                    aryButton.add(json);
                }
            } else {
                if (conf.getIsDisplay("FLOW_BUTTON_SUSPEND")) {
                    com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                    json.put("type", "button");
                    json.put("text", conf.getBtnName("FLOW_BUTTON_SUSPEND").startsWith("#") ? i18nUtil.get("hangUp") : conf.getBtnName("FLOW_BUTTON_SUSPEND"));
                    json.put("title", conf.getBtnTitle("FLOW_BUTTON_SUSPEND").startsWith("#") ? i18nUtil.get("hang") : conf.getBtnTitle("FLOW_BUTTON_SUSPEND"));
                    json.put("name", "suspend");
//                    json.put("useable", "T");
//                    json.put("handler", "suspend();");
                    aryButton.add(json);
                }
            }
        }

        if (conf.getIsDisplay("FLOW_BUTTON_TRANSFER") && wpd.isTransfer()) {
            if (!isReadOnly && wf.isStarted() && wa.getIsStart()!=1 && !lf.isDebug()) {
                if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
                    com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                    json.put("type", "button");
                    json.put("text", conf.getBtnName("FLOW_BUTTON_TRANSFER").startsWith("#") ? i18nUtil.get("assign") : conf.getBtnName("FLOW_BUTTON_TRANSFER"));
                    json.put("title", conf.getBtnTitle("FLOW_BUTTON_TRANSFER").startsWith("#") ? i18nUtil.get("assignUser") : conf.getBtnTitle("FLOW_BUTTON_TRANSFER"));
                    json.put("name", "transfer");
//                    json.put("useable", "T");
//                    json.put("handler", "transfer();");
                    aryButton.add(json);
                }
            }
        }

        if (conf.getIsDisplay("FLOW_BUTTON_DIRECT")) {
            if (!isReadOnly && mad.getActionStatus() == WorkflowActionDb.STATE_RETURN && wpd.getReturnMode() == WorkflowPredefineDb.RETURN_MODE_TO_RETURNER) {
                if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
                    com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                    json.put("type", "button");
                    json.put("text", conf.getBtnName("FLOW_BUTTON_DIRECT").startsWith("#") ? i18nUtil.get("direct") : conf.getBtnName("FLOW_BUTTON_DIRECT"));
                    json.put("title", conf.getBtnTitle("FLOW_BUTTON_DIRECT").startsWith("#") ? i18nUtil.get("directBack") : conf.getBtnTitle("FLOW_BUTTON_DIRECT"));
                    json.put("name", "toRetuner");
//                    json.put("useable", "T");
//                    json.put("handler", "toRetuner();");
                    aryButton.add(json);
                }
            }
        }

        if (conf.getIsDisplay("FLOW_BUTTON_DISCARD")) {
            if (!isReadOnly && flag.length() >= 3 && "1".equals(flag.substring(2, 3))) {
                if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
                    com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                    json.put("type", "button");
                    json.put("text", conf.getBtnName("FLOW_BUTTON_DISCARD").startsWith("#") ? i18nUtil.get("discard") : conf.getBtnName("FLOW_BUTTON_DISCARD"));
                    json.put("title", conf.getBtnTitle("FLOW_BUTTON_DISCARD").startsWith("#") ? i18nUtil.get("discardFlow") : conf.getBtnTitle("FLOW_BUTTON_DISCARD"));
                    json.put("name", "discard");
                    aryButton.add(json);
                }
            }
        }

        if (conf.getIsDisplay("FLOW_BUTTON_DEL") && !isReadOnly) {
            if (WorkflowMgr.canDelFlowOnAction(request, wf, wa, mad)) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("type", "button");
                json.put("text", conf.getBtnName("FLOW_BUTTON_DEL").startsWith("#") ? i18nUtil.get("delete") : conf.getBtnName("FLOW_BUTTON_DEL"));
                json.put("title", conf.getBtnTitle("FLOW_BUTTON_DEL").startsWith("#") ? i18nUtil.get("deleteFlow") : conf.getBtnTitle("FLOW_BUTTON_DEL"));
                json.put("name", "del");
                aryButton.add(json);
            }
        }

        if (conf.getIsDisplay("FLOW_BUTTON_ARCHIVE")) {
            if (!isReadOnly && wf.isStarted() && flag.length() >= 5) {
                if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
                    if (wa.isArchiveManual()) {
                        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                        json.put("type", "button");
                        json.put("text", conf.getBtnName("FLOW_BUTTON_ARCHIVE").startsWith("#") ? i18nUtil.get("archive") : conf.getBtnName("FLOW_BUTTON_ARCHIVE"));
                        json.put("title", conf.getBtnTitle("FLOW_BUTTON_ARCHIVE").startsWith("#") ? i18nUtil.get("archiveFile") : conf.getBtnTitle("FLOW_BUTTON_ARCHIVE"));
                        json.put("name", "archive");
//                        json.put("useable", "T");
//                        json.put("handler", "saveArchive(" + wf.getId() + "," + wa.getId() + ");");
                    } else if ("3".equals(flag.substring(4, 5))) {
                        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                        json.put("type", "button");
                        json.put("text", conf.getBtnName("FLOW_BUTTON_ARCHIVE").startsWith("#") ? i18nUtil.get("archive") : conf.getBtnName("FLOW_BUTTON_ARCHIVE"));
                        json.put("title", i18nUtil.get("archiveForm"));
                        json.put("name", "archive");
//                        json.put("useable", "T");
//                        json.put("handler", "saveArchiveGov(" + wf.getId() + "," + wa.getId() + ");");
                        aryButton.add(json);
                    }
                }
            }
        }

        if (conf.getIsDisplay("FLOW_BUTTON_DISTRIBUTE")) {
            if (!isReadOnly && (wpd.isDistribute() || wa.isDistribute())) {
                if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
                    String text = "", title = "";
                    if (!conf.getBtnName("FLOW_BUTTON_DISTRIBUTE").startsWith("#")) {
                        text = conf.getBtnName("FLOW_BUTTON_DISTRIBUTE");
                        title = conf.getBtnTitle("FLOW_BUTTON_DISTRIBUTE");
                    } else {
                        String kind = License.getInstance().getKind();
                        if (kind.equalsIgnoreCase(License.KIND_COM)) {
                            text = i18nUtil.get("notify");
                            title = i18nUtil.get("notify");
                        } else {
                            text = i18nUtil.get("distribute");
                            title = i18nUtil.get("fileDistribute");
                        }
                    }

                    com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                    json.put("type", "button");
                    json.put("text", text);
                    json.put("title", title);
                    json.put("name", "distribute");
//                    json.put("useable", "T");
//                    json.put("handler", "distributeDoc(" + wf.getId() + ");");
                    aryButton.add(json);
                }
            }
        }
        if (conf.getIsDisplay("FLOW_BUTTON_CHART")) {
            if (canUserSeeDesignerWhenDispose || canUserSeeFlowChart) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("type", "button");
                json.put("text", conf.getBtnName("FLOW_BUTTON_CHART").startsWith("#") ? i18nUtil.get("flowChart") : conf.getBtnName("FLOW_BUTTON_CHART"));
                json.put("title", conf.getBtnTitle("FLOW_BUTTON_CHART").startsWith("#") ? i18nUtil.get("showFlowChart") : conf.getBtnTitle("FLOW_BUTTON_CHART"));
                json.put("name", "chart");
//                json.put("useable", "T");
//                json.put("handler", "ShowDesigner();");
                aryButton.add(json);
            }
        }

        if (!lf.isDebug() && conf.getIsDisplay("FLOW_BUTTON_ATTENTION")) {
            WorkflowFavoriteDb wffd = new WorkflowFavoriteDb();
            if (!wffd.isExist(myname, wf.getId())) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("type", "button");
                json.put("text", conf.getBtnName("FLOW_BUTTON_ATTENTION").startsWith("#") ? i18nUtil.get("attention") : conf.getBtnName("FLOW_BUTTON_ATTENTION"));
                json.put("title", conf.getBtnTitle("FLOW_BUTTON_ATTENTION").startsWith("#") ? i18nUtil.get("attention") : conf.getBtnTitle("FLOW_BUTTON_ATTENTION"));
                json.put("name", "attention");
//                json.put("useable", "T");
//                json.put("handler", "setAttention(true);");
                aryButton.add(json);
            } else {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("type", "button");
                json.put("text", conf.getBtnName("FLOW_BUTTON_CANCEL_ATTENTION").startsWith("#") ? i18nUtil.get("cancelAttention") : conf.getBtnName("FLOW_BUTTON_CANCEL_ATTENTION"));
                json.put("title", conf.getBtnTitle("FLOW_BUTTON_CANCEL_ATTENTION").startsWith("#") ? i18nUtil.get("cancelAttention") : conf.getBtnTitle("FLOW_BUTTON_CANCEL_ATTENTION"));
                json.put("name", "cancelAttention");
//                json.put("useable", "T");
//                json.put("handler", "setAttention(false);");
                aryButton.add(json);
            }
        }

        String tester = (String) com.redmoon.oa.pvg.Privilege.getAttribute(request, com.redmoon.oa.pvg.Privilege.SESSION_OA_FLOW_TESTER); // 流程测试员
        Privilege privilege = new Privilege();
        if (tester == null && privilege.isUserPrivValid(request, "admin")) {
            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("type", "button");
            json.put("text", "管理");
            json.put("title", "管理流程");
            json.put("name", "manage");
//            json.put("useable", "T");
//            json.put("handler", "manage();");
            aryButton.add(json);
        }

        if (lf.isDebug() && com.redmoon.oa.kernel.License.getInstance().isPlatformSrc()) {
            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("type", "button");
            json.put("text", "调试");
            json.put("title", "调试面板");
            json.put("name", "debug");
//            json.put("useable", "T");
//            json.put("handler", "");
            aryButton.add(json);
        }
        return aryButton;
    }

    @Override
    public com.alibaba.fastjson.JSONArray getToolbarBottonsFree(WorkflowPredefineDb wpd, Leaf lf, WorkflowDb wf, MyActionDb mad, WorkflowActionDb wa, Vector vreturn, String myname) {
        com.redmoon.oa.flow.FlowConfig conf = new com.redmoon.oa.flow.FlowConfig();
        com.alibaba.fastjson.JSONArray aryButton = new com.alibaba.fastjson.JSONArray();

        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        json.put("type", "button");
        json.put("text", "保存草稿");
        json.put("title", "不提交，仅保存草稿");
        json.put("name", "save");
        // json.put("useable", "T");
        // json.put("handler", "saveDraft();");
        aryButton.add(json);

        if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
            json = new com.alibaba.fastjson.JSONObject();
            json.put("type", "button");
            json.put("text", "提交");
            json.put("title", "提交至下一节点处理");
            json.put("name", "commit");
            // json.put("useable", "T");
            // json.put("handler", "SubmitResult();");
            aryButton.add(json);
        }

        if (mad.getActionStatus() != WorkflowActionDb.STATE_PLUS && vreturn.size() > 0) {
            json = new com.alibaba.fastjson.JSONObject();
            json.put("type", "button");
            json.put("text", "返回");
            json.put("title", "返回给之前的节点处理人员");
            json.put("name", "return");
            // json.put("useable", "T");
            // json.put("handler", "returnFlow();");
            aryButton.add(json);
        }

        if (wf.isStarted()) {
            json = new com.alibaba.fastjson.JSONObject();
            json.put("type", "button");
            json.put("text", "过程");
            json.put("title", "查看流程的流转过程");
            json.put("name", "process");
            // json.put("useable", "T");
            // json.put("handler", "showFlow();");
            aryButton.add(json);
        }

        if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
            if (conf.getIsDisplay("FLOW_BUTTON_PLUS") && "".equals(wa.getPlus())) {
                json = new com.alibaba.fastjson.JSONObject();
                json.put("type", "button");
                json.put("text", "加签");
                json.put("title", "加签");
                json.put("name", "plus");
                // json.put("useable", "T");
                // json.put("handler", "addPlus();");
                aryButton.add(json);
            }
        }

        if (conf.getIsDisplay("FLOW_BUTTON_DOC")) {
            json = new com.alibaba.fastjson.JSONObject();
            json.put("type", "button");
            json.put("text", "拟文");
            json.put("title", "拟订Word文档");
            json.put("name", "doc");
            // json.put("useable", "T");
            // json.put("handler", "writeDoc();");
            aryButton.add(json);
        }

        UserDb user = new UserDb();
        user = user.getUserDb(myname);
        try {
            if (wf.isStarted() && wpd.canUserDo(user, wf, "stop")) {
                json = new com.alibaba.fastjson.JSONObject();
                json.put("type", "button");
                json.put("text", "拒绝");
                json.put("title", "拒绝同时结束流程");
                json.put("name", "disagree");
                // json.put("useable", "T");
                // json.put("handler", "disagree();");
                aryButton.add(json);
            }
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
        }

        if (wf.isStarted()) {
            if (mad.getCheckStatus() == MyActionDb.CHECK_STATUS_SUSPEND) {
                json = new com.alibaba.fastjson.JSONObject();
                json.put("type", "button");
                json.put("text", "恢复");
                json.put("title", "恢复处理流程");
                json.put("name", "suspend");
                // json.put("useable", "T");
                // json.put("handler", "resume();");
                aryButton.add(json);
            } else {
                json = new com.alibaba.fastjson.JSONObject();
                json.put("type", "button");
                json.put("text", "挂起");
                json.put("title", "将流程挂起，暂不处理");
                json.put("name", "suspend");
                // json.put("useable", "T");
                // json.put("handler", "suspend();");
                aryButton.add(json);
            }
        }

        if (conf.getIsDisplay("FLOW_BUTTON_TRANSFER")) {
            if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
                if (wf.isStarted()) {
                    json = new com.alibaba.fastjson.JSONObject();
                    json.put("type", "button");
                    json.put("text", "转办");
                    json.put("title", "转办给其他人处理");
                    json.put("name", "transfer");
                    // json.put("useable", "T");
                    // json.put("handler", "transfer();");
                    aryButton.add(json);
                }
            }
        }

        try {
            if (wpd.canUserDo(user, wf, "del")) {
                json = new JSONObject();
                json.put("type", "button");
                json.put("text", "删除");
                json.put("title", "删除流程");
                json.put("name", "del");
                // json.put("useable", "T");
                // json.put("handler", "del();");
                aryButton.add(json);
            }
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
        }

        try {
            if (wpd.canUserDo(user, wf, "archive")) {
                json = new JSONObject();
                json.put("type", "button");
                json.put("text", "存档");
                json.put("title", "将表单存入文件柜");
                json.put("name", "archive");
                // json.put("useable", "T");
                // json.put("handler", "saveArchive(" + wf.getId() + "," + wa.getId() + ");");
                aryButton.add(json);
            }
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
        }

        if (conf.getIsDisplay("FLOW_BUTTON_ATTENTION")) {
            WorkflowFavoriteDb wffd = new WorkflowFavoriteDb();
            if (!wffd.isExist(myname, wf.getId())) {
                json = new JSONObject();
                json.put("type", "button");
                json.put("text", conf.getBtnName("FLOW_BUTTON_ATTENTION").startsWith("#") ? i18nUtil.get("attention") : conf.getBtnName("FLOW_BUTTON_ATTENTION"));
                json.put("title", conf.getBtnName("FLOW_BUTTON_ATTENTION").startsWith("#") ? i18nUtil.get("attention") : conf.getBtnName("FLOW_BUTTON_ATTENTION"));
                json.put("name", "attention");
                // json.put("useable", "T");
                // json.put("handler", "setAttention(true);");
                aryButton.add(json);
            } else {
                json = new JSONObject();
                json.put("type", "button");
                json.put("text", conf.getBtnName("FLOW_BUTTON_CANCEL_ATTENTION").startsWith("#") ? i18nUtil.get("attention") : conf.getBtnName("FLOW_BUTTON_CANCEL_ATTENTION"));
                json.put("title", conf.getBtnName("FLOW_BUTTON_CANCEL_ATTENTION").startsWith("#") ? i18nUtil.get("attention") : conf.getBtnName("FLOW_BUTTON_CANCEL_ATTENTION"));
                json.put("name", "cancelAttention");
                // json.put("useable", "T");
                // json.put("handler", "setAttention(false);");
                aryButton.add(json);
            }
        }

        if (conf.getIsDisplay("FLOW_BUTTON_CHART")) {
            com.redmoon.oa.Config cfg = com.redmoon.oa.Config.getInstance();
            boolean canUserSeeDesignerWhenDispose = cfg.getBooleanProperty("canUserSeeDesignerWhenDispose");
            boolean canUserSeeFlowChart = cfg.getBooleanProperty("canUserSeeFlowChart");
            if (canUserSeeDesignerWhenDispose || canUserSeeFlowChart) {
                json = new com.alibaba.fastjson.JSONObject();
                json.put("type", "button");
                json.put("text", conf.getBtnName("FLOW_BUTTON_CHART").startsWith("#") ? i18nUtil.get("flowChart") : conf.getBtnName("FLOW_BUTTON_CHART"));
                json.put("title", conf.getBtnTitle("FLOW_BUTTON_CHART").startsWith("#") ? i18nUtil.get("showFlowChart") : conf.getBtnTitle("FLOW_BUTTON_CHART"));
                json.put("name", "chart");
                aryButton.add(json);
            }
        }

        return aryButton;
    }

    /**
     * 列出处理过程，显示于流程处理页面
     *
     * @param flowId
     * @return
     */
    @Override
    public com.alibaba.fastjson.JSONArray listProcess(int flowId) {
        com.redmoon.oa.Config cfg = com.redmoon.oa.Config.getInstance();
        String flowExpireUnit = cfg.get("flowExpireUnit");
        boolean isHour = !"day".equals(flowExpireUnit);

        UserMgr um = new UserMgr();
        MyActionDb mad = new MyActionDb();
        Vector<MyActionDb> vProcess = mad.getMyActionDbOfFlow(flowId);
        DeptMgr deptMgr = new DeptMgr();
        OACalendarDb oad = new OACalendarDb();
        WorkflowDb wfd = new WorkflowDb();
        wfd = wfd.getWorkflowDb(flowId);
        com.alibaba.fastjson.JSONArray aryMyAction = new com.alibaba.fastjson.JSONArray();
        int k = 0;
        for (MyActionDb madPro : vProcess) {
            String userName = madPro.getUserName();
            String userRealName = "";
            if (userName != null) {
                UserDb user = um.getUserDb(madPro.getUserName());
                userRealName = user.getRealName();
            }
            WorkflowActionDb wad = new WorkflowActionDb();
            wad = wad.getWorkflowActionDb((int) madPro.getActionId());

            JSONObject json = new JSONObject();

            String deptCodes = madPro.getDeptCodes();
            String[] depts = StrUtil.split(deptCodes, ",");
            String dts = "";
            if (depts != null) {
                for (String dept : depts) {
                    DeptDb dd = deptMgr.getDeptDb(dept);
                    if (dd != null) {
                        if ("".equals(dts)) {
                            dts = dd.getName();
                        } else {
                            dts += "," + dd.getName();
                        }
                    }
                }
            }
            json.put("depts", dts + ": ");

            boolean isExpired = false;
            Date chkDate = madPro.getCheckDate();
            if (chkDate == null) {
                chkDate = new Date();
            }
            if (DateUtil.compare(chkDate, madPro.getExpireDate()) == 1) {
                isExpired = true;
            }
            json.put("isExpired", isExpired);
            json.put("userRealName", userRealName);

            String privRealName = "";
            if (madPro.getPrivMyActionId() != -1) {
                MyActionDb mad2 = madPro.getMyActionDb(madPro.getPrivMyActionId());
                if (mad2.getUserName() != null) {
                    privRealName = um.getUserDb(mad2.getUserName()).getRealName();
                }
            }
            json.put("privRealName", privRealName);
            json.put("actionTitle", wad.getTitle());

            json.put("receiveDate", DateUtil.format(madPro.getReceiveDate(), "yyyy-MM-dd HH:mm:ss"));
            json.put("checkDate", DateUtil.format(madPro.getCheckDate(), "yyyy-MM-dd HH:mm:ss"));

            String remainDateStr = "";
            if (mad.getExpireDate() != null && DateUtil.compare(new Date(), mad.getExpireDate()) == 2) {
                int[] ary = DateUtil.dateDiffDHMS(mad.getExpireDate(), new Date());
                String strDay = i18nUtil.get("day");
                String strHour = i18nUtil.get("h_hour");
                String strMinute = i18nUtil.get("minute");
                remainDateStr = ary[0] + " " + strDay + ary[1] + " " + strHour + ary[2] + " " + strMinute;
            }
            json.put("remainDate", remainDateStr);

            String workDuration;
            if (isHour) {
                try {
                    double d = oad.getWorkHourCount(madPro.getReceiveDate(), madPro.getCheckDate());
                    workDuration = NumberUtil.round(d, 1);
                } catch (ErrMsgException e) {
                    workDuration = e.getMessage();
                }
            } else {
                workDuration = String.valueOf(oad.getWorkDayCountFromDb(madPro.getReceiveDate(), madPro.getCheckDate()));
            }
            json.put("workDuration", workDuration);
            json.put("performance", NumberUtil.round(madPro.getPerformance(), 2));

            json.put("alterTime", madPro.getAlterTime());

            String checkStatusName;
            if (madPro.getChecker().equals(UserDb.SYSTEM)) {
                checkStatusName = i18nUtil.get("systemAccessed");
            } else {
                checkStatusName = madPro.getCheckStatusName();
            }

            boolean isResultDesc = false;
            String resultDesc = "";
            // 发起节点不显示“同意”
            if (k != 0 && madPro.getCheckStatus() != 0 && madPro.getCheckStatus() != MyActionDb.CHECK_STATUS_TRANSFER && madPro.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
                if (madPro.getResultValue() != WorkflowActionDb.RESULT_VALUE_RETURN) {
                    isResultDesc = true;
                    String desc = WorkflowActionDb.getResultValueDesc(madPro.getResultValue());
                    if (!StrUtil.isEmpty(desc)) {
                        resultDesc = "(" + desc + ")";
                    }
                }
            }
            if (isResultDesc) {
                checkStatusName += resultDesc;
            }
            json.put("checkStatusName", checkStatusName);
            // 留言
            json.put("result", madPro.getResult());
            aryMyAction.add(json);

            k++;
        }

        return aryMyAction;
    }

    /**
     * 取得处理过程
     *
     * @param wf
     * @param myUserName
     * @param isPaperReceived
     * @param isRecall
     * @param isFlowManager
     * @param isReactive
     * @return
     */
    @Override
    public com.alibaba.fastjson.JSONArray listProcessForShow(WorkflowDb wf, String myUserName, boolean isPaperReceived, boolean isRecall, boolean isFlowManager, boolean isReactive) {
        com.redmoon.oa.Config cfg = com.redmoon.oa.Config.getInstance();
        com.alibaba.fastjson.JSONArray aryMyAction = new com.alibaba.fastjson.JSONArray();
        UserMgr um = new UserMgr();
        WorkflowActionDb wa = new WorkflowActionDb();
        MyActionDb mad = new MyActionDb();
        DeptMgr deptMgr = new DeptMgr();
        OACalendarDb oad = new OACalendarDb();
        int k = 0;
        Vector<MyActionDb> v = mad.getMyActionDbOfFlow(wf.getId());
        for (MyActionDb myActionDb : v) {
            mad = myActionDb;

            String userName = mad.getUserName();
            String userRealName = "";
            if (userName != null) {
                UserDb user = um.getUserDb(mad.getUserName());
                userRealName = user.getRealName();
            }
            wa = wa.getWorkflowActionDb((int) mad.getActionId());

            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("id", mad.getId());
            json.put("checkStatus", mad.getCheckStatus());

            String partDept = ""; // 所选择的兼职部门
            if (!"".equals(mad.getPartDept())) {
                partDept = mad.getPartDept();
            }
            String deptCodes = mad.getDeptCodes();
            String[] depts = StrUtil.split(deptCodes, ",");
            if (depts != null) {
                String dts = "";
                for (String dept : depts) {
                    DeptDb dd = deptMgr.getDeptDb(dept);
                    if (dd != null) {
                        String deptName = dd.getName();
                        if (dd.getCode().equals(partDept)) {
                            deptName = "<span class='part-dept' title='处理时选择的部门'>" + deptName + "</span>";
                        }
                        if ("".equals(dts)) {
                            dts = deptName;
                        } else {
                            dts += "," + deptName;
                        }
                    }
                }
                if (!"".equals(dts)) {
                    json.put("depts", dts + "：");
                } else {
                    json.put("depts", "");
                }
            } else {
                json.put("depts", "");
            }

            boolean isExpired = false;
            Date chkDate = mad.getCheckDate();
            if (chkDate == null) {
                chkDate = new Date();
            }
            if (DateUtil.compare(chkDate, mad.getExpireDate()) == 1) {
                isExpired = true;
            }
            json.put("isExpired", isExpired);
            json.put("expireDate", DateUtil.format(mad.getExpireDate(), "MM-dd HH:mm"));

            json.put("realName", userRealName);
            json.put("isReaded", mad.isReaded());

            String privRealName = "";
            if (mad.getPrivMyActionId() != -1) {
                MyActionDb mad2 = mad.getMyActionDb(mad.getPrivMyActionId());
                if (mad2.getUserName() != null) {
                    privRealName = um.getUserDb(mad2.getUserName()).getRealName();
                } else {
                    privRealName = "无";
                }
            } else {
                privRealName = "";
            }
            json.put("privRealName", privRealName);
            boolean isProxy = false;
            if (!"".equals(mad.getProxyUserName())) {
                isProxy = true;
                json.put("proxy", um.getUserDb(mad.getProxyUserName()).getRealName());
            }
            json.put("isProxy", isProxy);
            json.put("actionTitle", wa.getTitle());

            boolean isDateDelayed = false;
            if (wa.getDateDelayed() != null) {
                isDateDelayed = true;
            } else {
                json.put("receiveDate", DateUtil.format(mad.getReceiveDate(), "MM-dd HH:mm"));
                json.put("statusName", WorkflowActionDb.getStatusName(mad.getActionStatus()));

                boolean isReason = false;
                String reas = wa.getReason();
                if (reas != null && !"".equals(reas.trim())) {
                    isReason = true;
                    json.put("reason", reas);
                }
                json.put("isReason", isReason);
            }
            json.put("isDateDelayed", isDateDelayed);

            json.put("readDate", DateUtil.format(mad.getReadDate(), "MM-dd HH:mm"));
            json.put("checkDate", DateUtil.format(mad.getCheckDate(), "MM-dd HH:mm"));

            boolean canChangeExpireDate = isFlowManager && !mad.isChecked();
            json.put("canChangeExpireDate", canChangeExpireDate);

            String remainDateStr = "";
            if (mad.getExpireDate() != null && DateUtil.compare(new Date(), mad.getExpireDate()) == 2) {
                int[] ary = DateUtil.dateDiffDHMS(mad.getExpireDate(), new Date());
                String str_day = LocalUtil.LoadString(request, "res.flow.Flow", "day");
                String str_hour = LocalUtil.LoadString(request, "res.flow.Flow", "h_hour");
                String str_minute = LocalUtil.LoadString(request, "res.flow.Flow", "minute");
                remainDateStr = ary[0] + " " + str_day + ary[1] + " " + str_hour + ary[2] + " " + str_minute;
            }
            json.put("remainDate", remainDateStr);

            String flowExpireUnit = cfg.get("flowExpireUnit");
            boolean isHour = !"day".equals(flowExpireUnit);
            if (isHour) {
                double d = 0;
                try {
                    d = oad.getWorkHourCount(mad.getReceiveDate(), mad.getCheckDate());
                } catch (ErrMsgException e) {
                    LogUtil.getLog(getClass()).error(e);
                }
                json.put("dayOrHoutCount", NumberUtil.round(d, 1));
            } else {
                int d = oad.getWorkDayCountFromDb(mad.getReceiveDate(), mad.getCheckDate());
                json.put("dayOrHoutCount", d);
            }

            json.put("performance", NumberUtil.round(mad.getPerformance(), 2));

            String checker = "";
            if (mad.isChecked()) {
                if (mad.getChecker().equals(UserDb.SYSTEM)) {
                    checker = i18nUtil.get("system");
                } else {
                    if (!"".equals(mad.getChecker())) {
                        checker = um.getUserDb(mad.getChecker()).getRealName();
                    }
                }
            }
            json.put("checker", checker);

            json.put("checkStatusClass", MyActionDb.getCheckStatusClass(mad.getCheckStatus()));

            String checkStatusName;
            if (mad.getChecker().equals(UserDb.SYSTEM)) {
                checkStatusName = i18nUtil.get("systemAccessed");
            } else {
                checkStatusName = mad.getCheckStatusName();
            }

            boolean isResultValueDesc = false;
            String resultValueDesc = "";
            // 发起节点不显示“同意”
            if (k != 0 && mad.getCheckStatus() != 0 && mad.getCheckStatus() != MyActionDb.CHECK_STATUS_TRANSFER && mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
                if (mad.getResultValue() != WorkflowActionDb.RESULT_VALUE_RETURN) {
                    if (mad.getSubMyActionId() == MyActionDb.SUB_MYACTION_ID_NONE) {
                        isResultValueDesc = true;
                        String desc = WorkflowActionDb.getResultValueDesc(mad.getResultValue());
                        // 有可能会在config_flow.xml中把同意置为了空
                        if (!StrUtil.isEmpty(desc)) {
                            resultValueDesc = "(" + desc + ")";
                        }
                    }
                }
            }
            if (isResultValueDesc) {
                checkStatusName += resultValueDesc;
            }
            json.put("checkStatusName", checkStatusName);

            json.put("alterTime", DateUtil.format(mad.getAlterTime(), "MM-dd HH:mm"));
            // 留言
            json.put("result", MyActionMgr.renderResult(request, mad));

            boolean isSubMyAction = false;
            if (mad.getSubMyActionId() != MyActionDb.SUB_MYACTION_ID_NONE) {
                MyActionDb submad = new MyActionDb();
                submad = submad.getMyActionDb(mad.getSubMyActionId());
                isSubMyAction = true;
                json.put("subFlowLink", "&nbsp;<a href=\"javascript:;\" onclick=\"addTab('" + i18nUtil.get("subprocess") + "', '" + request.getContextPath() + "/flowShowPage.do?flowId=" + submad.getFlowId() + "')\"><font color='red'>" + i18nUtil.get("subprocess") + "</font></a>");
            }
            json.put("isSubMyAction", isSubMyAction);

            boolean isReactiveBtnShow = false;
            if (isReactive && (wf.getStatus() == WorkflowDb.STATUS_FINISHED || wf.getStatus() == WorkflowDb.STATUS_DISCARDED) && (mad.getUserName().equals(myUserName) || mad.getProxyUserName().equals(myUserName)) && mad.isChecked() && mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND_OVER && mad.getCheckStatus() != MyActionDb.CHECK_STATUS_PASS_BY_RETURN) {
                isReactiveBtnShow = true;
            }
            json.put("isReactiveBtnShow", isReactiveBtnShow);

            boolean isRecallBtnShow = false;
            if (!isPaperReceived && isRecall && mad.canRecall(myUserName)) {
                isRecallBtnShow = true;
            }
            json.put("isRecallBtnShow", isRecallBtnShow);

            boolean canHandle = true;
            // 如果流程已被放弃，则不显示“处理”按钮
            if (wf.getStatus() == WorkflowDb.STATUS_DISCARDED) {
                canHandle = false;
            }
            json.put("canHandle", canHandle);

            boolean isHandleBtnShow = false;
            if (canHandle) {
                // if (!mad.getChecker().equals(UserDb.SYSTEM)) {
                    if ((myUserName.equals(mad.getUserName()) || myUserName.equals(mad.getProxyUserName())) && !mad.isChecked() && mad.getCheckStatus() != MyActionDb.CHECK_STATUS_WAITING_TO_DO) {
                        isHandleBtnShow = true;
                    }
                // }
            }
            json.put("isHandleBtnShow", isHandleBtnShow);

            boolean isRemindBtnShow = false;
            boolean canRemind = cfg.getBooleanProperty("flowCanRemind");
            if (canRemind && !isPaperReceived && !mad.isChecked() && !myUserName.equals(mad.getUserName()) && mad.getCheckStatus() != MyActionDb.CHECK_STATUS_PASS && mad.getCheckStatus() != MyActionDb.CHECK_STATUS_WAITING_TO_DO) {
                String action = "action=" + MessageDb.ACTION_FLOW_DISPOSE + "|myActionId=" + mad.getId();
                isRemindBtnShow = true;
                json.put("action", action);
            }
            json.put("isRemindBtnShow", isRemindBtnShow);
            json.put("userName", mad.getUserName());

            aryMyAction.add(json);

            k++;
        }
        return aryMyAction;
    }

    @Override
    public com.alibaba.fastjson.JSONArray listAttachmentByField(int flowId, String fieldName) throws ErrMsgException {
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);
        com.alibaba.fastjson.JSONArray aryAtt = new com.alibaba.fastjson.JSONArray();
        int doc_id = wf.getDocId();
        DocumentMgr dm = new DocumentMgr();
        Document doc = dm.getDocument(doc_id);
        Config cfg = Config.getInstance();
        java.util.Vector<IAttachment> attachments = null;
        if (doc != null) {
            attachments = doc.getAttachments(1);

            UserMgr um = new UserMgr();
            String creatorRealName = "";
            for (IAttachment am : attachments) {
                if (am.getFieldName() != null && !"".equals(am.getFieldName())) {
                    // IOS上传fieldName为upload
                    if (!am.getFieldName().equals(fieldName)) {
                        continue;
                    }
                }

                UserDb creator = um.getUserDb(am.getCreator());
                if (creator.isLoaded()) {
                    creatorRealName = creator.getRealName();
                } else {
                    creatorRealName = creator + "不存在";
                }

                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("id", am.getId());
                json.put("name", am.getName());
                json.put("fieldName", am.getFieldName());
                json.put("creatorRealName", creatorRealName);
                json.put("createDate", DateUtil.format(am.getCreateDate(), "yyyy-MM-dd"));
                json.put("size", NumberUtil.round((double) am.getSize() / 1024000, 2) + "M");
                boolean isToPdf = false;
                if ("true".equals(cfg.get("canConvertToPDF")) && ("doc".equals(StrUtil.getFileExt(am.getName())) || "docx".equals(StrUtil.getFileExt(am.getName())))) {
                    isToPdf = true;
                }
                json.put("isToPdf", isToPdf);
                json.put("diskName", am.getDiskName());
                json.put("visualPath", am.getVisualPath());

                boolean isPreview = false;
                if (cfg.getBooleanProperty("canPdfFilePreview") || cfg.getBooleanProperty("canOfficeFilePreview")) {
                    String s = Global.getRealPath() + am.getVisualPath() + "/" + am.getDiskName();
                    String htmlfile = s.substring(0, s.lastIndexOf(".")) + ".html";
                    java.io.File fileExist = new java.io.File(htmlfile);
                    if (fileExist.exists()) {
                        isPreview = true;
                    }
                }
                boolean isImg = false;
                if (StrUtil.isImage(StrUtil.getFileExt(am.getDiskName()))) {
                    isImg = true;
                }
                json.put("isPreview", isPreview || isImg);
                if (isPreview) {
                    json.put("previewUrl", sysUtil.getRootPath() + "/" + am.getVisualPath() + "/" + am.getDiskName().substring(0, am.getDiskName().lastIndexOf(".")) + ".html");
                } else if (isImg) {
                    json.put("previewUrl", sysUtil.getRootPath() + "/showImg?path=" + am.getVisualPath() + "/" + am.getDiskName());
                } else {
                    json.put("previewUrl", sysUtil.getRootPath() + "/" + am.getVisualPath() + "/" + am.getDiskName());
                }
                aryAtt.add(json);
            }
            return aryAtt;
        } else {
            throw new ErrMsgException("流程文档不存在");
        }
    }

    /**
     * 取得加签的描述
     *
     * @param plusJson
     * @return
     */
    @Override
    public String getPlusDesc(JSONObject plusJson) {
        String plusDesc = "";
        int plusType = plusJson.getIntValue("type");
        int plusMode = plusJson.getIntValue("mode");
        if (plusType == WorkflowActionDb.PLUS_TYPE_BEFORE) {
            plusDesc = "前加签";
        } else if (plusType == WorkflowActionDb.PLUS_TYPE_AFTER) {
            plusDesc = "后加签";
        } else {
            plusDesc = "并签";
        }
        if (plusMode == WorkflowActionDb.PLUS_MODE_ORDER) {
            plusDesc += " 顺序审批";
        } else if (plusMode == WorkflowActionDb.PLUS_MODE_ONE) {
            plusDesc += " 只需其中一人处理";
        } else {
            plusDesc += " 全部审批";
        }

        plusDesc += "(";
        List<String> userNameList = new ArrayList<>();
        String[] plusUsers = StrUtil.split(plusJson.getString("users"), ",");
        if (plusUsers != null) {
            for (String userName : plusUsers) {
                User pUser = userCache.getUser(userName);
                if (pUser != null) {
                    userNameList.add(pUser.getRealName());
                } else {
                    userNameList.add(userName + " 不存在");
                }
            }
        }
        plusDesc += org.apache.commons.lang3.StringUtils.join(userNameList, ",");
        plusDesc += ")";
        return plusDesc;
    }

    /**
     * 抄送给我的记录
     *
     * @param page
     * @param pageSize
     * @return
     */
    @Override
    public ListResult listDistributeToMe(String op, String title, Date fromDate, Date toDate, int page, int pageSize, String field, String order) {
        String userName = authUtil.getUserName();

        boolean isSearch = "search".equals(op);

        if (StrUtil.isEmpty(field)) {
            field = "id";
        }
        String sort = order;
        if (StrUtil.isEmpty(sort)) {
            sort = "desc";
        }

        String myUnitCode = authUtil.getUserUnitCode();
        DeptUserDb dud = new DeptUserDb();
        String[] ary = dud.getUnitsOfUser(userName);

        PaperConfig pc = PaperConfig.getInstance();
        // 取得收文角色
        String swRoles = pc.getProperty("swRoles");
        String[] swAry = StrUtil.split(swRoles, ",");
        UserDb user = new UserDb();
        user = user.getUserDb(userName);
        // 检查用户是否为收文角色
        int swLen = 0;
        if (swAry != null) {
            swLen = swAry.length;
        }
        boolean isWithUnit = false;
        // 如果没有在配置文件中限定收文角色，则允许有“收文处理”权限的用户，收取分发给单位的文件
        if (swLen == 0) {
            isWithUnit = true;
        } else {
            for (int i = 0; i < swLen; i++) {
                if (user.isUserOfRole(swAry[i])) {
                    isWithUnit = true;
                    break;
                }
            }
        }

        String sql = "";
        PaperDistributeDb pdd = new PaperDistributeDb();
        // 如果用户只存在于一个单位（没有兼职单位）
        if (ary.length == 1) {
            if (isWithUnit) {
                if (!isSearch) {
                    sql = "select id from " + pdd.getTable().getName() + " where (to_unit=" + StrUtil.sqlstr(myUnitCode) + " or to_unit=" + StrUtil.sqlstr(userName) + ")";
                } else {
                    sql = "select id from " + pdd.getTable().getName() + " where (to_unit=" + StrUtil.sqlstr(myUnitCode) + " or to_unit=" + StrUtil.sqlstr(userName) + ")";
                    if (!StrUtil.isEmpty(title)) {
                        sql += " and title like " + StrUtil.sqlstr("%" + title + "%");
                    }
                }
            } else {
                if (!isSearch) {
                    sql = "select id from " + pdd.getTable().getName() + " where to_unit=" + StrUtil.sqlstr(userName);
                } else {
                    sql = "select id from " + pdd.getTable().getName() + " where to_unit=" + StrUtil.sqlstr(userName);
                    if (!StrUtil.isEmpty(title)) {
                        sql += " and title like " + StrUtil.sqlstr("%" + title + "%");
                    }
                }
            }
        } else {
            if (isWithUnit) {
                String units = StrUtil.sqlstr(userName); // 加上用户自己
                for (String s : ary) {
                    if ("".equals(units)) {
                        units = StrUtil.sqlstr(s);
                    } else {
                        units += "," + StrUtil.sqlstr(s);
                    }
                }
                units = "(" + units + ")";

                if (!isSearch) {
                    sql = "select id, count(distinct flow) from " + pdd.getTable().getName() + " where to_unit in " + units + " group by flow";
                } else {
                    if (!StrUtil.isEmpty(title)) {
                        sql = "select id, count(distinct flow) from " + pdd.getTable().getName() + " where to_unit in " + units + " and title like " + StrUtil.sqlstr("%" + title + "%") + " group by flow";
                    } else {
                        sql = "select id, count(distinct flow) from " + pdd.getTable().getName() + " where to_unit in " + units + " group by flow";
                    }
                }
            } else {
                if (!isSearch) {
                    sql = "select id from " + pdd.getTable().getName() + " where to_unit=" + StrUtil.sqlstr(userName);
                } else {
                    if (!StrUtil.isEmpty(title)) {
                        sql = "select id from " + pdd.getTable().getName() + " where to_unit=" + StrUtil.sqlstr(userName) + " and title like " + StrUtil.sqlstr("%" + title + "%");
                    } else {
                        sql = "select id from " + pdd.getTable().getName() + " where to_unit=" + StrUtil.sqlstr(userName);
                    }
                }
            }
        }

        if (fromDate != null) {
            sql += " and dis_date>=" + SQLFilter.getDateStr(DateUtil.format(fromDate, "yyyy-MM-dd"), "yyyy-MM-dd");
        }
        if (toDate != null) {
            toDate = DateUtil.addDate(toDate, 1);
            sql += " and dis_date<=" + SQLFilter.getDateStr(DateUtil.format(toDate, "yyyy-MM-dd"), "yyyy-MM-dd");
        }

        sql += " order by " + field + " " + sort;
        DebugUtil.i(getClass(), "listDistributeToMe", sql);
        ListResult lr;
        try {
            lr = pdd.listResult(sql, page, pageSize);
        } catch (ResKeyException e) {
            LogUtil.getLog(getClass()).error(e);
            throw new ErrMsgException(e.getMessage(request));
        }
        return lr;
    }

    /**
     * 抄送给我的记录
     *
     * @param page
     * @param pageSize
     * @return
     */
    @Override
    public ListResult listMyDistribute(String op, String title, String realName, Date fromDate, Date toDate, int page, int pageSize, String field, String order) {
        String userName = authUtil.getUserName();
        String sql = "";

        boolean isSearch = "search".equals(op);

        if (StrUtil.isEmpty(field)) {
            field = "id";
        }
        String sort = order;
        if (StrUtil.isEmpty(sort)) {
            sort = "desc";
        }

        PaperDistributeDb pdd = new PaperDistributeDb();

        if (!isSearch) {
            sql = "select id from " + pdd.getTable().getName() + " where user_name=" + StrUtil.sqlstr(userName);
        } else {
            sql = "select id from " + pdd.getTable().getName() + " where user_name=" + StrUtil.sqlstr(userName);
            if (!StrUtil.isEmpty(realName)) {
                sql = "select p.id from " + pdd.getTable().getName() + " p, users u where p.user_name=" + StrUtil.sqlstr(userName) + " u.name=p.user_name and u.realname like " + StrUtil.sqlstr("%" + realName + "%");
            }
            if (!StrUtil.isEmpty(title)) {
                sql += " and title like " + StrUtil.sqlstr("%" + title + "%");
            }
            if (fromDate != null) {
                sql += " and dis_date>=" + SQLFilter.getDateStr(DateUtil.format(fromDate, "yyyy-MM-dd"), "yyyy-MM-dd");
            }
            if (toDate != null) {
                toDate = DateUtil.addDate(toDate, 1);
                sql += " and dis_date<=" + SQLFilter.getDateStr(DateUtil.format(toDate, "yyyy-MM-dd"), "yyyy-MM-dd");
            }
        }

        sql += " order by " + field + " " + sort;
        DebugUtil.i(getClass(), "listMyDistribute", sql);
        ListResult lr;
        try {
            lr = pdd.listResult(sql, page, pageSize);
        } catch (ResKeyException e) {
            LogUtil.getLog(getClass()).error(e);
            throw new ErrMsgException(e.getMessage(request));
        }
        return lr;
    }
}
