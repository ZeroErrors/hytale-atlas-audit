package dev.zero.atlasaudit;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;

public class AtlasAuditCommand extends CommandBase {

    public AtlasAuditCommand() {
        super("atlasaudit", "Reports the predicted size of each texture atlas built from mods.");
        setPermissionGroups(HytalePermissionsProvider.GROUP_ADMIN);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        AtlasAudit.Report report;
        try {
            report = AtlasAudit.run();
        } catch (Exception | LinkageError e) {
            context.sendMessage(Message.raw("Atlas audit failed: " + e));
            return;
        }

        for (var line : report.lines()) {
            context.sendMessage(Message.raw(line));
        }

        try {
            var path = Path.of(AtlasAudit.REPORT_FILE).toAbsolutePath();
            Files.writeString(path, report.fullReport());
            context.sendMessage(Message.raw("Written to " + path));
        } catch (Exception e) {
            context.sendMessage(Message.raw("Could not write report file: " + e.getMessage()));
        }
    }
}
