import http from './http'

// POST /api/v1/auth/login：用 Email 和密碼登入，回傳 JWT 與目前使用者
export async function login(email, password) {
  const { data } = await http.post('/auth/login', { email, password })
  return data
}

// GET /api/v1/users/me：目前使用者 { id, email, name, roles[], permissions[] }
export async function fetchCurrentUser() {
  const { data } = await http.get('/users/me')
  return data
}
