import { useEffect, useState, useCallback, useMemo, useRef } from "react";
import { useAuth } from "../context/AuthContext";
import {
  useTransfer,
  type TransferSummary,
  type TransferType,
} from "../context/TransferContext";
import { useAgent, type Peer } from "../context/AgentContext";
import { Trash2 } from "lucide-react";

interface TransferViewProps {
  onUploadingChange?: (active: boolean) => void;
}

const STATUS_COLORS: Record<string, string> = {
  PENDING: "text-orange-400",
  ACTIVE: "text-blue-400",
  PAUSED: "text-yellow-400",
  PARTIAL: "text-amber-400",
  COMPLETED: "text-green-400",
  CANCELLED: "text-gray-400",
  FAILED: "text-red-400",
  EXPIRED: "text-gray-500",
};

const TYPE_LABEL: Record<TransferType, string> = {
  SINGLE: "File",
  BATCH: "Batch",
  DIRECTORY: "Folder",
};

const formatBytes = (bytes: number): string => {
  if (bytes === 0) return "0 B";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024)
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
};

const formatDate = (iso: string): string => new Date(iso).toLocaleString();

/** Derives a single status from multiple transfer statuses */
const deriveGroupStatus = (statuses: string[]): string => {
  if (statuses.some((s) => s === "ACTIVE")) return "ACTIVE";
  if (statuses.some((s) => s === "PAUSED")) return "PAUSED";
  if (statuses.every((s) => s === "COMPLETED")) return "COMPLETED";
  if (statuses.every((s) => s === "CANCELLED")) return "CANCELLED";
  if (statuses.some((s) => s === "FAILED")) return "PARTIAL";
  return statuses[0];
};

interface DispatchGroup {
  dispatchId: string;
  transfers: TransferSummary[];
  totalSize: number;
  confirmedBytes: number;
  status: string;
  createdAt: string;
  targetAgentId: string;
}

const formatElapsedMs = (ms: number): string => {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  const minutes = Math.floor(ms / 60000);
  const seconds = Math.floor((ms % 60000) / 1000);
  return `${minutes}m ${seconds}s`;
};

