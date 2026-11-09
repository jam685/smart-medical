# 可观测性（Loki + Promtail + Grafana）

把后端日志采集到 **Loki**，在 **Grafana** 里按 `traceId` 查整条链路。

```
后端 logback 输出 → Promtail 采集 → Loki 存储 → Grafana 查询/看板
```

## 目录内容

| 文件 | 说明 |
| --- | --- |
| `loki-config.yaml` | Loki 单机配置（schema v13 + tsdb，监听 127.0.0.1:3100） |
| `promtail-config.yaml` | Promtail 采集配置（监听 9080，抓后端日志文件并解析 traceId / userId / role） |
| `start-observability.ps1` | 一键启动 Loki + Promtail（Windows） |
| `grafana-provisioning/` | Grafana 数据源与看板自动配置（`smart-medical.json`） |
| `alloy-config.alloy` | Grafana Alloy 的等价采集配置（可选，替代 Promtail） |

> **二进制文件不在仓库里**（体积太大，GitHub 单文件上限 100MB），请按下面自行下载。

## 1. 下载二进制

放到本目录下，保持文件名一致：

| 组件 | 下载地址 | 需要的文件名 |
| --- | --- | --- |
| Loki | https://github.com/grafana/loki/releases （`loki-windows-amd64.exe.zip`） | `loki-windows-amd64.exe` |
| Promtail | https://github.com/grafana/loki/releases （`promtail-windows-amd64.exe.zip`） | `promtail-windows-amd64.exe` |
| Grafana | https://grafana.com/grafana/download?platform=windows | 解压后运行 `bin/grafana.exe` |

本项目实测版本：Loki v3.6.20、Promtail v3.0.0、Grafana 13.3.0。

## 2. 启动

```powershell
# 1) 先启动后端（dev profile，日志写到 smart-medical-backend/logs/smart-medical.log）
cd smart-medical-backend
mvn spring-boot:run

# 2) 启动 Loki + Promtail
cd observability
powershell -ExecutionPolicy Bypass -File start-observability.ps1

# 3) 解压 Grafana 后启动
.\grafana-13.3.0\bin\grafana.exe server --homepath .\grafana-13.3.0
```

访问：

- Loki：http://127.0.0.1:3100
- Promtail targets：http://127.0.0.1:9080/targets
- Grafana：http://127.0.0.1:3000（默认 admin / admin）

## 3. 配置 Grafana

- 数据源：`http://127.0.0.1:3100`（Loki）
- 看板：导入 `grafana-provisioning/dashboards/smart-medical.json`
- 也可以把 `grafana-provisioning/` 整个拷到 Grafana 的 `conf/provisioning/` 下自动加载

## 4. 用 traceId 查链路

在 Grafana Explore 里选 Loki 数据源：

```logql
{job="smart-medical-backend"} |= "traceId=3f9a1c2b"
```

统计某接口的慢请求（按耗时排序需要日志里带 cost 字段）：

```logql
{job="smart-medical-backend"} |= "ai.chat" | pattern `<time> <_> <_> traceId=<traceId> <_>` 
```

## 注意

- 配置里的路径（`D:\smart-medical\...`）是作者本机路径，换机器请改成自己的绝对路径；
- `loki-data/`、`promtail-data/` 是运行时数据，已在 `.gitignore` 中忽略；
- Loki 默认保留 7 天（`limits_config.retention_period: 168h`）。
