import { createRouter, createWebHistory } from 'vue-router'

// 1. 定義路由配置
const routes = [
  // 根路徑：開發測試導覽頁
{
  path: '/',
  name: 'Home',
  // 刪除原本的 component: { template: '...' }，改成下面這樣：
  component: () => import('../views/HomeView.vue')
},
  
  // 學生端：考試頁面
  {
    path: '/student/exam',
    name: 'StudentExam',
    // 使用動態 import 實現懶加載
    component: () => import('../views/student/ExamPaper.vue'),
    meta: { title: '學生考試中' }
  },

  // 教師端：監控大屏
  {
    path: '/teacher/dashboard',
    name: 'TeacherDashboard',
    component: () => import('../views/teacher/TeacherDashboard.vue'),
    meta: { title: '監考管理大屏' }
  },

  // 404 頁面重定向 (選配：當輸入錯誤網址時回到首頁)
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

// 2. 創建路由實例
const router = createRouter({
  // 使用 HTML5 History 模式，網址不會有 # 號
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

// 3. 全域前置守衛：動態修改頁面標題 (選配)
router.beforeEach((to, from, next) => {
  if (to.meta.title) {
    document.title = to.meta.title;
  }
  next();
})

export default router