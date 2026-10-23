<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { navigation, visibleNavigation } from '@/config/navigation'
import { useSidebar } from '@/composables/useSidebar'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()
const { isCollapsed, isNarrowScreen, toggleCollapsed } = useSidebar()

const expandedGroups = reactive({})
const openFlyoutLabel = ref(null)
const sideRef = ref(null)

function isActive(to) {
  if (to === '/') return route.path === '/'
  return route.path === to || route.path.startsWith(`${to}/`)
}

const isGroupActive = (item) => item.children?.some((child) => isActive(child.to))

// 沒有權限的選單項目不顯示
const visibleItems = computed(() => visibleNavigation(authStore))

// 展開模式：目前頁面所在的群組自動展開
watch(
  () => route.path,
  () => {
    openFlyoutLabel.value = null
    navigation.forEach((item) => {
      if (item.children && isGroupActive(item)) expandedGroups[item.label] = true
    })
  },
  { immediate: true },
)

watch(isCollapsed, () => {
  openFlyoutLabel.value = null
})

// 收合模式：有子選單的模組點擊後從旁邊彈出
async function onGroupClick(item, event) {
  if (!isCollapsed.value) {
    expandedGroups[item.label] = !expandedGroups[item.label]
    return
  }
  // 先記住所在的項目：await 之後 event.currentTarget 會變成 null
  const navItem = event.currentTarget.parentElement
  const isOpening = openFlyoutLabel.value !== item.label
  openFlyoutLabel.value = isOpening ? item.label : null
  if (isOpening) {
    await nextTick()
    navItem.querySelector('.flyout a')?.focus()
  }
}

function closeFlyout(returnFocusTo) {
  openFlyoutLabel.value = null
  returnFocusTo?.focus()
}

function onDocumentClick(event) {
  if (openFlyoutLabel.value && sideRef.value && !sideRef.value.contains(event.target)) closeFlyout()
}

function onDocumentKeydown(event) {
  if (event.key !== 'Escape' || !openFlyoutLabel.value) return
  const trigger = sideRef.value?.querySelector(`[data-group="${openFlyoutLabel.value}"]`)
  closeFlyout(trigger)
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onDocumentKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onDocumentKeydown)
})
</script>

