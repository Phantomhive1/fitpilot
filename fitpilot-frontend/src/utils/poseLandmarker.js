/**
 * 浏览器端姿态估计（MediaPipe Tasks Vision / BlazePose）。
 *
 * 架构动机：把姿态估计放在浏览器端做——
 *   1. 客户端零成本提取 33 个骨骼关键点（不占服务器算力）
 *   2. 关键点数据（每帧仅 ~100 个数字）随图片一起送 AI，
 *      让多模态模型"看图 + 读数"双重判断，比纯视觉猜测更准
 *   3. 骨架可视化在 canvas 上直接绘制，给用户直观反馈
 *
 * 运行时资源全部本地化（public/mediapipe-wasm + public/models），
 * 不依赖 Google CDN，国内服务器/内网均可运行。
 */
import { FilesetResolver, PoseLandmarker } from '@mediapipe/tasks-vision'

let landmarkerPromise = null

/** 懒加载单例：先试 GPU（WebGL delegate），失败回退 CPU */
export function getLandmarker() {
  if (!landmarkerPromise) {
    landmarkerPromise = create('GPU')
      .catch(() => create('CPU'))
      .catch(e => {
        landmarkerPromise = null
        throw new Error('姿态模型加载失败：' + (e?.message || e))
      })
  }
  return landmarkerPromise
}

async function create(delegate) {
  const fileset = await FilesetResolver.forVisionTasks('/mediapipe-wasm')
  return PoseLandmarker.createFromOptions(fileset, {
    baseOptions: {
      modelAssetPath: '/models/pose_landmarker_heavy.task',
      delegate
    },
    runningMode: 'VIDEO', // IMAGE 模式不能用 detectForVideo；统一 VIDEO 模式，图片也按单帧喂
    numPoses: 1,
    minPoseDetectionConfidence: 0.5,
    minPosePresenceConfidence: 0.5
  })
}

/* ──────────────────────── 提取 ──────────────────────── */

/**
 * 图片：单帧提取。
 * @returns {{el: HTMLImageElement, isVideo: boolean, frames: Array}}
 */
export async function extractFromImage(file) {
  const lm = await getLandmarker()
  const img = new Image()
  img.src = URL.createObjectURL(file)
  await new Promise((res, rej) => {
    img.onload = res
    img.onerror = () => rej(new Error('图片无法解码'))
  })
  const res = lm.detectForVideo(img, performance.now())
  const raw = res?.landmarks?.[0] || null
  const world = res?.worldLandmarks?.[0] || null
  return {
    el: img,
    isVideo: false,
    frames: raw?.length ? [{ t: 0, pts: compact(raw), world: world?.length ? compactWorld(world) : null, raw }] : []
  }
}

/**
 * 视频：等间隔抽帧提取（默认 8 帧，覆盖整个动作周期）。
 * @param {File} file
 * @param {number} sampleCount
 * @param {(progress:number)=>void} onProgress 0~1
 * @returns {{el: HTMLVideoElement, isVideo: boolean, duration: number, frames: Array}}
 */
export async function extractFromVideo(file, sampleCount = 8, onProgress) {
  const lm = await getLandmarker()
  const video = document.createElement('video')
  video.muted = true
  video.playsInline = true
  video.src = URL.createObjectURL(file)
  await new Promise((res, rej) => {
    video.onloadedmetadata = res
    video.onerror = () => rej(new Error('视频无法解码（格式或编码不受浏览器支持）'))
  })
  const duration = video.duration
  if (!duration || !isFinite(duration)) throw new Error('无法读取视频时长')

  const frames = []
  for (let i = 0; i < sampleCount; i++) {
    const t = duration * (i + 0.5) / sampleCount
    await seekTo(video, t)
    const res = lm.detectForVideo(video, performance.now())
    const raw = res?.landmarks?.[0]
    if (raw?.length) {
      const world = res?.worldLandmarks?.[0]
      frames.push({ t: +t.toFixed(2), pts: compact(raw), world: world?.length ? compactWorld(world) : null, raw })
    }
    onProgress?.((i + 1) / sampleCount)
  }
  return { el: video, isVideo: true, duration, frames }
}

/** video.currentTime 定位（同位置重复 seek 不会触发 seeked，加 0.02s 抖动） */
export function seekTo(video, t) {
  return new Promise(resolve => {
    const target = Math.max(0, Math.min(t, (video.duration || 1) - 0.05))
    const done = () => { video.removeEventListener('seeked', done); resolve() }
    video.addEventListener('seeked', done)
    video.currentTime = Math.abs(video.currentTime - target) < 0.02 ? target + 0.02 : target
  })
}

/** 33 关键点 → 紧凑三元组 [x, y, visibility]（归一化坐标，省 payload） */
function compact(raw) {
  const r4 = n => Math.round(n * 10000) / 10000
  return raw.map(p => [r4(p.x), r4(p.y), Math.round((p.visibility ?? 1) * 100) / 100])
}

/**
 * 33 个 worldLandmark → [x, y, z]（米制世界坐标，髋部为原点）。
 * 深度是 BlazePose 单目推断出来的（2.5D），后端用它补算正面视频的屈膝/前倾类指标，
 * 并统一标注"估算"。world 点没有可靠 visibility，置信度由后端用同索引 2D 点把关。
 */
function compactWorld(world) {
  const r4 = n => Math.round(n * 10000) / 10000
  return world.map(p => [r4(p.x), r4(p.y), r4(p.z)])
}

/* ──────────────────────── 骨架可视化 ──────────────────────── */

/** BlazePose 主要骨骼连接（不含面部/手部，健身场景够用） */
const CONNECTIONS = [
  [11, 12], // 双肩
  [11, 13], [13, 15], // 左臂
  [12, 14], [14, 16], // 右臂
  [11, 23], [12, 24], // 肩-髋
  [23, 24], // 双髋
  [23, 25], [25, 27], // 左腿
  [24, 26], [26, 28], // 右腿
  [27, 29], [29, 31], // 左足
  [28, 30], [30, 32]  // 右足
]
const KEY_JOINTS = [11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32]

/**
 * 在 canvas 上绘制骨架（raw 为归一化 landmark 数组，visibility < 0.25 的点跳过）。
 */
export function drawSkeleton(ctx, raw, w, h) {
  if (!raw || !w || !h) return
  const vis = i => (raw[i]?.visibility ?? 1) > 0.25

  ctx.save()
  ctx.lineWidth = Math.max(2, w / 220)
  ctx.strokeStyle = 'rgba(74, 222, 128, 0.9)'
  ctx.lineCap = 'round'
  ctx.shadowColor = 'rgba(74, 222, 128, 0.55)'
  ctx.shadowBlur = Math.max(4, w / 120)
  for (const [a, b] of CONNECTIONS) {
    if (!raw[a] || !raw[b] || !vis(a) || !vis(b)) continue
    ctx.beginPath()
    ctx.moveTo(raw[a].x * w, raw[a].y * h)
    ctx.lineTo(raw[b].x * w, raw[b].y * h)
    ctx.stroke()
  }
  ctx.shadowBlur = 0
  for (const i of KEY_JOINTS) {
    const p = raw[i]
    if (!p || !vis(i)) continue
    ctx.beginPath()
    ctx.fillStyle = '#fb923c'
    ctx.arc(p.x * w, p.y * h, Math.max(3, w / 150), 0, Math.PI * 2)
    ctx.fill()
  }
  ctx.restore()
}
