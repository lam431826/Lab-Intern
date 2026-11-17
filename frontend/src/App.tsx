import { createBrowserRouter, RouterProvider, type RouteObject } from 'react-router'
import { AppLayout } from '@/app/AppLayout'
import { SignInPage, SignUpPage } from '@/app/pages/AuthPages'
import { HomePage } from '@/app/pages/HomePage'
import { PlaceholderPage } from '@/app/pages/PlaceholderPage'
import { NotFoundPage } from '@/app/pages/StatusPages'
import { AccessGate } from '@/auth/AccessGate'
import { ClerkRoot } from '@/auth/ClerkRoot'
import { RequireRole } from '@/auth/RequireRole'
import { RequireSignIn } from '@/auth/RequireSignIn'
import { NAV } from '@/lib/nav'

/** Mốc kế hoạch sẽ làm từng màn hình (docs/frontend-plan.md §5). */
const MILESTONE: Record<string, string> = {
  '/scoring': 'F6',
  '/sessions': 'F5',
  '/candidates': 'F4',
  '/results': 'F7',
  '/submissions': 'F3',
  '/admin/users': 'F2',
  '/admin/tracks': 'F2',
  '/admin/settings': 'F2',
}

const featureRoutes: RouteObject[] = NAV.flatMap((g) => g.items)
  .filter((item) => item.to !== '/')
  .map((item) => ({
    path: `${item.to.slice(1)}/*`,
    element: (
      <RequireRole roles={item.roles}>
        <PlaceholderPage milestone={MILESTONE[item.to] ?? 'sau'} />
      </RequireRole>
    ),
  }))

export const routes: RouteObject[] = [
  {
    element: <ClerkRoot />,
    children: [
      // Clerk tự điều hướng các bước con (xác minh email, mật khẩu...) dưới /sign-in/*, /sign-up/*
      { path: 'sign-in/*', element: <SignInPage /> },
      { path: 'sign-up/*', element: <SignUpPage /> },
      {
        element: <RequireSignIn />,
        children: [
          {
            element: <AccessGate />,
            children: [
              {
                element: <AppLayout />,
                children: [
                  { index: true, element: <HomePage /> },
                  ...featureRoutes,
                  { path: '*', element: <NotFoundPage /> },
                ],
              },
            ],
          },
        ],
      },
    ],
  },
]

const router = createBrowserRouter(routes)

export default function App() {
  return <RouterProvider router={router} />
}
