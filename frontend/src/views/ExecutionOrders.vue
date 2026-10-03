<template>
  <div class="page execution-page">
    <div class="panel exec-summary">
      <div class="exec-summary__item">
        <span class="muted">待确认</span>
        <b class="pending">{{ statusCount.PENDING_CONFIRM || 0 }}</b>
      </div>
      <div class="exec-summary__item">
        <span class="muted">已生效</span>
        <b class="issued">{{ statusCount.ISSUED || 0 }}</b>
      </div>
      <div class="exec-summary__item">
        <span class="muted">已失效</span>
        <b class="invalid">{{ statusCount.INVALID || 0 }}</b>
      </div>
      <div class="exec-summary__grow"></div>
      <el-button size="small" @click="load">刷新状态</el-button>
      <el-button size="small" type="primary" @click="openCreate">新增开缸执行单</el-button>
    </div>

    <div class="panel">
      <div class="panel__head">
        <span class="panel__title">开缸依据（布车 / 染缸 / 配方版本 / 工艺曲线）</span>
        <span class="muted">待确认单每次刷新按最新资料复核；失效单不能下达</span>
      </div>
      <el-table :data="orders" size="small" border stripe @row-click="openDetail">
        <el-table-column prop="orderCode" label="执行单" width="150" fixed />
        <el-table-column label="状态" width="106" fixed>
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" effect="dark" size="small">{{ statusName(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="布车 → 染缸" width="160">
          <template #default="{ row }">
            <b>{{ row.cartCode }}</b>
            <span class="arrow">→</span>{{ row.vatCode }}
          </template>
        </el-table-column>
        <el-table-column label="配方版本" width="150">
          <template #default="{ row }">{{ row.recipeCode }} · V{{ row.recipeVersionNo }}</template>
        </el-table-column>
        <el-table-column prop="curveCode" label="工艺曲线" width="110" />
        <el-table-column label="预计时长" width="100">
          <template #default="{ row }">{{ row.estimatedMinutes }}′</template>
        </el-table-column>
        <el-table-column label="关键温度" width="120">
          <template #default="{ row }">{{ fmt(row.keyTemp) }}℃ / 保温 {{ row.holdMinutes }}′</template>
        </el-table-column>
        <el-table-column label="机台上限 / 布重" width="150">
          <template #default="{ row }">{{ fmt(row.machineMaxTemp) }}℃ · {{ fmt(row.estimatedWeightKg, 2) }}kg</template>
        </el-table-column>
        <el-table-column prop="confirmedBy" label="下达人" width="90" />
        <el-table-column prop="invalidReason" label="失效原因" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="178" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openDetail(row)">查看</el-button>
            <el-button
              v-if="row.status === 'PENDING_CONFIRM'"
              link
              type="success"
              size="small"
              @click.stop="openIssue(row)"
            >下达</el-button>
            <el-tag v-if="row.status === 'ISSUED'" type="success" size="small">快照已锁定</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增：选择布车后自动匹配最新已发布配方和曲线，并展示所有差异 -->
    <el-dialog v-model="createVisible" title="新增开缸执行单" width="900px" top="5vh">
      <el-form label-width="92px">
        <el-form-item label="布车">
          <el-select
            v-model="createForm.cartId"
            filterable
            placeholder="仅可选择已排入染缸的布车"
            style="width: 100%"
            @change="runPreview"
          >
            <el-option
              v-for="cart in assignedCarts"
              :key="cart.id"
              :value="cart.id"
              :label="`${cart.cartCode} · ${fabricName(cart.fabricType)} · ${cart.colorName}(${cart.colorCode}) · ${cart.vatCode}`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="配方 / 曲线">
          <span class="muted">默认自动匹配该布种、色号的最新已发布配方及其绑定曲线；如需核对可手工切换。</span>
        </el-form-item>
      </el-form>

      <div v-loading="previewLoading" class="preview">
        <el-empty v-if="!createForm.cartId" description="请先选择已排入染缸的布车" :image-size="70" />
        <template v-else>
          <el-alert
            :title="previewData.valid ? '全部条件满足，可以生成待确认执行单' : '存在具体差异，不能生成看似可用的执行单'"
            :type="previewData.valid ? 'success' : 'error'"
            :closable="false"
            show-icon
          />
          <el-alert
            v-for="(diff, i) in previewData.differences"
            :key="i"
            :title="diff"
            type="error"
            :closable="false"
            show-icon
            class="preview__diff"
          />

          <div class="preview-grid">
            <div class="source-card">
              <h4>布车与染缸</h4>
              <template v-if="previewData.cart">
                <p><b>{{ previewData.cart.cartCode }}</b> → <b>{{ previewData.vat?.vatCode }}</b></p>
                <p>{{ fabricName(previewData.cart.fabricType) }} · {{ previewData.cart.colorName }}（{{ previewData.cart.colorCode }}）</p>
                <p>米数 {{ fmt(previewData.cart.meters) }}m · 折算 {{ fmt(previewData.estimatedWeightKg, 2) }}kg</p>
                <p>染缸容量 {{ fmt(previewData.vat?.capacityKg, 2) }}kg · 上限 {{ fmt(previewData.machineMaxTemp) }}℃</p>
              </template>
              <p v-else class="danger">布车或排缸资料不可用</p>
            </div>

            <div class="source-card">
              <h4>已发布配方</h4>
              <template v-if="previewData.recipe">
                <p><b>{{ previewData.recipe.recipeCode }}</b> · V{{ previewData.recipe.versionNo }}</p>
                <p>{{ previewData.recipe.colorName }} · {{ fabricName(previewData.recipe.fabricType) }}</p>
                <p>OWF {{ fmt(previewData.recipe.totalOwf, 3) }}% · 成本 {{ fmt(previewData.recipe.costPerKg, 2) }} 元/kg</p>
                <p v-if="previewData.recipe.items?.length">明细 {{ previewData.recipe.items.length }} 行</p>
              </template>
              <p v-else class="danger">无匹配的已发布配方</p>
            </div>

            <div class="source-card">
              <h4>曲线与关键参数</h4>
              <template v-if="previewData.curve">
                <p><b>{{ previewData.curve.curveCode }}</b> {{ previewData.curve.curveName }}</p>
                <p>预计总时长 <b>{{ previewData.estimatedMinutes }}′</b></p>
                <p>最高保温 <b>{{ fmt(previewData.keyTemp) }}℃</b>，累计保温 {{ previewData.holdMinutes }}′</p>
                <p>节点数 {{ previewData.curve.nodes?.length || 0 }}</p>
              </template>
              <p v-else class="danger">无匹配工艺曲线</p>
            </div>
          </div>

          <el-table v-if="previewData.curve?.nodes?.length" :data="previewData.curve.nodes" size="small" border class="node-table">
            <el-table-column prop="seqNo" label="#" width="54" />
            <el-table-column label="节点" width="90">
              <template #default="{ row }"><el-tag size="small" :type="stageTag(row.stage)">{{ stageName(row.stage) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="目标温度" width="100"><template #default="{ row }">{{ fmt(row.targetTemp) }}℃</template></el-table-column>
            <el-table-column label="时长" width="80"><template #default="{ row }">{{ row.durationMin }}′</template></el-table-column>
            <el-table-column label="速率" width="100"><template #default="{ row }">{{ fmt(row.rampRate, 2) }}℃/min</template></el-table-column>
            <el-table-column prop="memo" label="节点说明" min-width="180" />
          </el-table>
        </template>
      </div>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!previewData.valid" :loading="createLoading" @click="submitCreate">
          生成待确认执行单
        </el-button>
      </template>
    </el-dialog>

    <!-- 下达 -->
    <el-dialog v-model="issueVisible" title="确认下达开缸执行单" width="520px">
      <el-alert
        title="下达后会保存完整工艺快照，并把目标温度与保温时长推进染缸；后续来源资料编辑不影响本单。"
        type="warning"
        :closable="false"
        show-icon
      />
      <div class="issue-box" v-if="activeOrder.id">
        <div><span>执行单</span><b>{{ activeOrder.orderCode }}</b></div>
        <div><span>布车 / 染缸</span><b>{{ activeOrder.cartCode }} → {{ activeOrder.vatCode }}</b></div>
        <div><span>目标温度</span><b>{{ fmt(activeOrder.keyTemp) }}℃</b></div>
        <div><span>保温时长</span><b>{{ activeOrder.holdMinutes }} 分钟</b></div>
      </div>
      <el-input v-model="issueForm.confirmedBy" placeholder="请输入下达人员姓名" maxlength="32" show-word-limit />
      <template #footer>
        <el-button @click="issueVisible = false">取消</el-button>
        <el-button type="success" :loading="issueLoading" @click="submitIssue">确认下达</el-button>
      </template>
    </el-dialog>

    <!-- 详情 / 快照 -->
    <el-drawer v-model="detailVisible" title="开缸执行单详情" size="62%">
      <template v-if="activeOrder.id">
        <div class="detail-head">
          <div>
            <h3>{{ activeOrder.orderCode }}</h3>
            <el-tag :type="statusTag(activeOrder.status)" effect="dark">{{ statusName(activeOrder.status) }}</el-tag>
          </div>
          <el-button v-if="activeOrder.status === 'PENDING_CONFIRM'" type="success" @click="openIssue(activeOrder)">下达此单</el-button>
        </div>

        <el-alert v-if="activeOrder.status === 'INVALID'" :title="activeOrder.invalidReason" type="error" :closable="false" show-icon />
        <el-alert v-if="activeOrder.status === 'ISSUED'" title="该单已锁定下达时工艺快照，后续配方、曲线或排缸编辑不会改写已执行内容。" type="success" :closable="false" show-icon />

        <el-descriptions :column="2" border size="small" class="detail-desc">
          <el-descriptions-item label="布车">{{ activeOrder.cartCode }}</el-descriptions-item>
          <el-descriptions-item label="染缸">{{ activeOrder.vatCode }}</el-descriptions-item>
          <el-descriptions-item label="配方">{{ activeOrder.recipeCode }} V{{ activeOrder.recipeVersionNo }}</el-descriptions-item>
          <el-descriptions-item label="曲线">{{ activeOrder.curveCode }}</el-descriptions-item>
          <el-descriptions-item label="预计时长">{{ activeOrder.estimatedMinutes }} 分钟</el-descriptions-item>
          <el-descriptions-item label="关键温度">{{ fmt(activeOrder.keyTemp) }}℃</el-descriptions-item>
          <el-descriptions-item label="保温时长">{{ activeOrder.holdMinutes }} 分钟</el-descriptions-item>
          <el-descriptions-item label="机台上限">{{ fmt(activeOrder.machineMaxTemp) }}℃</el-descriptions-item>
        </el-descriptions>

        <template v-if="snapshot">
          <el-tabs>
            <el-tab-pane label="曲线节点">
              <el-table :data="snapshot.curve?.nodes || []" size="small" border>
                <el-table-column prop="seqNo" label="#" width="54" />
                <el-table-column label="节点" width="90"><template #default="{ row }">{{ stageName(row.stage) }}</template></el-table-column>
                <el-table-column label="温度" width="90"><template #default="{ row }">{{ fmt(row.targetTemp) }}℃</template></el-table-column>
                <el-table-column label="时长" width="80"><template #default="{ row }">{{ row.durationMin }}′</template></el-table-column>
                <el-table-column label="速率" width="110"><template #default="{ row }">{{ fmt(row.rampRate, 2) }}℃/min</template></el-table-column>
                <el-table-column prop="memo" label="说明" min-width="180" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="配方快照">
              <el-table :data="snapshot.recipe?.items || []" size="small" border>
                <el-table-column prop="seqNo" label="#" width="54" />
                <el-table-column label="类型" width="80"><template #default="{ row }">{{ row.itemType === 'DYE' ? '染料' : '助剂' }}</template></el-table-column>
                <el-table-column prop="dyeName" label="名称" min-width="170" />
                <el-table-column label="OWF" width="110"><template #default="{ row }">{{ fmt(row.owfPercent, 3) }}%</template></el-table-column>
                <el-table-column label="单价" width="100"><template #default="{ row }">{{ fmt(row.unitPrice, 2) }}</template></el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>
        <el-alert v-else title="待确认阶段引用最新资料，不保存可执行快照；下达成功后此处才可追溯。" type="info" :closable="false" show-icon />
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createExecutionOrder,
  getExecutionOrder,
  issueExecutionOrder,
  listCarts,
  listExecutionOrders,
  previewExecutionOrder
} from '../api'

