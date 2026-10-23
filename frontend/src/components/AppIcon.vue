<script setup>
// 線條圖示。裝飾用時保持 aria-hidden；需要讓螢幕閱讀器念出來時傳入 label。
const props = defineProps({
  name: { type: String, required: true },
  size: { type: Number, default: 18 },
  label: { type: String, default: '' },
})

const paths = {
  home: ['M3 10.5 12 3l9 7.5', 'M5 9.5V21h14V9.5'],
  check: [
    'M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z',
    'm8 12 3 3 5-6',
  ],
  user: ['M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z', 'M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6'],
  tag: ['M3 12V4a1 1 0 0 1 1-1h8l9 9-9 9z', 'M8 9.6a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2z'],
  factory: ['M3 21V10l6 3.5V10l6 3.5V5h6v16z'],
  doc: ['M6 3h9l4 4v14H6z', 'M9 12h7M9 16h7'],
  cart: [
    'M3 4h2.5l2.2 11h10.6L20.5 7H7',
    'M9 21a1.4 1.4 0 1 0 0-2.8A1.4 1.4 0 0 0 9 21z',
    'M17 21a1.4 1.4 0 1 0 0-2.8 1.4 1.4 0 0 0 0 2.8z',
  ],
  store: ['M3 9 12 4l9 5v12H3z', 'M7 21v-8h10v8'],
  coin: [
    'M5 6h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z',
    'M12 14.6a2.6 2.6 0 1 0 0-5.2 2.6 2.6 0 0 0 0 5.2z',
  ],
  chart: ['M4 20V4', 'M4 20h16', 'm7 15 4-4 3 3 5-6'],
  gear: [
    'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
    'M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1',
  ],
  alert: ['M12 3 2 20h20z', 'M12 10v4M12 17h.01'],
  clock: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M12 7v5l3 2'],
  ret: ['M9 14 4 9l5-5', 'M4 9h11a5 5 0 0 1 0 10h-4'],
  list: ['M9 6h11M9 12h11M9 18h11', 'M4 6h.01M4 12h.01M4 18h.01'],
  cal: [
    'M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z',
    'M3 10h18M8 3v4M16 3v4',
  ],
  truck: [
    'M2 6h11v10H2z',
    'M13 9h4l4 4v3h-8',
    'M6 19.8a1.8 1.8 0 1 0 0-3.6 1.8 1.8 0 0 0 0 3.6z',
    'M17 19.8a1.8 1.8 0 1 0 0-3.6 1.8 1.8 0 0 0 0 3.6z',
  ],
  box: ['M3 7l9-4 9 4v10l-9 4-9-4z', 'M3 7l9 4 9-4M12 11v10'],
  activity: ['M3 12h4l3-7 4 14 3-7h4'],
  inbound: ['M12 3v10', 'm8 9 4 4 4-4', 'M4 15v5h16v-5'],
  search: ['M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z', 'm20 20-3.5-3.5'],
  bell: ['M6 16V11a6 6 0 0 1 12 0v5l2 2H4z', 'M10 20a2 2 0 0 0 4 0'],
  refresh: ['M20 12a8 8 0 1 1-2.34-5.66', 'M20 4v5h-5'],
  logout: ['M10 4H5v16h5', 'M14 8l4 4-4 4', 'M18 12H9'],
  chevron: ['m6 9 6 6 6-6'],
  chevronLeft: ['m15 6-6 6 6 6'],
  chevronRight: ['m9 6 6 6-6 6'],
  plus: ['M12 5v14M5 12h14'],
  sparkle: [
    'M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z',
    'M19 16l.7 1.8L21.5 18.5l-1.8.7L19 21l-.7-1.8-1.8-.7 1.8-.7z',
  ],
  link: [
    'M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1',
    'M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1',
  ],
  layers: ['M12 3 3 8l9 5 9-5z', 'M3 13l9 5 9-5'],
  wallet: ['M3 7h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z', 'M3 7l12-4v4'],
  pin: [
    'M12 21s7-6.1 7-12a7 7 0 1 0-14 0c0 5.9 7 12 7 12z',
    'M12 11.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z',
  ],
  edit: ['M4 20h4L19 9l-4-4L4 16z'],
  more: ['M5 12h.01M12 12h.01M19 12h.01'],
  pause: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M10 9v6M14 9v6'],
  play: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'm10 8.5 5 3.5-5 3.5z'],
  send: ['M4 12 20 4l-6 16-3-7z', 'm11 13 9-9'],
  info: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M12 11v5M12 8h.01'],
  phone: [
    'M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a1 1 0 0 1-1 1A16 16 0 0 1 4 5a1 1 0 0 1 1-1z',
  ],
}
</script>

<template>
  <svg
    class="icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    :role="label ? 'img' : undefined"
    :aria-label="label || undefined"
    :aria-hidden="label ? undefined : 'true'"
    focusable="false"
  >
    <path v-for="(d, i) in paths[props.name] || []" :key="i" :d="d" />
  </svg>
</template>

<style scoped>
.icon {
  flex: none;
  display: block;
}
</style>
