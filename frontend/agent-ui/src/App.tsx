import { useState } from "react";
import {
  BrowserRouter as Router,
  Routes,
  Route,
  Navigate,
} from "react-router-dom";
import { FileUpload } from "./components/FileUpload";
import { Login } from "./components/Login";
import { AgentInfo } from "./components/AgentInfo";

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  if (!isAuthenticated) {
    return <Login onLogin={() => setIsAuthenticated(true)} />;
  }

  return (
    <Router>
      <Routes>
        <Route
          path="/"
          element={<FileUpload onLogout={() => setIsAuthenticated(false)} />}
        />
        <Route
          path="/info"
          element={<AgentInfo onLogout={() => setIsAuthenticated(false)} />}
        />
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </Router>
  );
}

export default App;
