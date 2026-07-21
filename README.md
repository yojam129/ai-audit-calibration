# 基于 AI 的审核判读一致性校准与标准化管控

后端使用 Java 21、Spring Boot 3、Spring Cloud Alibaba 和独立 Python 推理服务；
前端使用 Vue 3、Element Plus 与 ECharts。

## 本地配置

各服务默认连接 `192.168.1.4（本人Linux虚拟机地址）`，敏感配置通过环境变量提供：

- `MYSQL_PASSWORD`
- `REDIS_PASSWORD`
- `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD`
- `MONGODB_USERNAME` / `MONGODB_PASSWORD`
- `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`
- `JWT_SECRET`

各服务使用独立数据库和独立 Flyway history table，避免共享数据库时迁移版本冲突。

## 构建

```bash
mvn -gs .mvn/settings.xml -s .mvn/settings.xml clean test
cd frontend
npm install
npm run build
```

网关端口为 `8088`，前端开发端口为 `5173`。所有 Java 业务服务均注册到 Nacos，
服务间 OpenFeign 定义在 `yo-api`。
