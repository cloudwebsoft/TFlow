package com.redmoon.oa.flow.macroctl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import bsh.EvalError;
import bsh.Interpreter;
import com.cloudweb.oa.api.INestSheetCtl;
import com.cloudweb.oa.service.IMobileService;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudweb.oa.utils.SysProperties;
import com.cloudweb.oa.utils.SysUtil;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.util.BeanShellUtil;
import com.redmoon.oa.visual.*;
import jxl.Cell;
import jxl.Workbook;
import jxl.read.biff.BiffException;
import jxl.read.biff.WorkbookParser;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import cn.js.fan.db.Conn;
import cn.js.fan.db.ResultIterator;
import cn.js.fan.db.ResultRecord;
import cn.js.fan.util.CheckErrException;
import cn.js.fan.util.DateUtil;
import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.ParamChecker;
import cn.js.fan.util.ParamUtil;
import cn.js.fan.util.StrUtil;
import cn.js.fan.web.Global;

import com.cloudwebsoft.framework.db.JdbcTemplate;
import com.cloudwebsoft.framework.util.LogUtil;
import com.cloudwebsoft.framework.util.NetUtil;
import com.redmoon.kit.util.FileInfo;
import com.redmoon.kit.util.FileUpload;
import com.redmoon.oa.flow.Directory;
import com.redmoon.oa.flow.FormDAOMgr;
import com.redmoon.oa.flow.FormDb;
import com.redmoon.oa.flow.FormField;
import com.redmoon.oa.flow.Leaf;
import com.redmoon.oa.flow.WorkflowDb;
import com.redmoon.oa.pvg.Privilege;

/**
 * <p>
 * Title: 嵌套表格2，与关联模块相对应
 * </p>
 * <p>
 *  
 * Description:{"sourceForm":"sales_customer", "destForm":"access_control", "filter":"customer like {$@client}", "maps":[{"sourceField": "customer", "destField":"c"},{"sourceField": "address", "destField":"description"}]}
 * </p>
 * 
 * <p>
 * Copyright: Copyright (c) 2007
 * </p>
 * 
 * <p>
 * Company:
 * </p>
 * 
 * @author not attributable
 * @version 1.0
 */
public class NestSheetCtl extends AbstractMacroCtl implements INestSheetCtl {

	public NestSheetCtl() {
		super();
	}

	@Override
	public String convertToHTMLCtl(HttpServletRequest request, FormField ff) {
		return null;
	}

	@Override
	public String getNestSheet(HttpServletRequest request, FormField ff) {
		return null;
	}

	@Override
	public boolean autoSelect(HttpServletRequest request, long parentId, FormField nestField) throws ErrMsgException {
		return false;
	}

	@Override
	public int uploadExcel(ServletContext application, HttpServletRequest request, long parentId) throws ErrMsgException {
		FileUpload fileUpload = doUpload(application, request);
		String upFile = writeExcel(fileUpload);
		String excelFile = Global.getRealPath() + upFile;
		try {
			if (!"".equals(upFile)) {
				String moduleCode = fileUpload.getFieldValue("moduleCode");
				int flowId = StrUtil.toInt(fileUpload.getFieldValue("flowId"), FormDAO.NONEFLOWID);
				String parentFormCode = fileUpload.getFieldValue("parentFormCode");
				int rows = 0;
				try {
					rows = read(excelFile, moduleCode, parentFormCode, parentId, flowId, request);
				} catch (IOException e) {
					LogUtil.getLog(getClass()).error(e);
				}
				return rows;
			} else {
				throw new ErrMsgException("文件不能为空！");
			}
		} finally {
			File file = new File(excelFile);
			file.delete();
		}
	}

	public FileUpload doUpload(ServletContext application, HttpServletRequest request) throws ErrMsgException {
		FileUpload fileUpload = new FileUpload();
		fileUpload.setMaxFileSize(Global.FileSize); // 每个文件最大30000K 即近300M
		String[] extnames = { "xls", "xlsx" };
		fileUpload.setValidExtname(extnames); // 设置可上传的文件类型
		int ret = 0;
		try {
			ret = fileUpload.doUpload(application, request);
			if (ret != FileUpload.RET_SUCCESS) {
				throw new ErrMsgException(fileUpload.getErrMessage());
			}
		} catch (IOException e) {
			LogUtil.getLog(getClass()).error("doUpload:" + e.getMessage());
		}
		return fileUpload;
	}

