import { createContext, useContext } from "react";
import type { KitchenSettings, UserProfile } from "./api/types";

export interface AppContext {
  me: UserProfile;
  kitchen: KitchenSettings;
  isOwner: boolean;
  /** The operating day every page shows (kitchen-local, yyyy-mm-dd). */
  date: string;
  setDate: (date: string) => void;
  reloadKitchen: () => void;
}

export const Ctx = createContext<AppContext | null>(null);

export function useApp(): AppContext {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useApp outside provider");
  return ctx;
}
