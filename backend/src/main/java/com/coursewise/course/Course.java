package com.coursewise.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Represents a university course entity in the system.
 */
@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor
public class Course {

    @Id
    @Column(name = "code", length = 20, nullable = false)
    private String code;

    @Column(name = "title", nullable = false)
    private String title;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Course course)) return false;
        return Objects.equals(code, course.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }
}
