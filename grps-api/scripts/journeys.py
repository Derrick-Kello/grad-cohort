"""Runs the PRD's critical journeys (section 25) against a running local API."""
import datetime as dt
import json
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = "http://localhost:8080/api/v1"
FILES = sys.argv[1]
PASSWORD = "Graduate2026"
results = []


def call(method, path, token=None, body=None, files=None, fields=None, expect=None):
    headers = {}
    data = None
    if files is not None or fields is not None:
        boundary = uuid.uuid4().hex
        chunks = []
        for k, v in (fields or {}).items():
            chunks.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode())
        for k, (name, content) in (files or {}).items():
            chunks.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"; filename=\"{name}\"\r\n"
                          f"Content-Type: application/octet-stream\r\n\r\n".encode() + content + b"\r\n")
        chunks.append(f"--{boundary}--\r\n".encode())
        data = b"".join(chunks)
        headers["Content-Type"] = f"multipart/form-data; boundary={boundary}"
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            status, raw = r.status, r.read()
    except urllib.error.HTTPError as e:
        status, raw = e.code, e.read()
    try:
        payload = json.loads(raw) if raw else None
    except ValueError:
        payload = raw
    if expect is not None and status != expect:
        raise AssertionError(f"{method} {path} -> {status} (expected {expect}): {payload}")
    return status, payload


def login(who):
    return call("POST", "/auth/login", body={"login": who, "password": PASSWORD}, expect=200)[1]["accessToken"]


def check(name, cond, detail=""):
    results.append((name, bool(cond), detail))
    print(("PASS " if cond else "FAIL ") + name + (f"  [{detail}]" if detail and not cond else ""))


def iso(d):
    return d.strftime("%Y-%m-%dT%H:%M:%SZ")


def set_clock(admin, days):
    call("POST", "/dev/clock", admin, {"offsetDays": days}, expect=200)


def wait_status(token, path, statuses, timeout=20):
    end = time.time() + timeout
    last = None
    while time.time() < end:
        last = call("GET", path, token)[1]
        st = last["versions"][0]["status"] if isinstance(last, dict) and "versions" in last else last.get("status")
        if st in statuses:
            return last
        time.sleep(0.5)
    return last


admin = login("admin@grps.local")
secretary = login("secretary@grps.local")
librarian = login("librarian@grps.local")
lecturer = login("lecturer@grps.local")
set_clock(admin, 0)

programmes = call("GET", "/reference/programmes", admin, expect=200)[1]
masters = next(p for p in programmes if p["code"] == "MASTERS")
cohort = call("GET", f"/reference/cohorts?programmeId={masters['id']}", admin, expect=200)[1][0]
staff = call("GET", "/admin/users?role=SUPERVISOR&size=50", admin, expect=200)[1]["content"]
supervisor = next(u for u in staff if u["email"] == "lecturer@grps.local")
venue = call("GET", "/reference/venues", admin, expect=200)[1][0]
today = dt.date.today()
suffix = uuid.uuid4().hex[:6]

# Journey 1: enrol a Masters student; milestones 1-5 are created LOCKED.
st, enrolled = call("POST", "/admin/students", admin, {
    "email": f"j1-{suffix}@grps.local", "number": f"J1-{suffix}", "firstName": "Journey", "lastName": "One",
    "programmeId": masters["id"], "cohortId": cohort["id"], "enrolledOn": str(today),
    "supervisorUserId": supervisor["id"]})
check("J1 enrol returns 201", st == 201, enrolled)
ms = call("GET", f"/students/{enrolled['id']}/milestones", admin, expect=200)[1]
check("J1 five milestones, all LOCKED", len(ms) == 5 and all(m["status"] == "LOCKED" for m in ms), [m["status"] for m in ms])
st, body = call("POST", "/admin/students", admin, {
    "email": f"nosup-{suffix}@grps.local", "number": f"NS-{suffix}", "firstName": "No", "lastName": "Supervisor",
    "programmeId": masters["id"], "cohortId": cohort["id"], "enrolledOn": str(today)})
