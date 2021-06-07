package qualitychain.inspur.common.logchain;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import qualitychain.inspur.common.exception.ParamException;

import java.security.PrivateKey;
import java.util.*;

public class LogchainUtil {

    private String userId;
    private String labelId;
    private String requestUrl;
    private String prvKey;
    private BatchLogchainUtil batchLogchainUtil;

    // 默认的日志级别
    private static final String defaultLevel = "trace";

    public LogchainUtil(String userId,String labelId,String requestUrl,String prvKey) {
        this.userId = userId;
        this.labelId = labelId;
        this.requestUrl = requestUrl;
        this.prvKey = prvKey;
        this.batchLogchainUtil = new BatchLogchainUtil(this);
    }

    public BatchLogchainUtil batch() {
        return batchLogchainUtil;
    }

    public Map<String,Object> trace(String message) throws Exception {
        return logchain(message,"trace");
    }

    public Map<String,Object> debug(String message) throws Exception {
        return logchain(message,"debug");
    }

    public Map<String,Object> info(String message) throws Exception {
        return logchain(message, "info");
    }

    public Map<String,Object> warn(String message) throws Exception {
        return logchain(message,"warn");
    }

    public Map<String,Object> error(String message) throws Exception {
        return logchain(message,"error");
    }

    public Map<String,Object> logchainDefaultLevel(String message) throws Exception {
        return logchain(message, "trace");
    }

    public Map<String,Object> batchDefaultLevel(List<String> messageList) throws Exception {
        return logchainBaseBatch(messageList, Arrays.asList("trace"));
    }


