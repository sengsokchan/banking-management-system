import type { FieldError, Session } from "./types";

const STORAGE_KEY = "meridian.session";

export class ApiError extends Error {
  status: number;
  code: string;
  fieldErrors: FieldError[];

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[] = []) {
    super(message);
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

let session: Session | null = readSession();
const listeners = new Set<(next: Session | null) => void>();
let refreshPromise: Promise<boolean> | null = null;

export function currentSession() {
  return session;
}

export function subscribeSession(listener: (next: Session | null) => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function setSession(next: Session | null) {
  session = next;
  if (next) {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } else {
    sessionStorage.removeItem(STORAGE_KEY);
  }
  listeners.forEach((listener) => listener(next));
}

export async function api<T>(path: string, options: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (session?.accessToken) {
    headers.set("Authorization", `Bearer ${session.accessToken}`);
  }
  const response = await fetch(path, { ...options, headers });
  if (response.status === 401 && retry && session && !path.startsWith("/api/auth/")) {
    const refreshed = await refresh();
    if (refreshed) {
      return api<T>(path, options, false);
    }
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  let payload: { code?: string; message?: string; fieldErrors?: FieldError[] } | null = null;
  if (text) {
    try {
      payload = JSON.parse(text) as { code?: string; message?: string; fieldErrors?: FieldError[] };
    } catch {
      payload = null;
    }
  }
  if (!response.ok) {
    throw new ApiError(
      response.status,
      payload?.code ?? "REQUEST_FAILED",
      payload?.message ?? "The request could not be completed.",
      payload?.fieldErrors ?? []
    );
  }
  return payload as T;
}

export function fieldMessage(error: unknown, field: string) {
  if (!(error instanceof ApiError)) {
    return undefined;
  }
  return error.fieldErrors.find((item) => item.field === field)?.message;
}

async function refresh() {
  if (!session) {
    return false;
  }
  if (!refreshPromise) {
    const refreshToken = session.refreshToken;
    refreshPromise = fetch("/api/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken })
    })
      .then(async (response) => {
        if (!response.ok) {
          setSession(null);
          return false;
        }
        setSession((await response.json()) as Session);
        return true;
      })
      .catch(() => {
        setSession(null);
        return false;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

function readSession(): Session | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as Session;
  } catch {
    sessionStorage.removeItem(STORAGE_KEY);
    return null;
  }
}
