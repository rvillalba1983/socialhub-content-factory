package com.socialhub.tools.domain;

/** El resultado de comprobar una herramienta local. */
public record ToolResult(LocalTool tool, LocalTool.Check check) {}
