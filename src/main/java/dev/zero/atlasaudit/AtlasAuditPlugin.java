package dev.zero.atlasaudit;

import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

public class AtlasAuditPlugin extends JavaPlugin {

    public AtlasAuditPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getCommandRegistry().registerCommand(new AtlasAuditCommand());
        getEventRegistry().register(BootEvent.class, event -> runAudit());
    }

    private void runAudit() {
        try {
            var report = AtlasAudit.run();
            getLogger().at(report.anyOverflowMinSpec() ? Level.WARNING : Level.INFO).log(report.summaryLine());
            try {
                var path = Path.of(AtlasAudit.REPORT_FILE).toAbsolutePath();
                Files.writeString(path, report.fullReport());
                getLogger().at(Level.INFO).log("Atlas audit written to %s", path);
            } catch (Exception e) {
                getLogger().at(Level.WARNING).log("Could not write atlas audit report: %s", e);
            }
        } catch (Exception | LinkageError e) {
            getLogger().at(Level.WARNING).log("Atlas audit failed: %s", e);
        }
    }
}
