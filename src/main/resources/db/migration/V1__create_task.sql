CREATE TABLE task (
	id              UUID PRIMARY KEY,
	headline        VARCHAR(120)     NOT NULL,
	description     VARCHAR(2000)    NOT NULL DEFAULT '',
	estimated_hours DOUBLE PRECISION NOT NULL,
	status          VARCHAR(20)      NOT NULL,
	scheduled_date  DATE             NOT NULL,
	created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
	completed_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX task_scheduled_date_idx ON task (scheduled_date);
CREATE INDEX task_completed_at_idx ON task (completed_at);