check("J1 enrol without supervisor is refused (422 SUPERVISOR_REQUIRED)", st == 422 and body["code"] == "SUPERVISOR_REQUIRED", body)

# A second student enrolled 7 months ago, so milestone 1 opens at once.
st, s2 = call("POST", "/admin/students", admin, {
    "email": f"j3-{suffix}@grps.local", "number": f"J3-{suffix}", "firstName": "Journey", "lastName": "Three",
    "programmeId": masters["id"], "cohortId": cohort["id"], "enrolledOn": str(today - dt.timedelta(days=215)),
    "supervisorUserId": supervisor["id"]}, expect=201)
ms = call("GET", f"/students/{s2['id']}/milestones", admin, expect=200)[1]
check("Milestone 1 AVAILABLE from semester 2", ms[0]["status"] == "AVAILABLE", ms[0]["status"])

# Journey 2: scheduling milestone 2 before 1 is approved is refused with MILESTONE_LOCKED.
tomorrow = dt.datetime.now(dt.timezone.utc).replace(tzinfo=None).replace(hour=9, minute=0, second=0, microsecond=0) + dt.timedelta(days=1)
st, body = call("POST", f"/admin/milestones/{ms[1]['id']}/schedule", admin,
                {"start": iso(tomorrow), "durationMinutes": 45, "venueId": venue["id"], "panel": []})
check("J2 schedule milestone 2 first -> 409 MILESTONE_LOCKED", st == 409 and body["code"] == "MILESTONE_LOCKED", body)

# Journey 3: scheduled -> FAILED -> rescheduled -> PASSED -> evidence -> returned -> re-uploaded -> approved.
m1 = ms[0]["id"]
st, sched = call("POST", f"/admin/milestones/{m1}/schedule", admin,
                 {"start": iso(tomorrow), "durationMinutes": 45, "venueId": venue["id"],
                  "panel": [{"userId": supervisor["id"], "role": "CHAIR"}, {"externalName": "Prof. A. Mensah", "role": "MEMBER"}]})
check("J3 schedule milestone 1 (201)", st == 201, sched)
att = sched["attempt"]["id"]
st, body = call("POST", f"/secretary/attempts/{att}/outcome", secretary, {"outcome": "FAILED", "remarks": "Too early"})
check("J3 outcome before the date is refused", st == 422, body)
set_clock(admin, 2)
st, body = call("POST", f"/secretary/attempts/{att}/outcome", secretary, {"outcome": "FAILED"})
check("J3 FAILED without remarks is refused", st == 400, body)
call("POST", f"/secretary/attempts/{att}/outcome", secretary, {"outcome": "FAILED", "remarks": "Objectives unclear"}, expect=200)
ms = call("GET", f"/students/{s2['id']}/milestones", admin, expect=200)[1]
check("J3 failed milestone returns to AVAILABLE with attempt kept", ms[0]["status"] == "AVAILABLE" and len(ms[0]["attempts"]) == 1)
again = dt.datetime.now(dt.timezone.utc).replace(tzinfo=None).replace(hour=9, minute=0, second=0, microsecond=0) + dt.timedelta(days=4)
st, sched2 = call("POST", f"/admin/milestones/{m1}/schedule", admin, {"start": iso(again), "venueId": venue["id"]})
check("J3 second attempt scheduled", st == 201 and sched2["attempt"]["attemptNo"] == 2, sched2)
set_clock(admin, 5)
call("POST", f"/secretary/attempts/{sched2['attempt']['id']}/outcome", secretary, {"outcome": "PASSED", "score": 72}, expect=200)
st, body = call("POST", f"/admin/milestones/{m1}/approve", admin)
check("J3 approve without evidence -> 422 EVIDENCE_REQUIRED", st == 422 and body["code"] == "EVIDENCE_REQUIRED", body)
call("POST", "/dev/clock", admin, {"offsetDays": 5})
student2 = call("POST", "/auth/login", body={"login": f"J3-{suffix}", "password": PASSWORD})[1]
# The student has no password yet (activation link); set one through the activation flow is email-based, so use admin
# reset: generate via forgot-password is email-only. Instead, check access rules with the seeded student below.
seeded_student = login("student@grps.local")
st, body = call("POST", f"/milestones/{m1}/evidence", seeded_student, files={"file": ("x.pdf", open(f"{FILES}/evidence.pdf", "rb").read())})
check("Another student uploading to this milestone gets 404", st == 404, st)
st, body = call("GET", f"/milestones/{m1}/evidence", seeded_student)
check("Another student listing this milestone's evidence gets 404 (SEC-05)", st == 404, st)
st, body = call("GET", "/admin/verification-queue", seeded_student)
check("Student calling an admin endpoint gets 403", st == 403, st)
set_clock(admin, 0)

