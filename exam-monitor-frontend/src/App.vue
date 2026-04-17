<template>
  <div id="app">
    <nav style="padding: 10px; background: #f4f4f4; margin-bottom: 20px;">
      <strong>線上考試監控系統</strong> | 
      <router-link to="/student/exam">學生端</router-link> | 
      <router-link to="/teacher/dashboard">教師端</router-link>
    </nav>

    <router-view></router-view>
  </div>
</template>

<style>
/* 全域樣式 */
#app {
  font-family: Arial, sans-serif;
  color: #2c3e50;
}
</style>