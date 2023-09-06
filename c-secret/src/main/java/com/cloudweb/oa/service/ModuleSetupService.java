package com.cloudweb.oa.service;

import cn.js.fan.util.ErrMsgException;
import com.alibaba.fastjson.JSONObject;

import javax.servlet.http.HttpServletRequest;

public interface ModuleSetupService {
    JSONObject add(HttpServletRequest request, String listFieldModule, String listFieldWidthModule, String listFieldLinkModule, String listFieldOrderModule, String listFieldShowModule, String listFieldTitleModule, String listFieldAlignModule) throws ErrMsgException;

    JSONObject modify(HttpServletRequest request, String listFieldModule, String listFieldWidthModule, String listFieldLinkModule, String listFieldOrderModule, String listFieldShowModule, String listFieldTitleModule, String listFieldAlignModule) throws ErrMsgException;

    JSONObject addBtn(HttpServletRequest request, String tName, String tOrder, String tScript, String tBclass, String tRole) throws ErrMsgException;

    JSONObject addCond(HttpServletRequest request, String tName, String tOrder, String tScript, String tBclass, String tRole) throws ErrMsgException;

    JSONObject addBtnBatch(HttpServletRequest request, String tName, String tOrder, String tScript, String tBclass, String tRole) throws ErrMsgException;

    JSONObject addBtnFlow(HttpServletRequest request, String tName, String tOrder, String tScript, String tBclass, String tRole) throws ErrMsgException;

    JSONObject addBtnModule(HttpServletRequest request, String tName, String tOrder, String tScript, String tBclass, String tRole) throws ErrMsgException;

    String addLink(HttpServletRequest request, String tName, String tOrder, String tUrl, String tField, String tCond, String tValue, String tEvent, String tRole, String tColor, String tIcon);

    String modifyLink(HttpServletRequest request, String tName, String tOrder, String tUrl, String tField, String tCond, String tValue, String tEvent, String tRole, String tColor, String tIcon);

    JSONObject modifyBtn(HttpServletRequest request, String tName, String tOrder, String tScript, String tBclass, String tRole) throws ErrMsgException;
}