<template>
  <aside ref="sideRef" class="side glass" :class="{ collapsed: isCollapsed }">
    <!-- 用 custom 避免 Logo 在首頁也被標成 aria-current -->
    <RouterLink v-slot="{ href, navigate }" to="/" custom>
      <a :href="href" class="logo" aria-label="THERMAL ROLL，回到我的工作台" @click="navigate">
        <span class="logo-mark mono" aria-hidden="true">TR</span>
        <span class="logo-text" aria-hidden="true">THERMAL<em>ROLL</em></span>
      </a>
    </RouterLink>

    <h6 id="side-title" :class="{ 'sr-only': isCollapsed }">選單</h6>
    <nav aria-labelledby="side-title" class="nav-scroll">
      <ul class="nav">
        <li v-for="item in visibleItems" :key="item.label" class="nav-item">
          <RouterLink
            v-if="!item.children"
            :to="item.to"
            class="link"
            :class="{ on: isActive(item.to) }"
            :data-tip="item.label"
            :aria-current="isActive(item.to) ? 'page' : undefined"
          >
            <AppIcon :name="item.icon" :size="isCollapsed ? 20 : 17" />
            <span class="label">{{ item.label }}</span>
          </RouterLink>

          <template v-else>
            <button
              type="button"
              class="link group"
              :class="{
                'group-on': isGroupActive(item),
                'flyout-open': openFlyoutLabel === item.label,
              }"
              :data-group="item.label"
              :data-tip="item.label"
              :aria-expanded="
                isCollapsed ? openFlyoutLabel === item.label : !!expandedGroups[item.label]
              "
              @click="onGroupClick(item, $event)"
            >
              <AppIcon :name="item.icon" :size="isCollapsed ? 20 : 17" />
              <span class="label">{{ item.label }}</span>
              <AppIcon
                name="chevron"
                :size="14"
                class="chev"
                :class="{ open: expandedGroups[item.label] }"
              />
            </button>

            <!-- 展開模式：子選單在下方 -->
            <ul v-if="!isCollapsed" v-show="expandedGroups[item.label]" class="sub-nav">
              <li v-for="child in item.children" :key="child.to">
                <RouterLink
                  :to="child.to"
                  class="sub-link"
                  :class="{ on: isActive(child.to) }"
                  :aria-current="isActive(child.to) ? 'page' : undefined"
                >
                  {{ child.label }}
                </RouterLink>
              </li>
            </ul>

            <!-- 收合模式：子選單從旁邊彈出 -->
            <div
              v-else-if="openFlyoutLabel === item.label"
              class="flyout"
              role="group"
              :aria-label="item.label"
            >
              <div class="flyout-title">{{ item.label }}</div>
              <RouterLink
                v-for="child in item.children"
                :key="child.to"
                :to="child.to"
                class="flyout-link"
                :class="{ on: isActive(child.to) }"
                :aria-current="isActive(child.to) ? 'page' : undefined"
              >
                {{ child.label }}
              </RouterLink>
            </div>
          </template>
        </li>
      </ul>
    </nav>

    <div class="side-foot">
      <button
        v-if="!isNarrowScreen"
        type="button"
        class="toggle"
        :aria-label="isCollapsed ? '展開側邊欄' : '收合側邊欄'"
        :aria-expanded="!isCollapsed"
        :data-tip="isCollapsed ? '展開側邊欄' : undefined"
        @click="toggleCollapsed"
      >
        <AppIcon :name="isCollapsed ? 'chevronRight' : 'chevronLeft'" :size="16" />
        <span class="label">收合選單</span>
      </button>
      <div class="status">
        <span class="status-online"
          ><i class="dot" aria-hidden="true"></i><span class="label">連線正常</span></span
        >
        <span class="label">已連線後端</span>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.side {
  display: flex;
  flex-direction: column;
  padding: 0 0 12px;
  overflow: hidden;
}

/* Logo */
.logo {
  display: flex;
  align-items: center;
  gap: 9px;
  flex: none;
  height: var(--bar-h);
  padding: 0 18px;
  margin-bottom: 10px;
  border-bottom: 1px solid var(--line-soft);
  font-weight: 800;
  font-size: 15px;
  letter-spacing: 0.3px;
  color: var(--ink);
}

.logo-mark {
  display: grid;
  place-items: center;
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: var(--r-xs);
  background: var(--accent-strong);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0;
}

.logo-text em {
  font-style: normal;
  color: var(--accent);
  margin-left: 6px;
}

h6 {
  margin: 0;
  padding: 0 18px 8px;
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 1.5px;
  color: var(--ink-3);
}

.nav-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.nav,
.sub-nav {
  list-style: none;
  margin: 0;
  padding: 0;
}

.nav-item {
  position: relative;
}

/* 選單項目（展開） */
.link {
  position: relative;
  display: grid;
  grid-template-columns: 30px 1fr auto;
  align-items: center;
  width: calc(100% - 20px);
  margin: 2px 10px;
  padding: 10px 14px;
  border: 0;
  border-radius: var(--r-lg);
  background: transparent;
  color: var(--ink-2);
  font-size: 14px;
  text-align: left;
}

.link .icon {
  color: var(--ink-3);
}

.link:hover,
.link.flyout-open {
  background: rgba(0, 98, 132, 0.08);
}

.link.on {
  background: linear-gradient(135deg, var(--brand-navy), var(--brand-navy-2));
  color: #fff;
  font-weight: 600;
  box-shadow: var(--shadow-active);
}

.link.on .icon {
  color: #fff;
}

