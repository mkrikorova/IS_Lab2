package ru.itmo.spacemarine.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "coordinates")
public class Coordinates {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Integer id;

    @NotNull(message = "x не может быть null")
    @Column(name = "x", nullable = false)
    private Long x;

    @NotNull(message = "y не может быть null")
    @DecimalMin(value = "-833", inclusive = false, message = "y должен быть больше -833")
    @Column(name = "y", nullable = false)
    private Double y;

    public Coordinates() {
    }

    public Coordinates(Long x, Double y) {
        this.x = x;
        this.y = y;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Long getX() { return x; }
    public void setX(Long x) { this.x = x; }

    public Double getY() { return y; }
    public void setY(Double y) { this.y = y; }
}
