package com.cloudweb.oa.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import bsh.EvalError;
import bsh.Interpreter;
import cn.js.fan.db.ResultIterator;
import cn.js.fan.db.ResultRecord;
import cn.js.fan.util.*;
import cn.js.fan.web.Global;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cloudweb.oa.api.INestTableCtl;
import com.cloudweb.oa.cache.UserCache;
import com.cloudweb.oa.service.IFileService;
import com.cloudweb.oa.service.MacroCtlService;
import com.cloudweb.oa.utils.ConfigUtil;
import com.cloudweb.oa.utils.ResponseUtil;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudweb.oa.vo.Result;
import com.redmoon.kit.util.FileUpload;
import com.redmoon.oa.flow.FormViewDb;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.util.BeanShellUtil;
import com.redmoon.oa.visual.*;
import io.swagger.annotations.*;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import com.cloudwebsoft.framework.db.JdbcTemplate;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.basic.SelectDb;
import com.redmoon.oa.basic.SelectMgr;
import com.redmoon.oa.basic.SelectOptionDb;
import com.redmoon.oa.dept.DeptUserDb;
import com.redmoon.oa.flow.FormDb;
import com.redmoon.oa.flow.FormField;
import com.redmoon.oa.flow.macroctl.MacroCtlMgr;
import com.redmoon.oa.flow.macroctl.MacroCtlUnit;
import com.redmoon.oa.person.UserDb;
import com.redmoon.oa.pvg.Privilege;

@Controller
public class ModuleImportController {
	@Autowired  
	private HttpServletRequest request;

	@Autowired
	private ResponseUtil responseUtil;

	@Autowired
	private IFileService fileService;

	/***
	 * 上传Excel文件
	 * 
	 * @param file
	 * @return
	 */
	@RequestMapping("/modular/importUpload")
	public String importUpload(@RequestParam(value = "excel", required = true) MultipartFile file,
			 @RequestParam("code") String code,
			 @RequestParam("formCode") String formCode, @RequestParam(value="isBatch", defaultValue="0") int isBatch, Model model) {
		model.addAttribute("code", code);
		model.addAttribute("formCode", formCode);
		// 判断文件是否为空
		if (!file.isEmpty()) {
			if (isBatch == 0) {
				try {
					List<List> lists = getCellsOfSingleTemplate(file);
					model.addAttribute("cells", lists);
				} catch (InvalidFormatException e) {
					LogUtil.getLog(getClass()).error(e);
				}
			}
			else {
				InputStream in = null;
				try {
					in = file.getInputStream();
					String pa = StrUtil.getFileExt(file.getOriginalFilename());
					if ("xls".equals(pa)) {
						// 读取xls格式的excel文档
						org.apache.poi.ss.usermodel.Workbook w = WorkbookFactory.create(in);
						// 获取sheet
						for (int i = 0; i < w.getNumberOfSheets(); i++) {
							org.apache.poi.ss.usermodel.Sheet sheet = w.getSheetAt(i);
							if (sheet != null) {
								org.apache.poi.ss.usermodel.Cell cell = null;
								// 获取第一行
								org.apache.poi.ss.usermodel.Row row = sheet.getRow(0);
								if (row != null) {
									int colcount = row.getLastCellNum();
									String[] cols = new String[colcount];
									// 获取每一单元格
									for (int m = 0; m < colcount; m++) {
										cell = row.getCell(m);
										if (cell == null) {
											continue;
										}

										cell.setCellType(CellType.STRING);
										String val = cell.getStringCellValue();
										cols[m] = val;
									}
									model.addAttribute("cols", cols);
									break;
								}
							}
						}
					} else if ("xlsx".equals(pa)) {
						XSSFWorkbook w = (XSSFWorkbook) WorkbookFactory.create(in);
						for (int i = 0; i < w.getNumberOfSheets(); i++) {
							XSSFSheet sheet = w.getSheetAt(i);
							if (sheet != null) {
								XSSFCell cell = null;
								XSSFRow row = sheet.getRow(0);
								if (row != null) {
									int colcount = row.getLastCellNum();
									String[] cols = new String[colcount];

									for (int m = 0; m < colcount; m++) {
										cell = row.getCell(m);
										if (cell == null) {
											continue;
										}
										cell.setCellType(CellType.STRING);
										String val = cell.getStringCellValue();
										cols[m] = val;
									}
									model.addAttribute("cols", cols);
									break;
								}
							}
						}
					}
				} catch (Exception e) {
					// LogUtil.getLog(SignMgr.class).error(e.getMessage());
					LogUtil.getLog(getClass()).error(e);
				} finally {
					if (in != null) {
						try {
							in.close();
						} catch (IOException e) {
							LogUtil.getLog(getClass()).error(e);
						}
					}
				}

				String name = file.getOriginalFilename();
				String ext = StrUtil.getFileExt(name);
				String diskName = FileUpload.getRandName() + "." + ext;
				try {
					fileService.write(file, FileUpload.TEMP_PATH, diskName);
				} catch (IOException e) {
					LogUtil.getLog(getClass()).error(e);
				}
				model.addAttribute("xlsTmpPath", FileUpload.TEMP_PATH + "/" + diskName);
			}
		}
		else {
			request.setAttribute("info", "请上传文件");
			return "error";
		}
		if (isBatch == 1) {
			return "visual/module_import_cols";
		}
		else {
			return "visual/module_import_cells";
		}
	}

