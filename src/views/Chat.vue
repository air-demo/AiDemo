<template>
  <!-- Chat layout: collapsible sidebar + chat + avatar at top‑right -->
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
          <li v-for="m in models" :key="m.id" :class="{ active: m.id === currentModel.id }" @click="switchModel(m)">{{ m.name }}</li>
        </ul>

        <h3 class="sb-title">历史记录</h3>
        <ul class="list">
          <li v-for="h in history" :key="h.id" @click="loadHistory(h)">{{ h.title }}</li>
        </ul>
      </nav>
    </aside>

    <!-- Chat -->
    <main class="chat">
      <header class="chat-head">
        <div class="model-selector" @click="showModelMenu = !showModelMenu">
          当前模型：{{ currentModel.name }} ▼
          <ul v-if="showModelMenu" class="model-menu">
            <li v-for="m in models" :key="m.id" @click.stop="switchModel(m)">{{ m.name }}</li>
          </ul>
        </div>
        <!-- Avatar button at right -->
        <img class="top-avatar" src="https://i.pravatar.cc/60?img=68" alt="me" @click="router.push('/profile')" />
      </header>

      
      <!-- AI thinking indicator -->
      <div v-if="loading" class="thinking">
        <span class="dot"></span><span class="dot"></span><span class="dot"></span>
        <span class="txt">AI 正在思考…</span>
      </div>
          <div class="bubble">{{ m.content }}</div>
        </div>
      </div>

      <form class="inputbar" @submit.prevent="send">
        <div class="wrap">
          <input v-model.trim="input" class="input" type="text" placeholder="输入消息…" required />
          <button type="button" class="voice" @click="voiceChat">🎤</button>
          <button class="send" type="submit" :disabled="loading">{{ loading ? '…' : '发送' }}</button>
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
  { id: 'gpt4o', name: 'GPT‑4o‑mini' },
  { id: 'wizard', name: '巫师助手' },
  { id: 'soc', name: '苏格拉底辩手' }
])
const history = reactive([
  { id: 1, title: '和哈利聊天' },
  { id: 2, title: '逻辑辩论' }
])
const currentModel = ref(models[0])
const messagesBox = ref(null)
const messages = ref([{ role:'assistant', content:'你好！开始和我对话吧～' }])
const input = ref('')
const loading = ref(false)
function scrollBottom(){ if(messagesBox.value) messagesBox.value.scrollTop = messagesBox.value.scrollHeight }
async function send(){
  if(!input.value) return
  messages.value.push({ role:'user', content: input.value })
  const userText = input.value
  input.value=''; loading.value=true
  await nextTick(); scrollBottom()
  const reply = `(${currentModel.value.name}): 你说 “${userText}”`
  messages.value.push({ role:'assistant', content: reply })
  loading.value=false; await nextTick(); scrollBottom()
}
function newChat(){ messages.value=[] }
function switchModel(m){ currentModel.value=m; showModelMenu.value=false }
function loadHistory(h){ alert('加载历史 '+h.title) }
function voiceChat(){ alert('语音功能待实现') }
</script>

<style>
:root{ --blue:#2563eb; --bg:#f8fafc; --sidebar:#0f172a; --sidebar-text:#cbd5e1; --card:#1e293b; }
html,body{margin:0;height:100%;font-family:"Inter",system-ui,sans-serif}
.container{display:grid;grid-template-columns:auto 1fr;height:100vh;overflow:hidden}

/* Sidebar */
.sidebar{background:var(--sidebar);color:var(--sidebar-text);display:flex;flex-direction:column;width:240px;transition:width .2s}
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

/* Chat */
.chat{display:flex;flex-direction:column;background:var(--bg)}
.chat-head{display:flex;align-items:center;justify-content:space-between;padding:10px 18px;border-bottom:1px solid #e2e8f0;background:#fff;font-size:14px;position:relative}
.model-selector{cursor:pointer;user-select:none}
.model-menu{position:absolute;top:40px;left:18px;background:#fff;border:1px solid #e2e8f0;border-radius:8px;box-shadow:0 4px 10px rgba(0,0,0,.08);list-style:none;padding:6px 0;margin:0;width:160px;z-index:10}
.model-menu li{padding:8px 14px;cursor:pointer;font-size:14px}
.model-menu li:hover{background:#f1f5f9}
.top-avatar{width:40px;height:40px;border-radius:50%;cursor:pointer}

.messages{flex:1;overflow-y:auto;padding:24px 20px;display:flex;flex-direction:column;gap:16px}
.msg{display:flex;align-items:flex-start;gap:10px}.msg.user{flex-direction:row-reverse}
.avatar{font-size:22px;width:32px;height:32px;display:flex;align-items:center;justify-content:center}
.bubble{max-width:70%;padding:14px 18px;border-radius:20px;font-size:15px;line-height:1.5;background:rgba(30,41,59,.9);color:#fff;box-shadow:0 2px 6px rgba(0,0,0,.15)}
.msg.user .bubble{background:#3b82f6;color:#fff;border-bottom-right-radius:6px;box-shadow:0 2px 6px rgba(0,0,0,.12)}
.msg.assistant .bubble{background:rgba(30,41,59,.9);border-bottom-left-radius:6px;box-shadow:0 2px 6px rgba(0,0,0,.12)}

.inputbar{display:flex;justify-content:center;padding:12px;background:var(--bg);border-top:1px solid #e2e8f0}
.wrap{display:flex;align-items:center;width:100%;max-width:720px}
.input{flex:1;padding:10px 14px;border:1px solid #cbd5e1;border-radius:999px;font-size:15px;outline:none;width:100%;color:#111;background:#fff}
.input:focus{border-color:var(--blue);box-shadow:0 0 0 2px rgba(37,99,235,.2)}
.voice{margin-left:10px;border:none;background:none;font-size:22px;cursor:pointer}
.send{margin-left:10px;border:none;border-radius:999px;background:var(--blue);color:#fff;padding:0 24px;font-weight:600;cursor:pointer}
.send:disabled{opacity:.6;cursor:not-allowed}
@media(max-width:768px){.sidebar{display:none}}
.thinking{display:flex;align-items:center;gap:6px;font-size:14px;color:#64748b;margin:4px 20px 8px}
.dot{width:6px;height:6px;background:#64748b;border-radius:50%;animation:bounce 1s infinite}
.dot:nth-child(2){animation-delay:.2s}.dot:nth-child(3){animation-delay:.4s}
@keyframes bounce{0%,80%,100%{transform:scale(0)}40%{transform:scale(1)}}
</style>
