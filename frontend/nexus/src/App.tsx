import { useState } from "react";
import { Dashboard } from "./components/Dashboard";
import { Login } from "./components/Login";
import { AuthProvider, useAuth } from "./context/AuthContext";
import { AgentProvider } from "./context/AgentContext";
import { TransferProvider } from "./context/TransferContext";
import StartupScreen from "./components/StartupScreen";
import icon from "../assets/icon.png";

function AppContent() {
  const { isAuthenticated } = useAuth();

  return (
    <div className="bg-linear-to-br from-[#1a0f0a] via-[#3b1f12] to-black">
      <div
        style={{ WebkitAppRegion: "drag" } as React.CSSProperties}
        className="h-10 w-full flex items-center px-4 gap-3"
      >
        <img
          src={icon}
          className="w-5 h-5"
          style={{ WebkitAppRegion: "no-drag" } as React.CSSProperties}
        />
        <span className="text-orange-400 text-sm font-medium select-none">
          BeamLink Nexus
        </span>
      </div>
      {isAuthenticated ? <Dashboard /> : <Login />}
    </div>
  );
}

function App() {
  const [ready, setReady] = useState(false);

  if (!ready) return <StartupScreen onReady={() => setReady(true)} />;

  return (
    <AuthProvider>
      <AgentProvider>
        <TransferProvider>
          <AppContent />
        </TransferProvider>
      </AgentProvider>
    </AuthProvider>
  );
}

export default App;
