<template>
  <div class="page vat-page">
    <!-- 概览条 -->
    <div class="panel vat-summary">
      <div class="vat-summary__item">
        <span class="vat-summary__label">染缸总数</span>
        <span class="vat-summary__value">{{ vats.length }}</span>
      </div>
      <div class="vat-summary__item">
        <span class="vat-summary__label">在制缸数</span>
        <span class="vat-summary__value vat-summary__value--hot">{{ runningCount }}</span>
      </div>
      <div class="vat-summary__item vat-summary__item--grow">
        <span class="vat-summary__label">阶段分布</span>
        <div class="vat-legend">
          <span v-for="s in STATUS_LIST" :key="s.key" class="vat-legend__tag">
            <i :style="{ background: s.color }"></i>{{ s.name }} {{ statusCount[s.key] || 0 }}
          </span>
        </div>
      </div>
      <div class="vat-summary__ops">
        <el-checkbox v-model="autoTick" size="small">自动推演温度</el-checkbox>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
    </div>

    <!-- 缸位温度环阵列 -->
    <div class="vat-grid">
      <div
        v-for="vat in filteredVats"
        :key="vat.id"
        class="vat-card"
        :style="{ borderTopColor: colorOf(vat) }"
        @click="openPanel(vat)"
      >
        <svg class="vat-ring" viewBox="0 0 140 140">
          <circle cx="70" cy="70" r="56" class="ring-track" />
          <circle
            cx="70" cy="70" r="56"
            class="ring-value"
            :stroke="colorOf(vat)"
            :stroke-dasharray="dashOf(vat)"
          />
          <!-- 目标温度刻度 -->
          <line
            :x1="markOf(vat).x1" :y1="markOf(vat).y1"
            :x2="markOf(vat).x2" :y2="markOf(vat).y2"
            stroke="#4a148c" stroke-width="3" stroke-linecap="round"
          />
          <text x="70" y="68" class="ring-temp">{{ fmt(vat.currentTemp) }}</text>
          <text x="70" y="86" class="ring-unit">℃</text>
          <text x="70" y="104" class="ring-goal">目标 {{ fmt(vat.targetTemp) }}℃</text>
        </svg>
        <div class="vat-meta">
          <div class="vat-meta__code">
            {{ vat.vatCode }}
            <span class="vat-chip" :style="{ background: colorOf(vat) }">{{ statusName(vat.status) }}</span>
          </div>
          <div class="vat-meta__line">{{ machineName(vat.machineType) }} · {{ vat.capacityKg }}kg · 浴比 1:{{ vat.liquorRatio }}</div>
          <div class="vat-meta__line muted">{{ vat.workshop }} · 保温 {{ vat.holdMinutes }}′/剩 {{ vat.holdRemainMinutes }}′</div>
        </div>
      </div>
    </div>

    <!-- 控温面板（点环滑出） -->
    <el-drawer v-model="panelVisible" :title="`控温面板 · ${active.vatCode || ''}`" size="360px">
      <div v-if="active.id" class="ctrl">
        <div class="ctrl__row"><span class="muted">机台类型</span><b>{{ machineName(active.machineType) }}</b></div>
        <div class="ctrl__row"><span class="muted">容量 / 浴比</span><b>{{ active.capacityKg }}kg · 1:{{ active.liquorRatio }}</b></div>
        <div class="ctrl__row"><span class="muted">当前温度</span><b class="ctrl__temp">{{ fmt(active.currentTemp) }} ℃</b></div>
        <div class="ctrl__row"><span class="muted">当前阶段</span>
          <span class="vat-chip" :style="{ background: colorOf(active) }">{{ statusName(active.status) }}</span>
        </div>

        <div class="ctrl__field">
          <span class="ctrl__label">目标温度（℃）</span>
          <el-slider v-model="targetTemp" :min="20" :max="140" :step="1" show-input :show-input-controls="false" />
        </div>
        <div class="ctrl__field">
          <span class="ctrl__label">保温时长（分钟）</span>
          <el-input-number v-model="holdMinutes" :min="0" :max="240" :step="5" />
        </div>

        <div class="ctrl__tips muted">
          机台温度上限：{{ machineMax }}℃ · 目标高于当前进入升温，低于当前进入降温
        </div>

        <div class="ctrl__ops">
          <el-button type="primary" @click="submitControl">下达控温指令</el-button>
          <el-button @click="tick(active.id)">推进一拍</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { controlVat, listVats, tickVat } from '../api'

const props = defineProps({ params: { type: Object, default: () => ({}) } })

const MAX_TEMP = 140
const CIRC = 2 * Math.PI * 56

const STATUS_LIST = [
  { key: 'IDLE', name: '空闲', color: '#9e9e9e' },
  { key: 'HEATING', name: '升温', color: '#ef6c00' },
  { key: 'HOLDING', name: '保温', color: '#4a148c' },
  { key: 'COOLING', name: '降温', color: '#0277bd' },
  { key: 'DRAINING', name: '排液', color: '#00838f' },
  { key: 'WASHING', name: '清洗', color: '#2e7d32' }
]
const MACHINE = { OVERFLOW: '溢喷', AIRFLOW: '气流', JIG: '卷染' }
const MACHINE_MAX = { OVERFLOW: 135, AIRFLOW: 140, JIG: 98 }

const vats = ref([])
const panelVisible = ref(false)
const active = ref({})
const targetTemp = ref(60)
const holdMinutes = ref(30)
const autoTick = ref(false)
let timer = null

