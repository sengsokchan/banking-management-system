import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { api, currentSession, setSession, subscribeSession } from "./api";
import type { Session } from "./types";

type AuthContextValue = {
  session: Session | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setLocal] = useState<Session | null>(currentSession());

  useEffect(() => subscribeSession(setLocal), []);

  async function login(username: string, password: string) {
    const next = await api<Session>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password })
    });
    setSession(next);
  }

  async function logout() {
    const refreshToken = currentSession()?.refreshToken;
    try {
      await api("/api/auth/logout", {
        method: "POST",
        body: JSON.stringify({ refreshToken })
      });
    } finally {
      setSession(null);
    }
  }

  return <AuthContext.Provider value={{ session, login, logout }}>{children}</AuthContext.Provider>;
}

export function useSession() {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error("AuthProvider is missing.");
  }
  return value;
}
