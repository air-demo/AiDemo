<template>
  <div class="container">
    <!-- Sidebar -->
    <aside :class="['sidebar', { collapsed }]">
      <header class="sb-head">
        <button class="toggle" @click="collapsed = !collapsed">☰</button>
        <span v-if="!collapsed" class="brand">Air<span class="accent">Ai</span></span>
      </header>

      <nav v-if="!collapsed" class="sb-scroll">
        <button class="nav-btn" @click="newChat">＋ 新聊天</button>

        <h3 class="sb-title">已训练模型</h3>
        <ul class="list">
          <li v-for="m in models" :key="m.id"
              :class="{ active: m.id === currentModel.id }"
              @click="switchModel(m)">
            {{ m.name }}
          </li>
        </ul>

        <h3 class="sb-title">历史记录</h3>
        <ul class="list">
          <li v-for="h in history" :key="h.id" @click="loadHistory(h)">
            {{ h.title }}
          </li>
        </ul>
      </nav>
    </aside>

    <!-- Chat -->
    <main class="chat">
      <header class="chat-head">
        <div class="model-selector" @click="showModelMenu = !showModelMenu">
          当前模型：{{ currentModel.name }} ▼
          <ul v-if="showModelMenu" class="model-menu">
            <li v-for="m in models" :key="m.id" @click.stop="switchModel(m)">
              {{ m.name }}
            </li>
          </ul>
        </div>
        <img class="top-avatar" src="https://i.pravatar.cc/60?img=68"
             alt="me" @click="router.push('/profile')" />
      </header>

     <div v-if="messages.length === 0 && !loadingChat" class="empty">
    <h1 class="empty-title">有什么我能帮你的吗？</h1>
  </div>

  <!-- 消息区 -->
  <div class="messages" v-else ref="messagesBox">
    <div v-for="(m,i) in messages" :key="i" :class="['msg', m.role]">
      <div class="avatar">{{ m.role==='user' ? '👤' : '🤖' }}</div>
      <div class="bubble">{{ m.content }}</div>
    </div>
  </div>

  <div v-if="loadingChat" class="thinking">
    <span class="dot"></span><span class="dot"></span><span class="dot"></span>
    <span class="txt">AI 正在思考…</span>
  </div>
      <form class="composer" @submit.prevent="send">
  <div class="composer-box">
    <!-- 文本输入（多行），保留原来的 input 绑定变量 -->
    <textarea
      v-model.trim="input"
      class="composer-input"
      rows="3"
      placeholder="发消息或输入 / 选择技能"
    ></textarea>

    <!-- 底部工具行 -->
    <div class="composer-footer">
      <!-- 左侧：附件 -->
      <button type="button" class="icon-btn-circle" title="附件（占位）">
        <svg viewBox="0 0 24 24" class="icon">
          <path d="M21 12.5l-8.5 8.5a6 6 0 0 1-8.5-8.5L12 4.5a4 4 0 1 1 5.7 5.6L9.4 18.4a2 2 0 1 1-2.8-2.8L14 8.3"
                fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>

      <!-- 中部：标签 Chips（示例两枚，可按需增删） -->
      <div class="chips">
        <span class="chip chip-primary">极速</span>
        <span class="chip">思考</span>
      </div>

      <!-- 右侧：剪刀、麦克风、发送 -->
      <div class="tools-right">
        <button type="button" class="icon-btn-ghost" title="剪裁（占位）">
          <svg viewBox="0 0 24 24" class="icon">
            <path d="M14 4l-4 8 4 8M5 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm14 0a2 2 0 1 0 0-4 2 2 0 0 0 0 4z"
                  fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>

        <button
  type="button"
  class="icon-btn-ghost"
  :class="{ recording }"
  @click="toggleRec"
  title="语音输入"
>
  <svg viewBox="0 0 24 24" class="icon">
    <path d="M12 14a3 3 0 0 0 3-3V7a3 3 0 0 0-6 0v4a3 3 0 0 0 3 3zm5-3a5 5 0 0 1-10 0M12 16v4m-3 0h6"
          fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  </svg>
