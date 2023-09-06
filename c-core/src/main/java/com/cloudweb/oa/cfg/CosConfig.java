package com.cloudweb.oa.cfg;

/**
 * <p>Title: </p>
 *
 * <p>Description: </p>
 *
 * <p>Copyright: Copyright (c) 2007</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */

import cn.js.fan.util.StrUtil;
import cn.js.fan.util.XMLProperties;
import com.cloudweb.oa.base.IConfigUtil;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudwebsoft.framework.util.LogUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;

public class CosConfig {
    private XMLProperties properties;
    private Element root = null;

    public static CosConfig cfg = null;

    private static final Object initLock = new Object();

    public CosConfig() {
    }

    public void init() {
        URL cfgURL = getClass().getResource("/config_cos.xml");
        String cfgpath = cfgURL.getFile();
        cfgpath = URLDecoder.decode(cfgpath);
        // properties = new XMLProperties(cfgpath);

        IConfigUtil configUtil = SpringUtil.getBean(IConfigUtil.class);
        Document doc = configUtil.getDocument("config_cos.xml");
        root = doc.getRootElement();
        properties = new XMLProperties("config_cos.xml", doc);
    }

    public Element getRoot() {
        return root;
    }

    public static CosConfig getInstance() {
        if (cfg == null || cfg.properties == null) {
            synchronized (initLock) {
                cfg = new CosConfig();
                cfg.init();
            }
        }
        return cfg;
    }

    public String getProperty(String name) {
        String str = "";
        try {
            str = StrUtil.getNullStr(properties.getProperty(name));
        } catch (Exception e) {
            LogUtil.getLog(getClass()).error(e);
        }
        return str;
    }

    public int getIntProperty(String name) {
        String p = getProperty(name);
        if (StrUtil.isNumeric(p)) {
            return Integer.parseInt(p);
        } else {
            return -65536;
        }
    }

    public boolean getBooleanProperty(String name) {
        return "true".equals(getProperty(name));
    }

    public void setProperty(String name, String value) {
        properties.setProperty(name, value);
        refresh();
    }

    public String getProperty(String name, String childAttributeName,
                              String childAttributeValue) {
        return StrUtil.getNullStr(properties.getProperty(name, childAttributeName,
                childAttributeValue));
    }

    public String getProperty(String name, String childAttributeName,
                              String childAttributeValue, String subChildName) {
        return StrUtil.getNullStr(properties.getProperty(name, childAttributeName,
                childAttributeValue, subChildName));
    }

    public void setProperty(String name, String childAttributeName,
                            String childAttributeValue, String value) {
        properties.setProperty(name, childAttributeName, childAttributeValue,
                value);
        refresh();
    }

    public void setProperty(String name, String childAttributeName,
                            String childAttributeValue, String subChildName,
                            String value) {
        properties.setProperty(name, childAttributeName, childAttributeValue,
                subChildName, value);
        refresh();
    }

    public void refresh() {
        cfg = null;
    }
}
