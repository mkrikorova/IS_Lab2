package ru.itmo.spacemarine.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import ru.itmo.spacemarine.dto.*;
import ru.itmo.spacemarine.model.Chapter;
import ru.itmo.spacemarine.model.Coordinates;
import ru.itmo.spacemarine.model.MeleeWeapon;
import ru.itmo.spacemarine.model.SpaceMarine;
import ru.itmo.spacemarine.repository.SpaceMarineRepository;
import ru.itmo.spacemarine.security.AccessPolicy;
import ru.itmo.spacemarine.security.UserPrincipal;
import ru.itmo.spacemarine.service.event.ChangeEvent;
import ru.itmo.spacemarine.service.exception.BadRequestException;
import ru.itmo.spacemarine.service.exception.ConflictException;
import ru.itmo.spacemarine.service.exception.NotFoundException;

import ru.itmo.spacemarine.tx.SerializableExecutor;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Transactional
public class SpaceMarineService {

    private static final String ENTITY = "spaceMarine";
    private static final int MAX_PAGE_SIZE = 100;

    @Inject
    private SerializableExecutor serializable;
    @Inject
    private SpaceMarineRepository marines;
    @Inject
    private ChapterService chapterService;
    @Inject
    private CoordinatesService coordinatesService;
    @Inject
    private AccessPolicy access;
    @Inject
    private UniquenessService uniqueness;
    @Inject
    private Event<ChangeEvent> events;

    public PageDto<SpaceMarineDto> findPage(SpaceMarineFilter f) {
        if (f.page < 0) throw new BadRequestException("page не может быть отрицательным");
        if (f.size < 1 || f.size > MAX_PAGE_SIZE) throw new BadRequestException("size должен быть от 1 до " + MAX_PAGE_SIZE);
        if (f.meleeWeapon != null && !f.meleeWeapon.isEmpty()) {
            parseWeapon(f.meleeWeapon);
        }
        List<SpaceMarineDto> content = marines.findPage(f).stream().map(DtoMapper::toDto).toList();
        return new PageDto<>(content, f.page, f.size, marines.count(f));
    }

    /** Все объекты без пагинации — для визуализации на карте. */
    public List<SpaceMarineDto> findAll() {
        return marines.findAll().stream().map(DtoMapper::toDto).toList();
    }

    public SpaceMarineDto findById(Integer id) {
        return DtoMapper.toDto(get(id));
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED) // транзакцию SERIALIZABLE открывает executor
    public SpaceMarineDto create(SpaceMarineRequest req) {
        return serializable.execute("SpaceMarineService.create", () -> {
            access.requireUser();
            SpaceMarine m = new SpaceMarine();
            apply(m, req);
            uniqueness.findDuplicate(m, null).ifPresent(d -> {
                throw duplicateOf(d);
            });
            marines.save(m);
            events.fire(new ChangeEvent(ENTITY, ChangeEvent.Action.CREATED, m.getId()));
            return DtoMapper.toDto(m);
        });
    }

