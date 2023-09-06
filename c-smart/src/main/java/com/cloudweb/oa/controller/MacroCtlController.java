package com.cloudweb.oa.controller;

import cn.js.fan.db.ResultIterator;
import cn.js.fan.db.ResultRecord;
import cn.js.fan.security.SecurityUtil;
import cn.js.fan.util.DateUtil;
import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cloudweb.oa.api.IModuleFieldSelectCtl;
import com.cloudweb.oa.api.ISQLCtl;
import com.cloudweb.oa.cache.UserCache;
import com.cloudweb.oa.entity.User;
import com.cloudweb.oa.security.AuthUtil;
import com.cloudweb.oa.service.MacroCtlService;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudweb.oa.vo.Result;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.flow.FormDb;
import com.redmoon.oa.flow.FormField;
import com.redmoon.oa.person.UserDb;
import com.redmoon.oa.stamp.StampDb;
import com.redmoon.oa.stamp.StampPriv;
import com.redmoon.oa.visual.FormDAO;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/flow/macro")
public class MacroCtlController {
    @Autowired
    private HttpServletRequest request;

    @Autowired
    UserCache userCache;

    @Autowired
    AuthUtil authUtil;

    /**
     * 当表单域选择宏控件选择时
     *
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/onFieldCtlSelect", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public com.alibaba.fastjson.JSONArray onFieldCtlSelect(@RequestParam(value = "formCode") String formCode, @RequestParam(value = "fieldName") String fieldName) {
        FormDb fd = new FormDb();
        fd = fd.getFormDb(formCode);
        FormField ff = fd.getFormField(fieldName);
        try {
            MacroCtlService macroCtlService = SpringUtil.getBean(MacroCtlService.class);
            IModuleFieldSelectCtl moduleFieldSelectCtl = macroCtlService.getModuleFieldSelectCtl();
            return moduleFieldSelectCtl.getOnSel(request, ff, fd);
        } catch (ErrMsgException | SQLException e) {
            LogUtil.getLog(getClass()).error(e);
        }
        return new com.alibaba.fastjson.JSONArray();
    }

    /**
     * 表单域选择宏控件通过ajax方式获得下拉菜单选项
     *
     * @param formCode
     * @param fieldName
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/getAjaxOptions", method = RequestMethod.GET, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<com.alibaba.fastjson.JSONArray> getAjaxOptions(@RequestParam(value = "formCode") String formCode, @RequestParam(value = "fieldName") String fieldName) {
        FormDb fd = new FormDb();
        fd = fd.getFormDb(formCode);
        FormField ff = fd.getFormField(fieldName);

        try {
            MacroCtlService macroCtlService = SpringUtil.getBean(MacroCtlService.class);
            IModuleFieldSelectCtl moduleFieldSelectCtl = macroCtlService.getModuleFieldSelectCtl();
            return new Result<>(moduleFieldSelectCtl.getAjaxOpts(request, ff));
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
        }
        return new Result<>(new com.alibaba.fastjson.JSONArray());
    }

    /**
     * 删除嵌套表格中的记录
     *
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/delNestTableRows", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String delNestTableRows(@RequestParam(value="ids", required = true) String ids, @RequestParam(value="formCode", required = true) String formCode) {
        String[] ary = StrUtil.split(ids, ",");

        FormDb fd = new FormDb();
        fd = fd.getFormDb(formCode);
        FormDAO fdao = new FormDAO();

        for (String strId : ary) {
            long id = StrUtil.toLong(strId, -1);
            if (id == -1) {
                JSONObject json = new JSONObject();
                json.put("ret", 0);
                json.put("msg", "ids:" + ids + " 参数错误");
                return json.toString();
            }
            fdao = fdao.getFormDAO(id, fd);
            fdao.del();
        }

        JSONObject json = new JSONObject();
        json.put("ret", 1);
        json.put("msg", "操作成功");
        return json.toString();
    }

    /**
     * 取得SQL宏控件生成的下拉菜单的选项
     *
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/getSqlCtlOptions", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String getSqlCtlOptions(@RequestParam(value="id", required = true) long id, @RequestParam(value="formCode", required = true) String formCode, @RequestParam(value="fieldName", required = true) String fieldName) {
        com.alibaba.fastjson.JSONArray arr = new com.alibaba.fastjson.JSONArray();
        FormDb fd = new FormDb();
        fd = fd.getFormDb(formCode);
        FormDAO fdao = new FormDAO();
        fdao = fdao.getFormDAO(id, fd);

        FormField ff = fdao.getFormField(fieldName);
        MacroCtlService macroCtlService = SpringUtil.getBean(MacroCtlService.class);
        ISQLCtl sqlCtl = macroCtlService.getSQLCtl();
        ResultIterator ri = sqlCtl.getResultByDAO(fdao, ff);
        if (ri != null) {
            while (ri.hasNext()) {
                ResultRecord rr = ri.next();
                JSONObject json = new JSONObject();
                if (rr.getRow().size() == 1) { // 只有一列
                    String v = StrUtil.getNullStr(rr.getString(1));
                    json.put("value", v);
                    json.put("name", v);
                } else {
                    json.put("value", rr.getString(1));
                    json.put("name", rr.getString(2));
                }
                arr.add(json);
            }
        }

        JSONObject json = new JSONObject();
        json.put("ret", 1);
        json.put("msg", "操作成功");
        json.put("result", arr);
        return json.toString();
    }

    @ApiOperation(value = "验证密码，用于签名宏控件", notes = "验证密码，用于签名宏控件", httpMethod = "POST")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/checkPwd", method = RequestMethod.POST, produces = {"application/json;charset=UTF-8;"})
    public Result<Object> checkPwd(@RequestParam(value="pwd") String pwd) {
        User user = userCache.getUser(authUtil.getUserName());
        boolean re = false;
        try {
            re = user.getPwd().equals(SecurityUtil.MD5(pwd));
        } catch (Exception e) {
            LogUtil.getLog(getClass()).error(e);
        }
        JSONObject json = new JSONObject();
        json.put("res", re ? 0 : 1);
        if (re) {
            json.put("realName", user.getRealName());
            json.put("date", DateUtil.format(new java.util.Date(), "yyyy年MM月dd日"));
        }
        return new Result<>(json);
    }
}