# Journey 3 continued with the seeded student whose Presentation 3 passed and awaits evidence.
ama_ms = call("GET", "/students/me/milestones", seeded_student, expect=200)[1]
p3 = next(m for m in ama_ms if m["sequenceNo"] == 3)
st, ev = call("POST", f"/milestones/{p3['id']}/evidence?kind=PANEL_SHEET", seeded_student,
              files={"file": ("panel-sheet.pdf", open(f"{FILES}/evidence.pdf", "rb").read())})
check("Evidence upload (201)", st == 201, ev)
st, body = call("POST", f"/milestones/{p3['id']}/evidence", seeded_student, files={"file": ("fake.pdf", b"MZ not a pdf really....")})
check("Upload with wrong content type is refused", st == 422, body)
st, body = call("POST", f"/admin/milestones/{p3['id']}/return", admin, {"comment": "Panel sheet is unsigned"})
check("Admin returns milestone", st == 200 and body["status"] == "RETURNED", body)
st, ev2 = call("POST", f"/milestones/{p3['id']}/evidence?kind=PANEL_SHEET&replaces={ev['id']}", seeded_student,
               files={"file": ("panel-sheet-signed.pdf", open(f"{FILES}/evidence.pdf", "rb").read())})
check("Re-upload replaces file as version 2 and resubmits", st == 201 and ev2["fileVersion"] == 2, ev2)
st, body = call("POST", f"/admin/milestones/{p3['id']}/approve", admin)
check("Admin approves milestone", st == 200 and body["status"] == "APPROVED", body)
ama_ms = call("GET", "/students/me/milestones", seeded_student, expect=200)[1]
check("Next milestone becomes AVAILABLE", ama_ms[3]["status"] == "AVAILABLE", ama_ms[3]["status"])
st, link = call("GET", f"/evidence/{ev2['id']}/download?inline=true", seeded_student)
check("Signed download link issued", st == 200 and "/api/v1/files/" in link["url"], link)
with urllib.request.urlopen(link["url"]) as r:
    check("Signed link serves the PDF", r.read(4) == b"%PDF")

# Journey 4: publication -> changes requested -> resubmitted -> approved -> in achievements.
st, pub = call("POST", "/publications", seeded_student, {"title": "Journey four paper on irrigation scheduling " + suffix,
               "type": "JOURNAL_ARTICLE", "venueName": "Journal of Water", "authors": "Ama Serwaa, Y. Owusu", "year": 2026,
               "doi": f"10.1234/j4.{suffix}", "pubStatus": "PUBLISHED"})
