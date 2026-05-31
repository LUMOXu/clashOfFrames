import { unwrap } from "./client";
import type { AuthResult } from "@/types/api";

export interface Credentials {
  username: string;
  password: string;
}

export async function register(credentials: Credentials): Promise<AuthResult> {
  return unwrap<AuthResult>({
    method: "POST",
    url: "/auth/register",
    data: credentials,
  });
}

export async function login(credentials: Credentials): Promise<AuthResult> {
  return unwrap<AuthResult>({
    method: "POST",
    url: "/auth/login",
    data: credentials,
  });
}

export async function logout(): Promise<void> {
  await unwrap<void>({ method: "POST", url: "/auth/logout" });
}
