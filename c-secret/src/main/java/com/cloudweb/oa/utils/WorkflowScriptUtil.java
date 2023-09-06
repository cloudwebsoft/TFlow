package com.cloudweb.oa.utils;

import bsh.EvalError;
import bsh.Interpreter;
import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.ParamUtil;
import cn.js.fan.web.SkinUtil;
import com.cloudweb.oa.api.IWorkflowScriptUtil;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.kit.util.FileUpload;
import com.redmoon.oa.flow.*;
import com.redmoon.oa.pvg.Privilege;
import com.redmoon.oa.shell.BSHShell;
import com.redmoon.oa.ui.LocalUtil;
import com.redmoon.oa.util.BeanShellUtil;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.Vector;

@Component
public class WorkflowScriptUtil implements IWorkflowScriptUtil {

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
    @Override
    public BSHShell runFinishScript(HttpServletRequest request, WorkflowDb wfd, FormDAO fdao, WorkflowActionDb lastAction, String script, boolean isTest) throws ErrMsgException {
        BSHShell bs = new BSHShell();

        StringBuffer sb = new StringBuffer();
        BeanShellUtil.setFieldsValue(fdao, sb);

        // 赋值给用户
        // sb.append("flowId=" + wfd.getId() + ";");
        sb.append("int flowId=" + wfd.getId() + ";");

        bs.set(ConstUtil.SCENE, ConstUtil.SCENE_FLOW_ON_FINISH);

        Privilege pvg = new Privilege();
        // 当调中调用了WorkflowDb.changeStatus时，request为null
        if (request!=null) {
            bs.set("userName", pvg.getUser(request));
        }
        else {
            bs.set("userName", "");
        }
        bs.set("lastAction", lastAction);
        bs.set("fdao", fdao);
        MyActionDb firstMyActionDb = new MyActionDb();
        firstMyActionDb = firstMyActionDb.getFirstMyActionDbOfFlow(wfd.getId());
        bs.set("firstMyAction", firstMyActionDb);
        bs.set("wf", wfd);
        bs.set("request", request);

        bs.eval(BeanShellUtil.escape(sb.toString()));

        bs.eval(script);

        return bs;
    }

    @Override
    public BSHShell runDeliverScript(HttpServletRequest request, String curUserName, WorkflowDb wf, FormDAO fdao, MyActionDb mad, String script, boolean isTest, FileUpload fu) throws ErrMsgException {
        BSHShell bs = new BSHShell();
        StringBuffer sb = new StringBuffer();

        BeanShellUtil.setFieldsValue(fdao, sb);

        // 赋值用户
        sb.append("String userName=\"" + curUserName + "\";");
        sb.append("int flowId=" + wf.getId() + ";");

        bs.set(ConstUtil.SCENE, ConstUtil.SCENE_FLOW_ACTION_FINISH);

        bs.set("fdao", fdao);

        // 在myAction.changeStatus中mad已经有变化了
        // 所以此处要重新获取，如果直接在脚本中运行mad.save会导致节点不能结束，还是高亮状态
        mad = mad.getMyActionDb(mad.getId());

        bs.set("mad", mad);
        bs.set("request", request);
        bs.set("fileUpload", fu);

        bs.eval(BeanShellUtil.escape(sb.toString()));

        bs.eval(script);
        Object obj = bs.get("ret");
        if (obj != null) {
            boolean ret = ((Boolean) obj).booleanValue();
            if (!ret) {
                String errMsg = (String) bs.get("errMsg");
                LogUtil.getLog(getClass()).error("bsh errMsg=" + errMsg);
            }
        }

        return bs;
    }

