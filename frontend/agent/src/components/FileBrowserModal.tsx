import { useState, useEffect, useCallback, useRef } from "react";
import { FolderPlus, X, Check, Trash2 } from "lucide-react";

export interface FsEntry {
  name: string;
  fullPath: string;
  isDirectory: boolean;
  size?: number;
  locked: boolean;
  hidden: boolean;
}

interface SidebarItem {
  label: string;
  path: string;
  icon: string;
}

export interface FileBrowserModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: (paths: string[]) => void;
  title?: string;
  /**
   * "multi"  — default, files + folders, multi-select (existing behaviour)
   * "folder" — single folder picker for directory selection
   */
  mode?: "multi" | "folder";
}

function getSidebarItems(): SidebarItem[] {
  const home = window.electronAPI.getHomedir();
  const sep = window.electronAPI.getSep();
  const join = (...parts: string[]) => parts.join(sep).replace(/[/\\]+/g, sep);

  return [
    { label: "Desktop", icon: "🖥", path: join(home, "Desktop") },
    { label: "Downloads", icon: "⬇", path: join(home, "Downloads") },
    { label: "Documents", icon: "📄", path: join(home, "Documents") },
    { label: "Pictures", icon: "🖼", path: join(home, "Pictures") },
    { label: "Music", icon: "♪", path: join(home, "Music") },
    { label: "Videos", icon: "▶", path: join(home, "Videos") },
  ];
}

function formatSize(bytes?: number): string {
  if (bytes == null) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024)
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

function getFileIcon(name: string): string {
  const ext = name.split(".").pop()?.toLowerCase() ?? "";
  const map: Record<string, string> = {
    png: "🖼",
    jpg: "🖼",
    jpeg: "🖼",
    gif: "🖼",
    webp: "🖼",
    svg: "🖼",
    ico: "🖼",
    mp4: "🎬",
    mkv: "🎬",
    avi: "🎬",
    mov: "🎬",
    wmv: "🎬",
    mp3: "🎵",
    wav: "🎵",
    flac: "🎵",
    ogg: "🎵",
    aac: "🎵",
    zip: "📦",
    rar: "📦",
    "7z": "📦",
    tar: "📦",
    gz: "📦",
    ts: "📝",
    tsx: "📝",
    js: "📝",
    jsx: "📝",
    py: "📝",
    java: "📝",
    json: "📝",
    xml: "📝",
    yaml: "📝",
    yml: "📝",
    pdf: "📕",
    doc: "📄",
    docx: "📄",
    txt: "📄",
    md: "📄",
    xls: "📊",
    xlsx: "📊",
    csv: "📊",
    exe: "⚙",
    msi: "⚙",
    dmg: "⚙",
    sh: "⚙",
    bat: "⚙",
  };
  return map[ext] ?? "📄";
}

// /** Generates a non-colliding "New Folder (N)" name in the given directory. */
// function resolveNewFolderName(parentPath: string, sep: string): string {
//   const base = "New Folder";
//   const candidate = (n: number) => (n === 0 ? base : `${base} (${n})`);

//   for (let i = 0; i <= 99; i++) {
//     const name = candidate(i);
//     const full = [parentPath, name].join(sep).replace(/[/\\]+/g, sep);
//     if (!window.electronAPI.exists(full)) return name;
//   }
//   return `${base} (${Date.now()})`;
// }

function EntryTooltip({ entry }: { entry: FsEntry }) {
  const lines: string[] = [];
  if (entry.locked) lines.push("Locked by OS, cannot be sent.");
  if (entry.hidden) lines.push("Hidden file, can be sent.");
  return (
    <div className="absolute -top-1 left-1/2 -translate-x-1/2 -translate-y-full z-50 pointer-events-none">
      <div className="bg-black/85 border border-orange-900/50 rounded px-2 py-1 text-[10px] text-amber-200/80 whitespace-nowrap backdrop-blur-sm">
        {lines.map((l, i) => (
          <div key={i}>{l}</div>
        ))}
      </div>
      <div className="w-2 h-2 bg-black/85 border-r border-b border-orange-900/50 rotate-45 mx-auto -mt-1.25" />
    </div>
  );
}

