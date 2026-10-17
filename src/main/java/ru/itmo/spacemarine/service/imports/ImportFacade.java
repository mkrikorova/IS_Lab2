package ru.itmo.spacemarine.service.imports;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import ru.itmo.spacemarine.controller.mapper.ErrorMapping;
import ru.itmo.spacemarine.dto.ErrorDto;
import ru.itmo.spacemarine.dto.ImportResultDto;
import ru.itmo.spacemarine.dto.SpaceMarineRequest;
import ru.itmo.spacemarine.security.AccessPolicy;
import ru.itmo.spacemarine.security.UserPrincipal;
import ru.itmo.spacemarine.service.ImportHistoryService;
import ru.itmo.spacemarine.service.exception.ImportException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Сценарий импорта целиком. Сам фасад работает ВНЕ транзакции (NOT_SUPPORTED),
 * а транзакции открывают вызываемые им бины:
 * <pre>
 *  1. history.start(...)        REQUIRES_NEW  -> запись IN_PROGRESS закоммичена сразу
 *  2. разбор и проверка файла    без транзакции (в БД ничего не пишется)
 *  3. importService.importAll   REQUIRED      -> одна транзакция на все записи: всё или ничего
 *  4. history.succeed / fail    REQUIRES_NEW  -> итог записывается, даже если шаг 3 откатился
 * </pre>
 * Если бы история писалась в транзакции импорта, при ошибке она откатилась бы вместе
 * с ним, и в истории не осталось бы неуспешных операций.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.NOT_SUPPORTED)
public class ImportFacade {

    @Inject
    private AccessPolicy access;
    @Inject
    private ImportHistoryService history;
    @Inject
    private ImportService importService;
    @Inject
    private Validator validator;

    public ImportResultDto importFile(String fileName, byte[] content) {
        UserPrincipal user = access.requireUser();
        int operationId = history.start(user.id(), fileName);
        try {
            List<SpaceMarineRequest> records = ImportParser.parse(content);
            validate(records);
            ImportService.Result result = importService.importAll(records);
            history.succeed(operationId, result.added(), result.skipped().size());
            return new ImportResultDto(operationId, result.added(), result.skipped());
        } catch (RuntimeException e) {
            ErrorDto error = ErrorMapping.describe(e);
            history.fail(operationId, summary(error));
            throw new ImportException(error.status, "Импорт номер " + operationId + ": " + error.message, error.details);
        }
    }

    /** Ограничения предметной области — те же аннотации, что и при создании через форму. */
    private void validate(List<SpaceMarineRequest> records) {
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            Set<ConstraintViolation<SpaceMarineRequest>> violations = validator.validate(records.get(i));
            List<String> forRecord = new ArrayList<>();
            for (ConstraintViolation<SpaceMarineRequest> v : violations) {
                forRecord.add("Запись " + (i + 1) + ": " + v.getPropertyPath() + " — " + v.getMessage());
            }
            forRecord.sort(null); // внутри записи — по имени поля, записи — по порядку в файле
            errors.addAll(forRecord);
        }
        if (!errors.isEmpty()) {
            throw new ImportException(400, "Записи не прошли проверку, ни один объект не создан", errors);
        }
    }

    private static String summary(ErrorDto e) {
        StringBuilder sb = new StringBuilder(e.message);
        if (e.details != null && !e.details.isEmpty()) {
            sb.append(": ").append(String.join("; ", e.details.subList(0, Math.min(5, e.details.size()))));
            if (e.details.size() > 5) sb.append("; … ещё ").append(e.details.size() - 5);
        }
        return sb.toString();
    }
}
