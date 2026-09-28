<template>
  <div class="page" v-if="plan">
    <!-- Header -->
    <header class="dash-header">
      <div class="header-main">
        <div class="kicker">你的训练周</div>
        <h1 class="title">{{ plan.title }}</h1>
        <p class="summary">{{ plan.summary }}</p>
        <div class="meta-row">
          <span class="meta-tag">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
            v{{ plan.version }}
          </span>
          <span class="meta-tag" :class="plan.source">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><circle cx="12" cy="12" r="9"/></svg>
            {{ plan.source === 'chat' ? '聊天调整版' : 'AI 初版' }}
          </span>
          <el-select v-if="planList.length > 1" v-model="currentId" class="version-select" @change="switchPlan" size="small">
            <template #prefix><span style="font-size:11px;color:#94a3b8">版本</span></template>
            <el-option v-for="p in planList" :key="p.id" :value="p.id"
              :label="`#${p.id} v${p.version}`" />
          </el-select>
        </div>
      </div>

      <!-- 统计 -->
      <div class="stats">
        <div class="stat-card">
          <div class="stat-num">{{ plan.days?.length || 0 }}</div>
          <div class="stat-label">训练日</div>
        </div>
        <div class="stat-card">
          <div class="stat-num">{{ totalExercises }}</div>
          <div class="stat-label">动作</div>
        </div>
        <div class="stat-card">
          <div class="stat-num">{{ plan.requirement?.daysPerWeek || '-' }}</div>
          <div class="stat-label">周频</div>
        </div>
        <div class="stat-card">
          <div class="stat-num">{{ plan.requirement?.sessionMinutes || '-' }}<span class="stat-unit">min</span></div>
          <div class="stat-label">单次</div>
        </div>
      </div>
    </header>

    <!-- 周日历时间线 -->
    <section class="week-timeline">
      <div class="day-strip">
        <button v-for="d in plan.days" :key="d.day"
                class="day-pill" :class="{ active: activeDay === String(d.day) }"
                @click="activeDay = String(d.day)">
          <span class="day-num">Day {{ d.day }}</span>
          <span class="day-focus">{{ d.focus }}</span>
        </button>
      </div>
    </section>

    <!-- 当日详情 -->
    <section v-for="d in plan.days" :key="d.day" v-show="activeDay === String(d.day)" class="day-detail">
      <div class="day-header">
        <div class="day-title">
          <span class="day-tag">第 {{ d.day }} 天</span>
          <h2>{{ d.focus }}</h2>
        </div>
        <div class="day-stats">
          <span class="day-stat">
            <strong>{{ d.exercises.length }}</strong> 个动作
          </span>
        </div>
      </div>

      <div class="exercises">
        <article v-for="(ex, i) in d.exercises" :key="i" class="exercise-card">
          <div class="ex-num">{{ String(i + 1).padStart(2, '0') }}</div>
          <div class="ex-body">
            <div class="ex-head">
              <h3 class="ex-name">{{ ex.name }}</h3>
              <div class="ex-metrics">
                <div class="metric">
                  <div class="metric-num">{{ ex.sets }}</div>
                  <div class="metric-label">组</div>
                </div>
                <div class="metric-divider"></div>
                <div class="metric">
                  <div class="metric-num">{{ ex.reps }}</div>
                  <div class="metric-label">次</div>
                </div>
                <div v-if="ex.restSeconds" class="metric-divider"></div>
                <div v-if="ex.restSeconds" class="metric">
                  <div class="metric-num">{{ ex.restSeconds }}<span class="metric-unit">s</span></div>
                  <div class="metric-label">休息</div>
                </div>
              </div>
            </div>
            <p v-if="ex.notes" class="ex-notes">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/></svg>
              {{ ex.notes }}
            </p>
          </div>
        </article>
      </div>
    </section>

    <!-- Tip -->
    <div class="tip-card">
      <div class="tip-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
      </div>
      <div class="tip-content">
        <strong>想让计划更顺手？</strong>
        去 <router-link to="/chat" class="link">AI 教练</router-link> 说一句，例如「周三腿日太累，帮我减一个动作」，新版会自动出现在这里。
      </div>
    </div>
  </div>

  <div v-else-if="loaded" class="empty-state">
    <div class="empty-art">
      <svg viewBox="0 0 200 200" fill="none">
        <defs>
          <linearGradient id="emptyG" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#4ade80"/>
            <stop offset="100%" stop-color="#fb923c"/>
          </linearGradient>
        </defs>
        <rect x="20" y="90" width="30" height="20" rx="4" fill="url(#emptyG)" opacity="0.4"/>
        <rect x="50" y="80" width="100" height="40" rx="6" fill="url(#emptyG)" opacity="0.4"/>
        <rect x="150" y="90" width="30" height="20" rx="4" fill="url(#emptyG)" opacity="0.4"/>
      </svg>
    </div>
    <h3>还没有训练计划</h3>
    <p>填写训练需求，AI 教练 10 秒生成专属一周计划</p>
    <el-button type="primary" size="large" @click="$router.push('/requirement')">立即开始</el-button>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import api from '../api'

