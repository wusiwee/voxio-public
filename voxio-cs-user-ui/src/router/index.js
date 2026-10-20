import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    // 打开即是对话框：不再多做一层入口页
    path: '/',
    name: 'chat',
    component: () => import('@/views/Chat.vue'),
    meta: { title: 'Voxio 在线客服' }
  },
  {
    // 兜底：未知来源页统一回到对话框，避免嵌入站点时 404
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.VITE_APP_BASE_PATH || '/'),
  routes
})

router.afterEach((to) => {
  document.title = to.meta.title || import.meta.env.VITE_APP_TITLE
})

export default router
