-- V11__rename_username_add_names.sql

ALTER TABLE users RENAME COLUMN username TO student_number;
ALTER INDEX users_username_key RENAME TO users_student_number_key;
ALTER TABLE users ADD COLUMN first_name VARCHAR(255);
ALTER TABLE users ADD COLUMN last_name VARCHAR(255);
