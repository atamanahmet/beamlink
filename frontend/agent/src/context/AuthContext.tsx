import { createContext, useContext, useState, useEffect, useRef } from "react";
import axios, { type AxiosInstance } from "axios";

interface AgentIdentity {
  agentId: string;
  agentName: string;
  state: string;
}

interface AuthContextType {
  isAuthenticated: boolean;
  isBackendReady: boolean;
  backendError: string | null;
  identity: AgentIdentity | null;
  view: "login" | "register";
  onSseEvent: (eventName: string, handler: (data: any) => void) => () => void;
  setView: (view: "login" | "register") => void;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  register: (username: string, password: string) => Promise<void>;
  refreshIdentity: () => Promise<void>;
  apiClient: AxiosInstance;
  healthClient: AxiosInstance;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isBackendReady, setIsBackendReady] = useState(false);
  const [backendError, setBackendError] = useState<string | null>(null);
  const [identity, setIdentity] = useState<AgentIdentity | null>(null);
  const [view, setView] = useState<"login" | "register">("login");

  const apiClientRef = useRef<AxiosInstance>(
    axios.create({
      baseURL: import.meta.env.DEV
        ? "/api/agent"
        : "http://localhost:9090/api/agent",
      timeout: 10000,
      withCredentials: false,
    }),
  );

  const healthClientRef = useRef<AxiosInstance>(
    axios.create({
      baseURL: import.meta.env.DEV ? "" : "http://localhost:9090",
      timeout: 5000,
    }),
  );

  const onBackendReady = (backendUrl?: string) => {
    if (backendUrl && !import.meta.env.DEV) {
      apiClientRef.current.defaults.baseURL = `${backendUrl}/api/agent`;
    }
    setIsBackendReady(true);
  };

  useEffect(() => {
    window.electronAPI?.getBackendStatus().then((data) => {
      if (data.status === "ready") onBackendReady(data.backendUrl);
      else if (data.status === "error")
        setBackendError(data.message ?? "Backend failed to start.");
    });

    window.electronAPI?.onBackendStatus((data) => {
      if (data.status === "ready") onBackendReady(data.backendUrl);
      else if (data.status === "error")
        setBackendError(data.message ?? "Backend failed to start.");
    });
  }, []);

  const sseHandlersRef = useRef<Map<string, Set<(data: any) => void>>>(
    new Map(),
  );

  const onSseEvent = (eventName: string, handler: (data: any) => void) => {
    if (!sseHandlersRef.current.has(eventName)) {
      sseHandlersRef.current.set(eventName, new Set());
    }
    sseHandlersRef.current.get(eventName)!.add(handler);

    return () => void sseHandlersRef.current.get(eventName)?.delete(handler);
  };

  useEffect(() => {
    if (!isAuthenticated) return;
    const unsubscribe = onSseEvent("identity_updated", (data) => {
      setIdentity(data as AgentIdentity);
    });
    return () => unsubscribe();
  }, [isAuthenticated]);

  useEffect(() => {
    if (!isAuthenticated) return;

    const es = new EventSource(
      `${apiClientRef.current.defaults.baseURL}/events`,
    );

    es.onmessage = (e) => {
      const { event, data } = JSON.parse(e.data);
      sseHandlersRef.current.get(event)?.forEach((fn) => fn(data));
    };

    /** Typed event listeners for named SSE events */
    ["identity_updated", "agent_status_updated"].forEach((eventName) => {
      es.addEventListener(eventName, (e) => {
        const data = JSON.parse((e as MessageEvent).data);
        sseHandlersRef.current.get(eventName)?.forEach((fn) => fn(data));
      });
    });

    es.onerror = () => es.close();
    return () => es.close();
  }, [isAuthenticated]);

  const refreshIdentity = async () => {
    try {
      const response = await apiClientRef.current.get("/auth/me");
      setIdentity(response.data);
      setIsAuthenticated(true);
    } catch {
      setIdentity(null);
      setIsAuthenticated(false);
    }
  };

  const login = async (username: string, password: string) => {
    const response = await apiClientRef.current.post("/auth/login", {
      username: username.trim(),
      password,
    });

    if (response.status === 200) {
      apiClientRef.current.defaults.headers.common["X-Auth-Token"] =
        response.data.token;
      setIdentity(response.data.identity ?? null);
      setIsAuthenticated(true);
    } else {
      throw new Error("Invalid credentials");
    }
  };

  const logout = async () => {
    await apiClientRef.current.post("/auth/logout");
    delete apiClientRef.current.defaults.headers.common["X-Auth-Token"];
    setIdentity(null);
    setIsAuthenticated(false);
    setView("login");
  };

  const register = async (username: string, password: string) => {
    await apiClientRef.current.post("/auth/register", { username, password });
    setView("login");
  };

  return (
    <AuthContext.Provider
      value={{
        isAuthenticated,
        isBackendReady,
        backendError,
        identity,
        view,
        setView,
        login,
        logout,
        register,
        refreshIdentity,
        onSseEvent,
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
