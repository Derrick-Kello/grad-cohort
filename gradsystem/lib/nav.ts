import {
  Award,
  Bell,
  BookCheck,
  BookOpen,
  CalendarCheck,
  CalendarDays,
  ChartColumn,
  ClipboardCheck,
  ClipboardList,
  FileText,
  FileUp,
  GraduationCap,
  Layers,
  LayoutDashboard,
  Library,
  ListChecks,
  MessageSquare,
  Presentation,
  ScrollText,
  Settings,
  ShieldAlert,
  User,
  UserCog,
  Users,
  type LucideIcon,
} from "lucide-react";

export type View = "admin" | "secretary" | "librarian" | "lecturer" | "supervisor" | "student";

export type NavItem = { href: string; label: string; icon: LucideIcon; exact?: boolean; lockKey?: "thesis" };

export const VIEW_LABEL: Record<View, string> = {
  admin: "Admin",
  secretary: "Secretary",
  librarian: "Librarian",
  lecturer: "Lecturer",
  supervisor: "Supervisor",
  student: "Student",
};

/** Roles map to views; Super Admin uses the Admin view. Order sets the default view. */
const ROLE_VIEW: [string, View][] = [
  ["SUPER_ADMIN", "admin"],
  ["ADMIN", "admin"],
  ["SECRETARY", "secretary"],
  ["LIBRARIAN", "librarian"],
  ["LECTURER", "lecturer"],
  ["SUPERVISOR", "supervisor"],
  ["STUDENT", "student"],
];

export function viewsFor(roles: string[]): View[] {
  const out: View[] = [];
  for (const [role, view] of ROLE_VIEW) if (roles.includes(role) && !out.includes(view)) out.push(view);
  return out;
}

export function viewForPath(pathname: string): View | null {
  const first = pathname.split("/")[1];
  return (["admin", "secretary", "librarian", "lecturer", "supervisor", "student"] as View[]).find((v) => v === first) ?? null;
}

// Sidebar items per role (PRD section 18).
export const NAV: Record<View, NavItem[]> = {
  student: [
    { href: "/student", label: "Dashboard", icon: LayoutDashboard, exact: true },
    { href: "/student/presentations", label: "My Presentations", icon: Presentation },
    { href: "/student/evidence", label: "Evidence", icon: FileUp },
    { href: "/student/publications", label: "Publications", icon: BookOpen },
    { href: "/student/thesis", label: "Thesis", icon: GraduationCap, lockKey: "thesis" },
    { href: "/student/attendance", label: "Attendance", icon: CalendarCheck },
    { href: "/student/achievements", label: "Achievements", icon: Award },
    { href: "/notifications", label: "Notifications", icon: Bell },
    { href: "/profile", label: "Profile", icon: User },
  ],
  supervisor: [
    { href: "/supervisor", label: "Dashboard", icon: LayoutDashboard, exact: true },
    { href: "/supervisor/students", label: "My Students", icon: Users },
    { href: "/supervisor/comments", label: "Comments", icon: MessageSquare },
    { href: "/notifications", label: "Notifications", icon: Bell },
  ],
  lecturer: [
    { href: "/lecturer", label: "Dashboard", icon: LayoutDashboard, exact: true },
    { href: "/lecturer/courses", label: "My Courses", icon: BookOpen },
    { href: "/lecturer/sessions", label: "Sessions", icon: CalendarDays },
    { href: "/lecturer/attendance", label: "Attendance", icon: ClipboardCheck },
    { href: "/lecturer/eligibility", label: "Eligibility Lists", icon: ListChecks },
    { href: "/notifications", label: "Notifications", icon: Bell },
  ],
  secretary: [
    { href: "/secretary", label: "Dashboard", icon: LayoutDashboard, exact: true },
    { href: "/secretary/calendar", label: "Presentation Calendar", icon: CalendarDays },
    { href: "/secretary/outcomes", label: "Record Outcomes", icon: ClipboardCheck },
    { href: "/secretary/progress", label: "Student Progress", icon: Users },
    { href: "/notifications", label: "Notifications", icon: Bell },
  ],
  admin: [
    { href: "/admin", label: "Dashboard", icon: LayoutDashboard, exact: true },
    { href: "/admin/students", label: "Students", icon: GraduationCap },
    { href: "/admin/staff", label: "Staff", icon: UserCog },
    { href: "/admin/programmes", label: "Programmes", icon: Layers },
    { href: "/admin/scheduling", label: "Scheduling", icon: CalendarDays },
    { href: "/admin/verification", label: "Verification Queue", icon: ClipboardList },
    { href: "/admin/publications", label: "Publications", icon: BookCheck },
    { href: "/admin/theses", label: "Theses", icon: FileText },
    { href: "/admin/courses", label: "Courses", icon: BookOpen },
    { href: "/admin/at-risk", label: "At-Risk & Expulsion", icon: ShieldAlert },
    { href: "/admin/reports", label: "Reports", icon: ChartColumn },
    { href: "/admin/settings", label: "Settings", icon: Settings },
    { href: "/admin/audit", label: "Audit Log", icon: ScrollText },
  ],
  librarian: [
    { href: "/librarian", label: "Dashboard", icon: LayoutDashboard, exact: true },
    { href: "/librarian/queue", label: "Review Queue", icon: Library },
    { href: "/librarian/approved", label: "Approved Publications", icon: BookCheck },
    { href: "/notifications", label: "Notifications", icon: Bell },
  ],
};

export function isActive(item: NavItem, pathname: string): boolean {
  if (item.exact) return pathname === item.href;
  return pathname === item.href || pathname.startsWith(item.href + "/");
}

/** Page title from the active nav item, for the top bar. */
export function titleFor(pathname: string, view: View | null): string {
  const items = view ? NAV[view] : [];
  const match = [...items].sort((a, b) => b.href.length - a.href.length).find((i) => isActive(i, pathname) || pathname.startsWith(i.href + "/"));
  if (match) return match.label;
  if (pathname.startsWith("/notifications")) return "Notifications";
  if (pathname.startsWith("/profile")) return "Profile";
  return "GRPS";
}
