package com.cloudweb.oa.service.impl;

import cn.js.fan.util.DateUtil;
import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.StrUtil;
import com.cloudweb.oa.api.IWorkflowPlusService;
import com.cloudweb.oa.security.AuthUtil;
import com.cloudweb.oa.vo.Result;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.flow.*;
import com.redmoon.oa.flow.macroctl.MacroCtlMgr;
import com.redmoon.oa.flow.macroctl.MacroCtlUnit;
import com.redmoon.oa.visual.FormDAOLog;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@Service
public class WorkflowPlusServiceImpl implements IWorkflowPlusService {

    @Autowired
    AuthUtil authUtil;

    @Autowired
    HttpServletRequest request;

    @Override
    @Transactional(rollbackFor = {Exception.class, RuntimeException.class})
    public boolean rollBack(Integer actionId, boolean isRollBackData) throws ErrMsgException {
        WorkflowActionDb wad = new WorkflowActionDb();
        wad = wad.getWorkflowActionDb(actionId);
        if (wad.getStatus() == WorkflowActionDb.STATE_DOING || wad.getStatus() == WorkflowActionDb.STATE_RETURN) {
            throw new ErrMsgException("节点正在处理中，无法回滚");
        }

        // 将待办记录忽略掉
        MyActionDb mad = new MyActionDb();
        Map<Integer, WorkflowActionDb> map = new HashMap<>();
        Vector<MyActionDb> v = mad.getMyActionDbDoingOfFlow(wad.getFlowId());
        for (MyActionDb myActionDb : v) {
            WorkflowActionDb waDoing = wad.getWorkflowActionDb((int) myActionDb.getActionId());
            if (!map.containsKey(waDoing.getId())) {
                map.put(waDoing.getId(), waDoing);
                waDoing.setStatus(WorkflowActionDb.STATE_NOTDO);
                waDoing.save();
            }

            myActionDb.setCheckStatus(MyActionDb.CHECK_STATUS_PASS_BY_RETURN);
            myActionDb.setCheckDate(new java.util.Date());
            myActionDb.setChecked(true);
            myActionDb.setChecker(authUtil.getUserName());
            myActionDb.save();
        }

        // 将actionId节点的待办记录变为待处理状态
        Date receiveDate = null;
        WorkflowMgr wm = new WorkflowMgr();
        List<MyActionDb> list = mad.listByAction(actionId);
        for (MyActionDb myActionDb : list) {
            receiveDate = myActionDb.getReceiveDate();
            wm.setMyActionStatus(request, myActionDb.getId(), MyActionDb.CHECK_STATUS_NOT);
        }

        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(wad.getFlowId());
        wf.setIntervenor(authUtil.getUserName());
        wf.setInterveneTime(new Date());

        boolean re = wf.save();
        if (isRollBackData) {
            Leaf lf = new Leaf();
            lf = lf.getLeaf(wf.getTypeCode());
            FormDb fd = new FormDb();
            fd = fd.getFormDb(lf.getFormCode());
            if (fd.isLog()) {
                FormDAO fdao = new FormDAO();
                fdao = fdao.getFormDAO(wf.getId(), fd);

                // 取得receiveDate之前的第一条历史记录进行恢复
                // 恢复主表
                FormDAOLog formDAOLog = new FormDAOLog(fd);
                FormDAOLog daoLog = formDAOLog.getLastBeforeReceiveDate(wf.getId(), receiveDate);
                if (daoLog != null) {
                    Vector<FormField> vLog = daoLog.getFields();
                    for (FormField ff : vLog) {
                        fdao.setFieldValue(ff.getName(), ff.getValue());
                    }
                    fdao.save();
                } else {
                    LogUtil.getLog(getClass()).error("表单: " + fd.getName() + " 的历史记录早于到达时间: " + DateUtil.format(receiveDate, "yyyy-MM-dd HH:mm:ss") + " 的不存在");
                }

                // 恢复嵌套表
                MacroCtlMgr mm = new MacroCtlMgr();
                Vector<FormField> vt = fd.getFields();
                for (FormField ff : vt) {
                    if (ff.getType().equals(FormField.TYPE_MACRO)) {
                        MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                        if (mu.getNestType() != MacroCtlUnit.NEST_TYPE_NONE) {
                            String nestFormCode = ff.getDescription();
                            try {
                                String defaultVal = StrUtil.decodeJSON(nestFormCode);
                                JSONObject json = new JSONObject(defaultVal);
                                nestFormCode = json.getString("destForm");
                            } catch (JSONException e) {
                                LogUtil.getLog(getClass()).info(fd.getName() + ": " + ff.getName() + " " + nestFormCode + " is old version before 20131123. ff.getDefaultValueRaw()=" + ff.getDefaultValueRaw());
                            }

                            FormDb fdNest = new FormDb();
                            fdNest = fdNest.getFormDb(nestFormCode);
                            FormDAOLog formDAOLogNest = new FormDAOLog(fdNest);

                            List<com.redmoon.oa.visual.FormDAO> nestList = fdao.listNest(nestFormCode);
                            for (com.redmoon.oa.visual.FormDAO daoNest : nestList) {
                                FormDAOLog daoLogNest = formDAOLogNest.getLastBeforeReceiveDate(wf.getId(), receiveDate);
                                if (daoLogNest != null) {
                                    Vector<FormField> vLogNest = daoLogNest.getFields();
                                    for (FormField ffNestLog : vLogNest) {
                                        daoNest.setFieldValue(ffNestLog.getName(), ffNestLog.getValue());
                                    }
                                    daoNest.save();
                                } else {
                                    LogUtil.getLog(getClass()).error("嵌套表单: " + fdNest.getName() + " 的历史记录早于到达时间: " + DateUtil.format(receiveDate, "yyyy-MM-dd HH:mm:ss") + " 的不存在");
                                }
                            }
                        }
                    }
                }
            } else {
                LogUtil.getLog(getClass()).error("表单: " + fd.getName() + " 的历史记录未保留");
            }
        }

        return re;
    }
}
