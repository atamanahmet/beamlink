import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, LogOut } from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { useData } from "../context/DataContext";

export const AgentInfo = () => {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const { checkStatus, requestRename } = useData();

  const [agentInfo, setAgentInfo] = useState({
    agentId: "",
    agentName: "",
    nexusUrl: "",
    status: "",
    uploadDirectory: "",
  });

  const [name, setName] = useState("");
  const [editing, setEditing] = useState(false);
  const [newName, setNewName] = useState("");
  const [requestMessage, setRequestMessage] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const fetchStatus = async () => {
      try {
        const status = await checkStatus();
        setAgentInfo({
          agentId: status.agentId,
          agentName: status.name,
          nexusUrl: status.nexusUrl,
          status: status.status,
          uploadDirectory: status.uploadDirectory,
        });
        setName(status.name);
      } catch (error) {
        console.error("Failed to fetch agent status", error);
      }
    };

    fetchStatus();
  }, []);

  const handleConfirm = async () => {
    if (!newName.trim()) return;
    try {
      setLoading(true);
      await requestRename(agentInfo.agentId, newName);
      setRequestMessage("Name change requested for: " + newName);
      setEditing(false);
    } catch (error) {
      console.error("Rename failed", error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-linear-to-br from-[#1a0f0a] via-[#3b1f12] to-black p-6">
      <div className="max-w-4xl mx-auto">
        <div className="flex justify-between items-center mb-8">
          <button
            onClick={() => navigate("/")}
            className="flex items-center gap-2 text-orange-300 hover:text-orange-400"
          >
            <ArrowLeft className="w-4 h-4" />
            Back
          </button>

          <button
            onClick={logout}
            className="flex items-center gap-2 px-4 py-2 bg-orange-900/30 hover:bg-orange-900/50 border border-orange-700 rounded-lg text-orange-300 transition-all"
          >
            <LogOut className="w-4 h-4" />
            Logout
          </button>
        </div>

        <div className="bg-black/70 backdrop-blur-xl border border-orange-500/20 shadow-[0_0_40px_rgba(255,120,0,0.15)] rounded-2xl p-8">
          <h2 className="text-3xl font-bold text-orange-400 mb-6">
            Agent Information
          </h2>

          <div className="space-y-4 text-orange-200">
            <div>
              <p className="text-sm text-orange-400">Agent Name</p>
              {!editing ? (
                <div className="flex items-center gap-4">
                  <p className="text-lg">{name}</p>
                  <button
                    onClick={() => {
                      setNewName(name);
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
                {agentInfo.agentId}
              </p>
            </div>

            <div>
              <p className="text-sm text-orange-400">Upload Directory</p>
              <p className="text-lg">{agentInfo.uploadDirectory}</p>
            </div>

            <div>
              <p className="text-sm text-orange-400">Nexus</p>
              <p className="text-lg">{agentInfo.nexusUrl}</p>
            </div>

            <div>
              <p className="text-sm text-orange-400">Status</p>
              <p className="text-lg text-green-400">{agentInfo.status}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
