<template>
  <div class="page stock-page">
    <!-- 库存总览 -->
    <div class="panel stock-summary">
      <div class="stock-summary__item"><span class="muted">库存总量</span><b>{{ fmtNum(overview.totalKg) }} kg</b></div>
      <div class="stock-summary__item"><span class="muted">染料</span><b>{{ fmtNum(overview.dyeKg) }} kg</b></div>
      <div class="stock-summary__item"><span class="muted">助剂</span><b>{{ fmtNum(overview.auxKg) }} kg</b></div>
      <div class="stock-summary__item"><span class="muted">低于安全库存</span><b class="warn">{{ overview.alertCount }}</b></div>
      <div class="stock-summary__item"><span class="muted">已过期批次</span><b class="warn">{{ overview.expiredCount }}</b></div>
      <div class="stock-summary__grow"></div>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="panel">
      <div class="panel__head">
        <el-radio-group v-model="tab" size="small">
          <el-radio-button label="ALL">全部</el-radio-button>
          <el-radio-button label="DYE">染料</el-radio-button>
          <el-radio-button label="AUX">助剂</el-radio-button>
          <el-radio-button label="alerts">库存预警</el-radio-button>
          <el-radio-button label="issues">领用记录</el-radio-button>
        </el-radio-group>
      </div>
      <div class="panel__body">
        <!-- 库存清单 -->
        <el-table v-if="tab !== 'issues'" :data="tableRows" size="small" border stripe>
          <el-table-column prop="chemCode" label="编码" width="96" />
          <el-table-column prop="chemName" label="名称" min-width="150" />
          <el-table-column label="类别" width="76">
            <template #default="{ row }">
              <span :class="['type-tag', row.category === 'DYE' ? 'type-tag--dye' : 'type-tag--aux']">
                {{ row.category === 'DYE' ? '染料' : '助剂' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="库存 / 安全库存" width="220">
            <template #default="{ row }">
              <div class="stock-bar">
                <div class="stock-bar__track">
                  <div
                    class="stock-bar__fill"
                    :style="{ width: barWidth(row) + '%', background: barColor(row) }"
                  ></div>
                </div>
                <span class="stock-bar__text">{{ fmtNum(row.stockKg) }} / {{ fmtNum(row.safetyKg) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="batchNo" label="批次" width="106" />
          <el-table-column prop="expireDate" label="效期" width="106" />
          <el-table-column label="单价" width="90">
            <template #default="{ row }">{{ fmtNum(row.unitPrice) }} 元/kg</template>
          </el-table-column>
          <el-table-column prop="supplier" label="供应商" width="110" />
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openIssue(row)">领用</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 领用记录 -->
        <el-table v-else :data="issues" size="small" border stripe>
          <el-table-column label="领用时间" width="150">
            <template #default="{ row }">{{ String(row.issueTime || '').replace('T', ' ').slice(0, 16) }}</template>
          </el-table-column>
          <el-table-column prop="chemCode" label="编码" width="96" />
          <el-table-column prop="chemName" label="名称" min-width="150" />
          <el-table-column label="领用量" width="100">
            <template #default="{ row }">{{ fmtNum(row.qtyKg) }} kg</template>
          </el-table-column>
          <el-table-column prop="operator" label="领用人" width="100" />
          <el-table-column prop="recipeCode" label="配方" width="90" />
          <el-table-column prop="cartCode" label="布车" width="90" />
        </el-table>
      </div>
    </div>

    <!-- 领用 -->
    <el-dialog v-model="issueVisible" title="染化料领用" width="420px">
      <el-form label-width="88px">
        <el-form-item label="物料">
          <b>{{ active.chemName }}</b>
          <span class="muted" style="margin-left: 8px">{{ active.chemCode }} · 现存 {{ fmtNum(active.stockKg) }}kg</span>
        </el-form-item>
        <el-form-item label="领用量">
          <el-input-number v-model="form.qtyKg" :min="0.5" :max="9999" :step="0.5" :precision="2" />
          <span class="muted" style="margin-left: 8px">kg</span>
        </el-form-item>
        <el-form-item label="领用人">
          <el-input v-model="form.operator" style="width: 180px" />
        </el-form-item>
        <el-form-item label="关联配方">
          <el-input v-model="form.recipeCode" placeholder="如 R-2401" style="width: 180px" />
        </el-form-item>
        <el-form-item label="关联布车">
          <el-input v-model="form.cartCode" placeholder="如 C-2401" style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <span class="muted">领用量不得超过当前库存；过期批次禁止领用</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueVisible = false">取消</el-button>
        <el-button type="primary" @click="submitIssue">确认领用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { issueStock, listIssues, listStock, stockAlerts, stockOverview } from '../api'

const props = defineProps({ params: { type: Object, default: () => ({}) } })

const tab = ref('ALL')
const stocks = ref([])
const alerts = ref([])
const issues = ref([])
const overview = ref({})
const issueVisible = ref(false)
const active = ref({})
const form = ref({ qtyKg: 10, operator: '王建国', recipeCode: '', cartCode: '' })

const tableRows = computed(() => {
  if (tab.value === 'alerts') return alerts.value
  if (tab.value === 'ALL') return stocks.value
  return stocks.value.filter((s) => s.category === tab.value)
})

function fmtNum(v) {
  return v === null || v === undefined ? '--' : Number(v).toFixed(2)
}
function ratio(row) {
  const safety = Number(row.safetyKg) || 1
  return Number(row.stockKg || 0) / safety
}
function barWidth(row) {
  return Math.min(100, Math.max(4, ratio(row) * 100 / 2))
}
function barColor(row) {
  const r = ratio(row)
  if (r <= 1) return '#c62828'
  if (r <= 1.5) return '#ef6c00'
  return '#4a148c'
}

function openIssue(row) {
  active.value = row
  form.value = { qtyKg: 10, operator: '王建国', recipeCode: '', cartCode: '' }
  issueVisible.value = true
}

async function load() {
  try {
    const [s, a, o, i] = await Promise.all([listStock(), stockAlerts(), stockOverview(), listIssues()])
    stocks.value = s.data || []
    alerts.value = a.data || []
    overview.value = o.data || {}
    issues.value = i.data || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function submitIssue() {
  try {
    const res = await issueStock({
      stockId: active.value.id,
      qtyKg: form.value.qtyKg,
      operator: form.value.operator,
      recipeCode: form.value.recipeCode || null,
      cartCode: form.value.cartCode || null
    })
    ElMessage.success(res.message || '领用已登记')
    issueVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

watch(() => props.params.tab, (v) => { if (v) tab.value = v }, { immediate: true })
load()
</script>

<style scoped>
.stock-page { background: var(--mill-bg); }

.stock-summary {
  display: flex; align-items: center; gap: 30px;
  padding: 12px 16px; margin-bottom: 14px;
}
.stock-summary__item { display: flex; flex-direction: column; gap: 3px; }
.stock-summary__item b { font-size: 19px; color: var(--mill-ink); }
.stock-summary__item .warn { color: #c62828; }
.stock-summary__grow { flex: 1 1 auto; }

.stock-bar { display: flex; align-items: center; gap: 8px; }
.stock-bar__track { flex: 1 1 auto; height: 8px; background: #efe9f7; border-radius: 4px; overflow: hidden; }
.stock-bar__fill { height: 100%; border-radius: 4px; }
.stock-bar__text { font-size: 11px; color: var(--mill-muted); white-space: nowrap; }

.type-tag { font-size: 11px; padding: 1px 6px; border-radius: 9px; color: #fff; }
.type-tag--dye { background: var(--mill-primary); }
.type-tag--aux { background: #8d7fa6; }
</style>
