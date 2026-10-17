package com.wikipane.view;

import java.util.*;

/** Per-page diagnostics. Composition runs on a worker; bounded visual findings may arrive on EDT. */
public final class CompositionAudit {
    public enum Outcome { REPAIRED, WARNING, ERROR }
    public static final class Finding {
        public final String rule, unit, detail;
        public final Outcome outcome;
        Finding(String rule, String unit, Outcome outcome, String detail) {
            this.rule = rule;
            this.unit = unit;
            this.outcome = outcome;
            this.detail = detail;
        }
    }
    public final int expectedUnits, referenceDefinitions;
    private int coveredUnits;
    private int measuredWidth, measuredComponents, inspectionCount;
    private boolean truncated;
    private final LinkedHashMap<String, Finding> findings = new LinkedHashMap<>();
    public CompositionAudit(int expectedUnits, int references) {
        this.expectedUnits = expectedUnits;
        referenceDefinitions = references;
    }
    public synchronized void add(String rule, String unit, Outcome outcome, String detail) {
        if (findings.size() >= 256) {
            truncated = true;
            return;
        }
        if (findings.size() < 256)
            findings.putIfAbsent(
                rule + "\0" + unit + "\0" + detail, new Finding(rule, unit, outcome, detail));
    }
    public synchronized void measured(int width, int components) {
        measuredWidth = width;
        measuredComponents = components;
        inspectionCount++;
    }
    synchronized void covered(int count) {
        coveredUnits = count;
    }
    public synchronized List<Finding> findings() {
        return List.copyOf(findings.values());
    }
    public synchronized boolean valid() {
        return coveredUnits == expectedUnits
            && findings.values().stream().noneMatch(f -> f.outcome == Outcome.ERROR);
    }
    public synchronized String summary() {
        StringBuilder out = new StringBuilder("Article composition audit\n");
        out.append("Semantic units accounted for: ")
            .append(coveredUnits)
            .append('/')
            .append(expectedUnits)
            .append("\nReference definitions preserved: ")
            .append(referenceDefinitions)
            .append("\nComposition: ")
            .append(valid() ? "valid" : "needs review")
            .append("\nVisual inspection: ")
            .append(inspectionCount == 0
                    ? "not measured"
                    : measuredWidth + " px, " + measuredComponents + " materialized components")
            .append("\nWarnings: ")
            .append(findings.values().stream().filter(f -> f.outcome == Outcome.WARNING).count())
            .append("\nVisual checks cover materialized components at measured widths only.\n");
        if (truncated)
            out.append("Additional diagnostic entries omitted after 256 findings.\n");
        for (Finding finding : findings.values())
            out.append('\n')
                .append(finding.outcome)
                .append("  ")
                .append(finding.rule)
                .append(" [")
                .append(finding.unit)
                .append("] ")
                .append(finding.detail);
        return out.toString();
    }
}
