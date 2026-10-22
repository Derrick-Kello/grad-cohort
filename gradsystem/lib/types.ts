// Shapes of the API responses the screens use. Generated from the backend DTOs by hand;
// the OpenAPI spec at /v3/api-docs is the source of truth.

export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };

export type Me = {
  id: string;
  email: string;
  number: string;
  firstName: string;
  lastName: string;
  name: string;
  phone: string | null;
  roles: string[];
  studentId: string | null;
  programmeCode: string | null;
  programmeLevel: string | null;
  emailDigest: boolean;
};

export type StudentSummary = {
  id: string;
  userId: string;
  name: string;
  number: string;
  email: string;
  programmeId: string;
  programmeCode: string;
  programmeName: string;
  level: string;
  cohortId: string;
  cohortName: string;
  enrolledOn: string;
  currentSemester: number;
  status: string;
  statusReason: string | null;
  approvedMilestones: number;
  requiredMilestones: number;
  checkpointDueOn: string;
  programmeEndOn: string;
  daysToCheckpoint: number;
  daysToProgrammeEnd: number;
  supervisorId: string | null;
  supervisorName: string | null;
  coSupervisorName: string | null;
  userActive: boolean;
  flaggedAt: string | null;
};

export type Supervision = { id: string; supervisorId: string; supervisorName: string; type: string; startsOn: string; endsOn: string | null };
export type Extension = { id: string; months: number; reason: string; approvedBy: string; approvedOn: string };
export type StudentDetail = { summary: StudentSummary; supervisions: Supervision[]; extensions: Extension[] };

export type PanelMember = { userId: string | null; name: string | null; externalName: string | null; role: string };

export type Attempt = {
  id: string;
  milestoneId: string;
  attemptNo: number;
  scheduledStart: string;
  scheduledEnd: string;
  venueId: string | null;
  venueName: string | null;
  onlineLink: string | null;
  outcome: string | null;
  score: number | null;
  panelRemarks: string | null;
  recordedByName: string | null;
  recordedAt: string | null;
  cancelledReason: string | null;
  cancelledAt: string | null;
  panel: PanelMember[];
  state: string;
};

export type Milestone = {
  id: string;
  studentId: string;
  sequenceNo: number;
  label: string;
  title: string;
  finalPreDefense: boolean;
  evidenceRequired: boolean;
  status: string;
  lockedReason: string | null;
  availableAt: string | null;
  approvedAt: string | null;
  approvedByName: string | null;
  evidenceCount: number;
  failedAttempts: number;
  maxAttempts: number;
  currentAttempt: Attempt | null;
  attempts: Attempt[];
};

export type PresentationRow = {
  attemptId: string;
  milestoneId: string;
  studentId: string;
  studentName: string;
  studentNumber: string;
  programmeCode: string;
  sequenceNo: number;
  milestoneTitle: string;
  finalPreDefense: boolean;
  attemptNo: number;
  scheduledStart: string;
  scheduledEnd: string;
  venueName: string | null;
  onlineLink: string | null;
  outcome: string | null;
  state: string;
  panel: PanelMember[];
};

export type Schedulable = {
  milestoneId: string;
  studentId: string;
  studentName: string;
  studentNumber: string;
  programmeCode: string;
  cohortName: string;
  sequenceNo: number;
  title: string;
  finalPreDefense: boolean;
  nextAttemptNo: number;
  availableAt: string | null;
  daysAvailable: number;
  studentStatus: string;
};

export type VerificationItem = {
  milestoneId: string;
  studentId: string;
  studentName: string;
  studentNumber: string;
  programmeCode: string;
  sequenceNo: number;
  title: string;
  evidenceRequired: boolean;
  status: string;
  outcomeAt: string | null;
  evidenceCount: number;
  daysWaiting: number;
  attemptId: string | null;
  score: number | null;
  panelRemarks: string | null;
};

export type Evidence = {
  id: string;
  milestoneId: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  kind: string;
  fileVersion: number;
  current: boolean;
  supersededBy: string | null;
  scanStatus: string;
  previewable: boolean;
  uploadedAt: string;
  uploadedBy: string | null;
};

export type Comment = {
  id: string;
  targetType: string;
  targetId: string;
  authorId: string;
  authorName: string | null;
  body: string;
  createdAt: string;
  targetLabel: string | null;
  studentName: string | null;
  studentId: string | null;
};

export type Publication = {
  id: string;
  studentId: string;
  studentName: string;
  studentNumber: string;
  programmeCode: string;
  title: string;
  type: string;
  venueName: string | null;
  authors: string;
  year: number | null;
  volume: string | null;
  issue: string | null;
  pages: string | null;
  doi: string | null;
  url: string | null;
  issnIsbn: string | null;
  pubStatus: string;
  hasFile: boolean;
  fileName: string | null;
  reviewStatus: string;
  submittedAt: string | null;
  reviewedAt: string | null;
  reviewedByName: string | null;
  reviewNote: string | null;
  venueVerified: boolean;
  authorshipVerified: boolean;
  indexingChecked: boolean;
  daysWaiting: number;
  duplicateWarnings: string[];
  updatedAt: string;
  version: number;
};

