import { unwrap } from "./client";
import type { BootstrapData } from "@/types/api";

export async function bootstrap(): Promise<BootstrapData> {
  return unwrap<BootstrapData>({ method: "GET", url: "/session/bootstrap" });
}
