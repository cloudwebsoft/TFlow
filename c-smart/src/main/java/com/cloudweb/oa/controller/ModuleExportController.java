package com.cloudweb.oa.controller;

import java.util.Vector;
import javax.servlet.http.HttpServletRequest;

import com.alibaba.fastjson.JSONObject;
import com.cloudweb.oa.utils.ResponseUtil;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.visual.ModuleImportTemplateDb;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import cn.js.fan.util.ParamUtil;
import cn.js.fan.util.ResKeyException;
import com.cloudwebsoft.framework.db.JdbcTemplate;
import com.redmoon.oa.visual.ModuleExportTemplateDb;

@Controller
@RequestMapping("/visual")
public class ModuleExportController {
    @Autowired
    private HttpServletRequest request;

    @Autowired
    private ResponseUtil responseUtil;

    @RequestMapping(value = "/module_export_list", method = RequestMethod.GET)
    public String getModuleExportList(@RequestParam(value = "code", required = false, defaultValue = "")
                                              String code, @RequestParam(value="formCode") String formCode, Model model) {
        model.addAttribute("code", code);
        model.addAttribute("formCode", formCode);

        ModuleExportTemplateDb mitd = new ModuleExportTemplateDb();
        String sql = mitd.getTable().getSql("listForForm");
        Vector v = mitd.list(sql, new Object[]{formCode});

        model.addAttribute("items", v);

        return "visual/module_export_list";
    }

    /**
     * @param id
     * @return
     * @Description:
     */
    @ResponseBody
    @RequestMapping(value = "/delExport", method = RequestMethod.GET, produces = {"text/html;charset=UTF-8;", "application/json;"})
    public String delExport(@RequestParam(value = "id", required = true)
                                    long id) {
        // 注意在注解中加入produces是为了防止乱码
        ModuleExportTemplateDb mid = new ModuleExportTemplateDb();
        mid = mid.getModuleExportTemplateDb(id);
        boolean re = false;
        try {
            re = mid.del();
        } catch (ResKeyException e) {
            // TODO Auto-generated catch block
            LogUtil.getLog(getClass()).error(e);
        }

        JSONObject json = new JSONObject();
        if (re) {
            json.put("ret", 1);
            json.put("msg", "删除成功！");
        } else {
            json.put("ret", 0);
            json.put("msg", "删除失败");
        }

        return json.toString();
    }

