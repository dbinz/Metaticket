import { cn } from '@/lib/utils'

const Wrapper = ({ children }: { children: React.ReactNode }) => (
  <div className="flex flex-col md:flex-row mt-5">{children}</div>
)

const Main = ({ children }: { children: React.ReactNode }) => (
  <div className="w-full md:w-2/3 flex-1">{children}</div>
)

const Sidebar = ({
  children,
  className,
}: {
  children: React.ReactNode
  className?: string
}) => (
  <div
    className={cn(
      'w-full md:w-1/3 md:min-w-[330px] md:max-w-md md:pl-4 mt-6 md:mt-0 md:ml-4 md:border-l-[1px] mb-10',
      className
    )}
  >
    {children}
  </div>
)

export { Wrapper, Main, Sidebar }
