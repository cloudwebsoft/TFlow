package com.cloudweb.oa.controller;

import cn.js.fan.util.ResKeyException;
import com.alibaba.fastjson.JSONObject;
import com.cloudweb.oa.exception.ValidateException;
import com.cloudweb.oa.security.AuthUtil;
import com.cloudweb.oa.vo.Result;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.flow.FlowConfig;
import com.redmoon.oa.person.UserPhraseDb;
import com.redmoon.oa.ui.LocalUtil;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
public class TipPhraseController {

    @Autowired
    AuthUtil authUtil;

    @Autowired
    HttpServletRequest request;

    @ApiOperation(value = "取得常用语", notes = "取得常用语", httpMethod = "POST")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/tipPhrase/getPhrases", method = RequestMethod.POST, produces = {"application/json;charset=UTF-8;"})
    public Result<Object> getPhrases() throws ValidateException {
        StringBuilder sb = new StringBuilder();
        FlowConfig flowConfig = new FlowConfig();
        List<String> listPhrase = flowConfig.getPhrases();
        for (String phrase : listPhrase) {
            sb.append("<span style=\"cursor:pointer\" onclick=\"insertText(this.innerHTML);\">" + phrase + "</span>&nbsp;&nbsp;&nbsp;");
        }
        UserPhraseDb updTip = new UserPhraseDb();
        for (Object o : updTip.list(updTip.getTable().getSql("listMine"), new Object[]{authUtil.getUserName()})) {
            updTip = (UserPhraseDb) o;
            sb.append("<span style = \"cursor:pointer\" onclick = \"insertText(this.innerHTML); addFrequency('" + updTip.getLong("id") + "')\" >");
            sb.append(updTip.getString("phrase"));
            sb.append("</span>");
            sb.append("<span class=\"tip-phrase-close\" onclick=\"removePhrase(" + updTip.getLong("id") + ")\" >×</span>&nbsp;&nbsp;&nbsp;");
        }
        JSONObject json = new JSONObject();
        json.put("tipHtml", sb.toString());
        return new Result<>(json);
    }

    @ApiOperation(value = "添加常用语", notes = "添加常用语", httpMethod = "POST")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/tipPhrase/add", method = RequestMethod.POST, produces = {"application/json;charset=UTF-8;"})
    public Result<Object> add(@RequestParam(value="phrase", required = true) String phrase) throws ValidateException {
        UserPhraseDb upd = new UserPhraseDb();
        boolean re;
        try {
            re = upd.create(new com.cloudwebsoft.framework.db.JdbcTemplate(), new Object[]{authUtil.getUserName(), phrase, 1, new java.util.Date()});
        } catch (ResKeyException e) {
            LogUtil.getLog(getClass()).error(e);
            return new Result<>(false, e.getMessage(request));
        }
        return new Result<>(re);
    }

    @ApiOperation(value = "添加频次", notes = "添加频次", httpMethod = "POST")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/tipPhrase/addFrequency", method = RequestMethod.POST, produces = {"application/json;charset=UTF-8;"})
    public Result<Object> addFrequency(@RequestParam(value="id", required = true) Long id) throws ValidateException {
        boolean re;
        try {
            UserPhraseDb upd = new UserPhraseDb();
            upd = (UserPhraseDb) upd.getQObjectDb(id);
            upd.set("frequency", upd.getInt("frequency") + 1);
            re = upd.save();
        } catch (ResKeyException e) {
            LogUtil.getLog(getClass()).error(e);
            return new Result<>(false, e.getMessage(request));
        }
        return new Result<>(re);
    }

    @ApiOperation(value = "删除常用语", notes = "删除常用语", httpMethod = "POST")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/tipPhrase/del", method = RequestMethod.POST, produces = {"application/json;charset=UTF-8;"})
    public Result<Object> del(@RequestParam(value="id", required = true) Long id) throws ValidateException {
        boolean re;
        try {
            UserPhraseDb upd = new UserPhraseDb();
            upd = (UserPhraseDb) upd.getQObjectDb(id);
            re = upd.del();
        } catch (ResKeyException e) {
            LogUtil.getLog(getClass()).error(e);
            return new Result<>(false, e.getMessage(request));
        }
        return new Result<>(re);
    }
}