check("J4 draft created", st == 201 and pub["reviewStatus"] == "DRAFT", pub)
st, body = call("POST", f"/publications/{pub['id']}/submit", seeded_student)
check("J4 submit without PDF refused", st == 400, body)
call("POST", f"/publications/{pub['id']}/file", seeded_student, files={"file": ("paper.pdf", open(f"{FILES}/paper.pdf", "rb").read())}, expect=200)
call("POST", f"/publications/{pub['id']}/submit", seeded_student, expect=200)
queue = call("GET", "/librarian/publications/queue", librarian, expect=200)[1]
check("J4 in librarian queue", any(q["id"] == pub["id"] for q in queue))
call("POST", f"/librarian/publications/{pub['id']}/request-changes", librarian, {"reason": "Add volume and pages"}, expect=200)
call("PUT", f"/publications/{pub['id']}", seeded_student, {"title": pub["title"], "type": "JOURNAL_ARTICLE", "venueName": "Journal of Water",
     "authors": "Ama Serwaa, Y. Owusu", "year": 2026, "volume": "12", "pages": "44-60", "doi": pub["doi"], "pubStatus": "PUBLISHED"}, expect=200)
call("POST", f"/publications/{pub['id']}/submit", seeded_student, expect=200)
call("PUT", f"/librarian/publications/{pub['id']}/checklist", librarian, {"venueVerified": True, "authorshipVerified": True, "indexingChecked": True}, expect=200)
st, body = call("POST", f"/librarian/publications/{pub['id']}/approve", librarian, {})
check("J4 librarian approves", st == 200 and body["reviewStatus"] == "APPROVED", body)
ach = call("GET", "/students/me/achievements", seeded_student, expect=200)[1]
check("J4 publication appears in achievements", any(a["title"] == pub["title"] for a in ach))
st, body = call("PUT", f"/publications/{pub['id']}", seeded_student, {"title": "x", "type": "OTHER", "authors": "x", "pubStatus": "ACCEPTED"})
check("Approved publication is read-only", st == 409, body)

# Journey 5: final pre-defense approved -> thesis format fails -> passes -> screening 23% rejected -> 14% accepted.
adwoa = login("adwoa.nyarko@grps.local")
ov = call("GET", "/thesis/me", adwoa, expect=200)[1]
check("J5 thesis unlocked after final pre-defense", not ov["locked"] and ov["canSubmit"], ov)
st, body = call("POST", "/thesis", seeded_student, fields={"title": "t", "abstract": "a", "keywords": "k"},
                files={"file": ("t.docx", open(f"{FILES}/thesis-good.docx", "rb").read())})
check("Thesis locked before final pre-defense -> 409 THESIS_LOCKED", st == 409 and body["code"] == "THESIS_LOCKED", body)
fields = {"title": "Sensor networks for rain-fed agriculture", "abstract": "We study sensors.", "keywords": "sensors, irrigation"}
call("POST", "/thesis", adwoa, fields=fields, files={"file": ("thesis.docx", open(f"{FILES}/thesis-bad.docx", "rb").read())}, expect=201)
ov = wait_status(adwoa, "/thesis/me", {"FORMAT_FAILED", "FORMAT_PASSED", "SCREENING_PASSED", "ACCEPTED_FOR_EXAMINATION"})
v1 = ov["versions"][0]
check("J5 bad thesis FORMAT_FAILED", v1["status"] == "FORMAT_FAILED", v1["status"])
rep = call("GET", f"/thesis/{v1['id']}/format-report", adwoa, expect=200)[1]
failed_rules = [r["ruleCode"] for r in rep["results"] if not r["passed"] and not r["skipped"]]
check("J5 format report lists failing rules with detail", "FMT-MARGIN" in failed_rules and "FMT-FONT" in failed_rules, failed_rules)
print("     margin detail:", next(r for r in rep["results"] if r["ruleCode"] == "FMT-MARGIN"))
call("POST", "/dev/screening/next", admin, {"similarityPct": 23, "aiPct": 5}, expect=200)
call("POST", "/thesis", adwoa, fields=fields, files={"file": ("thesis-v2.docx", open(f"{FILES}/thesis-good.docx", "rb").read())}, expect=201)
ov = wait_status(adwoa, "/thesis/me", {"FORMAT_FAILED", "SCREENING_REJECTED", "ACCEPTED_FOR_EXAMINATION"})
v2 = ov["versions"][0]
rep2 = call("GET", f"/thesis/{v2['id']}/format-report", adwoa, expect=200)[1]
check("J5 corrected thesis passes format and is rejected at 23%", v2["status"] == "SCREENING_REJECTED",
      (v2["status"], [(r["ruleCode"], r["detail"]) for r in rep2["results"] if not r["passed"]]))
