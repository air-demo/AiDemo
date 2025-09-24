<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>AirAi | 角色扮演 AI</title>
  <script src="https://cdn.tailwindcss.com"></script>
  <style>
    /* 简单星空背景动画 */
    body::before {content:"";position:fixed;inset:0;background:url('https://cdn.jsdelivr.net/gh/akzhy/Vivid@master/gradient.svg') center/cover no-repeat;z-index:-2;}
    body::after {content:"";position:fixed;inset:0;background:url('https://raw.githubusercontent.com/tholman/space.js/master/assets/space.png') repeat;animation:move 100s linear infinite;opacity:.25;z-index:-1;}
    @keyframes move {from{background-position:0 0}to{background-position:-10000px 5000px}}
  </style>
</head>
<body class="flex items-center justify-center min-h-screen text-white">
  <main class="text-center">
    <h1 class="font-extrabold text-6xl md:text-8xl drop-shadow-xl">AirAi</h1>
    <p class="mt-4 text-xl md:text-2xl opacity-90">与虚拟角色畅聊 · 语音沉浸体验</p>

    <a href="login.html" class="mt-10 inline-block px-8 py-3 rounded-full text-lg font-semibold bg-indigo-600 hover:bg-indigo-700 transition shadow-lg ring-2 ring-white/10 backdrop-blur-md">
      开始探索 ▶
    </a>
  </main>
  <footer class="absolute bottom-4 text-sm opacity-70 w-full text-center">© 2025 AirAi</footer>
</body>
</html>
