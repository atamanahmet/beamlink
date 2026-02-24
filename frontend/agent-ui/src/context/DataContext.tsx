import { createContext, useContext } from "react";
import { useAuth } from "./AuthContext";

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
}

const DataContext = createContext<DataContextType | undefined>(undefined);

export const DataProvider = ({ children }: { children: React.ReactNode }) => {
  const { apiClient } = useAuth();

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
