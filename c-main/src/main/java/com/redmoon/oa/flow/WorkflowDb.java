package com.redmoon.oa.flow;

import cn.js.fan.db.*;
import cn.js.fan.security.SecurityUtil;
import cn.js.fan.util.DateUtil;
import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.ParamUtil;
import cn.js.fan.util.StrUtil;
import cn.js.fan.web.Global;
import cn.js.fan.web.SkinUtil;
import com.alibaba.fastjson.JSONArray;
import com.cloudweb.oa.api.IModuleFieldSelectCtl;
import com.cloudweb.oa.api.IWorkflowHelper;
import com.cloudweb.oa.api.IWorkflowScriptUtil;
import com.cloudweb.oa.api.IWorkflowUtil;
import com.cloudweb.oa.entity.PostUser;
import com.cloudweb.oa.service.FormArchiveService;
import com.cloudweb.oa.service.IFileService;
import com.cloudweb.oa.service.IPostUserService;
import com.cloudweb.oa.service.MacroCtlService;
import com.cloudweb.oa.utils.ConstUtil;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudwebsoft.framework.db.Connection;
import com.cloudwebsoft.framework.db.JdbcTemplate;
import com.cloudwebsoft.framework.util.IPUtil;
import com.cloudwebsoft.framework.util.LogUtil;
import com.cloudwebsoft.framework.web.UserAgentParser;
import com.redmoon.kit.util.FileInfo;
import com.redmoon.kit.util.FileUpload;
import com.redmoon.oa.Config;
import com.redmoon.oa.base.IFormDAO;
import com.redmoon.oa.base.IFormMacroCtl;
import com.redmoon.oa.base.IFormValidator;
import com.redmoon.oa.db.SequenceManager;
import com.redmoon.oa.dept.DeptDb;
import com.redmoon.oa.dept.DeptMgr;
import com.redmoon.oa.dept.DeptUserDb;
import com.redmoon.oa.flow.macroctl.MacroCtlMgr;
import com.redmoon.oa.flow.macroctl.MacroCtlUnit;
import com.redmoon.oa.message.MessageDb;
import com.redmoon.oa.person.*;
import com.redmoon.oa.pvg.Privilege;
import com.redmoon.oa.pvg.RoleDb;
import com.redmoon.oa.shell.BSHShell;
import com.redmoon.oa.sms.IMsgUtil;
import com.redmoon.oa.sms.SMSFactory;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.ui.IDesktopUnit;
import com.redmoon.oa.ui.LocalUtil;
import com.redmoon.oa.util.BeanShellUtil;
import com.redmoon.oa.visual.SQLBuilder;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.InputSource;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.sql.*;
import java.sql.Date;
import java.util.*;

public class WorkflowDb implements Serializable,IDesktopUnit {
    String connname;

    public boolean isLoaded() {
        return loaded;
    }

    private boolean loaded = false;

    public String getDelUser() {
        return delUser;
    }

    public void setDelUser(String delUser) {
        this.delUser = delUser;
    }

    public java.util.Date getDelDate() {
        return delDate;
    }

    public void setDelDate(java.util.Date delDate) {
        this.delDate = delDate;
    }

    private String delUser;
    private java.util.Date delDate;

    public boolean isRenewed() {
        return renewed;
    }

    public void setRenewed(boolean renewed) {
        this.renewed = renewed;
    }

    private boolean renewed;

    public String getImgVisualPath() {
        return imgVisualPath;
    }

    public void setImgVisualPath(String imgVisualPath) {
        this.imgVisualPath = imgVisualPath;
    }

    private String imgVisualPath;

    /**
     * 是否有变更
     */
    private boolean alter;
    /**
     * 变更者
     */
    private String alterUser;

    public boolean isAlter() {
        return alter;
    }

    public void setAlter(boolean alter) {
        this.alter = alter;
    }

    public String getAlterUser() {
        return alterUser;
    }

    public void setAlterUser(String alterUser) {
        this.alterUser = alterUser;
    }

    public java.util.Date getAlterTime() {
        return alterTime;
    }

    public void setAlterTime(java.util.Date alterTime) {
        this.alterTime = alterTime;
    }

    /**
     * 变更时间
     */
    private java.util.Date alterTime;
    /**
     * 创建时间
     */
    private java.util.Date mydate;

    final String INSERT = "insert into flow (id, type_code, title, userName, jobCode, resultValue, return_back, mydate, project_id, unit_code, flow_level, parent_action_id,status,form_archive_id) values (?,?,?,?,?," + WorkflowActionDb.RESULT_VALUE_NOT_ACCESSED + ",?,?,?,?,?,?," + STATUS_NONE + ",?)";
    final String LOAD = "select type_code,title,doc_id,userName,flow_string,status,resultValue,checkUserName,mydate,remark,return_back,BEGIN_DATE,END_DATE,project_id,unit_code,flow_level,parent_action_id,locker,del_user,del_date,is_renewed,img_visual_path,is_alter,alter_time,alter_user,flow_json,form_archive_id,intervenor,intervene_time from flow where id=?";
    final String SAVE = "update flow set type_code=?,title=?,doc_id=?,userName=?,flow_string=?,status=?,resultValue=?,checkUserName=?,remark=?,return_back=?,BEGIN_DATE=?,END_DATE=?,project_id=?,flow_level=?,parent_action_id=?,locker=?,del_user=?,del_date=?,is_renewed=?,img_visual_path=?,is_alter=?,alter_time=?,alter_user=?,flow_json=?,form_archive_id=?,intervenor=?,intervene_time=? where id=?";
    final String DELETE = "delete from flow where id=?";

    /**
     * 流程未开始
     */
    public static final int STATUS_NOT_STARTED = 0;
    /**
     * 流程已开始
     */
    public static final int STATUS_STARTED = 1;
    /**
     * 流程已结束
     */
    public static final int STATUS_FINISHED = 2;
    /**
     * 流程已放弃
     */
    public static final int STATUS_DISCARDED = -1;
    /**
     * 流程已拒绝
     */
    public static final int STATUS_REFUSED = -2;

    /**
     * 流程未生效，尚未发起，将会在一天后被调度删除
     */
    public static final int STATUS_NONE = -10;
    
    /**
     * 流程已被删除
     */
    public static final int STATUS_DELETED = -11;

    public static final int LEVEL_NORMAL = 0;
    public static final int LEVEL_IMPORTANT = 1;
    public static final int LEVEL_URGENT = 2;

    public static final long PARENT_ACTION_ID_NONE = 0;

    public WorkflowDb() {
        init();
    }

    public WorkflowDb(int id) {
        init();
        this.id = id;
        load();
    }

    public void init() {
        id = -1;
        docId = -1;
        connname = Global.getDefaultDB();
        if ("".equals(connname)) {
            LogUtil.getLog(getClass()).info("Directory:默认数据库名为空！");
        }
    }

    /**
     * 创建流程，初始化一条空白表单记录
     * @param typeCode String 流程类型
     * @param title String 标题
     * @param userName String 创建者用户名
     * @return boolean
     * @throws ErrMsgException
     */
    public boolean create(String typeCode, String title, String userName) throws ErrMsgException {
        PreparedStatement pstmt = null;
        this.id = (int) SequenceManager.nextID(SequenceManager.OA_WORKFLOW);

        Conn conn = new Conn(connname);
        try {
            conn.beginTrans();
            pstmt = conn.prepareStatement(INSERT);
            pstmt.setInt(1, id);
            pstmt.setString(2, typeCode);
            pstmt.setString(3, title);
            pstmt.setString(4, userName);
            pstmt.setString(5, ""); // pd.getCode());
            pstmt.setInt(6, returnBack?1:0);
            pstmt.setTimestamp(7, new Timestamp(new java.util.Date().getTime()));
            pstmt.setLong(8, projectId);

            // LogUtil.getLog(getClass()).info("create: projectId=" + projectId);

            DeptUserDb dud = new DeptUserDb();
            unitCode = dud.getUnitOfUser(userName).getCode();

            pstmt.setString(9, unitCode);
            pstmt.setInt(10, level);
            pstmt.setLong(11, parentActionId);
            pstmt.setLong(12, formArchiveId);
            int r = conn.executePreUpdate();
            if (r == 1) {
                // 清缓存
                WorkflowCacheMgr wcm = new WorkflowCacheMgr();
                wcm.refreshCreate();

                pstmt.close();

                // 为流程创建默认监控人员，即流程的创建者本身（监控人员暂时无效）
                /*String sql = "insert into flow_monitor (flowId, userName, flowCreateDate) values (?, ?, ?)";
                pstmt = conn.prepareStatement(sql);
                pstmt.setInt(1, id);
                pstmt.setString(2, userName);
                pstmt.setTimestamp(3, new Timestamp(new java.util.Date().getTime()));
                conn.executePreUpdate();
                */

                conn.commit();

                // 为其创建流程文档，文档中存放表单
                Document doc = new Document();
                Leaf lf = new Leaf();
                lf = lf.getLeaf(typeCode);
                FormDb fd = new FormDb();
                fd = fd.getFormDb(lf.getFormCode());
                doc.setFlowId(id);
                // boolean re = doc.create(typeCode, title, fd.getContent(), 0, "", "", userName, Document.NOTEMPLATE, userName);
                boolean re = doc.create(typeCode, title, "", 0, "", "", userName, Document.NOTEMPLATE, userName);
                if (re) {
                    WorkflowDb wfd = getWorkflowDb(id);
                    wfd.setDocId(doc.getID());
                    wfd.save();
                }
                else {
                    LogUtil.getLog(getClass()).error("create: Create document failed.");
                }

                //  为流程创建一条空表单记录，记录中存储的为表单域的默认值
                FormDAO fdao = new FormDAO(id, fd);
                for (FormField ff : fd.getFields()) {
                    ff.setValue(ff.getDefaultValue());
                }
                fdao.setFlowTypeCode(typeCode);
                fdao.setFields(fd.getFields()); // 设置默认值
                fdao.setUnitCode(unitCode);
                fdao.setCreator(userName);
               
                re = fdao.create();
                if (re) {
                    // 为嵌套表单宏控件创建空表单记录
                    Iterator<FormField> ir = fd.getFields().iterator();
                    MacroCtlMgr mm = new MacroCtlMgr();
                    while (ir.hasNext()) {
                        FormField macroField = ir.next();
                        if (macroField.getType().equals(FormField.TYPE_MACRO)) {
                            MacroCtlUnit mu = mm.getMacroCtlUnit(macroField.getMacroType());
                            if (mu!=null) {
                                if (mu.getNestType() == MacroCtlUnit.NEST_TYPE_NORMAIL) {
                                    mu.getIFormMacroCtl().initNestCtlOnInitWorkflow(macroField, id, userName);
                                }
                            }
                            else {
                                LogUtil.getLog(getClass()).error(macroField.getTitle() + " 不存在！fieldName=" + macroField.getName() + " macroType=" + macroField.getMacroType());
                            }
                        }
                    }
                }
                return re;
            }
        } catch (SQLException e) {
            conn.rollback();
            LogUtil.getLog(getClass()).error(e);
        } finally {
            conn.close();
        }
        return false;
    }

    public WorkflowDb getWorkflowDb(int id) {
        WorkflowCacheMgr wfc = new WorkflowCacheMgr();
        return wfc.getWorkflow(id);
    }

