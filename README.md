~~weili-framework-example 项目文档

项目概述

weili-framework-example 是一个基于微服务架构的示例项目，展示了标准的Java项目结构和分层设计。

项目结构

```weili-framework-example/
├── iot-portal-common/           # 公共模块
│   ├── src/
│   └── pom.xml
├── iot-portal-dal/              # 数据访问层
├── iot-portal-domain/           # 领域模型层
├── iot-portal-service/          # 业务服务层
├── iot-portal-starter/          # 启动模块
├── iot-portal-task/             # 任务消费模块
├── iot-portal-web/              # Web控制层
├── .gitignore
├── pom.xml
└── README.md

```

模块说明

1. iot-portal-common 公共工具模块

* 包含项目通用的工具类、常量定义、基础配置等
* 被其他所有模块依赖

2. iot-portal-dal 数据访问层（Data Access Layer）

* 数据库实体类定义（Entity）
* 数据访问接口（Mapper/Repository）
* 数据库连接配置

3. iot-portal-domain 领域模型层

* 业务领域对象定义(VO/BO/DTO)

4. iot-portal-service 业务服务层

* 业务逻辑实现
* 事务管理
* 服务接口定义

5. iot-portal-starter 应用启动模块

* Spring Boot启动配置
* 应用配置文件
* 启动类定义

6. iot-portal-task 任务层

* 定时任务、异步任务、批处理任务
* 消息队列消费任务

7. iot-portal-web Web表现层

* 控制器（Controller）
* API接口定义
* Web配置和拦截器

创建项目命令

```
mvn archetype:generate -DgroupId=com.weili -DartifactId=weili-framework-example -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
```

技术栈

* 构建工具: Maven
* 语言: Java
* 框架: Spring Boot
* 项目管理: 多模块Maven项目

开发规范

1. 遵循分层架构原则，避免跨层调用
2. 公共代码放在common模块
3. 数据库相关代码放在dal模块
4. 各模块间保持单向依赖关系~~

