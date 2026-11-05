package craftbound.game;

import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;

/** Allowlist persistence and fail-closed validation; no live config is read or edited. */
final class DeveloperAccessChecks {
    static void check() {
        var stranger=UUID.fromString("54e3515a-1051-4de8-bff1-ae25677bebac");
        if(!DeveloperAccess.permits(DeveloperAccess.OWNER,false,Set.of(DeveloperAccess.OWNER))
                || DeveloperAccess.permits(stranger,false,Set.of(DeveloperAccess.OWNER))
                || DeveloperAccess.permits(DeveloperAccess.OWNER,false,Set.of())
                || !DeveloperAccess.permits(stranger,true,Set.of()))
            throw new AssertionError("Developer UUID/development-host access policy");
        try {
            var folder=Files.createTempDirectory("craftbound-developer-access");
            var file=folder.resolve("developers.json");
            try {
                if(!DeveloperAccess.load(file).equals(Set.of(DeveloperAccess.OWNER)))
                    throw new AssertionError("Owner-only initial allowlist");
                Files.writeString(file,"{\"allowedUuids\":[]}");
                if(!DeveloperAccess.load(file).isEmpty())throw new AssertionError("Empty list must revoke even owner");
                Files.writeString(file,"{\"allowedUuids\":[\""+stranger+"\"]}");
                if(!DeveloperAccess.load(file).equals(Set.of(stranger)))throw new AssertionError("Keep server's custom list");
                for(String invalid:new String[]{"{}", "{\"allowedUuids\":[\"ZeElectron\"]}", "{\"allowedUuids\":[\"1-1-1-1-1\"]}"}) {
                    Files.writeString(file,invalid);
                    boolean rejected=false;
                    try { DeveloperAccess.load(file); } catch(RuntimeException expected){rejected=true;}
                    if(!rejected)throw new AssertionError("Invalid allowlist must fail closed");
                }
            } finally {Files.deleteIfExists(file);Files.deleteIfExists(folder);}
        } catch(java.io.IOException ex) {throw new AssertionError(ex);}
    }
    private DeveloperAccessChecks(){}
}
