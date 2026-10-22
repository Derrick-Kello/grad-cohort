"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, session, type Tokens } from "./api";
import { viewsFor, type View } from "./nav";
import type { Me } from "./types";

type AuthState = {
  me: Me | null;
  status: "loading" | "anonymous" | "signed-in";
  views: View[];
  view: View | null;
  setView: (view: View) => void;
  login: (login: string, password: string) => Promise<View>;
  logout: () => Promise<void>;
  reload: () => Promise<void>;
};

const AuthContext = createContext<AuthState | null>(null);
const VIEW_KEY = "grps.view";

function storedView(): View | null {
  try {
    return (window.localStorage.getItem(VIEW_KEY) as View | null) ?? null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [status, setStatus] = useState<AuthState["status"]>("loading");
  const [view, setViewState] = useState<View | null>(null);

  const adopt = useCallback((user: Me) => {
    const views = viewsFor(user.roles);
    const saved = storedView();
    setMe(user);
    setViewState(saved && views.includes(saved) ? saved : views[0] ?? null);
    setStatus("signed-in");
    return saved && views.includes(saved) ? saved : views[0];
  }, []);

  const reload = useCallback(async () => {
    if (!session.access() && !session.refresh()) {
      setStatus("anonymous");
      return;
    }
    try {
      adopt(await api.get<Me>("/me"));
    } catch {
      session.clear();
      setMe(null);
      setStatus("anonymous");
    }
  }, [adopt]);

  useEffect(() => {
    // Restore the session from browser storage (an external store) on first load.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void reload();
    return session.onExpire(() => {
      setMe(null);
      setStatus("anonymous");
    });
  }, [reload]);

  const setView = useCallback((next: View) => {
    setViewState(next);
    try {
      window.localStorage.setItem(VIEW_KEY, next);
    } catch {
      // Not critical; the view resets to the default next time.
    }
  }, []);

  const login = useCallback(
    async (loginName: string, password: string) => {
      const tokens = await api.post<Tokens>("/auth/login", { login: loginName, password });
      session.set(tokens);
      const user = await api.get<Me>("/me");
      return adopt(user);
    },
    [adopt],
  );

  const logout = useCallback(async () => {
    const refreshToken = session.refresh();
    try {
      if (refreshToken) await api.post("/auth/logout", { refreshToken });
    } catch {
      // Signing out locally is enough if the server is unreachable.
    }
    session.clear();
    setMe(null);
    setStatus("anonymous");
  }, []);

  const value = useMemo<AuthState>(
    () => ({ me, status, views: me ? viewsFor(me.roles) : [], view, setView, login, logout, reload }),
    [me, status, view, setView, login, logout, reload],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}

export function homeFor(view: View | null | undefined): string {
  return view ? `/${view}` : "/login";
}
