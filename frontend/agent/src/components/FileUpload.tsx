import { useState, useEffect, useRef } from "react";
import { useAuth } from "../context/AuthContext";
import { FileBrowserModal } from "./FileBrowserModal";
import { useTransfer } from "../context/TransferContext";

interface Peer {
  agentId: string;
  agentName: string;
  ipAddress: string;
  port: number;
  online: boolean;
}

interface FileUploadProps {
  onTransferStarted: () => void;
}

export const FileUpload = ({ onTransferStarted }: FileUploadProps) => {
  const { apiClient } = useAuth();
  const { initiateDispatch } = useTransfer();

  const [showBrowser, setShowBrowser] = useState(false);
  const [peers, setPeers] = useState<Peer[]>([]);
  const [selectedPeer, setSelectedPeer] = useState(
    () => localStorage.getItem("lastSelectedPeer") ?? "",
  );
  const [message, setMessage] = useState("");
  const [isSending, setIsSending] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const dragCounter = useRef(0);

  const selectedPeerObject = peers.find((p) => p.agentId === selectedPeer);
  const isOffline = !!selectedPeerObject && !selectedPeerObject.online;

  /**
   * Persists peer selection across refreshes.
   * Restored on mount only if that peer is still in the list.
   */
  const handlePeerSelect = (agentId: string) => {
    localStorage.setItem("lastSelectedPeer", agentId);
    setSelectedPeer(agentId);
  };

  useEffect(() => {
    loadPeers();
    const interval = setInterval(loadPeers, 5000);
    return () => clearInterval(interval);
  }, []);

  const loadPeers = async () => {
    try {
      const res = await apiClient.get("/peers");
      setPeers(res.data.peers || res.data);
    } catch {
      setPeers([]);
    }
  };

  const dispatch = async (paths: string[]) => {
    if (!selectedPeer) {
      setMessage("Select a destination agent first.");
      return;
    }
    if (isOffline) {
      setMessage("Selected agent is offline.");
      return;
    }
    const validPaths = paths.filter(Boolean);
    if (validPaths.length === 0) {
      setMessage("No valid paths selected.");
      return;
    }

    const peer = selectedPeerObject!;
    setIsSending(true);
    setMessage("");

    try {
      const results = await initiateDispatch({
        paths: validPaths,
        targetAgentId: peer.agentId,
        targetIp: peer.ipAddress,
        targetPort: peer.port,
      });

      if (results.length > 0) {
        onTransferStarted();
      } else {
        setMessage("Nothing was initiated. Check the selected paths.");
      }
    } catch (err: any) {
      const msg =
        err.response?.data?.message || err.message || "Dispatch failed.";
      setMessage(`Failed: ${msg}`);
    } finally {
      setIsSending(false);
    }
  };

  /** dragCounter prevents flicker when dragging over child elements */
  const handleDragEnter = (e: React.DragEvent) => {
    e.preventDefault();
    dragCounter.current++;
    setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    dragCounter.current--;
    if (dragCounter.current === 0) setIsDragging(false);
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
  };

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    dragCounter.current = 0;
    setIsDragging(false);

    const files = Array.from(e.dataTransfer.files);
    if (files.length === 0) return;

    const paths = window.electronAPI.getPathForFiles(files);
    if (paths.length > 0) {
      await dispatch(paths);
    }
  };

  const canInteract = !isSending && !!selectedPeer && !isOffline;

  return (
    <div className="flex flex-col items-center w-full">
      <div
        className="bg-black/70 backdrop-blur-xl border border-orange-500/20
                    shadow-[0_0_40px_rgba(255,120,0,0.15)]
                    rounded-2xl p-8 max-w-5xl w-full"
      >
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold text-orange-400 tracking-wide">
            Send to Network
          </h1>
        </div>

        <div className="mb-8">
          <label className="block text-orange-300 text-sm mb-2 font-medium">
            Destination Agent
          </label>
          <select
            value={selectedPeer}
            onChange={(e) => handlePeerSelect(e.target.value)}
            disabled={isSending}
            className="w-full bg-black/60 border border-orange-800 rounded-lg px-4 py-3
                       text-orange-100 focus:outline-none focus:border-orange-500
                       focus:ring-2 focus:ring-orange-500/20 transition-all disabled:opacity-50"
          >
            <option value="">Choose destination agent...</option>
            {peers.map((peer) => (
              <option key={peer.agentId} value={peer.agentId}>
                {peer.agentName} ({peer.ipAddress}:{peer.port}) —{" "}
                {peer.online ? "Online" : "Offline"}
              </option>
            ))}
          </select>
          {peers.length === 0 && (
            <p className="text-orange-300/60 text-sm mt-2">
              No agents registered.
            </p>
          )}
          {isOffline && (
            <p className="text-red-400 text-sm mt-2">This agent is offline.</p>
          )}
        </div>

        <div
          onDragEnter={handleDragEnter}
          onDragLeave={handleDragLeave}
          onDragOver={handleDragOver}
          onDrop={canInteract ? handleDrop : undefined}
          className={`relative border-2 border-dashed rounded-xl flex flex-col
                      justify-center items-center gap-6 p-12 text-center min-h-64
                      transition-all
                      ${
                        isDragging && canInteract
                          ? "border-orange-400 bg-orange-950/40 scale-[1.01]"
                          : "border-orange-800"
                      }
                      ${!canInteract ? "opacity-50 cursor-not-allowed" : "cursor-pointer"}`}
        >
          <svg
            className={`h-16 w-16 transition-colors ${isDragging && canInteract ? "text-orange-400" : "text-orange-700"}`}
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

          <p className="text-orange-400 text-lg">
            {isSending
              ? "Initiating transfer..."
              : !selectedPeer
                ? "Select a destination agent above"
                : isOffline
                  ? "Selected agent is offline"
                  : isDragging
                    ? "Drop to send"
                    : "Drag files or folders here"}
          </p>

          {canInteract && !isSending && (
            <button
              onClick={() => setShowBrowser(true)}
              className="px-6 py-2.5 bg-orange-800 hover:bg-orange-700
                         text-white rounded-lg transition-all font-medium"
            >
              Choose Files
            </button>
          )}

          {isSending && (
            <div className="w-8 h-8 border-2 border-orange-400 border-t-transparent rounded-full animate-spin" />
          )}
        </div>

        {message && (
          <div className="mt-6 p-4 rounded-lg border bg-red-900/50 border-red-600">
            <p className="text-sm text-gray-200">{message}</p>
          </div>
        )}
      </div>

      <FileBrowserModal
        isOpen={showBrowser}
        onClose={() => setShowBrowser(false)}
        onConfirm={async (paths) => {
          setShowBrowser(false);
          await dispatch(paths);
        }}
      />
    </div>
  );
};
