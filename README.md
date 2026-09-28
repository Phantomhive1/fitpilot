# FitPilot · AI 健身教练 🏋️

基于 **Spring Boot 3 + Vue 3 + 火山方舟豆包 Seed 系列模型** 的智能健身训练系统。

> 五大功能：训练需求 → AI 生成计划 → 仪表盘 → AI 实时教练聊天 → 姿势纠正（图片/视频）

## 功能

| # | 功能 | 说明 |
|---|------|------|
| 1 | 填写训练需求 | 目标 / 水平 / 可用器械 / 伤病受限 / 频率时长 |
| 2 | AI 生成训练计划 | 豆包按需求定制一周计划，规避伤病部位、匹配器械与水平 |
| 3 | 计划仪表盘 | 按天列出动作 / 组数 / 次数 / 组间休息 / 要点，支持多版本切换 |
| 4 | AI 教练实时聊天 | SSE 流式对话；说一句"周三腿日减个动作"即自动保存新版本计划 |
| 5 | 姿势纠正 | 上传图片或视频（≤100MB），豆包视觉模型评分 + 问题 + 纠正建议 |

## 技术栈

- **后端**：Spring Boot 3.3 / Java 17 / JPA / H2（开发）/ MySQL 8（生产）
- **前端**：Vue 3 + Vite + Element Plus，玻璃拟态深色健身主题
- **AI 模型**：火山方舟豆包 Seed 系列（多模态理解 + 文本生成）
- **流式协议**：SSE（Server-Sent Events），聊天与前端实时推送

## 目录结构

```
fitpilot/
├── fitpilot-backend/                # Spring Boot 后端
│   └── src/main/
│       ├── java/com/fitpilot/
│       │   ├── client/ArkClient     # 方舟 API 客户端（chat + SSE 流式 + Files API）
│       │   ├── service/             # PlanService / ChatService / VisionService
│       │   ├── controller/          # REST + SSE
│       │   ├── config/              # CORS / ArkProperties
│       │   ├── model/ repo/ dto/
│       │   └── ...
│       └── resources/
│           ├── application.yml        # 开发（默认）
│           └── application-prod.yml   # 生产（MySQL + 日志文件）
├── fitpilot-frontend/               # Vue 3 前端
│   └── src/views/                  # RequirementForm / Dashboard / Chat / PoseCheck
└── deploy/                          # 部署脚手架
    ├── Dockerfile / Dockerfile.frontend
    ├── docker-compose.yml          # MySQL + 后端 + 前端一键起
    ├── .env.example                # 环境变量模板（cp 到 .env 改值）
    ├── nginx/fitpilot.conf         # 反向代理 + HTTPS
    ├── fitpilot.service            # systemd unit（非 Docker 部署）
    └── scripts/deploy.sh
```

## 快速开始（开发）

### 0. 前置条件

