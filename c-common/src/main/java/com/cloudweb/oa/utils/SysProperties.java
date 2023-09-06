package com.cloudweb.oa.utils;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Data
// @ConfigurationProperties(prefix = "sys")
@RefreshScope
@Configuration
public class SysProperties {

    @Value("${sys.web.rootPath}")
    private String rootPath;

    @Value("${sys.web.uploadPath}")
    private String uploadPath;

    @Value("${sys.web.publicPath}")
    private String publicPath;

    @Value("${sys.web.msg.fetchDays}")
    private int msgFetchDays;

    @Value("${mq.isOpen}")
    private boolean mqOpen;

    @Value("${sys.cache.enabled}")
    private boolean cache;

    @Value("${sys.cache.type}")
    private String cacheType;

    @Value("${sys.cache.redis.cluster}")
    private boolean redisCluster;

    @Value("${sys.cache.redis.host}")
    private String redisHost;

    @Value("${sys.cache.redis.port}")
    private String redisPort;

    @Value("${sys.cache.redis.password}")
    private String redisPassword;

    @Value("${sys.cache.redis.maxTotal:8}")
    private int redisMaxTotal;

    @Value("${sys.cache.redis.maxIdle:8}")
    private int redisMaxIdle;

    @Value("${sys.cache.redis.minIdle:0}")
    private int redisMinIdle;

    @Value("${sys.cache.redis.maxWaitMillis:-1}")
    private int redisMaxWaitMillis;

    @Value("${sys.cache.redis.db:0}")
    private int redisDb;

    @Value("${sys.web.frontPath}")
    private String frontPath;

    @Value("${sys.web.domainName}")
    private String domainName;

    @Value("${report.type}")
    private String reportType;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Value("${sys.id}")
    private String id;

    @Value("${sys.showId}")
    private boolean showId;

    @Value("${sys.user.mobile.required}")
    private boolean userMobileRequired;

    @Value("${sys.user.email.required}")
    private boolean userEmailRequired;
}