    /**
     * Одна запись импорта — в транзакции вызывающего (ImportService), события не рассылаются
     * (импорт отправит одно общее уведомление после коммита).
     * Полный дубль своего десантника (ADMIN — любого) не ошибка: запись пропускается.
     * Чужого дубля здесь быть не может — чужой орден отсекается раньше, в resolveChapter.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public ImportOutcome importOne(SpaceMarineRequest req) {
        SpaceMarine m = new SpaceMarine();
        apply(m, req);
        Optional<SpaceMarine> duplicate = uniqueness.findDuplicate(m, null);
        if (duplicate.isPresent()) {
            return new ImportOutcome(duplicate.get(), false);
        }
        marines.save(m);
        return new ImportOutcome(m, true);
    }

    /** created == false — запись пропущена, marine — найденный дубль. */
    public record ImportOutcome(SpaceMarine marine, boolean created) {
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED) // транзакцию SERIALIZABLE открывает executor
    public SpaceMarineDto update(Integer id, SpaceMarineRequest req) {
        return serializable.execute("SpaceMarineService.update", () -> {
            SpaceMarine m = get(id); // вернул управляемый объект
            access.requireManage(m); // править можно только своих десантников (ADMIN — любых)
            Versions.require(req.version, m.getVersion(), "этого десантника");
            marines.forceVersionIncrement(m);
            apply(m, req); // id и creationDate не меняются
            // после изменения десантник не должен совпасть с другим (сам с собой — можно)
            uniqueness.findDuplicate(m, id).ifPresent(d -> {
                throw duplicateOf(d);
            });
            marines.flush(); // UPDATE ... WHERE version = ? — конфликт проявится здесь
            events.fire(new ChangeEvent(ENTITY, ChangeEvent.Action.UPDATED, id));
            return DtoMapper.toDto(m);
        });
    }

    public void delete(Integer id) {
        SpaceMarine m = get(id);
        access.requireManage(m);
        marines.delete(m);
        marines.flush(); // DELETE ... WHERE version = ? — если объект успели изменить, будет 409
        events.fire(new ChangeEvent(ENTITY, ChangeEvent.Action.DELETED, id));
    }

    private void apply(SpaceMarine m, SpaceMarineRequest req) {
        m.setName(Names.normalize(req.name));
        m.setCoordinates(resolveCoordinates(req));
        m.setChapter(resolveChapter(req));
        m.setHealth(req.health);
        m.setHeartCount(req.heartCount);
        m.setHeight(req.height);
        m.setMeleeWeapon(req.meleeWeapon);
    }

    /**
     * Либо существующие координаты по id, либо значения (x, y) — тогда find-or-create:
     * если такая точка уже есть, десантник встаёт в неё (ограничение уникальности №2).
     */
    private Coordinates resolveCoordinates(SpaceMarineRequest req) {
        if ((req.coordinatesId == null) == (req.coordinates == null)) {
            throw new BadRequestException("Укажите либо coordinatesId существующих координат, либо новые coordinates");
        }
        if (req.coordinatesId != null) {
            return coordinatesService.get(req.coordinatesId);
        }
        return coordinatesService.findOrCreate(req.coordinates);
    }

    /**
     * Либо существующий орден по id (только свой; ADMIN — любой), либо поля ордена —
     * тогда find-or-create по названию (ограничение уникальности №1, см. ChapterService.findOrCreate).
     */
    private Chapter resolveChapter(SpaceMarineRequest req) {
        if ((req.chapterId == null) == (req.chapter == null)) {
            throw new BadRequestException("Укажите либо chapterId существующего ордена, либо новый chapter");
        }
        if (req.chapterId != null) {
            Chapter existing = chapterService.get(req.chapterId);
            access.requireAssignTo(existing);
            return existing;
        }
        return chapterService.findOrCreate(req.chapter, "chapter.");
    }

    /** Ограничение уникальности №3: полный дубль десантника. */
    private ConflictException duplicateOf(SpaceMarine d) {
        UserPrincipal user = access.requireUser();
        boolean mine = d.getChapter().getOwner().getId() == user.id();
        String who = mine ? "У вас уже есть такой десантник" : "Такой десантник уже существует у командующего "
                + d.getChapter().getOwner().getLogin();
        return new ConflictException(who + ": номер " + d.getId() + " «" + d.getName()
                + "». Полный дубль (совпадают все поля) создать нельзя");
    }

    private SpaceMarine get(Integer id) {
        return marines.findById(id)
                .orElseThrow(() -> new NotFoundException("Десантник с id=" + id + " не найден"));
    }

    static MeleeWeapon parseWeapon(String value) {
        try {
            return MeleeWeapon.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Неизвестное оружие '" + value + "'. Допустимо: "
                    + Arrays.toString(MeleeWeapon.values()));
        }
    }
}
