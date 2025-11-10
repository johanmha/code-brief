package com.codebrief.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents the final processed digest ready to be sent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Digest {
    private String date;
    private List<NewsItem> topUpdates;
    private List<NewsItem> quickMentions;
    private List<NewsItem> communityBuzz;
    private String insight;
    private String summary;
}
