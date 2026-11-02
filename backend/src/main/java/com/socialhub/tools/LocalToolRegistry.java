package com.socialhub.tools;

import com.socialhub.armory.ArmoryService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Comprueba las herramientas locales y deja el resultado en la armería del modo real. */
@Component
@RequiredArgsConstructor
public class LocalToolRegistry {

    private final List<LocalTool> tools;
    private final ArmoryService armory;

    public record Result(LocalTool tool, LocalTool.Check check) {}

    public List<Result> checkAll(Consumer<String> log) throws InterruptedException {
        List<Result> results = new ArrayList<>();
        for (LocalTool tool : tools) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            LocalTool.Check check = tool.check();
            armory.registerLocalTool(tool.id(), tool.name(), tool.description(), check.ok(),
                    check.detail() + " · " + tool.license(), tool.capabilities());
            log.accept((check.ok() ? "OK · " : "Error · ") + tool.name() + ": " + check.detail());
            results.add(new Result(tool, check));
        }
        return results;
    }
}