    /**
     * 运行返回事件脚本
     * @param request
     * @param curUserName
     * @param flowId
     * @param fdao
     * @param script 脚本
     * @return
     * @throws ErrMsgException
     */
    @Override
    public BSHShell runReturnScript(HttpServletRequest request, String curUserName, int flowId, FormDAO fdao, String script, FileUpload fu) throws ErrMsgException {
        BSHShell bs = new BSHShell();
        StringBuffer sb = new StringBuffer();

        BeanShellUtil.setFieldsValue(fdao, sb);

        // 赋值当前用户
        sb.append("String userName=\"" + curUserName + "\";");
        sb.append("int flowId=" + flowId + ";");

        bs.set(ConstUtil.SCENE, ConstUtil.SCENE_FLOW_ACTION_RETURN);
        bs.set("request", request);
        bs.set("fileUpload", fu);
        bs.set("fdao", fdao);

        bs.eval(BeanShellUtil.escape(sb.toString()));

        bs.eval(script);
        Object obj = bs.get("ret");
        if (obj != null) {
            boolean ret = ((Boolean) obj).booleanValue();
            if (!ret) {
                String errMsg = (String) bs.get("errMsg");
                LogUtil.getLog(getClass()).error("ReturnAction bsh errMsg=" + errMsg);
            }
        }
        return bs;
    }

    @Override
    public BSHShell runDiscardScript(HttpServletRequest request, String curUserName, int flowId, FormDAO fdao, String script, FileUpload fu) throws ErrMsgException {
        BSHShell bs = new BSHShell();
        StringBuffer sb = new StringBuffer();
        BeanShellUtil.setFieldsValue(fdao, sb);
        // 赋值当前用户
        sb.append("String userName=\"" + curUserName + "\";");
        sb.append("int flowId=" + flowId + ";");

        bs.set(ConstUtil.SCENE, ConstUtil.SCENE_FLOW_DISCARD);
        bs.set("request", request);
        bs.set("fileUpload", fu);

        bs.eval(BeanShellUtil.escape(sb.toString()));
        bs.eval(script);
        Object obj = bs.get("ret");
        if (obj != null) {
            boolean ret = (Boolean) obj;
            if (!ret) {
                String errMsg = (String) bs.get("errMsg");
                LogUtil.getLog(getClass()).error("Discard bsh errMsg=" + errMsg);
            }
        }
        return bs;
    }

    /**
     * 运行流程初始化事件
     * @param request
     * @param curUserName
     * @param flowId
     * @param script
     * @return
     * @throws ErrMsgException
     */
    @Override
    public BSHShell runPreInitScript(HttpServletRequest request, String curUserName, int flowId, String script, FormDAO fdao) throws ErrMsgException {
        BSHShell bs = new BSHShell();
        StringBuilder sb = new StringBuilder();
        // 赋值当前用户
        sb.append("String userName=\"" + curUserName + "\";");
        sb.append("int flowId=" + flowId + ";");

        bs.set("fdao", fdao);

        bs.set(ConstUtil.SCENE, ConstUtil.SCENE_FLOW_PRE_INIT);
        bs.set("request", request);

        bs.eval(BeanShellUtil.escape(sb.toString()));
        bs.eval(script);
        Object obj = bs.get("ret");
        if (obj != null) {
            boolean ret = (Boolean) obj;
            if (!ret) {
                String errMsg = (String) bs.get("errMsg");
                LogUtil.getLog(getClass()).error("Pre init bsh errMsg=" + errMsg);
            }
        }
        return bs;
    }

    /**
     * 运行预处理事件脚本
     * @param request
     * @param curUserName
     * @param wf
     * @param fdao
     * @param mad
     * @param script
     * @return
     * @throws ErrMsgException
     */
    @Override
    public BSHShell runPreDisposeScript(HttpServletRequest request, String curUserName, WorkflowDb wf, FormDAO fdao, MyActionDb mad, String script) throws ErrMsgException {
        BSHShell bs = new BSHShell();
        StringBuffer sb = new StringBuffer();

        BeanShellUtil.setFieldsValue(fdao, sb);

        // 赋值用户
        sb.append("String userName=\"" + curUserName + "\";");
        sb.append("int flowId=" + wf.getId() + ";");

        bs.set(ConstUtil.SCENE, ConstUtil.SCENE_FLOW_ACTION_PRE_DISPOSE);

        bs.set("fdao", fdao);

        // 在myAction.changeStatus中mad已经有变化了
        // 所以此处要重新获取，如果直接在脚本中运行mad.save会导致节点不能结束，还是高亮状态
        mad = mad.getMyActionDb(mad.getId());

        bs.set("mad", mad);
        bs.set("request", request);

        bs.eval(BeanShellUtil.escape(sb.toString()));

        bs.eval(script);
        Object obj = bs.get("ret");
        if (obj != null) {
            boolean ret = (Boolean) obj;
            if (!ret) {
                String errMsg = (String) bs.get("errMsg");
                LogUtil.getLog(getClass()).error("bsh errMsg=" + errMsg);
            }
        }

        return bs;
    }

