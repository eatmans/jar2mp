package com.z0fsec.jar2mp.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RestorationScore {
    private int overallScore;
    private int sourceScore;
    private int resourceScore;
    private int runtimeScore;
    private int verificationScore;
    private final Map<String, Integer> breakdown = new LinkedHashMap<>();
    private final List<GapItem> topGapItems = new ArrayList<>();

    public int getOverall() {
        return overallScore;
    }

    public void setOverall(int overall) {
        setOverallScore(overall);
    }

    public int getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(int overallScore) {
        this.overallScore = clamp(overallScore);
    }

    public int getSourceScore() {
        return sourceScore;
    }

    public void setSourceScore(int sourceScore) {
        this.sourceScore = clamp(sourceScore);
        breakdown.put("source", Integer.valueOf(this.sourceScore));
    }

    public int getResourceScore() {
        return resourceScore;
    }

    public void setResourceScore(int resourceScore) {
        this.resourceScore = clamp(resourceScore);
        breakdown.put("resource", Integer.valueOf(this.resourceScore));
    }

    public int getRuntimeScore() {
        return runtimeScore;
    }

    public void setRuntimeScore(int runtimeScore) {
        this.runtimeScore = clamp(runtimeScore);
        breakdown.put("runtime", Integer.valueOf(this.runtimeScore));
    }

    public int getVerificationScore() {
        return verificationScore;
    }

    public void setVerificationScore(int verificationScore) {
        this.verificationScore = clamp(verificationScore);
        breakdown.put("verification", Integer.valueOf(this.verificationScore));
    }

    public Map<String, Integer> getBreakdown() {
        return breakdown;
    }

    public List<GapItem> getGaps() {
        return topGapItems;
    }

    public List<GapItem> getTopGapItems() {
        return topGapItems;
    }

    public void setTopGapItems(Collection<GapItem> gapItems) {
        topGapItems.clear();
        if (gapItems != null) {
            topGapItems.addAll(gapItems);
        }
    }

    public void putBucket(String bucket, int score) {
        if (bucket == null) {
            return;
        }
        String normalized = bucket.trim().toLowerCase();
        int clamped = clamp(score);
        breakdown.put(normalized, Integer.valueOf(clamped));
        if ("source".equals(normalized)) {
            sourceScore = clamped;
        } else if ("resource".equals(normalized)) {
            resourceScore = clamped;
        } else if ("runtime".equals(normalized)) {
            runtimeScore = clamped;
        } else if ("verification".equals(normalized)) {
            verificationScore = clamped;
        }
    }

    public void addGap(String category, String detail, int impact) {
        topGapItems.add(new GapItem(category, detail, null, impact));
    }

    public void addTopGapItem(GapItem gapItem) {
        if (gapItem != null) {
            topGapItems.add(gapItem);
        }
    }

    private int clamp(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 100) {
            return 100;
        }
        return value;
    }

    public static class GapItem {
        private String category;
        private String message;
        private String evidence;
        private int penalty;

        public GapItem() {
        }

        public GapItem(String category, String message, int penalty) {
            this(category, message, null, penalty);
        }

        public GapItem(String category, String message, String evidence, int penalty) {
            this.category = category;
            this.message = message;
            this.evidence = evidence;
            this.penalty = penalty;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getEvidence() {
            return evidence;
        }

        public void setEvidence(String evidence) {
            this.evidence = evidence;
        }

        public int getPenalty() {
            return penalty;
        }

        public void setPenalty(int penalty) {
            this.penalty = penalty;
        }

        public String getDetail() {
            return message;
        }

        public void setDetail(String detail) {
            this.message = detail;
        }

        public int getImpact() {
            return penalty;
        }

        public void setImpact(int impact) {
            this.penalty = impact;
        }
    }
}
