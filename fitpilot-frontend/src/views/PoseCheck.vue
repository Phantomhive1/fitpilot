<template>
  <div class="page">
    <!-- Hero -->
    <header class="hero">
      <div class="hero-text">
        <div class="kicker">视觉分析</div>
        <h1 class="hero-title">姿势<span class="grad">纠正</span></h1>
        <p class="hero-sub">上传动作照片或视频，AI 多模态模型按时间顺序识别姿态问题并给出建议</p>
      </div>
      <div class="hero-art" aria-hidden="true">
        <svg viewBox="0 0 200 200" fill="none">
          <defs>
            <linearGradient id="poseG" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stop-color="#4ade80"/>
              <stop offset="100%" stop-color="#fb923c"/>
            </linearGradient>
          </defs>
          <circle cx="100" cy="100" r="80" stroke="url(#poseG)" stroke-width="2" stroke-dasharray="6 6" opacity="0.4"/>
          <circle cx="100" cy="70" r="20" stroke="url(#poseG)" stroke-width="3" fill="rgba(74,222,128,0.1)"/>
          <path d="M100 90 L100 130 M100 110 L75 100 M100 110 L125 100 M100 130 L80 160 M100 130 L120 160" stroke="url(#poseG)" stroke-width="3" stroke-linecap="round"/>
          <circle cx="125" cy="100" r="4" fill="#fb923c"/>
          <circle cx="80" cy="160" r="4" fill="#fb923c"/>
        </svg>
      </div>
    </header>

    <div class="grid">
      <!-- 左：上传与预览 -->
      <div class="card">
        <div class="card-head">
          <h3 class="card-title">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>
            上传动作素材
          </h3>
          <span v-if="file" class="file-tag" :class="{ video: isVideoFlag }">
            <svg v-if="isVideoFlag" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="23 7 16 12 23 17 23 7"/><rect x="1" y="5" width="15" height="14" rx="2"/></svg>
            <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/></svg>
            {{ isVideoFlag ? '视频' : '图片' }}
          </span>
        </div>

        <el-upload
          drag
          :auto-upload="false"
          :limit="1"
          :on-change="onChange"
          :on-remove="() => { file = null; previewUrl = ''; isVideoFlag = false; result = null }"
          accept="image/*,video/*"
          list-type="picture"
          class="upload"
        >
          <div class="upload-hint">
            <svg width="42" height="42" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="17 8 12 3 7 8"/>
              <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
            <div class="upload-text">{{ file ? '点击或拖拽替换' : '把动作照片或视频拖到这里' }}</div>
            <div class="upload-sub">图片 / mp4 / mov · 视频 100MB 以内 · 建议侧面照/全身入镜</div>
          </div>
        </el-upload>

        <video v-if="isVideoFlag && previewUrl" :src="previewUrl" controls class="preview" />
        <img v-else-if="previewUrl" :src="previewUrl" class="preview" alt="预览" />

        <div class="meta-form">
          <label class="form-label">动作名称 <span class="optional">（选填，AI 会自动识别）</span></label>
          <el-input v-model="movement" placeholder="如：深蹲 / 卧推 / 硬拉" />
        </div>

        <button class="analyze-btn" :disabled="loading || !file" @click="analyze">
          <span v-if="!loading" class="btn-content">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            开始分析
          </span>
          <span v-else class="btn-content">
            <span class="spinner"></span>
            {{ isVideoFlag ? '视频抽帧分析中（首次上传需几十秒）…' : '视觉模型分析中…' }}
          </span>
        </button>
      </div>

      <!-- 右：分析结果 -->
      <div class="card">
        <div class="card-head">
          <h3 class="card-title">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
            分析结果
          </h3>
        </div>

        <template v-if="result">
          <!-- 评分仪表 -->
          <div class="score-block">
            <div class="score-ring" :style="ringStyle">
              <svg viewBox="0 0 120 120" class="ring-svg">
                <circle cx="60" cy="60" r="50" class="ring-bg"/>
                <circle cx="60" cy="60" r="50" class="ring-fg" :stroke="scoreColor"
                        :stroke-dasharray="`${scorePercent * 3.14159} 999`"
                        transform="rotate(-90 60 60)"/>
              </svg>
              <div class="score-text">
                <div class="score-num">{{ Number(result.score) || 0 }}</div>
                <div class="score-unit">/ 100</div>
              </div>
            </div>
            <div class="score-meta">
              <div class="verdict">{{ result.verdict }}</div>
              <div class="score-level" :style="{ color: scoreColor }">{{ scoreLabel }}</div>
            </div>
          </div>

          <!-- 问题 -->
          <section v-if="result.issues?.length" class="result-section">
            <h4 class="section-title issues-title">
              <span class="section-badge">!</span>
              发现的问题
            </h4>
            <div class="result-list">
              <div v-for="(it, i) in result.issues" :key="i" class="result-item issues">
                <span class="item-num">{{ i + 1 }}</span>
                <span>{{ it }}</span>
              </div>
            </div>
          </section>

          <!-- 建议 -->
          <section v-if="result.suggestions?.length" class="result-section">
            <h4 class="section-title suggestions-title">
              <span class="section-badge">✓</span>
              纠正建议
            </h4>
            <div class="result-list">
              <div v-for="(it, i) in result.suggestions" :key="i" class="result-item suggestions">
                <span class="item-num">{{ i + 1 }}</span>
                <span>{{ it }}</span>
              </div>
            </div>
          </section>

          <!-- 安全 -->
          <div v-if="result.safety" class="safety-card">
            <div class="safety-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
            </div>
            <div>
              <div class="safety-title">安全提醒</div>
              <div class="safety-content">{{ result.safety }}</div>
            </div>
          </div>
        </template>

        <div v-else class="empty-result">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" opacity="0.3">
            <circle cx="12" cy="12" r="10"/>
            <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
            <line x1="12" y1="17" x2="12.01" y2="17"/>
          </svg>
          <p>上传照片或视频并分析后，<br/>结果会显示在这里</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'

