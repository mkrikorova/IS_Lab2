package ru.itmo.spacemarine.service.imports;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import ru.itmo.spacemarine.dto.SpaceMarineRequest;
import ru.itmo.spacemarine.model.SpaceMarine;
import ru.itmo.spacemarine.service.SpaceMarineService;
import ru.itmo.spacemarine.service.event.ChangeEvent;
import ru.itmo.spacemarine.service.exception.AccessDeniedException;
import ru.itmo.spacemarine.service.exception.ConflictException;
import ru.itmo.spacemarine.service.exception.ImportException;
import ru.itmo.spacemarine.service.exception.NotFoundException;

import ru.itmo.spacemarine.tx.SerializableExecutor;
import java.util.ArrayList;
import java.util.List;

/**
 * Сохранение всех записей импорта в ОДНОЙ транзакции: либо создаются все объекты,
 * либо (при любой ошибке) транзакция откатывается целиком и не создаётся ни один —
 * ни десантник, ни орден, ни координаты.
 * Откат происходит автоматически: @Transactional откатывает транзакцию при RuntimeException.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class ImportService {

    @Inject
    private SerializableExecutor serializable;
    @Inject
    private SpaceMarineService marines;
    @Inject
    private Event<ChangeEvent> events;

    @Transactional(Transactional.TxType.NOT_SUPPORTED) // транзакцию SERIALIZABLE открывает executor
    public Result importAll(List<SpaceMarineRequest> records) {
        return serializable.execute("ImportService.importAll", () -> {
            int added = 0;
            List<String> skipped = new ArrayList<>();
            for (int i = 0; i < records.size(); i++) {
                String rec = "Запись " + (i + 1);
                SpaceMarineService.ImportOutcome outcome;
                try {
                    outcome = marines.importOne(records.get(i));
                } catch (ConflictException e) {
                    throw new ImportException(409, "Импорт отменён: запись противоречит данным в системе."
                            + " Ни один объект не создан", List.of(rec + ": " + e.getMessage()));
                } catch (AccessDeniedException | NotFoundException e) {
                    throw new ImportException(403, "Импорт отменён. Ни один объект не создан",
                            List.of(rec + ": " + e.getMessage()));
                }
                if (outcome.created()) {
                    added++;
                } else {
                    SpaceMarine d = outcome.marine();
                    skipped.add(rec + ": такой десантник уже есть — номер " + d.getId() + " «" + d.getName() + "», пропущена");
                }
            }
            if (added > 0) {
                // одно уведомление на весь импорт; уйдёт клиентам только после коммита
                events.fire(new ChangeEvent("spaceMarine", ChangeEvent.Action.CREATED, null));
            }
            return new Result(added, skipped);
        });
    }

    public record Result(int added, List<String> skipped) {
    }
}
