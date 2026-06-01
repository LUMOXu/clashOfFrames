import { shallowRef } from "vue";

export interface ActionToastState {
  message: string;
  x: number;
  y: number;
  variant: "error" | "success";
}

const toast = shallowRef<ActionToastState | null>(null);

export function useActionToast() {
  function showAt(
    event: MouseEvent | { clientX: number; clientY: number },
    message: string,
    variant: "error" | "success" = "error",
  ): void {
    toast.value = {
      message,
      x: event.clientX,
      y: event.clientY,
      variant,
    };
  }

  function clear(): void {
    toast.value = null;
  }

  return { toast, showAt, clear };
}

export function useSharedActionToast() {
  return { toast };
}
