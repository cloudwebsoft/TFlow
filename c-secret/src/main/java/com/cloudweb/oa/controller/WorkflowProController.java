package com.cloudweb.oa.controller;

import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.ParamUtil;
import cn.js.fan.util.StrUtil;
import com.cloudweb.oa.api.IWorkflowProService;
import com.cloudweb.oa.service.WorkflowService;
import com.cloudweb.oa.utils.I18nUtil;
import com.cloudweb.oa.utils.ResponseUtil;
import com.cloudweb.oa.vo.Result;
import com.redmoon.oa.flow.*;
import com.redmoon.oa.sys.DebugUtil;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@Api(tags = "流程高级模块")
@Slf4j
@RestController
public class WorkflowProController {

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private I18nUtil i18nUtil;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private IWorkflowProService workflowProService;

    @Autowired
    private ResponseUtil responseUtil;

    /**
     * 加签
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/plus", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> plus() {
        long myActionId = ParamUtil.getLong(request, "myActionId", -1);
        DebugUtil.i(getClass(), "plus", "myActionId " + myActionId);
        MyActionDb myActionDb = new MyActionDb();
        myActionDb = myActionDb.getMyActionDb(myActionId);
        DebugUtil.i(getClass(), "plus", "isLoaded " + myActionDb.isLoaded());
        if (myActionDb.getCheckStatus()==MyActionDb.CHECK_STATUS_PASS) {
            return new Result<>(false, i18nUtil.get("nodeByOtherPersonal"));
        }

        WorkflowActionDb wa = new WorkflowActionDb();
        int actionId = (int)myActionDb.getActionId();

        DebugUtil.i(getClass(), "plus", "actionId " + actionId);

        wa = wa.getWorkflowActionDb(actionId);
        if ( wa==null || !wa.isLoaded()) {
            return new Result<>(false, i18nUtil.get("actionNotExist"));
        }
        int flowId = wa.getFlowId();
        WorkflowMgr wfm = new WorkflowMgr();
        WorkflowDb wf = wfm.getWorkflowDb(flowId);

        WorkflowPredefineDb wfp = new WorkflowPredefineDb();
        wfp = wfp.getPredefineFlowOfFree(wf.getTypeCode());

        if (wa.getStatus()== WorkflowActionDb.STATE_DOING || wa.getStatus()== WorkflowActionDb.STATE_RETURN) {
            ;
        } else {
            // 有可能会是重激活的情况
            if (!wfp.isReactive()) {
                String str = i18nUtil.get("processStatus");
                String str1 = i18nUtil.get("mayHaveBeenProcess");
                return new Result<>(false, str + wa.getStatus() + "，" + str1);
            }
        }

        DebugUtil.i(getClass(), "typeCode", wf.getTypeCode());

        // int type = ParamUtil.getInt(request, "type", WorkflowActionDb.PLUS_TYPE_BEFORE);
        boolean re = false;
        String plusDesc = "";
        int type = ParamUtil.getInt(request, "type", WorkflowActionDb.PLUS_TYPE_BEFORE);
        try {
            int mode = ParamUtil.getInt(request, "mode", WorkflowActionDb.PLUS_MODE_ORDER);
            re = workflowProService.addPlus(request, myActionId, type, mode);
            wa = wa.getWorkflowActionDb(actionId);
            if (!StrUtil.isEmpty(wa.getPlus())) {
                com.alibaba.fastjson.JSONObject jsonObject = com.alibaba.fastjson.JSONObject.parseObject(wa.getPlus());
                plusDesc = workflowService.getPlusDesc(jsonObject);
            }
        }
        catch (ErrMsgException e) {
            return new Result<>(false, e.getMessage());
        }

        com.alibaba.fastjson.JSONObject jsonObject = responseUtil.getResultJson(re);
        jsonObject.put("type", type);
        jsonObject.put("plusDesc", plusDesc);
        return new Result<>(jsonObject);
    }

    /**
     * 取消加签
     * @param actionId
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/delPlus", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> delPlus(@RequestParam(value="actionId")Integer actionId) {
        boolean re = false;
        try {
            // 删除加签生成的待办记录
            MyActionDb mad = new MyActionDb();
            mad.delByPlus(actionId);

            WorkflowActionDb wa = new WorkflowActionDb();
            wa = wa.getWorkflowActionDb(actionId);
            wa.setPlus("");
            re = wa.saveOnlyToDb();
        } catch (ErrMsgException e) {
            return new Result<>(false, e.getMessage());
        }
        return new Result<>(re);
    }
}
