<template>
  <div class="page recipe-page">
    <!-- 统计视图（九宫格「色号缸次」入口） -->
    <div v-if="params.view === 'stat'" class="stat-wrap">
      <div class="panel">
        <div class="panel__head"><span class="panel__title">按色号统计缸次</span></div>
        <div class="panel__body">
          <el-table :data="colorRows" size="small" border stripe>
            <el-table-column prop="colorCode" label="色号" width="110" />
            <el-table-column prop="colorName" label="色名" width="110" />
            <el-table-column prop="batchCount" label="缸次" width="90" />
            <el-table-column label="总米数">
              <template #default="{ row }">{{ fmtNum(row.totalMeters) }} m</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <div class="panel">
        <div class="panel__head"><span class="panel__title">按客户统计配方</span></div>
        <div class="panel__body">
          <el-table :data="customerRows" size="small" border stripe>
            <el-table-column prop="customer" label="客户" width="140" />
            <el-table-column prop="recipeCount" label="配方数" width="100" />
            <el-table-column label="平均成本">
              <template #default="{ row }">{{ fmtNum(row.avgCostPerKg) }} 元/kg</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <!-- 正常视图：色卡条 + 阶梯表 -->
    <div v-else class="lab">
      <!-- 左侧配方清单 -->
      <div class="panel lab__list">
        <div class="panel__head"><span class="panel__title">配方清单</span></div>
        <div class="panel__body lab__list-body">
          <div
            v-for="r in recipes"
            :key="r.id"
            :class="['recipe-chip', current && current.id === r.id ? 'is-active' : '']"
            @click="select(r)"
          >
            <span class="recipe-chip__swatch" :style="{ background: r.colorHex }"></span>
            <span class="recipe-chip__text">
              <b>{{ r.colorName }} · {{ r.colorCode }}</b>
              <i>{{ r.recipeCode }} V{{ r.versionNo }} · {{ r.customer }}</i>
            </span>
          </div>
        </div>
      </div>

      <!-- 右侧色卡 + 阶梯表 -->
      <div class="lab__main" v-if="current">
        <div class="panel">
          <div class="panel__head">
            <span class="panel__title">目标色卡 · {{ current.recipeCode }}</span>
            <span class="muted">{{ current.customer }} · {{ current.fabricType }} · 版本 V{{ current.versionNo }}</span>
          </div>
          <div class="panel__body colorbar">
            <div class="colorbar__block" :style="{ background: current.colorHex }">
              <span class="colorbar__code">{{ current.colorCode }}</span>
            </div>
            <div class="colorbar__info">
              <div class="colorbar__name">{{ current.colorName }} <span class="muted">{{ current.colorHex }}</span></div>
              <div class="colorbar__lab">
                <span>L* {{ fmtNum(current.labL) }}</span>
                <span>a* {{ fmtNum(current.labA) }}</span>
                <span>b* {{ fmtNum(current.labB) }}</span>
              </div>
              <div class="colorbar__lab muted">
                <span>状态 {{ current.status }}</span>
                <span>登记成本 {{ fmtNum(current.costPerKg) }} 元/kg</span>
              </div>
            </div>
            <div class="colorbar__ops">
              <el-button type="primary" size="small" @click="save">保存配方</el-button>
              <el-button size="small" @click="clone">升版</el-button>
              <el-button size="small" type="danger" plain @click="remove">删除</el-button>
            </div>
          </div>
        </div>

        <div class="panel lab__table-panel">
          <div class="panel__head">
            <span class="panel__title">染料配方阶梯表（行内编辑 owf）</span>
            <span class="muted">染料 owf 合计必须为 100%，助剂单独计</span>
          </div>
          <div class="panel__body">
            <table class="ladder">
              <thead>
                <tr>
                  <th style="width: 44px">序</th>
                  <th style="width: 62px">类别</th>
                  <th style="width: 170px">染料 / 助剂</th>
                  <th style="width: 190px">色阶（浅 → 深）</th>
                  <th style="width: 130px">owf %</th>
                  <th style="width: 110px">单价 元/kg</th>
                  <th style="width: 110px">成本 元/kg</th>
                  <th style="width: 60px"></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, i) in rows" :key="i" :class="row.itemType === 'AUX' ? 'is-aux' : ''">
                  <td>{{ i + 1 }}</td>
                  <td>
                    <span :class="['type-tag', row.itemType === 'DYE' ? 'type-tag--dye' : 'type-tag--aux']">
                      {{ row.itemType === 'DYE' ? '染料' : '助剂' }}
                    </span>
                  </td>
                  <td>
                    <input v-model="row.dyeName" class="cell-input" />
                  </td>
                  <td>
                    <span class="shade" :style="{ background: shadeOf(row) }"></span>
                  </td>
                  <td>
                    <el-input-number
                      v-model="row.owfPercent"
                      size="small" :min="0" :max="100" :step="0.5" :precision="3"
                      controls-position="right" style="width: 118px"
                    />
                  </td>
                  <td>
                    <el-input-number
                      v-model="row.unitPrice"
                      size="small" :min="0" :max="9999" :step="1" :precision="2"
                      controls-position="right" style="width: 100px"
                    />
                  </td>
                  <td class="num">{{ fmtNum(row.owfPercent / 100 * row.unitPrice) }}</td>
                  <td>
                    <el-button link type="danger" size="small" @click="rows.splice(i, 1)">删</el-button>
                  </td>
                </tr>
              </tbody>
              <tfoot>
                <tr :class="dyeOk ? 'sum-ok' : 'sum-bad'">
                  <td colspan="4" class="sum-label">染料 owf 合计（须 100%）</td>
                  <td class="num">{{ dyeSum.toFixed(3) }} %</td>
                  <td colspan="3" class="sum-tip">
                    <span v-if="dyeOk">校验通过</span>
                    <span v-else>
                      {{ dyeGap > 0 ? '还差' : '超出' }} {{ Math.abs(dyeGap).toFixed(3) }}% 才到 100%
                    </span>
                  </td>
                </tr>
                <tr>
                  <td colspan="4" class="sum-label">助剂合计（不计入 100%）</td>
                  <td class="num">{{ auxSum.toFixed(3) }} %</td>
                  <td colspan="3" class="sum-tip muted">染化料成本 {{ costPerKg.toFixed(2) }} 元/kg</td>
                </tr>
              </tfoot>
            </table>

            <div class="ladder__ops">
              <el-button size="small" @click="addRow('DYE')">+ 染料行</el-button>
              <el-button size="small" @click="addRow('AUX')">+ 助剂行</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cloneRecipe, colorStats, deleteRecipe, listRecipes, recipeCustomerStats, saveRecipe } from '../api'

