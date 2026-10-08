<template>
  <div class="home-page">
    <!-- ===== 固定吸顶导航 ===== -->
    <header class="top-nav" :class="{ scrolled: navScrolled }">
      <div class="logo" @click="scrollTo('home')">
        <AppLogo :size="38"/>
        <span class="brand-font">小林智疗</span>
      </div>
      <nav class="nav-menu">
        <a v-for="item in navs" :key="item.id"
           class="nav-item"
           :class="{ active: activeSection === item.id }"
           @click="scrollTo(item.id)">
          {{ item.label }}
          <span class="nav-underline"></span>
        </a>
      </nav>
    </header>

    <main>
      <!-- ===== 区块① 首页 Hero ===== -->
      <section id="home" ref="home" class="section hero-section">
        <div class="bg-decor">
          <svg class="decor-circle c1" viewBox="0 0 200 200"><circle cx="100" cy="100" r="80" fill="none" stroke="#1E7AE6" stroke-width="1" stroke-dasharray="4 6"/></svg>
          <svg class="decor-circle c2" viewBox="0 0 200 200"><circle cx="100" cy="100" r="60" fill="none" stroke="#7A5AF8" stroke-width="1" stroke-dasharray="2 8"/></svg>
          <svg class="decor-line l1" viewBox="0 0 300 200"><path d="M10 180 Q80 60 160 120 T290 100" fill="none" stroke="#10B981" stroke-width="1.5" opacity="0.4"/></svg>
        </div>

        <div class="hero-inner">
          <div class="hero-badge">
            <span class="dot"></span> AI + 医疗 · 医生在环 · 全程合规
          </div>
          <h1 class="brand-font hero-title">小林智疗</h1>
          <p class="hero-slogan">AI 驱动的智慧医疗平台</p>
          <p class="hero-sub">让每一次问诊都更高效、更专业、更安心</p>

          <div class="hero-actions">
            <button class="hero-btn primary" @click="scrollTo('login')">
              立即进入
              <svg viewBox="0 0 16 16"><path d="M3 8h10M9 4l4 4-4 4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </button>
            <button class="hero-btn ghost" @click="scrollTo('about')">了解平台</button>
          </div>

          <div class="tech-badges">
            <span>🔒 全程加密</span>
            <span>🧠 AI 辅助</span>
            <span>📋 合规审计</span>
            <span>🏥 医生在环</span>
          </div>
        </div>

        <!-- 向下滚动提示 -->
        <div class="scroll-hint" @click="scrollTo('about')">
          <span>向下滑动</span>
          <div class="mouse"><div class="wheel"></div></div>
        </div>
      </section>

      <!-- ===== 区块② 背景简介 ===== -->
      <section id="about" ref="about" class="section about-section">
        <div class="container">
          <div class="section-head">
            <span class="section-tag">ABOUT US</span>
            <h2 class="brand-font">背景简介</h2>
            <p>人工智能 × 专业医疗，打通就医、接诊、管理三大场景</p>
          </div>

          <div class="about-grid">
            <!-- 左：文案 -->
            <div class="about-text">
              <h3>连接医患的智能医疗基础设施</h3>
              <p>
                <strong>小林智疗</strong>是一款融合人工智能与医疗专业能力的智慧医疗服务平台，
                服务覆盖<strong>患者就医、医生接诊、机构管理</strong>三大端，构建从预约挂号、在线问诊、
                报告解读到电子病历的全流程数字化闭环。
              </p>
              <p>
                平台以 <strong>AI 辅助诊断、智能报告解读、在线问诊</strong>为核心能力，
                让优质医疗资源触手可及；同时坚守「<strong>医生在环、AI 辅助</strong>」的合规底线——
                所有 AI 输出均经专业医生审核确认，敏感信息实时拦截，关键操作全程留痕。
              </p>
              <ul class="about-points">
                <li><i class="p-dot blue"></i>患者端：挂号 / 问诊 / 报告 / 病历一站式自助</li>
                <li><i class="p-dot green"></i>医生端：AI 分析辅助，接诊与电子签名高效协同</li>
                <li><i class="p-dot purple"></i>管理端：用户权限、审计监控、知识库统一治理</li>
              </ul>
            </div>

            <!-- 右：线性扁平插画 -->
            <div class="about-visual">
              <svg viewBox="0 0 460 380" fill="none" class="about-svg">
                <!-- 背景虚线环 -->
                <circle cx="230" cy="190" r="150" stroke="#1E7AE6" stroke-width="1.2" stroke-dasharray="5 9" opacity="0.35"/>
                <circle cx="230" cy="190" r="108" stroke="#7A5AF8" stroke-width="1" stroke-dasharray="3 8" opacity="0.3"/>

                <!-- 中央诊疗平台卡片 -->
                <rect x="140" y="120" width="180" height="130" rx="18" fill="#ffffff" stroke="#1E7AE6" stroke-width="2.5"/>
                <rect x="140" y="120" width="180" height="34" rx="18" fill="#1E7AE6"/>
                <rect x="140" y="140" width="180" height="14" fill="#1E7AE6"/>
                <circle cx="158" cy="137" r="4" fill="#fff" opacity="0.9"/>
                <circle cx="172" cy="137" r="4" fill="#fff" opacity="0.6"/>
                <circle cx="186" cy="137" r="4" fill="#fff" opacity="0.4"/>
                <!-- 屏幕内十字 -->
                <path d="M222 172 h16 a6 6 0 0 1 6 6 v14 h14 a6 6 0 0 1 6 6 v12 a6 6 0 0 1-6 6 h-14 v14 a6 6 0 0 1-6 6 h-16 a6 6 0 0 1-6-6 v-14 h-14 a6 6 0 0 1-6-6 v-12 a6 6 0 0 1 6-6 h14 v-14 a6 6 0 0 1 6-6z"
                      fill="#E3F2FF" stroke="#1E7AE6" stroke-width="2.2" stroke-linejoin="round"/>
                <!-- 底座 -->
                <path d="M205 250 h50 l10 26 h-70z" fill="#D6E8FF" stroke="#1E7AE6" stroke-width="2.2" stroke-linejoin="round"/>
                <rect x="180" y="276" width="100" height="10" rx="5" fill="#1E7AE6" opacity="0.85"/>

                <!-- 左浮动卡片：患者 -->
                <g class="float-card fc-left">
                  <rect x="30" y="150" width="96" height="86" rx="14" fill="#fff" stroke="#1E7AE6" stroke-width="2"/>
                  <circle cx="60" cy="182" r="13" fill="none" stroke="#1E7AE6" stroke-width="2.2"/>
                  <path d="M38 224 q0-26 22-26 q22 0 22 26" fill="#E3F2FF" stroke="#1E7AE6" stroke-width="2.2"/>
                  <path d="M96 166 h18 a8 8 0 0 1 8 8 v14 a8 8 0 0 1-8 8 h-6 l-10 9 v-9 z" fill="#EAF7FF" stroke="#1E7AE6" stroke-width="1.8"/>
                </g>
                <path d="M126 192 C132 192 134 188 140 188" stroke="#1E7AE6" stroke-width="2" stroke-dasharray="4 5" fill="none"/>

                <!-- 右浮动卡片：医生 -->
                <g class="float-card fc-right">
                  <rect x="334" y="150" width="96" height="86" rx="14" fill="#fff" stroke="#10B981" stroke-width="2"/>
                  <circle cx="366" cy="180" r="12" fill="none" stroke="#10B981" stroke-width="2.2"/>
                  <path d="M344 224 q0-26 22-26 q22 0 22 26" fill="#E7FAF3" stroke="#10B981" stroke-width="2.2"/>
                  <path d="M358 198 v20 M348 208 h20" stroke="#10B981" stroke-width="2" stroke-linecap="round"/>
                  <!-- 听诊器 -->
                  <path d="M378 172 c8 0 12 7 12 13 c0 6-4 9-9 9" stroke="#7A5AF8" stroke-width="1.8" fill="none"/>
                  <circle cx="381" cy="196" r="3.5" fill="#fff" stroke="#7A5AF8" stroke-width="1.6"/>
                </g>
                <path d="M320 188 c-6 0-8 4-14 4" stroke="#10B981" stroke-width="2" stroke-dasharray="4 5" fill="none"/>

                <!-- AI 星标芯片（紫色，AI 标识） -->
                <g class="float-card fc-ai">
                  <rect x="300" y="66" width="74" height="46" rx="12" fill="#F3EFFF" stroke="#7A5AF8" stroke-width="2"/>
                  <path d="M320 89 l5-11 5 11 11 5-11 5-5 11-5-11-11-5z" fill="#7A5AF8"/>
                  <text x="352" y="94" text-anchor="middle" font-size="13" fill="#7A5AF8" font-weight="700" font-family="sans-serif">AI</text>
                </g>
                <path d="M330 112 c0 10-8 12-14 16" stroke="#7A5AF8" stroke-width="1.8" stroke-dasharray="4 5" fill="none"/>

                <!-- 底部数据点连线 -->
                <circle cx="150" cy="330" r="7" fill="#1E7AE6"/>
                <circle cx="230" cy="330" r="7" fill="#7A5AF8"/>
                <circle cx="310" cy="330" r="7" fill="#10B981"/>
                <path d="M157 330 h66 M237 330 h66" stroke="#94A3B8" stroke-width="2" stroke-dasharray="5 5"/>
              </svg>
            </div>
          </div>

          <!-- 4 张功能特色卡片 -->
          <div class="feature-cards">
            <div class="feature-card">
              <div class="fc-icon blue">
                <svg viewBox="0 0 24 24" fill="none">
                  <path d="M21 11.5a8.38 8.38 0 0 1-8.5 8.5 8.5 8.5 0 0 1-3.8-.9L3 21l1.9-5.7a8.5 8.5 0 1 1 16.1-3.8z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>
                  <path d="M12 8v6M9 11h6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                </svg>
              </div>
              <h4>AI 智能问诊</h4>
              <p>7×24 小时智能导诊与健康咨询，精准理解病情描述，智能追问不遗漏</p>
            </div>
            <div class="feature-card">
              <div class="fc-icon purple">
                <svg viewBox="0 0 24 24" fill="none">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>
                  <path d="M14 2v6h6M8 13h8M8 17h5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                </svg>
              </div>
              <h4>报告智能解读</h4>
              <p>上传检查报告，AI 生成通俗易懂的解读，经医生审核后发布，可信可读</p>
            </div>
            <div class="feature-card">
              <div class="fc-icon green">
                <svg viewBox="0 0 24 24" fill="none">
                  <path d="M3 12h4l2.5-7 5 14L17 12h4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </div>
              <h4>电子病历协同</h4>
              <p>在线问诊自动生成病历草稿，修改次数受控，医生电子签名后权威发布</p>
            </div>
            <div class="feature-card">
              <div class="fc-icon red">
                <svg viewBox="0 0 24 24" fill="none">
                  <path d="M12 2l8 4v6c0 5-3.4 8.5-8 10-4.6-1.5-8-5-8-10V6z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>
                  <path d="M9 12l2 2 4-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </div>
              <h4>安全合规审计</h4>
              <p>全链路数据加密，敏感内容实时拦截，管理操作 100% 留痕、可追溯</p>
            </div>
          </div>
        </div>
      </section>

      <!-- ===== 区块③ 登录（角色入口） ===== -->
      <section id="login" ref="login" class="section login-section">
        <div class="container">
          <div class="section-head">
            <span class="section-tag">LOGIN</span>
            <h2 class="brand-font">选择登录入口</h2>
            <p>请根据您的身份选择对应端口进入系统</p>
          </div>

          <div class="role-cards">
            <!-- 患者卡片 -->
            <div class="role-card patient" @click="$router.push('/login/patient')">
              <div class="card-illustration">
                <svg viewBox="0 0 120 120" fill="none">
                  <circle cx="60" cy="38" r="16" stroke="#1E7AE6" stroke-width="2"/>
                  <path d="M34 108 Q34 72 60 72 Q86 72 86 108" stroke="#1E7AE6" stroke-width="2" fill="none"/>
                  <path d="M78 56 C90 56 96 64 96 74 C96 82 90 88 82 88" stroke="#7A5AF8" stroke-width="2" fill="none"/>
                  <circle cx="82" cy="92" r="5" stroke="#7A5AF8" stroke-width="2" fill="white"/>
                  <rect x="24" y="24" width="20" height="20" rx="4" fill="#E53935" opacity="0.1"/>
                  <path d="M34 28 V40 M28 34 H40" stroke="#E53935" stroke-width="2" stroke-linecap="round"/>
                </svg>
              </div>
              <div class="card-info">
                <h3>患者入口</h3>
                <p>在线挂号 · 查看报告 · AI 问诊</p>
                <div class="card-features">
                  <span><svg viewBox="0 0 16 16"><path d="M3 8h10M8 3v10" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>在线挂号</span>
                  <span><svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="5" stroke="currentColor" stroke-width="2"/></svg>AI 解读</span>
                  <span><svg viewBox="0 0 16 16"><path d="M3 4h10M3 8h10M3 12h6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>电子病历</span>
                </div>
                <button class="enter-btn patient-btn">
                  进入患者端
                  <svg viewBox="0 0 16 16"><path d="M3 8h10M9 4l4 4-4 4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </button>
              </div>
            </div>

            <!-- 医生卡片 -->
            <div class="role-card doctor" @click="$router.push('/login/doctor')">
              <div class="card-illustration">
                <svg viewBox="0 0 120 120" fill="none">
                  <circle cx="60" cy="36" r="14" stroke="#10B981" stroke-width="2"/>
                  <path d="M36 108 Q36 70 60 70 Q84 70 84 108" stroke="#10B981" stroke-width="2" fill="white"/>
                  <path d="M54 70 L60 82 L66 70" stroke="#10B981" stroke-width="2" fill="none"/>
                  <path d="M48 48 C40 48 36 56 36 64 C36 72 42 76 50 76" stroke="#1E7AE6" stroke-width="2" fill="none"/>
                  <circle cx="50" cy="80" r="4" stroke="#1E7AE6" stroke-width="2" fill="white"/>
                  <rect x="72" y="86" width="16" height="16" rx="2" fill="#1E7AE6"/>
                  <path d="M80 89 V99 M75 94 H85" stroke="white" stroke-width="2" stroke-linecap="round"/>
                </svg>
              </div>
              <div class="card-info">
                <h3>医生入口</h3>
                <p>AI 辅助诊断 · 在线接诊 · 电子签名</p>
                <div class="card-features">
                  <span><svg viewBox="0 0 16 16"><path d="M2 4h12M2 8h12M2 12h8" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>AI 分析</span>
                  <span><svg viewBox="0 0 16 16"><path d="M3 12h10M5 12V6h6v6" stroke="currentColor" stroke-width="2" fill="none"/></svg>在线接诊</span>
                  <span><svg viewBox="0 0 16 16"><path d="M12 2l2 2-8 8H4v-2l8-8z" stroke="currentColor" stroke-width="2" fill="none"/></svg>电子签名</span>
                </div>
                <button class="enter-btn doctor-btn">
                  进入医生端
                  <svg viewBox="0 0 16 16"><path d="M3 8h10M9 4l4 4-4 4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </button>
              </div>
            </div>

            <!-- 管理员卡片 -->
            <div class="role-card admin" @click="$router.push('/login/admin')">
              <div class="card-illustration">
                <svg viewBox="0 0 120 120" fill="none">
                  <circle cx="60" cy="40" r="14" stroke="#7A5AF8" stroke-width="2"/>
                  <path d="M34 108 Q34 72 60 72 Q86 72 86 108" stroke="#7A5AF8" stroke-width="2" fill="white"/>
                  <path d="M58 72 L62 72 L64 86 L60 92 L56 86 Z" fill="#7A5AF8" opacity="0.2"/>
                  <path d="M22 24 L36 24 L36 36 Q36 48 29 52 Q22 48 22 36 Z" stroke="#7A5AF8" stroke-width="2" fill="none"/>
                  <path d="M27 32 L27 42 M22 37 L32 37" stroke="#7A5AF8" stroke-width="1.5" stroke-linecap="round"/>
                  <circle cx="96" cy="32" r="10" stroke="#7A5AF8" stroke-width="2" fill="none"/>
                  <circle cx="96" cy="32" r="4" fill="#7A5AF8" opacity="0.3"/>
                </svg>
              </div>
              <div class="card-info">
                <h3>管理员入口</h3>
                <p>用户管理 · 审计监控 · 系统配置</p>
                <div class="card-features">
                  <span><svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="5" stroke="currentColor" stroke-width="2"/><path d="M8 5v3l2 1" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>审计日志</span>
                  <span><svg viewBox="0 0 16 16"><path d="M3 8h10M11 4l4 4-4 4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>权限管理</span>
                  <span><svg viewBox="0 0 16 16"><rect x="2" y="4" width="12" height="8" rx="1" stroke="currentColor" stroke-width="2"/></svg>知识库</span>
                </div>
                <button class="enter-btn admin-btn">
                  进入管理端
                  <svg viewBox="0 0 16 16"><path d="M3 8h10M9 4l4 4-4 4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- ===== 区块④ 版权说明 ===== -->
      <section id="copyright" ref="copyright" class="section copyright-section">
        <div class="container footer-grid">
          <div class="footer-brand">
            <div class="footer-logo">
              <AppLogo :size="42"/>
              <span class="brand-font">小林智疗</span>
            </div>
            <p class="footer-slogan">AI 驱动的智慧医疗平台<br/>让每一次问诊都更高效、更专业、更安心</p>
            <div class="footer-tags">
              <span>🔒 全程加密</span><span>🏥 医生在环</span><span>📋 合规审计</span>
            </div>
          </div>

          <div class="footer-col">
            <h5>联系方式</h5>
            <ul>
              <li>
                <svg viewBox="0 0 16 16"><path d="M3 2h3l1.5 4-2 1.5a11 11 0 0 0 5 5l1.5-2 4 1.5v3a1 1 0 0 1-1 1A13 13 0 0 1 2 3a1 1 0 0 1 1-1z" stroke="currentColor" stroke-width="1.4" fill="none"/></svg>
                客服热线：400-888-0000
              </li>
              <li>
                <svg viewBox="0 0 16 16"><rect x="2" y="3" width="12" height="10" rx="1.5" stroke="currentColor" stroke-width="1.4" fill="none"/><path d="M2 4.5l6 5 6-5" stroke="currentColor" stroke-width="1.4" fill="none"/></svg>
                service@xiaolin-med.com
              </li>
              <li>
                <svg viewBox="0 0 16 16"><path d="M8 1.5a5 5 0 0 1 5 5c0 3.5-5 8-5 8s-5-4.5-5-8a5 5 0 0 1 5-5z" stroke="currentColor" stroke-width="1.4" fill="none"/><circle cx="8" cy="6.5" r="1.8" stroke="currentColor" stroke-width="1.4"/></svg>
                上海市浦东新区智慧医疗产业园 88 号
              </li>
              <li class="service-time">服务时间：工作日 8:00 - 20:00</li>
            </ul>
          </div>

          <div class="footer-col">
            <h5>协议与条款</h5>
            <ul>
              <li class="link-item" @click="showAgreement('隐私政策')">隐私政策</li>
              <li class="link-item" @click="showAgreement('用户服务协议')">用户服务协议</li>
              <li class="link-item" @click="showAgreement('医疗免责声明')">医疗免责声明</li>
            </ul>
          </div>
        </div>

        <div class="copyright-bar">
          <p>© 2026 小林智疗 XiaoLin MediTech · 版权所有 · 保留一切权利</p>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import AppLogo from '@/components/AppLogo.vue'

