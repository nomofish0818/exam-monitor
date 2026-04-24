<template>
  <div id="app">
    <nav style="padding: 10px; background: #f4f4f4; margin-bottom: 20px;">
      <strong>线上考试监控系统</strong> | 
      <router-link to="/student/exam">学生端</router-link> | 
      <router-link to="/teacher/dashboard">教师端</router-link>
    </nav>

    <router-view></router-view>
  </div>
</template>

<style>
/* 全域样式 */
#app {
  font-family: Arial, sans-serif;
  color: #2c3e50;
}
</style>