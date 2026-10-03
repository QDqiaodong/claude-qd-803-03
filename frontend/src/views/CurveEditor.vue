<template>
  <div class="page curve-page">
    <div class="curve">
      <!-- 曲线清单 -->
      <div class="panel curve__list">
        <div class="panel__head"><span class="panel__title">工艺曲线</span></div>
        <div class="panel__body curve__list-body">
          <div
            v-for="c in curves"
            :key="c.id"
            :class="['curve-chip', current && current.id === c.id ? 'is-active' : '']"
            @click="select(c)"
          >
            <b>{{ c.curveName }}</b>
            <i>{{ c.curveCode }} · {{ c.fabricType }}</i>
          </div>
        </div>
      </div>

      <!-- 编辑器 -->
      <div class="curve__main" v-if="current">
        <div class="panel">
          <div class="panel__head">
            <span class="panel__title">曲线编辑器 · {{ current.curveName }}</span>
            <div class="curve__head-ops">
              <span class="muted">适用布种</span>
              <el-select v-model="fabricType" size="small" style="width: 150px" @change="revalidate">
                <el-option v-for="f in fabrics" :key="f.fabricType" :label="`${f.fabricName}（≤${f.maxRampRate}℃/min）`" :value="f.fabricType" />
              </el-select>
              <el-button size="small" @click="revalidate">校验速率</el-button>
              <el-button type="primary" size="small" @click="save">保存节点</el-button>
            </div>
          </div>
          <div class="panel__body">
            <svg ref="svgEl" class="curve__canvas" viewBox="0 0 820 360" @mouseleave="dragging = null">
              <!-- 网格 -->
              <g class="grid">
                <line v-for="t in yTicks" :key="'y' + t"
                      :x1="PAD.left" :y1="yOf(t)" :x2="820 - PAD.right" :y2="yOf(t)" />
                <line v-for="t in xTicks" :key="'x' + t"
                      :x1="xOf(t)" :y1="PAD.top" :x2="xOf(t)" :y2="360 - PAD.bottom" />
              </g>
              <g class="axis-label">
                <text v-for="t in yTicks" :key="'yt' + t" :x="PAD.left - 8" :y="yOf(t) + 4">{{ t }}℃</text>
                <text v-for="t in xTicks" :key="'xt' + t" :x="xOf(t)" :y="360 - PAD.bottom + 18">{{ t }}′</text>
              </g>

              <!-- 曲线面积 + 折线 -->
              <path :d="areaPath" class="curve-area" />
              <path :d="linePath" class="curve-line" />

              <!-- 分段着色 -->
              <line
                v-for="(seg, i) in segments"
                :key="'seg' + i"
                :x1="xOf(seg.t0)" :y1="yOf(seg.from)"
                :x2="xOf(seg.t1)" :y2="yOf(seg.to)"
                :stroke="stageColor(seg.stage)"
                :class="seg.overLimit ? 'seg-line is-danger' : 'seg-line'"
              />

              <!-- 节点（可拖动） -->
              <g v-for="(p, i) in points" :key="'p' + i"
                 :class="['node', dragging === i ? 'is-dragging' : '']"
                 @mousedown="startDrag(i, $event)">
                <circle :cx="p.x" :cy="p.y" r="9" :fill="stageColor(p.stage)" class="node__dot" />
                <circle :cx="p.x" :cy="p.y" r="14" class="node__halo" />
                <text :x="p.x" :y="p.y - 16" class="node__text">{{ p.temp }}℃ / {{ p.time }}′</text>
              </g>

              <!-- 起点 -->
              <circle :cx="xOf(0)" :cy="yOf(startTemp)" r="5" fill="#4a148c" />
              <text :x="xOf(0) + 6" :y="yOf(startTemp) - 8" class="node__text">入染 {{ startTemp }}℃</text>
            </svg>

            <div class="curve__legend">
              <span><i style="background:#ef6c00"></i>升温 RAMP</span>
              <span><i style="background:#4a148c"></i>保温 HOLD</span>
              <span><i style="background:#0277bd"></i>降温 COOL</span>
              <span class="muted">拖动圆点改温度 / 时长（横轴=分钟，纵轴=℃）</span>
            </div>
          </div>
        </div>

        <!-- 分段速率表 -->
        <div class="panel">
          <div class="panel__head">
            <span class="panel__title">分段速率 · 上限 {{ maxRampRate }}℃/min（{{ fabricName }}）</span>
            <span class="muted">总时长 {{ totalMinutes }}′ · 全曲线峰值 {{ peakRate }}℃/min</span>
          </div>
          <div class="panel__body">
            <table class="seg-table">
              <thead>
                <tr>
                  <th style="width:46px">段</th>
                  <th style="width:96px">阶段</th>
                  <th style="width:130px">温度区间</th>
                  <th style="width:100px">时长 min</th>
                  <th style="width:120px">速率 ℃/min</th>
                  <th>提示</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(s, i) in segments" :key="i" :class="s.overLimit ? 'is-bad' : ''">
                  <td>{{ i + 1 }}</td>
                  <td>
                    <el-select v-model="nodes[i].stage" size="small" style="width:92px">
                      <el-option label="升温" value="RAMP" />
                      <el-option label="保温" value="HOLD" />
                      <el-option label="降温" value="COOL" />
                    </el-select>
                  </td>
                  <td>{{ s.from }}℃ → {{ s.to }}℃</td>
                  <td>{{ s.duration }}</td>
                  <td class="num" :class="s.overLimit ? 'bad-num' : ''">{{ s.rate.toFixed(2) }}</td>
                  <td>
                    <span v-if="s.overLimit" class="bad-tip">
                      升温速率 {{ s.rate.toFixed(2) }}℃/min 已超布种上限 {{ maxRampRate }}℃/min，请拖长该段时长
                    </span>
                    <span v-else-if="s.overTemp" class="bad-tip">节点温度超过布种保温上限</span>
                    <span v-else class="ok-tip">正常</span>
                  </td>
                </tr>
              </tbody>
            </table>
            <div class="curve__foot">
              <el-button size="small" @click="addNode">+ 节点</el-button>
              <el-button size="small" @click="resetNodes">还原</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getCurve, listCurves, listFabrics, saveCurveNodes, validateCurve } from '../api'

