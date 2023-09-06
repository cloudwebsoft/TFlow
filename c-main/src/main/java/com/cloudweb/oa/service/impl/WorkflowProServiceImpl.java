package com.cloudweb.oa.service.impl;

import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.ParamUtil;
import cn.js.fan.util.StrUtil;
import com.cloudweb.oa.api.IWorkflowProService;
import com.cloudweb.oa.security.AuthUtil;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.Config;
import com.redmoon.oa.flow.*;
import com.redmoon.oa.person.UserDb;
import com.redmoon.oa.pvg.Privilege;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.ui.LocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Vector;

@Service
public class WorkflowProServiceImpl implements IWorkflowProService {

    @Autowired
    AuthUtil authUtil;

    /**
     * 加签
     * 加签MyActionDb中的privMyActionId记录的始终为节点上的原始处理人员
     * @param request HttpServletRequest
     * @return boolean
     * @throws ErrMsgException
     */
    @Override
    public boolean addPlus(HttpServletRequest request, long myActionId, int type, int mode) throws ErrMsgException {
        // int actionId = ParamUtil.getInt(request, "actionId");
        // String users = request.getParameter("users"); // 解决ajax中文问题
        String users = ParamUtil.get(request, "users");

        Privilege pvg = new Privilege();
        String curUser = pvg.getUser(request);
        String[] ary = StrUtil.split(users, ",");
        if (ary == null) {
            throw new ErrMsgException("请选择人员");
        }

        users = "";
        for (String user : ary) {
            if (user.equals(curUser)) {
                continue;
            }
            users += (users.equals("") ? "" : ",") + user;
        }
        if ("".equals(users)) {
            throw new ErrMsgException("请选择加签人员！");
        }

        String cwsWorkflowResult = ParamUtil.get(request, "cwsWorkflowResult");
        cn.js.fan.mail.SendMail sendmail = WorkflowDb.getSendMail();
        MyActionDb mad = new MyActionDb();
        mad = mad.getMyActionDb(myActionId);
        WorkflowActionDb wa = new WorkflowActionDb();
        wa = wa.getWorkflowActionDb((int)mad.getActionId());
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        json.put("type", type);
        json.put("mode", mode);
        json.put("users", users);
        json.put("from", curUser);
        json.put("internal", wa.getInternalName());
        wa.setPlus(json.toString());
        boolean re = wa.saveOnlyToDb();
        if (re) {
            ary = StrUtil.split(users, ",");
            if (ary!=null) {
                if (!"".equals(cwsWorkflowResult)) {
                    mad.setResult(cwsWorkflowResult);
                    mad.save();
                }

                WorkflowDb wf = new WorkflowDb();
                wf = wf.getWorkflowDb((int)mad.getFlowId());

                long privMyActionId = mad.getPrivMyActionId();
                MyActionDb privMyActionDb = mad.getMyActionDb(privMyActionId);
                WorkflowActionDb privAction = wa.getWorkflowActionDb((int)privMyActionDb.getActionId());

                // 前加签
                if (type == WorkflowActionDb.PLUS_TYPE_BEFORE) {
                    // 如果为前加签，则本myActionId应该被置为已完成
                    wa.changeMyActionDb(mad, pvg.getUser(request));
                    // 如果为顺序加签，则先发给第一个人待办通知
                    if (mode == WorkflowActionDb.PLUS_MODE_ORDER) {
                        MyActionDb plusmad = wf.notifyUser(ary[0], new java.util.Date(), mad.getId(), privAction, wa,
                                WorkflowActionDb.STATE_PLUS, wa.getFlowId());
                        wf.sendNotifyMsgAndEmail(request, plusmad, sendmail);
                    } else { // 否则发给全部加签人员
                        for (String userName : ary) {
                            MyActionDb plusmad = wf.notifyUser(userName, new java.util.Date(), mad.getId(), privAction, wa,
                                    WorkflowActionDb.STATE_PLUS, wa.getFlowId());
                            wf.sendNotifyMsgAndEmail(request, plusmad, sendmail);
                        }
                    }
                }
                else if (type == WorkflowActionDb.PLUS_TYPE_CONCURRENT) { // 并签，给每个加签人员发待办通知
                    for (String userName : ary) {
                        MyActionDb plusmad = wf.notifyUser(userName, new java.util.Date(), mad.getId(), privAction, wa,
                                WorkflowActionDb.STATE_PLUS, wa.getFlowId());
                        wf.sendNotifyMsgAndEmail(request, plusmad, sendmail);
                    }
                }
            }
        }
        return re;
    }

