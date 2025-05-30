package com.el.enrollment.domain;

import com.el.common.MoneyUtils;
import com.el.common.exception.InputInvalidException;
import com.el.common.exception.ResourceNotFoundException;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.*;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import javax.money.MonetaryAmount;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

@Table("enrollment")
@Getter
@ToString
public class Enrollment extends AbstractAggregateRoot<Enrollment> {
    @Id
    private Long id;
    private String student;
    private Long courseId;
    private String teacher;
    private Set<Long> quizIds;
    private Integer totalLessons;
    private LocalDateTime enrollmentDate;
    @MappedCollection(idColumn = "enrollment")
    private Set<LessonProgress> lessonProgresses = new HashSet<>();
    private Boolean completed;
    private Boolean reviewed = false;
    private LocalDateTime completedDate;

    private Certificate certificate;
    @MappedCollection(idColumn = "enrollment")
    private Set<QuizSubmission> quizSubmissions = new HashSet<>();
    private Boolean changedCourse;
    @CreatedBy
    private String createdBy;
    @CreatedDate
    private LocalDateTime createdDate;
    @LastModifiedBy
    private String lastModifiedBy;
    @LastModifiedDate
    private LocalDateTime lastModifiedDate;

    public Enrollment(String student, Long courseId, String teacher, Set<LessonProgress> lessonProgresses, Set<Long> quizIds) {
        if (student == null) throw new InputInvalidException("Student must not be null.");
        if (courseId == null) throw new InputInvalidException("CourseId must not be null.");
        if (teacher == null) throw new InputInvalidException("Teacher must not be null.");
        if (lessonProgresses == null || lessonProgresses.isEmpty())
            throw new InputInvalidException("LessonProgresses must not be null or empty.");
        if (quizIds == null || quizIds.isEmpty())
            throw new InputInvalidException("QuizIds must not be null or empty.");

        this.student = student;
        this.courseId = courseId;
        this.teacher = teacher;
        this.completed = false;
        this.quizIds = quizIds;
        this.totalLessons = 0;
        this.changedCourse = false;

        lessonProgresses.forEach(this::addLessonProgress);
    }

    public void changeCourse(Long newCourseId, String teacher, Set<LessonProgress> lessonProgresses,
                             Set<Long> quizIds) {
        if (newCourseId == null) throw new InputInvalidException("New course id must not be null.");
        if (lessonProgresses == null || lessonProgresses.isEmpty())
            throw new InputInvalidException("LessonProgresses must not be null or empty.");
        if (quizIds == null || quizIds.isEmpty())
            throw new InputInvalidException("QuizIds must not be null or empty.");
        if (newCourseId.equals(courseId))
            throw new InputInvalidException("New course id must be different from the current course id.");

        if (!getCanChangeCourse()) {
            throw new InputInvalidException("You can't request change course.");
        }

        if (!this.teacher.equals(teacher)) {
            registerEvent(new EnrollNewTeacherCourseEvent(this.teacher, teacher));
        }

        this.courseId = newCourseId;
        this.teacher = teacher;
        this.quizIds = quizIds;
        this.totalLessons = 0;
        this.lessonProgresses.clear();
        this.quizSubmissions.clear();
        this.changedCourse = true;
        lessonProgresses.forEach(this::addLessonProgress);
        enrollmentDate = LocalDateTime.now();
    }

    public void requestChangeCourse(Long newCourseId,
                                    MonetaryAmount oldCoursePrice,
                                    MonetaryAmount newCoursePrice,
                                    String teacher,
                                    Set<LessonProgress> lessonProgresses,
                                    Set<Long> quizIds) throws AdditionalPaymentRequiredException {
        if (oldCoursePrice == null) throw new InputInvalidException("Old course price must not be null.");
        if (newCoursePrice == null) throw new InputInvalidException("New course price must not be null.");
        if (newCourseId.equals(courseId))
            throw new InputInvalidException("New course id must be different from the current course id.");

        if (!getCanChangeCourse()) {
            throw new InputInvalidException("You can't request change course.");
        }
        MoneyUtils.checkValidPrice(oldCoursePrice, newCoursePrice.getCurrency());

        MonetaryAmount priceDifference = newCoursePrice.subtract(oldCoursePrice);
        if (priceDifference.isPositive()) {
            throw new AdditionalPaymentRequiredException(priceDifference);
        }

        changeCourse(newCourseId, teacher, lessonProgresses, quizIds);
    }