</button>

        <!-- 发送：圆形按钮，提交表单 -->
        <button type="submit" class="send-circle" :disabled="loadingChat || loadingAsr" title="发送">
          <svg viewBox="0 0 24 24" class="icon">
            <path d="M5 12l14-7-6 14-2-5-6-2z" fill="currentColor"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</form>

    </main>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const collapsed = ref(false)
const showModelMenu = ref(false)

const models = reactive([
  { id: 'gpt4o', name: 'GPT-4o-mini' },
  { id: 'wizard', name: '巫师助手' },
  { id: 'soc',   name: '苏格拉底辩手' }
])

const history = reactive([
  { id: 1, title: '和哈利聊天' },
  { id: 2, title: '逻辑辩论' }
])

const currentModel = ref(models[0])

const messagesBox = ref(null)
const messages = ref([])


const input = ref('')
const loadingChat = ref(false)   // 只管 SSE 聊天的 loading
const loadingAsr  = ref(false)   // 只管 ASR 语音的 loading

// —— 语音请求并发控制（防旧结果覆盖新结果）——
let currentRunId = 0
let currentAbort = null

function scrollBottom () {
  if (messagesBox.value) {
    messagesBox.value.scrollTop = messagesBox.value.scrollHeight
  }
}

async function send () {
  if (!input.value) return
  messages.value.push({ role: 'user', content: input.value })
  const userText = input.value
  input.value = ''
  loadingChat.value = true
  await nextTick(); scrollBottom()

  // 打开 SSE
  const es = new EventSource(`http://localhost:8080/api/chat/stream?msg=${encodeURIComponent(userText)}`)
  let aiBuf = ''
  es.onmessage = e => {
    aiBuf += e.data
    // 如果已存在 AI 气泡则更新，否则新增
    if (messages.value[messages.value.length - 1].role === 'assistant') {
      messages.value[messages.value.length - 1].content = aiBuf
    } else {
      messages.value.push({ role: 'assistant', content: aiBuf })
    }
    nextTick().then(scrollBottom)
  }
  es.onerror = () => { es.close(); loadingChat.value = false }
es.addEventListener('end', () => { es.close(); loadingChat.value = false })
}


function newChat () { messages.value = [] }
function switchModel (m) { currentModel.value = m; showModelMenu.value = false }
function loadHistory (h) { alert('加载历史 ' + h.title) }
const recording = ref(false)
let mediaRec = null
let audioChunks = []

async function toggleRec () {
  if (recording.value) {
    mediaRec.stop()
    recording.value = false
    return
  }
  // 请求麦克风权限
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    mediaRec = new MediaRecorder(stream)
    audioChunks = []
    mediaRec.ondataavailable = e => audioChunks.push(e.data)
    mediaRec.onstop = async () => {
      const blob = new Blob(audioChunks, { type: mediaRec.mimeType })
      await sendAudio(blob)          // 调用后端
      stream.getTracks().forEach(t => t.stop()) // 释放麦克风
    }
    mediaRec.start()
    recording.value = true
  } catch (err) {
    console.error('麦克风权限失败', err)
  }
}

async function sendAudio(blob) {
  // 取消上一轮（如果有）
  if (currentAbort && typeof currentAbort.abort === 'function') {
    try { currentAbort.abort() } catch (e) {}
  }

  // 新建本轮的 AbortController（老浏览器可能没有）
  const controller = (typeof AbortController !== 'undefined')
    ? new AbortController()
    : null
  currentAbort = controller

  const runId = ++currentRunId
  loadingAsr.value = true

  try {
    const wavBlob = await webmOrOggToWav16k(blob)
    const form = new FormData()
    form.append('file', wavBlob, 'record.wav')

    const opts = { method: 'POST', body: form }
    if (controller) opts.signal = controller.signal   // 只有有 controller 时才传递

    const resp = await fetch('http://localhost:8080/api/asr', opts)       // 用 Vite 代理同源
    if (!resp.ok) {
      const errText = await safeText(resp)
      throw new Error(`ASR 请求失败：${resp.status} ${resp.statusText} - ${errText}`)
    }

    const text = (await resp.text() || '').trim()

    // 只在仍是“当前批次”时写入，避免旧请求覆盖
    if (runId === currentRunId) {
      input.value = text
      await nextTick(() => document.querySelector('.composer-input')?.focus())
    }
  } catch (e) {
    if (!(e && e.name === 'AbortError')) {
      console.error(e)
      alert(e?.message || '语音识别失败，请稍后重试')
    }
  } finally {
    if (runId === currentRunId) loadingAsr.value = false
    if (currentAbort === controller) currentAbort = null // 清理本轮 controller
  }
}



