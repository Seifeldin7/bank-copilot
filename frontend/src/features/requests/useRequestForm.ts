import { useEffect } from 'react'
import { useForm, type Path } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/api/client'
import { requestFormSchema, type RequestFormValues } from './schema'

export function useRequestForm(initial: RequestFormValues, serverError: unknown) {
  const form = useForm<RequestFormValues>({ resolver: zodResolver(requestFormSchema), defaultValues: initial, mode: 'onTouched' })
  useEffect(() => {
    if (serverError instanceof ApiError) {
      for (const fieldError of serverError.fieldErrors) {
        form.setError(fieldError.field as Path<RequestFormValues>, { type: 'server', message: fieldError.message })
      }
    }
  }, [serverError, form])
  return form
}
