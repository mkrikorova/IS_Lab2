package ru.itmo.spacemarine.dto;

/** Результат группировки по meleeWeapon (meleeWeapon == null — группа «без оружия»). */
public class WeaponGroupDto {
    public String meleeWeapon;
    public long count;

    public WeaponGroupDto() {
    }

    public WeaponGroupDto(String meleeWeapon, long count) {
        this.meleeWeapon = meleeWeapon;
        this.count = count;
    }
}