// 兼容 decodeAudioData 在旧 Safari 的回调形式
function decodeAudioCompat(audioCtx, arrayBuf) {
  return new Promise((resolve, reject) => {
    // 新标准：返回 Promise
    const maybePromise = audioCtx.decodeAudioData(arrayBuf, resolve, reject)
    if (maybePromise && typeof maybePromise.then === 'function') {
      maybePromise.then(resolve).catch(reject)
    }
  })
}

// WebM/Ogg(通常是 Opus) → 16k/mono/16bit WAV
async function webmOrOggToWav16k(blob) {
  const arrayBuf = await blob.arrayBuffer()

  // 用 Online AudioContext 解码（兼容最好）
  const online = new (window.AudioContext || window.webkitAudioContext)()
  let decoded
  try {
    decoded = await decodeAudioCompat(online, arrayBuf)
  } finally {
    // iOS 会限制并发上下文数量，尽快关闭
    await online.close()
  }

  // 先在原采样率下把多声道合成单声道
  const srcRate = decoded.sampleRate
  const monoBuf = mergeToMono(decoded) // 仍是 Float32、srcRate

  // 用 OfflineAudioContext 进行重采样到 16k/mono
  const targetRate = 16000
  const frameCount = Math.ceil(monoBuf.duration * targetRate)
  const off = new (window.OfflineAudioContext || window.webkitOfflineAudioContext)(1, frameCount, targetRate)

  const src = off.createBufferSource()
  src.buffer = monoBuf
  src.connect(off.destination)
  src.start(0)

  const rendered = await off.startRendering()
  // 离线上下文不需要 close()

  // Float32 PCM → Int16，并封装 WAV
  const pcm = rendered.getChannelData(0)
  const i16 = floatToInt16(pcm)
  const wav = pcmToWav(i16, targetRate, 1)
  return new Blob([wav], { type: 'audio/wav' })
}

function mergeToMono(decoded) {
  const { numberOfChannels, length, sampleRate } = decoded
  if (numberOfChannels === 1) return decoded

  const out = new AudioBuffer({ numberOfChannels: 1, length, sampleRate })
  const ch0 = out.getChannelData(0)
  const a0 = decoded.getChannelData(0)
  const a1 = decoded.getChannelData(1)
  const n = Math.min(a0.length, a1.length)
  for (let i = 0; i < n; i++) {
    const s0 = a0[i] || 0
    const s1 = a1[i] || 0
    ch0[i] = (s0 + s1) / 2
  }
  return out
}

function floatToInt16(float32) {
  const i16 = new Int16Array(float32.length)
  for (let i = 0; i < float32.length; i++) {
    let s = float32[i]
    if (!Number.isFinite(s)) s = 0
    s = Math.max(-1, Math.min(1, s))
    i16[i] = (s * 0x7fff) | 0
  }
  return i16
}

function pcmToWav(int16, sampleRate, channels) {
  const blockAlign = channels * 2
  const byteRate = sampleRate * blockAlign
  const dataSize = int16.length * 2
  const buffer = new ArrayBuffer(44 + dataSize)
  const view = new DataView(buffer)
  let o = 0
  const writeStr = (s) => { for (let i = 0; i < s.length; i++) view.setUint8(o++, s.charCodeAt(i)) }
  const writeU32 = (v) => { view.setUint32(o, v, true); o += 4 }
  const writeU16 = (v) => { view.setUint16(o, v, true); o += 2 }

  writeStr('RIFF'); writeU32(36 + dataSize); writeStr('WAVE')
  writeStr('fmt '); writeU32(16); writeU16(1) // PCM
  writeU16(channels); writeU32(sampleRate); writeU32(byteRate)
  writeU16(blockAlign); writeU16(16) // bits
  writeStr('data'); writeU32(dataSize)
  new Int16Array(buffer, 44).set(int16)
  return buffer
}

