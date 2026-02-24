import { useState, useRef, useEffect } from "react";
import {
  Upload,
  RefreshCw,
  CheckCircle,
  XCircle,
  Wifi,
  WifiOff,
  Send,
  Package,
  AlertTriangle,
} from "lucide-react";
import { useData } from "../context/DataContext";
import { useAuth } from "../context/AuthContext";

interface Agent {
  id: string;
  agentName: string;
  ipAddress: string;
  port: number;
  online: boolean;
}

type PushStatus = "idle" | "pushing" | "success" | "error";

export const UpdateView = () => {
  const { getApprovedAgents } = useData();
  const { apiClient } = useAuth();

  const [agents, setAgents] = useState<Agent[]>([]);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploadStatus, setUploadStatus] = useState<
    "idle" | "uploading" | "ready" | "error"
  >("idle");
  const [dragOver, setDragOver] = useState(false);
  const [agentPushStates, setAgentPushStates] = useState<
    Record<string, PushStatus>
  >({});
  const [pushAllStatus, setPushAllStatus] = useState<PushStatus>("idle");
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    loadAgents();
    const interval = setInterval(loadAgents, 10000);
    return () => clearInterval(interval);
  }, []);

  const loadAgents = async () => {
    try {
      const data = await getApprovedAgents();
      setAgents(data);
    } catch (err) {
      console.error("Failed to load agents:", err);
    }
  };

  const handleFileDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files[0];
    if (file?.name.endsWith(".zip")) {
      setSelectedFile(file);
      setUploadStatus("idle");
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setSelectedFile(file);
      setUploadStatus("idle");
    }
  };

  const handleUpload = async () => {
    if (!selectedFile) return;
    setUploadStatus("uploading");
    try {
      const formData = new FormData();
      formData.append("file", selectedFile);
      await apiClient.post("/update/upload", formData);
      setUploadStatus("ready");
    } catch {
      setUploadStatus("error");
    }
  };

  const handlePushOne = async (agentId: string) => {
    setAgentPushStates((prev) => ({ ...prev, [agentId]: "pushing" }));
    try {
      await apiClient.post(`/update/push/${agentId}`);
      setAgentPushStates((prev) => ({ ...prev, [agentId]: "success" }));
    } catch {
      setAgentPushStates((prev) => ({ ...prev, [agentId]: "error" }));
    }
  };

  const handlePushAll = async () => {
    setPushAllStatus("pushing");
    try {
      await apiClient.post("/update/push-all");
      setPushAllStatus("success");
      const allSuccess: Record<string, PushStatus> = {};
      agents
        .filter((a) => a.online)
        .forEach((a) => {
          allSuccess[a.id] = "success";
        });
      setAgentPushStates(allSuccess);
    } catch {
      setPushAllStatus("error");
    }
  };

  const onlineAgents = agents.filter((a) => a.online);
  const offlineAgents = agents.filter((a) => !a.online);

  return (
    <div className="min-h-screen p-6">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-4xl font-bold text-orange-400 tracking-wide mb-2">
            Agent Updater
          </h1>
          <p className="text-orange-300/60">
            Upload a new build and push it to agents
          </p>
        </div>

        {/* Upload Zone */}
        <div className="bg-black/70 backdrop-blur-xl border border-orange-500/20 shadow-[0_0_40px_rgba(255,120,0,0.15)] rounded-2xl p-6">
          <div className="flex items-center gap-2 mb-4">
            <Package className="w-5 h-5 text-orange-400" />
            <h2 className="text-lg font-semibold text-orange-300">
              Update Package
            </h2>
          </div>

          {/* Drop Zone */}
          <div
            onDragOver={(e) => {
              e.preventDefault();
              setDragOver(true);
            }}
            onDragLeave={() => setDragOver(false)}
            onDrop={handleFileDrop}
            onClick={() => fileInputRef.current?.click()}
            className={`border-2 border-dashed rounded-xl p-10 text-center cursor-pointer transition-all
              ${
                dragOver
                  ? "border-orange-400 bg-orange-500/10"
                  : "border-orange-800/40 hover:border-orange-600/60 hover:bg-orange-500/5"
              }`}
          >
            <Upload className="w-10 h-10 text-orange-500/60 mx-auto mb-3" />
            {selectedFile ? (
              <div>
                <p className="text-orange-300 font-medium">
                  {selectedFile.name}
                </p>
                <p className="text-orange-300/50 text-sm mt-1">
                  {(selectedFile.size / 1024 / 1024).toFixed(1)} MB
                </p>
              </div>
            ) : (
              <div>
                <p className="text-orange-300/70">
                  Drop your <span className="text-orange-400">.zip</span> here
                  or click to browse
                </p>
                <p className="text-orange-300/40 text-sm mt-1">
                  Must contain agent.jar and static/ folder
                </p>
              </div>
            )}
            <input
              ref={fileInputRef}
              type="file"
              accept=".zip"
              className="hidden"
              onChange={handleFileSelect}
            />
          </div>

          {/* Upload Actions */}
          <div className="flex items-center gap-4 mt-4">
            <button
              onClick={handleUpload}
              disabled={
                !selectedFile ||
                uploadStatus === "uploading" ||
                uploadStatus === "ready"
              }
              className="flex items-center gap-2 px-6 py-2.5 bg-orange-600 hover:bg-orange-500
                         disabled:opacity-40 disabled:cursor-not-allowed
                         text-white rounded-lg transition-all font-medium"
            >
              {uploadStatus === "uploading" ? (
                <RefreshCw className="w-4 h-4 animate-spin" />
              ) : (
                <Upload className="w-4 h-4" />
              )}
              {uploadStatus === "uploading"
                ? "Uploading..."
                : "Upload to Nexus"}
            </button>

            {uploadStatus === "ready" && (
              <div className="flex items-center gap-2 text-green-400">
                <CheckCircle className="w-4 h-4" />
                <span className="text-sm">Ready to push</span>
              </div>
            )}
            {uploadStatus === "error" && (
              <div className="flex items-center gap-2 text-red-400">
                <XCircle className="w-4 h-4" />
                <span className="text-sm">Upload failed</span>
              </div>
            )}
          </div>
        </div>

        {/* Agent List */}
        <div className="bg-black/70 backdrop-blur-xl border border-orange-500/20 shadow-[0_0_40px_rgba(255,120,0,0.15)] rounded-2xl overflow-hidden">
          {/* List Header */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-orange-800/30">
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-semibold text-orange-300">Agents</h2>
              <span className="text-xs bg-orange-500/20 text-orange-400 px-2 py-0.5 rounded-full">
                {onlineAgents.length} online
              </span>
            </div>

            <button
              onClick={handlePushAll}
              disabled={
                uploadStatus !== "ready" ||
                onlineAgents.length === 0 ||
                pushAllStatus === "pushing"
              }
              className="flex items-center gap-2 px-4 py-2 bg-orange-600 hover:bg-orange-500
                         disabled:opacity-40 disabled:cursor-not-allowed
                         text-white rounded-lg transition-all text-sm font-medium"
            >
              {pushAllStatus === "pushing" ? (
                <RefreshCw className="w-4 h-4 animate-spin" />
              ) : (
                <Send className="w-4 h-4" />
              )}
              Push to All Online
            </button>
          </div>

          {/* Upload not ready warning */}
          {uploadStatus !== "ready" && (
            <div className="flex items-center gap-2 px-6 py-3 bg-orange-900/20 border-b border-orange-800/20 text-orange-300/60 text-sm">
              <AlertTriangle className="w-4 h-4" />
              Upload a package first to enable pushing
            </div>
          )}

          {/* Online Agents */}
          <div className="divide-y divide-orange-800/20">
            {onlineAgents.map((agent) => (
              <div
                key={agent.id}
                className="flex items-center justify-between px-6 py-4 hover:bg-orange-500/5 transition-all"
              >
                <div className="flex items-center gap-3">
                  <Wifi className="w-4 h-4 text-green-400" />
                  <div>
                    <p className="text-orange-200 font-medium">
                      {agent.agentName}
                    </p>
                    <p className="text-orange-300/50 text-sm">
                      {agent.ipAddress}:{agent.port}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  {agentPushStates[agent.id] === "success" && (
                    <span className="flex items-center gap-1 text-green-400 text-sm">
                      <CheckCircle className="w-4 h-4" /> Pushed
                    </span>
                  )}
                  {agentPushStates[agent.id] === "error" && (
                    <span className="flex items-center gap-1 text-red-400 text-sm">
                      <XCircle className="w-4 h-4" /> Failed
                    </span>
                  )}
                  <button
                    onClick={() => handlePushOne(agent.id)}
                    disabled={
                      uploadStatus !== "ready" ||
                      agentPushStates[agent.id] === "pushing"
                    }
                    className="flex items-center gap-2 px-3 py-1.5 bg-orange-900/40 hover:bg-orange-800/60
                               border border-orange-700/40 disabled:opacity-40 disabled:cursor-not-allowed
                               text-orange-300 rounded-lg transition-all text-sm"
                  >
                    {agentPushStates[agent.id] === "pushing" ? (
                      <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                    ) : (
                      <Send className="w-3.5 h-3.5" />
                    )}
                    Push
                  </button>
                </div>
              </div>
            ))}

            {/* Offline Agents */}
            {offlineAgents.map((agent) => (
              <div
                key={agent.id}
                className="flex items-center justify-between px-6 py-4 opacity-40"
              >
                <div className="flex items-center gap-3">
                  <WifiOff className="w-4 h-4 text-orange-300/50" />
                  <div>
                    <p className="text-orange-200 font-medium">
                      {agent.agentName}
                    </p>
                    <p className="text-orange-300/50 text-sm">
                      {agent.ipAddress}:{agent.port}
                    </p>
                  </div>
                </div>
                <span className="text-orange-300/40 text-sm">Offline</span>
              </div>
            ))}

            {agents.length === 0 && (
              <div className="px-6 py-12 text-center text-orange-300/40">
                No approved agents found
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
