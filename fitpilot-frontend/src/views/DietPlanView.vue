<template>
  <div class="page">
    <!-- Hero -->
    <header class="hero">
      <div class="hero-text">
        <div class="kicker">NUTRITION</div>
        <h1 class="hero-title">吃对了<br/><span class="grad">练才有效</span></h1>
        <p class="hero-sub">懒得算宏量？一键生成。自己懂营养？精确到克。两种模式都由 AI 营养师排餐</p>
      </div>
      <div class="hero-art" aria-hidden="true">
        <svg viewBox="0 0 200 200" fill="none">
          <defs>
            <linearGradient id="dg1" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stop-color="#4ade80"/>
              <stop offset="100%" stop-color="#22c55e"/>
            </linearGradient>
            <linearGradient id="dg2" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stop-color="#fb923c"/>
              <stop offset="100%" stop-color="#f97316"/>
            </linearGradient>
          </defs>
          <!-- 餐盘 -->
          <circle cx="100" cy="105" r="70" stroke="url(#dg1)" stroke-width="6" opacity="0.5"/>
          <path d="M30 105 a70 70 0 0 1 140 0" stroke="url(#dg1)" stroke-width="6" stroke-linecap="round"/>
          <path d="M55 60 l30 30 M145 60 l-30 30" stroke="url(#dg2)" stroke-width="5" stroke-linecap="round" opacity="0.7"/>
          <!-- 苹果 -->
          <path d="M160 40 c-8 0 -14 6 -14 14 0 10 8 16 14 16 6 0 14 -6 14 -16 0 -8 -6 -14 -14 -14z" fill="url(#dg2)" opacity="0.85"/>
          <path d="M160 40 v-8" stroke="#4ade80" stroke-width="3" stroke-linecap="round"/>
        </svg>
      </div>
    </header>

    <!-- 用餐方式切换 -->
    <div class="dining-switch">
      <button type="button" class="dining-pill" :class="{ active: dining === 'cook' }" @click="dining = 'cook'">
        <span class="dining-name">🍳 自己做饭</span>
        <span class="dining-meta">有时间做饭 · 一日循环计划</span>
      </button>
      <button type="button" class="dining-pill" :class="{ active: dining === 'takeaway' }" @click="dining = 'takeaway'">
        <span class="dining-name">🛵 点外卖</span>
        <span class="dining-meta">没时间做饭 · 按周边真实餐厅排一周</span>
      </button>
    </div>

    <!-- 模式切换 -->
    <div class="mode-switch">
      <button type="button" class="mode-card" :class="{ active: mode === 'lazy' }" @click="mode = 'lazy'">
        <span class="mode-icon" v-html="icons.lazy"></span>
        <div class="mode-text">
          <span class="mode-name">懒人模式</span>
          <span class="mode-desc">填身高体重就行，AI 自动算热量和碳蛋脂</span>
        </div>
      </button>
      <button type="button" class="mode-card" :class="{ active: mode === 'pro' }" @click="mode = 'pro'">
        <span class="mode-icon" v-html="icons.pro"></span>
        <div class="mode-text">
          <span class="mode-name">专业模式</span>
          <span class="mode-desc">自己定碳蛋脂克数（或按体重倍数），AI 按克排餐</span>
        </div>
      </button>
    </div>

    <!-- 表单卡片 -->
    <div class="form-card">
      <!-- ══════════ 懒人模式 ══════════ -->
      <template v-if="mode === 'lazy'">
        <section class="section">
          <div class="section-head">
            <span class="section-num">01</span>
            <div>
              <h3 class="section-title">基础数据</h3>
              <p class="section-desc">用于估算基础代谢（BMR）和每日总消耗（TDEE）</p>
            </div>
          </div>
          <div class="row">
            <div class="field">
              <div class="field-label">性别</div>
              <div class="pill-group">
                <button v-for="g in ['男','女']" :key="g" type="button" class="pill"
                        :class="{ active: lazyForm.gender === g }" @click="lazyForm.gender = g">{{ g }}</button>
              </div>
            </div>
            <div class="field">
              <div class="field-label">年龄（岁）</div>
              <el-input-number v-model="lazyForm.age" :min="10" :max="90" size="large" />
            </div>
          </div>
          <div class="row">
            <div class="field">
              <div class="field-label">身高（cm）</div>
              <el-input-number v-model="lazyForm.heightCm" :min="120" :max="230" size="large" />
            </div>
            <div class="field">
              <div class="field-label">体重（kg）</div>
              <el-input-number v-model="lazyForm.weightKg" :min="30" :max="300" :precision="1" size="large" />
            </div>
          </div>
        </section>

        <section class="section">
          <div class="section-head">
            <span class="section-num">02</span>
            <div>
              <h3 class="section-title">目标</h3>
              <p class="section-desc">决定热量缺口 / 盈余的方向</p>
            </div>
          </div>
          <div class="pill-group wide">
            <button v-for="g in goals" :key="g.value" type="button" class="pill goal-pill"
                    :class="{ active: lazyForm.goal === g.value }" @click="lazyForm.goal = g.value">
              <span class="goal-text">{{ g.label }}</span>
              <span class="goal-meta">{{ g.meta }}</span>
            </button>
          </div>
        </section>

        <section class="section">
          <div class="section-head">
            <span class="section-num">03</span>
            <div>
              <h3 class="section-title">训练量</h3>
              <p class="section-desc">影响活动系数与总消耗估算</p>
            </div>
          </div>
          <div class="row">
            <div class="field">
              <div class="field-label">每周训练天数</div>
              <el-input-number v-model="lazyForm.daysPerWeek" :min="1" :max="7" size="large" />
            </div>
            <div class="field">
              <div class="field-label">单次时长（分钟）</div>
              <el-input-number v-model="lazyForm.sessionMinutes" :min="20" :max="180" :step="10" size="large" />
            </div>
          </div>
        </section>
      </template>

      <!-- ══════════ 专业模式 ══════════ -->
      <template v-else>
        <section class="section">
          <div class="section-head">
            <span class="section-num">01</span>
            <div>
              <h3 class="section-title">宏量输入方式</h3>
              <p class="section-desc">直接给克数，或按每公斤体重的倍数换算</p>
            </div>
          </div>
          <div class="pill-group">
            <button type="button" class="pill" :class="{ active: proInputType === 'grams' }"
                    @click="proInputType = 'grams'">直接输入克数</button>
            <button type="button" class="pill" :class="{ active: proInputType === 'multiplier' }"
                    @click="proInputType = 'multiplier'">按体重倍数</button>
          </div>
        </section>

        <section class="section">
          <div class="section-head">
            <span class="section-num">02</span>
            <div>
              <h3 class="section-title">每日宏量目标</h3>
              <p class="section-desc" v-if="proInputType === 'grams'">AI 将严格按照克数排餐，不会改动</p>
              <p class="section-desc" v-else>常见参考：碳水 2~5 g/kg，蛋白 1.5~2.2 g/kg，脂肪 0.6~1 g/kg</p>
            </div>
          </div>

          <div v-if="proInputType === 'grams'" class="row three">
            <div class="field">
              <div class="field-label">碳水化合物（g）</div>
              <el-input-number v-model="proForm.grams.carbsG" :min="20" :max="1500" :step="10" size="large" />
            </div>
            <div class="field">
              <div class="field-label">蛋白质（g）</div>
              <el-input-number v-model="proForm.grams.proteinG" :min="20" :max="500" :step="10" size="large" />
            </div>
            <div class="field">
              <div class="field-label">脂肪（g）</div>
              <el-input-number v-model="proForm.grams.fatG" :min="10" :max="400" :step="5" size="large" />
            </div>
          </div>

          <template v-else>
            <div class="row">
              <div class="field">
                <div class="field-label">体重（kg）</div>
                <el-input-number v-model="proForm.multiplier.weightKg" :min="30" :max="300" :precision="1" size="large" />
              </div>
            </div>
            <div class="row three">
              <div class="field">
                <div class="field-label">碳水（g/kg 体重）</div>
                <el-input-number v-model="proForm.multiplier.carbX" :min="0.1" :max="15" :step="0.1" :precision="1" size="large" />
              </div>
              <div class="field">
                <div class="field-label">蛋白（g/kg 体重）</div>
                <el-input-number v-model="proForm.multiplier.proteinX" :min="0.1" :max="8" :step="0.1" :precision="1" size="large" />
              </div>
              <div class="field">
                <div class="field-label">脂肪（g/kg 体重）</div>
                <el-input-number v-model="proForm.multiplier.fatX" :min="0.1" :max="5" :step="0.1" :precision="1" size="large" />
              </div>
            </div>
            <!-- 实时换算预览 -->
            <div class="macro-preview">
              <div class="mp-item"><span class="mp-label">换算克数</span><span class="mp-val">碳水 {{ calc.carbs }}g · 蛋白 {{ calc.protein }}g · 脂肪 {{ calc.fat }}g</span></div>
              <div class="mp-item"><span class="mp-label">估算热量</span><span class="mp-val">{{ calc.calories }} kcal / 天</span></div>
            </div>
          </template>
        </section>
      </template>

      <!-- ══════════ 外卖模式：地址 ══════════ -->
      <section v-if="dining === 'takeaway'" class="section">
        <div class="section-head">
          <span class="section-num">📍</span>
          <div>
            <h3 class="section-title">你的位置</h3>
            <p class="section-desc">填到小区 / 大厦 / 街道级别，AI 会查这个范围内的真实餐厅来排餐</p>
          </div>
        </div>
        <el-input v-model="takeawayForm.address" placeholder="如：北京市海淀区中关村大街1号 / 上海市浦东新区张江路100号" style="margin-bottom: 14px" />
        <div class="field">
          <div class="field-label">搜索范围</div>
          <div class="pill-group">
            <button v-for="r in radiusOptions" :key="r.value" type="button" class="pill"
                    :class="{ active: takeawayForm.radius === r.value }"
                    @click="takeawayForm.radius = r.value">{{ r.label }}</button>
          </div>
        </div>
      </section>

      <!-- ══════════ 公共：餐数 + 偏好 ══════════ -->
      <section class="section">
        <div class="section-head">
          <span class="section-num">{{ commonSectionNum }}</span>
          <div>
            <h3 class="section-title">餐数与偏好</h3>
            <p class="section-desc">有忌口或想吃外卖都写上，AI 会严格遵守</p>
          </div>
        </div>
        <div class="field" style="margin-bottom: 14px">
          <div class="field-label">每日几餐（含加餐）</div>
          <div class="pill-group">
            <button v-for="n in [3,4,5,6]" :key="n" type="button" class="pill"
                    :class="{ active: form.mealsPerDay === n }" @click="form.mealsPerDay = n">{{ n }} 餐</button>
          </div>
        </div>
        <el-input v-model="form.preference" type="textarea" :rows="2"
                  placeholder="如：不吃香菜、乳糖不耐、偏好中式家常菜、工作日只能点外卖……" />
      </section>

      <!-- 提交 -->
      <div class="submit-bar">
        <div class="submit-tip">相同参数重复生成会命中缓存，不重复计费 · 10 分钟内最多 5 次生成</div>
        <button class="gen-btn" :disabled="generating || cooldown > 0" @click="submit">
          <span v-if="!generating && cooldown === 0" class="btn-content">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>
            {{ mode === 'lazy' ? '一键生成饮食计划' : '按宏量生成饮食计划' }}
          </span>
          <span v-else-if="generating" class="btn-content">
            <span class="spinner"></span>
            AI 营养师配餐中…
          </span>
          <span v-else class="btn-content">冷却中 ({{ cooldown }}s)</span>
        </button>
      </div>
    </div>

    <!-- ══════════ 结果 ══════════ -->
    <div v-if="plan" ref="resultCard" class="result-card" :class="{ 'just-loaded': highlightResult }">
      <div class="result-head">
        <div>
          <h2 class="result-title">{{ plan.title }}</h2>
          <p class="result-summary">{{ plan.summary }}</p>
        </div>
        <span v-if="plan.cached" class="cached-badge">♻ 复用{{ plan.cacheSource === 'database' ? '历史' : '缓存' }}结果，未重复计费</span>
      </div>

      <!-- 宏量统计 -->
      <div class="stat-grid">
        <div class="stat-item">
          <div class="stat-label">每日热量</div>
          <div class="stat-val">{{ plan.targetCalories }}<span class="unit">kcal</span></div>
        </div>
        <div class="stat-item c-carb">
          <div class="stat-label">碳水化合物</div>
          <div class="stat-val">{{ plan.macros?.carbs }}<span class="unit">g</span></div>
        </div>
        <div class="stat-item c-protein">
          <div class="stat-label">蛋白质</div>
          <div class="stat-val">{{ plan.macros?.protein }}<span class="unit">g</span></div>
        </div>
        <div class="stat-item c-fat">
          <div class="stat-label">脂肪</div>
          <div class="stat-val">{{ plan.macros?.fat }}<span class="unit">g</span></div>
        </div>
      </div>

      <!-- 餐次（自己做饭模式：一日） -->
      <div v-if="!isWeekPlan" class="meals-grid">
        <div v-for="(m, i) in plan.meals" :key="i" class="meal-card">
          <div class="meal-head">
            <span class="meal-name">{{ m.meal }}</span>
            <span class="meal-time">{{ m.time }}</span>
          </div>
          <div class="meal-macros">
            <span>{{ m.calories }} kcal</span>
            <span v-if="m.carbs != null">碳 {{ m.carbs }}g</span>
            <span v-if="m.protein != null">蛋 {{ m.protein }}g</span>
            <span v-if="m.fat != null">脂 {{ m.fat }}g</span>
          </div>
          <ul class="food-list">
            <li v-for="(f, j) in m.foods" :key="j">
              <span class="food-name">{{ f.name }}</span>
              <span class="food-amount">{{ f.amount }}</span>
              <span class="food-cal">{{ f.calories }} kcal</span>
            </li>
          </ul>
        </div>
      </div>

      <!-- 一周排餐（外卖模式） -->
      <div v-else class="week-grid">
        <div v-for="d in plan.days" :key="d.day" class="day-card">
          <div class="day-head">
            <span class="day-name">{{ d.label || ('第' + d.day + '天') }}</span>
            <span class="day-total">{{ dayCalories(d) }} kcal</span>
          </div>
          <div v-for="(m, i) in d.meals" :key="i" class="t-meal">
            <div class="t-meal-top">
              <span class="t-meal-name">{{ m.meal }}</span>
              <span class="t-meal-cal">{{ m.calories }} kcal</span>
            </div>
            <div class="t-restaurant">🏪 {{ m.restaurant }}</div>
            <div class="t-order">{{ m.order }}</div>
            <div v-if="m.carbs != null || m.protein != null || m.fat != null" class="t-macros">
              <span v-if="m.carbs != null">碳 {{ m.carbs }}g</span>
              <span v-if="m.protein != null">蛋 {{ m.protein }}g</span>
              <span v-if="m.fat != null">脂 {{ m.fat }}g</span>
            </div>
            <ul v-if="m.foods?.length" class="food-list">
              <li v-for="(f, j) in m.foods" :key="j">
                <span class="food-name">{{ f.name }}</span>
                <span class="food-amount">{{ f.amount }}</span>
                <span class="food-cal">{{ f.calories }} kcal</span>
              </li>
            </ul>
          </div>
        </div>
      </div>

      <!-- Tips -->
      <div v-if="plan.tips?.length" class="tips">
        <div v-for="(t, i) in plan.tips" :key="i" class="tip-item">💡 {{ t }}</div>
      </div>
    </div>

    <!-- ══════════ 历史计划 ══════════ -->
    <div v-if="history.length" class="history-card">
      <div class="history-head">📜 我的饮食计划历史</div>
      <button v-for="h in history" :key="h.id" type="button" class="history-item" @click="loadDetail(h.id)">
        <span class="hi-title">{{ h.title || 'AI 饮食计划' }}</span>
        <span class="hi-meta">
          {{ h.mode === 'lazy' ? '懒人' : '专业' }}{{ h.plan?.days ? '外卖' : '' }} · {{ formatTime(h.createdAt) }}
          <span v-if="h.plan?.cached" class="hi-cached">（复用）</span>
        </span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const icons = {
  lazy: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>',
  pro: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>'
}

