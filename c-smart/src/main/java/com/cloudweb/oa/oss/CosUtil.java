package com.cloudweb.oa.oss;

import cn.js.fan.web.Global;
import com.cloudweb.oa.cfg.CosConfig;
import com.cloudwebsoft.framework.util.LogUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.exception.MultiObjectDeleteException;
import com.qcloud.cos.model.*;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.transfer.Transfer;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.TransferProgress;
import com.qcloud.cos.transfer.Upload;

import java.io.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CosUtil {
    private static ClientConfig clientConfig;

    public static COSClient getCosClient() {
        CosConfig cosConfig = CosConfig.getInstance();
        String secretId = cosConfig.getProperty("secretId");
        String secretKey = cosConfig.getProperty("secretKey");
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        clientConfig = new ClientConfig(new Region(cosConfig.getProperty(("region"))));
        return new COSClient(cred, clientConfig);
    }

    /**
     * 同步上传文件, 最大支持5GB, 适用于小文件上传, 建议 20M 以下的文件使用该接口
     * 大文件上传请参照 API 文档高级 API 上传
     * @param localFile
     */
    /*public static void uploadFile(String visualPath, String diskName, File localFile) throws CosClientException {
        String bucketName = CosConfig.getInstance().getProperty("name");

        // 生成cos客户端
        COSClient cosclient = getCosClient();
        try {
            // 指定要上传到 COS 上的路径
            String key = visualPath + "/" + diskName;
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, localFile);
            PutObjectResult putObjectResult = cosclient.putObject(putObjectRequest);
            boolean isCosReserveLocalFile = com.redmoon.oa.Config.getInstance().getBooleanProperty("isCosReserveLocalFile");
            if (!isCosReserveLocalFile) {
                localFile.delete();
            }
        } catch (Exception e) {
            LogUtil.getLog(getClass()).error(e);
        } finally {
            // 关闭客户端(关闭后台线程)
            cosclient.shutdown();
        }
    }*/

    /**
     * 仅上传文件，而不删除本地文件
     * @param visualPath
     * @param diskName
     * @param localFile
     * @throws CosClientException
     */
    public static void upload(String visualPath, String diskName, File localFile) throws CosClientException {
        upload(visualPath + "/" + diskName, localFile);
    }

    public static void upload(String path, File localFile) throws CosClientException {
        String bucketName = CosConfig.getInstance().getProperty("name");
        // 生成cos客户端
        COSClient cosclient = getCosClient();
        try {
            // 指定要上传到 COS 上的路径
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, path, localFile);
            PutObjectResult putObjectResult = cosclient.putObject(putObjectRequest);
        } catch (Exception e) {
            LogUtil.getLog(CosUtil.class).error(e);
        } finally {
            // 关闭客户端(关闭后台线程)
            cosclient.shutdown();
        }
    }

    /**
     * 异步上传文件, 根据文件大小自动选择简单上传或者分块上传。
     *
     * @param localFile
     */
    public static void uploadFileAsync(String path, File localFile, boolean isRemoveLocalFile) {
        // 生成cos客户端
        COSClient cosClient = getCosClient();
        ExecutorService threadPool = Executors.newFixedThreadPool(32);

        // 传入一个threadpool, 若不传入线程池, 默认TransferManager中会生成一个单线程的线程池。
        TransferManager transferManager = new TransferManager(cosClient, threadPool);

        String key = path + "/" + localFile.getName();
        String bucketName = CosConfig.getInstance().getProperty("name");
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, localFile);
        try {
            // 返回一个异步结果Upload, 可同步的调用waitForUploadResult等待upload结束, 成功返回UploadResult, 失败抛出异常.
            // long startTime = System.currentTimeMillis();
            Upload upload = transferManager.upload(putObjectRequest);

            // transferManager.shutdownNow();

            if (isRemoveLocalFile) {
                localFile.delete();
            }
        } catch (CosClientException /*| InterruptedException*/ e) {
            LogUtil.getLog(CosUtil.class).error(e);
        }
        finally {
            cosClient.shutdown();
        }
    }

    /**
     * 删除文件
     *
     * @param key
     */
    public static void delete(String key) throws CosClientException {
        // bucket的命名规则为{name}-{appid}
        String bucketName = CosConfig.getInstance().getProperty("name");

        // 生成cos客户端
        COSClient cosClient = getCosClient();
        // 指定要删除的 bucket 和路径
        cosClient.deleteObject(bucketName, key);
        // 关闭客户端(关闭后台线程)
        cosClient.shutdown();
    }

    /**
     * 删除文件（批量）
     * @param keyList //要删除文件的key表
     */
    public static void deleteObjects(List<DeleteObjectsRequest.KeyVersion> keyList){
        /*keyList.add(new DeleteObjectsRequest.KeyVersion("project/folder2/text.txt"));
        keyList.add(new DeleteObjectsRequest.KeyVersion("project/folder2/music.mp3"));*/

        String bucketName = CosConfig.getInstance().getProperty("name");
        DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName);
        deleteObjectsRequest.setKeys(keyList);

        CosConfig cosConfig = CosConfig.getInstance();
        String secretId = cosConfig.getProperty("secretId");
        String secretKey = cosConfig.getProperty("secretKey");
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        COSClient cosclient = new COSClient(cred, clientConfig);
        // 批量删除文件
        try {
            DeleteObjectsResult deleteObjectsResult = cosclient.deleteObjects(deleteObjectsRequest);
            List<DeleteObjectsResult.DeletedObject> deleteObjectResultArray = deleteObjectsResult.getDeletedObjects();
        } catch (MultiObjectDeleteException mde) { // 如果部分删除成功部分失败, 返回MultiObjectDeleteException
            List<DeleteObjectsResult.DeletedObject> deleteObjects = mde.getDeletedObjects();
            List<MultiObjectDeleteException.DeleteError> deleteErrors = mde.getErrors();
        } catch (CosClientException e) { // 如果是其他错误，例如参数错误， 身份验证不过等会抛出 CosServiceException
            // 如果是客户端错误，例如连接不上COS
            LogUtil.getLog(CosUtil.class).error(e);
        }
    }

    /**
     * 打印进度，等待传输完成
     *
     * @param transfer
     */
    private static void showTransferProgress(Transfer transfer) {
        do {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
            TransferProgress progress = transfer.getProgress();
            long so_far = progress.getBytesTransferred();
            long total = progress.getTotalBytesToTransfer();
            double pct = progress.getPercentTransferred();
        } while (!transfer.isDone());
        Transfer.TransferState xfer_state = transfer.getState();
    }

    /**
     * 获取文件下载流
     * @param key
     * @return
     */
    public static COSObjectInputStream getInputStream(String key){
        COSClient cosClient = getCosClient();
        // bucket的命名规则为{name}-{appid} ，此处填写的存储桶名称必须为此格式，这个为存储桶名称
        String appId = CosConfig.getInstance().getProperty("appId");
        String bucketName = CosConfig.getInstance().getProperty("name");
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, key);//根据桶和key获取文件请求
        COSObject cosObject = cosClient.getObject(getObjectRequest);
        COSObjectInputStream cosObjectInput = cosObject.getObjectContent();
        return cosObjectInput;
    }

    /**
     * 拷贝文件，是否保留用于临时存储的本地文件
     * @param srcKey
     * @param desKey
     * @param isReserveLocalSrcFile
     * @throws IOException
     */
    public static void copy(String srcKey, String desKey, boolean isReserveLocalSrcFile) throws IOException {
        BufferedInputStream bis = null;
        FileOutputStream os = null;
        try {
            COSObjectInputStream is = getInputStream(srcKey);
            bis = new BufferedInputStream(is);
            os = new FileOutputStream(Global.getRealPath() + desKey);

            int len = 0;
            byte[] b = new byte[8192];
            while ((len = bis.read(b)) != -1) {
                os.write(b, 0, len);
            }

            File fileTmp = new File(Global.getRealPath() + desKey);
            upload(desKey, fileTmp);

            // 如果不需要保留临时本地文件，则删除
            if (!isReserveLocalSrcFile) {
                fileTmp.delete();
            }
        }
        finally {
            if (bis != null) {
                bis.close();
            }
            if (os != null) {
                os.close();
            }
        }
    }

    /**
     * 获取文件临时url
     * @param key
     * @return
     */
    /*public static String generatePresignedUrl(String key){
        COSClient cosClient = new COSClient(cred, clientConfig);
        //若要用此url查看在线文件，key必须以".png",".pdf"等后缀结尾   2020-01-04 12:02:47
        Date expirationTime = new Date(System.currentTimeMillis() + 30L * 60L * 1000L); //半小时后过期
        URL url = cosClient.generatePresignedUrl(bucketName, key, expirationTime);      //获取url地址
        return url.toString();
    }*/

    /**
     * 测试上传
     *
     * @param args
     */
    public static void main(String[] args) {
        try {
            File localFile = new File("/Users/Downloads/123.mp4");
            // uploadFile(localFile);
        } catch (Exception e) {
            LogUtil.getLog(CosUtil.class).error(e);
        }
    }
}
