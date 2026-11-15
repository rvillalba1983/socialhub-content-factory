package com.socialhub.operations.domain;

import java.util.List;

/** Una página de operaciones, de la más reciente a la más antigua, y cuántas hay en total. */
public record OperationPage(List<ExternalOperation> items, long total) {}
