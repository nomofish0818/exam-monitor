<template>
  <div class="home-container">
    <div class="glass-card">
      <h1>智能监考系统入口</h1>
      
      <div class="button-group">
        <el-button type="primary" size="large" @click="handleEntry('student')">
          进入学生考试端
        </el-button>
        <el-button type="success" size="large" @click="handleEntry('teacher')">
          进入教师监控端
        </el-button>
      </div>

      <div v-if="currentUser" class="status-footer">
        <el-divider />
        <p>
          当前用户：<strong>{{ currentUser }}</strong> 
          <el-tag :type="currentRole == 1 ? 'danger' : 'info'" size="small" style="margin-left: 8px">
            {{ roleDisplayName }}
          </el-tag>
        </p>
        <el-button link type="primary" @click="handleLogout">退出登录</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';

const router = useRouter();

// 从本地缓存动态获取
const currentUser = ref(localStorage.getItem('userId'));
const currentRole = ref(localStorage.getItem('userRole'));

// 🚨 修正判定逻辑：确保 1 是老师，其他是学生
const roleDisplayName = computed(() => {
  const role = localStorage.getItem('userRole'); // 实时从缓存拿
  if (!role) return '访客';
  // 🚨 务必确认这里的逻辑：1 是老师，2 是学生
  return role == '1' ? '监考教师' : '参试学生';
});

const handleEntry = (type) => {
  const token = localStorage.getItem('token');
  if (!token) {
    ElMessage.info('请先登录');
    router.push('/login');
    return;
  }

  // 权限拦截逻辑
  if (type === 'student' && currentRole.value == 1) {
    ElMessage.warning('您当前为教师身份，不可参加考试');
    router.push('/teacher/dashboard');
  } else if (type === 'teacher' && currentRole.value == 2) {
    ElMessage.warning('您当前为学生身份，无权进入监控大厅');
    router.push('/student/exam');
  } else {
    router.push(type === 'student' ? '/student/exam' : '/teacher/dashboard');
  }
};

const handleLogout = () => {
  localStorage.clear();
  window.location.reload();
};
</script>

<style scoped>
.home-container { height: 100vh; display: flex; justify-content: center; align-items: center; background: #eef2f7; }
.glass-card { background: white; padding: 50px; border-radius: 15px; box-shadow: 0 4px 20px rgba(0,0,0,0.08); text-align: center; }
.button-group { display: flex; gap: 20px; margin-top: 30px; }
.status-footer { margin-top: 30px; color: #666; font-size: 14px; }
</style>