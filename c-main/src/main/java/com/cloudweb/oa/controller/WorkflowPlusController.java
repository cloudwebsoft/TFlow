package com.cloudweb.oa.controller;

import com.cloudweb.oa.api.IWorkflowPlusService;
import com.cloudweb.oa.api.IWorkflowProService;
import com.cloudweb.oa.security.AuthUtil;
import com.cloudweb.oa.service.WorkflowService;
import com.cloudweb.oa.utils.I18nUtil;
import com.cloudweb.oa.utils.ResponseUtil;
import com.cloudweb.oa.vo.Result;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@Api(tags = "流程加强版模块")
@Slf4j
@RestController
public class WorkflowPlusController {
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

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private IWorkflowPlusService workflowPlusService;

    /**
     * 回滚
     * @param actionId
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/rollBack", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> rollBack(@RequestParam(value="actionId")Integer actionId, Integer isRollBackData) {
        return new Result<>(workflowPlusService.rollBack(actionId, isRollBackData == 1));
    }
}
