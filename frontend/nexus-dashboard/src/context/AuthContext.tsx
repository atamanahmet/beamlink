import { createContext, useContext, useState } from "react";
import axios, { type AxiosInstance } from "axios";

interface AuthContextType {
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  apiClient: AxiosInstance;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

// Single shared client with httponly cookie
const apiClient = axios.create({
  baseURL: "http://localhost:5000/api/nexus",
  timeout: 10000,
  withCredentials: true,
});

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  const login = async (username: string, password: string) => {
    username = username.trim();
    const response = await apiClient.post("/auth/login", {
      username,
      password,
    });
    if (response.status === 200) {
      setIsAuthenticated(true);
    } else {
      alert("Invalid Credential");
    }
  };

  const logout = async () => {
    await apiClient.post("/auth/logout");
    setIsAuthenticated(false);
  };

  return (
    <AuthContext.Provider value={{ isAuthenticated, login, logout, apiClient }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
};
