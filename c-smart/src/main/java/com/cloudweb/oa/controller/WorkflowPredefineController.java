package com.cloudweb.oa.controller;

import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.ParamUtil;
import cn.js.fan.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cloudweb.oa.cache.GroupCache;
import com.cloudweb.oa.cache.RoleCache;
import com.cloudweb.oa.cache.UserCache;
import com.cloudweb.oa.entity.Department;
import com.cloudweb.oa.security.AuthUtil;
import com.cloudweb.oa.utils.ConstUtil;
import com.cloudweb.oa.vo.Result;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.flow.*;
import com.redmoon.oa.flow.strategy.StrategyMgr;
import com.redmoon.oa.flow.strategy.StrategyUnit;
import com.redmoon.oa.person.UserDb;
import com.redmoon.oa.pvg.Privilege;
import io.swagger.annotations.*;
import org.jdom.JDOMException;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotEmpty;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

@Controller
@RequestMapping("/admin")
public class WorkflowPredefineController {
    @Autowired
    private HttpServletRequest request;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private UserCache userCache;

    @Autowired
    private RoleCache roleCache;

    @Autowired
    private GroupCache groupCache;

    @ResponseBody
    @RequestMapping(value = "/setFlowDebug", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String setFlowDebug() throws ErrMsgException {
        JSONObject json = new JSONObject();
        Privilege privilege = new Privilege();
        String code = ParamUtil.get(request, "code");
        boolean isDebug = ParamUtil.getBoolean(request, "isDebug", true);
        Leaf lf = new Leaf();
        lf = lf.getLeaf(code);
        String myUnitCode = privilege.getUserUnitCode(request);
        LeafPriv lp = new LeafPriv(code);
        if (privilege.isUserPrivValid(request, "admin.unit") && lf.getUnitCode().equals(myUnitCode)) {
            ;
        } else if (!lp.canUserExamine(privilege.getUser(request))) {
            json.put("ret", "0");
            json.put("msg", "权限非法！");
            return json.toString();
        }

        boolean hasChild = false;
        if (lf.getChildCount() > 0) {
            hasChild = true;
            Vector v = new Vector();
            lf.getAllChild(v, lf);
            v.addElement(lf);
            Iterator ir = v.iterator();
            while (ir.hasNext()) {
                lf = (Leaf) ir.next();
                if (lf.getType() != Leaf.TYPE_NONE) {
                    lf.setDebug(isDebug);
                    lf.update();
                }
            }
        } else {
            lf.setDebug(isDebug);
            lf.update();
        }
        json.put("ret", "1");
        if (hasChild) {
            if (isDebug) {
                json.put("msg", lf.getName() + "下的流程已置为调试模式！");
            } else {
                json.put("msg", lf.getName() + "下的流程已置为正常模式！");
            }
        } else {
            if (isDebug) {
                json.put("msg", lf.getName() + " 已置为调试模式！");
            } else {
                json.put("msg", lf.getName() + " 已置为正常模式！");
            }
        }
        return json.toString();
    }

    @ResponseBody
    @RequestMapping(value = "/moveFlowNode", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String moveFlowNode() throws ErrMsgException {
        JSONObject json = new JSONObject();
        String code = ParamUtil.get(request, "code");
        String parent_code = ParamUtil.get(request, "parent_code");
        int position = Integer.parseInt(ParamUtil.get(request, "position"));
        if ("root".equals(code)) {
            json.put("ret", "0");
            json.put("msg", "根节点不能移动！");
            return json.toString();
        }
        if ("#".equals(parent_code)) {
            json.put("ret", "0");
            json.put("msg", "不能与根节点平级！");
            return json.toString();
        }

        Directory dir = new Directory();
        Leaf moveleaf = dir.getLeaf(code);
        String oldParentCode = moveleaf.getParentCode();
        int old_position = moveleaf.getOrders();//得到被移动节点原来的位置，从1开始

        Leaf oldParentLeaf = dir.getLeaf(oldParentCode);
        Leaf newParentLeaf;
        if (parent_code.equals(oldParentCode)) {
            newParentLeaf = oldParentLeaf;
        } else {
            newParentLeaf = dir.getLeaf(parent_code);
        }
        // 移动后的层级需一致
        if (oldParentLeaf.getLayer() != newParentLeaf.getLayer()) {
            json.put("ret", "0");
            json.put("msg", "层级不一致，不能移动！");
            return json.toString();
        }
        int p = position + 1;  // jstree的position是从0开始的，而orders是从1开始的
        moveleaf.setParentCode(parent_code);
        moveleaf.setOrders(p);
        moveleaf.update();

        boolean isSameParent = oldParentCode.equals(parent_code);

        // 重新梳理orders
        Iterator ir = newParentLeaf.getChildren().iterator();
        while (ir.hasNext()) {
            Leaf lf = (Leaf) ir.next();
            // 跳过自己
            if (lf.getCode().equals(code)) {
                continue;
            }

            // 如果移动后父节点变了
            if (!isSameParent) {
                if (lf.getOrders() >= p) {
                    lf.setOrders(lf.getOrders() + 1);
                    lf.update();
                }
            } else {
                if (p < old_position) {//上移
                    if (lf.getOrders() >= p) {
                        lf.setOrders(lf.getOrders() + 1);
                        lf.update();
                    }
                } else {//下移
                    if (lf.getOrders() <= p && lf.getOrders() > old_position) {
                        lf.setOrders(lf.getOrders() - 1);
                        lf.update();
                    }
                }
            }
        }

        // 原节点下的孩子节点通过修复repairTree处理
        Leaf rootLeaf = dir.getLeaf(Leaf.CODE_ROOT);
        Directory dm = new Directory();
        try {
            dm.repairTree(rootLeaf);
        } catch (Exception e) {
            LogUtil.getLog(getClass()).error(e);
        }

        // 父节点有变化
        if (!isSameParent) {
            // 只有二级节点的父节点才会有变化，此时需变动其表单所属的类别
            FormDb fd = new FormDb();
            fd = fd.getFormDb(moveleaf.getFormCode());
            fd.setFlowTypeCode(parent_code);
            fd.saveContent();
        }

        json.put("ret", "1");
        json.put("msg", "操作成功！");
        return json.toString();
    }

    @ResponseBody
    @RequestMapping(value = "/delFlowNode", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String delFlowNode(@RequestParam(value = "code") String code) throws ErrMsgException {
        JSONObject json = new JSONObject();
        Directory dir = new Directory();
        try {
            dir.del(request, code);
        } catch (ErrMsgException e) {
            json.put("ret", "0");
            json.put("msg", e.getMessage());
            return json.toString();
        }
        json.put("ret", "1");
        json.put("msg", "操作成功！");
        return json.toString();
    }

    @ApiOperation(value = "删除节点", notes = "删除节点", httpMethod = "POST")
    @ApiImplicitParam(name = "code", value = "节点编码", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/delNode", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> delNode(@RequestParam(value = "code") String code) throws ErrMsgException {
        Directory dir = new Directory();
        try {
            dir.del(request, code);
        } catch (ErrMsgException e) {
            return new Result<>(e.getMessage());
        }
        return new Result<>();
    }

    @ApiOperation(value = "移动节点", notes = "移动节点", httpMethod = "POST")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/moveNode", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> moveNode() throws ErrMsgException {
        JSONObject json = new JSONObject();
        String code = ParamUtil.get(request, "code");
        String parentCode = ParamUtil.get(request, "parent_code");
        int position = Integer.parseInt(ParamUtil.get(request, "position"));
        if ("root".equals(code)) {
            return new Result<>(false, "根节点不能移动");
        }
        if ("#".equals(parentCode)) {
            return new Result<>(false, "不能与根节点平级");
        }

        Directory dir = new Directory();
        Leaf moveleaf = dir.getLeaf(code);
        String oldParentCode = moveleaf.getParentCode();
        int oldPosition = moveleaf.getOrders();//得到被移动节点原来的位置，从1开始

        Leaf oldParentLeaf = dir.getLeaf(oldParentCode);
        Leaf newParentLeaf;
        if (parentCode.equals(oldParentCode)) {
            newParentLeaf = oldParentLeaf;
        } else {
            newParentLeaf = dir.getLeaf(parentCode);
        }
        // 移动后的层级需一致
        if (oldParentLeaf.getLayer() != newParentLeaf.getLayer()) {
            return new Result<>(false, "层级不一致，不能移动");
        }
        int p = position + 1;  // jstree的position是从0开始的，而orders是从1开始的
        moveleaf.setParentCode(parentCode);
        moveleaf.setOrders(p);
        moveleaf.update();

        boolean isSameParent = oldParentCode.equals(parentCode);

        // 重新梳理orders
        for (Leaf lf : newParentLeaf.getChildren()) {
            // 跳过自己
            if (lf.getCode().equals(code)) {
                continue;
            }

            // 如果移动后父节点变了
            if (!isSameParent) {
                if (lf.getOrders() >= p) {
                    lf.setOrders(lf.getOrders() + 1);
                    lf.update();
                }
            } else {
                if (p < oldPosition) {//上移
                    if (lf.getOrders() >= p) {
                        lf.setOrders(lf.getOrders() + 1);
                        lf.update();
                    }
                } else {//下移
                    if (lf.getOrders() <= p && lf.getOrders() > oldPosition) {
                        lf.setOrders(lf.getOrders() - 1);
                        lf.update();
                    }
                }
            }
        }

        // 原节点下的孩子节点通过修复repairTree处理
        Leaf rootLeaf = dir.getLeaf(Leaf.CODE_ROOT);
        Directory dm = new Directory();
        try {
            dm.repairTree(rootLeaf);
        } catch (Exception e) {
            LogUtil.getLog(getClass()).error(e);
        }

        // 父节点有变化
        if (!isSameParent) {
            // 只有二级节点的父节点才会有变化，此时需变动其表单所属的类别
            FormDb fd = new FormDb();
            fd = fd.getFormDb(moveleaf.getFormCode());
            fd.setFlowTypeCode(parentCode);
            fd.saveContent();
        }
        return new Result<>();
    }

    @ResponseBody
    @RequestMapping(value = "/modifyFlowNode", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String modifyFlowNode() throws ErrMsgException {
        JSONObject json = new JSONObject();
        Directory dir = new Directory();
        boolean re;
        try {
            re = dir.update(request);
        } catch (ErrMsgException e) {
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

    @ResponseBody
    @RequestMapping(value = "/addFlowNode", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String addFlowNode() throws ErrMsgException {
        JSONObject json = new JSONObject();
        Directory dir = new Directory();
        boolean re = false;
        try {
            re = dir.AddChild(request);
        } catch (ErrMsgException e) {
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

    @ResponseBody
    @RequestMapping(value = "/getFormColumn", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String getFormColumn() throws ErrMsgException {
        JSONObject json = new JSONObject();
        String formCode = ParamUtil.get(request, "formCode");
        FormDb fd = new FormDb();
        fd = fd.getFormDb(formCode);
        Iterator field_v = fd.getFields().iterator();
        String str = "";
        while (field_v.hasNext()) {
            FormField ff = (FormField) field_v.next();
            str += "<span id='{" + ff.getName() + "}' name='list_field' onMouseOut='outtable(this)' onMouseOver='overtable(this)' style='width:200px;'>" + ff.getTitle() + "</span><br/>";
        }
        json.put("ret", "1");
        json.put("msg", str);
        return json.toString();
    }

    @ResponseBody
    @RequestMapping(value = "/createFlowPredefined", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String createFlowPredefined() throws ErrMsgException {
        JSONObject json = new JSONObject();
        WorkflowPredefineMgr wpm = new WorkflowPredefineMgr();
        boolean re = false;
        try {
            re = wpm.create(request);
        } catch (ErrMsgException e) {
            json.put("ret", "0");
            json.put("msg", e.getMessage());
            return json.toString();
        }
        if (re) {
            json.put("ret", "1");
            json.put("msg", "操作成功！");
            json.put("newId", wpm.getNewId());
        } else {
            json.put("ret", "0");
            json.put("msg", "操作失败");
        }
        return json.toString();
    }

    @ResponseBody
    @RequestMapping(value = "/modifyFlowPredefined", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String modifyFlowPredefined() throws ErrMsgException {
        JSONObject json = new JSONObject();
        boolean re;
        WorkflowPredefineMgr wpm = new WorkflowPredefineMgr();
        try {
            re = wpm.modify(request);
        } catch (Exception e) {
            json.put("ret", "0");
            json.put("msg", e.getMessage());
            return json.toString();
        }
        if (re) {
            json.put("ret", "1");
            json.put("msg", "操作成功！");
        } else {
            json.put("ret", "0");
            json.put("msg", "操作失败");
        }
        return json.toString();
    }

    @ApiOperation(value = "创建节点", notes = "创建节点", httpMethod = "POST")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/createNode", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> createNode() throws ErrMsgException {
        Directory dir = new Directory();
        boolean re = false;
        try {
            re = dir.AddChild(request);
        } catch (ErrMsgException e) {
            return new Result<>(false, e.getMessage());
        }
        return new Result<>(re);
    }

    @ApiOperation(value = "更新节点", notes = "更新节点", httpMethod = "POST")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/updateNode", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public Result<Object> updateNode() throws ErrMsgException {
        boolean re;
        Directory dir = new Directory();
        try {
            re = dir.update(request);
        } catch (ErrMsgException e) {
            return new Result<>(false, e.getMessage());
        }
        return new Result<>(re);
    }

    @ApiOperation(value = "编辑时获取节点属性", notes = "编辑时获取节点属性", httpMethod = "POST")
    @ApiImplicitParam(name = "code", value = "节点编码", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/editNode", produces = {"text/html;", "application/json;charset=UTF-8;"})
    public Result<Object> editNode(String code) {
        Leaf lf = new Leaf();
        lf = lf.getLeaf(code);
        JSONObject jsonObject = (JSONObject) JSONObject.toJSON(lf);
        return new Result<>(jsonObject);
    }

    @ResponseBody
    @RequestMapping(value = "/applyFlow", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String applyFlow(@RequestParam(value = "flowTypeCode") String flowTypeCode, @RequestParam(value = "isWithScript") boolean isWithScript) {
        JSONObject json = new JSONObject();
        boolean re;
        try {
            WorkflowPredefineDb wpd = new WorkflowPredefineDb();
            wpd = wpd.getDefaultPredefineFlow(flowTypeCode);
            String templateCode = ParamUtil.get(request, "templateCode");

            String title = "";
            Leaf lf = new Leaf();
            lf = lf.getLeaf(flowTypeCode);
            if (lf != null) {
                title = lf.getName();
            }

            // 如果还没有预定义流程
            if (wpd == null) {
                wpd = new WorkflowPredefineDb();
                WorkflowPredefineDb twpd = wpd.getDefaultPredefineFlow(templateCode);
                if (twpd == null || !twpd.isLoaded()) {
                    json.put("ret", "0");
                    json.put("msg", "流程图不存在！");
                    return json.toString();
                }
                wpd.setTypeCode(flowTypeCode);
                wpd.setFlowString(twpd.getFlowString());
                wpd.setTitle(title);
                wpd.setReturnBack(twpd.isReturnBack());
                wpd.setReactive(twpd.isReactive());
                wpd.setRecall(twpd.isRecall());
                wpd.setReturnMode(twpd.getReturnMode());
                wpd.setReturnStyle(twpd.getReturnStyle());
                wpd.setRoleRankMode(twpd.getRoleRankMode());
                wpd.setProps(twpd.getProps());
                wpd.setViews(twpd.getViews());
                if (isWithScript) {
                    wpd.setScripts(twpd.getScripts());
                }
                wpd.setLinkProp(twpd.getLinkProp());
                wpd.setFlowJson(twpd.getFlowJson());
                re = wpd.create();
            } else {
                WorkflowPredefineDb twpd = wpd.getDefaultPredefineFlow(templateCode);
                if (twpd == null || !twpd.isLoaded()) {
                    json.put("ret", "0");
                    json.put("msg", "流程图不存在！");
                    return json.toString();
                }
                wpd.setFlowString(twpd.getFlowString());
                wpd.setReturnBack(twpd.isReturnBack());
                wpd.setReactive(twpd.isReactive());
                wpd.setRecall(twpd.isRecall());
                wpd.setReturnMode(twpd.getReturnMode());
                wpd.setReturnStyle(twpd.getReturnStyle());
                wpd.setRoleRankMode(twpd.getRoleRankMode());
                wpd.setProps(twpd.getProps());
                wpd.setViews(twpd.getViews());
                if (isWithScript) {
                    wpd.setScripts(twpd.getScripts());
                }
                wpd.setLinkProp(twpd.getLinkProp());
                wpd.setFlowJson(twpd.getFlowJson());
                re = wpd.save();
            }
        } catch (ErrMsgException e) {
            json.put("ret", "0");
            json.put("msg", e.getMessage());
            return json.toString();
        }
        if (re) {
            json.put("ret", "1");
            json.put("msg", "操作成功！");
        } else {
            json.put("ret", "0");
            json.put("msg", "操作失败");
        }
        return json.toString();
    }

    @ResponseBody
    @RequestMapping(value = "/getFieldsAsOptions", method = RequestMethod.GET, produces = {"text/html;charset=UTF-8;", "application/json;charset=UTF-8;"})
    public String getFieldsAsOptions(@RequestParam(value = "flowTypeCode", required = true)String flowTypeCode) throws ErrMsgException {
        JSONObject json = new JSONObject();
        StringBuffer sb = new StringBuffer();
        Leaf lf = new Leaf();
        lf = lf.getLeaf(flowTypeCode);
        if (lf == null) {
            json.put("ret", "0");
            json.put("msg", "流程类型不存在");
            return json.toString();
        }

        String formCode = lf.getFormCode();
        FormDb fd = new FormDb();
        fd = fd.getFormDb(formCode);
        Iterator ir = fd.getFields().iterator();
        while (ir.hasNext()) {
            FormField ff = (FormField) ir.next();
            sb.append("<option value=" + ff.getName() + ">" + ff.getTitle() + "</option>");
        }

        json.put("ret", "1");
        json.put("msg", "操作成功！");
        json.put("options", sb.toString());

        return json.toString();
    }

    @ApiOperation(value = "流程权限列表", notes = "流程权限列表", httpMethod = "POST")
    @ApiImplicitParam(name = "code", value = "流程类型编码", required = false, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/listPriv", method = RequestMethod.POST, produces = {"text/html;", "application/json;charset=UTF-8;"})
    public Result<Object> listPriv(@RequestParam(value = "code", required = false) String code) {
        JSONArray arr = new JSONArray();
        LeafPriv leafPriv = new LeafPriv(code);
        if (!(leafPriv.canUserExamine(authUtil.getUserName()))) {
            return new Result<>(false, cn.js.fan.web.SkinUtil.LoadString(request, "pvg_invalid") + " 用户需对该节点拥有管理的权限");
        }
        Directory dir = new Directory();
        String sql = "select id from flow_dir_priv dp where dp.dir_code=" + StrUtil.sqlstr(code) + " order by id desc";
        Vector v = leafPriv.list(sql);
        for (Object o : v) {
            leafPriv = (LeafPriv)o;
            JSONObject json = new JSONObject();
            json.put("id", leafPriv.getId());
            json.put("name", leafPriv.getName());
            if (leafPriv.getType() == LeafPriv.TYPE_USER) {
                json.put("title", userCache.getUser(leafPriv.getName()).getRealName());
            } else if (leafPriv.getType() == LeafPriv.TYPE_USERGROUP) {
                json.put("title", groupCache.getGroup(leafPriv.getName()).getDescription());
            } else {
                json.put("title", roleCache.getRole(leafPriv.getName()).getDescription());
            }
            json.put("see", leafPriv.getSee());
            json.put("modify", leafPriv.getModify());
            json.put("examine", leafPriv.getExamine());
            json.put("typeDesc", leafPriv.getTypeDesc());
            json.put("dirCode", leafPriv.getDirCode());
            Leaf lf = dir.getLeaf(leafPriv.getDirCode());
            json.put("dirName", lf == null ? "" : lf.getName(request));
            arr.add(json);
        }
        return new Result<>(arr);
    }

    @ApiOperation(value = "添加流程权限", notes = "添加流程权限", httpMethod = "POST")
    @ApiImplicitParam(name = "name", value = "具有权限的角色、用户组或人员", required = true, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/createPriv", method = RequestMethod.POST, produces = {"text/html;", "application/json;charset=UTF-8;"})
    public Result<Object> createPriv(@RequestBody JSONObject jsonObject) {
        String nodeCode = jsonObject.getString("nodeCode");
        JSONArray privs = jsonObject.getJSONArray("privs");
        boolean re = false;
        for (Object o : privs) {
            JSONObject json = (JSONObject)o;
            if (json.getIntValue("type") == LeafPriv.TYPE_USER) {
                UserDb user = new UserDb();
                user = user.getUserDb(json.getString("name"));
                if (!user.isLoaded()) {
                    continue;
                }
            }
            LeafPriv leafPriv = new LeafPriv(nodeCode);
            try {
                re = leafPriv.add(json.getString("name"), json.getIntValue("type"));
            } catch (ErrMsgException e) {
                return new Result<>(false, e.getMessage());
            }
        }
        return new Result<>(re);
    }

    @ApiOperation(value = "修改流程权限", notes = "修改流程权限", httpMethod = "POST")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "ID", dataType = "id"),
            @ApiImplicitParam(name = "see", value = "发起", dataType = "Integer"),
            @ApiImplicitParam(name = "modify", value = "查询", dataType = "Integer"),
            @ApiImplicitParam(name = "examine", value = "管理", dataType = "Integer"),
    })
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/updatePriv", method = RequestMethod.POST, produces = {"text/html;", "application/json;charset=UTF-8;"})
    public Result<Object> updatePriv(@RequestParam(value = "id") Integer id, @RequestParam(value = "see", defaultValue = "0") Integer see,
                                     @RequestParam(value = "modify", defaultValue = "0") Integer modify,
                                     @RequestParam(value = "examine", defaultValue = "0") Integer examine) {
        int append = 0, del = 0;
        LeafPriv leafPriv = new LeafPriv();
        leafPriv.setId(id);
        leafPriv.setAppend(append);
        leafPriv.setModify(modify);
        leafPriv.setDel(del);
        leafPriv.setSee(see);
        leafPriv.setExamine(examine);
        return new Result<>(leafPriv.save());
    }

    @ApiOperation(value = "删除流程权限", notes = "删除流程权限", httpMethod = "POST")
    @ApiImplicitParam(name = "id", value = "ID", required = true, dataType = "Integer")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/delPriv", method = RequestMethod.POST, produces = {"text/html;", "application/json;charset=UTF-8;"})
    public Result<Object> delPriv(@RequestParam(value = "id") Integer id) {
        LeafPriv lp = new LeafPriv();
        lp = lp.getLeafPriv(id);
        return new Result<>(lp.del());
    }

    @ApiOperation(value = "获取流程图", notes = "获取流程图", httpMethod = "POST")
    @ApiImplicitParam(name = "typeCode", value = "流程类型", required = true, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/getFlowJson", method = RequestMethod.POST, produces = {"text/html;", "application/json;charset=UTF-8;"})
    public Result<Object> getFlowJson(@RequestParam(value = "typeCode") String typeCode) {
        WorkflowPredefineDb workflowPredefineDb = new WorkflowPredefineDb();
        workflowPredefineDb = workflowPredefineDb.getDefaultPredefineFlow(typeCode);
        JSONObject jsonObject = new JSONObject();
        try {
            org.json.JSONObject flowJsonObject = new org.json.JSONObject(WorkflowActionDb.tran(workflowPredefineDb.getFlowJson()));
            jsonObject = (JSONObject)JSONObject.parse(flowJsonObject.toString());
        } catch (JSONException e) {
            LogUtil.getLog(getClass()).error(e);
        }
        return new Result<>(jsonObject);
    }

    @ApiOperation(value = "取流程中的所有节点，除去节点internalName", notes = "用于所选节点上的人员", httpMethod = "POST")
    @ApiImplicitParam(name = "typeCode", value = "流程类型", required = true, dataType = "String")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/getActionsForProcessByFlowJson", method = RequestMethod.POST, produces = {"text/html;", "application/json;charset=UTF-8;"})
    public Result<Object> getActionsForProcessByFlowJson(@RequestParam(value = "flowJson") String flowJson, @RequestParam(value = "internalName")String internalName) {
        WorkflowActionDb wad = new WorkflowActionDb();
        JSONArray arr = new JSONArray();
        try {
            org.json.JSONObject flowJsonObject = new org.json.JSONObject(WorkflowActionDb.tran(flowJson));
            org.json.JSONObject stateJsonObject = flowJsonObject.getJSONObject("states");
            Iterator ir = stateJsonObject.keys();
            while (ir.hasNext()) {
                String key = (String) ir.next();
                org.json.JSONObject state = stateJsonObject.getJSONObject(key);
                if (!state.getString("ID").equals(internalName)) {
                    org.json.JSONObject props = state.getJSONObject("props");
                    String title = wad.tranReverseForFlowJson(props.getJSONObject("ActionTitle").getString("value"));
                    String jobName = wad.tranReverseForFlowJson(props.getJSONObject("ActionJobName").getString("value"));
                    JSONObject json = new JSONObject();
                    json.put("internalName", state.getString("ID"));
                    json.put("jobName", jobName);
                    json.put("title", title);
                    arr.add(json);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return new Result<>(arr);
    }

    @ApiOperation(value = "获取分配策略", notes = "获取分配策略", httpMethod = "POST")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/listTaskStrategy", method = RequestMethod.POST, produces = {"text/html;", "application/json;charset=UTF-8;"})
    public Result<Object> listTaskStrategy() {
        JSONArray arr = new JSONArray();
        StrategyMgr sm = new StrategyMgr();
        Vector smv = sm.getAllStrategy();
        if (smv != null) {
            for (Object o : smv) {
                StrategyUnit su = (StrategyUnit) o;
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("code", su.getCode());
                jsonObject.put("selectable", su.getIStrategy().isSelectable());
                jsonObject.put("name", su.getName());
                arr.add(jsonObject);
            }
        }
        return new Result<>(arr);
    }

    @ApiOperation(value = "获取视图", notes = "获取视图", httpMethod = "POST")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/flow/listView", method = RequestMethod.POST, produces = {"text/html;", "application/json;charset=UTF-8;"})
    public Result<Object> listView(@RequestParam(value = "typeCode") String typeCode) {
        Leaf lf = new Leaf();
        lf = lf.getLeaf(typeCode);
        JSONArray arr = new JSONArray();
        FormViewDb fvd = new FormViewDb();
        for (Object o : fvd.getViews(lf.getFormCode())) {
            fvd = (FormViewDb) o;
            JSONObject json = new JSONObject();
            json.put("id", fvd.getInt("id"));
            json.put("name", fvd.getString("name"));
            arr.add(json);
        }
        return new Result<>(arr);
    }
}
