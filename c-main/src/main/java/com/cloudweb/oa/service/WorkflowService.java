package com.cloudweb.oa.service;

import cn.js.fan.db.ListResult;
import cn.js.fan.util.ErrMsgException;
import com.alibaba.fastjson.JSONObject;
import com.redmoon.oa.flow.*;
import com.redmoon.oa.pvg.Privilege;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.ui.LocalUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.Vector;

public interface WorkflowService {

    JSONObject finishAction(HttpServletRequest request, Privilege privilege) throws ErrMsgException;

    JSONObject finishActionByMobile(HttpServletRequest request, Privilege privilege) throws ErrMsgException;

    JSONObject finishActionFree(HttpServletRequest request, Privilege privilege) throws ErrMsgException;

    JSONObject finishActionFreeByMobile(HttpServletRequest request, Privilege privilege) throws ErrMsgException;

    JSONObject createNestSheetRelated(HttpServletRequest request) throws ErrMsgException;

    JSONObject updateNestSheetRelated(HttpServletRequest request) throws ErrMsgException;

    String runFinishScript(HttpServletRequest request, int flowId, int actionId) throws ErrMsgException;

    com.alibaba.fastjson.JSONObject matchBranchAndUser(HttpServletRequest request, long myActionId, int actionId) throws ErrMsgException;

    com.alibaba.fastjson.JSONArray getToolbarButtons(WorkflowPredefineDb wpd, Leaf lf, WorkflowDb wf, MyActionDb mad, WorkflowActionDb wa, Vector vreturn, String flag, String myname, boolean isBtnSaveShow, boolean isActionKindRead, boolean canUserSeeDesignerWhenDispose, boolean canUserSeeFlowChart);

    com.alibaba.fastjson.JSONArray getToolbarBottonsFree(WorkflowPredefineDb wpd, Leaf lf, WorkflowDb wf, MyActionDb mad, WorkflowActionDb wa, Vector vreturn, String myname);

    com.alibaba.fastjson.JSONArray listProcess(int flowId);

    String getPlusDesc(JSONObject plusJson);

    com.alibaba.fastjson.JSONArray listProcessForShow(WorkflowDb wf, String myUserName, boolean isPaperReceived, boolean isRecall, boolean isFlowManager, boolean isReactive);

    com.alibaba.fastjson.JSONArray listAttachmentByField(int flowId, String fieldName) throws ErrMsgException;

    ListResult listDistributeToMe(String op, String title, Date fromDate, Date toDate, int page, int pageSize, String field, String order);

    ListResult listMyDistribute(String op, String title, String realName, Date fromDate, Date toDate, int page, int pageSize, String field, String order);
}
