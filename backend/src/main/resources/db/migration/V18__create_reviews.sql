CREATE TABLE reviews (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    course_code     VARCHAR(20)  NOT NULL REFERENCES courses(code) ON DELETE CASCADE,
    workload_rating   INTEGER     NOT NULL CHECK (workload_rating  BETWEEN 1 AND 5),
    difficulty_rating INTEGER     NOT NULL CHECK (difficulty_rating BETWEEN 1 AND 5),
    enjoyment_rating  INTEGER     NOT NULL CHECK (enjoyment_rating  BETWEEN 1 AND 5),
    lecturer_rating   INTEGER     NOT NULL CHECK (lecturer_rating   BETWEEN 1 AND 5),
    would_recommend BOOLEAN,
    body            TEXT,
    created_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, course_code)
);

CREATE INDEX idx_reviews_course_code ON reviews(course_code);
CREATE INDEX idx_reviews_user_id     ON reviews(user_id);
