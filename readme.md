审计日志侧链sdk
新的改进点：
1. 补全异常处理  ok
2. 降低传参数量  ok
3. 可选批量上传

使用方式较前几个版本有所改变，同一个项目中仅支持同一个租户使用  
需要在配置文件中logchain.properties中配置
```aidl
userId = 租户Id
privateKey.标签一Id = 标签一对应的私钥
privateKey.标签二Id = 标签二对应的私钥
```
使用方式如下，使用需要先声明一个logchain对象，通过logchain来上传日志
```java
private static final LogchainUtil logchain = LogchainFactory.getInstance(labelId);
logchain.log(String message);
logchian.debug(String message);
logchain.error(String message);
logchain.info(String message);
logchain.trace(String message);
```

###  审计侧链

#### 测试

需要发布到中央仓库中方可使用mavan引入，引用方式如下所示:
```
<dependency>
    <groupId>com.qualink.baas</groupId>
    <artifactId>logchain-sdk</artifactId>
    <version>0.1.4</version>
</dependency>
```
将jar包加入到本地仓库的命令为:

```
mvn install:install-file -DgroupId=com.qualink.baas -DartifactId=logchain-sdk -Dversion=0.0.2 -Dpackaging=jar -Dfile=D:/logchain-sdk-0.0.2-SNAPSHOT.jar
```

DgroupId和DartifactId是pom文件中的配置项，-Dfile是jar包所在位置

#### 项目构成
三个工程：
```
baas-web 前端工程
logchain-sdk sdk
aside 后端服务
```
####  使用
相对于0.1.4版本，新版本增加了批量上传功能。现有功能即为**批量上传** or **单条上传**
具体的使用方式如下:

1. maven中引入
```
<dependency>
    <groupId>com.qualink.baas</groupId>
    <artifactId>logchain-sdk</artifactId>
    <version>0.1.5</version>
</dependency>
```
2. 配置文件中配置（logchain.properties)
```
userId = 租户Id
privateKey.标签一 = 标签一对应的私钥
privateKey.标签二 = 标签二对应的私钥
requestUrl = 域名+上下文  （比如https://baas.qualink.com/aside)
```
3. 代码中引入

```java
// 声明一个logchainUtil,指向对应的标签
private static LogchainUtil log = LogchainFactory.getInstance(labelId);
// 上传单条日志
// 日志级别分为：trace,debug,warn,error,info
// message为对应的信息
log.info(message);
// 批量上传，所批量上传日志为同一级别
List<String> messageList = new ArrayList<>();
// messageList中上传的日志均为同一级别
log.batch().info(messageList);
// 如果批量上传时不想使用同一级别，要使用自定义的级别，可以使用如下方式
List<String> levelList = new ArrayList<>();
log.logchainBaseBatch(messageList,levelList);
    
```



#### 部署相关

```
上传日志 --》 定时任务打块 ---> 定时任务锚定
```

```
linux下路径:  /etc/qc-schedule.conf
配置:
IS_RUN_SCHEDULE=true
IS_BAAS_SCHEDULE=true
```

```
打块用户： 从表里查
表: base_organ_params  查询ADMIN_USER_ID
需要用户的私钥：  baas_wallet_identity
```

```
锚定用户：  trace1   peerOrg1Admin
```

### sdk相关 

```
配置文件路径： logchain.properties
```



### 判断审计侧链是否正常

```
定时任务数据库表： schedule_corn
打块： type 1   
锚定: type 2
```

```
如果有问题：
1. redis 密码不对
2. 打块用户数据库没有初始化   base_organ_params 查找ADMIN_USER_ID  baas_wallet_identity
3. 锚定不上  fabric网络的问题
4. 没有打块和锚定（定时任务没跑 ）  linux下没有对应的配置文件 ：/etc/qc-schedule.conf
```