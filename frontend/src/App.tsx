import { Loader2 } from 'lucide-react'
import { lazy, Suspense, type ComponentType, type LazyExoticComponent } from 'react'
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

/** Màn hình đã làm, tải khi cần (lazy) để trang đầu nhẹ. */
const PAGES: Record<string, LazyExoticComponent<ComponentType>> = {
  '/admin/users': lazy(() => import('@/features/admin/UsersPage').then((m) => ({ default: m.UsersPage }))),
  '/admin/tracks': lazy(() => import('@/features/admin/TracksPage').then((m) => ({ default: m.TracksPage }))),
  '/admin/settings': lazy(() =>
    import('@/features/admin/SettingsPage').then((m) => ({ default: m.SettingsPage })),
  ),
}

/** Mốc kế hoạch sẽ làm các màn hình còn lại (docs/frontend-plan.md §5). */
const MILESTONE: Record<string, string> = {
  '/scoring': 'F6',
  '/sessions': 'F5',
  '/candidates': 'F4',
  '/results': 'F7',
  '/submissions': 'F3',
}

const featureRoutes: RouteObject[] = NAV.flatMap((g) => g.items)
  .filter((item) => item.to !== '/')
  .map((item) => {
    const Page = PAGES[item.to]
    return {
      path: `${item.to.slice(1)}/*`,
      element: (
        <RequireRole roles={item.roles}>
          {Page ? (
            <Suspense fallback={<PageLoading />}>
              <Page />
            </Suspense>
          ) : (
            <PlaceholderPage milestone={MILESTONE[item.to] ?? 'sau'} />
          )}
        </RequireRole>
      ),
    }
  })

function PageLoading() {
  return (
    <div
      className="flex justify-center py-16 text-muted-foreground"
      role="status"
      aria-label="Đang tải trang"
    >
      <Loader2 className="size-5 animate-spin" aria-hidden />
    </div>
  )
}

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
