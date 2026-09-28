<template>
  <div class="page">
    <!-- Hero -->
    <header class="hero">
      <div class="hero-text">
        <div class="kicker">实时对话</div>
        <h1 class="hero-title">AI 教练<span class="grad">在线</span></h1>
        <p class="hero-sub">随时提问、要求改计划，回复会基于你当前的计划上下文</p>
      </div>
      <div class="hero-status">
        <span class="status-dot"></span>
        <span class="status-text">在线</span>
      </div>
    </header>

    <!-- 快捷问题 -->
    <div v-if="!messages.length" class="quick-prompts">
      <button v-for="q in quickPrompts" :key="q" class="prompt-chip" @click="usePrompt(q)">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z"/><path d="M12 8v4M12 16h.01"/></svg>
        {{ q }}
      </button>
    </div>

    <!-- 对话区 -->
    <div ref="listRef" class="chat-list" :class="{ empty: !messages.length }">
      <div v-if="!messages.length" class="empty-art-wrap">
        <svg viewBox="0 0 200 200" fill="none" class="empty-art">
          <defs>
            <linearGradient id="chatG" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stop-color="#4ade80"/>
              <stop offset="100%" stop-color="#fb923c"/>
            </linearGradient>
          </defs>
          <path d="M40 60h120a10 10 0 0 1 10 10v60a10 10 0 0 1-10 10H80l-30 25v-25H40a10 10 0 0 1-10-10V70a10 10 0 0 1 10-10z" stroke="url(#chatG)" stroke-width="2" fill="rgba(74,222,128,0.05)"/>
          <circle cx="70" cy="100" r="4" fill="#4ade80"/>
          <circle cx="100" cy="100" r="4" fill="#fb923c"/>
          <circle cx="130" cy="100" r="4" fill="#4ade80"/>
        </svg>
        <h3>和教练聊聊吧</h3>
        <p>问训练问题，或直接说"周三腿日帮我减一个动作"</p>
      </div>

      <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
        <div class="avatar" :class="m.role">
          <svg v-if="m.role === 'user'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>
        </div>
        <div class="bubble-wrap">
          <div class="bubble">
            <div class="bubble-text">{{ cleanContent(m.content) }}<span v-if="typing && i === messages.length - 1 && m.role === 'assistant'" class="cursor">▍</span></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-bar">
      <div class="input-wrap">
        <el-input
          v-model="input"
          :disabled="sending"
          placeholder="输入消息，按 Enter 发送…"
          size="large"
          @keydown.enter.exact.prevent="send" />
        <button class="send-btn" :disabled="sending || !input.trim()" @click="send">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M22 2L11 13"/><path d="M22 2L15 22l-4-9-9-4 20-7z"/></svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const router = useRouter()

const sessionId = localStorage.getItem('fitpilot_session') || ('s-' + Date.now())
localStorage.setItem('fitpilot_session', sessionId)

const messages = ref([])
const input = ref('')
const sending = ref(false)
const typing = ref(false)
const listRef = ref(null)

const quickPrompts = [
  '周三腿日帮我减一个动作',
  '硬拉时腰有点酸正常吗',
  '训练后吃什么增肌',
  '推荐一组腹肌动作'
]

onMounted(async () => {
  try {
    const res = await fetch(`/api/chat/history?sessionId=${encodeURIComponent(sessionId)}`, {
      headers: { 'X-Fitpilot-Session': sessionId }
    })
    if (res.ok) messages.value = await res.json()
    scrollDown()
  } catch { /* 忽略 */ }
})

function cleanContent(content) {
  return (content || '').replace(/```plan[\s\S]*?```/g, '\n✅ 训练计划已更新，点击上方通知查看新版本').trim()
}

function scrollDown() {
  nextTick(() => { if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight })
}

function usePrompt(q) {
  input.value = q
  send()
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  input.value = ''
  sending.value = true
  typing.value = true
  messages.value.push({ role: 'user', content: text })
  const reply = reactive({ role: 'assistant', content: '' })
  messages.value.push(reply)
  scrollDown()

  try {
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Fitpilot-Session': sessionId },
      body: JSON.stringify({ sessionId, message: text })
    })
    if (!res.ok || !res.body) throw new Error(await res.text().catch(() => res.status))

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop()
      for (const line of lines) {
        if (!line.startsWith('data:')) continue
        let payload
        try { payload = JSON.parse(line.slice(5)) } catch { continue }
        if (payload.type === 'token') {
          reply.content += payload.content
          scrollDown()
        } else if (payload.type === 'plan_updated') {
          ElMessage({
            type: 'success',
            message: `训练计划已更新到 v${payload.version}，点击查看`,
            duration: 6000,
            onClick: () => router.push(`/dashboard/${payload.planId}`)
          })
        } else if (payload.type === 'error') {
          reply.content += `\n[出错了：${payload.message}]`
        }
      }
    }
  } catch (e) {
    reply.content += `\n[请求失败：${e.message}]`
  } finally {
    typing.value = false
    sending.value = false
    scrollDown()
  }
}
</script>

