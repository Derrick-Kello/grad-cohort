// API client for the GRPS backend. Access tokens live 15 minutes; on a 401 the client
// rotates the refresh token once and retries (SEC-03).

export const API_URL = (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080").replace(/\/$/, "") + "/api/v1";

const ACCESS_KEY = "grps.access";
const REFRESH_KEY = "grps.refresh";

export type Problem = {
  status: number;
  code: string;
  title?: string;
  detail?: string;
  traceId?: string;
  errors?: { field?: string; row?: number; message: string }[];
  [key: string]: unknown;
};

export class ApiError extends Error {
  problem: Problem;
  constructor(problem: Problem) {
    super(problem.detail || problem.title || "Request failed");
    this.problem = problem;
  }
  get status() {
    return this.problem.status;
  }
  get code() {
    return this.problem.code;
  }
}

export type Tokens = { accessToken: string; refreshToken: string; expiresIn: number; roles: string[] };

function read(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null) {
  try {
    if (value === null) window.localStorage.removeItem(key);
    else window.localStorage.setItem(key, value);
  } catch {
    // Storage can be unavailable (private mode); the session then lasts for this page only.
  }
}

let memoryAccess: string | null = null;
let refreshing: Promise<boolean> | null = null;
const listeners = new Set<() => void>();

export const session = {
  access(): string | null {
    return memoryAccess ?? read(ACCESS_KEY);
  },
  refresh(): string | null {
    return read(REFRESH_KEY);
  },
  set(tokens: Tokens) {
    memoryAccess = tokens.accessToken;
    write(ACCESS_KEY, tokens.accessToken);
    write(REFRESH_KEY, tokens.refreshToken);
  },
  clear() {
    memoryAccess = null;
    write(ACCESS_KEY, null);
    write(REFRESH_KEY, null);
    listeners.forEach((l) => l());
  },
  onExpire(listener: () => void): () => void {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
};

async function tryRefresh(): Promise<boolean> {
  const refreshToken = session.refresh();
  if (!refreshToken) return false;
  if (!refreshing) {
    refreshing = (async () => {
      try {
        const res = await fetch(`${API_URL}/auth/refresh`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken }),
        });
        if (!res.ok) return false;
        session.set((await res.json()) as Tokens);
        return true;
      } catch {
        return false;
      } finally {
        setTimeout(() => (refreshing = null), 0);
      }
    })();
  }
  return refreshing;
}

async function toProblem(res: Response): Promise<Problem> {
  try {
    const body = await res.json();
    return { status: res.status, code: body.code ?? "ERROR", ...body };
  } catch {
    return { status: res.status, code: "ERROR", detail: res.statusText || "Request failed" };
  }
}

type Options = {
  method?: string;
  body?: unknown;
  form?: FormData;
  headers?: Record<string, string>;
  raw?: boolean;
};

export async function request<T = unknown>(path: string, options: Options = {}, retry = true): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json", ...(options.headers ?? {}) };
  const token = session.access();
  if (token) headers.Authorization = `Bearer ${token}`;
  let body: BodyInit | undefined;
  if (options.form) body = options.form;
  else if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(options.body);
  }
  let res: Response;
  try {
    res = await fetch(`${API_URL}${path}`, { method: options.method ?? "GET", headers, body });
  } catch {
    throw new ApiError({ status: 0, code: "NETWORK", detail: "Cannot reach the server. Check your connection and try again." });
  }
  if (res.status === 401 && retry && !path.startsWith("/auth/")) {
    if (await tryRefresh()) return request<T>(path, options, false);
    session.clear();
  }
  if (!res.ok) throw new ApiError(await toProblem(res));
  if (options.raw) return res as unknown as T;
  if (res.status === 204) return undefined as T;
  const type = res.headers.get("content-type") ?? "";
  return (type.includes("json") ? await res.json() : await res.text()) as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: "POST", body: body ?? {} }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: "PUT", body: body ?? {} }),
  del: <T>(path: string) => request<T>(path, { method: "DELETE" }),
  upload: <T>(path: string, form: FormData) => request<T>(path, { method: "POST", form }),
};

/** Downloads an authenticated file (CSV, PDF, ICS) and saves it with the server's file name. */
export async function download(path: string, fallbackName: string) {
  const res = await request<Response>(path, { raw: true, headers: { Accept: "*/*" } });
  const blob = await res.blob();
  const disposition = res.headers.get("content-disposition") ?? "";
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  const name = match ? decodeURIComponent(match[1]) : fallbackName;
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Opens a signed link (valid 10 minutes) returned by the API. */
export async function openSigned(path: string) {
  const link = await api.get<{ url: string }>(path);
  window.open(link.url, "_blank", "noopener");
}

export const fetcher = <T>(path: string) => request<T>(path);
