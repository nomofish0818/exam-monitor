<template>
  <div class="login-container">
    <el-card class="login-card">
      <div class="login-title">
        <img src="../assets/vue.svg" class="logo" />
        <h3>監考系統 - 身份驗證</h3>
      </div>

      <el-form :model="loginForm" @submit.prevent="handleLogin">
        <el-form-item label="賬號">
          <el-input 
            v-model="loginForm.userId" 
            placeholder="請輸入學號或工號 (例: 1001)"
            prefix-icon="User"
          ></el-input>
        </el-form-item>

        <el-form-item label="密碼">
          <el-input 
            v-model="loginForm.password" 
            type="password" 
            placeholder="請輸入密碼"
            show-password
            prefix-icon="Lock"
          ></el-input>
        </el-form-item>

        <el-button 
          type="primary" 
          @click="handleLogin" 
          class="login-btn"
        >
          立即登錄
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import request from '../utils/request';

const router = useRouter();
const loginForm = ref({ userId: '', password: '' });

const handleLogin = async () => {
  if (!loginForm.value.userId || !loginForm.value.password) {
    ElMessage.warning('賬號和密碼均不能為空');
    return;
  }

  try {
    const res = await request.post('/api/auth/login', loginForm.value);
    
    // 💡【排錯神器】在控制台打印完整的 res，看看攔截器到底返回了什麼結構
    console.log('--- Axios 返回的完整結果 ---', res);

    // 🛡️【萬能取值法】不管攔截器怎麼剝，我們都能精準拿到真實數據：
    // 情況 1: 攔截器沒剝 (返回原生 axios response) -> 取 res.data.data
    // 情況 2: 攔截器剝了一層 (返回 res.data) -> 取 res.data
    // 情況 3: 攔截器剝了兩層 (直接返回了 payload) -> 取 res 本身
    const payload = res.data?.data || res.data || res;
    
    // 獲取狀態碼 (兼容不同層級)
    const responseCode = res.data?.code || res.code;

    if (responseCode === 200) {
      // 提取並強轉為字符串，避免數字 1 和 字符串 '1' 的嚴格比較問題
      const role = String(payload.role); 
      const token = payload.token;
      
      console.log('--- 最終解析到的 Role 是:', role, '---');

      // 🚨 如果還是 undefined，說明前端真的沒拿到這個字段，阻止跳轉並報錯
      if (role === 'undefined' || !role) {
         ElMessage.error('嚴重錯誤：未能從後端解析到 role 字段！請按 F12 查看控制台。');
         return;
      }

      // 寫入緩存
      localStorage.setItem('token', token);
      localStorage.setItem('userId', loginForm.value.userId);
      localStorage.setItem('userRole', role); 
      
      ElMessage.success('登錄成功');

      // 精準跳轉
      if (role === '1') {
        router.push('/teacher/dashboard');
      } else if (role === '2') {
        router.push('/student/exam');
      } else {
        ElMessage.error('未知的角色權限，無法跳轉');
      }
    } else {
      ElMessage.error(res.message || res.data?.message || '登錄失敗');
    }
  } catch (err) {
    console.error('Login Error:', err);
    ElMessage.error('伺服器連線失敗');
  }
};
</script>

<style scoped>
.login-container { height: 100vh; display: flex; justify-content: center; align-items: center; background: #f0f2f5; }
.login-card { width: 400px; padding: 20px; }
.login-title { text-align: center; margin-bottom: 30px; }
.logo { width: 50px; margin-bottom: 10px; }
.login-btn { width: 100%; margin-top: 10px; height: 40px; }
</style>