const FABRIC = { COTTON: '全棉', POLY: '涤纶', NYLON: '锦纶', BLEND: '涤棉', VISCOSE: '人棉', SILK: '真丝' }

const orders = ref([])
const carts = ref([])
const createVisible = ref(false)
const issueVisible = ref(false)
const detailVisible = ref(false)
const createLoading = ref(false)
const previewLoading = ref(false)
const issueLoading = ref(false)
const createForm = ref({ cartId: null })
const issueForm = ref({ confirmedBy: '' })
const activeOrder = ref({})
const previewData = ref(emptyPreview())

const assignedCarts = computed(() => carts.value.filter((c) => c.vatId && c.status === 'DYEING'))
const statusCount = computed(() => orders.value.reduce((map, o) => {
  map[o.status] = (map[o.status] || 0) + 1
  return map
}, {}))
const snapshot = computed(() => {
  if (!activeOrder.value.snapshotJson) return null
  try {
    return JSON.parse(activeOrder.value.snapshotJson)
  } catch {
    return null
  }
})

function emptyPreview() {
  return { valid: false, differences: [], cart: null, vat: null, recipe: null, curve: null }
}
function fmt(v, scale = 1) {
  return v === null || v === undefined ? '--' : Number(v).toFixed(scale)
}
function fabricName(v) { return FABRIC[v] || v || '--' }
function statusName(s) {
  return { PENDING_CONFIRM: '待确认', ISSUED: '已下达', INVALID: '已失效' }[s] || s
}
function statusTag(s) {
  return { PENDING_CONFIRM: 'warning', ISSUED: 'success', INVALID: 'danger' }[s] || 'info'
}
function stageName(s) {
  return { RAMP: '升温', HOLD: '保温', COOL: '降温' }[s] || s
}
function stageTag(s) {
  return { RAMP: 'warning', HOLD: 'success', COOL: 'primary' }[s] || 'info'
}

