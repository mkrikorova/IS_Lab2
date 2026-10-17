package ru.itmo.spacemarine.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import ru.itmo.spacemarine.dto.CoordinatesDto;
import ru.itmo.spacemarine.dto.DtoMapper;
import ru.itmo.spacemarine.model.Coordinates;
import ru.itmo.spacemarine.repository.CoordinatesRepository;
import ru.itmo.spacemarine.security.AccessPolicy;
import ru.itmo.spacemarine.service.event.ChangeEvent;
import ru.itmo.spacemarine.service.exception.BadRequestException;
import ru.itmo.spacemarine.service.exception.LinkedObjectsException;
import ru.itmo.spacemarine.service.exception.NotFoundException;

import java.util.List;
import java.util.Optional;

/**
 * Координаты — общие точки пространства: создать может любой вошедший пользователь,
 * изменить или удалить — только администратор (иначе один командующий
 * «передвинул» бы чужих десантников, стоящих в этой точке).
 */
@ApplicationScoped
@Transactional
public class CoordinatesService {

    private static final String ENTITY = "coordinates";

    @Inject
    private CoordinatesRepository repository;

    @Inject
    private AccessPolicy access;
    @Inject
    private UniquenessService uniqueness;
    @Inject
    private Event<ChangeEvent> events;

    public List<CoordinatesDto> findAll() {
        return repository.findAll().stream().map(DtoMapper::toDto).toList();
    }

    public CoordinatesDto findById(Integer id) {
        return DtoMapper.toDto(get(id));
    }

    /** Явное создание точки (страница «Координаты»): занятая пара (x, y) — ошибка 409. */
    public CoordinatesDto create(CoordinatesDto dto) {
        access.requireUser();
        uniqueness.requireCoordinatesFree(dto.x, dto.y, null);
        return DtoMapper.toDto(insert(dto));
    }

    /**
     * Координаты, заданные полями внутри десантника (форма, импорт): find-or-create.
     * Если такая точка уже есть, десантник ссылается на неё — так все десантники
     * в одной точке получают один и тот же id координат.
     */
    public Coordinates findOrCreate(CoordinatesDto dto) {
        access.requireUser();
        Optional<Coordinates> found = uniqueness.findCoordinates(dto.x, dto.y, null);
        return found.orElseGet(() -> insert(dto));
    }

    private Coordinates insert(CoordinatesDto dto) {
        Coordinates saved = repository.save(new Coordinates(dto.x, UniquenessService.normalizeY(dto.y)));
        events.fire(new ChangeEvent(ENTITY, ChangeEvent.Action.CREATED, saved.getId()));
        return saved;
    }

    public CoordinatesDto update(Integer id, CoordinatesDto dto) {
        access.requireAdmin("Изменять координаты");
        Coordinates c = get(id);
        uniqueness.requireCoordinatesFree(dto.x, dto.y, id);
        c.setX(dto.x);
        c.setY(UniquenessService.normalizeY(dto.y));
        events.fire(new ChangeEvent(ENTITY, ChangeEvent.Action.UPDATED, id));
        return DtoMapper.toDto(c);
    }

    public void delete(Integer id, Integer reassignToId) {
        access.requireAdmin("Удалять координаты");
        Coordinates c = get(id);
        long linked = repository.countMarines(id);
        if (linked > 0) {
            if (reassignToId == null) {
                throw new LinkedObjectsException(
                        "С координатами связано десантников: " + linked + ". Выберите координаты для перепривязки", linked);
            }
            if (reassignToId.equals(id)) {
                throw new BadRequestException("Нельзя перепривязать десантников на удаляемые координаты");
            }
            get(reassignToId);
            repository.reassignMarines(id, reassignToId);
            events.fire(new ChangeEvent("spaceMarine", ChangeEvent.Action.UPDATED, null));
        }
        repository.delete(c);
        events.fire(new ChangeEvent(ENTITY, ChangeEvent.Action.DELETED, id));
    }

    public Coordinates get(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Координаты с id=" + id + " не найдены"));
    }
}
