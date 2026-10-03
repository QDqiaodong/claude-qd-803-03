<template>
  <div class="launcher">
    <!-- 顶部标题条：只放模块名 + 右上角菜单按钮，没有侧栏 -->
    <header class="launcher__bar">
      <div class="launcher__title">
        <span class="launcher__brand">DYEING MILL</span>
        <span class="launcher__module">{{ currentRoute.name }}</span>
        <span class="launcher__module-desc">{{ currentRoute.desc }}</span>
      </div>
      <button class="launcher__menu-btn" @click="menuOpen = true">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round">
          <path d="M4 7h16" />
          <path d="M4 12h16" />
          <path d="M4 17h16" />
        </svg>
        <span>模块导航</span>
      </button>
    </header>

    <!-- 内容区：选定模块后全屏铺开 -->
    <div class="launcher__content">
      <component :is="currentRoute.component" :key="routeKey" :params="currentLocation.query" />
    </div>

    <!-- 九宫格启动台 -->
    <div v-if="menuOpen" class="grid-mask" @click.self="menuOpen = false">
      <div class="grid-panel">
        <div class="grid-panel__head">
          <span class="grid-panel__title">印染车间 · 模块启动台</span>
          <span class="grid-panel__hint">点击格子切换模块（{{ routes.length }} 个入口 / 7 个模块）</span>
        </div>
        <div class="grid-9">
          <div
            v-for="(entry, index) in routes"
            :key="index"
            :class="['grid-cell', isActive(entry) ? 'is-active' : '']"
            @click="pick(entry)"
          >
            <svg class="grid-cell__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <path v-for="(d, i) in ICONS[entry.icon]" :key="i" :d="d" />
            </svg>
            <span class="grid-cell__name">{{ entry.name }}</span>
            <span class="grid-cell__desc">{{ entry.desc }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { currentLocation, currentRoute, navigate, routes } from './router'

const menuOpen = ref(false)

const ICONS = {
  schedule: ['M3 5h18', 'M3 5v14h18V5', 'M3 10h18', 'M7 5v14', 'M12 14l2-4 2 2 2-5'],
  plans: ['M4 4h16v6H4z', 'M4 14h16v6H4z', 'M8 7h8', 'M8 17h5'],
  matrix: ['M4 4h7v7H4z', 'M13 4h7v7h-7z', 'M4 13h7v7H4z', 'M13 13h7v7h-7z'],
  vat: ['M5 6h14', 'M6 6v11a3 3 0 003 3h6a3 3 0 003-3V6', 'M5 11h14', 'M9 15h6'],
  recipe: ['M4 5h16v14H4z', 'M8 9h8', 'M8 13h8', 'M8 17h5'],
  curve: ['M3 20h18', 'M3 20V4', 'M5 17l4-8 3 5 5-10'],
  cart: ['M5 7h14v11H5z', 'M8 7V4h8v3', 'M5 12h14'],
  execution: ['M5 4h14v16H5z', 'M8 8h8', 'M8 12h8', 'M8 16h5', 'M15 15l2 2 4-4'],
  stock: ['M4 7l8-4 8 4v10l-8 4-8-4z', 'M4 7l8 4 8-4', 'M12 11v10'],
  running: ['M10 13V5a2 2 0 114 0v8a4 4 0 11-4 0z', 'M12 9v6'],
  stat: ['M3 20h18', 'M6 20V12', 'M11 20V6', 'M16 20v-6'],
  dye: ['M12 3s6 6.5 6 10.5a6 6 0 11-12 0C6 9.5 12 3 12 3z'],
  alert: ['M12 4l9 16H3z', 'M12 10v4', 'M12 17h.01']
}

const routeKey = computed(() => currentRoute.value.path + JSON.stringify(currentLocation.value.query))

function isActive(entry) {
  const loc = currentLocation.value
  if (entry.path !== loc.path) return false
  return Object.keys(entry.query || {}).every((k) => loc.query[k] === entry.query[k])
}

function pick(entry) {
  navigate(entry)
  menuOpen.value = false
}

onMounted(() => {
  if (!window.location.hash) {
    navigate(routes[0])
  }
})
</script>