async function load() {
  try {
    const [orderRes, cartRes] = await Promise.all([listExecutionOrders(), listCarts()])
    orders.value = orderRes.data || []
    carts.value = cartRes.data || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openCreate() {
  createForm.value = { cartId: null }
  previewData.value = emptyPreview()
  createVisible.value = true
}

async function runPreview(cartId) {
  if (!cartId) {
    previewData.value = emptyPreview()
    return
  }
  previewLoading.value = true
  try {
    const res = await previewExecutionOrder({ cartId })
    previewData.value = res.data || emptyPreview()
  } catch (e) {
    previewData.value = { ...emptyPreview(), differences: [e.message] }
  } finally {
    previewLoading.value = false
  }
}

async function submitCreate() {
  createLoading.value = true
  try {
    const res = await createExecutionOrder({ cartId: createForm.value.cartId })
    ElMessage.success(res.message || '待确认执行单已创建')
    createVisible.value = false
    await load()
    openDetail(res.data)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    createLoading.value = false
  }
}

function openIssue(order) {
  activeOrder.value = order
  issueForm.value = { confirmedBy: '' }
  issueVisible.value = true
}

async function submitIssue() {
  if (!issueForm.value.confirmedBy.trim()) {
    ElMessage.warning('请输入下达人员')
    return
  }
  issueLoading.value = true
  try {
    const res = await issueExecutionOrder(activeOrder.value.id, { confirmedBy: issueForm.value.confirmedBy.trim() })
    ElMessage.success(res.message || '已下达')
    issueVisible.value = false
    detailVisible.value = false
    await load()
    ElMessage.info('如另一页面正在操作同一布车，请刷新查看已生效执行单')
  } catch (e) {
    ElMessage.error(e.message)
    await load()
  } finally {
    issueLoading.value = false
  }
}

async function openDetail(row) {
  activeOrder.value = row || {}
  detailVisible.value = true
  try {
    const [detailRes, listRes] = await Promise.all([
      getExecutionOrder(row.id),
      listExecutionOrders()
    ])
    activeOrder.value = detailRes.data
    orders.value = listRes.data || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

load()
</script>

<style scoped>
.exec-summary { display: flex; align-items: center; gap: 30px; padding: 12px 16px; margin-bottom: 14px; }
.exec-summary__item { display: flex; flex-direction: column; gap: 3px; }
.exec-summary__item b { font-size: 22px; }
.exec-summary__item .pending { color: #ef6c00; }
.exec-summary__item .issued { color: #2e7d32; }
.exec-summary__item .invalid { color: #c62828; }
.exec-summary__grow { flex: 1 1 auto; }
.arrow { margin: 0 8px; color: var(--mill-muted); }

.preview { margin-top: 14px; min-height: 220px; }
.preview__diff { margin-top: 8px; }
.preview-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 14px 0; }
.source-card { border: 1px solid var(--mill-line); border-radius: 10px; padding: 12px; background: #fcfaff; }
.source-card h4 { margin: 0 0 10px; color: var(--mill-primary); font-size: 14px; }
.source-card p { margin: 7px 0; font-size: 12px; line-height: 1.45; }
.source-card .danger { color: #c62828; font-weight: 700; }
.node-table { margin-top: 10px; }

.issue-box { margin: 14px 0; border: 1px solid var(--mill-line); border-radius: 8px; overflow: hidden; }
.issue-box div { display: flex; justify-content: space-between; padding: 9px 12px; border-bottom: 1px solid var(--mill-line); font-size: 13px; }
.issue-box div:last-child { border-bottom: 0; }
.issue-box span { color: var(--mill-muted); }

.detail-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.detail-head h3 { margin: 0 0 8px; }
.detail-desc { margin-top: 14px; }
:deep(.el-table) { cursor: pointer; }
:deep(.el-table .el-button) { cursor: pointer; }
</style>
