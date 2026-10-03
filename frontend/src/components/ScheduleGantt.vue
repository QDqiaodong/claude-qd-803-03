<template>
  <div class="gantt">
    <div class="gantt__scale">
      <div class="gantt__axis-label">缸号 / 时间</div>
      <div
        v-for="(t, i) in timeTicks"
        :key="i"
        class="gantt__tick"
        :style="{ left: pct(t.time) + '%' }"
      >
        <span>{{ t.label }}</span>
      </div>
    </div>

    <div v-for="vat in vats" :key="vat.id" class="grow">
      <div class="grow__head">
        <b>{{ vat.vatCode }}</b>
        <span>{{ machine(vat.machineType) }} · {{ vat.capacityKg }}kg</span>
      </div>
      <div class="grow__track" :data-vat="vat.vatCode">
        <!-- 网格时间格 -->
        <div
          v-for="(t, i) in gridLines"
          :key="'g' + i"
          class="grow__gridline"
          :style="{ left: pct(t) + '%' }"
        ></div>

        <!-- 维护段 -->
        <div
          v-for="m in blocksOf('maint', vat.id)"
          :key="'m' + m.id"
          class="seg seg--maint"
          :style="segStyle(m.startTime, m.endTime)"
        >
          <span>维护 · {{ m.reason }}</span>
        </div>

        <!-- 锁定段 -->
        <div
          v-for="l in blocksOf('lock', vat.id)"
          :key="'l' + l.id"
          class="seg seg--lock"
          :style="segStyle(l.startTime, l.endTime)"
        >
          <span>🔒 {{ l.cartCode || '封缸' }} · {{ l.lockReason }}</span>
        </div>

        <!-- 排程结果 -->
        <template v-for="a in assignmentsOf(vat.id)" :key="a.id">
          <!-- 清洗占时段 -->
          <div
            v-if="a.washBeforeMin > 0"
            class="seg seg--wash"
            :style="segStyle(washStart(a), a.startTime)"
            :title="`${washName(a.switchType)} ${a.washBeforeMin} 分钟 / ${a.washBeforeCost || 0} 元`"
          >
            <span>洗 {{ (a.washBeforeMin / 60).toFixed(1) }}h</span>
          </div>
          <div
            :class="['seg', segClass(a)]"
            :style="segStyle(a.startTime, a.endTime)"
            @click="$emit('select', a)"
          >
            <span class="seg__code">{{ a.cartCode }}</span>
            <span v-if="a.locked" class="seg__lock">🔒</span>
          </div>
        </template>

        <!-- 当前时间线 -->
        <div v-if="nowPct !== null" class="grow__now" :style="{ left: nowPct + '%' }"></div>
      </div>
    </div>

    <div class="gantt__legend">
      <i class="lg lg--dye"></i> 染色
      <i class="lg lg--wash"></i> 清洗占时
      <i class="lg lg--maint"></i> 维护
      <i class="lg lg--lock"></i> 人工锁定
      <i class="lg lg--late"></i> 逾期
      <i class="lg lg--fixed"></i> 在制固定段
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  vats: { type: Array, default: () => [] },
  assignments: { type: Array, default: () => [] },
  maintenance: { type: Array, default: () => [] },
  locks: { type: Array, default: () => [] },
  start: { type: String, required: true },
  end: { type: String, required: true }
})
defineEmits(['select'])

const MACHINE = { OVERFLOW: '溢喷', AIRFLOW: '气流', JIG: '卷染' }
function machine(k) { return MACHINE[k] || k }

const t0 = computed(() => new Date(props.start.replace(' ', 'T')).getTime())
const t1 = computed(() => new Date(props.end.replace(' ', 'T')).getTime())
const span = computed(() => t1.value - t0.value)

function pct(time) {
  const t = new Date(String(time).replace(' ', 'T')).getTime()
  return Math.max(0, Math.min(100, ((t - t0.value) / span.value) * 100))
}

function segStyle(s, e) {
  const left = pct(s)
  const width = Math.max(0.6, pct(e) - left)
  return { left: left + '%', width: width + '%' }
}

const timeTicks = computed(() => {
  const ticks = []
  const dayMs = 24 * 3600 * 1000
  const startD = new Date(t0.value)
  startD.setHours(0, 0, 0, 0)
  for (let t = startD.getTime(); t <= t1.value + dayMs; t += 12 * 3600 * 1000) {
    if (t < t0.value - 3600 * 1000) continue
    const d = new Date(t)
    const hh = String(d.getHours()).padStart(2, '0')
    const label = hh === '00' ? `${d.getMonth() + 1}/${d.getDate()}` : `${d.getMonth() + 1}/${d.getDate()} ${hh}时`
    ticks.push({ time: d.toISOString ? formatLocal(d) : t, label })
  }
  return ticks
})

const gridLines = computed(() => {
  const lines = []
  const dayMs = 24 * 3600 * 1000
  const startD = new Date(t0.value)
  startD.setHours(0, 0, 0, 0)
  for (let t = startD.getTime(); t <= t1.value; t += 12 * 3600 * 1000) {
    if (t < t0.value) continue
    lines.push(formatLocal(new Date(t)))
  }
  return lines
})

