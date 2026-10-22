-- Keep the AI-score limit that applied to each screening decision, next to the similarity limit,
-- so later setting changes never recalculate past decisions (FR-SET-02).
alter table thesis_submission add column ai_limit_applied_pct numeric(5, 2);
