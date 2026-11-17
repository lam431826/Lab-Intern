import { Loader2 } from 'lucide-react'
import { lazy, Suspense, type ComponentType, type LazyExoticComponent } from 'react'
import { createBrowserRouter, RouterProvider, type RouteObject } from 'react-router'
import { AppLayout } from '@/app/AppLayout'
import { SignInPage, SignUpPage } from '@/app/pages/AuthPages'
import { NotFoundPage } from '@/app/pages/StatusPages'
import { AccessGate } from '@/auth/AccessGate'
import { ClerkRoot } from '@/auth/ClerkRoot'
import { RequireRole } from '@/auth/RequireRole'
import { RequireSignIn } from '@/auth/RequireSignIn'
import { NAV } from '@/lib/nav'

/** Mỗi màn hình tải khi cần (lazy) để trang đầu nhẹ. */
const PAGES: Record<string, LazyExoticComponent<ComponentType>> = {
  '/': lazy(() => import('@/features/dashboard/DashboardPage').then((m) => ({ default: m.DashboardPage }))),
  '/admin/users': lazy(() => import('@/features/admin/UsersPage').then((m) => ({ default: m.UsersPage }))),
  '/admin/tracks': lazy(() => import('@/features/admin/TracksPage').then((m) => ({ default: m.TracksPage }))),
  '/results': lazy(() => import('@/features/results/ResultsPage').then((m) => ({ default: m.ResultsPage }))),
  '/scoring': lazy(() =>
    import('@/features/scoring/ScoringRoutes').then((m) => ({ default: m.ScoringRoutes })),
  ),
  '/sessions': lazy(() =>
    import('@/features/sessions/SessionsRoutes').then((m) => ({ default: m.SessionsRoutes })),
  ),
  '/candidates': lazy(() =>
    import('@/features/candidates/CandidatesRoutes').then((m) => ({ default: m.CandidatesRoutes })),
  ),
  '/submissions': lazy(() =>
    import('@/features/submissions/SubmissionsRoutes').then((m) => ({ default: m.SubmissionsRoutes })),
  ),
  '/admin/settings': lazy(() =>
    import('@/features/admin/SettingsPage').then((m) => ({ default: m.SettingsPage })),
  ),
}

function page(to: string) {
  const Page = PAGES[to]
  return (
    <Suspense fallback={<PageLoading />}>
      <Page />
    </Suspense>
  )
}

const featureRoutes: RouteObject[] = NAV.flatMap((g) => g.items)
  .filter((item) => item.to !== '/')
  .map((item) => ({
    path: `${item.to.slice(1)}/*`,
    element: <RequireRole roles={item.roles}>{page(item.to)}</RequireRole>,
  }))

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
                  { index: true, element: page('/') },
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
