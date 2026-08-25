package com.company.kanban.service;

import com.company.kanban.dto.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import javax.sql.DataSource;
import java.io.*; import java.net.URI; import java.nio.file.*; import java.time.*; import java.time.format.DateTimeFormatter; import java.util.*;

@Service
public class DataManagementService {
    private static final Logger log = LoggerFactory.getLogger(DataManagementService.class);
    private static final Set<String> EXTENSIONS = Set.of(".backup", ".dump");
    private final Path backupDirectory; private final Path metadataFile; private final ObjectMapper mapper; private final JdbcTemplate jdbc;
    private final String datasourceUrl, username, password, pgBin, helperPath; private final boolean restoreEnabled;
    private volatile String operationStatus = "IDLE";

    public DataManagementService(ObjectMapper mapper, DataSource dataSource,
            @Value("${app.backup.directory:backups}") String backupDirectory,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${app.postgres.bin:}") String pgBin,
            @Value("${app.restore.helper-path:}") String helperPath,
            @Value("${app.restore.enabled:false}") boolean restoreEnabled) {
        this.mapper=mapper; this.jdbc=new JdbcTemplate(dataSource); this.backupDirectory=Path.of(backupDirectory).toAbsolutePath().normalize();
        this.metadataFile=this.backupDirectory.resolve("backup-metadata.json"); this.datasourceUrl=datasourceUrl; this.username=username;
        this.password=password; this.pgBin=pgBin; this.helperPath=helperPath; this.restoreEnabled=restoreEnabled;
    }