const props = defineProps({ params: { type: Object, default: () => ({}) } })

const recipes = ref([])
const current = ref(null)
const rows = ref([])
const colorRows = ref([])
const customerRows = ref([])

const dyeSum = computed(() =>
  rows.value.filter((r) => r.itemType === 'DYE').reduce((s, r) => s + (Number(r.owfPercent) || 0), 0)
)
const auxSum = computed(() =>
  rows.value.filter((r) => r.itemType === 'AUX').reduce((s, r) => s + (Number(r.owfPercent) || 0), 0)
)
const dyeGap = computed(() => 100 - dyeSum.value)
const dyeOk = computed(() => Math.abs(dyeGap.value) < 0.005)
const costPerKg = computed(() =>
  rows.value.reduce((s, r) => s + (Number(r.owfPercent) || 0) / 100 * (Number(r.unitPrice) || 0), 0)
)

function fmtNum(v) {
  return v === null || v === undefined ? '--' : Number(v).toFixed(2)
}

function mix(hex, target, amount) {
  const base = String(hex || '#cccccc').replace('#', '')
  const full = base.length === 3 ? base.split('').map((c) => c + c).join('') : base
  const r = parseInt(full.slice(0, 2), 16)
  const g = parseInt(full.slice(2, 4), 16)
  const b = parseInt(full.slice(4, 6), 16)
  const t = target === 'w' ? 255 : 0
  const m = (c) => Math.round(c + (t - c) * amount)
  return `rgb(${m(r)}, ${m(g)}, ${m(b)})`
}

/** 每行一条由浅到深的色阶小条 */
function shadeOf(row) {
  const base = row.colorHex || '#9e9e9e'
  return `linear-gradient(90deg, ${mix(base, 'w', 0.72)} 0%, ${base} 52%, ${mix(base, 'b', 0.45)} 100%)`
}

function addRow(type) {
  rows.value.push({
    itemType: type,
    dyeName: type === 'DYE' ? '新增染料' : '新增助剂',
    owfPercent: 0,
    unitPrice: type === 'DYE' ? 100 : 20,
    colorHex: type === 'DYE' ? '#7b1fa2' : '#b0a8c0'
  })
}

function select(recipe) {
  current.value = recipe
  rows.value = (recipe.items || []).map((it) => ({
    id: it.id,
    itemType: it.itemType,
    dyeName: it.dyeName,
    owfPercent: Number(it.owfPercent),
    unitPrice: Number(it.unitPrice),
    colorHex: it.colorHex
  }))
}

