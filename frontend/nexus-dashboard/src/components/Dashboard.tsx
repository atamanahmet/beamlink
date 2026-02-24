import { useState } from "react";
import { LogOut, Upload, LayoutDashboard, RefreshCw } from "lucide-react";
import { FileUpload } from "./FileUpload";
import { DashboardView } from "./DashboardView";
import WarpBackground from "./WarpBackground";
import { useAuth } from "../context/AuthContext";
import { UpdateView } from "./UpdateView";

export const Dashboard = () => {
  const [activeView, setActiveView] = useState<
    "upload" | "dashboard" | "update"
  >("upload");
  const [isUploading, setIsUploading] = useState(false);

  const { logout } = useAuth();

  return (
    <div className="relative min-h-screen overflow-hidden bg-linear-to-br from-[#1a0f0a] via-[#3b1f12] to-black">
      {/* WarpBackground at root level - covers full screen */}
      <WarpBackground active={isUploading} />

      {/* Content with z-index */}
      <div className="relative z-10 min-h-screen p-6">
        <div className="max-w-7xl mx-auto">
          {/* Header */}
          <div className="flex justify-between items-center mb-8">
            <h1 className="text-4xl font-bold text-orange-400">BeamLink</h1>

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
                onClick={() => setActiveView("dashboard")}
                className={`flex items-center gap-2 px-4 py-2 rounded-lg transition-all cursor-pointer ${
                  activeView === "dashboard"
                    ? "bg-orange-600 text-white"
                    : "bg-orange-900/30 border border-orange-700 text-orange-300 hover:bg-orange-900/50"
                }`}
              >
                <LayoutDashboard className="w-4 h-4" />
                Dashboard
              </button>
              <button
                onClick={() => setActiveView("update")}
                className={`flex items-center gap-2 px-4 py-2 rounded-lg transition-all cursor-pointer ${
                  activeView === "update"
                    ? "bg-orange-600 text-white"
                    : "bg-orange-900/30 border border-orange-700 text-orange-300 hover:bg-orange-900/50"
                }`}
              >
                <RefreshCw className="w-4 h-4" />
                Update
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

          {/* Content */}
          {activeView === "upload" && (
            <FileUpload onUploadStateChange={setIsUploading} />
          )}

          {activeView === "dashboard" && <DashboardView />}
          {activeView === "update" && <UpdateView />}
        </div>
      </div>
    </div>
  );
};
