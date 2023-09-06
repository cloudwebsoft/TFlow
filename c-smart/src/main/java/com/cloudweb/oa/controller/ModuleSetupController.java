package com.cloudweb.oa.controller;

import cn.js.fan.db.ListResult;
import cn.js.fan.util.*;
import cn.js.fan.util.file.FileUtil;
import cn.js.fan.web.Global;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cloudweb.oa.api.IModuleViewService;
import com.cloudweb.oa.utils.ConstUtil;
import com.cloudweb.oa.utils.ResponseUtil;
import com.cloudweb.oa.vo.Result;
import com.cloudwebsoft.framework.db.JdbcTemplate;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.Config;
import com.redmoon.oa.flow.FormDb;
import com.redmoon.oa.flow.FormMgr;
import com.redmoon.kit.util.FileUpload;
import com.redmoon.oa.db.SequenceManager;
import com.redmoon.oa.ui.SkinMgr;
import com.redmoon.oa.visual.ModulePrivDb;
import com.redmoon.oa.visual.ModuleRelateDb;
import com.redmoon.oa.visual.ModuleSetupDb;
import io.swagger.annotations.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import com.cloudweb.oa.utils.ResponseUtil;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Vector;

@Api(tags = "模块管理")
@Controller
@RequestMapping("/visual")
public class ModuleSetupController {
    @Autowired
    private HttpServletRequest request;

}
