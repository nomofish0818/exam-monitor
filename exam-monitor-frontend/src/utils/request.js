import axios from 'axios';
import { ElMessage } from 'element-plus';

const service = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 5000
});

// 请求拦截器：自动注入 Token
service.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers['Authorization'] = token;
  }
  return config;
}, error => Promise.reject(error));

// 响应拦截器：处理 401 登录失效
service.interceptors.response.use(response => {
  return response.data;
}, error => {
  if (error.response && error.response.status === 401) {
    ElMessage.error('登录失效，请重新登录');
    localStorage.clear();
    window.location.href = '/login'; // 强制跳转回登录页
  }
  return Promise.reject(error);
});

export default service;