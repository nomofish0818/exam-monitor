<template>
  <div class="login-container">
    <el-card class="login-card">
      <div class="login-title">
        <img src="../assets/vue.svg" class="logo" />
        <h3>监考系统 - 身份验证</h3>
      </div>

      <el-form :model="loginForm" @submit.prevent="handleLogin">
        <el-form-item label="账号">
          <el-input 
            v-model="loginForm.userId" 
            placeholder="请输入学号或工号 (例: 1001)"
            prefix-icon="User"
          ></el-input>
        </el-form-item>

        <el-form-item label="密码">
          <el-input 
            v-model="loginForm.password" 
            type="password" 
            placeholder="请输入密码"
            show-password
            prefix-icon="Lock"
          ></el-input>
        </el-form-item>

        <el-button 
          type="primary" 
          @click="handleLogin" 
          class="login-btn"
        >
          立即登录
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
    ElMessage.warning('账号和密码均不能为空');
    return;
  }

  try {
    const res = await request.post('/api/auth/login', loginForm.value);
    
    // 💡【排错神器】在控制台打印完整的 res，看看拦截器到底返回了什么结构
    console.log('--- Axios 返回的完整结果 ---', res);

    // 🛡️【万能取值法】不管拦截器怎么剥，我们都能精准拿到真实数据：
    // 情况 1: 拦截器没剥 (返回原生 axios response) -> 取 res.data.data
    // 情况 2: 拦截器剥了一层 (返回 res.data) -> 取 res.data
    // 情况 3: 拦截器剥了两层 (直接返回了 payload) -> 取 res 本身
    const payload = res.data?.data || res.data || res;
    
    // 获取状态码 (兼容不同层级)
    const responseCode = res.data?.code || res.code;

    if (responseCode === 200) {
      // 提取并强转为字符串，避免数字 1 和 字符串 '1' 的严格比较问题
      const role = String(payload.role); 
      const token = payload.token;
      
      console.log('--- 最终解析到的 Role 是:', role, '---');

      // 🚨 如果还是 undefined，说明前端真的没拿到这个字段，阻止跳转并报错
      if (role === 'undefined' || !role) {
         ElMessage.error('严重错误：未能从后端解析到 role 字段！请按 F12 查看控制台。');
         return;
      }

      // 写入缓存
      localStorage.setItem('token', token);
      localStorage.setItem('userId', loginForm.value.userId);
      localStorage.setItem('userRole', role); 
      
      ElMessage.success('登录成功');

      // 精准跳转
      if (role === '1') {
        router.push('/teacher/dashboard');
      } else if (role === '2') {
        router.push('/student/exam');
      } else {
        ElMessage.error('未知的角色权限，无法跳转');
      }
    } else {
      ElMessage.error(res.message || res.data?.message || '登录失败');
    }
  } catch (err) {
    console.error('Login Error:', err);
    ElMessage.error('服务器连线失败');
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