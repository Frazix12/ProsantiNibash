"use client";
import type { ComponentProps, ReactNode } from "react";
import {
  ArrowDownToLine,
  ArrowRight,
  Bell,
  Building2,
  Check,
  CreditCard,
  FileText,
  History,
  House,
  LayoutDashboard,
  Plus,
  ReceiptText,
  RefreshCw,
  Repeat2,
  Search,
  Users,
  Wrench,
  X,
} from "lucide-react";
import { money } from "@/lib/money";
import { cn } from "@/lib/utils";
import { Badge as StatusBadge } from "./ui/badge";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "./ui/card";
import { Dialog as Modal, DialogContent, DialogHeader, DialogTitle } from "./ui/dialog";
import { Empty as EmptyState, EmptyHeader, EmptyTitle, EmptyDescription, EmptyMedia, EmptyContent } from "./ui/empty";
import { Table as DataTable, TableBody, TableHead, TableHeader, TableRow } from "./ui/table";
const icons = {
  overview: LayoutDashboard,
  flats: Building2,
  tenants: Users,
  payments: CreditCard,
  expenses: ReceiptText,
  recurring: Repeat2,
  maintenance: Wrench,
  documents: FileText,
  reports: History,
  audit: History,
  reminders: Bell,
  building: House,
  plus: Plus,
  search: Search,
  arrow: ArrowRight,
  refresh: RefreshCw,
  close: X,
  download: ArrowDownToLine,
  check: Check,
};
export function Icon({ name, ...props }: ComponentProps<"svg"> & { name: string }) {
  const Component = icons[name as keyof typeof icons] ?? FileText;
  return <Component aria-hidden="true" data-icon="inline-start" {...props} />;
}
export function Badge({ children }: { children: ReactNode }) {
  const text = String(children);
  const warning = ["Overdue", "Due", "Voided"].includes(text);
  const positive = ["Paid", "active", "Active"].includes(text);
  return <StatusBadge variant={warning ? "destructive" : positive ? "default" : "secondary"}>{children}</StatusBadge>;
}
export function Empty({
  title = "Nothing here yet",
  children,
  action,
}: {
  title?: string;
  children?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <EmptyState>
      <EmptyHeader>
        <EmptyMedia variant="icon">
          <FileText />
        </EmptyMedia>
        <EmptyTitle>{title}</EmptyTitle>
        {children && <EmptyDescription>{children}</EmptyDescription>}
      </EmptyHeader>
      {action && <EmptyContent>{action}</EmptyContent>}
    </EmptyState>
  );
}
export function Panel({
  title,
  children,
  action,
  className,
}: {
  title?: string;
  children: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <Card className={cn("panel", className)}>
      {title && (
        <CardHeader>
          <CardTitle>{title}</CardTitle>
          {action && <CardAction>{action}</CardAction>}
        </CardHeader>
      )}
      <CardContent>{children}</CardContent>
    </Card>
  );
}
export function MoneyStat({ label, value, accent = false }: { label: string; value: bigint; accent?: boolean }) {
  return (
    <Card className={cn("stat", accent && "stat-accent")}>
      <CardHeader>
        <CardTitle>{label}</CardTitle>
      </CardHeader>
      <CardContent>
        <strong>{money(value)}</strong>
      </CardContent>
    </Card>
  );
}
export function Dialog({
  title,
  children,
  onClose,
  wide = false,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  wide?: boolean;
}) {
  return (
    <Modal
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent className={cn("management-dialog", wide && "sm:max-w-3xl")}>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        {children}
      </DialogContent>
    </Modal>
  );
}
export function Table({ headers, children }: { headers: string[]; children: ReactNode }) {
  return (
    <DataTable>
      <TableHeader>
        <TableRow>
          {headers.map((h, i) => (
            <TableHead key={i} scope="col">
              {h}
            </TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>{children}</TableBody>
    </DataTable>
  );
}