    public void load() {
        // Based on the id in the object, get the message data from the database.
        Conn conn = new Conn(connname);
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = conn.prepareStatement(LOAD);
            pstmt.setInt(1, id);
            rs = conn.executePreQuery();
            if (!rs.next()) {
                LogUtil.getLog(getClass()).error("流程id= " + id +
                             " 在数据库中未找到.");
            } else {
                this.typeCode = rs.getString(1);
                this.title = rs.getString(2);
                this.docId = rs.getInt(3);
                this.userName = rs.getString(4);
                this.flowString = rs.getString(5);
                this.status = rs.getInt(6);
                this.resultValue = rs.getInt(7);
                this.checkUserName = rs.getString(8);
                Timestamp ts = rs.getTimestamp(9);
                if (ts!=null) {
                    this.mydate = new java.util.Date(ts.getTime());
                }
                this.remark = StrUtil.getNullStr(rs.getString(10));
                this.returnBack = rs.getInt(11)==1;
                ts = rs.getTimestamp(12);
                if (ts!=null) {
                    this.beginDate = new java.util.Date(ts.getTime());
                }
                ts = rs.getTimestamp(13);
                if (ts!=null) {
                    this.endDate = new java.util.Date(ts.getTime());
                }

                // "select type_code,title,doc_id,userName,flow_string,status,resultValue,checkUserName,mydate,remark,return_back,BEGIN_DATE,END_DATE,project_id,unit_code from flow where id=?";
                projectId = rs.getLong(14);
                unitCode = rs.getString(15);
                level = rs.getInt(16);
                parentActionId = rs.getInt(17);
                locker = StrUtil.getNullStr(rs.getString(18));
                delUser = StrUtil.getNullStr(rs.getString(19));
                ts = rs.getTimestamp(20);
                if (ts!=null) {
                    delDate = new java.util.Date((ts.getTime()));
                }
                renewed = rs.getInt(21)==1;
                imgVisualPath = StrUtil.getNullStr(rs.getString(22));
                alter = rs.getInt(23)==1;
                alterTime = rs.getTimestamp(24);
                alterUser = StrUtil.getNullStr(rs.getString(25));
                flowJson = StrUtil.getNullStr(rs.getString(26));
                formArchiveId = rs.getLong(27);
				intervenor = StrUtil.getNullStr(rs.getString(28));
                interveneTime = rs.getTimestamp(29);
                loaded = true;

                rs.close();
                pstmt.close();

                // 获取监控人员
                String sql = "select userName from flow_monitor where flowId=?";
                pstmt = conn.prepareStatement(sql);
                pstmt.setInt(1, id);
                rs = conn.executePreQuery();
                if (rs!=null) {
                    monitors = new String[conn.getRows()];
                    int i = 0;
                    while (rs.next()) {
                        monitors[i] = rs.getString(1);
                        i ++;
                    }
                }
            }
        } catch (SQLException e) {
            LogUtil.getLog(getClass()).error(e);
        } finally {
            conn.close();
        }
    }

    /**
     * 放弃流程
     * @param userName String 放弃流程的操作者
     * @return boolean
     */
    public boolean discard(String userName) throws ErrMsgException {
        if (remark == null) {
            remark = "";
        }
        // 忽略正在待办的myAction
        MyActionDb mad = new MyActionDb();
        mad.onDiscard(id);

        UserMgr um = new UserMgr();
        UserDb ud = um.getUserDb(userName);

        remark += ud.getRealName() + " 放弃流程于" +
                DateUtil.format(Calendar.getInstance(), "yyyy-MM-dd HH:mm:ss");
        endDate = new java.util.Date();
        status = STATUS_DISCARDED;
        save();

        Leaf lf = new Leaf(typeCode);
        FormDAO fdao = new FormDAO();
        fdao = fdao.getFormDAO(id, new FormDb(lf.getFormCode()));
        fdao.setStatus(FormDAO.STATUS_DISCARD);
        fdao.save();

        // 更新缓存
        WorkflowCacheMgr wfc = new WorkflowCacheMgr();
        wfc.refreshDel(id);
        // 置流程中正在处理状态的节点的状态为已放弃
        WorkflowActionDb wad = new WorkflowActionDb();
        wad.onWorkflowDiscarded(id);

        return true;
    }

    /**
     * 拒绝
     * @param userName String
     * @param myActionId long
     * @return boolean
     * @throws ErrMsgException
     */
    public boolean ManualFinish(HttpServletRequest request, String userName, long myActionId, boolean isFinishAgree) throws ErrMsgException {
        if (remark == null) {
            remark = "";
        }
        // 置myAction为已check状态
        MyActionDb mad = new MyActionDb();
        mad = mad.getMyActionDb(myActionId);

        mad.setIp(IPUtil.getRemoteAddr(request));
        String ua = request.getHeader("User-Agent");
        mad.setOs(UserAgentParser.getOS(ua));
        mad.setBrowser(UserAgentParser.getBrowser(ua));
        mad.setClusterNo(Global.getInstance().getClusterNo());

        mad.onWorkflowManualFinished(id, userName, isFinishAgree);

        UserMgr um = new UserMgr();
        UserDb ud = um.getUserDb(userName);

        remark += ud.getRealName() + "结束流程于" +
                DateUtil.format(Calendar.getInstance(), "yyyy-MM-dd HH:mm:ss");

        /*
        // 2010.4.15注释掉，改为changeStatus
        status = this.STATUS_FINISHED;
        endDate = new java.util.Date();
        save();
        */
        WorkflowActionDb lastAction = new WorkflowActionDb();
        lastAction = lastAction.getWorkflowActionDb((int)mad.getActionId());
        
        if (!isFinishAgree) {
        	changeStatus(request, STATUS_REFUSED, lastAction);
        }
        else  {
        	changeStatus(request, STATUS_FINISHED, lastAction);   	
        }

        // 更新缓存
        WorkflowCacheMgr wfc = new WorkflowCacheMgr();
        wfc.refreshList();
        // 置流程中正在处理状态的节点的状态为已结束
        WorkflowActionDb wad = new WorkflowActionDb();
        wad.onWorkflowManualFinished(id);

        return true;
    }

    public boolean save() {
        PreparedStatement pstmt = null;
        Conn conn = new Conn(connname);
        try {
            // conn = jt.getDataSource().getConnection();
            pstmt = conn.prepareStatement(SAVE);
            pstmt.setString(1, typeCode);
            pstmt.setString(2, title);
            pstmt.setInt(3, docId);
            pstmt.setString(4, userName);
            pstmt.setString(5, flowString);
            pstmt.setInt(6, status);
            pstmt.setInt(7, resultValue);
            pstmt.setString(8, checkUserName);
            pstmt.setString(9, remark);
            pstmt.setInt(10, returnBack ? 1 : 0);
            if (beginDate != null) {
                pstmt.setTimestamp(11, new Timestamp(beginDate.getTime()));
            } else {
                pstmt.setTimestamp(11, null);
            }
            if (endDate != null) {
                pstmt.setTimestamp(12, new Timestamp(endDate.getTime()));
            } else {
                pstmt.setTimestamp(12, null);
            }
            pstmt.setLong(13, projectId);
            pstmt.setInt(14, level);
            pstmt.setLong(15, parentActionId);
            pstmt.setString(16, locker);
            pstmt.setString(17, delUser);
            if (delDate!=null) {
                pstmt.setTimestamp(18, new Timestamp(delDate.getTime()));
            } else {
                pstmt.setTimestamp(18, null);
            }
            pstmt.setInt(19, renewed?1:0);
            pstmt.setString(20, imgVisualPath);
            pstmt.setInt(21, alter?1:0);
            if (alterTime==null) {
                pstmt.setTimestamp(22, null);
            }
            else {
                pstmt.setTimestamp(22, new Timestamp(alterTime.getTime()));
            }
            pstmt.setString(23, alterUser);
            pstmt.setString(24, flowJson);
            pstmt.setLong(25, formArchiveId);
            pstmt.setString(26, intervenor);
            if (interveneTime == null) {
                pstmt.setTimestamp(27, null);
            }
            else {
                pstmt.setTimestamp(27, new Timestamp(interveneTime.getTime()));
            }
            pstmt.setInt(28, id);
            int r = pstmt.executeUpdate();
            if (r == 1) {
                // 更新缓存
                WorkflowCacheMgr wfc = new WorkflowCacheMgr();
                wfc.refreshSave(id);
                return true;
            }
        } catch (SQLException e) {
            LogUtil.getLog(getClass()).error(e);
        } finally {
            conn.close();
        }
        return false;
    }

    private int level = LEVEL_NORMAL;

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setDocId(int doc_id) {
        this.docId = doc_id;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setFlowString(String flowString) {
        this.flowString = flowString;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void setResultValue(int resultValue) {
        this.resultValue = resultValue;
    }

    public void setCheckUserName(String checkUserName) {
        this.checkUserName = checkUserName;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public void setReturnBack(boolean returnBack) {
        this.returnBack = returnBack;
    }

    public void setBeginDate(java.util.Date beginDate) {
        this.beginDate = beginDate;
    }

    public void setEndDate(java.util.Date endDate) {
        this.endDate = endDate;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public void setUnitCode(String unitCode) {
        this.unitCode = unitCode;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setParentActionId(long parentActionId) {
        this.parentActionId = parentActionId;
    }

    public void setLocker(String locker) {
        this.locker = locker;
    }

    public void setMydate(Date mydate) {
        this.mydate = mydate;
    }

    public boolean changeStatus(HttpServletRequest request, int status, WorkflowActionDb lastAction) throws ErrMsgException {
        int oldStatus = this.status;

        setStatus(status);
        setCheckUserName(lastAction.getCheckUserName());
        setResultValue(lastAction.getResultValue());
        if (status==STATUS_FINISHED || status==STATUS_DISCARDED || status==STATUS_REFUSED) {
            endDate = new java.util.Date();
        }

        // 如果原先是已完成状态，现切换为未完成状态，则置fdao的cws_status为0
        if (oldStatus==WorkflowDb.STATUS_FINISHED && oldStatus!=status) {
            if (status==WorkflowDb.STATUS_STARTED) {
                com.redmoon.oa.flow.FormDAO fdao = new com.redmoon.oa.flow.FormDAO();
                Leaf lf = new Leaf();
                lf = lf.getLeaf(getTypeCode());
                FormDb fd = new FormDb();
                fd = fd.getFormDb(lf.getFormCode());
                fdao = fdao.getFormDAO(id, fd);
                fdao.setStatus(FormDAO.STATUS_NOT);
                try {
                    fdao.save();
                } catch (ErrMsgException e) {
                    LogUtil.getLog(getClass()).error(e);
                }
            }
        }

        boolean re = save();
        if (re) {
            // 当流程处理完毕时，通过IFormValidator作进一步的数据处理
            if (status==STATUS_FINISHED || status==STATUS_REFUSED) {
                WorkflowDb wfd = new WorkflowDb();
                wfd = wfd.getWorkflowDb(lastAction.getFlowId());

                Leaf lf = new Leaf();
                lf = lf.getLeaf(wfd.getTypeCode());
                FormValidatorConfig fvc = new FormValidatorConfig();
                IFormValidator ifv = fvc.getIFormValidatorOfForm(lf.getFormCode());
                try {
                    if (ifv != null) {
                        ifv.onWorkflowFinished(wfd, lastAction);
                    }
                }
                catch (ErrMsgException e) {
                    LogUtil.getLog(getClass()).error(StrUtil.trace(e));
                }

     	       WorkflowPredefineDb wpd = new WorkflowPredefineDb();
    	       wpd = wpd.getDefaultPredefineFlow(wfd.getTypeCode());
    	       WorkflowPredefineMgr wpm = new WorkflowPredefineMgr();
    	       String script = wpm.getOnFinishScript(wpd.getScripts());
    	       
    	       FormDAO fdao = null;

               FormDb fd = new FormDb();
               fd = fd.getFormDb(lf.getFormCode());
               
               fdao = new FormDAO();
               fdao = fdao.getFormDAO(wfd.getId(), fd);
               
               long fdaoId = fdao.getId();
               
               int cwsStatus = FormDAO.STATUS_NOT;
               if (status==STATUS_FINISHED) {
            	   cwsStatus = FormDAO.STATUS_DONE;
            	   fdao.setCwsFinishDate(new java.util.Date());
               }
               else if (status==STATUS_REFUSED) {
            	   cwsStatus = FormDAO.STATUS_REFUSED;
               }
               
               // 置表單標志位已完成
               fdao.setStatus(cwsStatus);
               fdao.save();
			   
			   	// 置所有嵌套表的记录的cws_status
		        MacroCtlMgr mm = new MacroCtlMgr();
                for (FormField macroField : fdao.getFields()) {
                    if (macroField.getType().equals(FormField.TYPE_MACRO)) {
                        MacroCtlUnit mu = mm.getMacroCtlUnit(macroField.getMacroType());
                        if (mu == null) {
                            DebugUtil.e(getClass(), macroField.getTitle() + "宏控件类型", macroField.getMacroType() + "不存在");
                            continue;
                        }
                        if ("module_field_select".equals(mu.getCode())) {
                            MacroCtlService macroCtlService = SpringUtil.getBean(MacroCtlService.class);
                            IModuleFieldSelectCtl moduleFieldSelectCtl = macroCtlService.getModuleFieldSelectCtl();
                            com.alibaba.fastjson.JSONObject jsonDesc = moduleFieldSelectCtl.getCtlDescription(macroField);
                            boolean isAgainst = jsonDesc.getBoolean("isAgainst");
                            // 冲抵表单域选择宏控件所选的那条记录
                            if (status==STATUS_FINISHED && isAgainst) {
                                FormDAO.updateFlagForModuleFieldSelectCtl(jsonDesc, macroField);
                            }
                        } else if (mu.getNestType() != MacroCtlUnit.NEST_TYPE_NONE) {
                            String formCode = macroField.getDescription();
                            boolean isAgainst = false;
                            String sourceForm = "";
                            try {
                                String defaultVal = StrUtil.decodeJSON(formCode);
                                JSONObject json = new JSONObject(defaultVal);
                                formCode = json.getString("destForm");
                                sourceForm = json.getString("sourceForm");
                                // 是否冲抵
                                if (json.has("isAgainst")) {
                                    isAgainst = "1".equals(json.getString("isAgainst"));
                                }
                            } catch (JSONException e) {
                                LogUtil.getLog(getClass()).info("changeStatus:" + formCode + " is old version before 20131123. ff.getDefaultValueRaw()=" + macroField.getDefaultValueRaw());
                                LogUtil.getLog(getClass()).error(e);
                            }

                            FormDAO.updateStatus(formCode, fdaoId, cwsStatus);

                            if (status == STATUS_FINISHED && isAgainst) {
                                FormDAO.updateFlag((int) fdao.getId(), sourceForm, formCode, 1);
                            }
                        }
                    }
                }

		        // 干预流程时，会用到isCallEvent
		        boolean isCallEvent = ParamUtil.getBoolean(request, "isCallEvent", true);
                LogUtil.getLog(getClass()).info("changeStatus: runFinishScript isCallEvent=" + isCallEvent);
                if (isCallEvent) {
                    if (!StrUtil.isEmpty(script)) {
                        runFinishScript(request, wfd, fdao, lastAction, script, false);
                    }
                }
            }
        }
        else {
            LogUtil.getLog(getClass()).error("changeStatus: save failed.");
        }
        // 根据流程的类型，获取其对应于status的action
        // PluginMgr pm = new PluginMgr();
        // PluginUnit pu = pm.getPluginUnitOfDir(typeCode);
        //if (pu != null) {
        //    IPluginWorkflowOperater iwa = pu.getUnit().getWorkflowOperater(id);
        //    iwa.OnStatusChange(status, lastAction);
        //}

        return re;
    }
    
    /**
     * 运行结束脚本
     * @Description: 
     * @param request
     * @param wfd
     * @param fdao
     * @param lastAction
     * @param script
     * @param isTest
     * @return
     */
   	public BSHShell runFinishScript(HttpServletRequest request, WorkflowDb wfd, FormDAO fdao, WorkflowActionDb lastAction, String script, boolean isTest) throws ErrMsgException {
        IWorkflowScriptUtil workflowScriptUtil = SpringUtil.getBean(IWorkflowScriptUtil.class);
        return workflowScriptUtil.runFinishScript(request, wfd, fdao, lastAction, script, isTest);
   	}    

    public String getTypeCode() {
        return typeCode;
    }

    public String getTitle() {
        return title;
    }

    public Document getDocument() {
    	Document doc = new Document();
    	doc = doc.getDocument(docId);
        return doc;
    }

    public int getId() {
        return id;
    }

    public String getUserName() {
        return userName;
    }

    public String getFlowString() {
        return flowString;
    }

    public int getDocId() {
        return docId;
    }

    public int getResultValue() {
        return resultValue;
    }

    public String getCheckUserName() {
        return checkUserName;
    }

    public java.util.Date getMydate() {
        return mydate;
    }

    private String typeCode;
    private String title;
    private int id;
    private int docId;
    private String userName;
    private String flowString;

    /**
     * 根据流程图字符串取出其中的action，用于节点上选择由某另一个节点上的用户处理
     * @param str String
     * @return Vector
     */
    public Vector<WorkflowActionDb> getActionsFromString(String str) throws ErrMsgException {
        if (!str.startsWith("paper")) {
            return null;
        }
        String[] ary = str.split("\\r\\n");
        int len = ary.length;

        Vector<WorkflowActionDb> v = new Vector<>();

        // 检查各个节点的合法性
        WorkflowActionDb wachk = new WorkflowActionDb();
        String errMsg = "";
        for (int i = 1; i < len; i++) {
            try {
                wachk.fromString(ary[i], true);
            } catch (ErrMsgException e) {
                errMsg += e.getMessage() + "\\r\\n";
            }
        }
        if (!"".equals(errMsg)) {
            throw new ErrMsgException(errMsg);
        }

        for (int i = 1; i < len; i++) {
            WorkflowActionDb wa = new WorkflowActionDb();
            wa.setFlowId(id);
            boolean re = wa.fromString(ary[i], true);
            // 如果流程尚未开始，而动作节点为开始节点，则将节点的状态置为正处理状态
            if (!isStarted()) {
                if (wa.isStart == 1) {
                    // 动作状态出现改变，需要renewWorkflowString
                    wa.setStatus(WorkflowActionDb.STATE_DOING);
                }
            }
            if (re) {
                v.addElement(wa);
            }
        }
        return v;
    }

    public Vector<WorkflowLinkDb> getLinksFromString(String str) throws ErrMsgException {
        if (!str.startsWith("paper")) {
            return null;
        }
        String[] ary = str.split("\\r\\n");
        int len = ary.length;

        Vector<WorkflowLinkDb> v = new Vector<>();
        for (int i = 1; i < len; i++) {
            WorkflowLinkDb wl = new WorkflowLinkDb();
            wl.setFlowId(id);
            boolean re = wl.fromString(ary[i]);
            if (re) {
                v.addElement(wl);
            }
        }
        return v;
    }

    /**
     * 从str创建流程中的action及link
     * @param str String
     * @return long 开始节点处理者的myActionId
     * @throws ErrMsgException
     */
    public long createFromString(String str, String flowJson) throws ErrMsgException {
        long t = System.currentTimeMillis();
        long starterMyActionId = -1;

        if (!str.startsWith("paper")) {
            throw new ErrMsgException("流程图错误：" + str);
            // return starterMyActionId;
        }
        
        this.flowString = str;
        this.flowJson = flowJson;
        save();
        
        String[] ary = str.split("\\r\\n");
        int len = ary.length;
        
        // 检查各个节点的合法性
        WorkflowActionDb wachk = new WorkflowActionDb();
        String errMsg = "";
        for (int i = 1; i < len; i++) {
            try {
                wachk.fromString(ary[i], true);
            }
            catch (ErrMsgException e) {
                errMsg += e.getMessage() + "\\r\\n";
            }
        }
        if (!"".equals(errMsg)) {
            throw new ErrMsgException(errMsg);
        }

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "createFromString 合法性校驗", (System.currentTimeMillis() - t) + " ms");
        }

        // 20101017用以记录发起节点ID
        Map<String, WorkflowActionDb> actionMap = new HashMap<>();
        List<WorkflowLinkDb> linkList = new ArrayList<>();
        int startActionId = -1;
        int actionCount = 0;
        int linkCount = 0;
        for (int i = 1; i < len; i++) {
            WorkflowActionDb wa = new WorkflowActionDb();
            wa.setFlowId(id);
            boolean re = wa.fromString(ary[i], true);

            if (re) {
                actionCount++;
                actionMap.put(wa.getInternalName(), wa);
            }
            else {
                WorkflowLinkDb wl = new WorkflowLinkDb();
                wl.setFlowId(id);
                if (wl.fromString(ary[i])) {
                    linkCount++;
                    linkList.add(wl);
                }
            }
        }

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "createFromString 創建節點和連線", (System.currentTimeMillis() - t) + " ms");
        }
        
        // 201610 fgf 取得所有的起始节点，并匹配得到第一个符合条件的节点
        String errMsgStarter = "";

        Vector<String> vStart = new Vector<>();
        Vector<WorkflowActionDb> vStartMatched = new Vector<>();

        JSONObject flowJsonObject = null;
        try {
            flowJsonObject = new JSONObject(flowJson);
        } catch (JSONException e) {
            LogUtil.getLog(getClass()).error(e);
            flowJsonObject = new JSONObject();
        }

        int inDegree = 0;
        try {
            JSONObject states = flowJsonObject.getJSONObject("states");
            Iterator irJson = states.keys();
            while (irJson.hasNext()) {
                String key = (String) irJson.next();
                JSONObject jsonAction = states.getJSONObject(key);
                inDegree = jsonAction.getInt("inDegree");
                if (inDegree == 0) {
                    vStart.addElement(key);
                    WorkflowActionDb wad = actionMap.get(key);

                    // 判断是否满足条件
                    boolean re = false;
                    String jobCode = wad.getJobCode();
                    if (jobCode.equals(WorkflowActionDb.PRE_TYPE_SELF)) {
                        // 本人的優先級最低
                        // re = true; // waStart = wad;
                        try {
                            re = checkStarterSelf(wad, userName);
                        } catch (ErrMsgException e) {
                            errMsgStarter += e.getMessage() + "\r\n";
                        }
                    }
                    else {
                        try {
                            re = checkStarter(wad, userName);
                        }
                        catch (ErrMsgException e) {
                            errMsgStarter += e.getMessage() + "\r\n";
                        }
                    }
                    // 如满足，则置其为起始节点
                    if (re) {
                        vStartMatched.addElement(wad);
                    }
                    else {
                        if (wad.getIsStart() != 0) {
                            wad.setIsStart(0);
                            wad.save();
                        }
                    }
                }
            }
        } catch (JSONException e) {
            LogUtil.getLog(getClass()).error(e);
        }

        /* 20230622 本機通過flowJson判斷入度為1后優化后快了近0.3秒，服务器上似乎区别不大
        Vector<WorkflowActionDb> v = getActions();
        Iterator<WorkflowActionDb> ir = v.iterator();
        WorkflowLinkDb wl = new WorkflowLinkDb();
        // 2016 fgf 20161127 如果多个起点满足条件，则按指定用户的节点优先，如果有多个起点的角色满足条件，则走其中角色最大的那个起点
        while (ir.hasNext()) {
        	WorkflowActionDb wad = ir.next();
            // 如果入度为0，或者是发起节点（有可能是循环的情况，其入度不为0）
        	if (wad.getIsStart()==1 || wl.getFromLinkCount(wad)==0) {
    			vStart.addElement(wad);
        		
        		// 判断是否满足条件
        		boolean re = false;
        		String jobCode = wad.getJobCode();
                if (jobCode.equals(WorkflowActionDb.PRE_TYPE_SELF)) {
                    // 本人的優先級最低
                    // re = true; // waStart = wad;
                    try {
                        re = checkStarterSelf(wad, userName);
                    } catch (ErrMsgException e) {
                        errMsgStarter += e.getMessage() + "\r\n";
                    }
                }
                else {
	        		try {
	        			re = checkStarter(wad, userName);
	        		}
	        		catch (ErrMsgException e) {
	        			errMsgStarter += e.getMessage() + "\r\n";
	        		}
                }
        		// 如满足，则置其为起始节点
        		if (re) {
        			vStartMatched.addElement(wad);
        		}
        		else {
        		    if (wad.getIsStart() != 0) {
                        wad.setIsStart(0);
                        wad.save();
                    }
        		}
        	}
        }*/

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "createFromString 找出可能匹配的開始節點", (System.currentTimeMillis() - t) + " ms");
        }
        
        WorkflowActionDb waStart = null;
        int orderStart = -100000;
        
        // 从满足条件的多起点中，找出角色最大的那个作为发起节点
        for (WorkflowActionDb wad : vStartMatched) {
            int rOrder = -1;
            // 检查节点上的多个角色的大小，取得角色最大的那个order
            if (wad.getNodeMode() == WorkflowActionDb.NODE_MODE_ROLE || wad.getNodeMode() == WorkflowActionDb.NODE_MODE_ROLE_SELECTED) {
                String[] prearyrole = StrUtil.split(wad.getJobCode(), ",");
                int prelen = 0;
                if (prearyrole != null) {
                    prelen = prearyrole.length;
                }
                for (int m = 0; m < prelen; m++) {
                    RoleDb rd = new RoleDb();
                    rd = rd.getRoleDb(prearyrole[m]);
                    if (rd != null) {
                        if (rOrder < rd.getOrders()) {
                            rOrder = rd.getOrders();
                        }
                    }
                }
            } else {
                String jobCode = wad.getJobCode();
                if (jobCode.equals(WorkflowActionDb.PRE_TYPE_SELF)) {
                    // 如果限定了部门
                    if (!"".equals(wad.getDept())) {
                        rOrder = -999;
                    } else {
                        rOrder = -1000;
                    }
                } else {
                    // 如果预先定义的是当前用户
                    waStart = wad;
                    break;
                }
            }

            // 取得较大角色的那个节点
            if (orderStart < rOrder) {
                waStart = wad;
                orderStart = rOrder;
            }
        }

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "createFromString 匹配開始節點", (System.currentTimeMillis() - t) + " ms");
        }

        // 如果流程尚未开始，而动作节点为开始节点，则将节点的状态置为正处理状态
        if (waStart!=null) {
            // 創建開始節點
            waStart.create();

            WorkflowInitThread workflowInitThread = new WorkflowInitThread(this, vStart, waStart, actionMap, linkList);
            workflowInitThread.start();
            /*List<WorkflowActionDb> actionsToIgnore = new ArrayList<>();
            for (WorkflowActionDb wd : vStart) {
                if (!wd.equals(waStart)) {
                    // 忽略无法被匹配的起点分支
                    WorkflowMgr wm = new WorkflowMgr();
                    wm.ignoreBranch(wd, null, actionsToIgnore);
                }
            }
            wachk.ignoreActions(this, actionsToIgnore);*/

            if (Global.getInstance().isDebug()) {
                DebugUtil.i(getClass(), "createFromString ignoreActions", (System.currentTimeMillis() - t) + " ms");
            }

        	if (!isStarted()) {
                // 动作状态出现改变，需要renewWorkflowString
        		waStart.setStatus(WorkflowActionDb.STATE_DOING);
        		waStart.setIsStart(1);
        		waStart.save();

                WorkflowCacheMgr wcm = new WorkflowCacheMgr();
                wcm.refreshCreate();
                
                // 给发起者创建一条待办记录
                starterMyActionId = notifyUser(userName, new java.util.Date(), -1, null, waStart, WorkflowActionDb.STATE_DOING, id).getId();

                startActionId = waStart.getId();
        	}
        }

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "createFromString notifyUser", (System.currentTimeMillis() - t) + " ms");
        }

        if (startActionId!=-1) {
            WorkflowActionDb startAction = wachk.getWorkflowActionDb(startActionId);
            renewWorkflowString(startAction, false);
        }
        else {
            if ("".equals(errMsgStarter)) {
                if (vStartMatched.size() == 0) {
                    errMsgStarter = "未匹配到开始节点";
                }
            }
        	throw new ErrMsgException(errMsgStarter);
        }

        if (Global.getInstance().isDebug()) {
            DebugUtil.i(getClass(), "createFromString renewWorkflowString", (System.currentTimeMillis() - t) + " ms");
        }

        return starterMyActionId;
    }

    public static cn.js.fan.mail.SendMail getSendMail() {
        cn.js.fan.mail.SendMail sendmail = new cn.js.fan.mail.SendMail();
        Config cfg = new Config();
        boolean flowNotifyByEmail = cfg.getBooleanProperty("flowNotifyByEmail");
        if (flowNotifyByEmail) {
            String mailserver = Global.getSmtpServer();
            int smtp_port = Global.getSmtpPort();
            String name = Global.getSmtpUser();
            String pwd_raw = Global.getSmtpPwd();
            try {
                sendmail.initSession(mailserver, smtp_port, name,
                                     pwd_raw);
            } catch (Exception ex) {
                LogUtil.getLog(WorkflowDb.class).error(StrUtil.trace(ex));
            }
        }
        return sendmail;
    }

    public void sendNotifyMsgAndEmail(HttpServletRequest request, MyActionDb mad, cn.js.fan.mail.SendMail sendmail) {
        UserMgr um = new UserMgr();
        Config cfg = new Config();

        boolean flowNotifyByEmail = cfg.getBooleanProperty("flowNotifyByEmail");
        String senderName = StrUtil.GBToUnicode(Global.AppName);
        senderName += "<" + Global.getEmail() + ">";

        com.redmoon.oa.sso.Config ssoCfg = new com.redmoon.oa.sso.Config();
        String t = SkinUtil.LoadString(request,
                                       "res.module.flow",
                                       "msg_user_actived_title");
        String c = SkinUtil.LoadString(request,
                                       "res.module.flow",
                                       "msg_user_actived_content");
        String tail = WorkflowMgr.getFormAbstractTable(this);

        MessageDb md = new MessageDb();

        t = t.replaceFirst("\\$flowTitle", getTitle());
        String fc = c.replaceFirst("\\$flowTitle", getTitle());
        
        // 防止用户已被删除
        UserDb ud = um.getUserDb(mad.getUserName());
        if (!ud.isLoaded()) {
        	return;
        }
        
        fc = fc.replaceFirst("\\$fromUser", ud.getRealName());
        if (SMSFactory.isUseSMS()) {
            IMsgUtil imu = SMSFactory.getMsgUtil();
            if (imu != null) {
                try {
                    imu.send(ud, fc, MessageDb.SENDER_SYSTEM);
                } catch (ErrMsgException ex1) {
                    ex1.printStackTrace();
                }
            }
        }

        fc += tail;

        // 发送信息
        String action = "action=" + MessageDb.ACTION_FLOW_DISPOSE + "|myActionId=" + mad.getId();
        try {
            md.sendSysMsg(mad.getUserName(), t, fc, action);
        } catch (ErrMsgException ex2) {
            ex2.printStackTrace();
        }

        if (flowNotifyByEmail) {
            UserDb user = um.getUserDb(mad.getUserName());
            if (!"".equals(user.getEmail())) {
                action = "userName=" + user.getName() + "|" +
                         "myActionId=" + mad.getId();
                action = cn.js.fan.security.ThreeDesUtil.encrypt2hex(
                        ssoCfg.getKey(), action);
                fc += "<BR />>>&nbsp;<a href='" +
                        WorkflowUtil.getJumpUrl(WorkflowUtil.OP_FLOW_PROCESS, action) +
                        "' target='_blank'>请点击此处办理</a>";
                sendmail.initMsg(user.getEmail(),
                                 senderName,
                                 t, fc, true);
                sendmail.send();
                sendmail.clear();
            }
        }
    }

    public MyActionDb notifyUser(String userName, java.util.Date receiveDate, long privMyActionId, WorkflowActionDb privAction, WorkflowActionDb actionToDo, int actionStatus, long flowId) throws ErrMsgException {
        return notifyUser(userName, receiveDate, privMyActionId, privAction, actionToDo, actionStatus, flowId, MyActionDb.SUB_MYACTION_ID_NONE);
    }

    /**
     * 给用户发一条待办记录
     * @param userName String 用户名
     * @param receiveDate Date 创建待办记录的时间
     * @param privMyActionId long 转交人的myActionId
     * @param privAction WorkflowActionDb 待办节点的上一节点，当发起流程时，privAction=null
     * @param actionToDo WorkflowActionDb 下一节点动作
     * @param actionStatus int 动作状态
     * @param flowId long 流程号
     * @param subMyActionId long 子流程中对应的myActionId
     * @return boolean
     */
    public MyActionDb notifyUser(String userName, java.util.Date receiveDate, long privMyActionId, WorkflowActionDb privAction, WorkflowActionDb actionToDo, int actionStatus, long flowId, long subMyActionId) throws ErrMsgException {
        if (MyActionDb.isNotifyExist(flowId, userName, privMyActionId)) {
            // 考虑到流程自动存档时需重定向，而此处如果抛出异常，则页面将不再重定向
            // 有可能是在条件匹配时，多个分支线上均匹配到了某用户，而该用户都被选择了时，就会出现重复notifUser的情况
            LogUtil.getLog(getClass()).error("流程已被转交，请勿重复操作！" + StrUtil.trace(new Exception()));
            // throw new ErrMsgException("流程已被转交，请勿重复操作！");
        }

        String proxy = UserProxyMgr.getProxy(userName, (int)flowId);
        LogUtil.getLog(getClass()).info("proxy=" + proxy + " userName=" + userName);

        DeptUserDb du = new DeptUserDb();
        Iterator ir = du.getDeptsOfUser(userName).iterator();
        String deptCodes = "";
        while (ir.hasNext()) {
            DeptDb dd = (DeptDb)ir.next();
            if (deptCodes.equals("")) {
                deptCodes = dd.getCode();
            } else {
                deptCodes += "," + dd.getCode();
            }
        }

        MyActionDb mad = new MyActionDb();
        int myActionId = MyActionDb.isActionWaitExist(actionToDo.getId(), userName);
        if (myActionId != 0) {
        	mad = mad.getMyActionDb(myActionId);
        }
        mad.setReceiveDate(receiveDate);
        mad.setActionStatus(actionStatus);
        mad.setProxyUserName(proxy);
        mad.setDeptCodes(deptCodes);
        mad.setSubMyActionId(subMyActionId);

        // 当不为起始节点时
        if (privAction!=null) {
            // 置到期时间
            WorkflowLinkDb wld = new WorkflowLinkDb();
            wld = wld.getWorkflowLinkDbForward(privAction, actionToDo);
            if (wld != null) {
                LogUtil.getLog(getClass()).info("notifyUser link id=" + wld.getId() + " privAction:" + privAction.getInternalName() + " actionToDo:" + actionToDo.getInternalName());
                if (wld.getExpireHour()!=0) {
                    mad.setExpireDate(wld.calulateExpireDate());
                }
            }
        }
        if (myActionId == 0) {
            mad.setUserName(userName);
            mad.setFlowId(flowId);
            mad.setActionId(actionToDo.getId());
            mad.setPrivMyActionId(privMyActionId);
            mad.create();
        } else {
        	// 可能已存在待办记录，状态为：CHECK_STATUS_WAITING_TO_DO
        	mad.setCheckStatus(MyActionDb.CHECK_STATUS_NOT);
            mad.save();
        }

        // 发至日程安排中
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb((int)flowId);
        if (wf.getStatus()!=WorkflowDb.STATUS_NONE) {
	        Config cfg = new Config();
	        String flowActionPlan = cfg.get("flowActionPlan");
	        String flowActionPlanContent = cfg.get("flowActionPlanContent");
	        PlanDb pd = new PlanDb();
	        pd.setTitle(StrUtil.format(flowActionPlan, new Object[]{getTitle().replace("$", "\\$")}));
	        UserMgr um = new UserMgr();
	        UserDb user = um.getUserDb(getUserName());
	        String cont = StrUtil.format(flowActionPlanContent, new Object[]{user.getRealName(), um.getUserDb(mad.getUserName()).getRealName()});
	        pd.setContent(cont);
	        pd.setMyDate(new java.util.Date());
	        pd.setEndDate(new java.util.Date());
	        pd.setActionData("" + mad.getId());
	        pd.setActionType(PlanDb.ACTION_TYPE_FLOW);
	        pd.setUserName(mad.getUserName());
	        pd.setRemind(false);
	        pd.setRemindBySMS(false);
	        pd.setRemindDate(new java.util.Date());
	        pd.create();
        }

        return mad;
    }

    public int getStartActionId() {
        String sql = "select id from flow_action where flow_id=? and isStart=1";
        Conn conn = new Conn(connname);
        ResultSet rs = null;
        try {
            //置开始项为完成状态
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, id);
            rs = conn.executePreQuery();
            if (rs != null) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LogUtil.getLog(getClass()).error(e.getMessage());
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (Exception e) {}
                rs = null;
            }
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }
        return -1;
    }

    public boolean checkActions() throws ErrMsgException {
        WorkflowActionDb wad = new WorkflowActionDb();
        Vector v = wad.listActionsOfFlow(id);
        Iterator ir = v.iterator();
        WorkflowActionChecker wac = new WorkflowActionChecker();
        while (ir.hasNext()) {
            wad = (WorkflowActionDb)ir.next();
            if (!wac.check(wad)) {
                throw new ErrMsgException(wac.getErrMsg());
            }
        }
        return true;
    }

    public boolean start(HttpServletRequest request, String userName, FileUpload fu, WorkflowActionDb wa, long myActionId) throws ErrMsgException {
        Leaf lf = new Leaf();
        lf = lf.getLeaf(typeCode);
        if (lf.getType()==Leaf.TYPE_LIST) {
            // 对流程中的每个动作进行合法性检查
            checkActions();

            int startid = getStartActionId();
            if (startid == -1) {
                LogUtil.getLog(getClass()).error("start:未找到流程中的开始动作！startId=-1");
                return false;
            }

            status = STATUS_STARTED;
            beginDate = new java.util.Date();
            save();

            // WorkflowActionMgr wam = new WorkflowActionMgr();
            // WorkflowActionDb wa = wam.getWorkflowActionDb(startid);

            int resultValue = 0;
            if (wa.changeStatus(request, this, userName, WorkflowActionDb.STATE_FINISHED, "",
                                "", resultValue, myActionId)) {
                return true;
            } else {
                LogUtil.getLog(getClass()).error("start:更新action状态时出错！");
                return false;
            }
        }
        else {
            // 开始自由流程
            status = STATUS_STARTED;
            beginDate = new java.util.Date();
            save();

            int resultValue = 0;

            return wa.changeStatusFree(request, this, userName, WorkflowActionDb.STATE_FINISHED, "",
                                "", resultValue, myActionId);
        }
    }

    /**
     * 当载入预定义流程时，更新其中的$self节点，同时检查发起人是否符合start所限定的角色或人员
     * @param curUserName String
     * @param predefinedStr String
     * @return boolean
     * @throws ErrMsgException
     */
    public String regeneratePredefinedFlowString(String curUserName, String predefinedStr) throws ErrMsgException {
        flowString = predefinedStr;
        if (!predefinedStr.startsWith("paper")) {
            return predefinedStr;
        }
        String[] ary = predefinedStr.split("\\r\\n");
        int len = ary.length;
        for (int i = 1; i < len; i++) {
            WorkflowActionDb wa = new WorkflowActionDb();
            boolean re = wa.fromString(ary[i], false);
            if (re) { // 如果是action，而不是link
                String jobCode = wa.getJobCode();
                if (jobCode.equals(WorkflowActionDb.PRE_TYPE_SELF)) {
                	// checkSelf();
                }
                else {
                    // if (wa.isStart==1) {
                        // 检查发起人是否合法，因增加多起点功能，所以注释掉 20160924
                    	// checkStarter(wa, curUserName);
                    // }
                }
            }
        }

        return flowString;
    }
    
    public boolean checkStarterSelf(WorkflowActionDb wa, String curUserName) throws ErrMsgException { 
        boolean isValidDept = true;
        // 检查用户所在部门是否合法
        String[] deptary = StrUtil.split(wa.getDept(), ",");
        String deptNames = "";
        if (deptary != null) {
            DeptMgr dm = new DeptMgr();
            isValidDept = false;
            int deptlen = deptary.length;
            DeptUserDb du = new DeptUserDb();
            for (int k = 0; k < deptlen; k++) {
                DeptDb dd = dm.getDeptDb(deptary[k]);
                if (dd==null || !dd.isLoaded()) {
                    continue;
                }
                if (deptNames.equals("")) {
                    deptNames = dd.getName();
                } else {
                    deptNames += "，" + dd.getName();
                }
                if (du.isUserOfDept(curUserName, deptary[k])) {
                    isValidDept = true;
                    break;
                }
            }
        }
        if (!isValidDept) {
            throw new ErrMsgException("发起人所在部门不符合发起条件！合法的部门是：" + deptNames);
        }

        UserDb ud = new UserDb();
        ud = ud.getUserDb(curUserName);
        wa.setUserName(curUserName);
        wa.setUserRealName(ud.getRealName());
        wa.setNodeMode(WorkflowActionDb.NODE_MODE_USER_SELECTED);
        renewWorkflowString(wa, false);   
        return true;
    }
    
    /**
     * 检查发起人是否合法
     * @Description: 
     * @param wa
     * @param curUserName
     * @throws ErrMsgException
     */
    public boolean checkStarter(WorkflowActionDb wa, String curUserName) throws ErrMsgException {    
    	 if (wa.getNodeMode()==WorkflowActionDb.NODE_MODE_ROLE) {
             UserDb ud = new UserDb();
             ud = ud.getUserDb(curUserName);
             RoleDb[] aryrole = ud.getRoles();
             int rolelen = 0;
             if (aryrole!=null) {
                 rolelen = aryrole.length;
             }

             String[] prearyrole = StrUtil.split(wa.getJobCode(), ",");
             int prelen = 0;
             if (prearyrole!=null) {
                 prelen = prearyrole.length;
             }
             boolean isValidRole = false;
             // 检查角色是否合法
             for (int m=0; m<prelen; m++) {
                 for (int n=0; n<rolelen; n++) {
                     if (prearyrole[m].equals(aryrole[n].getCode())) {
                         isValidRole = true;
                         break;
                     }
                 }
                 if (isValidRole) {
                     break;
                 }
             }
             if (!isValidRole) {
            	 /*20180127如果多起点，就乱了，所以不允許admin可以发起任意流程
            	 Leaf lf = new Leaf();
            	 lf = lf.getLeaf(typeCode);
            	 // 调试模式下admin可以发起任意流程
            	 if (lf.isDebug() && curUserName.equals(UserDb.ADMIN)) {
            		 isValidRole = true;
            	 }
            	 */
             }
             if (!isValidRole) {
                 throw new ErrMsgException("发起人角色非法！只有 " + wa.getJobName() + " 才能发起！");
             }             
             boolean isValidDept = true;
             // 检查用户所在部门是否合法
             String[] deptary = StrUtil.split(wa.getDept().trim(), ",");
             if (deptary != null) {
                 isValidDept = false;
                 DeptUserDb du = new DeptUserDb();
                 for (String deptCode : deptary) {
                     if (du.isUserOfDept(curUserName, deptCode)) {
                         isValidDept = true;
                         break;
                     }
                 }
             }
             if (!isValidDept) {
                 throw new ErrMsgException("发起人部门非法！");
             }
             wa.setUserName(curUserName);
             wa.setUserRealName(ud.getRealName());
             wa.setNodeMode(WorkflowActionDb.NODE_MODE_ROLE_SELECTED);
             renewWorkflowString(wa, false);
         }
    	 else if (wa.getNodeMode()==WorkflowActionDb.NODE_MODE_POST) {
             // 判断用户是否为此岗位
             String[] postIds = StrUtil.split(wa.getJobCode(), ",");
             boolean isValid = false;
             if (postIds != null) {
                 IPostUserService postUserService = SpringUtil.getBean(IPostUserService.class);
                 for (String id : postIds) {
                     int postId = StrUtil.toInt(id, -1);
                     PostUser postUser = postUserService.getPostUser(curUserName, postId);
                     if (postUser != null) {
                         isValid = true;
                         break;
                     }
                 }
             }
             if (!isValid) {
                 throw new ErrMsgException("发起人职位非法！只有 " + wa.getJobName() + " 才能发起！");
             }

             UserDb ud = new UserDb();
             ud = ud.getUserDb(curUserName);
             wa.setUserName(curUserName);
             wa.setUserRealName(ud.getRealName());
             wa.setNodeMode(WorkflowActionDb.NODE_MODE_ROLE_SELECTED);
             renewWorkflowString(wa, false);
         }
    	 else {
             String[] aryuser = StrUtil.split(wa.getJobCode(), ",");
             int aryuserlen = 0;
             if (aryuser!=null) {
                 aryuserlen = aryuser.length;
             }
             boolean isValid = false;
             for (int m=0; m<aryuserlen; m++) {
                 if (aryuser[m].equals(curUserName)) {
                     isValid = true;
                     break;
                 }
             }
             if (!isValid) {
                 throw new ErrMsgException("发起人非法！只有 " + wa.getJobName() + " 才能发起！");
             }
             else {
                 UserDb ud = new UserDb();
                 ud = ud.getUserDb(curUserName);
                 wa.setUserName(curUserName);
                 wa.setUserRealName(ud.getRealName());
                 wa.setNodeMode(WorkflowActionDb.NODE_MODE_USER_SELECTED);
                 renewWorkflowString(wa, false);
             }
         }    	
    	 return true;
    }
    
    /**
     * 更新流程
     * @Description: 
     * @throws ErrMsgException
     */
    public void refreshFlow() throws ErrMsgException {
    	Leaf lf = new Leaf();
    	lf = lf.getLeaf(getTypeCode());
    	if (lf==null) {
    	    DebugUtil.e(getClass(), "refreshFlow", getTitle() + " 流程类型：" + getTypeCode() + " 已不存在");
    	    return;
        }

        WorkflowPredefineDb wpd = new WorkflowPredefineDb();
        wpd = wpd.getDefaultPredefineFlow(getTypeCode());

        java.util.Vector<WorkflowActionDb> v = getActionsFromString(wpd.getFlowString());
        for (WorkflowActionDb wa : v) {
            WorkflowActionDb wad = wa.getWorkflowActionDbByInternalName(wa.getInternalName(), id);
            if (wad == null) {
                // 跳过新建的节点
                continue;
            }
            wad.setTitle(wa.getTitle());
            wad.setJobCode(wa.getJobCode());
            wad.setJobName(wa.getJobName());
            wad.setRelateRoleToOrganization(wa.isRelateRoleToOrganization());
            wad.setFieldWrite(wa.getFieldWrite());
            wad.setFlag(wa.getFlag());
            wad.setNodeMode(wa.getNodeMode());
            wad.setDept(wa.getDept());
            wad.setItem1(wa.getItem1());
            wad.setDirection(wa.getDirection());
            wad.setItem2(wa.getItem2());
            wad.setMsg(wa.isMsg());
            wad.setStrategy(wa.getStrategy());
            wad.save();
        }

        java.util.Vector<WorkflowLinkDb> vl = getLinksFromString(wpd.getFlowString());
        for (WorkflowLinkDb wl : vl) {
            WorkflowLinkDb wld = wl.getWorkflowLinkDb(id, wl.getFrom(), wl.getTo());
            if (wld == null) {
                // 跳过新建的连线
                continue;
            }
            wld.setExpireHour(wl.getExpireHour());
            wld.setExpireAction(wl.getExpireAction());
            wld.setCondDesc(wl.getCondDesc());
            wld.setCondType(wl.getCondType());
            wld.setIsSpeedup(wl.getIsSpeedup());
            wld.setSpeedupDate(wl.getSpeedupDate());
            wld.setTitle(wl.getTitle());
            wld.save();
        }

        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(id);
        // 向下兼容
        if (wf.getFormArchiveId() == ConstUtil.FORM_ARCHIVE_NONE) {
            FormDb fd = new FormDb();
            fd = fd.getFormDb(lf.getFormCode());
            DocContent docContent = wf.getDocument().getDocContent(1);
            docContent.setContent(fd.getContent());
            docContent.save();
        } else {
            FormArchiveService formArchiveService = SpringUtil.getBean(FormArchiveService.class);
            IFormDAO fdao = formArchiveService.getCurFormArchiveOrInit(lf.getFormCode());
            wf.setFormArchiveId(fdao.getId());
            wf.save();
        }
    }

    /**
     * 当动作节点出现更改后（如设置代理或状态改变时），重新生成flowString
     * @param wad WorkflowActionDb
     * @param isSaveFlow boolean
     * @return boolean
     * @throws ErrMsgException
     */
    public synchronized boolean renewWorkflowString(WorkflowActionDb wad, boolean isSaveFlow) throws
            ErrMsgException {
        IWorkflowHelper workflowHelper = SpringUtil.getBean(IWorkflowHelper.class);
        return workflowHelper.renewWorkflowString(this, wad, isSaveFlow);
    }

    public String tranString(String str) {
        if (str == null) {
            return "";
        }
        str = str.replaceAll("\\:", "\\\\colon");
        str = str.replaceAll("\\;", "\\\\semicolon");
        str = str.replaceAll("\\,", "\\\\comma");
        str = str.replaceAll("\\r\\n", "\\\\newline");
        return str;
    }

    public WorkflowBlockIterator getWorkflows(String query, String groupKey,
                                              int startIndex,
                                              int endIndex) {
        if (!SecurityUtil.isValidSql(query)) {
            return null;
        }
        //可能取得的infoBlock中的元素的顺序号小于endIndex
        long[] docBlock = getWorkflowBlock(query, groupKey, startIndex);

        return new WorkflowBlockIterator(docBlock, query, groupKey,
                                         startIndex, endIndex);
    }

    protected long[] getWorkflowBlock(String query, String groupKey,
                                      int startIndex) {
        WorkflowCacheMgr wfc = new WorkflowCacheMgr();
        return wfc.getWorkflowBlock(query, WorkflowCacheMgr.FLOW_GROUP_KEY, startIndex);
    }

    public boolean isUserAttended(String userName) {
        MyActionDb mad = new MyActionDb();
        return mad.isUserAttendFlow(id, userName);
    }

