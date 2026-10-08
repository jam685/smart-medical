<template>
  <svg :width="size" :height="size" viewBox="0 0 200 200" fill="none"
       :class="['app-logo', { 'logo-spin': spin }]" role="img" aria-label="小林智疗">
    <defs>
      <linearGradient id="appLogoBubble" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="#E3F2FF"/>
        <stop offset="100%" stop-color="#A9D3F8"/>
      </linearGradient>
      <linearGradient id="appLogoCross" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="#B7DEFB"/>
        <stop offset="100%" stop-color="#86C1F5"/>
      </linearGradient>
    </defs>

    <!-- 白色底 + 蓝色圆环 -->
    <circle cx="100" cy="100" r="90" fill="#ffffff"/>
    <circle cx="100" cy="100" r="86" fill="none" stroke="#1E7AE6" stroke-width="7"/>

    <!-- 对话气泡尾巴（先画，被气泡主体压住接缝） -->
    <path d="M63 113 L47 134 L83 117 Z" fill="url(#appLogoBubble)" stroke="#1E7AE6" stroke-width="5" stroke-linejoin="round"/>
    <path d="M137 113 L153 134 L117 117 Z" fill="url(#appLogoBubble)" stroke="#1E7AE6" stroke-width="5" stroke-linejoin="round"/>

    <!-- 双对话气泡（合成心形） -->
    <ellipse cx="75" cy="84" rx="47" ry="38" fill="url(#appLogoBubble)" stroke="#1E7AE6" stroke-width="5.5"/>
    <ellipse cx="125" cy="84" rx="47" ry="38" fill="url(#appLogoBubble)" stroke="#1E7AE6" stroke-width="5.5"/>
    <!-- 顶部心形凹陷（用白底切出 V 口） -->
    <path d="M100 46 L88 66 H112 Z" fill="#ffffff"/>

    <!-- 医疗十字 -->
    <path d="M96 64 H104 A6 6 0 0 1 110 70 V86 H128 A6 6 0 0 1 134 92 V100 A6 6 0 0 1 128 106 H110 V122 A6 6 0 0 1 104 128 H96 A6 6 0 0 1 90 122 V106 H72 A6 6 0 0 1 66 100 V92 A6 6 0 0 1 72 86 H90 V70 A6 6 0 0 1 96 64 Z"
          fill="url(#appLogoCross)" stroke="#1E7AE6" stroke-width="5" stroke-linejoin="round"/>

    <!-- 十字下延的柄 -->
    <path d="M100 128 V162" stroke="#1E7AE6" stroke-width="7" stroke-linecap="round"/>

    <!-- 心电图折线 -->
    <path d="M34 150 H80 L91 128 L100 172 L109 150 H166"
          fill="none" stroke="#1E7AE6" stroke-width="6.5"
          stroke-linecap="round" stroke-linejoin="round"/>

    <!-- 红点 -->
    <circle cx="127" cy="150" r="11" fill="#E53935"/>
  </svg>
</template>

<script setup>
defineProps({
  size: { type: [Number, String], default: 40 },
  spin: { type: Boolean, default: false }
})
</script>

<style scoped>
.app-logo { display: block; flex-shrink: 0; }
.logo-spin { animation: logo-spin 8s linear infinite; }
@keyframes logo-spin { to { transform: rotate(360deg); } }
</style>
