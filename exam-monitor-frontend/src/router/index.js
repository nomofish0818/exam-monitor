import { createRouter, createWebHistory } from 'vue-router'
// 🚨 必须引入 ElMessage，否则路由守卫报错会导致整站跳转失效
import { ElMessage } from 'element-plus'

const routes = [
  { path: '/', name: 'Home', component: () => import('../views/HomeView.vue') },
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
  { path: '/student/exam', name: 'StudentExam', component: () => import('../views/student/ExamPaper.vue') },
  { path: '/teacher/dashboard', name: 'TeacherDashboard', component: () => import('../views/teacher/TeacherDashboard.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token');
  // 🚨 修复：直接获取字符串，不进行 parseInt 避免 NaN 问题
  const role = localStorage.getItem('userRole'); 

  // 1. 免登录白名单
  if (to.path === '/login' || to.path === '/') {
    return next();
  }

  // 2. 登录检查
  if (!token) {
    ElMessage.warning('请先登录');
    return next('/login');
  }

  // 3. 权限分流 (1-教师, 2-学生)
  // 使用 == 模糊匹配，兼容字符串和数字
  if (to.path.startsWith('/student') && role != '2') {
    ElMessage.error('您的帐号无权进入学生考场');
    return next('/teacher/dashboard'); // 老师强制去教师端
  }

  if (to.path.startsWith('/teacher') && role != '1') {
    ElMessage.error('您的帐号无权进入监考大厅');
    return next('/student/exam'); // 学生强制去学生端
  }

  next();
});

export default router