/*
    public ListResult listUserAttended(String userName, int curPage, int pageSize) throws
            ErrMsgException {
        int total = 0;
        ResultSet rs = null;
        Vector result = new Vector();
        ListResult lr = new ListResult();
        lr.setResult(result);
        lr.setTotal(total);
        Conn conn = new Conn(connname);
        // 在OADS中oracle中因使用了distinct，所以where中必须有flow_id
        // String sql = "select distinct flow_id from flow_action where userName=? or proxyUserName=? order by mydate desc";
        String sql = "select distinct flow_id from flow_my_action where user_name=? or proxy=? order by flow_id desc";

        try {
            // 取得总记录条数
            String countsql = "select count(distinct flow_id) from flow_my_action where user_name=? or proxy=?"; // SQLFilter.getCountSql(sql);
            // 取得总记录条数
            PreparedStatement ps = conn.prepareStatement(countsql);
            ps.setString(1, userName);
            ps.setString(2, userName);

            rs = conn.executePreQuery();
            if (rs != null && rs.next()) {
                total = rs.getInt(1);
            }
            if (rs != null) {
                rs.close();
                rs = null;
            }

            if (ps!=null) {
                ps.close();
                ps = null;
            }

            // 防止受到攻击时，curPage被置为很大，或者很小
            int totalpages = (int) Math.ceil((double) total / pageSize);
            if (curPage > totalpages)
                curPage = totalpages;
            if (curPage <= 0)
                curPage = 1;

            ps = conn.prepareStatement(sql);
            ps.setString(1, userName);
            ps.setString(2, userName);

            if (total != 0)
                conn.setMaxRows(curPage * pageSize); // 尽量减少内存的使用

            rs = conn.executePreQuery();
            if (rs == null) {
                return lr;
            } else {
                WorkflowDb wd;
                rs.setFetchSize(pageSize);
                int absoluteLocation = pageSize * (curPage - 1) + 1;
                if (rs.absolute(absoluteLocation) == false) {
                    return lr;
                }
                do {
                    wd = getWorkflowDb(rs.getInt(1));
                    result.addElement(wd);
                } while (rs.next());
            }
        } catch (SQLException e) {
            LogUtil.getLog(getClass()).error("listUserAttended:" + e.getMessage());
            throw new ErrMsgException("数据库出错！");
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (Exception e) {}
                rs = null;
            }
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }

        lr.setResult(result);
        lr.setTotal(total);
        return lr;
    }
 */

    public ListResult listMonitored(String userName, int curPage, int pageSize) throws
            ErrMsgException {
        int total = 0;
        ResultSet rs = null;
        Vector result = new Vector();
        ListResult lr = new ListResult();
        lr.setResult(result);
        lr.setTotal(total);
        String sql =
                "select flowId from flow_monitor where userName=? order by flowCreateDate desc";
        // 取得总记录条数
        String countsql = "select count(flowId) from flow_monitor where userName=?"; // SQLFilter.getCountSql(sql);
        Conn conn = new Conn(connname);
        try {
            // 取得总记录条数
            PreparedStatement ps = conn.prepareStatement(countsql);
            ps.setString(1, userName);

            rs = conn.executePreQuery();
            if (rs != null && rs.next()) {
                total = rs.getInt(1);
            }
            if (rs != null) {
                rs.close();
                rs = null;
            }

            if (ps!=null) {
                ps.close();
                ps = null;
            }
            // 防止受到攻击时，curPage被置为很大，或者很小
            int totalpages = (int) Math.ceil((double) total / pageSize);
            if (curPage > totalpages)
                curPage = totalpages;
            if (curPage <= 0)
                curPage = 1;

            ps = conn.prepareStatement(sql);
            ps.setString(1, userName);

            if (total != 0)
                conn.setMaxRows(curPage * pageSize); // 尽量减少内存的使用

            rs = conn.executePreQuery();
            if (rs == null) {
                return lr;
            } else {
                WorkflowDb wd;
                rs.setFetchSize(pageSize);
                int absoluteLocation = pageSize * (curPage - 1) + 1;
                if (rs.absolute(absoluteLocation) == false) {
                    return lr;
                }
                do {
                    wd = getWorkflowDb(rs.getInt(1));
                    result.addElement(wd);
                } while (rs.next());
            }
        } catch (SQLException e) {
            LogUtil.getLog(getClass()).error("listMonitored:" + e.getMessage());
            throw new ErrMsgException("数据库出错！");
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (Exception e) {}
                rs = null;
            }
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }

        lr.setResult(result);
        lr.setTotal(total);
        return lr;
    }

    /**
     * 取出全部信息置于result中
     */
    public Vector<WorkflowDb> list(String sql) {
        ResultSet rs = null;
        Conn conn = new Conn(connname);
        Vector result = new Vector();
        try {
            rs = conn.executeQuery(sql);
            if (rs == null) {
                return null;
            } else {
                while (rs.next()) {
                    result.addElement(getWorkflowDb(rs.getInt(1)));
                }
            }
        } catch (SQLException e) {
            LogUtil.getLog(getClass()).error("list:" + e.getMessage());
        } finally {
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }
        return result;
    }

    public ListResult listResult(String sql, int curPage, int pageSize) throws
            ErrMsgException {
        int total = 0;
        ResultSet rs = null;
        Vector result = new Vector();
        ListResult lr = new ListResult();
        lr.setResult(result);
        lr.setTotal(total);
        Conn conn = new Conn(connname);
        try {
            // 取得总记录条数
            String countsql = SQLFilter.getCountSql(sql);
            // 取得总记录条数
            PreparedStatement ps = conn.prepareStatement(countsql);
            rs = conn.executePreQuery();
            if (rs != null && rs.next()) {
                total = rs.getInt(1);
            }
            if (rs != null) {
                rs.close();
                rs = null;
            }

            if (ps!=null) {
                ps.close();
                ps = null;
            }

            // 防止受到攻击时，curPage被置为很大，或者很小
            int totalpages = (int) Math.ceil((double) total / pageSize);
            if (curPage > totalpages)
                curPage = totalpages;
            if (curPage <= 0)
                curPage = 1;

            // long t = new java.util.Date().getTime();
            ps = conn.prepareStatement(sql);

            if (total != 0) {
                conn.setMaxRows(curPage * pageSize); // 尽量减少内存的使用
            }

            rs = conn.executePreQuery();
            // DebugUtil.i(getClass(), "listResult", "时长：" + (new java.util.Date().getTime() - t) + "毫秒 sql=" + sql);

            if (rs == null) {
                return lr;
            } else {
                WorkflowDb wd;
                rs.setFetchSize(pageSize);
                int absoluteLocation = pageSize * (curPage - 1) + 1;
                if (rs.absolute(absoluteLocation) == false) {
                    return lr;
                }
                do {
                    // t = new java.util.Date().getTime();
                    wd = getWorkflowDb(rs.getInt(1));
                    // DebugUtil.i(getClass(), "listResult", "getWorkflowDb 时长：" + (new java.util.Date().getTime() - t) + "毫秒");

                    result.addElement(wd);
                } while (rs.next());
            }
        } catch (SQLException e) {
            LogUtil.getLog(getClass()).error("listResult:" + e.getMessage());
            throw new ErrMsgException("数据库出错！");
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (Exception e) {}
                rs = null;
            }
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }

        lr.setResult(result);
        lr.setTotal(total);
        return lr;
    }

    /**
     * 取得待办流程的条数
     * @param userName String
     * @return int
     */
    public static int getWaitCount(String userName) {
        //  String sql = "select count(m.id) from flow_my_action m, flow f where m.flow_id=f.id and f.status<>" + WorkflowDb.STATUS_NONE + " and (user_name=" + StrUtil.sqlstr(userName) + " or proxy=" + StrUtil.sqlstr(userName) + ") and (is_checked=0 or is_checked=2) and sub_my_action_id=" + MyActionDb.SUB_MYACTION_ID_NONE;
    	String sql = "select count(m.id) from flow_my_action m, flow f where m.flow_id=f.id and f.status<>" + WorkflowDb.STATUS_NONE + " and (user_name=" + StrUtil.sqlstr(userName);

        Config cfg = Config.getInstance();
        boolean isFlowProxy = cfg.getBooleanProperty("isFlowProxy");
        if (isFlowProxy) {
    	    sql += " or proxy=" + StrUtil.sqlstr(userName);
        }
    	sql += ") and (is_checked=0 or is_checked=2) and sub_my_action_id=" + MyActionDb.SUB_MYACTION_ID_NONE;
    	sql += " and f.status<>" + WorkflowDb.STATUS_DELETED + " and f.status<>" + WorkflowDb.STATUS_DISCARDED;
    	JdbcTemplate jt = new JdbcTemplate();
        ResultIterator ri = null;
        try {
            ri = jt.executeQuery(sql);
            if (ri.hasNext()) {
                ResultRecord rr = (ResultRecord) ri.next();
                return rr.getInt(1);
            }
        } catch (SQLException ex) {
            LogUtil.getLog(WorkflowDb.class).error(ex);
        }
        return 0;
    }

    /**
     *
     * @param sql String
     * @return int -1 表示sql语句不合法
     */
    public int getWorkflowCount(String sql) {
        WorkflowCacheMgr dcm = new WorkflowCacheMgr();
        return dcm.getWorkflowCount(sql);
    }

    public int getWorkflowCountOfType(String typeCode) {
        String sql = "select id from flow where type_code=" + StrUtil.sqlstr(typeCode);
        return getWorkflowCount(sql);
    }

    /**
     * 只要有一个结束型节点完成，则判定整个流程为已完成
     * @return boolean
     */
    public boolean checkEndActionsStatusFinished() {
        WorkflowActionDb wad = new WorkflowActionDb();
        Vector v = wad.getEndActionsOfFlow(id);
        Iterator ir = v.iterator();
        while (ir.hasNext()) {
            wad = (WorkflowActionDb) ir.next();
            /*
            if (wad.getStatus() == wad.STATE_DOING ||
                wad.getStatus() == wad.STATE_NOTDO ||
                wad.getStatus() == wad.STATE_RETURN) {
                // return false;
            }
            */
            // 20180127 fgf 加入如果爲被忽略的狀態
            if (wad.getStatus() == WorkflowActionDb.STATE_FINISHED) {// || wad.getStatus()==WorkflowActionDb.STATE_IGNORED) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查流程中的各个节点是否都已结束
     * @return boolean 如果有正在办的、未办的、被打回的则判定为未结束，否则判定为结束
     */
    public boolean checkStatusFinished() {
        Vector v = getActions();
        Iterator ir = v.iterator();
        while (ir.hasNext()) {
            WorkflowActionDb wad = (WorkflowActionDb)ir.next();
            if (wad.getStatus()==WorkflowActionDb.STATE_DOING || wad.getStatus()==WorkflowActionDb.STATE_NOTDO || wad.getStatus()==WorkflowActionDb.STATE_RETURN) {
                return false;
            }
        }
        return true;
    }

    /**
     * 取得流程中的所有action节点
     * @return Vector
     */
    public Vector<WorkflowActionDb> getActions() {
        WorkflowActionDb wad = new WorkflowActionDb();
        return wad.getActionsOfFlow(id);
    }
    
    /**
     * 取得流程中用户名为user的动作
     * @param user String
     * @return WorkflowActionDb
     */
    public WorkflowActionDb getWorkflowDbOfUser(String user) {
        String sql =
                "select id from flow_action where flow_id=? and username=?";
        // Based on the id in the object, get the message data from the database.
        Conn conn = new Conn(connname);
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, id);
            pstmt.setString(2, user);
            rs = conn.executePreQuery();
            if (!rs.next()) {
                LogUtil.getLog(getClass()).error("流程动作flow_id= " + id + " username=" + user +
                             " 在数据库中未找到.");
            } else {
                int actionId = rs.getInt(1);
                WorkflowActionDb wa = new WorkflowActionDb();
                return wa.getWorkflowActionDb(actionId);
            }
        } catch (SQLException e) {
            LogUtil.getLog(getClass()).error(e.getMessage());
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (Exception e) {}
                rs = null;
            }
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }
        return new WorkflowActionDb();
    }

    /**
     * 拟文
     * @return boolean
     * @throws ErrMsgException
     */
    public int writeDocument(String creator, FileUpload TheBean) throws
            ErrMsgException {

        boolean re = false;

        FormDAO fdao = new FormDAO();

        Vector v = TheBean.getFiles();
        if (v.size()==0)
            return -1;
        FileInfo fi = (FileInfo)v.get(0);

        IFileService fileService = SpringUtil.getBean(IFileService.class);
        fileService.write(fi, fdao.getVisualPath());

        if (fi.getExt().equals("wps"))
            fi.setDiskName(fi.getDiskName() + ".wps");
        else if (fi.getExt().equals("docx"))
            fi.setDiskName(fi.getDiskName() + ".docx");
        else
            fi.setDiskName(fi.getDiskName() + ".doc");

        String lockUser = StrUtil.getNullStr(TheBean.getFieldValue("lockUser"));

        Attachment att = new Attachment();
        att.setDocId(docId);
        // att.setName(fi.getName()); // WebOffice控件不支持utf-8
        att.setName(getTitle() + ".doc");
        att.setDiskName(fi.getDiskName());
        att.setVisualPath(fdao.getVisualPath());
        att.setPageNum(1);
        att.setCreator(creator);
        att.setLockUser(lockUser);

        Document doc = new Document();
        doc = doc.getDocument(docId);
        int orders = doc.getAttachments(1).size() + 1;

        att.setOrders(orders);
        att.setFieldName("");

        if (att.create()) {
            return (int)att.getId();
        } else {
            return -1;
        }
    }

    /**
     * 审批文件时用以处理上传
     * @param TheBean FileUploadBean
     * @return boolean
     * @throws ErrMsgException
     */
    public boolean uploadDocument(FileUpload TheBean) throws
            ErrMsgException {
        String strdocId = StrUtil.getNullStr(TheBean.getFieldValue("doc_id"));
        String strfileId = StrUtil.getNullStr(TheBean.getFieldValue("file_id"));

        int docId = Integer.parseInt(strdocId);
        int fileId = Integer.parseInt(strfileId);
        
        // @task:可能应该把templateId字段放在附件表中
        int templateId = StrUtil.toInt(TheBean.getFieldValue("templateId"), -1);

        DocumentMgr dm = new DocumentMgr();
        Document doc = dm.getDocument(docId);
        Attachment att = doc.getAttachment(1, fileId);
        if (att == null) {
            throw new ErrMsgException("取文件" + docId + "的附件" + fileId + "时，未找到！");
        }

        Vector<FileInfo> v = TheBean.getFiles();
        if (v.size()==0) {
            return false;
        }
        FileInfo fi = v.get(0);

        att.setSize(fi.getSize());
        boolean re = att.save();
        if (re) {        	
        	// 保存模板类型
        	doc.setTemplateId(templateId);
        	doc.updateTemplateId();
            return fi.writeToPath(Global.getRealPath() + "/" + att.getVisualPath() + "/" + att.getDiskName());
        }
        else {
        	return false;
        }
    }

    /**
     * 判断流程是否已开始，如未开始，则可以修改流程的开始项，及流程信息
     * @return boolean
     */
    public boolean isStarted() {
        if (true) {
            return status > STATUS_NOT_STARTED;
        }

        String sql =
                "select status from flow_action where flow_id=? and isStart=1";
        Conn conn = new Conn(connname);
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, id);
            rs = conn.executePreQuery();
            if (!rs.next()) {
                LogUtil.getLog(getClass()).info("流程id= " + id +
                            " 的开始项在数据库中未找到.");
            } else {
                int status = rs.getInt(1);
                if (status == WorkflowActionDb.STATE_FINISHED)
                    return true;
            }
        } catch (SQLException e) {
            LogUtil.getLog(getClass()).error("isStarted:" + e.getMessage());
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (Exception e) {}
                rs = null;
            }
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }
        return false;
    }

    public int delWorkflowDbOfType(String typeCode) {
        Vector v = new Vector();
        String sql = "select id from flow where type_code=?";
        // Based on the id in the object, get the message data from the database.
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        int i = 0;
        Conn conn = new Conn(connname);
        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, typeCode);
            rs = conn.executePreQuery();
            while (rs.next()) {
                WorkflowDb wd = getWorkflowDb(rs.getInt(1));
                try {
                    wd.del();
                }
                catch (ErrMsgException e) {
                    LogUtil.getLog(getClass()).error("delWorkflowDbOfType:" + e.getMessage());
                }
                i++;
            }
        } catch (SQLException e) {
            LogUtil.getLog(getClass()).error("load:" + e.getMessage());
        } finally {
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }
        return i;
    }

    public void del() throws ErrMsgException {
        // 删除其所有的动作和连接
        delActionsAndLinks();
        // 删除其所有的文档
        DocumentMgr dm = new DocumentMgr();
        Document doc = dm.getDocument(docId);
        // 防止文档已不存在的情况下，删除垃圾数据时出错
        if (doc!=null && doc.isLoaded()) {
            doc.del();
        }
        // 删除其表单记录
        FormDb fd = new FormDb();
        Leaf lf = new Leaf();
        lf = lf.getLeaf(typeCode);
        if (lf!=null) {
            fd = fd.getFormDb(lf.getFormCode());
            // 防止表单已不存在的情况下，删除垃圾数据时出错
            if (fd != null && fd.isLoaded()) {
                FormDAO fdao = new FormDAO();
                fdao = fdao.getFormDAO(id, fd);
                fdao.del();
            }
        }

        // 删除myAction
        MyActionDb mad = new MyActionDb();
        mad.delMyActionOfFlow(id);

        // 删除流程图片
        com.redmoon.oa.Config cfg = new com.redmoon.oa.Config();
        boolean isGenerateFlowImage = cfg.getBooleanProperty("isGenerateFlowImage");
        if (isGenerateFlowImage) {
            String filePath = Global.getRealPath() + getImgVisualPath() + "/" + id + ".jpg";
            File f = new File(filePath);
            if (f.exists()) {
                f.delete();
            }
        }

        Conn conn = new Conn(connname);
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn.beginTrans();
            pstmt = conn.prepareStatement(DELETE);
            pstmt.setInt(1, id);
            int r = pstmt.executeUpdate();
            if (r == 1) {
                // 删除流程监控人员
                if (pstmt!=null) {
                    pstmt.close();
                    pstmt = null;
                }
                pstmt = conn.prepareStatement("delete from flow_monitor where flowId=? and userName=?");
                pstmt.setInt(1, id);
                pstmt.setString(2, userName);
                r = pstmt.executeUpdate();
                if (r == 1){
                	 //删除关注流程
                    if (pstmt!=null) {
                        pstmt.close();
                        pstmt = null;
                    }
                    pstmt = conn.prepareStatement("delete from flow_favorite where flow_id=?");
                    pstmt.setInt(1, id);
                    pstmt.executeUpdate();
                }
                conn.executePreUpdate();
                conn.commit();

                // 更新缓存
                WorkflowCacheMgr wfc = new WorkflowCacheMgr();
                wfc.refreshDel(id);
            }
        } catch (SQLException e) {
            conn.rollback();
            LogUtil.getLog(getClass()).error("del:" + e.getMessage());
        } finally {
            if (pstmt!=null) {
                try {
                    pstmt.close();
                }
                catch (Exception e) {}
                pstmt = null;
            }
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }

    }

    public void delActionsAndLinks() {
        String sqlaction = "select id from flow_action where flow_id=?";
        String sqllink = "select id from flow_link where flow_id=?";
        Conn conn = new Conn(connname);
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = conn.prepareStatement(sqlaction);
            pstmt.setInt(1, id);
            rs = pstmt.executeQuery();
            if (rs != null) {
                WorkflowActionMgr wam = new WorkflowActionMgr();
                while (rs.next()) {
                    WorkflowActionDb wa = wam.getWorkflowActionDb(rs.getInt(1));
                    wa.del();
                }
                rs.close();
                rs = null;
            }
            pstmt.close();
            pstmt = null;
            pstmt = conn.prepareStatement(sqllink);
            pstmt.setInt(1, id);
            rs = pstmt.executeQuery();
            if (rs != null) {
                WorkflowLinkMgr wlm = new WorkflowLinkMgr();
                while (rs.next()) {
                    WorkflowLinkDb wl = wlm.getWorkflowLinkDb(rs.getInt(1));
                    wl.del();
                }
                rs.close();
                rs = null;
            }
        } catch (SQLException e) {
            LogUtil.getLog(getClass()).error(e.getMessage());
            return;
        } finally {
            if (rs!=null) {
                try { rs.close(); } catch (SQLException e) {}
                rs = null;
            }
            if (conn != null) {
                conn.close();
                conn = null;
            }
        }
    }
