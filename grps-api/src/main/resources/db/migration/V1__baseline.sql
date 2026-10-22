-- GRPS baseline schema (PRD section 22).
-- Every table except join tables, audit_log and shedlock carries the base columns:
-- id, created_at, updated_at, created_by, updated_by, version.

create extension if not exists btree_gist;

-- Identity -------------------------------------------------------------------

create table app_user (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    email varchar(255) not null,
    staff_or_student_no varchar(50) not null,
    first_name varchar(100) not null,
    last_name varchar(100) not null,
    phone varchar(40),
    password_hash varchar(100),
    active boolean not null default true,
    locked_until timestamptz,
    failed_logins int not null default 0,
    first_failed_at timestamptz,
    last_login_at timestamptz,
    email_digest boolean not null default false
);
create unique index ux_app_user_email on app_user (lower(email));
create unique index ux_app_user_number on app_user (lower(staff_or_student_no));

create table role (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    code varchar(30) not null unique
);

create table user_role (
    user_id uuid not null references app_user (id),
    role_id uuid not null references role (id),
    primary key (user_id, role_id)
);

create table refresh_token (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    user_id uuid not null references app_user (id),
    token_hash varchar(100) not null,
    expires_at timestamptz not null,
    revoked_at timestamptz
);
create index ix_refresh_token_hash on refresh_token (token_hash);

-- Password reset and account activation links (single use).
create table action_token (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    user_id uuid not null references app_user (id),
    purpose varchar(20) not null,
    token_hash varchar(100) not null,
    expires_at timestamptz not null,
    used_at timestamptz
);
create index ix_action_token_hash on action_token (token_hash);

-- Programmes and calendar ----------------------------------------------------

create table programme (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    code varchar(30) not null unique,
    name varchar(150) not null,
    level varchar(20) not null,
    duration_months int not null,
    semesters int not null,
    required_milestones int not null,
    checkpoint_months int not null,
    checkpoint_milestones int not null,
    evidence_required boolean not null,
    first_milestone_semester int not null default 2,
    required_publications int not null default 0
);

create table milestone_template (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    programme_id uuid not null references programme (id),
    sequence_no int not null,
    title varchar(200) not null,
    description text,
    is_final_pre_defense boolean not null default false,
    evidence_required boolean not null default false,
    unique (programme_id, sequence_no)
);
create unique index ux_template_one_final on milestone_template (programme_id) where is_final_pre_defense;

create table academic_semester (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    academic_year varchar(20) not null,
    number int not null check (number between 1 and 2),
    starts_on date not null,
    ends_on date not null,
    check (ends_on > starts_on),
    exclude using gist (daterange(starts_on, ends_on, '[]') with &&)
);

create table cohort (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    programme_id uuid not null references programme (id),
    name varchar(100) not null,
    intake_semester_id uuid references academic_semester (id),
    unique (programme_id, name)
);

create table venue (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    name varchar(150) not null unique,
    capacity int,
    active boolean not null default true
);

-- Students and supervision ---------------------------------------------------

create table student (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    user_id uuid not null unique references app_user (id),
    programme_id uuid not null references programme (id),
    cohort_id uuid not null references cohort (id),
    enrolled_on date not null,
    checkpoint_due_on date not null,
    programme_end_on date not null,
    current_semester int not null default 1 check (current_semester between 1 and 4),
    status varchar(30) not null,
    status_reason text,
    flagged_at timestamptz,
    expelled_at timestamptz,
    completed_at timestamptz
);
create index ix_student_status on student (status);

create table supervision (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    student_id uuid not null references student (id),
    supervisor_user_id uuid not null references app_user (id),
    type varchar(10) not null,
    starts_on date not null,
    ends_on date
);
create unique index ux_supervision_one_primary on supervision (student_id) where type = 'PRIMARY' and ends_on is null;
create index ix_supervision_supervisor on supervision (supervisor_user_id) where ends_on is null;

