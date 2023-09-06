package com.redmoon.oa.job;

import cn.js.fan.db.Conn;
import cn.js.fan.db.ResultIterator;
import cn.js.fan.db.ResultRecord;
import cn.js.fan.util.DateUtil;
import cn.js.fan.util.StrUtil;
import cn.js.fan.web.Global;
import com.cloudwebsoft.framework.db.JdbcTemplate;
import com.cloudwebsoft.framework.util.LogUtil;
import com.redmoon.oa.flow.FormDb;
import com.redmoon.oa.sys.DebugUtil;
import com.redmoon.oa.visual.FormDAO;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Iterator;

//持久化
@PersistJobDataAfterExecution
//禁止并发执行(Quartz不要并发地执行同一个job定义（这里指一个job类的多个实例）)
@DisallowConcurrentExecution
@Slf4j
public class ClearTempJob extends QuartzJobBean {

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        clearTmpFormTable();
    }

    /**
     * 清空嵌套表2添加的临时记录
     */
    public void clearTmpFormTable() {
        FormDb fd = new FormDb();
        Iterator ir = fd.list().iterator();
        JdbcTemplate jt = new JdbcTemplate();
        while (ir.hasNext()) {
            fd = (FormDb)ir.next();
            if (!fd.isLoaded()) {
                continue;
            }
            int maxId = -1;
            String sql = "select max(id) from " + fd.getTableNameByForm() + " where cws_id=" + FormDAO.CWS_ID_TO_ASSIGN;
            try {
                ResultIterator ri = jt.executeQuery(sql);
                if (ri.hasNext()) {
                    ResultRecord rr = ri.next();
                    maxId = rr.getInt(1);

                    int lastId = maxId - 100; // 100条以内不删除，以免误删新增的
                    if (lastId>0) {
                        sql = "delete from " + fd.getTableNameByForm() + " where id<=" + lastId + " and cws_id=" + FormDAO.CWS_ID_TO_ASSIGN;
                        jt.executeUpdate(sql);
                    }
                }
            } catch (SQLException e) {
                DebugUtil.log(getClass(), "clearTmpFormTable", sql);
                LogUtil.getLog(getClass()).error(e);
            }
        }

    }
}
