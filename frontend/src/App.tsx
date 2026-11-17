import { createBrowserRouter, RouterProvider } from 'react-router'
import { BootstrapCheck } from '@/app/BootstrapCheck'

const router = createBrowserRouter([{ path: '*', element: <BootstrapCheck /> }])

export default function App() {
  return <RouterProvider router={router} />
}