    // 批量上传的时候，每条对应一个时间:进程号:哈希以及签名
    public Map<String,Object> logchainBaseBatch(List<String> messageList,List<String> levelList) throws Exception {
        if(messageList.isEmpty()) {
            throw new ParamException("日志列表不能为空!");
        }
        if(levelList.isEmpty()) {
            levelList.add("trace");
        }
        List<Map<String,Object>> logContentList = new ArrayList<Map<String, Object>>();
        PrivateKey privateKey = LogchainTool.getPrivateKey(prvKey, 1);
        for(int i=0;i<messageList.size();i++) {
            Map<String,Object> middelMap = new HashMap<String, Object>();
            String nodeId = LogchainTool.getPid();
            long createDt = new Date().getTime();
            middelMap.put("nodeId",nodeId);
            middelMap.put("createDt",createDt);
            middelMap.put("userId",userId);
            middelMap.put("label",labelId);
            String level = "";
            if(1 == levelList.size()) {
                level = levelList.get(0);
            } else if (levelList.get(i)!=null &&
                        !"".equals(levelList.get(i))) {
                level = levelList.get(i);
            } else {
                level = defaultLevel;
            }
            // 如果出现了日志不符合要求的情况，则直接中断
            if (!Level.containsLevel(level)) {
                throw new ParamException("参数错误：第" + (i + 1) + "条日志的级别不符合要求，请于trace,info,debug,warn,error中进行选择");
            }
            middelMap.put("level", level);
            String txHash = LogchainTool.sha256(userId+labelId+nodeId+createDt+messageList.get(i)+level);
            String signature = LogchainTool.sign(txHash, privateKey, LogchainTool.PARAM_FORMAT_HEX);
            middelMap.put("signature",signature);
            middelMap.put("logTxt",messageList.get(i));
            middelMap.put("txHash",txHash);
            logContentList.add(middelMap);
        }
        // 获得Http客户端(可以理解为:你得先有一个浏览器;注意:实际上HttpClient与浏览器是不一样的)
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        // 参数
        Map<String, Object> params = new HashMap<String, Object>();
        Map<String, Object> resultMap = new HashMap<String, Object>();
        // 字符数据最好encoding以下;这样一来，某些特殊字符才能传过去(如:某人的名字就是“&”,不encoding的话,传不过去)
        params.put("logContentList",logContentList);
        params.put("userId",userId);
        params.put("label",labelId);
        StringEntity sEntity = new StringEntity(JSONObject.toJSONString(params), "utf-8");
        sEntity.setContentType("application/json");
        sEntity.setContentEncoding("utf-8");
        HttpPost httpPost = new HttpPost(requestUrl + "/logchain/put/batch");
        // 设置ContentType(注:如果只是传普通参数的话,ContentType不一定非要用application/json)
        httpPost.setHeader("Content-Type", "application/json;charset=utf-8");
        httpPost.setEntity(sEntity);
        // 响应模型
        CloseableHttpResponse response = null;
        try {
            // 由客户端执行(发送)Post请求
            response = httpClient.execute(httpPost);
            // 从响应模型中获取响应实体
            HttpEntity responseEntity = response.getEntity();
            //System.out.println("响应状态为:" + response.getStatusLine());
            if (responseEntity != null) {
                String resultString = EntityUtils.toString(responseEntity);
                JSONObject jsonObject = JSONObject.parseObject(resultString);
                resultMap = jsonObject;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            try {
                // 释放资源
                if (httpClient != null) {
                    httpClient.close();
                }
                if (response != null) {
                    response.close();
                }
            } catch (Exception e) {
                throw e;
            }
        }
        return resultMap;
    }


    public Map<String,Object> logchain(String message,String level) throws Exception {
        String nodeId = LogchainTool.getPid();
        if(!Level.containsLevel(level)) {
            throw new ParamException("没有对应的日志级别，请在trace,debug,info,warn,error中选取一个");
        }
        long createdt = new Date().getTime();
        String txHash = LogchainTool.sha256(this.userId + this.labelId + nodeId + createdt + message+level).toString();

        PrivateKey privateKey = LogchainTool.getPrivateKey(this.prvKey, 1);
        String signature = LogchainTool.sign(txHash, privateKey, LogchainTool.PARAM_FORMAT_HEX);
        // 获得Http客户端(可以理解为:你得先有一个浏览器;注意:实际上HttpClient与浏览器是不一样的)
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        // 参数
        Map<String, Object> params = new HashMap<String, Object>();
        Map<String, Object> resultMap = new HashMap<String, Object>();
        // 字符数据最好encoding以下;这样一来，某些特殊字符才能传过去(如:某人的名字就是“&”,不encoding的话,传不过去)
        params.put("user_id", userId);
        params.put("label", labelId);
        params.put("nodeId", nodeId);
        params.put("createDt", createdt);
        params.put("log_txt", message);
        params.put("txHash", txHash);
        params.put("signature", signature);
        params.put("level",level);
        StringEntity sEntity = new StringEntity(JSONObject.toJSONString(params), "utf-8");
        sEntity.setContentType("application/json");
        sEntity.setContentEncoding("utf-8");
        HttpPost httpPost = new HttpPost(requestUrl + "/logchain/put/logs");
        // 设置ContentType(注:如果只是传普通参数的话,ContentType不一定非要用application/json)
        httpPost.setHeader("Content-Type", "application/json;charset=utf-8");
        httpPost.setEntity(sEntity);
        // 响应模型
        CloseableHttpResponse response = null;
        try {
            // 由客户端执行(发送)Post请求
            response = httpClient.execute(httpPost);
            // 从响应模型中获取响应实体
            HttpEntity responseEntity = response.getEntity();
            //System.out.println("响应状态为:" + response.getStatusLine());
            if (responseEntity != null) {
                String resultString = EntityUtils.toString(responseEntity);
                JSONObject jsonObject = JSONObject.parseObject(resultString);
                resultMap = jsonObject;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            try {
                // 释放资源
                if (httpClient != null) {
                    httpClient.close();
                }
                if (response != null) {
                    response.close();
                }
            } catch (Exception e) {
                throw e;
            }
        }
        return resultMap;
    }

}