	public String writeExcel(FileUpload fu) {
		if (fu.getRet() == FileUpload.RET_SUCCESS) {
			Vector v = fu.getFiles();
			FileInfo fi = null;
			if (v.size() > 0) {
				fi = (FileInfo) v.get(0);
			}
			String vpath = "";
			if (fi != null) {
				// 置保存路径
				Calendar cal = Calendar.getInstance();
				String year = "" + (cal.get(Calendar.YEAR));
				String month = "" + (cal.get(Calendar.MONTH) + 1);
				vpath = "upfile/" + fi.getExt() + "/" + year + "/" + month
						+ "/";
				String filepath = Global.getRealPath() + vpath;
				fu.setSavePath(filepath);
				// 将临时文件使用随机名称写入磁盘
				fu.writeFile(true);

				// File f = new File(vpath + fi.getDiskName());
				// f.delete();
				return vpath + fi.getDiskName();
			}
		}
		return "";
	}

	/**
	 * import excel
	 * @param xlspath
	 * @param parentId
	 * @param flowId
	 * @param request
	 * @return
	 * @throws IOException
	 */
	public int read(String xlspath, String moduleCode, String parentFormCode, long parentId, int flowId, HttpServletRequest request) throws IOException {
		Privilege pvg = new Privilege();
		String unitCode = pvg.getUserUnitCode(request);
		InputStream in = null;
		int rowcount = 0;
		try {
			ModuleSetupDb msd = new ModuleSetupDb();
			msd = msd.getModuleSetupDbOrInit(moduleCode);
			String formCode = msd.getString("form_code");

			// String listField = StrUtil.getNullStr(msd.getString("list_field"));
			String[] fields = msd.getColAry(false, "list_field");

			int templateId = -1;
			ModuleImportTemplateDb mid = new ModuleImportTemplateDb();
			mid = mid.getDefault(formCode);
			if (mid != null) {
				templateId = mid.getInt("id");
			}

			JSONArray arr = null;
			JSONArray aryCleans = null;
			if (templateId != -1) {
				String rules = mid.getString("rules");
				// DebugUtil.i(getClass(), "doImport", rules);
				try {
					arr = new JSONArray(rules);
					if (arr.length() > 0) {
						fields = new String[arr.length()];
						for (int i = 0; i < arr.length(); i++) {
							org.json.JSONObject json = (org.json.JSONObject) arr.get(i);
							fields[i] = json.getString("name");
						}
					}

					String strJson = StrUtil.getNullStr(mid.getString("cleans"));
					if (!"".equals(strJson)) {
						aryCleans = new JSONArray(strJson);
					}
				} catch (org.json.JSONException e) {
					LogUtil.getLog(getClass()).error(e);
				}
			}

			// 记录不允许重复的字段组合
			Vector vFieldCanNotRepeat = new Vector();
			if (templateId != -1) {
				String rules = mid.getString("rules");
				try {
					arr = new JSONArray(rules);
					if (arr.length() > 0) {
						fields = new String[arr.length()];
						for (int i = 0; i < arr.length(); i++) {
							org.json.JSONObject json = (org.json.JSONObject) arr.get(i);
							fields[i] = json.getString("name");
							int canNotRepeat = json.getInt("canNotRepeat");
							if (canNotRepeat == 1) {
								vFieldCanNotRepeat.addElement(fields[i]);
							}
						}
					}
				} catch (org.json.JSONException e) {
					LogUtil.getLog(getClass()).error(e);
				}
			}

			FormDb fd = new FormDb(formCode);
			FormDAO fdao = new FormDAO(fd);
			MacroCtlMgr mm = new MacroCtlMgr();

			Vector records = new Vector();

			in = new FileInputStream(xlspath);
			String pa = StrUtil.getFileExt(xlspath);
			if ("xls".equals(pa)) {
				JdbcTemplate jt = new JdbcTemplate();
				jt.setAutoClose(false);
				try {
					// 读取xls格式的excel文档
					// HSSFWorkbook w = (HSSFWorkbook) WorkbookFactory.create(in);
					org.apache.poi.ss.usermodel.Workbook w = WorkbookFactory.create(in);
					// 获取sheet
					for (int i = 0; i < w.getNumberOfSheets() && i < 1; i++) {
						org.apache.poi.ss.usermodel.Sheet sheet = w.getSheetAt(i);
						if (sheet != null) {
							// 获取行数
							rowcount = sheet.getLastRowNum();
							org.apache.poi.ss.usermodel.Cell cell = null;

							FormField ff = null;
							Vector<FormField> vfields = null;
							// 获取每一行
							for (int k = 1; k <= rowcount; k++) {
								vfields = new Vector<>();
								org.apache.poi.ss.usermodel.Row row = sheet.getRow(k);
								if (row != null) {
									int colcount = row.getLastCellNum();

									// 获取每一单元格
									for (int m = 0; m <= fields.length - 1; m++) {
										ff = fd.getFormField(fields[m]);
										cell = row.getCell(m);
										// 如果单元格中的值为空，则cell可能为null
										if (cell != null) {
											if (cell.getCellType() == CellType.NUMERIC && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
												Date date = org.apache.poi.ss.usermodel.DateUtil.getJavaDate(cell.getNumericCellValue());
												ff.setValue(DateUtil.format(date, "yyyy-MM-dd"));
											} else {
												cell.setCellType(CellType.STRING);
												ff.setValue(cell.getStringCellValue());
											}
										}

										String val = ff.getValue();
										if (ff.getType().equals(FormField.TYPE_MACRO)) {
											MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
											if (mu != null && !"macro_raty".equals(mu.getCode())) {
												// 如果是基础数据宏控件
												boolean isClean = false;
												if ("macro_flow_select".equals(mu.getCode())) {
													org.json.JSONObject json = null;
													if (aryCleans != null) {
														for (int n = 0; n < aryCleans.length(); n++) {
															json = aryCleans.getJSONObject(n);
															if (ff.getName().equals(json.get("fieldName"))) {
																isClean = true;
																break;
															}
														}
													}
													// 如果需清洗数据
													if (isClean) {
														if (json.has(val)) {
															val = json.getString(val);
														} else {
															DebugUtil.w(getClass(), json.get("fieldName") + " 清洗", val + "不存在");
														}
													}
												}
												if (!isClean) {
													val = mu.getIFormMacroCtl().getValueByName(ff, val);
												}
											}
										}
										ff.setValue(val);

										vfields.add(ff);
									}
									fdao.setFields(vfields);

									long mainId = -1;
									// 检查主表中是否已存在重复记录，如果已存在，则提取出记录的ID
									if (templateId != -1) {
										StringBuffer conds = new StringBuffer();
										Iterator ir = vFieldCanNotRepeat.iterator();
										while (ir.hasNext()) {
											String fieldName = (String) ir.next();
											if (!fieldName.startsWith("nest.")) {
												StrUtil.concat(conds, " and ", FormDb.getTableName(formCode)
														+ "."
														+ fieldName
														+ "="
														+ StrUtil.sqlstr(fdao.getFieldValue(fieldName)));
											}
										}
										if (conds.length() > 0) {
											String sql = "select id from ft_" + formCode + " where " + conds.toString();
											ResultIterator ri = jt.executeQuery(sql);
											if (ri.hasNext()) {
												ResultRecord rr = (ResultRecord) ri.next();
												mainId = rr.getLong(1);
											}
										}
									}

									if (mainId == -1) {
										fdao.setUnitCode(unitCode);
										fdao.setFlowId(flowId);
										fdao.setCwsId(String.valueOf(parentId));
										fdao.setCreator(pvg.getUser(request));
										fdao.setCwsParentForm(parentFormCode);
										fdao.create();

										// 如果需要记录历史
										if (fd.isLog()) {
											FormDAO.log(pvg.getUser(request), FormDAOLog.LOG_TYPE_CREATE, fdao);
										}
										records.addElement(fdao);
									}
								}
							}
						}
					}
				} catch (Exception e) {
					LogUtil.getLog(getClass()).error(e);
				} finally {
					jt.close();
				}
			} else if ("xlsx".equals(pa)) {
				JdbcTemplate jt = new JdbcTemplate();
				jt.setAutoClose(false);
				try {
					XSSFWorkbook w = (XSSFWorkbook) WorkbookFactory.create(in);
					for (int i = 0; i < w.getNumberOfSheets() && i < 1; i++) {
						XSSFSheet sheet = w.getSheetAt(i);
						if (sheet != null) {
							rowcount = sheet.getLastRowNum();
							XSSFCell cell = null;

							// FormDAO fdao = new FormDAO();
							FormField ff = null;
							Vector<FormField> vfields = null;
							for (int k = 1; k <= rowcount; k++) {
								vfields = new Vector<>();
								XSSFRow row = sheet.getRow(k);
								if (row != null) {
									int colcount = row.getLastCellNum();
									for (int m = 0; m <= fields.length - 1; m++) {
										cell = row.getCell(m);
										if (cell == null) {
											continue;
										}
										ff = fd.getFormField(fields[m]);
										if (cell.getCellType() == CellType.NUMERIC && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
											Date date = org.apache.poi.ss.usermodel.DateUtil.getJavaDate(cell.getNumericCellValue());
											ff.setValue(DateUtil.format(date, "yyyy-MM-dd"));
										} else {
											cell.setCellType(CellType.STRING);
											ff.setValue(cell.getStringCellValue());
										}

										String val = ff.getValue();
										if (ff.getType().equals(FormField.TYPE_MACRO)) {
											MacroCtlUnit mu = mm.getMacroCtlUnit(ff.getMacroType());
											if (mu != null && !"macro_raty".equals(mu.getCode())) {
												// 如果是基础数据宏控件
												boolean isClean = false;
												if ("macro_flow_select".equals(mu.getCode())) {
													org.json.JSONObject json = null;
													if (aryCleans != null) {
														for (int n = 0; n < aryCleans.length(); n++) {
															json = aryCleans.getJSONObject(n);
															if (ff.getName().equals(json.get("fieldName"))) {
																isClean = true;
																break;
															}
														}
													}
													// 如果需清洗数据
													if (isClean) {
														if (json.has(val)) {
															val = json.getString(val);
														} else {
															DebugUtil.w(getClass(), json.get("fieldName") + " 清洗", val + "不存在");
														}
													}
												}
												if (!isClean) {
													val = mu.getIFormMacroCtl().getValueByName(ff, val);
												}
											}
										}
										ff.setValue(val);

										vfields.add(ff);
									}
									fdao.setFields(vfields);

									long mainId = -1;
									// 检查主表中是否已存在重复记录，如果已存在，则提取出记录的ID
									if (templateId != -1) {
										StringBuffer conds = new StringBuffer();
										Iterator ir = vFieldCanNotRepeat.iterator();
										while (ir.hasNext()) {
											String fieldName = (String) ir.next();
											if (!fieldName.startsWith("nest.")) {
												StrUtil.concat(conds, " and ", FormDb.getTableName(formCode)
														+ "."
														+ fieldName
														+ "="
														+ StrUtil.sqlstr(fdao.getFieldValue(fieldName)));
											}
										}
										if (conds.length() > 0) {
											String sql = "select id from ft_" + formCode + " where " + conds.toString();
											ResultIterator ri = jt.executeQuery(sql);
											if (ri.hasNext()) {
												ResultRecord rr = (ResultRecord) ri.next();
												mainId = rr.getLong(1);
											}
										}
									}

									if (mainId == -1) {
										fdao.setUnitCode(unitCode);
										fdao.setFlowId(flowId);
										fdao.setCwsId(String.valueOf(parentId));
										fdao.setCreator(pvg.getUser(request));
										fdao.setCwsParentForm(parentFormCode);
										fdao.create();

										// 如果需要记录历史
										if (fd.isLog()) {
											FormDAO.log(pvg.getUser(request), FormDAOLog.LOG_TYPE_CREATE, fdao);
										}
										records.addElement(fdao);
									}
								}
							}
						}
					}
				} finally {
					jt.close();
				}
			}

			// 导入后事件
			if (!formCode.equals(moduleCode)) {
				msd = msd.getModuleSetupDbOrInit(formCode);
			}
			String script = msd.getScript("import_create");
			if (script != null && !script.equals("")) {
				Interpreter bsh = new Interpreter();
				try {
					StringBuffer sb = new StringBuffer();

					// 赋值用户
					sb.append("userName=\"" + pvg.getUser(request) + "\";");
					bsh.eval(BeanShellUtil.escape(sb.toString()));

					bsh.set("records", records);
					bsh.set("request", request);

					bsh.eval(script);
				} catch (EvalError e) {
					LogUtil.getLog(getClass()).error(e);
				}
			}
		} catch (Exception e) {
			LogUtil.getLog(getClass()).error(e);
			throw new ErrMsgException(e.getMessage());
		} finally {
			if (in != null) {
				in.close();
			}
		}
		return rowcount;
	}
	