defineProps({ params: { type: Object, default: () => ({}) } })

const PAD = { left: 54, right: 20, top: 18, bottom: 34 }
const TEMP_MIN = 20
const TEMP_MAX = 140
const PLOT_W = 820 - PAD.left - PAD.right
const PLOT_H = 360 - PAD.top - PAD.bottom

const curves = ref([])
const fabrics = ref([])
const current = ref(null)
const nodes = ref([])
const startTemp = ref(30)
const fabricType = ref('COTTON')
const dragging = ref(null)
const svgEl = ref(null)

const maxRampRate = computed(() => {
  const f = fabrics.value.find((x) => x.fabricType === fabricType.value)
  return f ? Number(f.maxRampRate) : 3
})
const fabricName = computed(() => {
  const f = fabrics.value.find((x) => x.fabricType === fabricType.value)
  return f ? f.fabricName : fabricType.value
})
const maxHoldTemp = computed(() => {
  const f = fabrics.value.find((x) => x.fabricType === fabricType.value)
  return f ? Number(f.maxHoldTemp) : 130
})
const totalMinutes = computed(() => nodes.value.reduce((s, n) => s + (Number(n.durationMin) || 0), 0))
const timeMax = computed(() => Math.max(totalMinutes.value * 1.12, 60))

const Y_TICKS = [20, 40, 60, 80, 100, 120, 140]
const yTicks = Y_TICKS
const xTicks = computed(() => {
  const step = timeMax.value > 200 ? 40 : 20
  const out = []
  for (let t = 0; t <= timeMax.value; t += step) out.push(t)
  return out
})