.group-on {
  color: var(--ink);
  font-weight: 600;
}

.chev {
  transition: transform 0.15s;
}

.chev.open {
  transform: rotate(180deg);
}

.badge {
  font-size: 11px;
  font-weight: 700;
  padding: 0 6px;
  border-radius: var(--r-xs);
  background: var(--success-soft);
  color: var(--accent-strong);
}

.sub-nav {
  margin: 2px 0 6px;
}

.sub-link {
  display: block;
  margin: 1px 10px 1px 40px;
  padding: 7px 12px;
  border-radius: var(--r-md);
  font-size: 13.5px;
  color: var(--ink-2);
}

.sub-link:hover {
  background: rgba(0, 98, 132, 0.06);
}

.sub-link.on,
.flyout-link.on {
  background: linear-gradient(135deg, var(--brand-navy), var(--brand-navy-2));
  color: #fff;
  font-weight: 600;
  box-shadow: var(--shadow-active);
}

/* 底部：收合按鈕與連線狀態 */
.side-foot {
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 8px;
  padding: 12px 18px 0;
  border-top: 1px solid var(--line-soft);
}

.toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: rgba(255, 255, 255, 0.85);
  color: var(--ink-2);
  font-size: 12px;
}

.toggle .icon {
  color: var(--accent);
}

.toggle:hover {
  border-color: var(--accent);
}

.status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: var(--ink-3);
}

.status-online {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--ink);
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #5dac81;
}

/* ===== 收合模式 ===== */
.collapsed {
  overflow: visible;
}

.collapsed .logo {
  justify-content: center;
  padding: 0;
}

.collapsed .logo-mark {
  width: 48px;
  height: 44px;
  border-radius: var(--r-lg);
  font-size: 14px;
}

.collapsed .label,
.collapsed .logo-text,
.collapsed .chev {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

.collapsed .nav-scroll {
  overflow: visible;
}

.collapsed .link {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 44px;
  margin: 4px auto;
  padding: 0;
}

/* 收合時看不到子選單，所以目前所在的模組用底色標示 */
.collapsed .group-on {
  background: rgba(0, 98, 132, 0.12);
}

.collapsed .group-on .icon {
  color: var(--accent);
}

.collapsed .badge {
  position: absolute;
  top: 3px;
  right: 2px;
  min-width: 18px;
  padding: 0 4px;
  line-height: 16px;
  border-radius: 8px;
  font-size: 9px;
  text-align: center;
  background: var(--warning-strong);
  color: #fff;
}

/* 收合時的文字提示：滑鼠移過去或鍵盤聚焦時出現 */
.collapsed [data-tip]:hover::after,
.collapsed [data-tip]:focus-visible::after {
  content: attr(data-tip);
  position: absolute;
  left: calc(100% + 12px);
  top: 50%;
  transform: translateY(-50%);
  z-index: 60;
  padding: 6px 10px;
  border-radius: var(--r-sm);
  background: var(--ink);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  pointer-events: none;
}

.collapsed .flyout-open::after,
.collapsed .flyout-open:hover::after {
  display: none;
}

.flyout {
  position: absolute;
  left: calc(100% - 4px);
  top: 0;
  z-index: 60;
  width: 190px;
  padding: 8px;
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--shadow-pop);
}

.flyout-title {
  padding: 6px 10px 4px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--ink-3);
}

.flyout-link {
  display: block;
  padding: 8px 10px;
  border-radius: var(--r-sm);
  font-size: 14px;
  color: var(--ink-2);
}

.flyout-link:hover {
  background: rgba(0, 98, 132, 0.06);
}

.collapsed .side-foot {
  align-items: center;
  padding: 12px 0 0;
}

.collapsed .toggle {
  position: relative;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
}

.collapsed .status {
  justify-content: center;
}

@media (prefers-reduced-motion: reduce) {
  .chev {
    transition: none;
  }
}
</style>
