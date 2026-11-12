<template>
  <div class="login-wrap">
    <div class="panel">
      <h1 class="title">卡 牌 对 战</h1>
      <p class="subtitle">Card · Battle</p>

      <div class="switch">
        <span class="tab" :class="{ on: tab === 'login' }" @click="tab = 'login'">登 录</span>
        <span class="divider">|</span>
        <span class="tab" :class="{ on: tab === 'sms' }" @click="tab = 'sms'">验证码登录</span>
        <span class="divider">|</span>
        <span class="tab" :class="{ on: tab === 'reg' }" @click="tab = 'reg'">注 册</span>
        <span class="divider">|</span>
        <span class="tab" :class="{ on: tab === 'reset' }" @click="tab = 'reset'">找 回</span>
      </div>

      <div v-if="tab === 'login'" class="form">
        <el-input v-model="form.username" placeholder="用户名" />
        <el-input v-model="form.password" type="password" placeholder="密码" show-password />
        <button class="btn-main" :disabled="loading" @click="doLogin">
          {{ loading ? '进 入 中 …' : '踏 入 战 场' }}
        </button>
        <p class="tip">测试账号: ljh / 123456 · 也可用手机号验证码登录</p>
      </div>

      <div v-else-if="tab === 'sms'" class="form">
        <el-input v-model="smsLogin.phone" placeholder="注册时的手机号" maxlength="11" />
        <div class="sms-row">
          <el-input v-model="smsLogin.code" placeholder="验证码 (6 位)" maxlength="6" />
          <button class="btn-sms" :disabled="smsLeft > 0 || smsSending" @click="sendCode(smsLogin.phone)">
            {{ smsLeft > 0 ? smsLeft + ' s' : (smsSending ? '发送中…' : '获取验证码') }}
          </button>
        </div>
        <button class="btn-main" :disabled="loading" @click="doLoginByCode">
          {{ loading ? '验 证 中 …' : '验 证 码 登 录' }}
        </button>
        <p class="tip">一个手机号只能注册一次，未注册请先注册</p>
      </div>

      <div v-else-if="tab === 'reg'" class="form">
        <el-input v-model="reg.username" placeholder="用户名 (3–16 字符)" />
        <el-input v-model="reg.password" type="password" placeholder="密码 (6–20 位)" show-password />
        <el-input v-model="reg.nickname" placeholder="昵称 (可空)" />
        <div class="sms-row">
          <el-input v-model="reg.phone" placeholder="手机号" maxlength="11" />
          <button class="btn-sms" :disabled="smsLeft > 0 || smsSending" @click="sendCode(reg.phone)">
            {{ smsLeft > 0 ? smsLeft + ' s' : (smsSending ? '发送中…' : '获取验证码') }}
          </button>
        </div>
        <el-input v-model="reg.code" placeholder="短信验证码 (6 位数字)" maxlength="6" />
        <button class="btn-main" :disabled="loading" @click="doRegister">
          {{ loading ? '创 建 中 …' : '召 唤 英 雄' }}
        </button>
      </div>

      <div v-else class="form">
        <el-input v-model="reset.phone" placeholder="注册时的手机号" maxlength="11" />
        <div class="sms-row">
          <el-input v-model="reset.code" placeholder="验证码" maxlength="6" />
          <button class="btn-sms" :disabled="smsLeft > 0 || smsSending" @click="sendCode(reset.phone)">
            {{ smsLeft > 0 ? smsLeft + ' s' : (smsSending ? '发送中…' : '获取验证码') }}
          </button>
        </div>
        <el-input v-model="reset.newPassword" type="password" placeholder="新密码 (6–20 位)" show-password />
        <el-input v-model="reset.confirmPassword" type="password" placeholder="确认新密码" show-password />
        <button class="btn-main" :disabled="loading" @click="doReset">
          {{ loading ? '提 交 中 …' : '重 置 密 码' }}
        </button>
        <p class="tip">重置成功后请用新密码登录</p>
      </div>
    </div>

    <div class="corner-tag">v 1.0 · 基于 Spring Boot & Vue</div>
  </div>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/request'

