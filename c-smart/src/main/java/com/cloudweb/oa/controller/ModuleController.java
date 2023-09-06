package com.cloudweb.oa.controller;

import java.awt.*;
import java.io.*;
import java.lang.Boolean;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import bsh.EvalError;
import bsh.Interpreter;
import cn.js.fan.util.*;
import cn.js.fan.util.file.FileUtil;
import cn.js.fan.web.Global;
import cn.js.fan.web.SkinUtil;
import com.alibaba.fastjson.JSON;
import com.cloudweb.oa.api.*;
import com.cloudweb.oa.cache.FlowFormDaoCache;
import com.cloudweb.oa.cache.VisualFormDaoCache;
import com.cloudweb.oa.entity.Account;
import com.cloudweb.oa.entity.Department;
import com.cloudweb.oa.exception.ValidateException;
import com.cloudweb.oa.permission.ModuleTreePermission;
import com.cloudweb.oa.security.AuthUtil;
import com.cloudweb.oa.service.*;
import com.cloudweb.oa.utils.*;
import com.cloudweb.oa.utils.I18nUtil;
import com.cloudweb.oa.utils.ResponseUtil;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudweb.oa.vo.Result;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObjectInputStream;
import com.redmoon.kit.util.FileUpload;
import com.redmoon.oa.Config;
import com.redmoon.oa.base.IAttachment;
import com.redmoon.oa.base.IFormMacroCtl;
import com.redmoon.oa.basic.TreeSelectDb;
import com.redmoon.oa.basic.TreeSelectView;
import com.redmoon.oa.dept.DeptChildrenCache;
import com.redmoon.oa.dept.DeptDb;
import com.redmoon.oa.flow.*;
import com.redmoon.oa.security.SecurityUtil;
import com.redmoon.oa.shell.BSHShell;
import com.redmoon.oa.util.*;
import com.redmoon.oa.visual.FormDAO;
import com.redmoon.oa.visual.*;
import com.redmoon.oa.visual.Attachment;
import com.redmoon.oa.visual.AttachmentLogDb;
import com.redmoon.oa.visual.FormDAOMgr;
import com.redmoon.oa.visual.FormUtil;
import com.redmoon.oa.visual.Render;
import io.swagger.annotations.*;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.write.*;
import jxl.write.Label;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.tools.zip.ZipOutputStream;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.cloudwebsoft.framework.db.JdbcTemplate;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.flow.macroctl.MacroCtlMgr;
import com.redmoon.oa.flow.macroctl.MacroCtlUnit;
import com.redmoon.oa.person.UserDb;
import com.redmoon.oa.person.UserMgr;
import com.redmoon.oa.pvg.Privilege;
import com.redmoon.oa.pvg.RoleDb;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.ui.SkinMgr;

import cn.js.fan.db.ListResult;
import cn.js.fan.db.Paginator;
import cn.js.fan.db.ResultIterator;
import cn.js.fan.db.ResultRecord;
import sun.security.util.Debug;

@Api(tags = "智能模块")
@Slf4j
@Controller
@RequestMapping("/visual")
public class ModuleController {
	@Autowired
	private HttpServletRequest request;

	@Autowired
	private ResponseUtil responseUtil;

	@Autowired
	private I18nUtil i18nUtil;

	@Autowired
	IFileService fileService;

	@Autowired
	AuthUtil authUtil;

	@Autowired
	ConfigUtil configUtil;

	@Autowired
	MacroCtlService macroCtlService;

	@Autowired
	SysUtil sysUtil;

	@Autowired
	WorkflowService workflowService;

	@Autowired
	ModuleTreePermission moduleTreePermission;

	@Autowired
	ZipUtil zipUtil;

	@Autowired
	FlowFormDaoCache flowFormDaoCache;

	@Autowired
	VisualFormDaoCache visualFormDaoCache;