call("POST", "/dev/screening/next", admin, {"similarityPct": 14, "aiPct": 6}, expect=200)
call("POST", "/thesis", adwoa, fields=fields, files={"file": ("thesis-v3.docx", open(f"{FILES}/thesis-good.docx", "rb").read())}, expect=201)
ov = wait_status(adwoa, "/thesis/me", {"FORMAT_FAILED", "SCREENING_REJECTED", "ACCEPTED_FOR_EXAMINATION"})
check("J5 revised thesis at 14% ACCEPTED_FOR_EXAMINATION", ov["versions"][0]["status"] == "ACCEPTED_FOR_EXAMINATION", ov["versions"][0]["status"])
me = call("GET", "/students/me/dashboard", adwoa, expect=200)[1]
check("Student with every milestone and accepted thesis is COMPLETED", me["student"]["status"] == "COMPLETED", me["student"]["status"])

# Journey 6: 10 sessions, a student attending 6 shows 60% and NOT_ELIGIBLE.
courses = call("GET", "/lecturer/courses", lecturer, expect=200)[1]
gr701 = next(c for c in courses if c["code"] == "GRS 701")
rows = call("GET", f"/lecturer/courses/{gr701['id']}/eligibility", lecturer, expect=200)[1]
akua = next(r for r in rows if r["name"] == "Akua Agyeman")
check("J6 10 sessions, 6 attended -> 60% NOT_ELIGIBLE", akua["sessionsCounted"] == 10 and float(akua["attendancePct"]) == 60.0
      and akua["status"] == "NOT_ELIGIBLE", akua)
req = urllib.request.Request(BASE + f"/lecturer/courses/{gr701['id']}/eligibility?format=pdf", headers={"Authorization": "Bearer " + lecturer})
with urllib.request.urlopen(req) as r:
    check("J6 eligibility list exports as PDF", r.read(4) == b"%PDF")
st, sess = call("POST", f"/lecturer/courses/{gr701['id']}/sessions", lecturer,
                {"heldOn": str(today), "startsAt": "00:00", "endsAt": "00:30", "topic": "Smoke test"})
check("Lecturer creates a session", st == 201, sess)
roster = call("GET", f"/lecturer/sessions/{sess['id']}/attendance", lecturer, expect=200)[1]
check("Roster defaults to ABSENT", all(e["status"] == "ABSENT" for e in roster["entries"]))
entries = [{"studentId": e["studentId"], "status": "PRESENT"} for e in roster["entries"]]
st, body = call("PUT", f"/lecturer/sessions/{sess['id']}/attendance", lecturer, {"entries": entries})
check("Mark all present saves in one request", st == 200 and all(e["status"] == "PRESENT" for e in body["entries"]), body)
future = call("POST", f"/lecturer/courses/{gr701['id']}/sessions", lecturer,
              {"heldOn": str(today + dt.timedelta(days=3)), "startsAt": "09:00", "endsAt": "10:00"})[1]
st, body = call("PUT", f"/lecturer/sessions/{future['id']}/attendance", lecturer, {"entries": entries})
check("Future sessions cannot have attendance marked", st == 422, body)

# Journey 7: month 18 with < 4 approved -> AT_RISK; month 24 with milestones outstanding -> FLAGGED -> EXPELLED.
st, j7 = call("POST", "/admin/students", admin, {
    "email": f"j7-{suffix}@grps.local", "number": f"J7-{suffix}", "firstName": "Journey", "lastName": "Seven",
    "programmeId": masters["id"], "cohortId": cohort["id"], "enrolledOn": str(today), "supervisorUserId": supervisor["id"]}, expect=201)
