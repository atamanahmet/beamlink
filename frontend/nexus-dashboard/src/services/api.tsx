import axios from "axios";

const API_URL = "http://localhost:5000/api";

const AUTH_USERNAME = "admin";
const AUTH_PASSWORD = "password123";

const apiClient = axios.create({
  baseURL: API_URL,
  timeout: 10000,
  auth: {
    username: AUTH_USERNAME,
    password: AUTH_PASSWORD,
  },
});

interface UploadResponse {
  success: boolean;
  message: string;
  error?: string;
}

interface StatusResponse {
  status: string;
  ftp_server?: string;
  protocols?: string[];
}

export const api = {
  // Dashboard
  getStats: async () => {
    const response = await apiClient.get("/dashboard/stats");
    return response.data;
  },

  // Agents
  getAgents: async () => {
    const response = await apiClient.get("/agents");
    return response.data;
  },

  removeAgent: async (agentId: string) => {
    const response = await apiClient.post(`/agents/${agentId}/remove`);
    return response.data;
  },

  getPendingAgents: async () => {
    const response = await apiClient.get("/agents/pending");
    return response.data;
  },

  approveAgent: async (agentId: string) => {
    const response = await apiClient.post(`/agents/${agentId}/approve`);
    return response.data;
  },

  rejectAgent: async (agentId: string) => {
    const response = await apiClient.post(`/agents/${agentId}/reject`);
    return response.data;
  },

  getPendingRenames: async () => {
    const response = await apiClient.get("/agents/pending-renames");
    console.log(response.data);
    return response.data;
  },

  approveRename: async (agentId: string) => {
    const response = await apiClient.post(`/agents/${agentId}/approve-rename`);
    return response.data;
  },

  rejectRename: async (agentId: string) => {
    const response = await apiClient.post(`/agents/${agentId}/reject-rename`);
    return response.data;
  },

  // Logs
  getTransferLogs: async () => {
    const response = await apiClient.get("/logs");
    return response.data;
  },

  getRecentTransferLogs: async (limit = 50) => {
    const response = await apiClient.get(`/logs/recent?limit=${limit}`);
    return response.data;
  },

  // Status
  checkStatus: async (): Promise<StatusResponse> => {
    const response = await apiClient.get("/status");
    return response.data;
  },

  // Upload
  uploadFile: async (
    file: File,
    onProgress?: (progress: number, loaded: number, total: number) => void,
    signal?: AbortSignal,
  ): Promise<UploadResponse> => {
    const formData = new FormData();
    formData.append("file", file);

    const response = await apiClient.post("/upload", formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
      signal,
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total) {
          const percentCompleted = Math.round(
            (progressEvent.loaded * 100) / progressEvent.total,
          );

          onProgress?.(
            percentCompleted,
            progressEvent.loaded,
            progressEvent.total,
          );
        }
      },
    });

    return response.data;
  },
};
