package com.cloudweb.oa.utils;

import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.StrUtil;
import cn.js.fan.web.Global;
import cn.js.fan.web.SkinUtil;
import com.alibaba.fastjson.JSON;
import com.cloudweb.oa.api.ICloudUtil;
import com.cloudweb.oa.api.IUserSecretService;
import com.cloudweb.oa.bean.Field;
import com.cloudwebsoft.framework.util.IPUtil;
import com.redmoon.oa.Config;
import com.redmoon.oa.SpConfig;
import com.redmoon.weixin.util.HttpPostFileUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Vector;

@Component
public class CloudUtil implements ICloudUtil {

    @Override
    public String getUserSecret() {
        return "";
    }

    @Override
    public String getLoginCheckUrl(String cwsToken, String formCode) {
        com.redmoon.oa.Config cfg = Config.getInstance();
        String cloudUrl = cfg.get("cloudUrl");
        String enterpriseNum = "16883767111721995920";
        String company = "云网软件";
        String ver = "4.0";

        SpConfig spCfg = new SpConfig();
        String sysVer = StrUtil.getNullStr(cfg.get("version"));
        String sp = StrUtil.getNullStr(spCfg.get("version"));

        String userSecret = "545db2425f3751e9997c046813f111fcfe9974c7ed546323d39452b763cb2389";
        String oaIp = IPUtil.getRemoteAddr(SpringUtil.getRequest());
        String oaSessionId = SpringUtil.getRequest().getSession().getId();
        String licCategory = "cloud";

        cloudUrl += "/public/agent/checkLogin.do?type=ide&userSecret=" + userSecret + "&oaIp=" + oaIp + "&oaSessionId=" + oaSessionId + "&licCategory=" + licCategory + "&sysVer=" + sysVer + "&sp=" + sp + "&ver=" + ver + "&com=" + StrUtil.UrlEncode(company) + "&entNum=" + StrUtil.UrlEncode(enterpriseNum) + "&cwsToken=" + cwsToken + "&extra=" + StrUtil.UrlEncode(Global.getFullRootPath() + "/admin/ide_left.jsp?formCode=" + StrUtil.UrlEncode(formCode));

        return cloudUrl;
    }

    @Override
    public void addParam(HttpPostFileUtil post) throws ErrMsgException {
        HttpServletRequest request = SpringUtil.getRequest();

    }

    @Override
    public String parseForm(String content) throws ErrMsgException {
        FormParseUtil fu = new FormParseUtil(content);
        Vector<Field> v = fu.getFields();
        return JSON.toJSON(v).toString();
    }
}
