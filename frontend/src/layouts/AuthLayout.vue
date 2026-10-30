<script setup>
// 不用登入的頁面外框（登入、設定密碼）：背景、左側品牌區、右側放各頁的卡片。
</script>

<template>
  <div class="blobs" aria-hidden="true"><i class="b1"></i><i class="b2"></i><i class="b3"></i></div>

  <main class="auth-page">
    <section class="brand">
      <div class="logo" aria-hidden="true">
        <span class="logo-mark mono">TR</span>
        <span>THERMAL<em>ROLL</em></span>
      </div>
      <p class="headline">保溫與防蝕材料<br />經銷營運系統</p>
      <p class="lead">客戶、訂單、採購、庫存、出貨與應收，<br />在同一套系統裡追蹤到底。</p>
      <ul class="points">
        <li><span class="dot" aria-hidden="true"></span>主管核准後才預留庫存</li>
        <li><span class="dot" aria-hidden="true"></span>缺料自動標示，一鍵轉採購</li>
        <li><span class="dot" aria-hidden="true"></span>出貨過帳即建立應收</li>
      </ul>
    </section>

    <slot />
  </main>

  <footer class="foot mono">THERMAL ROLL</footer>
</template>

<style scoped>
.blobs i {
  position: fixed;
  border-radius: 50%;
  filter: blur(90px);
  z-index: 0;
  pointer-events: none;
}

.b1 {
  width: 760px;
  height: 760px;
  left: -220px;
  top: -240px;
  background: var(--brand-teal);
  opacity: 0.42;
}

.b2 {
  width: 720px;
  height: 720px;
  right: -200px;
  bottom: -240px;
  background: var(--brand-ocean);
  opacity: 0.3;
}

.b3 {
  width: 420px;
  height: 420px;
  left: 40%;
  top: 30%;
  background: var(--brand-sage);
  opacity: 0.3;
}

.auth-page {
  position: relative;
  z-index: 1;
  min-height: calc(100vh - 40px);
  display: grid;
  grid-template-columns: minmax(0, 1fr) 440px;
  align-items: center;
  gap: 64px;
  max-width: 1080px;
  margin: 0 auto;
  padding: 48px 32px 24px;
}

/* 左側品牌區 */
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 0.4px;
  color: var(--ink);
}

.logo em {
  font-style: normal;
  color: var(--accent);
  margin-left: 6px;
}

.logo-mark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: var(--r-sm);
  background: var(--accent-strong);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.headline {
  margin-top: 40px;
  font-size: 40px;
  line-height: 1.25;
  font-weight: 800;
  color: var(--accent-strong);
}

.lead {
  margin-top: 16px;
  font-size: 16px;
  line-height: 1.7;
  color: var(--ink-2);
}

.points {
  list-style: none;
  margin: 32px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-size: 14px;
  color: var(--ink);
}

.points li {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dot {
  width: 8px;
  height: 8px;
  flex: none;
  background: var(--accent);
}

.foot {
  position: relative;
  z-index: 1;
  text-align: center;
  font-size: 11px;
  color: var(--ink-3);
  padding-bottom: 16px;
}

@media (max-width: 900px) {
  .auth-page {
    grid-template-columns: minmax(0, 1fr);
    gap: 32px;
    padding: 32px 16px;
  }

  .headline {
    margin-top: 24px;
    font-size: 30px;
  }

  .points {
    display: none;
  }
}
</style>
