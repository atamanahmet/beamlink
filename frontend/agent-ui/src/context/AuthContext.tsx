import { createContext, useContext, useState, useEffect } from "react";
import axios, { type AxiosInstance } from "axios";

interface AgentIdentity {
  agentId: string;
  agentName: string;
  state: string;
  publicToken: string;
  authToken: string;
}

interface AuthContextType {
  isAuthenticated: boolean;
  isLoading: boolean;
  identity: AgentIdentity | null;
  publicToken: string | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshIdentity: () => Promise<string | null>;
  apiClient: AxiosInstance;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const apiClient = axios.create({
  baseURL: "/api",
  timeout: 10000,
  withCredentials: true,
});

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [identity, setIdentity] = useState<AgentIdentity | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  const applyIdentity = (data: AgentIdentity | null) => {
    if (data && !data.publicToken) {
      data = { ...data, publicToken: null as any };
    }
    setIdentity(data);
    setIsAuthenticated(!!data);
  };

  const refreshIdentity = async () => {
    try {
      const response = await apiClient.get("/auth/me");
      applyIdentity(response.data);
      return response.data.publicToken ?? null;
    } catch {
      applyIdentity(null);
      return null;
    } finally {
      setIsLoading(false);
    }
  };

  // SSE
  useEffect(() => {
    const es = new EventSource("/api/agent/events");

    es.addEventListener("identity_updated", (e) => {
      const data = JSON.parse(e.data) as AgentIdentity;
      applyIdentity(data);
    });

    es.onerror = () => es.close();
    return () => es.close();
  }, []);

  useEffect(() => {
    refreshIdentity();
  }, []);

  const login = async (username: string, password: string) => {
    try {
      const response = await apiClient.post("/auth/login", {
        username: username.trim(),
        password,
      });
      applyIdentity(response.data);
    } catch {
      throw new Error("Invalid credentials");
    }
  };

  const logout = async () => {
    await apiClient.post("/auth/logout");
    applyIdentity(null);
  };

  return (
    <AuthContext.Provider
      value={{
        isAuthenticated,
        isLoading,
        identity,
        publicToken: identity?.publicToken ?? null,
        login,
        logout,
        apiClient,
        refreshIdentity,
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