	@Override
	public String getControlType() {
		return "text";
	}

	@Override
	public String getControlValue(String userName, FormField ff) {
		return "";
	}

	@Override
	public String getControlText(String userName, FormField ff) {
		return "";
	}

	@Override
	public String getControlOptions(String userName, FormField ff) {
		return "";
	}

	@Override
	public JSONObject getCtlDescription(FormField ff) {
		return getCtlDesc(ff);
	}

	/**
	 * 用于手机端处理流程时，得到嵌套表格、嵌套表格2、明细表宏控件的描述json，用于传值给H5界面
	 * @Description: fgf 20170412
	 * @param ff
	 * @return
	 */
	public static JSONObject getCtlDesc(FormField ff) {
		try {
			// 20131123 fgf 添加
			JSONObject jsonObj = new JSONObject();
			String defaultVal = StrUtil.decodeJSON(ff.getDescription());
			JSONObject json = new JSONObject(defaultVal);
			String nestFormCode = json.getString("destForm");
			String filter = json.getString("filter");
			
			String formCode = json.getString("sourceForm");
			
			jsonObj.put("sourceForm", formCode);
			jsonObj.put("destForm", nestFormCode);
			
			StringBuffer parentFields = new StringBuffer();
			if (!"".equals(filter)) {
				Pattern p = Pattern.compile(
						"\\{\\$([A-Z0-9a-z-_@\\u4e00-\\u9fa5\\xa1-\\xff]+)\\}", // 前为utf8中文范围，后为gb2312中文范围
						Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
				Matcher m = p.matcher(filter);
			    while (m.find()) {
			        String fieldName = m.group(1);
			        if (fieldName.equals("cwsCurUser") || fieldName.equals("curUser") 
			         	|| fieldName.equals("curUserDept") || fieldName.equals("curUserRole") || fieldName.equals("admin.dept")) {
			         	continue;
			        }
					StrUtil.concat(parentFields, ",", fieldName);
			    }

				// {#fieldName}，展开后不带有单引号
				p = Pattern.compile(
						"\\{#([@A-Z0-9a-z-_\\u4e00-\\u9fa5\\xa1-\\xff\\.]+)\\}", // 前为utf8中文范围，后为gb2312中文范围
						Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
				m = p.matcher(filter);
				while (m.find()) {
					String fieldName = m.group(1);
					if (fieldName.equals("cwsCurUser") || fieldName.equals("curUser")
							|| fieldName.equals("curUserDept") || fieldName.equals("curUserRole") || fieldName.equals("admin.dept")) {
						continue;
					}
					StrUtil.concat(parentFields, ",", fieldName);
				}
			}
			jsonObj.put("parentFields", parentFields.toString());	
			return jsonObj;
		} catch (JSONException e) {
			LogUtil.getLog(NestSheetCtl.class).error(e);
		}
		return null;
	}	
	
    /**
     * 用于流程处理时，生成表单默认值
     * @param ff FormField
     * @return Object
     */
    @Override
	public Object getValueForCreate(int flowId, FormField ff) {
        return "";
    }	
}
