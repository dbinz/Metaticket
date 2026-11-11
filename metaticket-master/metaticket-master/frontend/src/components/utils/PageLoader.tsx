import { Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

const PageLoader = ({ className }: { className?: string }) => (
  <div className={cn('flex justify-center items-center h-screen', className)}>
    <Loader2 size={48} className="animate-spin text-muted-foreground" />
  </div>
)

export { PageLoader }