set_clock(admin, 18 * 31)
call("POST", "/dev/jobs/deadline-evaluation", admin, expect=200)
detail = call("GET", f"/admin/students/{j7['id']}", admin, expect=200)[1]
check("J7 month 18 with 0 approved -> AT_RISK", detail["summary"]["status"] == "AT_RISK", detail["summary"]["status"])
set_clock(admin, 24 * 31)
call("POST", "/dev/jobs/deadline-evaluation", admin, expect=200)
detail = call("GET", f"/admin/students/{j7['id']}", admin, expect=200)[1]
check("J7 month 24 -> FLAGGED_FOR_EXPULSION", detail["summary"]["status"] == "FLAGGED_FOR_EXPULSION", detail["summary"]["status"])
queue = call("GET", "/admin/expulsion-queue", admin, expect=200)[1]
check("J7 student in expulsion queue with milestones", any(c["student"]["id"] == j7["id"] and len(c["milestones"]) == 5 for c in queue))
st, body = call("POST", f"/admin/students/{j7['id']}/expulsion/confirm", admin, {"reason": ""})
check("J7 confirm without reason refused", st == 400, body)
st, body = call("POST", f"/admin/students/{j7['id']}/expulsion/confirm", admin, {"reason": "No milestones completed by programme end."})
check("J7 admin confirms -> EXPELLED", st == 200 and body["summary"]["status"] == "EXPELLED", body)
ms7 = call("GET", f"/students/{j7['id']}/milestones", admin, expect=200)[1]
st, body = call("POST", f"/admin/milestones/{ms7[0]['id']}/schedule", admin, {"start": iso(dt.datetime.now(dt.timezone.utc).replace(tzinfo=None) + dt.timedelta(days=900)), "venueId": venue["id"]})
check("Expelled students cannot be scheduled (BR-20)", st == 409, body)
set_clock(admin, 0)

# Journey 8: a lecturer with the Supervisor role sees supervisees.
me = call("GET", "/me", lecturer, expect=200)[1]
sups = call("GET", "/supervisor/students", lecturer, expect=200)[1]
check("J8 lecturer holds both roles and sees supervisees", {"LECTURER", "SUPERVISOR"} <= set(me["roles"]) and len(sups) >= 3, (me["roles"], len(sups)))
st, body = call("GET", f"/supervisor/students/{sups[0]['id']}", lecturer)
check("Supervisor opens supervisee detail", st == 200, st)
other = login("kojo.amponsah@grps.local")
st, body = call("GET", f"/students/{sups[0]['id']}/milestones", other)
check("Lecturer without the Supervisor role cannot read student milestones", st in (403, 404), st)

# Reports, settings, audit.
st, rep = call("GET", "/admin/reports/programme-progress", admin)
check("Programme progress report", st == 200 and len(rep["rows"]) > 0, rep)
req = urllib.request.Request(BASE + "/admin/reports/at-risk?format=csv", headers={"Authorization": "Bearer " + admin})
with urllib.request.urlopen(req) as r:
    check("At-risk report exports CSV", b"Student" in r.read(200))
audit = call("GET", f"/admin/audit?studentId={j7['id']}", admin, expect=200)[1]
check("Audit trail records the student's history", audit["totalElements"] >= 4, audit["totalElements"])
inbox = call("GET", "/notifications?size=5", seeded_student, expect=200)[1]
check("Student has notifications", inbox["totalElements"] > 0)
st, body = call("POST", "/auth/login", body={"login": "student@grps.local", "password": "nope"})
check("Bad password -> 401 without hint", st == 401 and "incorrect" in body["detail"], body)

failed = [r for r in results if not r[1]]
print(f"\n{len(results) - len(failed)} passed, {len(failed)} failed")
for name, _, detail in failed:
    print(" -", name, detail)
sys.exit(1 if failed else 0)