const statusCount = computed(() => {
  const map = {}
  vats.value.forEach((v) => { map[v.status] = (map[v.status] || 0) + 1 })
  return map
})
const runningCount = computed(() => vats.value.filter((v) => v.status !== 'IDLE').length)
const filteredVats = computed(() =>
  props.params.view === 'running' ? vats.value.filter((v) => v.status !== 'IDLE') : vats.value
)
const machineMax = computed(() => MACHINE_MAX[active.value.machineType] || 140)

function colorOf(vat) {
  const hit = STATUS_LIST.find((s) => s.key === vat.status)
  return hit ? hit.color : '#9e9e9e'
}
function statusName(key) {
  const hit = STATUS_LIST.find((s) => s.key === key)
  return hit ? hit.name : key
}
function machineName(key) {
  return MACHINE[key] || key
}
function fmt(v) {
  return v === null || v === undefined ? '--' : Number(v).toFixed(1)
}
function ratio(v) {
  const t = Number(v || 0)
  return Math.max(0, Math.min(1, t / MAX_TEMP))
}
function dashOf(vat) {
  const len = ratio(vat.currentTemp) * CIRC
  return `${len} ${CIRC}`
}
function markOf(vat) {
  const angle = ratio(vat.targetTemp) * 2 * Math.PI - Math.PI / 2
  const cos = Math.cos(angle)
  const sin = Math.sin(angle)
  return {
    x1: 70 + 62 * cos,
    y1: 70 + 62 * sin,
    x2: 70 + 72 * cos,
    y2: 70 + 72 * sin
  }
}

async function load() {
  try {
    const res = await listVats()
    vats.value = res.data || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openPanel(vat) {
  active.value = vat
  targetTemp.value = Number(vat.targetTemp || 60)
  holdMinutes.value = Number(vat.holdMinutes || 30)
  panelVisible.value = true
}

async function submitControl() {
  try {
    const res = await controlVat(active.value.id, { targetTemp: targetTemp.value, holdMinutes: holdMinutes.value })
    ElMessage.success(res.message || '控温指令已下达')
    Object.assign(active.value, res.data)
    panelVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function tick(id) {
  try {
    await tickVat(id)
    await load()
    const still = vats.value.find((v) => v.id === id)
    if (still) Object.assign(active.value, still)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

watch(autoTick, (on) => {
  if (timer) { clearInterval(timer); timer = null }
  if (on) {
    timer = setInterval(() => {
      const running = vats.value.filter((v) => v.status !== 'IDLE')
      if (!running.length) return
      Promise.all(running.map((v) => tickVat(v.id).catch(() => null))).then(load)
    }, 2000)
  }
})

onBeforeUnmount(() => { if (timer) clearInterval(timer) })
load()
</script>

<style scoped>
.vat-page { background: var(--mill-bg); }

.vat-summary {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 12px 16px;
  margin-bottom: 14px;
}

.vat-summary__item { display: flex; flex-direction: column; gap: 4px; }
.vat-summary__item--grow { flex: 1 1 auto; }
.vat-summary__label { font-size: 12px; color: var(--mill-muted); }
.vat-summary__value { font-size: 22px; font-weight: 700; color: var(--mill-ink); }
.vat-summary__value--hot { color: var(--mill-primary); }
.vat-summary__ops { display: flex; align-items: center; gap: 12px; }

.vat-legend { display: flex; flex-wrap: wrap; gap: 10px; }
.vat-legend__tag { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; color: var(--mill-text); }
.vat-legend__tag i { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }

.vat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(196px, 1fr));
  gap: 14px;
}

.vat-card {
  background: #fff;
  border: 1px solid var(--mill-line);
  border-top: 4px solid var(--mill-primary);
  border-radius: 10px;
  padding: 12px 10px 10px;
  cursor: pointer;
  transition: transform 0.16s, box-shadow 0.16s;
}
.vat-card:hover { transform: translateY(-3px); box-shadow: 0 10px 22px rgba(74, 20, 140, 0.16); }

.vat-ring { width: 100%; height: 156px; display: block; }
.ring-track { fill: none; stroke: #efe9f7; stroke-width: 12; }
.ring-value {
  fill: none;
  stroke-width: 12;
  stroke-linecap: round;
  transform: rotate(-90deg);
  transform-origin: 70px 70px;
  transition: stroke-dasharray 0.5s ease, stroke 0.3s ease;
}
.ring-temp { text-anchor: middle; font-size: 26px; font-weight: 700; fill: var(--mill-ink); }
.ring-unit { text-anchor: middle; font-size: 12px; fill: var(--mill-muted); }
.ring-goal { text-anchor: middle; font-size: 11px; fill: var(--mill-primary); }

.vat-meta { text-align: center; margin-top: 4px; }
.vat-meta__code { font-size: 15px; font-weight: 700; color: var(--mill-ink); display: flex; align-items: center; justify-content: center; gap: 6px; }
.vat-meta__line { font-size: 12px; color: var(--mill-text); margin-top: 3px; }

.vat-chip {
  display: inline-block;
  padding: 1px 7px;
  border-radius: 9px;
  color: #fff;
  font-size: 11px;
  font-weight: 500;
}

.ctrl { padding: 4px 6px; }
.ctrl__row { display: flex; align-items: center; justify-content: space-between; padding: 7px 0; border-bottom: 1px dashed var(--mill-line); }
.ctrl__temp { color: var(--mill-primary); font-size: 16px; }
.ctrl__field { margin-top: 16px; }
.ctrl__label { display: block; font-size: 12px; color: var(--mill-muted); margin-bottom: 6px; }
.ctrl__tips { margin-top: 14px; line-height: 1.6; }
.ctrl__ops { margin-top: 18px; display: flex; gap: 10px; }
</style>