create table extension (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    student_id uuid not null references student (id),
    months int not null check (months > 0),
    reason text not null,
    approved_by varchar(200) not null,
    approved_on date not null
);

-- Milestones and presentations -----------------------------------------------

create table student_milestone (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    student_id uuid not null references student (id),
    template_id uuid not null references milestone_template (id),
    sequence_no int not null,
    title varchar(200) not null,
    is_final_pre_defense boolean not null,
    evidence_required boolean not null,
    status varchar(30) not null,
    available_at timestamptz,
    approved_by uuid references app_user (id),
    approved_at timestamptz,
    unique (student_id, sequence_no)
);
create index ix_student_milestone_status on student_milestone (status);

create table presentation_attempt (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    student_milestone_id uuid not null references student_milestone (id),
    attempt_no int not null,
    scheduled_start timestamptz not null,
    scheduled_end timestamptz not null,
    venue_id uuid references venue (id),
    online_link varchar(500),
    outcome varchar(20),
    score numeric(5, 2),
    panel_remarks text,
    recorded_by uuid references app_user (id),
    recorded_at timestamptz,
    cancelled_reason text,
    cancelled_at timestamptz,
    reminder_48_sent boolean not null default false,
    reminder_24_sent boolean not null default false,
    unique (student_milestone_id, attempt_no)
);
create index ix_attempt_start on presentation_attempt (scheduled_start);

create table panel_member (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    attempt_id uuid not null references presentation_attempt (id) on delete cascade,
    user_id uuid references app_user (id),
    external_name varchar(200),
    role varchar(10) not null,
    check (user_id is not null or external_name is not null)
);

-- Evidence and comments ------------------------------------------------------

create table evidence_file (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    student_milestone_id uuid not null references student_milestone (id),
    storage_key varchar(300) not null,
    file_name varchar(255) not null,
    content_type varchar(150) not null,
    size_bytes bigint not null,
    sha256 varchar(64) not null,
    kind varchar(20) not null,
    file_version int not null default 1,
    superseded_by uuid references evidence_file (id),
    scan_status varchar(20) not null
);
create index ix_evidence_milestone on evidence_file (student_milestone_id);

create table review_comment (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    target_type varchar(20) not null,
    target_id uuid not null,
    author_id uuid not null references app_user (id),
    body text not null
);
create index ix_comment_target on review_comment (target_type, target_id);

-- Publications ---------------------------------------------------------------

create table publication (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    student_id uuid not null references student (id),
    title varchar(500) not null,
    type varchar(30) not null,
    venue_name varchar(300),
    authors varchar(1000) not null,
    year int,
    volume varchar(30),
    issue varchar(30),
    pages varchar(30),
    doi varchar(200),
    url varchar(500),
    issn_isbn varchar(50),
    pub_status varchar(20) not null,
    storage_key varchar(300),
    file_name varchar(255),
    review_status varchar(30) not null,
    submitted_at timestamptz,
    reviewed_by uuid references app_user (id),
    reviewed_at timestamptz,
    review_note text,
    venue_verified boolean not null default false,
    authorship_verified boolean not null default false,
    indexing_checked boolean not null default false
);
create unique index ux_publication_doi_approved on publication (lower(doi)) where review_status = 'APPROVED';
create index ix_publication_review on publication (review_status);

-- Thesis ---------------------------------------------------------------------

create table thesis_submission (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    student_id uuid not null references student (id),
    version_no int not null,
    title varchar(500) not null,
    abstract text not null,
    keywords varchar(500) not null,
    storage_key varchar(300) not null,
    file_name varchar(255) not null,
    file_type varchar(10) not null,
    size_bytes bigint not null,
    status varchar(40) not null,
    page_count int,
    similarity_pct numeric(5, 2),
    ai_pct numeric(5, 2),
    limit_applied_pct numeric(5, 2),
    provider_ref varchar(200),
    provider_report_key varchar(300),
    provider_report_url varchar(500),
    screening_manual boolean not null default false,
    override_by uuid references app_user (id),
    override_reason text,
    override_at timestamptz,
    unique (student_id, version_no)
);
create index ix_thesis_status on thesis_submission (status);

