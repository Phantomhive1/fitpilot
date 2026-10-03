<template>
  <div class="page">
    <!-- Hero -->
    <header class="hero">
      <div class="hero-text">
        <div class="kicker">STEP 01</div>
        <h1 class="hero-title">开启你的<br/><span class="grad">训练旅程</span></h1>
        <p class="hero-sub">告诉教练你的目标、时间和可用器械，10 秒生成专属一周计划</p>
      </div>
      <div class="hero-art" aria-hidden="true">
        <svg viewBox="0 0 200 200" fill="none">
          <defs>
            <linearGradient id="g1" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stop-color="#4ade80"/>
              <stop offset="100%" stop-color="#22c55e"/>
            </linearGradient>
            <linearGradient id="g2" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stop-color="#fb923c"/>
              <stop offset="100%" stop-color="#f97316"/>
            </linearGradient>
          </defs>
          <!-- 哑铃 -->
          <rect x="20" y="90" width="30" height="20" rx="4" fill="url(#g1)" opacity="0.9"/>
          <rect x="50" y="80" width="100" height="40" rx="6" fill="url(#g1)"/>
          <rect x="150" y="90" width="30" height="20" rx="4" fill="url(#g1)" opacity="0.9"/>
          <!-- 闪电 -->
          <path d="M110 30 L90 90 L115 90 L100 145 L150 75 L120 75 Z" fill="url(#g2)" opacity="0.85"/>
        </svg>
      </div>
    </header>

    <!-- 表单卡片 -->
    <div class="form-card">
      <!-- 1. 目标 -->
      <section class="section">
        <div class="section-head">
          <span class="section-num">01</span>
          <div>
            <h3 class="section-title">训练目标</h3>
            <p class="section-desc">选择最想达成的一项</p>
          </div>
        </div>
        <div class="goal-grid">
          <button v-for="g in goals" :key="g.value"
                  type="button" class="goal-card"
                  :class="{ active: form.goal === g.value }"
                  @click="form.goal = g.value">
            <span class="goal-icon" v-html="g.icon"></span>
            <span class="goal-label">{{ g.label }}</span>
          </button>
        </div>
      </section>

      <!-- 2. 水平 -->
      <section class="section">
        <div class="section-head">
          <span class="section-num">02</span>
          <div>
            <h3 class="section-title">训练水平</h3>
            <p class="section-desc">影响动作难度与组数建议</p>
          </div>
        </div>
        <div class="level-pills">
          <button v-for="l in levels" :key="l.value" type="button"
                  class="level-pill" :class="{ active: form.level === l.value }"
                  @click="form.level = l.value">
            <span class="level-name">{{ l.label }}</span>
            <span class="level-meta">{{ l.meta }}</span>
          </button>
        </div>
      </section>

      <!-- 3. 器械 -->
      <section class="section">
        <div class="section-head">
          <span class="section-num">03</span>
          <div>
            <h3 class="section-title">可用器械</h3>
            <p class="section-desc">可多选，AI 会优先用这些器械</p>
          </div>
        </div>
        <div class="chip-grid">
          <button v-for="e in equipments" :key="e" type="button"
                  class="chip" :class="{ active: form.equipment.includes(e) }"
                  @click="toggleEquipment(e)">
            <span v-if="form.equipment.includes(e)" class="chip-check">✓</span>
            {{ e }}
          </button>
        </div>
      </section>

      <!-- 4. 伤病 -->
      <section class="section">
        <div class="section-head">
          <span class="section-num">04</span>
          <div>
            <h3 class="section-title">伤病 / 受限部位</h3>
            <p class="section-desc">没有请填「无」，AI 会主动规避</p>
          </div>
        </div>
        <el-input
          v-model="form.injuries"
          type="textarea" :rows="2"
          placeholder="如：左膝不适、腰椎间盘突出、肩袖曾受伤……" />
      </section>

      <!-- 5. 频率 + 时长 -->
      <section class="section">
        <div class="section-head">
          <span class="section-num">05</span>
          <div>
            <h3 class="section-title">训练节奏</h3>
            <p class="section-desc">每周训练天数与单次时长</p>
          </div>
        </div>
        <div class="rhythm-row">
          <div class="rhythm-block">
            <div class="rhythm-label">每周训练天数</div>
            <el-input-number v-model="form.daysPerWeek" :min="1" :max="7" size="large" />
          </div>
          <div class="rhythm-block">
            <div class="rhythm-label">单次时长（分钟）</div>
            <el-input-number v-model="form.sessionMinutes" :min="20" :max="180" :step="10" size="large" />
          </div>
        </div>
      </section>

      <!-- 6. 昵称 + 补充 -->
      <section class="section">
        <div class="section-head">
          <span class="section-num">06</span>
          <div>
            <h3 class="section-title">昵称 / 补充说明</h3>
            <p class="section-desc">选填，让教练更懂你</p>
          </div>
        </div>
        <el-input v-model="form.nickname" placeholder="昵称（选填）" style="margin-bottom: 12px" />
        <el-input v-model="form.notes" type="textarea" :rows="2"
                  placeholder="如：偏好徒手、讨厌跳绳、最近睡眠差……" />
      </section>

      <!-- 提交 -->
      <div class="submit-bar">
        <div class="submit-tip">生成后可在「AI 教练」中随时对话调整计划</div>
        <button class="gantt-btn" :disabled="generating" @click="submit">
          <span v-if="!generating" class="btn-content">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>
            生成训练计划
          </span>
          <span v-else class="btn-content">
            <span class="spinner"></span>
            AI 教练编排中…
          </span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'