export const TransferView = ({ onUploadingChange }: TransferViewProps) => {
  const {
    getAllTransfers,
    pauseTransfer,
    resumeTransfer,
    cancelTransfer,
    deleteTransfer,
    getChildTransfers,
  } = useTransfer();
  const { getPeers } = useAgent();
  const { apiClient, isAuthenticated } = useAuth();

  const [transfers, setTransfers] = useState<TransferSummary[]>([]);
  const [peers, setPeers] = useState<Peer[]>([]);
  const [actionLoading, setActionLoading] = useState<Record<string, boolean>>(
    {},
  );
  const [actionErrors, setActionErrors] = useState<Record<string, string>>({});
  const [now, setNow] = useState(Date.now());
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [children, setChildren] = useState<Record<string, TransferSummary[]>>(
    {},
  );
  const [groupExpanded, setGroupExpanded] = useState<Record<string, boolean>>(
    {},
  );

  /**
   * Ref tracks if any transfer is active without causing re-renders.
   * Polling interval reads this instead of state to avoid cascade restarts.
   */
  const hasActiveRef = useRef(false);

  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), 100);
    return () => clearInterval(interval);
  }, []);

  const loadPeers = useCallback(async () => {
    try {
      const data = await getPeers();
      setPeers(data);
    } catch {
      setPeers([]);
    }
  }, [getPeers]);

  const loadTransfers = useCallback(async () => {
    try {
      const data = await getAllTransfers();
      setTransfers(data);
      const hasActive = data.some(
        (t) => t.status === "ACTIVE" || t.status === "PENDING",
      );
      hasActiveRef.current = hasActive;
      onUploadingChange?.(hasActive);
    } catch {}
  }, [getAllTransfers, onUploadingChange]);

  useEffect(() => {
    if (!isAuthenticated) return;
    loadPeers();
    loadTransfers();
  }, [isAuthenticated]);

  /**
   * Stable interval — no state in deps.
   * Reads hasActiveRef to decide whether to poll, avoids cascade re-renders.
   */
  useEffect(() => {
    const interval = setInterval(() => {
      if (hasActiveRef.current) {
        loadTransfers();
      }
    }, 500);
    return () => clearInterval(interval);
  }, [loadTransfers]);

  useEffect(() => {
    if (!isAuthenticated) return;
    const interval = setInterval(loadPeers, 5000);
    return () => clearInterval(interval);
  }, [isAuthenticated, loadPeers]);

  const { groups, standalones } = useMemo(() => {
    const groupMap = new Map<string, TransferSummary[]>();
    const standalones: TransferSummary[] = [];

    for (const t of transfers) {
      if (
        t.dispatchId &&
        transfers.filter((x) => x.dispatchId === t.dispatchId).length > 1
      ) {
        const existing = groupMap.get(t.dispatchId) ?? [];
        groupMap.set(t.dispatchId, [...existing, t]);
      } else {
        standalones.push(t);
      }
    }

    const groups: DispatchGroup[] = Array.from(groupMap.entries()).map(
      ([dispatchId, members]) => ({
        dispatchId,
        transfers: members,
        totalSize: members.reduce((sum, t) => sum + t.totalSize, 0),
        confirmedBytes: members.reduce((sum, t) => sum + t.confirmedBytes, 0),
        status: deriveGroupStatus(members.map((t) => t.status)),
        createdAt: members[0].createdAt,
        targetAgentId: members[0].targetAgentId,
      }),
    );

    groups.sort(
      (a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    );

    return { groups, standalones };
  }, [transfers]);

  const toggleExpand = async (t: TransferSummary) => {
    if (t.type === "SINGLE") return;
    const isOpen = expanded[t.id];
    if (!isOpen && !children[t.id]) {
      try {
        const data = await getChildTransfers(t.id, t.type);
        setChildren((prev) => ({ ...prev, [t.id]: data }));
      } catch {
        setChildren((prev) => ({ ...prev, [t.id]: [] }));
      }
    }
    setExpanded((prev) => ({ ...prev, [t.id]: !isOpen }));
  };

  const toggleGroup = (dispatchId: string) => {
    setGroupExpanded((prev) => ({ ...prev, [dispatchId]: !prev[dispatchId] }));
  };

  const getPeer = (agentId: string) => peers.find((p) => p.id === agentId);

  const setLoading = (id: string, val: boolean) =>
    setActionLoading((prev) => ({ ...prev, [id]: val }));

  const setError = (id: string, msg: string) =>
    setActionErrors((prev) => ({ ...prev, [id]: msg }));

  const handlePause = async (t: TransferSummary) => {
    setLoading(t.id, true);
    setError(t.id, "");
    try {
      await pauseTransfer(t.id, t.type);
      await loadTransfers();
    } catch (err: any) {
      setError(t.id, err.response?.data?.message || "Pause failed");
    } finally {
      setLoading(t.id, false);
    }
  };

  const handleResume = async (t: TransferSummary) => {
    setLoading(t.id, true);
    setError(t.id, "");
    try {
      await resumeTransfer(t.id, t.type);
      await loadTransfers();
    } catch (err: any) {
      const status = err.response?.status;
      let msg = "Resume failed";
      if (status === 503) msg = "Agent is offline";
      else if (status === 409) msg = "Not paused";
      else if (err.response?.data?.message) msg = err.response.data.message;
      setError(t.id, msg);
    } finally {
      setLoading(t.id, false);
    }
  };

  const handleCancel = async (t: TransferSummary) => {
    setLoading(t.id, true);
    setError(t.id, "");
    try {
      await cancelTransfer(t.id, t.type);
      await loadTransfers();
    } catch {
    } finally {
      setLoading(t.id, false);
    }
  };

  const handleDelete = async (t: TransferSummary) => {
    setLoading(t.id, true);
    try {
      await deleteTransfer(t.id, t.type);
      setTransfers((prev) => prev.filter((x) => x.id !== t.id));
    } catch {
    } finally {
      setLoading(t.id, false);
    }
  };

  const formatElapsed = (t: TransferSummary): string => {
    let ms = 0;
    if (t.status === "COMPLETED" && t.completedAt) {
      ms = new Date(t.completedAt).getTime() - new Date(t.createdAt).getTime();
    } else if (t.status === "ACTIVE") {
      ms = now - new Date(t.createdAt).getTime();
    } else {
      return "-";
    }
    return formatElapsedMs(ms);
  };

  const formatSpeed = (t: TransferSummary): string => {
    if (t.status !== "ACTIVE" || t.confirmedBytes === 0) return "";
    const elapsedSec = t.activeTransferMs / 1000;
    if (elapsedSec < 1) return "";
    const mbps = (t.confirmedBytes * 8) / elapsedSec / 1_000_000;
    return `${mbps.toFixed(1)} Mb/s`;
  };

  const handleOpenFolder = async () => {
    try {
      await apiClient.post("/storage/open-uploads-folder");
    } catch {}
  };

  /** Single transfer row - used both standalone and inside dispatch groups */
  const renderTransferRow = (t: TransferSummary, indented = false) => {
    const peer = getPeer(t.targetAgentId);
    const peerOnline = peer?.online ?? false;
    const peerName = peer?.agentName ?? t.targetAgentId?.slice(0, 8) ?? "-";
    const progress =
      t.totalSize > 0
        ? Math.min(100, Math.round((t.confirmedBytes / t.totalSize) * 100))
        : 0;
    const loading = actionLoading[t.id];
    const isExpanded = expanded[t.id];

    const canPause = t.status === "ACTIVE";
    const canResume = t.status === "PAUSED" && peerOnline;
    const canCancel = t.status === "ACTIVE" || t.status === "PAUSED";
    const canDelete = [
      "COMPLETED",
      "FAILED",
      "CANCELLED",
      "EXPIRED",
      "PARTIAL",
    ].includes(t.status);

    return (
      <div
        key={t.id}
        className={`border border-orange-900/50 rounded-xl p-4 bg-black/40 ${indented ? "ml-4 border-orange-900/30 bg-black/20" : ""}`}
      >
        <div className="flex items-start justify-between gap-4">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-1">
              <span className="text-xs font-mono px-1.5 py-0.5 rounded bg-orange-900/60 text-orange-400 border border-orange-800/50">
                {TYPE_LABEL[t.type]}
              </span>
              <p className="text-orange-200 font-medium truncate">{t.name}</p>
              {t.type !== "SINGLE" && (
                <button
                  onClick={() => toggleExpand(t)}
                  className="text-orange-500 hover:text-orange-300 transition-all ml-1 text-xs shrink-0"
                >
                  {isExpanded ? "▲" : "▼"}
                </button>
              )}
            </div>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-orange-600">
              <span>{formatBytes(t.totalSize)}</span>
              {t.totalFiles > 1 && (
                <>
                  <span>·</span>
                  <span>{t.totalFiles} files</span>
                </>
              )}
              <span>·</span>
              <span>{formatDate(t.createdAt)}</span>
              {!indented && (
                <>
                  <span>·</span>
                  <span className="flex items-center gap-1">
                    <span style={{ color: peerOnline ? "#4ade80" : "#ef4444" }}>
                      ●
                    </span>
                    {peerName}
                  </span>
                </>
              )}
              <span>·</span>
              <span>{formatElapsed(t)}</span>
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <span
              className={`text-xs font-mono font-medium ${STATUS_COLORS[t.status] ?? "text-orange-400"}`}
            >
              {t.status}
            </span>
            {canPause && (
              <button
                onClick={() => handlePause(t)}
                disabled={loading}
                className="text-xs px-3 py-1 bg-yellow-900/60 hover:bg-yellow-800 disabled:opacity-40 text-yellow-300 rounded-lg transition-all"
              >
                {loading ? "..." : "Pause"}
              </button>
            )}
            {canResume && (
              <button
                onClick={() => handleResume(t)}
                disabled={loading}
                className="text-xs px-3 py-1 bg-orange-800 hover:bg-orange-700 disabled:opacity-40 text-orange-200 rounded-lg transition-all"
              >
                {loading ? "..." : "Resume"}
              </button>
            )}
            {t.status === "PAUSED" && !peerOnline && (
              <span className="text-xs text-red-500 px-2">Agent offline</span>
            )}
            {canCancel && (
              <button
                onClick={() => handleCancel(t)}
                disabled={loading}
                className="text-xs px-3 py-1 bg-red-900/60 hover:bg-red-800 disabled:opacity-40 text-red-300 rounded-lg transition-all"
              >
                {loading ? "..." : "Cancel"}
              </button>
            )}
            {canDelete && (
              <button
                onClick={() => handleDelete(t)}
                disabled={loading}
                className="p-1.5 hover:bg-red-900/40 disabled:opacity-40 text-red-500 hover:text-red-400 rounded-lg transition-all"
              >
                {loading ? "..." : <Trash2 className="w-3.5 h-3.5" />}
              </button>
            )}
          </div>
        </div>

        {["ACTIVE", "PAUSED", "COMPLETED"].includes(t.status) && (
          <div className="mt-3">
            <div className="flex justify-between text-xs text-orange-600 mb-1">
              <span>
                {formatBytes(t.confirmedBytes)} / {formatBytes(t.totalSize)}
              </span>
              <span>
                {progress}%
                {t.status === "ACTIVE" && formatSpeed(t) && (
                  <span className="ml-2 text-orange-400">{formatSpeed(t)}</span>
                )}
              </span>
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

        {isExpanded && children[t.id] && (
          <div className="mt-3 space-y-1 border-t border-orange-900/30 pt-3">
            {children[t.id].map((child) => {
              const childProgress =
                child.totalSize > 0
                  ? Math.min(
                      100,
                      Math.round(
                        (child.confirmedBytes / child.totalSize) * 100,
                      ),
                    )
                  : 0;
              return (
                <div
                  key={child.id}
                  className="px-3 py-2 rounded-lg bg-black/30 border border-orange-900/20"
                >
                  <div className="flex items-center justify-between">
                    <p className="text-xs text-orange-200 truncate flex-1 min-w-0">
                      {child.name}
                    </p>
                    <span
                      className={`text-xs font-mono ml-3 shrink-0 ${STATUS_COLORS[child.status] ?? "text-orange-400"}`}
                    >
                      {child.status}
                    </span>
                  </div>
                  <div className="flex justify-between text-xs text-orange-700 mt-1 mb-1">
                    <span>
                      {formatBytes(child.confirmedBytes)} /{" "}
                      {formatBytes(child.totalSize)}
                    </span>
                    <span>{childProgress}%</span>
                  </div>
                  <div className="w-full bg-black/60 rounded-full h-1 border border-orange-900/30">
                    <div
                      className={`h-full rounded-full transition-all duration-500 ${
                        child.status === "COMPLETED"
                          ? "bg-green-600"
                          : child.status === "PAUSED"
                            ? "bg-yellow-600"
                            : child.status === "FAILED"
                              ? "bg-red-600"
                              : "bg-orange-600"
                      }`}
                      style={{ width: `${childProgress}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {t.status === "FAILED" && t.failureReason && (
          <p className="mt-2 text-xs text-red-400 font-mono truncate">
            {t.failureReason}
          </p>
        )}
        {actionErrors[t.id] && (
          <p className="mt-2 text-xs text-red-400">{actionErrors[t.id]}</p>
        )}
      </div>
    );
  };

  /** Dispatch group row - wraps multiple transfers under one dispatchId */
  const renderDispatchGroup = (group: DispatchGroup) => {
    const peer = getPeer(group.targetAgentId);
    const peerOnline = peer?.online ?? false;
    const peerName = peer?.agentName ?? group.targetAgentId?.slice(0, 8) ?? "-";
    const progress =
      group.totalSize > 0
        ? Math.min(
            100,
            Math.round((group.confirmedBytes / group.totalSize) * 100),
          )
        : 0;
    const isOpen = groupExpanded[group.dispatchId];

    return (
      <div
        key={group.dispatchId}
        className="border border-orange-700/50 rounded-xl p-4 bg-black/40"
      >
        <div className="flex items-start justify-between gap-4">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-1">
              <span className="text-xs font-mono px-1.5 py-0.5 rounded bg-orange-800/60 text-orange-300 border border-orange-700/50">
                Multiple
              </span>
              <p className="text-orange-200 font-medium">
                {group.transfers.map((t) => TYPE_LABEL[t.type]).join(" + ")}
              </p>
              <button
                onClick={() => toggleGroup(group.dispatchId)}
                className="text-orange-500 hover:text-orange-300 transition-all ml-1 text-xs shrink-0"
              >
                {isOpen ? "▲" : "▼"}
              </button>
            </div>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-orange-600">
              <span>{formatBytes(group.totalSize)}</span>
              <span>·</span>
              <span>{group.transfers.length} transfers</span>
              <span>·</span>
              <span>{formatDate(group.createdAt)}</span>
              <span>·</span>
              <span className="flex items-center gap-1">
                <span style={{ color: peerOnline ? "#4ade80" : "#ef4444" }}>
                  ●
                </span>
                {peerName}
              </span>
            </div>
          </div>
          <span
            className={`text-xs font-mono font-medium shrink-0 ${STATUS_COLORS[group.status] ?? "text-orange-400"}`}
          >
            {group.status}
          </span>
        </div>

        {["ACTIVE", "PAUSED", "COMPLETED"].includes(group.status) && (
          <div className="mt-3">
            <div className="flex justify-between text-xs text-orange-600 mb-1">
              <span>
                {formatBytes(group.confirmedBytes)} /{" "}
                {formatBytes(group.totalSize)}
              </span>
              <span>{progress}%</span>
            </div>
            <div className="w-full bg-black/60 rounded-full h-2 border border-orange-900/50">
              <div
                className={`h-full rounded-full transition-all duration-500 ${
                  group.status === "COMPLETED"
                    ? "bg-green-600"
                    : group.status === "PAUSED"
                      ? "bg-yellow-600"
                      : "bg-linear-to-r from-orange-600 to-amber-400 shadow-[0_0_8px_rgba(255,140,0,0.4)]"
                }`}
                style={{ width: `${progress}%` }}
              />
            </div>
          </div>
        )}

        {isOpen && (
          <div className="mt-3 space-y-2 border-t border-orange-900/30 pt-3">
            {group.transfers.map((t) => renderTransferRow(t, true))}
          </div>
        )}
      </div>
    );
  };

  const renderItems = useMemo(() => {
    type Item =
      | { kind: "standalone"; t: TransferSummary; createdAt: number }
      | { kind: "group"; g: DispatchGroup; createdAt: number };

    const items: Item[] = [
      ...standalones.map((t) => ({
        kind: "standalone" as const,
        t,
        createdAt: new Date(t.createdAt).getTime(),
      })),
      ...groups.map((g) => ({
        kind: "group" as const,
        g,
        createdAt: new Date(g.createdAt).getTime(),
      })),
    ];

    return items.sort((a, b) => b.createdAt - a.createdAt);
  }, [standalones, groups]);

  return (
    <div className="min-h-0 overflow-hidden px-6 py-6">
      <div className="max-w-6xl mx-auto">
        <div className="bg-black/70 overflow-hidden backdrop-blur-xl border border-orange-500/20 shadow-[0_0_40px_rgba(255,120,0,0.15)] rounded-2xl p-8 min-h-[60vh]">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-3xl font-bold text-orange-400">Transfers</h2>
            <div className="flex items-center justify-end gap-5 mb-6">
              <button
                onClick={handleOpenFolder}
                className="text-sm cursor-pointer font-bold text-orange-400 hover:text-orange-300 border-2 border-orange-800 px-3 py-1 rounded-lg transition-all"
              >
                Open Directory
              </button>
              <button
                onClick={loadTransfers}
                className="text-sm cursor-pointer font-bold text-orange-400 hover:text-orange-300 border-2 border-orange-800 px-3 py-1 rounded-lg transition-all"
              >
                Refresh
              </button>
            </div>
          </div>

          {transfers.length === 0 ? (
            <p className="text-orange-700 text-center py-12">
              No transfers yet
            </p>
          ) : (
            <div className="space-y-3">
              {renderItems.map((item) =>
                item.kind === "standalone"
                  ? renderTransferRow(item.t)
                  : renderDispatchGroup(item.g),
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
