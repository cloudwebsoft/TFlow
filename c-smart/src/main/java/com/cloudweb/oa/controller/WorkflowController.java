package com.cloudweb.oa.controller;

import cn.js.fan.db.ListResult;
import cn.js.fan.db.ResultIterator;
import cn.js.fan.db.ResultRecord;
import cn.js.fan.mail.SendMail;
import cn.js.fan.util.*;
import cn.js.fan.web.Global;
import cn.js.fan.web.SkinUtil;
import com.alibaba.fastjson.JSON;
import com.cloudweb.oa.api.IFlowRender;
import com.cloudweb.oa.api.IMyflowUtil;
import com.cloudweb.oa.api.IWorkflowProService;
import com.cloudweb.oa.cache.DepartmentCache;
import com.cloudweb.oa.cache.FlowFormDaoCache;
import com.cloudweb.oa.cache.UserCache;
import com.cloudweb.oa.entity.User;
import com.cloudweb.oa.exception.ValidateException;
import com.cloudweb.oa.security.AuthUtil;
import com.cloudweb.oa.service.IFileService;
import com.cloudweb.oa.service.WorkflowService;
import com.cloudweb.oa.utils.*;
import com.cloudweb.oa.vo.CommonConstant;
import com.cloudweb.oa.vo.Result;
import com.cloudwebsoft.framework.aop.ProxyFactory;
import com.cloudwebsoft.framework.db.JdbcTemplate;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.kit.util.FileUpload;
import com.redmoon.oa.Config;
import com.redmoon.oa.android.Privilege;
import com.redmoon.oa.base.IAttachment;
import com.redmoon.oa.base.IFormDAO;
import com.redmoon.oa.base.IFormMacroCtl;
import com.redmoon.oa.db.SequenceManager;
import com.redmoon.oa.dept.DeptDb;
import com.redmoon.oa.dept.DeptMgr;
import com.redmoon.oa.flow.*;
import com.redmoon.oa.flow.Attachment;
import com.redmoon.oa.flow.AttachmentLogDb;
import com.redmoon.oa.flow.FormDAO;
import com.redmoon.oa.flow.FormDAOMgr;
import com.redmoon.oa.flow.Render;
import com.redmoon.oa.flow.macroctl.MacroCtlMgr;
import com.redmoon.oa.flow.macroctl.MacroCtlUnit;
import com.redmoon.oa.kernel.License;
import com.redmoon.oa.message.IMessage;
import com.redmoon.oa.message.MessageDb;
import com.redmoon.oa.oacalendar.OACalendarDb;
import com.redmoon.oa.person.PlanDb;
import com.redmoon.oa.person.UserDb;
import com.redmoon.oa.person.UserMgr;
import com.redmoon.oa.person.UserSetupDb;
import com.redmoon.oa.pvg.RoleDb;
import com.redmoon.oa.security.SecurityUtil;
import com.redmoon.oa.shell.BSHShell;
import com.redmoon.oa.sms.SMSFactory;
import com.redmoon.oa.stamp.StampDb;
import com.redmoon.oa.stamp.StampPriv;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.ui.LocalUtil;
import com.redmoon.oa.ui.SkinMgr;
import com.redmoon.oa.util.RequestUtil;
import com.redmoon.oa.visual.*;
import com.redmoon.oa.visual.FormUtil;
import com.redmoon.weixin.mgr.FlowDoMgr;
import io.swagger.annotations.*;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.mail.internet.MimeUtility;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Api(tags = "流程模块")
@Slf4j
@Controller
public class WorkflowController {
    @Autowired
    private HttpServletRequest request;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ThreadContext threadContext;

    @Autowired
    private ResponseUtil responseUtil;

    @Autowired
    private I18nUtil i18nUtil;

    @Autowired
    private IFileService fileService;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    UserCache userCache;

    @Autowired
    SysUtil sysUtil;

    @Autowired
    DepartmentCache departmentCache;

    @Autowired
    ConfigUtil configUtil;

    @Autowired
    IWorkflowProService workflowProService;

    @Autowired
    FlowFormDaoCache flowFormDaoCache;

    @Autowired
    IFlowRender flowRender;

    @ResponseBody
    @RequestMapping(value = "/public/flow/addReply", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;"})
    public String addReply(@RequestParam(value = "skey", required = false) String skey) {
        Privilege pvg = new Privilege();
        boolean re = pvg.auth(request);
        JSONObject json = new JSONObject();
        if (!re) {
            json.put("ret", "0");
            json.put("msg", "权限非法！");
            return json.toString();
        }

        String myActionId = ParamUtil.get(request, "myActionId");//当前活跃的标志id
        int flowId = ParamUtil.getInt(request, "flowId", -1);//当前流程id
        long actionId = ParamUtil.getLong(request, "actionId", -1);//当前流程action的id
        String content = request.getParameter("content");//“评论”的内容
        int parentId = ParamUtil.getInt(request, "parentId", -1);

        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);

        String replyName = wf.getUserName(); // 被回复的用户
        UserMgr um = new UserMgr();
        UserDb ud = um.getUserDb(pvg.getUserName());

        String partakeUsers = "";
        int isSecret = ParamUtil.getInt(request, "isSecret", 0);//此“评论”是否隐藏
        // 将数据插入flow_annex附言表中
        long annexId = SequenceManager.nextID(SequenceManager.OA_FLOW_ANNEX);

        int progress = ParamUtil.getInt(request, "progress", 0);

        // id,flow_id,content,user_name,reply_name,add_date,action_id,is_secret,parent_id,progress
        WorkflowAnnexDb wad = new WorkflowAnnexDb();
        JdbcTemplate jt = new JdbcTemplate();
        wad.create(jt, new Object[]{annexId, flowId, content, pvg.getUserName(), replyName, new java.util.Date(), actionId, isSecret, parentId, progress});

        // 不管来源于“代办流程”还是“我的流程”，跳转之后都进入“我的流程”。如果这条回复是私密的，只给交流双方发送消息提醒，不然就给这条流程的每个人都发送一条消息提醒
        // 写入进度
        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());
        String formCode = lf.getFormCode();
        FormDb fd = new FormDb();
        fd = fd.getFormDb(formCode);

        try {
            // 进度为0的时候不更新
            if (fd.isProgress() && progress > 0) {
                com.redmoon.oa.flow.FormDAO fdao = new com.redmoon.oa.flow.FormDAO();
                fdao = fdao.getFormDAOByCache(flowId, fd);
                fdao.setCwsProgress(progress);
                fdao.save();
            }

            MessageDb md = new MessageDb();
            String myAction = "action=" + MessageDb.ACTION_FLOW_SHOW + "|flowId=" + flowId;
            MyActionDb mad = new MyActionDb();
            if (!"".equals(myActionId)) {
                mad = mad.getMyActionDb(Long.parseLong(myActionId));
            }

            if (isSecret == 1) { // 如果是隐藏“评论”，只提醒发起“意见”的人
                if (!replyName.equals(pvg.getUserName())) {// 如果发起“意见”的人不是自己，就提醒
                    if (!"".equals(myActionId)) {
                        md.sendSysMsg(replyName, "请注意查看我的流程：" + wf.getTitle(), ud.getRealName() + "对意见：" + mad.getResult() + "发表了评论：<p>" + content + "</p>", myAction);
                    } else {
                        md.sendSysMsg(replyName, "请注意查看我的流程：" + wf.getTitle(), ud.getRealName() + "发表了评论：<p>" + content + "</p>", myAction);
                    }
                }
            } else {
                // 如果不是隐藏“评论”，提醒所有参与流程的人
                // 解析得到参与流程的所有人
                String allUserListSql = "select distinct user_name from flow_my_action where flow_id=" + flowId + " order by receive_date asc";
                ResultIterator ri1 = jt.executeQuery(allUserListSql);
                ResultRecord rr1 = null;
                while (ri1.hasNext()) {
                    rr1 = ri1.next();
                    partakeUsers += rr1.getString(1) + ",";
                }
                if (!partakeUsers.equals("")) {
                    partakeUsers = partakeUsers.substring(0, partakeUsers.length() - 1);
                }
                String[] partakeUsersArr = StrUtil.split(partakeUsers, ",");
                for (String user : partakeUsersArr) {
                    // 如果不是自己就提醒
                    if (!user.equals(pvg.getUserName())) {
                        if (!myActionId.equals("")) {
                            md.sendSysMsg(user, "请注意查看我的流程：" + wf.getTitle(), ud.getRealName() + "对意见：" + mad.getResult() + "发表了评论：<p>" + content + "</p>", myAction);
                        } else {
                            md.sendSysMsg(user, "请注意查看我的流程：" + wf.getTitle(), ud.getRealName() + "发表了评论：<p>" + content + "</p>", myAction);
                        }
                    }
                }
            }
        } catch (ErrMsgException | SQLException e) {
            LogUtil.getLog(getClass()).error(e);
            json.put("ret", "0");
            json.put("msg", e.getMessage());
            return json.toString();
        }

        if (re) {
            json.put("ret", "1");
            json.put("msg", "操作成功！");
        } else {
            json.put("ret", "0");
            json.put("msg", "操作失败！");
        }
        return json.toString();
    }

    @ApiOperation(value = "保存列表设置", notes = "保存列表设置", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "typeCode", value = "流程类型", required = true, dataType = "String"),
            @ApiImplicitParam(name = "fields", value = "列表中的字段，以逗号分隔", required = false, dataType = "Integer"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/saveColProps", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;"})
    public Result<Object> saveColProps(@RequestParam(value = "typeCode", required = false) String typeCode, @RequestParam(value = "fields", required = false) String fields) {
        Leaf lf = new Leaf();
        if ("".equals(typeCode)) {
            typeCode = Leaf.CODE_ROOT;
        }
        lf = lf.getLeaf(typeCode);
        String colProps = lf.getColProps();
        JSONArray aryColProps = null;
        if (!StrUtil.isEmpty(colProps)) {
            aryColProps = com.alibaba.fastjson.JSONArray.parseArray(colProps);
            if (aryColProps == null) {
                LogUtil.getLog(getClass()).error("json格式非法：" + colProps);
                // aryColProps = new JSONArray();
            }
        }

        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());

        JSONArray arr = new JSONArray();
        String[] arrCol = fields.split(",");
        // 顺序拼接colProps
        for (String field : arrCol) {
            boolean isFound = false;
            if (aryColProps!=null && aryColProps.size() > 0) {
                for (Object o : aryColProps) {
                    JSONObject jsonObject = (JSONObject)o;
                    if (field.equals(jsonObject.getString("field")) || field.equals(jsonObject.getString("name"))) {
                        isFound = true;
                        // 清洗
                        if (field.equals(jsonObject.getString("name"))) {
                            jsonObject.put("field", field);
                            jsonObject.remove("name");
                        }
                        arr.add(jsonObject);
                        break;
                    }
                }
            }
            if (!isFound) {
                JSONObject json = new JSONObject();
                FormField ff = fd.getFormField(field);
                String title;
                if (ff == null) {
                    title = Leaf.getFlowColTitle(field);
                } else {
                    title = ff.getTitle();
                }
                json.put("title", title);
                json.put("field", field);
                json.put("width", 150);
                json.put("sort", false);
                json.put("align", "center");
                json.put("hide", false);
                arr.add(json);
            }
        }

        lf.setColProps(arr.toString());
        return new Result<>(lf.update());
    }

    @ApiOperation(value = "保存列表设置", notes = "保存列表设置", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "typeCode", value = "流程类型", required = true, dataType = "String"),
            @ApiImplicitParam(name = "fields", value = "列表中的字段，以逗号分隔", required = false, dataType = "Integer"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/saveColWidth", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;"})
    public Result<Object> saveColWidth(@RequestParam(value = "typeCode", required = false) String typeCode, @RequestParam(value = "field", required = false) String field, @RequestParam(value = "width", required = false)Double width) {
        Leaf lf = new Leaf();
        if ("".equals(typeCode)) {
            typeCode = Leaf.CODE_ROOT;
        }
        lf = lf.getLeaf(typeCode);
        String colProps = lf.getColProps();
        JSONArray aryColProps;
        if (!StrUtil.isEmpty(colProps)) {
            aryColProps = com.alibaba.fastjson.JSONArray.parseArray(colProps);
            if (aryColProps == null) {
                LogUtil.getLog(getClass()).error("json格式非法：" + colProps);
                return new Result<>(false, "json格式非法：" + colProps);
            }
        }
        else {
            int displayMode = ParamUtil.getInt(request, "displayMode", WorkflowMgr.DISPLAY_MODE_MINE);
            aryColProps = com.redmoon.oa.flow.Leaf.getDefaultColProps(request, typeCode, displayMode);
        }
        for (Object o : aryColProps) {
            JSONObject json = (JSONObject)o;
            // 清洗老的格式，将name改为field
            if (json.containsKey("name")) {
                json.put("field", json.remove("name"));
            }
            if (field.equals(json.getString("field"))) {
                json.put("width", width.intValue());
                break;
            }
        }

        lf.setColProps(aryColProps.toString());
        return new Result<>(lf.update());
    }

    // 用于flow_list.html页面
    @ResponseBody
    @RequestMapping(value = "/flow/saveSearchColProps", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;"})
    public String saveSearchColProps(@RequestParam(value = "typeCode", required = false) String typeCode, @RequestParam(value = "colProps", required = false) String colProps) {
        Privilege pvg = new Privilege();
        boolean re = pvg.auth(request);
        JSONObject json = new JSONObject();
        if (!re) {
            json.put("ret", "0");
            json.put("msg", "权限非法！");
            return json.toString();
        }

        Leaf lf = new Leaf();
        if ("".equals(typeCode)) {
            typeCode = Leaf.CODE_ROOT;
        }
        lf = lf.getLeaf(typeCode);
        lf.setColProps(colProps);
        re = lf.update();

        if (re) {
            json.put("ret", "1");
            json.put("msg", "操作成功！");
        } else {
            json.put("ret", "0");
            json.put("msg", "操作失败！");
        }
        return json.toString();
    }

    /**
     * 置待办记录的状态
     *
     * @param myActionId
     * @param checkStatus
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/setMyActionStatus", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;"})
    public String setMyActionStatus(@RequestParam(value = "myActionId", required = false) long myActionId, @RequestParam(value = "checkStatus", required = false) int checkStatus) {
        JSONObject json = new JSONObject();
        WorkflowMgr wm = new WorkflowMgr();
        boolean re = false;
        try {
            int actionStatus = wm.setMyActionStatus(request, myActionId, checkStatus);
            json.put("actionStatus", actionStatus);
            json.put("ret", "1");
            json.put("msg", "操作成功！");
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
            json.put("ret", "0");
            json.put("msg", e.getMessage());
        }
        return json.toString();
    }

    /**
     * 清除节点上的用户
     *
     * @param actionId
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/public/flow/clearActionUser", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;"})
    public String clearActionUser(@RequestParam(value = "actionId", required = false) int actionId) {
        boolean re = false;
        JSONObject json = new JSONObject();
        try {
            WorkflowActionDb wa = new WorkflowActionDb();
            wa = wa.getWorkflowActionDb(actionId);
            wa.setUserName("");
            wa.setUserRealName("");
            re = wa.save();
            if (re) {
                json.put("ret", "1");
                json.put("msg", "操作成功！");
            } else {
                json.put("ret", "0");
                json.put("msg", "操作失败！");
            }
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
        }
        return json.toString();
    }

    /**
     * 流程批量处理
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/finishBatch", method = RequestMethod.POST, produces = {"application/json;"})
    public Result<Object> finishBatch(@RequestParam(value="ids", required = true)String ids) {
        try {
            workflowProService.FinishActionBatch(request, ids);
        } catch (ErrMsgException e) {
            return new Result<>(false, e.getMessage());
        }
        return new Result<>(true);
    }

    /**
     * 列出下载日志
     *
     * @param flowId
     * @param attId
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/public/flow/listAttLog", method = RequestMethod.POST, produces = {"text/html;", "application/json;charset=UTF-8;"})
    public String listAttLog(@RequestParam(value = "flowId", required = false) long flowId, @RequestParam(value = "attId", required = false) long attId) {
        AttachmentLogDb ald = new AttachmentLogDb();
        String sql = ald.getQuery(request, flowId, attId);
        int pageSize = ParamUtil.getInt(request, "rp", 20);
        int curPage = ParamUtil.getInt(request, "page", 1);
        ListResult lr = null;
        try {
            lr = ald.listResult(sql, curPage, pageSize);
        } catch (ResKeyException e) {
            LogUtil.getLog(getClass()).error(e);
        }

        JSONArray rows = new JSONArray();
        JSONObject jobject = new JSONObject();

        jobject.put("rows", rows);
        jobject.put("page", curPage);
        jobject.put("total", lr.getTotal());

        UserDb user = new UserDb();
        Iterator ir = lr.getResult().iterator();
        while (ir.hasNext()) {
            ald = (AttachmentLogDb) ir.next();
            JSONObject jo = new JSONObject();
            jo.put("id", String.valueOf(ald.getLong("id")));
            jo.put("logTime", DateUtil.format(ald.getDate("log_time"), "yyyy-MM-dd HH:mm:ss"));

            user = user.getUserDb(ald.getString("user_name"));
            jo.put("realName", user.getRealName());

            Attachment att = new Attachment((int) ald.getLong("att_id"));
            jo.put("attName", att.getName());

            jo.put("logType", AttachmentLogDb.getTypeDesc(ald.getInt("log_type")));

            rows.add(jo);
        }

        return jobject.toString();
    }

    /**
     * 删除附件日志
     *
     * @param ids
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/public/flow/delLog", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;"})
    public String delLog(@RequestParam(value = "ids", required = false) String ids) {
        JSONObject json = new JSONObject();

        String[] ary = StrUtil.split(ids, ",");
        if (ary == null) {
            json.put("ret", "0");
            json.put("msg", "请选择记录！");
            return json.toString();
        }

        try {
            boolean re = false;
            for (String strId : ary) {
                long id = StrUtil.toLong(strId, -1);
                if (id != -1) {
                    AttachmentLogDb ald = new AttachmentLogDb();
                    ald = (AttachmentLogDb) ald.getQObjectDb(id);

                    boolean isValid = false;
                    com.redmoon.oa.pvg.Privilege pvg = new com.redmoon.oa.pvg.Privilege();
                    if (pvg.isUserPrivValid(request, "admin")) {
                        isValid = true;
                    } else {
                        long flowId = ald.getLong("flow_id");
                        WorkflowDb wf = new WorkflowDb();
                        wf = wf.getWorkflowDb((int) flowId);
                        if (wf != null) {
                            LeafPriv lp = new LeafPriv(wf.getTypeCode());
                            if (pvg.isUserPrivValid(request, "admin.flow")) {
                                if (lp.canUserExamine(pvg.getUser(request))) {
                                    isValid = true;
                                }
                            }
                        } else {
                            // 对应的流程如不存在，则允许删除
                            isValid = true;
                        }
                    }

                    if (!isValid) {
                        json.put("ret", "0");
                        json.put("msg", "权限非法！");
                        return json.toString();
                    }

                    if (isValid) {
                        re = ald.del();
                    }
                } else {
                    json.put("ret", "0");
                    json.put("msg", "标识非法！");
                    return json.toString();
                }
            }

            if (re) {
                json.put("ret", "1");
                json.put("msg", "操作成功！");
            } else {
                json.put("ret", "0");
                json.put("msg", "操作失败！");
            }
        } catch (ResKeyException e) {
            LogUtil.getLog(getClass()).error(e);
        }
        return json.toString();
    }

    @ApiOperation(value = "流程列表", notes = "流程列表", httpMethod = "POST")
    @ApiImplicitParam(name = "type", value = "列表类型", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/list", method = RequestMethod.POST, produces = {"text/html;", "application/json;charset=UTF-8;"})
    public Result<Object> list(@RequestParam(value = "type", required = false) String type) {
        // 默认值用DISPLAY_MODE_DOING，而原先是DISPLAY_MODE_SEARCH，防止360浏览器传上来的参数异常，致直接进入查询，而导致看到所有人的待办流程
        int displayMode;
        // 显示模式，0表示流程查询、1表示待办、2表示我参与的流程、3表示我发起的流程
        if ("mine".equals(type)) {
            displayMode = WorkflowMgr.DISPLAY_MODE_MINE;
        }
        else if ("attend".equals(type)) {
            displayMode = WorkflowMgr.DISPLAY_MODE_ATTEND;
        }
        else if ("favorite".equals(type)) {
            displayMode = WorkflowMgr.DISPLAY_MODE_FAVORIATE;
        }
        else if ("search".equals(type)) {
            displayMode = WorkflowMgr.DISPLAY_MODE_SEARCH;
        }
        else {
            // type=doing
            displayMode = WorkflowMgr.DISPLAY_MODE_DOING;
        }

        String typeCode = ParamUtil.get(request, "typeCode");
        String action = ParamUtil.get(request, "action"); // sel 选择我的流程

        MyActionDb mad = new MyActionDb();
        MacroCtlMgr mm = new MacroCtlMgr();
        FormDb fd = new FormDb();
        FormDAO fdao = new FormDAO();
        UserMgr um = new UserMgr();
        UserDb user;

        WorkflowDb wf = new WorkflowDb();
        com.alibaba.fastjson.JSONObject jobject = new com.alibaba.fastjson.JSONObject();
        Config cfg = Config.getInstance();
        jobject.put("flowOpStyle", StrUtil.toInt(cfg.get("flowOpStyle"), 1));
        int pageSize = ParamUtil.getInt(request, "pageSize", 20);
        int curPage = ParamUtil.getInt(request, "page", 1);
        String myUserName = authUtil.getUserName();
        com.redmoon.oa.pvg.Privilege pvg = new com.redmoon.oa.pvg.Privilege();
        Leaf leaf = new Leaf();
        if (displayMode == WorkflowMgr.DISPLAY_MODE_SEARCH) {
            if ("".equals(typeCode)) {
                if (pvg.isUserPrivValid(myUserName, "admin")) {
                    typeCode = Leaf.CODE_ROOT;
                } else {
                    LeafPriv lp = new LeafPriv(Leaf.CODE_ROOT);
                    if (lp.canUserQuery(myUserName)) {
                        typeCode = Leaf.CODE_ROOT;
                    } else {
                        jobject.put("list", new JSONArray());
                        return new Result<>(jobject);
                    }
                }
            }
        }

        if (!"".equals(typeCode)) {
            leaf = leaf.getLeaf(typeCode);
            if (leaf == null) {
                return new Result<>(false, "流程类型：" + typeCode + "不存在");
            }
            fd = fd.getFormDb(leaf.getFormCode());
        }

        com.redmoon.oa.sso.Config ssoconfig = new com.redmoon.oa.sso.Config();
        String desKey = ssoconfig.get("key");

        com.alibaba.fastjson.JSONArray colProps = null;
        if (leaf.isLoaded() && !"".equals(leaf.getColProps())) {
            colProps = com.alibaba.fastjson.JSONArray.parseArray(leaf.getColProps());
        }
        if (colProps == null) {
            colProps = com.redmoon.oa.flow.Leaf.getDefaultColProps(request, typeCode, displayMode);
        }
        String userRealName = "";

        // 显示模式，0表示流程查询、1表示待办、2表示我参与的流程、3表示我发起的流程、4表示我关注的流程
        if (displayMode == WorkflowMgr.DISPLAY_MODE_DOING) {
            String sql = wf.getSqlDoing(request);
            ListResult lr = null;
            try {
                lr = mad.listResult(sql, curPage, pageSize);
            } catch (ErrMsgException e) {
                LogUtil.getLog(getClass()).error(e);
            }
            com.alibaba.fastjson.JSONArray rows = new com.alibaba.fastjson.JSONArray();
            jobject.put("list", rows);
            jobject.put("page", curPage);
            jobject.put("total", lr.getTotal());

            WorkflowDb wfd = new WorkflowDb();
            java.util.Iterator ir = lr.getResult().iterator();
            while (ir.hasNext()) {
                mad = (MyActionDb) ir.next();
                wfd = wfd.getWorkflowDb((int) mad.getFlowId());
                String userName = wfd.getUserName();
                if (userName != null) {
                    user = um.getUserDb(wfd.getUserName());
                    userRealName = user.getRealName();
                }
                if (!typeCode.equals(wfd.getTypeCode())) { // 流程查询时，点击根节点，会显示所有的流程，此时typeCode可能与wfd.getTypeCode不一致
                    Leaf lf = leaf.getLeaf(wfd.getTypeCode());
                    if (lf == null) {
                        DebugUtil.e(getClass(), "list", "流程：" + wfd.getId() + "、" + wfd.getTitle() + " 类型：" + wfd.getTypeCode() + " 已不存在！");
                        continue;
                    } else {
                        fd = fd.getFormDb(lf.getFormCode());
                    }
                }
                fdao = fdao.getFormDAOByCache(wfd.getId(), fd);
                if (fdao == null) {
                    fdao = new FormDAO();
                    LogUtil.getLog(getClass()).warn("记录: flowId=" + wfd.getId() + " 已被删除");
                } else {
                    com.alibaba.fastjson.JSONObject jo = getRow(wfd, fdao, colProps, um, userRealName, mad, mm, leaf, displayMode, action, desKey);
                    rows.add(jo);
                }
            }
        } else {
            String sql;
            if (displayMode == WorkflowMgr.DISPLAY_MODE_ATTEND) {
                sql = wf.getSqlAttend(request);
            } else if (displayMode == WorkflowMgr.DISPLAY_MODE_MINE) {
                sql = wf.getSqlMine(request);
            } else if (displayMode == WorkflowMgr.DISPLAY_MODE_FAVORIATE) {
                sql = wf.getSqlFavorite(request);
            } else {
                // 判断是否有权限，以防止非法操作
                LeafPriv lp = new LeafPriv(typeCode);
                if (!lp.canUserQuery(pvg.getUser(request))) {
                    // 如果用户没有权限，则返回我的流程所用的sql
                    sql = wf.getSqlAttend(request);
                } else {
                    sql = wf.getSqlSearch(request);
                }
            }

            DebugUtil.i(getClass(), "list sql=", sql);

            ListResult lr = null;
            try {
                lr = wf.listResult(sql, curPage, pageSize);
            } catch (ErrMsgException e) {
                LogUtil.getLog(getClass()).error(e);
            }

            // DebugUtil.i(getClass(), "list", "listResult 时长：" + (new Date().getTime() - t) + "毫秒 sql=" + sql);

            com.alibaba.fastjson.JSONArray rows = new com.alibaba.fastjson.JSONArray();
            jobject.put("list", rows);
            jobject.put("page", curPage);
            jobject.put("total", lr.getTotal());

            Iterator ir = lr.getResult().iterator();
            while (ir.hasNext()) {
                WorkflowDb wfd = (WorkflowDb) ir.next();
                if (!typeCode.equals(wfd.getTypeCode())) { // 流程查询时，点击根节点，会显示所有的流程，此时typeCode可能与wfd.getTypeCode不一致
                    Leaf lf = leaf.getLeaf(wfd.getTypeCode());
                    if (lf == null) {
                        DebugUtil.e(getClass(), "list", "流程：" + wfd.getId() + " ，其类型：" + wfd.getTypeCode() + " 已不存在！");
                        continue;
                    }
                    fd = fd.getFormDb(lf.getFormCode());
                } else {
                    fd = fd.getFormDb(leaf.getFormCode());
                }

                user = um.getUserDb(wfd.getUserName());
                if (user.isLoaded()) {
                    userRealName = user.getRealName();
                }

                fdao = fdao.getFormDAOByCache(wfd.getId(), fd);
                if (fdao == null) {
                    fdao = new FormDAO();
                    LogUtil.getLog(getClass()).warn("记录: flowId=" + wfd.getId() + " 已被删除");
                } else {
                    com.alibaba.fastjson.JSONObject jo = getRow(wfd, fdao, colProps, um, userRealName, mad, mm, leaf, displayMode, action, desKey);
                    rows.add(jo);
                }
            }
        }

        return new Result<>(jobject);
    }

    public com.alibaba.fastjson.JSONObject getRow(WorkflowDb wfd, FormDAO fdao, com.alibaba.fastjson.JSONArray colProps, UserMgr um, String userRealName, MyActionDb mad, MacroCtlMgr mm, Leaf leaf, int displayMode, String action, String desKey) {
        com.alibaba.fastjson.JSONObject jo = new com.alibaba.fastjson.JSONObject();
        jo.put("id", String.valueOf(mad.getId()));

        Leaf lf = leaf.getLeaf(wfd.getTypeCode());
        if (lf == null) {
            jo.put("type", wfd.getTypeCode() + " 不存在");
        } else {
            jo.put("type", lf.getType());
        }
        RequestUtil.setFormDAO(request, fdao);
        for (Object colProp : colProps) {
            com.alibaba.fastjson.JSONObject json = (com.alibaba.fastjson.JSONObject) colProp;
            // 复选框列没有field
            String fieldName;
            if (json.containsKey("field")) {
                 fieldName = json.getString("field");
            } else if (json.containsKey("name")) {
                fieldName = json.getString("name");
            } else {
                continue;
            }

            String val = "";
            if (fieldName.startsWith("f.")) {
                String fieldNameReal = fieldName.substring(2);
                if ("id".equalsIgnoreCase(fieldNameReal)) {
                    val = String.valueOf(wfd.getId());
                } else if ("flow_level".equalsIgnoreCase(fieldNameReal)) {
                    val = String.valueOf(wfd.getLevel());
                } else if ("title".equalsIgnoreCase(fieldNameReal)) {
                    val = wfd.getTitle();
                } else if ("type_code".equalsIgnoreCase(fieldNameReal)) {
                    if (lf == null) {
                        val = "流程类型" + wfd.getTypeCode() + "不存在";
                    }
                    else {
                        val = lf.getName(request);
                    }
                } else if ("begin_date".equals(fieldNameReal) || "mydate".equals(fieldNameReal)) { // 保留mydate条件，是为了向下兼容，以免需重置后，才能显示时间
                    // val = DateUtil.format(wfd.getBeginDate(), "yy-MM-dd HH:mm");
                    val = DateUtil.format(wfd.getBeginDate(), "yyyy-MM-dd HH:mm:ss");
                } else if ("userName".equals(fieldNameReal)) {
                    val = userRealName;
                } else if ("finallyApply".equals(fieldNameReal)) {
                    // 取得最后一个已办理的人员
                    MyActionDb lastMad = mad.getLastMyActionDbDoneOfFlow(wfd.getId());
                    if (lastMad != null) {
                        val = um.getUserDb(lastMad.getUserName()).getRealName();
                    }
                } else if ("currentHandle".equals(fieldNameReal)) {
                    for (MyActionDb madCur : mad.getMyActionDbDoingOfFlow(wfd.getId())) {
                        if (!val.equals("")) {
                            val += "、";
                        }
                        val += um.getUserDb(madCur.getUserName()).getRealName();
                    }
                } else if ("status".equals(fieldNameReal)) {
                    if (displayMode != WorkflowMgr.DISPLAY_MODE_DOING) {
                        val = wfd.getStatusDesc();
                    } else {
                        val = WorkflowActionDb.getStatusName(mad.getActionStatus());
                    }
                } else if ("remainTime".equals(fieldNameReal)) {
                    String remainDateStr = "";
                    if (mad.getExpireDate() != null && DateUtil.compare(new Date(), mad.getExpireDate()) == 2) {
                        int[] ary = DateUtil.dateDiffDHMS(mad.getExpireDate(), new Date());
                        String str_day = i18nUtil.get("day");
                        String str_hour = i18nUtil.get("h_hour");
                        String str_minute = i18nUtil.get("minute");
                        remainDateStr = ary[0] + " " + str_day + ary[1] + " " + str_hour + ary[2] + " " + str_minute;
                        val = remainDateStr;
                    }
                } else if ("end_date".equals(fieldNameReal)) {
                    // val = DateUtil.format(wfd.getEndDate(), "yy-MM-dd HH:mm");
                    val = DateUtil.format(wfd.getEndDate(), "yyyy-MM-dd HH:mm:ss");
                }
            } else {
                FormField ff = fdao.getFormField(fieldName);
                if (ff != null) {
                    if (ff.getType().equals(FormField.TYPE_MACRO)) {
                        MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                        if (mu == null) {
                            DebugUtil.e(getClass(), "getRow", ff.getTitle() + " 宏控件: " + ff.getMacroType() + " 不存在");
                        } else {
                            val = mu.getIFormMacroCtl().converToHtml(request, ff, fdao.getFieldValue(fieldName));
                        }
                    } else {
                        val = FuncUtil.renderFieldValue(fdao, ff);
                    }
                }
            }
            jo.put(fieldName, val);
        }

        jo.put("isReaded", mad.isReaded());

        String visitKey = cn.js.fan.security.ThreeDesUtil.encrypt2hex(desKey, String.valueOf(fdao.getFlowId()));
        jo.put(visitKey, visitKey);
        return jo;
    }

    /**
     * 取得被renew的流程
     *
     * @param count int 数量
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/public/flow/getFlowsRenewed", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;"})
    public String getFlowsRenewed(@RequestParam(value = "count", required = false) int count) {
        com.redmoon.oa.Config cfg = new com.redmoon.oa.Config();//从XML文件里取出文件存入路径
        String flowImagePath = cfg.get("flowImagePath");
        Calendar cal = Calendar.getInstance();
        String year = String.valueOf(cal.get(Calendar.YEAR));
        String month = String.valueOf(cal.get(Calendar.MONTH) + 1);
        String vpath = flowImagePath + "/" + year + "/" + month;
        File f = new File(Global.getRealPath() + vpath);
        if (!f.isDirectory()) {
            f.mkdirs();
        }

        JSONArray rows = new JSONArray();
        JSONObject jobject = new JSONObject();
        String sql = "select id from flow where is_renewed=1 order by id desc";
        WorkflowDb wf = new WorkflowDb();
        try {
            ListResult lr = wf.listResult(sql, 1, count);
            jobject.put("visualPath", vpath);
            jobject.put("rows", rows);
            jobject.put("count", lr.getResult().size());

            Iterator ir = lr.getResult().iterator();
            while (ir.hasNext()) {
                wf = (WorkflowDb) ir.next();
                JSONObject jo = new JSONObject();
                jo.put("id", String.valueOf(wf.getId()));
                jo.put("title", wf.getTitle());
                jo.put("flowString", wf.getFlowString());
                rows.add(jo);
            }
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
        }
        return jobject.toString();
    }

    /**
     * 删除附件日志
     *
     * @param ids
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/public/flow/setFlowsRenewed", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;"})
    public String setFlowsRenewed(@RequestParam(value = "ids", required = false) String ids, @RequestParam(value = "visualPath", required = false) String visualPath) {
        JSONObject json = new JSONObject();
        String[] ary = StrUtil.split(ids, ",");
        if (ary == null) {
            json.put("ret", "0");
            json.put("msg", "请选择记录！");
            return json.toString();
        }

        boolean re = false;
        for (String strId : ary) {
            int id = StrUtil.toInt(strId, -1);
            if (id != -1) {
                WorkflowDb wf = new WorkflowDb();
                wf = wf.getWorkflowDb(id);
                wf.setRenewed(false);
                wf.setImgVisualPath(visualPath);
                re = wf.save();
            } else {
                json.put("ret", "0");
                json.put("msg", "标识非法！");
                return json.toString();
            }
        }

        if (re) {
            json.put("ret", "1");
            json.put("msg", "操作成功！");
        } else {
            json.put("ret", "0");
            json.put("msg", "操作失败！");
        }
        return json.toString();
    }

    /**
     * 关联项目
     *
     * @param request
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/linkProject", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String linkProject(HttpServletRequest request) {
        int flowId = ParamUtil.getInt(request, "flowId", -1);
        long projectId = ParamUtil.getLong(request, "projectId", -1);
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);
        wf.setProjectId(projectId);
        boolean re = wf.save();
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        if (re) {
            json.put("ret", "1");
            String str = i18nUtil.get("info_op_success");
            json.put("msg", str);
        } else {
            json.put("ret", "0");
            String str = i18nUtil.get("info_op_fail");
            json.put("msg", str);
        }
        return json.toString();
    }

    /**
     * 取消关联项目
     *
     * @param request
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/unlinkProject", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String unlinkProject(HttpServletRequest request) {
        int flowId = ParamUtil.getInt(request, "flowId", -1);
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);
        wf.setProjectId(-1);
        boolean re = wf.save();
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        if (re) {
            json.put("ret", "1");
            String str = i18nUtil.get("info_op_success");
            json.put("msg", str);
        } else {
            json.put("ret", "0");
            String str = i18nUtil.get("info_op_fail");
            json.put("msg", str);
        }
        return json.toString();
    }

    /**
     * 流程抄送
     * @param request
     * @return
     * @throws ResKeyException
     */
    @ResponseBody
    @RequestMapping(value = "/flow/distribute", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> distribute(HttpServletRequest request) throws ResKeyException {
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        String myname = privilege.getUser(request);

        UserDb user = new UserDb();
        user = user.getUserDb(myname);

        String units = ParamUtil.get(request, "units");
        String users = ParamUtil.get(request, "users");
        int flowId = ParamUtil.getInt(request, "flowId", -1);
        String paperTitle = ParamUtil.get(request, "title");
        int isFlowDisplay = ParamUtil.getInt(request, "isFlowDisplay", 0);
        String[] ary = StrUtil.split(units, ",");
        int len = 0;
        if (ary != null) {
            len = ary.length;
        }

        //发送邮件提醒
        com.redmoon.oa.Config cfg = new com.redmoon.oa.Config();
        boolean flowNotifyByEmail = cfg.getBooleanProperty("flowNotifyByEmail");
        SendMail sendmail = WorkflowDb.getSendMail();
        UserDb formUserDb = new UserDb();
        formUserDb = formUserDb.getUserDb(privilege.getUser(request));
        String fromNick = "";
        try {
            fromNick = MimeUtility.encodeText(formUserDb.getRealName());
        } catch (Exception e) {
            LogUtil.getLog(getClass()).error(e);
        }
        String fromEmail = Global.getEmail();
        fromNick = fromNick + "<" + fromEmail + ">";

        com.redmoon.oa.sso.Config ssoCfg = new com.redmoon.oa.sso.Config();

        boolean re = true;
        DeptDb dd = new DeptDb();
        String fromUnit = authUtil.getUserUnitCode();
        dd = dd.getDeptDb(fromUnit);
        String unitName = dd.getName();
        PaperConfig pc = PaperConfig.getInstance();
        // 从配置文件中得到收文角色
        String swRoles = pc.getProperty("swRoles");
        String[] aryRole = StrUtil.split(swRoles, ",");
        int aryRoleLen = 0;
        if (aryRole != null) {
            aryRoleLen = aryRole.length;
        }
        RoleDb[] aryR = new RoleDb[aryRoleLen];
        RoleDb rd = new RoleDb();
        // 取出收文角色
        for (int i = 0; i < aryRoleLen; i++) {
            aryR[i] = rd.getRoleDb(aryRole[i]);
        }
        for (int i = 0; i < len; i++) {
            PaperDistributeDb pdd = new PaperDistributeDb();
            String toUnit = ary[i];
            java.util.Date disDate = new java.util.Date();
            int isReaded = 0;
            int kind = PaperDistributeDb.KIND_UNIT;
            long paperId = SequenceManager.nextID(SequenceManager.OA_FLOW_PAPER_DISTRIBUTE);

            re = pdd.create(new JdbcTemplate(), new Object[]{paperId, paperTitle, flowId, fromUnit, toUnit, disDate, myname, isFlowDisplay, new Integer(isReaded), new Integer(kind)});
            if (re) {
                for (int j = 0; j < aryRoleLen; j++) {
                    // 取出角色中的全部用户
                    for (UserDb userDb : aryR[j].getAllUserOfRole()) {
                        user = userDb;
                        // 如果用户属于收文单位
                        if (user.getUnitCode().equals(toUnit)) {
                            // 消息提醒
                            String action;
                            if (isFlowDisplay == 0) {
                                action = "action=" + MessageDb.ACTION_PAPER_DISTRIBUTE + "|paperId=" + paperId;
                            } else {
                                action = "action=" + MessageDb.ACTION_FLOW_SHOW + "|flowId=" + flowId;
                            }

                            try {
                                String swNoticeTitle = pc.getProperty("swNoticeTitle");
                                swNoticeTitle = StrUtil.format(swNoticeTitle, new Object[]{unitName, paperTitle});
                                String swNoticeContent = pc.getProperty("swNoticeContent");
                                swNoticeContent = StrUtil.format(swNoticeContent, new Object[]{unitName, paperTitle, DateUtil.format(disDate, "yyyy-MM-dd")});
                                MessageDb md = new MessageDb();
                                md.sendSysMsg(user.getName(), swNoticeTitle, swNoticeContent, action);

                                if (flowNotifyByEmail) {
                                    if (isFlowDisplay == 1) {
                                        String actionFlow = "userName=" + user.getName() + "|" +
                                                "flowId=" + flowId;
                                        actionFlow = cn.js.fan.security.ThreeDesUtil.encrypt2hex(ssoCfg.getKey(), actionFlow);
                                        UserSetupDb usd = new UserSetupDb(user.getName());
                                        swNoticeContent += "<BR />>>&nbsp;<a href='" +
                                                WorkflowUtil.getJumpUrl(WorkflowUtil.OP_FLOW_SHOW, action) +
                                                "' target='_blank'>" +
                                                ("en-US".equals(usd.getLocal()) ? "Click here to view" : "请点击此处查看") + "</a>";
                                    }
                                    sendmail.initMsg(user.getEmail(), fromNick, swNoticeTitle, swNoticeContent, true);
                                    sendmail.send();
                                    sendmail.clear();
                                }

                                String swPlanTitle = pc.getProperty("swPlanTitle");
                                swPlanTitle = StrUtil.format(swPlanTitle, new Object[]{paperTitle});
                                String swPlanContent = pc.getProperty("swPlanContent");
                                swPlanContent = StrUtil.format(swPlanContent, new Object[]{unitName, paperTitle, DateUtil.format(disDate, "yyyy-MM-dd")});

                                // 创建日程安排
                                PlanDb pd = new PlanDb();
                                pd.setTitle(swPlanTitle);
                                pd.setContent(swPlanContent);
                                pd.setMyDate(new Date());
                                pd.setEndDate(new Date());
                                pd.setActionData(String.valueOf(paperId));
                                pd.setActionType(PlanDb.ACTION_TYPE_PAPER_DISTRIBUTE);
                                pd.setUserName(user.getName());
                                pd.setRemind(false);
                                pd.setRemindBySMS(false);
                                pd.setRemindDate(new Date());
                                pd.create();
                            } catch (ErrMsgException ex2) {
                                ex2.printStackTrace();
                            }
                        }
                    }
                }
            }
        }

        UserDb toUserDb = new UserDb();
        ary = StrUtil.split(users, ",");
        len = 0;
        if (ary != null) {
            len = ary.length;
        }
        for (int i = 0; i < len; i++) {
            PaperDistributeDb pdd = new PaperDistributeDb();
            java.util.Date disDate = new java.util.Date();
            int isReaded = 0;
            int kind = PaperDistributeDb.KIND_USER;
            long paperId = SequenceManager.nextID(SequenceManager.OA_FLOW_PAPER_DISTRIBUTE);
            re = pdd.create(new JdbcTemplate(), new Object[]{paperId, paperTitle, flowId, fromUnit, ary[i], disDate, myname, isFlowDisplay, new Integer(isReaded), new Integer(kind)});
            if (re) {
                // 消息提醒
                String action;
                if (isFlowDisplay == 0) {
                    action = "action=" + MessageDb.ACTION_PAPER_DISTRIBUTE + "|paperId=" + paperId;
                } else {
                    action = "action=" + MessageDb.ACTION_FLOW_SHOW + "|flowId=" + flowId;
                }
                try {
                    String swNoticeTitle = pc.getProperty("swNoticeTitle");
                    swNoticeTitle = StrUtil.format(swNoticeTitle, new Object[]{unitName, paperTitle});
                    String swNoticeContent = pc.getProperty("swNoticeContent");
                    swNoticeContent = StrUtil.format(swNoticeContent, new Object[]{unitName, paperTitle, DateUtil.format(disDate, "yyyy-MM-dd")});
                    MessageDb md = new MessageDb();
                    md.sendSysMsg(ary[i], swNoticeTitle, swNoticeContent, action);

                    if (flowNotifyByEmail) {
                        toUserDb = toUserDb.getUserDb(ary[i]);
                        if (isFlowDisplay == 1) {
                            String actionFlow = "userName=" + toUserDb.getName() + "|" + "flowId=" + flowId;
                            actionFlow = cn.js.fan.security.ThreeDesUtil.encrypt2hex(ssoCfg.getKey(), actionFlow);
                            UserSetupDb usd = new UserSetupDb(toUserDb.getName());
                            swNoticeContent += "<BR />>>&nbsp;<a href='" +
                                    WorkflowUtil.getJumpUrl(WorkflowUtil.OP_FLOW_SHOW, action) +
                                    "' target='_blank'>" +
                                    ("en-US".equals(usd.getLocal()) ? "Click here to view" : "请点击此处查看") + "</a>";
                        }
                        sendmail.initMsg(toUserDb.getEmail(), fromNick, swNoticeTitle, swNoticeContent, true);
                        sendmail.send();
                        sendmail.clear();
                    }

                    String swPlanTitle = pc.getProperty("swPlanTitle");
                    swPlanTitle = StrUtil.format(swPlanTitle, new Object[]{paperTitle});
                    String swPlanContent = pc.getProperty("swPlanContent");
                    swPlanContent = StrUtil.format(swPlanContent, new Object[]{unitName, paperTitle, DateUtil.format(disDate, "yyyy-MM-dd")});

                    // 创建日程安排
                    PlanDb pd = new PlanDb();
                    pd.setTitle(swPlanTitle);
                    pd.setContent(swPlanContent);
                    pd.setMyDate(new java.util.Date());
                    pd.setEndDate(new java.util.Date());
                    pd.setActionData(String.valueOf(paperId));
                    pd.setActionType(PlanDb.ACTION_TYPE_PAPER_DISTRIBUTE);
                    pd.setUserName(ary[i]);
                    pd.setRemind(false);
                    pd.setRemindBySMS(false);
                    pd.setRemindDate(new java.util.Date());
                    pd.create();
                } catch (ErrMsgException ex2) {
                    ex2.printStackTrace();
                }
            }
        }

        // 生成PDF
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);
        int doc_id = wf.getDocId();
        com.redmoon.oa.flow.DocumentMgr dm = new com.redmoon.oa.flow.DocumentMgr();
        com.redmoon.oa.flow.Document doc = dm.getDocument(doc_id);
        java.util.Vector<IAttachment> attachments = doc.getAttachments(1);
        for (IAttachment att : attachments) {
            String ext = StrUtil.getFileExt(att.getName());
            if ("doc".equals(ext) || "docx".equals(ext)) {
                ;
            } else {
                continue;
            }

            String fileName = att.getDiskName();
            String fName = fileName.substring(0, fileName.lastIndexOf("."));
            fileName = fName + ".pdf";

            String fileDiskPath = Global.getRealPath() + att.getVisualPath() + "/" + att.getDiskName();
            String pdfPath = Global.getRealPath() + att.getVisualPath() + "/" + fileName;
            try {
                com.redmoon.oa.util.PDFConverter.convert2PDF(fileDiskPath, pdfPath);
            } catch (Exception e) {
                // UnsatisfiedLinkError: no jacob in java.library.path
                LogUtil.getLog(getClass()).error(e);
            }
        }

        return new Result<>(re);
    }

    @ResponseBody
    @RequestMapping(value = "/flow/clearLocker", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String clearLocker(HttpServletRequest request) {
        int fileId = ParamUtil.getInt(request, "fileId", -1);
        boolean re = true;
        com.redmoon.oa.flow.Attachment at = new com.redmoon.oa.flow.Attachment(fileId);
        if (!"".equals(at.getLockUser())) {
            at.setLockUser("");
            re = at.save();
        }
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        if (re) {
            json.put("ret", "1");
        } else {
            json.put("ret", "0");
        }
        return json.toString();
    }

    /**
     * 撤回
     * @param request
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/recall", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> recall(HttpServletRequest request) {
        WorkflowMgr wfm = new WorkflowMgr();
        long myActionId = ParamUtil.getLong(request, "myActionId", -1);
        boolean re;
        try {
            re = wfm.recallMyAction(request, myActionId);
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
            return new Result<>(false, e.getMessage());
        }
        return new Result<>(re);
    }

    /**
     * 如果本节点是异或聚合，办理完毕，但不转交
     *
     * @param request
     * @return
     * @throws ErrMsgException
     */
    @ResponseBody
    @RequestMapping(value = "/flow/setFinishAndNotDelive", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String setFinishAndNotDelive(HttpServletRequest request) throws ErrMsgException {
        boolean re = false;
        long myActionId = ParamUtil.getLong(request, "myActionId");
        long actionId = ParamUtil.getLong(request, "actionId");
        WorkflowActionDb wa = new WorkflowActionDb();
        wa = wa.getWorkflowActionDb((int) actionId);
        wa.setStatus(WorkflowActionDb.STATE_FINISHED);
        re = wa.save();
        MyActionDb mad = new MyActionDb();
        mad = mad.getMyActionDb(myActionId);
        mad.setCheckDate(new java.util.Date());
        mad.setChecked(true);
        re = mad.save();
        WorkflowDb wfd = new WorkflowDb();
        wfd = wfd.getWorkflowDb(wa.getFlowId());
        // 检查流程中的节点是否都已完成
        if (wfd.checkStatusFinished()) {
            re = wfd.changeStatus(request, WorkflowDb.STATUS_FINISHED, wa);
        }
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        if (re) {
            String str = i18nUtil.get("info_op_success");
            json.put("ret", "1");
            json.put("msg", str);
        } else {
            String str = i18nUtil.get("info_op_fail");
            json.put("ret", "0");
            json.put("msg", str);
        }
        return json.toString();
    }

    @ResponseBody
    @RequestMapping(value = "/flow/renameAtt", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String renameAtt(HttpServletRequest request) {
        boolean re = false;
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();

        int attId = ParamUtil.getInt(request, "attId", -1);
        String newName = ParamUtil.get(request, "newName");
        String str1 = i18nUtil.get("info_op_success");
        String str_faile = i18nUtil.get("info_op_fail");
        com.redmoon.oa.flow.Attachment att = new com.redmoon.oa.flow.Attachment(attId);
        String name = att.getName();
        if (name.equals("")) {
            json.put("ret", "0");
            json.put("msg", i18nUtil.get("nameNotBeEmpty"));
            return json.toString();
        }
        if (!name.equals(newName)) {
            att.setName(newName);
            re = att.save();
        } else {
            json.put("ret", "0");
            json.put("msg", "名称相同");
            return json.toString();
        }
        if (re) {
            String str = i18nUtil.get("info_op_success");
            json.put("ret", "1");
            json.put("msg", str);
        } else {
            String str = i18nUtil.get("info_op_fail");
            json.put("ret", "0");
            json.put("msg", str);
        }
        return json.toString();
    }

    @ResponseBody
    @RequestMapping(value = "/flow/deliver", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String deliver(HttpServletRequest request) {
        WorkflowMgr wfm = new WorkflowMgr();
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        boolean re = false;
        int flowId = ParamUtil.getInt(request, "flowId", -1);
        try {
            re = wfm.deliverFree(request, flowId);
        } catch (ErrMsgException e) {
            json.put("ret", 0);
            json.put("msg", e.getMessage());
            LogUtil.getLog(getClass()).error(e);
            return json.toString();
        }
        if (re) {
            String str = i18nUtil.get("info_op_success");
            json.put("ret", "1");
            json.put("msg", str);
        } else {
            String str = i18nUtil.get("info_op_fail");
            json.put("ret", "0");
            json.put("msg", str);
        }
        return json.toString();
    }

    @ApiOperation(value = "回复", notes = "回复", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "flowId", value = "流程ID", required = true, dataType = "Long"),
            @ApiImplicitParam(name = "myActionId", value = "待办记录ID", required = false, dataType = "Long"),
            @ApiImplicitParam(name = "parentId", value = "被回复的ID", required = false, dataType = "Long"),
            @ApiImplicitParam(name = "content", value = "回复内容", required = true, dataType = "String"),
            @ApiImplicitParam(name = "isSecret", value = "是否私密", required = false, dataType = "Integer"),
            @ApiImplicitParam(name = "progress", value = "进度", required = false, dataType = "Integer"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/addReply", produces = {"application/json;charset=UTF-8;"})
    public Result<Object> addReply(@RequestParam(value="flowId", required = true)Integer flowId, @RequestParam(value="myActionId", defaultValue = "-1") Long myActionId, @RequestParam(value="parentId", defaultValue = "-1")Long parentId, @RequestParam(value="content", required = true) String content, @RequestParam(value="isSecret", defaultValue = "0") Integer isSecret, @RequestParam(value="progress", defaultValue = "0") Integer progress) {
        WorkflowAnnexMgr workflowAnnexMgr = new WorkflowAnnexMgr();
        String userName = authUtil.getUserName();
        String userRealName = userCache.getUser(userName).getRealName();
        boolean re = true;
        try {
            String replyName = "";
            long id = SequenceManager.nextID(SequenceManager.OA_FLOW_ANNEX);
            long actionId = 0;
            // 详情页回复时 myActionId为-1
            if (myActionId != -1){
                MyActionDb mad = new MyActionDb(myActionId);
                mad = mad.getMyActionDb(myActionId);
                actionId = mad.getActionId();
                replyName = mad.getUserName();
            }
            if (parentId != -1) {
                WorkflowAnnexDb workflowAnnexDb = new WorkflowAnnexDb();
                workflowAnnexDb = (WorkflowAnnexDb)workflowAnnexDb.getQObjectDb(parentId);
                replyName = workflowAnnexDb.getString("user_name");
            }

            String sql = "insert into flow_annex (id,flow_id,content,user_name,reply_name,add_date,action_id,is_secret,parent_id,progress) values(?,?,?,?,?,?,?,?,?,?)";
            re = workflowAnnexMgr.create(sql, new Object[]{id, flowId, content, userName, replyName, new Date(), actionId, isSecret, parentId, progress});

            if (re) {
                WorkflowDb wf = new WorkflowDb();
                wf = wf.getWorkflowDb(flowId);

                // 写入进度
                Leaf lf = new Leaf();
                lf = lf.getLeaf(wf.getTypeCode());
                String formCode = lf.getFormCode();
                FormDb fd = new FormDb();
                fd = fd.getFormDb(formCode);
                // 进度为0的时候不更新
                if (fd.isProgress() && progress > 0) {
                    com.redmoon.oa.flow.FormDAO fdao = new com.redmoon.oa.flow.FormDAO();
                    fdao = fdao.getFormDAOByCache(flowId, fd);
                    fdao.setCwsProgress(progress);
                    fdao.save();
                }

                String partakeUsers = "";
                MessageDb md = new MessageDb();
                String myAction = "action=" + MessageDb.ACTION_FLOW_SHOW + "|flowId=" + flowId;

                if (isSecret != 0) {//如果是隐藏“评论”，只提醒发起“意见”的人
                    if (!replyName.equals(userName)) {//如果发起“意见”的人不是自己，就提醒
                        md.sendSysMsg(replyName, "请注意查看我的流程：" + wf.getTitle(), userRealName + "发表了评论：<p>" + content + "</p>", myAction);
                    }
                } else {
                    //如果不是隐藏“评论”，提醒所有参与流程的人
                    //解析得到参与流程的所有人
                    JdbcTemplate jt = new JdbcTemplate();
                    String allUserListSql = "select distinct user_name from flow_my_action where flow_id=" + flowId + " order by receive_date asc";
                    ResultIterator ri1 = jt.executeQuery(allUserListSql);
                    ResultRecord rr1 = null;
                    while (ri1.hasNext()) {
                        rr1 = ri1.next();
                        partakeUsers += rr1.getString(1) + ",";
                    }
                    if (!"".equals(partakeUsers)) {
                        partakeUsers = partakeUsers.substring(0, partakeUsers.length() - 1);
                    }
                    String[] partakeUsersArr = StrUtil.split(partakeUsers, ",");
                    for (String user : partakeUsersArr) {
                        //如果不是自己就提醒
                        if (!user.equals(userName)) {
                            md.sendSysMsg(user, "请注意查看我的流程：" + wf.getTitle(), userRealName + "发表了评论：<p>" + content + "</p>", myAction);
                        }
                    }
                }
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        return new Result<>(re);
    }

    /**
     * 提交“评论”
     *
     * @param request
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/addReplyXXX", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String addReplyXXX(HttpServletRequest request) {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        // flow_dispose.jsp回复
        String myActionId = ParamUtil.get(request, "myActionId");//当前活跃的标志id
        long flowId = ParamUtil.getLong(request, "flow_id", -1);//当前流程id
        long actionId = ParamUtil.getLong(request, "action_id", -1);//当前流程action的id
        String replyContent = request.getParameter("content");//“评论”的内容
        String userRealName = request.getParameter("userRealName");
        String userName = request.getParameter("user_name");
        String replyName = request.getParameter("reply_name");
        int parentId = ParamUtil.getInt(request, "parent_id", -1);

        UserMgr um = new UserMgr();
        UserDb oldUser = um.getUserDb(userName);
        UserDb replyUser = um.getUserDb(replyName);

        String partakeUsers = "";
        int isSecret = ParamUtil.getInt(request, "isSecret", 0);//此“评论”是否隐藏
        //将数据插入flow_annex附言表中
        long id = SequenceManager.nextID(SequenceManager.OA_FLOW_ANNEX);
        String currentDate = DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss");
        String myDate = currentDate;

        int progress = ParamUtil.getInt(request, "cwsProgress", 0);
        StringBuilder sql = new StringBuilder("insert into flow_annex (id,flow_id,content,user_name,reply_name,add_date,action_id,is_secret,parent_id,progress) values(");
        sql.append(id).append(",").append(flowId).append(",").append(StrUtil.sqlstr(replyContent))
                .append(",").append(StrUtil.sqlstr(userName)).append(",").append(StrUtil.sqlstr(replyName))
                .append(",").append(StrUtil.sqlstr(myDate)).append(",").append(actionId).append(",").append(isSecret).append(",").append(parentId).append(",").append(progress).append(")");
        JdbcTemplate jt = new JdbcTemplate();
        try {
            jt.executeUpdate(sql.toString());

            //不管来源于“代办流程”还是“我的流程”，跳转之后都进入“我的流程”。如果这条回复是私密的，只给交流双方发送消息提醒，不然就给这条流程的每个人都发送一条消息提醒
            WorkflowDb wf = new WorkflowDb((int) flowId);

            // 写入进度
            Leaf lf = new Leaf();
            lf = lf.getLeaf(wf.getTypeCode());
            String formCode = lf.getFormCode();
            FormDb fd = new FormDb();
            fd = fd.getFormDb(formCode);
            // 进度为0的时候不更新
            if (fd.isProgress() && progress > 0) {
                com.redmoon.oa.flow.FormDAO fdao = new com.redmoon.oa.flow.FormDAO();
                fdao = fdao.getFormDAOByCache((int) flowId, fd);
                fdao.setCwsProgress(progress);
                fdao.save();
            }

            MessageDb md = new MessageDb();
            String myAction = "action=" + MessageDb.ACTION_FLOW_SHOW + "|flowId=" + flowId;
            MyActionDb mad = new MyActionDb();
            if (!myActionId.equals("")) {
                mad = mad.getMyActionDb(Long.parseLong(myActionId));
            }
            if (isSecret != 0) {//如果是隐藏“评论”，只提醒发起“意见”的人
                if (!replyName.equals(userName)) {//如果发起“意见”的人不是自己，就提醒
                    if (!myActionId.equals("")) {
                        md.sendSysMsg(replyName, "请注意查看我的流程：" + wf.getTitle(), userRealName + "对意见：" + mad.getResult() + "发表了评论：<p>" + replyContent + "</p>", myAction);
                    } else {
                        md.sendSysMsg(replyName, "请注意查看我的流程：" + wf.getTitle(), userRealName + "发表了评论：<p>" + replyContent + "</p>", myAction);
                    }
                }
            } else {
                //如果不是隐藏“评论”，提醒所有参与流程的人
                //解析得到参与流程的所有人
                String allUserListSql = "select distinct user_name from flow_my_action where flow_id=" + flowId + " order by receive_date asc";
                ResultIterator ri1 = jt.executeQuery(allUserListSql);
                ResultRecord rr1 = null;
                while (ri1.hasNext()) {
                    rr1 = (ResultRecord) ri1.next();
                    partakeUsers += rr1.getString(1) + ",";
                }
                if (!partakeUsers.equals("")) {
                    partakeUsers = partakeUsers.substring(0, partakeUsers.length() - 1);
                }
                String[] partakeUsersArr = StrUtil.split(partakeUsers, ",");
                for (String user : partakeUsersArr) {
                    //如果不是自己就提醒
                    if (!user.equals(userName)) {
                        if (!myActionId.equals("")) {
                            md.sendSysMsg(user, "请注意查看我的流程：" + wf.getTitle(), userRealName + "对意见：" + mad.getResult() + "发表了评论：<p>" + replyContent + "</p>", myAction);
                        } else {
                            md.sendSysMsg(user, "请注意查看我的流程：" + wf.getTitle(), userRealName + "发表了评论：<p>" + replyContent + "</p>", myAction);
                        }
                    }
                }
            }

            json.put("ret", "1");
            json.put("myDate", currentDate);

            StringBuilder sr = new StringBuilder();
            WorkflowPredefineDb wpd = new WorkflowPredefineDb();
            wpd = wpd.getPredefineFlowOfFree(wf.getTypeCode());

            String othersHidden = SkinUtil.LoadStr(request, "res.flow.Flow", "othersHidden");
            String needHidden = SkinUtil.LoadStr(request, "res.flow.Flow", "needHidden");
            String replyTo = SkinUtil.LoadStr(request, "res.flow.Flow", "replyTo");
            String sure = SkinUtil.LoadStr(request, "res.flow.Flow", "sure");
            if (wpd.isLight()) {
                if (parentId == -1) {
                    sr.append("<tr><td width=\"50\" class=\"nameColor\" style=\"text-align:left;\">")
                            .append(replyUser.getRealName()).append(":</td>")
                            .append("<td width=\"70%\" style=\"text-align:left;word-break:break-all;\">").append(replyContent).append("</td>")
                            .append("<td style=\"text-align:right;\">").append(myDate).append("&nbsp;&nbsp;&nbsp;&nbsp;")
                            .append("<a align=\"right\" class=\"comment\" href=\"javascript:;\" onclick=\"addMyReply(").append(id).append(") \">")
                            .append("<img title=\"").append(replyTo).append("\" src=\"images/dateline/replyto.png\"/></a></td></tr>")
                            // .append("<tr id=trline0 ><td colspan=3><hr/></td></tr>" );
                            .append("<tr id=trline").append(id).append(" ><td colspan=3><hr class=\"hrLine\"/></td></tr>");

                    sr.append("<tr><td colspan=3>").append("<div id=myReplyTextarea").append(id).append(" style='display:none; clear:both;position:relative;margin-bottom:40px'>")
                            .append("<form id=flowForm").append(id).append(" name=flowForm").append(id).append(" method=post >")
                            .append("<textarea name=content id=get").append(id).append(" class=myTextarea></textarea>")
                            .append("<input type=hidden name=myActionId value= >")
                            .append("<input type=hidden name=discussId value=").append(id).append(" >")
                            .append("<input type=hidden name=flow_id value=").append(flowId).append(" >")
                            .append("<input type=hidden name=action_id value=").append(actionId).append(" >")
                            .append("<input type=hidden name=user_name value=").append(userName).append(" >")
                            .append("<input type=hidden name=userRealName value=").append(userRealName).append(" >")
                            .append("<input type=hidden name=reply_name value=").append(replyName).append(" >")
                            .append("<input type=hidden name=parent_id value=").append(id).append(" >")
                            .append("<input type=hidden name=discussId value=").append(parentId).append(" >")
                            .append("<input class=mybtn value=").append(sure).append(" type=button onclick=submitPostscript(")
                            .append(id).append(",").append(id).append(") />")
                            .append("</form></div></td></tr>");
                } else {
                    sr.append("<tr><td width=\"180\" class=\"nameColor\" style=\"text-align:left;\">").append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;")
                            .append(replyUser.getRealName()).append("&nbsp;").append(replyTo).append("&nbsp;").append(oldUser.getRealName()).append(":</td>	")
                            .append("<td width=\"70%\" style=\"text-align:left;word-break:break-all;\">").append(replyContent).append("</td>")
                            .append("<td style=\"text-align:right;\">").append(myDate).append("&nbsp;&nbsp;&nbsp;&nbsp;")
                            .append("<a align=\"right\" class=\"comment\" href=\"javascript:;\" onclick=\"addMyReply(").append(id).append(") \">")
                            .append("<img title=\"").append(replyTo).append("\" src=\"images/dateline/replyto.png\"/></a></td></tr>");

                    sr.append("<tr><td colspan=3>").append("<div id=myReplyTextarea").append(id).append(" style='display:none; clear:both;position:relative;margin-bottom:40px'>")
                            .append("<form id=flowForm").append(id).append(" name=flowForm").append(id).append(" method=post >")
                            .append("<textarea name=content id=get").append(id).append(" class=myTextarea></textarea>")
                            .append("<input type=hidden name=myActionId value= >")
                            .append("<input type=hidden name=discussId value=").append(id).append(" >")
                            .append("<input type=hidden name=flow_id value=").append(flowId).append(" >")
                            .append("<input type=hidden name=action_id value=").append(actionId).append(" >")
                            .append("<input type=hidden name=user_name value=").append(replyName).append(" >")
                            .append("<input type=hidden name=userRealName value=").append(userRealName).append(" >")
                            .append("<input type=hidden name=reply_name value=").append(replyName).append(" >")
                            .append("<input type=hidden name=parent_id value=").append(parentId).append(" >")
                            .append("<input type=hidden name=discussId value=").append(parentId).append(" >")
                            .append("<input class=mybtn value=").append(sure).append(" type=button onclick=submitPostscript(")
                            .append(id).append(",").append(parentId).append(") />")
                            .append("</form></div></td></tr>");
                }
            } else {
                if (parentId == -1) {
                    sr.append("<tr><td width=\"50\" class=\"nameColor\" style=\"text-align:left;\">")
                            .append(replyUser.getRealName()).append(":</td>")
                            .append("<td width=\"70%\" style=\"text-align:left;word-break:break-all;\">").append(replyContent).append("</td>")
                            .append("<td style=\"text-align:right;\">").append(myDate).append("&nbsp;&nbsp;&nbsp;&nbsp;")
                            .append("<a align=\"right\" class=\"comment\" href=\"javascript:;\" onclick=\"addMyReply(").append(id).append(") \">")
                            .append("<img title=\"").append(replyTo).append("\" src=\"images/dateline/replyto.png\"/></a></td></tr>")
                            // .append("<tr id=trline0 ><td colspan=3><hr/></td></tr>" );
                            .append("<tr id=trline").append(id).append(" ><td colspan=3><hr class=\"hrLine\"/></td></tr>");

                    sr.append("<tr><td align=\"left\" colspan=3>").append("<div id=myReplyTextarea").append(id).append(" style='display:none; clear:both;position:relative;margin-bottom:40px'>")
                            .append("<form id=flowForm").append(id).append(" name=flowForm").append(id).append(" method=post >")
                            .append("<textarea name=content id=get").append(id).append(" class=myTextarea></textarea>")
                            .append("<span align=\"left\" title=\"").append(othersHidden).append("\" style=\"cursor:pointer;\" onclick=\"chooseHideComment(this);\"><img src=\"").append(SkinMgr.getSkinPath(request)).append("/images/admin/functionManage/checkbox_not.png\" />&nbsp;").append(needHidden).append("<input type=\"hidden\" id=\"isSecret0\" name=\"isSecret0\" value=\"0\"/></span>")
                            .append("<input type=hidden id=myActionId").append(id).append(" value= >")
                            .append("<input type=hidden id=discussId").append(id).append(" value=").append(id).append(" >")
                            .append("<input type=hidden id=flow_id").append(id).append(" value=").append(flowId).append(" >")
                            .append("<input type=hidden id=action_id").append(id).append(" value=").append(actionId).append(" >")
                            .append("<input type=hidden id=user_name").append(id).append(" value=").append(userName).append(" >")
                            .append("<input type=hidden id=userRealName").append(id).append(" value=").append(userRealName).append(" >")
                            .append("<input type=hidden id=reply_name").append(id).append(" value=").append(replyName).append(" >")
                            .append("<input type=hidden id=parent_id").append(id).append(" value=").append(id).append(" >")
                            .append("<input type=hidden id=discussId").append(id).append(" value=").append(parentId).append(" >")
                            .append("<input type=hidden id=isSecret").append(id).append(" value=").append(isSecret).append(" >")
                            .append("<input class=mybtn value=").append(sure).append(" type=button onclick=submitPostscript(")
                            .append(id).append(",").append(id).append(") />")
                            .append("</form></div></td></tr>");
                } else {
                    sr.append("<tr><td width=\"180\" class=\"nameColor\" style=\"text-align:left;\">").append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;")
                            .append(oldUser.getRealName()).append("&nbsp;").append(replyTo).append("&nbsp;").append(replyUser.getRealName()).append(":</td>	")
                            .append("<td style=\"text-align:left;\">").append(replyContent).append("</td>")
                            .append("<td style=\"text-align:right;\">").append(myDate).append("&nbsp;&nbsp;&nbsp;&nbsp;")
                            .append("<a align=\"right\" class=\"comment\" href=\"javascript:;\" onclick=\"addMyReply(").append(id).append(") \">")
                            .append("<img title=\"").append(replyTo).append("\" src=\"images/dateline/replyto.png\"/></a></td></tr>");

                    sr.append("<tr><td align=\"left\" colspan=3>").append("<div id=myReplyTextarea").append(id).append(" style='display:none; clear:both;position:relative;margin-bottom:40px'>")
                            .append("<form id=flowForm").append(id).append(" name=flowForm").append(id).append(" method=post >")
                            .append("<textarea name=content id=get").append(id).append(" class=myTextarea></textarea>")
                            .append("<span align=\"left\" title=\"").append(othersHidden).append("\" style=\"cursor:pointer;\" onclick=\"chooseHideComment(this);\"><img src=\"").append(SkinMgr.getSkinPath(request)).append("/images/admin/functionManage/checkbox_not.png\" />&nbsp;").append(needHidden).append("<input type=\"hidden\" id=\"isSecret").append(id).append("\" name=\"isSecret").append(id).append("\" value=\"0\"/></span>")
                            .append("<input type=hidden id=myActionId").append(id).append(" value= >")
                            .append("<input type=hidden id=discussId").append(id).append(" value=").append(id).append(" >")
                            .append("<input type=hidden id=flow_id").append(id).append(" value=").append(flowId).append(" >")
                            .append("<input type=hidden id=action_id").append(id).append(" value=").append(actionId).append(" >")
                            .append("<input type=hidden id=user_name").append(id).append(" value=").append(userName).append(" >")
                            .append("<input type=hidden id=userRealName").append(id).append(" value=").append(userRealName).append(" >")
                            .append("<input type=hidden id=reply_name").append(id).append(" value=").append(userName).append(" >")
                            .append("<input type=hidden id=parent_id").append(id).append(" value=").append(parentId).append(" >")
                            .append("<input type=hidden id=discussId").append(id).append(" value=").append(parentId).append(" >")
                            .append("<input type=hidden id=isSecret").append(id).append(" value=").append(isSecret).append(" >")
                            .append("<input class=mybtn value=").append(sure).append(" type=button onclick=submitPostscript(")
                            .append(id).append(",").append(parentId).append(") />")
                            .append("</form></div></td></tr>");
                }
            }

            json.put("result", sr.toString());
        } catch (ErrMsgException e) {
            json.put("ret", "0");
            json.put("msg", e.getMessage());
            LogUtil.getLog(getClass()).error(e);
        } catch (SQLException e) {
            json.put("ret", "0");
            json.put("msg", e.getMessage());
            LogUtil.getLog(getClass()).error(e);
        }
        return json.toString();
    }

    /**
     * flowShowPage.do、flow_dispose_free.jsp中回复
     *
     * @param request
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/addReplyDispose", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String addReplyDispose(HttpServletRequest request) {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        String myActionId = ParamUtil.get(request, "myActionId");//当前活跃的标志id
        long flowId = ParamUtil.getLong(request, "flow_id", -1);//当前流程id
        long actionId = ParamUtil.getLong(request, "action_id", -1);//当前流程action的id
        String replyContent = ParamUtil.get(request, "content");//“评论”的内容
        String userRealName = ParamUtil.get(request, "userRealName");//发起“评论”人真实姓名
        String userName = ParamUtil.get(request, "user_name");
        String replyName = ParamUtil.get(request, "reply_name");
        int parentId = ParamUtil.getInt(request, "parent_id", -1);

        UserMgr um = new UserMgr();
        UserDb oldUser = um.getUserDb(userName);
        UserDb replyUser = um.getUserDb(replyName);

        String partakeUsers = "";
        int isSecret = ParamUtil.getInt(request, "isSecret", 0);//此“评论”是否隐藏
        //将数据插入flow_annex附言表中
        long id = (long) SequenceManager.nextID(SequenceManager.OA_FLOW_ANNEX);
        String currentDate = DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss");
        String myDate = currentDate;

        int progress = ParamUtil.getInt(request, "cwsProgress", 0);

        StringBuilder sql = new StringBuilder("insert into flow_annex (id,flow_id,content,user_name,reply_name,add_date,action_id,is_secret,parent_id,progress) values(");
        sql.append(id).append(",").append(flowId).append(",").append(StrUtil.sqlstr(replyContent))
                .append(",").append(StrUtil.sqlstr(userName)).append(",").append(StrUtil.sqlstr(replyName))
                .append(",").append(StrUtil.sqlstr(myDate)).append(",").append(actionId).append(",").append(isSecret).append(",").append(parentId).append(",").append(progress).append(")");
        JdbcTemplate jt = new JdbcTemplate();
        try {
            jt.executeUpdate(sql.toString());

            //不管来源于“代办流程”还是“我的流程”，跳转之后都进入“我的流程”。如果这条回复是私密的，只给交流双方发送消息提醒，不然就给这条流程的每个人都发送一条消息提醒
            WorkflowDb wf = new WorkflowDb((int) flowId);

            // 写入进度
            Leaf lf = new Leaf();
            lf = lf.getLeaf(wf.getTypeCode());
            String formCode = lf.getFormCode();
            FormDb fd = new FormDb();
            fd = fd.getFormDb(formCode);
            // 进度为0的时候不更新
            if (fd.isProgress() && progress > 0) {
                com.redmoon.oa.flow.FormDAO fdao = new com.redmoon.oa.flow.FormDAO();
                fdao = fdao.getFormDAOByCache((int) flowId, fd);
                fdao.setCwsProgress(progress);
                fdao.save();
            }

            MessageDb md = new MessageDb();
            String myAction = "action=" + MessageDb.ACTION_FLOW_SHOW + "|flowId=" + flowId;
            MyActionDb mad = new MyActionDb();
            if (!myActionId.equals("")) {
                mad = mad.getMyActionDb(Long.parseLong(myActionId));
            }
            if (isSecret != 0) {//如果是隐藏“评论”，只提醒发起“意见”的人
                if (!replyName.equals(userName)) {//如果发起“意见”的人不是自己，就提醒
                    if (!myActionId.equals("")) {
                        md.sendSysMsg(replyName, "请注意查看我的流程：" + wf.getTitle(), userRealName + "对意见：" + mad.getResult() + "发表了评论：<p>" + replyContent + "</p>", myAction);
                    } else {
                        md.sendSysMsg(replyName, "请注意查看我的流程：" + wf.getTitle(), userRealName + "发表了评论：<p>" + replyContent + "</p>", myAction);
                    }
                }
            } else {
                //如果不是隐藏“评论”，提醒所有参与流程的人
                //解析得到参与流程的所有人
                String allUserListSql = "select distinct user_name from flow_my_action where flow_id=" + flowId + " order by receive_date asc";
                ResultIterator ri1 = jt.executeQuery(allUserListSql);
                ResultRecord rr1 = null;
                while (ri1.hasNext()) {
                    rr1 = (ResultRecord) ri1.next();
                    partakeUsers += rr1.getString(1) + ",";
                }
                if (!partakeUsers.equals("")) {
                    partakeUsers = partakeUsers.substring(0, partakeUsers.length() - 1);
                }
                String[] partakeUsersArr = StrUtil.split(partakeUsers, ",");
                for (String user : partakeUsersArr) {
                    //如果不是自己就提醒
                    if (!user.equals(userName)) {
                        if (!myActionId.equals("")) {
                            md.sendSysMsg(user, "请注意查看我的流程：" + wf.getTitle(), userRealName + "对意见：" + mad.getResult() + "发表了评论：<p>" + replyContent + "</p>", myAction);
                        } else {
                            md.sendSysMsg(user, "请注意查看我的流程：" + wf.getTitle(), userRealName + "发表了评论：<p>" + replyContent + "</p>", myAction);
                        }
                    }
                }
            }

            json.put("ret", "1");
            json.put("myDate", currentDate);

            String othersHidden = SkinUtil.LoadStr(request, "res.flow.Flow", "othersHidden");
            String needHidden = SkinUtil.LoadStr(request, "res.flow.Flow", "needHidden");
            String replyTo = SkinUtil.LoadStr(request, "res.flow.Flow", "replyTo");
            String sure = SkinUtil.LoadStr(request, "res.flow.Flow", "sure");
            StringBuilder sr = new StringBuilder();
            if (parentId == -1) {
                sr.append("<tr><td width=\"50\" class=\"nameColor\" style=\"text-align:left;\">")
                        .append(replyUser.getRealName()).append(":</td>")
                        .append("<td width=\"70%\" style=\"text-align:left;word-break:break-all;\">").append(replyContent).append("</td>")
                        .append("<td style=\"text-align:right;\">").append(myDate).append("&nbsp;&nbsp;&nbsp;&nbsp;")
                        .append("<a align=\"right\" class=\"comment\" href=\"javascript:;\" onclick=\"addMyReply(").append(id).append(") \">")
                        .append("<img title=\"").append(replyTo).append("\" src=\"images/dateline/replyto.png\"/></a></td></tr>")
                        // .append("<tr id=trline0 ><td colspan=3><hr/></td></tr>" );
                        .append("<tr id=trline").append(id).append(" ><td colspan=3><hr class=\"hrLine\"/></td></tr>");

                sr.append("<tr><td align=\"left\" colspan=3>").append("<div id=myReplyTextarea").append(id).append(" style='display:none; clear:both;position:relative;margin-bottom:40px'>")
                        .append("<textarea name=content id=get").append(id).append(" class=myTextarea></textarea>")
                        .append("<span align=\"left\" title=\"").append(othersHidden).append("\" style=\"cursor:pointer;\" onclick=\"chooseHideComment(this);\"><img src=\"").append(SkinMgr.getSkinPath(request)).append("/images/admin/functionManage/checkbox_not.png\" />&nbsp;").append(needHidden).append("<input type=\"hidden\" id=\"isSecret0\" name=\"isSecret0\" value=\"0\"/></span>")
                        .append("<input type=hidden id=myActionId").append(id).append(" value= >")
                        .append("<input type=hidden id=discussId").append(id).append(" value=").append(id).append(" >")
                        .append("<input type=hidden id=flow_id").append(id).append(" value=").append(flowId).append(" >")
                        .append("<input type=hidden id=action_id").append(id).append(" value=").append(actionId).append(" >")
                        .append("<input type=hidden id=user_name").append(id).append(" value=").append(userName).append(" >")
                        .append("<input type=hidden id=userRealName").append(id).append(" value=").append(userRealName).append(" >")
                        .append("<input type=hidden id=reply_name").append(id).append(" value=").append(replyName).append(" >")
                        .append("<input type=hidden id=parent_id").append(id).append(" value=").append(id).append(" >")
                        .append("<input type=hidden id=discussId").append(id).append(" value=").append(parentId).append(" >")
                        .append("<input type=hidden id=isSecret").append(id).append(" value=").append(isSecret).append(" >")
                        .append("<input class=mybtn value=").append(sure).append(" type=button onclick=submitPostscript(")
                        .append(id).append(",").append(id).append(") />")
                        .append("</div></td></tr>");
            } else {
                sr.append("<tr><td width=\"180\" class=\"nameColor\" style=\"text-align:left;\">").append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;")
                        .append(oldUser.getRealName()).append("&nbsp;").append(replyTo).append("&nbsp;").append(replyUser.getRealName()).append(":</td>	")
                        .append("<td width=\"70%\" style=\"text-align:left;word-break:break-all;\">").append(replyContent).append("</td>")
                        .append("<td style=\"text-align:right;\">").append(myDate).append("&nbsp;&nbsp;&nbsp;&nbsp;")
                        .append("<a align=\"right\" class=\"comment\" href=\"javascript:;\" onclick=\"addMyReply(").append(id).append(") \">")
                        .append("<img title=\"").append(replyTo).append("\" src=\"images/dateline/replyto.png\"/></a></td></tr>");

                sr.append("<tr><td align=\"left\" colspan=3>").append("<div id=myReplyTextarea").append(id).append(" style='display:none; clear:both;position:relative;margin-bottom:40px'>")
                        .append("<textarea name=content id=get").append(id).append(" class=myTextarea></textarea>")
                        .append("<span align=\"left\" title=\"").append(othersHidden).append("\" style=\"cursor:pointer;\" onclick=\"chooseHideComment(this);\"><img src=\"").append(SkinMgr.getSkinPath(request)).append("/images/admin/functionManage/checkbox_not.png\" />&nbsp;").append(needHidden).append("<input type=\"hidden\" id=\"isSecret").append(id).append("\" name=\"isSecret").append(id).append("\" value=\"0\"/></span>")
                        .append("<input type=hidden id=myActionId").append(id).append(" value= >")
                        .append("<input type=hidden id=discussId").append(id).append(" value=").append(id).append(" >")
                        .append("<input type=hidden id=flow_id").append(id).append(" value=").append(flowId).append(" >")
                        .append("<input type=hidden id=action_id").append(id).append(" value=").append(actionId).append(" >")
                        .append("<input type=hidden id=user_name").append(id).append(" value=").append(userName).append(" >")
                        .append("<input type=hidden id=userRealName").append(id).append(" value=").append(userRealName).append(" >")
                        .append("<input type=hidden id=reply_name").append(id).append(" value=").append(userName).append(" >")
                        .append("<input type=hidden id=parent_id").append(id).append(" value=").append(parentId).append(" >")
                        .append("<input type=hidden id=discussId").append(id).append(" value=").append(parentId).append(" >")
                        .append("<input type=hidden id=isSecret").append(id).append(" value=").append(isSecret).append(" >")
                        .append("<input class=mybtn value=").append(sure).append(" type=button onclick=submitPostscript(")
                        .append(id).append(",").append(parentId).append(") />")
                        .append("</div></td></tr>");
            }

            json.put("result", sr.toString());
        } catch (SQLException e) {
            LogUtil.getLog(getClass()).error(e);
            json.put("ret", 0);
            json.put("msg", e.getMessage());
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
        }
        return json.toString();
    }

    @ResponseBody
    @RequestMapping(value = "/flow/finishAction", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String finishAction(HttpServletRequest request) {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        try {
            // 将场景置为流程
            threadContext.setSceneFlow();
            json = workflowService.finishAction(request, new com.redmoon.oa.pvg.Privilege());
            if (json.containsKey("ret") && "0".equals(json.getString("ret"))) {
                json.put("code", CommonConstant.SC_INTERNAL_SERVER_ERROR_500);
            } else {
                json.put("code", CommonConstant.SC_OK_200);
            }

            // 自动存档
            if ("AutoSaveArchiveNodeCommit".equals(json.getString("operate"))) {
                WorkflowMgr wfm = new WorkflowMgr();
                WorkflowDb wf = new WorkflowDb();
                wf = wf.getWorkflowDb(json.getIntValue("flowId"));
                WorkflowActionDb wa = new WorkflowActionDb();
                wa = wa.getWorkflowActionDb(json.getIntValue("actionId"));
                wfm.autoSaveArchive(request, wf, wa);
            }
        }
        catch (ErrMsgException | ClassCastException | NullPointerException | IllegalArgumentException e) {
            LogUtil.getLog(getClass()).error(e);
            // 置为异常状态
            threadContext.setAbnormal(true);
            json.put("ret", 0);
            json.put("code", CommonConstant.SC_INTERNAL_SERVER_ERROR_500);
            json.put("msg", e.getMessage());
            json.put("op", "Exception occured in finishAction");
        }
        finally {
            // 清缓存
            threadContext.remove();
        }
        return json.toString();
    }

    @ResponseBody
    @RequestMapping(value = "/flow/finishActionFree", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String finishActionFree(HttpServletRequest request) {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        try {
            // 将场景置为流程
            threadContext.setSceneFlow();
            json = workflowService.finishActionFree(request, new com.redmoon.oa.pvg.Privilege());
        } catch (ErrMsgException | ClassCastException | NullPointerException | IllegalArgumentException e) {
            // 置为异常状态
            threadContext.setAbnormal(true);
            json.put("ret", 0);
            json.put("msg", e.getMessage());
            json.put("op", "");
            LogUtil.getLog(getClass()).error(e);
        } finally {
            // 清缓存
            threadContext.remove();
        }
        return json.toString();
    }

    @ResponseBody
    @RequestMapping(value = "/flow/favorite", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> favorite(@RequestParam(value="flowId", required = true) Integer flowId) {
        boolean re;
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        WorkflowFavoriteDb wfd = new WorkflowFavoriteDb();
        try {
            if (wfd.isExist(authUtil.getUserName(), flowId)) {
                String str = i18nUtil.get("processBeConcerned");
                throw new ErrMsgException(str);
            }
            re = wfd.create(new JdbcTemplate(), new Object[]{flowId, authUtil.getUserName(), new java.util.Date(), new Integer(0)});
        } catch (ErrMsgException | ResKeyException e) {
            json.put("res", 1);
            json.put("msg", e.getMessage().replace("\\r", "<BR />"));
            return new Result<>(json);
        }
        if (re) {
            json.put("res", 0);
            String str = i18nUtil.get("info_op_success");
            json.put("msg", str);
        } else {
            json.put("res", 1);
            String str = i18nUtil.get("info_op_fail");
            json.put("msg", str);
        }
        return new Result<>(json);
    }

    @ResponseBody
    @RequestMapping(value = "/flow/unfavorite", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> unfavorite(@RequestParam(value="flowId", required = true) Integer flowId) {
        boolean re;
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        WorkflowFavoriteDb wfd = new WorkflowFavoriteDb();
        try {
            wfd = wfd.getWorkflowFavoriteDb(authUtil.getUserName(), flowId);
            if (wfd != null) {
                re = wfd.del();
            } else {
                String str = i18nUtil.get("notAlreadyExist");
                throw new ErrMsgException(str);
            }
        } catch (ErrMsgException | ResKeyException e) {
            json.put("res", 1);
            json.put("msg", e.getMessage().replace("\\r", "<BR />"));
            // LogUtil.getLog(getClass()).error(e);
            return new Result<>(json);
        }
        if (re) {
            json.put("res", 0);
            String str = i18nUtil.get("info_op_success");
            json.put("msg", str);
        } else {
            json.put("res", 1);
            String str = i18nUtil.get("info_op_fail");
            json.put("msg", str);
        }
        return new Result<>(json);
    }

    @ResponseBody
    @RequestMapping(value = "/flow/refreshFlow", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String refreshFlow(HttpServletRequest request) {
        boolean re = false;
        int flowId = ParamUtil.getInt(request, "flowId", -1);
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);
        try {
            wf.refreshFlow();
        } catch (ErrMsgException e) {
            json.put("ret", "0");
            json.put("msg", e.getMessage().replace("\\r", "<BR />"));
            return json.toString();
        }

        json.put("ret", "1");
        String str = i18nUtil.get("info_op_success");
        json.put("msg", str);
        return json.toString();
    }

    @ResponseBody
    @RequestMapping(value = "/flow/refreshFlowBatch", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String refreshFlowBatch(HttpServletRequest request) {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        String ids = ParamUtil.get(request, "ids");
        String[] ary = StrUtil.split(ids, ",");
        for (String s : ary) {
            int flowId = StrUtil.toInt(s, -1);

            WorkflowDb wf = new WorkflowDb();
            wf = wf.getWorkflowDb(flowId);
            try {
                wf.refreshFlow();
            } catch (ErrMsgException e) {
                LogUtil.getLog(getClass()).error(e);
                json.put("ret", "0");
                json.put("msg", e.getMessage().replace("\\r", "<BR />"));
                return json.toString();
            }
        }

        json.put("ret", "1");
        String str = i18nUtil.get("info_op_success");
        json.put("msg", str);
        return json.toString();
    }

    @ResponseBody
    @RequestMapping(value = "/flow/discardFlowBatch", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String discardFlowBatch(HttpServletRequest request) {
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        String ids = ParamUtil.get(request, "ids");
        String[] ary = StrUtil.split(ids, ",");
        String userName = privilege.getUser(request);
        for (int i = 0; i < ary.length; i++) {
            int flowId = StrUtil.toInt(ary[i], -1);
            WorkflowDb wf = new WorkflowDb();
            wf = wf.getWorkflowDb(flowId);
            try {
                wf.discard(userName);
            } catch (ErrMsgException e) {
                LogUtil.getLog(getClass()).error(e);
                json.put("ret", "0");
                json.put("msg", e.getMessage().replace("\\r", "<BR />"));
                return json.toString();
            }
        }

        json.put("ret", "1");
        String str = i18nUtil.get("info_op_success");
        json.put("msg", str);
        return json.toString();
    }

    /**
     * 调试模块中配置应用属性
     *
     * @param request
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/applyProps", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> applyProps(HttpServletRequest request) {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        String fieldWrite = ParamUtil.get(request, "fieldWrite");
        String fieldHide = ParamUtil.get(request, "fieldHide");
        int flowId = ParamUtil.getInt(request, "flowId", -1);
        int actionId = ParamUtil.getInt(request, "actionId", -1);

        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);

        WorkflowPredefineDb wpd = new WorkflowPredefineDb();
        wpd = wpd.getDefaultPredefineFlow(wf.getTypeCode());
        WorkflowActionDb wad = new WorkflowActionDb();
        wad = wad.getWorkflowActionDb(actionId);

        WorkflowDb wfDefault = new WorkflowDb();
        wfDefault.setFlowString(wpd.getFlowString());
        wfDefault.setFlowJson(wpd.getFlowJson());

        WorkflowActionDb waDefault = null;

        try {
            java.util.Vector v = wfDefault.getActionsFromString(wfDefault.getFlowString());
            java.util.Iterator ir = v.iterator();
            while (ir.hasNext()) {
                WorkflowActionDb wa = (WorkflowActionDb) ir.next();
                if (wa.getInternalName().equals(wad.getInternalName())) {
                    wa.setFieldWrite(fieldWrite);
                    wa.setFieldHide(fieldHide);

                    String item2 = wa.generateItem2();
                    wa.setItem2(item2);

                    waDefault = wa;
                    break;
                }
            }

            if (waDefault != null) {
                wfDefault.renewWorkflowString(waDefault, false);

                wpd.setFlowString(wfDefault.getFlowString());
                DebugUtil.i(getClass(), "applyProps", wfDefault.getFlowJson());
                wpd.setFlowJson(wfDefault.getFlowJson());
                wpd.save();
            }
            wf.refreshFlow();
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
            return new Result<>(false, e.getMessage());
        }

        json.put("ret", "1");
        String str = LocalUtil.LoadString(request, "info_op_success");
        json.put("msg", str);
        return new Result<>(true);
    }

    @ResponseBody
    @RequestMapping(value = "/flow/runValidateScript", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> runValidateScript(HttpServletRequest request) {
        int flowId = ParamUtil.getInt(request, "flowId", -1);
        int actionId = ParamUtil.getInt(request, "actionId", -1);
        FormDAOMgr fdm = new FormDAOMgr();
        BSHShell shell = null;
        try {
            shell = fdm.runValidateScript(request, flowId, actionId);
            if (shell == null) {
                return new Result<>("请检查脚本是否存在");
            } else {
                String errDesc = shell.getConsole().getLogDesc();
                // json.put("msg", StrUtil.toHtml(errDesc));
                return new Result<>(errDesc);
            }
        } catch (ErrMsgException e) {
            String errDesc = "";
            if (shell != null) {
                errDesc = shell.getConsole().getLogDesc();
            }
            if (!"".equals(errDesc)) {
                errDesc += "\r\n" + e.getMessage();
            } else {
                errDesc = e.getMessage();
            }
            // json.put("msg", StrUtil.toHtml(errDesc));
            return new Result<>(errDesc);
        }
    }

    @ResponseBody
    @RequestMapping(value = "/flow/runFinishScript", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> runFinishScript(HttpServletRequest request, @RequestParam(value = "flowId", required = false) int flowId, @RequestParam(value = "actionId", required = false) int actionId) throws ErrMsgException {
        String re = workflowService.runFinishScript(request, flowId, actionId);
        return new Result<>(re);
    }

    @ResponseBody
    @RequestMapping(value = "/flow/runDeliverScript", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> runDeliverScript(HttpServletRequest request) throws ErrMsgException {
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        int flowId = ParamUtil.getInt(request, "flowId", -1);
        long myActionId = ParamUtil.getInt(request, "myActionId", -1);

        BSHShell shell = null;

        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);

        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());
        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());

        FormDAO fdao = new FormDAO();
        fdao = fdao.getFormDAOByCache(flowId, fd);

        MyActionDb mad = new MyActionDb();
        mad = mad.getMyActionDb(myActionId);

        WorkflowActionDb wa = new WorkflowActionDb();
        wa = wa.getWorkflowActionDb((int) mad.getActionId());

        WorkflowPredefineDb wpd = new WorkflowPredefineDb();
        wpd = wpd.getDefaultPredefineFlow(wf.getTypeCode());
        WorkflowPredefineMgr wpm = new WorkflowPredefineMgr();
        String script = wpm.getActionFinishScript(wpd.getScripts(), wa.getInternalName());

        if (script != null && !"".equals(script.trim())) {
            WorkflowMgr wm = new WorkflowMgr();
            shell = wm.runDeliverScript(request, privilege.getUser(request), wf, fdao, mad, script, true);
        }

        if (shell == null) {
            return new Result<>("请检查脚本是否存在");
        } else {
            String errDesc = shell.getConsole().getLogDesc().trim();
            // json.put("msg", StrUtil.toHtml(errDesc));
            return new Result<>(errDesc);
        }
    }

    /**
     * 恢复流程
     *
     * @param request
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/recover", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String recover(HttpServletRequest request) {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        int flowId = ParamUtil.getInt(request, "flowId", -1);

        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);
        wf.setStatus(WorkflowDb.STATUS_STARTED);
        wf.save();

        // 其对应台帐记录的状态也得恢复为处理中
        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());
        FormDAO dao = (FormDAO)flowFormDaoCache.getFormDao(wf.getId(), lf.getFormCode());
        dao.setStatus(FormDAO.STATUS_NOT);
        dao.save();

        json.put("ret", "1");
        String str = i18nUtil.get("info_op_success");
        json.put("msg", str);
        return json.toString();
    }

    /**
     * 回复列表
     * @param flowId
     * @return
     */
    @ApiOperation(value = "回复列表", notes = "回复列表", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "flowId", value = "流程ID", required = true, dataType = "Integer"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/listAnnex", method = RequestMethod.POST, produces = {"application/json;charset=UTF-8;"})
    public Result<Object> listAnnex(@RequestParam(value = "flowId") Integer flowId) {
        com.alibaba.fastjson.JSONArray aryAnnex = new com.alibaba.fastjson.JSONArray();
        WorkflowAnnexDb wad = new WorkflowAnnexDb();
        Vector<WorkflowAnnexDb> vec1 = wad.listRoot(flowId, authUtil.getUserName());
        for (WorkflowAnnexDb workflowAnnexDb : vec1) {
            wad = workflowAnnexDb;

            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("realName", userCache.getUser(wad.getString("user_name")).getRealName());
            json.put("process", wad.getInt("progress"));
            json.put("content", wad.getString("content"));
            json.put("id", wad.getLong("id"));
            json.put("addDate", wad.getDate("add_date"));
            json.put("flowId", wad.getString("flow_id"));
            json.put("actionId", wad.getString("action_id"));
            json.put("userName", wad.getString("user_name"));
            json.put("parentId", wad.getLong("parent_id"));
            aryAnnex.add(json);

            WorkflowAnnexDb wad2 = new WorkflowAnnexDb();
            com.alibaba.fastjson.JSONArray aryAnnexSub = new com.alibaba.fastjson.JSONArray();
            Vector<WorkflowAnnexDb> vec2 = wad2.listChildren(wad.getInt("id"), authUtil.getUserName());
            for (WorkflowAnnexDb annexDb : vec2) {
                wad2 = annexDb;
                int id2 = (int) wad2.getLong("id");

                com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
                jsonObject.put("id", id2);
                jsonObject.put("userName", wad2.getString("user_name"));
                jsonObject.put("realName", userCache.getUser(wad2.getString("user_name")).getRealName());
                jsonObject.put("content", wad2.getString("content"));
                jsonObject.put("addDate", wad2.getDate("add_date"));
                jsonObject.put("flowId", wad2.getString("flow_id"));
                jsonObject.put("actionId", wad2.getString("action_id"));
                jsonObject.put("replyRealName", userCache.getUser(wad2.getString("reply_name")).getRealName());
                jsonObject.put("parentId", wad2.getLong("parent_id"));
                aryAnnexSub.add(jsonObject);
            }
            json.put("aryAnnexSub", aryAnnexSub);
        }
        return new Result<>(aryAnnex);
    }

    @ApiOperation(value = "删除回复", notes = "删除回复", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "回复ID", required = true, dataType = "Integer"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/delAnnex", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> delAnnex(@RequestParam(value = "id") Integer id) {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        WorkflowAnnexDb wad = new WorkflowAnnexDb();
        wad = (WorkflowAnnexDb) wad.getQObjectDb(id);
        boolean re = false;
        try {
            re = wad.del();
        } catch (ResKeyException e) {
            LogUtil.getLog(getClass()).error(e);
            return new Result<>(false, e.getMessage(request));
        }
        return new Result<>(re);
    }

    @ResponseBody
    @RequestMapping(value = "/flow/getTree", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String getTree(HttpServletRequest request) {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        Leaf lf = new Leaf();
        lf = lf.getLeaf("root");
        DirectoryView dv = new DirectoryView(lf);
        StringBuffer opts = new StringBuffer();
        try {
            dv.getDirectoryAsOptions(request, lf, lf.getLayer(), opts);
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
        }
        return opts.toString();
    }

    /**
     * 所有用户 字母A-Z排序
     *
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/public/flow/initUserList", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;"})
    public String initUserList() {
        int actionId = ParamUtil.getInt(request, "actionId", -1);
        String[] limitDeptArr = null;
        if (actionId != -1) {
            // 取出限定的部门
            WorkflowActionDb wad = new WorkflowActionDb();
            wad = wad.getWorkflowActionDb(actionId);
            String limitDepts = wad.getDept();
            limitDeptArr = StrUtil.split(limitDepts, ",");
        }

        FlowDoMgr flowDoMgr = new FlowDoMgr();
        com.alibaba.fastjson.JSONObject json = flowDoMgr.usersInitList(limitDeptArr);
        return json.toString();
    }

    /**
     * 发起流程列表初始化 listview 字母 A-Z排序
     *
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/public/flow/initFlowTypeList", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;"})
    public String initFlowTypeList(@RequestParam(value = "skey", required = false) String skey) {
        Privilege pvg = new Privilege();
        boolean re = pvg.auth(request);
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        if (!re) {
            json.put("ret", "0");
            json.put("msg", "权限非法！");
            return json.toString();
        }
        FlowDoMgr flowDoMgr = new FlowDoMgr();
        json = flowDoMgr.flowInitList(request, pvg.getUserName());
        return json.toString();
    }

    @ResponseBody
    @RequestMapping(value = "/flow/createNestSheetRelated", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<com.alibaba.fastjson.JSONObject> createNestSheetRelated() {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        try {
            // 将场景置为流程
            threadContext.setSceneFlow();
            json = workflowService.createNestSheetRelated(request);
        }
        catch (ErrMsgException | ClassCastException | NullPointerException | IllegalArgumentException e) {
            // 置为异常状态
            threadContext.setAbnormal(true);
            LogUtil.getLog(getClass()).error(e);
            json.put("res", 1);
            json.put("msg", e.getMessage());
            json.put("op", "");
        }
        finally {
            // 清缓存
            threadContext.remove();
        }
        return new Result<>(json);
    }

    @ResponseBody
    @RequestMapping(value = "/flow/updateNestSheetRelated", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<com.alibaba.fastjson.JSONObject> updateNestSheetRelated() {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        try {
            // 将场景置为流程
            threadContext.setSceneFlow();
            json = workflowService.updateNestSheetRelated(request);
        }
        catch (ErrMsgException | ClassCastException | NullPointerException | IllegalArgumentException e) {
            // 置为异常状态
            threadContext.setAbnormal(true);
            json.put("res", 1);
            json.put("msg", e.getMessage());
        }
        finally {
            // 清缓存
            threadContext.remove();
        }
        return new Result<>(json);
    }

    /**
     * 删除嵌套表中的附件
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/delNestSheetRelated", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String delNestSheetRelated() {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        String formCodeRelated = ParamUtil.get(request, "formCodeRelated");

        com.redmoon.oa.Config cfg = new com.redmoon.oa.Config();
        boolean isNestSheetCheckPrivilege = cfg.getBooleanProperty("isNestSheetCheckPrivilege");

        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        ModulePrivDb mpd = new ModulePrivDb(formCodeRelated);
        if (isNestSheetCheckPrivilege && !mpd.canUserManage(privilege.getUser(request))) {
            json.put("ret", "0");
            json.put("msg", cn.js.fan.web.SkinUtil.LoadString(request, "pvg_invalid"));
            return json.toString();
        }

        String moduleCode = ParamUtil.get(request, "moduleCode");
        if ("".equals(moduleCode)) {
            moduleCode = formCodeRelated;
        }

        FormMgr fm = new FormMgr();
        FormDb fdRelated = fm.getFormDb(formCodeRelated);
        com.redmoon.oa.visual.FormDAOMgr fdm = new com.redmoon.oa.visual.FormDAOMgr(fdRelated);
        try {
            boolean isNestSheet = true;
            if (fdm.del(request, isNestSheet, moduleCode)) {
                json.put("ret", "1");
                json.put("msg", "操作成功！");
            } else {
                json.put("ret", "0");
                json.put("msg", "操作失败！");
            }
        } catch (ErrMsgException e) {
            json.put("ret", "0");
            json.put("msg", e.getMessage());
            // LogUtil.getLog(getClass()).error(e);
        }
        return json.toString();
    }

    /**
     * 删除嵌套表中的附件
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/delAttachForNestSheetRelated", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String delAttachForNestSheetRelated() {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        String formCode = ParamUtil.get(request, "formCode"); // 主模块编码
        if ("".equals(formCode)) {
            json.put("ret", "0");
            json.put("msg", "编码不能为空！");
            return json.toString();
        }

        String formCodeRelated = ParamUtil.get(request, "formCodeRelated"); // 从模块编码

        com.redmoon.oa.Config cfg = new com.redmoon.oa.Config();
        boolean isNestSheetCheckPrivilege = cfg.getBooleanProperty("isNestSheetCheckPrivilege");
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        ModulePrivDb mpd = new ModulePrivDb(formCodeRelated);
        if (isNestSheetCheckPrivilege && !mpd.canUserManage(privilege.getUser(request))) {
            json.put("ret", "0");
            json.put("msg", cn.js.fan.web.SkinUtil.LoadString(request, "pvg_invalid"));
            return json.toString();
        }

        long attachId = ParamUtil.getLong(request, "attachId", -1);
        if (attachId == -1) {
            json.put("ret", "0");
            json.put("msg", SkinUtil.LoadString(request, "err_id"));
            return json.toString();
        }

        boolean re = false;
        com.redmoon.oa.visual.Attachment att = new com.redmoon.oa.visual.Attachment(attachId);
        if (att.isLoaded()) {
            re = att.del();
        }
        if (re) {
            json.put("ret", "1");
            json.put("msg", "操作成功！");
        } else {
            json.put("ret", "0");
            json.put("msg", "操作失败！");
        }
        return json.toString();
    }

    @ResponseBody
    @RequestMapping(value = "/flow/matchBranchAndUser", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<com.alibaba.fastjson.JSONObject> matchBranchAndUser(@RequestParam(value = "myActionId", required = false) long myActionId, @RequestParam(value = "actionId", required = false) int actionId) {
        try {
            return new Result<>(workflowService.matchBranchAndUser(request, myActionId, actionId));
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
            return new Result<>(false, e.getMessage());
        }
    }

    @ResponseBody
    @RequestMapping(value = "/flow/transfer", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String transfer(@RequestParam(value = "myActionId", required = false) long myActionId, @RequestParam(value = "toUserName", required = false) String toUserName) {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        MyActionDb myActionDb = new MyActionDb();
        myActionDb = myActionDb.getMyActionDb(myActionId);
        if (myActionDb.getCheckStatus()==MyActionDb.CHECK_STATUS_PASS) {
            json.put("ret", "0");
            json.put("msg", i18nUtil.get("nodeByOtherPersonal"));
            return json.toString();
        }

        WorkflowActionDb wa = new WorkflowActionDb();
        int actionId = (int)myActionDb.getActionId();
        wa = wa.getWorkflowActionDb(actionId);
        if ( wa==null || !wa.isLoaded()) {
            json.put("ret", "0");
            json.put("msg", i18nUtil.get("actionNotExist"));
            return json.toString();
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
                json.put("ret", "0");
                String str = i18nUtil.get("processStatus");
                String str1 = i18nUtil.get("mayHaveBeenProcess");
                json.put("msg", str + wa.getStatus() + "，"+str1);
                return json.toString();
            }
        }

        try {
            wfm.transfer(request, myActionId, toUserName);
        }
        catch (ErrMsgException e) {
            json.put("ret", "0");
            json.put("msg", e.getMessage());
            return json.toString();
        }
        json.put("ret", "1");
        json.put("msg", LocalUtil.LoadString(request,"res.common","info_op_success"));
        return json.toString();
    }

    @ApiOperation(value = "挂起流程", notes = "挂起流程", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "myActionId", value = "待办记录的ID", required = true, dataType = "Long"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/suspend", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> suspend(@RequestParam(value = "myActionId", required = false) Long myActionId) {
        MyActionDb myActionDb = new MyActionDb();
        myActionDb = myActionDb.getMyActionDb(myActionId);
        if (myActionDb.getCheckStatus()==MyActionDb.CHECK_STATUS_PASS) {
            return new Result<>(false, i18nUtil.get("nodeByOtherPersonal"));
        }

        WorkflowActionDb wa = new WorkflowActionDb();
        int actionId = (int)myActionDb.getActionId();
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
                return new Result<>(false, i18nUtil.get("processStatus") + wa.getStatus() + ", "+i18nUtil.get("mayHaveBeenProcess"));
            }
        }

        try {
            wfm.suspend(request, myActionId);
        }
        catch (ErrMsgException e) {
            return new Result<>(false, e.getMessage());
        }
        return new Result<>(true);
    }

    @ApiOperation(value = "恢复流程", notes = "恢复流程", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "myActionId", value = "待办记录的ID", required = true, dataType = "Long"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/resume", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> resume(@RequestParam(value = "myActionId", required = false) Long myActionId) {
        MyActionDb myActionDb = new MyActionDb();
        myActionDb = myActionDb.getMyActionDb(myActionId);
        if (myActionDb.getCheckStatus()==MyActionDb.CHECK_STATUS_PASS) {
            return new Result<>(false, i18nUtil.get("nodeByOtherPersonal"));
        }

        WorkflowActionDb wa = new WorkflowActionDb();
        int actionId = (int)myActionDb.getActionId();
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
                return new Result<>(false, i18nUtil.get("processStatus") + wa.getStatus() + ", "+i18nUtil.get("mayHaveBeenProcess"));
            }
        }

        try {
            wfm.resume(request, myActionId);
        }
        catch (ErrMsgException e) {
            return new Result<>(false, e.getMessage());
        }
        return new Result<>(true);
    }

    @ApiOperation(value = "删除流程", notes = "删除流程", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "flowId", value = "流程ID", required = true, dataType = "Integer"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/del", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> del(@RequestParam(value="flowId", required = true) Integer flowId) {
        boolean re;
        try {
            WorkflowMgr wm = new WorkflowMgr();
            re = wm.del(request, flowId);
        } catch (ErrMsgException e) {
            return new Result<>(responseUtil.getResJson(false, e.getMessage()));
        }
        return new Result<>(responseUtil.getResJson(re));
    }

    @ApiOperation(value = "删除流程中的附件", notes = "删除流程中的附件", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "flowId", value = "流程ID", required = true, dataType = "Integer"),
            @ApiImplicitParam(name = "attId", value = "附件ID", required = true, dataType = "Integer")
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/delAtt", method={RequestMethod.GET,RequestMethod.POST}, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> delAtt(@RequestParam(value="flowId", required = true) Integer flowId, @RequestParam(value="attId", required = true) Integer attId) {
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);
        Document doc = new Document();
        doc = doc.getDocument(wf.getDocId());
        DocContent dc = doc.getDocContent(1);
        Attachment att = new Attachment(attId);
        boolean re = dc.delAttachment(attId);
        if (re) {
            // 清空对应的field中的值
            String fieldName = att.getFieldName();
            if (!StringUtils.isEmpty(fieldName)) {
                if (flowId != -1) {
                    Leaf lf = new Leaf();
                    lf = lf.getLeaf(wf.getTypeCode());
                    FormDb fd = new FormDb();
                    fd = fd.getFormDb(lf.getFormCode());
                    if (fd.getFormField(fieldName) != null) {
                        FormDAO fdao = new FormDAO();
                        fdao = fdao.getFormDAOByCache(flowId, fd);
                        fdao.setFieldValue(fieldName, "");
                        try {
                            fdao.save();
                            // 如果需要记录历史
                            if (fd.isLog()) {
                                com.redmoon.oa.flow.FormDAO.log(authUtil.getUserName(), FormDAOLog.LOG_TYPE_EDIT, fdao);
                            }
                        } catch (ErrMsgException e) {
                            LogUtil.getLog(getClass()).error(e);
                        }
                    }
                    else {
                        DebugUtil.e(getClass(), "delAttach", "字段：" + fieldName + "不存在");
                    }
                }
            }
        }
        return new Result<>(re);
    }

    @ApiOperation(value = "删除文件宏控件中的附件", notes = "删除文件宏控件中的附件", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "flowId", value = "流程ID", required = true, dataType = "Integer"),
            @ApiImplicitParam(name = "attId", value = "附件ID", required = true, dataType = "Integer")
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/delAttach", method={RequestMethod.GET,RequestMethod.POST}, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> delAttach() {
        int flowId = ParamUtil.getInt(request, "flowId", -1);
        boolean re;
        int attachId = ParamUtil.getInt(request, "attachId", -1);
        if (flowId != -1) {
            int docId = ParamUtil.getInt(request, "docId", -1);
            Document doc = new Document();
            doc = doc.getDocument(docId);
            DocContent dc = doc.getDocContent(1);
            Attachment att = new Attachment(attachId);
            re = dc.delAttachment(attachId);
            if (re) {
                // 清空对应的field中的值
                String fieldName = att.getFieldName();
                if (!StringUtils.isEmpty(fieldName)) {
                    WorkflowDb wf = new WorkflowDb();
                    wf = wf.getWorkflowDb(flowId);
                    Leaf lf = new Leaf();
                    lf = lf.getLeaf(wf.getTypeCode());
                    FormDb fd = new FormDb();
                    fd = fd.getFormDb(lf.getFormCode());
                    if (fd.getFormField(fieldName) != null) {
                        FormDAO fdao = new FormDAO();
                        fdao = fdao.getFormDAOByCache(flowId, fd);
                        fdao.setFieldValue(fieldName, "");
                        try {
                            fdao.save();
                            // 如果需要记录历史
                            if (fd.isLog()) {
                                com.redmoon.oa.flow.FormDAO.log(authUtil.getUserName(), FormDAOLog.LOG_TYPE_EDIT, fdao);
                            }
                        } catch (ErrMsgException e) {
                            LogUtil.getLog(getClass()).error(e);
                        }
                    } else {
                        DebugUtil.e(getClass(), "delAttach", "字段：" + fieldName + "不存在");
                    }
                }
            }
        } else {
            com.redmoon.oa.visual.Attachment att = new com.redmoon.oa.visual.Attachment(attachId);
            re = att.del();
        }
        return new Result<>(re);
    }

    /**
     * 选择退回节点
     * @param flowId
     * @param actionId 当前节点的ID
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/returnAction", method={RequestMethod.GET,RequestMethod.POST}, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String returnAction(@RequestParam(value = "flowId", required = false)Integer flowId, @RequestParam(value = "actionId", required = false)Integer actionId) {
        com.alibaba.fastjson.JSONArray ary = new com.alibaba.fastjson.JSONArray();
        String sql = "select id from flow_my_action where flow_id=" + flowId + " and is_checked<>" + MyActionDb.CHECK_STATUS_NOT + " and is_checked<>" + MyActionDb.CHECK_STATUS_WAITING_TO_DO + " order by receive_date desc";
        MyActionDb mad = new MyActionDb();
        Vector v = mad.list(sql);
        Map<String, String> map = new HashMap<>();
        WorkflowActionDb wa = new WorkflowActionDb();

        wa = wa.getWorkflowActionDb(actionId);
        Vector<WorkflowActionDb> vcfrom = wa.getLinkFromActions();
        String userName = authUtil.getUserName();

        // 如果之前曾经处理过本节点，则说明有可能当前是被退回
        // 取得用户最早处理本节点的待办记录ID
        long firstMyActionId = -1;
        Iterator ir = v.iterator();
        while (ir.hasNext()) {
            mad = (MyActionDb) ir.next();
            if (mad.getActionId() == actionId && mad.getChecker().equals(userName)) {
                firstMyActionId = mad.getId();
            }
        }

        String checked = "";
        if (v.size()==1) {
            checked = "checked";
        }
        ir = v.iterator();
        while (ir.hasNext()) {
            mad = (MyActionDb)ir.next();

            // 如果存在最早处理记录，则退回时，mad需小于最早记录的ID，即只能退回在最早记录之前处理的用户
            if (firstMyActionId != -1) {
                if (mad.getId() >= firstMyActionId) {
                    continue;
                }
            }
            // 如果记录对应本节点，则跳过
            if (mad.getActionId() == actionId) {
                continue;
            }

            long aId = mad.getActionId();
            wa = wa.getWorkflowActionDb((int)aId);

            // 是否为兄弟节点
            boolean isSibling = false;
            Vector<WorkflowActionDb> vtfrom = wa.getLinkFromActions();
            for (WorkflowActionDb workflowActionDb : vcfrom) {
                for (WorkflowActionDb actionDb : vtfrom) {
                    if (workflowActionDb.getId() == actionDb.getId()) {
                        isSibling = true;
                        break;
                    }
                }
            }

            if (isSibling) {
                // 如果是兄弟节点，但还需再判断一下从该节点至当前节点有没有连线，如果有，则应允许返回至该兄弟节点
                // 比如从节点2与节点3是兄弟节点，但节点2至节点3有连线，则节点3返回时，应可返回至节点2
                // ----[ 节点1  ]-|-------------------|---[ 节点3  ]-----
                //                |-----[ 节点2  ]----|
                boolean hasLinkFromSibling = false;
                for (WorkflowActionDb workflowActionDb : vcfrom) {
                    if (workflowActionDb.getId() == wa.getId()) {
                        hasLinkFromSibling = true;
                        break;
                    }
                }
                if (!hasLinkFromSibling) {
                    continue;
                }
            }

            if (map.get(String.valueOf(aId))!=null) {
                continue;
            }

            map.put(String.valueOf(aId), String.valueOf(aId));

            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            ary.add(json);
            json.put("returnId", wa.getId());
            json.put("checked", checked);
            json.put("actionTitle", wa.getTitle());
            json.put("userRealName", wa.getUserRealName());
            json.put("checkDate", DateUtil.format(mad.getCheckDate(), "yyyy-MM-dd HH:mm"));
        }

        com.alibaba.fastjson.JSONObject jsonObject = responseUtil.getResJson(true);
        jsonObject.put("result", ary);
        return jsonObject.toString();
    }

    /**
     * 放弃流程
     * @param flowId 流程ID
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/discard", method={RequestMethod.GET,RequestMethod.POST}, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> discard(@RequestParam(value="flowId", required = true)Integer flowId) {
        String myname = authUtil.getUserName();
        WorkflowMgr wfm = new WorkflowMgr();
        boolean re;
        try {
            re = wfm.discard(request, myname, flowId);
        } catch (ErrMsgException e) {
            return new Result<>(responseUtil.getResJson(false, e.getMessage()));
        }

        return new Result<>(responseUtil.getResJson(re));
    }

    /**
     * 预览时下载文件
     * @param response
     * @param attName
     * @param visualPath
     * @param diskName
     * @throws ErrMsgException
     * @throws
     */
    @RequestMapping(value="/flow/downloadFile", method={RequestMethod.GET,RequestMethod.POST})
    public void downloadFile(HttpServletResponse response, @RequestParam(value="flowId", required = true)Integer flowId, @RequestParam(value="attachId", required = true)Integer attachId, @RequestParam(value = "attName", required = false)String attName, @RequestParam(value = "visualPath")String visualPath, @RequestParam(value = "diskName")String diskName) throws IOException, ErrMsgException {
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        String userName = privilege.getUser(request);
        // 判断是否超出下载次数限制
        AttachmentLogMgr alm = new AttachmentLogMgr();
        if (!alm.canDownload(userName, flowId, attachId)) {
            throw new ErrMsgException(alm.getErrMsg(request));
        }

        // 下载记录存至日志
        AttachmentLogMgr.log(userName, flowId, attachId, AttachmentLogDb.TYPE_DOWNLOAD);

        fileService.preview(response, visualPath + "/" + diskName);
    }

    /**
     * 预览
     * @param attachId
     * @param model
     * @return
     */
    @RequestMapping(value = "/flow/preview", method={RequestMethod.GET,RequestMethod.POST})
    public String preview(@RequestParam(value="flowId", required = true)Integer flowId, @RequestParam(value="attachId", required = true)Integer attachId, Model model) {
        if (attachId == -1) {
            model.addAttribute("msg", i18nUtil.get("err_id"));
            return "th/error/error";
        }

        Attachment att = new Attachment(attachId);
        if (!att.isLoaded()) {
            model.addAttribute("msg", "文件不存在，flowId=" + flowId + " id=" + attachId);
            return "th/error/error";
        }

        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        String userName = privilege.getUser(request);
        // 判断是否超出下载次数限制
        AttachmentLogMgr alm = new AttachmentLogMgr();
        if (!alm.canDownload(userName, flowId, attachId)) {
            model.addAttribute("msg", alm.getErrMsg(request));
            return "th/error/error";
        }

        if (StrUtil.isImage(StrUtil.getFileExt(att.getDiskName()))) {
            // 下载记录存至日志
            AttachmentLogMgr.log(userName, flowId, attachId, AttachmentLogDb.TYPE_DOWNLOAD);
            model.addAttribute("imgPath", att.getVisualPath() + "/" + att.getDiskName());
            return "th/img_show";
        }
        else {
			// 文件显示不友好，如：会显示为：preview.xlsx
            // return "forward:downloadFile.do?flowId=" + flowId + "&attachId=" + attachId + "&attName=" + StrUtil.UrlEncode(att.getName()) + "&visualPath=" + att.getVisualPath() + "&diskName=" + att.getDiskName();
            return "forward:download.do?flowId=" + flowId + "&attachId=" + attachId;
        }
    }

    /**
     * 下载文件
     * @param response
     * @param attachId
     * @param visitKey
     * @throws ErrMsgException
     */
    @RequestMapping(value="/flow/download", method={RequestMethod.GET,RequestMethod.POST})
    public void download(HttpServletResponse response, @RequestParam(value="flowId", required = true)Integer flowId, @RequestParam(value="attachId", required = true)Integer attachId, @RequestParam(value = "visitKey", required = false)String visitKey) throws IOException, ErrMsgException {
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        String userName = privilege.getUser(request);
        // 判断是否超出下载次数限制
        AttachmentLogMgr alm = new AttachmentLogMgr();
        if (!alm.canDownload(userName, flowId, attachId)) {
            try (BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream())) {
                response.setContentType("text/html;charset=utf-8");
                String str = alm.getErrMsg(request);
                bos.write(str.getBytes(StandardCharsets.UTF_8));
            }
            catch (final IOException e) {
                LogUtil.getLog(getClass()).error(e);
            }
            return;
        }

        Attachment att = new Attachment(attachId);
        if (!att.isLoaded()) {
            throw new ErrMsgException("文件不存在，flowId=" + flowId + " id=" + attachId);
        }

        String fileName = att.getName();
        String fileDiskPath = cn.js.fan.web.Global.getRealPath() + att.getVisualPath() + "/" + att.getDiskName();
        String op = ParamUtil.get(request, "op");
        if ("toPDF".equals(op)) {
            String fName = fileName.substring(0, fileName.lastIndexOf("."));
            fileName = fName + ".pdf";

            String diskName = att.getDiskName();
            String pdfName = diskName.substring(0, diskName.lastIndexOf("."));
            pdfName += ".pdf";

            /*
            String pdfPath = cn.js.fan.web.Global.getRealPath() + com.redmoon.kit.util.FileUpload.TEMP_PATH + "/" + fileName;
            if (com.redmoon.oa.util.PDFConverter.convert2PDF(fileDiskPath, pdfPath))
                fileDiskPath = pdfPath;
            */

            String pdfPath = cn.js.fan.web.Global.getRealPath() + att.getVisualPath() + "/" + pdfName;
            try {
                com.redmoon.oa.util.PDFConverter.convert2PDF(fileDiskPath, pdfPath);
            }
            catch (Exception e) {
                throw new ErrMsgException("转换PDF失败：" + e.getMessage());
            }
            // }
            fileDiskPath = pdfPath;
        }

        // 下载记录存至日志
        AttachmentLogMgr.log(userName, flowId, attachId, AttachmentLogDb.TYPE_DOWNLOAD);

        fileService.download(response, att.getName(), att.getVisualPath(), att.getDiskName());
    }

    @RequestMapping(value="/flow/getTemplateFile", method={RequestMethod.GET,RequestMethod.POST})
    public void getTemplateFile(HttpServletResponse response, @RequestParam(value="id", required = true)Integer id, @RequestParam(value = "type", required = false)String type) throws IOException, ErrMsgException {
        DocTemplateMgr dtm = new DocTemplateMgr();
        DocTemplateDb dtd = dtm.getDocTemplateDb(id);
        if (!dtd.isLoaded()) {
            try (BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream())) {
                response.setContentType("text/html;charset=utf-8");
                String str = "模板：" + id + " 文件不存在";
                bos.write(str.getBytes(StandardCharsets.UTF_8));
            }
            catch (final IOException e) {
                LogUtil.getLog(getClass()).error(e);
            }
            return;
        }
        if ("tail".equals(type)) {
            fileService.download(response, dtd.getFileName(), "upfile/" + DocTemplateDb.linkBasePath + "/" + dtd.getFileNameTail());
        } else {
            fileService.download(response, dtd.getFileName(), "upfile/" + DocTemplateDb.linkBasePath + "/" + dtd.getFileName());
        }
    }

    @ApiOperation(value = "下载回复中的附件", notes = "下载回复中的附件", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "附件id", required = true, dataType = "Integer"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @RequestMapping(value="/flow/downloadAnnexAttachment", method={RequestMethod.GET,RequestMethod.POST})
    public void downloadAnnexAttachment(HttpServletResponse response, @RequestParam(value="id", required = true)Integer id) throws IOException, ErrMsgException {
        WorkflowAnnexAttachment workflowAnnexAttachment = new WorkflowAnnexAttachment(id);
        if (!workflowAnnexAttachment.isLoaded()) {
            try (BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream())) {
                response.setContentType("text/html;charset=utf-8");
                String str = "文件：" + id + " 文件不存在";
                bos.write(str.getBytes(StandardCharsets.UTF_8));
            }
            catch (final IOException e) {
                LogUtil.getLog(getClass()).error(e);
            }
            return;
        }
        fileService.download(response, workflowAnnexAttachment.getName(), workflowAnnexAttachment.getVisualPath(), workflowAnnexAttachment.getDiskName());
    }

    /**
     * PC端处理固定流程
     * @param
     * @param model
     * @return
     */
    @RequestMapping(value = "/flowDispose", method={RequestMethod.GET,RequestMethod.POST})
    public String flowDispose(@RequestParam(value="myActionId", required = true)Long myActionId, Model model) {
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        String myname = privilege.getUser(request);

        MyActionDb mad = new MyActionDb();
        mad = mad.getMyActionDb(myActionId);
        if (!mad.isLoaded()) {
            model.addAttribute("msg", i18nUtil.get("myActionNotExist"));
            return "th/error/error";
        }

        // 如果存在子流程，则处理子流程
        if (mad.getSubMyActionId() != MyActionDb.SUB_MYACTION_ID_NONE) {
            return "forward:flowDispose.do?myActionId=" + mad.getSubMyActionId();
        }

        UserMgr um = new UserMgr();
        UserDb myUser = um.getUserDb(myname);

        String myRealName = myUser.getRealName();

        int flowId = (int) mad.getFlowId();
        WorkflowMgr wfm = new WorkflowMgr();
        WorkflowDb wf = wfm.getWorkflowDb(flowId);

        WorkflowPredefineDb wpd = new WorkflowPredefineDb();
        wpd = wpd.getDefaultPredefineFlow(wf.getTypeCode());

        boolean isFlowManager = false;

        LeafPriv lp = new LeafPriv(wf.getTypeCode());
        if (privilege.isUserPrivValid(request, "admin.flow")) {
            if (lp.canUserExamine(privilege.getUser(request))) {
                isFlowManager = true;
            }
        }

        boolean isRecall = wpd.isRecall(); // 是否能撤回

        WorkflowActionDb wa = new WorkflowActionDb();
        int actionId = (int) mad.getActionId();
        wa = wa.getWorkflowActionDb(actionId);

        // 判断能否提交
        try {
            WorkflowMgr.canSubmit(request, wf, wa, mad, myname, wpd);
        } catch (ErrMsgException e) {
            model.addAttribute("msg", e.getMessage());
            return "th/error/error";
        }

        // 锁定流程
        if (wa.getKind() != WorkflowActionDb.KIND_READ) {
            wfm.lock(wf, myname);
        }

        // 如果是未读状态
        if (!mad.isReaded()) {
            mad.setReaded(true);
            mad.setReadDate(new java.util.Date());
            mad.save();
        }

        String flag = wa.getFlag();

        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());

        // 置嵌套表需要用到的cwsId
        request.setAttribute("cwsId", "" + flowId);
        // 置嵌套表需要用到的pageType
        request.setAttribute("pageType", ConstUtil.PAGE_TYPE_FLOW);
        // 置NestFromCtl及NestSheetCtl需要用到的workflowActionId
        request.setAttribute("workflowActionId", "" + wa.getId());
        // 置macro_js_ntko.jsp中需要用到的myActionId
        request.setAttribute("myActionId", "" + myActionId);
        // 置NestSheetCtl需要用到的formCode
        request.setAttribute("formCode", lf.getFormCode());

        com.redmoon.oa.Config cfg = com.redmoon.oa.Config.getInstance();
        boolean canUserSeeDesignerWhenDispose = cfg.getBooleanProperty("canUserSeeDesignerWhenDispose");
        boolean canUserModifyFlow = cfg.getBooleanProperty("canUserModifyFlow");
        boolean canUserSeeFlowChart = cfg.getBooleanProperty("canUserSeeFlowChart");

        String flowExpireUnit = cfg.get("flowExpireUnit");
        boolean isHour = !"day".equals(flowExpireUnit);
        if ("day".equals(flowExpireUnit)) {
            flowExpireUnit = i18nUtil.get("day");
        } else {
            flowExpireUnit = i18nUtil.get("hour");
        }

        String action = ParamUtil.get(request, "action");

        WorkflowRuler wr = new WorkflowRuler();
        com.redmoon.oa.flow.FlowConfig conf = new com.redmoon.oa.flow.FlowConfig();             //用于判断流程toolbar按钮是否显示

        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());

        String userName1 = privilege.getUser(request);
        FormDAO fdao = new FormDAO();

        // 运行节点预处理事件
        WorkflowPredefineMgr wpm = new WorkflowPredefineMgr();
        String script = wpm.getActionPreDisposeScript(wpd.getScripts(), wa.getInternalName());
        if (script != null) {
            fdao = fdao.getFormDAOByCache(wf.getId(), fd);
            try {
                wfm.runPreDisposeScript(request, privilege.getUser(request), wf, fdao, mad, script);
            } catch (ErrMsgException e) {
                // LogUtil.getLog(getClass()).error(e);
                model.addAttribute("msg", "预处理事件：" + e.getMessage());
                return "th/error/error";
            }
        }

        UserSetupDb userSetupDb = new UserSetupDb();
        userSetupDb = userSetupDb.getUserSetupDb(userName1);
        String str = userSetupDb.getLocal();
        boolean isEn = "en-US".equals(str);
        boolean isReturnStyleFree = wpd.getReturnStyle()==WorkflowPredefineDb.RETURN_STYLE_FREE;
        boolean isAutoSaveArchive = flag.length()>=5 && "2".equals(flag.substring(4, 5));
        boolean isStartNode = wa.getIsStart()==0;

        model.addAttribute("isFlowManager", isFlowManager);
        model.addAttribute("formCode", lf.getFormCode());
        model.addAttribute("flowId", flowId);
        model.addAttribute("myActionId", myActionId);
        model.addAttribute("myUserName", myname);
        model.addAttribute("myRealName", myRealName);
        model.addAttribute("random", Math.random());
        model.addAttribute("isEn", isEn);

        model.addAttribute("activeTabTitle", wf.getTitle().replaceAll("\r\n", "").trim().length() >= 8 ? wf.getTitle().replaceAll("\r\n", "").trim().substring(0, 8) : wf.getTitle().replaceAll("\r\n", "").trim());
        model.addAttribute("action", action);
        model.addAttribute("isAutoSaveArchive", isAutoSaveArchive);
        model.addAttribute("isFlowReturnWithRemark", cfg.getBooleanProperty("isFlowReturnWithRemark"));
        model.addAttribute("isReturnStyleFree", isReturnStyleFree);
        model.addAttribute("actionId", actionId);
        model.addAttribute("isStartNode", isStartNode);
        model.addAttribute("canUserSeeDesignerWhenDispose", canUserSeeDesignerWhenDispose);
        model.addAttribute("isRecall", isRecall);

        int doc_id = wf.getDocId();
        DocumentMgr dm = new DocumentMgr();
        Document doc = dm.getDocument(doc_id);
        Render rd = new Render(request, wf, doc);
        String content = "";
        try {
            content = rd.rend(wa);
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
        }

        String strIsShowNextUsers = WorkflowActionDb.getActionProperty(wpd, wa.getInternalName(), "isShowNextUsers");
        boolean isNotShowNextUsers = "0".equals(strIsShowNextUsers);

        String strIsBtnSaveShow = WorkflowActionDb.getActionProperty(wpd, wa.getInternalName(), "isBtnSaveShow");
        boolean isBtnSaveShow = !"0".equals(strIsBtnSaveShow);

        model.addAttribute("isNotShowNextUsers", isNotShowNextUsers);

        boolean isPlus = mad.getActionStatus() == WorkflowActionDb.STATE_PLUS;
        int plusType;
        boolean isPlusBefore = false;
        if (!"".equals(wa.getPlus())) {
            JSONObject plusJson = JSONObject.parseObject(wa.getPlus());
            plusType = plusJson.getIntValue("type");
            isPlusBefore = plusType == WorkflowActionDb.PLUS_TYPE_BEFORE;
        }

        model.addAttribute("isPlus", isPlus);
        model.addAttribute("isPlusBefore", isPlusBefore);

        boolean isMyActionExpire = mad.getExpireDate() != null;
        model.addAttribute("isMyActionExpire", isMyActionExpire);
        model.addAttribute("expirationDate", mad.getExpireDate());
        model.addAttribute("isActionSendMsg", wa.isMsg());
        model.addAttribute("isUseSms", com.redmoon.oa.sms.SMSFactory.isUseSMS());
        model.addAttribute("flowAutoSMSRemind", cfg.getBooleanProperty("flowAutoSMSRemind"));
        model.addAttribute("flowAutoMsgRemind", cfg.getBooleanProperty("flowAutoMsgRemind"));

        model.addAttribute("isFlowLevelDisplay", cfg.getBooleanProperty("isFlowLevelDisplay"));
        model.addAttribute("isFlowStarted", wf.isStarted());
        model.addAttribute("levelImg", WorkflowMgr.getLevelImg(request, wf));

        String flowTitle;
        if (wf.getTitle().startsWith("#")) {
            flowTitle = LocalUtil.LoadString(request, "res.ui.menu", wf.getTypeCode());
        } else {
            flowTitle = wf.getTitle();
        }
        boolean isFlowTitleReadonly = !StrUtil.isEmpty(lf.getDescription());
        model.addAttribute("flowTitle", flowTitle);
        model.addAttribute("isFlowTitleReadonly", isFlowTitleReadonly);

        String starterName = wf.getUserName();
        String starterRealName = "";
        if (starterName != null) {
            UserDb starter = um.getUserDb(wf.getUserName());
            starterRealName = starter.getRealName();
        }
        model.addAttribute("starterRealName", starterRealName);
        model.addAttribute("beginDate", wf.getBeginDate());
        model.addAttribute("isFlowFinished", wf.getStatus() == WorkflowDb.STATUS_FINISHED);

        model.addAttribute("isReactive", wpd.isReactive());
        model.addAttribute("isAlter", wf.isAlter());
        if (wf.isAlter()) {
            UserCache userCache = SpringUtil.getBean(UserCache.class);
            String alterUserRealName = "";
            User alterUser = userCache.getUser(wf.getAlterUser());
            if (alterUser != null) {
                alterUserRealName = alterUser.getRealName();
            }
            model.addAttribute("alterUserRealName", alterUserRealName);
            model.addAttribute("alterTime", wf.getAlterTime());
        }

        model.addAttribute("flowIsRemarkShow", cfg.getBooleanProperty("flowIsRemarkShow"));
        model.addAttribute("myActionResult", mad.getResult());

        model.addAttribute("flowPerformanceDisplay", cfg.getBooleanProperty("flowPerformanceDisplay"));
        model.addAttribute("flowExpireUnit", flowExpireUnit);

        model.addAttribute("aryMyAction", workflowService.listProcess(flowId));

        com.alibaba.fastjson.JSONArray aryReturnAction = new com.alibaba.fastjson.JSONArray();
        boolean hasReturnAction = false;
        Vector<WorkflowActionDb> returnv = wa.getLinkReturnActions();
        if (returnv.size() > 0 || wpd.getReturnStyle() == WorkflowPredefineDb.RETURN_STYLE_FREE) {
            hasReturnAction = true;
            for (WorkflowActionDb returnwa : returnv) {
                if (returnwa.getStatus() != WorkflowActionDb.STATE_IGNORED) {
                    com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
                    jsonObject.put("actionId", returnwa.getId());
                    jsonObject.put("actionTitle", returnwa.getTitle());
                    jsonObject.put("realName", returnwa.getUserRealName());
                    aryReturnAction.add(jsonObject);
                }
            }
        }
        model.addAttribute("hasReturnAction", hasReturnAction);
        model.addAttribute("aryReturnAction", aryReturnAction);

        model.addAttribute("isHasAttachment", fd.isHasAttachment());

        if (lf.getQueryId() != Leaf.QUERY_NONE) {
            // 判断权限，管理员能看见查询，其它人员根据角色进行判断
            String[] roles = StrUtil.split(lf.getQueryRole(), ",");
            boolean canSeeQuery = false;
            if (!privilege.isUserPrivValid(request, "admin")) {
                if (roles != null) {
                    UserDb user = new UserDb();
                    user = user.getUserDb(privilege.getUser(request));
                    for (String role : roles) {
                        if (user.isUserOfRole(role)) {
                            canSeeQuery = true;
                            break;
                        }
                    }
                } else {
                    canSeeQuery = true;
                }
            } else {
                canSeeQuery = true;
            }
            FormQueryDb aqd = new FormQueryDb();
            aqd = aqd.getFormQueryDb((int) lf.getQueryId());
            boolean canQuery = canSeeQuery && aqd.isLoaded();
            model.addAttribute("canQuery", canQuery);
            if (canQuery) {
                model.addAttribute("queryId", lf.getQueryId());
                model.addAttribute("queryName", aqd.getQueryName());
                String colratio = "";
                String colP = aqd.getColProps();
                if (colP == null || "".equals(colP)) {
                    colP = "[]";
                }
                int tableWidth = 0;
                com.alibaba.fastjson.JSONArray jsonArray = com.alibaba.fastjson.JSONArray.parseArray(colP);
                for (int i = 0; i < jsonArray.size(); i++) {
                    com.alibaba.fastjson.JSONObject jsonCol = jsonArray.getJSONObject(i);
                    if (jsonCol.getBoolean("hide")) {
                        continue;
                    }
                    String name = (String) jsonCol.get("name");
                    if ("cws_op".equalsIgnoreCase(name)) {
                        continue;
                    }
                    tableWidth += jsonCol.getIntValue("width");
                    if ("".equals(colratio)) {
                        colratio = jsonCol.getString("width");
                    } else {
                        colratio += "," + jsonCol.getString("width");
                    }
                }
                model.addAttribute("colRatio", colratio);
                model.addAttribute("queryTableWidth", tableWidth + 2);
                String queryAjaxUrl;
                if (aqd.isScript()) {
                    queryAjaxUrl = "flow/form_query_list_script_embed_ajax.jsp";
                } else {
                    queryAjaxUrl = "flow/form_query_list_embed_ajax.jsp";
                }
                model.addAttribute("queryAjaxUrl", queryAjaxUrl);
                com.alibaba.fastjson.JSONObject queryCond = com.alibaba.fastjson.JSONObject.parseObject(lf.getQueryCondMap());
                model.addAttribute("queryCond", queryCond);
            }
        }

        // 判断是否需按选项卡的方式显示
        StringBuilder sbUl = new StringBuilder();
        StringBuilder sbDiv = new StringBuilder();
        FormDb formDbOther = new FormDb();
        MacroCtlMgr mm = new MacroCtlMgr();
        for (FormField macroField : fd.getFields()) {
            if (macroField.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(macroField.getMacroType());
                if (mu == null) {
                    DebugUtil.e(getClass(), "flowDispose", macroField.getTitle() + " 宏控件: " + macroField.getMacroType() + " 不存在");
                }
                else if (mu.getNestType() == MacroCtlUnit.NEST_TYPE_NORMAIL) {
                    String destForm = macroField.getDescription();
                    String defaultVal = StrUtil.decodeJSON(destForm);
                    JSONObject jsonDef = JSONObject.parseObject(defaultVal);
                    int isTab = 0;
                    if (jsonDef.containsKey("isTab")) {
                        isTab = jsonDef.getIntValue("isTab");
                        if (isTab == 1) {
                            destForm = jsonDef.getString("destForm");
                            formDbOther = formDbOther.getFormDb(destForm);
                            sbUl.append("<li><a href='#tabs-" + destForm + "'>" + formDbOther.getName() + "</a></li>");
                            sbDiv.append("<div id='tabs-" + destForm + "' class='tabDiv'></div>");
                        }
                    }
                }
            }
        }

        boolean isTab = false;
        if (sbUl.length() > 0) {
            isTab = true;
        }
        model.addAttribute("isTab", isTab);
        String strUl = "<ul>" + "<li><a href='#tabs-" + fd.getCode() + "'>" + fd.getName() + "</a></li>" + sbUl.toString() + "</ul>";
        String strDiv = "<div id='tabs-" + fd.getCode() + "' class='tabDiv'>" + content + "</div>" + sbDiv.toString();
        model.addAttribute("tabs", strUl + strDiv);
        model.addAttribute("content", content);
        model.addAttribute("isReply", wpd.isReply());
        model.addAttribute("isProgress", fd.isProgress());

        if (fd.isProgress()) {
            // com.redmoon.oa.flow.FormDAO fdao = new com.redmoon.oa.flow.FormDAO();
            // 如果fdao未载入
            if (!fdao.isLoaded()) {
                fdao = fdao.getFormDAOByCache(flowId, fd);
            }
            model.addAttribute("progress", fdao.getCwsProgress());
        }

        com.alibaba.fastjson.JSONArray aryAnnex = new com.alibaba.fastjson.JSONArray();
        WorkflowAnnexDb wad = new WorkflowAnnexDb();
        Vector<WorkflowAnnexDb> vec1 = wad.listRoot(flowId, myname);
        for (WorkflowAnnexDb workflowAnnexDb : vec1) {
            wad = workflowAnnexDb;

            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("realName", um.getUserDb(wad.getString("user_name")).getRealName());
            json.put("process", wad.getInt("progress"));
            json.put("content", wad.getString("content"));
            json.put("id", wad.getLong("id"));
            json.put("addDate", wad.getDate("add_date"));
            json.put("flowId", wad.getString("flow_id"));
            json.put("actionId", wad.getString("action_id"));
            json.put("userName", wad.getString("user_name"));
            aryAnnex.add(json);

            WorkflowAnnexDb wad2 = new WorkflowAnnexDb();
            com.alibaba.fastjson.JSONArray aryAnnexSub = new com.alibaba.fastjson.JSONArray();
            Vector<WorkflowAnnexDb> vec2 = wad2.listChildren(wad.getInt("id"), myname);
            for (WorkflowAnnexDb annexDb : vec2) {
                wad2 = annexDb;
                int id2 = (int) wad2.getLong("id");

                com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
                jsonObject.put("id", id2);
                jsonObject.put("userName", wad2.getString("user_name"));
                jsonObject.put("realName", um.getUserDb(wad2.getString("user_name")).getRealName());
                jsonObject.put("content", wad2.getString("content"));
                jsonObject.put("addDate", wad2.getDate("add_date"));
                jsonObject.put("flowId", wad2.getString("flow_id"));
                jsonObject.put("actionId", wad2.getString("action_id"));
                jsonObject.put("replyRealName", um.getUserDb(wad2.getString("reply_name")).getRealName());
                aryAnnexSub.add(jsonObject);
            }
            json.put("aryAnnexSub", aryAnnexSub);
        }
        model.addAttribute("aryAnnex", aryAnnex);
        model.addAttribute("isReturnBack", wf.isReturnBack());

        model.addAttribute("PLUS_TYPE_BEFORE", WorkflowActionDb.PLUS_TYPE_BEFORE);
        model.addAttribute("PLUS_TYPE_AFTER", WorkflowActionDb.PLUS_TYPE_AFTER);
        model.addAttribute("PLUS_TYPE_CONCURRENT", WorkflowActionDb.PLUS_TYPE_CONCURRENT);

        model.addAttribute("PLUS_MODE_ORDER", WorkflowActionDb.PLUS_MODE_ORDER);
        model.addAttribute("PLUS_MODE_ONE", WorkflowActionDb.PLUS_MODE_ONE);
        model.addAttribute("PLUS_MODE_ALL", WorkflowActionDb.PLUS_MODE_ALL);

        model.addAttribute("isDebug", lf.isDebug());

        String fieldWrite = "," + StrUtil.getNullString(wa.getFieldWrite()).trim() + ",";
        String fieldHide = "," + StrUtil.getNullString(wa.getFieldHide()).trim() + ",";
        Vector<FormField> vFields = fd.getFields();
        int formView = wa.getFormView();
        if (formView != WorkflowActionDb.VIEW_DEFAULT) {
            FormViewDb fvd = new FormViewDb();
            fvd = fvd.getFormViewDb(formView);
            String form = fvd.getString("content");
            String ieVersion = fvd.getString("ie_version");
            FormParser fp = new FormParser();
            vFields = fp.parseCtlFromView(form, ieVersion, fd);
        }

        com.alibaba.fastjson.JSONArray aryFieldWritable = new com.alibaba.fastjson.JSONArray();
        Iterator<FormField> irFields = vFields.iterator();
        while (irFields.hasNext()) {
            FormField ff = (FormField) irFields.next();
            boolean isWrite = fieldWrite.contains("," + ff.getName() + ",");
            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("fieldTitle", ff.getTitle());
            json.put("isWrite", isWrite);
            json.put("fieldName", ff.getName());
            aryFieldWritable.add(json);
        }
        model.addAttribute("aryFieldWritable", aryFieldWritable);

        String[] fds = StrUtil.getNullString(wa.getFieldWrite()).trim().split(",");
        int len = fds.length;
        ModuleSetupDb msd = new ModuleSetupDb();

        com.alibaba.fastjson.JSONArray aryNestFieldWritable = new com.alibaba.fastjson.JSONArray();
        // 取出嵌套表
        irFields = vFields.iterator();
        while (irFields.hasNext()) {
            FormField ff = irFields.next();
            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu == null) {
                    DebugUtil.e(getClass(), "flowDispose", ff.getTitle() + " 宏控件: " + ff.getMacroType() + " 不存在");
                }
                else if (mu.getNestType() != MacroCtlUnit.NEST_TYPE_NONE) {
                    String defaultVal = "";
                    String nestFormCode = ff.getDefaultValue();
                    if (mu.getNestType() == MacroCtlUnit.NEST_DETAIL_LIST) {
                        defaultVal = StrUtil.decodeJSON(ff.getDescription());
                    } else {
                        defaultVal = ff.getDescription();
                        if ("".equals(defaultVal)) {
                            defaultVal = ff.getDefaultValueRaw();
                        }
                        defaultVal = StrUtil.decodeJSON(defaultVal); // ff.getDefaultValueRaw()
                    }
                    // 20131123 fgf 添加
                    JSONObject json = JSONObject.parseObject(defaultVal);
                    nestFormCode = json.getString("destForm");

                    FormDb nestfd = new FormDb();
                    nestfd = nestfd.getFormDb(nestFormCode);

                    msd = msd.getModuleSetupDbOrInit(nestFormCode);
                    String[] fields = msd.getColAry(false, "list_field");
                    for (FormField ff2 : nestfd.getFields()) {
                        String txt = ff2.getTitle() + "(嵌套表-" + nestfd.getName() + ")";
                        // 判断是否已被选中
                        boolean isFound = false;
                        for (int i = 0; i < len; i++) {
                            if (("nest." + ff2.getName()).equals(fds[i])) {
                                isFound = true;
                                break;
                            }
                        }
                        com.alibaba.fastjson.JSONObject jsonObj = new com.alibaba.fastjson.JSONObject();
                        jsonObj.put("fieldTitle", ff2.getTitle());
                        jsonObj.put("fieldName", ff2.getName());
                        jsonObj.put("fieldText", txt);
                        jsonObj.put("isWrite", isFound);
                        aryNestFieldWritable.add(jsonObj);
                    }
                }
            }
        }
        model.addAttribute("aryNestFieldWritable", aryNestFieldWritable);

        com.alibaba.fastjson.JSONArray aryFieldHide = new com.alibaba.fastjson.JSONArray();
        irFields = vFields.iterator();
        while (irFields.hasNext()) {
            FormField ff = irFields.next();
            boolean isHide = fieldHide.contains("," + ff.getName() + ",");
            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("isHide", isHide);
            json.put("fieldTitle", ff.getTitle());
            json.put("fieldName", ff.getName());
            aryFieldHide.add(json);
        }
        model.addAttribute("aryFieldHide", aryFieldHide);

        com.alibaba.fastjson.JSONArray aryNextFieldHide = new com.alibaba.fastjson.JSONArray();
        fds = StrUtil.getNullString(wa.getFieldHide()).trim().split(",");
        len = fds.length;
        // 取出嵌套表
        irFields = vFields.iterator();
        while (irFields.hasNext()) {
            FormField ff = irFields.next();
            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu == null) {
                    DebugUtil.e(getClass(), "flowDispose", ff.getTitle() + " 宏控件: " + ff.getMacroType() + " 不存在");
                }
                else if (mu.getNestType() != MacroCtlUnit.NEST_TYPE_NONE) {
                    String nestFormCode = ff.getDefaultValue();
                    String defaultVal = "";
                    if (mu.getNestType() == MacroCtlUnit.NEST_DETAIL_LIST) {
                        defaultVal = StrUtil.decodeJSON(ff.getDescription());
                    } else {
                        defaultVal = ff.getDescription();
                        if ("".equals(defaultVal)) {
                            defaultVal = ff.getDefaultValueRaw();
                        }
                        defaultVal = StrUtil.decodeJSON(defaultVal); // ff.getDefaultValueRaw()
                    }
                    // 20131123 fgf 添加
                    JSONObject json = JSONObject.parseObject(defaultVal);
                    nestFormCode = json.getString("destForm");

                    FormDb nestfd = new FormDb();
                    nestfd = nestfd.getFormDb(nestFormCode);

                    msd = msd.getModuleSetupDbOrInit(nestFormCode);

                    for (FormField ff2 : nestfd.getFields()) {
                        String txt = ff2.getTitle() + "(嵌套表-" + nestfd.getName() + ")";
                        // 判断是否已被选中
                        boolean isFound = false;
                        for (int i = 0; i < len; i++) {
                            if (("nest." + ff2.getName()).equals(fds[i])) {
                                isFound = true;
                                break;
                            }
                        }
                        com.alibaba.fastjson.JSONObject jsonObj = new com.alibaba.fastjson.JSONObject();
                        jsonObj.put("isHide", isFound);
                        jsonObj.put("fieldTitle", ff.getTitle());
                        jsonObj.put("fieldName", ff.getName());
                        jsonObj.put("fieldText", txt);
                        aryNextFieldHide.add(jsonObj);
                    }
                }
            }
        }
        model.addAttribute("aryNextFieldHide", aryNextFieldHide);
        model.addAttribute("isLicenseSrc", License.getInstance().isSrc());
        model.addAttribute("unitCode", privilege.getUserUnitCode(request));
        model.addAttribute("tabIdOpener", ParamUtil.get(request, "tabIdOpener"));

        boolean isCustomRedirectUrl = false;
        String redirectUrl = WorkflowActionDb.getActionProperty(wpd, wa.getInternalName(), "redirectUrl");
        if (redirectUrl!=null && !"".equals(redirectUrl)) {
            isCustomRedirectUrl = true;
            if (!redirectUrl.contains("?")) {
                redirectUrl += "?";
            }
            else {
                redirectUrl += "&";
            }
        }
        model.addAttribute("isCustomRedirectUrl", isCustomRedirectUrl);
        model.addAttribute("redirectUrl", redirectUrl);

        model.addAttribute("isNotShowNextUsers", isNotShowNextUsers);

        boolean isActionKindRead = wa.getKind()==WorkflowActionDb.KIND_READ;
        model.addAttribute("isActionKindRead", isActionKindRead);

        boolean isDocDistributed = false;
        // 检查文件是否已分发
        if (!isActionKindRead && wf.isStarted() && wa.isDistribute()) {
            if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
                PaperDistributeDb pdd = new PaperDistributeDb();
                int paperCount = pdd.getCountOfWorkflow(wf.getId());
                if (paperCount > 0) {
                    isDocDistributed = true;
                }
            }
        }
        model.addAttribute("isDocDistributed", isDocDistributed);

        Vector<FormField> fields = fd.getFields();
        fds = fieldWrite.split(",");
        len = fds.length;
        //将不可写的域筛选出
        for (FormField ff : fields) {
            boolean finded = false;
            for (int i = 0; i < len; i++) {
                if (ff.getName().equals(fds[i])) {
                    finded = true;
                    break;
                }
            }

            if (!finded) {
                ff.setEditable(false);
            }
        }
        String checkJs = FormUtil.doGetCheckJS(request, fields);
        String checkJsSub = checkJs.substring(9, checkJs.length() - 10);
        model.addAttribute("checkJsSub", checkJsSub);

        model.addAttribute("canUserStartFlow", wr.canUserStartFlow(request, wf));
        model.addAttribute("isBtnSaveShow", isBtnSaveShow && conf.getIsDisplay("FLOW_BUTTON_SAVE"));

        model.addAttribute("aryButton", workflowService.getToolbarButtons(wpd, lf, wf, mad, wa, returnv, flag, myname, isBtnSaveShow, isActionKindRead, canUserSeeDesignerWhenDispose, canUserSeeFlowChart));

        model.addAttribute("btnCancelAttention", conf.getBtnName("FLOW_BUTTON_CANCEL_ATTENTION").startsWith("#") ? i18nUtil.get("cancelAttention") : conf.getBtnName("FLOW_BUTTON_CANCEL_ATTENTION"));
        model.addAttribute("btnAttention", conf.getBtnName("FLOW_BUTTON_ATTENTION").startsWith("#")?i18nUtil.get("attention"):conf.getBtnName("FLOW_BUTTON_ATTENTION"));
        model.addAttribute("PLUS_TYPE_CONCURRENT", WorkflowActionDb.PLUS_TYPE_CONCURRENT);

        // 如果下一节点上设置为“表单中的人员”，或者限定了部门表单域，则绑定change事件，以重新匹配人员
        List<String> bindFieldList = new ArrayList<>();
        Vector<WorkflowActionDb> vto = wa.getLinkToActions();
        for (WorkflowActionDb towa : vto) {
            String jobCode = towa.getJobCode();
            if (jobCode.startsWith(WorkflowActionDb.PRE_TYPE_FIELD_USER)) {
                String fieldNames = jobCode.substring((WorkflowActionDb.PRE_TYPE_FIELD_USER + "_").length());
                if (!fieldNames.startsWith("nest.")) {
                    String[] fieldAry = StrUtil.split(fieldNames, ",");
                    if (fieldAry == null) {
                        continue;
                    }
                    Collections.addAll(bindFieldList, fieldAry);
                }
            } else {
                String deptField = WorkflowActionDb.getActionProperty(wpd, towa.getInternalName(), "deptField");
                if (!StringUtils.isEmpty(deptField)) {
                    bindFieldList.add(deptField);
                }
            }
        }
        model.addAttribute("bindFieldList", bindFieldList);
        // 如果处理过程显示于下方
        boolean flowProcessShowOnBottom = cfg.getBooleanProperty("flowProcessShowOnBottom");
        model.addAttribute("flowProcessShowOnBottom", flowProcessShowOnBottom);

        com.alibaba.fastjson.JSONObject matchJson;
        try {
            matchJson = workflowService.matchBranchAndUser(request, myActionId, actionId);
        } catch (ErrMsgException e) {
            matchJson = new com.alibaba.fastjson.JSONObject();
            LogUtil.getLog(getClass()).error(e);
        }
        model.addAttribute("matchJson", matchJson);

        boolean isPageStyleLight = false;
        msd = msd.getModuleSetupDbOrInit(lf.getFormCode());
        if (msd.getPageStyle()==ConstUtil.PAGE_STYLE_LIGHT) {
            isPageStyleLight = true;
        }
        model.addAttribute("isPageStyleLight", isPageStyleLight);
        model.addAttribute("skinPath", SkinMgr.getSkinPath(request, false));
        model.addAttribute("flowTypeCode", wpd.getTypeCode());
        model.addAttribute("level", wf.getLevel());

        model.addAttribute("LEVEL_NORMAL", WorkflowDb.LEVEL_NORMAL);
        model.addAttribute("LEVEL_IMPORTANT", WorkflowDb.LEVEL_IMPORTANT);
        model.addAttribute("LEVEL_URGENT", WorkflowDb.LEVEL_URGENT);

        return "th/flow_dispose";
    }

    @ApiOperation(value = "流程处理初始化", notes = "流程处理初始化", httpMethod = "GET")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "myActionId", value = "待办记录的ID", required = true, dataType = "Long"),
    })
    @RequestMapping(value = "/flow/flowProcess", method={RequestMethod.GET,RequestMethod.POST})
    @ResponseBody
    public Result<Object> flowProcess(@RequestParam(value="myActionId", required = true)Long myActionId) {
        long t = System.currentTimeMillis();
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        String myname = privilege.getUser(request);

        MyActionDb mad = new MyActionDb();
        mad = mad.getMyActionDb(myActionId);
        if (!mad.isLoaded()) {
            return new Result<>(false, i18nUtil.get("myActionNotExist"));
        }

        com.alibaba.fastjson.JSONObject jsonResult = new com.alibaba.fastjson.JSONObject();
        // 如果存在子流程，则处理子流程
        if (mad.getSubMyActionId() != MyActionDb.SUB_MYACTION_ID_NONE) {
            // return "forward:flowDispose.do?myActionId=" + mad.getSubMyActionId();
            jsonResult.put("toDo", "process");
            jsonResult.put("myActionId", mad.getSubMyActionId());
            return new Result<>(jsonResult);
        }

        UserMgr um = new UserMgr();
        UserDb myUser = um.getUserDb(myname);

        String myRealName = myUser.getRealName();

        int flowId = (int) mad.getFlowId();
        WorkflowMgr wfm = new WorkflowMgr();
        WorkflowDb wf = wfm.getWorkflowDb(flowId);

        WorkflowPredefineDb wpd = new WorkflowPredefineDb();
        wpd = wpd.getDefaultPredefineFlow(wf.getTypeCode());

        boolean isFlowManager = false;

        LeafPriv lp = new LeafPriv(wf.getTypeCode());
        if (privilege.isUserPrivValid(request, "admin.flow")) {
            if (lp.canUserExamine(privilege.getUser(request))) {
                isFlowManager = true;
            }
        }

        boolean isRecall = wpd.isRecall(); // 是否能撤回

        WorkflowActionDb wa = new WorkflowActionDb();
        int actionId = (int) mad.getActionId();
        wa = wa.getWorkflowActionDb(actionId);

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "flowProcess getWorkflowActionDb", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }
        // 判断能否提交
        try {
            WorkflowMgr.canSubmit(request, wf, wa, mad, myname, wpd);
        } catch (ErrMsgException e) {
            return new Result<>(false, e.getMessage());
        }
        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "flowProcess canSubmit", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        // 锁定流程
        if (wa.getKind() != WorkflowActionDb.KIND_READ) {
            wfm.lock(wf, myname);
        }

        // 如果是未读状态
        if (!mad.isReaded()) {
            mad.setReaded(true);
            mad.setReadDate(new java.util.Date());
            mad.save();
        }

        String flag = wa.getFlag();

        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());

        // 置嵌套表需要用到的cwsId
        request.setAttribute("cwsId", "" + flowId);
        // 置嵌套表需要用到的pageType
        request.setAttribute("pageType", ConstUtil.PAGE_TYPE_FLOW);
        // 置NestFromCtl及NestSheetCtl需要用到的workflowActionId
        request.setAttribute("workflowActionId", "" + wa.getId());
        // 置macro_js_ntko.jsp中需要用到的myActionId
        request.setAttribute("myActionId", "" + myActionId);
        // 置NestSheetCtl需要用到的formCode
        request.setAttribute("formCode", lf.getFormCode());

        com.redmoon.oa.Config cfg = new com.redmoon.oa.Config();
        boolean canUserSeeDesignerWhenDispose = cfg.getBooleanProperty("canUserSeeDesignerWhenDispose");
        boolean canUserModifyFlow = cfg.getBooleanProperty("canUserModifyFlow");
        boolean canUserSeeFlowChart = cfg.getBooleanProperty("canUserSeeFlowChart");

        String flowExpireUnit = cfg.get("flowExpireUnit");
        boolean isHour = !"day".equals(flowExpireUnit);
        if ("day".equals(flowExpireUnit)) {
            flowExpireUnit = i18nUtil.get("day");
        } else {
            flowExpireUnit = i18nUtil.get("hour");
        }

        String action = ParamUtil.get(request, "action");

        WorkflowRuler wr = new WorkflowRuler();
        com.redmoon.oa.flow.FlowConfig conf = new com.redmoon.oa.flow.FlowConfig();             //用于判断流程toolbar按钮是否显示

        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());

        String userName1 = privilege.getUser(request);
        FormDAO fdao = new FormDAO();
        fdao = fdao.getFormDAOByCache(wf.getId(), fd);
        // 用于当表单域选择宏控件中含有条件{$mainId}时，通过ModuleUtil.getModuleListSelCondScriptFromWinOpener获取
        jsonResult.put("cws_id", fdao.getId());

        // 运行节点预处理事件，注意保存草稿后，每次再进入时会再次运行
        WorkflowPredefineMgr wpm = new WorkflowPredefineMgr();
        String script = wpm.getActionPreDisposeScript(wpd.getScripts(), wa.getInternalName());
        if (script != null) {
            try {
                wfm.runPreDisposeScript(request, privilege.getUser(request), wf, fdao, mad, script);
            } catch (ErrMsgException e) {
                // LogUtil.getLog(getClass()).error(e);
                return new Result<>(false, "预处理事件：" + e.getMessage());
            }
        }

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "flowProcess runPreDisposeScript", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        UserSetupDb userSetupDb = new UserSetupDb();
        userSetupDb = userSetupDb.getUserSetupDb(userName1);
        String str = userSetupDb.getLocal();
        boolean isEn = "en-US".equals(str);
        boolean isReturnStyleFree = wpd.getReturnStyle()==WorkflowPredefineDb.RETURN_STYLE_FREE;
        boolean isAutoSaveArchive = flag.length()>=5 && "2".equals(flag.substring(4, 5));
        boolean isStartNode = wa.getIsStart()==0;

        jsonResult.put("isFlowManager", isFlowManager);
        jsonResult.put("formCode", lf.getFormCode());
        jsonResult.put("flowId", flowId);
        jsonResult.put("myActionId", myActionId);
        jsonResult.put("myUserName", myname);
        jsonResult.put("myRealName", myRealName);
        jsonResult.put("random", Math.random());
        jsonResult.put("isEn", isEn);

        ModuleSetupDb msd = new ModuleSetupDb();
        msd = msd.getModuleSetupDb(lf.getFormCode());
        String formJs = ModuleUtil.parseScript(flowId, fdao.getId(), lf.getFormCode(), null, -1, null, null, ConstUtil.PAGE_TYPE_LIST, msd.getScript("form_js"));
        jsonResult.put("formJs", StrUtil.getNullStr(formJs));

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "flowProcess parseScript", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        jsonResult.put("activeTabTitle", wf.getTitle().replaceAll("\r\n", "").trim().length() >= 8 ? wf.getTitle().replaceAll("\r\n", "").trim().substring(0, 8) : wf.getTitle().replaceAll("\r\n", "").trim());
        jsonResult.put("action", action);
        jsonResult.put("isAutoSaveArchive", isAutoSaveArchive);
        jsonResult.put("isFlowReturnWithRemark", cfg.getBooleanProperty("isFlowReturnWithRemark"));
        jsonResult.put("isReturnStyleFree", isReturnStyleFree);
        jsonResult.put("actionId", actionId);
        jsonResult.put("isStartNode", isStartNode);
        jsonResult.put("canUserSeeDesignerWhenDispose", canUserSeeDesignerWhenDispose);
        jsonResult.put("isRecall", isRecall);

        FormViewDb formViewDb = new FormViewDb();
        Vector vView = formViewDb.getViews(lf.getFormCode());
        boolean hasView = false;
        if (vView.size()>0) {
            hasView = true;
            com.alibaba.fastjson.JSONArray aryView = new com.alibaba.fastjson.JSONArray();
            Iterator irView = vView.iterator();
            while (irView.hasNext()) {
                formViewDb = (FormViewDb)irView.next();
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("id", formViewDb.getLong("id"));
                json.put("name", formViewDb.getString("name"));
                aryView.add(json);
            }
            jsonResult.put("aryView", aryView);
        }
        jsonResult.put("hasView", hasView);

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "flowProcess hasView", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        int doc_id = wf.getDocId();
        DocumentMgr dm = new DocumentMgr();
        Document doc = dm.getDocument(doc_id);
        Render rd = new Render(request, wf, doc);
        String content = "";
        try {
            content = rd.rend(wa);
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
        }

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "flowProcess rend", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        String strIsShowNextUsers = WorkflowActionDb.getActionProperty(wpd, wa.getInternalName(), "isShowNextUsers");
        boolean isNotShowNextUsers = "0".equals(strIsShowNextUsers);

        String strIsBtnSaveShow = WorkflowActionDb.getActionProperty(wpd, wa.getInternalName(), "isBtnSaveShow");
        boolean isBtnSaveShow = !"0".equals(strIsBtnSaveShow);

        jsonResult.put("isNotShowNextUsers", isNotShowNextUsers);

        boolean isPlus = mad.getActionStatus() == WorkflowActionDb.STATE_PLUS;
        int plusType;
        boolean isPlusBefore = false;
        String plusDesc = "";
        boolean isMyPlus = false;
        if (!"".equals(wa.getPlus())) {
            com.alibaba.fastjson.JSONObject plusJson = com.alibaba.fastjson.JSONObject.parseObject(wa.getPlus());
            plusType = plusJson.getIntValue("type");
            isPlusBefore = plusType == WorkflowActionDb.PLUS_TYPE_BEFORE;
            String from = plusJson.getString("from");
            if (myname.equals(from)) {
                isMyPlus = true;
                plusDesc = workflowService.getPlusDesc(plusJson);
            }
        }

        jsonResult.put("isPlus", isPlus);
        jsonResult.put("isPlusBefore", isPlusBefore);
        jsonResult.put("isMyPlus", isMyPlus);
        jsonResult.put("plusDesc", plusDesc);

        boolean isMyActionExpire = mad.getExpireDate() != null;
        jsonResult.put("isMyActionExpire", isMyActionExpire);
        jsonResult.put("expirationDate", mad.getExpireDate());
        jsonResult.put("isActionSendMsg", wa.isMsg());
        jsonResult.put("isUseSms", com.redmoon.oa.sms.SMSFactory.isUseSMS());
        jsonResult.put("flowAutoSMSRemind", cfg.getBooleanProperty("flowAutoSMSRemind"));
        jsonResult.put("flowAutoMsgRemind", cfg.getBooleanProperty("flowAutoMsgRemind"));

        jsonResult.put("isFlowLevelDisplay", cfg.getBooleanProperty("isFlowLevelDisplay"));
        jsonResult.put("isFlowStarted", wf.isStarted());
        jsonResult.put("levelImg", WorkflowMgr.getLevelImg(request, wf));

        String flowTitle;
        if (wf.getTitle().startsWith("#")) {
            flowTitle = LocalUtil.LoadString(request, "res.ui.menu", wf.getTypeCode());
        } else {
            flowTitle = wf.getTitle();
        }
        boolean isFlowTitleReadonly = !StrUtil.isEmpty(lf.getDescription());
        jsonResult.put("flowTitle", flowTitle);
        jsonResult.put("isFlowTitleReadonly", isFlowTitleReadonly);

        String starterName = wf.getUserName();
        String starterRealName = "";
        if (starterName != null) {
            UserDb starter = um.getUserDb(wf.getUserName());
            starterRealName = starter.getRealName();
        }
        jsonResult.put("starterRealName", starterRealName);
        jsonResult.put("beginDate", wf.getBeginDate());
        jsonResult.put("isFlowFinished", wf.getStatus() == WorkflowDb.STATUS_FINISHED);

        jsonResult.put("isReactive", wpd.isReactive());
        jsonResult.put("isAlter", wf.isAlter());
        if (wf.isAlter()) {
            UserCache userCache = SpringUtil.getBean(UserCache.class);
            String alterUserRealName = "";
            User alterUser = userCache.getUser(wf.getAlterUser());
            if (alterUser != null) {
                alterUserRealName = alterUser.getRealName();
            }
            jsonResult.put("alterUserRealName", alterUserRealName);
            jsonResult.put("alterTime", wf.getAlterTime());
        }

        jsonResult.put("flowIsRemarkShow", cfg.getBooleanProperty("flowIsRemarkShow"));
        jsonResult.put("myActionResult", mad.getResult());

        jsonResult.put("flowPerformanceDisplay", cfg.getBooleanProperty("flowPerformanceDisplay"));
        jsonResult.put("flowExpireUnit", flowExpireUnit);

        jsonResult.put("aryMyAction", workflowService.listProcess(flowId));

        com.alibaba.fastjson.JSONArray aryReturnAction = new com.alibaba.fastjson.JSONArray();
        boolean hasReturnAction = false;
        Vector<WorkflowActionDb> returnv = wa.getLinkReturnActions();
        if (returnv.size() > 0 || wpd.getReturnStyle() == WorkflowPredefineDb.RETURN_STYLE_FREE) {
            hasReturnAction = true;
            for (WorkflowActionDb returnwa : returnv) {
                if (returnwa.getStatus() != WorkflowActionDb.STATE_IGNORED) {
                    com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
                    jsonObject.put("actionId", returnwa.getId());
                    jsonObject.put("actionTitle", returnwa.getTitle());
                    jsonObject.put("realName", returnwa.getUserRealName());
                    aryReturnAction.add(jsonObject);
                }
            }
        }
        jsonResult.put("hasReturnAction", hasReturnAction);
        jsonResult.put("aryReturnAction", aryReturnAction);

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "flowProcess aryReturnAction", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        jsonResult.put("isHasAttachment", fd.isHasAttachment());
        int aryAttSize = 0;
        if (fd.isHasAttachment() && doc!=null) {
            aryAttSize = doc.getAttachments(1).size();
        }
        jsonResult.put("aryAttSize", aryAttSize);

        if (lf.getQueryId() != Leaf.QUERY_NONE) {
            // 判断权限，管理员能看见查询，其它人员根据角色进行判断
            String[] roles = StrUtil.split(lf.getQueryRole(), ",");
            boolean canSeeQuery = false;
            if (!privilege.isUserPrivValid(request, "admin")) {
                if (roles != null) {
                    UserDb user = new UserDb();
                    user = user.getUserDb(privilege.getUser(request));
                    for (String role : roles) {
                        if (user.isUserOfRole(role)) {
                            canSeeQuery = true;
                            break;
                        }
                    }
                } else {
                    canSeeQuery = true;
                }
            } else {
                canSeeQuery = true;
            }
            FormQueryDb aqd = new FormQueryDb();
            aqd = aqd.getFormQueryDb((int) lf.getQueryId());
            boolean canQuery = canSeeQuery && aqd.isLoaded();
            jsonResult.put("canQuery", canQuery);
            if (canQuery) {
                jsonResult.put("queryId", lf.getQueryId());
                jsonResult.put("queryName", aqd.getQueryName());
                String colratio = "";
                String colP = aqd.getColProps();
                if (colP == null || "".equals(colP)) {
                    colP = "[]";
                }
                int tableWidth = 0;
                com.alibaba.fastjson.JSONArray jsonArray = com.alibaba.fastjson.JSONArray.parseArray(colP);
                for (int i = 0; i < jsonArray.size(); i++) {
                    com.alibaba.fastjson.JSONObject jsonCol = jsonArray.getJSONObject(i);
                    if (jsonCol.getBoolean("hide")) {
                        continue;
                    }
                    String name = (String) jsonCol.get("name");
                    if ("cws_op".equalsIgnoreCase(name)) {
                        continue;
                    }
                    tableWidth += jsonCol.getIntValue("width");
                    if ("".equals(colratio)) {
                        colratio = jsonCol.getString("width");
                    } else {
                        colratio += "," + jsonCol.getString("width");
                    }
                }
                jsonResult.put("colRatio", colratio);
                jsonResult.put("queryTableWidth", tableWidth + 2);
                String queryAjaxUrl;
                if (aqd.isScript()) {
                    queryAjaxUrl = "/flow/form_query_list_script_embed_ajax.jsp";
                } else {
                    queryAjaxUrl = "/flow/form_query_list_embed_ajax.jsp";
                }
                jsonResult.put("queryAjaxUrl", queryAjaxUrl);
                com.alibaba.fastjson.JSONObject queryCond = com.alibaba.fastjson.JSONObject.parseObject(lf.getQueryCondMap());
                jsonResult.put("queryCond", queryCond);
            }
        }

        MacroCtlMgr mm = new MacroCtlMgr();
        // 判断是否需按选项卡的方式显示
        /*StringBuilder sbUl = new StringBuilder();
        StringBuilder sbDiv = new StringBuilder();
        FormDb formDbOther = new FormDb();
        for (FormField macroField : fd.getFields()) {
            if (macroField.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(macroField.getMacroType());
                if (mu == null) {
                    DebugUtil.e(getClass(), "flowProcess", macroField.getTitle() + " 宏控件: " + macroField.getMacroType() + " 不存在");
                } else if (mu.getNestType() == MacroCtlUnit.NEST_TYPE_NORMAIL) {
                    String destForm = macroField.getDescription();
                    String defaultVal = StrUtil.decodeJSON(destForm);
                    JSONObject jsonDef = JSONObject.parseObject(defaultVal);
                    int isTab = 0;
                    if (jsonDef.containsKey("isTab")) {
                        isTab = jsonDef.getIntValue("isTab");
                        if (isTab == 1) {
                            destForm = jsonDef.getString("destForm");
                            formDbOther = formDbOther.getFormDb(destForm);
                            sbUl.append("<li><a href='#tabs-" + destForm + "'>" + formDbOther.getName() + "</a></li>");
                            sbDiv.append("<div id='tabs-" + destForm + "' class='tabDiv'></div>");
                        }
                    }
                }
            }
        }

        boolean isTab = false;
        if (sbUl.length() > 0) {
            isTab = true;
        }
        jsonResult.put("isTab", isTab);
        String strUl = "<ul>" + "<li><a href='#tabs-" + fd.getCode() + "'>" + fd.getName() + "</a></li>" + sbUl.toString() + "</ul>";
        String strDiv = "<div id='tabs-" + fd.getCode() + "' class='tabDiv'>" + content + "</div>" + sbDiv.toString();
        jsonResult.put("tabs", strUl + strDiv);*/

        jsonResult.put("content", content);
        jsonResult.put("isReply", wpd.isReply());
        jsonResult.put("flowStatus", wf.getStatus());
        jsonResult.put("isProgress", fd.isProgress());

        if (fd.isProgress()) {
            jsonResult.put("progress", fdao.getCwsProgress());
        }

        jsonResult.put("isReturnBack", wf.isReturnBack());

        jsonResult.put("PLUS_TYPE_BEFORE", WorkflowActionDb.PLUS_TYPE_BEFORE);
        jsonResult.put("PLUS_TYPE_AFTER", WorkflowActionDb.PLUS_TYPE_AFTER);
        jsonResult.put("PLUS_TYPE_CONCURRENT", WorkflowActionDb.PLUS_TYPE_CONCURRENT);

        jsonResult.put("PLUS_MODE_ORDER", WorkflowActionDb.PLUS_MODE_ORDER);
        jsonResult.put("PLUS_MODE_ONE", WorkflowActionDb.PLUS_MODE_ONE);
        jsonResult.put("PLUS_MODE_ALL", WorkflowActionDb.PLUS_MODE_ALL);

        jsonResult.put("isDebug", lf.isDebug());

        String fieldWrite = "," + StrUtil.getNullString(wa.getFieldWrite()).trim() + ",";
        String fieldHide = "," + StrUtil.getNullString(wa.getFieldHide()).trim() + ",";
        Vector<FormField> vFields = fd.getFields();
        int formView = wa.getFormView();
        if (formView != WorkflowActionDb.VIEW_DEFAULT) {
            FormViewDb fvd = new FormViewDb();
            fvd = fvd.getFormViewDb(formView);
            String form = fvd.getString("content");
            String ieVersion = fvd.getString("ie_version");
            FormParser fp = new FormParser();
            vFields = fp.parseCtlFromView(form, ieVersion, fd);
        }

        com.alibaba.fastjson.JSONArray aryFieldWritable = new com.alibaba.fastjson.JSONArray();
        Iterator<FormField> irFields = vFields.iterator();
        while (irFields.hasNext()) {
            FormField ff = irFields.next();
            boolean isWrite = fieldWrite.contains("," + ff.getName() + ",");
            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("fieldTitle", ff.getTitle());
            json.put("isWrite", isWrite);
            json.put("fieldName", ff.getName());
            aryFieldWritable.add(json);
        }
        jsonResult.put("aryFieldWritable", aryFieldWritable);

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "flowProcess aryFieldWritable", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        String[] fds = StrUtil.getNullString(wa.getFieldWrite()).trim().split(",");
        int len = fds.length;

        com.alibaba.fastjson.JSONArray aryNestFieldWritable = new com.alibaba.fastjson.JSONArray();
        // 取出嵌套表
        irFields = vFields.iterator();
        while (irFields.hasNext()) {
            FormField ff = irFields.next();
            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu == null) {
                    DebugUtil.e(getClass(), "flowProcess", ff.getTitle() + " 宏控件: " + ff.getMacroType() + " 不存在");
                } else if (mu.getNestType() != MacroCtlUnit.NEST_TYPE_NONE) {
                    String defaultVal = "";
                    String nestFormCode = ff.getDefaultValue();
                    if (mu.getNestType() == MacroCtlUnit.NEST_DETAIL_LIST) {
                        defaultVal = StrUtil.decodeJSON(ff.getDescription());
                    } else {
                        defaultVal = ff.getDescription();
                        if ("".equals(defaultVal)) {
                            defaultVal = ff.getDefaultValueRaw();
                        }
                        defaultVal = StrUtil.decodeJSON(defaultVal); // ff.getDefaultValueRaw()
                    }
                    // 20131123 fgf 添加
                    JSONObject json = JSONObject.parseObject(defaultVal);
                    nestFormCode = json.getString("destForm");

                    FormDb nestfd = new FormDb();
                    nestfd = nestfd.getFormDb(nestFormCode);

                    msd = msd.getModuleSetupDbOrInit(nestFormCode);
                    String[] fields = msd.getColAry(false, "list_field");
                    for (FormField ff2 : nestfd.getFields()) {
                        String txt = ff2.getTitle() + "(嵌套表-" + nestfd.getName() + ")";
                        // 判断是否已被选中
                        boolean isFound = false;
                        for (int i = 0; i < len; i++) {
                            if (("nest." + ff2.getName()).equals(fds[i])) {
                                isFound = true;
                                break;
                            }
                        }
                        com.alibaba.fastjson.JSONObject jsonObj = new com.alibaba.fastjson.JSONObject();
                        jsonObj.put("fieldTitle", ff2.getTitle());
                        jsonObj.put("fieldName", ff2.getName());
                        jsonObj.put("fieldText", txt);
                        jsonObj.put("isWrite", isFound);
                        aryNestFieldWritable.add(jsonObj);
                    }
                }
            }
        }
        jsonResult.put("aryNestFieldWritable", aryNestFieldWritable);
        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "flowProcess aryNestFieldWritable", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        com.alibaba.fastjson.JSONArray aryFieldHide = new com.alibaba.fastjson.JSONArray();
        irFields = vFields.iterator();
        while (irFields.hasNext()) {
            FormField ff = irFields.next();
            boolean isHide = fieldHide.contains("," + ff.getName() + ",");
            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("isHide", isHide);
            json.put("fieldTitle", ff.getTitle());
            json.put("fieldName", ff.getName());
            aryFieldHide.add(json);
        }
        jsonResult.put("aryFieldHide", aryFieldHide);

        com.alibaba.fastjson.JSONArray aryNextFieldHide = new com.alibaba.fastjson.JSONArray();
        fds = StrUtil.getNullString(wa.getFieldHide()).trim().split(",");
        len = fds.length;
        // 取出嵌套表
        irFields = vFields.iterator();
        while (irFields.hasNext()) {
            FormField ff = irFields.next();
            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu == null) {
                    DebugUtil.e(getClass(), "flowProcess", ff.getTitle() + " 宏控件: " + ff.getMacroType() + " 不存在");
                } else if (mu.getNestType() != MacroCtlUnit.NEST_TYPE_NONE) {
                    String nestFormCode = ff.getDefaultValue();
                    String defaultVal = "";
                    if (mu.getNestType() == MacroCtlUnit.NEST_DETAIL_LIST) {
                        defaultVal = StrUtil.decodeJSON(ff.getDescription());
                    } else {
                        defaultVal = ff.getDescription();
                        if ("".equals(defaultVal)) {
                            defaultVal = ff.getDefaultValueRaw();
                        }
                        defaultVal = StrUtil.decodeJSON(defaultVal); // ff.getDefaultValueRaw()
                    }
                    // 20131123 fgf 添加
                    JSONObject json = JSONObject.parseObject(defaultVal);
                    nestFormCode = json.getString("destForm");

                    FormDb nestfd = new FormDb();
                    nestfd = nestfd.getFormDb(nestFormCode);

                    msd = msd.getModuleSetupDbOrInit(nestFormCode);

                    for (FormField ff2 : nestfd.getFields()) {
                        String txt = ff2.getTitle() + "(嵌套表-" + nestfd.getName() + ")";
                        // 判断是否已被选中
                        boolean isFound = false;
                        for (int i = 0; i < len; i++) {
                            if (("nest." + ff2.getName()).equals(fds[i])) {
                                isFound = true;
                                break;
                            }
                        }
                        com.alibaba.fastjson.JSONObject jsonObj = new com.alibaba.fastjson.JSONObject();
                        jsonObj.put("isHide", isFound);
                        jsonObj.put("fieldTitle", ff.getTitle());
                        jsonObj.put("fieldName", ff.getName());
                        jsonObj.put("fieldText", txt);
                        aryNextFieldHide.add(jsonObj);
                    }
                }
            }
        }
        jsonResult.put("aryNextFieldHide", aryNextFieldHide);
        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "flowProcess aryNextFieldHide", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        jsonResult.put("isLicenseSrc", License.getInstance().isSrc());
        jsonResult.put("unitCode", privilege.getUserUnitCode(request));
        jsonResult.put("tabIdOpener", ParamUtil.get(request, "tabIdOpener"));

        boolean isCustomRedirectUrl = false;
        String redirectUrl = WorkflowActionDb.getActionProperty(wpd, wa.getInternalName(), "redirectUrl");
        if (redirectUrl!=null && !"".equals(redirectUrl)) {
            isCustomRedirectUrl = true;
            if (!redirectUrl.contains("?")) {
                redirectUrl += "?";
            }
            else {
                redirectUrl += "&";
            }
        }
        jsonResult.put("isCustomRedirectUrl", isCustomRedirectUrl);
        jsonResult.put("redirectUrl", redirectUrl);

        jsonResult.put("isNotShowNextUsers", isNotShowNextUsers);

        boolean isActionKindRead = wa.getKind()==WorkflowActionDb.KIND_READ;
        jsonResult.put("isActionKindRead", isActionKindRead);

        boolean isDocDistributed = false;
        // 检查文件是否已分发
        if (!isActionKindRead && wf.isStarted() && wa.isDistribute()) {
            if (mad.getCheckStatus() != MyActionDb.CHECK_STATUS_SUSPEND) {
                PaperDistributeDb pdd = new PaperDistributeDb();
                int paperCount = pdd.getCountOfWorkflow(wf.getId());
                if (paperCount > 0) {
                    isDocDistributed = true;
                }
            }
        }
        jsonResult.put("isDocDistributed", isDocDistributed);

        Vector<FormField> fields = fd.getFields();
        fds = fieldWrite.split(",");
        len = fds.length;
        //将不可写的域筛选出
        for (FormField ff : fields) {
            boolean finded = false;
            for (int i = 0; i < len; i++) {
                if (ff.getName().equals(fds[i])) {
                    finded = true;
                    break;
                }
            }

            if (!finded) {
                ff.setEditable(false);
            }
        }
        String checkJs = FormUtil.doGetCheckJS(request, fields);
        String checkJsSub = checkJs.substring(9, checkJs.length() - 10);
        jsonResult.put("checkJsSub", checkJsSub);

        jsonResult.put("canUserStartFlow", wr.canUserStartFlow(request, wf));
        jsonResult.put("isBtnSaveShow", isBtnSaveShow && conf.getIsDisplay("FLOW_BUTTON_SAVE"));

        jsonResult.put("aryButton", workflowService.getToolbarButtons(wpd, lf, wf, mad, wa, returnv, flag, myname, isBtnSaveShow, isActionKindRead, canUserSeeDesignerWhenDispose, canUserSeeFlowChart));

        jsonResult.put("btnCancelAttention", conf.getBtnName("FLOW_BUTTON_CANCEL_ATTENTION").startsWith("#") ? i18nUtil.get("cancelAttention") : conf.getBtnName("FLOW_BUTTON_CANCEL_ATTENTION"));
        jsonResult.put("btnAttention", conf.getBtnName("FLOW_BUTTON_ATTENTION").startsWith("#")?i18nUtil.get("attention"):conf.getBtnName("FLOW_BUTTON_ATTENTION"));
        jsonResult.put("PLUS_TYPE_CONCURRENT", WorkflowActionDb.PLUS_TYPE_CONCURRENT);

        // 如果下一节点上设置为“表单中的人员”，或者限定了部门表单域，则绑定change事件，以重新匹配人员
        List<String> bindFieldList = new ArrayList<>();
        Vector<WorkflowActionDb> vto = wa.getLinkToActions();
        for (WorkflowActionDb towa : vto) {
            String jobCode = towa.getJobCode();
            if (jobCode.startsWith(WorkflowActionDb.PRE_TYPE_FIELD_USER)) {
                String fieldNames = jobCode.substring((WorkflowActionDb.PRE_TYPE_FIELD_USER + "_").length());
                if (!fieldNames.startsWith("nest.")) {
                    String[] fieldAry = StrUtil.split(fieldNames, ",");
                    if (fieldAry == null) {
                        continue;
                    }
                    Collections.addAll(bindFieldList, fieldAry);
                }
            } else {
                String deptField = WorkflowActionDb.getActionProperty(wpd, towa.getInternalName(), "deptField");
                if (!StringUtils.isEmpty(deptField)) {
                    bindFieldList.add(deptField);
                }
            }
        }
        jsonResult.put("bindFieldList", bindFieldList);
        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "flowProcess bindFieldList", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }

        // 如果处理过程显示于下方
        boolean flowProcessShowOnBottom = cfg.getBooleanProperty("flowProcessShowOnBottom");
        jsonResult.put("flowProcessShowOnBottom", flowProcessShowOnBottom);

        com.alibaba.fastjson.JSONObject matchJson;
        try {
            matchJson = workflowService.matchBranchAndUser(request, myActionId, actionId);
        } catch (ErrMsgException e) {
            matchJson = new com.alibaba.fastjson.JSONObject();
            LogUtil.getLog(getClass()).error(e);
        }
        jsonResult.put("matchJson", matchJson);

        boolean isPageStyleLight = false;
        msd = msd.getModuleSetupDbOrInit(lf.getFormCode());
        if (msd.getPageStyle()==ConstUtil.PAGE_STYLE_LIGHT) {
            isPageStyleLight = true;
        }
        jsonResult.put("isPageStyleLight", isPageStyleLight);
        jsonResult.put("skinPath", SkinMgr.getSkinPath(request, false));
        jsonResult.put("flowTypeCode", wpd.getTypeCode());
        jsonResult.put("level", wf.getLevel());
        jsonResult.put("levelDesc", WorkflowDb.getLevelDesc(wf.getLevel()));

        jsonResult.put("LEVEL_NORMAL", WorkflowDb.LEVEL_NORMAL);
        jsonResult.put("LEVEL_IMPORTANT", WorkflowDb.LEVEL_IMPORTANT);
        jsonResult.put("LEVEL_URGENT", WorkflowDb.LEVEL_URGENT);

        jsonResult.put("bindFieldList", bindFieldList);
        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "flowProcess end", String.valueOf(((double)System.currentTimeMillis() - t)/1000));
        }
        return new Result<>(jsonResult);
    }

    @ApiOperation(value = "渲染显示规则脚本", notes = "流程处理初始化", httpMethod = "GET")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "myActionId", value = "待办记录的ID", required = true, dataType = "Long"),
    })
    @RequestMapping(value = "/flow/flowProcessScript", method={RequestMethod.GET,RequestMethod.POST})
    @ResponseBody
    public Result<Object> flowProcessScript(@RequestParam(value="actionId", required = true)Integer actionId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("script", flowRender.rendScriptByAction(actionId, false));
        return new Result<>(jsonObject);
    }

    /**
     * PC端处理自由流程
     * @param
     * @param model
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/flow/flowProcessFree", method={RequestMethod.GET,RequestMethod.POST})
    public Result<Object> flowProcessFree(@RequestParam(value="myActionId", required = true)Long myActionId, Model model) {
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        String myname = privilege.getUser( request );

        UserMgr um = new UserMgr();
        UserDb myUser = um.getUserDb(myname);

        MyActionDb mad = new MyActionDb();
        mad = mad.getMyActionDb(myActionId);

        if (!mad.isLoaded()) {
            return new Result<>(false, "由于上一节点重新激活或撤回，待办记录已不存在");
        } else if (mad.getCheckStatus() == MyActionDb.CHECK_STATUS_TRANSFER) {
            return new Result<>(false, "流程已转办，不需要再处理");
        }

        // 权限检查
        if (!mad.getUserName().equals(myname) && !mad.getProxyUserName().equals(myname)) {
            return new Result<>(false, i18nUtil.get("pvg_invalid"));
        }

        WorkflowActionDb wa = new WorkflowActionDb();
        int actionId = (int)mad.getActionId();
        wa = wa.getWorkflowActionDb(actionId);
        if ( wa==null || !wa.isLoaded() ) {
            return new Result<>(false, "流程中的相应动作：" + actionId + "不存在！");
        }

        if (mad.getCheckStatus()==MyActionDb.CHECK_STATUS_TRANSFER) {
            return new Result<>(false, "流程已转办，不需要再处理");
        }

        int flowId = wa.getFlowId();

        // 置嵌套表需要用到的cwsId
        request.setAttribute("cwsId", "" + flowId);
        request.setAttribute("pageType", "flow");
        request.setAttribute("workflowActionId", "" + wa.getId());
        // 置macro_js_ntko.jsp中需要用到的myActionId
        request.setAttribute("myActionId", "" + myActionId);

        WorkflowMgr wfm = new WorkflowMgr();
        WorkflowDb wf = wfm.getWorkflowDb(flowId);
        if (!wf.isLoaded()) {
            return new Result<>(false, "流程不存在");
        }

        if (wf.getStatus()==WorkflowDb.STATUS_DELETED) {
            return new Result<>(false, i18nUtil.get("flowDeleted"));
        }

        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());
        if (lf == null) {
            return new Result<>(false, "流程类型已被删除");
        }

        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());

        // 置NestSheetCtl需要用到的formCode
        request.setAttribute("formCode", lf.getFormCode());

        // 锁定流程
        wfm.lock(wf, myname);

        // 如果是未读状态
        if (!mad.isReaded()) {
            mad.setReaded(true);
            mad.setReadDate(new java.util.Date());
            mad.save();
        }

        WorkflowPredefineDb wfp = new WorkflowPredefineDb();
        wfp = wfp.getPredefineFlowOfFree(wf.getTypeCode());

        boolean isFlowManager = false;
        try {
            if (wfp.canUserDo(myUser, wf.getTypeCode(), "del")) {
                isFlowManager = true;
            }
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
            return new Result<>(false, e.getMessage());
        }

        if (!wfp.isReactive()) {
            if (wa.getStatus()!= WorkflowActionDb.STATE_DOING && wa.getStatus()!= WorkflowActionDb.STATE_RETURN) {
                return new Result<>(false, "流程中动作节点不处在正处理或被打回状态，可能已经被处理过了");
            }
        }

        com.alibaba.fastjson.JSONObject jsonResult = new com.alibaba.fastjson.JSONObject();

        String op = ParamUtil.get(request, "op");

        String action = ParamUtil.get(request, "action");

        com.redmoon.oa.Config cfg = com.redmoon.oa.Config.getInstance();
        String flowExpireUnit = cfg.get("flowExpireUnit");
        boolean isHour = !"day".equals(flowExpireUnit);
        if ("day".equals(flowExpireUnit)) {
            flowExpireUnit = "天";
        } else {
            flowExpireUnit = "小时";
        }
        jsonResult.put("flowExpireUnit", flowExpireUnit);

        jsonResult.put("action", action);
        jsonResult.put("formCode", lf.getFormCode());

        boolean isActionStateNotPlus = mad.getActionStatus() != WorkflowActionDb.STATE_PLUS;
        jsonResult.put("isActionStateNotPlus", isActionStateNotPlus);

        FormDAO fdao = new FormDAO();
        fdao = fdao.getFormDAOByCache(flowId, fd);
        ModuleSetupDb msd = new ModuleSetupDb();
        msd = msd.getModuleSetupDb(lf.getFormCode());
        String formJs = ModuleUtil.parseScript(flowId, fdao.getId(), lf.getFormCode(), null, -1, null, null, ConstUtil.PAGE_TYPE_LIST, msd.getScript("form_js"));
        jsonResult.put("formJs", StrUtil.getNullStr(formJs));

        boolean isRoleMemberOfFlow = false;
        String rolesOfFlow = "";
        String[][] rolePrivs = wfp.getRolePrivsOfFree();
        int privLen = rolePrivs.length;
        for (int i = 0; i < privLen; i++) {
            if (rolePrivs[i][0].equals(RoleDb.CODE_MEMBER)) {
                isRoleMemberOfFlow = true;
            }
            if (rolesOfFlow.equals("")) {
                rolesOfFlow = rolePrivs[i][0];
            } else {
                rolesOfFlow += "," + rolePrivs[i][0];
            }
        }
        jsonResult.put("rolesOfFlow", rolesOfFlow);

        String tabTitle = wf.getTitle().replaceAll("\r\n", "").trim().length() >= 8 ? wf.getTitle().replaceAll("\r\n", "").trim().substring(0, 8) : wf.getTitle().replaceAll("\r\n", "").trim();
        jsonResult.put("tabTitle", tabTitle);

        boolean isMyActionExpire = mad.getExpireDate() != null;
        jsonResult.put("isMyActionExpire", isMyActionExpire);
        jsonResult.put("expirationDate", mad.getExpireDate());

        jsonResult.put("isUseSMS", cfg.getBooleanProperty("isUseSMS"));
        jsonResult.put("flowAutoSMSRemind", cfg.getBooleanProperty("flowAutoSMSRemind"));
        jsonResult.put("flowAutoMsgRemind", cfg.getBooleanProperty("flowAutoMsgRemind"));

        com.alibaba.fastjson.JSONArray aryNextUser = new com.alibaba.fastjson.JSONArray();
        Vector<WorkflowActionDb> vto = wa.getLinkToActions();
        Iterator<WorkflowActionDb> irto = vto.iterator();
        String userRealNames="", nextActionUsers="";
        int count = 0;
        while (irto.hasNext()) {
            WorkflowActionDb toAction = irto.next();
            if ("".equals(nextActionUsers)) {
                nextActionUsers = toAction.getUserName();
                userRealNames = um.getUserDb(toAction.getUserName()).getRealName();
            } else {
                nextActionUsers += "," + toAction.getUserName();
                userRealNames += "," + um.getUserDb(toAction.getUserName()).getRealName();
            }
            count++;
            WorkflowLinkDb wld = new WorkflowLinkDb();
            wld = wld.getWorkflowLinkDbForward(wa, toAction);
            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("userNames", toAction.getUserName());

            String realNames = "";
            String userNames = toAction.getUserName().replaceAll("，", ",");
            String[] userAry = StrUtil.split(userNames, ",");
            if (userAry!=null) {
                for (String s : userAry) {
                    if ("".equals(realNames)) {
                        realNames = um.getUserDb(s).getRealName();
                    } else {
                        realNames += "，" + um.getUserDb(s).getRealName();
                    }
                }
            }
            json.put("realNames", realNames);
            json.put("expireHourPrefix", StrUtil.escape(toAction.getUserName()).toUpperCase());
            json.put("expireHour", wld.getExpireHour());
            aryNextUser.add(json);
        }
        jsonResult.put("aryNextUser", aryNextUser);
        jsonResult.put("userRealNames", userRealNames);
        jsonResult.put("nextActionUsers", nextActionUsers);

        wf = wfm.getWorkflowDb(flowId);
        Vector<WorkflowActionDb> returnv = wa.getLinkReturnActions();
        com.alibaba.fastjson.JSONArray aryReturnAction = new com.alibaba.fastjson.JSONArray();
        boolean hasReturnAction = false;
        if (returnv.size() > 0) {
            hasReturnAction = true;
            for (WorkflowActionDb returnwa : returnv) {
                if (returnwa.getStatus() != WorkflowActionDb.STATE_IGNORED) {
                    com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
                    jsonObject.put("actionId", returnwa.getId());
                    jsonObject.put("actionTitle", returnwa.getTitle());
                    jsonObject.put("realName", returnwa.getUserRealName());
                    aryReturnAction.add(jsonObject);
                }
            }
        }
        jsonResult.put("hasReturnAction", hasReturnAction);
        jsonResult.put("aryReturnAction", aryReturnAction);

        jsonResult.put("isFlowLevelDisplay", cfg.getBooleanProperty("isFlowLevelDisplay"));
        jsonResult.put("isFlowStarted", wf.isStarted());
        jsonResult.put("levelImg", WorkflowMgr.getLevelImg(request, wf));
        jsonResult.put("level", wf.getLevel());
        jsonResult.put("LEVEL_NORMAL", WorkflowDb.LEVEL_NORMAL);
        jsonResult.put("LEVEL_IMPORTANT", WorkflowDb.LEVEL_IMPORTANT);
        jsonResult.put("LEVEL_URGENT", WorkflowDb.LEVEL_URGENT);

        jsonResult.put("flowTitle", wf.getTitle());
        jsonResult.put("flowTitleDefault", lf.getName());

        String starterName = wf.getUserName();
        String starterRealName = "";
        if (starterName != null) {
            UserDb starter = um.getUserDb(wf.getUserName());
            starterRealName = starter.getRealName();
        }
        jsonResult.put("starterRealName", starterRealName);
        jsonResult.put("beginDate", wf.getBeginDate());
        jsonResult.put("flowIsRemarkShow", cfg.getBooleanProperty("flowIsRemarkShow"));
        jsonResult.put("myActionResult", mad.getResult());

        jsonResult.put("aryMyAction", workflowService.listProcess(flowId));

        int doc_id = wf.getDocId();
        DocumentMgr dm = new DocumentMgr();
        Document doc = dm.getDocument(doc_id);
        UserDb user = new UserDb();
        user = user.getUserDb(privilege.getUser(request));

        jsonResult.put("isHasAttachment", fd.isHasAttachment());

        jsonResult.put("flowId", flowId);
        jsonResult.put("actionId", actionId);
        jsonResult.put("myActionId", myActionId);

        if (lf.getQueryId() != Leaf.QUERY_NONE) {
            // 判断权限，管理员能看见查询，其它人员根据角色进行判断
            String[] roles = StrUtil.split(lf.getQueryRole(), ",");
            boolean canSeeQuery = false;
            if (!privilege.isUserPrivValid(request, "admin")) {
                if (roles != null) {
                    for (String role : roles) {
                        if (user.isUserOfRole(role)) {
                            canSeeQuery = true;
                            break;
                        }
                    }
                } else {
                    canSeeQuery = true;
                }
            } else {
                canSeeQuery = true;
            }
            FormQueryDb aqd = new FormQueryDb();
            aqd = aqd.getFormQueryDb((int) lf.getQueryId());
            boolean canQuery = canSeeQuery && aqd.isLoaded();
            jsonResult.put("canQuery", canQuery);
            if (canQuery) {
                jsonResult.put("queryId", lf.getQueryId());
                jsonResult.put("queryName", aqd.getQueryName());
                String colratio = "";
                String colP = aqd.getColProps();
                if (colP == null || "".equals(colP)) {
                    colP = "[]";
                }
                int tableWidth = 0;
                com.alibaba.fastjson.JSONArray jsonArray = com.alibaba.fastjson.JSONArray.parseArray(colP);
                for (int i = 0; i < jsonArray.size(); i++) {
                    com.alibaba.fastjson.JSONObject jsonCol = jsonArray.getJSONObject(i);
                    if (jsonCol.getBoolean("hide")) {
                        continue;
                    }
                    String name = (String) jsonCol.get("name");
                    if ("cws_op".equalsIgnoreCase(name)) {
                        continue;
                    }
                    tableWidth += jsonCol.getIntValue("width");
                    if ("".equals(colratio)) {
                        colratio = jsonCol.getString("width");
                    } else {
                        colratio += "," + jsonCol.getString("width");
                    }
                }
                jsonResult.put("colRatio", colratio);
                jsonResult.put("queryTableWidth", tableWidth + 2);
                String queryAjaxUrl;
                if (aqd.isScript()) {
                    queryAjaxUrl = "/flow/form_query_list_script_embed_ajax.jsp";
                } else {
                    queryAjaxUrl = "/flow/form_query_list_embed_ajax.jsp";
                }
                jsonResult.put("queryAjaxUrl", queryAjaxUrl);
                com.alibaba.fastjson.JSONObject queryCond = com.alibaba.fastjson.JSONObject.parseObject(lf.getQueryCondMap());
                jsonResult.put("queryCond", queryCond);
            }
        }
        Render rd = new Render(request, wf, doc);
        jsonResult.put("content", rd.rendFree(wa));

        jsonResult.put("isFlowStarted", wf.isStarted());
        jsonResult.put("flowStatus", wf.getStatus());
        jsonResult.put("isProgress", fd.isProgress());

        jsonResult.put("myUserName", user.getName());
        jsonResult.put("myRealName", user.getRealName());

        com.alibaba.fastjson.JSONArray aryAnnex = new com.alibaba.fastjson.JSONArray();
        WorkflowAnnexDb wad = new WorkflowAnnexDb();
        Vector<WorkflowAnnexDb> vec1 = wad.listRoot(flowId, myname);
        for (WorkflowAnnexDb workflowAnnexDb : vec1) {
            wad = workflowAnnexDb;

            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("realName", um.getUserDb(wad.getString("user_name")).getRealName());
            json.put("process", wad.getInt("progress"));
            json.put("content", wad.getString("content"));
            json.put("id", wad.getLong("id"));
            json.put("addDate", wad.getDate("add_date"));
            json.put("flowId", wad.getString("flow_id"));
            json.put("actionId", wad.getString("action_id"));
            json.put("userName", wad.getString("user_name"));
            aryAnnex.add(json);

            WorkflowAnnexDb wad2 = new WorkflowAnnexDb();
            com.alibaba.fastjson.JSONArray aryAnnexSub = new com.alibaba.fastjson.JSONArray();
            Vector<WorkflowAnnexDb> vec2 = wad2.listChildren(wad.getInt("id"), myname);
            for (WorkflowAnnexDb annexDb : vec2) {
                wad2 = annexDb;
                int id2 = (int) wad2.getLong("id");

                com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
                jsonObject.put("id", id2);
                jsonObject.put("userName", wad2.getString("user_name"));
                jsonObject.put("realName", um.getUserDb(wad2.getString("user_name")).getRealName());
                jsonObject.put("content", wad2.getString("content"));
                jsonObject.put("addDate", wad2.getDate("add_date"));
                jsonObject.put("flowId", wad2.getString("flow_id"));
                jsonObject.put("actionId", wad2.getString("action_id"));
                jsonObject.put("replyRealName", um.getUserDb(wad2.getString("reply_name")).getRealName());
                aryAnnexSub.add(jsonObject);
            }
            json.put("aryAnnexSub", aryAnnexSub);
        }
        jsonResult.put("aryAnnex", aryAnnex);
        jsonResult.put("isReturnBack", wf.isReturnBack());

        jsonResult.put("PLUS_TYPE_BEFORE", WorkflowActionDb.PLUS_TYPE_BEFORE);
        jsonResult.put("PLUS_TYPE_AFTER", WorkflowActionDb.PLUS_TYPE_AFTER);
        jsonResult.put("PLUS_TYPE_CONCURRENT", WorkflowActionDb.PLUS_TYPE_CONCURRENT);

        jsonResult.put("PLUS_MODE_ORDER", WorkflowActionDb.PLUS_MODE_ORDER);
        jsonResult.put("PLUS_MODE_ONE", WorkflowActionDb.PLUS_MODE_ONE);
        jsonResult.put("PLUS_MODE_ALL", WorkflowActionDb.PLUS_MODE_ALL);

        jsonResult.put("unitCode", privilege.getUserUnitCode(request));

        jsonResult.put("aryButton", workflowService.getToolbarBottonsFree(wfp, lf, wf, mad, wa, returnv, myname));

        com.redmoon.oa.flow.FlowConfig conf = new com.redmoon.oa.flow.FlowConfig();
        String btnNameCancelAttention = conf.getBtnName("FLOW_BUTTON_CANCEL_ATTENTION").startsWith("#")?i18nUtil.get("cancelAttention"):conf.getBtnName("FLOW_BUTTON_CANCEL_ATTENTION");
        String btnTitleCancelAttention = conf.getBtnTitle("FLOW_BUTTON_CANCEL_ATTENTION").startsWith("#")?i18nUtil.get("cancelAttention"):conf.getBtnTitle("FLOW_BUTTON_CANCEL_ATTENTION");
        jsonResult.put("btnNameCancelAttention", btnNameCancelAttention);
        jsonResult.put("btnTitleCancelAttention", btnTitleCancelAttention);

        String btnNameAttention = conf.getBtnName("FLOW_BUTTON_ATTENTION").startsWith("#")?LocalUtil.LoadString(request,"res.flow.Flow","attention"):conf.getBtnName("FLOW_BUTTON_ATTENTION");
        String btnTitleAttention = conf.getBtnTitle("FLOW_BUTTON_ATTENTION").startsWith("#")?LocalUtil.LoadString(request,"res.flow.Flow","attention"):conf.getBtnTitle("FLOW_BUTTON_ATTENTION");
        jsonResult.put("btnNameAttention", btnNameAttention);
        jsonResult.put("btnTitleAttention", btnTitleAttention);

        boolean isPageStyleLight = false;
        if (msd.getPageStyle()==ConstUtil.PAGE_STYLE_LIGHT) {
            isPageStyleLight = true;
        }
        jsonResult.put("isPageStyleLight", isPageStyleLight);

        return new Result<>(jsonResult);
    }

    /**
     * PC端处理自由流程
     * @param
     * @param model
     * @return
     */
    @RequestMapping(value = "/flowDisposeFree", method={RequestMethod.GET,RequestMethod.POST})
    public String flowDisposeFree(@RequestParam(value="myActionId", required = true)Long myActionId, Model model) {
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        String myname = privilege.getUser( request );

        UserMgr um = new UserMgr();
        UserDb myUser = um.getUserDb(myname);
        String myRealName = myUser.getRealName();

        MyActionDb mad = new MyActionDb();
        mad = mad.getMyActionDb(myActionId);

        if (!mad.isLoaded()) {
            model.addAttribute("msg", "由于上一节点重新激活或撤回，待办记录已不存在！");
            return "th/error/error";
        }
        else if (mad.getCheckStatus()==MyActionDb.CHECK_STATUS_TRANSFER) {
            model.addAttribute("msg", "流程已转办，不需要再处理！");
            return "th/error/error";
        }

        if (!mad.getUserName().equals(myname) && !mad.getProxyUserName().equals(myname)) {
            // 权限检查
            model.addAttribute("msg", i18nUtil.get("pvg_invalid"));
            return "th/error/error";
        }

        WorkflowActionDb wa = new WorkflowActionDb();
        int actionId = (int)mad.getActionId();
        wa = wa.getWorkflowActionDb(actionId);
        if ( wa==null || !wa.isLoaded() ) {
            model.addAttribute("msg", "流程中的相应动作：" + actionId + "不存在！");
            return "th/error/error";
        }

        if (mad.getCheckStatus()==MyActionDb.CHECK_STATUS_TRANSFER) {
            model.addAttribute("msg", "流程已转办，不需要再处理！");
            return "th/error/error";
        }

        int flowId = wa.getFlowId();

        // 置嵌套表需要用到的cwsId
        request.setAttribute("cwsId", "" + flowId);
        request.setAttribute("pageType", "flow");
        request.setAttribute("workflowActionId", "" + wa.getId());
        // 置macro_js_ntko.jsp中需要用到的myActionId
        request.setAttribute("myActionId", "" + myActionId);

        WorkflowMgr wfm = new WorkflowMgr();
        WorkflowDb wf = wfm.getWorkflowDb(flowId);
        if (!wf.isLoaded()) {
            model.addAttribute("msg", "流程不存在");
            return "th/error/error";
        }

        if (wf.getStatus()==WorkflowDb.STATUS_DELETED) {
            model.addAttribute("msg", i18nUtil.get("flowDeleted"));
            return "th/error/error";
        }

        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());
        if (lf == null) {
            model.addAttribute("msg", "流程类型已被删除");
            return "th/error/error";
        }

        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());

        // 置NestSheetCtl需要用到的formCode
        request.setAttribute("formCode", lf.getFormCode());

        // 锁定流程
        wfm.lock(wf, myname);

        // 如果是未读状态
        if (!mad.isReaded()) {
            mad.setReaded(true);
            mad.setReadDate(new java.util.Date());
            mad.save();
        }

        WorkflowPredefineDb wfp = new WorkflowPredefineDb();
        wfp = wfp.getPredefineFlowOfFree(wf.getTypeCode());

        boolean isFlowManager = false;
        try {
            if (wfp.canUserDo(myUser, wf.getTypeCode(), "del")) {
                isFlowManager = true;
            }
        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
            model.addAttribute("msg", e.getMessage());
            return "th/error/error";
        }

        if (!wfp.isReactive()) {
            if (wa.getStatus()!= WorkflowActionDb.STATE_DOING && wa.getStatus()!= WorkflowActionDb.STATE_RETURN) {
                model.addAttribute("msg", "流程中动作节点不处在正处理或被打回状态，可能已经被处理过了");
                return "th/error/error";
            }
        }

        String op = ParamUtil.get(request, "op");

        String action = ParamUtil.get(request, "action");

        com.redmoon.oa.Config cfg = com.redmoon.oa.Config.getInstance();
        String flowExpireUnit = cfg.get("flowExpireUnit");
        boolean isHour = !"day".equals(flowExpireUnit);
        if ("day".equals(flowExpireUnit)) {
            flowExpireUnit = "天";
        } else {
            flowExpireUnit = "小时";
        }
        model.addAttribute("flowExpireUnit", flowExpireUnit);

        model.addAttribute("skinPath", SkinMgr.getSkinPath(request, false));
        model.addAttribute("action", action);
        model.addAttribute("formCode", lf.getFormCode());

        boolean isActionStateNotPlus = mad.getActionStatus() != WorkflowActionDb.STATE_PLUS;
        model.addAttribute("isActionStateNotPlus", isActionStateNotPlus);

        boolean isRoleMemberOfFlow = false;
        String rolesOfFlow = "";
        String[][] rolePrivs = wfp.getRolePrivsOfFree();
        int privLen = rolePrivs.length;
        for (int i = 0; i < privLen; i++) {
            if (rolePrivs[i][0].equals(RoleDb.CODE_MEMBER)) {
                isRoleMemberOfFlow = true;
            }
            if (rolesOfFlow.equals("")) {
                rolesOfFlow = rolePrivs[i][0];
            } else {
                rolesOfFlow += "," + rolePrivs[i][0];
            }
        }
        model.addAttribute("rolesOfFlow", rolesOfFlow);

        String tabTitle = wf.getTitle().replaceAll("\r\n", "").trim().length() >= 8 ? wf.getTitle().replaceAll("\r\n", "").trim().substring(0, 8) : wf.getTitle().replaceAll("\r\n", "").trim();
        model.addAttribute("tabTitle", tabTitle);

        boolean isMyActionExpire = mad.getExpireDate() != null;
        model.addAttribute("isMyActionExpire", isMyActionExpire);
        model.addAttribute("expirationDate", mad.getExpireDate());

        model.addAttribute("isUseSMS", cfg.getBooleanProperty("isUseSMS"));
        model.addAttribute("flowAutoSMSRemind", cfg.getBooleanProperty("flowAutoSMSRemind"));
        model.addAttribute("flowAutoMsgRemind", cfg.getBooleanProperty("flowAutoMsgRemind"));

        com.alibaba.fastjson.JSONArray aryNextUser = new com.alibaba.fastjson.JSONArray();
        Vector vto = wa.getLinkToActions();
        Iterator irto = vto.iterator();
        String userRealNames="", nextActionUsers="";
        int count = 0;
        while (irto.hasNext()) {
            WorkflowActionDb toAction = (WorkflowActionDb) irto.next();
            if ("".equals(nextActionUsers)) {
                nextActionUsers = toAction.getUserName();
                userRealNames = um.getUserDb(toAction.getUserName()).getRealName();
            } else {
                nextActionUsers += "," + toAction.getUserName();
                userRealNames += "," + um.getUserDb(toAction.getUserName()).getRealName();
            }
            count++;
            WorkflowLinkDb wld = new WorkflowLinkDb();
            wld = wld.getWorkflowLinkDbForward(wa, toAction);
            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("userNames", toAction.getUserName());

            String realNames = "";
            String userNames = toAction.getUserName().replaceAll("，", ",");
            String[] userAry = StrUtil.split(userNames, ",");
            if (userAry!=null) {
                for (String s : userAry) {
                    if ("".equals(realNames)) {
                        realNames = um.getUserDb(s).getRealName();
                    } else {
                        realNames += "，" + um.getUserDb(s).getRealName();
                    }
                }
            }
            json.put("realNames", realNames);
            json.put("expireHourPrefix", StrUtil.escape(toAction.getUserName()).toUpperCase());
            json.put("expireHour", wld.getExpireHour());
            aryNextUser.add(json);
        }
        model.addAttribute("aryNextUser", aryNextUser);
        model.addAttribute("userRealNames", userRealNames);
        model.addAttribute("nextActionUsers", nextActionUsers);

        wf = wfm.getWorkflowDb(flowId);
        Vector<WorkflowActionDb> returnv = wa.getLinkReturnActions();
        com.alibaba.fastjson.JSONArray aryReturnAction = new com.alibaba.fastjson.JSONArray();
        boolean hasReturnAction = false;
        if (returnv.size() > 0) {
            hasReturnAction = true;
            for (WorkflowActionDb returnwa : returnv) {
                if (returnwa.getStatus() != WorkflowActionDb.STATE_IGNORED) {
                    com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
                    jsonObject.put("actionId", returnwa.getId());
                    jsonObject.put("actionTitle", returnwa.getTitle());
                    jsonObject.put("realName", returnwa.getUserRealName());
                    aryReturnAction.add(jsonObject);
                }
            }
        }
        model.addAttribute("hasReturnAction", hasReturnAction);
        model.addAttribute("aryReturnAction", aryReturnAction);

        model.addAttribute("isFlowLevelDisplay", cfg.getBooleanProperty("isFlowLevelDisplay"));
        model.addAttribute("isFlowStarted", wf.isStarted());
        model.addAttribute("levelImg", WorkflowMgr.getLevelImg(request, wf));
        model.addAttribute("level", wf.getLevel());
        model.addAttribute("LEVEL_NORMAL", WorkflowDb.LEVEL_NORMAL);
        model.addAttribute("LEVEL_IMPORTANT", WorkflowDb.LEVEL_IMPORTANT);
        model.addAttribute("LEVEL_URGENT", WorkflowDb.LEVEL_URGENT);

        model.addAttribute("flowTitle", wf.getTitle());
        model.addAttribute("flowTitleDefault", lf.getName());

        String starterName = wf.getUserName();
        String starterRealName = "";
        if (starterName != null) {
            UserDb starter = um.getUserDb(wf.getUserName());
            starterRealName = starter.getRealName();
        }
        model.addAttribute("starterRealName", starterRealName);
        model.addAttribute("beginDate", wf.getBeginDate());
        model.addAttribute("flowIsRemarkShow", cfg.getBooleanProperty("flowIsRemarkShow"));
        model.addAttribute("myActionResult", mad.getResult());

        model.addAttribute("aryMyAction", workflowService.listProcess(flowId));

        int doc_id = wf.getDocId();
        DocumentMgr dm = new DocumentMgr();
        Document doc = dm.getDocument(doc_id);
        UserDb user = new UserDb();
        user = user.getUserDb(privilege.getUser(request));

        model.addAttribute("isHasAttachment", fd.isHasAttachment());

        model.addAttribute("flowId", flowId);
        model.addAttribute("actionId", actionId);
        model.addAttribute("myActionId", myActionId);

        if (lf.getQueryId() != Leaf.QUERY_NONE) {
            // 判断权限，管理员能看见查询，其它人员根据角色进行判断
            String[] roles = StrUtil.split(lf.getQueryRole(), ",");
            boolean canSeeQuery = false;
            if (!privilege.isUserPrivValid(request, "admin")) {
                if (roles != null) {
                    for (String role : roles) {
                        if (user.isUserOfRole(role)) {
                            canSeeQuery = true;
                            break;
                        }
                    }
                } else {
                    canSeeQuery = true;
                }
            } else {
                canSeeQuery = true;
            }
            FormQueryDb aqd = new FormQueryDb();
            aqd = aqd.getFormQueryDb((int) lf.getQueryId());
            boolean canQuery = canSeeQuery && aqd.isLoaded();
            model.addAttribute("canQuery", canQuery);
            if (canQuery) {
                model.addAttribute("queryId", lf.getQueryId());
                model.addAttribute("queryName", aqd.getQueryName());
                String colratio = "";
                String colP = aqd.getColProps();
                if (colP == null || "".equals(colP)) {
                    colP = "[]";
                }
                int tableWidth = 0;
                com.alibaba.fastjson.JSONArray jsonArray = com.alibaba.fastjson.JSONArray.parseArray(colP);
                for (int i = 0; i < jsonArray.size(); i++) {
                    com.alibaba.fastjson.JSONObject jsonCol = jsonArray.getJSONObject(i);
                    if (jsonCol.getBoolean("hide")) {
                        continue;
                    }
                    String name = (String) jsonCol.get("name");
                    if ("cws_op".equalsIgnoreCase(name)) {
                        continue;
                    }
                    tableWidth += jsonCol.getIntValue("width");
                    if ("".equals(colratio)) {
                        colratio = jsonCol.getString("width");
                    } else {
                        colratio += "," + jsonCol.getString("width");
                    }
                }
                model.addAttribute("colRatio", colratio);
                model.addAttribute("queryTableWidth", tableWidth + 2);
                String queryAjaxUrl;
                if (aqd.isScript()) {
                    queryAjaxUrl = "flow/form_query_list_script_embed_ajax.jsp";
                } else {
                    queryAjaxUrl = "flow/form_query_list_embed_ajax.jsp";
                }
                model.addAttribute("queryAjaxUrl", queryAjaxUrl);
                com.alibaba.fastjson.JSONObject queryCond = com.alibaba.fastjson.JSONObject.parseObject(lf.getQueryCondMap());
                model.addAttribute("queryCond", queryCond);
            }
        }
        Render rd = new Render(request, wf, doc);
        model.addAttribute("content", rd.rendFree(wa));

        model.addAttribute("isFlowStarted", wf.isStarted());
        model.addAttribute("isReply", wfp.isReply());
        model.addAttribute("isProgress", fd.isProgress());

        model.addAttribute("myUserName", user.getName());
        model.addAttribute("myRealName", user.getRealName());

        com.alibaba.fastjson.JSONArray aryAnnex = new com.alibaba.fastjson.JSONArray();
        WorkflowAnnexDb wad = new WorkflowAnnexDb();
        Vector<WorkflowAnnexDb> vec1 = wad.listRoot(flowId, myname);
        for (WorkflowAnnexDb workflowAnnexDb : vec1) {
            wad = workflowAnnexDb;

            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("realName", um.getUserDb(wad.getString("user_name")).getRealName());
            json.put("process", wad.getInt("progress"));
            json.put("content", wad.getString("content"));
            json.put("id", wad.getLong("id"));
            json.put("addDate", wad.getDate("add_date"));
            json.put("flowId", wad.getString("flow_id"));
            json.put("actionId", wad.getString("action_id"));
            json.put("userName", wad.getString("user_name"));
            aryAnnex.add(json);

            WorkflowAnnexDb wad2 = new WorkflowAnnexDb();
            com.alibaba.fastjson.JSONArray aryAnnexSub = new com.alibaba.fastjson.JSONArray();
            Vector<WorkflowAnnexDb> vec2 = wad2.listChildren(wad.getInt("id"), myname);
            for (WorkflowAnnexDb annexDb : vec2) {
                wad2 = annexDb;
                int id2 = (int) wad2.getLong("id");

                com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
                jsonObject.put("id", id2);
                jsonObject.put("userName", wad2.getString("user_name"));
                jsonObject.put("realName", um.getUserDb(wad2.getString("user_name")).getRealName());
                jsonObject.put("content", wad2.getString("content"));
                jsonObject.put("addDate", wad2.getDate("add_date"));
                jsonObject.put("flowId", wad2.getString("flow_id"));
                jsonObject.put("actionId", wad2.getString("action_id"));
                jsonObject.put("replyRealName", um.getUserDb(wad2.getString("reply_name")).getRealName());
                aryAnnexSub.add(jsonObject);
            }
            json.put("aryAnnexSub", aryAnnexSub);
        }
        model.addAttribute("aryAnnex", aryAnnex);
        model.addAttribute("isReturnBack", wf.isReturnBack());

        model.addAttribute("PLUS_TYPE_BEFORE", WorkflowActionDb.PLUS_TYPE_BEFORE);
        model.addAttribute("PLUS_TYPE_AFTER", WorkflowActionDb.PLUS_TYPE_AFTER);
        model.addAttribute("PLUS_TYPE_CONCURRENT", WorkflowActionDb.PLUS_TYPE_CONCURRENT);

        model.addAttribute("PLUS_MODE_ORDER", WorkflowActionDb.PLUS_MODE_ORDER);
        model.addAttribute("PLUS_MODE_ONE", WorkflowActionDb.PLUS_MODE_ONE);
        model.addAttribute("PLUS_MODE_ALL", WorkflowActionDb.PLUS_MODE_ALL);

        model.addAttribute("unitCode", privilege.getUserUnitCode(request));

        model.addAttribute("aryButton", workflowService.getToolbarBottonsFree(wfp, lf, wf, mad, wa, returnv, myname));

        com.redmoon.oa.flow.FlowConfig conf = new com.redmoon.oa.flow.FlowConfig();
        String btnNameCancelAttention = conf.getBtnName("FLOW_BUTTON_CANCEL_ATTENTION").startsWith("#")?i18nUtil.get("cancelAttention"):conf.getBtnName("FLOW_BUTTON_CANCEL_ATTENTION");
        String btnTitleCancelAttention = conf.getBtnTitle("FLOW_BUTTON_CANCEL_ATTENTION").startsWith("#")?i18nUtil.get("cancelAttention"):conf.getBtnTitle("FLOW_BUTTON_CANCEL_ATTENTION");
        model.addAttribute("btnNameCancelAttention", btnNameCancelAttention);
        model.addAttribute("btnTitleCancelAttention", btnTitleCancelAttention);

        String btnNameAttention = conf.getBtnName("FLOW_BUTTON_ATTENTION").startsWith("#")?LocalUtil.LoadString(request,"res.flow.Flow","attention"):conf.getBtnName("FLOW_BUTTON_ATTENTION");
        String btnTitleAttention = conf.getBtnTitle("FLOW_BUTTON_ATTENTION").startsWith("#")?LocalUtil.LoadString(request,"res.flow.Flow","attention"):conf.getBtnTitle("FLOW_BUTTON_ATTENTION");
        model.addAttribute("btnNameAttention", btnNameAttention);
        model.addAttribute("btnTitleAttention", btnTitleAttention);

        boolean isPageStyleLight = false;
        ModuleSetupDb msd = new ModuleSetupDb();
        msd = msd.getModuleSetupDb(lf.getFormCode());
        msd = msd.getModuleSetupDbOrInit(lf.getFormCode());
        if (msd.getPageStyle()==ConstUtil.PAGE_STYLE_LIGHT) {
            isPageStyleLight = true;
        }
        model.addAttribute("isPageStyleLight", isPageStyleLight);
        return "th/flow_dispose_free";
    }

    @RequestMapping(value = "/flow/flowListPage", method=RequestMethod.GET)
    public String flowListPage(Model model) {
        // 原getInt的默认值为DISPLAY_MODE_SEARCH，360浏览器，可能会传过来有问题的值，导致进入了搜索，会致看到全部用户的待办
        int displayMode = ParamUtil.getInt(request, "displayMode", WorkflowMgr.DISPLAY_MODE_DOING); // 显示模式，0表示流程查询、1表示待办、2表示我参与的流程、3表示我发起的流程
        String op = StrUtil.getNullString(request.getParameter("op"));
        String action = ParamUtil.get(request, "action"); // sel 选择我的流程

        model.addAttribute("skinPath", SkinMgr.getSkinPath(request, false));
        model.addAttribute("action", action);

        String toa = ParamUtil.get(request, "toa");
        String msg = ParamUtil.get(request, "msg");
        boolean isShowMsg = "ok".equals(toa) && !"".equals(msg);
        if (isShowMsg) {
            model.addAttribute("msg", msg);
        }
        model.addAttribute("isShowMsg", isShowMsg);

        boolean isNav = ParamUtil.getBoolean(request, "isNav", true);
        if (!"sel".equals(action) && isNav) {
            if (displayMode == WorkflowMgr.DISPLAY_MODE_ATTEND || displayMode == WorkflowMgr.DISPLAY_MODE_MINE) {
                isNav = true;
                if (displayMode==WorkflowMgr.DISPLAY_MODE_ATTEND) {
                    model.addAttribute("navIndex", 1);
                }
                else {
                    model.addAttribute("navIndex", 2);
                }
            }
            else {
                isNav = false;
            }
        }
        else {
            isNav = false;
        }
        model.addAttribute("isNav", isNav);
        model.addAttribute("displayMode", displayMode);
        model.addAttribute("DISPLAY_MODE_ATTEND", WorkflowMgr.DISPLAY_MODE_ATTEND);
        model.addAttribute("DISPLAY_MODE_MINE", WorkflowMgr.DISPLAY_MODE_MINE);
        model.addAttribute("DISPLAY_MODE_DOING", WorkflowMgr.DISPLAY_MODE_DOING);
        model.addAttribute("DISPLAY_MODE_FAVORIATE", WorkflowMgr.DISPLAY_MODE_FAVORIATE);
        model.addAttribute("DISPLAY_MODE_SEARCH", WorkflowMgr.DISPLAY_MODE_SEARCH);

        Config cfgTop = Config.getInstance();
        model.addAttribute("flowPerformanceDisplay", cfgTop.getBooleanProperty("flowPerformanceDisplay"));

        String typeCode = ParamUtil.get(request, "typeCode");
        String title = ParamUtil.get(request, "title");
        // 发起人
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        String userName = ParamUtil.get(request, "userName");
        String myUserName = userName;
        if ("".equals(myUserName)) {
            myUserName = privilege.getUser(request);
        }
        model.addAttribute("myUserName", myUserName);

        com.alibaba.fastjson.JSONArray colProps = null;
        Leaf colLeaf = new Leaf();
        FormDb fd = new FormDb();
        if (!"".equals(typeCode)) {
            colLeaf = colLeaf.getLeaf(typeCode);
            if (colLeaf == null) {
                model.addAttribute("msg", "流程typeCode=" + typeCode + "不存在");
                return "th/error/info";
            }
            fd = fd.getFormDb(colLeaf.getFormCode());
        }

        if (colLeaf.isLoaded() && !"".equals(colLeaf.getColProps())) {
            colProps = com.alibaba.fastjson.JSONArray.parseArray(colLeaf.getColProps());
        }
        if (colProps == null) {
            colProps = com.redmoon.oa.flow.Leaf.getDefaultColProps(request, typeCode, displayMode);
        }
        // 如果是待办流程，且允许批处理，则加上复选框列
        if (displayMode == WorkflowMgr.DISPLAY_MODE_DOING && cfgTop.getBooleanProperty("canFlowDisposeBatch")) {
            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("type", "checkbox");
            json.put("align", "center");
            json.put("fixed", "left");
            colProps.add(0, json);
        }

        Leaf leaf = new Leaf();
        if (!"".equals(typeCode)) {
            leaf = leaf.getLeaf(typeCode);
            if (!leaf.isLoaded()) {
                model.addAttribute("msg", i18nUtil.get("selectTypeProcess"));
                return "th/error/info";
            }
        }

        if (displayMode==WorkflowMgr.DISPLAY_MODE_SEARCH && "".equals(typeCode)) {
            model.addAttribute("msg", i18nUtil.get("selectTypeProcess"));
            return "th/error/info";
        }

        model.addAttribute("colProps", colProps);

        LeafPriv leafPriv = null;
        boolean canQuery;
        if (!"".equals(typeCode)) {
            leafPriv = new LeafPriv(typeCode);
            canQuery = leafPriv.canUserQuery(myUserName);
        } else {
            // 当typeCode为空时需判断是否为流程管理员
            canQuery = privilege.isUserPrivValid(request, "admin");
            if (!canQuery) {
                // 判断是否具有根节点的管理权限
                leafPriv = new LeafPriv(Leaf.CODE_ROOT);
                canQuery = leafPriv.canUserQuery(myUserName) || leafPriv.canUserExamine(myUserName);
            }
        }

        if (displayMode == WorkflowMgr.DISPLAY_MODE_SEARCH) {
            // 如果是分类节点，且用户不是管理员权限
            if (leaf.getType() == Leaf.TYPE_NONE && !privilege.isUserPrivValid(myUserName, "admin")) {
                Vector<Leaf> v = new Vector<>();
                try {
                    v = leaf.getAllChild(v, leaf);
                    for (Leaf sl : v) {
                        leafPriv = new LeafPriv(sl.getCode());
                        // 如果分类节点的某个子节点无查询权限则退出
                        if (!leafPriv.canUserQuery(myUserName)) {
                            model.addAttribute("msg", i18nUtil.get("selectTypeProcess"));
                            return "th/error/info";
                        }
                    }
                } catch (ErrMsgException e) {
                    LogUtil.getLog(getClass()).error(e);
                }
            }

            if (!canQuery) {
                model.addAttribute("msg", i18nUtil.get("selectTypeProcess"));
                return "th/error/info";
            }

            if (!myUserName.equals(privilege.getUser(request))) {
                if (!leafPriv.canUserQuery(privilege.getUser(request))) {
                    model.addAttribute("msg", i18nUtil.get("pvg_invalid"));
                    return "th/error/info";
                }
            }
        }

        model.addAttribute("flowTypeCode", typeCode);
        model.addAttribute("isFlowTypeSelected", !"".equals(typeCode));
        model.addAttribute("flowTitle", title);

        model.addAttribute("STATUS_NOT_STARTED", WorkflowDb.STATUS_NOT_STARTED);
        model.addAttribute("STATUS_STARTED", WorkflowDb.STATUS_STARTED);
        model.addAttribute("STATUS_FINISHED", WorkflowDb.STATUS_FINISHED);
        model.addAttribute("STATUS_DISCARDED", WorkflowDb.STATUS_DISCARDED);
        model.addAttribute("STATUS_REFUSED", WorkflowDb.STATUS_REFUSED);

        model.addAttribute("STATUS_NOT_STARTED_DESC", WorkflowDb.getStatusDesc(WorkflowDb.STATUS_NOT_STARTED));
        model.addAttribute("STATUS_STARTED_DESC", WorkflowDb.getStatusDesc(WorkflowDb.STATUS_STARTED));
        model.addAttribute("STATUS_FINISHED_DESC", WorkflowDb.getStatusDesc(WorkflowDb.STATUS_FINISHED));
        model.addAttribute("STATUS_DISCARDED_DESC", WorkflowDb.getStatusDesc(WorkflowDb.STATUS_DISCARDED));
        model.addAttribute("STATUS_REFUSED_DESC", WorkflowDb.getStatusDesc(WorkflowDb.STATUS_REFUSED));

        model.addAttribute("TYPE_DATE", FormField.TYPE_DATE);
        model.addAttribute("TYPE_MACRO", FormField.TYPE_MACRO);
        model.addAttribute("TYPE_NUMBERIC", "numberic");
        model.addAttribute("TYPE_SELECT", FormField.TYPE_SELECT);
        model.addAttribute("TYPE_RADIO", FormField.TYPE_RADIO);
        model.addAttribute("TYPE_CHECKBOX", FormField.TYPE_CHECKBOX);

        ArrayList<String> list = new ArrayList<String>();

        boolean isCondProps = false;
        if (leaf.isLoaded() && !"".equals(leaf.getCondProps())) {
            isCondProps = true;
            com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSONObject.parseObject(leaf.getCondProps());
            MacroCtlMgr mm = new MacroCtlMgr();
            Map<String, String> checkboxGroupMap = new HashMap<String, String>();

            com.alibaba.fastjson.JSONArray ary = new com.alibaba.fastjson.JSONArray();
            String condFields = (String) json.get("fields");
            String[] fieldAry = StrUtil.split(condFields, ",");
            for (int j = 0; j < fieldAry.length; j++) {
                boolean isSub = false;
                FormDb subFormDb = null;
                String fieldName = fieldAry[j];
                FormField ff = null;
                String fieldTitle;
                String condType = (String) json.get(fieldName);
                String queryValue = ParamUtil.get(request, fieldName);

                com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();

                ff = fd.getFormField(fieldName);
                if (ff == null) {
                    jsonObject.put("fieldTitle", fieldName + "不存在");
                    continue;
                }
                if (ff.getType().equals(FormField.TYPE_CHECKBOX)) {
                    String desc = StrUtil.getNullStr(ff.getDescription());
                    if (!"".equals(desc)) {
                        fieldTitle = desc;
                    } else {
                        fieldTitle = ff.getTitle();
                    }
                    String chkGroup = StrUtil.getNullStr(ff.getDescription());
                    if (!"".equals(chkGroup)) {
                        if (!checkboxGroupMap.containsKey(chkGroup)) {
                            checkboxGroupMap.put(chkGroup, "");
                        } else {
                            continue;
                        }
                    }
                } else {
                    fieldTitle = ff.getTitle();
                }

                jsonObject.put("fieldTitle", fieldTitle);
                jsonObject.put("fieldName", fieldName);
                jsonObject.put("queryValue", queryValue);
                jsonObject.put("condType", condType);
                jsonObject.put("type", ff.getType());

                // 用于给convertToHTMLCtlForQuery辅助传值
                ff.setCondType(condType);
                if (ff.getType().equals(FormField.TYPE_DATE) || ff.getType().equals(FormField.TYPE_DATE_TIME)) {
                    jsonObject.put("typeOfField", FormField.TYPE_DATE);
                    if ("0".equals(condType)) {
                        String fDate = ParamUtil.get(request, ff.getName() + "FromDate");
                        String tDate = ParamUtil.get(request, ff.getName() + "ToDate");
                        jsonObject.put("fromDate", fDate);
                        jsonObject.put("toDate", tDate);
                        list.add(ff.getName() + "FromDate");
                        list.add(ff.getName() + "ToDate");
                    }
                    else {
                        list.add(ff.getName());
                    }
                } else if (ff.getType().equals(FormField.TYPE_MACRO)) {
                    jsonObject.put("typeOfField", FormField.TYPE_MACRO);

                    MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                    if (mu == null) {
                        DebugUtil.e(getClass(), "flowListPage", ff.getTitle() + " 宏控件: " + ff.getMacroType() + " 不存在");
                    } else {
                        String queryValueRealShow = ParamUtil.get(request, fieldName + "_realshow");
                        // 用main及other映射字段的描述替换其name，以使得生成的查询控件的id及name中带有main及other
                        FormField ffQuery = null;
                        try {
                            ffQuery = (FormField) ff.clone();
                            ffQuery.setName(fieldName);
                            IFormMacroCtl ifmc = mu.getIFormMacroCtl();
                            jsonObject.put("ctlForQuery", ifmc.convertToHTMLCtlForQuery(request, ffQuery));
                            jsonObject.put("queryValueRealShow", queryValueRealShow);
                            boolean isJudgeEmpty = false;
                            if ("text".equals(ifmc.getControlType()) || "img".equals(ifmc.getControlType()) || "textarea".equals(ifmc.getControlType())) {
                                isJudgeEmpty = true;
                            }
                            jsonObject.put("isJudgeEmpty", isJudgeEmpty);
                        } catch (CloneNotSupportedException e) {
                            LogUtil.getLog(getClass()).error(e);
                        }
                    }
                } else if (FormField.isNumeric(ff.getFieldType())) {
                    jsonObject.put("typeOfField", "numberic");

                    String nameCond = ParamUtil.get(request, fieldName + "_cond");
                    if ("".equals(nameCond)) {
                        nameCond = condType;
                    }
                    jsonObject.put("nameCond", nameCond);
                } else {
                    jsonObject.put("typeOfField", "others");

                    boolean isSpecial = false;
                    if (condType.equals(SQLBuilder.COND_TYPE_NORMAL)) {
                        if (ff.getType().equals(FormField.TYPE_SELECT)) {
                            isSpecial = true;
                            jsonObject.put("options", FormParser.getOptionsOfSelect(fd, ff));
                        }
                    } else if (ff.getType().equals(FormField.TYPE_RADIO)) {
                        isSpecial = true;
                        com.alibaba.fastjson.JSONArray aryR = new com.alibaba.fastjson.JSONArray();
                        String[][] aryRadio = FormParser.getOptionsArrayOfRadio(fd, ff);
                        for (String[] strings : aryRadio) {
                            String val = strings[0];
                            String text = strings[1];
                            com.alibaba.fastjson.JSONObject jsonR = new com.alibaba.fastjson.JSONObject();
                            jsonR.put("val", val);
                            jsonR.put("text", text);
                            aryR.add(jsonR);
                        }
                        jsonObject.put("aryRadio", aryR);
                    } else if (ff.getType().equals(FormField.TYPE_CHECKBOX)) {
                        isSpecial = true;
                        String[][] aryChk = null;
                        if (isSub) {
                            aryChk = FormParser.getOptionsArrayOfCheckbox(subFormDb, ff);
                        } else {
                            aryChk = FormParser.getOptionsArrayOfCheckbox(fd, ff);
                        }
                        com.alibaba.fastjson.JSONArray aryCh = new com.alibaba.fastjson.JSONArray();
                        for (String[] strings : aryChk) {
                            String val = strings[0];
                            String fName = strings[1];
                            if (isSub) {
                                fName = "sub:" + subFormDb.getCode() + ":" + fName;
                            }
                            String text = strings[2];
                            queryValue = ParamUtil.get(request, fName);

                            com.alibaba.fastjson.JSONObject jsonChk = new com.alibaba.fastjson.JSONObject();
                            jsonChk.put("val", val);
                            jsonChk.put("fieldName", fName);
                            jsonChk.put("queryValue", queryValue);
                            jsonChk.put("text", text);
                            aryCh.add(jsonChk);
                        }
                        jsonObject.put("aryChk", aryCh);
                    }

                    jsonObject.put("isSpecial", isSpecial);
                }
                ary.add(jsonObject);
            }
            model.addAttribute("aryCond", ary);
        }

        model.addAttribute("dateFieldList", list);
        model.addAttribute("isCondProps", isCondProps);
        model.addAttribute("IS_EMPTY", SQLBuilder.IS_EMPTY);
        model.addAttribute("IS_NOT_EMPTY", SQLBuilder.IS_NOT_EMPTY);
        model.addAttribute("COND_TYPE_NORMAL", SQLBuilder.COND_TYPE_NORMAL);

        boolean canDisposeBatch = false;
        com.redmoon.oa.Config cfg = new com.redmoon.oa.Config();
        if (displayMode==WorkflowMgr.DISPLAY_MODE_DOING && cfg.getBooleanProperty("canFlowDisposeBatch")) {
            canDisposeBatch = true;
        }

        boolean isExport = false;
        if (displayMode==WorkflowMgr.DISPLAY_MODE_SEARCH) {
            if (leaf != null && leaf.isLoaded() && leaf.getType() != Leaf.TYPE_NONE) {
                isExport = true;
            }
        }

        boolean isAdmin = privilege.isUserPrivValid(request, "admin");
        // boolean isButtons = canDisposeBatch || isExport || isAdmin;

        model.addAttribute("isAdminFlow", privilege.isUserPrivValid(request, "admin.flow") || isAdmin);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isExport", isExport);
        model.addAttribute("canDisposeBatch", canDisposeBatch);
        model.addAttribute("curUserName", privilege.getUser(request));
        model.addAttribute("STATE_RETURN", WorkflowActionDb.STATE_RETURN);
        model.addAttribute("STATE_DOING", WorkflowActionDb.STATE_DOING);
        model.addAttribute("STATE_RETURN_NAME", WorkflowActionDb.getStatusName(WorkflowActionDb.STATE_RETURN));
        model.addAttribute("STATE_DOING_NAME", WorkflowActionDb.getStatusName(WorkflowActionDb.STATE_DOING));

        return "th/flow/flow_list";
    }

    @PreAuthorize("hasAnyAuthority('admin')")
    @RequestMapping(value = "/flow/resetColProps", method={RequestMethod.GET,RequestMethod.POST}, produces={"text/html;charset=UTF-8;","application/json;charset=UTF-8;"})
    @ResponseBody
    public Result<Object> resetColProps(@RequestParam(value = "typeCode", required = false)String typeCode, @RequestParam(value = "displayMode", required = false) int displayMode) {
        Leaf colLeaf = new Leaf();
        FormDb fd = new FormDb();
        if (!"".equals(typeCode)) {
            colLeaf = colLeaf.getLeaf(typeCode);
        }
        if ("".equals(typeCode)) {
            colLeaf = colLeaf.getLeaf(Leaf.CODE_ROOT);
            colLeaf.setColProps("");
        }
        else {
            colLeaf.setColProps(com.redmoon.oa.flow.Leaf.getDefaultColProps(request, typeCode, displayMode).toString());
        }
        return new Result<>(colLeaf.update());
    }

    @ApiOperation(value = "取得列的设置", notes = "取得列的设置", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "code", value = "编码", dataType = "String"),
            @ApiImplicitParam(name = "displayMode", value = "显示模式（查询、待办、我的流程、关注的流程）", dataType = "Integer"),
    })
    // @PreAuthorize("hasAnyAuthority('admin')")
    @RequestMapping(value = "/flow/getColProps", method={RequestMethod.POST}, produces={"text/html;charset=UTF-8;","application/json;charset=UTF-8;"})
    @ResponseBody
    public Result<Object> getColProps(@RequestParam(value = "typeCode", required = false)String typeCode, @RequestParam(value = "displayMode", required = false) Integer displayMode) {
        Leaf colLeaf = new Leaf();
        JSONArray colProps = new JSONArray();
        if (!"".equals(typeCode)) {
            colLeaf = colLeaf.getLeaf(typeCode);
        }
        if ("".equals(typeCode)) {
            colLeaf = colLeaf.getLeaf(Leaf.CODE_ROOT);
        }

        if (colLeaf.isLoaded() && !StrUtil.isEmpty(colLeaf.getColProps())) {
            colProps = com.alibaba.fastjson.JSONArray.parseArray(colLeaf.getColProps());
        }
        if (colProps == null) {
            colProps = com.redmoon.oa.flow.Leaf.getDefaultColProps(request, typeCode, displayMode);
        }
        return new Result<>(colProps);
    }

    @ApiOperation(value = "取表单中的字段", notes = "取表单中的字段", httpMethod = "POST")
    @ApiImplicitParam(name = "typeCode", value = "流程类型", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/getFlowListFields", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> getFlowListFields(@RequestParam(value = "typeCode", required = false) String typeCode) {
        Leaf colLeaf = new Leaf();
        JSONArray colProps = new JSONArray();
        if (!"".equals(typeCode)) {
            colLeaf = colLeaf.getLeaf(typeCode);
        }
        if ("".equals(typeCode)) {
            colLeaf = colLeaf.getLeaf(Leaf.CODE_ROOT);
        }

        if (colLeaf.isLoaded() && !StrUtil.isEmpty(colLeaf.getColProps())) {
            colProps = com.alibaba.fastjson.JSONArray.parseArray(colLeaf.getColProps());
        }
        if (colProps == null) {
            colProps = com.redmoon.oa.flow.Leaf.getDefaultColProps(request, typeCode, WorkflowMgr.DISPLAY_MODE_SEARCH);
        } else {
            // 按顺序拼接f.id、f.flow_level、f.flow_title、f.type_code...及不在colProps中的字段
            JSONArray defaultColProps = com.redmoon.oa.flow.Leaf.getDefaultColProps(request, typeCode, WorkflowMgr.DISPLAY_MODE_SEARCH);
            JSONArray arrNot = new JSONArray();
            for (Object defo : defaultColProps) {
                JSONObject defJson = (JSONObject)defo;
                String field = defJson.getString("field");
                boolean isFound = false;
                for (Object o : colProps) {
                    JSONObject json = (JSONObject) o;
                    if (field.equals(json.getString("field")) || field.equals(json.getString("name"))) {
                        isFound = true;
                        break;
                    }
                }
                if (!isFound) {
                    arrNot.add(defJson);
                }
            }

            if (colLeaf.getType() != Leaf.TYPE_NONE) {
                FormDb fd = new FormDb();
                fd = fd.getFormDb(colLeaf.getFormCode());
                Vector<FormField> fields = fd.getFields();
                for (FormField ff : fields) {
                    boolean isFound = false;
                    for (Object o : colProps) {
                        JSONObject json = (JSONObject) o;
                        if (ff.getName().equals(json.getString("field")) || ff.getName().equals(json.getString("name"))) {
                            isFound = true;
                            break;
                        }
                    }
                    if (!isFound) {
                        JSONObject json = new JSONObject();
                        json.put("field", ff.getName());
                        json.put("title", ff.getTitle());
                        arrNot.add(json);
                    }
                }
            }
            colProps.addAll(arrNot);
        }
        return new Result<>(colProps);
    }

    @RequestMapping(value = "/flow/setExpireDate", method={RequestMethod.GET,RequestMethod.POST}, produces={"text/html;charset=UTF-8;","application/json;charset=UTF-8;"})
    @ResponseBody
    public String setExpireDate(@RequestParam(value="flowId", required = true) Integer flowId, @RequestParam(value="myActionId", required = true) Long myActionId) {
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);
        boolean isFlowManager = false;
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        LeafPriv lp = new LeafPriv(wf.getTypeCode());
        if (privilege.isUserPrivValid(request, "admin.flow")) {
            if (lp.canUserExamine(privilege.getUser(request))) {
                isFlowManager = true;
            }
        }

        if (!isFlowManager) {
            return responseUtil.getResJson(false, i18nUtil.get("pvg_invalid")).toString();
        }

        MyActionDb mad = new MyActionDb();
        mad = mad.getMyActionDb(myActionId);
        java.util.Date expireDate = DateUtil.parse(ParamUtil.get(request, "expireDate"), "yyyy-MM-dd HH:mm");
        mad.setExpireDate(expireDate);
        return responseUtil.getResJson(mad.save()).toString();
    }

    @RequestMapping(value = "/flowShowPage", method={RequestMethod.GET})
    public String flowShowPage(Model model) {
        int flowId = ParamUtil.getInt(request, "flowId",-1);
        WorkflowMgr wfm = new WorkflowMgr();
        WorkflowDb wf = wfm.getWorkflowDb(flowId);

        if (wf.getTypeCode()==null) {
            String msg = i18nUtil.get("processNotExist");
            model.addAttribute("msg", msg);
            return "th/error/error";
        }

        // 检查表单是否存在
        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());
        if (lf==null) {
            String msg = "流程：" + wf.getTitle() + " 类型不存在！";
            model.addAttribute("msg", msg);
            return "th/error/error";
        }
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();

        model.addAttribute("skinPath", SkinMgr.getSkinPath(request, false));
        model.addAttribute("formCode", lf.getFormCode());
        model.addAttribute("pageType", ConstUtil.PAGE_TYPE_FLOW_SHOW);
        model.addAttribute("flowId", flowId);
        model.addAttribute("myUserName", privilege.getUser(request));

        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());
        if (!fd.isLoaded()) {
            model.addAttribute("msg", i18nUtil.get("form") + lf.getFormCode() + i18nUtil.get("noLongerExist"));
            return "th/error/error";
        }

        com.redmoon.oa.flow.FormDAO fdao = new com.redmoon.oa.flow.FormDAO();
        fdao = fdao.getFormDAOByCache(flowId, fd);

        com.redmoon.oa.person.UserMgr um = new com.redmoon.oa.person.UserMgr();

        String myUserName = privilege.getUser(request);
        String myRealName = um.getUserDb(myUserName).getRealName();

        // 是来自于收文界面paper_received_list.jsp，置公文为已读状态
        int paperId = ParamUtil.getInt(request, "paperId", -1);
        if (paperId!=-1) {
            PaperDistributeDb pdd = new PaperDistributeDb();
            pdd = pdd.getPaperDistributeDb(paperId);

            if (pdd.getInt("is_readed")==0) {
                pdd.set("is_readed", 1);
                pdd.set("read_date", new java.util.Date());
                try {
                    pdd.save();
                } catch (ResKeyException e) {
                    LogUtil.getLog(getClass()).error(e);
                }

                // 置日程为关闭状态
                PlanDb pd = new PlanDb();
                pd = pd.getPlanDb(myUserName, PlanDb.ACTION_TYPE_PAPER_DISTRIBUTE, String.valueOf(paperId));
                if (pd!=null) {
                    pd.setClosed(true);
                    try {
                        pd.save();
                    } catch (ErrMsgException e) {
                        LogUtil.getLog(getClass()).error(e);
                    }
                }
            }
        }

        // 置嵌套表需要用到的cwsId
        request.setAttribute("cwsId", "" + flowId);
        // 置嵌套表需要用到的curOperate
        request.setAttribute("pageType", ConstUtil.PAGE_TYPE_FLOW_SHOW);
        // 置NestSheetCtl需要用到的formCode
        request.setAttribute("formCode", lf.getFormCode());

        Directory dir = new Directory();
        Leaf ft = dir.getLeaf(wf.getTypeCode());
        boolean isFree = false;
        boolean isReactive = false;
        boolean isRecall = false;
        if (ft!=null) {
            isFree = ft.getType()!=Leaf.TYPE_LIST;
            WorkflowPredefineDb wfp = new WorkflowPredefineDb();
            wfp = wfp.getPredefineFlowOfFree(wf.getTypeCode());
            isReactive = wfp.isReactive();
            isRecall = wfp.isRecall();
        }

        WorkflowRuler wr = new WorkflowRuler();

        boolean isFlowManager = false;
        LeafPriv lp = new LeafPriv(wf.getTypeCode());
        if (privilege.isUserPrivValid(request, "admin.flow")) {
            if (lp.canUserExamine(privilege.getUser(request))) {
                isFlowManager = true;
            }
        }

        boolean canUserView = lp.canUserQuery(privilege.getUser(request));

        if (!canUserView) {
            canUserView = privilege.isUserPrivValid(request, "paper.receive");
        }

        boolean isPaperReceived = false;
        if (!canUserView) {
            // 从流程控件或知会中访问
            String visitKey = ParamUtil.get(request, "visitKey");
            if (!"".equals(visitKey)) {
                String fId = String.valueOf(flowId);
                com.redmoon.oa.sso.Config ssoconfig = new com.redmoon.oa.sso.Config();
                String desKey = ssoconfig.get("key");
                visitKey = cn.js.fan.security.ThreeDesUtil.decrypthexstr(desKey, visitKey);
                if (visitKey.equals(fId)) {
                    canUserView = true;
                    isPaperReceived = true;
                }
            }
        }

        // 判断是否拥有查看流程过程的权限
        if (!isFlowManager && !canUserView) {
            // 判断是否参与了流程
            if (!wf.isUserAttended(privilege.getUser(request))) {
                model.addAttribute("msg", i18nUtil.get("pvg_invalid"));
                return "th/error/error";
            }
        }

        boolean isStarted = wf.isStarted();
        String op = ParamUtil.get(request, "op");
        String prompt = LocalUtil.LoadString(request,"res.flow.Flow","prompt");
        com.redmoon.oa.Config cfg = com.redmoon.oa.Config.getInstance();
        String canUserModifyFlow = cfg.get("canUserModifyFlow");
        String mode = "user";
        if ("true".equals(canUserModifyFlow)) {
            mode = "user";
        } else {
            mode = "view";
        }

        String flowExpireUnit = cfg.get("flowExpireUnit");
        boolean isHour = !"day".equals(flowExpireUnit);
        if (flowExpireUnit.equals("day")){
            flowExpireUnit = LocalUtil.LoadString(request,"res.flow.Flow","day");
        }
        else{
            flowExpireUnit = LocalUtil.LoadString(request,"res.flow.Flow","hour");
        }

        boolean isNav = ParamUtil.getBoolean(request, "isNav", true);
        model.addAttribute("isNav", isNav);
        // 如果是自由流程且是发起人
        boolean isFreeAndStarter = false;
        if (ft.getType()==Leaf.TYPE_FREE && myUserName.equals(wf.getUserName())) {
            isFreeAndStarter = true;
            WorkflowPredefineDb wfp = new WorkflowPredefineDb();
            wfp = wfp.getPredefineFlowOfFree(wf.getTypeCode());

            boolean isRoleMemberOfFlow = false;
            String[][] rolePrivs = wfp.getRolePrivsOfFree();
            int privLen = rolePrivs.length;
            for (int i = 0; i < privLen; i++) {
                if (rolePrivs[i][0].equals(RoleDb.CODE_MEMBER)) {
                    isRoleMemberOfFlow = true;
                    break;
                }
            }
            model.addAttribute("isRoleMemberOfFlow", isRoleMemberOfFlow);
        }

        model.addAttribute("isUseSMS", com.redmoon.oa.sms.SMSFactory.isUseSMS());

        /*Leaf lfParent = new Leaf();
        lfParent = lfParent.getLeaf(lf.getParentCode());*/

        int formViewId = ParamUtil.getInt(request, "formViewId", -1);
        FormViewDb formViewDb = new FormViewDb();
        Vector vView = formViewDb.getViews(lf.getFormCode());
        boolean hasView = false;
        if (vView.size()>0) {
            hasView = true;
            com.alibaba.fastjson.JSONArray aryView = new com.alibaba.fastjson.JSONArray();
            Iterator irView = vView.iterator();
            while (irView.hasNext()) {
                formViewDb = (FormViewDb)irView.next();
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("id", formViewDb.getLong("id"));
                json.put("name", formViewDb.getString("name"));
                aryView.add(json);
            }
            model.addAttribute("aryView", aryView);
        }
        model.addAttribute("hasView", hasView);

        WorkflowPredefineDb wpd = new WorkflowPredefineDb();
        wpd = wpd.getPredefineFlowOfFree(wf.getTypeCode());

		model.addAttribute("levelImg", WorkflowMgr.getLevelImg(request, wf));
		String flowTitle;
        if (wpd.isLight()) {
            flowTitle = MyActionMgr.renderTitle(request, wf);
        } else {
            flowTitle = wf.getTitle();
        }
        model.addAttribute("flowTitle", flowTitle);

        boolean hasQuery = false;
        if (lf.getQueryId()!=Leaf.QUERY_NONE) {
            // 判断权限，管理员能看见查询，其它人员根据角色进行判断
            String[] roles = StrUtil.split(lf.getQueryRole(), ",");
            boolean canSeeQuery = false;
            if (!privilege.isUserPrivValid(request, "admin")) {
                if (roles != null) {
                    UserDb user = new UserDb();
                    user = user.getUserDb(privilege.getUser(request));
                    for (String role : roles) {
                        if (user.isUserOfRole(role)) {
                            canSeeQuery = true;
                            break;
                        }
                    }
                } else {
                    canSeeQuery = true;
                }
            } else {
                canSeeQuery = true;
            }
            FormQueryDb aqd = new FormQueryDb();
            aqd = aqd.getFormQueryDb((int) lf.getQueryId());
            if (canSeeQuery && aqd.isLoaded()) {
                hasQuery = true;

                String colratio = "";
                String colP = aqd.getColProps();
                if (colP == null || "".equals(colP)) {
                    colP = "[]";
                }
                int tableWidth = 0;
                com.alibaba.fastjson.JSONArray jsonArray = com.alibaba.fastjson.JSONArray.parseArray(colP);
                for (int i=0; i<jsonArray.size(); i++) {
                    com.alibaba.fastjson.JSONObject json = jsonArray.getJSONObject(i);
                    if (json.getBoolean("hide")) {
                        continue;
                    }
                    String name = (String)json.get("name");
                    if ("cws_op".equalsIgnoreCase(name)) {
                        continue;
                    }
                    tableWidth += json.getIntValue("width");
                    if ("".equals(colratio)) {
                        colratio = "" + json.get("width");
                    } else {
                        colratio += "," + json.get("width");
                    }
                }

                String queryAjaxUrl;
                if (aqd.isScript()) {
                    queryAjaxUrl = "flow/form_query_list_script_embed_ajax.jsp";
                }
                else {
                    queryAjaxUrl = "flow/form_query_list_embed_ajax.jsp";
                }

                model.addAttribute("colRatio", colratio);
                model.addAttribute("queryName", aqd.getQueryName());
                model.addAttribute("queryAjaxUrl", queryAjaxUrl);
                model.addAttribute("queryId", lf.getQueryId());
                model.addAttribute("queryTableWidth", tableWidth + 2);

                com.alibaba.fastjson.JSONArray aryCond = new com.alibaba.fastjson.JSONArray();
                com.alibaba.fastjson.JSONObject condMap = com.alibaba.fastjson.JSONObject.parseObject(lf.getQueryCondMap());
                Set keys = condMap.keySet();
                for (Object o : keys) {
                    String qField = (String)o;
                    com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                    json.put("fieldName", qField);
                    json.put("fieldValue", fdao.getFieldValue(qField));
                    aryCond.add(json);
                }
                model.addAttribute("aryCond", aryCond);
            }
        }
        model.addAttribute("hasQuery", hasQuery);
        model.addAttribute("flowTypeCode", lf.getCode());

        int doc_id = wf.getDocId();
        DocumentMgr dm = new DocumentMgr();
        Document doc = dm.getDocument(doc_id);

        String starter = um.getUserDb(wf.getUserName()).getRealName();
		String flowStatusDesc = wf.getStatusDesc();
		model.addAttribute("starter", starter);
		model.addAttribute("flowStatusDesc", flowStatusDesc);

		boolean isShowDelInfo = false;
        if (isFlowManager &&  wf.getStatus()==WorkflowDb.STATUS_DELETED) {
            if (!"".equals(StrUtil.getNullStr(wf.getDelUser()))) {
                isShowDelInfo = true;
                model.addAttribute("delByUser", um.getUserDb(wf.getDelUser()).getRealName());
                model.addAttribute("delTime", DateUtil.format(wf.getDelDate(), "yyyy-MM-dd HH:mm:ss"));
            }
        }
        model.addAttribute("isShowDelInfo", isShowDelInfo);

        String content;
        Render rd = new Render(request, wf, doc);
        if (formViewId!=-1) {
            content = rd.reportForView(formViewId, true);
        }
        else {
            content = rd.report();
        }
        model.addAttribute("formViewId", formViewId);
        model.addAttribute("content", content);
        model.addAttribute("fdaoId", fdao.getId());

        java.util.Vector<IAttachment> attachments = null;
        if (doc!=null) {
            attachments = doc.getAttachments(1);
        }
        WorkflowAnnexAttachment wfaa = new WorkflowAnnexAttachment();
        java.util.Vector<WorkflowAnnexAttachment> annexAtt = wfaa.getAllAttachments(flowId);
        boolean isAttachmentShow = false;
        if ((attachments!=null && attachments.size()>0) || (annexAtt != null && annexAtt.size() > 0)) {
            isAttachmentShow = true;

            com.alibaba.fastjson.JSONArray aryAtt = new com.alibaba.fastjson.JSONArray();
            String creatorRealName = "";
            for (IAttachment am : attachments) {
                if (am.getFieldName() != null && !"".equals(am.getFieldName())) {
                    // IOS上传fieldName为upload
                    if (!am.getFieldName().startsWith("att") && !am.getFieldName().startsWith("upload")) {
                        // 不再跳过，因为ntko office在线编辑宏控件对应的为表单域的编码
                        // continue;
                    }
                }

                UserDb creator = um.getUserDb(am.getCreator());
                if (creator.isLoaded()) {
                    creatorRealName = creator.getRealName();
                }

                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("id", am.getId());
                json.put("name", am.getName());
                json.put("creatorRealName", creatorRealName);
                json.put("createDate", am.getCreateDate());
                json.put("size", NumberUtil.round((double) am.getSize() / 1024000, 2));
                boolean isToPdf = false;
                if ("true".equals(cfg.get("canConvertToPDF")) && ("doc".equals(StrUtil.getFileExt(am.getName())) || "docx".equals(StrUtil.getFileExt(am.getName())))) {
                    isToPdf = true;
                }
                json.put("isToPdf", isToPdf);

                boolean isPreview = false;
                if (cfg.getBooleanProperty("canPdfFilePreview") || cfg.getBooleanProperty("canOfficeFilePreview")) {
                    String s = Global.getRealPath() + am.getVisualPath() + "/" + am.getDiskName();
                    String htmlfile = s.substring(0, s.lastIndexOf(".")) + ".html";
                    java.io.File fileExist = new java.io.File(htmlfile);
                    if (fileExist.exists()) {
                        isPreview = true;
                    }
                }
                json.put("isPreview", isPreview);
                json.put("previewUrl", sysUtil.getRootPath() + "/" + am.getVisualPath() + "/" + am.getDiskName().substring(0, am.getDiskName().lastIndexOf(".")) + ".html");

                aryAtt.add(json);
            }
            model.addAttribute("aryAtt", aryAtt);
        }
        boolean canDelAtt = false;
        if (privilege.isUserPrivValid(request, "admin") || isFlowManager) {
            canDelAtt = true;
        }
        model.addAttribute("canDelAtt", canDelAtt);
        model.addAttribute("isAttachmentShow", isAttachmentShow);
        model.addAttribute("docId", doc_id);

        com.alibaba.fastjson.JSONArray aryAnnexAtt = new com.alibaba.fastjson.JSONArray();
        for (WorkflowAnnexAttachment wfaatt : annexAtt) {
            long annexId = wfaatt.getAnnexId();
            WorkflowAnnexDb wfadb = new WorkflowAnnexDb();
            wfadb = (WorkflowAnnexDb) wfadb.getQObjectDb(annexId);
            String annexUser = wfadb.getString("user_name");
            UserDb annexUserDb = new UserDb(annexUser);

            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("url", wfaatt.getAttachmentUrl(request));
            json.put("name", wfaatt.getName());
            json.put("realName", annexUserDb.getRealName());
            json.put("addDate", wfadb.getDate("add_date"));
            json.put("size", NumberUtil.round((double) wfaatt.getSize() / 1024000, 2));

            aryAnnexAtt.add(json);
        }
        model.addAttribute("aryAnnexAtt", aryAnnexAtt);

        MyActionDb mad = new MyActionDb();
        Vector<MyActionDb> v = mad.getMyActionDbOfFlow(flowId);
        model.addAttribute("flowPerformanceDisplay", cfg.getBooleanProperty("flowPerformanceDisplay"));
        model.addAttribute("isReactive", wpd.isReactive());
        model.addAttribute("flowIsRemarkShow", cfg.getBooleanProperty("flowIsRemarkShow"));
        model.addAttribute("flowExpireUnit", flowExpireUnit);

        model.addAttribute("CHECK_STATUS_NOT", MyActionDb.CHECK_STATUS_NOT);
        model.addAttribute("CHECK_STATUS_CHECKED", MyActionDb.CHECK_STATUS_CHECKED);
        model.addAttribute("CHECK_STATUS_WAITING_TO_DO", MyActionDb.CHECK_STATUS_WAITING_TO_DO);
        model.addAttribute("CHECK_STATUS_PASS", MyActionDb.CHECK_STATUS_PASS);
        model.addAttribute("CHECK_STATUS_PASS_BY_RETURN", MyActionDb.CHECK_STATUS_PASS_BY_RETURN);
        model.addAttribute("CHECK_STATUS_SUSPEND", MyActionDb.CHECK_STATUS_SUSPEND);
        model.addAttribute("CHECK_STATUS_RETURN", MyActionDb.CHECK_STATUS_RETURN);

        WorkflowDb wfd = new WorkflowDb();
        wfd = wfd.getWorkflowDb((int)mad.getFlowId());
        WorkflowActionDb wa = new WorkflowActionDb();

        com.alibaba.fastjson.JSONArray aryMyAction = new com.alibaba.fastjson.JSONArray();
        DeptMgr deptMgr = new DeptMgr();
        OACalendarDb oad = new OACalendarDb();
        int m=0;
        java.util.Iterator<MyActionDb> ir = v.iterator();
        while (ir.hasNext()) {
            mad = ir.next();

            String userName = mad.getUserName();
            String userRealName = "";
            if (userName != null) {
                UserDb user = um.getUserDb(mad.getUserName());
                userRealName = user.getRealName();
            }
            wa = wa.getWorkflowActionDb((int) mad.getActionId());
            m++;

            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("id", mad.getId());
            json.put("checkStatus", mad.getCheckStatus());

            String partDept = ""; // 所选择的兼职部门
            if (!"".equals(mad.getPartDept())) {
                partDept = mad.getPartDept();
            }
            String deptCodes = mad.getDeptCodes();
            String[] depts = StrUtil.split(deptCodes, ",");
            if (depts!=null) {
                String dts = "";
                int deptLen = depts.length;
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
                }
                else {
                    json.put("depts", "");
                }
            }
            else {
                json.put("depts", "");
            }

            boolean isExpired = false;
            java.util.Date chkDate = mad.getCheckDate();
            if (chkDate==null) {
                chkDate = new Date();
            }
            if (DateUtil.compare(chkDate, mad.getExpireDate())==1) {
                isExpired = true;
            }
            json.put("isExpired", isExpired);
            json.put("expireDate", DateUtil.format(mad.getExpireDate(), "MM-dd HH:mm"));

            json.put("realName", userRealName);
            json.put("isReaded", mad.isReaded());

            String privRealName = "";
            if (mad.getPrivMyActionId()!=-1) {
                MyActionDb mad2 = mad.getMyActionDb(mad.getPrivMyActionId());
                if (mad2.getUserName() != null) {
                    privRealName = um.getUserDb(mad2.getUserName()).getRealName();
                }
                else {
                    privRealName = "无";
                }
            }
            else {
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
            if (wa.getDateDelayed()!=null) {
                isDateDelayed = true;
            }else{
                json.put("receiveDate", DateUtil.format(mad.getReceiveDate(), "MM-dd HH:mm"));
                json.put("statusName", WorkflowActionDb.getStatusName(mad.getActionStatus()));

                boolean isReason = false;
                String reas = wa.getReason();
                if (reas!=null && !"".equals(reas.trim())) {
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
            if (mad.getExpireDate()!=null && DateUtil.compare(new java.util.Date(), mad.getExpireDate())==2) {
                int[] ary = DateUtil.dateDiffDHMS(mad.getExpireDate(), new java.util.Date());
                String str_day = LocalUtil.LoadString(request,"res.flow.Flow","day");
                String str_hour = LocalUtil.LoadString(request,"res.flow.Flow","h_hour");
                String str_minute = LocalUtil.LoadString(request,"res.flow.Flow","minute");
                remainDateStr = ary[0] + " "+str_day + ary[1] + " "+str_hour + ary[2] + " "+str_minute;
            }
            json.put("remainDate", remainDateStr);

            if (isHour) {
                double d = 0;
                try {
                    d = oad.getWorkHourCount(mad.getReceiveDate(), mad.getCheckDate());
                } catch (ErrMsgException e) {
                    LogUtil.getLog(getClass()).error(e);
                }
                json.put("dayOrHoutCount", NumberUtil.round(d, 1));
            }
            else {
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
            json.put("checkStatusName", checkStatusName);

            boolean isResultValueDesc = false;
            if (mad.getCheckStatus()!=0 && mad.getCheckStatus()!=MyActionDb.CHECK_STATUS_TRANSFER && mad.getCheckStatus()!=MyActionDb.CHECK_STATUS_SUSPEND) {
                if (mad.getResultValue()!=WorkflowActionDb.RESULT_VALUE_RETURN) {
                    if (mad.getSubMyActionId()==MyActionDb.SUB_MYACTION_ID_NONE) {
                        isResultValueDesc = true;
                        model.addAttribute("resultValueDesc", "<BR>(" + WorkflowActionDb.getResultValueDesc(mad.getResultValue()) + ")");
                    }
                }
            }
            json.put("isResultValueDesc", isResultValueDesc);
            json.put("alterTime", DateUtil.format(mad.getAlterTime(), "MM-dd HH:mm"));
            json.put("result", MyActionMgr.renderResult(request, mad));

            boolean isSubMyAction = false;
            if (mad.getSubMyActionId()!=MyActionDb.SUB_MYACTION_ID_NONE) {
                MyActionDb submad = new MyActionDb();
                submad = submad.getMyActionDb(mad.getSubMyActionId());
                isSubMyAction = true;
                json.put("subFlowLink", "&nbsp;<a href=\"javascript:;\" onclick=\"addTab('" + i18nUtil.get("subprocess") + "', '" + request.getContextPath() + "/flowShowPage.do?flowId=" + submad.getFlowId() + "')\"><font color='red'>" + i18nUtil.get("subprocess") + "</font></a>");
            }
            json.put("isSubMyAction", isSubMyAction);

            boolean isReactiveBtnShow = false;
            if (isReactive && (wf.getStatus()==WorkflowDb.STATUS_FINISHED || wf.getStatus()==WorkflowDb.STATUS_DISCARDED) && (mad.getUserName().equals(myUserName) || mad.getProxyUserName().equals(myUserName)) && mad.isChecked() && mad.getCheckStatus()!=MyActionDb.CHECK_STATUS_SUSPEND_OVER && mad.getCheckStatus()!=MyActionDb.CHECK_STATUS_PASS_BY_RETURN) {
                isReactiveBtnShow = true;
            }
            json.put("isReactiveBtnShow", isReactiveBtnShow);

            boolean isRecallBtnShow = false;
            if (isRecall && mad.canRecall(myUserName)) {
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
                if (!mad.getChecker().equals(UserDb.SYSTEM)) {
                    if ((myUserName.equals(mad.getUserName()) || myUserName.equals(mad.getProxyUserName())) && !mad.isChecked() && mad.getCheckStatus() != MyActionDb.CHECK_STATUS_WAITING_TO_DO) {
                        isHandleBtnShow = true;
                    }
                }
            }
            json.put("isHandleBtnShow", isHandleBtnShow);

            boolean isRemindBtnShow = false;
            boolean canRemind = cfg.getBooleanProperty("flowCanRemind");
            if (canRemind && !isPaperReceived && !mad.isChecked() && !privilege.getUser(request).equals(mad.getUserName()) && mad.getCheckStatus()!=MyActionDb.CHECK_STATUS_PASS && mad.getCheckStatus()!=MyActionDb.CHECK_STATUS_WAITING_TO_DO) {
                String action = "action=" + MessageDb.ACTION_FLOW_DISPOSE + "|myActionId=" + mad.getId();
                isRemindBtnShow = true;
                json.put("action", action);
            }
            json.put("isRemindBtnShow", isRemindBtnShow);
            json.put("userName", mad.getUserName());

            aryMyAction.add(json);
        }
        model.addAttribute("aryMyAction", aryMyAction);

        boolean isLight = false;
        if (isFree) {
            if ((myUserName.equals(mad.getUserName()) || myUserName.equals(mad.getProxyUserName())) && !mad.isChecked()) {
                if (wpd.isLight()) {
                    isLight = true;
                }
            }
        }
        model.addAttribute("isFree", isFree);
        model.addAttribute("isLight", isLight);

        boolean isDelayed = false;
        WorkflowActionDb actionDelayed = new WorkflowActionDb();
        Vector<WorkflowActionDb> vdelayed = actionDelayed.getActionsDelayedOfFlow(flowId);
        Iterator<WorkflowActionDb> irdelayed = vdelayed.iterator();
        if (vdelayed.size()>0) {
            isDelayed = true;
            com.alibaba.fastjson.JSONArray aryDelayed = new com.alibaba.fastjson.JSONArray();
            while (irdelayed.hasNext()) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                actionDelayed = (WorkflowActionDb)irdelayed.next();
                json.put("title", actionDelayed.getTitle());
                json.put("jobName", actionDelayed.getJobName());
                json.put("dateDelayed", DateUtil.format(actionDelayed.getDateDelayed(), "yyyy-MM-dd HH:mm"));
				aryDelayed.add(json);
            }
            model.addAttribute("aryDelayed", aryDelayed);
        }
        model.addAttribute("isDelayed", isDelayed);
        model.addAttribute("flowRemark", wf.getRemark());

        model.addAttribute("flowIsBtnExportWordShow", cfg.getBooleanProperty("flowIsBtnExportWordShow"));

        model.addAttribute("isFlowStarted", wf.isStarted());
        model.addAttribute("isReply", wpd.isReply());
        model.addAttribute("isProgress", fd.isProgress());

        com.alibaba.fastjson.JSONArray aryAnnex = new com.alibaba.fastjson.JSONArray();
        WorkflowAnnexDb wad = new WorkflowAnnexDb();
        Vector<WorkflowAnnexDb> vec1 = wad.listRoot(wf.getId(), myUserName);
        for (WorkflowAnnexDb workflowAnnexDb : vec1) {
            wad = workflowAnnexDb;

            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("realName", um.getUserDb(wad.getString("user_name")).getRealName());
            json.put("process", wad.getInt("progress"));
            json.put("content", wad.getString("content"));
            json.put("id", wad.getLong("id"));
            json.put("addDate", wad.getDate("add_date"));
            json.put("flowId", wad.getString("flow_id"));
            json.put("actionId", wad.getString("action_id"));
            json.put("userName", wad.getString("user_name"));
            aryAnnex.add(json);

            WorkflowAnnexDb wad2 = new WorkflowAnnexDb();
            com.alibaba.fastjson.JSONArray aryAnnexSub = new com.alibaba.fastjson.JSONArray();
            Vector<WorkflowAnnexDb> vec2 = wad2.listChildren(wad.getInt("id"), myUserName);
            for (WorkflowAnnexDb annexDb : vec2) {
                wad2 = annexDb;
                int id2 = (int) wad2.getLong("id");

                com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
                jsonObject.put("id", id2);
                jsonObject.put("userName", wad2.getString("user_name"));
                jsonObject.put("realName", um.getUserDb(wad2.getString("user_name")).getRealName());
                jsonObject.put("content", wad2.getString("content"));
                jsonObject.put("addDate", wad2.getDate("add_date"));
                jsonObject.put("flowId", wad2.getString("flow_id"));
                jsonObject.put("actionId", wad2.getString("action_id"));
                jsonObject.put("replyRealName", um.getUserDb(wad2.getString("reply_name")).getRealName());
                aryAnnexSub.add(jsonObject);
            }
            json.put("aryAnnexSub", aryAnnexSub);
        }
        model.addAttribute("aryAnnex", aryAnnex);

        boolean isPageStyleLight = false;
        ModuleSetupDb msd = new ModuleSetupDb();
        msd = msd.getModuleSetupDb(lf.getFormCode());
        if (msd.getPageStyle()==ConstUtil.PAGE_STYLE_LIGHT) {
            isPageStyleLight = true;
        }
        model.addAttribute("isPageStyleLight", isPageStyleLight);

        WorkflowRuler wrTop = new WorkflowRuler();
        WorkflowMgr wfmTop = new WorkflowMgr();
        WorkflowDb wfTop = wfmTop.getWorkflowDb(flowId);

        com.redmoon.oa.flow.Directory dirTop = new com.redmoon.oa.flow.Directory();
        Leaf ftTop = dirTop.getLeaf(wfTop.getTypeCode());
        boolean isFreeTop = false;
        if (ftTop != null) {
            isFreeTop = ftTop.getType() != Leaf.TYPE_LIST;
        }

        boolean canUserSeeFlowChartTop = cfg.getBooleanProperty("canUserSeeFlowChart");
        String canUserSeeDesignerWhenDisposeTop = cfg.get("canUserSeeDesignerWhenDispose");
        boolean isFlowManagerTop = false;
        if (privilege.isUserPrivValid(request, "admin")) {
            isFlowManagerTop = true;
        } else {
            LeafPriv lpTop = new LeafPriv(wfTop.getTypeCode());
            if (privilege.isUserPrivValid(request, "admin.flow")) {
                if (lpTop.canUserExamine(privilege.getUser(request))) {
                    isFlowManagerTop = true;
                }
            }
        }
        String visitKeyTop = ParamUtil.get(request, "visitKey");
        model.addAttribute("canManageFlow", isFlowManagerTop);
        model.addAttribute("visitKey", visitKeyTop);

        boolean isFlowChartShow = false;
        if (canUserSeeFlowChartTop || "true".equals(canUserSeeDesignerWhenDisposeTop) || isFlowManagerTop) {
            isFlowChartShow = true;
        }
        model.addAttribute("isFlowChartShow", isFlowChartShow);

        boolean canModifyTitle = false;
        if (privilege.isUserPrivValid(request, "admin") || isFlowManagerTop || wrTop.canUserModifyFlow(request, wfTop)) {
            canModifyTitle = true;
        }
        model.addAttribute("canModifyTitle", canModifyTitle);

        boolean isNotifyTabShow = false;
        PaperDistributeDb pddTop = new PaperDistributeDb();
        int paperCountTop = pddTop.getCountOfWorkflow(flowId);
        if (paperCountTop > 0) {
            isNotifyTabShow = true;
            String notifyTabName = i18nUtil.get("notify"); // "流程抄送";
            String kindTop = com.redmoon.oa.kernel.License.getInstance().getKind();
            if (kindTop.equalsIgnoreCase(com.redmoon.oa.kernel.License.KIND_COM)) {
                notifyTabName = "流程知会";
            }
            model.addAttribute("notifyTabName", notifyTabName);
        }
        model.addAttribute("isNotifyTabShow", isNotifyTabShow);

        // 如果配置了自动存档目录
        boolean isArchiveTabShow = false;
        WorkflowPredefineDb wpdTop = new WorkflowPredefineDb();
        wpdTop = wpdTop.getDefaultPredefineFlow(wfTop.getTypeCode());
        if (!"".equals(wpdTop.getDirCode())) {
            isArchiveTabShow = true;
        }
        model.addAttribute("isArchiveTabShow", isArchiveTabShow);

        return "th/flow_show";
    }

    @RequestMapping(value = "/flowModifyTitlePage", method={RequestMethod.GET})
    public String flowModifyTitlePage(@RequestParam(value = "flowId", required = false) int flowId, Model model) {
        WorkflowMgr wfm = new WorkflowMgr();
        WorkflowDb wf = wfm.getWorkflowDb(flowId);

        boolean isFlowManager = false;
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        LeafPriv lp = new LeafPriv(wf.getTypeCode());
        if (privilege.isUserPrivValid(request, "admin.flow")) {
            if (lp.canUserExamine(privilege.getUser(request))) {
                isFlowManager = true;
            }
        }

        boolean canModifyTitle = false;
        WorkflowRuler wr = new WorkflowRuler();
        if (privilege.isUserPrivValid(request, "admin") || isFlowManager || wr.canUserModifyFlow(request, wf)) {
            canModifyTitle = true;
        }
        else {
            model.addAttribute("msg", i18nUtil.get("pvg_invalid"));
            return "th/error/error";
        }
        model.addAttribute("canModifyTitle", canModifyTitle);

        model.addAttribute("skinPath", SkinMgr.getSkinPath(request, false));
        model.addAttribute("myUserName", privilege.getUser(request));
        model.addAttribute("canManageFlow", privilege.isUserPrivValid(request, "admin") || isFlowManager);

        String visitKeyTop = ParamUtil.get(request, "visitKey");
        model.addAttribute("visitKey", visitKeyTop);

        boolean isFlowChartShow = false;
        boolean isFree = false;
        com.redmoon.oa.flow.Directory dirTop = new com.redmoon.oa.flow.Directory();
        Leaf lf = dirTop.getLeaf(wf.getTypeCode());
        isFree = lf.getType() != Leaf.TYPE_LIST;

        Config cfg = Config.getInstance();
        boolean canUserSeeFlowChart = cfg.getBooleanProperty("canUserSeeFlowChart");
        boolean canUserSeeFlowImage = cfg.getBooleanProperty("canUserSeeFlowImage");
        boolean canUserSeeDesignerWhenDispose = cfg.getBooleanProperty("canUserSeeDesignerWhenDispose");
        if (canUserSeeFlowChart || canUserSeeFlowImage || canUserSeeDesignerWhenDispose || isFlowManager) {
            isFlowChartShow = true;
        }
        model.addAttribute("isFlowChartShow", isFlowChartShow);

        model.addAttribute("flowTypeName", lf.getName());
        model.addAttribute("flowTypeCode", lf.getCode());

        model.addAttribute("isFlowLevelDisplay", cfg.getBooleanProperty("isFlowLevelDisplay"));
        model.addAttribute("level", wf.getLevel());
        model.addAttribute("LEVEL_NORMAL", WorkflowDb.LEVEL_NORMAL);
        model.addAttribute("LEVEL_IMPORTANT", WorkflowDb.LEVEL_IMPORTANT);
        model.addAttribute("LEVEL_URGENT", WorkflowDb.LEVEL_URGENT);

        model.addAttribute("flowId", wf.getId());
        model.addAttribute("flowTitle", wf.getTitle());
        return "th/flow_modify_title";
    }

    @RequestMapping(value = "/flowShowChartPage", method={RequestMethod.GET})
    public String flowShowChartPage(@RequestParam(value = "flowId", required = false) int flowId, Model model) {
        WorkflowMgr wfm = new WorkflowMgr();
        WorkflowDb wf = wfm.getWorkflowDb(flowId);
        com.redmoon.oa.Config cfg = Config.getInstance();
        String flowExpireUnit = cfg.get("flowExpireUnit");
        boolean isHour = !"day".equals(flowExpireUnit);
        if ("day".equals(flowExpireUnit)) {
            flowExpireUnit = LocalUtil.LoadString(request, "res.flow.Flow", "day");
        } else {
            flowExpireUnit = LocalUtil.LoadString(request, "res.flow.Flow", "hour");
        }
        model.addAttribute("flowExpireUnit", flowExpireUnit);
        model.addAttribute("licenseKey", License.getInstance().getKey());

        boolean isFlowManager = false;
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        LeafPriv lp = new LeafPriv(wf.getTypeCode());
        if (privilege.isUserPrivValid(request, "admin.flow")) {
            if (lp.canUserExamine(privilege.getUser(request))) {
                isFlowManager = true;
            }
        }

        boolean canUserSeeFlowChart = cfg.getBooleanProperty("canUserSeeFlowChart");
        if (privilege.isUserPrivValid(request, "admin")) {
            canUserSeeFlowChart = true;
        }
        model.addAttribute("canUserSeeFlowChart", canUserSeeFlowChart);
        if (canUserSeeFlowChart) {
            String flowJson = "";
            if (StringUtils.isEmpty(wf.getFlowJson())) {
                IMyflowUtil myflowUtil = SpringUtil.getBean(IMyflowUtil.class);
                try {
                    flowJson = myflowUtil.toMyflow(wf.getFlowString());
                } catch (ErrMsgException e) {
                    LogUtil.getLog(getClass()).error(e);
                }
            } else {
                flowJson = wf.getFlowJson();
            }
            model.addAttribute("flowJson", flowJson);
        }

        model.addAttribute("skinPath", SkinMgr.getSkinPath(request, false));

        boolean canModifyTitle = false;
        WorkflowRuler wr = new WorkflowRuler();
        if (privilege.isUserPrivValid(request, "admin") || isFlowManager || wr.canUserModifyFlow(request, wf)) {
            canModifyTitle = true;
        }
        else {
            model.addAttribute("msg", i18nUtil.get("pvg_invalid"));
            return "th/error/error";
        }
        model.addAttribute("canModifyTitle", canModifyTitle);

        model.addAttribute("flowId", flowId);
        model.addAttribute("myUserName", privilege.getUser(request));
        model.addAttribute("canManageFlow", privilege.isUserPrivValid(request, "admin") || isFlowManager);

        String visitKeyTop = ParamUtil.get(request, "visitKey");
        model.addAttribute("visitKey", visitKeyTop);
        boolean isFlowChartShow = false;
        com.redmoon.oa.flow.Directory dirTop = new com.redmoon.oa.flow.Directory();
        Leaf ftTop = dirTop.getLeaf(wf.getTypeCode());
        boolean isFreeTop = false;
        if (ftTop != null) {
            isFreeTop = ftTop.getType() != Leaf.TYPE_LIST;
        }
        boolean canUserSeeFlowChartTop = cfg.getBooleanProperty("canUserSeeFlowChart");
        String canUserSeeDesignerWhenDisposeTop = cfg.get("canUserSeeDesignerWhenDispose");
        if (canUserSeeFlowChartTop || "true".equals(canUserSeeDesignerWhenDisposeTop) || isFlowManager) {
            isFlowChartShow = true;
        }
        model.addAttribute("isFlowChartShow", isFlowChartShow);
        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());
        model.addAttribute("isDebug", lf.isDebug());

        // 取出激活节点和已办节点
        com.alibaba.fastjson.JSONArray activeActions = new com.alibaba.fastjson.JSONArray();
        com.alibaba.fastjson.JSONArray finishActions = new com.alibaba.fastjson.JSONArray();
        com.alibaba.fastjson.JSONArray ignoreActions = new com.alibaba.fastjson.JSONArray();
        com.alibaba.fastjson.JSONArray discardActions = new com.alibaba.fastjson.JSONArray();
        com.alibaba.fastjson.JSONArray returnActions = new com.alibaba.fastjson.JSONArray();

        for (WorkflowActionDb wa : wf.getActions()) {
            if (wa.getStatus() == WorkflowActionDb.STATE_DOING) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("ID", wa.getInternalName());
                activeActions.add(json);
            } else if (wa.getStatus() == WorkflowActionDb.STATE_FINISHED) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("ID", wa.getInternalName());
                finishActions.add(json);
            } else if (wa.getStatus() == WorkflowActionDb.STATE_IGNORED) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("ID", wa.getInternalName());
                ignoreActions.add(json);
            } else if (wa.getStatus() == WorkflowActionDb.STATE_DISCARDED) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("ID", wa.getInternalName());
                discardActions.add(json);
            } else if (wa.getStatus() == WorkflowActionDb.STATE_RETURN) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("ID", wa.getInternalName());
                returnActions.add(json);
            }
        }

        model.addAttribute("activeActions", activeActions);
        model.addAttribute("finishActions", finishActions);
        model.addAttribute("ignoreActions", ignoreActions);
        model.addAttribute("discardActions", discardActions);
        model.addAttribute("returnActions", returnActions);

        model.addAttribute("STATE_FINISHED", WorkflowActionDb.STATE_FINISHED);
        model.addAttribute("STATE_DOING", WorkflowActionDb.STATE_DOING);
        model.addAttribute("STATE_IGNORED", WorkflowActionDb.STATE_IGNORED);
        model.addAttribute("STATE_RETURN", WorkflowActionDb.STATE_RETURN);
        model.addAttribute("STATE_DISCARDED", WorkflowActionDb.STATE_DISCARDED);

        com.alibaba.fastjson.JSONArray aryMyAction = new com.alibaba.fastjson.JSONArray();
        MyActionDb mad = new MyActionDb();
        WorkflowActionDb wa = new WorkflowActionDb();
        Vector<MyActionDb> v = mad.getMyActionDbOfFlow(flowId);
        for (MyActionDb myActionDb : v) {
            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            wa = wa.getWorkflowActionDb((int)myActionDb.getActionId());

            json.put("receiveTime", myActionDb.getReceiveDate().getTime());
            json.put("internalName", wa.getInternalName());
            json.put("actionStatus", myActionDb.getActionStatus());
            json.put("checkTime", myActionDb.getCheckDate()!=null?myActionDb.getCheckDate().getTime()+"":"999999999999999");
            json.put("checkStatus", myActionDb.isChecked()?WorkflowActionDb.STATE_FINISHED:myActionDb.getActionStatus());
            aryMyAction.add(json);
        }
        model.addAttribute("aryMyAction", aryMyAction);

        String cloudUrl = request.getContextPath();
        model.addAttribute("cloudUrl", cloudUrl);
        model.addAttribute("isTab", true);

        return "th/flow_show_chart";
    }

    @ApiOperation(value = "获取有权限发起的目录树", notes = "获取有权限发起的目录树，只能选择layer=3的节点", httpMethod = "POST")
    @ApiImplicitParam(name = "parentCode", value = "父节点编码", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/getDirTree", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<com.alibaba.fastjson.JSONArray> getDirTree(@RequestParam(value="parentCode", required = false) String parentCode, @RequestParam(value="isIcon", defaultValue = "true")Boolean isIcon) {
        if (parentCode == null) {
            parentCode = Leaf.CODE_ROOT;
        }
        Leaf lf = new Leaf();
        lf = lf.getLeaf(parentCode);
        DirectoryView dv = new DirectoryView(lf);
        com.alibaba.fastjson.JSONObject jsonRoot = new com.alibaba.fastjson.JSONObject();
        // 如果是根节点，则将code置为空，以免在前台看到是“全部类别”却查不出来引起误解
        jsonRoot.put("code", lf.getCode());
        jsonRoot.put("parentCode", lf.getParentCode());
        jsonRoot.put("name", lf.getName());
        jsonRoot.put("layer", lf.getLayer());
        dv.getTree(lf, jsonRoot, isIcon);

        com.alibaba.fastjson.JSONArray ary = new com.alibaba.fastjson.JSONArray();
        ary.add(jsonRoot);
        return new Result<>(ary);
    }

    @ApiOperation(value = "取得目录树，含启用的节点", notes = "取得目录树，含启用的节点", httpMethod = "POST")
    @ApiImplicitParam(name = "parentCode", value = "父节点编码", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/getDirTreeOpened", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<com.alibaba.fastjson.JSONArray> getDirTreeOpened(@RequestParam(value="parentCode", required = false) String parentCode, @RequestParam(value="isIcon", defaultValue = "true")Boolean isIcon) {
        if (parentCode == null) {
            parentCode = Leaf.CODE_ROOT;
        }
        Leaf lf = new Leaf();
        lf = lf.getLeaf(parentCode);
        DirectoryView dv = new DirectoryView(lf);
        com.alibaba.fastjson.JSONObject jsonRoot = new com.alibaba.fastjson.JSONObject();
        // 如果是根节点，则将code置为空，以免在前台看到是“全部类别”却查不出来引起误解
        jsonRoot.put("code", lf.getCode());
        jsonRoot.put("parentCode", lf.getParentCode());
        jsonRoot.put("name", lf.getName());
        jsonRoot.put("layer", lf.getLayer());
        dv.getTreeOpened(lf, jsonRoot);

        com.alibaba.fastjson.JSONArray ary = new com.alibaba.fastjson.JSONArray();
        ary.add(jsonRoot);
        return new Result<>(ary);
    }

    @ApiOperation(value = "获取有权限查询的目录树", notes = "获取有权限查询的目录树", httpMethod = "POST")
    @ApiImplicitParam(name = "parentCode", value = "父节点编码", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/getDirTreeForQuery", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<com.alibaba.fastjson.JSONArray> getDirTreeForQuery(@RequestParam(value="parentCode", required = false) String parentCode, @RequestParam(value="isIcon", defaultValue = "true")Boolean isIcon) {
        if (parentCode == null) {
            parentCode = Leaf.CODE_ROOT;
        }
        Leaf lf = new Leaf();
        lf = lf.getLeaf(parentCode);
        DirectoryView dv = new DirectoryView(lf);
        com.alibaba.fastjson.JSONObject jsonRoot = new com.alibaba.fastjson.JSONObject();
        // 如果是根节点，则将code置为空，以免在前台看到是“全部类别”却查不出来引起误解
        jsonRoot.put("code", lf.getCode());
        jsonRoot.put("parentCode", lf.getParentCode());
        jsonRoot.put("name", lf.getName());
        jsonRoot.put("layer", lf.getLayer());
        dv.getTreeForQuery(lf, jsonRoot, isIcon);

        com.alibaba.fastjson.JSONArray ary = new com.alibaba.fastjson.JSONArray();
        ary.add(jsonRoot);
        return new Result<>(ary);
    }

    @ApiOperation(value = "获取流程中的附件", notes = "获取流程中的附件", httpMethod = "POST")
    @ApiImplicitParam(name = "flowId", value = "流程ID", required = false, dataType = "Integer")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/listAttachment", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> listAttachment(@RequestParam(value = "flowId", required = false) Integer flowId, @RequestParam(value = "actionId", required = false) Integer actionId) {
        if (flowId == null) {
            return new Result<>(new Vector());
        } else {
            LogUtil.getLog(getClass()).error("flowId 为null");
        }
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);

        boolean canDocInRed = false, canSeal = false;
        if (actionId != null) {
            WorkflowActionDb wa = new WorkflowActionDb();
            wa = wa.getWorkflowActionDb(actionId);
            Leaf lf = new Leaf();
            lf = lf.getLeaf(wf.getTypeCode());
            canDocInRed = lf.getType() != Leaf.TYPE_FREE && wa.canReceiveRevise();
            canSeal = lf.getType() != Leaf.TYPE_FREE && wa.canSeal();
        }

        int doc_id = wf.getDocId();
        DocumentMgr dm = new DocumentMgr();
        Document doc = dm.getDocument(doc_id);

        java.util.Vector<IAttachment> attachments = null;
        if (doc != null) {
            attachments = doc.getAttachments(1);

            for (IAttachment am : attachments) {
                am.setCanDocInRed(canDocInRed && am.getCanDocInRed());
                am.setCanSeal(canSeal && am.getCanSeal());
            }
            return new Result<>(attachments);
        }
        else {
            return new Result<>(false, "流程文档不存在");
        }
    }

    @ApiOperation(value = "获取流程中指定字段的附件", notes = "获取流程中指定字段的附件", httpMethod = "POST")
    @ApiImplicitParams({
        @ApiImplicitParam(name = "flowId", value = "流程ID", required = false, dataType = "Integer"),
        @ApiImplicitParam(name = "fieldName", value = "字段名", required = false, dataType = "String ")
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/listAttachmentByField", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<com.alibaba.fastjson.JSONArray> listAttachmentByField(@RequestParam(value = "flowId", required = false) Integer flowId, @RequestParam(value = "fieldName", required = false) String fieldName) {
        try {
            return new Result<>(workflowService.listAttachmentByField(flowId, fieldName));
        } catch (ErrMsgException e) {
            return new Result<>(false, e.getMessage());
        }
    }

    @ApiOperation(value = "发起流程", notes = "发起流程", httpMethod = "POST")
    @ApiImplicitParam(name = "typeCode", value = "流程类型编码", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/init", method = {RequestMethod.POST, RequestMethod.GET}, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> init(@RequestParam(value="typeCode", required = true) String typeCode) {
        if ("".equals(typeCode) || "not".equals(typeCode)) {
            return new Result<>(false, i18nUtil.get("selectTypeProcess"));
        }

        Leaf lf = new Leaf();
        lf = lf.getLeaf(typeCode);

        if (lf == null) {
            return new Result<>(false, i18nUtil.get("processType"));
        }

        if (lf.getType() == Leaf.TYPE_NONE) {
            return new Result<>(false, i18nUtil.get("selectTypeProcess"));
        }

        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        String flowTitle = WorkflowMgr.makeTitle(request, privilege, lf);

        WorkflowMgr wm = new WorkflowMgr();
        long startActionId = -1;

        long projectId = ParamUtil.getLong(request, "projectId", -1);
        int level = ParamUtil.getInt(request, "level", WorkflowDb.LEVEL_NORMAL);

        if (lf.getType() == Leaf.TYPE_FREE) {
            try {
                startActionId = wm.initWorkflowFree(privilege.getUser(request), typeCode, flowTitle, projectId, level);
            } catch (ErrMsgException e) {
                return new Result<>(false, e.getMessage());
            }

            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("myActionId", startActionId);
            json.put("type", Leaf.TYPE_FREE);
            return new Result<>(json);
        } else {
            String op = ParamUtil.get(request, "op");
            try {
                startActionId = wm.initWorkflow(privilege.getUser(request), typeCode, flowTitle, projectId, level);
            } catch (ErrMsgException e) {
                // LogUtil.getLog(getClass()).error(e);
                return new Result<>(false, e.getMessage());
            }

            // 如果是来自于智能模块的操作列，则映射字段值
            if ("opLinkFlow".equals(op)) {
                long moduleId = ParamUtil.getLong(request, "moduleId", -1);
                if (moduleId != -1) {
                    String moduleCode = ParamUtil.get(request, "moduleCode");
                    String linkName = ParamUtil.get(request, "btnId");

                    int i = 0;
                    ModuleSetupDb msd = new ModuleSetupDb();
                    msd = msd.getModuleSetupDb(moduleCode);
                    FormDb fdModule = new FormDb();
                    fdModule = fdModule.getFormDb(msd.getString("form_code"));
                    com.redmoon.oa.visual.FormDAO fdaoModule = new com.redmoon.oa.visual.FormDAO();
                    fdaoModule = fdaoModule.getFormDAOByCache(moduleId, fdModule);

                    String op_link_name = StrUtil.getNullStr(msd.getString("op_link_name"));
                    String[] linkNames = StrUtil.split(op_link_name, ",");
                    String op_link_url = StrUtil.getNullStr(msd.getString("op_link_url"));
                    String[] linkHrefs = StrUtil.split(op_link_url, ",");

                    // 取操作列的配置，映射字段值
                    int len = 0;
                    if (linkNames != null) {
                        len = linkNames.length;
                    }
                    for (i = 0; i < len; i++) {
                        String lkName = linkNames[i];
                        if (lkName.equals(linkName)) {
                            String url = StrUtil.decodeJSON(linkHrefs[i]);
                            JSONObject json = JSONObject.parseObject(url);
                            String maps = json.getString("params");

                            MyActionDb mad = new MyActionDb();
                            mad = mad.getMyActionDb(startActionId);
                            long flowId = mad.getFlowId();
                            FormDb fdFlow = new FormDb(lf.getFormCode());
                            FormDAO fdaoFlow = new FormDAO();
                            fdaoFlow = fdaoFlow.getFormDAOByCache((int) flowId, fdFlow);

                            if (!StrUtil.isEmpty(maps)) {
                                try {
                                    // 映射字段值
                                    ModuleUtil.doMapOnFlow(request, fdaoModule, fdaoFlow, maps);
                                } catch (ErrMsgException e) {
                                    LogUtil.getLog(getClass()).error(e);
                                } catch (SQLException throwables) {
                                    throwables.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
            else if ("opBtnFlow".equals(op)) {
                // 编辑页、详情页中的按钮
                long moduleId = ParamUtil.getLong(request, "moduleId", -1);
                if (moduleId != -1) {
                    String moduleCode = ParamUtil.get(request, "moduleCode");
                    String btnId = ParamUtil.get(request, "btnId");
                    String pageType = ParamUtil.get(request, "pageType");

                    ModuleSetupDb msd = new ModuleSetupDb();
                    msd = msd.getModuleSetupDb(moduleCode);
                    FormDb fdModule = new FormDb();
                    fdModule = fdModule.getFormDb(msd.getString("form_code"));
                    com.redmoon.oa.visual.FormDAO fdaoModule = new com.redmoon.oa.visual.FormDAO();
                    fdaoModule = fdaoModule.getFormDAOByCache(moduleId, fdModule);

                    com.alibaba.fastjson.JSONArray ary = msd.getBtnProps(pageType);
                    if (ary != null) {
                        for (int i = 0; i < ary.size(); i++) {
                            com.alibaba.fastjson.JSONObject jsonBtn = ary.getJSONObject(i);
                            if (!btnId.equals(jsonBtn.getString("id"))) {
                                continue;
                            }

                            String href = jsonBtn.getString("href");
                            href = StrUtil.decodeJSON(href);
                            com.alibaba.fastjson.JSONObject jsonHref = com.alibaba.fastjson.JSONObject.parseObject(href);
                            String maps = jsonHref.getString("params");

                            MyActionDb mad = new MyActionDb();
                            mad = mad.getMyActionDb(startActionId);
                            long flowId = mad.getFlowId();
                            FormDb fdFlow = new FormDb(lf.getFormCode());
                            FormDAO fdaoFlow = new FormDAO();
                            fdaoFlow = fdaoFlow.getFormDAOByCache((int) flowId, fdFlow);

                            if (!StrUtil.isEmpty(maps)) {
                                try {
                                    // 映射字段值
                                    ModuleUtil.doMapOnFlow(request, fdaoModule, fdaoFlow, maps);
                                } catch (ErrMsgException e) {
                                    LogUtil.getLog(getClass()).error(e);
                                } catch (SQLException throwables) {
                                    throwables.printStackTrace();
                                }
                            }
                        }
                    }
                    else {
                        DebugUtil.i(getClass(), "opBtnFlow", "未找到btnProps");
                    }
                }
            } else if ("handlePaper".equals(op)) {
                // 收文处理
                int paperFlowId = ParamUtil.getInt(request, "paperFlowId", -1);
                if (paperFlowId != -1) {
                    WorkflowDb wf = new WorkflowDb();
                    wf = wf.getWorkflowDb(paperFlowId);
                    int doc_id = wf.getDocId();
                    DocumentMgr dm = new DocumentMgr();
                    Document doc = dm.getDocument(doc_id);
                    if (doc != null) {
                        FormDAO fdao = new FormDAO();
                        String visualPath = fdao.getVisualPath();
                        java.util.Vector<IAttachment> attachments = doc.getAttachments(1);
                        for (IAttachment am : attachments) {
                            String randName = FileUpload.getRandName();
                            String ext = StrUtil.getFileExt(am.getName());

                            String diskName = am.getDiskName();
                            String fileName = am.getName();
                            // 不再复制pdf的文件，而直接复制原文件
                            if (false && ("doc".equals(ext) || "docx".equals(ext))) {
                                randName += ".pdf";
                                int p = diskName.lastIndexOf(".");
                                diskName = diskName.substring(0, p);
                                diskName += ".pdf";

                                p = fileName.lastIndexOf(".");
                                fileName = fileName.substring(0, p);
                                fileName += ".pdf";
                            } else {
                                randName += "." + ext;
                            }

                            IFileService fileService = SpringUtil.getBean(IFileService.class);
                            fileService.write(Global.getRealPath() + am.getVisualPath() + "/" + diskName, visualPath, randName);

                            MyActionDb mad = new MyActionDb();
                            mad = mad.getMyActionDb(startActionId);
                            int flowId = (int) mad.getFlowId();

                            WorkflowDb mywf = new WorkflowDb();
                            mywf = mywf.getWorkflowDb(flowId);
                            int myDocId = mywf.getDocId();

                            Attachment att = new Attachment();
                            att.setDocId(myDocId);
                            att.setName(fileName);
                            att.setDiskName(randName);
                            att.setVisualPath(visualPath);
                            att.setPageNum(1);
                            att.setOrders(am.getOrders());
                            att.setFieldName("");
                            att.setCreator(privilege.getUser(request));
                            att.setSize(am.getSize());
                            att.create();
                        }
                    }
                }
            }

            // 将request中其它参数也传至url中，表单域选择窗体可能会接收此参数
            String param = "";
            Enumeration paramNames = request.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String paramName = (String) paramNames.nextElement();
                String[] paramValues = request.getParameterValues(paramName);
                if (paramValues.length == 1) {
                    String paramValue = paramValues[0];
                    // 过滤掉formCode
                    if (!"typeCode".equals(paramName)) {
                        param += "&" + paramName + "=" + StrUtil.UrlEncode(paramValue);
                    }
                }
            }

            if (!"".equals(lf.getParams())) {
                String lfParams = lf.getParams();
                lfParams = lfParams.replaceFirst("\\$userName", StrUtil.UrlEncode(privilege.getUser(request)));
                param += "&" + lfParams;
            }

            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("myActionId", startActionId);
            json.put("params", param);
            json.put("type", Leaf.TYPE_LIST);
            return new Result<>(json);
        }
    }

    @ApiOperation(value = "流程列表初始化", notes = "流程列表初始化", httpMethod = "POST")
    @ApiImplicitParam(name = "type", value = "列表类型", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/listPage", method = RequestMethod.POST, produces = {"text/html;", "application/json;charset=UTF-8;"})
    public Result<com.alibaba.fastjson.JSONObject> listPage(@RequestParam(value = "type", required = false) String type) {
        com.alibaba.fastjson.JSONObject jsonResult = new com.alibaba.fastjson.JSONObject();

        int displayMode;
        // 显示模式，0表示流程查询、1表示待办、2表示我参与的流程、3表示我发起的流程
        if ("mine".equals(type)) {
            displayMode = WorkflowMgr.DISPLAY_MODE_MINE;
        }
        else if ("attend".equals(type)) {
            displayMode = WorkflowMgr.DISPLAY_MODE_ATTEND;
        }
        else if ("favorite".equals(type)) {
            displayMode = WorkflowMgr.DISPLAY_MODE_FAVORIATE;
        }
        else if ("search".equals(type)) {
            displayMode = WorkflowMgr.DISPLAY_MODE_SEARCH;
        }
        else {
            // type=doing
            displayMode = WorkflowMgr.DISPLAY_MODE_DOING;
        }

        String action = ParamUtil.get(request, "action"); // sel 选择我的流程

        jsonResult.put("action", action);

        String toa = ParamUtil.get(request, "toa");
        String msg = ParamUtil.get(request, "msg");
        boolean isShowMsg = "ok".equals(toa) && !"".equals(msg);
        if (isShowMsg) {
            jsonResult.put("msg", msg);
        }
        jsonResult.put("isShowMsg", isShowMsg);

        boolean isNav = ParamUtil.getBoolean(request, "isNav", true);
        if (!"sel".equals(action) && isNav) {
            if (displayMode == WorkflowMgr.DISPLAY_MODE_ATTEND || displayMode == WorkflowMgr.DISPLAY_MODE_MINE) {
                isNav = true;
                if (displayMode==WorkflowMgr.DISPLAY_MODE_ATTEND) {
                    jsonResult.put("navIndex", 1);
                }
                else {
                    jsonResult.put("navIndex", 2);
                }
            }
            else {
                isNav = false;
            }
        }
        else {
            isNav = false;
        }
        jsonResult.put("isNav", isNav);
        jsonResult.put("displayMode", displayMode);
        jsonResult.put("DISPLAY_MODE_ATTEND", WorkflowMgr.DISPLAY_MODE_ATTEND);
        jsonResult.put("DISPLAY_MODE_MINE", WorkflowMgr.DISPLAY_MODE_MINE);
        jsonResult.put("DISPLAY_MODE_DOING", WorkflowMgr.DISPLAY_MODE_DOING);
        jsonResult.put("DISPLAY_MODE_FAVORIATE", WorkflowMgr.DISPLAY_MODE_FAVORIATE);
        jsonResult.put("DISPLAY_MODE_SEARCH", WorkflowMgr.DISPLAY_MODE_SEARCH);

        Config cfgTop = Config.getInstance();
        jsonResult.put("flowPerformanceDisplay", cfgTop.getBooleanProperty("flowPerformanceDisplay"));
        jsonResult.put("flowOpStyle", StrUtil.toInt(cfgTop.get("flowOpStyle"), 1));

        String typeCode = ParamUtil.get(request, "typeCode");
        String title = ParamUtil.get(request, "title");
        // 发起人
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        String myUserName = privilege.getUser(request);
        jsonResult.put("myUserName", myUserName);

        com.alibaba.fastjson.JSONArray colProps = null;
        Leaf colLeaf = new Leaf();
        FormDb fd = new FormDb();
        if (!"".equals(typeCode)) {
            colLeaf = colLeaf.getLeaf(typeCode);
            if (colLeaf == null) {
                return new Result<>(false, "流程类型 typeCode: " + typeCode + "不存在");
            }
            fd = fd.getFormDb(colLeaf.getFormCode());
        }

        if (colLeaf.isLoaded() && !StrUtil.isEmpty(colLeaf.getColProps())) {
            colProps = com.alibaba.fastjson.JSONArray.parseArray(colLeaf.getColProps());
        }
        if (colProps == null) {
            colProps = com.redmoon.oa.flow.Leaf.getDefaultColProps(request, typeCode, displayMode);
        }
        // 如果是待办流程，且允许批处理，则加上复选框列
        if (displayMode == WorkflowMgr.DISPLAY_MODE_DOING && cfgTop.getBooleanProperty("canFlowDisposeBatch")) {
            jsonResult.put("isCheckbox", true);
        }
        else {
            jsonResult.put("isCheckbox", false);
        }

        Leaf leaf = new Leaf();
        if (!"".equals(typeCode)) {
            leaf = leaf.getLeaf(typeCode);
            if (!leaf.isLoaded()) {
                return new Result<>(false, i18nUtil.get("selectTypeProcess"));
            }
        }

        /*if (displayMode==WorkflowMgr.DISPLAY_MODE_SEARCH && "".equals(typeCode)) {
            Result<com.alibaba.fastjson.JSONObject> result = new Result<>(false, i18nUtil.get("selectTypeProcess"));
            return result;
        }*/

        jsonResult.put("colProps", colProps);

        if (displayMode == WorkflowMgr.DISPLAY_MODE_SEARCH) {
            LeafPriv leafPriv = null;
            boolean canQuery = false;
            // 如果没有选节点
            if ("".equals(typeCode)) {
                // 如果总查询的权限（即根目录节点上有查询权限），则可以列出全部的记录
                canQuery = LeafPriv.canUserQueryAll(request);
                if (!canQuery) {
                    return new Result<>(false, i18nUtil.get("selectTypeProcess"));
                }
            } else {
                // 如果是分类节点，且用户不是管理员权限
                if (leaf.getType() == Leaf.TYPE_NONE) {
                    canQuery = LeafPriv.canUserQueryAll(request);
                    if (!canQuery) {
                        Vector<Leaf> v = new Vector<>();
                        try {
                            v = leaf.getAllChild(v, leaf);
                            for (Leaf sl : v) {
                                leafPriv = new LeafPriv(sl.getCode());
                                // 如果分类节点的某个子节点无查询权限则退出
                                if (!leafPriv.canUserQuery(myUserName)) {
                                    return new Result<>(false, i18nUtil.get("selectTypeProcess"));
                                }
                            }
                        } catch (ErrMsgException e) {
                            LogUtil.getLog(getClass()).error(e);
                        }
                    }
                } else {
                    leafPriv = new LeafPriv(typeCode);
                    canQuery = leafPriv.canUserQuery(myUserName);
                }
            }

            if (!canQuery) {
                return new Result<>(false, i18nUtil.get("permissionsIllegal"));
            }
        }

        jsonResult.put("flowTypeCode", typeCode);
        jsonResult.put("isFlowTypeSelected", !"".equals(typeCode));
        jsonResult.put("flowTitle", title);

        jsonResult.put("STATUS_NOT_STARTED", WorkflowDb.STATUS_NOT_STARTED);
        jsonResult.put("STATUS_STARTED", WorkflowDb.STATUS_STARTED);
        jsonResult.put("STATUS_FINISHED", WorkflowDb.STATUS_FINISHED);
        jsonResult.put("STATUS_DISCARDED", WorkflowDb.STATUS_DISCARDED);
        jsonResult.put("STATUS_REFUSED", WorkflowDb.STATUS_REFUSED);

        jsonResult.put("STATUS_NOT_STARTED_DESC", WorkflowDb.getStatusDesc(WorkflowDb.STATUS_NOT_STARTED));
        jsonResult.put("STATUS_STARTED_DESC", WorkflowDb.getStatusDesc(WorkflowDb.STATUS_STARTED));
        jsonResult.put("STATUS_FINISHED_DESC", WorkflowDb.getStatusDesc(WorkflowDb.STATUS_FINISHED));
        jsonResult.put("STATUS_DISCARDED_DESC", WorkflowDb.getStatusDesc(WorkflowDb.STATUS_DISCARDED));
        jsonResult.put("STATUS_REFUSED_DESC", WorkflowDb.getStatusDesc(WorkflowDb.STATUS_REFUSED));

        jsonResult.put("TYPE_DATE", FormField.TYPE_DATE);
        jsonResult.put("TYPE_MACRO", FormField.TYPE_MACRO);
        jsonResult.put("TYPE_NUMBERIC", "numberic");
        jsonResult.put("TYPE_SELECT", FormField.TYPE_SELECT);
        jsonResult.put("TYPE_RADIO", FormField.TYPE_RADIO);
        jsonResult.put("TYPE_CHECKBOX", FormField.TYPE_CHECKBOX);

        ArrayList<String> list = new ArrayList<String>();

        boolean isCondProps = false;
        if (leaf.isLoaded() && !"".equals(leaf.getCondProps())) {
            isCondProps = true;
            com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSONObject.parseObject(leaf.getCondProps());
            MacroCtlMgr mm = new MacroCtlMgr();
            Map<String, String> checkboxGroupMap = new HashMap<String, String>();

            com.alibaba.fastjson.JSONArray ary = new com.alibaba.fastjson.JSONArray();
            String condFields = (String) json.get("fields");
            String[] fieldAry = condFields.split(",");
            for (String fieldName : fieldAry) {
                boolean isSub = false;
                FormDb subFormDb = null;
                FormField ff = null;
                String fieldTitle;
                String condType = (String) json.get(fieldName);
                String queryValue = ParamUtil.get(request, fieldName);

                com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();

                ff = fd.getFormField(fieldName);
                if (ff == null) {
                    jsonObject.put("fieldTitle", fieldName + "不存在");
                    continue;
                }
                if (ff.getType().equals(FormField.TYPE_CHECKBOX)) {
                    String desc = StrUtil.getNullStr(ff.getDescription());
                    fieldTitle = ff.getTitle();
                    /*if (!"".equals(desc)) {
                        fieldTitle = desc;
                    } else {
                        fieldTitle = ff.getTitle();
                    }*/
                    String chkGroup = desc;
                    if (!"".equals(chkGroup)) {
                        if (!checkboxGroupMap.containsKey(chkGroup)) {
                            checkboxGroupMap.put(chkGroup, "");
                        } else {
                            continue;
                        }
                    }
                } else {
                    fieldTitle = ff.getTitle();
                }

                jsonObject.put("fieldTitle", fieldTitle);
                jsonObject.put("fieldName", fieldName);
                jsonObject.put("queryValue", queryValue);
                jsonObject.put("condType", condType);
                jsonObject.put("type", ff.getType());

                // 用于给convertToHTMLCtlForQuery辅助传值
                ff.setCondType(condType);
                if (ff.getType().equals(FormField.TYPE_DATE) || ff.getType().equals(FormField.TYPE_DATE_TIME)) {
                    jsonObject.put("typeOfField", FormField.TYPE_DATE);
                    if ("0".equals(condType)) {
                        String fDate = ParamUtil.get(request, ff.getName() + "FromDate");
                        String tDate = ParamUtil.get(request, ff.getName() + "ToDate");
                        jsonObject.put("fromDate", fDate);
                        jsonObject.put("toDate", tDate);
                        list.add(ff.getName() + "FromDate");
                        list.add(ff.getName() + "ToDate");
                    } else {
                        list.add(ff.getName());
                    }
                } else if (ff.getType().equals(FormField.TYPE_MACRO)) {
                    jsonObject.put("typeOfField", FormField.TYPE_MACRO);

                    MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                    if (mu == null) {
                        DebugUtil.e(getClass(), "listPage", ff.getTitle() + " 宏控件: " + ff.getMacroType() + " 不存在");
                    } else {
                        String queryValueRealShow = ParamUtil.get(request, fieldName + "_realshow");
                        // 用main及other映射字段的描述替换其name，以使得生成的查询控件的id及name中带有main及other
                        FormField ffQuery = null;
                        try {
                            ffQuery = (FormField) ff.clone();
                            ffQuery.setName(fieldName);
                            IFormMacroCtl ifmc = mu.getIFormMacroCtl();
                            jsonObject.put("ctlForQuery", ifmc.convertToHTMLCtlForQuery(request, ffQuery));
                            jsonObject.put("queryValueRealShow", queryValueRealShow);
                            boolean isJudgeEmpty = false;
                            if ("text".equals(ifmc.getControlType()) || "img".equals(ifmc.getControlType()) || "textarea".equals(ifmc.getControlType())) {
                                isJudgeEmpty = true;
                            }
                            jsonObject.put("isJudgeEmpty", isJudgeEmpty);
                        } catch (CloneNotSupportedException e) {
                            LogUtil.getLog(getClass()).error(e);
                        }
                    }
                } else if (FormField.isNumeric(ff.getFieldType())) {
                    jsonObject.put("typeOfField", "numberic");

                    String nameCond = ParamUtil.get(request, fieldName + "_cond");
                    if ("".equals(nameCond)) {
                        nameCond = condType;
                    }
                    jsonObject.put("nameCond", nameCond);
                } else {
                    jsonObject.put("typeOfField", "others");

                    boolean isSpecial = false;
                    if (condType.equals(SQLBuilder.COND_TYPE_NORMAL)) {
                        if (ff.getType().equals(FormField.TYPE_SELECT)) {
                            isSpecial = true;
                            jsonObject.put("options", FormParser.getOptionsOfSelect(fd, ff));
                        }
                    } else if (ff.getType().equals(FormField.TYPE_RADIO)) {
                        isSpecial = true;
                        com.alibaba.fastjson.JSONArray aryR = new com.alibaba.fastjson.JSONArray();
                        String[][] aryRadio = FormParser.getOptionsArrayOfRadio(fd, ff);
                        for (String[] strings : aryRadio) {
                            String val = strings[0];
                            String text = strings[1];
                            com.alibaba.fastjson.JSONObject jsonR = new com.alibaba.fastjson.JSONObject();
                            jsonR.put("val", val);
                            jsonR.put("text", text);
                            aryR.add(jsonR);
                        }
                        jsonObject.put("aryRadio", aryR);
                    } else if (ff.getType().equals(FormField.TYPE_CHECKBOX)) {
                        isSpecial = true;
                        String[][] aryChk = null;
                        if (isSub) {
                            aryChk = FormParser.getOptionsArrayOfCheckbox(subFormDb, ff);
                        } else {
                            aryChk = FormParser.getOptionsArrayOfCheckbox(fd, ff);
                        }
                        com.alibaba.fastjson.JSONArray aryCh = new com.alibaba.fastjson.JSONArray();
                        for (String[] strings : aryChk) {
                            String val = strings[0];
                            String fName = strings[1];
                            if (isSub) {
                                fName = "sub:" + subFormDb.getCode() + ":" + fName;
                            }
                            String text = strings[2];
                            queryValue = ParamUtil.get(request, fName);

                            com.alibaba.fastjson.JSONObject jsonChk = new com.alibaba.fastjson.JSONObject();
                            jsonChk.put("val", val);
                            jsonChk.put("fieldName", fName);
                            jsonChk.put("queryValue", queryValue);
                            jsonChk.put("text", text);
                            aryCh.add(jsonChk);
                        }
                        jsonObject.put("aryChk", aryCh);
                    }

                    jsonObject.put("isSpecial", isSpecial);
                }
                ary.add(jsonObject);
            }
            jsonResult.put("aryCond", ary);
        }

        jsonResult.put("dateFieldList", list);
        jsonResult.put("isCondProps", isCondProps);
        jsonResult.put("IS_EMPTY", SQLBuilder.IS_EMPTY);
        jsonResult.put("IS_NOT_EMPTY", SQLBuilder.IS_NOT_EMPTY);
        jsonResult.put("COND_TYPE_NORMAL", SQLBuilder.COND_TYPE_NORMAL);

        boolean canDisposeBatch = false;
        com.redmoon.oa.Config cfg = new com.redmoon.oa.Config();
        if (displayMode==WorkflowMgr.DISPLAY_MODE_DOING && cfg.getBooleanProperty("canFlowDisposeBatch")) {
            if (privilege.isUserPrivValid(request, "flow.batch")) {
                canDisposeBatch = true;
            }
        }

        boolean isExport = false;
        if (displayMode==WorkflowMgr.DISPLAY_MODE_SEARCH) {
            if (leaf != null && leaf.isLoaded() && leaf.getType() != Leaf.TYPE_NONE) {
                isExport = true;
            }
        }

        // 流程节点类型
        jsonResult.put("nodeType", leaf.getType());

        boolean isFlowManager = false;
        if (privilege.isUserPrivValid(request, "admin")) {
            isFlowManager = true;
        } else {
            if (privilege.isUserPrivValid(request, "admin.flow")) {
                if (!"".equals(typeCode)) {
                    LeafPriv lp = new LeafPriv(typeCode);
                    if (lp.canUserExamine(privilege.getUser(request))) {
                        isFlowManager = true;
                    }
                }
            }
        }
        jsonResult.put("isFlowManager", isFlowManager);

        boolean isAdmin = privilege.isUserPrivValid(request, "admin");
        // boolean isButtons = canDisposeBatch || isExport || isAdmin;
        jsonResult.put("isAdmin", isAdmin);
        jsonResult.put("isExport", isExport);
        jsonResult.put("canDisposeBatch", canDisposeBatch);
        jsonResult.put("curUserName", privilege.getUser(request));
        jsonResult.put("STATE_RETURN", WorkflowActionDb.STATE_RETURN);
        jsonResult.put("STATE_DOING", WorkflowActionDb.STATE_DOING);
        jsonResult.put("STATE_RETURN_NAME", WorkflowActionDb.getStatusName(WorkflowActionDb.STATE_RETURN));
        jsonResult.put("STATE_DOING_NAME", WorkflowActionDb.getStatusName(WorkflowActionDb.STATE_DOING));

        com.redmoon.oa.flow.FlowConfig conf = new com.redmoon.oa.flow.FlowConfig();
        boolean isBtnAttentionShow = conf.getIsDisplay("FLOW_BUTTON_ATTENTION");
        jsonResult.put("isBtnAttentionShow", isBtnAttentionShow);
        return new Result<>(jsonResult);
    }

    @ApiOperation(value = "取得某流程类型的查询条件", notes = "取得某流程类型的查询条件", httpMethod = "POST")
    @ApiImplicitParam(name = "typeCode", value = "流程类型", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/getConds", method=RequestMethod.GET)
    public Result<Object> getConds(@RequestParam(value = "typeCode", required = false) String typeCode) {
        Leaf leaf = new Leaf();
        if (!"".equals(typeCode)) {
            leaf = leaf.getLeaf(typeCode);
            if (!leaf.isLoaded()) {
                return new Result<>(false, i18nUtil.get("selectTypeProcess"));
            }
        }

        FormDb fd = new FormDb();
        fd = fd.getFormDb(leaf.getFormCode());

        com.alibaba.fastjson.JSONObject jsonResult = new com.alibaba.fastjson.JSONObject();
        com.alibaba.fastjson.JSONArray ary = new com.alibaba.fastjson.JSONArray();
        boolean isCondProps = false;
        if (leaf.isLoaded() && !"".equals(leaf.getCondProps())) {
            isCondProps = true;
            com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSONObject.parseObject(leaf.getCondProps());
            MacroCtlMgr mm = new MacroCtlMgr();
            Map<String, String> checkboxGroupMap = new HashMap<String, String>();

            String condFields = (String) json.get("fields");
            String[] fieldAry = StrUtil.split(condFields, ",");
            if (fieldAry == null) {
                fieldAry = new String[]{};
            }
            for (String fieldName : fieldAry) {
                boolean isSub = false;
                FormDb subFormDb = null;
                String fieldTitle;
                String condType = (String) json.get(fieldName);
                String queryValue = ParamUtil.get(request, fieldName);

                com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();

                FormField ff = fd.getFormField(fieldName);
                if (ff == null) {
                    jsonObject.put("fieldTitle", fieldName + "不存在");
                    continue;
                }
                if (ff.getType().equals(FormField.TYPE_CHECKBOX)) {
                    String desc = StrUtil.getNullStr(ff.getDescription());
                    /*if (!"".equals(desc)) {
                        fieldTitle = desc;
                    } else {
                        fieldTitle = ff.getTitle();
                    }*/
                    fieldTitle = ff.getTitle();
                    String chkGroup = desc;
                    if (!"".equals(chkGroup)) {
                        if (!checkboxGroupMap.containsKey(chkGroup)) {
                            checkboxGroupMap.put(chkGroup, "");
                        } else {
                            continue;
                        }
                    }
                } else {
                    fieldTitle = ff.getTitle();
                }

                jsonObject.put("fieldTitle", fieldTitle);
                jsonObject.put("fieldName", fieldName);
                jsonObject.put("queryValue", queryValue);
                jsonObject.put("condType", condType);
                jsonObject.put("type", ff.getType());

                // 用于给convertToHTMLCtlForQuery辅助传值
                ff.setCondType(condType);
                if (ff.getType().equals(FormField.TYPE_DATE) || ff.getType().equals(FormField.TYPE_DATE_TIME)) {
                    jsonObject.put("typeOfField", FormField.TYPE_DATE);
                    if ("0".equals(condType)) {
                        String fDate = ParamUtil.get(request, ff.getName() + "FromDate");
                        String tDate = ParamUtil.get(request, ff.getName() + "ToDate");
                        jsonObject.put("fromDate", fDate);
                        jsonObject.put("toDate", tDate);
                    }
                } else if (ff.getType().equals(FormField.TYPE_MACRO)) {
                    jsonObject.put("typeOfField", FormField.TYPE_MACRO);

                    MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                    if (mu == null) {
                        DebugUtil.e(getClass(), "getConds", ff.getTitle() + " 宏控件: " + ff.getMacroType() + " 不存在");
                    } else {
                        String queryValueRealShow = ParamUtil.get(request, fieldName + "_realshow");
                        // 用main及other映射字段的描述替换其name，以使得生成的查询控件的id及name中带有main及other
                        FormField ffQuery = null;
                        try {
                            ffQuery = (FormField) ff.clone();
                            ffQuery.setName(fieldName);
                            IFormMacroCtl ifmc = mu.getIFormMacroCtl();
                            jsonObject.put("ctlForQuery", ifmc.convertToHTMLCtlForQuery(request, ffQuery));
                            jsonObject.put("queryValueRealShow", queryValueRealShow);
                            boolean isJudgeEmpty = false;
                            if ("text".equals(ifmc.getControlType()) || "img".equals(ifmc.getControlType()) || "textarea".equals(ifmc.getControlType())) {
                                isJudgeEmpty = true;
                            }
                            jsonObject.put("isJudgeEmpty", isJudgeEmpty);
                        } catch (CloneNotSupportedException e) {
                            LogUtil.getLog(getClass()).error(e);
                        }
                    }
                } else if (FormField.isNumeric(ff.getFieldType())) {
                    jsonObject.put("typeOfField", "numberic");

                    String nameCond = ParamUtil.get(request, fieldName + "_cond");
                    if ("".equals(nameCond)) {
                        nameCond = condType;
                    }
                    jsonObject.put("nameCond", nameCond);
                } else {
                    jsonObject.put("typeOfField", "others");

                    boolean isSpecial = false;
                    if (condType.equals(SQLBuilder.COND_TYPE_NORMAL)) {
                        if (ff.getType().equals(FormField.TYPE_SELECT)) {
                            isSpecial = true;
                            jsonObject.put("options", FormParser.getOptionsOfSelect(fd, ff));
                        }
                    } else if (ff.getType().equals(FormField.TYPE_RADIO)) {
                        isSpecial = true;
                        com.alibaba.fastjson.JSONArray aryR = new com.alibaba.fastjson.JSONArray();
                        String[][] aryRadio = FormParser.getOptionsArrayOfRadio(fd, ff);
                        for (String[] strings : aryRadio) {
                            String val = strings[0];
                            String text = strings[1];
                            com.alibaba.fastjson.JSONObject jsonR = new com.alibaba.fastjson.JSONObject();
                            jsonR.put("val", val);
                            jsonR.put("text", text);
                            aryR.add(jsonR);
                        }
                        jsonObject.put("aryRadio", aryR);
                    } else if (ff.getType().equals(FormField.TYPE_CHECKBOX)) {
                        isSpecial = true;
                        String[][] aryChk = null;
                        if (isSub) {
                            aryChk = FormParser.getOptionsArrayOfCheckbox(subFormDb, ff);
                        } else {
                            aryChk = FormParser.getOptionsArrayOfCheckbox(fd, ff);
                        }
                        com.alibaba.fastjson.JSONArray aryCh = new com.alibaba.fastjson.JSONArray();
                        for (String[] strings : aryChk) {
                            String val = strings[0];
                            String fName = strings[1];
                            if (isSub) {
                                fName = "sub:" + subFormDb.getCode() + ":" + fName;
                            }
                            String text = strings[2];
                            queryValue = ParamUtil.get(request, fName);

                            com.alibaba.fastjson.JSONObject jsonChk = new com.alibaba.fastjson.JSONObject();
                            jsonChk.put("val", val);
                            jsonChk.put("fieldName", fName);
                            jsonChk.put("queryValue", queryValue);
                            jsonChk.put("text", text);
                            aryCh.add(jsonChk);
                        }
                        jsonObject.put("aryChk", aryCh);
                    }

                    jsonObject.put("isSpecial", isSpecial);
                }
                ary.add(jsonObject);
            }
        }
        jsonResult.put("aryCond", ary);
        jsonResult.put("isCondProps", isCondProps);
        return new Result<>(jsonResult);
    }

    @ApiOperation(value = "置某流程类型的查询条件", notes = "置某流程类型的查询条件", httpMethod = "POST")
    @ApiImplicitParam(name = "typeCode", value = "流程类型", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/setConds", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> setConds(@RequestParam(value="typeCode", required = true) String typeCode) {
        Leaf lf = new Leaf();
        lf = lf.getLeaf(typeCode);
        if (lf == null) {
            return new Result<>(false, cn.js.fan.web.SkinUtil.LoadString(request, "err_id"));
        }

        int isToolbar = ParamUtil.getInt(request, "isToolbar", 0);

        String[] queryFields = ParamUtil.getParameters(request, "queryFields");
        if (queryFields==null) {
            queryFields = new String[]{};
        }
        JSONObject json = new JSONObject();
        // 因为json是无序的，所以需要用fields记录条件字段的顺序
        String fields = "";
        for (String queryField : queryFields) {
            String token = ParamUtil.get(request, queryField + "_cond");
            json.put(queryField, token);
            if ("".equals(fields)) {
                fields = queryField;
            } else {
                fields += "," + queryField;
            }
        }
        json.put("fields", fields);
        json.put("isToolbar", isToolbar);

        lf.setCondProps(json.toString());
        return new Result<>(lf.update());
    }

    @ApiOperation(value = "取表单中的字段", notes = "取表单中的字段", httpMethod = "POST")
    @ApiImplicitParam(name = "typeCode", value = "流程类型", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/getFields", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> getFields(@RequestParam(value = "typeCode", required = false) String typeCode) {
        Leaf lf = new Leaf();
        lf = lf.getLeaf(typeCode);
        if (lf == null) {
            return new Result<>(false, "流程类型:" + typeCode + "不存在");
        }
        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());
        if (!fd.isLoaded()) {
            return new Result<>(false, "表单不存在");
        }
        return new Result<>(fd.getFields());
    }

    @ApiOperation(value = "取表单中的嵌套表格的字段", notes = "取表单中的嵌套表格的字段", httpMethod = "POST")
    @ApiImplicitParam(name = "typeCode", value = "流程类型", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/getNestFields", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> getNestFields(@RequestParam(value = "typeCode", required = false) String typeCode) {
        Leaf lf = new Leaf();
        lf = lf.getLeaf(typeCode);
        if (lf == null) {
            return new Result<>(false, "流程类型:" + typeCode + "不存在");
        }
        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());
        if (!fd.isLoaded()) {
            return new Result<>(false, "表单不存在");
        }
        JSONArray arr = new JSONArray();

        // 取嵌套表格字段
        MacroCtlMgr mm = new MacroCtlMgr();
        Iterator<FormField> ir = fd.getFields().iterator();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu.getNestType() != MacroCtlUnit.NEST_TYPE_NONE) {
                    String nestFormCode = ff.getDescription();
                    try {
                        String defaultVal = StrUtil.decodeJSON(nestFormCode);
                        org.json.JSONObject json = new org.json.JSONObject(defaultVal);
                        nestFormCode = json.getString("destForm");
                    } catch (JSONException e) {
                        LogUtil.getLog(getClass()).info(fd.getName() + ": " + ff.getName() + " " + nestFormCode + " is old version before 20131123. ff.getDefaultValueRaw()=" + ff.getDefaultValueRaw());
                    }

                    FormDb fdNest = new FormDb();
                    fdNest = fdNest.getFormDb(nestFormCode);
                    JSONArray arrNest = (JSONArray)JSON.toJSON(fdNest.getFields());
                    arr.addAll(arrNest);
                }
            }
        }

        return new Result<>(arr);
    }

    @ApiOperation(value = "取表单及嵌套表中的字段", notes = "取表单及嵌套表中的字段", httpMethod = "POST")
    @ApiImplicitParam(name = "typeCode", value = "流程类型", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/getFieldsWithNest", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> getFieldsWithNest(@RequestParam(value = "typeCode") String typeCode) {
        Leaf lf = new Leaf();
        lf = lf.getLeaf(typeCode);
        if (lf == null) {
            return new Result<>(false, "流程类型:" + typeCode + "不存在");
        }
        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());
        if (!fd.isLoaded()) {
            return new Result<>(false, "表单不存在");
        }
        Vector<FormField> v = fd.getFields();
        JSONArray arr = (JSONArray)JSON.toJSON(v);

        // 去除其中的嵌套表格字段
        MacroCtlMgr mm = new MacroCtlMgr();
        Iterator<FormField> ir = v.iterator();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                if (mu.getNestType() != MacroCtlUnit.NEST_TYPE_NONE) {
                    ir.remove();

                    String nestFormCode = ff.getDescription();
                    try {
                        String defaultVal = StrUtil.decodeJSON(nestFormCode);
                        org.json.JSONObject json = new org.json.JSONObject(defaultVal);
                        nestFormCode = json.getString("destForm");
                    } catch (JSONException e) {
                        LogUtil.getLog(getClass()).info(fd.getName() + ": " + ff.getName() + " " + nestFormCode + " is old version before 20131123. ff.getDefaultValueRaw()=" + ff.getDefaultValueRaw());
                    }

                    FormDb fdNest = new FormDb();
                    fdNest = fdNest.getFormDb(nestFormCode);
                    JSONArray arrNest = (JSONArray)JSON.toJSON(fdNest.getFields());
                    for (Object o : arrNest) {
                        JSONObject jsonObject = (JSONObject)o;
                        jsonObject.put("isNest", true);
                        jsonObject.put("name", fdNest.getCode() + ":" + jsonObject.getString("name"));
                        jsonObject.put("title", fdNest.getName() + ":" + jsonObject.getString("title"));
                    }
                    arr.addAll(arrNest);
                }
            }
        }

        return new Result<>(arr);
    }

    @ApiOperation(value = "查看流程详情", notes = "查看流程详情", httpMethod = "POST")
    @ApiImplicitParam(name = "flowId", value = "流程ID", required = false, dataType = "Integer")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flowShow", method={RequestMethod.GET, RequestMethod.POST})
    public Result<com.alibaba.fastjson.JSONObject> flowShow(@RequestParam(value="flowId", required = true) Integer flowId) {
        WorkflowMgr wfm = new WorkflowMgr();
        WorkflowDb wf = wfm.getWorkflowDb(flowId);

        if (!wf.isLoaded()) {
            return new Result<>(false, i18nUtil.get("processNotExist"));
        }

        // 检查
        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());
        if (lf==null) {
            return new Result<>(false, "流程：" + wf.getTitle() + " 类型不存在！");
        }
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        com.alibaba.fastjson.JSONObject jsonResult = new com.alibaba.fastjson.JSONObject();

        jsonResult.put("formCode", lf.getFormCode());
        jsonResult.put("pageType", ConstUtil.PAGE_TYPE_FLOW_SHOW);
        jsonResult.put("flowId", flowId);
        jsonResult.put("myUserName", privilege.getUser(request));
        jsonResult.put("type", lf.getType());

        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());
        if (!fd.isLoaded()) {
            return new Result<>(false, i18nUtil.get("form") + lf.getFormCode() + i18nUtil.get("noLongerExist"));
        }

        com.redmoon.oa.flow.FormDAO fdao = new com.redmoon.oa.flow.FormDAO();
        fdao = fdao.getFormDAOByCache(flowId, fd);

        ModuleSetupDb msd = new ModuleSetupDb();
        msd = msd.getModuleSetupDb(lf.getFormCode());
        String formJs = ModuleUtil.parseScript(flowId, fdao.getId(), lf.getFormCode(), null, -1, null, null, ConstUtil.PAGE_TYPE_LIST, msd.getScript("form_js"));
        jsonResult.put("formJs", StrUtil.getNullStr(formJs));

        com.redmoon.oa.person.UserMgr um = new com.redmoon.oa.person.UserMgr();

        String myUserName = privilege.getUser(request);
        String myRealName = um.getUserDb(myUserName).getRealName();

        // 是来自于收文界面paper_received_list.jsp，置公文为已读状态
        int paperId = ParamUtil.getInt(request, "paperId", -1);
        if (paperId!=-1) {
            PaperDistributeDb pdd = new PaperDistributeDb();
            pdd = pdd.getPaperDistributeDb(paperId);

            if (pdd.getInt("is_readed")==0) {
                pdd.set("is_readed", 1);
                pdd.set("read_date", new java.util.Date());
                try {
                    pdd.save();
                } catch (ResKeyException e) {
                    LogUtil.getLog(getClass()).error(e);
                }

                // 置日程为关闭状态
                PlanDb pd = new PlanDb();
                pd = pd.getPlanDb(myUserName, PlanDb.ACTION_TYPE_PAPER_DISTRIBUTE, String.valueOf(paperId));
                if (pd!=null) {
                    pd.setClosed(true);
                    try {
                        pd.save();
                    } catch (ErrMsgException e) {
                        LogUtil.getLog(getClass()).error(e);
                    }
                }
            }
        }

        // 置嵌套表需要用到的cwsId
        request.setAttribute("cwsId", "" + flowId);
        // 置嵌套表需要用到的curOperate
        request.setAttribute("pageType", ConstUtil.PAGE_TYPE_FLOW_SHOW);
        // 置NestSheetCtl需要用到的formCode
        request.setAttribute("formCode", lf.getFormCode());

        Directory dir = new Directory();
        Leaf ft = dir.getLeaf(wf.getTypeCode());
        boolean isFree = false;
        boolean isReactive = false;
        boolean isRecall = false;
        if (ft!=null) {
            isFree = ft.getType()!=Leaf.TYPE_LIST;
            WorkflowPredefineDb wfp = new WorkflowPredefineDb();
            wfp = wfp.getPredefineFlowOfFree(wf.getTypeCode());
            isReactive = wfp.isReactive();
            isRecall = wfp.isRecall();
        }

        boolean isFlowManager = false;
        LeafPriv lp = new LeafPriv(wf.getTypeCode());
        if (privilege.isUserPrivValid(request, "admin.flow")) {
            if (lp.canUserExamine(privilege.getUser(request))) {
                isFlowManager = true;
            }
        }

        boolean canUserView = lp.canUserQuery(privilege.getUser(request));

        if (!canUserView) {
            canUserView = privilege.isUserPrivValid(request, "paper.receive");
        }

        boolean isPaperReceived = false;
        if (!canUserView) {
            // 从流程控件或知会中访问
            String visitKey = ParamUtil.get(request, "visitKey");
            if (!"".equals(visitKey)) {
                String fId = String.valueOf(flowId);
                com.redmoon.oa.sso.Config ssoconfig = new com.redmoon.oa.sso.Config();
                String desKey = ssoconfig.get("key");
                visitKey = cn.js.fan.security.ThreeDesUtil.decrypthexstr(desKey, visitKey);
                if (visitKey.equals(fId)) {
                    canUserView = true;
                    isPaperReceived = true;
                } else {
                    // 来自于模块的列表
                    int r = SecurityUtil.validateVisitKey(visitKey, fId);
                    if (r == 1) {
                        canUserView = true;
                        isPaperReceived = true;
                    }
                    else {
                        return new Result<>(false, SecurityUtil.getValidateVisitKeyErrMsg(r));
                    }
                }
            }
        }

        // 判断是否拥有查看流程过程的权限
        if (!isFlowManager && !canUserView) {
            // 判断是否参与了流程
            if (!wf.isUserAttended(privilege.getUser(request))) {
                return new Result<>(false, i18nUtil.get("pvg_invalid"));
            }
        }

        boolean isStarted = wf.isStarted();
        String op = ParamUtil.get(request, "op");
        String prompt = LocalUtil.LoadString(request,"res.flow.Flow","prompt");
        com.redmoon.oa.Config cfg = com.redmoon.oa.Config.getInstance();
        String canUserModifyFlow = cfg.get("canUserModifyFlow");
        String mode = "user";
        if ("true".equals(canUserModifyFlow)) {
            mode = "user";
        } else {
            mode = "view";
        }

        String flowExpireUnit = cfg.get("flowExpireUnit");
        boolean isHour = !"day".equals(flowExpireUnit);
        if ("day".equals(flowExpireUnit)){
            flowExpireUnit = LocalUtil.LoadString(request,"res.flow.Flow","day");
        }
        else{
            flowExpireUnit = LocalUtil.LoadString(request,"res.flow.Flow","hour");
        }

        boolean isNav = ParamUtil.getBoolean(request, "isNav", true);
        jsonResult.put("isNav", isNav);
        jsonResult.put("isHasAttachment", fd.isHasAttachment());

        // 如果是自由流程且是发起人
        boolean isFreeAndStarter = false;
        if (ft.getType()==Leaf.TYPE_FREE && myUserName.equals(wf.getUserName())) {
            isFreeAndStarter = true;
            WorkflowPredefineDb wfp = new WorkflowPredefineDb();
            wfp = wfp.getPredefineFlowOfFree(wf.getTypeCode());

            boolean isRoleMemberOfFlow = false;
            String[][] rolePrivs = wfp.getRolePrivsOfFree();
            int privLen = rolePrivs.length;
            for (String[] rolePriv : rolePrivs) {
                if (rolePriv[0].equals(RoleDb.CODE_MEMBER)) {
                    isRoleMemberOfFlow = true;
                    break;
                }
            }
            jsonResult.put("isRoleMemberOfFlow", isRoleMemberOfFlow);
        }

        jsonResult.put("isUseSMS", com.redmoon.oa.sms.SMSFactory.isUseSMS());

        /*Leaf lfParent = new Leaf();
        lfParent = lfParent.getLeaf(lf.getParentCode());*/

        int formViewId = ParamUtil.getInt(request, "formViewId", -1);
        FormViewDb formViewDb = new FormViewDb();
        Vector vView = formViewDb.getViews(lf.getFormCode());
        boolean hasView = false;
        if (vView.size()>0) {
            hasView = true;
            com.alibaba.fastjson.JSONArray aryView = new com.alibaba.fastjson.JSONArray();
            Iterator irView = vView.iterator();
            while (irView.hasNext()) {
                formViewDb = (FormViewDb)irView.next();
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("id", formViewDb.getLong("id"));
                json.put("name", formViewDb.getString("name"));
                aryView.add(json);
            }
            jsonResult.put("aryView", aryView);
        }
        jsonResult.put("hasView", hasView);

        WorkflowPredefineDb wpd = new WorkflowPredefineDb();
        wpd = wpd.getPredefineFlowOfFree(wf.getTypeCode());

        jsonResult.put("levelImg", WorkflowMgr.getLevelImg(request, wf));
        String flowTitle;
        if (wpd.isLight()) {
            flowTitle = MyActionMgr.renderTitle(request, wf);
        } else {
            flowTitle = wf.getTitle();
        }
        jsonResult.put("flowTitle", flowTitle);

        boolean hasQuery = false;
        if (lf.getQueryId()!=Leaf.QUERY_NONE) {
            // 判断权限，管理员能看见查询，其它人员根据角色进行判断
            String[] roles = StrUtil.split(lf.getQueryRole(), ",");
            boolean canSeeQuery = false;
            if (!privilege.isUserPrivValid(request, "admin")) {
                if (roles != null) {
                    UserDb user = new UserDb();
                    user = user.getUserDb(privilege.getUser(request));
                    for (String role : roles) {
                        if (user.isUserOfRole(role)) {
                            canSeeQuery = true;
                            break;
                        }
                    }
                } else {
                    canSeeQuery = true;
                }
            } else {
                canSeeQuery = true;
            }
            FormQueryDb aqd = new FormQueryDb();
            aqd = aqd.getFormQueryDb((int) lf.getQueryId());
            if (canSeeQuery && aqd.isLoaded()) {
                hasQuery = true;

                String colratio = "";
                String colP = aqd.getColProps();
                if (colP == null || "".equals(colP)) {
                    colP = "[]";
                }
                int tableWidth = 0;
                com.alibaba.fastjson.JSONArray jsonArray = com.alibaba.fastjson.JSONArray.parseArray(colP);
                for (int i=0; i<jsonArray.size(); i++) {
                    com.alibaba.fastjson.JSONObject json = jsonArray.getJSONObject(i);
                    if (json.getBoolean("hide")) {
                        continue;
                    }
                    String name = (String)json.get("name");
                    if ("cws_op".equalsIgnoreCase(name)) {
                        continue;
                    }
                    tableWidth += json.getIntValue("width");
                    if ("".equals(colratio)) {
                        colratio = "" + json.get("width");
                    } else {
                        colratio += "," + json.get("width");
                    }
                }

                String queryAjaxUrl;
                if (aqd.isScript()) {
                    queryAjaxUrl = "flow/form_query_list_script_embed_ajax.jsp";
                }
                else {
                    queryAjaxUrl = "flow/form_query_list_embed_ajax.jsp";
                }

                jsonResult.put("colRatio", colratio);
                jsonResult.put("queryName", aqd.getQueryName());
                jsonResult.put("queryAjaxUrl", queryAjaxUrl);
                jsonResult.put("queryId", lf.getQueryId());
                jsonResult.put("queryTableWidth", tableWidth + 2);

                com.alibaba.fastjson.JSONArray aryCond = new com.alibaba.fastjson.JSONArray();
                com.alibaba.fastjson.JSONObject condMap = com.alibaba.fastjson.JSONObject.parseObject(lf.getQueryCondMap());
                Set keys = condMap.keySet();
                for (Object o : keys) {
                    String qField = (String)o;
                    com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                    json.put("fieldName", qField);
                    json.put("fieldValue", fdao.getFieldValue(qField));
                    aryCond.add(json);
                }
                jsonResult.put("aryCond", aryCond);
            }
        }
        jsonResult.put("hasQuery", hasQuery);
        jsonResult.put("flowTypeCode", lf.getCode());

        int doc_id = wf.getDocId();
        DocumentMgr dm = new DocumentMgr();
        Document doc = dm.getDocument(doc_id);

        String starter = um.getUserDb(wf.getUserName()).getRealName();
        String flowStatusDesc = wf.getStatusDesc();
        jsonResult.put("starter", starter);
        jsonResult.put("flowStatus", wf.getStatus());
        jsonResult.put("flowStatusDesc", flowStatusDesc);

        boolean isShowDelInfo = false;
        if (isFlowManager &&  wf.getStatus()==WorkflowDb.STATUS_DELETED) {
            if (!"".equals(StrUtil.getNullStr(wf.getDelUser()))) {
                isShowDelInfo = true;
                jsonResult.put("delByUser", um.getUserDb(wf.getDelUser()).getRealName());
                jsonResult.put("delTime", DateUtil.format(wf.getDelDate(), "yyyy-MM-dd HH:mm:ss"));
            }
        }
        jsonResult.put("isShowDelInfo", isShowDelInfo);

        String content;
        Render rd = new Render(request, wf, doc);
        if (formViewId!=-1) {
            content = rd.reportForView(formViewId, true);
        }
        else {
            content = rd.report();
        }
        jsonResult.put("formViewId", formViewId);
        jsonResult.put("content", content);
        jsonResult.put("fdaoId", fdao.getId());

        java.util.Vector<IAttachment> attachments = null;
        if (doc!=null) {
            attachments = doc.getAttachments(1);
        }
        WorkflowAnnexAttachment wfaa = new WorkflowAnnexAttachment();
        java.util.Vector<WorkflowAnnexAttachment> annexAtt = wfaa.getAllAttachments(flowId);
        boolean isAttachmentShow = false;
        com.alibaba.fastjson.JSONArray aryAtt = new com.alibaba.fastjson.JSONArray();
        if ((attachments!=null && attachments.size()>0) || (annexAtt != null && annexAtt.size() > 0)) {
            isAttachmentShow = true;

            String creatorRealName = "";
            for (IAttachment am : attachments) {
                if (am.getFieldName() != null && !"".equals(am.getFieldName())) {
                    // IOS上传fieldName为upload
                    if (!am.getFieldName().startsWith("att") && !am.getFieldName().startsWith("upload")) {
                        // 不再跳过，因为ntko office在线编辑宏控件对应的为表单域的编码
                        // continue;
                    }
                }

                UserDb creator = um.getUserDb(am.getCreator());
                if (creator.isLoaded()) {
                    creatorRealName = creator.getRealName();
                }

                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("id", am.getId());
                json.put("name", am.getName());
                json.put("creatorRealName", creatorRealName);
                json.put("createDate", am.getCreateDate());
                json.put("size", NumberUtil.round((double) am.getSize() / 1024000, 2));
                boolean isToPdf = false;
                if ("true".equals(cfg.get("canConvertToPDF")) && ("doc".equals(StrUtil.getFileExt(am.getName())) || "docx".equals(StrUtil.getFileExt(am.getName())))) {
                    isToPdf = true;
                }
                json.put("isToPdf", isToPdf);

                boolean isPreview = false;
                if (cfg.getBooleanProperty("canPdfFilePreview") || cfg.getBooleanProperty("canOfficeFilePreview")) {
                    String s = Global.getRealPath() + am.getVisualPath() + "/" + am.getDiskName();
                    String htmlfile = s.substring(0, s.lastIndexOf(".")) + ".html";
                    java.io.File fileExist = new java.io.File(htmlfile);
                    if (fileExist.exists()) {
                        isPreview = true;
                    }
                }
                json.put("isPreview", isPreview);
                json.put("previewUrl", sysUtil.getRootPath() + "/" + am.getVisualPath() + "/" + am.getDiskName().substring(0, am.getDiskName().lastIndexOf(".")) + ".html");

                aryAtt.add(json);
            }
        }
        jsonResult.put("aryAtt", aryAtt);
        boolean canDelAtt = false;
        if (privilege.isUserPrivValid(request, "admin") || isFlowManager) {
            canDelAtt = true;
        }
        jsonResult.put("canDelAtt", canDelAtt);
        jsonResult.put("isAttachmentShow", isAttachmentShow);
        jsonResult.put("docId", doc_id);

        com.alibaba.fastjson.JSONArray aryAnnexAtt = new com.alibaba.fastjson.JSONArray();
        for (WorkflowAnnexAttachment wfaatt : annexAtt) {
            long annexId = wfaatt.getAnnexId();
            WorkflowAnnexDb wfadb = new WorkflowAnnexDb();
            wfadb = (WorkflowAnnexDb) wfadb.getQObjectDb(annexId);
            String annexUser = wfadb.getString("user_name");
            UserDb annexUserDb = new UserDb(annexUser);

            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("url", wfaatt.getAttachmentUrl(request));
            json.put("name", wfaatt.getName());
            json.put("realName", annexUserDb.getRealName());
            json.put("addDate", wfadb.getDate("add_date"));
            json.put("size", NumberUtil.round((double) wfaatt.getSize() / 1024000, 2));

            aryAnnexAtt.add(json);
        }
        jsonResult.put("aryAnnexAtt", aryAnnexAtt);

        MyActionDb mad = new MyActionDb();
        jsonResult.put("flowPerformanceDisplay", cfg.getBooleanProperty("flowPerformanceDisplay"));
        jsonResult.put("isReactive", wpd.isReactive());
        jsonResult.put("flowIsRemarkShow", cfg.getBooleanProperty("flowIsRemarkShow"));
        jsonResult.put("flowExpireUnit", flowExpireUnit);

        jsonResult.put("CHECK_STATUS_NOT", MyActionDb.CHECK_STATUS_NOT);
        jsonResult.put("CHECK_STATUS_CHECKED", MyActionDb.CHECK_STATUS_CHECKED);
        jsonResult.put("CHECK_STATUS_WAITING_TO_DO", MyActionDb.CHECK_STATUS_WAITING_TO_DO);
        jsonResult.put("CHECK_STATUS_PASS", MyActionDb.CHECK_STATUS_PASS);
        jsonResult.put("CHECK_STATUS_PASS_BY_RETURN", MyActionDb.CHECK_STATUS_PASS_BY_RETURN);
        jsonResult.put("CHECK_STATUS_SUSPEND", MyActionDb.CHECK_STATUS_SUSPEND);
        jsonResult.put("CHECK_STATUS_RETURN", MyActionDb.CHECK_STATUS_RETURN);

        com.alibaba.fastjson.JSONArray aryMyAction = workflowService.listProcessForShow(wf, myUserName, isPaperReceived, isRecall, isFlowManager, isReactive);
        jsonResult.put("aryMyAction", aryMyAction);

        boolean isLight = false;
        if (isFree) {
            if ((myUserName.equals(mad.getUserName()) || myUserName.equals(mad.getProxyUserName())) && !mad.isChecked()) {
                if (wpd.isLight()) {
                    isLight = true;
                }
            }
        }
        jsonResult.put("isFree", isFree);
        jsonResult.put("isLight", isLight);

        boolean isDelayed = false;
        WorkflowActionDb actionDelayed = new WorkflowActionDb();
        Vector<WorkflowActionDb> vdelayed = actionDelayed.getActionsDelayedOfFlow(flowId);
        Iterator<WorkflowActionDb> irdelayed = vdelayed.iterator();
        if (vdelayed.size()>0) {
            isDelayed = true;
            com.alibaba.fastjson.JSONArray aryDelayed = new com.alibaba.fastjson.JSONArray();
            while (irdelayed.hasNext()) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                actionDelayed = irdelayed.next();
                json.put("title", actionDelayed.getTitle());
                json.put("jobName", actionDelayed.getJobName());
                json.put("dateDelayed", DateUtil.format(actionDelayed.getDateDelayed(), "yyyy-MM-dd HH:mm"));
                aryDelayed.add(json);
            }
            jsonResult.put("aryDelayed", aryDelayed);
        }
        jsonResult.put("isDelayed", isDelayed);
        jsonResult.put("flowRemark", wf.getRemark());

        jsonResult.put("flowIsBtnExportWordShow", cfg.getBooleanProperty("flowIsBtnExportWordShow"));

        jsonResult.put("isFlowStarted", wf.isStarted());
        jsonResult.put("isReply", wpd.isReply());
        jsonResult.put("isProgress", fd.isProgress());

        com.alibaba.fastjson.JSONArray aryAnnex = new com.alibaba.fastjson.JSONArray();
        WorkflowAnnexDb wad = new WorkflowAnnexDb();
        Vector<WorkflowAnnexDb> vec1 = wad.listRoot(wf.getId(), myUserName);
        for (WorkflowAnnexDb workflowAnnexDb : vec1) {
            wad = workflowAnnexDb;

            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            json.put("realName", um.getUserDb(wad.getString("user_name")).getRealName());
            json.put("process", wad.getInt("progress"));
            json.put("content", wad.getString("content"));
            json.put("id", wad.getLong("id"));
            json.put("addDate", wad.getDate("add_date"));
            json.put("flowId", wad.getString("flow_id"));
            json.put("actionId", wad.getString("action_id"));
            json.put("userName", wad.getString("user_name"));
            aryAnnex.add(json);

            WorkflowAnnexDb wad2 = new WorkflowAnnexDb();
            com.alibaba.fastjson.JSONArray aryAnnexSub = new com.alibaba.fastjson.JSONArray();
            Vector<WorkflowAnnexDb> vec2 = wad2.listChildren(wad.getInt("id"), myUserName);
            for (WorkflowAnnexDb annexDb : vec2) {
                wad2 = annexDb;
                int id2 = (int) wad2.getLong("id");

                com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
                jsonObject.put("id", id2);
                jsonObject.put("userName", wad2.getString("user_name"));
                jsonObject.put("realName", um.getUserDb(wad2.getString("user_name")).getRealName());
                jsonObject.put("content", wad2.getString("content"));
                jsonObject.put("addDate", wad2.getDate("add_date"));
                jsonObject.put("flowId", wad2.getString("flow_id"));
                jsonObject.put("actionId", wad2.getString("action_id"));
                jsonObject.put("replyRealName", um.getUserDb(wad2.getString("reply_name")).getRealName());
                aryAnnexSub.add(jsonObject);
            }
            json.put("aryAnnexSub", aryAnnexSub);
        }
        jsonResult.put("aryAnnex", aryAnnex);

        boolean isPageStyleLight = false;
        if (msd.getPageStyle()==ConstUtil.PAGE_STYLE_LIGHT) {
            isPageStyleLight = true;
        }
        jsonResult.put("isPageStyleLight", isPageStyleLight);

        WorkflowRuler wrTop = new WorkflowRuler();
        WorkflowMgr wfmTop = new WorkflowMgr();
        WorkflowDb wfTop = wfmTop.getWorkflowDb(flowId);

        com.redmoon.oa.flow.Directory dirTop = new com.redmoon.oa.flow.Directory();
        Leaf ftTop = dirTop.getLeaf(wfTop.getTypeCode());

        boolean canUserSeeFlowChartTop = cfg.getBooleanProperty("canUserSeeFlowChart");
        boolean isFlowManagerTop = false;
        if (privilege.isUserPrivValid(request, "admin")) {
            isFlowManagerTop = true;
        } else {
            LeafPriv lpTop = new LeafPriv(wfTop.getTypeCode());
            if (privilege.isUserPrivValid(request, "admin.flow")) {
                if (lpTop.canUserExamine(privilege.getUser(request))) {
                    isFlowManagerTop = true;
                }
            }
        }
        String visitKeyTop = ParamUtil.get(request, "visitKey");
        jsonResult.put("canManageFlow", isFlowManagerTop);
        jsonResult.put("visitKey", visitKeyTop);

        boolean isFlowChartShow = false;
        if (canUserSeeFlowChartTop || isFlowManagerTop) {
            isFlowChartShow = true;
        }
        jsonResult.put("isFlowChartShow", isFlowChartShow);

        boolean canModifyTitle = false;
        if (privilege.isUserPrivValid(request, "admin") || isFlowManagerTop || wrTop.canUserModifyFlow(request, wfTop)) {
            canModifyTitle = true;
        }
        jsonResult.put("canModifyTitle", canModifyTitle);

        boolean isNotifyTabShow = false;
        PaperDistributeDb pddTop = new PaperDistributeDb();
        int paperCountTop = pddTop.getCountOfWorkflow(flowId);
        if (paperCountTop > 0) {
            isNotifyTabShow = true;
            String notifyTabName = i18nUtil.get("notify"); // "流程抄送";
            String kindTop = com.redmoon.oa.kernel.License.getInstance().getKind();
            if (kindTop.equalsIgnoreCase(com.redmoon.oa.kernel.License.KIND_COM)) {
                notifyTabName = "流程知会";
            }
            jsonResult.put("notifyTabName", notifyTabName);
        }
        jsonResult.put("isNotifyTabShow", isNotifyTabShow);

        // 如果配置了自动存档目录
        boolean isArchiveTabShow = false;
        WorkflowPredefineDb wpdTop = new WorkflowPredefineDb();
        wpdTop = wpdTop.getDefaultPredefineFlow(wfTop.getTypeCode());
        if (!"".equals(wpdTop.getDirCode())) {
            isArchiveTabShow = true;
        }
        jsonResult.put("isArchiveTabShow", isArchiveTabShow);

        boolean isDiscardBtnShow = false;
        // 如果是发起人，且开始节点设置了可以放弃
        if (wf.getUserName().equals(authUtil.getUserName()) && (wf.getStatus() == WorkflowDb.STATUS_NOT_STARTED || wf.getStatus() == WorkflowDb.STATUS_STARTED)) {
            MyActionDb firstMad = mad.getFirstMyActionDbOfFlow(flowId);
            WorkflowActionDb wa = new WorkflowActionDb();
            wa = wa.getWorkflowActionDb((int)firstMad.getActionId());
            com.redmoon.oa.flow.FlowConfig conf = new com.redmoon.oa.flow.FlowConfig();
            if (conf.getIsDisplay("FLOW_BUTTON_DISCARD")) {
                if (wa.canDiscard()) {
                    isDiscardBtnShow = true;
                }
            }
        }
        jsonResult.put("isDiscardBtnShow", isDiscardBtnShow);

        return new Result<>(jsonResult);
    }

    @ApiOperation(value = "获取显示规则脚本", notes = "获取显示规则脚本", httpMethod = "POST")
    @ApiImplicitParam(name = "flowId", value = "流程ID", required = false, dataType = "Integer")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flowShowScript", method={RequestMethod.GET, RequestMethod.POST})
    public Result<com.alibaba.fastjson.JSONObject> flowShowScript(@RequestParam(value="flowId", required = true) Integer flowId) {
        WorkflowMgr wfm = new WorkflowMgr();
        WorkflowDb wf = wfm.getWorkflowDb(flowId);

        if (!wf.isLoaded()) {
            return new Result<>(false, i18nUtil.get("processNotExist"));
        }

        request.setAttribute("pageType", ConstUtil.PAGE_TYPE_FLOW_SHOW);

        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());

        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());

        FormDAO fdao = new FormDAO();
        fdao = fdao.getFormDAOByCache(flowId, fd);
        JSONObject json = new JSONObject();
        json.put("script", flowRender.reportScript(fd, fdao));
        return new Result<>(json);
    }

    @ApiOperation(value = "查看流程详情", notes = "查看流程详情", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "flowId", value = "流程ID", required = false, dataType = "Integer"),
            @ApiImplicitParam(name = "viewId", value = "视图ID", required = false, dataType = "Integer"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flowView", method={RequestMethod.GET, RequestMethod.POST})
    public Result<com.alibaba.fastjson.JSONObject> flowView(@RequestParam(value="flowId", required = true) Integer flowId, @RequestParam(value="viewId", required = true)Integer viewId) {
        WorkflowMgr wfm = new WorkflowMgr();
        WorkflowDb wf = wfm.getWorkflowDb(flowId);

        if (!wf.isLoaded()) {
            return new Result<>(false, i18nUtil.get("processNotExist"));
        }

        // 检查
        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());
        if (lf==null) {
            return new Result<>(false, "流程：" + wf.getTitle() + " 类型不存在！");
        }
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        com.alibaba.fastjson.JSONObject jsonResult = new com.alibaba.fastjson.JSONObject();

        jsonResult.put("flowId", flowId);
        jsonResult.put("myUserName", privilege.getUser(request));

        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());
        if (!fd.isLoaded()) {
            return new Result<>(false, i18nUtil.get("form") + lf.getFormCode() + i18nUtil.get("noLongerExist"));
        }

        boolean isPageStyleLight = false;
        ModuleSetupDb msd = new ModuleSetupDb();
        msd = msd.getModuleSetupDb(lf.getFormCode());
        if (msd.getPageStyle()==ConstUtil.PAGE_STYLE_LIGHT) {
            isPageStyleLight = true;
        }
        jsonResult.put("isPageStyleLight", isPageStyleLight);

        com.redmoon.oa.flow.FormDAO fdao = new com.redmoon.oa.flow.FormDAO();
        fdao = fdao.getFormDAOByCache(flowId, fd);

        String myUserName = privilege.getUser(request);
        Directory dir = new Directory();
        Leaf ft = dir.getLeaf(wf.getTypeCode());
        boolean isFree = false;
        boolean isReactive = false;
        boolean isRecall = false;
        if (ft!=null) {
            isFree = ft.getType()!=Leaf.TYPE_LIST;
            WorkflowPredefineDb wfp = new WorkflowPredefineDb();
            wfp = wfp.getPredefineFlowOfFree(wf.getTypeCode());
            isReactive = wfp.isReactive();
            isRecall = wfp.isRecall();
        }

        boolean isFlowManager = false;
        LeafPriv lp = new LeafPriv(wf.getTypeCode());
        if (privilege.isUserPrivValid(request, "admin.flow")) {
            if (lp.canUserExamine(privilege.getUser(request))) {
                isFlowManager = true;
            }
        }

        boolean canUserView = lp.canUserQuery(privilege.getUser(request));

        if (!canUserView) {
            canUserView = privilege.isUserPrivValid(request, "paper.receive");
        }

        boolean isPaperReceived = false;
        if (!canUserView) {
            // 从流程控件或知会中访问
            String visitKey = ParamUtil.get(request, "visitKey");
            if (!"".equals(visitKey)) {
                String fId = String.valueOf(flowId);
                com.redmoon.oa.sso.Config ssoconfig = new com.redmoon.oa.sso.Config();
                String desKey = ssoconfig.get("key");
                visitKey = cn.js.fan.security.ThreeDesUtil.decrypthexstr(desKey, visitKey);
                if (visitKey.equals(fId)) {
                    canUserView = true;
                    isPaperReceived = true;
                }
            }
        }

        // 判断是否拥有查看流程过程的权限
        if (!isFlowManager && !canUserView) {
            // 判断是否参与了流程
            if (!wf.isUserAttended(privilege.getUser(request))) {
                return new Result<>(false, i18nUtil.get("pvg_invalid"));
            }
        }

        WorkflowPredefineDb wpd = new WorkflowPredefineDb();
        wpd = wpd.getPredefineFlowOfFree(wf.getTypeCode());

        jsonResult.put("levelImg", WorkflowMgr.getLevelImg(request, wf));
        String flowTitle;
        if (wpd.isLight()) {
            flowTitle = MyActionMgr.renderTitle(request, wf);
        } else {
            flowTitle = wf.getTitle();
        }
        jsonResult.put("flowTitle", flowTitle);

        int doc_id = wf.getDocId();
        DocumentMgr dm = new DocumentMgr();
        Document doc = dm.getDocument(doc_id);

        Render rd = new Render(request, wf, doc);
        String content = rd.reportForView(viewId, true);

        jsonResult.put("content", content);
        jsonResult.put("fdaoId", fdao.getId());
        com.redmoon.oa.Config cfg = com.redmoon.oa.Config.getInstance();
        jsonResult.put("flowIsRemarkShow", cfg.getBooleanProperty("flowIsRemarkShow"));

        com.alibaba.fastjson.JSONArray aryMyAction = workflowService.listProcessForShow(wf, myUserName, isPaperReceived, isRecall, isFlowManager, isReactive);
        jsonResult.put("aryMyAction", aryMyAction);
        return new Result<>(jsonResult);
    }

    @ApiOperation(value = "修改流程标题", notes = "修改流程标题", httpMethod = "POST")
    @ApiImplicitParam(name = "flowId", value = "流程ID", required = false, dataType = "Integer")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flowModifyTitle", method={RequestMethod.GET})
    public Result<Object> flowModifyTitle(@RequestParam(value="flowId", required = true) Integer flowId) {
        WorkflowMgr wfm = new WorkflowMgr();
        WorkflowDb wf = wfm.getWorkflowDb(flowId);

        boolean isFlowManager = false;
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        LeafPriv lp = new LeafPriv(wf.getTypeCode());
        if (privilege.isUserPrivValid(request, "admin.flow")) {
            if (lp.canUserExamine(privilege.getUser(request))) {
                isFlowManager = true;
            }
        }

        boolean canModifyTitle = false;
        WorkflowRuler wr = new WorkflowRuler();
        if (privilege.isUserPrivValid(request, "admin") || isFlowManager || wr.canUserModifyFlow(request, wf)) {
            canModifyTitle = true;
        }
        else {
            return new Result<>(false, i18nUtil.get("pvg_invalid"));
        }

        com.alibaba.fastjson.JSONObject jsonResult = new com.alibaba.fastjson.JSONObject();
        jsonResult.put("canModifyTitle", canModifyTitle);

        jsonResult.put("skinPath", SkinMgr.getSkinPath(request, false));
        jsonResult.put("myUserName", privilege.getUser(request));
        jsonResult.put("canManageFlow", privilege.isUserPrivValid(request, "admin") || isFlowManager);

        String visitKeyTop = ParamUtil.get(request, "visitKey");
        jsonResult.put("visitKey", visitKeyTop);

        boolean isFlowChartShow = false;
        boolean isFree = false;
        com.redmoon.oa.flow.Directory dirTop = new com.redmoon.oa.flow.Directory();
        Leaf lf = dirTop.getLeaf(wf.getTypeCode());
        isFree = lf.getType() != Leaf.TYPE_LIST;

        Config cfg = Config.getInstance();
        boolean canUserSeeFlowChart = cfg.getBooleanProperty("canUserSeeFlowChart");
        boolean canUserSeeFlowImage = cfg.getBooleanProperty("canUserSeeFlowImage");
        boolean canUserSeeDesignerWhenDispose = cfg.getBooleanProperty("canUserSeeDesignerWhenDispose");
        if (!isFree && (canUserSeeFlowChart || canUserSeeFlowImage || canUserSeeDesignerWhenDispose || isFlowManager)) {
            isFlowChartShow = true;
        }
        jsonResult.put("isFlowChartShow", isFlowChartShow);

        jsonResult.put("flowTypeName", lf.getName());
        jsonResult.put("flowTypeCode", lf.getCode());

        jsonResult.put("isFlowLevelDisplay", cfg.getBooleanProperty("isFlowLevelDisplay"));
        jsonResult.put("level", wf.getLevel());
        jsonResult.put("LEVEL_NORMAL", WorkflowDb.LEVEL_NORMAL);
        jsonResult.put("LEVEL_IMPORTANT", WorkflowDb.LEVEL_IMPORTANT);
        jsonResult.put("LEVEL_URGENT", WorkflowDb.LEVEL_URGENT);

        jsonResult.put("flowId", wf.getId());
        jsonResult.put("flowTitle", wf.getTitle());
        return new Result<>(jsonResult);
    }

    @ApiOperation(value = "查看流程图", notes = "查看流程图", httpMethod = "POST")
    @ApiImplicitParam(name = "flowId", value = "流程ID", required = false, dataType = "Integer")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flowShowChart", method={RequestMethod.GET})
    public Result<com.alibaba.fastjson.JSONObject> flowShowChart(@RequestParam(value = "flowId") int flowId) {
        WorkflowMgr wfm = new WorkflowMgr();
        WorkflowDb wf = wfm.getWorkflowDb(flowId);
        com.redmoon.oa.Config cfg = Config.getInstance();
        String flowExpireUnit = cfg.get("flowExpireUnit");
        boolean isHour = !"day".equals(flowExpireUnit);
        if ("day".equals(flowExpireUnit)) {
            flowExpireUnit = LocalUtil.LoadString(request, "res.flow.Flow", "day");
        } else {
            flowExpireUnit = LocalUtil.LoadString(request, "res.flow.Flow", "hour");
        }

        com.alibaba.fastjson.JSONObject jsonResult = new com.alibaba.fastjson.JSONObject();
        jsonResult.put("flowExpireUnit", flowExpireUnit);
        jsonResult.put("licenseKey", License.getInstance().getKey());

        boolean isFlowManager = false;
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        LeafPriv lp = new LeafPriv(wf.getTypeCode());
        if (privilege.isUserPrivValid(request, "admin.flow")) {
            if (lp.canUserExamine(privilege.getUser(request))) {
                isFlowManager = true;
            }
        }

        boolean isFlowDebug = ParamUtil.getBoolean(request, "isFlowDebug", false);
        boolean canUserSeeFlowChart = cfg.getBooleanProperty("canUserSeeFlowChart");

        jsonResult.put("canUserSeeFlowChart", canUserSeeFlowChart || isFlowManager || isFlowDebug);
        if (canUserSeeFlowChart || isFlowManager || isFlowDebug) {
            String flowJson = "";
            if (StringUtils.isEmpty(wf.getFlowJson())) {
                IMyflowUtil myflowUtil = SpringUtil.getBean(IMyflowUtil.class);
                try {
                    flowJson = myflowUtil.toMyflow(wf.getFlowString());
                } catch (ErrMsgException e) {
                    LogUtil.getLog(getClass()).error(e);
                }
            } else {
                flowJson = wf.getFlowJson();
            }
            jsonResult.put("flowJson", flowJson);
        }

        jsonResult.put("flowId", flowId);
        jsonResult.put("myUserName", privilege.getUser(request));
        jsonResult.put("canManageFlow", privilege.isUserPrivValid(request, "admin") || isFlowManager);

        String visitKeyTop = ParamUtil.get(request, "visitKey");
        jsonResult.put("visitKey", visitKeyTop);
        boolean isFlowChartShow = false;
        com.redmoon.oa.flow.Directory dirTop = new com.redmoon.oa.flow.Directory();
        Leaf ftTop = dirTop.getLeaf(wf.getTypeCode());
        boolean isFreeTop = false;
        if (ftTop != null) {
            isFreeTop = ftTop.getType() != Leaf.TYPE_LIST;
        }
        boolean canUserSeeFlowChartTop = cfg.getBooleanProperty("canUserSeeFlowChart");
        if (canUserSeeFlowChartTop || isFlowManager) {
            isFlowChartShow = true;
        }
        jsonResult.put("isFlowChartShow", isFlowChartShow);
        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());
        jsonResult.put("isDebug", lf.isDebug());

        // 取出激活节点和已办节点
        com.alibaba.fastjson.JSONArray activeActions = new com.alibaba.fastjson.JSONArray();
        com.alibaba.fastjson.JSONArray finishActions = new com.alibaba.fastjson.JSONArray();
        com.alibaba.fastjson.JSONArray ignoreActions = new com.alibaba.fastjson.JSONArray();
        com.alibaba.fastjson.JSONArray discardActions = new com.alibaba.fastjson.JSONArray();
        com.alibaba.fastjson.JSONArray returnActions = new com.alibaba.fastjson.JSONArray();

        for (WorkflowActionDb wa : wf.getActions()) {
            if (wa.getStatus() == WorkflowActionDb.STATE_DOING) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("ID", wa.getInternalName());
                activeActions.add(json);
            } else if (wa.getStatus() == WorkflowActionDb.STATE_FINISHED) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("ID", wa.getInternalName());
                finishActions.add(json);
            } else if (wa.getStatus() == WorkflowActionDb.STATE_IGNORED) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("ID", wa.getInternalName());
                ignoreActions.add(json);
            } else if (wa.getStatus() == WorkflowActionDb.STATE_DISCARDED) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("ID", wa.getInternalName());
                discardActions.add(json);
            } else if (wa.getStatus() == WorkflowActionDb.STATE_RETURN) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("ID", wa.getInternalName());
                returnActions.add(json);
            }
        }

        jsonResult.put("activeActions", activeActions);
        jsonResult.put("finishActions", finishActions);
        jsonResult.put("ignoreActions", ignoreActions);
        jsonResult.put("discardActions", discardActions);
        jsonResult.put("returnActions", returnActions);

        jsonResult.put("STATE_FINISHED", WorkflowActionDb.STATE_FINISHED);
        jsonResult.put("STATE_DOING", WorkflowActionDb.STATE_DOING);
        jsonResult.put("STATE_IGNORED", WorkflowActionDb.STATE_IGNORED);
        jsonResult.put("STATE_RETURN", WorkflowActionDb.STATE_RETURN);
        jsonResult.put("STATE_DISCARDED", WorkflowActionDb.STATE_DISCARDED);

        com.alibaba.fastjson.JSONArray aryMyAction = new com.alibaba.fastjson.JSONArray();
        MyActionDb mad = new MyActionDb();
        WorkflowActionDb wa = new WorkflowActionDb();
        Vector<MyActionDb> v = mad.getMyActionDbOfFlow(flowId);
        for (MyActionDb myActionDb : v) {
            com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
            wa = wa.getWorkflowActionDb((int)myActionDb.getActionId());

            json.put("receiveTime", myActionDb.getReceiveDate().getTime());
            json.put("internalName", wa.getInternalName());
            json.put("actionStatus", myActionDb.getActionStatus());
            json.put("checkTime", myActionDb.getCheckDate()!=null?myActionDb.getCheckDate().getTime()+"":"999999999999999");
            json.put("checkStatus", myActionDb.isChecked()?WorkflowActionDb.STATE_FINISHED:myActionDb.getActionStatus());
            aryMyAction.add(json);
        }
        jsonResult.put("aryMyAction", aryMyAction);

        String cloudUrl = request.getContextPath();
        jsonResult.put("cloudUrl", cloudUrl);

        return new Result<>(jsonResult);
    }

    @ApiOperation(value = "更新流程标题", notes = "更新流程标题", httpMethod = "POST")
    @ApiImplicitParam(name = "flowId", value = "流程ID", required = false, dataType = "Integer")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/modifyTitle", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> modifyTitle() {
        boolean re;
        try {
            int flowId = ParamUtil.getInt(request, "flowId");
            String typeCode = ParamUtil.get(request, "typeCode");
            String title = ParamUtil.get(request, "title");
            int level = ParamUtil.getInt(request, "level", WorkflowDb.LEVEL_NORMAL);
            WorkflowMgr wm = new WorkflowMgr();
            WorkflowDb wf = wm.getWorkflowDb(flowId);
            wf.setTypeCode(typeCode);
            wf.setTitle(title);
            wf.setLevel(level);
            re = wf.save();
        }
        catch (ErrMsgException e) {
            return new Result<>(false, e.getMessage());
        }
        return new Result<>(re);
    }

    @ApiOperation(value = "取流程导出中已设置的字段", notes = "取流程导出中已设置的字段", httpMethod = "POST")
    @ApiImplicitParam(name = "typeCode", value = "流程类型", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/getExportFields", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> getExportFields(@RequestParam(value = "typeCode") String typeCode) {
        Leaf lf = new Leaf();
        lf = lf.getLeaf(typeCode);
        if (lf == null) {
            return new Result<>(false, "流程类型:" + typeCode + "不存在");
        }
        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());
        if (!fd.isLoaded()) {
            return new Result<>(false, "表单不存在");
        }

        Vector<FormField> v = fd.getFields();
        List<Object> list = new ArrayList<>();

        String exportColProps = lf.getExportColProps();
        if ("".equals(exportColProps)) {
            List<String> listName = new ArrayList<>();
            for (FormField ff : v) {
                list.add(JSON.toJSON(ff));

                listName.add(ff.getName());
            }
            lf.setExportColProps(String.join(",", listName.stream().map(String::valueOf).collect(Collectors.toList())));
            lf.update();
        } else {
            String[] ary = StrUtil.split(exportColProps, ",");
            if (ary != null) {
                for (String fieldName : ary) {
                    if (fieldName.contains(":")) {
                        String[] aryNest = fieldName.split(":");
                        FormDb fdNest = fd.getFormDb(aryNest[0]);
                        FormField ffNest = fdNest.getFormField(aryNest[1]);
                        if (ffNest == null) {
                            LogUtil.getLog(getClass()).warn("嵌套表: " + fdNest.getName() + " 中字段: " + fieldName + " 不存在");
                        } else {
                            JSONObject jsonObject = (JSONObject) JSON.toJSON(ffNest);
                            jsonObject.put("name", fdNest.getCode() + ":" + ffNest.getName());
                            jsonObject.put("title", fdNest.getName() + ":" + ffNest.getTitle());
                            list.add(jsonObject);
                        }
                    } else {
                        FormField ff = fd.getFormField(fieldName);
                        if (ff == null) {
                            LogUtil.getLog(getClass()).warn("字段: " + fieldName + " 不存在");
                        } else {
                            list.add(JSON.toJSON(ff));
                        }
                    }
                }
            }
        }
        return new Result<>(list);
    }

    @ApiOperation(value = "导出Excel", notes = "导出Excel", httpMethod = "POST")
    @ApiImplicitParams({
        @ApiImplicitParam(name = "typeCode", value = "流程类型", required = false, dataType = "String"),
        @ApiImplicitParam(name = "fields", value = "需导出的字段，以逗号分隔", required = false, dataType = "String")
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/exportExcel", method = RequestMethod.POST)
    public void exportExcel(HttpServletResponse response, @RequestParam(value="typeCode", required = true) String typeCode, @RequestParam(value = "fields", required = false) String fields) throws ErrMsgException, IOException {
        com.redmoon.oa.flow.Leaf lf = new com.redmoon.oa.flow.Leaf();
        lf = lf.getLeaf(typeCode);
        if (lf==null) {
            throw new ErrMsgException("typeCode=" + typeCode + " 流程类型：" + typeCode + " 不存在！");
        }

        if (!fields.equals(lf.getExportColProps())) {
            lf.setExportColProps(fields);
            lf.update();
        }

        /*com.redmoon.oa.sso.Config cfg = new com.redmoon.oa.sso.Config();
        String query = ParamUtil.get(request, "query");
        String sql = cn.js.fan.security.ThreeDesUtil.decrypthexstr(cfg.get("key"), query);*/

        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());

        String[] fieldsSelected = StrUtil.split(fields, ",");
        if (fieldsSelected==null) {
            fieldsSelected = new String[0];
        }

        WorkflowDb wf = new WorkflowDb();
        String sql = wf.getSqlSearch(request);
        LogUtil.getLog(getClass()).info("sql: " + sql);

        Vector<WorkflowDb> v = wf.list(sql);

        response.setContentType("application/vnd.ms-excel");
        response.setHeader("Content-disposition","attachment; filename="+StrUtil.GBToUnicode("查询结果导出.xls"));

        try (OutputStream os = response.getOutputStream()) {
            // File file = new File(Global.getAppPath(request) + "flow/blank.xls");
            // File file = new File(Global.getRealPath() + "flow/blank.xls");
            InputStream inputStream = configUtil.getFile("templ/blank.xls");

            Workbook wb = Workbook.getWorkbook(inputStream);

            WorkbookSettings settings = new WorkbookSettings();
            settings.setWriteAccess(null);

            // 打开一个文件的副本，并且指定数据写回到原文件
            WritableWorkbook wwb = Workbook.createWorkbook(os, wb, settings);
            WritableSheet ws = wwb.getSheet(0);

            Iterator<WorkflowDb> ir = v.iterator();
            UserMgr um = new UserMgr();
            FormDAO fdao = new FormDAO();

            int j = 0;
            Label t = new Label(0, j, "ID");
            ws.addCell(t);
            t = new Label(1, j, "标题");
            ws.addCell(t);
            int k = 2;

            List<String> nestFormList = new ArrayList<>();
            for (String s : fieldsSelected) {
                if (s.contains(":")) {
                    String[] ary = s.split(":");
                    nestFormList.add(ary[0]);
                    FormDb fdNest = fd.getFormDb(ary[0]);
                    FormField ff = fdNest.getFormField(ary[1]);
                    t = new Label(k, j, ff.getTitle());
                } else {
                    FormField ff = fd.getFormField(s);
                    t = new Label(k, j, ff.getTitle());
                }
                ws.addCell(t);
                k++;
            }

            t = new Label(k, j, "发起时间");
            ws.addCell(t);
            k++;
            t = new Label(k, j, "发起人");
            ws.addCell(t);
            k++;
            t = new Label(k, j, "状态");
            ws.addCell(t);
            k++;
            t = new Label(k, j, "当前办理");
            ws.addCell(t);
            k++;

            MyActionDb mad = new MyActionDb();
            j = 1;
            MacroCtlMgr mm = new MacroCtlMgr();
            while (ir.hasNext()) {
                WorkflowDb wfd = ir.next();

                fdao = fdao.getFormDAOByCache(wfd.getId(), fd);

                // 置SQL宏控件中需要用到的fdao
                RequestUtil.setFormDAO(request, fdao);

                t = new Label(0, j, String.valueOf(wfd.getId()));
                ws.addCell(t);

                t = new Label(1, j, wfd.getTitle());
                ws.addCell(t);

                // 取出嵌套表的记录
                Map<String, List<com.redmoon.oa.visual.FormDAO>> mapNest = new HashMap<>();
                for (String nestFormCode : nestFormList) {
                    mapNest.put(nestFormCode, fdao.listNest(nestFormCode));
                }

                k = 2;
                for (String s : fieldsSelected) {
                    FormField ff;
                    String str = "";
                    if (s.contains(":")) {
                        String[] ary = s.split(":");
                        List<com.redmoon.oa.visual.FormDAO> nestList = mapNest.get(ary[0]);
                        StringBuilder sb = new StringBuilder();
                        for (com.redmoon.oa.visual.FormDAO nestDao : nestList) {
                            FormField nestFf = nestDao.getFormField(ary[1]);
                            if (nestFf.getType().equals(FormField.TYPE_MACRO)) {
                                MacroCtlUnit mu = mm.getMacroCtlUnit(nestFf.getMacroType());
                                if (mu == null) {
                                    DebugUtil.e(getClass(), "exportExcel", nestFf.getTitle() + " 嵌套表 宏控件: " + nestFf.getMacroType() + " 不存在");
                                } else if (!"macro_raty".equals(mu.getCode())) {
                                    StrUtil.concat(sb, ",", StrUtil.getAbstract(request, mu.getIFormMacroCtl().converToHtml(request, nestFf, nestFf.getValue()), 1000, ""));
                                }
                            } else {
                                StrUtil.concat(sb, ",", nestFf.convertToHtml());
                            }
                        }
                        str = sb.toString();
                    } else {
                        ff = fdao.getFormField(s);
                        if (ff.getType().equals(FormField.TYPE_MACRO)) {
                            MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                            if (mu == null) {
                                DebugUtil.e(getClass(), "exportExcel", ff.getTitle() + " 宏控件: " + ff.getMacroType() + " 不存在");
                            } else if (!"macro_raty".equals(mu.getCode())) {
                                str = StrUtil.getAbstract(request, mu.getIFormMacroCtl().converToHtml(request, ff, ff.getValue()), 1000, "");
                            }
                        } else {
                            str = ff.convertToHtml();
                        }
                    }

                    t = new Label(k, j, str);
                    ws.addCell(t);
                    k++;
                }

                t = new Label(k, j, DateUtil.format(wfd.getBeginDate(), "yy-MM-dd HH:mm:ss"));
                ws.addCell(t);
                k++;

                String userName = StrUtil.getNullStr(wfd.getUserName());
                String realName = "";
                if (!"".equals(userName)) {
                    realName = um.getUserDb(userName).getRealName();
                }
                t = new Label(k, j, realName);
                ws.addCell(t);
                k++;
                t = new Label(k, j, wfd.getStatusDesc());
                ws.addCell(t);
                k++;

                String val = "";
                for (MyActionDb madCur : mad.getMyActionDbDoingOfFlow(wfd.getId())) {
                    if (!"".equals(val)) {
                        val += "、";
                    }
                    val += um.getUserDb(madCur.getUserName()).getRealName();
                }
                t = new Label(k, j, val);
                ws.addCell(t);
                k++;

                j++;
            }

            wwb.write();
            wwb.close();
            wb.close();
        } catch (Exception e) {
            LogUtil.getLog(getClass()).error(e);
        }
    }

    @ResponseBody
    @RequestMapping(value = "/flow/getTestInfo", method={RequestMethod.POST}, produces = {"application/json;charset=UTF-8;"})
    public Result<Object> getTestInfo(@RequestParam(value = "myActionId") long myActionId) {
        com.alibaba.fastjson.JSONObject jsonResult = new com.alibaba.fastjson.JSONObject();

        MyActionDb mad = new MyActionDb();
        mad = mad.getMyActionDb(myActionId);
        long actionId = mad.getActionId();
        WorkflowActionDb wad = new WorkflowActionDb();
        wad = wad.getWorkflowActionDb((int) actionId);

        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb((int) mad.getFlowId());

        com.redmoon.oa.Config cfg = new com.redmoon.oa.Config();
        String flowExpireUnit = cfg.get("flowExpireUnit");
        jsonResult.put("flowExpireUnit", flowExpireUnit);

        boolean canUserSeeFlowChart = cfg.getBooleanProperty("canUserSeeFlowChart");
        jsonResult.put("canUserSeeFlowChart", canUserSeeFlowChart);

        if (canUserSeeFlowChart) {
            jsonResult.put("flowJson", wf.getFlowJson());
        }

        com.alibaba.fastjson.JSONArray aryNextActions = new com.alibaba.fastjson.JSONArray();
        jsonResult.put("nextActions", aryNextActions);

        UserDb user = new UserDb();
        for (WorkflowActionDb nextwad : wad.getLinkToActions()) {
            com.alibaba.fastjson.JSONObject jsonAction = new com.alibaba.fastjson.JSONObject();
            jsonAction.put("actionTitle", nextwad.getTitle());
            com.alibaba.fastjson.JSONArray ary = new com.alibaba.fastjson.JSONArray();
            jsonAction.put("checkers", ary);
            Iterator<MyActionDb> irmad = mad.getActionDoing(nextwad.getId()).iterator();
            while (irmad.hasNext()) {
                MyActionDb nextmad = irmad.next();
                com.alibaba.fastjson.JSONObject checker = new com.alibaba.fastjson.JSONObject();
                checker.put("nextMyActionId", nextmad.getId());
                checker.put("userName", nextmad.getUserName());
                checker.put("realName", user.getUserDb(nextmad.getUserName()).getRealName());
                ary.add(checker);
            }
            aryNextActions.add(jsonAction);
        }

        com.alibaba.fastjson.JSONArray allActions = new com.alibaba.fastjson.JSONArray();

        for (MyActionDb nextmad : mad.getFlowDoingWithoutAction(mad.getFlowId())) {
            WorkflowActionDb nextwad = wad.getWorkflowActionDb((int) nextmad.getActionId());
            com.alibaba.fastjson.JSONObject jsonAction = new com.alibaba.fastjson.JSONObject();
            jsonAction.put("actionTitle", nextwad.getTitle());
            jsonAction.put("nextMyActionId", nextmad.getId());
            jsonAction.put("userName", nextmad.getUserName());
            jsonAction.put("realName", user.getUserDb(nextmad.getUserName()).getRealName());
            allActions.add(jsonAction);
        }
        jsonResult.put("allActions", allActions);
        return new Result<>(jsonResult);
    }

    @ApiOperation(value = "为用户生成token", notes = "为用户生成token，用于流程调试模式", httpMethod = "POST")
    @RequestMapping(value = "/flow/getTokenByUser", method= RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;"})
    @ResponseBody
    public Result<Object> getTokenByUser(@RequestParam(value = "newUserName") String newUserName) {
        Result<Object> result = new Result<>(true);
        result.setData(jwtUtil.generate(newUserName));
        return result;
    }

    @ApiOperation(value = "催办", notes = "催办", httpMethod = "POST")
    @RequestMapping(value = "/flow/remind", method= RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;"})
    @ResponseBody
    public Result<Object> remind(@RequestParam(value = "myActionId") long myActionId) throws ErrMsgException {
        MyActionDb myActionDb = new MyActionDb();
        myActionDb = myActionDb.getMyActionDb(myActionId);
        String userName = myActionDb.getUserName();
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb((int)myActionDb.getFlowId());
        String myUserName = authUtil.getUserName();
        User user = userCache.getUser(myUserName);
        String title = user.getRealName() + " 催办：" + wf.getTypeCode();
        String content = WorkflowMgr.getFormAbstractTable(wf);

        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        boolean isToMobile = SMSFactory.isUseSMS();
        if (isToMobile) {
            ProxyFactory proxyFactory = new ProxyFactory("com.redmoon.oa.message.MessageDb");
            IMessage imsg = (IMessage) proxyFactory.getProxy();
            imsg.sendSysMsg(userName, title, content);
        }
        else {
            // 发送信息
            MessageDb md = new MessageDb();
            md.sendSysMsg(userName, title, content);
        }
        return new Result<>(true);
    }

    /**
     * 抄送给我的列表
     * @return
     */
    @ApiOperation(value = "抄送给我的列表", notes = "抄送给我的列表", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "op", value = "操作类型", required = true, dataType = "String"),
            @ApiImplicitParam(name = "title", value = "标题", required = true, dataType = "String"),
            @ApiImplicitParam(name = "page", value = "页数", required = true, dataType = "Integer"),
            @ApiImplicitParam(name = "pageSize", value = "每页条数", required = true, dataType = "Integer"),
            @ApiImplicitParam(name = "fromDate", value = "开始日期", required = true, dataType = "Date"),
            @ApiImplicitParam(name = "toDate", value = "结束日期", required = true, dataType = "Date"),
            @ApiImplicitParam(name = "field", value = "排序字段", required = true, dataType = "String"),
            @ApiImplicitParam(name = "order", value = "排序顺序", required = true, dataType = "String"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/listDistributeToMe", method = RequestMethod.POST, produces = {"application/json;charset=UTF-8;"})
    public Result<Object> listDistributeToMe(@RequestParam(value = "op", required = false) String op, @RequestParam(value = "title", required = false) String title, @RequestParam(value="page", required = false, defaultValue = "1") Integer page, @RequestParam(value="pageSize", required = false, defaultValue = "20") Integer pageSize, @RequestParam(value = "fromDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date fromDate, @RequestParam(value = "toDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date toDate, @RequestParam(value = "field", required = false) String field, @RequestParam(value = "order", required = false) String order) {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        com.alibaba.fastjson.JSONArray list = new com.alibaba.fastjson.JSONArray();

        com.redmoon.oa.sso.Config ssoconfig = new com.redmoon.oa.sso.Config();
        String desKey = ssoconfig.get("key");
        ListResult lr = workflowService.listDistributeToMe(op, title, fromDate, toDate, page, pageSize, field, order);
        for (Object o : lr.getResult()) {
            PaperDistributeDb pdd = (PaperDistributeDb)o;
            com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
            jsonObject.put("title", pdd.getString("title"));
            String unit = pdd.getString("from_unit");
            String unitName = "";
            if (!StrUtil.isEmpty(unit)) {
                unitName = departmentCache.getDepartment(pdd.getString("from_unit")).getName();
            }
            String realName = userCache.getUser(pdd.getString("user_name")).getRealName();
            jsonObject.put("unitName", unitName);
            jsonObject.put("realName", realName);
            jsonObject.put("flowId", pdd.getInt("flow"));
            jsonObject.put("disDate", DateUtil.format(pdd.getDate("dis_date"), "yyyy-MM-dd HH:mm:ss"));

            String visitKey = cn.js.fan.security.ThreeDesUtil.encrypt2hex(desKey, String.valueOf(pdd.getInt("flow")));
            jsonObject.put("visitKey", visitKey);
            list.add(jsonObject);
        }
        json.put("list", list);
        json.put("total", lr.getTotal());
        return new Result<>(json);
    }

    /**
     * 我抄送的列表
     * @return
     */
    @ApiOperation(value = "我抄送的列表", notes = "我抄送的列表", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "op", value = "操作类型", required = true, dataType = "String"),
            @ApiImplicitParam(name = "title", value = "标题", required = true, dataType = "String"),
            @ApiImplicitParam(name = "page", value = "页数", required = true, dataType = "Integer"),
            @ApiImplicitParam(name = "pageSize", value = "每页条数", required = true, dataType = "Integer"),
            @ApiImplicitParam(name = "fromDate", value = "开始日期", required = true, dataType = "Date"),
            @ApiImplicitParam(name = "toDate", value = "结束日期", required = true, dataType = "Date"),
            @ApiImplicitParam(name = "field", value = "排序字段", required = true, dataType = "String"),
            @ApiImplicitParam(name = "order", value = "排序顺序", required = true, dataType = "String"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/listMyDistribute", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public Result<Object> listMyDistribute(@RequestParam(value = "op", required = false) String op, @RequestParam(value = "title", required = false) String title, @RequestParam(value = "realName", required = false) String realName, @RequestParam(value="page", required = false, defaultValue = "1") Integer page, @RequestParam(value="pageSize", required = false, defaultValue = "20") Integer pageSize, @RequestParam(value = "fromDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date fromDate, @RequestParam(value = "toDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date toDate, @RequestParam(value = "field", required = false) String field, @RequestParam(value = "order", required = false) String order) {
        com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
        com.alibaba.fastjson.JSONArray list = new com.alibaba.fastjson.JSONArray();

        com.redmoon.oa.sso.Config ssoconfig = new com.redmoon.oa.sso.Config();
        String desKey = ssoconfig.get("key");
        ListResult lr = workflowService.listMyDistribute(op, title, realName, fromDate, toDate, page, pageSize, field, order);
        for (Object o : lr.getResult()) {
            PaperDistributeDb pdd = (PaperDistributeDb)o;
            com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
            jsonObject.put("title", pdd.getString("title"));
            String unit = pdd.getString("from_unit");
            String unitName = "";
            if (!StrUtil.isEmpty(unit)) {
                unitName = departmentCache.getDepartment(pdd.getString("from_unit")).getName();
            }
            String realNamePage = userCache.getUser(pdd.getString("user_name")).getRealName();
            jsonObject.put("id", pdd.getLong("id"));
            jsonObject.put("unitName", unitName);
            jsonObject.put("realName", realNamePage);
            jsonObject.put("flowId", pdd.getInt("flow"));
            jsonObject.put("disDate", DateUtil.format(pdd.getDate("dis_date"), "yyyy-MM-dd HH:mm:ss"));
            String visitKey = cn.js.fan.security.ThreeDesUtil.encrypt2hex(desKey, String.valueOf(pdd.getInt("flow")));
            jsonObject.put("visitKey", visitKey);
            list.add(jsonObject);
        }
        json.put("list", list);
        json.put("total", lr.getTotal());
        return new Result<>(json);
    }

    @ApiOperation(value = "删除抄送", notes = "删除抄送", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "ID", required = true, dataType = "Long"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/delDistribute", method = RequestMethod.POST, produces = {"application/json;charset=UTF-8;"})
    public Result<Object> delDistribute(@RequestParam(value = "id") Long id) throws ValidateException {
        PaperDistributeDb pdd = new PaperDistributeDb();
        pdd = pdd.getPaperDistributeDb(id);
        if (!pdd.getString("user_name").equals(authUtil.getUserName())) {
            throw new ValidateException("非法操作");
        }
        try {
            return new Result<>(pdd.del());
        } catch (ResKeyException e) {
            LogUtil.getLog(getClass()).error(e);
            throw new ErrMsgException(e.getMessage(request));
        }
    }

    @ApiOperation(value = "获得用户有权限的公文模板", notes = "获得用户有权限的公文模板", httpMethod = "POST")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/getTemplates", method = RequestMethod.POST, produces = {"application/json;charset=UTF-8;"})
    public Result<Object> getTemplates() {
        DocTemplateDb ld = new DocTemplateDb();
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        String sql = "select id from " + ld.getTableName() + " where unit_code=" + StrUtil.sqlstr(com.redmoon.oa.flow.Leaf.UNIT_CODE_PUBLIC) + " or unit_code=" + StrUtil.sqlstr(privilege.getUserUnitCode(request)) + " order by sort";
        com.alibaba.fastjson.JSONArray ary = new com.alibaba.fastjson.JSONArray();
        DocTemplateMgr dtm = new DocTemplateMgr();
        for (Object o : ld.list(sql)) {
            ld = (DocTemplateDb) o;
            if (dtm.canUserSee(request, ld)) {
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                json.put("id", ld.getId());
                json.put("name", ld.getTitle());
                ary.add(json);
            }
        }
        return new Result<>(ary);
    }

    @ApiOperation(value = "模板套红", notes = "模板套红", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "flowId", value = "流程ID", required = true, dataType = "Integer"),
            @ApiImplicitParam(name = "attachId", value = "附件ID", required = true, dataType = "Integer"),
            @ApiImplicitParam(name = "templateId", value = "模板ID", required = true, dataType = "Integer"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/convertToRedDocument", method = RequestMethod.POST, produces = {"application/json;charset=UTF-8;"})
    public Result<Object> convertToRedDocument(@RequestParam(value = "flowId") Integer flowId, @RequestParam(value = "attachId") Integer attachId, @RequestParam(value = "templateId") Integer templateId) throws ValidateException {
        DocTemplateDb ld = new DocTemplateDb();
        ld = ld.getDocTemplateDb(templateId);
        if (!ld.isLoaded()) {
            throw new ValidateException("模板不存在");
        }
        String templateFilePath = sysUtil.getUploadPath() + "upfile/" + DocTemplateDb.linkBasePath + "/" + ld.getFileName();
        String templateTailFilePath = "";
        if (!StrUtil.isEmpty(ld.getFileNameTail())) {
            templateTailFilePath = sysUtil.getUploadPath() + "upfile/" + DocTemplateDb.linkBasePath + "/" + ld.getFileNameTail();
        }

        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);
        Attachment att = wf.getDocument().getAttachment(1, attachId);
        if (att.isRed()) {
            throw new ValidateException("文件已被套红");
        }

        // Attachment att = new Attachment(attachId);
        String attchFilePath = sysUtil.getUploadPath() + att.getVisualPath() + "/" + att.getDiskName();

        Leaf lf = new Leaf();
        lf = lf.getLeaf(wf.getTypeCode());
        FormDb fd = new FormDb();
        fd = fd.getFormDb(lf.getFormCode());
        FormDAO fdao = new FormDAO();
        fdao = fdao.getFormDAOByCache(flowId, fd);
        MacroCtlMgr mcm = new MacroCtlMgr();

        Map<String, String> data = new HashMap<>();
        for (FormField ff : fdao.getFields()) {
            String val;
            if (ff.getType().equals(FormField.TYPE_MACRO)) {
                String macroType = ff.getMacroType();
                MacroCtlUnit mcu = mcm.getMacroCtlUnit(macroType);
                // 取得request中原来的fdao
                IFormDAO ifdao = RequestUtil.getFormDAO(request);
                RequestUtil.setFormDAO(request, fdao);
                val = mcu.getIFormMacroCtl().converToHtml(request, ff, ff.getValue());
                if (ifdao != null) {
                    // 恢复request中原来的fdao，以免ModuleController中setFormDAO的值被修改为本方法中的fdao
                    RequestUtil.setFormDAO(request, ifdao);
                }
            }
            else {
                val = ff.convertToHtml();
            }
            data.put(ff.getTitle(), val);
            data.put(ff.getName(), val);
        }

        try {
            // 合并头部
            PoiTlUtil.toRedDocument(templateFilePath, attchFilePath, data, attchFilePath, false);

            // 合并尾部
            if (!StrUtil.isEmpty(templateTailFilePath)) {
                PoiTlUtil.toRedDocument(templateTailFilePath, attchFilePath, data, attchFilePath, true);
            }

            att.setRed(true);
            att.save();
            // 更新缓存
            DocContentCacheMgr dcm = new DocContentCacheMgr();
            dcm.refreshUpdate(wf.getDocId(), 1);

            // 保存模板类型，用以检测是否已套红
            Document doc = wf.getDocument();
            doc.setTemplateId(templateId);
            doc.updateTemplateId();

            String ext = StrUtil.getFileExt(att.getDiskName());
            com.redmoon.oa.Config cfg = Config.getInstance();
            boolean canOfficeFilePreview = cfg.getBooleanProperty("canOfficeFilePreview");
            if (canOfficeFilePreview) {
                if ("doc".equals(ext) || "docx".equals(ext) || "xls".equals(ext) || "xlsx".equals(ext)) {
                    PreviewUtil.createOfficeFilePreviewHTML(attchFilePath);
                }
            }

            return new Result<>(true);
        } catch (Exception e) {
            LogUtil.getLog(getClass()).error(e);
            return new Result<>(false, e.getMessage());
        }
    }

    @ApiOperation(value = "获得用户有权限的印章", notes = "获得用户有权限的印章", httpMethod = "POST")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/getStamps", method = RequestMethod.POST, produces = {"application/json;charset=UTF-8;"})
    public Result<Object> getStamps() {
        com.alibaba.fastjson.JSONArray ary = new com.alibaba.fastjson.JSONArray();
        com.redmoon.oa.pvg.Privilege privilege = new com.redmoon.oa.pvg.Privilege();
        StampPriv sp = new StampPriv();
        Map<String, String> map = new HashMap<>();
        Vector<StampDb> v = sp.getStampsOfUser(privilege.getUser(request));
        for (StampDb sd : v) {
            if (sd != null && sd.isLoaded()) {
                if (map.containsKey(String.valueOf(sd.getId()))) {
                    continue;
                }
                com.alibaba.fastjson.JSONObject json = new com.alibaba.fastjson.JSONObject();
                map.put(String.valueOf(sd.getId()), "");
                json.put("id", sd.getId());
                json.put("name", sd.getTitle());
                json.put("imageUrl", sd.getImageUrl());
                ary.add(json);
            }
        }

        return new Result<>(ary);
    }

    @ApiOperation(value = "文件盖章", notes = "文件盖章", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "flowId", value = "流程ID", required = true, dataType = "Integer"),
            @ApiImplicitParam(name = "attachId", value = "附件ID", required = true, dataType = "Integer"),
            @ApiImplicitParam(name = "stampId", value = "印章ID", required = true, dataType = "Integer"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/sealDocument", method = RequestMethod.POST, produces = {"application/json;charset=UTF-8;"})
    public Result<Object> sealDocument(@RequestParam(value = "flowId") Integer flowId, @RequestParam(value = "attachId") Integer attachId, @RequestParam(value = "stampId") Integer stampId) throws ValidateException {
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);
        Attachment att = wf.getDocument().getAttachment(1, attachId);

        // Attachment att = new Attachment(attachId);
        String attchFilePath = sysUtil.getUploadPath() + att.getVisualPath() + "/" + att.getDiskName();

        try {
            PoiTlUtil.sealDocumentByBookmark(attchFilePath, stampId);

            // 跟套红不一样，一个文件可能会被加盖多个印章，故此处的处理意义不大
            att.setSealed(true);
            att.save();

            // 更新缓存
            DocContentCacheMgr dcm = new DocContentCacheMgr();
            dcm.refreshUpdate(wf.getDocId(), 1);

            String ext = StrUtil.getFileExt(att.getDiskName());
            com.redmoon.oa.Config cfg = Config.getInstance();
            boolean canOfficeFilePreview = cfg.getBooleanProperty("canOfficeFilePreview");
            if (canOfficeFilePreview) {
                if ("doc".equals(ext) || "docx".equals(ext) || "xls".equals(ext) || "xlsx".equals(ext)) {
                    PreviewUtil.createOfficeFilePreviewHTML(attchFilePath);
                }
            }

            return new Result<>(true);
        } catch (IOException | ErrMsgException | InvalidFormatException e) {
            LogUtil.getLog(getClass()).error(e);
            return new Result<>(false, e.getMessage());
        }
    }

    @ApiOperation(value = "取得流程中已处理的节点", notes = "取得流程中已处理的节点，按时间降序排序", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "flowId", value = "流程ID", required = true, dataType = "Integer"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/getActionsFinished", method = RequestMethod.POST, produces = {"application/json;charset=UTF-8;"})
    public Result<Object> getActionsFinished(@RequestParam(value = "flowId") Integer flowId) throws ValidateException {
        WorkflowActionDb wad = new WorkflowActionDb();
        List<WorkflowActionDb> list = wad.getActionsFinishedOfFlow(flowId);
        JSONArray arr = new JSONArray();
        for (WorkflowActionDb wa : list) {
            JSONObject json = new JSONObject();
            json.put("id", wa.getId());
            json.put("name", wa.getTitle());
            arr.add(json);
        }
        return new Result<>(arr);
    }

    @ApiOperation(value = "取得全部流程目录树", notes = "取得全部流程目录树", httpMethod = "POST")
    @ApiImplicitParam(name = "parentCode", value = "父节点编码", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/getDirTreeAll", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<com.alibaba.fastjson.JSONArray> getDirTreeAll(@RequestParam(value="parentCode", required = false) String parentCode, @RequestParam(value="isIcon", defaultValue = "true")Boolean isIcon) {
        if (parentCode == null) {
            parentCode = Leaf.CODE_ROOT;
        }
        Leaf lf = new Leaf();
        lf = lf.getLeaf(parentCode);
        DirectoryView dv = new DirectoryView(lf);
        com.alibaba.fastjson.JSONObject jsonRoot = new com.alibaba.fastjson.JSONObject();
        // 如果是根节点，则将code置为空，以免在前台看到是“全部类别”却查不出来引起误解
        jsonRoot.put("code", lf.getCode());
        jsonRoot.put("parentCode", lf.getParentCode());
        jsonRoot.put("name", lf.getName());
        jsonRoot.put("layer", lf.getLayer());
        dv.getTreeAll(lf, jsonRoot);

        com.alibaba.fastjson.JSONArray ary = new com.alibaba.fastjson.JSONArray();
        ary.add(jsonRoot);
        return new Result<>(ary);
    }
}
