import { useEffect, useState, useCallback } from "react";
import { useAuth } from "../context/AuthContext";
import { useData } from "../context/DataContext";
import { Trash2 } from "lucide-react";

interface Peer {
  id: string;
  agentName: string;
  ipAddress: string;
  port: number;
  online: boolean;
}

interface Transfer {
  transferId: string;
  status: string;
  confirmedOffset: number;
  fileSize: number;
  fileName: string;
  failureReason: string | null;
  targetAgentId: string;
  createdAt: string;
  lastChunkAt: string | null;
}

interface TransferViewProps {
  onUploadingChange?: (active: boolean) => void;
}

const STATUS_COLORS: Record<string, string> = {
  PENDING: "text-orange-400",
  ACTIVE: "text-blue-400",
  PAUSED: "text-yellow-400",
  COMPLETED: "text-green-400",
  CANCELLED: "text-gray-400",
  FAILED: "text-red-400",
  EXPIRED: "text-gray-500",
};

const formatBytes = (bytes: number): string => {
  if (bytes === 0) return "0 B";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024)
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
};

const formatSpeed = (
  offset: number,
  createdAt: string,
  lastChunkAt: string | null,
): string => {
  if (!lastChunkAt || offset === 0) return "—";
  const elapsedMs =
    new Date(lastChunkAt).getTime() - new Date(createdAt).getTime();
  if (elapsedMs <= 0) return "—";
  const bytesPerSec = (offset / elapsedMs) * 1000;
  if (bytesPerSec < 1024) return `${bytesPerSec.toFixed(0)} B/s`;
  if (bytesPerSec < 1024 * 1024)
    return `${(bytesPerSec / 1024).toFixed(1)} KB/s`;
  return `${(bytesPerSec / 1024 / 1024).toFixed(2)} MB/s`;
};

const formatDate = (iso: string): string => {
  const d = new Date(iso);
  return d.toLocaleString();
};

