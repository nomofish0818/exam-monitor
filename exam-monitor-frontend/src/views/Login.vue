<template>
  <div class="login-container">
    <el-card class="login-card">
      <h3>在线考试系统 - 学生登录</h3>
      <el-form :model="loginForm">
        <el-form-item label="学号">
          <el-input v-model="loginForm.id" placeholder="请输入 ID"></el-input>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="loginForm.password" type="password" show-password></el-input>
        </el-form-item>
        <el-button type="primary" @click="handleLogin" block>登 录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import request from '../utils/request'; // 使用刚才封装的工具
import { useRouter } from 'vue-router';

const loginForm = ref({ userId: '', password: '' });
const router = useRouter();

const handleLogin = async () => {
  try {
    const res = await request.post('/api/auth/login', loginForm.value);
    if (res.code === 200) {
      // 关键：将 Token 和 ID 存入持久化存储
      localStorage.setItem('token', res.data.token);
      localStorage.setItem('userId', res.data.userId);
      
      router.push('/student/exam');
    }
  } catch (err) {
    console.error('登录失败', err);
  }
};
</script>