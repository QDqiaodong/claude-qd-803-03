# 换色排程台 · 本地验证资产（不参与 docker 构建）

这个目录是排程台的**离线验收工具**，不依赖 docker / MySQL / Redis，也不改动 `pom.xml`（无 test scope 依赖，预热镜像依赖面不变）。

| 文件 | 作用 |
| --- | --- |
| `EngineHarness.java` | 纯引擎夹具：深浅连续切换、急单 vs 清洗、维护窗口、人工锁定、缺资料排除、版本兼容、重算一致性（15 项断言） |
| `H2TestApplication.java` | 用 H2(MySQL 方言) 起 Spring Boot 实例（:8399），StringRedisTemplate 用不连库的空壳 |
| `schema-h2.sql` / `data-h2.sql` | 与 `../src/main/resources/schema.sql` 同结构、同种子的 H2 方言版本 |
| `application-h2.yml` | H2 数据源/初始化脚本位置/排除 Redis 自动配置 |
| `e2e.sh` | 接口端到端验收：作业合并、排除原因、维护/锁定、版本发布、复算、唯一参考、不回写布车 |
| `run-h2.sh` | 一键脚本：编译主代码 → 拉单个 H2 jar → 跑引擎夹具 → 起实例 → 跑 e2e |

## 运行

```bash
cd backend
bash harness/run-h2.sh            # 引擎夹具 + 接口 e2e（结束自动停实例）
bash harness/run-h2.sh --no-e2e   # 只起实例，手动 curl http://127.0.0.1:8399/api/schedule/context
```

> `schema-h2.sql`/`data-h2.sql` 是手工同步 MySQL schema 的方言副本；调整 `src/main/resources/schema.sql`
> 后，若影响结构/种子需同步这两份文件（生产构建只认 `mysql/schema.sql` 与 `src/main/resources/schema.sql`）。