    @ResponseBody
    @RequestMapping(value = "/editExport", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;"})
    public String editExport(@RequestParam(value = "code", required = false, defaultValue = "")
                                     String code, @RequestParam(value="formCode", required = false) String formCode, @RequestParam(value = "id", required = true) long id, Model model) {
        boolean re = false;

        String name = ParamUtil.get(request, "name");
        String cols = ParamUtil.get(request, "cols");
        String font_family = ParamUtil.get(request, "font_family");
        int font_size = ParamUtil.getInt(request, "font_size", 14);
        int is_bold = ParamUtil.getInt(request, "is_bold", 0);
        int line_height = ParamUtil.getInt(request, "line_height", 30);
        String fore_color = ParamUtil.get(request, "fore_color");
        String back_color = ParamUtil.get(request, "back_color");
        String roles = ParamUtil.get(request, "roleCodes");

        String bar_font_family = ParamUtil.get(request, "bar_font_family");
        int bar_font_size = ParamUtil.getInt(request, "bar_font_size", 14);
        int bar_is_bold = ParamUtil.getInt(request, "bar_is_bold", 0);
        int bar_line_height = ParamUtil.getInt(request, "bar_line_height", 30);
        String bar_fore_color = ParamUtil.get(request, "bar_fore_color");
        String bar_back_color = ParamUtil.get(request, "bar_back_color");
        String bar_name = ParamUtil.get(request, "bar_name");

        int is_serial_no = ParamUtil.getInt(request, "is_serial_no", 0);

        ModuleExportTemplateDb mid = new ModuleExportTemplateDb();
        mid = mid.getModuleExportTemplateDb(id);
        try {
            mid.set("name", name);
            mid.set("cols", cols);
            mid.set("font_family", font_family);
            mid.set("font_size", font_size);
            mid.set("is_bold", is_bold);
            mid.set("line_height", line_height);
            mid.set("fore_color", fore_color);
            mid.set("back_color", back_color);
            mid.set("roles", roles);

            mid.set("bar_font_family", bar_font_family);
            mid.set("bar_font_size", bar_font_size);
            mid.set("bar_is_bold", bar_is_bold);
            mid.set("bar_line_height", bar_line_height);
            mid.set("bar_fore_color", bar_fore_color);
            mid.set("bar_back_color", bar_back_color);
            mid.set("bar_name", bar_name);

            mid.set("is_serial_no", is_serial_no);

            re = mid.save();
        } catch (ResKeyException e) {
            LogUtil.getLog(getClass()).error(e);
        }

        JSONObject json = new JSONObject();
        if (re) {
            json.put("ret", 1);
            json.put("msg", "操作成功！");
        } else {
            json.put("ret", 0);
            json.put("msg", "操作失败");
        }
        return json.toString();
    }

    @ResponseBody
    @RequestMapping(value = "/addExport", method = RequestMethod.POST, produces = {"text/html;charset=UTF-8;", "application/json;"})
    public String addExport(@RequestParam(value = "code", required = false, defaultValue = "")
                                    String code, @RequestParam(value="formCode", required = false) String formCode) {
        String name = ParamUtil.get(request, "name");
        String cols = ParamUtil.get(request, "cols");

        String font_family = ParamUtil.get(request, "font_family");
        int font_size = ParamUtil.getInt(request, "font_size", 14);
        int is_bold = ParamUtil.getInt(request, "is_bold", 0);
        int height = ParamUtil.getInt(request, "line_height", 30);
        String fore_color = ParamUtil.get(request, "fore_color");
        String back_color = ParamUtil.get(request, "back_color");
        String roles = ParamUtil.get(request, "roleCodes");

        String bar_font_family = ParamUtil.get(request, "bar_font_family");
        int bar_font_size = ParamUtil.getInt(request, "bar_font_size", 14);
        int bar_is_bold = ParamUtil.getInt(request, "bar_is_bold", 0);
        int bar_line_height = ParamUtil.getInt(request, "bar_line_height", 30);
        String bar_fore_color = ParamUtil.get(request, "bar_fore_color");
        String bar_back_color = ParamUtil.get(request, "bar_back_color");
        String bar_name = ParamUtil.get(request, "bar_name");

        int is_serial_no = ParamUtil.getInt(request, "is_serial_no", 0);

        boolean re = false;
        ModuleExportTemplateDb med = new ModuleExportTemplateDb();
        try {
            int isDefault = med.getDefault(formCode) == null ? 1 : 0;
            re = med.create(new JdbcTemplate(), new Object[]{name, formCode, cols, font_family, font_size, is_bold, height, fore_color, back_color, roles, bar_font_family, bar_font_size, bar_is_bold, bar_line_height, bar_fore_color, bar_back_color, bar_name, is_serial_no, isDefault});
        } catch (ResKeyException e) {
            LogUtil.getLog(getClass()).error(e);
        }

        JSONObject json = new JSONObject();
        if (re) {
            json.put("ret", 1);
            json.put("msg", "操作成功！");
        } else {
            json.put("ret", 0);
            json.put("msg", "操作失败");
        }
        return json.toString();
    }

    @ApiOperation(value = "设置默认导出模板", notes = "设置默认导出模板", httpMethod = "GET")
    @ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
    @ResponseBody
    @RequestMapping(value = "/setDefaultExportTempalte", method = {RequestMethod.POST}, produces = {"application/json;charset=UTF-8;"})
    public String setDefaultExportTempalte(@RequestParam(value = "moduleCode") String moduleCode, @RequestParam(value = "id") Integer id, @RequestParam(value = "isDefault") Boolean isDefault) {
        ModuleExportTemplateDb moduleExportTemplateDb = new ModuleExportTemplateDb();
        return responseUtil.getResJson(moduleExportTemplateDb.setDefault(moduleCode, id, isDefault)).toString();
    }
}
