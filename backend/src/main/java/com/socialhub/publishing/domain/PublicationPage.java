package com.socialhub.publishing.domain;

import java.util.List;

public record PublicationPage(List<Publication> items, long offset, int limit, long total) {}
