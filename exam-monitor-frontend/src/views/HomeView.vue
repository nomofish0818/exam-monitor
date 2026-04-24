<template>
  <div class="home-container">
    <div class="glass-card">
      <h1>智能監考系統入口</h1>
      
      <div class="button-group">
        <el-button type="primary" size="large" @click="handleEntry('student')">
          進入學生考試端
        </el-button>
        <el-button type="success" size="large" @click="handleEntry('teacher')">
          進入教師監控端
        </el-button>
      </div>

      <div v-if="currentUser" class="status-footer">
        <el-divider />
        <p>
          當前用戶：<strong>{{ currentUser }}</strong> 
          <el-tag :type="currentRole == 1 ? 'danger' : 'info'" size="small" style="margin-left: 8px">
            {{ roleDisplayName }}
          </el-tag>
        </p>
        <el-button link type="primary" @click="handleLogout">退出登錄</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';

const router = useRouter();

// 從本地緩存動態獲取
const currentUser = ref(localStorage.getItem('userId'));
const currentRole = ref(localStorage.getItem('userRole'));

// 🚨 修正判定邏輯：確保 1 是老師，其他是學生
const roleDisplayName = computed(() => {
  const role = localStorage.getItem('userRole'); // 实时从缓存拿
  if (!role) return '訪客';
  // 🚨 务必确认这里的逻辑：1 是老师，2 是学生
  return role == '1' ? '監考教師' : '參試學生';
});

const handleEntry = (type) => {
  const token = localStorage.getItem('token');
  if (!token) {
    ElMessage.info('請先登錄');
    router.push('/login');
    return;
  }

  // 權限攔截逻辑
  if (type === 'student' && currentRole.value == 1) {
    ElMessage.warning('您當前為教師身份，不可參加考試');
    router.push('/teacher/dashboard');
  } else if (type === 'teacher' && currentRole.value == 2) {
    ElMessage.warning('您當前為學生身份，無權進入監控大廳');
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