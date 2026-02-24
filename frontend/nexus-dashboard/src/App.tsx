import { Dashboard } from "./components/Dashboard";
import { Login } from "./components/Login";
import { AuthProvider, useAuth } from "./context/AuthContext";
import { DataProvider } from "./context/DataContext";

function AppContent() {
  const { isAuthenticated } = useAuth();

  return (
    <div className="bg-linear-to-br from-[#1a0f0a] via-[#3b1f12] to-black">
      {isAuthenticated ? <Dashboard /> : <Login />}
    </div>
  );
}

function App() {
  return (
    <AuthProvider>
      <DataProvider>
        <AppContent />
      </DataProvider>
    </AuthProvider>
  );
}

export default App;
