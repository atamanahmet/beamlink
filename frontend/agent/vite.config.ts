import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import electron from "vite-plugin-electron";
import renderer from "vite-plugin-electron-renderer";

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    electron([
      {
        /**
         * Main process entry.
         * Plugin compiles this and restarts Electron on change.
         */
        entry: "electron/main.ts",
        vite: {
          build: {
            outDir: "dist-electron",
          },
        },
      },
      {
        /**
         * Preload runs in renderer context with Node access.
         * Needs its own build target, sandboxed, not the main bundle.
         */
        entry: "electron/preload.ts",
        onstart(args) {
          args.reload();
        },
        vite: {
          build: {
            outDir: "dist-electron",
          },
        },
      },
    ]),
    renderer(),
  ],
  base: "./",
  server: {
    port: 5174,
    strictPort: true,
    proxy: {
      "/api": "http://localhost:9090",
      "/actuator": "http://localhost:9090",
    },
  },
});
