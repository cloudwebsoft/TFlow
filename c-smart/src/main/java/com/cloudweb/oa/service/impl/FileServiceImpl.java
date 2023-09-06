package com.cloudweb.oa.service.impl;

import cn.js.fan.util.ErrMsgException;
import cn.js.fan.util.MIMEMap;
import cn.js.fan.util.ParamUtil;
import cn.js.fan.util.StrUtil;
import cn.js.fan.util.file.FileUtil;
import cn.js.fan.web.Global;
import com.cloudweb.oa.api.ICosService;
import com.cloudweb.oa.service.IFileService;
import com.cloudweb.oa.utils.SpringUtil;
import com.cloudwebsoft.framework.util.LogUtil;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObjectInputStream;
import com.redmoon.kit.util.FileInfo;
import com.redmoon.kit.util.FileUpload;
import com.redmoon.oa.pvg.Privilege;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

@Slf4j
@Service
public class FileServiceImpl implements IFileService {

    @Autowired
    ICosService cosService;

    @Override
    public void write(MultipartFile file, String visualPath, String diskName) throws IOException {
        String filePath = Global.getRealPath() + "/" + visualPath + "/" + diskName;
        File f = new File(filePath);
        if (!f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }

        file.transferTo(f);

        boolean isCosUsed = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosUsed");
        if (isCosUsed) {
            cosService.upload(visualPath, diskName, f);
            // 20220201不管是否保留本地文件，均需保留，因为生成office、pdf文件预览时，需用到本地文件且生成文件路径与原文件在同一目录下，留待将来再优化
            /*boolean isCosReserveLocalFile = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosReserveLocalFile");
            if (!isCosReserveLocalFile) {
                f.delete();
            }*/
        }
    }

    @Override
    public void write(FileInfo fileInfo, String visualPath) {
        write(fileInfo, visualPath, true);
    }

    @Override
    public void write(FileInfo fileInfo, String visualPath, boolean isRand) {
        String filePath = Global.getRealPath() + visualPath + "/";
        File f = new File(filePath);
        if (!f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }

        // 写入本地文件
        fileInfo.write(filePath, isRand);

        boolean isLocal = true;
        boolean isCosUsed = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosUsed");
        if (isCosUsed) {
            f = new File(filePath + fileInfo.getDiskName());
            cosService.upload(visualPath, fileInfo.getDiskName(), f);
            isLocal = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosReserveLocalFile");
        }

        // 删除本地文件
        // if (!isLocal) {
            // 20220201不管是否保留本地文件，均需保留，因为生成office、pdf文件预览时，需用到本地文件且生成文件路径与原文件在同一目录下
            // f.delete();
        // }
    }

    /**
     * 上传文件，用于复制本模板文件、邮件，不删除本地文件
     * @param localFilePath 源文件路径
     * @param visualPath
     * @param diskName
     */
    @Override
    public void write(String localFilePath, String visualPath, String diskName) {
        write(localFilePath, visualPath, diskName, false);
    }

    @Override
    public void write(String localFilePath, String visualPath, String diskName, boolean isLocalFileDelByConfig) {
        boolean isLocal = true;
        boolean isCosUsed = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosUsed");
        boolean isCosReserveLocalFile = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosReserveLocalFile");
        if (isCosUsed) {
            File file = new File(localFilePath);
            cosService.upload(visualPath, diskName, file);
            isLocal = isCosReserveLocalFile;
        }

        if (isLocal) {
            // 判断路径是否存在，不存在则创建，因CopyFile并不会自动创建目录，会报：系统找不到指定文件
            File f = new File(Global.getRealPath() + visualPath);
            if (!f.exists()) {
                f.mkdirs();
            }
            String fullPath = Global.getRealPath() + visualPath + "/" + diskName;
            FileUtil.CopyFile(localFilePath, fullPath);
        }

        // 如果本地文件不保留，则删除，如：邮件通过InputStream接收后
        if (isLocalFileDelByConfig && !isCosReserveLocalFile) {
            File f = new File(localFilePath);
            f.delete();
        }
    }

