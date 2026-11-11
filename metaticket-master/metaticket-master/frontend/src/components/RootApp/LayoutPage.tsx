'use client'

import { ResizeObserverComponent } from '@/components/utils/ResizeObserverComponent'
import { Inter } from 'next/font/google'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from '@/components/ui/toaster'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'

const inter = Inter({ subsets: ['latin'] })

// Create a client
const queryClient = new QueryClient()

export default function LayoutPage({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <QueryClientProvider client={queryClient}>
        <ReactQueryDevtools initialIsOpen={false} />
        <body className={inter.className}>
          {children}
          <Toaster />
        </body>
      </QueryClientProvider>
      <ResizeObserverComponent />
    </html>
  )
}
