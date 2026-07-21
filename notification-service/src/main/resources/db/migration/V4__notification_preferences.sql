ALTER TABLE notification_preference
 ADD COLUMN email VARCHAR(320) NULL,
 ADD COLUMN event_types VARCHAR(1000) NULL;
