package com.redmoon.oa.sms;

import cn.js.fan.base.*;

/**
 * <p>Title: </p>
 *
 * <p>Description: </p>
 *
 * <p>Copyright: Copyright (c) 2005</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class SMSSendRecordCache extends ObjectCache{
    public SMSSendRecordCache() {
    }

    public SMSSendRecordCache(SMSSendRecordDb ssrd) {
        super(ssrd);
    }

}