    /**
     * 运行删除验证脚本
     * @param request
     * @param pvg
     * @param wf
     * @param fdao
     * @throws ErrMsgException
     */
    @Override
    public BSHShell runDeleteValidateScript(HttpServletRequest request, Privilege pvg, WorkflowDb wf, FormDAO fdao, WorkflowActionDb action, boolean isTest) throws ErrMsgException {
        // 如果许可证支持使用验证脚本
        boolean isValidateScript = com.redmoon.oa.kernel.License.getInstance().canUseModule(com.redmoon.oa.kernel.License.MODULE_ACTION_EVENT_SCRIPT);
        if (!isValidateScript) {
            return null;
        }

        WorkflowPredefineDb wpd = new WorkflowPredefineDb();
        wpd = wpd.getDefaultPredefineFlow(wf.getTypeCode());
        WorkflowPredefineMgr wpm = new WorkflowPredefineMgr();
        String script = wpm.getDeleteValidateScript(wpd.getScripts());
        if (script != null && !"".equals(script.trim())) {
            BSHShell bs = new BSHShell();

            StringBuffer sb = new StringBuffer();

            // 因fdao在update方法中当getFieldsByForm(request, fields)时，已被赋予上传的数据
            // 此时fdao的fields中已经为将要保存的值
            BeanShellUtil.setFieldsValue(fdao, sb);

            // 赋值用户
            sb.append("userName=\"" + pvg.getUser(request) + "\";");
            sb.append("int flowId=" + wf.getId() + ";");

            // 20160124 fgf 加入ret=true，以免在验证脚本中忘写ret=true
            // sb.append("ret=true;");

            bs.eval(BeanShellUtil.escape(sb.toString()));

            bs.set("fdao", fdao);
            bs.set("request", request);
            if (action != null) {
                bs.set("actionId", action.getId());
            } else {
                bs.set("actionId", -1);
            }

            bs.eval(script);
            Object obj = bs.get("ret");
            if (obj != null) {
                boolean re = (Boolean) obj;
                if (!re) {
                    String errMsg = (String) bs.get("errMsg");
                    if (errMsg != null) {
                        throw new ErrMsgException(LocalUtil.LoadString(request, "res.flow.Flow", "validError") + errMsg);
                    } else {
                        throw new ErrMsgException(LocalUtil.LoadString(request, "res.flow.Flow", "validError"));
                    }
                }
            } else {
                if (!bs.isError()) {
                    throw new ErrMsgException(LocalUtil.LoadString(request, "res.flow.Flow", "scriptError"));//"该节点脚本中未配置ret=...");
                }
            }

            return bs;
        }
        return null;
    }

