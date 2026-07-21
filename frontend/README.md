# YO AI 审核一致性管控前端

技术栈：Vue 3、TypeScript、Vite、Element Plus、ECharts、Pinia、Vue Router、Axios。

```bash
npm install
npm run dev
```

前端只读取真实后端接口，不包含 Mock 数据或失败时的假数据回退。默认通过
`http://localhost:18088` 网关访问微服务，可在 `.env.local` 中用
`VITE_API_BASE_URL` 覆盖。

```bash
npm run build
```

演示账号和基础数据由后端 Flyway 脚本维护。部署时必须通过环境变量提供基础设施密码、
JWT 密钥和内部服务调用密钥，禁止把真实密钥写入前端或提交到仓库。
