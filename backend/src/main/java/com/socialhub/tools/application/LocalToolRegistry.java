package com.socialhub.tools.application;

import com.socialhub.armory.ArmoryRegistry;
import com.socialhub.tools.LocalTools;
import com.socialhub.tools.domain.LocalTool;
import com.socialhub.tools.domain.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Comprueba las herramientas locales y deja el resultado en la armería del modo real. */
@Service
@RequiredArgsConstructor
class LocalToolRegistry implements LocalTools {

    private final List<LocalTool> tools;
    private final ArmoryRegistry armory;

    @Override
    public List<ToolResult> checkAll(Consumer<String> log) throws InterruptedException {
        List<ToolResult> results = new ArrayList<>();
        for (LocalTool tool : tools) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            LocalTool.Check check = tool.check();
            armory.registerLocalTool(tool.id(), tool.name(), tool.description(), check.ok(),
                    check.detail() + " · " + tool.license(), tool.capabilities());
            log.accept((check.ok() ? "OK · " : "Error · ") + tool.name() + ": " + check.detail());
            results.add(new ToolResult(tool, check));
        }
        return results;
    }
}
