import { contextBridge, ipcRenderer } from "electron";
import path from "path";
import fs from "fs";
import os from "os";

contextBridge.exposeInMainWorld("electronAPI", {
  selectFilesAndDirectories: () =>
    ipcRenderer.invoke("select-files-and-directories"),

  getPathForFiles: (files: File[]) => {
    const { webUtils } = require("electron");
    return files.map((f) => webUtils.getPathForFile(f)).filter(Boolean);
  },

  /**
   * Returns available drive roots.
   * Windows: scans A-Z for accessible drives.
   * Unix: returns single root "/".
   */
  getRoots: (): {
    name: string;
    fullPath: string;
    isDirectory: true;
    locked: boolean;
    hidden: boolean;
  }[] => {
    if (process.platform === "win32") {
      return "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        .split("")
        .filter((letter) => {
          try {
            fs.accessSync(`${letter}:\\`);
            return true;
          } catch {
            return false;
          }
        })
        .map((letter) => ({
          name: `${letter}:`,
          fullPath: `${letter}:\\`,
          isDirectory: true as const,
          locked: false,
          hidden: false,
        }));
    }
    return [
      {
        name: "/",
        fullPath: "/",
        isDirectory: true as const,
        locked: false,
        hidden: false,
      },
    ];
  },

  getHomedir: (): string => os.homedir(),
  getSep: (): string => path.sep,

  /**
   * Reads all directory entries including hidden and locked.
   * hidden: dot-prefix detection only (cross-platform safe).
   * TODO: Windows FILE_ATTRIBUTE_HIDDEN bit detection via native addon.
   */
  readDir: (
    dirPath: string,
  ): {
    name: string;
    fullPath: string;
    isDirectory: boolean;
    size?: number;
    locked: boolean;
    hidden: boolean;
  }[] => {
    const raw = fs.readdirSync(dirPath, { withFileTypes: true });

    return raw.map((entry) => {
      const fullPath = path.join(dirPath, entry.name);
      const isDirectory = entry.isDirectory();
      const hidden = entry.name.startsWith(".");

      let locked = false;
      try {
        fs.accessSync(fullPath, fs.constants.R_OK);
      } catch {
        locked = true;
      }

      let size: number | undefined;
      if (!isDirectory && !locked) {
        try {
          size = fs.statSync(fullPath).size;
        } catch {
          locked = true;
        }
      }

      return { name: entry.name, fullPath, isDirectory, size, locked, hidden };
    });
  },

  /**
   * Creates a directory. Returns the created path or throws with reason.
   */
  mkdir: (dirPath: string): void => {
    fs.mkdirSync(dirPath, { recursive: false });
  },

  /**
   * Checks if a path exists (used for new folder name collision detection).
   */
  exists: (dirPath: string): boolean => {
    return fs.existsSync(dirPath);
  },

  onBackendStatus: (
    cb: (data: {
      status: string;
      backendUrl?: string;
      message?: string;
      attempt?: number;
      max?: number;
    }) => void,
  ) => {
    ipcRenderer.on("backend-status", (_e, data) => cb(data));
  },

  onBackendLog: (cb: (line: string) => void) => {
    ipcRenderer.on("backend-log", (_e, line) => cb(line));
  },

  getBackendStatus: () => ipcRenderer.invoke("get-backend-status"),
});
