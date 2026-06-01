export {};

declare global {
  interface Window {
    electronAPI: {
      selectFilesAndDirectories: () => Promise<string[]>;
      getPathForFiles: (files: File[]) => string[];
      onBackendStatus: (
        cb: (data: {
          status: "starting" | "ready" | "error";
          backendUrl?: string;
          message?: string;
          attempt?: number;
          max?: number;
        }) => void,
      ) => void;
      getHomedir: () => string;
      getSep: () => string;
      getRoots: () => {
        name: string;
        fullPath: string;
        isDirectory: true;
        locked: boolean;
        hidden: boolean;
      }[];
      readDir: (dirPath: string) => {
        name: string;
        fullPath: string;
        isDirectory: boolean;
        size?: number;
        locked: boolean;
        hidden: boolean;
      }[];
      /** Creates a single directory (non-recursive). Throws if path exists or no permission. */
      mkdir: (dirPath: string) => void;
      /** Returns true if path exists on disk. */
      exists: (dirPath: string) => boolean;
      onBackendLog: (cb: (line: string) => void) => void;
      getBackendStatus: () => Promise<{
        status: "starting" | "ready" | "error";
        backendUrl?: string;
        message?: string;
      }>;
    };
  }
}
