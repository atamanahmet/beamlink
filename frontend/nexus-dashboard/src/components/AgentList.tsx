import { useState, useEffect } from "react";
import { Wifi, WifiOff, Trash2, RefreshCw, Users } from "lucide-react";
import { useData } from "../context/DataContext";

interface Agent {
  id: string;
  name: string;
  ipAddress: string;
  port: number;
  online: boolean;
  lastSeen: string;
  fileCount: number;
}

export const AgentList = () => {
  const { getApprovedAgents, removeAgent } = useData();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadAgents();
    const interval = setInterval(loadAgents, 3000); // Refresh every 3s
    return () => clearInterval(interval);
  }, []);

  const loadAgents = async () => {
    try {
      const data = await getApprovedAgents();
      setAgents(data);
      setLoading(false);
    } catch (error) {
      console.error("Failed to load agents:", error);
      setLoading(false);
    }
  };

  const handleRefresh = () => {
    setLoading(true);
    loadAgents();
  };

  const formatDate = (dateString: string) => {
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

  if (agents.length === 0) {
    return (
      <div className="text-center py-12">
        <Users className="w-16 h-16 text-orange-400/40 mx-auto mb-4" />
        <p className="text-orange-300/60">No agents registered yet</p>
      </div>
    );
  }

  return (
    <div>
      {/* Header */}
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-xl font-bold text-orange-400">
          Registered Agents ({agents.length})
        </h2>
        <button
          onClick={handleRefresh}
          className="flex items-center gap-2 px-3 py-2 bg-orange-900/30 hover:bg-orange-900/50
                     border border-orange-700 rounded-lg text-orange-300 text-sm
                     transition-all"
        >
          <RefreshCw className="w-4 h-4" />
          Refresh
        </button>
      </div>

      {/* Agent Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {agents.map((agent) => (
          <div
            key={agent.id}
            className="bg-black/40 border border-orange-800/30 rounded-xl p-4
                       hover:border-orange-500/40 transition-all"
          >
            {/* Status Badge */}
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                {agent.online ? (
                  <Wifi className="w-5 h-5 text-green-400" />
                ) : (
                  <WifiOff className="w-5 h-5 text-red-400" />
                )}
                <span
                  className={`text-sm font-medium ${
                    agent.online ? "text-green-400" : "text-red-400"
                  }`}
                >
                  {agent.online ? "Online" : "Offline"}
                </span>
              </div>

              <span className="text-xs text-orange-300/60">
                {agent.fileCount} files
              </span>
            </div>

            {/* Agent Info */}
            <h3 className="text-lg font-bold text-orange-300 mb-2 truncate">
              {agent.name}
            </h3>

            <div className="space-y-1 text-sm text-orange-300/60">
              <p>
                IP: {agent.ipAddress}:{agent.port}
              </p>
              <p>Last seen: {formatDate(agent.lastSeen)}</p>
            </div>

            {/* Actions */}
            <div className="mt-4 pt-4 border-t border-orange-800/30">
              <button
                className="flex items-center gap-2 text-red-400 hover:text-red-300
                           text-sm transition-colors cursor-pointer"
                onClick={() => removeAgent(agent.id)}
              >
                <Trash2 className="w-4 h-4" />
                Remove Agent
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
