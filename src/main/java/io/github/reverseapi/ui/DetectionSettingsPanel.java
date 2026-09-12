package io.github.reverseapi.ui;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import io.github.reverseapi.detection.DetectionSettings;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class DetectionSettingsPanel extends JPanel {
    private final DetectionSettings settings;
    private final Consumer<String> status;
    private final JComboBox<Sensitivity> sensitivity = new JComboBox<>(Sensitivity.values());
    private final JLabel sensitivityHint = new JLabel();
    private final JCheckBox json = new JCheckBox("Detect JSON APIs");
    private final JCheckBox graphQl = new JCheckBox("Detect GraphQL endpoints and request bodies");
    private final JCheckBox xml = new JCheckBox("Detect XML and SOAP APIs");
    private final JCheckBox apiPaths = new JCheckBox("Recognize API-style paths such as /api, /rest and /v1");
    private final JCheckBox staticSuppression = new JCheckBox("Ignore obvious static assets and media");
    private final JCheckBox analyticsSuppression = new JCheckBox("Ignore common analytics and beacon traffic");
    private final JCheckBox lowConfidence = new JCheckBox("Show possible API matches for manual review");
    private final JCheckBox xhr = new JCheckBox("Use XMLHttpRequest headers as a detection signal");
    private final JCheckBox regexMode = new JCheckBox("Interpret filters as RE2 regular expressions (expert mode)");
    private final JTextField allowedHosts = field();
    private final JTextField forceInclude = field();
    private final JTextField excludeUrls = field();
    private final JTextField apiKeys = field();
    private final JTextField additionalTypes = field();
    private final List<Component> advancedComponents = new ArrayList<>();
    private final JButton advancedToggle = new JButton("Show advanced settings");

    public DetectionSettingsPanel(DetectionSettings settings, Consumer<String> status) {
        super(new GridBagLayout());
        this.settings = settings; this.status = status;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        int row = 0;
        addLabel("Detection", row++);
        addCompactRow("Sensitivity", sensitivity, row++);
        sensitivityHint.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        addWide(sensitivityHint, row++);
        for (JCheckBox box : List.of(json, graphQl, xml, apiPaths)) addWide(box, row++);

        addLabel("Noise reduction and review", row++);
        for (JCheckBox box : List.of(staticSuppression, analyticsSuppression, lowConfidence)) addWide(box, row++);

        advancedToggle.addActionListener(event -> setAdvancedVisible(!advancedComponents.get(0).isVisible()));
        addWide(advancedToggle, row++);
        row = addAdvancedLabel("Advanced detection signals", row);
        row = addAdvancedWide(xhr, row);
        row = addAdvancedLabel("Traffic filters", row);
        row = addAdvancedWide(regexMode, row);
        row = addAdvancedRow("Hosts to include", allowedHosts, row);
        row = addAdvancedHint("Example: api.example.com, *.example.com", row);
        row = addAdvancedRow("URLs to always include", forceInclude, row);
        row = addAdvancedHint("Example: */graphql, */api/*", row);
        row = addAdvancedRow("URLs to ignore", excludeUrls, row);
        row = addAdvancedHint("Example: */health*, */metrics*", row);
        row = addAdvancedLabel("Custom protocol metadata", row);
        row = addAdvancedRow("Additional JSON media types", additionalTypes, row);
        row = addAdvancedHint("Comma separated, for example: application/vnd.company+json", row);
        row = addAdvancedRow("Additional API-key header names", apiKeys, row);
        row = addAdvancedHint("Comma separated, for example: X-Company-Key, X-Tenant-Token", row);

        JLabel timingHint = new JLabel("Settings apply to newly captured traffic; existing rows are not rescored.");
        addWide(timingHint, row++);
        JPanel buttons = new JPanel();
        JButton apply = new JButton("Apply to new traffic");
        JButton reset = new JButton("Reset defaults");
        apply.addActionListener(event -> apply());
        reset.addActionListener(event -> {
            settings.reset(); regexMode.setSelected(false); load();
            status.accept("Detection settings reset to defaults");
        });
        buttons.add(apply); buttons.add(reset); addWide(buttons, row++);
        GridBagConstraints filler = constraints(0, row); filler.weighty = 1; filler.fill = GridBagConstraints.BOTH;
        add(new JPanel(), filler);

        sensitivity.addActionListener(event -> updateSensitivityHint());
        load(); setAdvancedVisible(false);
    }

    private void apply() {
        try {
            String allowed = filter(allowedHosts.getText());
            String forced = filter(forceInclude.getText());
            String excluded = filter(excludeUrls.getText());
            validateRegex(allowed); validateRegex(forced); validateRegex(excluded);
            Sensitivity selected = (Sensitivity) sensitivity.getSelectedItem();
            settings.minimumConfidence(selected == null ? Sensitivity.BALANCED.threshold : selected.threshold);
            settings.jsonContentTypes(json.isSelected()); settings.jsonBodies(json.isSelected());
            settings.graphQl(graphQl.isSelected()); settings.xml(xml.isSelected()); settings.apiPaths(apiPaths.isSelected());
            settings.xhr(xhr.isSelected()); settings.suppressStatic(staticSuppression.isSelected());
            settings.suppressAnalytics(analyticsSuppression.isSelected()); settings.showLowConfidence(lowConfidence.isSelected());
            settings.allowedHostRegex(allowed); settings.forceIncludeUrlRegex(forced); settings.excludeUrlRegex(excluded);
            settings.additionalJsonTypes(additionalTypes.getText()); settings.apiKeyHeaders(apiKeys.getText());
            status.accept("Detection settings applied to new Proxy traffic");
        } catch (PatternSyntaxException e) {
            status.accept("Invalid filter pattern: " + e.getMessage());
        }
    }

    private String filter(String value) { return regexMode.isSelected() ? value.trim() : wildcardListToRegex(value); }

    static String wildcardListToRegex(String value) {
        if (value == null || value.isBlank()) return "";
        StringBuilder result = new StringBuilder("^(?:");
        boolean first = true;
        for (String raw : value.split("[,\\r\\n]+")) {
            String token = raw.trim(); if (token.isEmpty()) continue;
            if (!first) result.append('|'); first = false;
            for (int i = 0; i < token.length(); i++) {
                char c = token.charAt(i);
                if (c == '*') result.append(".*");
                else if (c == '?') result.append('.');
                else {
                    if ("\\.^$|()[]{}+".indexOf(c) >= 0) result.append('\\');
                    result.append(c);
                }
            }
        }
        return first ? "" : result.append(")$").toString();
    }

    private void load() {
        int threshold = settings.minimumConfidence();
        sensitivity.setSelectedItem(threshold <= 10 ? Sensitivity.BROAD : threshold >= 40 ? Sensitivity.STRICT : Sensitivity.BALANCED);
        json.setSelected(settings.jsonContentTypes() || settings.jsonBodies()); graphQl.setSelected(settings.graphQl());
        xml.setSelected(settings.xml()); apiPaths.setSelected(settings.apiPaths()); xhr.setSelected(settings.xhr());
        staticSuppression.setSelected(settings.suppressStatic()); analyticsSuppression.setSelected(settings.suppressAnalytics());
        lowConfidence.setSelected(settings.showLowConfidence()); allowedHosts.setText(settings.allowedHostRegex());
        forceInclude.setText(settings.forceIncludeUrlRegex()); excludeUrls.setText(settings.excludeUrlRegex());
        additionalTypes.setText(settings.additionalJsonTypes()); apiKeys.setText(settings.apiKeyHeaders());
        if (!allowedHosts.getText().isBlank() || !forceInclude.getText().isBlank() || !excludeUrls.getText().isBlank()) regexMode.setSelected(true);
        updateSensitivityHint();
    }

    private void updateSensitivityHint() {
        Sensitivity selected = (Sensitivity) sensitivity.getSelectedItem();
        sensitivityHint.setText(selected == null ? "" : selected.description);
    }

    private void setAdvancedVisible(boolean visible) {
        advancedComponents.forEach(component -> component.setVisible(visible));
        advancedToggle.setText(visible ? "Hide advanced settings" : "Show advanced settings");
        revalidate(); repaint();
    }

    private int addAdvancedLabel(String text, int row) {
        JLabel label = new JLabel(text); label.setBorder(BorderFactory.createEmptyBorder(8, 0, 3, 0));
        addAdvancedWide(label, row); return row + 1;
    }
    private int addAdvancedWide(Component component, int row) {
        advancedComponents.add(component); addWide(component, row); return row + 1;
    }
    private int addAdvancedRow(String label, Component component, int row) {
        JLabel leftLabel = new JLabel(label); advancedComponents.add(leftLabel); advancedComponents.add(component);
        GridBagConstraints left = constraints(0, row); add(leftLabel, left);
        GridBagConstraints right = constraints(1, row); right.anchor = GridBagConstraints.WEST; add(component, right);
        return row + 1;
    }
    private int addAdvancedHint(String text, int row) {
        JLabel hint = new JLabel(text); hint.setBorder(BorderFactory.createEmptyBorder(0, 12, 3, 5));
        return addAdvancedWide(hint, row);
    }
    private void addLabel(String text, int row) {
        JLabel label = new JLabel(text); label.setBorder(BorderFactory.createEmptyBorder(8, 0, 3, 0)); addWide(label, row);
    }
    private void addWide(Component component, int row) {
        GridBagConstraints c = constraints(0, row); c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; add(component, c);
    }
    private void addCompactRow(String label, Component component, int row) {
        GridBagConstraints left = constraints(0, row); add(new JLabel(label), left);
        GridBagConstraints right = constraints(1, row); right.anchor = GridBagConstraints.WEST; add(component, right);
    }
    private static GridBagConstraints constraints(int x, int y) {
        GridBagConstraints c = new GridBagConstraints(); c.gridx = x; c.gridy = y; c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 5, 3, 5); return c;
    }
    private static JTextField field() {
        JTextField field = new JTextField(32);
        field.setMaximumSize(new Dimension(420, field.getPreferredSize().height));
        return field;
    }
    private static void validateRegex(String value) {
        if (value != null && value.length() > 1024) throw new PatternSyntaxException("Filter exceeds 1024 characters", value);
        if (value != null && !value.isBlank()) Pattern.compile(value, Pattern.CASE_INSENSITIVE);
    }

    private enum Sensitivity {
        BROAD("Broad", 10, "Finds weak API signals too. Useful for discovery, with more false positives."),
        BALANCED("Balanced", 25, "Recommended for most testing: captures common APIs while suppressing obvious noise."),
        STRICT("Strict", 40, "Requires stronger API evidence. Useful for noisy targets and focused review.");
        private final String label; private final int threshold; private final String description;
        Sensitivity(String label, int threshold, String description) { this.label = label; this.threshold = threshold; this.description = description; }
        @Override public String toString() { return label; }
    }
}