create table format_check_result (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    thesis_submission_id uuid not null references thesis_submission (id),
    rule_code varchar(30) not null,
    passed boolean not null,
    skipped boolean not null default false,
    detail text not null,
    location varchar(500)
);

create table format_rule (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    programme_id uuid not null references programme (id),
    rule_code varchar(30) not null,
    parameters jsonb not null,
    active boolean not null default true,
    unique (programme_id, rule_code)
);

-- Attendance -----------------------------------------------------------------

create table course (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    code varchar(30) not null,
    title varchar(200) not null,
    semester_id uuid not null references academic_semester (id),
    credit_hours int not null default 3,
    unique (code, semester_id)
);

create table course_lecturer (
    course_id uuid not null references course (id),
    lecturer_user_id uuid not null references app_user (id),
    primary key (course_id, lecturer_user_id)
);

create table course_enrolment (
    course_id uuid not null references course (id),
    student_id uuid not null references student (id),
    primary key (course_id, student_id)
);

create table class_session (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    course_id uuid not null references course (id),
    held_on date not null,
    starts_at time not null,
    ends_at time not null,
    topic varchar(300),
    attendance_taken_at timestamptz,
    check (ends_at > starts_at)
);
create index ix_session_course on class_session (course_id, held_on);

create table attendance_record (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    session_id uuid not null references class_session (id),
    student_id uuid not null references student (id),
    status varchar(10) not null,
    marked_by uuid references app_user (id),
    marked_at timestamptz not null,
    unique (session_id, student_id)
);

create table eligibility (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    course_id uuid not null references course (id),
    student_id uuid not null references student (id),
    sessions_held int not null default 0,
    sessions_counted int not null default 0,
    sessions_attended int not null default 0,
    attendance_pct numeric(5, 1),
    status varchar(20) not null,
    exception_reason text,
    warned_below_warning boolean not null default false,
    warned_below_threshold boolean not null default false,
    unique (course_id, student_id)
);

-- Notifications, settings, audit --------------------------------------------

create table notification (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    recipient_id uuid not null references app_user (id),
    code varchar(20) not null,
    title varchar(300) not null,
    body text not null,
    link varchar(500),
    urgent boolean not null default false,
    read_at timestamptz,
    email_status varchar(20) not null,
    email_attempts int not null default 0,
    email_last_error text
);
create index ix_notification_inbox on notification (recipient_id, read_at);
create index ix_notification_email on notification (email_status);

-- One row per deadline warning sent, so the nightly job stays idempotent.
create table deadline_notice (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    student_id uuid not null references student (id),
    notice_key varchar(60) not null,
    unique (student_id, notice_key)
);

create table progress_statement (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    student_id uuid not null references student (id),
    verification_code varchar(20) not null unique,
    approved_items int not null
);

create table system_setting (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    key varchar(100) not null unique,
    value varchar(500) not null,
    value_type varchar(20) not null,
    description varchar(500)
);

create table audit_log (
    id uuid primary key,
    actor_id uuid,
    action varchar(60) not null,
    entity_type varchar(60) not null,
    entity_id uuid,
    student_id uuid,
    old_value jsonb,
    new_value jsonb,
    ip_address varchar(64),
    at timestamptz not null
);
create index ix_audit_entity on audit_log (entity_type, entity_id);
create index ix_audit_student on audit_log (student_id);
create index ix_audit_at on audit_log (at);

-- FR-AUD-01: the audit trail cannot be edited or deleted by any role.
create or replace function audit_log_append_only() returns trigger as $$
begin
    raise exception 'audit_log is append-only';
end;
$$ language plpgsql;

create trigger trg_audit_log_append_only
    before update or delete on audit_log
    for each row execute function audit_log_append_only();

create table shedlock (
    name varchar(64) primary key,
    lock_until timestamp not null,
    locked_at timestamp not null,
    locked_by varchar(255) not null
);