const file = ref(null)
const previewUrl = ref('')
const isVideoFlag = ref(false)
const movement = ref('')
const loading = ref(false)
const result = ref(null)

const scorePercent = computed(() => Math.min(100, Math.max(0, Number(result.value?.score) || 0)))
const scoreColor = computed(() => {
  const s = scorePercent.value
  return s >= 80 ? '#4ade80' : s >= 60 ? '#facc15' : '#f87171'
})
const scoreLabel = computed(() => {
  const s = scorePercent.value
  return s >= 80 ? '动作标准' : s >= 60 ? '需要调整' : '需要重点纠正'
})
const ringStyle = computed(() => ({ '--ring-color': scoreColor.value }))

function onChange(uploadFile) {
  file.value = uploadFile.raw
  previewUrl.value = URL.createObjectURL(uploadFile.raw)
  isVideoFlag.value = (uploadFile.raw?.type || '').startsWith('video/')
  result.value = null
}

async function analyze() {
  if (!file.value) { ElMessage.warning('请先上传照片或视频'); return }
  const fd = new FormData()
  fd.append('file', file.value)
  if (movement.value.trim()) fd.append('movement', movement.value.trim())
  loading.value = true
  try {
    const res = await fetch('/api/vision/analyze', { method: 'POST', body: fd })
    if (!res.ok) throw new Error((await res.json().catch(() => ({}))).message || res.status)
    result.value = await res.json()
  } catch (e) {
    ElMessage.error('分析失败：' + e.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page { max-width: 1200px; margin: 0 auto; }

/* Hero */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  padding: 0 0 24px;
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
.hero-title {
  font-size: 32px;
  line-height: 1.15;
  margin: 0 0 8px;
  font-weight: 800;
  letter-spacing: -0.5px;
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
  font-size: 14px;
  margin: 0;
  max-width: 460px;
}
.hero-art svg { width: 150px; height: 150px; }

/* Grid */
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}
@media (max-width: 980px) {
  .grid { grid-template-columns: 1fr; }
}

.card {
  background: rgba(15, 22, 38, 0.7);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 18px;
  padding: 22px 24px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #e5eaf3;
}
.card-title svg { color: #4ade80; }

.file-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(74, 222, 128, 0.12);
  border: 1px solid rgba(74, 222, 128, 0.25);
  color: #4ade80;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}
.file-tag.video {
  background: rgba(251, 146, 60, 0.12);
  border-color: rgba(251, 146, 60, 0.3);
  color: #fb923c;
}

/* 上传 */
.upload { width: 100%; }
.upload :deep(.el-upload-dragger) {
  background: rgba(255, 255, 255, 0.02);
  border: 2px dashed rgba(74, 222, 128, 0.25);
  border-radius: 14px;
  padding: 24px;
  transition: all 0.2s ease;
}
.upload :deep(.el-upload-dragger:hover) {
  background: rgba(74, 222, 128, 0.05);
  border-color: rgba(74, 222, 128, 0.5);
}
.upload-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #94a3b8;
}
.upload-hint svg { color: #4ade80; }
.upload-text {
  font-size: 14px;
  font-weight: 600;
  color: #cbd5e1;
}
.upload-sub {
  font-size: 12px;
  color: #6b7895;
}
.preview {
  display: block;
  max-width: 100%;
  border-radius: 12px;
  margin-top: 16px;
  max-height: 360px;
  object-fit: contain;
  background: rgba(0,0,0,0.3);
}

.meta-form {
  margin-top: 16px;
}
.form-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #cbd5e1;
  margin-bottom: 6px;
}
.optional {
  font-weight: 400;
  color: #6b7895;
  font-size: 12px;
}

.analyze-btn {
  margin-top: 16px;
  width: 100%;
  background: linear-gradient(135deg, #4ade80 0%, #22c55e 100%);
  color: #06090f;
  border: none;
  border-radius: 12px;
  padding: 14px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 8px 24px rgba(74, 222, 128, 0.3);
}
.analyze-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(74, 222, 128, 0.4);
}
.analyze-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
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

/* ===== 结果区 ===== */
.score-block {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 14px;
  margin-bottom: 18px;
}
.score-ring {
  position: relative;
  width: 120px;
  height: 120px;
  flex-shrink: 0;
}
.ring-svg {
  width: 100%;
  height: 100%;
  transform: rotate(0deg);
}
.ring-bg {
  fill: none;
  stroke: rgba(255, 255, 255, 0.06);
  stroke-width: 8;
}
.ring-fg {
  fill: none;
  stroke-width: 8;
  stroke-linecap: round;
  transition: stroke-dasharray 0.6s ease;
}
.score-text {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.score-num {
  font-size: 36px;
  font-weight: 800;
  line-height: 1;
  color: #e5eaf3;
  font-family: ui-monospace, monospace;
}
.score-unit {
  font-size: 11px;
  color: #6b7895;
  margin-top: 2px;
}
.score-meta { flex: 1; }
.verdict {
  font-size: 15px;
  font-weight: 600;
  color: #e5eaf3;
  line-height: 1.5;
}
.score-level {
  font-size: 12px;
  font-weight: 700;
  margin-top: 6px;
  letter-spacing: 1px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  margin: 18px 0 10px;
}
.section-badge {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 800;
}
.issues-title { color: #f87171; }
.issues-title .section-badge {
  background: rgba(248, 113, 113, 0.15);
  color: #f87171;
}
.suggestions-title { color: #4ade80; }
.suggestions-title .section-badge {
  background: rgba(74, 222, 128, 0.15);
  color: #4ade80;
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.result-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 12px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.6;
  color: #dbe4f3;
}
.result-item.issues {
  background: rgba(248, 113, 113, 0.06);
  border-left: 2px solid rgba(248, 113, 113, 0.5);
}
.result-item.suggestions {
  background: rgba(74, 222, 128, 0.06);
  border-left: 2px solid rgba(74, 222, 128, 0.5);
}
.item-num {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
  font-family: ui-monospace, monospace;
}
.issues .item-num {
  background: rgba(248, 113, 113, 0.2);
  color: #f87171;
}
.suggestions .item-num {
  background: rgba(74, 222, 128, 0.2);
  color: #4ade80;
}

.safety-card {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  margin-top: 18px;
  padding: 16px;
  background: rgba(251, 146, 60, 0.08);
  border: 1px solid rgba(251, 146, 60, 0.3);
  border-radius: 12px;
}
.safety-icon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  background: rgba(251, 146, 60, 0.15);
  border-radius: 10px;
  color: #fb923c;
}
.safety-title {
  font-size: 13px;
  font-weight: 700;
  color: #fb923c;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}
.safety-content {
  font-size: 13px;
  color: #dbe4f3;
  line-height: 1.6;
}

.empty-result {
  text-align: center;
  padding: 60px 20px;
  color: #6b7895;
}
.empty-result p {
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.7;
}

@media (max-width: 720px) {
  .hero { flex-direction: column; align-items: flex-start; gap: 12px; }
  .hero-title { font-size: 24px; }
  .hero-art svg { width: 100px; height: 100px; }
  .score-block { flex-direction: column; text-align: center; }
}

:deep(.el-textarea__inner),
:deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.04) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.08) !important;
  border-radius: 10px !important;
}
:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #4ade80 !important;
}
:deep(.el-input__inner) {
  color: #e5eaf3 !important;
}
:deep(.el-input__inner::placeholder) {
  color: #4b5670 !important;
}
</style>