	public List<List> getCellsOfSingleTemplate(@RequestParam(value = "file") MultipartFile file) throws InvalidFormatException {
		List<List> lists = new ArrayList<List>();
		InputStream in = null;
		try {
			in = file.getInputStream();
			String pa = StrUtil.getFileExt(file.getOriginalFilename());
			if ("xls".equals(pa)) {
				// 读取xls格式的excel文档
				HSSFWorkbook w = (HSSFWorkbook) WorkbookFactory.create(in);
				// 获取sheet
				HSSFSheet sheet = w.getSheetAt(0);
				if (sheet != null) {
					// 获取行数
					int rowcount = sheet.getLastRowNum();
					HSSFCell cell = null;

					// 获取每一行
					for (int k = 0; k < rowcount; k++) {
						HSSFRow row = sheet.getRow(k);
						if (row != null) {
							if (isXlsRowEmpty(row)) {
								continue;
							}
							int colcount = row.getLastCellNum();
							List<String> list = new ArrayList<String>();
							// 获取每一单元格
							for (int m = 0; m < colcount; m++) {
								cell = row.getCell(m);

								if (cell == null) {
									list.add("");
									continue;
								}

								if (CellType.NUMERIC == cell.getCellType() && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
									Date date = org.apache.poi.ss.usermodel.DateUtil.getJavaDate(cell.getNumericCellValue());
									list.add(DateUtil.format(date, "yyyy-MM-dd"));
								} else {
									cell.setCellType(CellType.STRING);
									String val = cell.getStringCellValue().trim();
									list.add(val);
								}
							}
							lists.add(list);
						}
					}
				}
			} else if ("xlsx".equals(pa)) {
				XSSFWorkbook w = (XSSFWorkbook) WorkbookFactory.create(in);
				XSSFSheet sheet = w.getSheetAt(0);
				if (sheet != null) {
					int rowcount = sheet.getLastRowNum();
					XSSFCell cell = null;
					for (int k = 0; k < rowcount; k++) {
						XSSFRow row = sheet.getRow(k);
						if (row != null) {
							// 如果是空行则跳过
							if (isXlsxRowEmpty(row)) {
								continue;
							}
							int colcount = row.getLastCellNum();
							List<String> list = new ArrayList<String>();
							for (int m = 0; m < colcount; m++) {
								cell = row.getCell(m);

								if (cell == null) {
									list.add("");
									continue;
								}

								if (CellType.NUMERIC == cell.getCellType() && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
									Date date = org.apache.poi.ss.usermodel.DateUtil.getJavaDate(cell.getNumericCellValue());
									list.add(DateUtil.format(date, "yyyy-MM-dd"));
								} else {
									cell.setCellType(CellType.STRING);
									String val = cell.getStringCellValue().trim();
									list.add(val);
								}
							}
							lists.add(list);
						}
					}
				}
			}
		} catch (IOException e) {
			LogUtil.getLog(getClass()).error(e);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					LogUtil.getLog(getClass()).error(e);
				}
			}
		}
		return lists;
	}

	public static boolean isXlsRowEmpty(HSSFRow row) {
		for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
			HSSFCell cell = row.getCell(c);
			if (cell != null && cell.getCellType() != CellType.BLANK) {
				return false;
			}
		}
		return true;
	}

	public static boolean isXlsxRowEmpty(XSSFRow row) {
		for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
			XSSFCell cell = row.getCell(c);
			if (cell != null && cell.getCellType() != CellType.BLANK) {
				return false;
			}
		}
		return true;
	}

	@ApiOperation(value = "下载嵌套表格模板", notes = "下载嵌套表格模板", httpMethod = "POST")
	@RequestMapping("/visual/downloadExcelTemplForNest")
	@ApiImplicitParams({
			@ApiImplicitParam(name = "嵌套表格字段名", value = "nestFieldName", dataType = "String"),
			@ApiImplicitParam(name = "父模块编码", value = "parentFormCode", dataType = "String"),
			@ApiImplicitParam(name = "模块编码", value = "moduleCode", dataType = "long"),
	})
	public void downloadExcelTemplForNest(HttpServletResponse response, @RequestParam(value = "nestFieldName") String nestFieldName, @RequestParam(value = "parentFormCode") String parentFormCode, @RequestParam(value = "moduleCode") String moduleCode) throws IOException, ErrMsgException {
		ModuleSetupDb msd = new ModuleSetupDb();
		msd = msd.getModuleSetupDb(moduleCode);
		if (msd == null) {
			throw new ErrMsgException("模块:" + moduleCode + "不存在");
		}
		String formCode = msd.getString("form_code");

		FormDb fd = new FormDb();
		String[] fields = null;
		String[] fieldTitles = null;
		int templateId = -1;

		JSONObject json = null;
		int formViewId = -1;
		FormField nestField = null;
		if (!"".equals(nestFieldName)) {
			FormDb parentFd = new FormDb();
			parentFd = parentFd.getFormDb(parentFormCode);
			nestField = parentFd.getFormField(nestFieldName);
			if (nestField == null) {
				throw new ErrMsgException("父表单（" + parentFormCode + "）中的嵌套表字段：" + nestFieldName + " 不存在");
			}

			String defaultVal = StrUtil.decodeJSON(nestField.getDescription());
			json = JSONObject.parseObject(defaultVal);

			boolean isView = false;
			if (json.containsKey("formViewId")) {
				formViewId = StrUtil.toInt((String) json.get("formViewId"), -1);
				if (formViewId!=-1) {
					isView = true;
				}
			}
			if (isView) {
				FormViewDb formViewDb = new FormViewDb();
				formViewDb = formViewDb.getFormViewDb(formViewId);

				fd = fd.getFormDb(formCode);
				if (!fd.isLoaded()) {
					throw new ErrMsgException("表单不存在！");
				}

				MacroCtlService macroCtlService = SpringUtil.getBean(MacroCtlService.class);
				INestTableCtl nestTableCtl = macroCtlService.getNestTableCtl();
				Vector<FormField> fieldsV = nestTableCtl.parseFieldsByView(fd, formViewDb.getString("content"));
				fields = new String[fieldsV.size()];
				int i = 0;
				for (FormField ff : fieldsV) {
					fields[i] = ff.getName();
					i++;
				}
			}
			else {
				fd = fd.getFormDb(formCode);
				if (!fd.isLoaded()) {
					throw new ErrMsgException("表单不存在！");
				}

				ModuleImportTemplateDb mid = new ModuleImportTemplateDb();
				mid = mid.getDefault(moduleCode);

				// 向下兼容，旧版中没有path
				if (mid != null) {
					String path = mid.getString("path");
					if (!StrUtil.isEmpty(path)) {
						String ext = StrUtil.getFileExt(path);
						fileService.download(response, mid.getString("name") + "." + ext, path);
						return;
					}

					templateId = mid.getInt("id");
					String rules = mid.getString("rules");
					JSONArray arr = JSONArray.parseArray(rules);
					if (arr.size()>0) {
						fields = new String[arr.size()];
						fieldTitles = new String[arr.size()];
						for (int i = 0; i < arr.size(); i++) {
							JSONObject jsonObject = (JSONObject) arr.get(i);
							fields[i] = jsonObject.getString("name");
							fieldTitles[i] = jsonObject.getString("title");
						}
					}
				} else {
					fields = msd.getColAry(false, "list_field");
				}
			}
		}

		response.setContentType("application/vnd.ms-excel");
		response.setHeader("Content-disposition", "attachment; filename=" + StrUtil.GBToUnicode(fd.getName()) + ".xls");

		try (OutputStream os = response.getOutputStream()) {
			ConfigUtil configUtil = SpringUtil.getBean(ConfigUtil.class);
			InputStream inputStream = configUtil.getFile("templ/blank.xls");
			Workbook wb = Workbook.getWorkbook(inputStream);

			WorkbookSettings settings = new WorkbookSettings();
			settings.setWriteAccess(null);

			// 打开一个文件的副本，并且指定数据写回到原文件
			WritableWorkbook wwb = Workbook.createWorkbook(os, wb, settings);
			WritableSheet ws = wwb.getSheet(0);

			int len = 0;
			if (fields != null) {
				len = fields.length;
			}
			for (int i = 0; i < len; i++) {
				String fieldName = fields[i];
				String title = "创建者";
				if (templateId != -1) {
					title = fieldTitles[i];
				} else {
					if (!"cws_creator".equals(fieldName)) {
						title = fd.getFieldTitle(fieldName);
					}
				}

				Label a = new Label(i, 0, title);
				ws.addCell(a);
			}

			wwb.write();
			wwb.close();
			wb.close();
		} catch (Exception e) {
			LogUtil.getLog(getClass()).error(e);
		}
	}
}
