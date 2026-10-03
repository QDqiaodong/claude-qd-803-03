<template>
  <div class="page cart-page">
    <!-- 概览 -->
    <div class="panel cart-summary">
      <div class="cart-summary__item">
        <span class="muted">在库布车</span>
        <b>{{ allCount }}</b>
      </div>
      <div class="cart-summary__item">
        <span class="muted">总米数</span>
        <b>{{ fmtNum(totalMeters) }} m</b>
      </div>
      <div class="cart-summary__item">
        <span class="muted">返修车</span>
        <b class="warn">{{ reworkCount }}</b>
      </div>
      <div class="cart-summary__grow"></div>
      <el-button size="small" @click="load">刷新看板</el-button>
    </div>

    <!-- 三列看板 -->
    <div class="board">
      <div
        v-for="col in COLUMNS"
        :key="col.key"
        :class="['col', hoverCol === col.key ? 'is-over' : '']"
        @dragover.prevent="hoverCol = col.key"
        @dragleave="hoverCol = null"
        @drop="onDrop(col.key, $event)"
      >
        <div class="col__head" :style="{ borderTopColor: col.color }">
          <span class="col__title">{{ col.name }}</span>
          <span class="col__count">{{ (columns[col.key] || []).length }}</span>
        </div>
        <div class="col__body">
          <div
            v-for="cart in columns[col.key] || []"
            :key="cart.id"
            class="card"
            draggable="true"
            @dragstart="onDragStart(cart, $event)"
            @dragend="hoverCol = null"
          >
            <div class="card__top">
              <span class="card__code">{{ cart.cartCode }}</span>
              <span v-if="cart.status === 'REWORK'" class="card__badge card__badge--rework">返修 ×{{ cart.reworkTimes }}</span>
              <span v-if="cart.status === 'DYEING'" class="card__badge card__badge--dyeing">{{ cart.vatCode }}</span>
            </div>
            <div class="card__color">
              <i :style="{ background: colorOf(cart.colorCode) }"></i>
              <span>{{ cart.colorName }} · {{ cart.colorCode }}</span>
            </div>
            <div class="card__line">{{ fabricName(cart.fabricType) }} · {{ fmtNum(cart.meters) }} m</div>
            <div class="card__line muted">{{ cart.customer }}</div>
            <div v-if="cart.dyeStartTime" class="card__time muted">
              {{ fmtTime(cart.dyeStartTime) }} → {{ fmtTime(cart.dyeEndTime) }}
            </div>
          </div>
          <div v-if="!(columns[col.key] || []).length" class="col__empty">把布车拖到这里</div>
        </div>
      </div>
    </div>

    <!-- 客户米数汇总 -->
    <div class="panel cart-stat">
      <div class="panel__head"><span class="panel__title">按客户汇总米数</span></div>
      <div class="panel__body">
        <el-table :data="customerRows" size="small" border stripe>
          <el-table-column prop="customer" label="客户" width="160" />
          <el-table-column prop="batchCount" label="车数" width="100" />
          <el-table-column label="总米数">
            <template #default="{ row }">{{ fmtNum(row.totalMeters) }} m</template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 落入「在染」列时选染缸 -->
    <el-dialog v-model="assignVisible" title="排缸 · 选择染缸与时段" width="440px">
      <el-form label-width="88px">
        <el-form-item label="布车">
          <b>{{ pending.cartCode }}</b>
          <span class="muted" style="margin-left: 10px">{{ pending.colorName }} · {{ fmtNum(pending.meters) }}m</span>
        </el-form-item>
        <el-form-item label="染缸">
          <el-select v-model="form.vatId" placeholder="请选择染缸" style="width: 100%">
            <el-option v-for="v in vats" :key="v.id" :value="v.id"
                       :label="`${v.vatCode} · ${machineName(v.machineType)} · ${v.capacityKg}kg · ${statusName(v.status)}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm" style="width: 100%" />
        </el-form-item>
        <el-form-item>
          <span class="muted">同一染缸同一时段只能有一个在制批次，冲突会被拦截</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">确认排缸</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { assignCart, cartBoard, cartCustomerStats, changeCartStatus, listVats } from '../api'

defineProps({ params: { type: Object, default: () => ({}) } })

const COLUMNS = [
  { key: 'PENDING', name: '待排产', color: '#8d7fa6' },
  { key: 'DYEING', name: '在染', color: '#4a148c' },
  { key: 'DONE', name: '已完成', color: '#2e7d32' }
]
const MACHINE = { OVERFLOW: '溢喷', AIRFLOW: '气流', JIG: '卷染' }
const FABRIC = { COTTON: '全棉', POLY: '涤纶', NYLON: '锦纶', BLEND: '涤棉', VISCOSE: '人棉', SILK: '真丝' }
const STATUS = { IDLE: '空闲', HEATING: '升温', HOLDING: '保温', COOLING: '降温', DRAINING: '排液', WASHING: '清洗' }
const COLOR_MAP = {
  'C-1901': '#1A2B6B', 'C-1902': '#8B1A1A', 'C-1903': '#0F6E4F', 'C-1904': '#C9A227',
  'C-1905': '#6B4A8F', 'C-1906': '#D8D3C4', 'C-1907': '#1F1F1F', 'C-1908': '#2E8BC0'
}

const columns = ref({ PENDING: [], DYEING: [], DONE: [] })
const vats = ref([])
const customerRows = ref([])
const reworkCount = ref(0)
const totalMeters = ref(0)
const hoverCol = ref(null)
const assignVisible = ref(false)
const pending = ref({})
const form = ref({ vatId: null, startTime: '', endTime: '' })

