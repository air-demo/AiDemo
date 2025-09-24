import { createRouter, createWebHistory } from 'vue-router';
import Landing from '@/views/Landing.vue';
import Login   from '@/views/Login.vue';
import Chat    from '@/views/Chat.vue';     // 你的主页面

const routes = [
  { path: '/',         name: 'landing', component: Landing },
  { path: '/login',    name: 'login',   component: Login },
  { path: '/chat',     name: 'chat',    component: Chat }
];

export default createRouter({
  history: createWebHistory(),
  routes
});
