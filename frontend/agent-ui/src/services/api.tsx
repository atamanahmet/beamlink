import axios from "axios";

const API_URL = "http://localhost:8081/api";

// Add credentials
const AUTH_USERNAME = "admin";
const AUTH_PASSWORD = "password123";

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

export const api = {
  checkStatus: async (): Promise<StatusResponse> => {
    const response = await axios.get(`${API_URL}/status`, {
      auth: {
        username: AUTH_USERNAME,
        password: AUTH_PASSWORD,
      },
    });
    return response.data;
  },

  requestRename: async (nexusUrl: string, agentId: string, newName: string) => {
    const response = await axios.post(
      `${nexusUrl}/api/agents/${agentId}/rename`,
      { name: newName },
      {
        auth: {
          username: AUTH_USERNAME,
          password: AUTH_PASSWORD,
        },
      },
    );

    return response.data;
  },

  uploadFile: async (
    file: File,
    onProgress?: (progress: number, loaded: number, total: number) => void,
    signal?: AbortSignal,
  ): Promise<UploadResponse> => {
    const formData = new FormData();
    formData.append("file", file);

    const response = await axios.post(`${API_URL}/upload`, formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
      auth: {
        username: AUTH_USERNAME,
        password: AUTH_PASSWORD,
      },
      signal: signal,
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