const navs = [
  { id: 'home', label: '首页' },
  { id: 'about', label: '背景简介' },
  { id: 'login', label: '登录' },
  { id: 'copyright', label: '版权说明' }
]

const activeSection = ref('home')
const navScrolled = ref(false)

function scrollTo(id) {
  const el = document.getElementById(id)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function onScroll() {
  navScrolled.value = window.scrollY > 20
  // 计算当前可视区域最靠中的区块
  const mid = window.scrollY + window.innerHeight * 0.35
  let current = 'home'
  for (const n of navs) {
    const el = document.getElementById(n.id)
    if (el && el.offsetTop <= mid) current = n.id
  }
  activeSection.value = current
}

function showAgreement(name) {
  ElMessage.info(`「${name}」为 Demo 占位内容，正式版本将在此展示完整条款`)
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: #F6F9FE;
  color: #1a1a2e;
}

/* ===== 吸顶导航 ===== */
.top-nav {
  position: fixed; top: 0; left: 0; right: 0; z-index: 100;
  height: 64px; padding: 0 48px;
  display: flex; align-items: center; justify-content: space-between;
  background: rgba(255,255,255,0.75);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid transparent;
  transition: all 0.3s ease;
}
.top-nav.scrolled {
  background: rgba(255,255,255,0.92);
  border-bottom-color: rgba(30,122,230,0.12);
  box-shadow: 0 4px 24px rgba(15,23,42,0.06);
}
.logo { display: flex; align-items: center; gap: 10px; font-size: 20px; font-weight: 600; cursor: pointer; }
.nav-menu { display: flex; align-items: center; gap: 8px; }
.nav-item {
  position: relative; padding: 8px 18px; cursor: pointer;
  font-size: 15px; color: #475569; border-radius: 8px;
  transition: color 0.25s;
  white-space: nowrap;
}
.nav-item:hover { color: #1E7AE6; }
.nav-item.active { color: #1E7AE6; font-weight: 600; }
.nav-underline {
  position: absolute; left: 50%; bottom: 0; width: 0; height: 3px;
  background: linear-gradient(90deg, #1E7AE6, #7A5AF8);
  border-radius: 3px; transform: translateX(-50%);
  transition: width 0.3s ease;
}
.nav-item.active .nav-underline { width: 60%; }

/* ===== 通用区块 ===== */
.section { scroll-margin-top: 64px; position: relative; }
.container { max-width: 1200px; margin: 0 auto; padding: 0 32px; }
.section-head { text-align: center; margin-bottom: 56px; }
.section-tag {
  display: inline-block; font-size: 12px; font-weight: 700; letter-spacing: 3px;
  color: #1E7AE6; background: #E3F2FF; padding: 5px 16px; border-radius: 20px; margin-bottom: 14px;
}
.section-head h2 { font-size: 34px; font-weight: 700; margin: 0 0 10px; }
.section-head p { font-size: 15px; color: #64748b; margin: 0; }

/* ===== ① Hero ===== */
.hero-section {
  min-height: 100vh;
  background: linear-gradient(135deg, #F0F7FF 0%, #FAF5FF 55%, #F0FDF4 100%);
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
}
.bg-decor { position: absolute; inset: 0; pointer-events: none; }
.decor-circle { position: absolute; width: 400px; height: 400px; }
.c1 { top: -100px; right: -100px; opacity: 0.5; animation: spin 60s linear infinite; }
.c2 { bottom: -80px; left: -80px; opacity: 0.4; animation: spin 80s linear infinite reverse; }
.l1 { position: absolute; top: 30%; left: -50px; width: 400px; }
@keyframes spin { to { transform: rotate(360deg); } }

.hero-inner { position: relative; z-index: 2; text-align: center; padding: 80px 24px 0; }
.hero-badge {
  display: inline-flex; align-items: center; gap: 8px;
  background: rgba(255,255,255,0.85); border: 1px solid #D6E8FF;
  padding: 7px 18px; border-radius: 100px; font-size: 13px; color: #1E7AE6;
  box-shadow: 0 4px 16px rgba(30,122,230,0.08); margin-bottom: 28px;
}
.hero-badge .dot { width: 8px; height: 8px; border-radius: 50%; background: #10B981; animation: blink 1.6s infinite; }
@keyframes blink { 50% { opacity: 0.3; } }
.hero-title {
  font-size: 72px; font-weight: 700; margin: 0 0 8px;
  background: linear-gradient(135deg, #1E7AE6 0%, #7A5AF8 100%);
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
  letter-spacing: 10px;
}
.hero-slogan { font-size: 26px; font-weight: 600; color: #1a1a2e; margin: 0 0 10px; }
.hero-sub { font-size: 16px; color: #64748b; margin: 0 0 36px; }
.hero-actions { display: flex; gap: 16px; justify-content: center; margin-bottom: 44px; }
.hero-btn {
  padding: 13px 32px; border-radius: 12px; font-size: 15px; font-weight: 600;
  cursor: pointer; display: inline-flex; align-items: center; gap: 8px;
  transition: all 0.25s; border: none;
}
.hero-btn svg { width: 16px; height: 16px; }
.hero-btn.primary { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); color: #fff; box-shadow: 0 8px 24px rgba(30,122,230,0.35); }
.hero-btn.primary:hover { transform: translateY(-2px); box-shadow: 0 12px 32px rgba(30,122,230,0.45); }
.hero-btn.ghost { background: #fff; color: #1E7AE6; border: 1.5px solid #BBD7FB; }
.hero-btn.ghost:hover { border-color: #1E7AE6; background: #F0F7FF; }
.tech-badges { display: inline-flex; gap: 14px; flex-wrap: wrap; justify-content: center; }
.tech-badges span {
  font-size: 13px; color: #475569; background: rgba(255,255,255,0.9);
  padding: 8px 18px; border-radius: 100px; box-shadow: 0 2px 10px rgba(15,23,42,0.05);
}

.scroll-hint {
  position: absolute; bottom: 32px; left: 50%; transform: translateX(-50%);
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  color: #94A3B8; font-size: 12px; cursor: pointer; z-index: 2;
}
.mouse {
  width: 24px; height: 38px; border: 2px solid #94A3B8; border-radius: 14px;
  display: flex; justify-content: center; padding-top: 6px;
}
.wheel { width: 3px; height: 8px; border-radius: 3px; background: #1E7AE6; animation: wheelMove 1.6s infinite; }
@keyframes wheelMove { 0% { transform: translateY(0); opacity: 1; } 100% { transform: translateY(12px); opacity: 0; } }

/* ===== ② 背景简介 ===== */
.about-section { background: #fff; padding: 96px 0; }
.about-grid {
  display: grid; grid-template-columns: 1.05fr 0.95fr; gap: 56px;
  align-items: center; margin-bottom: 72px;
}
.about-text h3 { font-size: 24px; font-weight: 700; margin: 0 0 20px; color: #1a1a2e; }
.about-text p { font-size: 15px; line-height: 1.95; color: #475569; margin: 0 0 18px; text-align: justify; }
.about-text strong { color: #1E7AE6; font-weight: 600; }
.about-points { list-style: none; padding: 0; margin: 24px 0 0; display: flex; flex-direction: column; gap: 12px; }
.about-points li { display: flex; align-items: center; gap: 10px; font-size: 14px; color: #334155; }
.p-dot { width: 9px; height: 9px; border-radius: 50%; flex-shrink: 0; }
.p-dot.blue { background: #1E7AE6; }
.p-dot.green { background: #10B981; }
.p-dot.purple { background: #7A5AF8; }

.about-visual { display: flex; justify-content: center; }
.about-svg { width: 100%; max-width: 460px; }
.float-card { animation: floatCard 4.5s ease-in-out infinite; transform-origin: center; }
.fc-left { animation-delay: 0s; }
.fc-right { animation-delay: 0.7s; }
.fc-ai { animation-delay: 1.3s; }
@keyframes floatCard { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-9px); } }

.feature-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; }
.feature-card {
  background: #F8FBFF; border: 1px solid #E8F1FC; border-radius: 18px;
  padding: 28px 22px; text-align: center;
  transition: all 0.3s ease;
}
.feature-card:hover { transform: translateY(-6px); background: #fff; box-shadow: 0 16px 40px rgba(30,122,230,0.12); border-color: transparent; }
.fc-icon {
  width: 58px; height: 58px; border-radius: 16px; margin: 0 auto 16px;
  display: flex; align-items: center; justify-content: center;
}
.fc-icon svg { width: 28px; height: 28px; }
.fc-icon.blue { background: #E3F2FF; color: #1E7AE6; }
.fc-icon.purple { background: #F0ECFE; color: #7A5AF8; }
.fc-icon.green { background: #E6F9F2; color: #10B981; }
.fc-icon.red { background: #FDECEC; color: #E53935; }
.feature-card h4 { font-size: 17px; font-weight: 700; margin: 0 0 10px; }
.feature-card p { font-size: 13px; line-height: 1.8; color: #64748b; margin: 0; }

/* ===== ③ 登录 ===== */
.login-section {
  background: linear-gradient(180deg, #F6F9FE 0%, #EEF4FD 100%);
  padding: 96px 0;
}
.role-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 28px; }
.role-card {
  background: white; border-radius: 24px; padding: 32px; cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 2px solid transparent;
  box-shadow: 0 4px 24px rgba(0,0,0,0.04);
}
.role-card:hover { transform: translateY(-8px); box-shadow: 0 20px 48px rgba(0,0,0,0.1); }
.role-card.patient:hover { border-color: #1E7AE6; }
.role-card.doctor:hover { border-color: #10B981; }
.role-card.admin:hover { border-color: #7A5AF8; }
.card-illustration { display: flex; justify-content: center; margin-bottom: 20px; }
.card-illustration svg { width: 130px; height: 130px; }
.card-info h3 { font-size: 22px; font-weight: 700; margin: 0 0 6px; color: #1a1a2e; }
.card-info p { font-size: 14px; color: #64748b; margin: 0 0 16px; }
.card-features { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 24px; }
.card-features span {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 12px; padding: 4px 10px; border-radius: 20px;
  background: #f1f5f9; color: #475569;
}
.card-features svg { width: 12px; height: 12px; }
.enter-btn {
  width: 100%; padding: 12px 20px; border-radius: 12px; border: none;
  font-size: 15px; font-weight: 600; cursor: pointer;
  display: flex; align-items: center; justify-content: center; gap: 8px;
  transition: all 0.2s;
}
.enter-btn svg { width: 16px; height: 16px; transition: transform 0.2s; }
.enter-btn:hover svg { transform: translateX(3px); }
.patient-btn { background: linear-gradient(135deg, #1E7AE6, #4A9EF5); color: white; }
.doctor-btn { background: linear-gradient(135deg, #10B981, #34D399); color: white; }
.admin-btn { background: linear-gradient(135deg, #7A5AF8, #9B7DFB); color: white; }

/* ===== ④ 版权说明 ===== */
.copyright-section { background: #0F1B2E; color: #CBD5E1; padding: 72px 0 0; }
.footer-grid {
  display: grid; grid-template-columns: 1.4fr 1.2fr 0.8fr; gap: 48px;
  padding-bottom: 48px;
}
.footer-logo { display: flex; align-items: center; gap: 10px; margin-bottom: 18px; }
.footer-logo span { font-size: 20px; font-weight: 600; color: #fff; }
.footer-slogan { font-size: 13px; line-height: 1.9; color: #94A3B8; margin: 0 0 18px; }
.footer-tags { display: flex; gap: 8px; flex-wrap: wrap; }
.footer-tags span {
  font-size: 12px; padding: 5px 12px; border-radius: 100px;
  background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.1);
}
.footer-col h5 { font-size: 15px; color: #fff; margin: 4px 0 18px; font-weight: 600; }
.footer-col ul { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 13px; }
.footer-col li { display: flex; align-items: center; gap: 9px; font-size: 13px; color: #94A3B8; line-height: 1.6; }
.footer-col li svg { width: 15px; height: 15px; flex-shrink: 0; color: #1E7AE6; }
.footer-col .service-time { color: #64748B; padding-left: 24px; }
.link-item { cursor: pointer; transition: color 0.2s; }
.link-item:hover { color: #1E7AE6; }
.copyright-bar {
  border-top: 1px solid rgba(255,255,255,0.08);
  padding: 20px 32px; text-align: center;
}
.copyright-bar p { margin: 0; font-size: 13px; color: #94A3B8; }

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .about-grid { grid-template-columns: 1fr; gap: 36px; }
  .about-svg { max-width: 380px; }
  .feature-cards { grid-template-columns: repeat(2, 1fr); }
  .role-cards { grid-template-columns: 1fr; max-width: 460px; margin: 0 auto; }
  .footer-grid { grid-template-columns: 1fr; gap: 36px; }
}
@media (max-width: 640px) {
  .top-nav { padding: 0 18px; }
  .nav-item { padding: 8px 10px; font-size: 14px; }
  .logo span { font-size: 17px; }
  .hero-title { font-size: 48px; letter-spacing: 6px; }
  .hero-slogan { font-size: 20px; }
  .feature-cards { grid-template-columns: 1fr; }
  .container { padding: 0 20px; }
  .about-section, .login-section { padding: 64px 0; }
}
</style>