const route = useRoute()
const plan = ref(null)
const planList = ref([])
const activeDay = ref('1')
const loaded = ref(false)
const currentId = ref(null)

const totalExercises = computed(() => {
  if (!plan.value?.days) return 0
  return plan.value.days.reduce((sum, d) => sum + (d.exercises?.length || 0), 0)
})

async function load() {
  let planId = route.params.planId
  const { data: list } = await api.get('/plans')
  planList.value = list
  if (!planId) {
    if (!list.length) { loaded.value = true; return }
    planId = list[0].id
  }
  currentId.value = Number(planId)
  const { data } = await api.get(`/plans/${planId}`)
  plan.value = data
  activeDay.value = String(data.days?.[0]?.day ?? 1)
  loaded.value = true
}

function switchPlan(id) {
  window.location.href = `/dashboard/${id}`
}

onMounted(load)
watch(() => route.params.planId, load)
</script>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}

/* ===== Header ===== */
.dash-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 32px;
  padding: 8px 0 28px;
}
.kicker {
  display: inline-block;
  font-size: 11px;
  letter-spacing: 3px;
  font-weight: 600;
  color: #4ade80;
  background: rgba(74, 222, 128, 0.1);
  padding: 4px 10px;
  border-radius: 999px;
  margin-bottom: 12px;
}
.title {
  font-size: 32px;
  font-weight: 800;
  margin: 0 0 8px;
  letter-spacing: -0.5px;
  background: linear-gradient(135deg, #4ade80 0%, #fb923c 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.summary {
  color: #94a3b8;
  font-size: 14px;
  margin: 0 0 14px;
  max-width: 540px;
  line-height: 1.6;
}
.meta-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.meta-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(74, 222, 128, 0.12);
  border: 1px solid rgba(74, 222, 128, 0.25);
  color: #4ade80;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 999px;
}
.meta-tag svg {
  width: 12px;
  height: 12px;
}
.meta-tag.chat {
  background: rgba(251, 146, 60, 0.12);
  border-color: rgba(251, 146, 60, 0.3);
  color: #fb923c;
}
.version-select {
  margin-left: 4px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  min-width: 320px;
}
.stat-card {
  background: rgba(15, 22, 38, 0.7);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 14px;
  padding: 14px 16px;
  text-align: center;
  transition: all 0.2s ease;
}
.stat-card:hover {
  border-color: rgba(74, 222, 128, 0.3);
  transform: translateY(-2px);
}
.stat-num {
  font-size: 22px;
  font-weight: 800;
  color: #4ade80;
  line-height: 1.1;
}
.stat-unit {
  font-size: 12px;
  margin-left: 2px;
  color: #94a3b8;
  font-weight: 500;
}
.stat-label {
  font-size: 11px;
  color: #6b7895;
  margin-top: 4px;
  letter-spacing: 1px;
}

/* ===== 时间线 ===== */
.week-timeline {
  background: rgba(15, 22, 38, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 16px;
  padding: 18px;
  margin-bottom: 24px;
}
.day-strip {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  scrollbar-width: thin;
}
.day-pill {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 10px 16px;
  cursor: pointer;
  transition: all 0.18s ease;
  text-align: left;
  color: #94a3b8;
  min-width: 110px;
}
.day-pill:hover {
  border-color: rgba(74, 222, 128, 0.3);
  color: #e5eaf3;
}
.day-pill.active {
  background: linear-gradient(135deg, rgba(74,222,128,0.18), rgba(251,146,60,0.12));
  border-color: #4ade80;
  color: #e5eaf3;
  box-shadow: 0 4px 16px rgba(74, 222, 128, 0.25);
}
.day-num {
  display: block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.5px;
  color: #4ade80;
}
.day-focus {
  display: block;
  font-size: 13px;
  font-weight: 600;
  margin-top: 2px;
}

/* ===== 当日详情 ===== */
.day-detail {
  margin-bottom: 24px;
}
.day-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.day-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.day-title h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #e5eaf3;
}
.day-tag {
  background: rgba(74, 222, 128, 0.12);
  border: 1px solid rgba(74, 222, 128, 0.3);
  color: #4ade80;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1px;
}
.day-stats {
  color: #94a3b8;
  font-size: 14px;
}
.day-stat strong {
  color: #4ade80;
  font-weight: 700;
  font-size: 18px;
}

