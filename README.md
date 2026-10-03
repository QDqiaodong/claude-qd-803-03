# 印染厂 · 染缸与染色配方管理

以「染缸 + 染色配方 + 工艺曲线」为核心的印染车间管理系统。

## 启动

```bash
./start.sh          # 秒起（镜像已存在）
./start.sh --build  # 首次或改代码后重建
./stop.sh / ./resume.sh / ./clean.sh / ./nuke.sh
```

| 服务 | 端口 |
| --- | --- |
| 前端（Vue 3 + Element Plus + Vite / nginx） | 8283 |
| 后端（Spring Boot 3.3.0 + Java 17） | 8383 |
| MySQL 8（库名 `dyeing_mill`） | 3583 |
| Redis 7 | 6583 |

表结构与种子数据由 `mysql/schema.sql` 在构建期导入（`ddl-auto: none`，应用不建表）。

## 七个模块

1. **染缸台账** — 缸号、机台类型（溢喷/气流/卷染）、容量、浴比、六态流转；缸位温度环阵列 + 控温面板。
2. **染色配方** — 色号、客户、布种、LAB；染料阶梯表行内编辑 owf，合计必须 100%；助剂单列，自动核算元/kg 成本。
3. **工艺曲线** — 升温/保温/降温节点序列，SVG 上拖动节点改温度与时长，实时算各段 ℃/min。
4. **布车流转** — 三列看板（待排产/在染/已完成），HTML5 原生拖拽，落入「在染」时选染缸与时段。
5. **开缸执行单** — 串联已排缸布车、最新已发布配方和匹配曲线；待确认资料指纹复核，下达时保存完整快照并同步染缸目标温度/保温时长。
6. **染料与助剂库存** — 库存 / 安全库存 / 批次 / 效期，领用登记与库存预警。
7. **染缸换色排程台** — 待排产布车/配方色值/缸容机型/维护时段/交期统一进跨缸计划。
   - 颜色族清洗矩阵区分同色续染、浅转深、深转浅、特殊染料切换的清洗时长与成本；
   - 后台批量重算（相同输入自动合并、页面轮询，不阻塞页面），只调未锁定的未来区段，人工锁定的承诺缸位原样保留；
   - 每辆布车给出安排/延后/排除解释；容量不足、机型不适配、配方资料不完整、维护窗口冲突单列具体原因；
   - 时间轴、染缸负载、清洗占时、方案差异；多方案留存、选定唯一车间参考，绝不回写布车或开缸执行单；
   - 矩阵调整发新版本，新方案用新口径；旧方案凭输入快照 + 当时规则版本复算并显示原解释。

## 后端技术标识

- Hibernate 6 `@SQLRestriction` 做全局数据过滤：软删除（`deleted = 0`）、报废布车（`status <> 'SCRAPPED'`）、布种档案（`enabled = 1`）。
- `@OneToMany + @OrderBy` 维护有序明细：配方明细按 `seqNo`、曲线节点按 `seqNo`、领用记录按 `issueTime DESC`。
- 手写 JPQL：按色号统计缸次、按客户汇总米数、染料库存预警、染缸时段冲突、机台类型分布等。
- 业务规则放 service 并抛 `BizException`：染缸时段冲突、owf 合计 100%（报错给差值）、领用不得超过库存、升温速率超布种上限。
- 开缸执行单：创建时同时核对布种/色号、已发布最新版本、匹配曲线、染缸容量和机台温度上限；待确认单按资料指纹复核，配方/曲线/排缸变化自动失效；下达用布车行级锁 + `uk_issued_cart(active_cart_id)` 保证同车仅一张生效，并在同一事务内锁定快照、更新布车/染缸和 Redis 参数。
- 异常验收开关：后端设置 `EXECUTION_FAIL_AFTER_PARAMS=true` 可在 Redis 参数写入后模拟失败，用于确认执行单/布车/染缸回滚、Redis 参数恢复。
- 换色排程：排程引擎（`engine` 包）是无副作用、可重放的纯算法（硬约束=机型/容量/时段/清洗接续/维护/锁定，评分=急单交期→逾期→空档→清洗成本）；
  输入经 `ScheduleInputAssembler` 从现场数据或历史快照装配；方案异步生成走单线程重算池 + 同 scope 作业合并；
  计算期间若交期/锁定等输入又被修改，同一作业自动按最新输入补跑一轮（`dirty` + 每 scope 锁），
  因此连续修改只重算最新口径，且轮询到的最终方案必然与最新输入一致；
  结果存 `schedule_plan`/`schedule_plan_assignment`，并带生成列唯一索引 `uk_plan_reference` 保证同表仅一个参考方案。

## 前端导航

九宫格启动台：默认全屏无侧栏，右上角菜单按钮弹出模块导航（7 个模块 + 多个带预设参数的快捷入口），
选定后模块名显示在顶部标题条，内容区全屏铺开。主色 `#4a148c`。

## 主要接口

```
GET    /api/vats                  染缸列表
PATCH  /api/vats/{id}/control     控温（目标温度 + 保温时长）
POST   /api/vats/{id}/tick        推进一拍
GET    /api/recipes               配方（含明细）
PUT    /api/recipes/{id}          保存配方（校验 owf 合计 100%）
GET    /api/curves                工艺曲线（含节点）
PUT    /api/curves/{id}/nodes     保存节点（校验升温速率）
GET    /api/carts/board           布车看板三列
POST   /api/carts/{id}/assign     排缸（校验染缸时段冲突）
GET    /api/execution-orders       开缸执行单列表（自动复核待确认单）
POST   /api/execution-orders/preview 开缸前核对并返回具体差异
POST   /api/execution-orders       创建待确认执行单
POST   /api/execution-orders/{id}/issue 下达（快照 + 布车/染缸/Redis 同事务）
GET    /api/inventory/alerts      库存预警
POST   /api/inventory/issue       领用（校验库存与效期）

GET    /api/schedule/context               排程台上下文（待排池预检/缸/色族/维护/锁定/当前规则版本）
PATCH  /api/schedule/carts/{id}            改交期/急单（只改排程字段，不动布车状态）
GET|POST|DELETE /api/schedule/maintenance  维护时段增删查
GET|POST|DELETE /api/schedule/locks        人工锁定段增删查（同缸重叠拦截）
GET    /api/schedule/wash-versions         清洗规则版本列表
GET    /api/schedule/wash-matrix           颜色族清洗矩阵（可按版本查）
POST   /api/schedule/wash-versions         发布新版矩阵（旧版本归档保留）
POST   /api/schedule/plans/generate        后台批量重算（返回 jobId，相同输入合并）
GET    /api/schedule/jobs/{jobId}          轮询重算作业
GET    /api/schedule/plans                 方案列表（可留存多份）
GET    /api/schedule/plans/{id}            方案详情（时间轴/负载/解释/口径版本）
POST   /api/schedule/plans/{id}/reference  选定为车间参考（全局唯一，仅标注）
POST   /api/schedule/plans/{id}/replay     按方案当时规则版本 + 输入快照复算
```
