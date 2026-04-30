import { createContext, useContext } from "react";
import { useAuth } from "./AuthContext";

interface TransferStatusResponse {
  transferId: string;
  status:
    | "PENDING"
    | "ACTIVE"
    | "PAUSED"
    | "COMPLETED"
    | "CANCELLED"
    | "FAILED"
    | "EXPIRED";
  confirmedOffset: number;
  fileSize: number;
  fileName: string;
  failureReason: string | null;
  targetAgentId: string;
  createdAt: string;
  lastChunkAt: string | null;
}

interface InitiateTransferRequest {
  filePath: string;
  targetAgentId: string;
  targetIp: string;
  targetPort: number;
  targetToken: string | null;
}

interface UploadResponse {
  success: boolean;
  message: string;
  error?: string;
}

interface StatusResponse {
  agentId: string;
  name: string;
  port: number;
  uploadDirectory: string;
  fileCount: number;
  status: string;
  nexusUrl: string;
}

interface DataContextType {
  checkStatus: () => Promise<StatusResponse>;
  requestRename: (agentId: string, newName: string) => Promise<any>;
  uploadFile: (
    file: File,
    onProgress?: (progress: number, loaded: number, total: number) => void,
    signal?: AbortSignal,
  ) => Promise<UploadResponse>;
  getTransferLogs: () => Promise<any>;
  getRecentTransferLogs: (limit?: number) => Promise<any>;
  initiateTransfer: (
    req: InitiateTransferRequest,
  ) => Promise<{ transferId: string }>;
  resumeTransfer: (transferId: string) => Promise<void>;
  cancelTransfer: (transferId: string) => Promise<void>;
  getTransferStatus: (transferId: string) => Promise<TransferStatusResponse>;
  getAllTransfers: () => Promise<TransferStatusResponse[]>;
}

const DataContext = createContext<DataContextType | undefined>(undefined);

export const DataProvider = ({ children }: { children: React.ReactNode }) => {
  const { apiClient } = useAuth();

  const initiateTransfer = async (req: InitiateTransferRequest) =>
    (await apiClient.post("/transfers", req)).data;

  const resumeTransfer = async (transferId: string): Promise<void> => {
    await apiClient.post(`/transfers/${transferId}/resume`);
  };

  const cancelTransfer = async (transferId: string): Promise<void> => {
    await apiClient.delete(`/transfers/${transferId}`);
  };

  const getTransferStatus = async (transferId: string) =>
    (await apiClient.get(`/transfers/${transferId}/status`)).data;

  const getAllTransfers = async () => (await apiClient.get("/transfers")).data;

  const checkStatus = async (): Promise<StatusResponse> =>
    (await apiClient.get("/status")).data;

  const requestRename = async (agentId: string, newName: string) =>
    (await apiClient.post(`/agents/${agentId}/rename`, { name: newName })).data;

  const uploadFile = async (
    file: File,
    onProgress?: (progress: number, loaded: number, total: number) => void,
    signal?: AbortSignal,
  ): Promise<UploadResponse> => {
    const formData = new FormData();
    formData.append("file", file);

    return (
      await apiClient.post("/upload", formData, {
        signal,
        onUploadProgress: (progressEvent) => {
          if (progressEvent.total) {
            const percent = Math.round(
              (progressEvent.loaded * 100) / progressEvent.total,
            );
            onProgress?.(percent, progressEvent.loaded, progressEvent.total);
          }
        },
      })
    ).data;
  };

  const getTransferLogs = async () => (await apiClient.get("/logs")).data;

  const getRecentTransferLogs = async (limit = 50) =>
    (await apiClient.get(`/logs/recent?limit=${limit}`)).data;

  return (
    <DataContext.Provider
      value={{
        checkStatus,
        requestRename,
        uploadFile,
        getTransferLogs,
        getRecentTransferLogs,
        initiateTransfer,
        resumeTransfer,
        cancelTransfer,
        getTransferStatus,
        getAllTransfers,
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