const goals = [
  { value: '减脂', label: '减脂', meta: '15~20% 热量缺口' },
  { value: '维持', label: '维持', meta: '热量收支平衡' },
  { value: '增肌', label: '增肌', meta: '10~15% 热量盈余' }
]

const mode = ref('lazy')            // lazy | pro
const proInputType = ref('grams')   // grams | multiplier
const dining = ref('cook')          // cook（自己做饭）| takeaway（点外卖）
const generating = ref(false)
const cooldown = ref(0)
const plan = ref(null)
const history = ref([])
const resultCard = ref(null)          // 结果卡片 DOM 引用，用于精准滚动定位
const highlightResult = ref(false)    // 刚加载的历史计划短暂高亮

const radiusOptions = [
  { value: 1000, label: '1 公里' },
  { value: 3000, label: '3 公里' },
  { value: 5000, label: '5 公里' }
]

const takeawayForm = reactive({ address: '', radius: 3000 })

// 公共「餐数与偏好」section 的编号：外卖模式多一个「你的位置」section
const commonSectionNum = computed(() => {
  if (dining.value === 'takeaway') return mode.value === 'lazy' ? '05' : '04'
  return mode.value === 'lazy' ? '04' : '03'
})

/** 是否是一周结构（外卖模式） */
const isWeekPlan = computed(() => Array.isArray(plan.value?.days))