    @Override
    public void copy(String srcVisualPath, String srcDiskName, String visualPath, String diskName) throws IOException {
        // 判断路径是否存在，不存在则创建，因CopyFile并不会自动创建目录，会报：系统找不到指定文件
        File f = new File(Global.getRealPath() + visualPath);
        if (!f.exists()) {
            f.mkdirs();
        }

        // 如果本地文件存在
        boolean isLocalDone = false;
        File srcFile = new File(Global.getRealPath() + srcVisualPath + "/" + srcDiskName);
        if (srcFile.exists()) {
            isLocalDone = true;
            FileUtil.CopyFile(Global.getRealPath() + srcVisualPath + "/" + srcDiskName, Global.getRealPath() + visualPath + "/" + diskName);
        }

        boolean isCosUsed = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosUsed");
        if (isCosUsed) {
            boolean isCosReserveLocalFile = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosReserveLocalFile");
            boolean isReserve = isCosReserveLocalFile && !isLocalDone;
            cosService.copy(srcVisualPath + "/" + srcDiskName, visualPath + "/" + diskName, isReserve);
        }
    }

    /**
     * 仅上传文件
     * @param filePath
     * @param visualPath
     * @param diskName
     */
    @Override
    public void upload(String filePath, String visualPath, String diskName) {
        boolean isCosUsed = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosUsed");
        if (isCosUsed) {
            cosService.upload(visualPath, diskName, new File(filePath));
        }
    }

    @Override
    public boolean del(String visualPath, String diskName) {
        return del(visualPath + "/" + diskName);
    }

    @Override
    public boolean del(String path) {
        boolean re = true;
        boolean isCosUsed = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosUsed");
        boolean isCosReserveLocalFile = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosReserveLocalFile");
        boolean isDelLocalFile = true;
        if (isCosUsed) {
            cosService.delete(path);

            if (!isCosReserveLocalFile) {
                isDelLocalFile = false;
            }
        }

        if (isDelLocalFile) {
            File file = new File(Global.getRealPath() + "/" + path);
            if (file.exists()) {
                re = file.delete();
            }
        }
        return re;
    }

    @Override
    public void preview(HttpServletResponse response, String filePath) throws IOException {
        response.setContentType(MIMEMap.get(StrUtil.getFileExt(filePath)));
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;

        try {
            boolean isLocal = true;
            boolean isCosUsed = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosUsed");
            if (isCosUsed) {
                try {
                    COSObjectInputStream is = cosService.getInputStream(filePath);
                    bis = new BufferedInputStream(is);
                    isLocal = false;
                }
                catch (CosServiceException e) {
                    // LogUtil.getLog(getClass()).error(e);
                    log.error("COS 路径 {} 不存在", filePath);
                    return;
                }
            }

            // 如果COS不可用，则使用
            if (isLocal) {
                boolean isCosReserveLocalFile = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosReserveLocalFile");
                if (!isCosUsed || (isCosUsed && isCosReserveLocalFile)) {
                    String fullPath = Global.realPath + filePath;
                    File tempFile = new File(fullPath);
                    if (tempFile.exists()) {
                        bis = new BufferedInputStream(new FileInputStream(fullPath));
                    }
                }
                /* else {
                    // 如果不保留本地文件，此处需处理类似 头像images/man.png 这样的系统图片
                    if (filePath.startsWith("images/")) {
                        String fullPath = Global.realPath + filePath;
                        File tempFile = new File(fullPath);
                        if (tempFile.exists()) {
                            bis = new BufferedInputStream(new FileInputStream(fullPath));
                        }
                    }
                }*/
            }

            if (bis == null) {
                LogUtil.getLog(getClass()).warn(filePath + " is not exist.");
                // throw new FileNotFoundException(filePath + " is not exist.");
                return;
            }

            bos = new BufferedOutputStream(response.getOutputStream());

            byte[] buff = new byte[2048];
            int bytesRead;

            while(-1 != (bytesRead = bis.read(buff, 0, buff.length))) {
                bos.write(buff,0,bytesRead);
            }
        } catch(final IOException e) {
            LogUtil.getLog(getClass()).error(e);
        } finally {
            if (bis != null) {
                bis.close();
            }
            if (bos != null) {
                bos.close();
            }
        }
    }