const allCount = computed(() => COLUMNS.reduce((s, c) => s + (columns.value[c.key] || []).length, 0))

function fmtNum(v) {
  return v === null || v === undefined ? '--' : Number(v).toFixed(1)
}
function fmtTime(v) {
  if (!v) return ''
  return String(v).replace('T', ' ').slice(5, 16)
}
function colorOf(code) {
  return COLOR_MAP[code] || '#9e9e9e'
}
function fabricName(key) {
  return FABRIC[key] || key
}
function machineName(key) {
  return MACHINE[key] || key
}
function statusName(key) {
  return STATUS[key] || key
}
function pad(n) {
  return n < 10 ? '0' + n : String(n)
}
function stamp(offsetHours) {
  const d = new Date(Date.now() + offsetHours * 3600 * 1000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load() {
  try {
    const [board, vatRes, stat] = await Promise.all([cartBoard(), listVats(), cartCustomerStats()])
    columns.value = board.data.columns || { PENDING: [], DYEING: [], DONE: [] }
    reworkCount.value = board.data.reworkCount || 0
    totalMeters.value = board.data.totalMeters || 0
    vats.value = vatRes.data || []
    customerRows.value = stat.data || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function onDragStart(cart, e) {
  e.dataTransfer.setData('text/plain', String(cart.id))
  e.dataTransfer.effectAllowed = 'move'
}

async function onDrop(colKey, e) {
  hoverCol.value = null
  const id = Number(e.dataTransfer.getData('text/plain'))
  if (!id) return
  const cart = [...(columns.value.PENDING || []), ...(columns.value.DYEING || []), ...(columns.value.DONE || [])]
    .find((c) => c.id === id)
  if (!cart || cart.status === colKey) return

  if (colKey === 'DYEING') {
    pending.value = cart
    form.value = { vatId: cart.vatId || null, startTime: stamp(0), endTime: stamp(3.5) }
    assignVisible.value = true
    return
  }

  if (colKey === 'DONE' && cart.status === 'PENDING') {
    ElMessage.warning('待排产布车需先排缸进入「在染」，才能标记完成')
    return
  }

  try {
    const res = await changeCartStatus(id, { status: colKey })
    ElMessage.success(res.message || '状态已更新')
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function submitAssign() {
  if (!form.value.vatId) {
    ElMessage.warning('请选择染缸')
    return
  }
  try {
    const res = await assignCart(pending.value.id, {
      vatId: form.value.vatId,
      startTime: form.value.startTime,
      endTime: form.value.endTime
    })
    ElMessage.success(res.message || '排缸成功')
    assignVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

load()
</script>

<style scoped>
.cart-page { background: var(--mill-bg); }

.cart-summary {
  display: flex; align-items: center; gap: 30px;
  padding: 12px 16px; margin-bottom: 14px;
}
.cart-summary__item { display: flex; flex-direction: column; gap: 3px; }
.cart-summary__item b { font-size: 19px; color: var(--mill-ink); }
.cart-summary__item .warn { color: #c62828; }
.cart-summary__grow { flex: 1 1 auto; }

.board { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }

.col {
  background: #fff;
  border: 1px solid var(--mill-line);
  border-radius: 10px;
  min-height: 420px;
  display: flex;
  flex-direction: column;
  transition: box-shadow 0.16s, border-color 0.16s;
}
.col.is-over { border-color: var(--mill-primary); box-shadow: 0 0 0 3px rgba(74, 20, 140, 0.14); }

.col__head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px;
  border-top: 3px solid var(--mill-primary);
  border-bottom: 1px solid var(--mill-line);
  border-radius: 10px 10px 0 0;
}
.col__title { font-size: 14px; font-weight: 700; color: var(--mill-ink); }
.col__count {
  min-width: 24px; height: 20px; line-height: 20px; text-align: center;
  border-radius: 10px; background: var(--mill-primary-wash);
  color: var(--mill-primary); font-size: 12px; font-weight: 700;
}
.col__body { flex: 1 1 auto; padding: 10px; display: flex; flex-direction: column; gap: 10px; }
.col__empty { margin: auto; color: #b8aecd; font-size: 12px; }

.card {
  border: 1px solid var(--mill-line);
  border-left: 4px solid var(--mill-primary);
  border-radius: 8px;
  padding: 9px 10px;
  background: #fff;
  cursor: grab;
  transition: transform 0.14s, box-shadow 0.14s;
}
.card:hover { transform: translateY(-2px) rotate(-0.4deg); box-shadow: 0 8px 18px rgba(74, 20, 140, 0.16); }
.card:active { cursor: grabbing; }

.card__top { display: flex; align-items: center; justify-content: space-between; }
.card__code { font-size: 14px; font-weight: 700; color: var(--mill-ink); }
.card__badge { font-size: 11px; padding: 1px 7px; border-radius: 9px; color: #fff; }
.card__badge--rework { background: #c62828; }
.card__badge--dyeing { background: var(--mill-primary); }
.card__color { display: flex; align-items: center; gap: 6px; margin-top: 6px; font-size: 12px; }
.card__color i { width: 14px; height: 14px; border-radius: 3px; border: 1px solid rgba(0,0,0,0.1); }
.card__line { font-size: 12px; margin-top: 4px; }
.card__time { font-size: 11px; margin-top: 4px; }

.cart-stat { margin-top: 14px; }
</style>
