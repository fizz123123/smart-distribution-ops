<template>
  <section class="card notfound">
    <h1>找不到這個頁面</h1>
    <p class="sub">網址可能有誤，或你沒有查看權限。</p>
    <RouterLink to="/" class="btn btn-primary"
      >回到首頁<span class="arrow" aria-hidden="true">↗</span></RouterLink
    >
  </section>
</template>

<style scoped>
.notfound {
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
