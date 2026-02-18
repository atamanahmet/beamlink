import { useState, useEffect } from "react";
import { Check, X, Clock } from "lucide-react";
import { api } from "../services/api";

interface PendingAgent {
  agentId: string;
  name: string;
  ipAddress: string;
  port: number;
  requestedAt: string;
}

interface PendingRename {
  agentId: string;
  currentName: string;
  requestedName: string;
  requestedAt: string;
}

interface PendingApprovalsProps {
  onUpdate: () => void;
}

export const PendingApprovals = ({ onUpdate }: PendingApprovalsProps) => {
  const [pendingAgents, setPendingAgents] = useState<PendingAgent[]>([]);
  const [pendingRenames, setPendingRenames] = useState<PendingRename[]>([]);

  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadPending();
  }, []);

  const loadPending = async () => {
    try {
      const [agents, renames] = await Promise.all([
        api.getPendingAgents(),
        api.getPendingRenames(),
      ]);

      setPendingAgents(agents);
      setPendingRenames(renames);
      setLoading(false);
    } catch (error) {
      console.error("Failed to load pending approvals:", error);
      setLoading(false);
    }
  };

  const handleApproveRename = async (agentId: string) => {
    try {
      await api.approveRename(agentId);
      setPendingRenames(pendingRenames.filter((r) => r.agentId !== agentId));
      onUpdate();
    } catch (error) {
      console.error("Failed to approve rename:", error);
    }
  };

  const handleRejectRename = async (agentId: string) => {
    try {
      await api.rejectRename(agentId);
      setPendingRenames(pendingRenames.filter((r) => r.agentId !== agentId));
      onUpdate();
    } catch (error) {
      console.error("Failed to reject rename:", error);
    }
  };

  const handleApprove = async (agentId: string) => {
    try {
      await api.approveAgent(agentId);
      setPendingAgents(pendingAgents.filter((a) => a.agentId !== agentId));
      onUpdate();
    } catch (error) {
      console.error("Failed to approve agent:", error);
    }
  };

  const handleReject = async (agentId: string) => {
    try {
      await api.rejectAgent(agentId);
      setPendingAgents(pendingAgents.filter((a) => a.agentId !== agentId));
      onUpdate();
    } catch (error) {
      console.error("Failed to reject agent:", error);
    }
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

  if (pendingAgents.length === 0 && pendingRenames.length === 0) {
    return (
      <div className="text-center py-12">
        <Clock className="w-16 h-16 text-orange-400/40 mx-auto mb-4" />
        <p className="text-orange-300/60">No pending approvals</p>
      </div>
    );
  }

  return (
    <div>
      <h2 className="text-xl font-bold text-orange-400 mb-4">
        Pending Approvals ({pendingAgents.length + pendingRenames.length})
      </h2>

      <div className="space-y-4">
        {pendingAgents.map((agent) => (
          <div
            key={agent.agentId}
            className="bg-black/40 border border-yellow-600/40 rounded-xl p-4
                       flex items-center justify-between"
          >
            <div>
              <h3 className="text-lg font-bold text-orange-300 mb-1">
                {agent.name}
              </h3>
              <div className="space-y-1 text-sm text-orange-300/60">
                <p>
                  IP: {agent.ipAddress}:{agent.port}
                </p>
                <p>Requested: {formatDate(agent.requestedAt)}</p>
              </div>
            </div>

            <div className="flex gap-2">
              <button
                onClick={() => handleApprove(agent.agentId)}
                className="flex items-center gap-2 px-4 py-2 bg-green-600 hover:bg-green-500
                           text-white rounded-lg transition-all shadow-lg shadow-green-900/40"
              >
                <Check className="w-4 h-4" />
                Approve
              </button>

              <button
                onClick={() => handleReject(agent.agentId)}
                className="flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-500
                           text-white rounded-lg transition-all shadow-lg shadow-red-900/40"
              >
                <X className="w-4 h-4" />
                Reject
              </button>
            </div>
          </div>
        ))}
        {/* Pending Rename Requests */}
        {pendingRenames.length > 0 && (
          <>
            <h2 className="text-xl font-bold text-orange-400 mt-8 mb-4">
              Pending Rename Requests ({pendingRenames.length})
            </h2>

            <div className="space-y-4">
              {pendingRenames.map((rename) => (
                <div
                  key={rename.agentId}
                  className="bg-black/40 border border-blue-600/40 rounded-xl p-4
                     flex items-center justify-between"
                >
                  <div>
                    <h3 className="text-lg font-bold text-orange-300 mb-1">
                      {rename.currentName} → {rename.requestedName}
                    </h3>
                    <div className="space-y-1 text-sm text-orange-300/60">
                      <p>Agent ID: {rename.agentId}</p>
                      <p>Requested: {formatDate(rename.requestedAt)}</p>
                    </div>
                  </div>

                  <div className="flex gap-2">
                    <button
                      onClick={() => handleApproveRename(rename.agentId)}
                      className="flex items-center gap-2 px-4 py-2 bg-green-600 hover:bg-green-500
                         text-white rounded-lg transition-all shadow-lg shadow-green-900/40"
                    >
                      <Check className="w-4 h-4" />
                      Approve
                    </button>

                    <button
                      onClick={() => handleRejectRename(rename.agentId)}
                      className="flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-500
                         text-white rounded-lg transition-all shadow-lg shadow-red-900/40"
                    >
                      <X className="w-4 h-4" />
                      Reject
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
};