    @Override
    public void download(HttpServletResponse response, String fileName, String filePath) throws IOException {
        // 用下句会使IE在本窗口中打开文件
        // response.setContentType(MIMEMap.get(StrUtil.getFileExt(att.getDiskName())));
        // 使客户端直接下载，上句会使IE在本窗口中打开文件，下句也一样
        response.setContentType("application/octet-stream");
        response.setHeader("Content-disposition", "attachment; filename=\""+ StrUtil.GBToUnicode(fileName) + "\"");

        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;

        try {
            boolean isLocal = true;
            boolean isCosUsed = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosUsed");
            if (isCosUsed) {
                try {
                    COSObjectInputStream is = cosService.getInputStream(filePath);
                    bis = new BufferedInputStream(is);
                    isLocal = false;
                }
                catch (CosServiceException e) {
                    LogUtil.getLog(getClass()).error(e);
                }
            }

            // 如果COS不可用，则使用
            if (isLocal) {
                boolean isCosReserveLocalFile = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosReserveLocalFile");
                if (!isCosUsed || (isCosUsed && isCosReserveLocalFile)) {
                    String fullFilePath = Global.realPath + filePath;
                    File tempFile = new File(fullFilePath);
                    if (tempFile.exists()) {
                        bis = new BufferedInputStream(new FileInputStream(fullFilePath));
                    }
                }
            }

            if (bis == null) {
                throw new FileNotFoundException(filePath + " is not exist.");
            }

            bos = new BufferedOutputStream(response.getOutputStream());

            byte[] buff = new byte[2048];
            int bytesRead;

            while(-1 != (bytesRead = bis.read(buff, 0, buff.length))) {
                bos.write(buff,0,bytesRead);
            }
        } catch(final IOException e) {
            LogUtil.getLog(getClass()).error(e);
        } finally {
            if (bis != null) {
                bis.close();
            }
            if (bos != null) {
                bos.close();
            }
        }
    }

    @Override
    public void download(HttpServletResponse response, String fileName, String visualPath, String diskName) throws IOException {
        download(response, fileName, visualPath + "/" + diskName);
    }

    @Override
    public void copyToLocalFile(String fileName, String visualPath, String diskName, String localDirPath) throws IOException {
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;

        try {
            boolean isLocal = true;
            String filePath = visualPath + "/" + diskName;
            boolean isCosUsed = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosUsed");
            if (isCosUsed) {
                try {
                    COSObjectInputStream is = cosService.getInputStream(filePath);
                    bis = new BufferedInputStream(is);
                    isLocal = false;
                }
                catch (CosServiceException e) {
                    LogUtil.getLog(getClass()).error(e);
                }
            }

            // 如果COS不可用，则使用
            if (isLocal) {
                boolean isCosReserveLocalFile = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosReserveLocalFile");
                if (!isCosUsed || (isCosUsed && isCosReserveLocalFile)) {
                    String fullFilePath = Global.realPath + filePath;
                    File tempFile = new File(fullFilePath);
                    if (tempFile.exists()) {
                        bis = new BufferedInputStream(new FileInputStream(fullFilePath));
                    }
                }
            }

            if (bis == null) {
                throw new FileNotFoundException(filePath + " is not exist.");
            }

            File f = new File(localDirPath + "/" + fileName);
            if (!f.exists()) {
                f.createNewFile();
            }
            bos = new BufferedOutputStream(new FileOutputStream(f));

            byte[] buff = new byte[2048];
            int bytesRead;

            while(-1 != (bytesRead = bis.read(buff, 0, buff.length))) {
                bos.write(buff,0,bytesRead);
            }
        } catch(final IOException e) {
            LogUtil.getLog(getClass()).error(e);
        } finally {
            if (bis != null) {
                bis.close();
            }
            if (bos != null) {
                bos.close();
            }
        }
    }
}
