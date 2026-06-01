import { useEffect, useState } from "react";
import icon from "../assets/icon.png";

type Status = "starting" | "ready" | "error";

interface Props {
  onReady: () => void;
}

export default function StartupScreen({ onReady }: Props) {
  const [status, setStatus] = useState<Status>("starting");
  const [errorMsg, setErrorMsg] = useState("");

  useEffect(() => {
    const handle = (data: {
      status: string;
      backendUrl?: string;
      message?: string;
    }) => {
      if (data.status === "ready") {
        setStatus("ready");
        setTimeout(onReady, 400);
      } else if (data.status === "error") {
        setStatus("error");
        setErrorMsg(data.message ?? "Unknown error");
      }
    };

    window.electronAPI.getBackendStatus().then(handle);
    window.electronAPI.onBackendStatus(handle);
  }, []);

  return (
    <div style={styles.overlay}>
      <div style={styles.center}>
        <img src={icon} style={styles.logo} />

        <div style={styles.appName}>BeamLink Nexus</div>

        {status === "starting" && (
          <div style={styles.barTrack}>
            <div style={styles.barFill} />
          </div>
        )}

        {status === "ready" && (
          <div style={{ ...styles.sub, color: "#4ade80" }}>Ready</div>
        )}

        {status === "error" && (
          <>
            <div style={{ ...styles.sub, color: "#f87171" }}>
              Startup failed
            </div>
            <div style={styles.errorMsg}>{errorMsg}</div>
          </>
        )}
      </div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  overlay: {
    position: "fixed",
    inset: 0,
    background: "#1a0f0a",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  },
  center: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    gap: 16,
  },
  logo: {
    width: 72,
    height: 72,
    borderRadius: 16,
    marginBottom: 8,
  },
  appName: {
    fontSize: 22,
    fontWeight: 700,
    color: "#fb923c",
    letterSpacing: "0.05em",
  },
  barTrack: {
    width: 200,
    height: 3,
    background: "#3b1f12",
    borderRadius: 99,
    overflow: "hidden",
    marginTop: 8,
  },
  barFill: {
    height: "100%",
    width: "40%",
    background: "#f97316",
    borderRadius: 99,
    animation: "slide 1.2s ease-in-out infinite",
  },
  sub: {
    fontSize: 13,
    color: "#7c5a47",
    marginTop: 4,
  },
  errorMsg: {
    fontSize: 11,
    color: "#6b4030",
    maxWidth: 320,
    textAlign: "center",
    lineHeight: 1.5,
  },
};