const router = useRouter()
const tab = ref('login')
const loading = ref(false)
const form = ref({ username: 'ljh', password: '123456' })
const reg = ref({ username: '', password: '', nickname: '', phone: '', code: '' })
const smsLogin = ref({ phone: '', code: '' })
const reset = ref({ phone: '', code: '', newPassword: '', confirmPassword: '' })
const smsLeft = ref(0)
const smsSending = ref(false)
let smsTimer = null

function saveAndGo(data) {
  localStorage.setItem('token', data.token)
  localStorage.setItem('user', JSON.stringify(data.user))
  // 管理员和玩家统一进玩家大厅, 管理后台从大厅入口进入
  router.push('/lobby')
}

async function doLogin() {
  loading.value = true
  // 失败由 axios 拦截器统一提示, 这里吞掉异常避免 unhandled rejection 触发 Vue warn
  try { saveAndGo(await http.post('/auth/login', form.value)) }
  catch (e) { console.warn('login failed:', e) }
  finally { loading.value = false }
}
/** 手机号 + 验证码免密登录 */
async function doLoginByCode() {
  if (!/^1[3-9]\d{9}$/.test(smsLogin.value.phone)) return ElMessage.error('请输入 11 位手机号')
  if (!/^\d{6}$/.test(smsLogin.value.code)) return ElMessage.error('验证码为 6 位数字')
  loading.value = true
  try { saveAndGo(await http.post('/auth/login-by-code', smsLogin.value)) }
  catch (e) { console.warn('login by code failed:', e) }
  finally { loading.value = false }
}

/** 手机号唯一: 注册前先查一次, 让用户当场看到提示(后端仍有 uk_phone 兜底) */
async function doRegister() {
  if (!/^1[3-9]\d{9}$/.test(reg.value.phone)) return ElMessage.error('请输入 11 位手机号')
  if (!/^\d{6}$/.test(reg.value.code)) return ElMessage.error('验证码为 6 位数字')

  loading.value = true
  try {
    // 新号: 进大厅后询问是否看新手教程(可以不看, 直接跳过)
    localStorage.setItem('tutorialPending', '1')
    localStorage.removeItem('tutorialDone')
    saveAndGo(await http.post('/auth/register', reg.value))
  } catch (e) {
    localStorage.removeItem('tutorialPending')
    console.warn('register failed:', e)
  } finally { loading.value = false }
}

async function doReset() {
  if (!/^1[3-9]\d{9}$/.test(reset.value.phone)) return ElMessage.error('请输入正确的手机号')
  if (!reset.value.code) return ElMessage.error('请输入验证码')
  if (!reset.value.newPassword || reset.value.newPassword.length < 6) return ElMessage.error('密码长度 6–20 位')
  if (reset.value.newPassword !== reset.value.confirmPassword) return ElMessage.error('两次输入的密码不一致')
  loading.value = true
  try {
    await http.post('/auth/reset-password', {
      phone: reset.value.phone,
      code: reset.value.code,
      newPassword: reset.value.newPassword
    })
    ElMessage.success('密码重置成功, 请使用新密码登录')
    tab.value = 'login'
    form.value.username = ''
    form.value.password = ''
  } catch (e) { console.warn('reset failed:', e) }
  finally { loading.value = false }
}

async function sendCode(phone) {
  if (!/^1[3-9]\d{9}$/.test(phone)) return ElMessage.error('请输入 11 位手机号')
  // 场景: 注册要求手机号未注册; 验证码登录/找回要求已注册
  const scene = tab.value === 'reg' ? 'register' : (tab.value === 'sms' ? 'login' : 'reset')
  smsSending.value = true
  try {
    const r = await http.post('/auth/sms', { phone, scene })
    // 安全: 服务端默认不再下发明文验证码(只下发脱敏提示), 由短信网关投递。
    // 答辩演示时把 application.yml 的 cardbattle.sms.echo-code 改成 true, 接口会回显, 这里自动填入。
    if (r.code) {
      if (tab.value === 'reg') reg.value.code = r.code
      else if (tab.value === 'sms') smsLogin.value.code = r.code
      else reset.value.code = r.code
      ElMessage.success(`演示环境：验证码 ${r.code} 已自动填入（5 分钟内有效）`)
    } else {
      ElMessage.success(r.tip || '验证码已发送，5 分钟内有效')
    }
    smsLeft.value = 60
    smsTimer = setInterval(() => {
      smsLeft.value--
      if (smsLeft.value <= 0) clearInterval(smsTimer)
    }, 1000)
  } catch (e) { console.warn('sms failed:', e) }
  finally {
    smsSending.value = false
  }
}

