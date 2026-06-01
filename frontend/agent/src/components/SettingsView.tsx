import { useState, useEffect } from "react";
import {
  FolderOpen,
  ScrollText,
  CheckCircle,
  AlertCircle,
  Loader2,
  KeyRound,
  Activity,
  Gauge,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { FileBrowserModal } from "./FileBrowserModal";

type SaveState = "idle" | "saving" | "success" | "error";

interface UploadDirState {
  current: string;
  save: SaveState;
  error: string | null;
}

interface LogState {
  opening: boolean;
  error: string | null;
}

interface CredentialsState {
  currentPassword: string;
  newUsername: string;
  newPassword: string;
  confirmNewPassword: string;
  save: SaveState;
  error: string | null;
}

interface SpeedCapState {
  value: string;
  save: SaveState;
  error: string | null;
}

export const SettingsView = () => {
  const { apiClient, healthClient, logout } = useAuth();

  const [backendOnline, setBackendOnline] = useState<boolean | null>(null);

  const [uploadDir, setUploadDir] = useState<UploadDirState>({
    current: "",
    save: "idle",
    error: null,
  });

  const [log, setLog] = useState<LogState>({
    opening: false,
    error: null,
  });

  const [credentials, setCredentials] = useState<CredentialsState>({
    currentPassword: "",
    newUsername: "",
    newPassword: "",
    confirmNewPassword: "",
    save: "idle",
    error: null,
  });

  const [speedCap, setSpeedCap] = useState<SpeedCapState>({
    value: "80",
    save: "idle",
    error: null,
  });

  const [folderBrowserOpen, setFolderBrowserOpen] = useState(false);

  useEffect(() => {
    apiClient
      .get<{ path: string }>("/storage/upload-dir")
      .then((res) =>
        setUploadDir((prev) => ({ ...prev, current: res.data.path })),
      )
      .catch(() =>
        setUploadDir((prev) => ({
          ...prev,
          error: "Failed to load upload directory.",
        })),
      );

    apiClient
      .get<{ value: number }>("/settings/speed-cap")
      .then((res) =>
        setSpeedCap((prev) => ({ ...prev, value: String(res.data.value) })),
      )
      .catch(() => {});

    checkBackendHealth();
    const interval = setInterval(checkBackendHealth, 15000);
    return () => clearInterval(interval);
  }, []);

  const checkBackendHealth = async () => {
    try {
      await healthClient.get("/actuator/health");
      setBackendOnline(true);
    } catch {
      setBackendOnline(false);
    }
  };

  const saveUploadDir = async (path: string) => {
    setUploadDir((prev) => ({ ...prev, save: "saving", error: null }));
    try {
      const res = await apiClient.put<{ path: string; error?: string }>(
        "/storage/upload-dir",
        { path },
      );
      if (res.data.error) {
        setUploadDir((prev) => ({
          ...prev,
          save: "error",
          error: res.data.error ?? null,
        }));
        return;
      }
      setUploadDir((prev) => ({
        ...prev,
        current: res.data.path,
        save: "success",
        error: null,
      }));
      setTimeout(
        () => setUploadDir((prev) => ({ ...prev, save: "idle" })),
        2500,
      );
    } catch (err: any) {
      const message =
        err?.response?.data?.error ?? "Failed to update upload directory.";
      setUploadDir((prev) => ({ ...prev, save: "error", error: message }));
    }
  };

  const handleOpenFolder = async () => {
    try {
      await apiClient.post("/storage/open-uploads-folder");
    } catch {}
  };

  const openLogs = async () => {
    setLog({ opening: true, error: null });
    try {
      await apiClient.post("/storage/open-logs");
      setLog({ opening: false, error: null });
    } catch {
      setLog({ opening: false, error: "Failed to open log file." });
    }
  };

  const saveSpeedCap = async () => {
    const parsed = parseFloat(speedCap.value);
    if (isNaN(parsed) || parsed < 1 || parsed > 1000) {
      setSpeedCap((prev) => ({
        ...prev,
        error: "Enter a value between 1 and 1000 Mbps.",
      }));
      return;
    }

    setSpeedCap((prev) => ({ ...prev, save: "saving", error: null }));
    try {
      await apiClient.put("/settings/speed-cap", { value: parsed });
      setSpeedCap((prev) => ({ ...prev, save: "success", error: null }));
      setTimeout(
        () => setSpeedCap((prev) => ({ ...prev, save: "idle" })),
        2500,
      );
    } catch {
      setSpeedCap((prev) => ({
        ...prev,
        save: "error",
        error: "Failed to save speed cap.",
      }));
    }
  };

  const changeCredentials = async () => {
    const { currentPassword, newUsername, newPassword, confirmNewPassword } =
      credentials;

    if (!currentPassword) {
      setCredentials((prev) => ({
        ...prev,
        error: "Current password is required.",
      }));
      return;
    }
    if (!newUsername.trim()) {
      setCredentials((prev) => ({
        ...prev,
        error: "Username cannot be blank.",
      }));
      return;
    }
    if (newPassword.length < 6) {
      setCredentials((prev) => ({
        ...prev,
        error: "New password must be at least 6 characters.",
      }));
      return;
    }
    if (newPassword !== confirmNewPassword) {
      setCredentials((prev) => ({
        ...prev,
        error: "New passwords do not match.",
      }));
      return;
    }

    setCredentials((prev) => ({ ...prev, save: "saving", error: null }));

    try {
      await apiClient.post("/auth/change-credentials", {
        currentPassword,
        newUsername,
        newPassword,
      });

      setCredentials({
        currentPassword: "",
        newUsername: "",
        newPassword: "",
        confirmNewPassword: "",
        save: "success",
        error: null,
      });

      setTimeout(() => {
        setCredentials((prev) => ({ ...prev, save: "idle" }));
        logout();
      }, 1500);
    } catch (err: any) {
      const message =
        err?.response?.data?.error ?? "Failed to update credentials.";
      setCredentials((prev) => ({ ...prev, save: "error", error: message }));
    }
  };

  return (
    <div className="min-h-0 overflow-hidden px-6 py-6">
      <div className="max-w-2xl mx-auto">
        <div className="bg-black/70 overflow-hidden backdrop-blur-xl border border-orange-500/20 shadow-[0_0_40px_rgba(255,120,0,0.15)] rounded-2xl p-8 space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-2xl font-bold text-orange-300">Settings</h2>
              <p className="text-orange-700 text-sm mt-1">
                Manage storage, network, and diagnostics
              </p>
            </div>
            <div className="flex items-center gap-2">
              <Activity className="w-4 h-4 text-orange-400" />
              <span className="text-xs font-semibold uppercase tracking-widest text-orange-400">
                Backend
              </span>
              {backendOnline === null ? (
                <span className="flex items-center gap-1 text-xs text-orange-600">
                  <Loader2 className="w-3 h-3 animate-spin" /> Checking...
                </span>
              ) : backendOnline ? (
                <span className="flex items-center gap-1.5 text-xs text-green-400">
                  <span className="w-2 h-2 rounded-full bg-green-400 shadow-[0_0_6px_rgba(74,222,128,0.8)]" />
                  Online
                </span>
              ) : (
                <span className="flex items-center gap-1.5 text-xs text-red-400">
                  <span className="w-2 h-2 rounded-full bg-red-400" />
                  Offline
                </span>
              )}
            </div>
          </div>

          {/* Upload Directory */}
          <section className="bg-orange-950/30 border border-orange-900/40 rounded-xl p-6 space-y-4">
            <div className="flex items-center gap-2 text-orange-400">
              <FolderOpen className="w-4 h-4" />
              <span className="text-sm font-semibold uppercase tracking-widest">
                Upload Directory
              </span>
            </div>
            <button
              onClick={handleOpenFolder}
              className="text-sm cursor-pointer font-bold text-orange-400 hover:text-orange-300 border-2 border-orange-800 px-3 py-1 rounded-lg transition-all"
            >
              Open
            </button>
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <code className="flex-1 bg-black/40 border border-orange-900/40 rounded-lg px-3 py-2 text-orange-200 text-sm font-mono truncate">
                  {uploadDir.current || "—"}
                </code>
                <button
                  onClick={() => setFolderBrowserOpen(true)}
                  className="flex items-center gap-1.5 px-3 py-2 bg-orange-900/30 hover:bg-orange-900/50
                         border border-orange-700 rounded-lg text-orange-300 text-sm transition-colors cursor-pointer"
                >
                  <FolderOpen className="w-3.5 h-3.5" />
                  Browse
                </button>
              </div>
              {uploadDir.save === "saving" && (
                <div className="flex items-center gap-2 text-sm text-orange-400">
                  <Loader2 className="w-4 h-4 animate-spin" /> Saving...
                </div>
              )}
              {uploadDir.save === "success" && (
                <StatusMessage
                  type="success"
                  message="Upload directory updated."
                />
              )}
              {uploadDir.error && (
                <StatusMessage type="error" message={uploadDir.error} />
              )}
            </div>
          </section>

          {/* Transfer Speed Cap */}
          <section className="bg-orange-950/30 border border-orange-900/40 rounded-xl p-6 space-y-4">
            <div className="flex items-center gap-2 text-orange-400">
              <Gauge className="w-4 h-4" />
              <span className="text-sm font-semibold uppercase tracking-widest">
                Transfer Speed Cap
              </span>
            </div>
            <p className="text-orange-600 text-xs">
              Max outbound transfer speed. Default is 80 Mbps. Set higher only
              if you want to saturate your network link.
            </p>
            <div className="flex items-center gap-2">
              <input
                type="number"
                min={1}
                max={1000}
                value={speedCap.value}
                onChange={(e) =>
                  setSpeedCap((prev) => ({
                    ...prev,
                    value: e.target.value,
                    error: null,
                  }))
                }
                onKeyDown={(e) => {
                  if (e.key === "Enter") saveSpeedCap();
                }}
                className="w-32 bg-black/40 border border-orange-700/60 rounded-lg px-3 py-2
                       text-orange-100 text-sm font-mono placeholder:text-orange-800
                       focus:outline-none focus:border-orange-500 transition-colors"
              />
              <span className="text-orange-500 text-sm">Mbps</span>
              <button
                onClick={saveSpeedCap}
                disabled={speedCap.save === "saving"}
                className="flex items-center gap-1.5 px-4 py-2 bg-orange-600 hover:bg-orange-500
                       rounded-lg text-white text-sm transition-colors disabled:opacity-50 cursor-pointer"
              >
                {speedCap.save === "saving" ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" /> Saving...
                  </>
                ) : (
                  "Save"
                )}
              </button>
            </div>
            {speedCap.save === "success" && (
              <StatusMessage type="success" message="Speed cap updated." />
            )}
            {speedCap.error && (
              <StatusMessage type="error" message={speedCap.error} />
            )}
          </section>

          {/* Change Credentials */}
          <section className="bg-orange-950/30 border border-orange-900/40 rounded-xl p-6 space-y-4">
            <div className="flex items-center gap-2 text-orange-400">
              <KeyRound className="w-4 h-4" />
              <span className="text-sm font-semibold uppercase tracking-widest">
                Change Credentials
              </span>
            </div>
            <p className="text-orange-600 text-xs">
              After saving, you will be signed out and prompted to log in with
              your new credentials.
            </p>
            <div className="space-y-3">
              <div>
                <label className="block text-orange-300 text-xs mb-1.5">
                  Current Password
                </label>
                <input
                  type="password"
                  value={credentials.currentPassword}
                  onChange={(e) =>
                    setCredentials((prev) => ({
                      ...prev,
                      currentPassword: e.target.value,
                      error: null,
                    }))
                  }
                  placeholder="Enter current password"
                  className="w-full bg-black/40 border border-orange-700/60 rounded-lg px-3 py-2
                         text-orange-100 text-sm placeholder:text-orange-800
                         focus:outline-none focus:border-orange-500 transition-colors"
                />
              </div>
              <div>
                <label className="block text-orange-300 text-xs mb-1.5">
                  New Username
                </label>
                <input
                  type="text"
                  value={credentials.newUsername}
                  onChange={(e) =>
                    setCredentials((prev) => ({
                      ...prev,
                      newUsername: e.target.value,
                      error: null,
                    }))
                  }
                  placeholder="Enter new username"
                  className="w-full bg-black/40 border border-orange-700/60 rounded-lg px-3 py-2
                         text-orange-100 text-sm placeholder:text-orange-800
                         focus:outline-none focus:border-orange-500 transition-colors"
                />
              </div>
              <div>
                <label className="block text-orange-300 text-xs mb-1.5">
                  New Password
                </label>
                <input
                  type="password"
                  value={credentials.newPassword}
                  onChange={(e) =>
                    setCredentials((prev) => ({
                      ...prev,
                      newPassword: e.target.value,
                      error: null,
                    }))
                  }
                  placeholder="At least 6 characters"
                  className="w-full bg-black/40 border border-orange-700/60 rounded-lg px-3 py-2
                         text-orange-100 text-sm placeholder:text-orange-800
                         focus:outline-none focus:border-orange-500 transition-colors"
                />
              </div>
              <div>
                <label className="block text-orange-300 text-xs mb-1.5">
                  Confirm New Password
                </label>
                <input
                  type="password"
                  value={credentials.confirmNewPassword}
                  onChange={(e) =>
                    setCredentials((prev) => ({
                      ...prev,
                      confirmNewPassword: e.target.value,
                      error: null,
                    }))
                  }
                  placeholder="Repeat new password"
                  className="w-full bg-black/40 border border-orange-700/60 rounded-lg px-3 py-2
                         text-orange-100 text-sm placeholder:text-orange-800
                         focus:outline-none focus:border-orange-500 transition-colors"
                />
              </div>
              {credentials.save === "success" && (
                <StatusMessage
                  type="success"
                  message="Credentials updated. Signing you out..."
                />
              )}
              {credentials.error && (
                <StatusMessage type="error" message={credentials.error} />
              )}
              <button
                onClick={changeCredentials}
                disabled={credentials.save === "saving"}
                className="flex items-center gap-2 px-4 py-2 bg-orange-600 hover:bg-orange-500
                       rounded-lg text-white text-sm transition-colors disabled:opacity-50 cursor-pointer"
              >
                {credentials.save === "saving" ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" /> Saving...
                  </>
                ) : (
                  "Update Credentials"
                )}
              </button>
            </div>
          </section>

          {/* Backend Logs */}
          <section className="bg-orange-950/30 border border-orange-900/40 rounded-xl p-6 space-y-4">
            <div className="flex items-center gap-2 text-orange-400">
              <ScrollText className="w-4 h-4" />
              <span className="text-sm font-semibold uppercase tracking-widest">
                Backend Logs
              </span>
            </div>
            <p className="text-orange-600 text-sm">
              Opens the backend log file in your system's default application.
            </p>
            <div className="space-y-3">
              <button
                onClick={openLogs}
                disabled={log.opening}
                className="flex items-center gap-2 px-4 py-2 bg-orange-900/30 hover:bg-orange-900/50
                       border border-orange-700 rounded-lg text-orange-300 text-sm
                       transition-colors disabled:opacity-50 cursor-pointer"
              >
                {log.opening ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <ScrollText className="w-4 h-4" />
                )}
                Open Logs
              </button>
              {log.error && <StatusMessage type="error" message={log.error} />}
            </div>
          </section>
        </div>
      </div>

      <FileBrowserModal
        isOpen={folderBrowserOpen}
        onClose={() => setFolderBrowserOpen(false)}
        mode="folder"
        title="Select Upload Directory"
        onConfirm={([path]) => {
          setFolderBrowserOpen(false);
          saveUploadDir(path);
        }}
      />
    </div>
  );
};

const StatusMessage = ({
  type,
  message,
}: {
  type: "success" | "error";
  message: string;
}) => {
  const isSuccess = type === "success";
  return (
    <div
      className={`flex items-center gap-2 text-sm px-3 py-2 rounded-lg border
      ${
        isSuccess
          ? "text-green-400 bg-green-900/20 border-green-800/40"
          : "text-red-400 bg-red-900/20 border-red-800/40"
      }`}
    >
      {isSuccess ? (
        <CheckCircle className="w-4 h-4 shrink-0" />
      ) : (
        <AlertCircle className="w-4 h-4 shrink-0" />
      )}
      {message}
    </div>
  );
};
