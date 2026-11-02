package com.socialhub.ai;

import java.util.List;

/** Un encargo a Claude tal como lo ven los manejadores de cada tipo. */
public record AiJob(String taskId, AiRequestType type, String promotionId, String brandId, String title, String goal,
                    List<String> inputs) {}