    private boolean isSevenDaysAfterEnrollment() {
        return LocalDateTime.now().minusDays(7).isAfter(enrollmentDate);
    }

    public boolean getCanChangeCourse() {
        return !completed && !changedCourse && !isSevenDaysAfterEnrollment();
    }

    public void markLessonAsCompleted(Long lessonId, String lessonTitle) {
        LessonProgress lessonProgress = lessonProgresses.stream()
                .filter(lp -> lp.getLessonId().equals(lessonId))
                .findFirst()
                .orElseGet(() -> {
                    int orderIndex = getTailLessonProgress().getOrderIndex() + 1;
                    LessonProgress newLessonProgress = new LessonProgress(lessonTitle, lessonId, orderIndex);
                    if (isInProgress(newLessonProgress)) {
                        newLessonProgress.makeInProgress();
                    }
                    newLessonProgress.markAsBonus();
                    lessonProgresses.add(newLessonProgress);
                    return newLessonProgress;
                });

        if (!lessonProgress.getInProgress())
            throw new InputInvalidException("You can't mark lesson as completed. Please follow the progress.");

        lessonProgress.markAsCompleted();
        nextLessonProgressInProgress(lessonProgress.getOrderIndex());
        checkCompleted();
    }

    private void nextLessonProgressInProgress(Integer orderIndex) {
        lessonProgresses.stream()
                .filter(lp -> lp.getOrderIndex() == orderIndex + 1)
                .findFirst()
                .ifPresent(LessonProgress::makeInProgress);
    }

    private boolean isInProgress(LessonProgress lessonProgress) {
        return lessonProgresses.stream()
                .filter(lp -> lp.getOrderIndex() < lessonProgress.getOrderIndex())
                .allMatch(LessonProgress::isCompleted);
    }

    private LessonProgress getTailLessonProgress() {
        return lessonProgresses.stream()
                .max(Comparator.comparing(LessonProgress::getOrderIndex))
                .orElseThrow(ResourceNotFoundException::new);
    }

    public void markLessonAsIncomplete(Long lessonId) {
        if (this.completed)
            throw new InputInvalidException("You can't mark lesson as incomplete for a completed enrollment.");
        LessonProgress lessonProgress = lessonProgresses.stream()
                .filter(lp -> lp.getLessonId().equals(lessonId))
                .findFirst()
                .orElseThrow(ResourceNotFoundException::new);

        boolean canMarkLesson = lessonProgresses.stream()
                .filter(lp -> lp.getOrderIndex() > lessonProgress.getOrderIndex())
                .noneMatch(LessonProgress::isCompleted);
        if (!canMarkLesson)
            throw new InputInvalidException("You can't mark lesson as incomplete. Please follow the progress.");

        lessonProgress.markAsIncomplete();
        nextLessonProgressNotInProgress(lessonProgress.getOrderIndex());
        this.completed = false;
    }

    private void nextLessonProgressNotInProgress(Integer orderIndex) {
        lessonProgresses.stream()
                .filter(lp -> lp.getOrderIndex() == orderIndex + 1)
                .findFirst()
                .ifPresent(LessonProgress::makeNotInProgress);
    }

    private void checkCompleted() {
        if (allLessonsCompleted() && allQuizSubmitPassed()) {
            this.completed = true;
            this.completedDate = LocalDateTime.now();
            registerEvent(new EnrollmentCompletedEvent(this.id, this.courseId, this.student));
        }
    }

    private boolean allLessonsCompleted() {
        long completedLessons = this.lessonProgresses.stream()
                .filter(lessonProgress -> lessonProgress.isCompleted() && !lessonProgress.isBonus()).count();
        return completedLessons == this.totalLessons;
    }

    private boolean allQuizSubmitPassed() {
        long passedQuizzes = this.quizSubmissions.stream()
                .filter(quizSubmission -> quizSubmission.isPassed() && !quizSubmission.isBonus()).count();
        // https://github.com/thainguyencoffee/e-learning-project/issues/157
        return passedQuizzes == this.quizIds.size();
    }

