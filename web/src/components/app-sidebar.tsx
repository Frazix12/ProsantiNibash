"use client";
import {
  Bell,
  Building2,
  ChartNoAxesCombined,
  CreditCard,
  Files,
  History,
  House,
  LayoutDashboard,
  ReceiptText,
  Repeat2,
  Settings2,
  Users,
  Wrench,
} from "lucide-react";
import type { Section } from "@/lib/types";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "./ui/sidebar";
import { Separator } from "./ui/separator";
export const navigation: { label: string; items: { id: Section; label: string; icon: typeof House }[] }[] = [
  {
    label: "Workspace",
    items: [
      { id: "overview", label: "Overview", icon: LayoutDashboard },
      { id: "flats", label: "Flats", icon: Building2 },
      { id: "tenants", label: "Tenants", icon: Users },
      { id: "payments", label: "Payments", icon: CreditCard },
      { id: "expenses", label: "Expenses", icon: ReceiptText },
      { id: "recurring", label: "Recurring expenses", icon: Repeat2 },
      { id: "maintenance", label: "Maintenance", icon: Wrench },
    ],
  },
  {
    label: "Management",
    items: [
      { id: "documents", label: "Documents", icon: Files },
      { id: "reports", label: "Reports", icon: ChartNoAxesCombined },
      { id: "audit", label: "Audit history", icon: History },
      { id: "reminders", label: "Reminders", icon: Bell },
      { id: "building", label: "Building details", icon: Settings2 },
    ],
  },
];
export function AppSidebar({
  section,
  navigate,
  name,
}: {
  section: Section;
  navigate: (section: Section) => void;
  name: string;
}) {
  const { setOpenMobile } = useSidebar();
  const select = (id: Section) => {
    navigate(id);
    setOpenMobile(false);
  };
  return (
    <Sidebar collapsible="offcanvas" className="dashboard-sidebar">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" onClick={() => select("overview")} aria-label="Prosanti Nibash home">
              <span className="brand-mark">
                <Building2 />
              </span>
              <span className="brand-name">
                Prosanti Nibash<small>Property workspace</small>
              </span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <nav aria-label="Main navigation">
          {navigation.map((group) => (
            <SidebarGroup key={group.label}>
              <SidebarGroupLabel>{group.label}</SidebarGroupLabel>
              <SidebarGroupContent>
                <SidebarMenu>
                  {group.items.map((item) => (
                    <SidebarMenuItem key={item.id}>
                      <SidebarMenuButton
                        isActive={section === item.id}
                        aria-current={section === item.id ? "page" : undefined}
                        onClick={() => select(item.id)}
                      >
                        <item.icon />
                        <span>{item.label}</span>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  ))}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          ))}
        </nav>
      </SidebarContent>
      <SidebarFooter>
        <Separator />
        <div className="sidebar-property">
          <span className="property-icon">
            <House />
          </span>
          <span>
            <strong>{name}</strong>
            <small>Building management</small>
          </span>
        </div>
      </SidebarFooter>
    </Sidebar>
  );
}