/** 滚动到结果卡片顶部（留 16px 呼吸空间），并短暂高亮提示 */
function scrollToResult() {
  nextTick(() => {
    if (!resultCard.value) return
    const top = resultCard.value.getBoundingClientRect().top + window.scrollY - 16
    window.scrollTo({ top, behavior: 'smooth' })
    highlightResult.value = true
    setTimeout(() => { highlightResult.value = false }, 1600)
  })
}

const form = reactive({ mealsPerDay: 3, preference: '' })

const lazyForm = reactive({
  gender: '男',
  age: 25,
  heightCm: 175,
  weightKg: 70,
  goal: '减脂',
  daysPerWeek: 4,
  sessionMinutes: 60
})

const proForm = reactive({
  grams: { carbsG: 250, proteinG: 140, fatG: 60 },
  multiplier: { weightKg: 70, carbX: 3, proteinX: 1.8, fatX: 0.8 }
})

// 体重倍数 → 实时换算预览
const calc = computed(() => {
  const m = proForm.multiplier
  const carbs = Math.round(m.weightKg * m.carbX)
  const protein = Math.round(m.weightKg * m.proteinX)
  const fat = Math.round(m.weightKg * m.fatX)
  return { carbs, protein, fat, calories: Math.round(carbs * 4 + protein * 4 + fat * 9) }
})

