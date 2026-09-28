<template>
  <div class="app-shell">
    <!-- 背景装饰层 -->
    <div class="bg-deco">
      <div class="orb orb-green"></div>
      <div class="orb orb-orange"></div>
      <div class="grid-overlay"></div>
    </div>

    <!-- 移动端顶栏 -->
    <header class="mobile-topbar">
      <button class="hamburger" @click="collapsed = false" aria-label="打开菜单">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M3 6h18M3 12h18M3 18h18"/>
        </svg>
      </button>
      <div class="mobile-brand">🏋️ FitPilot</div>
    </header>

    <!-- 侧栏：桌面常驻 / 移动端抽屉 -->
    <aside class="sidebar" :class="{ collapsed, 'mobile-open': mobileOpen }">
      <div class="brand">
        <div class="brand-logo">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M6.5 6.5h11M6.5 17.5h11M3 9v6M21 9v6M6.5 9v6M17.5 9v6"/>
          </svg>
        </div>
        <div class="brand-text">
          <div class="brand-title">FitPilot</div>
          <div class="brand-sub">AI 健身领航</div>
        </div>
      </div>

      <nav class="nav">
        <router-link v-for="(item, idx) in navItems" :key="idx" :to="item.path"
                     class="nav-item" :class="{ active: $route.path === item.path }">
          <span class="nav-icon" v-html="item.icon"></span>
          <span class="nav-label">{{ item.label }}</span>
          <span class="nav-bar"></span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="footer-card">
          <div class="footer-title">Powered by</div>
          <div class="footer-tech">豆包 Seed 2.1 Pro</div>
        </div>
      </div>

      <button class="close-btn mobile-only" @click="mobileOpen = false" aria-label="关闭菜单">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M18 6L6 18M6 6l12 12"/>
        </svg>
      </button>
    </aside>

    <!-- 抽屉遮罩 -->
    <div v-if="mobileOpen" class="drawer-mask" @click="mobileOpen = false"></div>

    <!-- 主区 -->
    <main class="content">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const mobileOpen = ref(false)
const collapsed = ref(false)

const navItems = [
  { path: '/requirement', label: '训练需求', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>' },
  { path: '/dashboard', label: '计划仪表盘', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="9"/><rect x="14" y="3" width="7" height="5"/><rect x="14" y="12" width="7" height="9"/><rect x="3" y="16" width="7" height="5"/></svg>' },
  { path: '/chat', label: 'AI 教练', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>' },
  { path: '/pose', label: '姿势纠正', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>' }
]
</script>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
  background: #06090f;
  color: #e5eaf3;
  position: relative;
  overflow: hidden;
}

/* ===== 背景装饰 ===== */
.bg-deco {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}
.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.35;
}
.orb-green {
  width: 520px;
  height: 520px;
  background: radial-gradient(circle, #4ade80 0%, transparent 70%);
  top: -180px;
  left: -120px;
  animation: float 18s ease-in-out infinite;
}
.orb-orange {
  width: 480px;
  height: 480px;
  background: radial-gradient(circle, #fb923c 0%, transparent 70%);
  bottom: -160px;
  right: -100px;
  animation: float 22s ease-in-out infinite reverse;
}
.grid-overlay {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.025) 1px, transparent 1px);
  background-size: 48px 48px;
}
@keyframes float {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(40px, -40px); }
}

/* ===== 移动端顶栏 ===== */
.mobile-topbar {
  display: none;
  position: sticky;
  top: 0;
  z-index: 50;
  height: 56px;
  padding: 0 16px;
  align-items: center;
  gap: 14px;
  background: rgba(10, 15, 26, 0.85);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.hamburger {
  background: transparent;
  border: none;
  color: #e5eaf3;
  cursor: pointer;
  padding: 6px;
  display: flex;
}
.mobile-brand {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 1px;
}

/* ===== 侧栏 ===== */
.sidebar {
  position: relative;
  z-index: 2;
  width: 240px;
  flex-shrink: 0;
  background: rgba(10, 15, 26, 0.75);
  backdrop-filter: blur(20px);
  border-right: 1px solid rgba(255,255,255,0.06);
  padding: 28px 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 28px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 6px 8px;
}
.brand-logo {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: linear-gradient(135deg, #4ade80 0%, #22c55e 100%);
  display: grid;
  place-items: center;
  color: #06090f;
  box-shadow: 0 6px 20px rgba(74, 222, 128, 0.35);
}
.brand-logo svg {
  width: 22px;
  height: 22px;
}
.brand-title {
  font-size: 19px;
  font-weight: 800;
  letter-spacing: 1.5px;
  background: linear-gradient(135deg, #4ade80 0%, #fb923c 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.brand-sub {
  font-size: 11px;
  color: #6b7895;
  letter-spacing: 1px;
  margin-top: 2px;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}
.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  color: #94a3b8;
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.18s ease;
}
.nav-item:hover {
  color: #e5eaf3;
  background: rgba(255,255,255,0.04);
}
.nav-item.active {
  color: #e5eaf3;
  background: linear-gradient(135deg, rgba(74,222,128,0.12) 0%, rgba(251,146,60,0.08) 100%);
  border: 1px solid rgba(74,222,128,0.2);
}
.nav-icon {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
}
.nav-icon :deep(svg) {
  width: 18px;
  height: 18px;
}
.nav-bar {
  position: absolute;
  left: -18px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 0;
  border-radius: 0 3px 3px 0;
  background: linear-gradient(180deg, #4ade80, #fb923c);
  transition: height 0.2s ease;
}
.nav-item.active .nav-bar {
  height: 24px;
}

.sidebar-footer {
  margin-top: auto;
}
.footer-card {
  background: linear-gradient(135deg, rgba(74,222,128,0.08), rgba(251,146,60,0.08));
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 12px;
  padding: 14px;
}
.footer-title {
  font-size: 11px;
  color: #6b7895;
  letter-spacing: 1.5px;
  text-transform: uppercase;
}
.footer-tech {
  font-size: 14px;
  color: #e5eaf3;
  margin-top: 6px;
  font-weight: 600;
}

.close-btn {
  display: none;
  position: absolute;
  top: 14px;
  right: 14px;
  background: rgba(255,255,255,0.05);
  border: none;
  color: #e5eaf3;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  cursor: pointer;
  align-items: center;
  justify-content: center;
}
.mobile-only {
  display: none;
}

/* ===== 主区 ===== */
.content {
  flex: 1;
  min-width: 0;
  position: relative;
  z-index: 1;
  overflow-y: auto;
  padding: 40px 48px;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* ===== 响应式 ===== */
.drawer-mask {
  display: none;
}

@media (max-width: 960px) {
  .mobile-topbar {
    display: flex;
  }
  .sidebar {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    width: 280px;
    transform: translateX(-100%);
    transition: transform 0.25s ease;
    z-index: 100;
    background: rgba(10, 15, 26, 0.98);
  }
  .sidebar.mobile-open {
    transform: translateX(0);
  }
  .close-btn.mobile-only {
    display: flex;
  }
  .drawer-mask {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0,0,0,0.6);
    z-index: 99;
    backdrop-filter: blur(4px);
  }
  .content {
    padding: 24px 20px;
  }
}

@media (min-width: 1400px) {
  .content {
    padding: 48px 64px;
  }
  .sidebar {
    width: 260px;
  }
}
</style>