import type { ApiError, AuthTokens } from "./types";

const BASE = "/api";
const STORAGE_KEY = "metech-lunch-session";

export class ApiFailure extends Error {
  constructor(public status: number, public code: string, message: string) {
    super(message);
  }
}

interface Session {
  tokens: AuthTokens;
}

function readSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

function writeSession(session: Session | null) {
  try {
    if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Storage unavailable (private mode): the session lives only in memory.
  }
  memorySession = session;
  listeners.forEach((l) => l(session != null));
}

let memorySession: Session | null = readSession();
const listeners = new Set<(signedIn: boolean) => void>();

export const session = {
  isSignedIn: () => memorySession != null,
  save: (tokens: AuthTokens) => writeSession({ tokens }),
  clear: () => writeSession(null),
  subscribe(listener: (signedIn: boolean) => void) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
};

let refreshing: Promise<boolean> | null = null;

/** Exchanges the refresh token once, shared by concurrent requests that hit a 401. */
async function refreshTokens(): Promise<boolean> {
  const refreshToken = memorySession?.tokens.refreshToken;
  if (!refreshToken) return false;
  refreshing ??= (async () => {
    try {
      const res = await fetch(`${BASE}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
      if (!res.ok) {
        session.clear();
        return false;
      }
      session.save((await res.json()) as AuthTokens);
      return true;
    } catch {
      return false;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

async function send(method: string, path: string, body?: unknown, retry = true): Promise<Response> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const token = memorySession?.tokens.accessToken;
  if (token) headers["Authorization"] = `Bearer ${token}`;

  let res: Response;
  try {
    res = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiFailure(0, "NETWORK", "Cannot reach the server. Check your connection.");
  }
  if (res.status === 401 && retry && memorySession) {
    if (await refreshTokens()) return send(method, path, body, false);
  }
  if (!res.ok) {
    let err: ApiError | null = null;
    try {
      err = (await res.json()) as ApiError;
    } catch {
      /* not JSON */
    }
    throw new ApiFailure(res.status, err?.code ?? `HTTP_${res.status}`, err?.message ?? `Request failed (${res.status})`);
  }
  return res;
}

async function json<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await send(method, path, body);
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  get: <T>(path: string) => json<T>("GET", path),
  post: <T>(path: string, body?: unknown) => json<T>("POST", path, body ?? {}),
  put: <T>(path: string, body: unknown) => json<T>("PUT", path, body),
  /** Downloads a file (e.g. CSV export) with the user's credentials. */
  async download(path: string, fallbackName: string) {
    const res = await send("GET", path);
    const disposition = res.headers.get("Content-Disposition") ?? "";
    const name = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? fallbackName;
    const url = URL.createObjectURL(await res.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
};
