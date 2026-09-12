package io.github.reverseapi;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import io.github.reverseapi.model.ApiOperation;
import io.github.reverseapi.model.CaptureStore;
import io.github.reverseapi.traffic.MontoyaExchangeMapper;
import io.github.reverseapi.traffic.ProxyHistoryImporter;
import io.github.reverseapi.traffic.TrafficCaptureHandler;
import io.github.reverseapi.ui.ReverseApiPanel;

import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

public final class ReverseApiExtension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        var extensionLoadedAt = java.time.ZonedDateTime.now();
        api.extension().setName("Reverse API");
        CaptureStore store = new CaptureStore();
        TrafficCaptureHandler captureHandler = new TrafficCaptureHandler(store);
        Runnable initializeUi = () -> {
            ReverseApiPanel panel = new ReverseApiPanel(store, captureHandler,
                    new ProxyHistoryImporter(api.proxy(), captureHandler, extensionLoadedAt), api.userInterface());
            api.userInterface().applyThemeToComponent(panel);
            api.userInterface().registerSuiteTab("Reverse API", panel);
            api.extension().registerUnloadingHandler(() -> {
                captureHandler.close();
                javax.swing.SwingUtilities.invokeLater(panel::close);
            });
        };
        try {
            if (javax.swing.SwingUtilities.isEventDispatchThread()) initializeUi.run();
            else javax.swing.SwingUtilities.invokeAndWait(initializeUi);
        } catch (Exception failure) { captureHandler.close(); throw new IllegalStateException("Unable to initialize Reverse API UI", failure); }
        api.http().registerHttpHandler(captureHandler);
        api.userInterface().registerContextMenuItemsProvider(new ReverseApiContextMenu(captureHandler));
        api.logging().logToOutput("Reverse API loaded: passive Proxy API discovery is active.");
    }

    private static final class ReverseApiContextMenu implements ContextMenuItemsProvider {
        private final TrafficCaptureHandler handler;
        private ReverseApiContextMenu(TrafficCaptureHandler handler) { this.handler = handler; }

        @Override
        public List<Component> provideMenuItems(ContextMenuEvent event) {
            List<HttpRequestResponse> selected = event.selectedRequestResponses();
            if (selected.isEmpty()) return List.of();
            List<Component> items = new ArrayList<>();
            JMenuItem add = new JMenuItem("Add to Reverse API");
            add.addActionListener(ignored -> selected.forEach(item -> capture(item, false)));
            JMenuItem ignore = new JMenuItem("Ignore in Reverse API");
            ignore.addActionListener(ignored -> selected.forEach(item -> capture(item, true)));
            items.add(add); items.add(ignore); return items;
        }

        private void capture(HttpRequestResponse pair, boolean ignore) {
            try {
                handler.capture(pair.request(), pair.hasResponse() ? pair.response() : null, true, ignore);
            } catch (RuntimeException ignored) { }
        }
    }
}

