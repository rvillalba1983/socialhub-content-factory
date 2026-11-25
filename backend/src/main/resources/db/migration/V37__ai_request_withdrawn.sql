-- Un encargo que SocialHub retira (lo sustituye otro o ya no hace falta) queda como WITHDRAWN: si Claude lo hizo igualmente, su
-- resultado llega tarde y se avisa en vez de descartarlo.
alter table ai_request drop constraint ai_request_status_check;
alter table ai_request add constraint ai_request_status_check
    check (status in ('SENT', 'COMPLETED', 'FAILED', 'NEEDS_HUMAN', 'INVALID', 'WITHDRAWN'));
