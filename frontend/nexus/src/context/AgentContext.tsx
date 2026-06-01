import { createContext, useContext } from "react";
import { useAuth } from "./AuthContext";

export interface Agent {
  id: string;
  name: string;
  ipAddress: string;
  port: number;
  online: boolean;
  state: string;
  requestedName?: string;
  registeredAt: string;
  renameRequestedAt?: string;
  lastSeen?: string;
  fileCount?: number;
}

export interface Peer {
  id: string;
  agentName: string;
  ipAddress: string;
  port: number;
  online: boolean;
}

interface AgentContextType {
  getStats: () => Promise<any>;
  getAgents: () => Promise<Agent[]>;
  getApprovedAgents: () => Promise<Agent[]>;
  getPendingAgents: () => Promise<Agent[]>;
  getPendingRenames: () => Promise<Agent[]>;
  getPeers: () => Promise<Peer[]>;
  approveAgent: (id: string) => Promise<void>;
  rejectAgent: (id: string) => Promise<void>;
  removeAgent: (id: string) => Promise<void>;
  approveRename: (id: string) => Promise<void>;
  rejectRename: (id: string) => Promise<void>;
}

const AgentContext = createContext<AgentContextType | undefined>(undefined);

export const AgentProvider = ({ children }: { children: React.ReactNode }) => {
  const { apiClient } = useAuth();

  return (
    <AgentContext.Provider
      value={{
        getStats: async () =>
          (await apiClient.get("/admin/dashboard/stats")).data,

        getAgents: async () => (await apiClient.get("/admin/agents")).data,

        getApprovedAgents: async () =>
          (await apiClient.get("/admin/agents/approved")).data,

        getPendingAgents: async () =>
          (await apiClient.get("/admin/agents/pending")).data,

        getPendingRenames: async () =>
          (await apiClient.get("/admin/agents/rename-pending")).data,

        getPeers: async () => {
          const res = await apiClient.get("/peers");
          return res.data.peers ?? res.data;
        },

        approveAgent: async (id) => {
          await apiClient.post(`/admin/agents/${id}/approve`);
        },

        rejectAgent: async (id) => {
          await apiClient.post(`/admin/agents/${id}/reject`);
        },

        removeAgent: async (id) => {
          await apiClient.delete(`/admin/agents/${id}`);
        },

        approveRename: async (id) => {
          await apiClient.post(`/admin/agents/${id}/rename/approve`);
        },

        rejectRename: async (id) => {
          await apiClient.post(`/admin/agents/${id}/rename/reject`);
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
