<script setup lang="ts">
import { ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useAuthStore } from "@/stores/authStore";

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const loginUser = ref("");
const loginPass = ref("");
const regUser = ref("");
const regPass = ref("");
const regConfirm = ref("");

async function submitLogin(): Promise<void> {
  await auth.login(loginUser.value.trim(), loginPass.value);
  await router.push((route.query.redirect as string) || { name: "home" });
}

async function submitRegister(): Promise<void> {
  if (regPass.value !== regConfirm.value) {
    auth.message = "两次密码不一致。";
    return;
  }
  await auth.register(regUser.value.trim(), regPass.value);
  await router.push({ name: "home" });
}
</script>

<template>
  <main class="page narrow">
    <section class="panel">
      <h1>帧封相对</h1>
      <p class="muted">登录后才能创建房间、加入对局和保存战绩。</p>
      <p class="auth-warning">没有找回密码功能！忘记密码请联系管理员重置。</p>
      <div v-if="auth.message" class="message">{{ auth.message }}</div>
      <div class="auth-grid">
        <form class="grid" @submit.prevent="submitLogin">
          <h2>登录</h2>
          <label>用户名
            <input v-model="loginUser" maxlength="24" required autocomplete="username" />
          </label>
          <label>密码
            <input v-model="loginPass" type="password" required autocomplete="current-password" />
          </label>
          <button class="primary" type="submit">登录</button>
        </form>
        <form class="grid" @submit.prevent="submitRegister">
          <h2>注册</h2>
          <p class="auth-warning">这是一个娱乐项目，请不要用你常用的密码以防止数据泄露。</p>
          <label>用户名
            <input v-model="regUser" maxlength="24" required autocomplete="username" />
          </label>
          <label>密码
            <input v-model="regPass" type="password" minlength="6" required autocomplete="new-password" />
          </label>
          <label>确认密码
            <input v-model="regConfirm" type="password" minlength="6" required autocomplete="new-password" />
          </label>
          <button type="submit">注册并进入</button>
        </form>
      </div>
    </section>
  </main>
</template>
