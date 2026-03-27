import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.zip.*;

public class ToolManager {

    private static String TOOLS_DIR; // APP_DIR 하위 tools\ 폴더 (log\ 와 형제)

    // =========================
    // 진입점 — 반드시 백그라운드 스레드에서 호출
    // =========================
    public static void init(String appDir) {
        // tools\ 폴더는 appDir 직속 하위 (log\ 와 같은 레벨)
        TOOLS_DIR = appDir + "tools";
        new Thread(() -> {
            try {
                Files.createDirectories(Paths.get(TOOLS_DIR));
                ensureYtDlp();
                ensureFfmpeg();
                System.out.println("[ToolManager] 초기화 완료");
            } catch (Exception e) {
                System.err.println("[ToolManager] 초기화 실패: " + e.getMessage());
                e.printStackTrace();
            }
        }, "ToolManager-Init").start();
    }

    // =========================
    // yt-dlp 다운로드
    // =========================
    private static void ensureYtDlp() throws Exception {
        Path exe = Paths.get(TOOLS_DIR, "yt-dlp.exe");
        if (Files.exists(exe)) {
            System.out.println("[ToolManager] yt-dlp 이미 존재");
            return;
        }
        System.out.println("[ToolManager] yt-dlp 다운로드 중...");
        downloadFile(
            "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe",
            exe
        );
        System.out.println("[ToolManager] yt-dlp 다운로드 완료");
    }

    // =========================
    // ffmpeg 다운로드 + 추출
    // =========================
    private static void ensureFfmpeg() throws Exception {
        Path exe = Paths.get(TOOLS_DIR, "ffmpeg.exe");
        if (Files.exists(exe)) {
            System.out.println("[ToolManager] ffmpeg 이미 존재");
            return;
        }
        System.out.println("[ToolManager] ffmpeg 다운로드 중... (100MB+, 시간 소요)");
        Path zipPath = Paths.get(TOOLS_DIR, "ffmpeg.zip");
        try {
            downloadFile(
                "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip",
                zipPath
            );
            System.out.println("[ToolManager] ffmpeg 압축 해제 중...");
            unzip(zipPath.toString(), TOOLS_DIR);

            // ffmpeg.exe 찾아서 tools/ 루트로 복사
            Files.walk(Paths.get(TOOLS_DIR))
                .filter(p -> p.getFileName().toString().equalsIgnoreCase("ffmpeg.exe")
                          && !p.equals(exe))
                .findFirst()
                .ifPresent(found -> {
                    try {
                        Files.copy(found, exe, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("[ToolManager] ffmpeg.exe 복사 완료: " + found);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

            // 압축 해제된 중간 폴더 정리 (ffmpeg-*-essentials_build 등)
            Files.walk(Paths.get(TOOLS_DIR), 1)
                .filter(p -> !p.equals(Paths.get(TOOLS_DIR))
                          && Files.isDirectory(p)
                          && p.getFileName().toString().startsWith("ffmpeg-"))
                .forEach(dir -> {
                    try {
                        deleteRecursively(dir);
                        System.out.println("[ToolManager] 임시 폴더 삭제: " + dir);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

            System.out.println("[ToolManager] ffmpeg 준비 완료");
        } finally {
            // zip 항상 삭제
            Files.deleteIfExists(zipPath);
        }
    }

    // =========================
    // 파일 다운로드 (진행률 출력)
    // =========================
    private static void downloadFile(String urlStr, Path target) throws Exception {
        java.net.URLConnection conn = URI.create(urlStr).toURL().openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);
        long total = conn.getContentLengthLong();

        try (InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[8192];
            long downloaded = 0;
            int len;
            int lastPct = -1;
            try (OutputStream out = Files.newOutputStream(target,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                    downloaded += len;
                    if (total > 0) {
                        int pct = (int) (downloaded * 100 / total);
                        if (pct != lastPct && pct % 10 == 0) {
                            System.out.printf("[ToolManager] %s ... %d%%%n",
                                target.getFileName(), pct);
                            lastPct = pct;
                        }
                    }
                }
            }
        }
    }

    // =========================
    // ZIP 해제 (zip slip 방어)
    // =========================
    private static void unzip(String zipFile, String destDir) throws Exception {
        File destDirFile = new File(destDir).getCanonicalFile();
        byte[] buffer = new byte[8192];

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(destDirFile, entry.getName()).getCanonicalFile();

                // ── zip slip 방어 ──────────────────────────────────
                if (!newFile.getCanonicalPath().startsWith(destDirFile.getCanonicalPath() + File.separator)) {
                    throw new SecurityException("Zip slip 차단: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    // =========================
    // 폴더 재귀 삭제
    // =========================
    private static void deleteRecursively(Path path) throws IOException {
        Files.walk(path)
            .sorted(java.util.Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
    }

    // =========================
    // 실행 유틸
    // =========================
    public static void runYtDlp(String url) throws Exception {
        // 다운로드 출력 폴더: %APPDATA%\KootPanKing\download\
        String appData = System.getenv("APPDATA");
        if (appData == null) appData = System.getProperty("user.home");
        String downloadDir = appData + File.separator + "KootPanKing"
            + File.separator + "download";
        Files.createDirectories(Paths.get(downloadDir));
        String outputTemplate = downloadDir + File.separator + "%(title)s.%(ext)s";
        new ProcessBuilder(TOOLS_DIR + File.separator + "yt-dlp.exe",
                "-o", outputTemplate, url)
            .inheritIO().start().waitFor();
    }

    public static void runFfmpeg() throws Exception {
        new ProcessBuilder(TOOLS_DIR + File.separator + "ffmpeg.exe", "-version")
            .inheritIO().start().waitFor();
    }
}