/*
    public boolean modifyFlowString(String newstring) throws ErrMsgException {
        setFlowString(newstring);
        save();
        delActionsAndLinks();
        return createFromString(newstring);
    }
*/

public void delLinks() {
    String sqllink = "select id from flow_link where flow_id=?";
    Conn conn = new Conn(connname);
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try {
        pstmt = conn.prepareStatement(sqllink);
        pstmt.setInt(1, id);
        rs = pstmt.executeQuery();
        if (rs != null) {
            WorkflowLinkMgr wlm = new WorkflowLinkMgr();
            while (rs.next()) {
                WorkflowLinkDb wl = wlm.getWorkflowLinkDb(rs.getInt(1));
                wl.del();
            }
        }
    } catch (SQLException e) {
        LogUtil.getLog(getClass()).error(e.getMessage());
    } finally {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {}
            rs = null;
        }
        if (conn != null) {
            conn.close();
            conn = null;
        }
    }
}

/**
 * 修改流程字符串，如果解析后检查到节点已存在，则不处理，否则增加，但是所有的link则全部删除重建
 * 如果解析后检查到节点已不存在，则将节点删除
 * @task:此处需修改，以免每次更新流程string时，致连接线被删除重建，使连接线的ID增长太快，而应该增量修改
 * @param newstring String
 * @return boolean
 * @throws ErrMsgException
 */
