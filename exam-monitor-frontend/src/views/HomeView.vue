<template>
  <div style="padding: 50px; text-align: center; font-family: sans-serif;">
    <h1>智能監考系統開發入口</h1>
    <p>請選擇進入的角色頁面進行測試：</p>
    <div style="margin-top: 30px; display: flex; justify-content: center; gap: 20px;">
      <button @click="$router.push('/student/exam')" style="padding: 15px 30px; cursor: pointer; background: #409eff; color: white; border: none; border-radius: 5px;">
        進入學生考試端 (ID: 202)
      </button>
      <button @click="$router.push('/teacher/dashboard')" style="padding: 15px 30px; cursor: pointer; background: #67c23a; color: white; border: none; border-radius: 5px;">
        進入教師監控端 (ID: 101)
      </button>
    </div>
  </div>
</template>

<script setup>
// 空的 script 即可
</script>