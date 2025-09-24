<template>
  <!-- AirAi Login (no Tailwind) -->
  <section class="auth">
    <div class="card">
      <div class="brand">
        <span class="logo">Air<span class="accent">Ai</span></span>
        <h1 class="title">登录</h1>
        <p class="desc">欢迎回来，请输入账号信息</p>
      </div>

      <form class="form" @submit.prevent="onSubmit">
        <label class="field">
          <span class="label">邮箱</span>
          <input class="input" type="email" v-model.trim="form.email" placeholder="name@example.com" autocomplete="email" required />
        </label>

        <label class="field password">
          <span class="label">密码</span>
          <input :type="showPwd ? 'text' : 'password'" class="input" v-model="form.password" placeholder="••••••••" autocomplete="current-password" minlength="6" required />
          <button type="button" class="toggle" @click="showPwd = !showPwd">{{ showPwd ? '隐藏' : '显示' }}</button>
        </label>

        <div class="row">
          <label class="checkbox">
            <input type="checkbox" v-model="form.remember" />
            <span>记住我</span>
          </label>
          <a class="link" href="#" @click.prevent>忘记密码？</a>
        </div>

        <p v-if="error" class="error">{{ error }}</p>

        <button class="submit" type="submit" :disabled="loading">
          <span v-if="!loading">登录</span>
          <span v-else>正在登录…</span>
        </button>
      </form>

      <p class="switch">还没有账号？ <a class="link" href="#" @click.prevent="$router.push('/register')">去注册</a></p>
    </div>

    <footer class="footer">© 2025 AirAi Inc.</footer>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const form = reactive({ email: '', password: '', remember: true })
const showPwd = ref(false)
const loading = ref(false)
const error = ref('')

async function onSubmit() {
  error.value = ''
  loading.value = true
  try {
    // 如果输入默认账号密码，直接跳转
    if (form.email === 'demo@airai.com' && form.password === '123456') {
      router.push('/chat') // 你的聊天页面路由
      return
    }

    // TODO: 否则调用后端接口
    const res = await fetch('/api/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(form)
    })
    if (!res.ok) {
      throw new Error(await res.text() || '登录失败')
    }
    router.push('/chat')
  } catch (e) {
    error.value = e.message || '网络错误，请稍后再试'
  } finally {
    loading.value = false
  }
}
</script>

<style>
:root{
  --bg-start:#0f172a; /* 与 Landing 保持一致 */
  --bg-end:#1e40af;
  --card-bg: rgba(255,255,255,.06);
  --card-border: rgba(255,255,255,.18);
  --text:#e5e7eb;
  --muted:#94a3b8;
  --primary:#60a5fa;
  --primary-hover:#3b82f6;
  --danger:#fca5a5;
}

html,body{margin:0;padding:0;height:100%}

.auth{
  position:relative;display:flex;align-items:center;justify-content:center;
  height:100vh;width:100%;color:var(--text);
  background: linear-gradient(135deg,var(--bg-start),var(--bg-end));
  font-family: "Inter",system-ui,sans-serif;overflow:hidden;text-align:center;
}

.card{
  width:min(100%, 480px);
  background:var(--card-bg);
  border:1px solid var(--card-border);
  backdrop-filter: blur(10px);
  border-radius:24px;
  padding:40px 32px;
  box-shadow: 0 10px 30px rgba(0,0,0,.25);
}
.brand .logo{font-weight:800;font-size:28px;letter-spacing:-.02em}
.brand .accent{background:linear-gradient(90deg,#60a5fa,#5eead4);-webkit-background-clip:text;-webkit-text-fill-color:transparent}
.brand .title{margin:10px 0 0;font-size:28px;font-weight:700}
.brand .desc{margin:6px 0 22px;color:var(--muted)}

.form{display:flex;flex-direction:column;gap:14px;text-align:left}
.field{display:flex;flex-direction:column;gap:8px}
.label{font-size:14px;color:var(--muted)}
.input{
  height:44px;padding:0 14px;border-radius:12px;border:1px solid rgba(255,255,255,.15);
  background:rgba(255,255,255,.04);color:#fff;outline:none;
}
.input:focus{border-color:var(--primary);box-shadow:0 0 0 3px rgba(96,165,250,.3)}

.password{position:relative}
.toggle{position:absolute;right:10px;top:34px;height:26px;padding:0 8px;border:none;border-radius:8px;cursor:pointer;color:#cbd5e1;background:transparent}
.toggle:hover{color:#fff}

.row{display:flex;justify-content:space-between;align-items:center;margin-top:2px}
.checkbox{display:flex;align-items:center;gap:8px;color:#cbd5e1;font-size:14px}
.link{color:var(--primary);text-decoration:none}
.link:hover{color:var(--primary-hover);text-decoration:underline}

.error{margin-top:6px;color:var(--danger);font-size:14px}

.submit{
  margin-top:10px;height:46px;width:100%;border:none;border-radius:999px;cursor:pointer;
  background:var(--primary);color:#fff;font-weight:700;font-size:16px;
  box-shadow:0 12px 24px rgba(96,165,250,.3);transition:.2s ease;
}
.submit:hover{background:var(--primary-hover)}
.submit:active{transform:scale(.98)}
.submit:disabled{opacity:.6;cursor:not-allowed}

.switch{margin-top:18px;color:var(--muted)}
.footer{position:absolute;bottom:16px;left:0;right:0;color:#cbd5e1;font-size:13px;user-select:none}
</style>
