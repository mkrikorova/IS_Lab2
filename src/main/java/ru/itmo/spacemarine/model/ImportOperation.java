package ru.itmo.spacemarine.model;

import jakarta.persistence.*;

import java.time.ZonedDateTime;

/** Запись истории импорта: одна загрузка файла одним пользователем. */
@Entity
@Table(name = "import_operation")
public class ImportOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ImportStatus status = ImportStatus.IN_PROGRESS;

    @Column(name = "file_name", length = 255)
    private String fileName;

    /** Только для SUCCESS. */
    @Column(name = "added_count")
    private Integer addedCount;

    /** Пропущенные полные дубли своих десантников. */
    @Column(name = "skipped_count")
    private Integer skippedCount;

    /** Только для FAILED. */
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at", nullable = false, updatable = false)
    private ZonedDateTime startedAt;

    @Column(name = "finished_at")
    private ZonedDateTime finishedAt;

    public ImportOperation() {
    }

    public ImportOperation(AppUser user, String fileName) {
        this.user = user;
        this.fileName = fileName;
        this.startedAt = ZonedDateTime.now();
    }

    public void succeed(int added, int skipped) {
        status = ImportStatus.SUCCESS;
        addedCount = added;
        skippedCount = skipped;
        errorMessage = null;
        finishedAt = ZonedDateTime.now();
    }

    public void fail(String message) {
        status = ImportStatus.FAILED;
        addedCount = null;
        errorMessage = message;
        finishedAt = ZonedDateTime.now();
    }

    public Integer getId() { return id; }
    public AppUser getUser() { return user; }
    public ImportStatus getStatus() { return status; }
    public String getFileName() { return fileName; }
    public Integer getAddedCount() { return addedCount; }
    public Integer getSkippedCount() { return skippedCount; }
    public String getErrorMessage() { return errorMessage; }
    public ZonedDateTime getStartedAt() { return startedAt; }
    public ZonedDateTime getFinishedAt() { return finishedAt; }
}
