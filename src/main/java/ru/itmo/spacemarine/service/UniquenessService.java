package ru.itmo.spacemarine.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import ru.itmo.spacemarine.model.Chapter;
import ru.itmo.spacemarine.model.Coordinates;
import ru.itmo.spacemarine.model.SpaceMarine;
import ru.itmo.spacemarine.repository.ChapterRepository;
import ru.itmo.spacemarine.repository.CoordinatesRepository;
import ru.itmo.spacemarine.repository.SpaceMarineRepository;
import ru.itmo.spacemarine.service.exception.ConflictException;

import java.util.Objects;
import java.util.Optional;

/**
 * Ограничения уникальности предметной области (ЛР2). Проверяются ТОЛЬКО здесь,
 * в бизнес-логике, — в БД соответствующих UNIQUE-ограничений нет:
 * <ol>
 *   <li>название ордена уникально без учёта регистра и лишних пробелов;</li>
 *   <li>пара координат (x, y) уникальна — десантники в одной точке ссылаются на одну запись;</li>
 *   <li>нельзя создать полный дубль десантника (совпадают все поля, кроме id и creationDate;
 *       имя — без учёта регистра).</li>
 * </ol>
 * MANDATORY: проверка имеет смысл только в той же транзакции, что и последующая запись.
 * Схема «проверил — записал» уязвима для гонки при одновременных запросах;
 * как это закрывается уровнем изоляции — см. этап с JMeter.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class UniquenessService {

    @Inject
    private ChapterRepository chapters;
    @Inject
    private CoordinatesRepository coordinates;
    @Inject
    private SpaceMarineRepository marines;

    // ---------- 1. Орден ----------

    /** Орден с таким же названием без учёта регистра, кроме excludeId. */
    public Optional<Chapter> findChapterByName(String name, Integer excludeId) {
        String normalized = Names.normalize(name);
        String key = Names.key(normalized);
        return chapters.findByNameLength(Names.length(normalized)).stream()
                .filter(c -> !Objects.equals(c.getId(), excludeId))
                .filter(c -> Names.key(c.getName()).equals(key))
                .findFirst();
    }

    /** 409, если название занято. field — поле формы для сообщения. */
    public void requireChapterNameFree(String name, Integer excludeId, String field) {
        findChapterByName(name, excludeId).ifPresent(c -> {
            throw new ConflictException("Орден «" + c.getName() + "» уже существует (номер " + c.getId()
                    + ", командующий " + c.getOwner().getLogin() + "). Название ордена должно быть уникальным"
                    + " без учёта регистра", field);
        });
    }

    // ---------- 2. Координаты ----------

    /** -0.0 и 0.0 в Java различаются (Double.equals), а в PostgreSQL равны — приводим к 0.0. */
    public static double normalizeY(double y) {
        return y + 0.0;
    }

    public Optional<Coordinates> findCoordinates(long x, double y, Integer excludeId) {
        return coordinates.findByXY(x, normalizeY(y), excludeId);
    }

    public void requireCoordinatesFree(long x, double y, Integer excludeId) {
        findCoordinates(x, y, excludeId).ifPresent(c -> {
            throw new ConflictException("Точка (" + c.getX() + "; " + c.getY() + ") уже существует — координаты номер "
                    + c.getId() + ". Пара (x, y) должна быть уникальной: выберите существующие координаты");
        });
    }

    // ---------- 3. Полный дубль десантника ----------

    /**
     * Десантник, совпадающий с m во всех полях, кроме id и creationDate.
     * Имя сравнивается без учёта регистра и лишних пробелов, как название ордена:
     * «Titus» и «titus» с одинаковыми данными — один и тот же десантник.
     */
    public Optional<SpaceMarine> findDuplicate(SpaceMarine m, Integer excludeId) {
        String normalized = Names.normalize(m.getName());
        String key = Names.key(normalized);
        return marines.findDuplicateCandidates(m, Names.length(normalized), excludeId).stream()
                .filter(d -> Names.key(d.getName()).equals(key))
                .findFirst();
    }
}
