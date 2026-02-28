import { createContext, useContext } from "react";
import { useAuth } from "./AuthContext";

interface DataContextType {
  getStats: () => Promise<any>;
  getAgents: () => Promise<any>;
  getApprovedAgents: () => Promise<any>;
  getPendingAgents: () => Promise<any>;
  approveAgent: (id: string) => Promise<any>;
  rejectAgent: (id: string) => Promise<any>;
  removeAgent: (id: string) => Promise<any>;
  getPendingRenames: () => Promise<any>;
  approveRename: (id: string) => Promise<any>;
  rejectRename: (id: string) => Promise<any>;
  getTransferLogs: () => Promise<any>;
  getRecentTransferLogs: (limit?: number) => Promise<any>;
}

const DataContext = createContext<DataContextType | undefined>(undefined);

export const DataProvider = ({ children }: { children: React.ReactNode }) => {
  const { apiClient } = useAuth();

  const getStatsFn = async () => (await apiClient.get("/dashboard/stats")).data;
  const getAgentsFn = async () => (await apiClient.get("/agents")).data;
  const getApprovedAgentsFn = async () =>
    (await apiClient.get("/agents/approved")).data;
  const getPendingAgentsFn = async () =>
    (await apiClient.get("/agents/pending")).data;
  const approveAgentFn = async (id: string) =>
    (await apiClient.post(`/agents/${id}/approve`)).data;
  const rejectAgentFn = async (id: string) =>
    (await apiClient.post(`/agents/${id}/reject`)).data;
  const removeAgentFn = async (id: string) =>
    (await apiClient.delete(`/agents/${id}`)).data;
  const getPendingRenamesFn = async () =>
    (await apiClient.get("/agents/rename-pending")).data;
  const approveRenameFn = async (id: string) =>
    (await apiClient.post(`/agents/${id}/rename/approve`)).data;
  const rejectRenameFn = async (id: string) =>
    (await apiClient.post(`/agents/${id}/rename/reject`)).data;
  const getTransferLogsFn = async () => (await apiClient.get("/logs")).data;
  const getRecentTransferLogsFn = async (limit = 50) =>
    (await apiClient.get(`/logs/recent?limit=${limit}`)).data;

  return (
    <DataContext.Provider
      value={{
        getStats: getStatsFn,
        getApprovedAgents: getApprovedAgentsFn,
        getAgents: getAgentsFn,
        getPendingAgents: getPendingAgentsFn,
        approveAgent: approveAgentFn,
        rejectAgent: rejectAgentFn,
        removeAgent: removeAgentFn,
        getPendingRenames: getPendingRenamesFn,
        approveRename: approveRenameFn,
        rejectRename: rejectRenameFn,
        getTransferLogs: getTransferLogsFn,
        getRecentTransferLogs: getRecentTransferLogsFn,
      }}
    >
      {children}
    </DataContext.Provider>
  );
};

export const useData = () => {
  const context = useContext(DataContext);
  if (!context) throw new Error("useData must be used within DataProvider");
  return context;
};
