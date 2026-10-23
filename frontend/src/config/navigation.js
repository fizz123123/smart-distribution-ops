/**
 * 側邊選單。只放已經串好後端的頁面；之後完成一個畫面就加一項。
 * screen：畫面編號，對應 12 號文件第 3 節，也是 findNavigationItem 的查詢鍵
 * icon：AppIcon 的名稱
 * permission：有這個權限才看得到；approval：有任何核准權限才看得到；都沒寫代表所有人可見
 * children：有子選單的模組，例如 { label, icon, children: [{ label, to, screen, permission }] }
 * 路由的 meta.navigation 使用這裡的項目，選單與路由守衛才會依同一份權限判斷。
 */
export const navigation = [
  { label: '工作台', icon: 'home', to: '/', screen: 'HOME-01' },
  {
    label: '使用者與權限',
    icon: 'gear',
    to: '/admin/users',
    screen: 'ADM-01',
    permission: 'USER_MANAGE',
  },
]

export function canAccess(item, authStore) {
  if (item.approval) {
    return authStore.canApprove
  }
  if (item.permission) {
    return authStore.hasPermission(item.permission)
  }
  return true
}

// 依權限過濾選單；子選單全部看不到時，整個模組也不顯示
export function visibleNavigation(authStore) {
  return navigation
    .map((item) =>
      item.children
        ? { ...item, children: item.children.filter((child) => canAccess(child, authStore)) }
        : item,
    )
    .filter((item) => (item.children ? item.children.length > 0 : canAccess(item, authStore)))
}

export function findNavigationItem(screen) {
  return navigation
    .flatMap((item) => item.children ?? [item])
    .find((item) => item.screen === screen)
}
