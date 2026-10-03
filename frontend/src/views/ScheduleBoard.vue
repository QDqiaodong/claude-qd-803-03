<template>
  <div class="page sch-page">
    <!-- 顶部操作条 -->
    <div class="panel sch-bar">
      <div class="sch-bar__group">
        <el-button size="small" @click="loadAll">刷新数据</el-button>
        <el-button size="small" :type="tab === 'board' ? 'primary' : ''" @click="tab = 'board'">排程时间轴</el-button>
        <el-button size="small" :type="tab === 'plans' ? 'primary' : ''" @click="tab = 'plans'; loadPlans()">方案库</el-button>
        <el-button size="small" :type="tab === 'matrix' ? 'primary' : ''" @click="tab = 'matrix'; loadMatrix()">清洗矩阵</el-button>
      </div>
      <div class="sch-bar__group">
        <el-tag size="small" type="info" effect="plain">当前口径：清洗规则 V{{ ctx.activeRuleVersion?.versionNo || '?' }}</el-tag>
        <el-button size="small" type="primary" :loading="generating" @click="onGenerate">
          {{ generating ? '后台重算中…' : '批量重算方案' }}
        </el-button>
      </div>
    </div>

    <!-- ============ 时间轴视图 ============ -->
    <template v-if="tab === 'board'">
      <!-- 指标条 -->
      <div class="panel metrics">
        <div class="metric"><span>待排布车</span><b>{{ pendingCarts.length }}</b></div>
        <div class="metric"><span>已排入</span><b class="ok">{{ currentPlan?.scheduledCarts ?? '—' }}</b></div>
        <div class="metric"><span>排除/缺资料</span><b :class="{ bad: (currentPlan?.excludedCarts || 0) > 0 }">{{ currentPlan?.excludedCarts ?? '—' }}</b></div>
        <div class="metric"><span>逾期车数</span><b :class="{ bad: (currentPlan?.lateCarts || 0) > 0 }">{{ currentPlan?.lateCarts ?? '—' }}</b></div>
        <div class="metric"><span>清洗总占时</span><b>{{ fmtMin(currentPlan?.totalWashMinutes) }}</b></div>
        <div class="metric"><span>清洗总成本</span><b>¥{{ currentPlan?.totalWashCost ?? '—' }}</b></div>
        <div class="metric"><span>空档总时长</span><b>{{ fmtMin(currentPlan?.totalIdleMinutes) }}</b></div>
        <div class="metric"><span>逾期总分钟</span><b :class="{ bad: (currentPlan?.totalLateMinutes || 0) > 0 }">{{ currentPlan?.totalLateMinutes ?? '—' }}</b></div>
        <div class="metric"><span>口径版本</span><b>V{{ currentPlan?.ruleVersionNo ?? '—' }}</b></div>
      </div>

      <div class="board-grid">
        <!-- 左：待排池 + 排除清单 -->
        <div class="side">
          <div class="panel side__panel">
            <div class="panel__head"><span class="panel__title">待排产布车（{{ pendingCarts.length }}）</span>
              <span class="muted" style="font-size:11px">急单 → 交期 优先</span>
            </div>
            <div class="panel__body side__body">
              <div v-for="c in pendingCarts" :key="c.id" :class="['ccard', c.rushFlag ? 'is-rush' : '']">
                <div class="ccard__top">
                  <span class="ccard__code">{{ c.cartCode }}</span>
                  <el-tag v-if="c.rushFlag" size="small" type="danger" effect="dark">急单</el-tag>
                  <el-tag v-if="c.status === 'REWORK'" size="small" type="warning">返修×{{ c.reworkTimes }}</el-tag>
                </div>
                <div class="ccard__color">
                  <i :style="{ background: colorHex(c.colorCode) }"></i>{{ c.colorName }} · {{ c.familyName || '未配色族' }}
                </div>
                <div class="ccard__line">{{ fabricName(c.fabricType) }} · {{ c.meters }}m ≈ {{ c.weightKg }}kg · {{ c.customer }}</div>
                <div class="ccard__line">
                  交期：<b :class="{ bad: isOverdue(c.dueTime) }">{{ fmtTime(c.dueTime) || '无' }}</b>
                  <el-button link type="primary" size="small" @click="openDue(c)">改交期/急单</el-button>
                </div>
                <div v-for="iss in c.issues" :key="iss" class="ccard__issue">⚠ {{ iss }}</div>
              </div>
              <div v-if="!pendingCarts.length" class="empty">暂无待排布车</div>
            </div>
          </div>

          <div class="panel side__panel">
            <div class="panel__head">
              <span class="panel__title">维护时段 / 锁定段</span>
              <div>
                <el-button link type="primary" size="small" @click="maintDialog = true">+维护</el-button>
                <el-button link type="primary" size="small" @click="openLock()">+锁定</el-button>
              </div>
            </div>
            <div class="panel__body side__body side__body--sm">
              <div v-for="l in ctx.locks" :key="'l'+l.id" class="blk blk--lock">
                <b>🔒 {{ l.vatCode }} {{ l.cartCode || '（封缸）' }}</b>
                <span>{{ fmtTime(l.startTime) }} → {{ fmtTime(l.endTime) }}：{{ l.lockReason }}</span>
                <el-button link type="danger" size="small" @click="removeLock(l.id)">解除</el-button>
              </div>
              <div v-for="m in ctx.maintenance" :key="'m'+m.id" class="blk blk--maint">
                <b>🛠 {{ m.vatCode }}</b>
                <span>{{ fmtTime(m.startTime) }} → {{ fmtTime(m.endTime) }}：{{ m.reason }}</span>
                <el-button link type="danger" size="small" @click="removeMaint(m.id)">删除</el-button>
              </div>
              <div v-if="!ctx.locks?.length && !ctx.maintenance?.length" class="empty">暂无维护/锁定</div>
            </div>
          </div>
        </div>

        <!-- 右：时间轴 + 缸负载 + 排除清单 -->
        <div class="main-col">
          <div class="panel">
            <div class="panel__head">
              <span class="panel__title">跨缸计划时间轴（锚点 {{ fmtTime(viewStart) }} 起 5 天）</span>
              <el-tag v-if="currentPlan?.status === 'REFERENCE'" type="success" size="small">★ 车间参考方案</el-tag>
              <el-tag v-else-if="currentPlan" size="small" type="info">草稿方案</el-tag>
            </div>
            <div class="panel__body">
              <ScheduleGantt
                :vats="ctx.vats || []"
                :assignments="planAssignments"
                :maintenance="ctx.maintenance || []"
                :locks="ctx.locks || []"
                :start="viewStart"
                :end="viewEnd"
                @select="openAssignment"
              />
              <div v-if="!planData" class="empty" style="padding: 30px">
                尚无方案，点击右上角「批量重算方案」生成第一版（后台执行，不阻塞页面）
              </div>
            </div>
          </div>

          <div class="panel">
            <div class="panel__head"><span class="panel__title">染缸负载与清洗占时</span></div>
            <div class="panel__body">
              <el-table :data="vatLoadRows" size="small" border stripe>
                <el-table-column prop="vatCode" label="缸号" width="80" />
                <el-table-column label="机型/容量" width="120">
                  <template #default="{ row }">{{ machine(row.machineType) }} / {{ row.capacityKg }}kg</template>
                </el-table-column>
                <el-table-column prop="dyeCount" label="缸次" width="70" />
                <el-table-column label="染色时长" width="100">
                  <template #default="{ row }">{{ fmtMin(row.dyeMin) }}</template>
                </el-table-column>
                <el-table-column label="清洗时长" width="100">
                  <template #default="{ row }">{{ fmtMin(row.washMin) }}</template>
                </el-table-column>
                <el-table-column label="清洗成本" width="100">
                  <template #default="{ row }">¥{{ row.washCost }}</template>
                </el-table-column>
                <el-table-column label="负载率（视窗内）">
                  <template #default="{ row }">
                    <el-progress :percentage="row.loadPct" :stroke-width="12"
                                 :color="row.loadPct > 90 ? '#c62828' : '#4a148c'" />
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>

          <div class="panel">
            <div class="panel__head">
              <span class="panel__title">排除 / 延后布车原因（{{ excludedRows.length }}）</span>
            </div>
            <div class="panel__body">
              <el-table :data="excludedRows" size="small" border stripe max-height="240">
                <el-table-column prop="cartCode" label="布车" width="90" />
                <el-table-column label="类型" width="90">
                  <template #default="{ row }">
                    <el-tag size="small" type="danger">排除</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="reasonText" label="具体原因（容量 / 机型 / 资料 / 维护窗口 / 视窗）" />
              </el-table>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ============ 方案库视图 ============ -->
    <template v-if="tab === 'plans'">
      <div class="panel">
        <div class="panel__head">
          <span class="panel__title">留存方案（旧方案保留当时规则版本，可随时复算查看原解释）</span>
        </div>
        <div class="panel__body">
          <el-table :data="plans" size="small" border stripe>
            <el-table-column label="方案" min-width="200">
              <template #default="{ row }">
                <b>{{ row.planName }}</b>
                <div class="muted" style="font-size:11px">{{ row.planCode }}</div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'REFERENCE'" type="success" size="small">★ 车间参考</el-tag>
                <el-tag v-else-if="row.anchorMode === 'REPLAY'" type="warning" size="small">历史复算</el-tag>
                <el-tag v-else size="small" type="info">草稿</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ruleVersionNo" label="口径版本" width="80">
              <template #default="{ row }">V{{ row.ruleVersionNo }}</template>
            </el-table-column>
            <el-table-column prop="scheduledCarts" label="排入" width="60" />
            <el-table-column prop="excludedCarts" label="排除" width="60" />
            <el-table-column prop="lateCarts" label="逾期" width="60" />
            <el-table-column label="清洗" width="120">
              <template #default="{ row }">{{ fmtMin(row.totalWashMinutes) }} / ¥{{ row.totalWashCost }}</template>
            </el-table-column>
            <el-table-column label="创建" width="140">
              <template #default="{ row }">{{ fmtTime(row.createTime) }}<div class="muted" style="font-size:11px">{{ row.createdBy }}</div></template>
            </el-table-column>
            <el-table-column label="操作" width="300">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openPlan(row)">打开</el-button>
                <el-button link type="primary" size="small" @click="diffPlan(row)">与参考对比</el-button>
                <el-button link type="warning" size="small" @click="replayPlan(row)">按当时版本复算</el-button>
                <el-button v-if="row.status !== 'REFERENCE'" link type="success" size="small" @click="referencePlan(row)">选为参考</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <!-- 方案差异 -->
      <div v-if="diffData" class="panel">
        <div class="panel__head"><span class="panel__title">方案差异：{{ diffData.a.planName }}（当前打开） ⇄ {{ diffData.b.planName }}（参考）</span></div>
        <div class="panel__body">
          <el-table :data="diffRows" size="small" border>
            <el-table-column prop="label" label="指标" width="160" />
            <el-table-column label="当前方案" width="140">
              <template #default="{ row }"><b>{{ row.a }}</b></template>
            </el-table-column>
            <el-table-column label="参考方案" width="140">
              <template #default="{ row }">{{ row.b }}</template>
            </el-table-column>
            <el-table-column label="变化">
              <template #default="{ row }">
                <el-tag size="small" :type="row.delta > 0 ? 'danger' : row.delta < 0 ? 'success' : 'info'">
                  {{ row.delta > 0 ? '+' : '' }}{{ row.deltaText }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div v-for="d in diffData.cartChanges" :key="d.cartCode" class="diff-line">
            <b>{{ d.cartCode }}</b>：{{ d.from || '未排' }} → {{ d.to || '排除' }}
            <span v-if="d.note" class="muted">（{{ d.note }}）</span>
          </div>
        </div>
      </div>
    </template>

    <!-- ============ 清洗矩阵视图 ============ -->
    <template v-if="tab === 'matrix'">
      <div class="panel">
        <div class="panel__head">
          <span class="panel__title">颜色族</span>
          <el-tag size="small" type="info">深浅 + 染料族共同决定清洗等级</el-tag>
        </div>
        <div class="panel__body family-chips">
          <div v-for="f in ctx.families" :key="f.id" class="fchip">
            <i :style="{ background: f.colorHex }"></i>
            <b>{{ f.familyName }}</b>
            <span class="muted">{{ depthName(f.depthLevel) }} · {{ f.familyCode }}</span>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel__head">
          <span class="panel__title">清洗切换矩阵（当前 V{{ matrixData?.version?.versionNo }}）</span>
          <div>
            <el-select v-model="matrixVersionId" size="small" style="width: 150px" placeholder="查看旧版本"
                       @change="loadMatrix(matrixVersionId)">
              <el-option v-for="v in washVersions" :key="v.id" :value="v.id"
                         :label="`V${v.versionNo} ${v.status}`" />
            </el-select>
            <el-button size="small" type="primary" @click="openPublish">发布新版本（新方案用新口径）</el-button>
          </div>
        </div>
        <div class="panel__body">
          <el-table :data="matrixRows" size="small" border stripe max-height="420">
            <el-table-column label="源颜色族" min-width="130">
              <template #default="{ row }"><i class="dot" :style="{ background: row.fromHex }"></i>{{ row.fromName }}</template>
            </el-table-column>
            <el-table-column label="→" width="40" />
            <el-table-column label="目标颜色族" min-width="130">
              <template #default="{ row }"><i class="dot" :style="{ background: row.toHex }"></i>{{ row.toName }}</template>
            </el-table-column>
            <el-table-column label="切换类型" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="switchTag(row.switchType)">{{ switchName(row.switchType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="清洗(分)" width="90">
              <template #default="{ row }">{{ row.washMinutes }}</template>
            </el-table-column>
            <el-table-column label="成本(元)" width="100">
              <template #default="{ row }">{{ row.washCost }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="说明" min-width="180" />
          </el-table>
        </div>
      </div>
    </template>

    <!-- ===== 弹窗 ===== -->
    <el-dialog v-model="dueDialog" title="维护交期 / 急单（不改变布车状态）" width="420px">
      <el-form label-width="80px">
        <el-form-item label="布车"><b>{{ dueForm.cartCode }}</b> {{ dueForm.colorName }}</el-form-item>
        <el-form-item label="交期">
          <el-date-picker v-model="dueForm.dueTime" type="datetime" value-format="YYYY-MM-DD HH:mm" style="width:100%" />
        </el-form-item>
        <el-form-item label="急单">
          <el-switch v-model="dueForm.rushFlag" :active-value="1" :inactive-value="0"
                     active-text="急单优先保交期" inactive-text="普通单" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dueDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDue">保存（连续修改会合并重算）</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="maintDialog" title="登记染缸维护时段" width="420px">
      <el-form label-width="80px">
        <el-form-item label="染缸">
          <el-select v-model="maintForm.vatId" placeholder="选择染缸" style="width:100%">
            <el-option v-for="v in ctx.vats" :key="v.id" :value="v.id" :label="`${v.vatCode} ${machine(v.machineType)}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始"><el-date-picker v-model="maintForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm" style="width:100%" /></el-form-item>
        <el-form-item label="结束"><el-date-picker v-model="maintForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm" style="width:100%" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="maintForm.reason" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="maintDialog = false">取消</el-button>
        <el-button type="primary" @click="saveMaint">登记</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="lockDialog" title="锁定承诺缸位（重算不得移动）" width="460px">
      <el-form label-width="80px">
        <el-form-item label="染缸">
          <el-select v-model="lockForm.vatId" placeholder="选择染缸" style="width:100%">
            <el-option v-for="v in ctx.vats" :key="v.id" :value="v.id" :label="`${v.vatCode} ${machine(v.machineType)}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="布车">
          <el-select v-model="lockForm.cartId" clearable placeholder="不选 = 仅封缸" style="width:100%" filterable>
            <el-option v-for="c in pendingCarts" :key="c.id" :value="c.id" :label="`${c.cartCode} ${c.colorName}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始"><el-date-picker v-model="lockForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm" style="width:100%" /></el-form-item>
        <el-form-item label="结束"><el-date-picker v-model="lockForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm" style="width:100%" /></el-form-item>
        <el-form-item label="承诺原因"><el-input v-model="lockForm.lockReason" placeholder="如：已向客户承诺的急单缸位" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lockDialog = false">取消</el-button>
        <el-button type="primary" @click="saveLock">锁定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignDialog" title="布车排程解释" width="560px">
      <div v-if="selectedAssign" class="explain">
        <h4>{{ selectedAssign.cartCode }}
          <el-tag size="small" :type="selectedAssign.resultType === 'FIXED' ? 'info' : 'primary'">
            {{ selectedAssign.locked ? '人工锁定' : resultName(selectedAssign.resultType) }}
          </el-tag>
        </h4>
        <div class="explain__row">缸位：<b>{{ selectedAssign.vatCode }}</b>
          {{ fmtTime(selectedAssign.startTime) }} → {{ fmtTime(selectedAssign.endTime) }}</div>
        <div class="explain__row">入缸前清洗：
          <el-tag size="small" :type="switchTag(selectedAssign.switchType)">{{ switchName(selectedAssign.switchType) }}</el-tag>
          {{ selectedAssign.washBeforeMin || 0 }} 分钟 / ¥{{ selectedAssign.washBeforeCost || 0 }}
        </div>
        <div v-if="selectedAssign.lateMinutes > 0" class="explain__row bad">
          预计逾期 {{ (selectedAssign.lateMinutes / 60).toFixed(1) }} 小时
        </div>
        <el-divider />
        <div class="explain__reason">{{ selectedReasonText }}</div>
      </div>
    </el-dialog>

    <el-dialog v-model="publishDialog" title="发布新版清洗矩阵" width="900px" top="5vh">
      <el-input v-model="publishNote" placeholder="本版调整说明（如：深转浅延长到 90 分钟）" style="margin-bottom:10px" />
      <el-alert type="info" :closable="false" style="margin-bottom:10px"
                title="整矩阵提交：在下表编辑时长/成本/类型后发布；发布后新方案按新版本计算，已留存旧方案仍按保存时版本复算。" />
      <el-table :data="editCells" size="small" border max-height="420">
        <el-table-column label="源族" width="120">
          <template #default="{ row }">{{ familyName(row.fromFamilyId) }}</template>
        </el-table-column>
        <el-table-column label="目标族" width="120">
          <template #default="{ row }">{{ familyName(row.toFamilyId) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-select v-model="row.switchType" size="small">
              <el-option label="同色续染" value="SAME" />
              <el-option label="浅转深" value="L2D" />
              <el-option label="深转浅" value="D2L" />
              <el-option label="特殊切换" value="SPECIAL" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="清洗(分)" width="110">
          <template #default="{ row }"><el-input-number v-model="row.washMinutes" :min="0" :max="600" size="small" controls-position="right" style="width:100px" /></template>
        </el-table-column>
        <el-table-column label="成本(元)" width="130">
          <template #default="{ row }"><el-input-number v-model="row.washCost" :min="0" :precision="2" size="small" controls-position="right" style="width:120px" /></template>
        </el-table-column>
        <el-table-column label="说明" min-width="160">
          <template #default="{ row }"><el-input v-model="row.remark" size="small" /></template>
        </el-table-column>
        <el-table-column width="60">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="editCells.splice($index, 1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button size="small" style="margin-top:8px" @click="addCell">+ 新增切换格</el-button>
      <template #footer>
        <el-button @click="publishDialog = false">取消</el-button>
        <el-button type="primary" @click="savePublish">发布新版本</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ScheduleGantt from '../components/ScheduleGantt.vue'
import {
  scheduleContext, patchCartSchedule,
  listMaintenance, createMaintenance, deleteMaintenance,
  listScheduleLocks, createScheduleLock, deleteScheduleLock,
  listWashVersions, getWashMatrix, publishWashVersion,
  generateSchedule, getScheduleJob, listSchedulePlans, getSchedulePlan,
  markPlanReference, renameSchedulePlan, replaySchedulePlan
} from '../api'

const props = defineProps({ params: { type: Object, default: () => ({}) } })

const FABRIC = { COTTON: '全棉', POLY: '涤纶', NYLON: '锦纶', BLEND: '涤棉', VISCOSE: '人棉', SILK: '真丝', WOOL: '羊毛' }
const MACHINE = { OVERFLOW: '溢喷', AIRFLOW: '气流', JIG: '卷染' }
const COLOR_MAP = {
  'C-1901': '#1A2B6B', 'C-1902': '#8B1A1A', 'C-1903': '#0F6E4F', 'C-1904': '#C9A227',
  'C-1905': '#6B4A8F', 'C-1906': '#D8D3C4', 'C-1907': '#1F1F1F', 'C-1908': '#2E8BC0'
}

const tab = ref(props.params.tab || 'board')
const ctx = ref({})
const plans = ref([])
const planData = ref(null)
const currentPlan = ref(null)
const generating = ref(false)
let pollTimer = null
let latestJobToken = 0

const matrixData = ref(null)
const matrixVersionId = ref(null)
const washVersions = ref([])
const editCells = ref([])
const publishDialog = ref(false)
const publishNote = ref('')

const dueDialog = ref(false)
const dueForm = ref({})
const maintDialog = ref(false)
const maintForm = ref({})
const lockDialog = ref(false)
const lockForm = ref({})
const assignDialog = ref(false)
const selectedAssign = ref(null)
const diffData = ref(null)

const pendingCarts = computed(() => ctx.value.pendingCarts || [])

const viewStart = computed(() => {
  const base = currentPlan.value?.horizonStartTime
  return base ? String(base).replace('T', ' ').slice(0, 16) : defaultAnchor()
})
const viewEnd = computed(() => {
  const base = currentPlan.value?.horizonEndTime
  return base ? String(base).replace('T', ' ').slice(0, 16) : defaultAnchor(120)
})
function defaultAnchor(addHours = 0) {
  const d = new Date(Date.now() + addHours * 3600e3)
  d.setMinutes(0, 0, 0)
  return fmt(d)
}
function fmt(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

const planAssignments = computed(() => planData.value?.assignments || [])
const excludedRows = computed(() => planAssignments.value.filter((a) => a.resultType === 'EXCLUDED'))

const vatLoadRows = computed(() => {
  const horizonMin = 5 * 24 * 60
  const map = new Map((ctx.value.vats || []).map((v) => [v.id, {
    vatCode: v.vatCode, machineType: v.machineType, capacityKg: v.capacityKg,
    dyeCount: 0, dyeMin: 0, washMin: 0, washCost: 0
  }]))
  for (const a of planAssignments.value) {
    const row = map.get(a.vatId)
    if (!row || !a.startTime) continue
    row.dyeCount += 1
    row.dyeMin += minutesBetween(a.startTime, a.endTime)
    row.washMin += a.washBeforeMin || 0
    row.washCost = Number((row.washCost + Number(a.washBeforeCost || 0)).toFixed(2))
  }
  for (const row of map.values()) {
    row.loadPct = Math.min(100, Math.round(((row.dyeMin + row.washMin) / horizonMin) * 100))
  }
  return [...map.values()]
})

function minutesBetween(s, e) {
  return Math.max(0, Math.round((new Date(String(e).replace(' ', 'T')) - new Date(String(s).replace(' ', 'T'))) / 60000))
}

async function loadAll() {
  try {
    const res = await scheduleContext()
    ctx.value = res.data
    await loadPlans()
    // 默认打开参考方案，否则打开最新方案
    const ref = plans.value.find((p) => p.status === 'REFERENCE')
    if (ref) await openPlan(ref, true)
  } catch (e) { ElMessage.error(e.message) }
}

async function loadPlans() {
  try {
    const res = await listSchedulePlans()
    plans.value = res.data || []
  } catch (e) { ElMessage.error(e.message) }
}

async function openPlan(row, silent) {
  try {
    const res = await getSchedulePlan(row.id)
    planData.value = res.data
    currentPlan.value = res.data.plan
    tab.value = 'board'
    if (!silent) diffData.value = null
  } catch (e) { ElMessage.error(e.message) }
}

// ---------------- 异步重算 + 合并轮询 ----------------
async function onGenerate() {
  if (generating.value) return
  generating.value = true
  try {
    const res = await generateSchedule({})
    const token = ++latestJobToken
    if (res.data.merged) ElMessage.info('已有相同输入的重算在进行，已合并等待同一结果')
    pollJob(res.data.jobId, token)
  } catch (e) {
    generating.value = false
    ElMessage.error(e.message)
  }
}

function pollJob(jobId, token) {
  clearTimeout(pollTimer)
  const tick = async () => {
    try {
      const res = await getScheduleJob(jobId)
      const job = res.data
      // 只认最新一次提交：过期作业结果丢弃（连续修改交期/锁定时的无效结果不渲染）
      if (token !== latestJobToken) { generating.value = false; return }
      if (!job) { generating.value = false; return }
      if (job.status === 'DONE') {
        generating.value = false
        ElMessage.success('重算完成，结果已按最新输入生成新方案')
        await loadPlans()
        const fresh = plans.value.find((p) => p.id === job.planId)
        if (fresh) await openPlan(fresh, true)
        return
      }
      if (job.status === 'FAILED') {
        generating.value = false
        ElMessage.error('重算失败：' + job.error)
        return
      }
      pollTimer = setTimeout(tick, 800)
    } catch (e) {
      generating.value = false
      ElMessage.error(e.message)
    }
  }
  pollTimer = setTimeout(tick, 600)
}

// ---------------- 交期/急单（去抖：连续修改只触发一次重算） ----------------
let dueDirtyTimer = null
function openDue(cart) {
  dueForm.value = {
    id: cart.id, cartCode: cart.cartCode, colorName: cart.colorName,
    dueTime: cart.dueTime ? String(cart.dueTime).replace('T', ' ').slice(0, 16) : '',
    rushFlag: cart.rushFlag || 0
  }
  dueDialog.value = true
}
async function saveDue() {
  try {
    await patchCartSchedule(dueForm.value.id, { dueTime: dueForm.value.dueTime, rushFlag: dueForm.value.rushFlag })
    ElMessage.success('已保存（仅排程字段，布车状态未变）')
    dueDialog.value = false
    await scheduleContext().then((r) => (ctx.value = r.data))
    // 连续修改合并：1.2 秒内的多次保存只自动重算一次
    clearTimeout(dueDirtyTimer)
    dueDirtyTimer = setTimeout(() => { if (!generating.value) onGenerate() }, 1200)
  } catch (e) { ElMessage.error(e.message) }
}

// ---------------- 维护/锁定 ----------------
async function saveMaint() {
  try {
    await createMaintenance(maintForm.value)
    ElMessage.success('维护时段已登记')
    maintDialog.value = false
    maintForm.value = {}
    const r = await scheduleContext(); ctx.value = r.data
    if (!generating.value) onGenerate()
  } catch (e) { ElMessage.error(e.message) }
}
async function removeMaint(id) {
  await deleteMaintenance(id)
  ElMessage.success('已删除')
  const r = await scheduleContext(); ctx.value = r.data
}
function openLock() {
  lockForm.value = { vatId: null, cartId: null, startTime: defaultAnchor(2), endTime: defaultAnchor(6), lockReason: '' }
  lockDialog.value = true
}
async function saveLock() {
  try {
    await createScheduleLock(lockForm.value)
    ElMessage.success('承诺缸位已锁定')
    lockDialog.value = false
    const r = await scheduleContext(); ctx.value = r.data
    if (!generating.value) onGenerate()
  } catch (e) { ElMessage.error(e.message) }
}
async function removeLock(id) {
  await deleteScheduleLock(id)
  ElMessage.success('锁定已解除')
  const r = await scheduleContext(); ctx.value = r.data
  if (!generating.value) onGenerate()
}

// ---------------- 解释 ----------------
function openAssignment(a) {
  selectedAssign.value = a
  assignDialog.value = true
}
const selectedReasonText = computed(() => {
  const a = selectedAssign.value
  if (!a) return ''
  const row = planAssignments.value.find((x) => x.id === a.id)
  return row?.reasonText || '（固定段，重算不移动）'
})

// ---------------- 方案库操作 ----------------
async function referencePlan(row) {
  try {
    await markPlanReference(row.id)
    ElMessage.success('已选为车间参考方案（不影响布车/执行单）')
    await loadPlans()
  } catch (e) { ElMessage.error(e.message) }
}
async function replayPlan(row) {
  try {
    const res = await replaySchedulePlan(row.id, '调度员')
    ElMessage.success(`已按 V${row.ruleVersionNo} 口径复算，新方案「${res.data.planName}」在方案库中`)
    await loadPlans()
  } catch (e) { ElMessage.error(e.message) }
}
async function diffPlan(row) {
  try {
    const ref = plans.value.find((p) => p.status === 'REFERENCE')
    if (!ref) { ElMessage.warning('还没有车间参考方案，请先选定一个'); return }
    const [a, b] = await Promise.all([getSchedulePlan(row.id), getSchedulePlan(ref.id)])
    diffData.value = buildDiff(a.data, b.data)
  } catch (e) { ElMessage.error(e.message) }
}

function buildDiff(a, b) {
  const num = (v) => Number(v || 0)
  const rows = [
    { label: '排入车数', key: 'scheduledCarts', better: 'up' },
    { label: '排除车数', key: 'excludedCarts', better: 'down' },
    { label: '逾期车数', key: 'lateCarts', better: 'down' },
    { label: '清洗总分钟', key: 'totalWashMinutes', better: 'down', fmt: (v) => fmtMin(v) },
    { label: '清洗总成本', key: 'totalWashCost', better: 'down', fmt: (v) => '¥' + v },
    { label: '空档总分钟', key: 'totalIdleMinutes', better: 'down', fmt: (v) => fmtMin(v) },
    { label: '逾期总分钟', key: 'totalLateMinutes', better: 'down' }
  ].map((r) => {
    const av = num(a.plan[r.key]); const bv = num(b.plan[r.key])
    const f = r.fmt || ((x) => x)
    return { label: r.label, a: f(av), b: f(bv), delta: av - bv, deltaText: f(Math.abs(av - bv)) }
  })
  const aMap = new Map(a.assignments.filter((x) => x.startTime).map((x) => [x.cartCode, x]))
  const bMap = new Map(b.assignments.filter((x) => x.startTime).map((x) => [x.cartCode, x]))
  const cartChanges = []
  for (const [code, xa] of aMap) {
    const xb = bMap.get(code)
    if (!xb || xb.vatCode !== xa.vatCode || String(xb.startTime) !== String(xa.startTime)) {
      cartChanges.push({
        cartCode: code,
        from: xa.vatCode + ' ' + fmtTime(xa.startTime),
        to: xb ? xb.vatCode + ' ' + fmtTime(xb.startTime) : '未排',
        note: !xb ? '参考方案中未排入' : '缸位/时段变化'
      })
    }
  }
  return { a: a.plan, b: b.plan, rows, cartChanges: cartChanges.slice(0, 30) }
}

// ---------------- 清洗矩阵 ----------------
async function loadMatrix(versionId) {
  try {
    const [v, m] = await Promise.all([listWashVersions(), getWashMatrix(versionId || matrixVersionId.value)])
    washVersions.value = v.data || []
    matrixData.value = m.data
    matrixVersionId.value = m.data.version.id
  } catch (e) { ElMessage.error(e.message) }
}
const matrixRows = computed(() => {
  if (!matrixData.value) return []
  const fam = new Map((ctx.value.families || []).map((f) => [f.id, f]))
  return (matrixData.value.cells || []).map((c) => ({
    ...c,
    fromName: fam.get(c.fromFamilyId)?.familyName || ('#' + c.fromFamilyId),
    fromHex: fam.get(c.fromFamilyId)?.colorHex || '#ccc',
    toName: fam.get(c.toFamilyId)?.familyName || ('#' + c.toFamilyId),
    toHex: fam.get(c.toFamilyId)?.colorHex || '#ccc'
  }))
})
function openPublish() {
  editCells.value = (matrixData.value.cells || []).map((c) => ({ ...c }))
  publishNote.value = ''
  publishDialog.value = true
}
function addCell() {
  const first = ctx.value.families?.[0]?.id
  editCells.value.push({ fromFamilyId: first, toFamilyId: first, switchType: 'SAME', washMinutes: 15, washCost: 60, remark: '' })
}
async function savePublish() {
  try {
    await publishWashVersion({ changeNote: publishNote.value, createdBy: '工艺员', cells: editCells.value })
    ElMessage.success('新版本已发布：之后的新方案采用新口径，旧方案仍按保存时版本复算')
    publishDialog.value = false
    const r = await scheduleContext(); ctx.value = r.data
    await loadMatrix()
  } catch (e) { ElMessage.error(e.message) }
}
function familyName(id) {
  return ctx.value.families?.find((f) => f.id === id)?.familyName || ('#' + id)
}

// ---------------- 小工具 ----------------
function fmtTime(v) { return v ? String(v).replace('T', ' ').slice(5, 16) : '' }
function fmtMin(v) {
  if (v === null || v === undefined) return '—'
  const h = Math.floor(v / 60); const m = v % 60
  return h ? `${h}h${m ? m + 'm' : ''}` : `${m}m`
}
function fabricName(k) { return FABRIC[k] || k }
function machine(k) { return MACHINE[k] || k }
function colorHex(code) { return COLOR_MAP[code] || '#9e9e9e' }
function isOverdue(t) { return t && new Date(String(t).replace(' ', 'T')) < Date.now() }
function depthName(k) { return { SAME: '同色', LIGHT: '浅色', DARK: '深色', SPECIAL: '特殊' }[k] || k }
function switchName(k) { return { SAME: '同色续染', L2D: '浅转深', D2L: '深转浅', SPECIAL: '特殊切换' }[k] || k }
function switchTag(k) { return { SAME: 'success', L2D: 'primary', D2L: 'warning', SPECIAL: 'danger' }[k] || 'info' }
function resultName(k) { return { SCHEDULED: '已排入', EXCLUDED: '排除', FIXED: '固定段' }[k] || k }

onMounted(loadAll)
</script>

<style scoped>
.sch-bar { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; margin-bottom: 12px; }
.sch-bar__group { display: flex; gap: 8px; align-items: center; }
.metrics { display: flex; gap: 0; padding: 0; margin-bottom: 12px; flex-wrap: wrap; }
.metric { display: flex; flex-direction: column; gap: 4px; padding: 12px 18px; border-right: 1px solid var(--mill-line); min-width: 110px; }
.metric:last-child { border-right: none; }
.metric span { font-size: 11px; color: var(--mill-muted); }
.metric b { font-size: 18px; color: var(--mill-ink); }
.metric b.ok { color: #2e7d32; }
.metric b.bad { color: #c62828; }

.board-grid { display: grid; grid-template-columns: 340px 1fr; gap: 12px; align-items: start; }
.side { display: flex; flex-direction: column; gap: 12px; }
.side__panel { margin-bottom: 0; }
.side__body { max-height: 430px; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; padding: 10px; }
.side__body--sm { max-height: 240px; }
.main-col { display: flex; flex-direction: column; gap: 12px; }

.ccard { border: 1px solid var(--mill-line); border-left: 4px solid #b39ddb; border-radius: 8px; padding: 8px 10px; background: #fff; }
.ccard.is-rush { border-left-color: #c62828; background: #fff8f7; }
.ccard__top { display: flex; justify-content: space-between; align-items: center; }
.ccard__code { font-weight: 700; font-size: 13px; }
.ccard__color { display: flex; align-items: center; gap: 6px; font-size: 12px; margin-top: 4px; }
.ccard__color i { width: 12px; height: 12px; border-radius: 3px; border: 1px solid rgba(0,0,0,.15); }
.ccard__line { font-size: 11px; color: var(--mill-text); margin-top: 3px; display: flex; align-items: center; gap: 6px; }
.ccard__issue { font-size: 11px; color: #c62828; margin-top: 3px; }
.bad { color: #c62828; }
.empty { color: #b8aecd; font-size: 12px; text-align: center; padding: 16px 0; }

.blk { display: flex; flex-direction: column; gap: 2px; padding: 6px 8px; border-radius: 6px; font-size: 11px; margin-bottom: 6px; position: relative; }
.blk b { font-size: 12px; }
.blk span { color: var(--mill-text); }
.blk .el-button { position: absolute; right: 4px; top: 4px; }
.blk--lock { background: #efebe9; border-left: 3px solid #5d4037; }
.blk--maint { background: #eceff1; border-left: 3px solid #607d8b; }

.family-chips { display: flex; gap: 10px; flex-wrap: wrap; }
.fchip { display: flex; flex-direction: column; gap: 3px; border: 1px solid var(--mill-line); border-radius: 8px; padding: 8px 12px; min-width: 130px; }
.fchip i { width: 22px; height: 22px; border-radius: 5px; border: 1px solid rgba(0,0,0,.15); }
.dot { display: inline-block; width: 10px; height: 10px; border-radius: 2px; margin-right: 5px; border: 1px solid rgba(0,0,0,.15); }

.explain h4 { display: flex; gap: 8px; align-items: center; margin: 0 0 12px; }
.explain__row { font-size: 13px; margin: 8px 0; }
.explain__reason { font-size: 13px; line-height: 1.7; color: var(--mill-text); white-space: pre-wrap; }

.diff-line { font-size: 12px; margin-top: 6px; padding: 6px 8px; background: var(--mill-primary-wash); border-radius: 6px; }
.muted { color: var(--mill-muted); }
</style>
