package io.github.reverseapi.ui;

import burp.api.montoya.proxy.Proxy;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import io.github.reverseapi.TestExchange;
import io.github.reverseapi.model.CaptureStore;
import io.github.reverseapi.traffic.ProxyHistoryImporter;
import io.github.reverseapi.traffic.TrafficCaptureHandler;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.lang.reflect.InvocationHandler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationPreviewTest {
    @Test void generationShowsItsResultTabAndReadableFailureDetails() throws Exception {
        var store = new CaptureStore();
        store.capture(TestExchange.exchange("GET", "/api/items", "", "", 200, "application/json", "{}"), false);
        var capture = new TrafficCaptureHandler(store);
        var holder = new ReverseApiPanel[1];
        try {
            SwingUtilities.invokeAndWait(() -> holder[0] = new ReverseApiPanel(store, capture,
                    new ProxyHistoryImporter(stub(Proxy.class, (p,m,a) -> List.of()), capture), ui()));
            var panel = holder[0];
            assertThat(generate(panel, "Generate OpenAPI")).startsWith("Generated 1 operations");
            SwingUtilities.invokeAndWait(() -> {
                var tabs = find(panel, JTabbedPane.class, null);
                assertThat(tabs.getTitleAt(tabs.getSelectedIndex())).isEqualTo("OpenAPI YAML");
            });
            assertThat(generate(panel, "Generate Postman")).startsWith("Generated 1 operations");
            SwingUtilities.invokeAndWait(() -> {
                var tabs = find(panel, JTabbedPane.class, null);
                assertThat(tabs.getTitleAt(tabs.getSelectedIndex())).isEqualTo("Postman JSON");
                store.operations().get(0).normalizedPath("/invalid/{");
                store.changed();
            });
            assertThat(generate(panel, "Generate OpenAPI")).contains("Generation failed", "Invalid normalized path");
            SwingUtilities.invokeAndWait(() -> {
                var tabs = find(panel, JTabbedPane.class, null);
                assertThat(tabs.getTitleAt(tabs.getSelectedIndex())).isEqualTo("OpenAPI YAML");
                var status = status(panel);
                assertThat(status.getLineWrap()).isTrue();
                assertThat(status.isEditable()).isFalse();
            });
        } finally {
            capture.close();
            if (holder[0] != null) SwingUtilities.invokeAndWait(holder[0]::close);
        }
    }

    private String generate(ReverseApiPanel panel, String button) throws Exception {
        var result = new CompletableFuture<String>();
        var listener = new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { changed(); }
            public void removeUpdate(DocumentEvent event) { changed(); }
            public void changedUpdate(DocumentEvent event) { changed(); }
            private void changed() {
                String text = status(panel).getText();
                if (text.startsWith("Generated ") || text.startsWith("Generation failed:")) result.complete(text);
            }
        };
        SwingUtilities.invokeAndWait(() -> {
            status(panel).getDocument().addDocumentListener(listener);
            find(panel, JButton.class, button).doClick();
        });
        try { return result.get(10, TimeUnit.SECONDS); }
        finally { SwingUtilities.invokeAndWait(() -> status(panel).getDocument().removeDocumentListener(listener)); }
    }

    private JTextArea status(ReverseApiPanel panel) {
        return (JTextArea) ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
    }

    private <T extends Component> T find(Container container, Class<T> type, String text) {
        for (Component child : container.getComponents()) {
            if (type.isInstance(child) && (text == null || child instanceof JButton button && button.getText().equals(text)))
                return type.cast(child);
            if (child instanceof Container nested) {
                T found = find(nested, type, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private UserInterface ui() {
        InvocationHandler editor = (proxy, method, args) -> method.getName().equals("uiComponent") ? new JPanel() : null;
        return stub(UserInterface.class, (proxy, method, args) -> switch (method.getName()) {
            case "createHttpRequestEditor" -> stub(HttpRequestEditor.class, editor);
            case "createHttpResponseEditor" -> stub(HttpResponseEditor.class, editor);
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private <T> T stub(Class<T> type, InvocationHandler handler) {
        return type.cast(java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, handler));
    }
}
