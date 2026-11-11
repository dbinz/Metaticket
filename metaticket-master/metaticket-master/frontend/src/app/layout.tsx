import './globals.css'
import "react-datepicker/dist/react-datepicker.css";
import LayoutPage from '@/components/RootApp/LayoutPage'

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <LayoutPage>
        {children}
      </LayoutPage>
    </html>
  )
}