- JDK 17+、Maven 3.8+、Node 18+
- 火山方舟 [API Key](https://console.volcengine.com/ark)：开通豆包并创建 Key

### 1. 配置 API Key 与模型 ID

**关键原则：Key 永远从环境变量读，绝不写进代码或提交到仓库。**

```bash
# Git Bash / Linux / macOS
export ARK_API_KEY='ark-xxxxxx-...'              # 必填
export ARK_CHAT_MODEL='doubao-seed-2-1-pro-260915'      # 文本/计划（可选）
export ARK_VISION_MODEL='doubao-seed-2-1-pro-260915'     # 视觉/姿势纠正（可选）
```

```powershell
# PowerShell
$env:ARK_API_KEY="ark-xxxxxx-..."
$env:ARK_CHAT_MODEL="doubao-seed-2-1-pro-260915"
$env:ARK_VISION_MODEL="doubao-seed-2-1-pro-260915"
```

**IDEA 用户**：Run → Edit Configurations → Environment variables 填同样几行。

> ⚠️ 模型 ID 必须以[方舟控制台 - 语言模型](https://console.volcengine.com/ark) 详情页复制的为准。
> 「视觉模型」页（Seedream/Seedance/Seed3D）是文生图/视频/3D 生成模型，不能做图片理解。
> Seed 2.1 Pro / 2.0 Pro / 1.8 文本/图片/视频都支持，文本与视觉可共用同一模型。

### 2. 启动后端（8080 端口，H2 文件库）

```bash
cd fitpilot-backend
mvn spring-boot:run
```

### 3. 启动前端（5173 端口）

```bash
cd fitpilot-frontend
npm install
npm run dev
```

访问 http://localhost:5173

## 部署到云服务器

### 方案 A：Docker Compose（最省事，推荐）

服务器只需装 Docker + Docker Compose，其他全都容器化。

```bash
# 1. 上传项目到服务器
scp -r fitpilot/ root@你的服务器:/opt/

# 2. 配置环境变量
cd /opt/fitpilot/deploy
cp .env.example .env
vim .env            # 填 ARK_API_KEY / DB_PASSWORD

# 3. 一键启动
docker compose up -d --build

# 4. 看日志
docker compose logs -f backend
```

访问 `http://服务器IP` 即可。三层都拉起来了：MySQL 8 + 后端 jar + 前端静态托管。

### 方案 B：传统部署（systemd + Nginx + MySQL）

适合不想用 Docker 的场景：

```bash
# 1. 本地打包
cd fitpilot-backend
mvn clean package -DskipTests
# 产物在 target/fitpilot-backend-*.jar

# 2. 上传到服务器
scp target/fitpilot-backend-*.jar root@server:/opt/fitpilot/fitpilot-backend.jar

# 3. 服务器：装 systemd unit
cp deploy/fitpilot.service /etc/systemd/system/fitpilot.service
vim /etc/systemd/system/fitpilot.service   # 改 ARK_API_KEY 等
systemctl daemon-reload
systemctl enable --now fitpilot
journalctl -u fitpilot -f
```

### Nginx + 域名 + HTTPS

```bash
cp deploy/nginx/fitpilot.conf /etc/nginx/conf.d/
vim /etc/nginx/conf.d/fitpilot.conf       # 改 server_name 和证书路径

# 申请免费 HTTPS 证书（Let's Encrypt）
certbot --nginx -d fit.yourdomain.cn

nginx -t && nginx -s reload
```

中国大陆服务器必须 ICP 备案，否则域名无法访问——备案免费但要 10~20 天，先提交再继续。

### 数据库切换

开发期默认 H2（文件库，零配置）。**生产必切 MySQL**：

```bash
# application-prod.yml 已配好，从环境变量读取：
DB_HOST=mysql
DB_PORT=3306
DB_NAME=fitpilot
DB_USER=fitpilot
DB_PASSWORD=xxxxxx

# 启动时指定：
java -jar fitpilot.jar --spring.profiles.active=prod
```

JPA `ddl-auto: update` 会自动建表，无需手写 schema。

### 配置总表

| 配置项 | 环境变量 | 默认 | 必填 |
|---|---|---|---|
| 方舟 API Key | `ARK_API_KEY` | — | ✅ |
| 方舟 base url | `ARK_BASE_URL` | `https://ark.cn-beijing.volces.com/api/v3` | |
| 文本模型 ID | `ARK_CHAT_MODEL` | `doubao-seed-2-1-pro-260915` | |
| 视觉模型 ID | `ARK_VISION_MODEL` | `doubao-seed-2-1-pro-260915` | |
| 数据库地址 | `DB_HOST` | `mysql`（compose 网络内） | 生产必填 |
| 数据库端口 | `DB_PORT` | `3306` | |
| 数据库名 | `DB_NAME` | `fitpilot` | |
| 数据库用户 | `DB_USER` | `fitpilot` | |
| 数据库密码 | `DB_PASSWORD` | — | ✅ |
| Spring profile | `SPRING_PROFILES_ACTIVE` | `default`（dev）→ `prod` | 生产设 `prod` |
| CORS 允许域 | `fitpilot.cors.allowed-origins` | `http://localhost:5173` | 生产改域名 |

## 主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/requirements` | 保存训练需求 |
| POST | `/api/requirements/{id}/plan` | AI 生成一周训练计划 |
| GET  | `/api/plans` · `/api/plans/{id}` | 计划列表 / 按天明细 |
| POST | `/api/chat` | SSE 流式聊天，事件含 `token` / `plan_updated` / `done` |
| GET  | `/api/chat/history?sessionId=` | 聊天历史 |
| POST | `/api/vision/analyze` | multipart 上传图片/视频，返回 `{score, verdict, issues, suggestions, safety}` |

## 聊天改计划的机制

系统提示要求模型在用户请求修改计划时，于回复末尾输出 ```plan``` 包裹的完整更新后 JSON，自动解析保存为**新版本计划**（source=chat），并通过 SSE 推送 `plan_updated` 事件。历史计划均保留，可随时回看对比。

## 后续可扩展

- 用户登录（Spring Security）与多用户隔离
- 训练打卡与完成度统计
- 计划周期化（4 周递进 + deload）
- 对象存储中转（OSS/COS）支持 >100MB 视频

## License

MIT