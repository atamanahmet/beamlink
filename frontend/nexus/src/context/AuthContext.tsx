import { createContext, useContext, useState, useEffect, useRef } from "react";
import axios, { type AxiosInstance } from "axios";

interface AuthContextType {
  isAuthenticated: boolean;
  isBackendReady: boolean;
  backendError: string | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  apiClient: AxiosInstance;
  healthClient: AxiosInstance;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isBackendReady, setIsBackendReady] = useState(false);
  const [backendError, setBackendError] = useState<string | null>(null);

  /**
   * Dev: relative URL, Vite proxy forwards to backend
   * Prod: overwritten by backendUrl from runtime.json via backend-status IPC
   */
  const apiClientRef = useRef<AxiosInstance>(
    axios.create({
      baseURL: import.meta.env.DEV
        ? "/api/nexus"
        : "http://localhost:7472/api/nexus",
      timeout: 10000,
      withCredentials: true,
    }),
  );

  const healthClientRef = useRef<AxiosInstance>(
    axios.create({
      baseURL: import.meta.env.DEV ? "" : "http://localhost:7472",
      timeout: 5000,
    }),
  );

  const tokenRef = useRef<string | null>(null);

  useEffect(() => {
    window.electronAPI?.onBackendStatus((data) => {
      if (data.status === "ready" && data.backendUrl) {
        if (!import.meta.env.DEV) {
          apiClientRef.current.defaults.baseURL = `${data.backendUrl}/api/nexus`;
          healthClientRef.current.defaults.baseURL = data.backendUrl;
        }
        setIsBackendReady(true);
      } else if (data.status === "error") {
        setBackendError(data.message ?? "Backend failed to start.");
      }
    });
  }, []);

  const login = async (username: string, password: string) => {
    username = username.trim();
    const response = await apiClientRef.current.post("/auth/login", {
      username,
      password,
    });
    if (response.status === 200) {
      const token = response.data.token;
      tokenRef.current = token;

      apiClientRef.current.defaults.headers.common["X-Auth-Token"] = token;

      setIsAuthenticated(true);
    } else {
      alert("Invalid Credential");
    }
  };

  const logout = async () => {
    await apiClientRef.current.post("/auth/logout");
    tokenRef.current = null;
    delete apiClientRef.current.defaults.headers.common["X-Auth-Token"];
    setIsAuthenticated(false);
  };

  return (
    <AuthContext.Provider
      value={{
        isAuthenticated,
        isBackendReady,
        backendError,
        login,
        logout,
        apiClient: apiClientRef.current,
        healthClient: healthClientRef.current,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
};
