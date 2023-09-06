package com.cloudweb.oa.controller;

import com.cloudweb.oa.service.MyflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/myflow")
public class MyflowController {

    @Autowired
    private MyflowService myflowService;

    @ResponseBody
    @RequestMapping(value = "/generateFlowString", method = RequestMethod.POST, produces={"text/html;charset=UTF-8", "application/json;charset=UTF-8"})
    public String generateFlowString(@RequestParam(value = "flowJson") String flowJson, @RequestParam(value = "serverName") String serverName) {
        return myflowService.generateFlowString(flowJson, serverName).toString();
    }
}
