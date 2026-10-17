package ru.itmo.spacemarine.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import ru.itmo.spacemarine.dto.ImportOperationDto;
import ru.itmo.spacemarine.dto.PageDto;
import ru.itmo.spacemarine.model.ImportOperation;
import ru.itmo.spacemarine.repository.ImportOperationRepository;
import ru.itmo.spacemarine.repository.UserRepository;
import ru.itmo.spacemarine.security.AccessPolicy;
import ru.itmo.spacemarine.security.UserPrincipal;
import ru.itmo.spacemarine.service.event.ChangeEvent;
import ru.itmo.spacemarine.service.exception.BadRequestException;
import ru.itmo.spacemarine.service.exception.NotFoundException;

/**
 * История импорта. Запись создаётся и завершается в ОТДЕЛЬНЫХ транзакциях
 * (REQUIRES_NEW), независимых от транзакции самого импорта: если импорт откатился,
 * запись со статусом FAILED всё равно остаётся в истории.
 * Методы вызываются из другого бина (ImportFacade) — иначе CDI-перехватчик
 * @Transactional не сработал бы (вызов внутри одного бина идёт мимо прокси).
 */
@ApplicationScoped
public class ImportHistoryService {

    private static final String ENTITY = "import";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_ERROR_LENGTH = 4000;

    @Inject
    private ImportOperationRepository repository;
    @Inject
    private UserRepository users;
    @Inject
    private AccessPolicy access;
    @Inject
    private Event<ChangeEvent> events;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int start(int userId, String fileName) {
        ImportOperation op = repository.save(new ImportOperation(users.get(userId), fileName));
        events.fire(new ChangeEvent(ENTITY, ChangeEvent.Action.CREATED, op.getId()));
        return op.getId();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void succeed(int operationId, int added, int skipped) {
        get(operationId).succeed(added, skipped);
        events.fire(new ChangeEvent(ENTITY, ChangeEvent.Action.UPDATED, operationId));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void fail(int operationId, String message) {
        String m = message == null ? "Неизвестная ошибка" : message;
        get(operationId).fail(m.length() > MAX_ERROR_LENGTH ? m.substring(0, MAX_ERROR_LENGTH - 1) + "…" : m);
        events.fire(new ChangeEvent(ENTITY, ChangeEvent.Action.UPDATED, operationId));
    }

    /** Обычный пользователь видит только свои операции, администратор — все. */
    @Transactional
    public PageDto<ImportOperationDto> page(int page, int size) {
        UserPrincipal user = access.requireUser();
        if (page < 0) throw new BadRequestException("page не может быть отрицательным");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new BadRequestException("size должен быть от 1 до " + MAX_PAGE_SIZE);
        Integer filter = user.isAdmin() ? null : user.id();
        return new PageDto<>(
                repository.findPage(filter, page, size).stream().map(ImportHistoryService::toDto).toList(),
                page, size, repository.count(filter));
    }

    private ImportOperation get(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Операция импорта с id=" + id + " не найдена"));
    }

    private static ImportOperationDto toDto(ImportOperation o) {
        ImportOperationDto d = new ImportOperationDto();
        d.id = o.getId();
        d.status = o.getStatus();
        d.userId = o.getUser().getId();
        d.userLogin = o.getUser().getLogin();
        d.fileName = o.getFileName();
        d.addedCount = o.getAddedCount();
        d.skippedCount = o.getSkippedCount();
        d.errorMessage = o.getErrorMessage();
        d.startedAt = o.getStartedAt();
        d.finishedAt = o.getFinishedAt();
        return d;
    }
}