export const TransferView = ({ onUploadingChange }: TransferViewProps) => {
  const { getAllTransfers, resumeTransfer, cancelTransfer, deleteTransfer } =
    useData();

  const [transfers, setTransfers] = useState<Transfer[]>([]);
  const [peers, setPeers] = useState<Peer[]>([]);
  const [resumeErrors, setResumeErrors] = useState<Record<string, string>>({});
  const [actionLoading, setActionLoading] = useState<Record<string, boolean>>(
    {},
  );

  const { apiClient } = useAuth();

  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), 100);
    return () => clearInterval(interval);
  }, []);

  const formatElapsed = (
    createdAt: string,
    lastChunkAt: string | null,
    status: string,
  ): string => {
    let ms = 0;
    if (status === "COMPLETED" && lastChunkAt) {
      ms = new Date(lastChunkAt).getTime() - new Date(createdAt).getTime();
    } else if (status === "ACTIVE") {
      ms = now - new Date(createdAt).getTime();
    } else {
      return "—";
    }

    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    const minutes = Math.floor(ms / 60000);
    const seconds = Math.floor((ms % 60000) / 1000);
    const millis = Math.floor((ms % 1000) / 10);
    return `${minutes}m ${seconds}.${String(millis).padStart(2, "0")}s`;
  };

  const loadPeers = useCallback(async () => {
    try {
      const res = await apiClient.get("/peers");
      const peerList = res.data.peers || res.data;
      setPeers(peerList);
    } catch {
      setPeers([]);
    }
  }, [apiClient]);

  const loadTransfers = useCallback(async () => {
    try {
      const data = await getAllTransfers();
      setTransfers(data);
      const hasActive = data.some(
        (t) => t.status === "ACTIVE" || t.status === "PENDING",
      );
      onUploadingChange?.(hasActive); // for warp effect
    } catch {}
  }, [getAllTransfers, onUploadingChange]);

  // Initial load
  useEffect(() => {
    loadPeers();
    loadTransfers();
  }, []);

  // Poll every 2s only while there are active transfers
  useEffect(() => {
    const hasActive = transfers.some(
      (t) => t.status === "ACTIVE" || t.status === "PENDING",
    );
    if (!hasActive) return;
    const interval = setInterval(loadTransfers, 2000);
    return () => clearInterval(interval);
  }, [transfers, loadTransfers]);

  // Keep peer list fresh
  useEffect(() => {
    const interval = setInterval(loadPeers, 5000);
    return () => clearInterval(interval);
  }, [loadPeers]);

  const getPeer = (agentId: string) => peers.find((p) => p.id === agentId);

  const handleResume = async (transferId: string) => {
    setActionLoading((prev) => ({ ...prev, [transferId]: true }));
    setResumeErrors((prev) => ({ ...prev, [transferId]: "" }));
    try {
      await resumeTransfer(transferId);
      await loadTransfers();
    } catch (err: any) {
      const status = err.response?.status;
      let msg = "Resume failed";
      if (status === 503) msg = "Peer is offline";
      else if (status === 409) msg = "Transfer is not paused";
      else if (err.response?.data?.message) msg = err.response.data.message;
      setResumeErrors((prev) => ({ ...prev, [transferId]: msg }));
    } finally {
      setActionLoading((prev) => ({ ...prev, [transferId]: false }));
    }
  };

  const handleDelete = async (transferId: string) => {
    setActionLoading((prev) => ({ ...prev, [transferId]: true }));
    try {
      await deleteTransfer(transferId);
      setTransfers((prev) => prev.filter((t) => t.transferId !== transferId));
      onUploadingChange?.(
        transfers
          .filter((t) => t.transferId !== transferId)
          .some((t) => t.status === "ACTIVE" || t.status === "PENDING"),
      );
    } catch {
    } finally {
      setActionLoading((prev) => ({ ...prev, [transferId]: false }));
    }
  };

  const handleCancel = async (transferId: string) => {
    setActionLoading((prev) => ({ ...prev, [transferId]: true }));
    try {
      await cancelTransfer(transferId);
      await loadTransfers();
    } catch {
    } finally {
      setActionLoading((prev) => ({ ...prev, [transferId]: false }));
    }
  };

  return (
    <div className="p-6">
      <div className="max-w-6xl mx-auto">
        <div className="bg-black/70 backdrop-blur-xl border border-orange-500/20 shadow-[0_0_40px_rgba(255,120,0,0.15)] rounded-2xl p-8">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-3xl font-bold text-orange-400">Transfers</h2>
            <button
              onClick={loadTransfers}
              className="text-sm text-orange-400 hover:text-orange-300 border border-orange-800 px-3 py-1 rounded-lg transition-all"
            >
              Refresh
            </button>
          </div>

          {transfers.length === 0 ? (
            <p className="text-orange-700 text-center py-12">
              No transfers yet
            </p>
          ) : (
            <div className="space-y-3">
              {transfers.map((t) => {
                const peer = getPeer(t.targetAgentId);
                const peerOnline = peer?.online ?? false;
                const peerName =
                  peer?.agentName ?? t.targetAgentId?.slice(0, 8) ?? "—";
                const progress =
                  t.fileSize > 0
                    ? Math.min(
                        100,
                        Math.round((t.confirmedOffset / t.fileSize) * 100),
                      )
                    : 0;
                const speed = formatSpeed(
                  t.confirmedOffset,
                  t.createdAt,
                  t.lastChunkAt,
                );
                const loading = actionLoading[t.transferId];
                const resumeErr = resumeErrors[t.transferId];
                const canResume = t.status === "PAUSED" && peerOnline;
                const canCancel =
                  t.status === "ACTIVE" || t.status === "PAUSED";
                const canDelete =
                  t.status === "COMPLETED" ||
                  t.status === "FAILED" ||
                  t.status === "CANCELLED" ||
                  t.status === "EXPIRED";

                return (
                  <div
                    key={t.transferId}
                    className="border border-orange-900/50 rounded-xl p-4 bg-black/40"
                  >
                    {/* Top row */}
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex-1 min-w-0">
                        <p className="text-orange-200 font-medium truncate">
                          {t.fileName}
                        </p>
                        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 mt-1 text-xs text-orange-600">
                          <span>{formatBytes(t.fileSize)}</span>
                          <span>·</span>
                          <span>{formatDate(t.createdAt)}</span>
                          <span>·</span>
                          <span className="flex items-center gap-1">
                            <span
                              style={{
                                color: peerOnline ? "#4ade80" : "#ef4444",
                              }}
                            >
                              ●
                            </span>
                            {peerName}
                          </span>
                          <span>·</span>
                          <span>avg {speed}</span>
                          <span>·</span>
                          <span>
                            {formatElapsed(
                              t.createdAt,
                              t.lastChunkAt,
                              t.status,
                            )}
                          </span>
                        </div>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <span
                          className={`text-xs font-mono font-medium ${STATUS_COLORS[t.status] ?? "text-orange-400"}`}
                        >
                          {t.status}
                        </span>
                        {canResume && (
                          <button
                            onClick={() => handleResume(t.transferId)}
                            disabled={loading}
                            className="text-xs px-3 py-1 bg-orange-800 hover:bg-orange-700 disabled:opacity-40 text-orange-200 rounded-lg transition-all"
                          >
                            {loading ? "..." : "Resume"}
                          </button>
                        )}
                        {t.status === "PAUSED" && !peerOnline && (
                          <span className="text-xs text-red-500 px-2">
                            Peer offline
                          </span>
                        )}
                        {canCancel && (
                          <button
                            onClick={() => handleCancel(t.transferId)}
                            disabled={loading}
                            className="text-xs px-3 py-1 bg-red-900/60 hover:bg-red-800 disabled:opacity-40 text-red-300 rounded-lg transition-all"
                          >
                            {loading ? "..." : "Cancel"}
                          </button>
                        )}
                        {canDelete && (
                          <button
                            onClick={() => handleDelete(t.transferId)}
                            disabled={loading}
                            className="p-1.5 hover:bg-red-900/40 disabled:opacity-40 text-red-500 hover:text-red-400 rounded-lg transition-all"
                          >
                            {loading ? (
                              "..."
                            ) : (
                              <Trash2 className="w-3.5 h-3.5" />
                            )}
                          </button>
                        )}
                      </div>
                    </div>

                    {/* Progress bar */}
                    {(t.status === "ACTIVE" ||
                      t.status === "PAUSED" ||
                      t.status === "COMPLETED") && (
                      <div className="mt-3">
                        <div className="flex justify-between text-xs text-orange-600 mb-1">
                          <span>
                            {formatBytes(t.confirmedOffset)} /{" "}
                            {formatBytes(t.fileSize)}
                          </span>
                          <span>{progress}%</span>
                        </div>
                        <div className="w-full bg-black/60 rounded-full h-2 border border-orange-900/50">
                          <div
                            className={`h-full rounded-full transition-all duration-500 ${
                              t.status === "COMPLETED"
                                ? "bg-green-600"
                                : t.status === "PAUSED"
                                  ? "bg-yellow-600"
                                  : "bg-linear-to-r from-orange-600 to-amber-400 shadow-[0_0_8px_rgba(255,140,0,0.4)]"
                            }`}
                            style={{ width: `${progress}%` }}
                          />
                        </div>
                      </div>
                    )}

                    {/* Failure reason */}
                    {t.status === "FAILED" && t.failureReason && (
                      <p className="mt-2 text-xs text-red-400 font-mono truncate">
                        {t.failureReason}
                      </p>
                    )}

                    {/* Resume error */}
                    {resumeErr && (
                      <p className="mt-2 text-xs text-red-400">{resumeErr}</p>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