function SidebarBtn({
  label,
  icon,
  active,
  onClick,
}: {
  label: string;
  icon: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`flex items-center gap-2 w-full px-3 py-1.5 text-xs text-left transition-all duration-100 border-l-2 cursor-pointer
        ${
          active
            ? "bg-amber-600/60 border-orange-500 text-amber-100"
            : "border-transparent text-amber-300 hover:bg-amber-700/30 hover:text-amber-200"
        }`}
    >
      <span className="text-sm w-4 text-center shrink-0">{icon}</span>
      <span className="truncate">{label}</span>
    </button>
  );
}

function DriveCard({
  entry,
  selected,
  onClick,
  onDoubleClick,
}: {
  entry: FsEntry;
  selected: boolean;
  onClick: () => void;
  onDoubleClick: () => void;
}) {
  const [hovered, setHovered] = useState(false);
  return (
    <div
      onClick={onClick}
      onDoubleClick={onDoubleClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      className={`relative w-32 p-3 rounded-lg border cursor-pointer select-none transition-all duration-150 flex flex-col gap-1.5
        ${
          selected
            ? "bg-orange-950/60 border-orange-500"
            : hovered
              ? "bg-orange-950/30 border-orange-900/60"
              : "bg-orange-950/10 border-orange-950/40"
        }`}
    >
      <span className="text-2xl">💽</span>
      <span className="text-xs font-semibold truncate text-amber-500">
        {entry.name}
      </span>
    </div>
  );
}

/** Inline rename input rendered inside the file grid for new folder creation. */
function NewFolderCard({
  onConfirm,
  onCancel,
  error,
}: {
  onConfirm: (name: string) => void;
  onCancel: () => void;
  error: string | null;
}) {
  const [name, setName] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);

  const commit = () => {
    const trimmed = name.trim();
    if (trimmed) onConfirm(trimmed);
    else onCancel();
  };

  return (
    <div className="flex flex-col items-center px-1 py-2 rounded-md border border-orange-500 bg-orange-950/60 select-none">
      <span className="text-2xl leading-none mb-1">📁</span>
      <input
        ref={inputRef}
        value={name}
        onChange={(e) => setName(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter") commit();
          if (e.key === "Escape") onCancel();
          e.stopPropagation();
        }}
        onBlur={commit}
        className="w-full bg-black/60 border border-orange-700/60 rounded px-1 py-0.5
                   text-orange-100 text-[11px] text-center font-mono
                   focus:outline-none focus:border-orange-400 transition-colors"
      />
      {error && (
        <span className="text-[10px] text-red-400 mt-1 text-center leading-tight">
          {error}
        </span>
      )}
    </div>
  );
}

