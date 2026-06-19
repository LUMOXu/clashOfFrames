export const GOD_USERNAME_HINT = "用户名不能包含神的名讳";

export function godUsernameError(username: string): string | null {
  return username.trim().toLowerCase().includes("god") ? GOD_USERNAME_HINT : null;
}
