package com.socialhub.ai.domain;

import java.util.List;

/** Una página de envíos, del más reciente al más antiguo, y cuántos hay en total. */
public record AiRequestPage(List<AiRequest> items, long total) {}
