# JobProof Web 前端

Vue 3 + TypeScript + Vite。当前产品包括 AI 简历、简历管理、模板中心、岗位/JD 匹配、职业规划、模拟面试和求职资料库；旧聚合工作台已删除，`/workspace` 仅保留为求职资料库的兼容跳转。

当前验证结果与剩余事项见 [实施记录](../docs/visual/2026-09-05-implementation-status.md)。`docs/plan/` 中的 S0 文档仅作历史参考。

```bash
cd frontend
npm ci
npm run dev
```

后端须另开：`cd backend && mvn spring-boot:run`。禁止用 Python 跑本项目。

使用 `.node-version` 中的 Node 24.19.0 和 npm；唯一安装基线为 `package-lock.json`。Node 24.6 不满足当前部分传递依赖的 engine 要求。

本机开发默认前端 5174、后端 18081（与隔离验收环境一致）。如需连接其他后端，在 PowerShell 中设置 `$env:JOBPROOF_API_TARGET='http://127.0.0.1:<端口>'`，再运行 `npm run dev -- --host 127.0.0.1 --port 5174`。后端还必须将该前端来源加入本地 CORS 配置，例如启动参数 `--jobproof.cors.origins=http://127.0.0.1:5174`；只改代理目标不能解决浏览器 POST 的来源校验。不要放宽生产白名单或停止不属于本项目的服务。

验证命令：`npm test`、`npm run build`。`scripts/authenticated-acceptance.mjs` 和 `scripts/resume-acceptance.mjs` 使用会写入合成数据的隔离测试环境，固定连接 `127.0.0.1:5174`，不得对生产数据运行。

首页品牌场景按需加载；小于 921px、系统减少动态、WebGL 不可用时显示静态示意。在开发环境使用 `?studio=still` 固定场景时间，`?studio=static` 检查静态降级；这两个参数不会改变认证或任何业务数据。
