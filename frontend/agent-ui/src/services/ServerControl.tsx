import React, { useEffect, useState } from "react";

type ServerStatus = {
  enabled: boolean;
};

const SERVER_CONTROL_URL = "http://localhost:8080/api/control";

export const ServerControl: React.FC = () => {
  const [enabled, setEnabled] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(false);

  // Fetch current status from server
  const fetchStatus = async () => {
    try {
      const res = await fetch(SERVER_CONTROL_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ action: "status" }),
      });
      const data: ServerStatus = await res.json();
      setEnabled(data.enabled);
    } catch (err) {
      console.error("Failed to fetch server status", err);
    }
  };

  // Send enable/disable command
  const setServer = async (enable: boolean) => {
    setLoading(true);
    try {
      const res = await fetch(SERVER_CONTROL_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ action: enable ? "enable" : "disable" }),
      });
      const data = await res.json();
      console.log(data);
      setEnabled(enable);
    } catch (err) {
      console.error("Failed to update server", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStatus();
  }, []);

  return (
    <div className="flex justify-center w-full mb-6">
      <div
        className="relative bg-black/70 backdrop-blur-xl 
                    border border-orange-500/20 
                    shadow-[0_0_40px_rgba(255,120,0,0.15)]
                    rounded-2xl px-6 py-3 w-full max-w-xl max-h-30"
      >
        {/* Title */}
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-orange-400 font-semibold tracking-widest text-sm uppercase">
            Server Control
          </h2>

          {/* Status Light */}
          <div className="flex items-center gap-3">
            <div
              className={`h-3 w-3 rounded-full ${
                enabled
                  ? "bg-green-400 shadow-[0_0_10px_rgba(255,180,0,0.8)]"
                  : "bg-red-500 shadow-[0_0_10px_rgba(255,0,0,0.6)]"
              }`}
            />
            <span
              className={`text-sm font-medium ${
                enabled ? "text-amber-300" : "text-red-400"
              }`}
            >
              {enabled ? "ONLINE" : "OFFLINE"}
            </span>
          </div>
        </div>

        {/* Toggle Switch */}
        <div className="flex justify-center mb-1">
          <button
            onClick={() => setServer(!enabled)}
            disabled={loading}
            className={`relative w-32 h-12 rounded-full transition-all duration-300
            ${
              enabled
                ? "bg-linear-to-r from-orange-600 to-amber-500 shadow-[0_0_20px_rgba(255,120,0,0.5)]"
                : "bg-linear-to-r from-red-800 to-red-600 shadow-[0_0_15px_rgba(255,0,0,0.4)]"
            }
          `}
          >
            <div
              className={`absolute top-1 left-1 h-10 w-10 bg-black rounded-full 
              transition-all duration-300 
              ${enabled ? "translate-x-20" : "translate-x-0"}
            `}
            />
          </button>
        </div>

        {/* {loading && (
          <p className="text-center text-orange-400 text-sm mt-4 animate-pulse">
            Updating server status...
          </p>
        )} */}
      </div>
    </div>
  );
};
