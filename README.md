# 智慧医疗系统 Smart Medical

一套面向医院场景的智慧医疗系统，包含**患者端 / 医生端 / 管理端**三个角色端，并在业务之上接入 **AI 多智能体客服**（LangGraph4j 状态图编排 + LangChain4j 调用大模型 + RAG 知识库检索）。

后端 Spring Boot 3 + MyBatis-Plus + MySQL + Redis + Milvus，前端 Vue 3 + Vite + Element Plus。

> 本项目为学习 / 课程设计 / 毕业设计作品，非医疗器械软件，AI 输出不构成任何诊疗建议。

---

## 功能概览

**患者端**
- 注册登录、个人资料、头像上传
- 科室与医生浏览、按日期选号预约、取消 / 改期
- 就诊记录、检查报告、处方与病历查看
- 在线图文问诊（发送消息、接收医生回复）
- AI 智能客服：分诊导诊、挂号流程答疑、个人信息与就诊进度查询（支持多轮会话、流式输出）

**医生端**
- 排班查看、号源容量调整、停诊申请
- 接诊与就诊状态推进、病历书写与版本留痕
- 在线问诊会话处理、回复草稿确认发送
- AI 辅助分析：根据病历 / 症状生成分析结论（流式返回）

**管理端**
- 医生、患者、科室、排班管理（排班生成 / 一键改期 / 停诊审批）
- 数据质量看板、运营统计报表
- 提示词模板管理、模型配置、审计日志

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端框架 | Spring Boot 3.4.1、Spring MVC、Bean Validation |
| 持久层 | MyBatis-Plus 3.5.7、MySQL 8/9（utf8mb4） |
| 缓存 / 会话 | Redis（Lettuce）、Caffeine 本地缓存 |
| 安全 | JJWT 0.12.6、自定义拦截器鉴权（PATIENT / DOCTOR / ADMIN） |
| AI | LangChain4j 1.22.0、LangGraph4j 1.9.6、DeepSeek 等 OpenAI 兼容接口 |
| RAG / 向量库 | Milvus 2.5（etcd + MinIO + standalone）、BGE / 千问向量模型 |
| 前端 | Vue 3.5、Vite 8、Vue Router、Pinia、Element Plus、Axios |
| 构建 | Maven（JDK 24）、npm |

## 目录结构

```
smart-medical/
├── smart-medical-backend/              # Spring Boot 后端
│   ├── src/main/java/com/hospital/smartmedical/
│   │   ├── common/                     # 统一返回体、异常处理、鉴权、配置、存储
│   │   └── module/                     # 业务模块
│   │       ├── patient / doctor / admin
│   │       ├── appointment / schedule / visit / prescription / report
│   │       ├── onlineconsult / notification / medicalnote / knowledge
│   │       ├── stats / prompt / preconsult
│   │       └── ai/                     # 多智能体：graph / node / tool / rag / cache
│   ├── src/main/resources/
│   │   ├── application.yml             # 主配置
│   │   ├── application-demo.yml        # 演示 profile
│   │   ├── schema.sql / data.sql       # 建表 + 初始数据
│   │   ├── db/migration-*.sql          # 增量迁移脚本
│   │   └── ai-prompts.yaml             # 提示词模板
│   ├── src/test/java/                  # 单元测试
│   └── docs/                           # 开发过程验证记录
├── smart-medical-frontend/             # Vue 3 前端
│   └── src/{api,components,layouts,router,views,stores}
├── docker-compose-milvus.yml           # Milvus 向量库一键启动
└── 启动命令.txt                         # 本地启动速查
```

## 快速开始

### 1. 环境要求

- JDK 24（与 `pom.xml` 中 `java.version` 一致）、Maven 3.8+
- Node.js 18+、npm
- MySQL 8+、Redis 6+
- 可选：Docker（启动 Milvus 向量库，不启用时自动回退内存向量库）

### 2. 初始化数据库

```sql
CREATE DATABASE smart_medical DEFAULT CHARACTER SET utf8mb4;
```

然后依次执行：

```
smart-medical-backend/src/main/resources/schema.sql          -- 建表
smart-medical-backend/src/main/resources/data.sql            -- 初始数据
smart-medical-backend/src/main/resources/db/migration-*.sql  -- 按版本号顺序执行增量脚本
```

### 3. 配置本地密钥

敏感配置不写进仓库。在**后端运行目录**（即 `smart-medical-backend/`）下新建 `ai-secret.yml`（已被 `.gitignore` 忽略）：

```yaml
jwt:
  secret: "请替换为不少于 32 字节的随机字符串"

ai:
  llm:
    api-key: "sk-xxxx"            # DeepSeek 等 OpenAI 兼容接口的 Key
    embedding:
      api-key: "sk-xxxx"          # 向量模型 Key（留空则使用本地 BGE 模型）
```

也可以用环境变量 `JWT_SECRET`、`AI_LLM_API_KEY`、`AI_EMBEDDING_API_KEY`、`DB_PASSWORD` 等覆盖。

> 没有大模型 Key 时，把 `application.yml` 里的 `ai.mock-mode` 改为 `true`，AI 会走本地规则回复，不影响业务功能演示。

### 4. 启动后端（默认 8080，上下文 `/api/smart-medical/v1`）

```bash
cd smart-medical-backend
mvn spring-boot:run
# 或运行已构建产物：java -jar target/smart-medical-1.0.0.jar
```

### 5. 启动前端（默认 5173）

```bash
cd smart-medical-frontend
npm install
npm run dev
```

### 6. （可选）启动 Milvus

```bash
docker compose -f docker-compose-milvus.yml up -d
```

Milvus 默认地址 `localhost:19530`，集合 `smart_medical_knowledge`。

### 7. 演示模式

空库演示时，可加 `--spring.profiles.active=demo` 启动：

- 任意账号密码 `123456` 均可登录（DemoMode）
- 启动时自动为医生生成近 7 天号源
- 忘记密码接口回显验证码

**生产环境不要启用 demo profile。**

## 默认账号（初始数据）

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 患者 | `zhangsan` | `123456` |
| 医生 | `D001` / `D002`（工号） | `123456` |
| 管理员 | 见 `data.sql` 中 `sm_admin` 初始记录 | `123456` |

## 免责声明

本项目仅用于技术学习与教学演示，不用于真实诊疗场景；系统中的 AI 生成内容仅供参考，不能替代执业医师的诊断与处方。
