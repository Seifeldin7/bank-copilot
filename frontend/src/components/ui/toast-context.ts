import { createContext, useContext } from 'react'

export type ToastTone = 'success' | 'info' | 'warning'

export interface ToastItem {
  id: number
  tone: ToastTone
  title: string
  description?: string
}

export interface ToastContextValue {
  notify: (toast: Omit<ToastItem, 'id'>) => void
}

export const ToastContext = createContext<ToastContextValue | null>(null)

export function useToast() {
  const context = useContext(ToastContext)
  if (!context) throw new Error('useToast must be used inside ToastProvider')
  return context
}
