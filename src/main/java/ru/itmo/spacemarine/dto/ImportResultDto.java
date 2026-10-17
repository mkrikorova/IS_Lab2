package ru.itmo.spacemarine.dto;

import java.util.List;

/** Ответ на успешный импорт. */
public class ImportResultDto {
    public int operationId;
    public String status = "SUCCESS";
    public int added;
    public int skipped;
    /** «Запись 4: у вас уже есть такой десантник — номер 12 «Titus»» */
    public List<String> skippedDetails;

    public ImportResultDto() {
    }

    public ImportResultDto(int operationId, int added, List<String> skippedDetails) {
        this.operationId = operationId;
        this.added = added;
        this.skipped = skippedDetails.size();
        this.skippedDetails = skippedDetails;
    }
}
