<script setup>
import TopBar from './TopBar.vue'
import SideNav from './SideNav.vue'
import { useSidebar } from '@/composables/useSidebar'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const { isCollapsed } = useSidebar()
</script>

<template>
  <div class="blobs" aria-hidden="true">
    <i class="b1"></i><i class="b2"></i><i class="b3"></i><i class="b4"></i>
  </div>

  <a class="skip" href="#main">跳到主要內容</a>

  <!-- 兩區版面：左側（Logo＋選單＋狀態），右側（工作區）。
       右上角工具不再是一整條頂部列，而是疊在工作區右上角，和頁面標題同一排。 -->
  <div class="app" :class="{ 'side-collapsed': isCollapsed }">
    <SideNav class="area-side" />
    <TopBar class="area-bar" />
    <main id="main" class="area-main" tabindex="-1">
      <!-- refreshKey 改變時重新掛載頁面，頁面會重新呼叫自己的 API -->
      <RouterView :key="appStore.refreshKey" />
    </main>
  </div>
</template>

<style scoped>
.blobs i {
  position: fixed;
  border-radius: 50%;
  filter: blur(90px);
  z-index: 0;
  pointer-events: none;
}

.b1 {
  width: 760px;
  height: 760px;
  left: -240px;
  top: -260px;
  background: var(--brand-teal);
  opacity: 0.38;
}

.b2 {
  width: 780px;
  height: 780px;
  right: -260px;
  top: 180px;
  background: var(--brand-ocean);
  opacity: 0.26;
}

.b3 {
  width: 600px;
  height: 600px;
  left: 40%;
  bottom: -320px;
  background: var(--brand-cyan);
  opacity: 0.22;
}

.b4 {
  width: 420px;
  height: 420px;
  left: 28%;
  top: -120px;
  background: var(--brand-sage);
  opacity: 0.28;
}

.skip {
  position: absolute;
  left: 16px;
  top: -60px;
  z-index: 100;
  padding: 8px 14px;
  border-radius: var(--r-md);
  background: var(--accent-strong);
  color: #fff;
}

.skip:focus {
  top: 16px;
}

.app {
  --current-side-w: var(--side-w);
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: var(--current-side-w) minmax(0, 1fr);
  grid-template-areas: 'side main';
  gap: var(--gap);
  min-height: calc(100vh - 28px);
  margin: 14px;
  transition: grid-template-columns 0.2s ease;
}

.app.side-collapsed {
  --current-side-w: var(--side-w-collapsed);
}

.area-bar {
  grid-area: main;
  align-self: start;
  justify-self: end;
  position: relative;
  z-index: 30;
}

.area-side {
  grid-area: side;
  align-self: start;
  position: sticky;
  top: 14px;
  z-index: 40;
  height: calc(100vh - 28px);
}

.area-main {
  grid-area: main;
  min-width: 0;
}

.area-main:focus {
  outline: none;
}

@media (prefers-reduced-motion: reduce) {
  .app {
    transition: none;
  }
}

@media (max-width: 900px) {
  .app {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto auto 1fr;
    grid-template-areas: 'bar' 'side' 'main';
    --tools-w: 0px;
  }

  .area-bar {
    grid-area: bar;
  }

  .area-side {
    position: static;
    height: auto;
  }
}
</style>