const segments = computed(() => {
  const out = []
  let from = Number(startTemp.value)
  let t = 0
  nodes.value.forEach((n) => {
    const to = Number(n.targetTemp)
    const duration = Number(n.durationMin) || 0
    const rate = duration > 0 ? (to - from) / duration : 0
    const overLimit = n.stage === 'RAMP' && rate > maxRampRate.value
    out.push({
      stage: n.stage, from, to, duration, rate,
      t0: t, t1: t + duration,
      overLimit,
      overTemp: to > maxHoldTemp.value
    })
    from = to
    t += duration
  })
  return out
})

const points = computed(() => {
  const out = []
  let t = 0
  nodes.value.forEach((n) => {
    t += Number(n.durationMin) || 0
    out.push({ x: xOf(t), y: yOf(Number(n.targetTemp)), temp: Number(n.targetTemp), time: t, stage: n.stage })
  })
  return out
})

const peakRate = computed(() => {
  const ramps = segments.value.filter((s) => s.stage === 'RAMP').map((s) => s.rate)
  return ramps.length ? Math.max(...ramps).toFixed(2) : '0.00'
})

const linePath = computed(() => {
  let d = `M ${xOf(0)} ${yOf(Number(startTemp.value))}`
  points.value.forEach((p) => { d += ` L ${p.x} ${p.y}` })
  return d
})

const areaPath = computed(() => {
  if (!points.value.length) return ''
  return `${linePath.value} L ${points.value[points.value.length - 1].x} ${360 - PAD.bottom} L ${xOf(0)} ${360 - PAD.bottom} Z`
})

function xOf(t) {
  return PAD.left + (t / timeMax.value) * PLOT_W
}
function yOf(temp) {
  return PAD.top + ((TEMP_MAX - temp) / (TEMP_MAX - TEMP_MIN)) * PLOT_H
}
function stageColor(stage) {
  if (stage === 'RAMP') return '#ef6c00'
  if (stage === 'COOL') return '#0277bd'
  return '#4a148c'
}

function startDrag(index, e) {
  dragging.value = index
  e.preventDefault()
  const move = (ev) => onDrag(index, ev)
  const up = () => {
    dragging.value = null
    window.removeEventListener('mousemove', move)
    window.removeEventListener('mouseup', up)
  }
  window.addEventListener('mousemove', move)
  window.addEventListener('mouseup', up)
}

function onDrag(index, ev) {
  const el = svgEl.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const vx = (ev.clientX - rect.left) * (820 / rect.width)
  const vy = (ev.clientY - rect.top) * (360 / rect.height)

  // 纵轴 → 温度
  let temp = TEMP_MAX - ((vy - PAD.top) / PLOT_H) * (TEMP_MAX - TEMP_MIN)
  temp = Math.round(Math.min(TEMP_MAX, Math.max(TEMP_MIN, temp)) * 2) / 2

  // 横轴 → 本段时长
  const t = ((vx - PAD.left) / PLOT_W) * timeMax.value
  let prev = 0
  for (let i = 0; i < index; i++) prev += Number(nodes.value[i].durationMin) || 0
  let duration = Math.round(t - prev)
  duration = Math.min(300, Math.max(1, duration))

  nodes.value[index].targetTemp = temp
  nodes.value[index].durationMin = duration
}

function addNode() {
  nodes.value.push({ stage: 'HOLD', targetTemp: 60, durationMin: 10, memo: '' })
}

function resetNodes() {
  if (current.value) select(current.value)
}

function select(curve) {
  current.value = curve
  nodes.value = (curve.nodes || []).map((n) => ({
    id: n.id,
    stage: n.stage,
    targetTemp: Number(n.targetTemp),
    durationMin: Number(n.durationMin),
    memo: n.memo
  }))
  startTemp.value = Number(curve.startTemp)
  fabricType.value = curve.fabricType
}