    /**
     * 撤回待办记录myActionId对应的下一节点
     * @param myActionId long
     * @return boolean
     */
    @Override
    public boolean recallMyAction(HttpServletRequest request, long myActionId) throws ErrMsgException {
        MyActionDb mad = new MyActionDb();
        mad = mad.getMyActionDb(myActionId);

        Privilege privilege = new Privilege();
        String myname = privilege.getUser(request);
        if (!privilege.isUserPrivValid(request, "admin")) {
            if (!mad.getUserName().equals(myname) &&
                    !mad.getProxyUserName().equals(myname)) {
                // 权限检查
                throw new ErrMsgException(SkinUtil.LoadString(request, "pvg_invalid"));
            }
        }

        if (!mad.isChecked()) {
            String str = LocalUtil.LoadString(request,"res.flow.Flow","upcomingItem");
            throw new ErrMsgException(str);
        }

        boolean re = false;

        Directory dir = new Directory();
        int flowId = (int)mad.getFlowId();
        WorkflowDb wf = new WorkflowDb();
        wf = wf.getWorkflowDb(flowId);
        Leaf ft = dir.getLeaf(wf.getTypeCode());
        boolean isFree = ft.getType()!=Leaf.TYPE_LIST;

        int k = 0;
        if (isFree) {
            k = mad.recallMyActionsByPrivMyActionFree(myActionId);
        }
        else {
            // 检查下一节点是否处于延时状态，如果是，则撤回
            WorkflowActionDb wad = new WorkflowActionDb();
            wad = wad.getWorkflowActionDb((int)mad.getActionId());

            // 撤回事件，事件在撤回处理之前运行，以取得与其相关的节点信息。
            WorkflowPredefineDb wpd = new WorkflowPredefineDb();
            wpd = wpd.getDefaultPredefineFlow(wf.getTypeCode());
            WorkflowPredefineMgr wpm = new WorkflowPredefineMgr();
            String script = wpm.getRecallScript(wpd.getScripts());
            if (script != null && !"".equals(script)) {
                Interpreter bsh = new Interpreter();
                try {
                    StringBuilder sb = new StringBuilder();

                    FormDAO fdao = new FormDAO();
                    fdao = fdao.getFormDAO(flowId, new FormDb(ft.getFormCode()));

                    // 赋值当前用户
                    sb.append("String userName=\"" + myname + "\";");
                    sb.append("int flowId=" + wf.getId() + ";");

                    bsh.set("request", request);
                    bsh.set("mad", mad);
                    bsh.set("action", wad);
                    bsh.set("fdao", fdao);

                    bsh.eval(BeanShellUtil.escape(sb.toString()));

                    bsh.eval(script);
                    Object obj = bsh.get("ret");
                    if (obj != null) {
                        boolean ret = (Boolean) obj;
                        if (!ret) {
                            String errMsg = (String) bsh.get("errMsg");
                            LogUtil.getLog(getClass()).error("Recall bsh errMsg=" + errMsg);
                        }
                    }
                } catch (EvalError e) {
                    LogUtil.getLog(getClass()).error(e);
                }
            }

            boolean isDelayed = false;
            Vector va = wad.getLinkToActions();
            if (va.size()==1) {
                wad = (WorkflowActionDb)va.elementAt(0);
                if (wad.getStatus()==WorkflowActionDb.STATE_DELAYED) {
                    isDelayed = true;
                }
            }

            if (isDelayed) {
                if (wad.isCanPrivUserModifyDelayDate()) {
                    wad.setStatus(WorkflowActionDb.STATE_NOTDO);
                    wad.save();
                    k = 1;
                }
                else {
                    String str = LocalUtil.LoadString(request,"res.flow.Flow","illegalOperation");
                    throw new ErrMsgException(str);
                }
            }
            else {
                k = mad.recallMyActionsByPrivMyAction(myActionId);
            }
        }

        // LogUtil.getLog(getClass()).info("recallMyAction k=" + k + " mad.id=" + mad.getId());
        // 如果是自由流程，则允许最后一个节点的人撤回，即允许重新处理
        if (k>0 || (k==0 && isFree)) {
            mad.setChecked(false);
            re = mad.save();
            if (re) {
                // 置节点状态为正在办理状态
                WorkflowActionDb wad = new WorkflowActionDb();
                wad = wad.getWorkflowActionDb((int)mad.getActionId());
                wad.setStatus(WorkflowActionDb.STATE_DOING);
                wad.save();

                // 置流程为处理状态
                if (wf.getStatus()==WorkflowDb.STATUS_FINISHED) {
                    wf.setStatus(WorkflowDb.STATUS_STARTED);
                    wf.save();
                }
            }
        }

        if (re) {
            boolean isIntervene = ParamUtil.getBoolean(request, "isIntervene", false);
            if (isIntervene) {
                wf.setIntervenor(myname);
                wf.setInterveneTime(new Date());
                wf.save();
            }
        }
        return re;
    }

