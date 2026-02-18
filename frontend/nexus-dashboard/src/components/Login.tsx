import { useState } from "react";
import { Lock } from "lucide-react";

interface LoginProps {
  onLogin: () => void;
}

export const Login = ({ onLogin }: LoginProps) => {
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    // TODO: implement auth
    if (password === "admin") {
      onLogin();
    } else {
      setError("Invalid admin password");
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div
        className="bg-black/70 backdrop-blur-xl border border-orange-500/20
                      shadow-[0_0_40px_rgba(255,120,0,0.15)]
                      rounded-2xl p-8 max-w-md w-full"
      >
        {/* Header */}
        <div className="text-center mb-8">
          <div className="inline-block p-4 bg-orange-500/10 rounded-full mb-4">
            <Lock className="w-12 h-12 text-orange-400" />
          </div>
          <h1 className="text-4xl font-bold text-orange-400 tracking-wide mb-2">
            BeamLink Nexus
          </h1>
          <p className="text-orange-300/60 text-sm">
            Central Command & Control
          </p>
        </div>

        {/* Login Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-orange-300 text-sm mb-2">
              Admin Password
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full bg-black/60 border border-orange-800 rounded-lg px-4 py-3
                         text-orange-100 placeholder-orange-700/50
                         focus:outline-none focus:border-orange-500 focus:ring-2 focus:ring-orange-500/20
                         transition-all"
              placeholder="Enter admin password"
            />
          </div>

          {error && (
            <div className="bg-red-900/50 border border-red-600 rounded-lg p-3">
              <p className="text-red-200 text-sm">{error}</p>
            </div>
          )}

          <button
            type="submit"
            className="w-full bg-linear-to-r from-orange-600 to-amber-500
                       text-black px-6 py-3 rounded-lg font-medium
                       hover:from-orange-500 hover:to-yellow-400
                       transition-all shadow-lg shadow-orange-900/40"
          >
            Access Nexus
          </button>
        </form>
      </div>
    </div>
  );
};
