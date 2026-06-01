import { useState } from "react";
import { useAgent } from "../context/AgentContext";

export const AgentInfo = () => {
  const { agentStatus, requestRename } = useAgent();

  const [editing, setEditing] = useState(false);
  const [newName, setNewName] = useState("");
  const [requestMessage, setRequestMessage] = useState("");
  const [loading, setLoading] = useState(false);

  const handleConfirm = async () => {
    if (!newName.trim()) return;
    try {
      setLoading(true);
      await requestRename(agentStatus!.agentId!, newName);
      setRequestMessage("Name change requested for: " + newName);
      setEditing(false);
    } catch (error) {
      console.error("Rename failed", error);
    } finally {
      setLoading(false);
    }
  };

  if (!agentStatus) {
    return (
      <div className="max-w-4xl mx-auto">
        <div className="bg-black/70 backdrop-blur-xl border border-orange-500/20 rounded-2xl p-8">
          <p className="text-orange-400">Loading...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto">
      <div className="bg-black/70 backdrop-blur-xl border border-orange-500/20 shadow-[0_0_40px_rgba(255,120,0,0.15)] rounded-2xl p-8">
        <h2 className="text-3xl font-bold text-orange-400 mb-6">
          Agent Information
        </h2>

        <div className="space-y-4 text-orange-200">
          <div>
            <p className="text-sm text-orange-400">Agent Name</p>
            {!editing ? (
              <div className="flex items-center gap-4">
                <p className="text-lg">{agentStatus.name}</p>
                <button
                  onClick={() => {
                    setNewName(agentStatus.name);
                    setEditing(true);
                  }}
                  className="text-sm text-orange-300 hover:text-orange-400 underline"
                >
                  Edit
                </button>
                {requestMessage && (
                  <p className="text-sm text-orange-300">{requestMessage}</p>
                )}
              </div>
            ) : (
              <div className="flex items-center gap-3">
                <input
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  className="px-3 py-1 bg-black border border-orange-500/30 rounded-md text-orange-200"
                />
                <button
                  onClick={handleConfirm}
                  disabled={loading}
                  className="px-3 py-1 bg-orange-700 hover:bg-orange-600 rounded-md text-white text-sm"
                >
                  {loading ? "Saving..." : "Confirm"}
                </button>
                <button
                  onClick={() => setEditing(false)}
                  className="text-sm text-gray-400 hover:text-gray-300"
                >
                  Cancel
                </button>
              </div>
            )}
          </div>

          <div>
            <p className="text-sm text-orange-400">Agent ID</p>
            <p className="text-sm font-mono text-orange-200/70">
              {agentStatus.agentId ?? "Unregistered"}
            </p>
          </div>

          <div>
            <p className="text-sm text-orange-400">Upload Directory</p>
            <p className="text-lg">{agentStatus.uploadDirectory}</p>
          </div>

          <div>
            <p className="text-sm text-orange-400">Nexus</p>
            <p className="text-lg">{agentStatus.nexusUrl}</p>
          </div>

          <div>
            <p className="text-sm text-orange-400">Status</p>
            <p className="text-lg text-green-400">{agentStatus.status}</p>
          </div>
        </div>
      </div>
    </div>
  );
};