public boolean modifyFlowString(HttpServletRequest request, String newstring) throws ErrMsgException {
        if (!newstring.startsWith("paper")) {
            return false;
        }
        // 删除所有的连接
        delLinks();

        // 用以记录修改后流程中的所有节点
        Vector was = new Vector();

        Vector newAddActions = new Vector();
        // 获取流程中原来的所有节点
        WorkflowActionDb wa1 = new WorkflowActionDb();
        Vector oldwas = wa1.listActionsOfFlow(id);

        String[] ary = newstring.split("\\r\\n");
        int len = ary.length;
        for (int i = 1; i < len; i++) {
            WorkflowActionDb wa = new WorkflowActionDb();
            wa.setFlowId(id);
            boolean re = wa.fromString(ary[i], true);
            // 如果流程尚未开始，而动作节点为开始节点，则将节点的状态置为正处理状态
            if (!isStarted()) {
                if (re && wa.isStart == 1) {
                    wa.setStatus(wa.STATE_DOING);
                    WorkflowCacheMgr wcm = new WorkflowCacheMgr();
                    wcm.refreshCreate();
                }
            }
            if (re) {
                was.addElement(wa);

                // 检查节点是否已存在，如果存在则修改其信息为新的节点的信息，如果不存在，则创建节点
                // 注意：在控件中设置属性时，需检查该节点的状态是否为已处理，否则不可更改其属性
                WorkflowActionDb oldwa = new WorkflowActionDb();
                oldwa = oldwa.getWorkflowActionDbByInternalName(wa.getInternalName(), id);
                if (oldwa==null) {
                    boolean r = wa.create();
                    newAddActions.addElement(wa); // 记录下新加节点
                    if (!r)
                        LogUtil.getLog(getClass()).error("modifyFlowString:从字符串创建动作失败！");
                }
                else {
                    // 节点已存在，则不作处理，以免丢失数据库中存储的taskID等信息，这些信息在flowString中并未保存
                    // 检查新、老节点的jobCode是否一致
                    if (oldwa.getStatus()!=wa.getStatus() || !oldwa.getUserName().equals(wa.getUserName()) || !oldwa.getFieldWrite().equals(wa.getFieldWrite()) || !oldwa.getRankCode().equals(wa.getRankCode()) || !oldwa.getTitle().equals(wa.getTitle()) || oldwa.getDirection()!=wa.getDirection() || !oldwa.getFlag().equals(wa.getFlag()) || !oldwa.getDept().equals(wa.getDept()) || oldwa.getNodeMode()!=wa.getNodeMode()) {
                        oldwa.setTitle(wa.getTitle());
                        oldwa.setUserName(wa.getUserName());
                        oldwa.setDirection(wa.getDirection());
                        oldwa.setUserRealName(wa.getUserRealName());
                        oldwa.setRankCode(wa.getRankCode());
                        oldwa.setOfficeColorIndex(wa.getOfficeColorIndex());
                        oldwa.setFieldWrite(wa.getFieldWrite());
                        oldwa.setDept(wa.getDept());
                        oldwa.setFlag(wa.getFlag());
                        oldwa.setNodeMode(wa.getNodeMode());
                        oldwa.setStatus(wa.getStatus());
                        oldwa.save();
                    }
                }
            }
            else {
                WorkflowLinkDb wl = new WorkflowLinkDb();
                wl.setFlowId(id);
                if (wl.fromString(ary[i])) {
                    boolean r = wl.create();
                    if (!r) {
                        LogUtil.getLog(getClass()).error("modifyFlowString:从字符串创建连接失败！");
                    }
                }
            }
        }

        // 检查是否有需要删除的节点，以节点的internalname为依据
        int oldlen = oldwas.size();
        int newlen = was.size();
        for (int i=0; i<oldlen; i++) {
            boolean isFounded = false;
            WorkflowActionDb oldwa = (WorkflowActionDb)oldwas.get(i);
            for (int j=0; j<newlen; j++) {
                WorkflowActionDb newwa = (WorkflowActionDb)was.get(j);
                if (newwa.getInternalName().equals(oldwa.getInternalName())) {
                    isFounded = true;
                    break;
                }
            }
            if (!isFounded) {
                oldwa.del();
            }
        }

        // 保存flowString
        setFlowString(newstring);
        // 如果新加入了节点，而流程原来的状态为已完成，则置状态为已开始
        if (newAddActions.size() > 0) {
            if (this.getStatus() == STATUS_FINISHED) {
                this.status = STATUS_STARTED;
            }
        }

        boolean returnBack = ParamUtil.getBoolean(request, "returnBack", false);
        setReturnBack(returnBack);
        boolean re = save();

        if (re) {
            Iterator newir = newAddActions.iterator();
            while (newir.hasNext()) {
                WorkflowActionDb wa = (WorkflowActionDb) newir.next();
                // 检查新加入的节点，在其之前相邻的节点是否为都已完成，如果是则置其为正处理状态，如果在其之后的节点为正处理，则置其之后的节点为未处理
                Iterator ir = wa.getLinkFromActions().iterator();
                boolean isPrivActionsFinished = true;
                WorkflowActionDb w1 = null;
                while (ir.hasNext()) {
                    w1 = (WorkflowActionDb) ir.next();
                    if (w1.getStatus() != w1.STATE_FINISHED &&
                        w1.getStatus() != w1.STATE_IGNORED) {
                        isPrivActionsFinished = false;
                        break;
                    }
                }
                if (isPrivActionsFinished) {
                    // 激活新加入的节点
                    w1.changeStatus(request, this, w1.getCheckUserName(),
                                    w1.getStatus(), w1.getReason(),
                                    w1.getResult(), w1.getResultValue(), -1);
                    boolean isUseMsg = ParamUtil.getBoolean(request,
                            "isUseMsg", false);
                    isUseMsg = true;

                    Iterator ir1 = w1.getTmpUserNameActived().iterator();
                    while (ir1.hasNext()) {
                        MyActionDb mad = (MyActionDb) ir1.next();
                        if (isUseMsg) {
                            // 发送信息
                            MessageDb md = new MessageDb();
                            String t = SkinUtil.LoadString(request,
                                    "res.module.flow",
                                    "msg_user_actived_title");
                            String c = SkinUtil.LoadString(request,
                                    "res.module.flow",
                                    "msg_user_actived_content");
                            t = t.replaceFirst("\\$flowTitle", getTitle());
                            c = c.replaceFirst("\\$flowTitle", getTitle());
                            c = c.replaceFirst("\\$fromUser",
                                               w1.getUserName());
                            md.sendSysMsg(mad.getUserName(), t, c);
                        }
                    }
                }

                ir = wa.getLinkToActions().iterator();
                while (ir.hasNext()) {
                    w1 = (WorkflowActionDb) ir.next();
                    if (w1.getStatus() == w1.STATE_DOING || w1.getStatus()==w1.STATE_FINISHED) {
                        // 新加入节点之后的节点的状态为正处理或已完成，则将其置为未处理
                        w1.setStatus(w1.STATE_NOTDO);
                        w1.save();
                    }
                }
            }

        }
        return re;
    }

    /**
     * 当在flow_dispose.jsp中选择了角色中的用户、异或选择分支时，更新数据库中action的用户名、是否被忽略等信息
     * @param request HttpServletRequest
     * @param newstring String
     * @return boolean
     * @throws ErrMsgException
     */
    public boolean modifyDBActionByFlowString(HttpServletRequest request, String newstring) throws ErrMsgException {
            if (!newstring.startsWith("paper"))
                return false;

            String[] ary = newstring.split("\\r\\n");
            int len = ary.length;
            for (int i = 1; i < len; i++) {
                WorkflowActionDb wa = new WorkflowActionDb();
                wa.setFlowId(id);
                boolean re = wa.fromString(ary[i], true);
                // 如果流程尚未开始，而动作节点为开始节点，则将节点的状态置为正处理状态
                if (!isStarted()) {
                    if (re && wa.isStart == 1) {
                        wa.setStatus(wa.STATE_DOING);
                        WorkflowCacheMgr wcm = new WorkflowCacheMgr();
                        wcm.refreshCreate();
                    }
                }
                // 如果是action
                if (re) {
                    // 从数据库中取出节点
                    WorkflowActionDb wa_db = new WorkflowActionDb();
                    wa_db = wa_db.getWorkflowActionDbByInternalName(wa.getInternalName(), id);
                    if (wa_db==null) {
                        LogUtil.getLog(getClass()).error("modifyActionByFlowString:在数据库中未找到action " + wa.getJobName() + "！");
                    }
                    else {
                        // 对比节点，如果更新了用户名
                        boolean needSave = false;
                        if (!wa_db.getUserName().equals(wa.getUserName())) {
                            needSave = true;
                        }
                        // 如果当原来wa_db的状态为未处理，而后来状态变为忽略时，说明用户在异或节点上选择下一分支
                        if (wa_db.getStatus()==WorkflowActionDb.STATE_NOTDO && wa.getStatus()==WorkflowActionDb.STATE_IGNORED) {
                            needSave = true;
                        }
                        // 如果当原来wa_db的状态为忽略，而后来状态变为未处理时，说明用户也是在异或节点选择下一分支
                        if (wa_db.getStatus()==WorkflowActionDb.STATE_IGNORED && wa.getStatus()==WorkflowActionDb.STATE_NOTDO) {
                            needSave = true;
                        }
                        // 如果原来状态为已完成，后来又改为被忽略，则说明是被打回了，在经过前一节点（异或发散条件）时，走了另外一个条件分支
                        if (wa_db.getStatus()==WorkflowActionDb.STATE_FINISHED && wa.getStatus()==WorkflowActionDb.STATE_IGNORED) {
                            needSave = true;
                        }
                        // 自选节点选择了新用户
                        if (wa_db.getJobCode().equals(WorkflowActionDb.PRE_TYPE_USER_SELECT) || wa_db.getJobCode().equals(WorkflowActionDb.PRE_TYPE_USER_SELECT_IN_ADMIN_DEPT)) {
                            if (!wa.getJobCode().equals(wa_db.getJobCode())) {
                                wa_db.setJobCode(wa.getJobCode());
                                wa_db.setJobName(wa.getJobName());
                                wa_db.setStrategy(wa.getStrategy());
                                wa_db.setTitle(wa.getTitle());
                                needSave = true;
                            }
                        }
                        if ( needSave ) {
                            wa_db.setUserName(wa.getUserName());
                            wa_db.setUserRealName(wa.getUserRealName());
                            wa_db.setNodeMode(wa.getNodeMode());

                            wa_db.setStatus(wa.getStatus());
                            wa_db.save();
                        }
                    }
                }
            }

            return true;
    }

    /*
    public static String getStatusDesc(int status) {
            String str = "";
            if (status==STATUS_NOT_STARTED)
                str = "未提交";
            else if (status == STATUS_STARTED)
                str = "处理中";
            else if (status==STATUS_FINISHED)
                str = "已结束";
            else if (status==STATUS_DISCARDED)
                str = "已放弃";
            return str;
    }
    */

    public static String getStatusDesc(int status) {
        String r;
        WorkflowConfig wfcfg = WorkflowConfig.getInstance();
        switch(status) {
          case STATUS_NOT_STARTED: r = wfcfg.getProperty("STATUS_NOT_STARTED"); break;
          case STATUS_STARTED: r = wfcfg.getProperty("STATUS_STARTED"); break;
          case STATUS_FINISHED: r = wfcfg.getProperty("STATUS_FINISHED"); break;
          case STATUS_DISCARDED: r = wfcfg.getProperty("STATUS_DISCARDED"); break;
          case STATUS_REFUSED: r = wfcfg.getProperty("STATUS_REFUSED"); break;
          case STATUS_NONE: r = wfcfg.getProperty("STATUS_NONE"); break;
          case STATUS_DELETED: r = wfcfg.getProperty("STATUS_DELETED"); break;
          default: r = wfcfg.getProperty("STATE_NOTDO");
        }
        return r;
    }

    /**
     * 取得状态CSS样式
     * @param status int
     * @return String
     */
    public static String getStatusClass(int status) {
        String r;
        switch( status) {
          case STATUS_NOT_STARTED: r = "STATUS_NOT_STARTED"; break;
          case STATUS_STARTED: r = "STATUS_STARTED"; break;
          case STATUS_FINISHED: r = "STATUS_FINISHED"; break;
          case STATUS_DISCARDED: r = "STATUS_DISCARDED"; break;
          case STATUS_REFUSED: r = "STATUS_REFUSED"; break;
          default: r = "STATE_NOTDO";
        }
        return r;
    }

    public String getStatusDesc() {
        String str = getStatusDesc(status);
        if (status==STATUS_FINISHED) {
            MyActionDb mad = new MyActionDb();
            mad = mad.getLastMyActionDbOfFlow(id);
            if (mad!=null) {
	            if (mad.getResultValue() == WorkflowActionDb.RESULT_VALUE_DISAGGREE) {
	                str += "(" +
	                        WorkflowActionDb.getResultValueDesc(WorkflowActionDb.
	                        RESULT_VALUE_DISAGGREE) + ")";
	            } else {
	                str += "(" +
	                        WorkflowActionDb.getResultValueDesc(WorkflowActionDb.
	                        RESULT_VALUE_AGGREE) + ")";
	            }
            }
        }
        return str;
    }

    public String getSqlDoing(HttpServletRequest request) {
        Privilege privilege = new Privilege();
        String op = ParamUtil.get(request, "op");
        String typeCode = ParamUtil.get(request, "typeCode");
        String myname = ParamUtil.get(request, "userName"); // userName为指定的人员
        if ("".equals(myname)) {
            myname = privilege.getUser(request);
        }
        String title = ParamUtil.getParam(request, "title");
        String starter = ParamUtil.getParam(request, "starter");
        String by = ParamUtil.get(request, "by");
        String fromDate = ParamUtil.get(request, "fromDate");
        String toDate = ParamUtil.get(request, "toDate");
        String orderBy = ParamUtil.get(request, "field");
        int actionStatus = ParamUtil.getInt(request, "actionStatus", -1);
        boolean hasFormCol = false;
        if ("".equals(orderBy)) {
            orderBy = "m.receive_date";
        } else {
            if ("m.receive_date".equals(orderBy) || "f.id".equals(orderBy)
                    || "f.flow_level".equals(orderBy) || "f.title".equals(orderBy) || "f.type_code".equals(orderBy)
                    || "f.userName".equals(orderBy) || "f.mydate".equals(orderBy) || "f.status".equals(orderBy) || "f.begin_date".equals(orderBy)) {
                ;
            } else {
                orderBy = "t1." + orderBy;
                hasFormCol = true;
            }
        }
        String sort = ParamUtil.get(request, "order");
        if ("ascend".equals(sort)) {
            sort = "asc";
        }
        else if ("descend".equals(sort)) {
            sort = "desc";
        }
        if ("".equals(sort)) {
            sort = "desc";
        }

        String formConds = "";
        String formTable = "";
        if (!"".equals(typeCode) && !Leaf.CODE_ROOT.equals(typeCode)) {
            Leaf leaf = new Leaf();
            leaf = leaf.getLeaf(typeCode);
            FormDb fd = new FormDb();
            fd = fd.getFormDb(leaf.getFormCode());
            formTable = FormDb.getTableName(fd.getCode());
            formConds = getConds(request, leaf, fd);
        }

        Config cfg = Config.getInstance();
        boolean isFlowProxy = cfg.getBooleanProperty("isFlowProxy");

        String sql;
        if ("".equals(formConds) && !hasFormCol) {
            if (!"".equals(starter)) {
                sql = "select m.id from flow_my_action m, flow f, users u where m.flow_id=f.id and f.userName=u.name and f.status<>" + WorkflowDb.STATUS_NONE + " and m.user_name=" + StrUtil.sqlstr(myname) + " and (is_checked=0 or is_checked=2) and sub_my_action_id=" + MyActionDb.SUB_MYACTION_ID_NONE;
            }
            else {
                sql = "select m.id from flow_my_action m, flow f where m.flow_id=f.id and f.status<>" + WorkflowDb.STATUS_NONE + " and m.user_name=" + StrUtil.sqlstr(myname) + " and (is_checked=0 or is_checked=2) and sub_my_action_id=" + MyActionDb.SUB_MYACTION_ID_NONE;
            }
        }
        else {
            if (!"".equals(starter)) {
                sql = "select m.id from flow_my_action m, flow f, users u, " + formTable + " t1 where f.id=t1.flowId and m.flow_id=f.id and f.userName=u.name and f.status<>" + WorkflowDb.STATUS_NONE + " and m.user_name=" + StrUtil.sqlstr(myname) + " and (is_checked=0 or is_checked=2) and sub_my_action_id=" + MyActionDb.SUB_MYACTION_ID_NONE;
            }
            else {
                sql = "select m.id from flow_my_action m, flow f, " + formTable + " t1 where f.id=t1.flowId and m.flow_id=f.id and f.status<>" + WorkflowDb.STATUS_NONE + " and m.user_name=" + StrUtil.sqlstr(myname) + " and (is_checked=0 or is_checked=2) and sub_my_action_id=" + MyActionDb.SUB_MYACTION_ID_NONE;
            }
        }

        if (!"".equals(typeCode) && !Leaf.CODE_ROOT.equals(typeCode)) {
            sql += " and f.type_code=" + StrUtil.sqlstr(typeCode);
        }

        if ("search".equals(op)) {
            if ("title".equals(by)) {
                if (!"".equals(title)) {
                    sql += " and f.title like " + StrUtil.sqlstr("%" + title + "%");
                }
            } else if ("flowId".equals(by)) {
                if (!StrUtil.isNumeric(title)) {
                    String str = LocalUtil.LoadString(request, "res.flow.Flow", "mustNumber");
                    LogUtil.getLog(getClass()).error("getSqlDoing:" + str);
                } else {
                    sql += " and f.id=" + title;
                }
            }
            if (!"".equals(fromDate)) {
                sql += " and f.BEGIN_DATE>=" + SQLFilter.getDateStr(fromDate, "yyyy-MM-dd");
            }
            if (!"".equals(toDate)) {
                java.util.Date d = DateUtil.parse(toDate, "yyyy-MM-dd");
                d = DateUtil.addDate(d, 1);
                String toDate2 = DateUtil.format(d, "yyyy-MM-dd");
                sql += " and f.BEGIN_DATE<" + SQLFilter.getDateStr(toDate2, "yyyy-MM-dd");
            }
            if (!"".equals(starter)) {
                sql += " and u.realname like " + StrUtil.sqlstr("%" + starter + "%");
            }
            if (actionStatus!=-1) {
                sql += " and m.action_status=" + actionStatus;
            }

            if (!"".equals(formConds)) {
                sql += " and " + formConds;
            }
        }

        sql += " and f.status<>" + WorkflowDb.STATUS_DELETED + " and f.status<>" + WorkflowDb.STATUS_DISCARDED;

        if (isFlowProxy) {
            String sqlProxy = sql.replace("m.user_name=" + StrUtil.sqlstr(myname), "m.proxy=" + StrUtil.sqlstr(myname));
            sql = "select * from (" + sql + ") tb1 union select * from (" + sqlProxy + ") tb2";
            int p = orderBy.indexOf(".");
            if (p!=-1) {
                orderBy = orderBy.substring(p + 1);
            }
            sql += " order by " + orderBy + " " + sort;
        }
        else {
            sql += " order by " + orderBy + " " + sort;
        }
        return sql;
    }

    /**
     * 我参与的流程
     * @param request
     * @return
     */
    public String getSqlAttend(HttpServletRequest request) {
        Privilege privilege = new Privilege();
        String myname = ParamUtil.get(request, "userName"); // userName为指定的人员
        if("".equals(myname)) {
            myname = privilege.getUser(request);
        }
        String op = ParamUtil.get(request, "op");
        String typeCode = ParamUtil.get(request, "typeCode");
        String starter = ParamUtil.getParam(request, "starter"); // 发起人
        String by = ParamUtil.get(request, "by");
        String title = ParamUtil.getParam(request, "title");
        String fromDate = ParamUtil.get(request, "fromDate");
        String toDate = ParamUtil.get(request, "toDate");
        int status = ParamUtil.getInt(request, "status", 1000);

        String orderBy = ParamUtil.get(request, "field");
        boolean hasFormCol = false;
        if ("".equals(orderBy)) {
            orderBy = "m.id";
        } else {
            if ("f.id".equals(orderBy)
                    || "f.flow_level".equals(orderBy) || "f.title".equals(orderBy) || "f.type_code".equals(orderBy)
                    || "f.userName".equals(orderBy) || "f.mydate".equals(orderBy) || "f.status".equals(orderBy) || "f.begin_date".equals(orderBy) || "f.end_date".equals(orderBy)) {
                ;
            } else {
                orderBy = "t1." + orderBy;
                hasFormCol = true;
            }
        }
        String sort = ParamUtil.get(request, "order");
        if ("ascend".equals(sort)) {
            sort = "asc";
        }
        else if ("descend".equals(sort)) {
            sort = "desc";
        }
        if ("".equals(sort)) {
            sort = "desc";
        }

        String formTable = "";
        String formConds = "";

        if (!"".equals(typeCode)) {
            Leaf leaf = new Leaf();
            leaf = leaf.getLeaf(typeCode);
            FormDb fd = new FormDb();
            fd = fd.getFormDb(leaf.getFormCode());
            formTable = FormDb.getTableName(fd.getCode());
            formConds = getConds(request, leaf, fd);
        }

        Config cfg = Config.getInstance();
        // 是否启用代理
        boolean isFlowProxy = cfg.getBooleanProperty("isFlowProxy");

        String sql;
        if (Global.db.equals(Global.DB_MYSQL)) {
            if ("".equals(formConds) && !hasFormCol) {
                if (!"".equals(starter)) {
                    sql = "select distinct f.id from flow_my_action m force index (user_checked), flow f, users u where m.flow_id=f.id and f.userName=u.name and m.user_name=" + StrUtil.sqlstr(myname) + " and f.status>" + WorkflowDb.STATUS_NONE + " and m.is_checked<" + MyActionDb.CHECK_STATUS_WAITING_TO_DO; // 等待前一节点结束
                } else {
                    sql = "select distinct f.id from flow_my_action m force index (user_checked), flow f where m.flow_id=f.id and m.user_name=" + StrUtil.sqlstr(myname) + " and f.status>" + WorkflowDb.STATUS_NONE + " and m.is_checked<" + MyActionDb.CHECK_STATUS_WAITING_TO_DO; // 等待前一节点结束
                }
            } else {
                if (!"".equals(starter)) {
                    sql = "select distinct f.id from flow_my_action m force index (user_checked), flow f, users u, " + formTable + " t1 where f.id=t1.flowId and f.userName=u.name and m.flow_id=f.id and m.user_name=" + StrUtil.sqlstr(myname) + " and f.status>" + WorkflowDb.STATUS_NONE + " and m.is_checked<" + MyActionDb.CHECK_STATUS_WAITING_TO_DO; // 等待前一节点结束
                } else {
                    sql = "select distinct f.id from flow_my_action m force index (user_checked), flow f, " + formTable + " t1 where f.id=t1.flowId and m.flow_id=f.id and m.user_name=" + StrUtil.sqlstr(myname) + " and f.status>" + WorkflowDb.STATUS_NONE + " and m.is_checked<" + MyActionDb.CHECK_STATUS_WAITING_TO_DO; // 等待前一节点结束
                }
            }
        }
        else {
            if ("".equals(formConds) && !hasFormCol) {
                if (!"".equals(starter)) {
                    sql = "select distinct f.id from flow_my_action m, flow f, users u where m.flow_id=f.id and f.userName=u.name and m.user_name=" + StrUtil.sqlstr(myname) + " and f.status>" + WorkflowDb.STATUS_NONE + " and m.is_checked<" + MyActionDb.CHECK_STATUS_WAITING_TO_DO; // 等待前一节点结束
                } else {
                    sql = "select distinct f.id from flow_my_action m, flow f where m.flow_id=f.id and m.user_name=" + StrUtil.sqlstr(myname) + " and f.status>" + WorkflowDb.STATUS_NONE + " and m.is_checked<" + MyActionDb.CHECK_STATUS_WAITING_TO_DO; // 等待前一节点结束
                }
            } else {
                if (!"".equals(starter)) {
                    sql = "select distinct f.id from flow_my_action m, flow f, users u, " + formTable + " t1 where f.id=t1.flowId and f.userName=u.name and m.flow_id=f.id and m.user_name=" + StrUtil.sqlstr(myname) + " and f.status>" + WorkflowDb.STATUS_NONE + " and m.is_checked<" + MyActionDb.CHECK_STATUS_WAITING_TO_DO; // 等待前一节点结束
                } else {
                    sql = "select distinct f.id from flow_my_action m, flow f, " + formTable + " t1 where f.id=t1.flowId and m.flow_id=f.id and m.user_name=" + StrUtil.sqlstr(myname) + " and f.status>" + WorkflowDb.STATUS_NONE + " and m.is_checked<" + MyActionDb.CHECK_STATUS_WAITING_TO_DO; // 等待前一节点结束
                }
            }
        }

        if (!"".equals(typeCode)) {
            sql += " and f.type_code=" + StrUtil.sqlstr(typeCode);
        }

        if ("search".equals(op)) {
            if (!"".equals(title)) {
                if ("flowId".equals(by)) {
                    sql += " and f.id = " + StrUtil.sqlstr(title);
                }
                else {
                    sql += " and f.title like " + StrUtil.sqlstr("%" + title + "%");
                }
            }

            if (!"".equals(fromDate) && !"".equals(toDate)) {
                java.util.Date d = DateUtil.parse(toDate, "yyyy-MM-dd");
                d = DateUtil.addDate(d, 1);
                String toDate2 = DateUtil.format(d, "yyyy-MM-dd");
                sql += " and (f.BEGIN_DATE>=" + SQLFilter.getDateStr(fromDate, "yyyy-MM-dd") + " and f.BEGIN_DATE<" + SQLFilter.getDateStr(toDate2, "yyyy-MM-dd") + ")";
            } else if (!"".equals(fromDate)) {
                sql += " and f.BEGIN_DATE>=" + SQLFilter.getDateStr(fromDate, "yyyy-MM-dd");
            } else if (fromDate.equals("") && !"".equals(toDate)) {
                sql += " and f.BEGIN_DATE<=" + SQLFilter.getDateStr(toDate, "yyyy-MM-dd");
            }

            if (status != 1000) {
                sql += " and f.status=" + status;
            }

            if (!"".equals(starter)) {
                sql += " and u.realname like " + StrUtil.sqlstr("%" + starter + "%");
            }

            if (!"".equals(formConds)) {
                sql += " and " + formConds;
            }
        }

        // 已通过 f.status>WorkflowDb.STATUS_NONE排除掉
        // 过滤掉已删除的
        // sql += " and f.status<>" + WorkflowDb.STATUS_DELETED;

        // 在select语句中加入orderBy字段，加入m.id会导致出现重复记录
        if (!"f.id".equals(orderBy) && !"id".equals(orderBy) && !"m.id".equals(orderBy)) {
            int p = sql.indexOf(" f.id ");
            String prefix = sql.substring(0, p);
            String suffix = sql.substring(p + " f.id ".length());
            sql = prefix + " f.id," + orderBy + " " + suffix;
        }

        if (isFlowProxy) {
            String sqlProxy = sql.replace("m.user_name=" + StrUtil.sqlstr(myname), "m.proxy=" + StrUtil.sqlstr(myname));
            sql = "select * from (" + sql + ") tb1 union select * from (" + sqlProxy + ") tb2";
            int p = orderBy.indexOf(".");
            if (p!=-1) {
                orderBy = orderBy.substring(p + 1);
            }
            sql += " order by " + orderBy + " " + sort;
        }
        else {
            sql += " order by " + orderBy + " " + sort;
        }
        return sql;
    }

    public String getSqlFavorite(HttpServletRequest request) {
        Privilege privilege = new Privilege();
        String myname = ParamUtil.get(request, "userName"); // userName为指定的人员
        if("".equals(myname)) {
            myname = privilege.getUser(request);
        }
        String op = ParamUtil.get(request, "op");
        String typeCode = ParamUtil.get(request, "typeCode");
        String starter = ParamUtil.getParam(request, "starter"); // 发起人
        String by = ParamUtil.get(request, "by");
        String title = ParamUtil.getParam(request, "title");
        String fromDate = ParamUtil.get(request, "fromDate");
        String toDate = ParamUtil.get(request, "toDate");
        int status = ParamUtil.getInt(request, "status", 1000);

        String orderBy = ParamUtil.get(request, "orderBy");
        boolean hasFormCol = false;
        if ("".equals(orderBy)) {
            orderBy = "v.flow_id";
        } else {
            if ("v.flow_id".equals(orderBy) || "id".equals(orderBy)
                    || "flow_level".equals(orderBy) || "title".equals(orderBy) || "type_code".equals(orderBy)
                    || "userName".equals(orderBy) || "mydate".equals(orderBy) || "status".equals(orderBy) || "begin_date".equals(orderBy))
                ;
            else {
                orderBy = "t1." + orderBy;
                hasFormCol = true;
            }
        }
        String sort = ParamUtil.get(request, "sort");
        if ("".equals(sort)) {
            sort = "desc";
        }

        String formTable = "";
        String formConds = "";
        if (!"".equals(typeCode)) {
            Leaf leaf = new Leaf();
            leaf = leaf.getLeaf(typeCode);
            FormDb fd = new FormDb();
            fd = fd.getFormDb(leaf.getFormCode());
            formTable = FormDb.getTableName(fd.getCode());
            formConds = getConds(request, leaf, fd);
        }

        String sql;
        if ("".equals(formConds) && !hasFormCol) {
            if (!"".equals(starter)) {
                sql = "select flow_id from flow_favorite v,flow f,users u where f.userName=u.name and v.user_name=" + StrUtil.sqlstr(myname) + " and v.flow_id=f.id and f.status<>" + WorkflowDb.STATUS_NONE;
            } else {
                sql = "select flow_id from flow_favorite v,flow f where v.user_name=" + StrUtil.sqlstr(myname) + " and v.flow_id=f.id and f.status<>" + WorkflowDb.STATUS_NONE;
            }
        }
        else {
            if (!"".equals(starter)) {
                sql = "select flow_id from flow_favorite v,flow f,users u," + formTable + " t1 where f.id=t1.flowId and f.userName=u.name and v.user_name=" + StrUtil.sqlstr(myname) + " and v.flow_id=f.id and f.status<>" + WorkflowDb.STATUS_NONE;
            } else {
                sql = "select flow_id from flow_favorite v,flow f," + formTable + " t1 where f.id=t1.flowId and v.user_name=" + StrUtil.sqlstr(myname) + " and v.flow_id=f.id and f.status<>" + WorkflowDb.STATUS_NONE;
            }
        }

        if (!"".equals(typeCode)) {
            sql += " and f.type_code=" + StrUtil.sqlstr(typeCode);
        }

        if ("search".equals(op)) {
            if (!"".equals(title)) {
                if ("flowId".equals(by)) {
                    sql += " and f.id = " + StrUtil.sqlstr(title);
                }
                else {
                    sql += " and f.title like " + StrUtil.sqlstr("%" + title + "%");
                }
            }

            if (!"".equals(fromDate) && !"".equals(toDate)) {
                java.util.Date d = DateUtil.parse(toDate, "yyyy-MM-dd");
                d = DateUtil.addDate(d, 1);
                String toDate2 = DateUtil.format(d, "yyyy-MM-dd");
                sql += " and (f.BEGIN_DATE>=" + SQLFilter.getDateStr(fromDate, "yyyy-MM-dd") + " and f.BEGIN_DATE<" + SQLFilter.getDateStr(toDate2, "yyyy-MM-dd") + ")";
            } else if (!"".equals(fromDate)) {
                sql += " and f.BEGIN_DATE>=" + SQLFilter.getDateStr(fromDate, "yyyy-MM-dd");
            } else if (!"".equals(toDate)) {
                sql += " and f.BEGIN_DATE<=" + SQLFilter.getDateStr(toDate, "yyyy-MM-dd");
            }

            if (status != 1000) {
                sql += " and f.status=" + status;
            }
            else {
                sql += " and f.status<>" + WorkflowDb.STATUS_DELETED;
            }

            if (!"".equals(formConds)) {
                sql += " and " + formConds;
            }
        }
        if (!sql.contains("f.status")) {
            sql += " and f.status<>" + WorkflowDb.STATUS_DELETED;
        }

        sql += " order by " + orderBy + " " + sort;
        // LogUtil.getLog(getClass()).info(getClass() + " " + sql);
        return sql;
    }

    /**
     * 我发起的流程
     * @param request
     * @return
     */
    public String getSqlMine(HttpServletRequest request) {
        Privilege privilege = new Privilege();
        String myname = ParamUtil.get(request, "userName"); // userName为指定的人员
        if("".equals(myname)) {
            myname = privilege.getUser(request);
        }
        String op = ParamUtil.get(request, "op");
        String typeCode = ParamUtil.get(request, "typeCode");
        String starter = ParamUtil.getParam(request, "starter"); // 发起人
        String by = ParamUtil.get(request, "by");
        String title = ParamUtil.getParam(request, "title");
        String fromDate = ParamUtil.get(request, "fromDate");
        String toDate = ParamUtil.get(request, "toDate");
        int status = ParamUtil.getInt(request, "status", 1000);

        String orderBy = ParamUtil.get(request, "field");
        boolean hasFormCol = false;
        if ("".equals(orderBy)) {
            orderBy = "mydate";
        } else {
            if ("f.id".equals(orderBy)
                    || "f.flow_level".equals(orderBy) || "f.title".equals(orderBy) || "f.type_code".equals(orderBy)
                    || "f.userName".equals(orderBy) || "f.mydate".equals(orderBy) || "f.status".equals(orderBy) || "f.begin_date".equals(orderBy)|| "f.end_date".equals(orderBy)) {
                ;
            } else {
                orderBy = "t1." + orderBy;
                hasFormCol = true;
            }
        }
        String sort = ParamUtil.get(request, "order");
        if ("ascend".equals(sort)) {
            sort = "asc";
        }
        else if ("descend".equals(sort)) {
            sort = "desc";
        }
        if ("".equals(sort)) {
            sort = "desc";
        }

        String formTable = "";
        String formConds = "";
        if (!"".equals(typeCode)) {
            Leaf leaf = new Leaf();
            leaf = leaf.getLeaf(typeCode);
            FormDb fd = new FormDb();
            fd = fd.getFormDb(leaf.getFormCode());
            formTable = FormDb.getTableName(fd.getCode());
            formConds = getConds(request, leaf, fd);
        }

        String sql;
        if ("".equals(formConds) && !hasFormCol) {
            if (!"".equals(starter)) {
                sql = "select f.id from flow f, users u where f.userName=u.name and f.userName=" + StrUtil.sqlstr(myname) + " and f.status>" + WorkflowDb.STATUS_NONE;
            } else {
                sql = "select f.id from flow f where f.userName=" + StrUtil.sqlstr(myname) + " and f.status>" + WorkflowDb.STATUS_NONE;
            }
        }
        else {
            if (!"".equals(starter)) {
                sql = "select f.id from flow f, users u, " + formTable + " t1 where f.id=t1.flowId and f.userName=u.name and f.userName=" + StrUtil.sqlstr(myname) + " and f.status>" + WorkflowDb.STATUS_NONE;
            } else {
                sql = "select f.id from flow f, " + formTable + " t1 where f.id=t1.flowId and f.userName=" + StrUtil.sqlstr(myname) + " and f.status>" + WorkflowDb.STATUS_NONE;
            }
        }

        if (!"".equals(typeCode)) {
            sql += " and f.type_code=" + StrUtil.sqlstr(typeCode);
        }

        if ("search".equals(op)) {
            if (!"".equals(title)) {
                if ("flowId".equals(by)) {
                    sql += " and f.id = " + StrUtil.sqlstr(title);
                }
                else {
                    sql += " and f.title like " + StrUtil.sqlstr("%" + title + "%");
                }
            }

            if (!"".equals(fromDate) && !"".equals(toDate)) {
                java.util.Date d = DateUtil.parse(toDate, "yyyy-MM-dd");
                d = DateUtil.addDate(d, 1);
                String toDate2 = DateUtil.format(d, "yyyy-MM-dd");
                sql += " and (f.BEGIN_DATE>=" + SQLFilter.getDateStr(fromDate, "yyyy-MM-dd") + " and f.BEGIN_DATE<" + SQLFilter.getDateStr(toDate2, "yyyy-MM-dd") + ")";
            } else if (!"".equals(fromDate)) {
                sql += " and f.BEGIN_DATE>=" + SQLFilter.getDateStr(fromDate, "yyyy-MM-dd");
            } else if (!"".equals(toDate)) {
                sql += " and f.BEGIN_DATE<=" + SQLFilter.getDateStr(toDate, "yyyy-MM-dd");
            }

            if (status != 1000) {
                sql += " and f.status=" + status;
            }

            if (!"".equals(formConds)) {
                sql += " and " + formConds;
            }
        }

        // > WorkfowDb.STATUS_NONE 已排除被删除的状态
        // sql += " and f.status<>" + WorkflowDb.STATUS_DELETED;

        sql += " order by " + orderBy + " " + sort;
        // LogUtil.getLog(getClass()).info(getClass() + " " + sql);
        return sql;
    }

    /**
     * 为flow_list.jsp取得sql
     * @param request
     * @return
     */
    public String getSqlSearch(HttpServletRequest request) {
        String op = ParamUtil.get(request, "op");
        String typeCode = ParamUtil.get(request, "typeCode");
        String starter = ParamUtil.getParam(request, "starter"); // 发起人
        String by = ParamUtil.get(request, "by");
        String title = ParamUtil.getParam(request, "title");
        String fromDate = ParamUtil.get(request, "fromDate");
        String toDate = ParamUtil.get(request, "toDate");
        int status = ParamUtil.getInt(request, "status", 1000);

        String orderBy = ParamUtil.get(request, "field");
        boolean hasFormCol = false;
        if ("".equals(orderBy)) {
            orderBy = "f.id";
        } else {
            if ("f.id".equals(orderBy)
                    || "f.flow_level".equals(orderBy) || "f.title".equals(orderBy) || "f.type_code".equals(orderBy)
                    || "f.userName".equals(orderBy) || "f.mydate".equals(orderBy) || "f.status".equals(orderBy) || "f.end_date".equals(orderBy)) {
                ;
            } else {
                orderBy = "t1." + orderBy;
                hasFormCol = true;
            }
        }
        String sort = ParamUtil.get(request, "order");
        if ("ascend".equals(sort)) {
            sort = "asc";
        }
        else if ("descend".equals(sort)) {
            sort = "desc";
        }
        if ("".equals(sort)) {
            sort = "desc";
        }

        String formTable = "";
        String formConds = "";
        Leaf leaf = new Leaf();
        if (!"".equals(typeCode)) {
            leaf = leaf.getLeaf(typeCode);
            if (leaf == null) {
                throw new ErrMsgException("流程类型: " + typeCode + " 不存在");
            }
            if (leaf.getType() != Leaf.TYPE_NONE) {
                FormDb fd = new FormDb();
                fd = fd.getFormDb(leaf.getFormCode());
                if (!fd.isLoaded()) {
                    throw new ErrMsgException("表单: " + leaf.getFormCode() + " 不存在");
                }
                formTable = FormDb.getTableName(fd.getCode());
                formConds = getConds(request, leaf, fd);
            }
        }

        String sql;

        // 如果没有配置表单字段条件
        if ("".equals(formConds) && !hasFormCol) {
            if (!"".equals(starter)) {
                sql = "select f.id from flow f, users u where f.userName=u.name and u.realName like " + StrUtil.sqlstr("%" + starter + "%") + " and f.status<>" + WorkflowDb.STATUS_NONE;
            }
            else {
                sql = "select f.id from flow f where f.status<>" + WorkflowDb.STATUS_NONE;
            }
            if (!"".equals(typeCode) && !typeCode.equals(Leaf.CODE_ROOT)) {
                if (leaf.getLayer() == 2) {
                    // 取出所有的下级节点的记录
                    if (!"".equals(starter)) {
                        sql = "select f.id from flow f, users u, flow_directory d where f.userName=u.name and u.realName like " + StrUtil.sqlstr("%" + starter + "%") + " and d.code=f.type_code and status<>" + WorkflowDb.STATUS_NONE;
                        sql += " and (f.type_code=" + StrUtil.sqlstr(typeCode) + " or d.parent_code=" + StrUtil.sqlstr(typeCode) + ")";
                    }
                    else {
                        sql = "select f.id from flow f,flow_directory d where d.code=f.type_code and status<>" + WorkflowDb.STATUS_NONE;
                        sql += " and (f.type_code=" + StrUtil.sqlstr(typeCode) + " or d.parent_code=" + StrUtil.sqlstr(typeCode) + ")";
                    }
                } else {
                    sql += " and f.type_code=" + StrUtil.sqlstr(typeCode);
                }
            }
        }
        else {
            if (!"".equals(starter)) {
                sql = "select f.id from flow f, users u, " + formTable + " t1 where f.userName=u.name and u.realName like " + StrUtil.sqlstr("%" + starter + "%") + " and f.id=t1.flowId and f.status<>" + WorkflowDb.STATUS_NONE;
            }
            else {
                sql = "select f.id from flow f, " + formTable + " t1 where f.id=t1.flowId and f.status<>" + WorkflowDb.STATUS_NONE;
            }
            if (!"".equals(typeCode) && !typeCode.equals(Leaf.CODE_ROOT)) {
                if (leaf.getLayer() == 2) { // 如果是分类节点
                    if (!"".equals(starter)) {
                        // 取出所有的下级节点的记录
                        sql = "select f.id from flow f, users u, " + formTable + " t1, flow_directory d where f.userName=u.name and u.realName like " + StrUtil.sqlstr("%" + starter + "%") + " and f.id=t1.flowId and d.code=f.type_code and status<>" + WorkflowDb.STATUS_NONE;
                        sql += " and (f.type_code=" + StrUtil.sqlstr(typeCode) + " or d.parent_code=" + StrUtil.sqlstr(typeCode) + ")";
                    }
                    else {
                        // 取出所有的下级节点的记录
                        sql = "select f.id from flow f," + formTable + " t1, flow_directory d where f.id=t1.flowId and d.code=f.type_code and status<>" + WorkflowDb.STATUS_NONE;
                        sql += " and (f.type_code=" + StrUtil.sqlstr(typeCode) + " or d.parent_code=" + StrUtil.sqlstr(typeCode) + ")";
                    }
                } else {
                    sql += " and f.type_code=" + StrUtil.sqlstr(typeCode);
                }
            }
        }

        if ("search".equals(op)) {
            if (!"".equals(title)) {
                if ("flowId".equals(by)) {
                    sql += " and f.id = " + StrUtil.sqlstr(title);
                }
                else {
                    sql += " and f.title like " + StrUtil.sqlstr("%" + title + "%");
                }
            }

            if (!"".equals(fromDate) && !toDate.equals("")) {
                java.util.Date d = DateUtil.parse(toDate, "yyyy-MM-dd");
                d = DateUtil.addDate(d, 1);
                String toDate2 = DateUtil.format(d, "yyyy-MM-dd");
                sql += " and (f.BEGIN_DATE>=" + SQLFilter.getDateStr(fromDate, "yyyy-MM-dd") + " and f.BEGIN_DATE<" + SQLFilter.getDateStr(toDate2, "yyyy-MM-dd") + ")";
            } else if (!fromDate.equals("")) {
                sql += " and f.BEGIN_DATE>=" + SQLFilter.getDateStr(fromDate, "yyyy-MM-dd");
            } else if (fromDate.equals("") && !toDate.equals("")) {
                java.util.Date d = DateUtil.parse(toDate, "yyyy-MM-dd");
                d = DateUtil.addDate(d, 1);
                String toDate2 = DateUtil.format(d, "yyyy-MM-dd");
                sql += " and f.BEGIN_DATE<=" + SQLFilter.getDateStr(toDate2, "yyyy-MM-dd");
            }

            if (status != 1000) {
                sql += " and f.status=" + status;
            }
            else {
                sql += " and f.status<>" + WorkflowDb.STATUS_DELETED;
            }

            if (!formConds.equals("")) {
                sql += " and " + formConds;
            }
        }
        else {
            sql += " and f.status<>" + WorkflowDb.STATUS_DELETED;
        }
        sql += " order by " + orderBy + " " + sort;
        // LogUtil.getLog(getClass()).info(getClass() + " " + sql);
        return sql;
    }

    /**
     * 根据条件配置取得条件
     * @param request
     * @param leaf
     * @param fd
     * @return
     */
    public String getConds(HttpServletRequest request, Leaf leaf, FormDb fd) {
        MacroCtlMgr mm = new MacroCtlMgr();
        Vector<FormField> vt = new Vector<FormField>();

        String[] fields = null;
        String condProps = leaf.getCondProps();
        if (!"".equals(condProps)) {
            JSONObject json;
            try {
                json = new JSONObject(condProps);
                String condFields = (String) json.get("fields");
                fields = StrUtil.split(condFields, ",");
            } catch (JSONException e) {
                LogUtil.getLog(getClass()).error(e);
            }
        }

        int fieldsLen = 0;
        if (fields!=null) {
            fieldsLen = fields.length;
        }
        for (int n=0; n<fieldsLen; n++) {
            String field = fields[n];
            FormField ff = null;
            ff = fd.getFormField(field);
            if (ff != null) {
                try {
                    ff = (FormField) ff.clone();
                } catch (CloneNotSupportedException e) {
                    LogUtil.getLog(getClass()).error(e);
                }
                vt.addElement(ff);
            }
        }

        String cond = "";
        Iterator<FormField> ir = vt.iterator();
        while (ir.hasNext()) {
            FormField ff = ir.next();
            // 防止出现中文问题，@task应该把ParamUtil.get中改为支持chrome浏览器
            String value = ParamUtil.getParam(request, ff.getName());

            String name_cond = ParamUtil.get(request, ff.getName() + "_cond");
            // 当name_cond为空说明查询条件中不含有此字段，如果不排除掉可能会误将filter中需要从父窗口带入的字段作为查询条件
            if ("".equals(name_cond)) {
                continue;
            }

            String tableAlias = "t1"; // 别名
            String field = ff.getName();

            if (ff.getType().equals(FormField.TYPE_DATE) ||
                    ff.getType().equals(FormField.TYPE_DATE_TIME)) {
                if (name_cond.equals("0")) {
                    // 时间段
                    String fDate = ParamUtil.get(request, field + "FromDate");
                    String tDate = ParamUtil.get(request, field + "ToDate");
                    if (!fDate.equals("")) {
                        if (cond.equals("")) {
                            cond += tableAlias + "." + ff.getName() + ">=" + StrUtil.sqlstr(fDate);
                        } else {
                            cond += " and " + ff.getName() + ">=" + StrUtil.sqlstr(fDate);
                        }
                    }
                    if (!tDate.equals("")) {
                        // 如果是日期型的，则需加1天，然后用<符号
                        // if (ff.getType().equals(FormField.TYPE_DATE)) {
                            java.util.Date d = DateUtil.parse(tDate, "yyyy-MM-dd");
                            d = DateUtil.addDate(d, 1);
                            String tDate1 = DateUtil.format(d, "yyyy-MM-dd");
                            if (cond.equals("")) {
                                cond += tableAlias + "." + ff.getName() + "<" + StrUtil.sqlstr(tDate1);
                            } else {
                                cond += " and " + tableAlias + "." + ff.getName() + "<" + StrUtil.sqlstr(tDate1);
                            }
                        // }
                        // 查询条件传入的为yyyy-MM-dd格式，所以无需判断是否为TYPE_DATE_TIME型
/*                        else {
                            if (cond.equals("")) {
                                cond += tableAlias + "." + ff.getName() + "<=" + StrUtil.sqlstr(tDate);
                            } else {
                                cond += " and " + tableAlias + "." + ff.getName() + "<=" + StrUtil.sqlstr(tDate);
                            }
                        }*/
                    }
                } else {
                    // 时间点
                    String d = ParamUtil.get(request, field);
                    if (!d.equals("")) {
                        cond = SQLFilter.concat(cond, "and", tableAlias + "." + ff.getName() + "=" + StrUtil.sqlstr(d));
                    }
                }
            } else if (ff.getType().equals(FormField.TYPE_SELECT)) {
                String[] ary = ParamUtil.getParameters(request, field);
                if (ary != null) {
                    int len = ary.length;
                    if (len == 1) {
                        if (!ary[0].equals("")) {
                            if (cond.equals("")) {
                                cond += tableAlias + "." + ff.getName() + "=" + StrUtil.sqlstr(ary[0]);
                            } else {
                                cond += " and " + tableAlias + "." + ff.getName() + "=" + StrUtil.sqlstr(ary[0]);
                            }
                        }
                    } else {
                        String orStr = "";
                        for (int n = 0; n < len; n++) {
                            if (!ary[n].equals("")) {
                                orStr = SQLFilter.concat(orStr, "or",
                                        tableAlias + "." + ff.getName() + "=" + StrUtil.sqlstr(ary[n]));
                            }
                        }
                        if (!orStr.equals("")) {
                            cond = SQLFilter.concat(cond, "and", orStr);
                        }
                    }
                }
            }
            else if (ff.getFieldType()==FormField.FIELD_TYPE_INT || ff.getFieldType()==FormField.FIELD_TYPE_DOUBLE || ff.getFieldType()==FormField.FIELD_TYPE_FLOAT || ff.getFieldType()==FormField.FIELD_TYPE_LONG || ff.getFieldType()==FormField.FIELD_TYPE_PRICE) {
                if (!value.equals("")) {
                    if (cond.equals("")) {
                        cond += tableAlias + "." + ff.getName() + name_cond + value;
                    } else {
                        cond += " and " + tableAlias + "." + ff.getName() + name_cond + value;
                    }
                }
            }
            else {
                boolean isSpecial = false; // 是否为特殊的控件（即支持模糊查询的宏控件）
                if (value.equals(SQLBuilder.IS_EMPTY) || value.equals(SQLBuilder.IS_NOT_EMPTY)) {
                    // 如果是为空或者不为空的查询，则不获取宏控件的模糊查询，否则会生成类似这样的无意义的条件 in (select name from users where realname like '%<>空%')
                }
                else {
                    /*if (ff.getType().equals(FormField.TYPE_MACRO)) {
                        MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
                        IFormMacroCtl ifmc = mu.getIFormMacroCtl();
                        String urlStr = "";
                        String[] ary = SQLBuilder.getMacroCondsAndUrlStrs(request, ifmc, ff, name_cond, value, cond, urlStr, tableAlias);
                        if (ary != null) {
                            isSpecial = true;
                            if (!"".equals(ary[0])) {
                                // ary[0]中会把cond之前的值带进去，ary[1]中会把urlStr之前的值带进去
                                cond = ary[0];
                            }
                        }
                    }*/
                }

                if (!isSpecial) {
                    if (name_cond.equals("0")) {
                        if (!value.equals("")) {
                            if (cond.equals("")) {
                                if (value.equals(SQLBuilder.IS_EMPTY)) {
                                    cond = "(" + tableAlias + "." + ff.getName() + " is null or " + tableAlias + "." + ff.getName() + "='')";
                                }
                                else if (value.equals(SQLBuilder.IS_NOT_EMPTY)) {
                                    cond = "(" + tableAlias + "." + ff.getName() + " is not null and " + tableAlias + "." + ff.getName() + "<>'')";
                                }
                                else {
                                    cond = tableAlias + "." + ff.getName() + " like " + StrUtil.sqlstr("%" + value + "%");
                                }
                            } else {
                                if (value.equals(SQLBuilder.IS_EMPTY)) {
                                    cond += " and (" + tableAlias + "." + ff.getName() + " is null or " + tableAlias + "." + ff.getName() + "='')";
                                }
                                else if (value.equals(SQLBuilder.IS_NOT_EMPTY)) {
                                    cond += " and (" + tableAlias + "." + ff.getName() + " is not null and " + tableAlias + "." + ff.getName() + "<>'')";
                                }
                                else {
                                    cond += " and " + tableAlias + "." + ff.getName() + " like "
                                            + StrUtil.sqlstr("%" + value + "%");
                                }
                            }
                        }
                    } else if (name_cond.equals("1")) {
                        if (!value.equals("")) {
                            if (cond.equals("")) {
                                if (value.equals(SQLBuilder.IS_EMPTY)) {
                                    cond = "(" + tableAlias + "." + ff.getName() + " is null or " + tableAlias + "." + ff.getName() + "='')";
                                }
                                else if (value.equals(SQLBuilder.IS_NOT_EMPTY)) {
                                    cond = "(" + tableAlias + "." + ff.getName() + " is not null and " + tableAlias + "." + ff.getName() + "<>'')";
                                }
                                else {
                                    cond = tableAlias + "." + ff.getName() + "=" + StrUtil.sqlstr(value);
                                }
                            } else {
                                if (value.equals(SQLBuilder.IS_EMPTY)) {
                                    cond += " and (" + tableAlias + "." + ff.getName() + " is null or " + tableAlias + "." + ff.getName() + "='')";
                                }
                                else if (value.equals(SQLBuilder.IS_NOT_EMPTY)) {
                                    cond += " and (" + tableAlias + "." + ff.getName() + " is not null and " + tableAlias + "." + ff.getName() + "<>'')";
                                }
                                else {
                                    cond += " and " + tableAlias + "." + ff.getName() + "=" + StrUtil.sqlstr(value);
                                }
                            }
                        }
                    }
                }
            }
        }

        return cond;
    }

    public String[] getMonitors() {
        return this.monitors;
    }

    public String getRemark() {
        return remark;
    }

    public boolean isReturnBack() {
        return returnBack;
    }

    public java.util.Date getBeginDate() {
        return beginDate;
    }

    public java.util.Date getEndDate() {
        return endDate;
    }

    public long getProjectId() {
        return projectId;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public int getLevel() {
        return level;
    }

    public long getParentActionId() {
        return parentActionId;
    }

    public String getLocker() {
        return locker;
    }

    public static String getLevelDesc(int level) {
        if (level==LEVEL_IMPORTANT)
            return "重要";
        else if (level==LEVEL_URGENT)
            return "紧急";
        else
            return "普通";
    }

    public boolean isMonitor(String userName) {
        int len = monitors.length;
        for (int i=0; i<len; i++) {
            if (monitors[i].equals(userName))
                return true;
        }
        return false;
    }

    public boolean delMonitor(String userName) throws ErrMsgException {
        String sql = "delete from flow_monitor where flowId=? and userName=?";
        PreparedStatement ps = null;
        Conn conn = new Conn(connname);
        boolean re = false;
        try {
            ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ps.setString(2, userName);
            re = conn.executePreUpdate()==1?true:false;
            if (re) {
                // 更新缓存
                WorkflowCacheMgr wfc = new WorkflowCacheMgr();
                wfc.refreshSave(id);
            }
        }
        catch (SQLException e) {
            LogUtil.getLog(getClass()).error("delMonitor:" + e.getMessage());
        }
        finally {
            if (ps!=null) {
                try { ps.close(); } catch (Exception e) {}
                ps = null;
            }
            if (conn!=null) {
                conn.close();
                conn = null;
            }
        }
        return re;
    }

    public boolean addMonitor(String userName) throws ErrMsgException {
        if (isMonitor(userName))
            throw new ErrMsgException("该用户已被加入！");
        String sql = "insert into flow_monitor (flowId, userName, flowCreateDate) values (?, ?, ?)";
        PreparedStatement ps = null;
        Conn conn = new Conn(connname);
        boolean re = false;
        try {
            ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ps.setString(2, userName);
            ps.setString(3, DateUtil.format(mydate, "yyyy-MM-dd HH:mm:ss"));
            re = conn.executePreUpdate()==1?true:false;
            if (re) {
                // 更新缓存
                WorkflowCacheMgr wfc = new WorkflowCacheMgr();
                wfc.refreshSave(id);
            }
        }
        catch (SQLException e) {
            LogUtil.getLog(getClass()).error("addMonitor:" + e.getMessage());
        }
        finally {
            if (ps!=null) {
                try { ps.close(); } catch (Exception e) {}
                ps = null;
            }
            if (conn!=null) {
                conn.close();
                conn = null;
            }
        }
        return re;
    }

    public void lock(String userName) {
        setLocker(userName);
        save();
    }

    /**
     * 判断表单存档是否被用
     * @param formArchiveId
     * @return
     */
    public boolean isFormArchiveUsed(long formArchiveId) {
        String sql = "select id from flow where form_archive_id=" + formArchiveId;
        try {
            ListResult lr = listResult(sql, 1, 1);
            Vector v = lr.getResult();
            if (v.size()>0) {
                return true;
            }

        } catch (ErrMsgException e) {
            LogUtil.getLog(getClass()).error(e);
        }
        return false;
    }

    public String[] monitors;

    private int status = STATUS_STARTED;
    private int resultValue = WorkflowActionDb.RESULT_VALUE_NOT_ACCESSED;
    private String checkUserName;
    private String remark;
    private boolean returnBack = true;
    private java.util.Date beginDate;
    private java.util.Date endDate;
    private long projectId = -1;
    private String unitCode;
    private long parentActionId = PARENT_ACTION_ID_NONE;
    private String locker;

    public String getFlowJson() {
        return flowJson;
    }

    public void setFlowJson(String flowJson) {
        this.flowJson = flowJson;
    }

    private String flowJson;

    public long getFormArchiveId() {
        return formArchiveId;
    }

    public void setFormArchiveId(long formArchiveId) {
        this.formArchiveId = formArchiveId;
    }

    /**
     * 所用的表单归档记录的ID
     */
    private long formArchiveId;


    public String getIntervenor() {
        return intervenor;
    }

    public void setIntervenor(String intervenor) {
        this.intervenor = intervenor;
    }

    public java.util.Date getInterveneTime() {
        return interveneTime;
    }

    public void setInterveneTime(java.util.Date interveneTime) {
        this.interveneTime = interveneTime;
    }

    private String intervenor;
    private java.util.Date interveneTime;

    @Override
    public String display(HttpServletRequest request, UserDesktopSetupDb uds) {
        return null;
    }

    @Override
    public String getPageList(HttpServletRequest request, UserDesktopSetupDb uds) {
        return null;
    }
}
