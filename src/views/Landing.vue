<template>
  <!-- Minimal Hero with gradient background -->
  <section class="hero">
    <h1 class="title">Air<span class="accent">Ai</span></h1>
    <p class="subtitle">与专属虚拟角色即时对话 · 企业级语音交互体验</p>

    <button class="cta" @click="$router.push('/login')">
      开始探索
      <svg viewBox="0 0 24 24" class="cta__icon"><path d="M9 5l7 7-7 7" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
    </button>

    <footer class="footer">© 2025 AirAi Inc.</footer>
  </section>
</template>

<script setup>
// purely presentational
</script>

<style>
:root {
  --bg-start: #0f172a;        /* 深色起点 */
  --bg-end:   #1e40af;        /* 深蓝终点 */
  --accent-a: #60a5fa;        /* blue‑400 */
  --accent-b: #5eead4;        /* teal‑300 */
  --text-base: #cbd5e1;       /* slate‑300 */
}

/* ---- Layout ---- */
html,body {
  margin:0;
  padding:0;
  height:100%;
}
.hero {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 100vh;
  width: 100%;
  color: white;
  background: linear-gradient(135deg, var(--bg-start) 0%, var(--bg-end) 100%);
  text-align: center;
  font-family: "Inter", system-ui, sans-serif;
  overflow: hidden;
}

/* ---- Typography ---- */
.title {
  font-size: clamp(3rem, 8vw, 6rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  margin: 0;
}
.accent {
  background: linear-gradient(90deg, var(--accent-a), var(--accent-b));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
.subtitle {
  margin-top: 1.5rem;
  max-width: 40rem;
  font-size: clamp(1rem, 2.5vw, 1.5rem);
  line-height: 1.6;
  color: var(--text-base);
}

/* ---- CTA ---- */
.cta {
  margin-top: 2.5rem;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem 2.5rem;
  font-size: 1.125rem;
  font-weight: 600;
  color: #fff;
  background: var(--accent-a);
  border: none;
  border-radius: 9999px;
  cursor: pointer;
  box-shadow: 0 8px 20px rgba(96, 165, 250, 0.3);
  transition: transform 0.15s ease, background 0.2s ease;
}
.cta:hover { background: #3b82f6; }
.cta:active { transform: scale(0.96); }
.cta:focus {
  outline: none;
  box-shadow: 0 0 0 4px rgba(96, 165, 250, 0.5);
}
.cta__icon { width: 1.25rem; height: 1.25rem; stroke: currentColor; }

/* Footer */
.footer {
  position: absolute;
  bottom: 1.5rem;
  font-size: 0.875rem;
  color: var(--text-base);
  user-select: none;
}
</style>