async function load() {
  try {
    const [c, f] = await Promise.all([listCurves(), listFabrics()])
    curves.value = c.data || []
    fabrics.value = f.data || []
    if (curves.value.length && !current.value) select(curves.value[0])
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function revalidate() {
  try {
    const res = await validateCurve(current.value.id, {
      fabricType: fabricType.value,
      nodes: nodes.value
    })
    const segs = res.data.segments || []
    ElMessage.success(segs.some((s) => s.overLimit) ? '存在超速率段，请查看提示' : '速率校验通过')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function save() {
  const bad = segments.value.find((s) => s.overLimit)
  if (bad) {
    ElMessage.error(`第 ${segments.value.indexOf(bad) + 1} 段升温速率 ${bad.rate.toFixed(2)}℃/min 超过上限 ${maxRampRate.value}℃/min`)
    return
  }
  try {
    const res = await saveCurveNodes(current.value.id, {
      curveName: current.value.curveName,
      fabricType: fabricType.value,
      colorCode: current.value.colorCode,
      recipeId: current.value.recipeId,
      startTemp: startTemp.value,
      nodes: nodes.value
    })
    ElMessage.success(res.message || '节点已保存')
    const fresh = await getCurve(current.value.id)
    current.value = fresh.data
    select(fresh.data)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onBeforeUnmount(() => { dragging.value = null })
load()
</script>

<style scoped>
.curve-page { background: var(--mill-bg); }
.curve { display: flex; gap: 14px; align-items: flex-start; }
.curve__list { width: 240px; flex: 0 0 240px; }
.curve__list-body { padding: 8px; }
.curve__main { flex: 1 1 auto; min-width: 0; display: flex; flex-direction: column; gap: 14px; }

.curve-chip { padding: 8px; border-radius: 8px; cursor: pointer; border: 1px solid transparent; }
.curve-chip:hover { background: var(--mill-primary-wash); }
.curve-chip.is-active { border-color: var(--mill-primary); background: var(--mill-primary-wash); }
.curve-chip b { display: block; font-size: 13px; color: var(--mill-ink); }
.curve-chip i { font-style: normal; font-size: 11px; color: var(--mill-muted); }

.curve__head-ops { display: flex; align-items: center; gap: 8px; }

.curve__canvas {
  width: 100%;
  height: 360px;
  background: #fdfcff;
  border: 1px solid var(--mill-line);
  border-radius: 8px;
  user-select: none;
}
.grid line { stroke: #efe9f7; stroke-width: 1; }
.axis-label text { font-size: 10px; fill: #8a7fa0; text-anchor: end; }
.axis-label text:nth-child(n + 8) { text-anchor: middle; }
.curve-area { fill: rgba(74, 20, 140, 0.07); stroke: none; }
.curve-line { fill: none; stroke: rgba(74, 20, 140, 0.25); stroke-width: 1.5; }
.seg-line { stroke-width: 3.5; stroke-linecap: round; }
.seg-line.is-danger { stroke: #c62828; }
.node { cursor: grab; }
.node.is-dragging { cursor: grabbing; }
.node__halo { fill: rgba(74, 20, 140, 0.12); }
.node__dot { stroke: #fff; stroke-width: 2; }
.node__text { font-size: 10px; fill: #4a148c; text-anchor: middle; font-weight: 600; }

.curve__legend { display: flex; align-items: center; gap: 16px; margin-top: 8px; font-size: 12px; }
.curve__legend span { display: inline-flex; align-items: center; gap: 5px; }
.curve__legend i { width: 12px; height: 4px; border-radius: 2px; display: inline-block; }

.seg-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.seg-table th {
  background: var(--mill-primary-wash); color: var(--mill-ink);
  text-align: left; padding: 8px; border-bottom: 1px solid var(--mill-line);
}
.seg-table td { padding: 6px 8px; border-bottom: 1px solid #f1edf8; }
.seg-table tr.is-bad td { background: #fdecea; }
.seg-table .num { text-align: right; font-variant-numeric: tabular-nums; font-weight: 700; }
.bad-num { color: #c62828; }
.bad-tip { color: #c62828; font-size: 12px; }
.ok-tip { color: #2e7d32; font-size: 12px; }
.curve__foot { margin-top: 10px; display: flex; gap: 8px; }
</style>
