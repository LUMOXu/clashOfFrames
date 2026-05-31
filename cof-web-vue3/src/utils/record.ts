export function recordId(value: unknown, fallback: string | number): string {
  if (value && typeof value === "object" && "id" in value) {
    const id = (value as { id?: unknown }).id;
    if (id != null) return String(id);
  }
  return String(fallback);
}

export function recordName(value: unknown, fallback: string): string {
  if (value && typeof value === "object" && "name" in value) {
    const name = (value as { name?: unknown }).name;
    if (typeof name === "string" && name) return name;
  }
  return fallback;
}

export function recordField<T>(value: unknown, key: string, fallback: T): T {
  if (value && typeof value === "object" && key in value) {
    const field = (value as Record<string, unknown>)[key];
    if (field != null) return field as T;
  }
  return fallback;
}
