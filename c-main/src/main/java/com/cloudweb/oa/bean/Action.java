package com.cloudweb.oa.bean;

import com.cloudweb.oa.service.impl.MyflowUtil;
import lombok.Data;

@Data
public class Action {
    int id;
    int x;
    int y;
    int w = MyflowUtil.ACTION_WIDTH;
    int h = MyflowUtil.ACTION_HEIGHT;
}
