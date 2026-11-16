package com.socialhub.ai.application;

import com.socialhub.ai.AiReports;
import com.socialhub.ai.AiTypeHandler;
import com.socialhub.ai.domain.AiJob;
import com.socialhub.ai.domain.AiRequestType;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Investigación: Claude devuelve un informe en Markdown que se guarda en Informes. */
@Component
@RequiredArgsConstructor
class ResearchHandler implements AiTypeHandler {

    private final AiReports reports;

    @Override public AiRequestType type() { return AiRequestType.RESEARCH; }

    @Override
    public java.util.Map<String, Object> inputs(AiJob job) {
        return java.util.Map.of("expected_outputs", "report (Markdown, con conclusiones accionables) y summary. Cita fuentes en «sources».");
    }

    @Override
    public String apply(AiJob job, JsonNode outputs, JsonNode result) {
        return reports.save(job, "investigacion-" + job.title(), outputs, result);
    }
}
