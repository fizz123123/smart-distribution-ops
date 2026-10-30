<script setup>
import { computed } from 'vue'
import { roleChoices } from '@/config/identity'

// 角色勾選清單（新增使用者、編輯角色共用）。
// 主管包含職員角色（後端 includedRoleCodes）：勾選主管時，被包含的職員角色顯示為已勾選、反灰、不能點，
// 框不上色；這些角色不放進 v-model，送出時只會有主管角色。
const selected = defineModel({ type: Array, required: true })

const props = defineProps({
  roles: { type: Array, required: true }, // 可指派的角色 [{ roleCode, name, includedRoleCodes }]
})

const assignable = computed(() => props.roles.filter((r) => r.roleCode !== 'APP_ADMIN'))
const choices = computed(() => roleChoices(assignable.value, selected.value))

function toggle(role, checked) {
  if (!checked) {
    selected.value = selected.value.filter((code) => code !== role.roleCode)
    return
  }
  // 勾選主管時，把它包含的職員角色從勾選中拿掉（改由主管涵蓋）；取消主管後職員角色恢復未勾選
  const included = role.includedRoleCodes ?? []
  selected.value = [...selected.value.filter((code) => !included.includes(code)), role.roleCode]
}
</script>

<template>
  <fieldset class="roles">
    <legend class="sr-only">角色</legend>
    <label v-for="r in choices" :key="r.roleCode" class="role" :class="{ locked: r.includedBy }">
      <input
        type="checkbox"
        :checked="r.checked"
        :disabled="Boolean(r.includedBy)"
        @change="toggle(r, $event.target.checked)"
      />
      <span>{{ r.name }}</span>
    </label>
  </fieldset>
</template>

<style scoped>
.roles {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  width: 100%;
  margin: 0;
  padding: 0;
  border: 0;
}

.role {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  line-height: 1.4;
  cursor: pointer;
}

.role:has(input:checked) {
  border-color: var(--accent);
  background: var(--info-soft);
}

.role.locked,
.role.locked:has(input:checked) {
  border-color: var(--line);
  background: transparent;
  cursor: not-allowed;
}

.role input {
  width: 16px;
  height: 16px;
  accent-color: var(--accent-strong);
}

.role input:disabled {
  accent-color: var(--ink-muted);
  cursor: not-allowed;
}

.role span {
  font-weight: 600;
}

.role.locked span {
  color: var(--ink-3);
}
</style>