    public Progress getProgress() {
        int totalLessons = this.totalLessons;
        int completedLessons = (int) this.lessonProgresses.stream()
                .filter(lessonProgress -> lessonProgress.isCompleted() && !lessonProgress.isBonus()).count();
        int totalQuizzes = this.quizIds.size();
        int passedQuizzes = (int) this.quizSubmissions.stream()
                .filter(quizSubmission -> quizSubmission.isPassed() && !quizSubmission.isBonus()).count();
        int lessonBonus = (int) this.lessonProgresses.stream()
                .filter(LessonProgress::isBonus).count();
        int quizBonus = (int) this.quizSubmissions.stream()
                .filter(QuizSubmission::isBonus).count();
        double progressPercentage = (completedLessons + passedQuizzes) * 100.0 / (totalLessons + totalQuizzes);
        return new Progress(totalLessons, completedLessons, totalQuizzes, passedQuizzes, lessonBonus, quizBonus, progressPercentage);
    }

    public void addLessonProgress(LessonProgress lessonProgress) {
        if (lessonProgress == null) throw new InputInvalidException("LessonProgress must not be null.");
        lessonProgresses.add(lessonProgress);
        this.totalLessons++;
    }

    public void addQuizSubmission(QuizSubmission quizSubmission) {
        if (quizSubmission == null) throw new InputInvalidException("QuizSubmission must not be null.");
        if (isSubmittedQuiz(quizSubmission.getQuizId())) {
            throw new InputInvalidException("QuizSubmission for this quiz already exists.");
        }
        boolean isNotBonusQuiz = quizIds.contains(quizSubmission.getQuizId());
        if (!isNotBonusQuiz) {
            quizSubmission.markAsBonus();
        }
        quizSubmissions.add(quizSubmission);
        checkCompleted();
    }

    public boolean isSubmittedQuiz(Long quizId) {
        return quizSubmissions.stream().anyMatch(qs -> qs.getQuizId().equals(quizId));
    }

    public LessonProgress findLessonProgressByLessonId(Long lessonId) {
        if (lessonId == null) throw new InputInvalidException("LessonId must not be null.");
        return lessonProgresses.stream()
                .filter(lp -> lp.getLessonId().equals(lessonId))
                .findFirst()
                .orElseThrow(ResourceNotFoundException::new);
    }

    public void createCertificate(String fullName, String email, String courseTitle, String teacher) {
        // Create certificate
        if (!this.completed) {
            throw new InputInvalidException("You can't create certificate for an incomplete enrollment.");
        }

        this.certificate = new Certificate(fullName, email, this.student, this.courseId, courseTitle, teacher);
    }

    public void markAsEnrolled() {
        this.enrollmentDate = LocalDateTime.now();
        registerEvent(new EnrollmentCreatedEvent(teacher));
    }

    public void markAsReviewed() {
        if (reviewed) {
            throw new InputInvalidException("Course enrollment is already reviewed.");
        }
        reviewed = true;
    }

    public QuizSubmission getQuizSubmission(Long quizSubmissionId) {
        return quizSubmissions.stream()
                .filter(qs -> qs.getId().equals(quizSubmissionId))
                .findFirst()
                .orElseThrow(ResourceNotFoundException::new);
    }

    public void deleteQuizSubmission(Long quizId) {
        QuizSubmission quizSubmission = getQuizSubmission(quizId);
        if (!quizSubmission.isBonus() && completed) {
            revocationCertificate();
        }
        quizSubmissions.remove(quizSubmission);
    }

    private void revocationCertificate() {
        // Revoke certificate
        if (!completed) {
            throw new InputInvalidException("You can't revoke certificate for an incomplete enrollment.");
        }
        String certificateUrl = this.certificate.getUrl();
        completed = false;
        completedDate = null;
        certificate = null;
        reviewed = false;
        registerEvent(new EnrollmentIncompleteEvent(this.id, this.courseId, this.student, certificateUrl));
    }

    public record EnrollmentCompletedEvent(Long id, Long courseId, String student) {
    }

    public record EnrollmentIncompleteEvent(Long id, Long courseId, String student, String certificateUrl) {
    }

    public record EnrollmentCreatedEvent(String teacher) {
    }

    public record EnrollNewTeacherCourseEvent(String oldTeacher, String newTeacher) {
    }

}
