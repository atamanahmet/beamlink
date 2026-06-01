import { createContext, useContext } from "react";
import { useAuth } from "./AuthContext";

export type TransferType = "SINGLE" | "BATCH" | "DIRECTORY";

export interface TransferSummary {
  id: string;
  dispatchId: string | null;
  type: TransferType;
  name: string;
  status: string;
  totalSize: number;
  confirmedBytes: number;
  totalFiles: number;
  targetAgentId: string;
  targetIp: string;
  targetPort: number;
  createdAt: string;
  completedAt: string | null;
  activeTransferMs: number;
  failureReason: string | null;
}

export interface DispatchResultItem {
  id: string;
  type: TransferType;
}

interface TransferContextType {
  getAllTransfers: () => Promise<TransferSummary[]>;
  getTransferLogs: () => Promise<any>;
  getRecentTransferLogs: (limit?: number) => Promise<any>;
  getChildTransfers: (
    id: string,
    type: TransferType,
  ) => Promise<TransferSummary[]>;
  initiateDispatch: (req: {
    paths: string[];
    targetAgentId: string;
    targetIp: string;
    targetPort: number;
  }) => Promise<DispatchResultItem[]>;
  pauseTransfer: (id: string, type: TransferType) => Promise<void>;
  resumeTransfer: (id: string, type: TransferType) => Promise<void>;
  cancelTransfer: (id: string, type: TransferType) => Promise<void>;
  deleteTransfer: (id: string, type: TransferType) => Promise<void>;
}

const TransferContext = createContext<TransferContextType | undefined>(
  undefined,
);

export const TransferProvider = ({
  children,
}: {
  children: React.ReactNode;
}) => {
  const { apiClient } = useAuth();

  const typePath = (type: TransferType) => type.toLowerCase();

  return (
    <TransferContext.Provider
      value={{
        getAllTransfers: async () => (await apiClient.get("/transfers")).data,

        getTransferLogs: async () => (await apiClient.get("/logs")).data,

        getRecentTransferLogs: async (limit = 50) =>
          (await apiClient.get(`/logs/recent?limit=${limit}`)).data,

        getChildTransfers: async (id, type) => {
          if (type === "SINGLE") return [];
          return (
            await apiClient.get(`/transfers/${typePath(type)}/${id}/files`)
          ).data;
        },

        initiateDispatch: async (req) =>
          (await apiClient.post("/transfers/send", req)).data,

        pauseTransfer: async (id, type) => {
          await apiClient.post(`/transfers/${typePath(type)}/${id}/pause`);
        },

        resumeTransfer: async (id, type) => {
          await apiClient.post(`/transfers/${typePath(type)}/${id}/resume`);
        },

        cancelTransfer: async (id, type) => {
          await apiClient.delete(`/transfers/${typePath(type)}/${id}`);
        },

        deleteTransfer: async (id, type) => {
          await apiClient.delete(`/transfers/${typePath(type)}/${id}/delete`);
        },
      }}
    >
      {children}
    </TransferContext.Provider>
  );
};

export const useTransfer = () => {
  const context = useContext(TransferContext);
  if (!context)
    throw new Error("useTransfers must be used within TransferProvider");
  return context;
};
