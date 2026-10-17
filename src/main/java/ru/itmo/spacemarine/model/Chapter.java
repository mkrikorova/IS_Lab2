package ru.itmo.spacemarine.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Entity
@Table(name = "chapter")
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Integer id;

    @NotNull
    @NotBlank(message = "Имя ордена не может быть пустым")
    @Column(name = "name", nullable = false)
    private String name;

    @Positive(message = "marinesCount должен быть больше 0")
    @Max(value = 1000, message = "marinesCount не может быть больше 1000")
    @Column(name = "marines_count", nullable = false)
    private long marinesCount;

    /**
     * Командующий ордена. Владелец десантника отдельно не хранится —
     * это владелец его ордена, поэтому противоречия «десантник одного
     * пользователя в ордене другого» в данных возникнуть не может.
     */
    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    public Chapter() {
    }

    public Chapter(String name, long marinesCount, AppUser owner) {
        this.name = name;
        this.marinesCount = marinesCount;
        this.owner = owner;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getMarinesCount() { return marinesCount; }
    public void setMarinesCount(long marinesCount) { this.marinesCount = marinesCount; }

    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
}
