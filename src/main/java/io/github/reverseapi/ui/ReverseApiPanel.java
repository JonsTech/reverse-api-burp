package io.github.reverseapi.ui;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import io.github.reverseapi.export.OpenApiExporter;
import io.github.reverseapi.model.ApiOperation;
import io.github.reverseapi.model.CaptureStore;
import io.github.reverseapi.model.CapturedExchange;
import io.github.reverseapi.openapi.GeneratedOpenApi;
import io.github.reverseapi.openapi.OpenApiGenerator;
import io.github.reverseapi.traffic.TrafficCaptureHandler;
import io.github.reverseapi.traffic.ProxyHistoryImporter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.net.URI;
import java.util.Map;

public final class ReverseApiPanel extends JPanel {
    private final CaptureStore store;
    private final TrafficCaptureHandler capture;
    private final ProxyHistoryImporter historyImporter;
    private final EndpointTableModel tableModel;
    private final JTable table;
    private final HttpRequestEditor request;
    private final HttpResponseEditor response;
    private final JPanel requestPanel = new JPanel(new CardLayout());
    private final JPanel responsePanel = new JPanel(new CardLayout());
    private final JTextArea yaml = textArea();
    private final JTextArea json = textArea();
    private final JTextArea postman = textArea();
    private final JTextArea details = textArea();
    private final JLabel status = new JLabel("Capturing new in-scope Proxy traffic. Import Proxy history to include older traffic.");
    private final JButton captureButton = new JButton("Auto Capture ON");
    private final JCheckBox obfuscateExport = new JCheckBox("Obfuscate generated examples", true);
    private final JComboBox<ServerChoice> generationServer = new JComboBox<>();
    private final OpenApiGenerator generator;
    private final OpenApiExporter exporter = new OpenApiExporter();
    private boolean generating;
    private long revision;
    private boolean closed;
    private boolean updatingGenerationServers;
    private GeneratedOpenApi generated = new GeneratedOpenApi("", "");
    private final Timer refreshTimer;
    private final java.util.concurrent.atomic.AtomicBoolean dirty = new java.util.concurrent.atomic.AtomicBoolean();

    private static final String SELECT_ENDPOINT = "Select an endpoint above to view its captured data.";
    private static final String OPENAPI_EMPTY = "No OpenAPI preview yet.\n\nCapture or import traffic, then click Generate OpenAPI.";
    private static final String OPENAPI_STALE = "The OpenAPI preview is out of date.\n\nClick Generate OpenAPI to refresh it.";
    private static final String POSTMAN_EMPTY = "No Postman preview yet.\n\nCapture or import traffic, then click Generate Postman.";
    private static final String POSTMAN_STALE = "The Postman preview is out of date.\n\nClick Generate Postman to refresh it.";