    /**
     * 运行验证脚本
     *
     * @param request
     * @param pvg
     * @param wf
     * @param fdao
     * @throws ErrMsgException
     */
    @Override
    public BSHShell runValidateScript(HttpServletRequest request, Privilege pvg, WorkflowDb wf, FormDAO fdao, WorkflowActionDb action, boolean isTest, FileUpload fu) throws ErrMsgException {
        // 如果许可证支持使用验证脚本
        boolean isValidateScript = com.redmoon.oa.kernel.License.getInstance().canUseModule(com.redmoon.oa.kernel.License.MODULE_ACTION_EVENT_SCRIPT);
        if (!isValidateScript) {
            return null;
        }

        WorkflowPredefineDb wpd = new WorkflowPredefineDb();
        wpd = wpd.getDefaultPredefineFlow(wf.getTypeCode());
        WorkflowPredefineMgr wpm = new WorkflowPredefineMgr();
        String script = wpm.getValidateScript(wpd.getScripts(), action.getInternalName());
        if (script != null && !"".equals(script.trim())) {
            BSHShell bs = new BSHShell();

            StringBuffer sb = new StringBuffer();

            // 因fdao在update方法中当getFieldsByForm(request, fields)时，已被赋予上传的数据
            // 此时fdao的fields中已经为将要保存的值
            BeanShellUtil.setFieldsValue(fdao, sb);

            // 赋值用户
            sb.append("userName=\"" + pvg.getUser(request) + "\";");
            sb.append("int flowId=" + wf.getId() + ";");

            // 20160124 fgf 加入ret=true，以免在验证脚本中忘写ret=true
            // sb.append("ret=true;");

            bs.eval(BeanShellUtil.escape(sb.toString()));

            bs.set(ConstUtil.SCENE, ConstUtil.SCENE_FLOW_VALIDATE);

            bs.set("fdao", fdao);
            bs.set("request", request);
            bs.set("actionId", action.getId());

            if (isTest) {
                fu = new FileUpload();
                BeanShellUtil.setFieldsValue(fdao, fu);
            }
            bs.set("fileUpload", fu);

            bs.eval(script);
            Object obj = bs.get("ret");
            if (obj != null) {
                boolean re = (Boolean) obj;
                if (!re) {
                    String errMsg = (String) bs.get("errMsg");
                    if (errMsg != null) {
                        throw new ErrMsgException(LocalUtil.LoadString(request, "res.flow.Flow", "validError") + errMsg);
                    } else {
                        throw new ErrMsgException(LocalUtil.LoadString(request, "res.flow.Flow", "validError"));
                    }
                }
            } else {
                if (!bs.isError()) {
                    throw new ErrMsgException(LocalUtil.LoadString(request, "res.flow.Flow", "scriptError"));//"该节点脚本中未配置ret=...");
                }
            }

            return bs;
        }
        return null;
    }

    /**
     * 运行验证脚本
     *
     * @param request
     * @param wf
     * @throws ErrMsgException
     */
    @Override
    public BSHShell runActiveScript(HttpServletRequest request, WorkflowDb wf, long myActionId, WorkflowActionDb action, boolean isTest) throws ErrMsgException {
        // 如果许可证支持使用验证脚本
        boolean isValidateScript = com.redmoon.oa.kernel.License.getInstance().canUseModule(com.redmoon.oa.kernel.License.MODULE_ACTION_EVENT_SCRIPT);
        if (!isValidateScript) {
            return null;
        }

        WorkflowPredefineDb wpd = new WorkflowPredefineDb();
        wpd = wpd.getDefaultPredefineFlow(wf.getTypeCode());
        WorkflowPredefineMgr wpm = new WorkflowPredefineMgr();
        String script = wpm.getActionActiveScript(wpd.getScripts(), action.getInternalName());
        if (script != null && !"".equals(script.trim())) {
            BSHShell bs = new BSHShell();

            StringBuilder sb = new StringBuilder();

            // 赋值用户
            sb.append("userName=\"" + SpringUtil.getUserName() + "\";");
            sb.append("int flowId=" + wf.getId() + ";");

            bs.eval(BeanShellUtil.escape(sb.toString()));

            bs.set(ConstUtil.SCENE, ConstUtil.SCENE_FLOW_ACTION_ACTIVE);

            bs.set("request", request);
            bs.set("actionId", action.getId());
            bs.set("myActionId", myActionId);

            bs.eval(script);
            Object obj = bs.get("ret");
            if (obj != null) {
                boolean ret = (Boolean) obj;
                if (!ret) {
                    String errMsg = (String) bs.get("errMsg");
                    LogUtil.getLog(getClass()).error("Discard bsh errMsg=" + errMsg);
                }
            }
            return bs;
        }
        return null;
    }
}