async function submit() {
  if (mode.value === 'lazy' && !lazyForm.goal) { ElMessage.warning('请选择目标'); return }
  if (dining.value === 'takeaway' && takeawayForm.address.trim().length < 2) {
    ElMessage.warning('点外卖模式请填写所在地址')
    return
  }
  generating.value = true
  try {
    const payload = { mode: mode.value, dining: dining.value, ...form }
    if (dining.value === 'takeaway') Object.assign(payload, takeawayForm)
    if (mode.value === 'lazy') Object.assign(payload, lazyForm)
    else if (proInputType.value === 'grams') Object.assign(payload, { inputType: 'grams', ...proForm.grams })
    else Object.assign(payload, { inputType: 'multiplier', ...proForm.multiplier })

    const { data } = await api.post('/diet/generate', payload)
    plan.value = data
    ElMessage.success(data.cached ? '已复用缓存结果' : (dining.value === 'takeaway' ? '一周外卖计划已生成！' : '饮食计划已生成！'))
    scrollToResult()
    startCooldown()
    loadHistory()
  } catch (e) {
    ElMessage.error('生成失败：' + (e.response?.data?.message || e.message))
  } finally {
    generating.value = false
  }
}

function startCooldown() {
  cooldown.value = 8
  const timer = setInterval(() => {
    cooldown.value--
    if (cooldown.value <= 0) clearInterval(timer)
  }, 1000)
}

