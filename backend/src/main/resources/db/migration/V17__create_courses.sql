-- V17__create_courses.sql
-- Read-only course reference data for the reviews prototype (CRS-1).

CREATE TABLE courses (
    code  VARCHAR(20)  PRIMARY KEY,
    title VARCHAR(255) NOT NULL
);