	@ApiOperation(value = "导入Excel至嵌套表", notes = "导入Excel至嵌套表", httpMethod = "POST")
	@ApiImplicitParams({
			@ApiImplicitParam(name = "模块编码", value = "moduleCode", dataType = "String"),
			@ApiImplicitParam(name = "关联模块编码", value = "moduleCodeRelated", dataType = "String"),
			@ApiImplicitParam(name = "主表的id", value = "parentId", dataType = "long"),
	})
	@ApiResponses({ @ApiResponse(code = 200, message = "操作成功") })
	@ResponseBody
	@RequestMapping(value = "/importExcelNest", method = RequestMethod.POST, produces = {"application/json;charset=UTF-8;"})
	public Result<Object> importExcelNest() {
		long parentId = ParamUtil.getLong(request, "parentId", -1);
		INestSheetCtl ntc = macroCtlService.getNestSheetCtl();
		ServletContext application = request.getSession().getServletContext();
		int rows = 0;
		try {
			rows = ntc.uploadExcel(application, request, parentId);
		} catch (ErrMsgException e) {
			LogUtil.getLog(getClass()).error(e);
		}
		return new Result<>(rows);
	}


	@RequestMapping("/exportExcelRelate")
	public void exportExcelRelate(HttpServletResponse response) throws IOException, ErrMsgException {
		String moduleCodeRelated = ParamUtil.get(request, "moduleCodeRelated");
		if ("".equals(moduleCodeRelated)) {
			// nest_sheet_view.jsp中传的是formCodeRelated
			moduleCodeRelated = ParamUtil.get(request, "formCodeRelated");
		}
		ModuleSetupDb msd = new ModuleSetupDb();
		msd = msd.getModuleSetupDbOrInit(moduleCodeRelated);
		String formCodeRelated = msd.getString("form_code");

		FormDb fd = new FormDb();
		fd = fd.getFormDb(formCodeRelated);
		if (!fd.isLoaded()) {
			throw new ErrMsgException("表单不存在！");
		}
		String op = ParamUtil.get(request, "op");

		String orderBy = ParamUtil.get(request, "orderBy");
		String sort = ParamUtil.get(request, "sort");
		if ("".equals(orderBy)) {
			Privilege pvg = new Privilege();
			String userName = pvg.getUser(request);
			String filter = StrUtil.getNullStr(msd.getFilter(userName)).trim();
			boolean isComb = filter.startsWith("<items>") || "".equals(filter);
			// 如果是组合条件，则赋予后台设置的排序字段
			if (isComb) {
				orderBy = StrUtil.getNullStr(msd.getString("orderby"));
				sort = StrUtil.getNullStr(msd.getString("sort"));
			}
			if ("".equals(orderBy)) {
				orderBy = "id";
			}
		}
		if ("".equals(sort)) {
			sort = "desc";
		}

		request.setAttribute(ConstUtil.IS_FOR_EXPORT, "true");
		String moduleCode = ParamUtil.get(request, "moduleCode");
		ModuleSetupDb msdParent = new ModuleSetupDb();
		msdParent = msdParent.getModuleSetupDb(moduleCode);
		String formCode = msdParent.getString("form_code"); // 主模块的表单编码
		String mode = ParamUtil.get(request, "mode");
		String tagName = ParamUtil.get(request, "tagName");

		// 通过选项卡标签关联
		boolean isSubTagRelated = "subTagRelated".equals(mode);

		if (isSubTagRelated) {
			String tagUrl = ModuleUtil.getModuleSubTagUrl(moduleCode, tagName);
			JSONObject json = JSONObject.parseObject(tagUrl);
			if (json.containsKey("formRelated") && !StrUtil.isEmpty(json.getString("formRelated"))) {
				// formCodeRelated = json.getString("formRelated");
				moduleCodeRelated = json.getString("formRelated");
				msd = msd.getModuleSetupDb(moduleCodeRelated);
				formCodeRelated = msd.getString("form_code");
			} else {
				throw new ErrMsgException("选项卡关联配置不正确！");
			}
		}

		String relateFieldValue = "";
		long parentId = ParamUtil.getLong(request, "parentId", -1);
		if (parentId == -1) {
			throw new ErrMsgException("缺少父模块记录的ID！");
		} else {
			if (!isSubTagRelated) {
				com.redmoon.oa.visual.FormDAOMgr fdm = new com.redmoon.oa.visual.FormDAOMgr(formCode);
				relateFieldValue = fdm.getRelateFieldValue(parentId, moduleCodeRelated);
				if (relateFieldValue == null) {
					// 20171016 fgf 如果取得的为null，则说明可能未设置两个模块相关联，但是为了能够使简单选项卡能链接至关联模块，此处应允许不关联
					relateFieldValue = SQLBuilder.IS_NOT_RELATED;
				}
			}
		}

		request.setAttribute(ConstUtil.IS_FOR_EXPORT, "true");
		request.setAttribute(ModuleUtil.MODULE_SETUP, msd);
		int nestType = ParamUtil.getInt(request, "nestType", MacroCtlUnit.NEST_TYPE_NONE);
		request.setAttribute("nestType", String.valueOf(nestType));

		// 如果是嵌套表，则根据ID顺序排序
		if (nestType != MacroCtlUnit.NEST_TYPE_NONE) {
			sort = "asc";
		}

		String sql;
		String rowIds = ParamUtil.get(request, "rowIds");
		if (!"".equals(rowIds)) {
			sql = "select id from " + FormDb.getTableName(fd.getCode()) + " where id in (" + rowIds + ")";
		} else {
			String[] arySQL = SQLBuilder.getModuleListRelateSqlAndUrlStr(request, fd, op, orderBy, sort, relateFieldValue);
			sql = arySQL[0];
		}

		FormDAO fdao = new FormDAO();
		Vector v = fdao.list(formCodeRelated, sql);

		DebugUtil.i(getClass(), "exportExcelRelate", sql);

		String fileName = fd.getName();
		long templateId = ParamUtil.getLong(request, "templateId", -1);
		ModuleExportTemplateDb metd = new ModuleExportTemplateDb();
		metd = metd.getDefault(formCodeRelated);
		if (metd != null) {
			templateId = metd.getInt("id");
			fileName = metd.getString("name");
		}

		String[] fields;
		String cols = ParamUtil.get(request, "cols");
		// 当表格带有序号时，cols为逗号开头
		if (cols.startsWith(",")) {
			cols = cols.substring(1);
		}
		if (!"".equals(cols)) {
			fields = StrUtil.split(cols, ",");
		} else {
			if (nestType != MacroCtlUnit.NEST_TYPE_TABLE) {
				fields = msd.getColAry(false, "list_field");
			} else {
				String nestFieldName = ParamUtil.get(request, "nestFieldName");
				String parentFormCode = formCode;
				JSONObject json = null;
				int formViewId = -1;
				FormField nestField = null;
				String nestFormCode = "";
				if (!nestFieldName.equals("")) {
					FormDb parentFd = new FormDb();
					parentFd = parentFd.getFormDb(parentFormCode);
					nestField = parentFd.getFormField(nestFieldName);
					if (nestField == null) {
						throw new ErrMsgException("父表单（" + parentFormCode + "）中的嵌套表字段：" + nestFieldName + " 不存在");
					}
					String defaultVal = StrUtil.decodeJSON(nestField.getDescription());
					json = JSONObject.parseObject(defaultVal);
					nestFormCode = json.getString("destForm");
					if (json.containsKey("formViewId") && !StrUtil.isEmpty(json.getString("formViewId"))) {
						formViewId = StrUtil.toInt(json.getString("formViewId"), -1);
					} else {
						throw new ErrMsgException("嵌套表格未视定视图");
					}
				}

				String viewContent = "";
				if (formViewId != -1) {
					FormViewDb formViewDb = new FormViewDb();
					formViewDb = formViewDb.getFormViewDb(formViewId);
					viewContent = formViewDb.getString("content");
				} else {
					viewContent = FormViewMgr.makeViewContent(msd);
				}

				MacroCtlService macroCtlService = SpringUtil.getBean(MacroCtlService.class);
				INestTableCtl nestTableCtl = macroCtlService.getNestTableCtl();
				Vector fieldV = nestTableCtl.parseFieldsByView(fd, viewContent);
				fields = new String[fieldV.size()];
				int i = 0;
				Iterator ir = fieldV.iterator();
				while (ir.hasNext()) {
					FormField ff = (FormField) ir.next();
					fields[i] = ff.getName();
					i++;
				}
			}
		}

		response.setContentType("application/vnd.ms-excel");
		response.setHeader("Content-disposition", "attachment; filename=" + StrUtil.GBToUnicode(fileName) + ".xls");

		OutputStream os = response.getOutputStream();
		Workbook wb = null;
		WritableWorkbook wwb = null;
		WritableSheet ws = null;
		try {
			// File file = new File(Global.getAppPath(request) + "visual/template/blank.xls");
			// Workbook wb = Workbook.getWorkbook(file);
			InputStream inputStream = configUtil.getFile("templ/blank.xls");
			wb = Workbook.getWorkbook(inputStream);

			// 20220801 jxl bug: 源代码中byte数组data的最大长度被定义为112，当被传入的参数达到一定长度时就会出错
			WorkbookSettings settings = new WorkbookSettings();
			settings.setWriteAccess(null);

			UserMgr um = new UserMgr();

			// 打开一个文件的副本，并且指定数据写回到原文件
			wwb = Workbook.createWorkbook(os, wb, settings);
			ws = wwb.getSheet(0);

			int len = 0;
			if (fields != null) {
				len = fields.length;
			}

			/*
			 * WritableFont.createFont("宋体")：设置字体为宋体
			 * 10：设置字体大小
			 * WritableFont.NO_BOLD:设置字体非加粗（BOLD：加粗     NO_BOLD：不加粗）
			 * false：设置非斜体
			 * UnderlineStyle.NO_UNDERLINE：没有下划线
			 */
			boolean isBar = false;
			int rowHeader = 0;
			Map mapWidth = new HashMap();
			WritableFont font;
			String backColor = "", foreColor = "";
			if (templateId != -1) {
				String barName = StrUtil.getNullStr(metd.getString("bar_name"));
				if (!"".equals(barName)) {
					isBar = true;
				}

				String fontFamily = metd.getString("font_family");
				int fontSize = metd.getInt("font_size");
				backColor = metd.getString("back_color");
				foreColor = metd.getString("fore_color");
				boolean isBold = metd.getInt("is_bold") == 1;
				if (isBold) {
					font = new WritableFont(WritableFont.createFont(fontFamily),
							fontSize,
							WritableFont.BOLD);
				} else {
					font = new WritableFont(WritableFont.createFont(fontFamily),
							fontSize,
							WritableFont.NO_BOLD);
				}

				if (!"".equals(foreColor)) {
					Color color = Color.decode(foreColor); // 自定义的颜色
					wwb.setColourRGB(Colour.BLUE, color.getRed(), color.getGreen(), color.getBlue());
					font.setColour(Colour.BLUE);
				}

				String columns = metd.getString("cols");

				boolean isSerialNo = metd.getString("is_serial_no").equals("1");
				if (isSerialNo) {
					columns = columns.substring(1); // [{}, {},...]去掉[
					columns = "[{\"field\":\"serialNoForExp\",\"title\":\"序号\",\"link\":\"#\",\"width\":80,\"name\":\"serialNoForExp\"}," + columns;
				}

				JSONArray arr = JSONArray.parseArray(columns);
				StringBuffer colsSb = new StringBuffer();
				for (int i = 0; i < arr.size(); i++) {
					JSONObject json = arr.getJSONObject(i);

					// LogUtil.getLog(getClass()).info(getClass() + " " + i + " " + json.getInt("width"));
					ws.setColumnView(i, (int) (json.getIntValue("width") * 0.09 * 0.94)); // 设置列的宽度 ，单位是自己根据实际的像素值推算出来的

					StrUtil.concat(colsSb, ",", json.getString("field"));
					mapWidth.put(json.getString("field"), json.getIntValue("width"));
				}

				String listField = colsSb.toString();
				fields = StrUtil.split(listField, ",");
				len = fields.length;

				if (isBar) {
					WritableFont barFont;
					String barBackColor = metd.getString("bar_back_color");
					String barForeColor = metd.getString("bar_fore_color");
					String barFontFamily = metd.getString("bar_font_family");
					int barFontSize = metd.getInt("bar_font_size");
					boolean isBarbBold = metd.getInt("bar_is_bold") == 1;
					if (isBarbBold) {
						barFont = new WritableFont(WritableFont.createFont(barFontFamily),
								barFontSize,
								WritableFont.BOLD);
					} else {
						barFont = new WritableFont(WritableFont.createFont(barFontFamily),
								barFontSize,
								WritableFont.NO_BOLD);
					}

					if (!"".equals(barForeColor)) {
						Color color = Color.decode(barForeColor); // 自定义的颜色
						wwb.setColourRGB(Colour.RED, color.getRed(), color.getGreen(), color.getBlue());
						barFont.setColour(Colour.RED);
					}

					WritableCellFormat barFormat = new WritableCellFormat(barFont);
					// 水平居中对齐
					barFormat.setAlignment(Alignment.CENTRE);
					// 竖直方向居中对齐
					barFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
					barFormat.setBorder(Border.ALL, BorderLineStyle.THIN);

					if (!"".equals(barBackColor)) {
						Color bClr = Color.decode(barBackColor); // 自定义的颜色
						wwb.setColourRGB(Colour.GREEN, bClr.getRed(), bClr.getGreen(), bClr.getBlue());
						barFormat.setBackground(Colour.GREEN);
					}

					Label a = new Label(0, 0, barName, barFormat);
					ws.addCell(a);

					ws.mergeCells(0, 0, len - 1, 0);

					ws.setRowView(0, metd.getInt("bar_line_height") * 10); // 设置行的高度 ，setRowView(row, 200) 在excel中的实际高度为10像素

					rowHeader = 1;
				}
				ws.setRowView(rowHeader, metd.getInt("line_height") * 10); // 设置行的高度 ，setRowView(row, 200) 在excel中的实际高度为10像素
			} else {
				font = new WritableFont(WritableFont.createFont("宋体"), 12, WritableFont.BOLD);
			}

			WritableCellFormat wcFormat = new WritableCellFormat(font);
			//水平居中对齐
			wcFormat.setAlignment(Alignment.CENTRE);
			//竖直方向居中对齐
			wcFormat.setVerticalAlignment(VerticalAlignment.CENTRE);
			wcFormat.setBorder(Border.ALL, BorderLineStyle.THIN);

			if (templateId != -1) {
				if (!"".equals(backColor)) {
					Color color = Color.decode(backColor); // 自定义的颜色
					wwb.setColourRGB(Colour.ORANGE, color.getRed(), color.getGreen(), color.getBlue());
					wcFormat.setBackground(Colour.ORANGE);
				}
			}

			FormMgr fm = new FormMgr();
			for (int i = 0; i < len; i++) {
				String fieldName = fields[i];
				String title;
				if (fieldName.equals("serialNoForExp")) {
					title = "序号";
				} else if (fieldName.equals("cws_creator")) {
					title = "创建者";
				} else if (fieldName.equals("ID")) {
					title = "ID";
				} else if (fieldName.equals("cws_status")) {
					title = "状态";
				} else if (fieldName.equals("cws_flag")) {
					title = "冲抵状态";
				}
				else if ("cws_create_date".equals(fieldName)) {
					title = "创建时间";
				} else if ("cws_modify_date".equals(fieldName)) {
					title = "修改时间";
				}
				else {
					if (fieldName.startsWith("main:")) {
						String[] ary = StrUtil.split(fieldName, ":");
						FormDb mainFormDb = fm.getFormDb(ary[1]);
						title = mainFormDb.getFieldTitle(ary[2]);
					} else if (fieldName.startsWith("other:")) {
						String[] ary = StrUtil.split(fieldName, ":");
						FormDb otherFormDb = fm.getFormDb(ary[2]);
						String showFieldName = ary[4];
						if ("id".equalsIgnoreCase(showFieldName)) {
							title = otherFormDb.getName() + "ID";
						} else {
							title = otherFormDb.getFieldTitle(showFieldName);
						}
					} else {
						title = fd.getFieldTitle(fieldName);
					}
					if ("".equals(title)) {
						title = fieldName + "不存在";
					}
				}

				Label a = new Label(i, rowHeader, title, wcFormat);
				ws.addCell(a);
			}

			Iterator ir = v.iterator();

			int j = rowHeader + 1;
			int k = 0;

			MacroCtlMgr mm = new MacroCtlMgr();
			while (ir.hasNext()) {
				fdao = (FormDAO) ir.next();
				// 置SQL宏控件中需要用到的fdao
				RequestUtil.setFormDAO(request, fdao);
				for (int i = 0; i < len; i++) {
					String fieldName = fields[i];
					String fieldValue = "";
					if (fieldName.equals("serialNoForExp")) {
						fieldValue = String.valueOf(++k);
					} else if (fieldName.equals("cws_creator")) {
						fieldValue = StrUtil.getNullStr(um.getUserDb(fdao.getCreator()).getRealName());
					} else if (fieldName.equals("cws_progress")) {
						fieldValue = String.valueOf(fdao.getCwsProgress());
					} else if (fieldName.equals("ID")) {
						fieldValue = String.valueOf(fdao.getId());
					} else if (fieldName.equals("cws_status")) {
						fieldValue = com.redmoon.oa.flow.FormDAO.getStatusDesc(fdao.getCwsStatus());
					} else if (fieldName.equals("cws_flag")) {
						fieldValue = String.valueOf(fdao.getCwsFlag());
					}
					else if ("cws_create_date".equals(fieldName)) {
						fieldValue = DateUtil.format(fdao.getCwsCreateDate(), "yyyy-MM-dd");
					} else if ("cws_modify_date".equals(fieldName)) {
						fieldValue = DateUtil.format(fdao.getCwsModifyDate(), "yyyy-MM-dd");
					}
					else {
						if (fieldName.startsWith("main")) {
							String[] ary = StrUtil.split(fieldName, ":");
							FormDb mainFormDb = fm.getFormDb(ary[1]);
							com.redmoon.oa.visual.FormDAOMgr fdmMain = new com.redmoon.oa.visual.FormDAOMgr(mainFormDb);
							com.redmoon.oa.visual.FormDAO fdaoMain = new FormDAO();
							fdaoMain = fdaoMain.getFormDAOByCache(parentId, mainFormDb);
							FormField ff = mainFormDb.getFormField(ary[2]);
							if (ff != null && ff.getType().equals(FormField.TYPE_MACRO)) {
								MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
								if (mu != null) {
									fieldValue = mu.getIFormMacroCtl().converToHtml(request, ff, fdaoMain.getFieldValue(ary[2]));
								}
							} else {
								fieldValue = fdmMain.getFieldValueOfMain(parentId, ary[2]);
							}
						} else if (fieldName.startsWith("other:")) {
							fieldValue = com.redmoon.oa.visual.FormDAOMgr.getFieldValueOfOther(request, fdao, fieldName);
						} else {
							FormField ff = fd.getFormField(fieldName);
							if (ff.getType().equals(FormField.TYPE_MACRO)) {
								MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
								if (mu != null && !mu.getCode().equals("macro_raty")) {
									fieldValue = StrUtil.getAbstract(request, mu.getIFormMacroCtl().converToHtml(request, ff, fdao.getFieldValue(fieldName)), 1000, "");
									// fieldValue = mu.getIFormMacroCtl().converToHtml(request, ff, fdao.getFieldValue(fieldName));
								} else {
									fieldValue = fdao.getFieldValue(fieldName);
								}
							} else {
								fieldValue = fdao.getFieldValue(fieldName);
							}
						}
					}

					Label a = new Label(i, j, fieldValue);
					ws.addCell(a);
				}

				j++;
			}
			wwb.write();
			wwb.close();
			wb.close();
		} catch (Exception ex) {
			// LogUtil.getLog(getClass()).info(e.toString());
			LogUtil.getLog(getClass()).error(ex);
			try {
				if (wwb != null) {
					try {
						wwb.close();
					} catch (WriteException e) {
						LogUtil.getLog(getClass()).error(e);
					}
				}
				if (wb != null) {
					wb.close();
				}
			} catch (IOException e) {
				LogUtil.getLog(getClass()).error(e);
			}
		} finally {
			os.close();
		}
	}
}