function formatLocal(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

const nowPct = computed(() => {
  const now = Date.now()
  if (now <= t0.value || now >= t1.value) return null
  return ((now - t0.value) / span.value) * 100
})

function blocksOf(kind, vatId) {
  if (kind === 'maint') return props.maintenance.filter((b) => b.vatId === vatId && overlaps(b))
  return props.locks.filter((b) => b.vatId === vatId && overlaps(b))
}
function overlaps(b) {
  const s = new Date(String(b.startTime).replace(' ', 'T')).getTime()
  const e = new Date(String(b.endTime).replace(' ', 'T')).getTime()
  return e > t0.value && s < t1.value
}

function assignmentsOf(vatId) {
  return props.assignments.filter((a) => a.vatId === vatId && a.startTime)
}

function washStart(a) {
  const d = new Date(String(a.startTime).replace(' ', 'T'))
  d.setMinutes(d.getMinutes() - (a.washBeforeMin || 0))
  return formatLocal(d)
}

function washName(type) {
  return { SAME: '同色续染清洗', L2D: '浅转深清洗', D2L: '深转浅清洗', SPECIAL: '特殊染料切换清洗' }[type] || '清洗'
}

function segClass(a) {
  if (a.resultType === 'FIXED') return a.locked ? 'seg--lock-dye' : 'seg--fixed'
  if (a.lateMinutes > 0) return 'seg--late'
  return 'seg--dye'
}
</script>

<style scoped>
.gantt { border: 1px solid var(--mill-line); border-radius: 10px; background: #fff; overflow: hidden; }
.gantt__scale { position: relative; height: 38px; border-bottom: 1px solid var(--mill-line); background: #faf8fe; }
.gantt__axis-label { position: absolute; left: 0; top: 0; width: 108px; height: 100%; display: flex; align-items: center; padding-left: 10px; font-size: 12px; color: var(--mill-muted); border-right: 1px solid var(--mill-line); }
.gantt__tick { position: absolute; top: 0; height: 100%; border-left: 1px dashed #e0d6ee; font-size: 10px; color: var(--mill-muted); }
.gantt__tick span { position: absolute; left: 4px; top: 4px; white-space: nowrap; }

.grow { display: flex; align-items: stretch; border-bottom: 1px solid var(--mill-line); }
.grow:last-of-type { border-bottom: none; }
.grow__head { flex: 0 0 108px; padding: 6px 10px; border-right: 1px solid var(--mill-line); display: flex; flex-direction: column; justify-content: center; gap: 2px; background: #faf8fe; }
.grow__head b { font-size: 13px; color: var(--mill-ink); }
.grow__head span { font-size: 10px; color: var(--mill-muted); }
.grow__track { position: relative; flex: 1 1 auto; height: 42px; }
.grow__gridline { position: absolute; top: 0; bottom: 0; width: 0; border-left: 1px dashed #eee8f7; }
.grow__now { position: absolute; top: 0; bottom: 0; width: 2px; background: #c62828; z-index: 6; }
.grow__now::after { content: '现在'; position: absolute; top: 1px; left: 3px; font-size: 9px; color: #c62828; }

.seg { position: absolute; top: 7px; height: 28px; border-radius: 5px; display: flex; align-items: center; gap: 3px; padding: 0 5px; font-size: 10px; color: #fff; overflow: hidden; z-index: 4; box-shadow: 0 1px 3px rgba(0,0,0,0.15); }
.seg span { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.seg__code { font-weight: 700; }
.seg__lock { font-size: 10px; }
.seg--dye { background: var(--mill-primary); cursor: pointer; }
.seg--fixed { background: #7e6fa0; cursor: pointer; }
.seg--late { background: linear-gradient(90deg, #c62828, #ef6c00); cursor: pointer; }
.seg--lock-dye { background: repeating-linear-gradient(45deg, #4a148c, #4a148c 6px, #6a1fb0 6px, #6a1fb0 12px); cursor: pointer; }
.seg--wash { top: 28px; height: 13px; background: repeating-linear-gradient(-45deg, #b39ddb, #b39ddb 4px, #d1c4e9 4px, #d1c4e9 8px); color: #311b92; font-size: 9px; z-index: 3; border: 1px solid #9575cd; }
.seg--maint { background: repeating-linear-gradient(45deg, #90a4ae, #90a4ae 5px, #b0bec5 5px, #b0bec5 10px); color: #263238; }
.seg--lock { background: repeating-linear-gradient(45deg, #5d4037, #5d4037 5px, #795548 5px, #795548 10px); }

.gantt__legend { display: flex; align-items: center; gap: 6px; padding: 8px 12px; font-size: 11px; color: var(--mill-muted); border-top: 1px solid var(--mill-line); flex-wrap: wrap; }
.lg { width: 14px; height: 10px; border-radius: 3px; display: inline-block; margin-left: 8px; }
.lg--dye { background: var(--mill-primary); }
.lg--wash { background: repeating-linear-gradient(-45deg, #b39ddb, #b39ddb 3px, #d1c4e9 3px, #d1c4e9 6px); }
.lg--maint { background: #90a4ae; }
.lg--lock { background: #5d4037; }
.lg--late { background: linear-gradient(90deg, #c62828, #ef6c00); }
.lg--fixed { background: #7e6fa0; }
</style>
