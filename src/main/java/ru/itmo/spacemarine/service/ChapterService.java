package ru.itmo.spacemarine.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import ru.itmo.spacemarine.dto.ChapterDto;
import ru.itmo.spacemarine.dto.DtoMapper;
import ru.itmo.spacemarine.model.Chapter;
import ru.itmo.spacemarine.repository.ChapterRepository;
import ru.itmo.spacemarine.repository.UserRepository;
import ru.itmo.spacemarine.security.AccessPolicy;
import ru.itmo.spacemarine.security.UserPrincipal;
import ru.itmo.spacemarine.service.event.ChangeEvent;
import ru.itmo.spacemarine.service.exception.BadRequestException;
import ru.itmo.spacemarine.service.exception.ConflictException;
import ru.itmo.spacemarine.service.exception.LinkedObjectsException;
import ru.itmo.spacemarine.service.exception.NotFoundException;

import ru.itmo.spacemarine.tx.SerializableExecutor;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Transactional
public class ChapterService {

    private static final String ENTITY = "chapter";

    @Inject
    private SerializableExecutor serializable;
    @Inject
    private ChapterRepository repository;
    @Inject
    private UserRepository users;
    @Inject
    private AccessPolicy access;
    @Inject
    private UniquenessService uniqueness;
    @Inject
    private Event<ChangeEvent> events;

    public List<ChapterDto> findAll() {
        return repository.findAll().stream().map(DtoMapper::toDto).toList();
    }

    public ChapterDto findById(Integer id) {
        return DtoMapper.toDto(get(id));
    }

    /**
     * Явное создание ордена (страница «Ордены»): занятое название — ошибка 409.
     * Владельцем нового ордена становится тот, кто его создал.
     */
    @Transactional(Transactional.TxType.NOT_SUPPORTED) // транзакцию SERIALIZABLE открывает executor
    public ChapterDto create(ChapterDto dto) {
        return serializable.execute("ChapterService.create", () -> {
            UserPrincipal user = access.requireUser();
            uniqueness.requireChapterNameFree(dto.name, null, "name");
            return DtoMapper.toDto(insert(dto, user));
        });
    }

    /**
     * Орден, заданный полями внутри десантника (форма с «новым орденом», импорт): find-or-create.
     * <ul>
     *   <li>ордена с таким названием нет — создаётся, командующий — текущий пользователь;</li>
     *   <li>есть свой (ADMIN — любой) с той же численностью — используется он;</li>
     *   <li>есть, но с другой численностью — 409: данные противоречат существующему ордену;</li>
     *   <li>есть, но чужой — 409: записать десантника в чужой орден нельзя.</li>
     * </ul>
     * fieldPrefix — префикс полей формы для сообщения ("chapter."), null — без привязки к полю.
     */
    public Chapter findOrCreate(ChapterDto dto, String fieldPrefix) {
        UserPrincipal user = access.requireUser();
        Optional<Chapter> found = uniqueness.findChapterByName(dto.name, null);
        if (found.isEmpty()) {
            return insert(dto, user);
        }
        Chapter c = found.get();
        if (!access.canManage(c)) {
            throw new ConflictException("Орден «" + c.getName() + "» уже существует и принадлежит командующему "
                    + c.getOwner().getLogin() + ". Записать десантника в чужой орден нельзя",
                    field(fieldPrefix, "name"));
        }
        if (c.getMarinesCount() != dto.marinesCount) {
            throw new ConflictException("Орден «" + c.getName() + "» уже есть, его численность " + c.getMarinesCount()
                    + ", а указано " + dto.marinesCount + ". Укажите ту же численность или выберите орден из списка",
                    field(fieldPrefix, "marinesCount"));
        }
        return c;
    }

    private Chapter insert(ChapterDto dto, UserPrincipal user) {
        ChapterDto normalized = copyWithName(dto, Names.normalize(dto.name));
        Chapter saved = repository.save(DtoMapper.toEntity(normalized, users.get(user.id())));
        events.fire(new ChangeEvent(ENTITY, ChangeEvent.Action.CREATED, saved.getId()));
        return saved;
    }

    private static ChapterDto copyWithName(ChapterDto dto, String name) {
        ChapterDto d = new ChapterDto();
        d.name = name;
        d.marinesCount = dto.marinesCount;
        return d;
    }

    private static String field(String prefix, String name) {
        return prefix == null ? null : prefix + name;
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED) // транзакцию SERIALIZABLE открывает executor
    public ChapterDto update(Integer id, ChapterDto dto) {
        return serializable.execute("ChapterService.update", () -> {
            Chapter c = get(id);
            access.requireManage(c);
            Versions.require(dto.version, c.getVersion(), "этот орден");
            repository.forceVersionIncrement(c);
            uniqueness.requireChapterNameFree(dto.name, id, "name");
            c.setName(Names.normalize(dto.name));
            c.setMarinesCount(dto.marinesCount);
            repository.flush(); // конфликт версии — здесь, а не при коммите
            events.fire(new ChangeEvent(ENTITY, ChangeEvent.Action.UPDATED, id));
            return DtoMapper.toDto(c);
        });
    }

    /**
     * Удаление с перепривязкой: если на орден ссылаются десантники,
     * их нужно перевести в орден reassignToId (выбирает пользователь).
     * Командующий может перевести бойцов только в свой орден, администратор — в любой.
     */
    public void delete(Integer id, Integer reassignToId) {
        Chapter c = get(id);
        access.requireManage(c);
        long linked = repository.countMarines(id);
        if (linked > 0) {
            if (reassignToId == null) {
                throw new LinkedObjectsException(
                        "С орденом связано десантников: " + linked + ". Выберите орден для перепривязки", linked);
            }
            if (reassignToId.equals(id)) {
                throw new BadRequestException("Нельзя перепривязать десантников на удаляемый орден");
            }
            access.requireAssignTo(get(reassignToId));
            repository.reassignMarines(id, reassignToId);
            events.fire(new ChangeEvent("spaceMarine", ChangeEvent.Action.UPDATED, null));
        }
        repository.delete(c);
        repository.flush();
        events.fire(new ChangeEvent(ENTITY, ChangeEvent.Action.DELETED, id));
    }

    public Chapter get(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Орден с id=" + id + " не найден"));
    }
}
