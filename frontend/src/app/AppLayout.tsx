import { UserButton } from '@clerk/react'
import { useEffect } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { useMe } from '@/auth/useMe'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarTrigger,
} from '@/components/ui/sidebar'
import { NAV, navFor } from '@/lib/nav'
import { ROLE_LABELS } from '@/lib/roles'

export function isActivePath(pathname: string, to: string): boolean {
  return to === '/' ? pathname === '/' : pathname === to || pathname.startsWith(`${to}/`)
}

/** Tên trang hiện tại cho header, lấy từ menu. */
function currentTitle(pathname: string): string {
  const items = NAV.flatMap((g) => g.items).toSorted((a, b) => b.to.length - a.to.length)
  return items.find((i) => isActivePath(pathname, i.to))?.label ?? ''
}

export function AppLayout() {
  const me = useMe()
  const { pathname } = useLocation()
  const groups = navFor(me.role)
  const title = currentTitle(pathname)

  // Tiêu đề tab trình duyệt theo trang (dễ phân biệt khi mở nhiều tab, trình đọc màn hình đọc được)
  useEffect(() => {
    document.title = title ? `${title} · Tuyển dụng DX Lab` : 'Tuyển dụng DX Lab'
  }, [title])

  return (
    <SidebarProvider>
      <Sidebar collapsible="icon">
        <SidebarHeader>
          <div className="flex items-center gap-2 px-2 py-1.5">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-md bg-primary text-sm font-semibold text-primary-foreground">
              DX
            </div>
            <div className="grid text-sm leading-tight group-data-[collapsible=icon]:hidden">
              <span className="font-semibold">DX Lab</span>
              <span className="text-xs text-muted-foreground">Tuyển dụng thực tập sinh</span>
            </div>
          </div>
        </SidebarHeader>
        <SidebarContent>
          {groups.map((group) => (
            <SidebarGroup key={group.label}>
              <SidebarGroupLabel>{group.label}</SidebarGroupLabel>
              <SidebarGroupContent>
                <SidebarMenu>
                  {group.items.map((item) => (
                    <SidebarMenuItem key={item.to}>
                      <SidebarMenuButton
                        isActive={isActivePath(pathname, item.to)}
                        tooltip={item.label}
                        render={<NavLink to={item.to} end={item.to === '/'} />}
                      >
                        <item.icon aria-hidden />
                        <span>{item.label}</span>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  ))}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          ))}
        </SidebarContent>
        <SidebarFooter>
          <div className="grid gap-0.5 px-2 py-1.5 text-xs group-data-[collapsible=icon]:hidden">
            <span className="truncate font-medium">{me.fullName}</span>
            <span className="truncate text-muted-foreground">{me.email}</span>
          </div>
        </SidebarFooter>
      </Sidebar>
      <SidebarInset>
        <header className="sticky top-0 z-10 flex h-14 items-center gap-2 border-b bg-background px-3 sm:px-4">
          <SidebarTrigger aria-label="Mở hoặc thu gọn menu" />
          <Separator orientation="vertical" className="mx-1 h-5" />
          <h1 className="truncate text-sm font-semibold sm:text-base">{title}</h1>
          <div className="ml-auto flex items-center gap-2">
            <Badge variant="secondary" className="hidden sm:inline-flex">
              {ROLE_LABELS[me.role]}
              {me.role === 'LEAD' && me.leadTrackCodes.length > 0 && ` · ${me.leadTrackCodes.join(', ')}`}
            </Badge>
            <UserButton />
          </div>
        </header>
        <div className="flex-1 p-3 sm:p-6">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
