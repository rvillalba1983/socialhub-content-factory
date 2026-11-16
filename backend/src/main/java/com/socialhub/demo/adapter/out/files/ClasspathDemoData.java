package com.socialhub.demo.adapter.out.files;

import com.socialhub.demo.domain.DemoData;
import com.socialhub.demo.domain.DemoDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Lee {@code demo/demo-data.json} del jar. */
@Component
class ClasspathDemoData implements DemoDataSource {

    static final String RESOURCE = "demo/demo-data.json";

    private final ObjectMapper mapper;

    ClasspathDemoData(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public DemoData read() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return mapper.readValue(in, DemoData.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
