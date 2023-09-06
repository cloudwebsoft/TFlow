package com.cloudweb.oa.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cloudweb.oa.cache.UserCache;
import com.cloudweb.oa.entity.VisualModuleTreePriv;
import com.cloudweb.oa.mapper.VisualModuleTreePrivMapper;
import com.cloudweb.oa.service.IVisualModuleTreePrivService;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author fgf
 * @since 2022-08-20
 */
@Service
public class VisualModuleTreePrivServiceImpl extends ServiceImpl<VisualModuleTreePrivMapper, VisualModuleTreePriv> implements IVisualModuleTreePrivService {

    @Autowired
    UserCache userCache;

    @Autowired
    VisualModuleTreePrivMapper visualModuleTreePrivMapper;

    @Override
    public List<VisualModuleTreePriv> list(String rootCode, String nodeCode, int pageSize, int curPage) {
        PageHelper.startPage(curPage, pageSize); // 分页查询
        /*String sql = "select * from visual_module_tree_priv where root_code=" + StrUtil.sqlstr(rootCode) + " and node_code=" + StrUtil.sqlstr(nodeCode);
        return visualModuleTreePrivMapper.selectTreePrivList(sql);*/
        return visualModuleTreePrivMapper.list(rootCode, nodeCode);
    }

    @Override
    public List<VisualModuleTreePriv> list(String rootCode, String nodeCode) {
        return visualModuleTreePrivMapper.list(rootCode, nodeCode);
    }

    public VisualModuleTreePriv get(long id) {
        return getById(id);
    }

}
