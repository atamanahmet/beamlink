import { useCallback, useState, useEffect } from "react";
import { useDropzone } from "react-dropzone";
import { LogOut } from "lucide-react";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import { Settings } from "lucide-react";

import WarpBackground from "./WarpBackground";
import { useAuth } from "../context/AuthContext";

interface Peer {
  agentId: string;
  agentName: string;
  ipAddress: string;
  port: number;
  online: boolean;
  publicToken: string;
}

export const FileUpload = () => {
  const { apiClient, logout, identity } = useAuth();

  const navigate = useNavigate();

  const [progressVisible, setProgressVisible] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [message, setMessage] = useState("");
  const [progress, setProgress] = useState(0);
  const [uploadedBytes, setUploadedBytes] = useState(0);
  const [totalBytes, setTotalBytes] = useState(0);
  const [startTime, setStartTime] = useState<number | null>(null);
  const [elapsedTime, setElapsedTime] = useState(0);
  const [abortController, setAbortController] =
    useState<AbortController | null>(null);
  const [uploadQueue, setUploadQueue] = useState<File[]>([]);
  const [currentFileIndex, setCurrentFileIndex] = useState(0);
  const [peers, setPeers] = useState<Peer[]>([]);
  const [selectedPeer, setSelectedPeer] = useState("");

  useEffect(() => {
    if (!isUploading || !startTime) return;
    const interval = setInterval(() => {
      setElapsedTime(Date.now() - startTime);
    }, 10);
    return () => clearInterval(interval);
  }, [isUploading, startTime]);

  useEffect(() => {
    if (isUploading) {
      setProgressVisible(true);
    } else {
      const timeout = setTimeout(() => {
        setProgressVisible(false);
        setProgress(0);
      }, 700);
      return () => clearTimeout(timeout);
    }
  }, [isUploading]);

  const loadPeers = useCallback(async () => {
    try {
      const response = await apiClient.get("/peers");
      setPeers(response.data || []);
    } catch {
      setPeers([]);
    }
  }, [apiClient]);

  useEffect(() => {
    loadPeers();
    const interval = setInterval(loadPeers, 5000);
    return () => clearInterval(interval);
  }, [loadPeers]);

  const formatElapsedTime = (ms: number): string =>
    ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`;

  const formatSpeed = (bytesUploaded: number, elapsedMs: number): string => {
    if (elapsedMs === 0) return "0 MB/s";
    const bytesPerSecond = (bytesUploaded / elapsedMs) * 1000;
    const mbPerSecond = bytesPerSecond / (1024 * 1024);
    if (mbPerSecond < 1) {
      return `${(bytesPerSecond / 1024).toFixed(1)} KB/s`;
    }
    return `${mbPerSecond.toFixed(2)} MB/s`;
  };

  const getFreshToken = async (): Promise<string | null> => {
    try {
      const response = await apiClient.get("/agent/token");
      return response.data;
    } catch {
      setMessage("⚠ Agent not yet approved by Nexus. Please wait.");
      setIsUploading(false);
      return null;
    }
  };

  const uploadFile = async (
    file: File,
    index: number,
    total: number,
    allFiles: File[],
  ) => {
    if (!selectedPeer) {
      setMessage("Please select a destination first");
      return;
    }

    const peer = peers.find((p) => p.agentId === selectedPeer);
    if (!peer) {
      setMessage("Selected destination not found");
      return;
    }

    const continueQueue = (idx: number) => {
      if (idx + 1 < total) {
        setTimeout(
          () => uploadFile(allFiles[idx + 1], idx + 1, total, allFiles),
          500,
        );
      } else {
        setMessage(
          `✓ All ${total} file${total > 1 ? "s" : ""} uploaded successfully to ${peer.agentName}!`,
        );
        setIsUploading(false);
      }
    };

    // Always get fresh token from agent backend before doing anything
    const token = await getFreshToken();
    if (!token) return;

    // Pre-flight
    setMessage(`Validating upload to ${peer.agentName}...`);

    try {
      const checkResp = await axios.get(
        `http://${peer.ipAddress}:${peer.port}/api/upload/check`,
        {
          params: { filename: file.name, fileSize: file.size },
          headers: { "X-Auth-Token": token },
        },
      );
      if (!checkResp.data.success) {
        setMessage(`❌ ${checkResp.data.message || "Pre-flight check failed"}`);
        setIsUploading(false);
        return;
      }
    } catch (err: any) {
      const status = err.response?.status;
      let errorMsg = "";
      if (status === 401 || status === 403)
        errorMsg = "Token rejected by peer. Try again in a moment.";
      else if (status === 507)
        errorMsg = `Insufficient disk space on ${peer.agentName}`;
      else if (status === 400)
        errorMsg = err.response?.data?.message || "Invalid filename";
      else if (status) errorMsg = err.response?.data?.message || err.message;
      else errorMsg = `Cannot connect to ${peer.agentName}`;
      setMessage(`❌ ${errorMsg}`);
      setIsUploading(false);
      return;
    }

    // Upload
    const controller = new AbortController();
    setAbortController(controller);
    setIsUploading(true);
    setProgress(0);
    setCurrentFileIndex(index);
    setMessage(`Uploading ${file.name}... (${index + 1}/${total})`);
    setStartTime(Date.now());

    try {
      const formData = new FormData();
      formData.append("file", file, file.name);
      await axios.post(
        `http://${peer.ipAddress}:${peer.port}/api/upload`,
        formData,
        {
          signal: controller.signal,
          headers: { "X-Auth-Token": token },
          onUploadProgress: (progressEvent) => {
            if (progressEvent.total) {
              setProgress(
                Math.round((progressEvent.loaded * 100) / progressEvent.total),
              );
              setUploadedBytes(progressEvent.loaded);
              setTotalBytes(progressEvent.total);
            }
          },
        },
      );
      setMessage(
        `✓ ${file.name} uploaded to ${peer.agentName} (${index + 1}/${total})`,
      );
      continueQueue(index);
    } catch (err: any) {
      const status = err.response?.status;
      if (err.name === "CanceledError" || err.code === "ERR_CANCELED") {
        setMessage(`Upload cancelled`);
      } else if (status === 400)
        setMessage(
          `❌ ${file.name} failed: ${err.response?.data?.message || "Invalid request"}`,
        );
      else if (status === 507)
        setMessage(`❌ ${file.name} failed: Insufficient disk space`);
      else if (status === 500)
        setMessage(`❌ ${file.name} failed: Server error`);
      else if (status)
        setMessage(
          `❌ ${file.name} failed: ${err.response?.data?.message || `Error ${status}`}`,
        );
      else setMessage(`❌ ${file.name} failed: Cannot reach ${peer.agentName}`);
      setIsUploading(false);
    } finally {
      setAbortController(null);
    }
  };

  const handleCancel = () => {
    abortController?.abort();
    setUploadQueue([]);
    setIsUploading(false);
  };

  const onDrop = useCallback(
    (acceptedFiles: File[]) => {
      if (acceptedFiles.length === 0) return;
      if (!selectedPeer) {
        setMessage("⚠ Please select a destination first");
        return;
      }
      setUploadQueue(acceptedFiles);
      setCurrentFileIndex(0);
      uploadFile(acceptedFiles[0], 0, acceptedFiles.length, acceptedFiles);
    },
    [selectedPeer, peers],
  );

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    multiple: true,
    disabled: isUploading,
  });

  return (
    <div className="relative min-h-screen overflow-hidden bg-linear-to-br from-[#1a0f0a] via-[#3b1f12] to-black">
      <WarpBackground active={isUploading} />

      <div className="relative z-10 min-h-screen p-6">
        <div className="max-w-7xl mx-auto">
          <div className="flex justify-between items-center mb-8">
            <h1 className="text-4xl font-bold text-orange-400">
              BeamLink Agent
            </h1>
            <div className="flex items-center gap-4">
              <button
                onClick={() => navigate("/info")}
                className="flex items-center gap-2 px-4 py-2 bg-orange-900/30 hover:bg-orange-900/50 border border-orange-700 rounded-lg text-orange-300 transition-all"
              >
                <Settings className="w-4 h-4" />
                Info
              </button>
              <button
                onClick={() => logout()}
                className="flex items-center gap-2 px-4 py-2 bg-orange-900/30 hover:bg-orange-900/50 border border-orange-700 rounded-lg text-orange-300 transition-all"
              >
                <LogOut className="w-4 h-4" />
                Logout
              </button>
            </div>
          </div>

          <div className="flex flex-col items-center w-full">
            <div className="bg-black/70 backdrop-blur-xl border border-orange-500/20 shadow-[0_0_40px_rgba(255,120,0,0.15)] rounded-2xl p-8 max-w-5xl w-full">
              <div className="text-center mb-8">
                <h2 className="text-3xl font-bold text-orange-400 tracking-wide">
                  Send File to Network
                </h2>
                {/* Show current agent identity */}
                {identity && (
                  <p className="text-sm text-orange-600 mt-1">
                    {identity.agentName} • {identity.state}
                  </p>
                )}
              </div>

              <div className="mb-6">
                <label className="block text-orange-300 text-sm mb-2 font-medium">
                  Select Destination
                </label>
                <select
                  value={selectedPeer}
                  onChange={(e) => setSelectedPeer(e.target.value)}
                  disabled={isUploading}
                  className="w-full bg-black/60 border border-orange-800 rounded-lg px-4 py-3 text-orange-100 focus:outline-none focus:border-orange-500 focus:ring-2 focus:ring-orange-500/20 transition-all disabled:opacity-50"
                >
                  <option value="">Choose destination...</option>
                  {peers.map((peer) => (
                    <option key={peer.agentId} value={peer.agentId}>
                      {peer.agentName} ({peer.ipAddress}:{peer.port}){" "}
                      {peer.agentName === "00000000-0000-0000-0000-000000000000"
                        ? "👑"
                        : peer.online
                          ? "🟢"
                          : "🔴"}
                    </option>
                  ))}
                </select>
              </div>

              <div
                {...getRootProps()}
                className={`relative border-2 border-dashed rounded-xl flex flex-col justify-center items-center gap-5 p-12 text-center transition-all cursor-pointer
                  ${isDragActive ? "border-orange-400 bg-orange-950/40 scale-105" : "border-orange-800 hover:border-orange-500"}
                  ${isUploading ? "opacity-50 cursor-not-allowed pointer-events-none" : ""}
                  ${!selectedPeer ? "opacity-50" : ""}
                `}
              >
                <input {...getInputProps()} />
                <div className="mb-4">
                  <svg
                    className="mx-auto h-16 w-16 text-orange-400"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"
                    />
                  </svg>
                </div>
                <p className="text-lg text-orange-300">
                  {isDragActive
                    ? "Drop files here"
                    : selectedPeer
                      ? "Drag & drop files here"
                      : "Select a destination first"}
                </p>
                {selectedPeer && (
                  <span className="bg-linear-to-r from-orange-600 to-amber-500 text-black px-6 py-3 rounded-lg hover:from-orange-500 hover:to-yellow-400 transition-all font-medium shadow-lg shadow-orange-900/40">
                    Choose Files
                  </span>
                )}
              </div>

              {progressVisible && (
                <div className="mt-6">
                  <div className="flex justify-between items-center mb-2">
                    <span className="text-sm text-orange-300">
                      Uploading... {formatElapsedTime(elapsedTime)} •{" "}
                      {formatSpeed(uploadedBytes, elapsedTime)} •{" "}
                      {(uploadedBytes / 1024 / 1024).toFixed(1)} MB/
                      {(totalBytes / 1024 / 1024).toFixed(1)} MB
                      {uploadQueue.length > 1 &&
                        ` • File ${currentFileIndex + 1}/${uploadQueue.length}`}
                    </span>
                    <span className="text-sm font-medium text-orange-400">
                      {progress}%
                    </span>
                  </div>
                  <div className="w-full bg-black/60 rounded-full h-3 overflow-hidden border border-orange-800">
                    <div
                      className="bg-linear-to-r from-orange-600 to-amber-400 h-full transition-all duration-300 ease-out shadow-[0_0_15px_rgba(255,140,0,0.6)]"
                      style={{ width: `${progress}%` }}
                    />
                  </div>
                  <div className="flex justify-center w-full">
                    <button
                      onClick={handleCancel}
                      className="mt-4 bg-red-700 hover:bg-red-600 text-white px-4 py-2 rounded-lg transition-colors shadow-lg shadow-red-900/40"
                    >
                      Cancel Upload
                    </button>
                  </div>
                </div>
              )}

              {message && (
                <div
                  className={`mt-6 p-4 rounded-lg border ${
                    message.includes("successfully") || message.includes("✓")
                      ? "bg-green-900/50 border-green-600"
                      : message.includes("failed") ||
                          message.includes("❌") ||
                          message.includes("⚠")
                        ? "bg-red-900/50 border-red-600"
                        : "bg-orange-900/50 border-orange-600"
                  }`}
                >
                  <p className="text-sm text-gray-200">{message}</p>
                  {(message.includes("successfully") ||
                    message.includes("✓")) && (
                    <p className="text-sm text-gray-300">
                      Uploaded in {formatElapsedTime(elapsedTime)}
                    </p>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
