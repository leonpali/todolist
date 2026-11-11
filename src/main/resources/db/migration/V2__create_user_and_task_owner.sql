CREATE TABLE app_user (
	email         VARCHAR(254) PRIMARY KEY,
	password_hash VARCHAR(100) NOT NULL,
	created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE task ADD COLUMN owner_email VARCHAR(254);

ALTER TABLE task ADD CONSTRAINT task_owner_fk
	FOREIGN KEY (owner_email) REFERENCES app_user (email) ON DELETE CASCADE;

CREATE INDEX task_owner_date_idx ON task (owner_email, scheduled_date);
