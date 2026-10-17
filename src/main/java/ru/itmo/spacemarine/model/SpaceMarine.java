package ru.itmo.spacemarine.model;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.ZonedDateTime;

@Entity
@Table(name = "space_marine")
public class SpaceMarine {

    /** Генерируется БД (IDENTITY), > 0, уникален. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Integer id;

    @NotNull
    @NotBlank(message = "Имя не может быть пустым")
    @Column(name = "name", nullable = false)
    private String name;

    @NotNull(message = "Координаты обязательны")
    @Valid
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "coordinates_id", nullable = false)
    private Coordinates coordinates;

    /** Генерируется автоматически при сохранении, не изменяется. */
    @NotNull
    @Column(name = "creation_date", nullable = false, updatable = false)
    private ZonedDateTime creationDate;

    @NotNull(message = "Орден обязателен")
    @Valid
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @Positive(message = "health должно быть больше 0")
    @Column(name = "health", nullable = false)
    private long health;

    @Positive(message = "heartCount должно быть больше 0")
    @Max(value = 3, message = "heartCount не может быть больше 3")
    @Column(name = "heart_count")
    private Integer heartCount;

    @Column(name = "height", nullable = false)
    private int height;

    @Enumerated(EnumType.STRING)
    @Column(name = "melee_weapon", length = 20)
    private MeleeWeapon meleeWeapon;

    @PrePersist
    void onCreate() {
        if (creationDate == null) {
            creationDate = ZonedDateTime.now();
        }
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Coordinates getCoordinates() { return coordinates; }
    public void setCoordinates(Coordinates coordinates) { this.coordinates = coordinates; }

    public ZonedDateTime getCreationDate() { return creationDate; }
    public void setCreationDate(ZonedDateTime creationDate) { this.creationDate = creationDate; }

    public Chapter getChapter() { return chapter; }
    public void setChapter(Chapter chapter) { this.chapter = chapter; }

    public long getHealth() { return health; }
    public void setHealth(long health) { this.health = health; }

    public Integer getHeartCount() { return heartCount; }
    public void setHeartCount(Integer heartCount) { this.heartCount = heartCount; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public MeleeWeapon getMeleeWeapon() { return meleeWeapon; }
    public void setMeleeWeapon(MeleeWeapon meleeWeapon) { this.meleeWeapon = meleeWeapon; }
}