async function loadHistory() {
  try {
    const { data } = await api.get('/diet/history')
    history.value = data
  } catch { /* 历史加载失败不打扰用户 */ }
}

async function loadDetail(id) {
  try {
    const { data } = await api.get(`/diet/${id}`)
    if (data.plan) {
      plan.value = data.plan
      scrollToResult()
    }
  } catch (e) {
    ElMessage.error('加载失败：' + (e.response?.data?.message || e.message))
  }
}

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** 一天热量合计（外卖周计划用） */
function dayCalories(d) {
  if (!d.meals?.length) return 0
  return d.meals.reduce((sum, m) => sum + (Number(m.calories) || 0), 0)
}

onMounted(loadHistory)
</script>

<style scoped>
.page {
  max-width: 920px;
  margin: 0 auto;
}

/* ===== Hero（与训练需求页同风格） ===== */
.hero {
  display: flex;
  align-items: center;
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
  margin-bottom: 14px;
}
.hero-title {
  font-size: 40px;
  line-height: 1.15;
  margin: 0 0 12px;
  font-weight: 800;
  letter-spacing: -1px;
  color: #e5eaf3;
}
.grad {
  background: linear-gradient(135deg, #4ade80 0%, #fb923c 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.hero-sub {
  color: #94a3b8;
  font-size: 15px;
  margin: 0;
  max-width: 480px;
}
.hero-art svg { width: 160px; height: 160px; }

/* ===== 用餐方式切换 ===== */
.dining-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}
.dining-pill {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 14px;
  padding: 14px 18px;
  cursor: pointer;
  transition: all 0.18s ease;
}
.dining-pill:hover { border-color: rgba(251, 146, 60, 0.35); }
.dining-pill.active {
  background: linear-gradient(135deg, rgba(251,146,60,0.18), rgba(249,115,22,0.1));
  border-color: rgba(251, 146, 60, 0.55);
  box-shadow: 0 8px 24px rgba(251, 146, 60, 0.16);
}
.dining-name { font-size: 16px; font-weight: 700; color: #e5eaf3; }
.dining-meta { font-size: 11px; color: #6b7895; }

/* ===== 模式切换 ===== */
.mode-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 20px;
}
.mode-card {
  display: flex;
  align-items: center;
  gap: 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 16px;
  padding: 18px 20px;
  cursor: pointer;
  transition: all 0.18s ease;
  color: #94a3b8;
  text-align: left;
}
.mode-card:hover {
  border-color: rgba(74, 222, 128, 0.3);
  transform: translateY(-2px);
}
.mode-card.active {
  background: linear-gradient(135deg, rgba(74,222,128,0.18), rgba(251,146,60,0.12));
  border-color: rgba(74,222,128,0.5);
  color: #e5eaf3;
  box-shadow: 0 8px 24px rgba(74, 222, 128, 0.18);
}
.mode-icon :deep(svg) { width: 30px; height: 30px; color: #4ade80; }
.mode-name {
  display: block;
  font-size: 16px;
  font-weight: 700;
  color: #e5eaf3;
}
.mode-desc {
  display: block;
  font-size: 12px;
  margin-top: 4px;
  line-height: 1.5;
}

/* ===== 表单卡片 ===== */
.form-card {
  background: rgba(15, 22, 38, 0.7);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 20px;
  padding: 36px 40px;
}
.section {
  padding: 22px 0;
  border-bottom: 1px solid rgba(255,255,255,0.04);
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
.section-title { margin: 0; font-size: 16px; color: #e5eaf3; font-weight: 700; }
.section-desc { margin: 2px 0 0; font-size: 12px; color: #6b7895; }

.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  margin-bottom: 16px;
}
.row.three { grid-template-columns: repeat(3, 1fr); }
.row:last-child { margin-bottom: 0; }
.field-label {
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 10px;
}
.field-label :deep(.el-input-number) { width: 100%; }

.pill-group {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.pill {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  padding: 10px 22px;
  font-size: 14px;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.15s ease;
}
.pill:hover { border-color: rgba(74, 222, 128, 0.3); color: #e5eaf3; }
.pill.active {
  background: linear-gradient(135deg, #4ade80 0%, #22c55e 100%);
  border-color: transparent;
  color: #06090f;
  font-weight: 700;
  box-shadow: 0 6px 20px rgba(74, 222, 128, 0.3);
}
.pill-group.wide { display: grid; grid-template-columns: repeat(3, 1fr); }
.goal-pill {
  border-radius: 12px;
  padding: 14px;
  text-align: center;
}
.goal-text { display: block; font-size: 15px; }
.goal-meta { display: block; font-size: 11px; margin-top: 4px; opacity: 0.75; }

/* 倍数换算预览 */
.macro-preview {
  background: linear-gradient(135deg, rgba(74,222,128,0.08), rgba(251,146,60,0.06));
  border: 1px solid rgba(74, 222, 128, 0.2);
  border-radius: 12px;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.mp-item { display: flex; align-items: baseline; gap: 12px; }
.mp-label { font-size: 12px; color: #6b7895; flex-shrink: 0; }
.mp-val { font-size: 14px; color: #4ade80; font-weight: 600; }

/* 提交 */
.submit-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 28px;
  margin-top: 8px;
  border-top: 1px solid rgba(255,255,255,0.06);
  gap: 18px;
  flex-wrap: wrap;
}
.submit-tip { font-size: 13px; color: #6b7895; }
.gen-btn {
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
.gen-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(74, 222, 128, 0.4);
}
.gen-btn:disabled { opacity: 0.7; cursor: wait; }
.btn-content { display: inline-flex; align-items: center; gap: 8px; }
.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ===== 结果 ===== */
.result-card {
  margin-top: 24px;
  background: rgba(15, 22, 38, 0.7);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 20px;
  padding: 32px 36px;
  scroll-margin-top: 24px;
  transition: border-color 0.4s ease, box-shadow 0.4s ease;
}
.result-card.just-loaded {
  border-color: rgba(74, 222, 128, 0.55);
  box-shadow: 0 0 0 1px rgba(74, 222, 128, 0.25), 0 10px 36px rgba(74, 222, 128, 0.16);
}
.result-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 22px;
}
.result-title {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 800;
  color: #e5eaf3;
}
.result-summary {
  margin: 0;
  font-size: 13px;
  color: #94a3b8;
  line-height: 1.6;
}
.cached-badge {
  flex-shrink: 0;
  font-size: 12px;
  color: #4ade80;
  background: rgba(74, 222, 128, 0.12);
  border: 1px solid rgba(74, 222, 128, 0.3);
  border-radius: 999px;
  padding: 6px 14px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}
.stat-item {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 14px;
  padding: 16px 18px;
  text-align: center;
}
.stat-label { font-size: 12px; color: #6b7895; }
.stat-val {
  font-size: 26px;
  font-weight: 800;
  margin-top: 6px;
  color: #e5eaf3;
}
.stat-val .unit { font-size: 12px; font-weight: 500; margin-left: 4px; color: #6b7895; }
.c-carb .stat-val { color: #fbbf24; }
.c-protein .stat-val { color: #4ade80; }
.c-fat .stat-val { color: #fb923c; }

.meals-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}
.meal-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 14px;
  padding: 18px;
}
.meal-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 8px;
}
.meal-name { font-size: 15px; font-weight: 700; color: #e5eaf3; }
.meal-time { font-size: 11px; color: #6b7895; }
.meal-macros {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #4ade80;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px dashed rgba(255,255,255,0.08);
}
.food-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.food-list li {
  display: flex;
  gap: 8px;
  font-size: 13px;
  line-height: 1.5;
}
.food-name { color: #e5eaf3; flex-shrink: 0; }
.food-amount { color: #6b7895; flex: 1; }
.food-cal { color: #fb923c; flex-shrink: 0; }

.tips {
  margin-top: 20px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 10px;
}

/* ===== 一周排餐（外卖） ===== */
.week-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 14px;
}
.day-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 14px;
  padding: 18px;
}
.day-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}
.day-name { font-size: 15px; font-weight: 700; color: #e5eaf3; }
.day-total { font-size: 12px; color: #fb923c; font-weight: 600; }
.t-meal {
  background: rgba(251, 146, 60, 0.04);
  border: 1px solid rgba(251, 146, 60, 0.1);
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 10px;
}
.t-meal:last-child { margin-bottom: 0; }
.t-meal-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 6px;
}
.t-meal-name { font-size: 13px; font-weight: 700; color: #e5eaf3; }
.t-meal-cal { font-size: 12px; color: #fb923c; }
.t-restaurant {
  font-size: 13px;
  color: #4ade80;
  font-weight: 600;
  margin-bottom: 4px;
}
.t-order {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.6;
  margin-bottom: 6px;
}
.t-macros {
  display: flex;
  gap: 10px;
  font-size: 11px;
  color: #6b7895;
}
.t-macros + .food-list { margin-top: 8px; padding-top: 8px; border-top: 1px dashed rgba(255,255,255,0.08); }
.tip-item {
  background: rgba(251, 146, 60, 0.07);
  border: 1px solid rgba(251, 146, 60, 0.18);
  border-radius: 10px;
  padding: 12px 14px;
  font-size: 13px;
  color: #e5eaf3;
  line-height: 1.6;
}

/* ===== 历史 ===== */
.history-card {
  margin-top: 20px;
  background: rgba(15, 22, 38, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 16px;
  padding: 20px 24px;
}
.history-head {
  font-size: 14px;
  font-weight: 700;
  color: #e5eaf3;
  margin-bottom: 14px;
}
.history-item {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 10px;
  padding: 12px 16px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
  text-align: left;
}
.history-item:hover { border-color: rgba(74, 222, 128, 0.35); background: rgba(255,255,255,0.04); }
.hi-title { font-size: 14px; color: #e5eaf3; font-weight: 600; }
.hi-meta { font-size: 12px; color: #6b7895; flex-shrink: 0; }
.hi-cached { color: #4ade80; }

/* ===== 响应式 ===== */
@media (max-width: 720px) {
  .hero { flex-direction: column; align-items: flex-start; gap: 12px; }
  .hero-title { font-size: 30px; }
  .hero-art svg { width: 110px; height: 110px; }
  .form-card { padding: 24px 20px; }
  .mode-switch { grid-template-columns: 1fr; }
  .dining-switch { grid-template-columns: 1fr; }
  .row, .row.three { grid-template-columns: 1fr; }
  .pill-group.wide { grid-template-columns: 1fr; }
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
  .submit-bar { flex-direction: column; align-items: stretch; }
  .gen-btn { width: 100%; justify-content: center; }
  .result-card { padding: 24px 20px; }
}

/* 覆盖 Element Plus 主题色 */
:deep(.el-textarea__inner),
:deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.04) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.08) !important;
}
:deep(.el-textarea__inner:focus),
:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #4ade80 !important;
}
:deep(.el-input__inner),
:deep(.el-textarea__inner) {
  color: #e5eaf3 !important;
}
:deep(.el-input__inner::placeholder),
:deep(.el-textarea__inner::placeholder) {
  color: #4b5670 !important;
}
</style>
