"use client";

import { useCallback, useState, type Dispatch, type SetStateAction } from "react";
import useSWR, { type SWRConfiguration } from "swr";
import { ApiError, fetcher } from "./api";

/** Loads an API path; pass null to skip. */
export function useApi<T>(path: string | null, config?: SWRConfiguration<T, ApiError>) {
  return useSWR<T, ApiError>(path, fetcher as (p: string) => Promise<T>, { revalidateOnFocus: false, ...config });
}

/** Wraps an async action with pending and error state for buttons and forms. */
export function useAction<A extends unknown[], R>(fn: (...args: A) => Promise<R>) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const run = useCallback(
    async (...args: A): Promise<R | undefined> => {
      setPending(true);
      setError(null);
      try {
        return await fn(...args);
      } catch (e) {
        setError(e instanceof ApiError ? e : new ApiError({ status: 0, code: "ERROR", detail: String(e) }));
        return undefined;
      } finally {
        setPending(false);
      }
    },
    [fn],
  );
  return { run, pending, error, setError };
}

/**
 * Editable copy of server data. Resets whenever a new response arrives, using React's
 * "adjust state while rendering" pattern instead of an effect.
 */
export function useEditable<S, T>(source: S | undefined, toState: (s: S) => T, initial: T): [T, Dispatch<SetStateAction<T>>] {
  const [state, setState] = useState<T>(initial);
  const [seen, setSeen] = useState<S | undefined>(undefined);
  if (source !== undefined && source !== seen) {
    setSeen(source);
    setState(toState(source));
  }
  return [state, setState];
}