    public List<BackupResponse> listBackups() {
        try { Files.createDirectories(backupDirectory); Map<String, RegistryEntry> registry=readRegistry();
            try (var stream=Files.list(backupDirectory)) { return stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)).filter(this::isBackup)
                    .map(path -> describe(path, registry.get(path.getFileName().toString()))).sorted(Comparator.comparing(BackupResponse::createdAt).reversed()).toList(); }
        } catch (IOException e) { throw failure("Could not read the configured backup directory", e); }
    }

    public List<ArchiveResponse> listArchives() {
        // The second pattern discovers archives created by pre-rebrand development installers.
        try { return jdbc.query("select datname from pg_database where (datname like 'flowops_%' or datname like 'kovax_flowops_%') and datname <> current_database() order by datname desc",
                (rs, row) -> { String name=rs.getString(1); LocalDateTime date=parseTimestamp(name); String match=findMatchingBackup(date);
                    return new ArchiveResponse(name, date, "Previous active database", match, "READ_ONLY"); });
        } catch (RuntimeException ex) { log.warn("Archive discovery unavailable: {}", ex.getMessage()); return List.of(); }
    }

    public synchronized BackupResponse createBackup(String type, String reason) {
        operationStatus="CREATING_BACKUP";
        try { Files.createDirectories(backupDirectory); LocalDateTime now=LocalDateTime.now(); String filename="flowops_"+now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))+".backup";
            Path output=resolveBackup(filename, false); runPgDump(output); RegistryEntry entry=new RegistryEntry(filename, now, type, reason, databaseName(), null, "COMPLETED");
            Map<String,RegistryEntry> registry=readRegistry(); registry.put(filename,entry); writeRegistry(registry); operationStatus="BACKUP_COMPLETED"; return describe(output,entry);
        } catch (Exception e) { operationStatus="BACKUP_FAILED"; throw failure("Backup creation failed",e); }
    }

    public ResponseEntity<FileSystemResource> download(String id) {
        Path path=resolveBackup(id,true); FileSystemResource resource=new FileSystemResource(path);
        try { return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+path.getFileName()+"\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(Files.size(path)).body(resource); }
        catch (IOException e) { throw failure("Backup size could not be read", e); }
    }

    public synchronized void delete(String id) {
        String current=status().status();
        if (Set.of("CREATING_SAFETY_BACKUP","RESTORE_QUEUED","STOPPING_SERVICE","RESTORING","RESTARTING").contains(current)) throw new ResponseStatusException(HttpStatus.CONFLICT,"A backup cannot be deleted during restore");
        Path path=resolveBackup(id,true); try { Files.delete(path); Map<String,RegistryEntry> registry=readRegistry(); registry.remove(path.getFileName().toString()); writeRegistry(registry); }
        catch(IOException e){ throw failure("Backup could not be deleted",e); }
    }

    public synchronized DataManagementStatusResponse queueRestore(RestoreRequest request) {
        if (!"RESTORE".equals(request.confirmation())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Restore confirmation is required");
        if (!restoreEnabled || helperPath.isBlank()) throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,"Restore helper is not enabled in this environment");
        Path selected=resolveBackup(request.backupId(),true); operationStatus="CREATING_SAFETY_BACKUP";
        BackupResponse safety=createBackup("PRE_RESTORE","Automatic safety backup before restoring "+selected.getFileName());
        try { operationStatus="RESTORE_QUEUED"; Path requestFile=backupDirectory.resolve("restore-request.json");
            Db db=parseDb();
            mapper.writeValue(requestFile.toFile(),Map.of(
                    "backup",selected.toString(),"safetyBackup",backupDirectory.resolve(safety.filename()).toString(),
                    "requestedAt",LocalDateTime.now().toString(),"host",db.host(),"port",db.port(),
                    "database",db.name(),"username",username,"postgresBin",pgBin));
            ProcessBuilder helper=new ProcessBuilder("powershell.exe","-NoProfile","-ExecutionPolicy","Bypass","-File",helperPath,"-RequestFile",requestFile.toString());
            helper.environment().put("PGPASSWORD",password);
            helper.start();
            return new DataManagementStatusResponse(operationStatus,"Restore queued. The selected backup is being applied.");
        } catch(IOException e){ operationStatus="RESTORE_FAILED"; throw failure("Restore helper could not be started; the safety backup was retained",e); }
    }
    public DataManagementStatusResponse status(){
        Path helperStatus=backupDirectory.getParent()==null?backupDirectory.resolve("restore-status.json"):backupDirectory.getParent().resolve("runtime").resolve("restore-status.json");
        if(Files.isRegularFile(helperStatus))try{Map<String,Object> value=mapper.readValue(helperStatus.toFile(),new TypeReference<>(){});return new DataManagementStatusResponse(String.valueOf(value.getOrDefault("status",operationStatus)),String.valueOf(value.getOrDefault("message","Restore status")));}catch(Exception e){log.warn("Restore status file could not be read: {}",e.getMessage());}
        return new DataManagementStatusResponse(operationStatus,"Data management operation status");
    }
    public String backupDirectory(){ return backupDirectory.toString(); }
    /** Keep daily history for 30 days, weekly representatives for 12 weeks, and monthly representatives for 12 months. */
    public synchronized int rotateBackups(LocalDateTime now) {
        try {
            List<BackupFile> files = listBackupFiles();
            if (files.size() <= 1) return 0;
            Set<String> keep = new HashSet<>();
            files.stream().filter(b -> b.entry == null || "COMPLETED".equalsIgnoreCase(b.entry.status()) || "AVAILABLE".equalsIgnoreCase(b.entry.status()))
                    .max(Comparator.comparing(BackupFile::createdAt)).ifPresent(b -> keep.add(b.name()));
            files.stream().filter(b -> b.entry != null && "PRE_RESTORE".equalsIgnoreCase(b.entry.backupType())).forEach(b -> keep.add(b.name()));
            for (BackupFile b : files) {
                long age = Duration.between(b.createdAt(), now).toDays();
                if (age <= 30) keep.add(b.name());
                else if (age <= 30 + 7 * 12) keep.add(representative(files, b.createdAt(), 7));
                else if (age <= 365) keep.add(representative(files, b.createdAt(), 30));
            }
            int deleted = 0; Map<String, RegistryEntry> registry = readRegistry();
            for (BackupFile b : files) if (!keep.contains(b.name()) && files.size() - deleted > 1) {
                Files.deleteIfExists(b.path()); registry.remove(b.name()); deleted++;
            }
            if (deleted > 0) writeRegistry(registry);
            return deleted;
        } catch (IOException e) { log.warn("Backup rotation skipped: {}", e.getMessage()); return 0; }
    }
    public boolean canOpenBackupFolder(String remoteAddress) {
        boolean local = "127.0.0.1".equals(remoteAddress) || "::1".equals(remoteAddress) || "0:0:0:0:0:0:0:1".equals(remoteAddress);
        return local && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }
    public void openBackupFolder(String remoteAddress) {
        if (!canOpenBackupFolder(remoteAddress))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The server backup folder can only be opened from a browser running on the Windows server");
        try { Files.createDirectories(backupDirectory); new ProcessBuilder("explorer.exe", backupDirectory.toString()).start(); }
        catch (IOException e) { throw failure("The backup folder could not be opened", e); }
    }

    private void runPgDump(Path output) throws Exception { Db db=parseDb(); String executable=pgBin.isBlank()?"pg_dump":Path.of(pgBin,"pg_dump.exe").toString();
        ProcessBuilder pb=new ProcessBuilder(executable,"-h",db.host,"-p",String.valueOf(db.port),"-U",username,"-Fc","-f",output.toString(),db.name);
        pb.environment().put("PGPASSWORD",password); pb.redirectErrorStream(true); Process process=pb.start(); String details=new String(process.getInputStream().readAllBytes());
        if(process.waitFor()!=0 || !Files.isRegularFile(output)){ Files.deleteIfExists(output); log.warn("pg_dump failed: {}",details.replaceAll("[\\r\\n]+"," ")); throw new IOException("pg_dump returned an error"); }
    }
    private Path resolveBackup(String id, boolean requireExisting){ if(id==null||id.isBlank()||!id.equals(Path.of(id).getFileName().toString())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid backup identifier");
        Path path=backupDirectory.resolve(id).normalize(); if(!path.getParent().equals(backupDirectory)||!isBackup(path)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid backup identifier");
        if(requireExisting){
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Backup file not found");
            try{if(!path.toRealPath().getParent().equals(backupDirectory.toRealPath()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid backup identifier");}catch(IOException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Backup file not found");}
        } return path; }
    private boolean isBackup(Path p){ String n=p.getFileName().toString().toLowerCase(Locale.ROOT); return EXTENSIONS.stream().anyMatch(n::endsWith); }
    private BackupResponse describe(Path p, RegistryEntry e){ try { LocalDateTime created=e==null?LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(),ZoneId.systemDefault()):e.createdAt;
        return new BackupResponse(p.getFileName().toString(),p.getFileName().toString(),created,Files.size(p),e==null?inferType(p):e.backupType,e==null?null:e.reason,e==null?databaseName():e.sourceDatabase,e==null?"AVAILABLE":e.status); }
        catch(IOException ex){ throw failure("Backup metadata could not be read",ex); } }
    // Recognize backup names created by both current and pre-rebrand installers.
    private String inferType(Path p){ String n=p.getFileName().toString(); return n.startsWith("flowops_")||n.startsWith("kovax_flowops_")?"PRE_NEW_DATABASE":"MANUAL"; }
    private String databaseName(){ try{return parseDb().name;}catch(Exception e){return "unknown";} }
    private Db parseDb(){ String value=datasourceUrl.replaceFirst("^jdbc:",""); URI uri=URI.create(value); return new Db(uri.getHost(),uri.getPort()<0?5432:uri.getPort(),uri.getPath().substring(1)); }
    private Map<String,RegistryEntry> readRegistry(){ if(!Files.isRegularFile(metadataFile))return new HashMap<>(); try{return mapper.readValue(metadataFile.toFile(),new TypeReference<>(){});}catch(Exception e){log.warn("Ignoring unreadable backup metadata registry: {}",e.getMessage());return new HashMap<>();} }
    private void writeRegistry(Map<String,RegistryEntry> entries)throws IOException{ Path temp=backupDirectory.resolve("backup-metadata.json.tmp");mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(),entries);Files.move(temp,metadataFile,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
    private LocalDateTime parseTimestamp(String name){ var m=java.util.regex.Pattern.compile("(\\d{8})[_-](\\d{4,6})").matcher(name);if(!m.find())return null;try{String t=m.group(2);if(t.length()==4)t+="00";return LocalDateTime.parse(m.group(1)+t,DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));}catch(Exception e){return null;} }
    private String findMatchingBackup(LocalDateTime date){if(date==null)return null;return listBackups().stream().filter(b->Math.abs(Duration.between(date,b.createdAt()).toMinutes())<=2).map(BackupResponse::filename).findFirst().orElse(null);}
    private List<BackupFile> listBackupFiles() throws IOException { Map<String, RegistryEntry> registry = readRegistry(); try (var stream = Files.list(backupDirectory)) { return stream.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(p)).filter(this::isBackup).map(p -> { RegistryEntry e = registry.get(p.getFileName().toString()); LocalDateTime created; try { created = e == null ? LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), ZoneId.systemDefault()) : e.createdAt(); } catch (IOException ex) { created = LocalDateTime.MIN; } return new BackupFile(p, p.getFileName().toString(), created, e); }).toList(); } }
    private String representative(List<BackupFile> files, LocalDateTime date, int bucketDays) { return files.stream().filter(b -> Math.abs(Duration.between(b.createdAt(), date).toDays()) <= bucketDays).min(Comparator.comparing(b -> Math.abs(Duration.between(b.createdAt(), date).toMinutes()))).map(BackupFile::name).orElse(""); }
    private ResponseStatusException failure(String message,Exception e){log.error(message,e);return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,message);}
    private record BackupFile(Path path, String name, LocalDateTime createdAt, RegistryEntry entry) {}
    public record RegistryEntry(String filename, LocalDateTime createdAt, String backupType, String reason, String sourceDatabase, String archivedDatabaseName, String status){}
    private record Db(String host,int port,String name){}
}
