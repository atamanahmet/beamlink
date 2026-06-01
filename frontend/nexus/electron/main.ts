import {
  app,
  BrowserWindow,
  dialog,
  ipcMain,
  Tray,
  Menu,
  nativeImage,
} from "electron";
import { spawn, ChildProcess } from "child_process";
import path from "path";
import fs from "fs";

let mainWindow: BrowserWindow | null = null;
let backendProcess: ChildProcess | null = null;
let tray: Tray | null = null;
let isQuitting = false;
let resourcesDir: string = "";
let runtimeDir: string = "";
let jreDir: string = "";
let BACKEND_PORT: number = 7472;

let backendState: { status: string; backendUrl?: string; message?: string } = {
  status: "starting",
};

ipcMain.handle("get-backend-status", () => backendState);

function readRuntimePort(resourcesDir: string, defaultPort: number): number {
  const runtimePath = path.join(resourcesDir, "nexus-runtime.json");
  try {
    const raw = fs.readFileSync(runtimePath, "utf-8");
    const parsed = JSON.parse(raw);
    const port = parseInt(parsed.port, 10);
    console.log(`[backend] port from nexus-runtime.json: ${port}`);
    return port;
  } catch {
    console.log(
      `[backend] nexus-runtime.json not found, using default port: ${defaultPort}`,
    );
    return defaultPort;
  }
}

async function waitForRuntimeJson(
  resourcesDir: string,
  defaultPort: number,
  retries = 30,
  intervalMs = 500,
): Promise<number> {
  const runtimePath = path.join(resourcesDir, "nexus-runtime.json");
  for (let i = 0; i < retries; i++) {
    if (fs.existsSync(runtimePath)) {
      return readRuntimePort(resourcesDir, defaultPort);
    }
    await new Promise((res) => setTimeout(res, intervalMs));
  }
  console.log(
    `[backend] nexus-runtime.json never appeared, using default: ${defaultPort}`,
  );
  return defaultPort;
}

function getIconPath() {
  const base = path.join(__dirname, "../assets");

  if (process.platform === "win32") {
    const ico = path.join(base, "icon.ico");
    return fs.existsSync(ico) ? ico : path.join(base, "icon.png");
  }

  if (process.platform === "darwin") {
    const icns = path.join(base, "icon.icns");
    return fs.existsSync(icns) ? icns : path.join(base, "icon.png");
  }

  return path.join(base, "icon.png");
}

async function waitForBackend(
  url: string,
  retries = 60,
  intervalMs = 1000,
): Promise<boolean> {
  for (let i = 0; i < retries; i++) {
    try {
      await fetch(url);
      return true;
    } catch {
      mainWindow?.webContents.send("backend-status", {
        status: "starting",
        attempt: i + 1,
        max: retries,
      });
      await new Promise((res) => setTimeout(res, intervalMs));
    }
  }
  return false;
}

function killBackend() {
  if (backendProcess && !backendProcess.killed) {
    backendProcess.kill();
    backendProcess = null;
  }
}

function startBackend() {
  const javaBinary = process.platform === "win32" ? "java.exe" : "java";

  const javaPath = path.join(jreDir, "bin", javaBinary);
  const jarPath = path.join(resourcesDir, "backend", "beamlink-nexus.jar");

  if (!fs.existsSync(javaPath)) {
    console.error("[backend] Java not found at:", javaPath);
    mainWindow?.webContents.send("backend-status", {
      status: "error",
      message: `Java not found at: ${javaPath}`,
    });
    return;
  }

  if (!fs.existsSync(jarPath)) {
    console.error("[backend] JAR not found at:", jarPath);
    mainWindow?.webContents.send("backend-status", {
      status: "error",
      message: `JAR not found at: ${jarPath}`,
    });
    return;
  }

  backendProcess = spawn(
    javaPath,
    [`-Dbeamlink.runtime.dir=${runtimeDir}`, "-jar", jarPath],
    {
      cwd: path.join(resourcesDir, "backend"),
      stdio: ["ignore", "pipe", "pipe"],
    },
  );

  backendProcess.stdout?.on("data", (d) =>
    console.log("[backend]", d.toString().trimEnd()),
  );
  backendProcess.stderr?.on("data", (d) =>
    console.error("[backend]", d.toString().trimEnd()),
  );
  backendProcess.on("exit", (code) => {
    console.log(`[backend] exited with code ${code}`);
    if (!isQuitting) {
      mainWindow?.webContents.send("backend-status", {
        status: "error",
        message: `Backend stopped unexpectedly (code ${code}).`,
      });
    }
  });
}

