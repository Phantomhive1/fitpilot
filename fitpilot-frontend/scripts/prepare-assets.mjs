// 准备前端 AI 运行资产：姿态模型 + MediaPipe wasm 运行时
// 幂等：文件已存在就跳过，可反复执行。
// 用法：npm run prepare:assets
import { existsSync, mkdirSync, copyFileSync, createWriteStream, statSync } from 'node:fs'
import { renameSync } from 'node:fs'
import { cpSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url))) // fitpilot-frontend/
const MODELS = [
  {
    file: 'pose_landmarker_lite.task',
    url: 'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task',
    bytes: 5777746,
  },
  {
    file: 'pose_landmarker_heavy.task',
    url: 'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/latest/pose_landmarker_heavy.task',
    bytes: 30664242,
  },
]

const modelsDir = path.join(root, 'public', 'models')
mkdirSync(modelsDir, { recursive: true })

for (const m of MODELS) {
  const dest = path.join(modelsDir, m.file)
  if (existsSync(dest) && statSync(dest).size > 1024 * 1024) {
    console.log(`[跳过] ${m.file} 已存在（${(statSync(dest).size / 1048576).toFixed(1)}MB）`)
    continue
  }
  console.log(`[下载] ${m.file}（约 ${(m.bytes / 1048576).toFixed(1)}MB）...`)
  const tmp = dest + '.part'
  const res = await fetch(m.url)
  if (!res.ok) throw new Error(`下载失败 HTTP ${res.status}：${m.url}`)
  await pipeline(res.body, tmp)
  renameSync(tmp, dest)
  const got = statSync(dest).size
  if (Math.abs(got - m.bytes) / m.bytes > 0.05) {
    console.warn(`[警告] ${m.file} 大小 ${got} 与预期 ${m.bytes} 相差较大，请检查网络`)
  } else {
    console.log(`[完成] ${m.file}（${(got / 1048576).toFixed(1)}MB）`)
  }
}

// wasm 运行时：从 node_modules 复制（npm ci 之后可执行）
const wasmSrc = path.join(root, 'node_modules', '@mediapipe', 'tasks-vision', 'wasm')
const wasmDst = path.join(root, 'public', 'mediapipe-wasm')
if (existsSync(wasmSrc)) {
  cpSync(wasmSrc, wasmDst, { recursive: true })
  console.log('[完成] mediapipe-wasm 已从 node_modules 复制到 public/')
} else {
  console.warn('[提示] 未找到 node_modules/@mediapipe/tasks-vision/wasm —— 先 npm install 再跑本脚本')
}

console.log('\n✅ 资产准备完成（models/ 和 mediapipe-wasm/ 均不进 git，靠本脚本恢复）')

async function pipeline(body, dest) {
  const ws = createWriteStream(dest)
  const reader = body.getReader()
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      ws.write(value)
    }
  } finally {
    ws.end()
    await new Promise((r) => ws.on('close', r))
  }
}
