// 使用者與權限：串接後端的 /api/v1/users（UserController）。
// 欄位依後端 DTO：UserSummaryResponse、UserDetailResponse、CreateUserRequest…
import http from './http'

// GET /users?page=&size=：使用者清單（分頁）
export async function fetchUsers(params = { page: 0, size: 100 }) {
  const { data } = await http.get('/users', { params })
  return data
}

// GET /users/{id}：使用者詳情（含角色、實際權限、個別授權）
export async function fetchUser(id) {
  const { data } = await http.get(`/users/${id}`)
  return data
}

// GET /users/authorization-options：可指派的角色與權限清單
export async function fetchAuthorizationOptions() {
  const { data } = await http.get('/users/authorization-options')
  return data
}

/**
 * 新增使用者：POST /users
 * @param {{ email: string, name: string, password: string }} body 密碼 8～20 字
 */
export async function createUser(body) {
  const { data } = await http.post('/users', body)
  return data
}

// 修改 Email、姓名：PATCH /users/{id}
export async function updateUser(id, { email, name, version }) {
  const { data } = await http.patch(`/users/${id}`, { email, name, version })
  return data
}

// 停用／重新啟用：POST /users/{id}/disable、/activate（必填原因；不能操作自己）
export async function disableUser(id, { reason, version }) {
  const { data } = await http.post(`/users/${id}/disable`, { reason, version })
  return data
}

export async function activateUser(id, { reason, version }) {
  const { data } = await http.post(`/users/${id}/activate`, { reason, version })
  return data
}

// 整組取代角色：PUT /users/{id}/roles（至少 1 個；必填原因；APP_ADMIN 不能指派）
export async function replaceRoles(id, { roleCodes, reason, version }) {
  const { data } = await http.put(`/users/${id}/roles`, { roleCodes, reason, version })
  return data
}

/**
 * 整組取代個別授權：PUT /users/{id}/permission-grants（只能加授權；帳號管理權限不能授予）
 * @param {{ grants: { permissionCode, reason, validFrom, validTo }[], reason: string, version: number }} body
 */
export async function replacePermissionGrants(id, body) {
  const { data } = await http.put(`/users/${id}/permission-grants`, body)
  return data
}