onUnmounted(() => { clearInterval(smsTimer) })
</script>

<style scoped>
.login-wrap {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 40px 20px;
}

/* ===== 手机竖屏: 登录面板占满屏宽 ===== */
@media (max-width: 680px) {
  .login-wrap { padding: 16px 12px; justify-content: flex-start; }
  .panel { width: 100%; max-width: 100%; padding: 26px 18px 22px; }
  .title { font-size: 26px; letter-spacing: 6px; }
  .subtitle { font-size: 11px; }
  .switch { margin-bottom: 16px; padding: 6px 0; gap: 2px; }
  .tab { font-size: 12px; letter-spacing: 1px; }
  .divider { font-size: 10px; }
  .sms-row { gap: 6px; }
  .btn-sms { padding: 0 10px; font-size: 11px; white-space: nowrap; }
  .tip { font-size: 10px; line-height: 1.7; }
}

/* 暗色工业面板 */
.panel {
  width: 380px;
  padding: 48px 40px 36px;
  background: linear-gradient(180deg, rgba(30, 35, 41, 0.94), rgba(14, 17, 21, 0.96));
  border: 1px solid var(--line);
  border-left: 3px solid var(--gold);
  border-radius: 2px;
  box-shadow:
    0 12px 48px rgba(0, 0, 0, 0.65),
    inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

/* 顶部发光标题 */
.title {
  margin: 0;
  text-align: center;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 10px;
  color: var(--gold-light);
  text-shadow:
    0 0 10px rgba(255, 196, 92, 0.55),
    0 0 28px rgba(240, 165, 42, 0.3),
    0 2px 4px rgba(0, 0, 0, 0.8);
}
.subtitle {
  margin: 4px 0 28px;
  text-align: center;
  font-size: 12px;
  letter-spacing: 6px;
  color: var(--text-dim);
}

/* 注册/登录切换 */
.switch {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
  padding: 8px 0;
  border-top: 1px solid rgba(240, 165, 42, 0.25);
  border-bottom: 1px solid rgba(240, 165, 42, 0.25);
}
.tab {
  cursor: pointer;
  padding: 0 18px;
  font-size: 14px;
  letter-spacing: 4px;
  color: var(--text-dim);
  transition: color 0.15s, text-shadow 0.15s;
}
.tab.on {
  color: var(--gold-light);
  text-shadow: 0 0 6px rgba(255, 196, 92, 0.5);
  font-weight: 600;
}
.tab:hover { color: var(--gold); }
.divider {
  color: #3a424d;
  font-size: 12px;
}

/* 表单 */
.form :deep(.el-input) {
  margin-bottom: 14px;
}
.sms-row {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.sms-row :deep(.el-input) { margin-bottom: 0; flex: 1; }
.btn-sms {
  flex-shrink: 0;
  width: 104px;
  padding: 0 8px;
  background: transparent;
  border: 1px solid var(--gold-dark);
  border-radius: 2px;
  color: #c3cad3;
  font-size: 12px;
  letter-spacing: 1px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-sms:hover:not(:disabled) { background: rgba(240, 165, 42, 0.15); color: var(--gold-light); }
.btn-sms:disabled { opacity: 0.45; cursor: not-allowed; }
.btn-main {
  width: 100%;
  margin-top: 8px;
  padding: 14px 0;
  border: 1px solid #8d5f14;
  border-radius: 2px;
  background: linear-gradient(180deg, #f2ad36 0%, #b8781a 100%);
  color: #1a1005;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 8px;
  cursor: pointer;
  box-shadow:
    0 4px 14px rgba(212, 168, 71, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.25),
    inset 0 -1px 0 rgba(0, 0, 0, 0.2);
  transition: transform 0.15s, box-shadow 0.15s;
}
.btn-main:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow:
    0 6px 22px rgba(240, 204, 109, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}
.btn-main:active:not(:disabled) { transform: translateY(0); }
.btn-main:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.tip {
  margin: 14px 0 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-dim);
  letter-spacing: 1px;
}

.corner-tag {
  position: fixed;
  bottom: 14px;
  right: 18px;
  font-size: 11px;
  color: #3f4750;
  letter-spacing: 2px;
}
</style>
