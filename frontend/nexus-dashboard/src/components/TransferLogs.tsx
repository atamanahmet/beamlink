import { useState, useEffect, useCallback } from "react";
import { Activity, ArrowRight, RefreshCw } from "lucide-react";
import { transferEvents } from "../services/event/event";
import { useData } from "../context/DataContext";

interface TransferLog {
  id: string;
  fromAgentId: string;
  fromAgentName: string;
  toAgentId: string;
  toAgentName: string;
  filename: string;
  fileSize: number;
  timestamp: string;
}

export const TransferLogs = () => {
  const { getTransferLogs } = useData();
  const [logs, setLogs] = useState<TransferLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadLogs = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const data = await getTransferLogs();

      console.log(data);

      // Sort newest first
      const sorted = [...data].sort(
        (a, b) =>
          new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime(),
      );

      setLogs(sorted);
    } catch (err) {
      console.error("Failed to load logs:", err);
      setError("Failed to load transfer logs");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadLogs();
  }, [loadLogs]);

  useEffect(() => {
    loadLogs();

    const unsubscribe = transferEvents.subscribe(() => {
      loadLogs();
    });

    return unsubscribe;
  }, [loadLogs]);

  const formatBytes = (bytes: number) => {
    if (!bytes) return "0 B";
    const k = 1024;
    const sizes = ["B", "KB", "MB", "GB", "TB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return (bytes / Math.pow(k, i)).toFixed(2) + " " + sizes[i];
  };

  const formatDate = (dateString: string) => {
    if (!dateString) return "—";
    const date = new Date(dateString);
    return date.toLocaleString();
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center py-12">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-orange-400"></div>
      </div>
    );
  }

  if (error) {
    return <div className="text-center py-12 text-red-400">{error}</div>;
  }

  if (logs.length === 0) {
    return (
      <div className="text-center py-12">
        <Activity className="w-16 h-16 text-orange-400/40 mx-auto mb-4" />
        <p className="text-orange-300/60">No transfer logs yet</p>
      </div>
    );
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-xl font-bold text-orange-400">
          Transfer History ({logs.length})
        </h2>

        <button
          onClick={loadLogs}
          className="flex items-center gap-2 text-sm text-orange-300 hover:text-orange-400 transition"
        >
          <RefreshCw className="w-4 h-4" />
          Refresh
        </button>
      </div>

      <div className="space-y-3">
        {logs.map((log) => (
          <div
            key={log.id}
            className="bg-black/40 border border-orange-800/30 rounded-xl p-4
                       hover:border-orange-500/40 transition-all"
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-4 flex-1">
                <span className="text-orange-300 font-medium">
                  {log.fromAgentName || "Unknown"}
                </span>

                <ArrowRight className="w-4 h-4 text-orange-500" />

                <span className="text-orange-300 font-medium">
                  {log.toAgentName || "Unknown"}
                </span>
              </div>

              <div className="text-right">
                <p className="text-orange-300 text-sm">{log.filename}</p>
                <p className="text-orange-300/60 text-xs">
                  {formatBytes(log.fileSize)} • {formatDate(log.timestamp)}
                </p>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
