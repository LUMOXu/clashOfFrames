import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    port: 9001,
    proxy: {
      "/api": {
        target: "http://localhost:9002",
        changeOrigin: true,
      },
      "/ws": {
        target: "http://localhost:9002",
        ws: true,
        changeOrigin: true,
      },
      "/cards": {
        target: "http://localhost:9002",
        changeOrigin: true,
      },
      "/assets": {
        target: "http://localhost:9002",
        changeOrigin: true,
      },
      "/audio": {
        target: "http://localhost:9002",
        changeOrigin: true,
      },
    },
  },
});
