package ru.itmo.spacemarine.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import ru.itmo.spacemarine.dto.ChapterDto;
import ru.itmo.spacemarine.dto.DtoMapper;
import ru.itmo.spacemarine.dto.SpaceMarineDto;
import ru.itmo.spacemarine.dto.WeaponGroupDto;
import ru.itmo.spacemarine.repository.SpecialOperationsRepository;
import ru.itmo.spacemarine.security.AccessPolicy;
import ru.itmo.spacemarine.security.UserPrincipal;
import ru.itmo.spacemarine.service.event.ChangeEvent;
import ru.itmo.spacemarine.service.exception.BadRequestException;

import ru.itmo.spacemarine.tx.SerializableExecutor;
import java.util.List;

/** Специальные операции — бизнес-логика вызывает функции БД. */
@ApplicationScoped
@Transactional
public class SpecialOperationsService {

    @Inject
    private SerializableExecutor serializable;
    @Inject
    private SpecialOperationsRepository repository;
    @Inject
    private ChapterService chapterService;
    @Inject
    private AccessPolicy access;
    @Inject
    private UniquenessService uniqueness;
    @Inject
    private Event<ChangeEvent> events;

    public long sumHeartCount() {
        return repository.sumHeartCount();
    }

    public List<WeaponGroupDto> groupByMeleeWeapon() {
        return repository.groupByMeleeWeapon().stream()
                .map(r -> new WeaponGroupDto((String) r[0], ((Number) r[1]).longValue()))
                .toList();
    }

    public List<SpaceMarineDto> findByNameSubstring(String substring) {
        if (substring == null || substring.isEmpty()) {
            throw new BadRequestException("Подстрока не может быть пустой");
        }
        return repository.findByNameSubstring(substring).stream().map(DtoMapper::toDto).toList();
    }

    /** Новый орден записывается на текущего пользователя. */
    @Transactional(Transactional.TxType.NOT_SUPPORTED) // транзакцию SERIALIZABLE открывает executor
    public ChapterDto createChapter(ChapterDto dto) {
        return serializable.execute("SpecialOperationsService.createChapter", () -> {
            UserPrincipal user = access.requireUser();
            uniqueness.requireChapterNameFree(dto.name, null, "name"); // функция БД уникальность не проверяет
            int id = repository.createChapter(Names.normalize(dto.name), dto.marinesCount, user.id());
            events.fire(new ChangeEvent("chapter", ChangeEvent.Action.CREATED, id));
            return chapterService.findById(id);
        });
    }

    /**
     * Возвращает количество удалённых вместе с орденом десантников.
     * Распустить орден может его командующий или администратор.
     */
    public int disbandChapter(int chapterId) {
        access.requireManage(chapterService.get(chapterId)); // 404, если ордена нет; 403, если чужой
        int deleted = repository.disbandChapter(chapterId);
        events.fire(new ChangeEvent("chapter", ChangeEvent.Action.DELETED, chapterId));
        events.fire(new ChangeEvent("spaceMarine", ChangeEvent.Action.DELETED, null));
        return deleted;
    }
}
