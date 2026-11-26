package com.hrflow.dto;

import java.util.List;

/** DTOs describing the outcome of a CSV employee import. */
public final class ImportDtos {

    private ImportDtos() { }

    public record ImportErrorDto(int rowNumber, String reason, String rawRow) { }

    public record ImportResultDto(
            Long batchId,
            String filename,
            int totalRows,
            int importedRows,
            int failedRows,
            List<ImportErrorDto> errors) { }
}
