import { createContext, useContext, useState, useEffect } from "react";
import { useAuth } from "./AuthContext";

export interface AgentStatus {
  agentId: string | null;
  name: string;
  nexusUrl: string;
  status: string;
  uploadDirectory: string;
  port: number;
  fileCount: number;
}

export interface Peer {
  id: string;
  agentName: string;
  ipAddress: string;
  port: number;
  online: boolean;
}

interface AgentContextType {
  agentStatus: AgentStatus | null;
  getPeers: () => Promise<Peer[]>;
  requestRename: (agentId: string, newName: string) => Promise<void>;
}

const AgentContext = createContext<AgentContextType | undefined>(undefined);

export const AgentProvider = ({ children }: { children: React.ReactNode }) => {
  const { apiClient, isBackendReady, onSseEvent, isAuthenticated } = useAuth();
  const [agentStatus, setAgentStatus] = useState<AgentStatus | null>(null);

  /** Initial load */
  useEffect(() => {
    if (!isBackendReady || !isAuthenticated) return;
    apiClient
      .get("/status")
      .then((r) => setAgentStatus(r.data))
      .catch(console.error);
  }, [isBackendReady, isAuthenticated]);

  /** Live updates from backend when agent state changes */
  useEffect(() => {
    if (!isAuthenticated) return;
    const unsubscribe = onSseEvent("agent_status_updated", (data) => {
      setAgentStatus(data as AgentStatus);
    });
    return () => unsubscribe();
  }, [isAuthenticated]);

  const requestRename = async (agentId: string, newName: string) => {
    await apiClient.post(`/${agentId}/rename`, { name: newName });
  };

  return (
    <AgentContext.Provider
      value={{
        agentStatus,
        requestRename,
        getPeers: async () => {
          const res = await apiClient.get("/peers");
          return res.data.peers ?? res.data;
        },
      }}
    >
      {children}
    </AgentContext.Provider>
  );
};

export const useAgent = () => {
  const context = useContext(AgentContext);
  if (!context) throw new Error("useAgent must be used within AgentProvider");
  return context;
};