<style scoped>
.page {
  max-width: 880px;
  margin: 0 auto;
  height: calc(100vh - 80px);
  display: flex;
  flex-direction: column;
}

/* ===== Hero ===== */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 18px;
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
  margin-bottom: 10px;
}
.hero-title {
  font-size: 32px;
  margin: 0 0 6px;
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
}
.hero-status {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(74, 222, 128, 0.1);
  border: 1px solid rgba(74, 222, 128, 0.3);
  padding: 6px 14px;
  border-radius: 999px;
  color: #4ade80;
  font-size: 13px;
  font-weight: 600;
}
.status-dot {
  width: 8px;
  height: 8px;
  background: #4ade80;
  border-radius: 50%;
  box-shadow: 0 0 8px rgba(74, 222, 128, 0.6);
  animation: pulse 2s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.7; transform: scale(0.9); }
}

/* 快捷问题 */
.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.prompt-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: #94a3b8;
  padding: 8px 14px;
  border-radius: 999px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.18s ease;
}
.prompt-chip:hover {
  background: rgba(74, 222, 128, 0.1);
  border-color: rgba(74, 222, 128, 0.3);
  color: #4ade80;
}

/* 对话列表 */
.chat-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: rgba(15, 22, 38, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.chat-list::-webkit-scrollbar { width: 6px; }
.chat-list::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.08); border-radius: 3px; }

.empty-art-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.empty-art {
  width: 120px;
  height: 120px;
  opacity: 0.7;
}
.empty-art-wrap h3 {
  color: #e5eaf3;
  margin: 16px 0 6px;
  font-size: 18px;
}
.empty-art-wrap p {
  color: #94a3b8;
  font-size: 13px;
  margin: 0;
}

/* 消息 */
.msg {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  animation: msgIn 0.25s ease;
}
@keyframes msgIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.msg.user { flex-direction: row-reverse; }
.avatar {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
}
.avatar svg {
  width: 18px;
  height: 18px;
}
.avatar.user {
  background: rgba(255, 255, 255, 0.06);
  color: #cbd5e1;
}
.avatar.assistant {
  background: linear-gradient(135deg, #4ade80 0%, #22c55e 100%);
  color: #06090f;
  box-shadow: 0 4px 12px rgba(74, 222, 128, 0.3);
}

.bubble-wrap {
  max-width: 72%;
  display: flex;
}
.msg.user .bubble-wrap { justify-content: flex-end; }
.bubble {
  padding: 12px 16px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg.user .bubble {
  background: linear-gradient(135deg, #4ade80 0%, #22c55e 100%);
  color: #06090f;
  border-bottom-right-radius: 4px;
  font-weight: 500;
}
.msg.assistant .bubble {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.06);
  color: #dbe4f3;
  border-bottom-left-radius: 4px;
}
.cursor {
  display: inline-block;
  margin-left: 2px;
  color: #4ade80;
  animation: blink 1s infinite;
}
@keyframes blink { 50% { opacity: 0; } }

/* 输入 */
.input-bar {
  padding-top: 16px;
}
.input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}
.send-btn {
  position: absolute;
  right: 8px;
  z-index: 2;
  background: linear-gradient(135deg, #4ade80 0%, #22c55e 100%);
  color: #06090f;
  border: none;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  cursor: pointer;
  transition: all 0.18s ease;
  box-shadow: 0 4px 12px rgba(74, 222, 128, 0.3);
}
.send-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(74, 222, 128, 0.4);
}
.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  box-shadow: none;
}
.send-btn svg {
  margin-left: 1px;
}

:deep(.input-wrap .el-input__wrapper) {
  background: rgba(255, 255, 255, 0.04) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.08) !important;
  border-radius: 14px !important;
  padding-right: 56px;
}
:deep(.input-wrap .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #4ade80 !important;
}
:deep(.input-wrap .el-input__inner) {
  color: #e5eaf3 !important;
  height: 50px;
  font-size: 14px;
}
:deep(.input-wrap .el-input__inner::placeholder) {
  color: #4b5670 !important;
}

@media (max-width: 720px) {
  .page { height: calc(100vh - 56px); }
  .hero { flex-direction: column; align-items: flex-start; gap: 8px; }
  .hero-title { font-size: 24px; }
  .bubble-wrap { max-width: 84%; }
}
</style>