// 读取 text，但避免非 UTF-8/无 body 的报错
async function safeText(resp) {
  try { return await resp.text() } catch { return '' }
}
</script>
<style>
:root{
  --blue:#2563eb;
  --bg:#f8fafc;
  --sidebar:#0f172a;
  --sidebar-text:#cbd5e1;
  --card:#1e293b;
}
html,body{margin:0;height:100%;font-family:"Inter",system-ui,sans-serif}
.container{display:grid;grid-template-columns:auto 1fr;height:100vh;overflow:hidden}

/* Sidebar */
.sidebar{
  background:var(--sidebar);color:var(--sidebar-text);
  display:flex;flex-direction:column;width:240px;transition:width .2s
}
.sidebar.collapsed{width:60px}
.sb-head{display:flex;align-items:center;gap:12px;padding:14px}
.toggle{font-size:20px;background:none;border:none;color:#fff;cursor:pointer}
.brand{font-weight:700;font-size:20px}.accent{color:var(--blue)}
.sb-scroll{flex:1;overflow-y:auto;padding:0 10px}
.sb-title{margin:16px 0 8px;font-size:12px;text-transform:uppercase;color:#64748b}
.list{list-style:none;padding:0;margin:0;display:flex;flex-direction:column;gap:6px;font-size:14px}
.list li{padding:6px 10px;border-radius:6px;cursor:pointer}
.list li:hover{background:rgba(255,255,255,.08)}
.list li.active{background:var(--blue)}
.nav-btn{width:100%;padding:8px 0;border:none;border-radius:8px;background:var(--blue);color:#fff;font-weight:600;cursor:pointer;margin-top:8px}


/* ① chat 根容器：加 min-height:0 */
.chat{
  display:flex;
  flex-direction:column;
  background:var(--bg);
  /* 新增 ↓ */
  min-height:0;
}


.chat-head{
  display:flex;align-items:center;justify-content:space-between;
  padding:10px 18px;border-bottom:1px solid #e2e8f0;background:#fff;font-size:14px;position:relative
}
.model-selector{cursor:pointer;user-select:none}
.model-menu{
  position:absolute;top:40px;left:18px;background:#fff;border:1px solid #e2e8f0;
  border-radius:8px;box-shadow:0 4px 10px rgba(0,0,0,.08);
  list-style:none;padding:6px 0;margin:0;width:160px;z-index:10
}
.model-menu li{padding:8px 14px;cursor:pointer;font-size:14px}
.model-menu li:hover{background:#f1f5f9}
.top-avatar{width:40px;height:40px;border-radius:50%;cursor:pointer}

/* ② messages 滚动区：改 flex & 加 min-height:0 */
.messages{
  flex:1 1 0;          /* 原来是 flex:1; → 改成可缩放 */
  min-height:0;        /* 新增，防止撑破父容器 */
  overflow-y:auto;
  padding:24px 20px;
  display:flex;
  flex-direction:column;
  gap:16px;
}
.msg{display:flex;align-items:flex-start;gap:10px}
.msg.user{flex-direction:row-reverse}
.avatar{font-size:22px;width:32px;height:32px;display:flex;align-items:center;justify-content:center}
.bubble{
  max-width:70%;padding:14px 18px;border-radius:20px;font-size:15px;line-height:1.5;
  background:rgba(30,41,59,.9);color:#fff;box-shadow:0 2px 6px rgba(0,0,0,.15)
}
.msg.user .bubble{
  background:#3b82f6;color:#fff;border-bottom-right-radius:6px;box-shadow:0 2px 6px rgba(0,0,0,.12)
}
.msg.assistant .bubble{
  background:rgba(30,41,59,.9);border-bottom-left-radius:6px;box-shadow:0 2px 6px rgba(0,0,0,.12)
}

/* Input bar */
.inputbar{display:flex;justify-content:center;padding:12px;background:var(--bg);border-top:1px solid #e2e8f0}
.wrap{display:flex;align-items:center;width:100%;max-width:720px}
.input{
  flex:1;padding:10px 14px;border:1px solid #cbd5e1;border-radius:999px;
  font-size:15px;outline:none;width:100%;color:#111;background:#fff
}
.input:focus{border-color:var(--blue);box-shadow:0 0 0 2px rgba(37,99,235,.2)}
.voice{margin-left:10px;border:none;background:none;font-size:22px;cursor:pointer}
.send{
  margin-left:10px;border:none;border-radius:999px;background:var(--blue);
  color:#fff;padding:0 24px;font-weight:600;cursor:pointer
}
.send:disabled{opacity:.6;cursor:not-allowed}

@media(max-width:768px){.sidebar{display:none}}

/* Thinking indicator */
.thinking{display:flex;align-items:center;gap:6px;font-size:14px;color:#64748b;padding:8px 20px}
.dot{
  width:6px;height:6px;background:#64748b;border-radius:50%;
  animation:bounce 1s infinite
}
.dot:nth-child(2){animation-delay:.2s}
.dot:nth-child(3){animation-delay:.4s}
@keyframes bounce{
  0%,80%,100%{transform:scale(0)}
  40%{transform:scale(1)}
}


.empty{
  display:flex; align-items:center; justify-content:center;
  padding:48px 20px 12px;
}
.empty-title{
  font-size:clamp(22px, 4vw, 36px);
  font-weight:800; color:#0f172a; letter-spacing:.5px;
}

/* 圆形图标按钮（语音） */
.icon-btn{
  flex:0 0 auto;
  width:36px; height:36px; border-radius:999px;
  border:1px solid #e2e8f0; background:#fff; color:#334155;
  display:inline-flex; align-items:center; justify-content:center;
  cursor:pointer; transition:all .15s ease;
  box-shadow:0 1px 2px rgba(0,0,0,.04);
}
.icon-btn:hover{ background:#f8fafc }
.icon-btn:active{ transform:scale(.96) }
.icon{ width:18px; height:18px }

/* 输入框略增高，更像图中的质感 */
.input{
  height:42px; /* 可按需调整 40~44 */
}


.composer{ display:flex; justify-content:center; padding:16px 20px; background:var(--bg); }
.composer-box{
  width:100%; max-width:900px; background:#fff;
  border:1px solid #eef2f7; border-radius:22px;
  box-shadow:0 12px 40px rgba(15,23,42,.08);
  padding:14px 16px 10px;
}

/* 多行输入 */
.composer-input{
  width:100%; border:none; outline:none; resize:none;
  font-size:16px; line-height:1.5; color:#0f172a; background:transparent;
  padding:6px 6px 0 6px;
}
.composer-input::placeholder{ color:#94a3b8; }

/* 底部工具行 */
.composer-footer{
  margin-top:8px; display:flex; align-items:center; justify-content:space-between;
}

/* 左侧圆形按钮（附件） */
.icon-btn-circle{
  width:40px; height:40px; border-radius:999px;
  border:1px solid #e2e8f0; background:#fff; color:#334155;
  display:inline-flex; align-items:center; justify-content:center;
  cursor:pointer; transition:all .15s ease; box-shadow:0 1px 2px rgba(0,0,0,.04);
}
.icon-btn-circle:hover{ background:#f8fafc }
.icon{ width:18px; height:18px }

/* 中部 chips */
.chips{ display:flex; align-items:center; gap:8px; margin-left:10px; flex:1; }
.chip{
  display:inline-flex; align-items:center; padding:6px 10px; border-radius:999px;
  background:#f1f5f9; color:#334155; font-size:14px;
}
.chip-primary{ background:#e7f0ff; color:#1d4ed8; font-weight:600; }

/* 右侧图标区 */
.tools-right{ display:flex; align-items:center; gap:6px; }
.icon-btn-ghost{
  width:34px; height:34px; border-radius:999px; border:none; background:transparent; color:#334155;
  display:inline-flex; align-items:center; justify-content:center; cursor:pointer;
}
.icon-btn-ghost:hover{ background:#f8fafc }

/* 发送按钮：圆形、品牌色 */
.send-circle{
  width:40px; height:40px; border:none; border-radius:999px; cursor:pointer;
  background:#2563eb; color:#fff; display:inline-flex; align-items:center; justify-content:center;
  box-shadow:0 6px 18px rgba(37,99,235,.35); transition:transform .12s ease, opacity .12s ease;
}
.send-circle:disabled{ opacity:.6; cursor:not-allowed; box-shadow:none; }
.send-circle:active{ transform:scale(.96); }


.icon-btn-ghost.recording{
  background:#fee2e2;            /* 轻红底 */
  color:#b91c1c;                 /* 深红图标 */
}
</style>
