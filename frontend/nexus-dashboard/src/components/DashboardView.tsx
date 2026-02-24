import { useState, useEffect } from "react";
import { Activity, Users, Clock } from "lucide-react";
import { AgentList } from "./AgentList";
import { PendingApprovals } from "./PendingApprovals";
import { Statistics } from "./Statistics";
import { TransferLogs } from "./TransferLogs";
import { useData } from "../context/DataContext";

export const DashboardView = () => {
  const { getStats } = useData();

  const [activeTab, setActiveTab] = useState<"agents" | "pending" | "logs">(
    "agents",
  );
  const [stats, setStats] = useState({
    agentStats: {
      total: 0,
      online: 0,
      offline: 0,
      pending: 0,
      pendingRename: 0,
    },
    transferStats: {
      totalTransfers: 0,
      totalDataTransferred: 0,
    },
  });

  useEffect(() => {
    const loadStats = async () => {
      try {
        const data = await getStats();
        setStats(data);
      } catch (err) {
        console.error("Failed to load stats:", err);
      }
    };

    loadStats();
    const interval = setInterval(loadStats, 5000); // refresh every 5s
    return () => clearInterval(interval);
  }, [getStats]);

  return (
    <div className="min-h-screen p-6">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="flex justify-between items-center mb-8">
          <div>
            <h1 className="text-4xl font-bold text-orange-400 tracking-wide mb-2">
              BeamLink Nexus
            </h1>
            <p className="text-orange-300/60">Central Command & Control</p>
          </div>
        </div>

        {/* Statistics */}
        <Statistics stats={stats} />

        {/* Tabs */}
        <div
          className="bg-black/70 backdrop-blur-xl border border-orange-500/20
                        shadow-[0_0_40px_rgba(255,120,0,0.15)]
                        rounded-2xl overflow-hidden"
        >
          {/* Tab Headers */}
          <div className="flex border-b border-orange-800/30">
            <button
              onClick={() => setActiveTab("agents")}
              className={`flex-1 px-6 py-4 flex items-center justify-center gap-2 transition-all
                ${
                  activeTab === "agents"
                    ? "bg-orange-500/10 text-orange-400 border-b-2 border-orange-400"
                    : "text-orange-300/60 hover:text-orange-300 hover:bg-orange-500/5"
                }`}
            >
              <Users className="w-5 h-5" />
              Agents
            </button>

            <button
              onClick={() => setActiveTab("pending")}
              className={`flex-1 px-6 py-4 flex items-center justify-center gap-2 transition-all relative
                ${
                  activeTab === "pending"
                    ? "bg-orange-500/10 text-orange-400 border-b-2 border-orange-400"
                    : "text-orange-300/60 hover:text-orange-300 hover:bg-orange-500/5"
                }`}
            >
              <Clock className="w-5 h-5" />
              Pending Approvals
              {stats.agentStats.pending + stats.agentStats.pendingRename >
                0 && (
                <span
                  className="absolute top-2 right-2 bg-orange-500 text-black text-xs
                                 px-2 py-0.5 rounded-full font-bold"
                >
                  {stats.agentStats.pending + stats.agentStats.pendingRename}
                </span>
              )}
            </button>

            <button
              onClick={() => setActiveTab("logs")}
              className={`flex-1 px-6 py-4 flex items-center justify-center gap-2 transition-all
                ${
                  activeTab === "logs"
                    ? "bg-orange-500/10 text-orange-400 border-b-2 border-orange-400"
                    : "text-orange-300/60 hover:text-orange-300 hover:bg-orange-500/5"
                }`}
            >
              <Activity className="w-5 h-5" />
              Transfer Logs
            </button>
          </div>

          {/* Tab Content */}
          <div className="p-6">
            {activeTab === "agents" && <AgentList />}
            {activeTab === "pending" && <PendingApprovals />}
            {activeTab === "logs" && <TransferLogs />}
          </div>
        </div>
      </div>
    </div>
  );
};