function FileCard({
  entry,
  selected,
  onClick,
  onDoubleClick,
}: {
  entry: FsEntry;
  selected: boolean;
  onClick: () => void;
  onDoubleClick: () => void;
}) {
  const [hovered, setHovered] = useState(false);
  const icon = entry.isDirectory ? "📁" : getFileIcon(entry.name);

  return (
    <div
      onClick={onClick}
      onDoubleClick={onDoubleClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      className={`relative flex flex-col items-center px-1 py-2 rounded-md border transition-all duration-100 select-none
        ${
          entry.locked
            ? "border-red-900/60 bg-red-950/20 cursor-not-allowed"
            : selected
              ? "border-orange-500 bg-orange-950/60 cursor-pointer"
              : hovered
                ? "border-orange-900/40 bg-orange-950/20 cursor-pointer"
                : "border-transparent cursor-pointer"
        }
        ${entry.hidden ? "opacity-50" : "opacity-100"}`}
    >
      {hovered && (entry.hidden || entry.locked) && (
        <EntryTooltip entry={entry} />
      )}

      {/* Tick indicator top-left */}
      {selected && !entry.locked && (
        <div className="absolute top-1 left-1 w-3.5 h-3.5 rounded-full bg-orange-500 flex items-center justify-center shadow-[0_0_4px_#fb923c]">
          <Check className="w-2 h-2 text-white" strokeWidth={3} />
        </div>
      )}

      {entry.locked && (
        <div className="absolute top-1 right-1 text-[10px] leading-none text-red-400">
          🔒
        </div>
      )}

      <span className="text-2xl leading-none mb-1">{icon}</span>

      <span
        className={`text-[11px] text-center w-full truncate leading-snug
        ${entry.locked ? "text-red-500/70" : selected ? "text-amber-500" : hovered ? "text-orange-600" : "text-amber-600"}
        ${entry.hidden ? "italic" : ""}`}
      >
        {entry.name}
      </span>

      {!entry.isDirectory && entry.size != null && (
        <span className="text-[10px] text-amber-600 mt-0.5">
          {formatSize(entry.size)}
        </span>
      )}
    </div>
  );
}

/** Slide-in panel showing selected items with individual untick + clear all. */
function SelectedPanel({
  selected,
  entries,
  onUnselect,
  onClearAll,
  onClose,
}: {
  selected: Set<string>;
  entries: FsEntry[];
  onUnselect: (path: string) => void;
  onClearAll: () => void;
  onClose: () => void;
}) {
  const entryMap = new Map(entries.map((e) => [e.fullPath, e]));

  return (
    <div className="absolute inset-y-0 right-0 w-64 bg-[#0b0805] border-l border-orange-900/40 flex flex-col z-20 shadow-[-8px_0_24px_rgba(0,0,0,0.5)]">
      <div className="flex items-center justify-between px-3 py-2 border-b border-orange-900/30">
        <span className="text-[11px] text-amber-400 font-bold uppercase tracking-widest">
          Selected ({selected.size})
        </span>
        <div className="flex items-center gap-1">
          <button
            onClick={onClearAll}
            title="Clear all"
            className="flex items-center gap-1 px-2 py-0.5 text-[10px] text-red-400 hover:text-red-300
                       bg-red-950/30 hover:bg-red-950/50 border border-red-900/40 rounded transition-colors cursor-pointer"
          >
            <Trash2 className="w-3 h-3" />
            Clear all
          </button>
          <button
            onClick={onClose}
            className="p-1 text-amber-600 hover:text-amber-300 transition-colors cursor-pointer"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto py-1">
        {[...selected].map((fullPath) => {
          const entry = entryMap.get(fullPath);
          const name = entry?.name ?? fullPath.split(/[/\\]/).pop() ?? fullPath;
          const icon = entry?.isDirectory ? "📁" : getFileIcon(name);
          return (
            <div
              key={fullPath}
              className="flex items-center gap-2 px-3 py-1.5 hover:bg-orange-950/20 group"
            >
              <span className="text-sm shrink-0">{icon}</span>
              <span
                className="flex-1 text-[11px] text-amber-400 truncate"
                title={fullPath}
              >
                {name}
              </span>
              <button
                onClick={() => onUnselect(fullPath)}
                className="shrink-0 opacity-0 group-hover:opacity-100 p-0.5 text-amber-600
                           hover:text-red-400 transition-all cursor-pointer"
              >
                <X className="w-3 h-3" />
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export function FileBrowserModal({
  isOpen,
  onClose,
  onConfirm,
  title = "Select Files or Folders",
  mode = "multi",
}: FileBrowserModalProps) {
  const [roots, setRoots] = useState<FsEntry[]>([]);
  const [sidebarItems, setSidebarItems] = useState<SidebarItem[]>([]);
  const [currentPath, setCurrentPath] = useState<string>("");
  const [entries, setEntries] = useState<FsEntry[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [history, setHistory] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showSelectedPanel, setShowSelectedPanel] = useState(false);

  /** New folder state */
  const [creatingFolder, setCreatingFolder] = useState(false);
  const [newFolderError, setNewFolderError] = useState<string | null>(null);
  const sep = window.electronAPI.getSep();

  const loadDirectory = useCallback(
    (dirPath: string, pushHistory = true) => {
      setLoading(true);
      setError(null);
      setCreatingFolder(false);
      setNewFolderError(null);
      try {
        const result: FsEntry[] = window.electronAPI.readDir(dirPath);
        const sorted = [...result].sort((a, b) => {
          if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1;
          return a.name.localeCompare(b.name);
        });
        if (pushHistory) setHistory((h) => [...h, currentPath]);
        setEntries(sorted);
        setCurrentPath(dirPath);
      } catch (e: any) {
        setError(e?.message ?? "Cannot read directory");
      } finally {
        setLoading(false);
      }
    },
    [currentPath],
  );

  useEffect(() => {
    if (!isOpen) return;
    setRoots(window.electronAPI.getRoots());
    setSidebarItems(getSidebarItems());
    setSelected(new Set());
    setHistory([]);
    setEntries([]);
    setCurrentPath("");
    setError(null);
    setCreatingFolder(false);
    setNewFolderError(null);
    setShowSelectedPanel(false);
  }, [isOpen]);

  const goBack = () => {
    if (history.length === 0) return;
    const prev = history[history.length - 1];
    setHistory((h) => h.slice(0, -1));
    if (prev === "") {
      setCurrentPath("");
      setEntries([]);
    } else {
      loadDirectory(prev, false);
    }
  };

  const toggleSelect = (entry: FsEntry) => {
    if (entry.locked) return;
    if (mode === "folder" && !entry.isDirectory) return;

    setSelected((prev) => {
      if (mode === "folder") {
        // Single select for folder mode
        return prev.has(entry.fullPath) ? new Set() : new Set([entry.fullPath]);
      }
      const next = new Set(prev);
      if (next.has(entry.fullPath)) next.delete(entry.fullPath);
      else next.add(entry.fullPath);
      return next;
    });
  };

  const navigateInto = (entry: FsEntry) => {
    if (!entry.isDirectory || entry.locked) return;
    setSelected((prev) => {
      const next = new Set(prev);
      next.delete(entry.fullPath);
      return next;
    });
    loadDirectory(entry.fullPath);
  };

  /** In folder mode, confirm with the current directory itself if nothing selected. */
  const handleConfirm = () => {
    if (mode === "folder") {
      const target = selected.size > 0 ? [...selected][0] : currentPath;
      if (!target) return;
      onConfirm([target]);
      onClose();
      return;
    }
    if (selected.size === 0) return;
    onConfirm([...selected]);
    onClose();
  };

  const handleCreateFolder = () => {
    if (!currentPath) return;
    setCreatingFolder(true);
    setNewFolderError(null);
  };

  const commitNewFolder = (name: string) => {
    const fullPath = [currentPath, name].join(sep).replace(/[/\\]+/g, sep);

    if (window.electronAPI.exists(fullPath)) {
      setNewFolderError(`"${name}" already exists.`);
      return;
    }

    // Validate name — no slashes, no null bytes, non-empty
    if (/[/\\:\0]/.test(name)) {
      setNewFolderError("Name contains invalid characters.");
      return;
    }

    try {
      window.electronAPI.mkdir(fullPath);
      setCreatingFolder(false);
      setNewFolderError(null);
      loadDirectory(currentPath, false); // refresh without pushing history
    } catch (e: any) {
      setNewFolderError(e?.message ?? "Could not create folder.");
    }
  };

  const cancelNewFolder = () => {
    setCreatingFolder(false);
    setNewFolderError(null);
  };

  const unselect = (fullPath: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      next.delete(fullPath);
      return next;
    });
  };

  const clearAll = () => {
    setSelected(new Set());
    setShowSelectedPanel(false);
  };

  const normalised = currentPath.replace(/\\/g, "/");
  const pathSegments = normalised ? normalised.split("/").filter(Boolean) : [];

  const buildSegmentPath = (index: number): string => {
    const segs = pathSegments.slice(0, index + 1);
    if (currentPath.startsWith("/")) return "/" + segs.join("/");
    return segs.join("\\") + (segs.length === 1 ? "\\" : "");
  };

  const canConfirm =
    mode === "folder" ? selected.size > 0 || !!currentPath : selected.size > 0;

  const confirmLabel =
    mode === "folder"
      ? selected.size > 0
        ? "Select Folder"
        : currentPath
          ? "Use This Folder"
          : "Select"
      : `Send${selected.size > 0 ? ` (${selected.size})` : ""}`;

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-9999 flex items-center justify-center">
      <div
        onClick={onClose}
        className="absolute inset-0 bg-amber-950/70 backdrop-blur-sm"
      />

      <div className="relative z-10 w-215 h-145 flex flex-col bg-[#0e0a08] border-2 border-orange-600/60 rounded-xl shadow-[0_0_60px_rgba(251,146,60,0.08),0_24px_48px_rgba(0,0,0,0.7)] overflow-hidden">
        {/* Title bar */}
        <div className="flex items-center justify-between px-4 py-2.5 bg-[#0b0805] border-b border-orange-950/40 shrink-0">
          <span className="text-amber-300 text-[13px] font-semibold tracking-wide">
            {title}
          </span>
          <button
            onClick={onClose}
            className="text-amber-300 hover:text-amber-100 transition-colors text-base px-1.5 py-0.5 rounded"
          >
            ✕
          </button>
        </div>

        {/* Toolbar */}
        <div className="flex items-center gap-2 px-3 py-1 bg-amber-950/70 border-b border-orange-950/30 shrink-0 min-h-8.5">
          <button
            onClick={goBack}
            disabled={history.length === 0}
            className="text-base px-1.5 py-0.5 rounded transition-colors disabled:text-orange-600/40 text-amber-500 hover:text-amber-100 disabled:cursor-default"
          >
            ←
          </button>

          {/* Breadcrumb */}
          <div className="flex items-center flex-1 overflow-hidden text-xs gap-0.5">
            <button
              onClick={() => {
                setHistory((h) => [...h, currentPath]);
                setCurrentPath("");
                setEntries([]);
              }}
              className="text-amber-500 hover:text-amber-100 px-1.5 py-0.5 rounded transition-colors"
            >
              PC
            </button>
            {pathSegments.map((seg, i) => (
              <span key={i} className="flex items-center min-w-0">
                <span className="text-amber-600 px-0.5">›</span>
                <button
                  onClick={() => loadDirectory(buildSegmentPath(i))}
                  className={`px-1 py-0.5 max-w-37.5 truncate rounded transition-colors
                    ${i === pathSegments.length - 1 ? "text-amber-500" : "text-amber-500 hover:text-amber-100"}`}
                >
                  {seg}
                </button>
              </span>
            ))}
          </div>

          {/* New folder button — only when inside a directory */}
          {currentPath && (
            <button
              onClick={handleCreateFolder}
              disabled={creatingFolder}
              title="New folder"
              className="flex items-center gap-1 px-2 py-0.5 text-[11px] text-amber-400
                         hover:text-amber-200 bg-amber-950/40 hover:bg-amber-900/40
                         border border-orange-900/40 hover:border-orange-700/60
                         rounded transition-colors disabled:opacity-40 cursor-pointer disabled:cursor-default"
            >
              <FolderPlus className="w-3.5 h-3.5" />
              New Folder
            </button>
          )}

          {/* Selected count badge — only in multi mode */}
          {mode === "multi" && selected.size > 0 && (
            <button
              onClick={() => setShowSelectedPanel((v) => !v)}
              className="flex items-center gap-1 text-[11px] text-amber-500 bg-amber-950/50
                         border border-orange-800/40 rounded-full px-2.5 py-0.5 shrink-0
                         hover:bg-amber-900/50 hover:text-amber-300 transition-colors cursor-pointer"
            >
              <Check className="w-3 h-3" />
              {selected.size} selected
            </button>
          )}
        </div>

        {/* Body */}
        <div className="flex flex-1 overflow-hidden relative">
          {/* Sidebar */}
          <div className="w-40 shrink-0 bg-amber-950/50 border-r border-orange-950/30 overflow-y-auto py-2">
            <p className="px-3 pt-1 pb-1 text-[10px] text-amber-500 font-bold tracking-widest uppercase">
              Drives
            </p>
            {roots.map((r) => (
              <SidebarBtn
                key={r.fullPath}
                label={r.name}
                icon="💽"
                active={currentPath.startsWith(r.fullPath)}
                onClick={() => loadDirectory(r.fullPath)}
              />
            ))}
            <div className="mx-3 my-1.5 border-t border-orange-950/40" />
            <p className="px-3 pt-1 pb-1 text-[10px] text-amber-300 font-bold tracking-widest uppercase">
              Quick Access
            </p>
            {sidebarItems.map((item) => (
              <SidebarBtn
                key={item.path}
                label={item.label}
                icon={item.icon}
                active={currentPath === item.path}
                onClick={() => loadDirectory(item.path)}
              />
            ))}
          </div>

          {/* Main content */}
          <div className="flex-1 overflow-y-auto bg-[#0e0a08]">
            {loading && (
              <div className="flex items-center justify-center h-full text-amber-300 text-sm">
                Loading...
              </div>
            )}

            {error && (
              <div className="flex items-center justify-center h-full text-red-500/70 text-xs px-6 text-center">
                {error}
              </div>
            )}

            {!loading && !error && currentPath === "" && (
              <div className="p-4">
                <p className="text-[11px] text-amber-500 font-bold tracking-widest uppercase mb-3">
                  Devices and Drives
                </p>
                <div className="flex flex-wrap gap-2">
                  {roots.map((r) => (
                    <DriveCard
                      key={r.fullPath}
                      entry={r}
                      selected={selected.has(r.fullPath)}
                      onClick={() => toggleSelect(r)}
                      onDoubleClick={() => loadDirectory(r.fullPath)}
                    />
                  ))}
                </div>
              </div>
            )}

            {!loading &&
              !error &&
              currentPath !== "" &&
              entries.length === 0 &&
              !creatingFolder && (
                <div className="flex items-center justify-center h-full text-amber-600 text-xs">
                  Empty folder
                </div>
              )}

            {!loading && !error && currentPath !== "" && (
              <div className="p-2.5">
                <div className="grid grid-cols-[repeat(auto-fill,minmax(88px,1fr))] gap-0.5">
                  {/* New folder inline card at the top */}
                  {creatingFolder && (
                    <NewFolderCard
                      onConfirm={commitNewFolder}
                      onCancel={cancelNewFolder}
                      error={newFolderError}
                    />
                  )}
                  {entries.map((entry) => (
                    <FileCard
                      key={entry.fullPath}
                      entry={entry}
                      selected={selected.has(entry.fullPath)}
                      onClick={() => toggleSelect(entry)}
                      onDoubleClick={() => navigateInto(entry)}
                    />
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Selected panel overlay */}
          {mode === "multi" && showSelectedPanel && (
            <SelectedPanel
              selected={selected}
              entries={entries}
              onUnselect={unselect}
              onClearAll={clearAll}
              onClose={() => setShowSelectedPanel(false)}
            />
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between px-4 py-2 bg-[#0b0805] border-t border-orange-950/40 shrink-0">
          <span className="text-[11px] text-amber-600">
            {mode === "folder"
              ? "Navigate to folder · Double-click to open · Click to select"
              : "Click to select · Double-click folder to open"}
          </span>
          <div className="flex gap-2">
            <button
              onClick={onClose}
              className="cursor-pointer px-4 py-1.5 rounded-md border border-orange-950/40 bg-orange-950/10 text-amber-600 hover:text-orange-500 hover:border-orange-800/60 text-xs transition-all"
            >
              Cancel
            </button>
            <button
              onClick={handleConfirm}
              disabled={!canConfirm}
              className={`px-5 py-1.5 rounded-md text-xs font-semibold transition-all cursor-pointer
                ${
                  canConfirm
                    ? "bg-orange-600 hover:bg-orange-500 text-white cursor-pointer"
                    : "bg-orange-950/30 text-amber-600 cursor-default"
                }`}
            >
              {confirmLabel}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