    /**
     * 批量同意流程
     * @param request
     * @throws ErrMsgException
     */
    @Override
    public int FinishActionBatch(HttpServletRequest request, String ids) throws ErrMsgException {
        Privilege pvg = new Privilege();
        String userName = pvg.getUser(request);
        String[] idsAry = StrUtil.split(ids,",");

        if (ids==null) {
            return 0;
        }

        int c = 0;
        StringBuffer sb = new StringBuffer();

        MyActionDb mad = new MyActionDb();
        WorkflowActionDb wa = new WorkflowActionDb();
        WorkflowPredefineDb wfp = new WorkflowPredefineDb();

        WorkflowDb wfd = new WorkflowDb();
        int len = idsAry.length;
        for (int i=0; i<len; i++) {
            // 检查能否提交
            mad = mad.getMyActionDb(StrUtil.toLong(idsAry[i]));

            int flowId = (int)mad.getFlowId();
            WorkflowDb wf = wfd.getWorkflowDb(flowId);

            int actionId = (int) mad.getActionId();
            wa = wa.getWorkflowActionDb(actionId);

            wfp = wfp.getPredefineFlowOfFree(wf.getTypeCode());

            try {
                WorkflowMgr.canSubmit(request, wf, wa, mad, userName, wfp);
            }
            catch (ErrMsgException e) {
                throw new ErrMsgException(wf.getTitle() + "，\r\n" + e.getMessage());
            }
        }

        for (int i=0; i<len; i++) {
            mad = mad.getMyActionDb(StrUtil.toLong(idsAry[i]));
            finishActionSingle(request, mad, userName, sb);
            c++;
        }

        if (sb.length()>0) {
            throw new ErrMsgException(sb.toString());
        }

        return c;
    }

    /**
     * 从FinishActionBatch中分离，因为在任务管理中需自动提交流程给受命人
     * @Description:
     * @param request
     * @param mad 提交人的代办记录
     * @param userName 提交人
     * @param sb 用于收集出错信息
     * @return
     * @throws ErrMsgException
     */
    @Override
    public boolean finishActionSingle(HttpServletRequest request, MyActionDb mad, String userName, StringBuffer sb) throws ErrMsgException {
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb((int) mad.getFlowId());
        WorkflowMgr wm = new WorkflowMgr();

        if (!wf.isStarted()) {
            //errMsg += wf.getTitle() + " 尚未开始，不能批量提交！\r\n";
            String str = LocalUtil.LoadString(request, "res.flow.Flow", "canNotSubmitBatchForNotStarted"); //%s 尚未开始，%s不能批量提交！\r\n
            sb.append( StrUtil.format(str, new Object[]{wf.getTitle()}));
            return false;
        }

        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());
        if (lf==null) {
            return false;
        }

        WorkflowActionDb wa = new WorkflowActionDb();
        wa = wa.getWorkflowActionDb((int)mad.getActionId());

        String reason = "";
        String result = "";
        int resultValue = 0; // WorkflowActionDb.RESULT_VALUE_AGGREE;

