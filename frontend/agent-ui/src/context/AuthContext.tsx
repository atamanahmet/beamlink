import { createContext, useContext, useState, useEffect } from "react";
import axios, { type AxiosInstance } from "axios";

interface AuthContextType {
  isAuthenticated: boolean;
  isLoading: boolean;
  publicToken: string | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshIdentity: () => Promise<void>;
  apiClient: AxiosInstance;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const apiClient = axios.create({
  baseURL: "/api",
  timeout: 10000,
  withCredentials: true,
});

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [publicToken, setPublicToken] = useState<string | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  const refreshIdentity = async () => {
    try {
      const response = await apiClient.get("/auth/me");
      setPublicToken(response.data.publicToken);
      setIsAuthenticated(!!response.data.publicToken);
      return response.data.publicToken;
    } catch {
      setPublicToken(null);
      setIsAuthenticated(false);
      return null;
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    refreshIdentity();
  }, []);

  const login = async (username: string, password: string) => {
    try {
      const response = await apiClient.post("/auth/login", {
        username: username.trim(),
        password,
      });
      if (response.data.publicToken == null) {
        console.log("Public token null issues. Check logic");
      }
      setPublicToken(response.data.publicToken);
      setIsAuthenticated(true);
    } catch {
      throw new Error("Invalid credentials");
    }
  };

  const logout = async () => {
    await apiClient.post("/auth/logout");
    setPublicToken(null);
    setIsAuthenticated(false);
  };

  return (
    <AuthContext.Provider
      value={{
        isAuthenticated,
        isLoading,
        login,
        logout,
        apiClient,
        publicToken,
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
