export const GOD_USERNAME_HINT = "用户名不能包含 GOD（不区分大小写）。";

export function godUsernameError(username: string): string | null {
  return username.trim().toLowerCase().includes("god") ? GOD_USERNAME_HINT : null;
}