/* 动作卡片 */
.exercises {
  display: grid;
  gap: 12px;
}
.exercise-card {
  display: flex;
  background: rgba(15, 22, 38, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 16px;
  padding: 18px 22px;
  transition: all 0.2s ease;
  position: relative;
  overflow: hidden;
}
.exercise-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: linear-gradient(180deg, #4ade80, #fb923c);
  opacity: 0.6;
}
.exercise-card:hover {
  border-color: rgba(74, 222, 128, 0.2);
  transform: translateX(2px);
  background: rgba(15, 22, 38, 0.8);
}
.ex-num {
  font-size: 24px;
  font-weight: 800;
  color: rgba(74, 222, 128, 0.35);
  margin-right: 18px;
  line-height: 1;
  font-family: ui-monospace, monospace;
}
.ex-body {
  flex: 1;
  min-width: 0;
}
.ex-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
}
.ex-name {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #e5eaf3;
}
.ex-metrics {
  display: flex;
  align-items: center;
  gap: 14px;
  background: rgba(255, 255, 255, 0.03);
  padding: 8px 14px;
  border-radius: 12px;
}
.metric {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.metric-num {
  font-size: 18px;
  font-weight: 800;
  color: #4ade80;
  font-family: ui-monospace, monospace;
}
.metric-unit {
  font-size: 11px;
  color: #94a3b8;
  margin-left: 2px;
  font-weight: 500;
}
.metric-label {
  font-size: 11px;
  color: #6b7895;
  letter-spacing: 1px;
}
.metric-divider {
  width: 1px;
  height: 18px;
  background: rgba(255,255,255,0.08);
}
.ex-notes {
  display: flex;
  gap: 8px;
  margin: 12px 0 0;
  padding: 10px 14px;
  background: rgba(251, 146, 60, 0.06);
  border-left: 2px solid rgba(251, 146, 60, 0.4);
  border-radius: 8px;
  color: #cbd5e1;
  font-size: 13px;
  line-height: 1.6;
}
.ex-notes svg {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  color: #fb923c;
  margin-top: 2px;
}

/* Tip */
.tip-card {
  display: flex;
  gap: 16px;
  background: linear-gradient(135deg, rgba(74,222,128,0.06), rgba(251,146,60,0.06));
  border: 1px solid rgba(74, 222, 128, 0.15);
  border-radius: 14px;
  padding: 16px 20px;
  margin-top: 8px;
}
.tip-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  background: rgba(74, 222, 128, 0.15);
  border-radius: 10px;
  color: #4ade80;
}
.tip-icon svg {
  width: 20px;
  height: 20px;
}
.tip-content {
  font-size: 14px;
  color: #cbd5e1;
  line-height: 1.7;
}
.link {
  color: #4ade80;
  font-weight: 600;
  text-decoration: none;
}
.link:hover {
  text-decoration: underline;
}

/* Empty */
.empty-state {
  text-align: center;
  padding: 80px 20px;
}
.empty-art svg {
  width: 160px;
  height: 160px;
  opacity: 0.5;
}
.empty-state h3 {
  color: #e5eaf3;
  margin: 16px 0 8px;
  font-size: 22px;
}
.empty-state p {
  color: #94a3b8;
  margin: 0 0 24px;
}

/* 响应式 */
@media (max-width: 900px) {
  .dash-header {
    flex-direction: column;
  }
  .stats {
    width: 100%;
    grid-template-columns: repeat(4, 1fr);
    min-width: 0;
  }
  .title { font-size: 24px; }
  .day-title h2 { font-size: 18px; }
  .ex-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .ex-metrics {
    width: 100%;
    justify-content: space-between;
  }
}
@media (max-width: 600px) {
  .stats { grid-template-columns: repeat(2, 1fr); }
  .exercise-card { padding: 14px 16px; }
  .ex-num { font-size: 18px; margin-right: 12px; }
}

:deep(.el-select) {
  background: rgba(255,255,255,0.04);
  border-radius: 8px;
}
:deep(.el-select .el-input__wrapper) {
  background: transparent !important;
  box-shadow: 0 0 0 1px rgba(255,255,255,0.08) !important;
}
</style>