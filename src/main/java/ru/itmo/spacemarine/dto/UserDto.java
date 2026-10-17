package ru.itmo.spacemarine.dto;

import ru.itmo.spacemarine.model.Role;

public class UserDto {
    public Integer id;
    public String login;
    public Role role;

    public UserDto() {
    }

    public UserDto(Integer id, String login, Role role) {
        this.id = id;
        this.login = login;
        this.role = role;
    }
}
