<script setup>
// 我的工作台（HOME-01）：所有人登入後的第一頁。
// 之後放目前使用者的待核准、待處理與近期操作；後端有 API 之前先空著。
</script>

<template>
  <div class="page">
    <header class="page-head">
      <h1>工作台</h1>
    </header>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding-top: 2px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  min-height: var(--bar-h);
  padding: 4px var(--tools-w) 0 4px;
}

.page-head h1 {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 0.5px;
  color: var(--accent-strong);
}
</style>
