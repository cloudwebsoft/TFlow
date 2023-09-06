package com.cloudweb.oa.service.impl;

import com.cloudweb.oa.api.ICosService;
import com.cloudweb.oa.oss.CosUtil;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.COSObjectInputStream;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
public class CosServiceImpl implements ICosService {

    /**
     * 仅上传文件，而不删除本地文件
     * @param visualPath
     * @param diskName
     * @param localFile
     * @throws CosClientException
     */
    @Override
    public void upload(String visualPath, String diskName, File localFile) throws CosClientException {
        CosUtil.upload(visualPath, diskName, localFile);
    }

    @Override
    public COSObjectInputStream getInputStream(String key) {
        return CosUtil.getInputStream(key);
    }

    @Override
    public void delete(String key) throws CosClientException {
        CosUtil.delete(key);
    }

    @Override
    public void copy(String srcKey, String desKey, boolean isReserveLocalSrcFile) throws IOException {
        CosUtil.copy(srcKey, desKey, isReserveLocalSrcFile);
    }
}