export type Thesis = {
  id: string;
  studentId: string;
  studentName: string;
  studentNumber: string;
  programmeCode: string;
  versionNo: number;
  title: string;
  abstractText: string;
  keywords: string;
  fileName: string;
  fileType: string;
  sizeBytes: number;
  status: string;
  pageCount: number | null;
  similarityPct: number | null;
  aiPct: number | null;
  similarityLimit: number | null;
  aiLimit: number | null;
  screeningManual: boolean;
  hasProviderReport: boolean;
  providerReportUrl: string | null;
  overrideByName: string | null;
  overrideReason: string | null;
  overrideAt: string | null;
  submittedAt: string;
  updatedAt: string;
  formatFailures: number;
  formatSkipped: number;
};

export type ThesisOverview = {
  locked: boolean;
  lockedReason: string | null;
  canSubmit: boolean;
  blockedReason: string | null;
  similarityLimit: number;
  aiLimit: number;
  maxMegabytes: number;
  versions: Thesis[];
};

export type FormatResult = { ruleCode: string; passed: boolean; skipped: boolean; detail: string; location: string | null };
export type FormatReport = { thesisId: string; versionNo: number; status: string; fileType: string; pageCount: number | null; results: FormatResult[] };

export type Person = { id: string; name: string; number: string };
export type Course = {
  id: string;
  code: string;
  title: string;
  semesterId: string;
  semesterLabel: string;
  creditHours: number;
  lecturers: Person[];
  studentCount: number;
  sessionsHeld: number;
};
export type Session = {
  id: string;
  courseId: string;
  heldOn: string;
  startsAt: string;
  endsAt: string;
  topic: string | null;
  attendanceTaken: boolean;
  present: number;
  late: number;
  absent: number;
  excused: number;
  lecturerCanEdit: boolean;
  future: boolean;
};
export type RosterEntry = { studentId: string; name: string; number: string; programmeCode: string; status: string; recorded: boolean };
export type Roster = { session: Session; courseCode: string; courseTitle: string; entries: RosterEntry[]; editable: boolean; editNote: string | null };
export type EligibilityRow = {
  eligibilityId: string | null;
  studentId: string;
  name: string;
  number: string;
  programmeCode: string;
  sessionsHeld: number;
  sessionsCounted: number;
  sessionsAttended: number;
  attendancePct: number | null;
  status: string;
  exceptionReason: string | null;
  canMiss: number;
};
export type StudentAttendance = {
  courseId: string;
  code: string;
  title: string;
  semesterLabel: string;
  sessionsHeld: number;
  sessionsCounted: number;
  sessionsAttended: number;
  attendancePct: number | null;
  status: string;
  exceptionReason: string | null;
  canMiss: number;
  threshold: number;
};

export type Notification = {
  id: string;
  code: string;
  codeLabel: string;
  title: string;
  body: string;
  link: string | null;
  createdAt: string;
  readAt: string | null;
};

export type Programme = {
  id: string;
  code: string;
  name: string;
  level: string;
  durationMonths: number;
  semesters: number;
  requiredMilestones: number;
  checkpointMonths: number;
  checkpointMilestones: number;
  evidenceRequired: boolean;
  firstMilestoneSemester: number;
  requiredPublications: number;
  version: number;
};
export type Template = { id: string; sequenceNo: number; title: string; description: string | null; finalPreDefense: boolean; evidenceRequired: boolean };
export type Semester = { id: string; academicYear: string; number: number; label: string; startsOn: string; endsOn: string; current: boolean };
export type Cohort = { id: string; programmeId: string; programmeCode: string; name: string; intakeSemesterId: string | null; intakeSemesterLabel: string | null };
export type Venue = { id: string; name: string; capacity: number | null; active: boolean };

export type User = {
  id: string;
  email: string;
  number: string;
  firstName: string;
  lastName: string;
  name: string;
  phone: string | null;
  roles: string[];
  active: boolean;
  activated: boolean;
  lockedUntil: string | null;
  lastLoginAt: string | null;
  createdAt: string;
  version: number;
};

export type Setting = { id: string; key: string; value: string; valueType: string; description: string | null; version: number; updatedAt: string };
export type AuditEntry = {
  id: string;
  at: string;
  actorId: string | null;
  actorName: string;
  action: string;
  entityType: string;
  entityId: string | null;
  studentId: string | null;
  oldValue: Record<string, unknown> | null;
  newValue: Record<string, unknown> | null;
  ipAddress: string | null;
};
export type ReportTable = { key: string; title: string; subtitle: string[]; columns: string[]; rows: string[][] };
export type Achievement = { type: string; title: string; detail: string; at: string | null; approvedBy: string | null; badge: string | null };
