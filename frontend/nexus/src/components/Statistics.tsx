import { Users, Wifi, WifiOff, Clock, Activity, HardDrive } from "lucide-react";

interface StatisticsProps {
  stats: {
    agentStats: {
      total: number;
      online: number;
      offline: number;
      pending: number;
      pendingRename: number;
    };
    transferStats: {
      totalTransfers: number;
      totalDataTransferred: number;
    };
  };
}

export const Statistics = ({ stats }: StatisticsProps) => {
  const formatBytes = (bytes: number) => {
    if (bytes === 0) return "0 B";
    const k = 1024;
    const sizes = ["B", "KB", "MB", "GB", "TB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + " " + sizes[i];
  };

  const statCards = [
    {
      icon: Users,
      label: "Total Agents",
      value: stats.agentStats.total,
      color: "orange",
    },
    {
      icon: Wifi,
      label: "Online",
      value: stats.agentStats.online,
      color: "green",
    },
    {
      icon: WifiOff,
      label: "Offline",
      value: stats.agentStats.offline,
      color: "red",
    },
    {
      icon: Clock,
      label: "Pending",
      value: stats.agentStats.pending,
      color: "red",
    },
    {
      icon: Activity,
      label: "Transfers",
      value: stats.transferStats.totalTransfers,
      color: "green",
    },
    {
      icon: HardDrive,
      label: "Data Transferred",
      value: formatBytes(stats.transferStats.totalDataTransferred),
      color: "orange",
    },
  ];

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4 mb-8">
      {statCards.map((stat, index) => {
        const Icon = stat.icon;
        return (
          <div
            key={index}
            className="bg-black/70 backdrop-blur-xl border border-orange-500/20
                       shadow-[0_0_20px_rgba(255,120,0,0.1)]
                       rounded-xl p-4 hover:shadow-[0_0_30px_rgba(255,120,0,0.2)]
                       transition-all"
          >
            <div className="flex items-center justify-between mb-2">
              <div className={`p-2 rounded-lg bg-${stat.color}-500/10`}>
                <Icon className={`w-5 h-5 text-${stat.color}-400`} />
              </div>
            </div>
            <p className="text-orange-300/60 text-xs mb-1">{stat.label}</p>
            <p className="text-2xl font-bold text-orange-400">
              {stat.value || 0}
            </p>
          </div>
        );
      })}
    </div>
  );
};