function createTray() {
  const icon = nativeImage.createFromPath(getIconPath());
  tray = new Tray(icon);
  tray.setToolTip("BeamLink Nexus");
  tray.setContextMenu(
    Menu.buildFromTemplate([
      {
        label: "Open",
        click: () => {
          mainWindow?.show();
          mainWindow?.focus();
          if (process.platform === "darwin") app.dock?.show();
        },
      },
      { type: "separator" },
      {
        label: "Quit",
        click: () => {
          isQuitting = true;
          app.quit();
        },
      },
    ]),
  );
  tray.on("click", () => {
    if (mainWindow?.isVisible()) {
      mainWindow.hide();
      if (process.platform === "darwin") app.dock?.hide();
    } else {
      mainWindow?.show();
      mainWindow?.focus();
      if (process.platform === "darwin") app.dock?.show();
    }
  });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    show: false,
    width: 1280,
    height: 800,
    icon: getIconPath(),
    titleBarStyle: "hidden",
    titleBarOverlay: {
      color: "#1a0f0a",
      symbolColor: "#f97316",
      height: 40,
    },
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  });

  if (process.env.NODE_ENV === "development") {
    mainWindow.loadURL("http://localhost:5173");
  } else {
    mainWindow.loadFile(path.join(__dirname, "../dist/index.html"));
  }
}

app.whenReady().then(async () => {
  resourcesDir =
    process.env.NODE_ENV === "development"
      ? path.resolve(__dirname, "../../../resources/nexus")
      : process.resourcesPath;

  jreDir =
    process.env.NODE_ENV === "development"
      ? path.resolve(__dirname, "../../../resources/jre")
      : path.join(process.resourcesPath, "jre");

  runtimeDir =
    process.env.NODE_ENV === "development"
      ? path.resolve(__dirname, "../../../resources/nexus")
      : path.join(app.getPath("appData"), "BeamLink", "Nexus");

  const runtimeSubdirs = ["data/database", "data/uploads", "data/temp", "logs"];
  for (const dir of runtimeSubdirs) {
    fs.mkdirSync(path.join(runtimeDir, dir), { recursive: true });
  }

  if (process.platform === "darwin") {
    app.dock?.setIcon(path.join(__dirname, "../assets/icon.png"));
  }

  createWindow();
  createTray();

  // Show window immediately so StartupScreen is visible while backend starts
  mainWindow?.once("ready-to-show", () => {
    mainWindow?.show();
    mainWindow?.focus();
    if (process.platform === "darwin") app.dock?.show();
  });

  startBackend();

  BACKEND_PORT = await waitForRuntimeJson(runtimeDir, 7472);

  try {
    const ok = await waitForBackend(
      `http://localhost:${BACKEND_PORT}/actuator/health`,
    );
    backendState = ok
      ? { status: "ready", backendUrl: `http://localhost:${BACKEND_PORT}` }
      : {
          status: "error",
          message: "Backend did not respond after 60 seconds.",
        };

    mainWindow?.webContents.send("backend-status", backendState);
  } catch (error) {
    dialog.showErrorBox("Backend Startup Failed", String(error));

    app.quit();
  }
});

app.on("window-all-closed", () => {
  // tray keeps the app alive
});

app.on("before-quit", () => {
  isQuitting = true;
  killBackend();
});

process.on("exit", () => {
  killBackend();
});

process.on("SIGINT", () => {
  isQuitting = true;
  killBackend();
  process.exit(0);
});

process.on("SIGTERM", () => {
  isQuitting = true;
  killBackend();
  process.exit(0);
});

ipcMain.handle("select-files-and-directories", async () => {
  const result = await dialog.showOpenDialog(mainWindow!, {
    properties: ["openFile", "openDirectory", "multiSelections"],
  });
  return result.filePaths;
});
