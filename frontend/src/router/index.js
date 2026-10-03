import { computed, ref } from 'vue'
import VatBoard from '../views/VatBoard.vue'
import RecipeLab from '../views/RecipeLab.vue'
import CurveEditor from '../views/CurveEditor.vue'
import CartKanban from '../views/CartKanban.vue'
import ExecutionOrders from '../views/ExecutionOrders.vue'
import Inventory from '../views/Inventory.vue'
import ScheduleBoard from '../views/ScheduleBoard.vue'

/**
 * 业务模块。启动台格子共用这些页面，部分格子是带预设参数的快捷入口（同一页面不同落点）。
 */
export const routes = [
  { path: '/schedule', name: '染缸换色排程台', desc: '跨缸计划 · 清洗矩阵 · 锁定与复算', icon: 'schedule', component: ScheduleBoard, query: {} },
  { path: '/vats', name: '染缸缸位', desc: '温度环阵列 · 控温面板', icon: 'vat', component: VatBoard, query: {} },
  { path: '/recipes', name: '染色配方', desc: '色卡条 · 染料阶梯表', icon: 'recipe', component: RecipeLab, query: {} },
  { path: '/curves', name: '工艺曲线', desc: 'SVG 节点可拖动编辑器', icon: 'curve', component: CurveEditor, query: {} },
  { path: '/carts', name: '布车流转', desc: '三列看板 · 原生拖拽', icon: 'cart', component: CartKanban, query: {} },
  { path: '/execution', name: '开缸执行单', desc: '资料核对 · 失效锁定 · 下达快照', icon: 'execution', component: ExecutionOrders, query: {} },
  { path: '/inventory', name: '染化料库存', desc: '库存 · 预警 · 领用', icon: 'stock', component: Inventory, query: {} },
  { path: '/vats', name: '在制缸位', desc: '只看升温/保温中的缸', icon: 'running', component: VatBoard, query: { view: 'running' } },
  { path: '/recipes', name: '色号缸次', desc: '按色号统计缸次与米数', icon: 'stat', component: RecipeLab, query: { view: 'stat' } },
  { path: '/schedule', name: '排程方案库', desc: '留存方案对比 · 选定车间参考', icon: 'plans', component: ScheduleBoard, query: { tab: 'plans' } },
  { path: '/schedule', name: '清洗矩阵', desc: '颜色族切换时长与成本口径', icon: 'matrix', component: ScheduleBoard, query: { tab: 'matrix' } },
  { path: '/inventory', name: '染料库存', desc: '只看染料（DYE）', icon: 'dye', component: Inventory, query: { tab: 'DYE' } },
  { path: '/inventory', name: '库存预警', desc: '低于安全库存的染化料', icon: 'alert', component: Inventory, query: { tab: 'alerts' } }
]

function parse(hash) {
  const raw = String(hash || '').replace(/^#/, '') || routes[0].path
  const [path, qs] = raw.split('?')
  const query = {}
  if (qs) {
    qs.split('&').forEach((pair) => {
      if (!pair) return
      const idx = pair.indexOf('=')
      const k = idx < 0 ? pair : pair.slice(0, idx)
      const v = idx < 0 ? '' : pair.slice(idx + 1)
      query[k] = decodeURIComponent(v)
    })
  }
  return { path, query }
}

function toHash(path, query) {
  const qs = Object.keys(query || {})
    .map((k) => `${k}=${encodeURIComponent(query[k])}`)
    .join('&')
  return `#${path}${qs ? '?' + qs : ''}`
}

const initial = parse(window.location.hash)

export const currentLocation = ref(initial)

/** 当前命中的模块（九宫格高亮 + 顶部标题条用） */
export const currentRoute = computed(() => {
  const loc = currentLocation.value
  const matched = routes.find((r) => {
    if (r.path !== loc.path) return false
    return Object.keys(r.query || {}).every((k) => loc.query[k] === r.query[k])
  })
  return matched || routes[0]
})

export function navigate(entry) {
  const target = toHash(entry.path, entry.query)
  if (window.location.hash === target) {
    currentLocation.value = parse(target)
    return
  }
  window.location.hash = target
}

window.addEventListener('hashchange', () => {
  currentLocation.value = parse(window.location.hash)
})

export default { routes, currentLocation, currentRoute, navigate }