    public ReverseApiPanel(CaptureStore store, TrafficCaptureHandler capture, ProxyHistoryImporter historyImporter,
                           UserInterface userInterface) {
        super(new BorderLayout(6, 6));
        this.store = store; this.capture = capture; this.historyImporter = historyImporter;
        this.generator = new OpenApiGenerator(store.settings());
        this.request = userInterface.createHttpRequestEditor(EditorOptions.READ_ONLY);
        this.response = userInterface.createHttpResponseEditor(EditorOptions.READ_ONLY);
        installMessageEditor(requestPanel, request.uiComponent());
        installMessageEditor(responsePanel, response.uiComponent());
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        tableModel = new EndpointTableModel(store);
        table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setMaxWidth(65);
        table.getColumnModel().getColumn(2).setMaxWidth(75);
        table.getColumnModel().getColumn(5).setMaxWidth(65);
        table.getColumnModel().getColumn(8).setMaxWidth(60);
        table.getSelectionModel().addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) showSelection(); });
        installTableMenu();

        add(toolbar(), BorderLayout.NORTH);
        JTabbedPane previewTabs = new JTabbedPane();
        previewTabs.addTab("Request", requestPanel);
        previewTabs.addTab("Response", responsePanel);
        previewTabs.addTab("OpenAPI YAML", new JScrollPane(yaml));
        previewTabs.addTab("OpenAPI JSON", new JScrollPane(json));
        previewTabs.addTab("Postman JSON", new JScrollPane(postman));
        previewTabs.addTab("Details / reasoning", new JScrollPane(details));
        previewTabs.addTab("Detection settings", new JScrollPane(new DetectionSettingsPanel(store.settings(), message -> { status.setText(message); store.changed(); })));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), previewTabs);
        split.setResizeWeight(0.55); split.setOneTouchExpandable(true);
        add(split, BorderLayout.CENTER); add(status, BorderLayout.SOUTH);

        refreshTimer = new Timer(500, event -> { if (dirty.getAndSet(false)) refreshFromStore(); });
        store.addListener(() -> dirty.set(true));
        refreshTimer.start();
        showMessageEditor(requestPanel, false); showMessageEditor(responsePanel, false); details.setText(SELECT_ENDPOINT);
        yaml.setText(OPENAPI_EMPTY); json.setText(OPENAPI_EMPTY); postman.setText(POSTMAN_EMPTY);
        refreshGenerationServers();
    }

    private JPanel toolbar() {
        JToolBar bar = new JToolBar(); bar.setFloatable(false);
        captureButton.addActionListener(event -> {
            capture.captureEnabled(!capture.captureEnabled());
            captureButton.setText(capture.captureEnabled() ? "Auto Capture ON" : "Auto Capture OFF");
            status.setText(capture.captureEnabled() ? "Passive Proxy capture resumed" : "Capture paused");
        });
        JCheckBox scope = new JCheckBox("Only include Burp in-scope traffic", true);
        scope.addActionListener(event -> capture.onlyInScope(scope.isSelected()));
        JButton importHistory = new JButton("Import Proxy history");
        importHistory.setToolTipText("Scan Proxy history entries from before Reverse API was loaded, using the current scope filter.");
        importHistory.addActionListener(event -> importEarlierHistory(importHistory));
        JButton clear = new JButton("Clear"); clear.addActionListener(event -> { historyImporter.reset(); capture.clear(); });
        JButton generate = new JButton("Generate OpenAPI"); generate.addActionListener(event -> regenerate());
        JButton generatePostman = new JButton("Generate Postman");
        generatePostman.addActionListener(event -> regenerate(() -> {}, true));
        JButton exportYaml = new JButton("Export YAML"); exportYaml.addActionListener(event -> { regenerate(() -> exporter.save(this, generated.yaml(), "yaml", "OpenAPI YAML")); });
        JButton exportJson = new JButton("Export JSON"); exportJson.addActionListener(event -> { regenerate(() -> exporter.save(this, generated.json(), "json", "OpenAPI JSON")); });
        JButton exportPostman = new JButton("Export Postman");
        exportPostman.addActionListener(event -> regenerate(() -> exporter.save(this, generated.json(), "json", "Postman Collection 2.1"), true));
        generationServer.setToolTipText("Choose which captured server is included in generated OpenAPI and Postman output.");
        generationServer.setMaximumSize(new Dimension(320, generationServer.getPreferredSize().height));
        generationServer.addActionListener(event -> {
            if (updatingGenerationServers) return;
            revision++;
            boolean empty = tableModel.getRowCount() == 0;
            generated = new GeneratedOpenApi("", "");
            yaml.setText(empty ? OPENAPI_EMPTY : OPENAPI_STALE);
            json.setText(empty ? OPENAPI_EMPTY : OPENAPI_STALE);
            postman.setText(empty ? POSTMAN_EMPTY : POSTMAN_STALE);
            ServerChoice choice = (ServerChoice) generationServer.getSelectedItem();
            status.setText(choice == null || choice.url() == null ? "Capture traffic before generating output"
                    : "Generation will include only " + choice.url());
        });
        obfuscateExport.setToolTipText("Checked: blank generated preview and export example values. Unchecked: include captured values, including credentials. Request/response views always show captured data.");
        obfuscateExport.addActionListener(event -> {
            revision++;
            generated = new GeneratedOpenApi("", ""); yaml.setText(OPENAPI_STALE); json.setText(OPENAPI_STALE); postman.setText(POSTMAN_STALE);
            status.setText(obfuscateExport.isSelected() ? "Exports will obfuscate example values" : "Exports will include captured values, including credentials");
        });
        bar.add(captureButton); bar.addSeparator(); bar.add(scope); bar.add(importHistory); bar.addSeparator(); bar.add(clear);
        JToolBar output = new JToolBar(); output.setFloatable(false);
        output.add(new JLabel("Output settings: ")); output.add(new JLabel("Server ")); output.add(generationServer);
        output.addSeparator(); output.add(obfuscateExport);
        JToolBar actions = new JToolBar(); actions.setFloatable(false);
        actions.add(new JLabel("Preview: ")); actions.add(generate); actions.add(generatePostman); actions.addSeparator();
        actions.add(new JLabel("Export: ")); actions.add(exportYaml); actions.add(exportJson); actions.add(exportPostman);
        JPanel toolbar = new JPanel(new java.awt.GridLayout(3, 1));
        toolbar.add(bar); toolbar.add(output); toolbar.add(actions);
        return toolbar;
    }

    private void refreshGenerationServers() {
        ServerChoice selected = (ServerChoice) generationServer.getSelectedItem();
        var urls = store.operations().stream().map(ServerChoice::serverUrl).distinct().toList();
        updatingGenerationServers = true;
        try {
            generationServer.removeAllItems();
            if (urls.isEmpty()) generationServer.addItem(new ServerChoice(null));
            else urls.forEach(url -> generationServer.addItem(new ServerChoice(url)));
            if (selected != null && urls.contains(selected.url())) generationServer.setSelectedItem(selected);
            else generationServer.setSelectedIndex(0); // Default to the first server observed.
            generationServer.setEnabled(urls.size() > 1);
        } finally {
            updatingGenerationServers = false;
        }
    }

    private record ServerChoice(String url) {
        private static String serverUrl(ApiOperation operation) {
            CapturedExchange sample = operation.latest();
            return sample.scheme() + "://" + sample.authority();
        }
        private boolean includes(ApiOperation operation) { return url != null && url.equals(serverUrl(operation)); }
        @Override public String toString() { return url == null ? "No captured servers" : url; }
    }

    private void refreshFromStore() {
        revision++;
        int selectedModel = selectedModelRow();
        tableModel.refresh();
        refreshGenerationServers();
        if (selectedModel >= 0 && selectedModel < tableModel.getRowCount()) {
            int view = table.convertRowIndexToView(selectedModel); if (view >= 0) table.setRowSelectionInterval(view, view);
        }
        showSelection();
        generated = new GeneratedOpenApi("", "");
        boolean empty = tableModel.getRowCount() == 0;
        yaml.setText(empty ? OPENAPI_EMPTY : OPENAPI_STALE);
        json.setText(empty ? OPENAPI_EMPTY : OPENAPI_STALE);
        postman.setText(empty ? POSTMAN_EMPTY : POSTMAN_STALE);
        status.setText("Traffic updated; click Generate OpenAPI to refresh its preview. Dropped observations: " + capture.dropped());
    }

    public void close() { closed = true; refreshTimer.stop(); }

    private void regenerate() { regenerate(() -> {}); }

    private void regenerate(Runnable after) { regenerate(after, false); }

    private void regenerate(Runnable after, boolean postmanExport) {
        if (generating || closed) return;
        if (table.isEditing() && !table.getCellEditor().stopCellEditing()) return;
        if (dirty.getAndSet(false)) refreshFromStore();
        long generation = revision;
        boolean obfuscate = obfuscateExport.isSelected();
        ServerChoice serverChoice = (ServerChoice) generationServer.getSelectedItem();
        var snapshot = store.operations().stream()
                .filter(operation -> serverChoice != null && serverChoice.includes(operation))
                .map(ApiOperation::snapshot).toList();
        generating = true;
        status.setText(postmanExport ? "Generating Postman collection..." : "Generating OpenAPI...");
        new javax.swing.SwingWorker<GeneratedOpenApi, Void>() {
            @Override protected GeneratedOpenApi doInBackground() throws Exception { return postmanExport ? new GeneratedOpenApi("", new io.github.reverseapi.export.PostmanGenerator(store.settings()).generate(snapshot, obfuscate))
                    : generator.generate(snapshot, obfuscate); }
            @Override protected void done() {
                generating = false;
                if (closed) return;
                if (generation != revision || dirty.get()) {
                    status.setText("Traffic changed during generation; pause capture and click Generate OpenAPI again"); return;
                }
                try {
                    generated = get();
                    if (postmanExport) { postman.setText(generated.json()); postman.setCaretPosition(0); }
                    else { yaml.setText(generated.yaml()); json.setText(generated.json()); yaml.setCaretPosition(0); json.setCaretPosition(0); }
                    status.setText("Generated " + snapshot.stream().filter(ApiOperation::included).count()
                            + " operations. Dropped observations: " + capture.dropped());
                    after.run();
                } catch (Exception failure) {
                    generated = new GeneratedOpenApi("", "");
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    // Generator errors deliberately omit captured values.
                    String message = "Generation failed: " + (cause instanceof IllegalArgumentException ? cause.getMessage() : "Unable to generate document");
                    if (postmanExport) postman.setText(message); else { yaml.setText(message); json.setText(message); }
                    status.setText(message);
                }
            }
        }.execute();
    }

    private void importEarlierHistory(JButton button) {
        if (closed || !button.isEnabled()) return;
        button.setEnabled(false); status.setText("Importing Proxy history...");
        new javax.swing.SwingWorker<ProxyHistoryImporter.ImportResult, Void>() {
            @Override protected ProxyHistoryImporter.ImportResult doInBackground() throws Exception {
                return historyImporter.importEarlierHistory().get();
            }
            @Override protected void done() {
                button.setEnabled(true);
                if (closed) return;
                try {
                    var result = get();
                    status.setText("Scanned " + result.scanned() + " earlier Proxy entries; processed " + result.eligible()
                            + " eligible entries (" + result.skippedOutOfScope() + " out of scope, "
                            + result.skippedWithoutResponse() + " without a response). Generate OpenAPI when ready.");
                } catch (Exception failure) {
                    status.setText("Could not import Proxy history");
                }
            }
        }.execute();
    }

    private void showSelection() {
        int row = selectedModelRow();
        if (row < 0 || row >= tableModel.getRowCount()) {
            showMessageEditor(requestPanel, false); showMessageEditor(responsePanel, false);
            details.setText(SELECT_ENDPOINT); return;
        }
        ApiOperation op = tableModel.row(row); CapturedExchange sample = op.latest();
        request.setRequest(toRequest(sample)); response.setResponse(toResponse(sample));
        showMessageEditor(requestPanel, true); showMessageEditor(responsePanel, true);
        details.setText("Review state: " + op.reviewState() + "\nIncluded: " + op.included() + "\nConfidence: " + op.confidence()
                + "%\nReason: " + op.reason() + "\nObservations: " + op.observations() + "\nCaptured: " + sample.capturedAt());
        request.setCaretPosition(0); response.setCaretPosition(0); details.setCaretPosition(0);
    }

    private static void installMessageEditor(JPanel panel, java.awt.Component editor) {
        panel.add(new JLabel(SELECT_ENDPOINT, javax.swing.SwingConstants.CENTER), "empty");
        panel.add(editor, "editor");
    }

    private static void showMessageEditor(JPanel panel, boolean populated) {
        ((CardLayout) panel.getLayout()).show(panel, populated ? "editor" : "empty");
    }

    private static HttpRequest toRequest(CapturedExchange exchange) {
        int port = exchange.port() > 0 ? exchange.port() : ("https".equalsIgnoreCase(exchange.scheme()) ? 443 : 80);
        HttpService service = HttpService.httpService(exchange.host(), port, "https".equalsIgnoreCase(exchange.scheme()));
        return HttpRequest.httpRequest(service, formatRequest(exchange));
    }

    private static HttpResponse toResponse(CapturedExchange exchange) {
        return HttpResponse.httpResponse(formatResponse(exchange));
    }

    private void installTableMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem add = new JMenuItem("Mark as API / include"); add.addActionListener(event -> updateSelected(true));
        JMenuItem ignore = new JMenuItem("Ignore in Reverse API"); ignore.addActionListener(event -> updateSelected(false));
        menu.add(add); menu.add(ignore); table.setComponentPopupMenu(menu);
    }

    private void updateSelected(boolean api) {
        int row = selectedModelRow(); if (row < 0) return;
        ApiOperation op = tableModel.row(row); if (api) op.markApi(); else op.ignore(); store.changed();
    }

    private int selectedModelRow() { int view = table.getSelectedRow(); return view < 0 ? -1 : table.convertRowIndexToModel(view); }

    private static JTextArea textArea() {
        JTextArea area = new JTextArea(); area.setEditable(false); area.setLineWrap(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); return area;
    }

    private static String formatRequest(CapturedExchange exchange) {
        String target = exchange.path();
        try {
            URI uri = URI.create(exchange.url());
            target = uri.getRawPath();
            if (uri.getRawQuery() != null) target += "?" + uri.getRawQuery();
        } catch (RuntimeException ignored) { }
        StringBuilder text = new StringBuilder(exchange.method()).append(' ').append(target).append(" HTTP/1.1\r\n");
        appendHeaders(text, exchange.requestHeaders()); return text.append("\r\n").append(exchange.requestBody()).toString();
    }

    private static String formatResponse(CapturedExchange exchange) {
        StringBuilder text = new StringBuilder("HTTP/1.1 ").append(exchange.statusCode()).append("\r\n");
        appendHeaders(text, exchange.responseHeaders()); return text.append("\r\n").append(exchange.responseBody()).toString();
    }

    private static void appendHeaders(StringBuilder text, Map<String, java.util.List<String>> headers) {
        headers.forEach((name, values) -> values.forEach(value -> text.append(name).append(": ")
                .append(value).append("\r\n")));
    }

}
