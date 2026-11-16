package com.coursewise.review;

import com.coursewise.auth.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.coursewise.course.Course;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reviews", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "course_code"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_code", nullable = false)
    private Course course;

    @Column(name = "workload_rating", nullable = false)
    private Integer workloadRating;

    @Column(name = "difficulty_rating", nullable = false)
    private Integer difficultyRating;

    @Column(name = "enjoyment_rating", nullable = false)
    private Integer enjoymentRating;

    @Column(name = "lecturer_rating", nullable = false)
    private Integer lecturerRating;

    @Column(name = "would_recommend", nullable = false)
    private Boolean wouldRecommend;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public String getCourseCode() {
        return course != null ? course.getCode() : null;
    }

    public Review(User user, Course course, Integer workloadRating, Integer difficultyRating,
                  Integer enjoymentRating, Integer lecturerRating, Boolean wouldRecommend, String body) {
        this.user = user;
        this.course = course;
        this.workloadRating = workloadRating;
        this.difficultyRating = difficultyRating;
        this.enjoymentRating = enjoymentRating;
        this.lecturerRating = lecturerRating;
        this.wouldRecommend = wouldRecommend;
        this.body = body;
    }

    public void update(Integer workloadRating, Integer difficultyRating, Integer enjoymentRating,
                       Integer lecturerRating, Boolean wouldRecommend, String body) {
        this.workloadRating = workloadRating;
        this.difficultyRating = difficultyRating;
        this.enjoymentRating = enjoymentRating;
        this.lecturerRating = lecturerRating;
        this.wouldRecommend = wouldRecommend;
        this.body = body;
    }
}
