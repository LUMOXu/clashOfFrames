import axios, { type AxiosInstance, type AxiosRequestConfig } from "axios";
import type { ApiResponse } from "@/types/api";

export const API_BASE = "/api/v1";

export class ApiError extends Error {
  code: number;

  constructor(message: string, code: number) {
    super(message);
    this.name = "ApiError";
    this.code = code;
  }
}

let tokenProvider: (() => string | null) | null = null;
let unauthorizedHandler: (() => void) | null = null;

export function setTokenProvider(provider: () => string | null): void {
  tokenProvider = provider;
}

export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler;
}

export function createApiClient(baseURL = API_BASE): AxiosInstance {
  const client = axios.create({
    baseURL,
    headers: { "Content-Type": "application/json" },
  });

  client.interceptors.request.use((config) => {
    const token = tokenProvider?.();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    // FormData 必须由浏览器设置 multipart boundary；全局 application/json 会导致上传 500
    if (typeof FormData !== "undefined" && config.data instanceof FormData) {
      if (config.headers) {
        const headers = config.headers as Record<string, unknown>;
        delete headers["Content-Type"];
        delete headers["content-type"];
      }
    }
    return config;
  });

  client.interceptors.response.use(
    (response) => {
      const body = response.data as ApiResponse<unknown>;
      if (body && typeof body.code === "number" && body.code !== 0) {
        throw new ApiError(body.message || "请求失败", body.code);
      }
      return response;
    },
    (error) => {
      const status = error.response?.status;
      if (status === 401) {
        unauthorizedHandler?.();
      }
      const body = error.response?.data as ApiResponse<unknown> | undefined;
      let message = body?.message || error.message || "请求失败";
      if (!error.response) {
        message = "无法连接后端服务，请确认后端已启动。";
      } else if (!body?.message && typeof status === "number" && status >= 500) {
        message = "后端服务异常，请查看后端日志。";
      }
      const code = body?.code ?? status ?? 500;
      return Promise.reject(new ApiError(message, code));
    },
  );

  return client;
}

export const apiClient = createApiClient();

export async function unwrap<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.request<ApiResponse<T>>(config);
  const body = response.data;
  if (body && typeof body.code === "number" && body.code !== 0) {
    throw new ApiError(body.message || "请求失败", body.code);
  }
  return body.data as T;
}