async function load() {
  try {
    const res = await listRecipes()
    recipes.value = res.data || []
    if (recipes.value.length && !current.value) {
      select(recipes.value[0])
    }
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function save() {
  if (!dyeOk.value) {
    ElMessage.error(`染料 owf 合计 ${dyeSum.value.toFixed(3)}%，${dyeGap.value > 0 ? '还差' : '超出'} ${Math.abs(dyeGap.value).toFixed(3)}% 才到 100%`)
    return
  }
  try {
    const payload = {
      recipeCode: current.value.recipeCode,
      colorCode: current.value.colorCode,
      colorName: current.value.colorName,
      customer: current.value.customer,
      fabricType: current.value.fabricType,
      versionNo: current.value.versionNo,
      colorHex: current.value.colorHex,
      labL: current.value.labL,
      labA: current.value.labA,
      labB: current.value.labB,
      status: current.value.status,
      items: rows.value
    }
    const res = await saveRecipe(current.value.id, payload)
    ElMessage.success(res.message || '配方已保存')
    current.value = res.data
    await load()
    const again = recipes.value.find((r) => r.id === res.data.id)
    if (again) select(again)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function clone() {
  try {
    const res = await cloneRecipe(current.value.id)
    ElMessage.success(res.message || '已生成新版本')
    await load()
    select(res.data)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function remove() {
  try {
    await ElMessageBox.confirm(`确认删除配方 ${current.value.recipeCode}？`, '删除配方', { type: 'warning' })
    await deleteRecipe(current.value.id)
    current.value = null
    rows.value = []
    await load()
  } catch (e) {
    if (e !== 'cancel' && e.message) ElMessage.error(e.message)
  }
}

async function loadStats() {
  try {
    const [c, k] = await Promise.all([colorStats(), recipeCustomerStats()])
    colorRows.value = c.data || []
    customerRows.value = k.data || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

watch(() => props.params.view, (v) => { if (v === 'stat') loadStats() }, { immediate: true })
load()
</script>

<style scoped>
.recipe-page { background: var(--mill-bg); }

.lab { display: flex; gap: 14px; align-items: flex-start; }
.lab__list { width: 250px; flex: 0 0 250px; }
.lab__list-body { padding: 8px; max-height: calc(100vh - 160px); overflow: auto; }
.lab__main { flex: 1 1 auto; min-width: 0; display: flex; flex-direction: column; gap: 14px; }

.recipe-chip {
  display: flex; align-items: center; gap: 10px;
  padding: 8px; border-radius: 8px; cursor: pointer;
  border: 1px solid transparent;
}
.recipe-chip:hover { background: var(--mill-primary-wash); }
.recipe-chip.is-active { border-color: var(--mill-primary); background: var(--mill-primary-wash); }
.recipe-chip__swatch { width: 30px; height: 30px; border-radius: 6px; flex: 0 0 30px; border: 1px solid rgba(0,0,0,0.08); }
.recipe-chip__text { display: flex; flex-direction: column; min-width: 0; }
.recipe-chip__text b { font-size: 13px; color: var(--mill-ink); }
.recipe-chip__text i { font-size: 11px; font-style: normal; color: var(--mill-muted); }

.colorbar { display: flex; align-items: center; gap: 16px; }
.colorbar__block {
  width: 132px; height: 78px; border-radius: 10px;
  display: flex; align-items: flex-end; justify-content: flex-end; padding: 6px 8px;
  border: 1px solid rgba(0,0,0,0.12);
  box-shadow: inset 0 -18px 30px rgba(0,0,0,0.18);
}
.colorbar__code { color: #fff; font-size: 12px; font-weight: 700; text-shadow: 0 1px 3px rgba(0,0,0,0.5); }
.colorbar__info { flex: 1 1 auto; }
.colorbar__name { font-size: 17px; font-weight: 700; color: var(--mill-ink); }
.colorbar__lab { display: flex; gap: 16px; font-size: 12px; margin-top: 6px; }
.colorbar__ops { display: flex; flex-direction: column; gap: 6px; }

.ladder { width: 100%; border-collapse: collapse; font-size: 13px; }
.ladder th {
  background: var(--mill-primary-wash);
  color: var(--mill-ink);
  text-align: left;
  padding: 8px 8px;
  font-weight: 600;
  border-bottom: 1px solid var(--mill-line);
}
.ladder td { padding: 6px 8px; border-bottom: 1px solid #f1edf8; vertical-align: middle; }
.ladder tr.is-aux td { background: #fbfaff; }
.ladder .num { text-align: right; font-variant-numeric: tabular-nums; }

.cell-input {
  width: 100%; border: 1px solid transparent; background: transparent;
  padding: 4px 6px; border-radius: 4px; font-size: 13px; color: var(--mill-text);
}
.cell-input:hover { border-color: var(--mill-line); }
.cell-input:focus { outline: none; border-color: var(--mill-primary); background: #fff; }

.shade { display: block; width: 100%; height: 14px; border-radius: 7px; border: 1px solid rgba(0,0,0,0.08); }

.type-tag { font-size: 11px; padding: 1px 6px; border-radius: 9px; color: #fff; }
.type-tag--dye { background: var(--mill-primary); }
.type-tag--aux { background: #8d7fa6; }

.ladder tfoot td { padding: 9px 8px; font-weight: 700; }
.sum-ok td { background: #eef7ef; color: #2e7d32; }
.sum-bad td { background: #fdecea; color: #c62828; }
.sum-label { text-align: right; }
.sum-tip { text-align: left; }

.ladder__ops { margin-top: 12px; display: flex; gap: 8px; }

.stat-wrap { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
</style>
