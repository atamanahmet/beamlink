import { useState, useEffect, useRef, useCallback } from "react";
import {
  LogOut,
  Upload,
  LayoutDashboard,
  FolderOpen,
  Settings,
} from "lucide-react";
import { FileUpload } from "./FileUpload";
import WarpBackground from "./WarpBackground";
import { useAuth } from "../context/AuthContext";
import { TransferView } from "./TransferView";
import { AgentInfo } from "./AgentInfo";
import { SettingsView } from "./SettingsView";

export const Dashboard = () => {
  const [activeView, setActiveView] = useState<
    "upload" | "info" | "transfers" | "settings"
  >("upload");

  const [isUploading, setIsUploading] = useState(false);

  const warpOffTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleUploadingChange = useCallback((active: boolean) => {
    if (active) {
      if (warpOffTimer.current) {
        clearTimeout(warpOffTimer.current);
        warpOffTimer.current = null;
      }
      setIsUploading(true);
    } else {
      warpOffTimer.current = setTimeout(() => {
        setIsUploading(false);
        warpOffTimer.current = null;
      }, 1200);
    }
  }, []);

  useEffect(() => {
    return () => {
      if (warpOffTimer.current) clearTimeout(warpOffTimer.current);
    };
  }, []);

  const { logout } = useAuth();

  return (
    <div className="relative h-screen overflow-hidden bg-linear-to-br from-[#1a0f0a] via-[#3b1f12] to-black">
      <WarpBackground active={isUploading} />

      <div className="relative z-10 flex flex-col h-screen">
        {/* Nav*/}
        <div className="shrink-0 flex justify-between items-center px-6 py-4 border-b border-orange-900/30">
          <h1 className="text-4xl font-bold text-orange-400"> </h1>

          <div className="flex gap-4">
            <button
              onClick={() => setActiveView("upload")}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg transition-all cursor-pointer ${
                activeView === "upload"
                  ? "bg-orange-600 text-white"
                  : "bg-orange-900/30 border border-orange-700 text-orange-300 hover:bg-orange-900/50"
              }`}
            >
              <Upload className="w-4 h-4" />
              Upload
            </button>

            <button
              onClick={() => setActiveView("info")}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg transition-all cursor-pointer ${
                activeView === "info"
                  ? "bg-orange-600 text-white"
                  : "bg-orange-900/30 border border-orange-700 text-orange-300 hover:bg-orange-900/50"
              }`}
            >
              <LayoutDashboard className="w-4 h-4" />
              Info
            </button>

            <button
              onClick={() => setActiveView("transfers")}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg transition-all cursor-pointer ${
                activeView === "transfers"
                  ? "bg-orange-600 text-white"
                  : "bg-orange-900/30 border border-orange-700 text-orange-300 hover:bg-orange-900/50"
              }`}
            >
              <FolderOpen className="w-4 h-4" />
              Transfers
            </button>

            <button
              onClick={() => setActiveView("settings")}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg transition-all cursor-pointer ${
                activeView === "settings"
                  ? "bg-orange-600 text-white"
                  : "bg-orange-900/30 border border-orange-700 text-orange-300 hover:bg-orange-900/50"
              }`}
            >
              <Settings className="w-4 h-4" />
              Settings
            </button>

            <button
              onClick={() => logout()}
              className="flex items-center gap-2 px-4 py-2 bg-orange-900/30 hover:bg-orange-900/50
                         border border-orange-700 rounded-lg text-orange-300 transition-all cursor-pointer"
            >
              <LogOut className="w-4 h-4" />
              Logout
            </button>
          </div>
        </div>

        {/* Scrollable content */}
        <div className="flex-1 overflow-y-auto p-6">
          <div className="max-w-7xl mx-auto">
            {activeView === "upload" && (
              <FileUpload
                onTransferStarted={() => setActiveView("transfers")}
              />
            )}
            {activeView === "info" && <AgentInfo />}
            {activeView === "transfers" && (
              <TransferView onUploadingChange={handleUploadingChange} />
            )}
            {activeView === "settings" && <SettingsView />}
          </div>
        </div>
      </div>
    </div>
  );
};