const router = useRouter()
const generating = ref(false)

const goals = [
  { value: '增肌', label: '增肌', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6.5 6.5h11M6.5 17.5h11M3 9v6M21 9v6"/></svg>' },
  { value: '减脂', label: '减脂', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a10 10 0 1 0 10 10"/><path d="M22 4L12 14l-3-3"/></svg>' },
  { value: '力量', label: '力量', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>' },
  { value: '体能', label: '体能', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>' },
  { value: '塑形', label: '塑形', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>' },
  { value: '健康习惯', label: '健康', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>' }
]

const levels = [
  { value: '初级', label: '初级', meta: '0 - 1 年' },
  { value: '中级', label: '中级', meta: '1 - 3 年' },
  { value: '高级', label: '高级', meta: '3 年以上' }
]

const equipments = ['杠铃 / 哑铃', '固定器械', '壶铃 / 弹力带', '单杠 / 双杠', '纯自重', '跑步机 / 划船机等有氧']

const form = reactive({
  nickname: '',
  goal: '',
  level: '初级',
  equipment: [],
  injuries: '',
  daysPerWeek: 4,
  sessionMinutes: 60,
  notes: ''
})

function toggleEquipment(e) {
  const i = form.equipment.indexOf(e)
  if (i >= 0) form.equipment.splice(i, 1)
  else form.equipment.push(e)
}

async function submit() {
  if (!form.goal) { ElMessage.warning('请选择训练目标'); return }
  if (!form.equipment.length) { ElMessage.warning('请至少选择一种可用器械'); return }
  generating.value = true
  try {
    const payload = {
      ...form,
      equipment: form.equipment.join('、'),
      injuries: form.injuries.trim() || '无'
    }
    const { data: req } = await api.post('/requirements', payload)
    const { data: plan } = await api.post(`/requirements/${req.id}/plan`)
    ElMessage.success('计划已生成！')
    router.push(`/dashboard/${plan.id}`)
  } catch (e) {
    ElMessage.error('生成失败：' + (e.response?.data?.message || e.message))
  } finally {
    generating.value = false
  }
}
</script>

<style scoped>
.page {
  max-width: 920px;
  margin: 0 auto;
}

/* ===== Hero ===== */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  padding: 8px 0 32px;
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
  margin-bottom: 14px;
}
.hero-title {
  font-size: 40px;
  line-height: 1.15;
  margin: 0 0 12px;
  font-weight: 800;
  letter-spacing: -1px;
  color: var(--text-1);
}
.grad {
  background: linear-gradient(135deg, #4ade80 0%, #fb923c 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.hero-sub {
  color: var(--text-2);
  font-size: 15px;
  margin: 0;
  max-width: 480px;
}
.hero-art svg {
  width: 180px;
  height: 180px;
}

/* ===== 表单卡片 ===== */
.form-card {
  background: var(--bg-panel);
  backdrop-filter: blur(20px);
  border: 1px solid var(--border-soft);
  border-radius: 20px;
  padding: 36px 40px;
}
.section {
  padding: 22px 0;
  border-bottom: 1px solid var(--bg-input);
}
.section:first-of-type { padding-top: 8px; }
.section:last-of-type { border-bottom: none; }
.section-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}
.section-num {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(74,222,128,0.15), rgba(251,146,60,0.15));
  border: 1px solid rgba(74,222,128,0.2);
  font-size: 13px;
  font-weight: 700;
  color: #4ade80;
}
.section-title {
  margin: 0;
  font-size: 16px;
  color: var(--text-1);
  font-weight: 700;
}
.section-desc {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--text-3);
}

/* 目标卡片 */
.goal-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}
.goal-card {
  background: var(--bg-raise);
  border: 1px solid var(--border-soft);
  border-radius: 14px;
  padding: 18px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: all 0.18s ease;
  color: var(--text-2);
}
.goal-card:hover {
  border-color: rgba(74, 222, 128, 0.3);
  transform: translateY(-2px);
  background: var(--bg-input);
}
.goal-card.active {
  background: linear-gradient(135deg, rgba(74,222,128,0.18), rgba(251,146,60,0.12));
  border-color: rgba(74,222,128,0.5);
  color: var(--text-1);
  box-shadow: 0 8px 24px rgba(74, 222, 128, 0.18);
}
.goal-icon :deep(svg) {
  width: 26px;
  height: 26px;
  color: #4ade80;
}
.goal-label {
  font-size: 14px;
  font-weight: 600;
}

/* 水平 pills */
.level-pills {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.level-pill {
  background: var(--bg-raise);
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.18s ease;
  text-align: center;
  color: var(--text-2);
}
.level-pill:hover {
  border-color: rgba(74, 222, 128, 0.3);
}
.level-pill.active {
  background: linear-gradient(135deg, #4ade80 0%, #22c55e 100%);
  border-color: transparent;
  color: #06090f;
  box-shadow: 0 6px 20px rgba(74, 222, 128, 0.3);
}
.level-name {
  display: block;
  font-size: 16px;
  font-weight: 700;
}
.level-meta {
  display: block;
  font-size: 12px;
  margin-top: 4px;
  opacity: 0.7;
}

/* 器械 chips */
.chip-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.chip {
  background: var(--bg-raise);
  border: 1px solid var(--border-strong);
  border-radius: 999px;
  padding: 8px 16px;
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  transition: all 0.15s ease;
  display: flex;
  align-items: center;
  gap: 6px;
}
.chip:hover {
  border-color: rgba(74, 222, 128, 0.3);
  color: var(--text-1);
}
.chip.active {
  background: linear-gradient(135deg, rgba(74,222,128,0.2), rgba(34,197,94,0.2));
  border-color: #4ade80;
  color: #4ade80;
}
.chip-check {
  font-weight: 700;
  font-size: 12px;
}

/* 节奏 */
.rhythm-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}
.rhythm-block {
  background: var(--bg-raise);
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  padding: 16px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.rhythm-label {
  font-size: 13px;
  color: var(--text-2);
}

/* 提交 */
.submit-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 28px;
  margin-top: 8px;
  border-top: 1px solid var(--border-soft);
  gap: 18px;
  flex-wrap: wrap;
}
.submit-tip {
  font-size: 13px;
  color: var(--text-3);
}
.gantt-btn {
  background: linear-gradient(135deg, #4ade80 0%, #22c55e 100%);
  color: #06090f;
  border: none;
  border-radius: 12px;
  padding: 14px 32px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 8px 24px rgba(74, 222, 128, 0.3);
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.gantt-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(74, 222, 128, 0.4);
}
.gantt-btn:disabled {
  opacity: 0.7;
  cursor: wait;
}
.btn-content {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ===== 响应式 ===== */
@media (max-width: 720px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .hero-title { font-size: 30px; }
  .hero-art svg { width: 120px; height: 120px; }
  .form-card { padding: 24px 20px; }
  .level-pills { grid-template-columns: 1fr; }
  .rhythm-row { grid-template-columns: 1fr; }
  .submit-bar { flex-direction: column; align-items: stretch; }
  .gantt-btn { width: 100%; justify-content: center; }
}

/* 覆盖 Element Plus 主题色 */
:deep(.el-textarea__inner),
:deep(.el-input__wrapper) {
  background: var(--bg-input) !important;
  box-shadow: 0 0 0 1px var(--border-strong) !important;
}
:deep(.el-textarea__inner:focus),
:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #4ade80 !important;
}
:deep(.el-input__inner),
:deep(.el-textarea__inner) {
  color: var(--text-1) !important;
}
:deep(.el-input__inner::placeholder),
:deep(.el-textarea__inner::placeholder) {
  color: var(--text-3) !important;
}
</style>