        if (lf.getType()==Leaf.TYPE_FREE) {
            WorkflowPredefineDb wfpd = new WorkflowPredefineDb();
            wfpd = wfpd.getPredefineFlowOfFree(wf.getTypeCode());
            // @流程
            if (wfpd.isLight()) {
                try {
                    wa.changeStatusFree(request, wf, userName, WorkflowActionDb.STATE_FINISHED, reason, result, resultValue, mad.getId());
                }
                catch(ErrMsgException e) {
                    sb.append( wf.getTitle() + "，" + e.getMessage() + "\r\n" );
                    return false;
                }

                java.util.Date date = new java.util.Date();
                if (!mad.isReaded()) {
                    mad.setReaded(true);
                    mad.setReadDate(date);
                }
                mad.setChecked(true);
                mad.setCheckDate(date);
                mad.setResultValue(WorkflowActionDb.RESULT_VALUE_AGGREE);
                mad.save();
            } else {
                String str = LocalUtil.LoadString(request, "res.flow.Flow", "notSubmitForFreeFlow"); //%s 尚未开始，%s不能批量提交！\r\n
                sb.append( StrUtil.format(str, new Object[]{wf.getTitle()}) + "\r\n");
                //errMsg += wf.getTitle() + " 是自由流程，不能批量提交！\r\n";
                return false;
            }
        } else {
            Vector<WorkflowActionDb> v = wa.getLinkToActions();
        	/*if (v.size() >= 2) {
        		String str = LocalUtil.LoadString(request, "res.flow.Flow", "distributionFlow"); //%s 分支流程，%s不能批量提交！\r\n
            	sb.append(StrUtil.format(str, new Object[]{wf.getTitle()}) + "\r\n");
            	return false;
        	}*/

            // 20230205 使支持匹配分支
            if (v.size() >= 2) {
                Vector<WorkflowLinkDb> vMatched = WorkflowRouter.matchNextBranch(wa, wa.getUserName(), new StringBuffer(), mad.getId());
                if (vMatched.size() > 0) {
                    WorkflowMgr wfm = new WorkflowMgr();
                    for (WorkflowLinkDb wld : vMatched) {
                        // 置分支线
                        wfm.setXorRadiateNextBranch(wa, wld.getTo());

                        WorkflowActionDb toBranchAct = wld.getToAction();
                        LogUtil.getLog(getClass()).info("finishActionSingle: toBranchAct=" + toBranchAct.getTitle());

                        // 匹配节点上的用户
                        Vector<UserDb> vt = null;
                        try {
                            WorkflowRouter workflowRouter = new WorkflowRouter();
                            vt = workflowRouter.matchActionUser(request, toBranchAct, wa, false, "");
                        } catch (MatchUserException e) {
                            LogUtil.getLog(getClass()).error(e);
                            throw new ErrMsgException(e.getMessage());
                        }
                    }
                } else {
                    throw new ErrMsgException("未匹配到分支");
                }
            } else if (v.size() == 1) {
                // 匹配节点上的用户
                Vector<UserDb> vt = null;
                try {
                    WorkflowRouter workflowRouter = new WorkflowRouter();
                    vt = workflowRouter.matchActionUser(request, v.elementAt(0), wa, false, "");
                } catch (MatchUserException e) {
                    LogUtil.getLog(getClass()).error(e);
                    throw new ErrMsgException(e.getMessage());
                }
            }

            Config cfg = Config.getInstance();
            long tDebug = System.currentTimeMillis();
            try {
                boolean re = wa.changeStatus(request, wf, userName, WorkflowActionDb.STATE_FINISHED, reason, result, resultValue, mad.getId());
                if (re) {
                    if (cfg.getBooleanProperty("isDebugFlow")) {
                        DebugUtil.i(getClass(), "finishAction", "before onFinishAction: " + (System.currentTimeMillis() - tDebug) + " ms " + new Privilege().getUser(request));
                    }
                    wm.onFinishAction(request, wf, wa, lf, mad, System.currentTimeMillis());
                    if (cfg.getBooleanProperty("isDebugFlow")) {
                        DebugUtil.i(getClass(), "finishAction", "after onFinishAction: " + (System.currentTimeMillis() - tDebug) + " ms " + new Privilege().getUser(request));
                    }
                }
            }
            catch(ErrMsgException e) {
                sb.append( wf.getTitle() + "，" + e.getMessage() + "\r\n" );
                LogUtil.getLog(getClass()).error(e);
                // 20161229 fgf 如果批处理有问题返回false的话，则该myaction的处理状态为：已处理()，
                // 而正常应为：已处理（同意），所以此处不应返回，以置为RESULT_VALUE_AGGREE
                // 还是保留原样吧，要不然出现此类反馈的时候，会导致无法定位这个问题，另外，把批处理置为默认不启用
                return false;
            }

            if (cfg.getBooleanProperty("isDebugFlow")) {
                DebugUtil.i(getClass(), "finishAction", "after onFinishAction: " + (System.currentTimeMillis() - tDebug) + " ms " + new Privilege().getUser(request));
            }

            // 虽然在wa.changeStatus中调用了WorkflowActionDb.changeMyActionDb对由id获取mad进行了setChecked(true)操作（但未置为RESULT_VALUE_AGGREE）
            // 但是为避免因参数为MyActionDb对象从而因缓存导致的脏数据问题，且为了保存RESULT_VALUE_AGGREE，此处再save一下
            mad.setChecked(true);
            mad.setCheckDate(new java.util.Date());
            mad.setResultValue(WorkflowActionDb.RESULT_VALUE_AGGREE);
            mad.setChecker(authUtil.getUserName());
            mad.save();
        }
        // 自动存档
        if (wa != null && wa.isLoaded()) {
            wm.autoSaveArchive(request, wf, wa);
        }

        return true;
    }
}
