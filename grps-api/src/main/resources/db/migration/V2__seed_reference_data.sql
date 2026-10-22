-- Roles, the Masters and PhD programmes with their milestone templates,
-- default settings (FR-SET-01) and the default thesis format rule set.

insert into role (id, created_at, updated_at, code)
select gen_random_uuid(), now(), now(), code
from (values ('SUPER_ADMIN'), ('ADMIN'), ('SECRETARY'), ('STUDENT'), ('LECTURER'), ('SUPERVISOR'), ('LIBRARIAN')) as r(code);

insert into programme (id, created_at, updated_at, code, name, level, duration_months, semesters,
                       required_milestones, checkpoint_months, checkpoint_milestones, evidence_required)
values (gen_random_uuid(), now(), now(), 'MASTERS', 'Masters by Research', 'MASTERS', 24, 4, 5, 18, 4, true),
       (gen_random_uuid(), now(), now(), 'PHD', 'Doctor of Philosophy', 'PHD', 24, 4, 6, 18, 4, false);

insert into milestone_template (id, created_at, updated_at, programme_id, sequence_no, title, description,
                                is_final_pre_defense, evidence_required)
select gen_random_uuid(), now(), now(), p.id, t.seq, t.title, t.description, t.final, true
from programme p
cross join (values
    (1, 'Research proposal', 'Problem statement, objectives, research questions and proposed methods.', false),
    (2, 'Literature review and methodology', 'Critical review of the literature and the detailed research design.', false),
    (3, 'Data collection progress', 'Progress on data collection, early observations and any changes to the plan.', false),
    (4, 'Results and analysis', 'Analysed results and how they answer the research questions.', false),
    (5, 'Final pre-defense', 'Complete thesis presented to the panel before submission.', true)
) as t(seq, title, description, final)
where p.code = 'MASTERS';

insert into milestone_template (id, created_at, updated_at, programme_id, sequence_no, title, description,
                                is_final_pre_defense, evidence_required)
select gen_random_uuid(), now(), now(), p.id, t.seq, t.title, t.description, t.final, false
from programme p
cross join (values
    (1, 'Research proposal', 'Problem statement, objectives, research questions and proposed methods.', false),
    (2, 'Literature review', 'Critical review of the literature and identification of the research gap.', false),
    (3, 'Methodology', 'Detailed research design, instruments and analysis plan.', false),
    (4, 'Data collection progress', 'Progress on data collection, early observations and any changes to the plan.', false),
    (5, 'Results and discussion', 'Analysed results, discussion and contribution to knowledge.', false),
    (6, 'Final pre-defense', 'Complete thesis presented to the panel before submission.', true)
) as t(seq, title, description, final)
where p.code = 'PHD';

insert into system_setting (id, created_at, updated_at, key, value, value_type, description)
values
    (gen_random_uuid(), now(), now(), 'screening.similarity_limit_pct', '20', 'DECIMAL', 'Similarity index above this percentage rejects a thesis'),
    (gen_random_uuid(), now(), now(), 'screening.ai_limit_pct', '20', 'DECIMAL', 'AI-content score above this percentage rejects a thesis'),
    (gen_random_uuid(), now(), now(), 'attendance.threshold_pct', '70', 'DECIMAL', 'Minimum attendance percentage for exam eligibility'),
    (gen_random_uuid(), now(), now(), 'attendance.warning_pct', '75', 'DECIMAL', 'Attendance percentage that triggers an early warning'),
    (gen_random_uuid(), now(), now(), 'attendance.lecturer_edit_days', '7', 'INTEGER', 'Days a lecturer can edit a session''s attendance'),
    (gen_random_uuid(), now(), now(), 'milestone.max_attempts', '3', 'INTEGER', 'Maximum failed attempts per milestone'),
    (gen_random_uuid(), now(), now(), 'milestone.secretary_correction_hours', '48', 'INTEGER', 'Hours a secretary can correct a recorded outcome'),
    (gen_random_uuid(), now(), now(), 'deadline.checkpoint_months', '18', 'INTEGER', 'Default checkpoint month for new programmes'),
    (gen_random_uuid(), now(), now(), 'deadline.checkpoint_milestones', '4', 'INTEGER', 'Default milestones required by the checkpoint for new programmes'),
    (gen_random_uuid(), now(), now(), 'deadline.programme_months', '24', 'INTEGER', 'Default programme length in months for new programmes'),
    (gen_random_uuid(), now(), now(), 'reminder.hours', '48,24', 'LIST', 'Hours before a presentation when reminders are sent'),
    (gen_random_uuid(), now(), now(), 'upload.evidence_max_mb', '25', 'INTEGER', 'Maximum evidence file size in MB'),
    (gen_random_uuid(), now(), now(), 'upload.evidence_max_files', '10', 'INTEGER', 'Maximum evidence files per milestone'),
    (gen_random_uuid(), now(), now(), 'upload.publication_max_mb', '25', 'INTEGER', 'Maximum publication PDF size in MB'),
    (gen_random_uuid(), now(), now(), 'upload.thesis_max_mb', '50', 'INTEGER', 'Maximum thesis file size in MB');

insert into format_rule (id, created_at, updated_at, programme_id, rule_code, parameters, active)
select gen_random_uuid(), now(), now(), p.id, r.code, r.params::jsonb, true
from programme p
cross join (values
    ('FMT-PAGE', '{"size": "A4", "widthMm": 210, "heightMm": 297, "toleranceMm": 3}'),
    ('FMT-MARGIN', '{"leftCm": 3.8, "topCm": 2.5, "rightCm": 2.5, "bottomCm": 2.5, "toleranceCm": 0.15}'),
    ('FMT-FONT', '{"family": "Times New Roman", "sizePt": 12, "maxDeviationPct": 5}'),
    ('FMT-SPACING', '{"lineSpacing": 1.5, "tolerance": 0.05, "maxDeviationPct": 10}'),
    ('FMT-HEAD', '{"styles": ["Heading1", "Heading2", "Heading3"], "minRequired": 1}'),
    ('FMT-PAGENUM', '{"preliminary": "lowerRoman", "main": "decimal"}'),
    ('FMT-SECTIONS', '{"sections": ["Title page", "Declaration", "Abstract", "Acknowledgements", "Table of contents", "Chapters", "References", "Appendices"]}'),
    ('FMT-ABSTRACT', '{"maxWords": 300}'),
    ('FMT-TOC', '{"required": true}'),
    ('FMT-REFS', '{"required": true}')
) as r(code, params);

insert into venue (id, created_at, updated_at, name, capacity, active)
values (gen_random_uuid(), now(), now(), 'Graduate School Seminar Room A', 40, true),
       (gen_random_uuid(), now(), now(), 'Graduate School Seminar Room B', 25, true),
       (gen_random_uuid(), now(), now(), 'Main Library Conference Hall', 120, true);
