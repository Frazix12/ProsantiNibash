"use client";
import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import type { StagedUpload } from "@/lib/recovery";
import { safePath } from "@/lib/validation";
import type { Book, Command } from "@/lib/types";
import { validateCommand } from "@/lib/validation";
export class RequestError extends Error {
  constructor(
    message: string,
    public uncertain = false,
  ) {
    super(message);
  }
}
export async function api<T>(url: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url, {
      ...init,
      cache: "no-store",
      signal: AbortSignal.timeout(45_000),
      headers: { "x-pn-local": "1", ...init?.headers },
    });
  } catch {
    throw new RequestError(
      "The local server or database could not be reached. Retry any unconfirmed request unchanged.",
      true,
    );
  }
  let data;
  try {
    data = await response.json();
  } catch {
    throw new RequestError("The server response was incomplete. Retry the original request.", true);
  }
  if (!response.ok) throw new RequestError(data.error || "Request failed.", data.uncertain === true);
  return data as T;
}
type Pending = { command: Command; cleanup: string[] };
type BookContext = {
  book: Book | null;
  loading: boolean;
  saving: boolean;
  error: string;
  notice: string;
  pending: Pending | null;
  cleanup: string[];
  staged: StagedUpload | null;
  stage: (value: StagedUpload | null) => void;
  abandonUpload: () => void;
  refresh: () => Promise<void>;
  submit: (command: Command, cleanup?: string[]) => Promise<boolean>;
  retryCleanup: () => Promise<void>;
  dismissNotice: () => void;
};
const Context = createContext<BookContext | null>(null);
export const useBook = () => {
  const value = useContext(Context);
  if (!value) throw new Error("Book provider missing.");
  return value;
};
export function BookProvider({ children }: { children: React.ReactNode }) {
  const [book, setBook] = useState<Book | null>(null),
    [loading, setLoading] = useState(true),
    [saving, setSaving] = useState(false);
  const [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const [pending, setPending] = useState<Pending | null>(null),
    [cleanup, setCleanup] = useState<string[]>([]);
  const [staged, setStaged] = useState<StagedUpload | null>(null);
  const [recoveryBlocked, setRecoveryBlocked] = useState(false);
  const busy = useRef(false),
    refreshing = useRef(false);
  const refresh = useCallback(async () => {
    if (refreshing.current || busy.current) return;
    refreshing.current = true;
    setLoading(true);
    setError("");
    try {
      setBook(await api<Book>("/api/book"));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      refreshing.current = false;
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    let active = true;
    api<Book>("/api/book")
      .then((value) => {
        if (active) setBook(value);
      })
      .catch((e) => {
        if (active) setError(e.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);
  useEffect(() => {
    Promise.resolve().then(() => {
      try {
        const stored = sessionStorage.getItem("pn.pending");
        if (stored) {
          const p = JSON.parse(stored) as Pending;
          validateCommand(p.command);
          if (!Array.isArray(p.cleanup) || !p.cleanup.every(safePath)) throw new Error("Invalid pending cleanup paths");
          setPending(p);
        }
        const paths = JSON.parse(sessionStorage.getItem("pn.cleanup") ?? "[]");
        if (!Array.isArray(paths) || !paths.every(safePath)) throw new Error("Invalid cleanup recovery data");
        setCleanup(paths);
        const upload = sessionStorage.getItem("pn.staged");
        if (upload) {
          const s = JSON.parse(upload) as StagedUpload;
          validateCommand(s.command);
          if (
            !safePath(s.path) ||
            !Array.isArray(s.cleanup) ||
            !s.cleanup.every(safePath) ||
            s.command.data.storage_path !== s.path
          )
            throw new Error("Invalid staged upload");
          setStaged(s);
        }
      } catch {
        setRecoveryBlocked(true);
        setError(
          "Local recovery data could not be read. Do not re-enter an uncertain payment; check its audit history first.",
        );
      }
    });
  }, []);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (pending || saving) event.preventDefault();
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [pending, saving]);
  const stage = (value: StagedUpload | null) => {
    if (value) sessionStorage.setItem("pn.staged", JSON.stringify(value));
    else sessionStorage.removeItem("pn.staged");
    setStaged(value);
  };
  const rememberCleanup = (paths: string[]) => {
    sessionStorage.setItem("pn.cleanup", JSON.stringify(paths));
    setCleanup(paths);
  };
  const abandonUpload = () => {
    if (staged && !pending) {
      rememberCleanup([...new Set([...cleanup, staged.path])]);
      stage(null);
    }
  };
  const removeFiles = async (paths: string[]) => {
    const remaining: string[] = [];
    for (const path of paths) {
      try {
        await api("/api/files", {
          method: "DELETE",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ path }),
        });
      } catch {
        remaining.push(path);
      }
    }
    return remaining;
  };
  const retryCleanup = async () => rememberCleanup(await removeFiles(cleanup));
  const submit = async (command: Command, paths: string[] = []): Promise<boolean> => {
    if (recoveryBlocked) {
      setError("Recovery data is unreadable. Check audit history before clearing this tab’s session storage.");
      return false;
    }
    if (refreshing.current) {
      setError("Wait for the current refresh to finish before saving.");
      return false;
    }
    if (busy.current || (pending && JSON.stringify(pending.command) !== JSON.stringify(command))) return false;
    const next = pending ?? { command, cleanup: paths };
    try {
      validateCommand(command);
      sessionStorage.setItem("pn.pending", JSON.stringify(next));
    } catch (e) {
      setError((e as Error).message);
      return false;
    }
    busy.current = true;
    setPending(next);
    setSaving(true);
    setError("");
    try {
      const response = await api<{ result: { paths?: string[] } }>("/api/book", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(command),
      });
      const serverPaths = command.action === "permanent_delete" ? (response.result.paths ?? []) : [];
      if (!Array.isArray(serverPaths) || !serverPaths.every(safePath))
        throw new Error("Deletion confirmed; file cleanup response was invalid.");
      const paths = [...new Set([...cleanup, ...next.cleanup, ...serverPaths])];
      // Persist cleanup before dropping the retry payload so storage failures cannot orphan files.
      rememberCleanup(paths);
      sessionStorage.removeItem("pn.pending");
      setPending(null);
      if (staged?.command.request_id === command.request_id) stage(null);
      rememberCleanup(await removeFiles(paths));
      setNotice("Saved.");
      busy.current = false;
      await refresh();
      return true;
    } catch (e) {
      if (e instanceof RequestError && !e.uncertain) {
        sessionStorage.removeItem("pn.pending");
        setPending(null);
      }
      setError((e as Error).message);
      return false;
    } finally {
      busy.current = false;
      setSaving(false);
    }
  };
  return (
    <Context.Provider
      value={{
        book,
        loading,
        saving,
        error,
        notice,
        pending,
        cleanup,
        staged,
        stage,
        abandonUpload,
        refresh,
        submit,
        retryCleanup,
        dismissNotice: () => setNotice(""),
      }}
    >
      {children}
    </Context.Provider>
  );
}
