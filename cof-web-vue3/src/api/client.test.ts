import { describe, expect, it, vi, beforeEach } from "vitest";

const { requestMock } = vi.hoisted(() => ({
  requestMock: vi.fn(),
}));

vi.mock("axios", () => ({
  default: {
    create: vi.fn(() => ({
      request: requestMock,
      interceptors: {
        request: { use: vi.fn() },
        response: { use: vi.fn() },
      },
    })),
  },
}));

import { ApiError, unwrap } from "./client";

describe("api client", () => {
  beforeEach(() => {
    requestMock.mockReset();
  });

  it("unwrap returns data when code is 0", async () => {
    requestMock.mockResolvedValue({
      data: { code: 0, message: "ok", data: { token: "abc" } },
    });
    const result = await unwrap<{ token: string }>({ method: "GET", url: "/test" });
    expect(result.token).toBe("abc");
  });

  it("throws ApiError when code is non-zero", async () => {
    requestMock.mockResolvedValue({
      data: { code: 401, message: "请先登录。", data: null },
    });
    await expect(unwrap({ method: "GET", url: "/test" })).rejects.toBeInstanceOf(ApiError);
  });
});
