<template>
  <section class="card forbidden">
    <h1>沒有查看權限</h1>
    <p class="sub">你的角色沒有這個頁面的權限，需要時請聯絡系統管理員。</p>
    <RouterLink to="/" class="btn btn-primary"
      >回到工作台<span class="arrow" aria-hidden="true">↗</span></RouterLink
    >
  </section>
</template>

<style scoped>
.forbidden {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 32px;
}

h1 {
  font-size: 22px;
  color: var(--accent-strong);
}
</style>
