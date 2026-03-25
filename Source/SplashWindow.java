import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Supplier;

import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
/**
	* SplashWindow — 프로그램 시작 직후 표시되는 메인 윈도우
	*
	* ── 기능 ──────────────────────────────────────────────────────
	*   • 시계(KootPanKing) 표시 전 가장 먼저 열림
	*   • 스크롤 가능한 로그 텍스트 창 — 진행 메시지를 누적 표시
	*   • 메뉴바: [File] [Help]
	*       File → Open / Global / Close / Exit
	*       Help → 기본설정파일 / About
	*
	* ── 사용법 ────────────────────────────────────────────────────
	*   // 1) main() 에서 SplashWindow 를 가장 먼저 생성
	*   SplashWindow splash = new SplashWindow();
	*   splash.log("프로그램 초기화 중...");
	*
	*   // 2) KootPanKing 생성 후 참조 주입
	*   //    (Global / Close / About 등의 위임 대상)
	*   splash.setClockHost(clockInstance);
	*
	*   // 3) 시계 초기화 완료 메시지 기록
	*   splash.log("시계 초기화 완료.");
	*
	* ── HostCallback ──────────────────────────────────────────────
	*   Global Cities / 기본설정파일 / About / Exit 동작은
	*   KootPanKing 이 ClockHostCallback 을 구현(또는 익명 클래스)
	*   하여 setClockHost() 로 주입한다.
	*   주입 전에 해당 메뉴를 사용하면 "시계가 아직 초기화되지 않았습니다."
	*   안내 메시지가 표시된다.
*/
public class SplashWindow extends JFrame {
	
    // ── 상수 ─────────────────────────────────────────────────────
    private static final String TITLE      = "끝판왕 (KootPanKing Ver 1.0f)";
    private static final Font   LOG_FONT   = new Font("Malgun Gothic", Font.PLAIN, 13);
    private static final Color  BG_COLOR   = new Color(235, 245, 255); // 연한 하늘색 배경
    private static final Color  FG_COLOR   = new Color( 20,  50,  90); // 진한 네이비 글자
    private static final Color  TS_COLOR   = new Color( 80, 120, 180); // 중간 파랑 타임스탬프
    private static final Color  SEP_COLOR  = new Color(150, 190, 220); // 연파랑 구분선
	
    // ── 로그 영역 ─────────────────────────────────────────────────
    private final JTextPane logPane;
    private final StyledDocument doc;
    private final SimpleAttributeSet tsStyle;    // 타임스탬프 스타일
    private final SimpleAttributeSet msgStyle;   // 메시지 스타일
    private final SimpleAttributeSet sepStyle;   // 구분선 스타일
	
    // ── 상태바 ────────────────────────────────────────────────────
    private final JLabel statusBar;
	
    // ── 시계 호스트 콜백 (KootPanKing 주입) ─────────────────
    private ClockHostCallback clockHost = null;
	
    // ═══════════════════════════════════════════════════════════
    //  ClockHostCallback — KootPanKing 이 구현체를 제공
    // ═══════════════════════════════════════════════════════════
	
    /**
		* SplashWindow 가 시계에 위임할 동작 인터페이스.
		* KootPanKing 에서 익명 클래스나 람다로 구현하여
		* splash.setClockHost(cb) 로 주입한다.
	*/
    public interface ClockHostCallback {
		
        /** File → Global : Global Cities 서브메뉴를 담은 JMenu 반환 */
        JMenu buildGlobalMenu();
		
        /** File → Exit : 프로그램 완전 종료 */
        void exitAll();
		
        /** Help → Log조회 : 현재 로그 파일을 브라우저로 표시 */
        void showLogFile();
		
        /** Help → 지난Log데이타 삭제 : log/ 폴더의 이전(오늘 제외) 로그 파일 삭제 */
        void deleteOldLogs();
		
        /** 현재 로그 파일 경로 */
        String getLogFilePath();
		
        /** Help → 기본설정파일 : ini 파일 표시 */
        void showConfigFile();
		
        /** Help → About : About 다이얼로그 표시 */
        void showAbout();
		
        /** 설정 파일 경로 (기본설정파일 메뉴에서 존재 여부 확인용) */
        String getConfigFilePath();
		
        /** File → Close / X버튼 : ini 에서 mainWindow 값 제거 */
        void onClose();
		
        /** 도구 → 차임벨 설정 */
        void showChimeDialog();
		
        /** 도구 → 알람 관리 */
        void showAlarmDialog();
		
        /** 도구 → Gmail / Calendar 서브메뉴 */
        JMenu buildGmailCalendarMenu();
		
        /** 도구 → 카카오톡 서브메뉴 */
        JMenu buildKakaoMenu();
		
        /** 도구 → 텔레그램 서브메뉴 */
        JMenu buildTelegramMenu();
		
        /** ini config 값 읽기 (key 없으면 defaultValue 반환) */
        String getConfig(String key, String defaultValue);
		
        /**
			* ini config 값 여러 개를 한 번에 쓰고 파일 저장.
			* 기존 setConfigAndSave(key, value) 를 두 번 호출하면 파일을 두 번 쓰는 문제를
			* 해결하기 위해 추가. 구현체에서 모든 값을 메모리에 반영한 뒤 파일을 1회만 저장한다.
			*
			* @param entries  [key, value] 쌍의 가변 인자 (반드시 짝수 개)
		*/
        void setMultipleConfigAndSave(String... entries);
		
        /**
			* ini config 값 1개 쓰기 + 파일 저장 (하위 호환용).
			* 2개 이상 저장 시에는 setMultipleConfigAndSave 를 사용할 것.
		*/
        void setConfigAndSave(String key, String value);
	}
    // ═══════════════════════════════════════════════════════════
    //  생성자
    // ═══════════════════════════════════════════════════════════
	
    public SplashWindow() {
        super(TITLE);
		
        // ── 스타일 초기화 ─────────────────────────────────────────
        tsStyle  = new SimpleAttributeSet();
        msgStyle = new SimpleAttributeSet();
        sepStyle = new SimpleAttributeSet();
		
        StyleConstants.setForeground(tsStyle,  TS_COLOR);
        StyleConstants.setFontFamily(tsStyle,  "Consolas");
        StyleConstants.setFontSize  (tsStyle,  12);
        StyleConstants.setBold      (tsStyle,  false);
		
        StyleConstants.setForeground(msgStyle, FG_COLOR);
        StyleConstants.setFontFamily(msgStyle, "Malgun Gothic");
        StyleConstants.setFontSize  (msgStyle, 13);
        StyleConstants.setBold      (msgStyle, false);
		
        StyleConstants.setForeground(sepStyle, SEP_COLOR);
        StyleConstants.setFontFamily(sepStyle, "Consolas");
        StyleConstants.setFontSize  (sepStyle, 11);
		
        // ── 로그 텍스트 패인 ──────────────────────────────────────
        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(BG_COLOR);
        logPane.setForeground(FG_COLOR);
        logPane.setFont(LOG_FONT);
        logPane.setMargin(new Insets(6, 8, 6, 8));
        doc = logPane.getStyledDocument();
		
        JScrollPane scrollPane = new JScrollPane(logPane);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(BG_COLOR);
		
        // ── 상태바 ────────────────────────────────────────────────
        statusBar = new JLabel(" 준비");
        statusBar.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        statusBar.setForeground(new Color( 20,  60, 120));
        statusBar.setBackground(new Color(200, 225, 245));
        statusBar.setOpaque(true);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(140, 180, 210)),
		BorderFactory.createEmptyBorder(2, 8, 2, 8)));
		
        // ── 레이아웃 조립 ─────────────────────────────────────────
        getContentPane().setBackground(BG_COLOR);
        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(statusBar,  BorderLayout.SOUTH);
		
        // ── 메뉴바 ────────────────────────────────────────────────
        setJMenuBar(buildMenuBar());
		
        // ── 창 설정 ───────────────────────────────────────────────
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // X 버튼 = Close (시계는 살려두고 이 창만 닫기)
                doClose();
			}
		});
		
        setSize(860, 520);
        setLocationRelativeTo(null);  // 화면 중앙
        setVisible(true);
	}
	
    // ═══════════════════════════════════════════════════════════
    //  공개 API
    // ═══════════════════════════════════════════════════════════
	
    /**
		* 시계 호스트 콜백 주입.
		* KootPanKing 초기화 완료 후 호출한다.
	*/
    public void setClockHost(ClockHostCallback cb) {
        this.clockHost = cb;
        // Global 메뉴 활성화를 위해 메뉴바 재빌드
        SwingUtilities.invokeLater(() -> {
            setJMenuBar(buildMenuBar());
            revalidate();
		});
	}
	
    /**
		* 로그 메시지 추가 (EDT 안팎 모두 안전).
		* 타임스탬프 접두어 자동 삽입, 자동 스크롤 다운.
	*/
    public void log(String message) {
        if (SwingUtilities.isEventDispatchThread()) {
            appendLog(message);
			} else {
            SwingUtilities.invokeLater(() -> appendLog(message));
		}
	}
	
    /**
		* 구분선 추가 (단계 구분용).
	*/
    public void logSep() {
        log("─────────────────────────────────────────────────────────────");
	}
	
    /**
		* 상태바 텍스트 갱신.
	*/
    public void setStatus(String text) {
        SwingUtilities.invokeLater(() -> statusBar.setText(" " + text));
	}
	
    // ═══════════════════════════════════════════════════════════
    //  메뉴바 빌드
    // ═══════════════════════════════════════════════════════════
	
    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.setBackground(new Color(173, 216, 230));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(120, 170, 200)));
        bar.add(buildFileMenu());
        bar.add(buildGlobalMenu());
        bar.add(buildToolsMenu());
        bar.add(buildLifeMenu());
        bar.add(buildOfficeMenu());
        bar.add(buildHelpMenu());
        return bar;
	}
	
	
    private JMenu buildGlobalMenu() {
		JMenu globalMenu = makeMenu("세계시계");
		
        // ── Global ────────────────────────────────────────────────
        // clockHost 주입 전엔 비활성 JMenuItem, 주입 후엔 JMenu 로 교체
        if (clockHost != null) {
            globalMenu = clockHost.buildGlobalMenu();
            styleMenu(globalMenu, "🌍 Global");
			} else {
            JMenuItem globalDisabled = makeMenuItem("🌍 Global", "시계 초기화 후 사용 가능");
            globalDisabled.setEnabled(false);
		}
		
		return globalMenu;
	}
	
    // ── [도구] 메뉴 ──────────────────────────────────────────────
	
    private JMenu buildToolsMenu() {
        JMenu toolsMenu = makeMenu("도구");
		
		
        // ── 차임벨 설정 ───────────────────────────────────────────
        JMenuItem chimeItem = makeMenuItem("차임벨 설정...", null);
        chimeItem.addActionListener(e -> {
            if (clockHost != null) clockHost.showChimeDialog();
		});
        // chimeItem.setEnabled(false); // [배포] 비활성화
        toolsMenu.add(chimeItem);
		
        // ── 알람 관리 ─────────────────────────────────────────────
        JMenuItem alarmItem = makeMenuItem("알람 관리...", null);
        alarmItem.addActionListener(e -> {
            if (clockHost != null) clockHost.showAlarmDialog();
		});
        alarmItem.setEnabled(false); // [배포] 비활성화
        toolsMenu.add(alarmItem);
		
        toolsMenu.addSeparator();
		
        // ── Gmail / Calendar ──────────────────────────────────────
        if (clockHost != null) {
            JMenu gmailMenu = clockHost.buildGmailCalendarMenu();
            if (gmailMenu != null) {
                gmailMenu.setEnabled(false); // [배포] 비활성화
                toolsMenu.add(gmailMenu);
			}
			} else {
            toolsMenu.add(makeMenuItem("Gmail / Calendar", null)).setEnabled(false);
		}
		
        // ── 카카오톡 ──────────────────────────────────────────────
        if (clockHost != null) {
            JMenu kakaoMenu = clockHost.buildKakaoMenu();
            if (kakaoMenu != null) {
                kakaoMenu.setEnabled(false); // [배포] 비활성화
                toolsMenu.add(kakaoMenu);
			}
			} else {
            toolsMenu.add(makeMenuItem("카카오톡...", null)).setEnabled(false);
		}
		
        // ── 텔레그램 ──────────────────────────────────────────────
        if (clockHost != null) {
            JMenu tgMenu = clockHost.buildTelegramMenu();
            if (tgMenu != null) {
                tgMenu.setEnabled(false); // [배포] 비활성화
                toolsMenu.add(tgMenu);
			}
			} else {
            toolsMenu.add(makeMenuItem("텔레그램", null)).setEnabled(false);
		}
		
        return toolsMenu;
	}
	
    // ── [생활정보] 메뉴 ──────────────────────────────────────────
	
    private JMenu buildLifeMenu() {
        JMenu lifeMenu = makeMenu("생활도구");
        // 생활도구 메뉴 활성화 (배포)
        String[][] lifeLinks = {
            {"🌏 생활천문관",  "https://astro.kasi.re.kr/index"},
            {"🕐 TIME.IS",    "https://time.is"},
            {"🕰 TIME&DATE",  "https://www.timeanddate.com/worldclock/full.html"},
            {"🌤 날씨",        "https://www.weather.go.kr/w/index.do"},
		};
        for (String[] entry : lifeLinks) {
            JMenuItem li = makeMenuItem(entry[0], null);
            final String url = entry[1];
            li.addActionListener(e -> {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
					} catch (Exception ex) {
                    JOptionPane.showMessageDialog(SplashWindow.this,
					"브라우저 열기 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
				}
			});
            lifeMenu.add(li);
		}
        lifeMenu.addSeparator();
        // ── 현재위치 ──────────────────────────────────────────────
        JMenuItem locationItem = makeMenuItem("📍 현재위치", "브라우저 위치정보로 현재 주소를 조회합니다");
        locationItem.addActionListener(e -> doShowLocation());
        lifeMenu.add(locationItem);
        lifeMenu.addSeparator();
        // ── 만년달력 ─────────────────────────────────────────────
        JMenuItem calendarItem = makeMenuItem("📅 만년달력", "만년달력을 브라우저로 엽니다");
        calendarItem.addActionListener(e -> openCalendarHtml());
        lifeMenu.add(calendarItem);
        // ── 만년달력 갱신 ─────────────────────────────────────────
        JMenuItem calendarUpdateItem = makeMenuItem("🔄 만년달력 갱신", "GitHub 에서 최신 달력을 다운로드합니다");
        calendarUpdateItem.addActionListener(e -> updateCalendarHtml());
        lifeMenu.add(calendarUpdateItem);
        return lifeMenu;
	}
	
    // ── 현재위치 헬퍼 메서드 ─────────────────────────────────────
	
    private static final String KAKAO_API_KEY = "95fabf11da95950af909e81cb9083210";
    private static final int LOCATION_PORT    = 9999;
	
    /**
		* location.html 을 브라우저로 열고 내장 HTTP 서버로 위경도를 수신한다.
		* 수신 성공 시 Kakao API 로 주소 변환 후 다이얼로그 + 로그에 출력한다.
	*/
    private void doShowLocation() {
        // location.html 경로: JAR 옆 폴더
        java.io.File baseDir = null;
        try {
            java.security.CodeSource cs =
			SplashWindow.class.getProtectionDomain().getCodeSource();
            if (cs != null)
			baseDir = new java.io.File(cs.getLocation().toURI()).getParentFile();
		} catch (Exception ignored) {}
        if (baseDir == null) baseDir = new java.io.File(".");
		
        java.io.File htmlFile = new java.io.File(baseDir, "location.html");
        if (!htmlFile.exists()) {
            log("location.html 없음 — GitHub에서 다운로드 중...");
            final java.io.File fDest = htmlFile;
            new Thread(() -> {
                try {
                    String rawUrl =
					"https://raw.githubusercontent.com/GarpsuKim/Calendar_Lunar_-_HTML/main/location.html";
                    java.net.HttpURLConnection con =
					(java.net.HttpURLConnection) new java.net.URI(rawUrl).toURL().openConnection();
                    con.setConnectTimeout(10000);
                    con.setReadTimeout(30000);
                    con.connect();
                    int code = con.getResponseCode();
                    if (code != 200) {
                        con.disconnect();
                        SwingUtilities.invokeLater(() -> {
                            log("❌ location.html 다운로드 실패 (HTTP " + code + ")");
                            JOptionPane.showMessageDialog(SplashWindow.this,
                                "location.html 다운로드 실패 (HTTP " + code + ")",
							"현재위치", JOptionPane.ERROR_MESSAGE);
						});
                        return;
					}
                    try (java.io.InputStream in = con.getInputStream();
						java.io.FileOutputStream out = new java.io.FileOutputStream(fDest)) {
                        byte[] buf = new byte[8192]; int n;
                        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
					}
                    con.disconnect();
                    SwingUtilities.invokeLater(() -> {
                        log("✅ location.html 다운로드 완료 → " + fDest.getAbsolutePath());
                        doShowLocation();
					});
					} catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        log("❌ location.html 다운로드 오류: " + ex.getMessage());
                        JOptionPane.showMessageDialog(SplashWindow.this,
                            "다운로드 오류: " + ex.getMessage(),
						"현재위치", JOptionPane.ERROR_MESSAGE);
					});
				}
			}, "LocationDownload").start();
            return;
		}
		
        log("📍 현재위치 조회 시작...");
		
        new Thread(() -> {
            try (java.net.ServerSocket server = new java.net.ServerSocket(LOCATION_PORT)) {
                server.setSoTimeout(60000);
				
                // 브라우저로 location.html 열기
                final java.io.File fHtml = htmlFile;
                SwingUtilities.invokeLater(() -> {
                    try {
                        java.awt.Desktop.getDesktop().browse(fHtml.toURI());
                        log("브라우저에서 '현재 위치 가져오기' 버튼을 눌러주세요.");
						} catch (Exception ex) {
                        log("브라우저 열기 실패: " + ex.getMessage());
					}
				});
				
                // 위경도 수신
                double[] coords = locationWaitForCoords(server);
                if (coords == null) {
                    SwingUtilities.invokeLater(() -> {
                        log("❌ 위경도 수신 실패 (60초 초과)");
                        JOptionPane.showMessageDialog(SplashWindow.this,
                            "위경도를 받지 못했습니다.\n브라우저에서 위치 권한을 허용하고 버튼을 눌러주세요.",
						"현재위치", JOptionPane.WARNING_MESSAGE);
					});
                    return;
				}
				
                double lat = coords[0], lon = coords[1];
                SwingUtilities.invokeLater(() ->
				log(String.format("✅ 수신: %.6f, %.6f — 주소 변환 중...", lat, lon)));
				
                // Kakao 역지오코딩
                String address = locationReverseGeocode(lat, lon);
				
                SwingUtilities.invokeLater(() -> {
                    if (address != null) {
                        log("현재위치: " + address);
						} else {
                        log("❌ 주소 변환 실패");
                        JOptionPane.showMessageDialog(SplashWindow.this,
                            String.format("주소 변환 실패%n위도 %.6f  /  경도 %.6f", lat, lon),
						"현재위치", JOptionPane.WARNING_MESSAGE);
					}
                    // 카카오 지도 브라우저 열기
                    try {
                        String mapUrl = String.format(
						"https://map.kakao.com/link/map/현재위치,%.6f,%.6f", lat, lon);
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(mapUrl));
                        log("🗺 카카오 지도 열기: " + mapUrl);
						} catch (Exception ex) {
                        log("카카오 지도 열기 실패: " + ex.getMessage());
					}
				});
				
				} catch (java.net.BindException ex) {
                SwingUtilities.invokeLater(() -> {
                    log("❌ 포트 " + LOCATION_PORT + " 사용 중 — 이미 실행 중이거나 다른 프로그램이 점유 중");
                    JOptionPane.showMessageDialog(SplashWindow.this,
                        "포트 " + LOCATION_PORT + "이 이미 사용 중입니다.\n잠시 후 다시 시도하세요.",
					"현재위치", JOptionPane.ERROR_MESSAGE);
				});
				} catch (Exception ex) {
                SwingUtilities.invokeLater(() -> log("현재위치 오류: " + ex.getMessage()));
			}
		}, "LocationThread").start();
	}
	
    /** HTTP GET /location?lat=xx&lon=xx 한 건 수신 후 [lat, lon] 반환 */
    private double[] locationWaitForCoords(java.net.ServerSocket server) {
        try {
            java.net.Socket client = server.accept();
			
            // CORS 응답 먼저 전송
            java.io.PrintWriter w = new java.io.PrintWriter(client.getOutputStream());
            w.println("HTTP/1.1 200 OK");
            w.println("Access-Control-Allow-Origin: *");
            w.println("Content-Type: text/plain");
            w.println();
            w.println("OK");
            w.flush();
			
            java.io.BufferedReader r = new java.io.BufferedReader(
			new java.io.InputStreamReader(client.getInputStream()));
            String line = r.readLine();
            client.close();
			
            if (line == null || !line.contains("lat=")) return null;
            String query = line.split(" ")[1];           // /location?lat=..&lon=..
            double lat = 0, lon = 0;
            for (String p : query.split("[?&]")) {
                if (p.startsWith("lat=")) lat = Double.parseDouble(p.substring(4));
                if (p.startsWith("lon=")) lon = Double.parseDouble(p.substring(4));
			}
            return new double[]{lat, lon};
			
			} catch (java.net.SocketTimeoutException e) {
            return null;
			} catch (Exception e) {
            return null;
		}
	}
	
    /** Kakao coord2regioncode API → 행정동 주소 문자열 */
    private String locationReverseGeocode(double lat, double lon) {
        try {
            String urlStr = String.format(
                "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=%.6f&y=%.6f",
			lon, lat);
            java.net.HttpURLConnection conn =
			(java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
            conn.setRequestProperty("Authorization", "KakaoAK " + KAKAO_API_KEY);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
			
            int code = conn.getResponseCode();
            java.io.InputStream is;
            try { is = conn.getInputStream(); } catch (Exception e) { is = conn.getErrorStream(); }
            if (is == null) return null;
            java.io.BufferedReader br = new java.io.BufferedReader(
			new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String ln;
            while ((ln = br.readLine()) != null) sb.append(ln);
            conn.disconnect();
            if (code == 401) return null;
			
            String json = sb.toString();
            String addr = locationExtractAddress(json, "H");
            if (addr == null || addr.isEmpty()) addr = locationExtractAddress(json, "B");
            return addr;
		} catch (Exception e) { return null; }
	}
	
    private String locationExtractAddress(String json, String regionType) {
        String key = "\"region_type\":\"" + regionType + "\"";
        int ti = json.indexOf(key);
        if (ti < 0) return null;
        int bs = json.lastIndexOf("{", ti);
        int be = json.indexOf("}", ti);
        if (bs < 0 || be < 0) return null;
        String block = json.substring(bs, be + 1);
        String r1 = locationParseStr(block, "region_1depth_name");
        String r2 = locationParseStr(block, "region_2depth_name");
        String r3 = locationParseStr(block, "region_3depth_name");
        if (r1.isEmpty()) return null;
        StringBuilder out = new StringBuilder(r1);
        if (!r2.isEmpty()) out.append(" ").append(r2);
        if (!r3.isEmpty()) out.append(" ").append(r3);
        return out.toString();
	}
	
    private String locationParseStr(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) return "";
        int s = idx + search.length();
        int e = json.indexOf("\"", s);
        return e < 0 ? "" : json.substring(s, e);
	}
	
    // ── 만년달력 헬퍼 메서드 ─────────────────────────────────────
	
    /** 실행파일 옆 폴더의 calendar.html File 객체를 반환한다. */
    private java.io.File getCalendarFile() {
        java.io.File baseDir = null;
        try {
            java.security.CodeSource cs =
			SplashWindow.class.getProtectionDomain().getCodeSource();
            if (cs != null) {
                baseDir = new java.io.File(cs.getLocation().toURI()).getParentFile();
			}
		} catch (Exception ignored) {}
        if (baseDir == null) baseDir = new java.io.File(".");
        return new java.io.File(baseDir, "calendar.html");
	}
	
    /**
		* calendar.html 이 존재하면 기본 브라우저로 열고,
		* 없으면 "[만년달력 갱신]을 먼저 실행하세요" 다이얼로그를 표시한다.
	*/
    private void openCalendarHtml() {
        java.io.File calFile = getCalendarFile();
        if (!calFile.exists()) {
            JOptionPane.showMessageDialog(this,
                "[만년달력 갱신]을 먼저 실행하세요.",
			"만년달력", JOptionPane.WARNING_MESSAGE);
            return;
		}
        try {
            java.awt.Desktop.getDesktop().browse(calFile.toURI());
			} catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
			"브라우저 열기 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
		}
	}
	
    /**
		* 확인 다이얼로그 후 GitHub 에서 Calendar.html 을 다운로드하여
		* 기본 폴더에 calendar.html 로 저장한다.
	*/
    private void updateCalendarHtml() {
        int choice = JOptionPane.showConfirmDialog(this,
            "임시 공휴일 추가 등 만년 달력을 자동 갱신합니다.",
		"만년달력 갱신", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
		
        final String DOWNLOAD_URL =
		"https://raw.githubusercontent.com/GarpsuKim/Calendar_Lunar_-_HTML/main/Calendar.html";
        final java.io.File destFile = getCalendarFile();
		
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URI(DOWNLOAD_URL).toURL();
                java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(30000);
                con.connect();
                int code = con.getResponseCode();
                if (code != 200) {
                    con.disconnect();
                    javax.swing.SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this,
						"다운로드 실패 (HTTP " + code + ")", "만년달력 갱신", JOptionPane.ERROR_MESSAGE));
						return;
				}
                try (java.io.InputStream in  = con.getInputStream();
					java.io.FileOutputStream out = new java.io.FileOutputStream(destFile)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
				}
                con.disconnect();
                javax.swing.SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this,
                        "만년달력이 갱신되었습니다.\n저장 위치: " + destFile.getAbsolutePath(),
					"만년달력 갱신", JOptionPane.INFORMATION_MESSAGE));
					} catch (Exception ex) {
					javax.swing.SwingUtilities.invokeLater(() ->
						JOptionPane.showMessageDialog(this,
						"다운로드 오류: " + ex.getMessage(), "만년달력 갱신", JOptionPane.ERROR_MESSAGE));
			}
		}, "CalendarUpdate").start();
	}
	
    // ── [File] 메뉴 ──────────────────────────────────────────────
	
    private JMenu buildFileMenu() {
        JMenu fileMenu = makeMenu("File");
		
        // ── Open ──────────────────────────────────────────────────
        JMenuItem openItem = makeMenuItem("Open", "텍스트 파일을 열어 새 창에 표시");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        openItem.addActionListener(e -> doOpen());
        fileMenu.add(openItem);
		
        fileMenu.addSeparator();
		
        // ── Global ────────────────────────────────────────────────
        // clockHost 주입 전엔 비활성 JMenuItem, 주입 후엔 JMenu 로 교체
        if (clockHost != null) {
            JMenu globalMenu = clockHost.buildGlobalMenu();
            styleMenu(globalMenu, "🌍 Global");
            fileMenu.add(globalMenu);
			} else {
            JMenuItem globalDisabled = makeMenuItem("🌍 Global", "시계 초기화 후 사용 가능");
            globalDisabled.setEnabled(false);
            fileMenu.add(globalDisabled);
		}
		
        fileMenu.addSeparator();
		
        // ── SuperDir ──────────────────────────────────────────────
        JMenuItem superDirItem = makeMenuItem("📁 SuperDir", "디렉터리 재귀 탐색기를 엽니다");
        superDirItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK));
        superDirItem.addActionListener(e -> SuperDir.open(SplashWindow.this));
        fileMenu.add(superDirItem);
		
        fileMenu.addSeparator();
        // ── Close ─────────────────────────────────────────────────
        JMenuItem closeItem = makeMenuItem("Close", "이 창을 닫습니다 (시계는 유지)");
        closeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK));
        closeItem.addActionListener(e -> doClose());
        fileMenu.add(closeItem);
		
        // ── Exit ──────────────────────────────────────────────────
        JMenuItem exitItem = makeMenuItem("Exit", "프로그램을 종료합니다");
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        exitItem.addActionListener(e -> doExit());
        fileMenu.add(exitItem);
		
        return fileMenu;
	}
	
    // ═══════════════════════════════════════════════════════════
    //  [업무도구] 메뉴 — 사용자 슬롯 등록/수정/삭제/순서변경
    //
    //  ★ 설계 원칙 ★
    //  JMenuItem 위에서 MouseListener 로 우클릭을 잡는 방식은
    //  Swing 의 MenuSelectionManager 가 마우스 이벤트를 가로채기 때문에
    //  원천적으로 동작하지 않는다.
    //
    //  해결책: 각 슬롯을 JMenu(서브메뉴) 로 만들고,
    //  그 안에 "▶ 실행 / ✏️ 수정 / 🗑️ 삭제 / ⬆️ 위로 / ⬇️ 아래로" 항목을 배치.
    //  등록 안 된 슬롯은 "➕ 등록" 하나만 넣는다.
    //  모든 동작이 일반 ActionListener 로 처리되므로 완전히 안정적.
    // ═══════════════════════════════════════════════════════════
	
    private static final int OFFICE_SLOT_COUNT = 20;
	
    private static String slotNameKey(int i) { return "office.slot." + i + ".name"; }
    private static String slotPathKey(int i) { return "office.slot." + i + ".path"; }
	
    private JMenu buildOfficeMenu() {
        JMenu officeMenu = makeMenu("업무도구");
		
        // ── 고정 3종 (레지스트리 자동 연결) ──────────────────────────
        String[][] fixed = {
            { "📊 Excel",  "excel.exe"    },
            { "📝 Word",   "winword.exe"  },
            { "📑 PPT",    "powerpnt.exe" },
		};
        for (String[] app : fixed) {
            String label   = app[0];
            String exeName = app[1];
            JMenuItem item = makeMenuItem(label, exeName + " 실행");
            item.addActionListener(e -> launchByRegistry(exeName));
            officeMenu.add(item);
		}
		
        officeMenu.addSeparator();
		
        // ── 사용자 슬롯 20개 (각 슬롯 = JMenu 서브메뉴) ──────────────
        for (int i = 0; i < OFFICE_SLOT_COUNT; i++) {
            officeMenu.add(buildSlotMenu(i));
		}
		
        return officeMenu;
	}
	
    /**
		* 슬롯 하나를 JMenu(서브메뉴) 로 생성한다.
		*
		* 등록된 슬롯  : "📌 앱이름 ▶"  → [▶ 실행] [──] [✏️ 수정] [🗑️ 삭제] [──] [⬆️ 위로] [⬇️ 아래로]
		* 미등록 슬롯  : "── 빈 슬롯 N ── ▶"  → [➕ 등록]
		*
		* JMenuItem 위 MouseListener 우클릭 방식은 Swing MenuSelectionManager 가
		* 이벤트를 가로채므로 원천적으로 동작하지 않는다.
		* JMenu 서브메뉴 방식은 표준 ActionListener 만 사용하므로 완전히 안정적이다.
	*/
    private JMenu buildSlotMenu(int idx) {
        String name = (clockHost != null) ? clockHost.getConfig(slotNameKey(idx), "") : "";
        String path = (clockHost != null) ? clockHost.getConfig(slotPathKey(idx), "") : "";
        boolean reg = !name.isEmpty() && !path.isEmpty();
		
        JMenu slotMenu = makeMenu(reg ? "📌 " + name : "── 빈 슬롯 " + (idx + 1) + " ──");
        if (reg) slotMenu.setToolTipText(path);
		
        if (reg) {
            // ── ▶ 실행 ───────────────────────────────────────────
            JMenuItem runItem = makeMenuItem("▶  실행", path);
            final String p = path;
            runItem.addActionListener(e -> launchByPath(p));
            slotMenu.add(runItem);
			
            slotMenu.addSeparator();
			
            // ── ✏️ 수정 ───────────────────────────────────────────
            JMenuItem editItem = makeMenuItem("✏️  수정", null);
            editItem.addActionListener(e -> openSlotEditor(idx));
            slotMenu.add(editItem);
			
            // ── 🗑️ 삭제 ───────────────────────────────────────────
            JMenuItem delItem = makeMenuItem("🗑️  삭제", null);
            delItem.addActionListener(e -> deleteSlot(idx, name));
            slotMenu.add(delItem);
			
            slotMenu.addSeparator();
			
            // ── ⬆️ 위로 이동 ──────────────────────────────────────
            JMenuItem upItem = makeMenuItem("⬆️  위로 이동", null);
            upItem.setEnabled(idx > 0);
            upItem.addActionListener(e -> {
                int newIdx = idx - 1;
                swapSlots(idx, newIdx);
                SwingUtilities.invokeLater(() -> openSlotSubmenu(newIdx));
			});
            slotMenu.add(upItem);
			
            // ── ⬇️ 아래로 이동 ────────────────────────────────────
            JMenuItem downItem = makeMenuItem("⬇️  아래로 이동", null);
            downItem.setEnabled(idx < OFFICE_SLOT_COUNT - 1);
            downItem.addActionListener(e -> {
                int newIdx = idx + 1;
                swapSlots(idx, newIdx);
                SwingUtilities.invokeLater(() -> openSlotSubmenu(newIdx));
			});
            slotMenu.add(downItem);
			
			} else {
            // ── ➕ 등록 ───────────────────────────────────────────
            JMenuItem addItem = makeMenuItem("➕  등록", null);
            addItem.addActionListener(e -> openSlotEditor(idx));
            slotMenu.add(addItem);
		}
		
        return slotMenu;
	}
	
    /** 레지스트리 경로 조회 후 실행 */
    private void launchByRegistry(String exeName) {
        String path = null;
        try {
            Process proc = new ProcessBuilder(
                "reg", "query",
                "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\" + exeName,
                "/ve"
			).start();
            java.io.BufferedReader br = new java.io.BufferedReader(
			new java.io.InputStreamReader(proc.getInputStream(), "MS949"));
            String ln;
            while ((ln = br.readLine()) != null) {
                if (ln.contains("REG_SZ")) { path = ln.split("REG_SZ")[1].trim(); break; }
			}
		} catch (Exception ex) { path = null; }
        if (path == null || path.isEmpty()) {
            JOptionPane.showMessageDialog(this, exeName + " 경로를 찾을 수 없습니다.",
			"업무도구", JOptionPane.WARNING_MESSAGE);
            return;
		}
        launchByPath(path);
	}
	/*
		//  절대 경로로 실행 
		private void launchByPath(String path) {
        try { new ProcessBuilder(path).start(); }
        catch (Exception ex) {
		JOptionPane.showMessageDialog(this, "실행 실패: " + ex.getMessage(),
		"업무도구", JOptionPane.ERROR_MESSAGE);
        }
		}
	*/
	/** 절대 경로로 실행 (.lnk 포함) */
	private void launchByPath(String path) {
		try {
			String lower = path.toLowerCase();
			if (lower.endsWith(".lnk")) {
				// .lnk 는 ProcessBuilder 로 직접 실행 불가 → cmd /c start 로 위임
				new ProcessBuilder("cmd", "/c", "start", "", path).start();
				} else {
				new ProcessBuilder(path).start();
			}
			} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "실행 실패: " + ex.getMessage(),
			"업무도구", JOptionPane.ERROR_MESSAGE);
		}
	}
    /**
		* 슬롯 등록/수정 다이얼로그.
		* 저장 후 rebuildMenuBar() 로 메뉴 전체를 즉시 갱신한다.
	*/
    private void openSlotEditor(int idx) {
        if (clockHost == null) { showNotReady(); return; }
		
        String curName = clockHost.getConfig(slotNameKey(idx), "");
        String curPath = clockHost.getConfig(slotPathKey(idx), "");
		
        JTextField nameField = new JTextField(curName, 26);
        JTextField pathField = new JTextField(curPath, 34);
        pathField.setEditable(true); // 직접 입력·붙여넣기 허용
		
        JButton browseBtn = new JButton("찾아보기...");
        browseBtn.addActionListener(e -> {
            String cur = pathField.getText().trim();
            String startDir = cur.isEmpty()
			? System.getProperty("user.home")
			: new java.io.File(cur).getParent();
            JFileChooser fc = new JFileChooser(startDir);
            fc.setDialogTitle("실행 파일 선택");
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
			"실행 가능 파일 (*.exe, *.bat, *.cmd, *.lnk)", "exe", "bat", "cmd", "lnk"));
            fc.setAcceptAllFileFilterUsed(true);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                java.io.File sel = fc.getSelectedFile();
                pathField.setText(sel.getAbsolutePath());
                if (nameField.getText().trim().isEmpty()) {
                    String fn = sel.getName();
                    int dot = fn.lastIndexOf('.');
                    if (dot > 0) fn = fn.substring(0, dot);
                    nameField.setText(fn);
				}
			}
		});
		
        JPanel row1 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        row1.add(new JLabel("이름 : ")); row1.add(nameField);
        JPanel row2 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        row2.add(new JLabel("경로 : ")); row2.add(pathField); row2.add(browseBtn);
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(row1);
        panel.add(javax.swing.Box.createVerticalStrut(6));
        panel.add(row2);
		
        int result = JOptionPane.showConfirmDialog(this, panel,
            "업무도구 슬롯 " + (idx + 1) + " 등록",
		JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
		
        String newName = nameField.getText().trim();
        String newPath = pathField.getText().trim();
        if (newName.isEmpty() || newPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "이름과 경로를 모두 입력하세요.",
			"업무도구", JOptionPane.WARNING_MESSAGE);
            return;
		}
        clockHost.setMultipleConfigAndSave(
            slotNameKey(idx), newName,
            slotPathKey(idx), newPath
		);
        rebuildMenuBar();
	}
	
    /** 슬롯 삭제 확인 후 저장, 메뉴 재빌드 */
    private void deleteSlot(int idx, String displayName) {
        if (clockHost == null) { showNotReady(); return; }
        int ok = JOptionPane.showConfirmDialog(this,
            "「" + displayName + "」 을 삭제하시겠습니까?",
		"업무도구 삭제", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        clockHost.setMultipleConfigAndSave(
            slotNameKey(idx), "",
            slotPathKey(idx), ""
		);
        rebuildMenuBar();
	}
	
    /** 두 슬롯의 이름·경로를 교환하고 메뉴를 즉시 재빌드한다 */
    private void swapSlots(int srcIdx, int dstIdx) {
        if (clockHost == null) { showNotReady(); return; }
        String srcName = clockHost.getConfig(slotNameKey(srcIdx), "");
        String srcPath = clockHost.getConfig(slotPathKey(srcIdx), "");
        String dstName = clockHost.getConfig(slotNameKey(dstIdx), "");
        String dstPath = clockHost.getConfig(slotPathKey(dstIdx), "");
        clockHost.setMultipleConfigAndSave(
            slotNameKey(srcIdx), dstName,
            slotPathKey(srcIdx), dstPath,
            slotNameKey(dstIdx), srcName,
            slotPathKey(dstIdx), srcPath
		);
        rebuildMenuBar();
	}
	
    /** 메뉴바 전체를 EDT 에서 재빌드·적용한다 */
    private void rebuildMenuBar() {
        SwingUtilities.invokeLater(() -> {
            setJMenuBar(buildMenuBar());
            revalidate();
            repaint();
		});
	}
	
    /**
		* 재빌드 후 업무도구 메뉴의 특정 슬롯 서브메뉴를 프로그래밍으로 열어
		* 이동 후에도 메뉴가 닫히지 않고 해당 슬롯이 열린 채로 유지되게 한다.
	*/
    private void openSlotSubmenu(int slotIdx) {
        JMenuBar bar = getJMenuBar();
        for (int m = 0; m < bar.getMenuCount(); m++) {
            JMenu menu = bar.getMenu(m);
            if (menu == null || !"업무도구".equals(menu.getText())) continue;
			
            menu.doClick(); // 업무도구 메뉴 열기
			
            // 고정 항목(Excel/Word/PPT)은 JMenuItem, 슬롯은 JMenu — JMenu 만 카운트
            int slotCount = 0;
            for (int c = 0; c < menu.getMenuComponentCount(); c++) {
                java.awt.Component comp = menu.getMenuComponent(c);
                if (comp instanceof JMenu slotMenu) {
                    if (slotCount == slotIdx) {
                        slotMenu.doClick(); // 해당 슬롯 서브메뉴 열기
                        return;
					}
                    slotCount++;
				}
			}
		}
	}
	
    // ═══════════════════════════════════════════════════════════
    //  [Help] 메뉴
    // ═══════════════════════════════════════════════════════════
	
    private JMenu buildHelpMenu() {
        JMenu helpMenu = makeMenu("Help");
		
        // ── 프로그램 업그레이드 ───────────────────────────────────
        JMenuItem upgradeItem = makeMenuItem("🔄 프로그램 업그레이드", "GitHub 에서 최신 버전을 내려받아 덮어씁니다");
        upgradeItem.addActionListener(e -> doUpgradeNew());
        helpMenu.add(upgradeItem);
		
        helpMenu.addSeparator();
		
        // ── Log 조회 ──────────────────────────────────────────────
        JMenuItem logViewItem = makeMenuItem("📋 Log조회", "현재 로그 파일을 표시합니다");
        logViewItem.addActionListener(e -> doShowLogFile());
        helpMenu.add(logViewItem);
		
        // ── 지난 Log 데이타 삭제 ─────────────────────────────────
        JMenuItem logDeleteItem = makeMenuItem("🗑 지난Log데이타 삭제", "이전 날짜 로그 파일을 삭제합니다");
        logDeleteItem.addActionListener(e -> doDeleteOldLogs());
        helpMenu.add(logDeleteItem);
		
        helpMenu.addSeparator();
		
        // ── 기본 설정 파일 ────────────────────────────────────────
        JMenuItem iniItem = makeMenuItem("⚙️ 기본 설정 파일", "설정 파일(ini)을 표시합니다");
        iniItem.addActionListener(e -> doShowConfigFile());
        helpMenu.add(iniItem);
		
        helpMenu.addSeparator();
		
        // ── 프로그램 개발자 ─────────────────────────────────────────────────
        JMenuItem prorgammer = makeMenuItem("개발자 소개", "김갑수 / 대한민국 서울");
        prorgammer.addActionListener(e -> 
			{
				try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/GarpsuKim"));
					} catch (Exception ex) {
                    JOptionPane.showMessageDialog(SplashWindow.this,
					"브라우저 열기 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
				}
				
			}
		);
        helpMenu.add(prorgammer);
		
		// ── 프로그램 다운로드 ─────────────────────────────────────────────────
        JMenuItem pgmDownload = makeMenuItem("설치 파일", "끝판왕 (이 프로그램) 설치파일 다운로드");
        pgmDownload.addActionListener(e -> 
			{
				try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/GarpsuKim/KootPanKing/releases/tag/KootPanKing"));
					} catch (Exception ex) {
                    JOptionPane.showMessageDialog(SplashWindow.this,
					"브라우저 열기 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
				}
				
			}
		);
        helpMenu.add(pgmDownload);
		
		// ── 프로그램 소스 ─────────────────────────────────────────────────
        JMenuItem pgmSource = makeMenuItem("프로그램 소스", "Java 프로그램 소스");
        pgmSource.addActionListener(e -> 
			{
				try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/GarpsuKim/KootPanKing"));
					} catch (Exception ex) {
                    JOptionPane.showMessageDialog(SplashWindow.this,
					"브라우저 열기 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
				}
				
			}
		);
        helpMenu.add(pgmSource);
		
		// ── 자바 ─────────────────────────────────────────────────
        JMenuItem javaInstaller = makeMenuItem("Java/JVM ", "Java 환경 설치파일 다운로드");
        javaInstaller.addActionListener(e -> 
			{
				try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI("https://www.oracle.com/java"));
					} catch (Exception ex) {
                    JOptionPane.showMessageDialog(SplashWindow.this,
					"브라우저 열기 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
				}
				
			}
		);
        helpMenu.add(javaInstaller);
        // ── About ─────────────────────────────────────────────────
        JMenuItem aboutItem = makeMenuItem("About", "프로그램 정보");
        aboutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        aboutItem.addActionListener(e -> doShowAbout());
        helpMenu.add(aboutItem);
		
        return helpMenu;
	}
	
    // ═══════════════════════════════════════════════════════════
    //  메뉴 액션 구현
    // ═══════════════════════════════════════════════════════════
	
    /** File → Open : 텍스트 파일 선택 → 새 서브 창에 내용 표시 */
    private void doOpen() {
        // JFileChooser 생성자 + pack() 워밍업을 백그라운드에서 처리해
        // EDT 블로킹(화면 멈춤)을 방지한다.
        new Thread(() -> {
            JFileChooser fc = new JFileChooser(System.getProperty("user.home"));
            fc.setDialogTitle("텍스트 파일 열기");
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "텍스트 파일 (*.txt, *.log, *.ini, *.java, *.md, *.csv)",
			"txt", "log", "ini", "java", "md", "csv", "properties", "xml", "json", "html", "htm"));
            fc.setAcceptAllFileFilterUsed(true);
            // 숨겨진 JDialog에 넣어 pack()으로 UI 초기화 (EDT 블로킹 방지)
            try {
                javax.swing.JDialog warmup = new javax.swing.JDialog();
                warmup.add(fc);
                warmup.pack();
                warmup.remove(fc);
                warmup.dispose();
			} catch (Exception ignored) {}
			
            SwingUtilities.invokeLater(() -> {
                int result = fc.showOpenDialog(this);
                if (result != JFileChooser.APPROVE_OPTION) return;
				
                File file = fc.getSelectedFile();
                if (!file.exists()) {
                    JOptionPane.showMessageDialog(this,
                        "파일을 찾을 수 없습니다:\n" + file.getAbsolutePath(),
					"Open", JOptionPane.ERROR_MESSAGE);
                    return;
				}
                openTextFileWindow(file);
			});
		}, "FileChooserInit").start();
	}
	
    // ═══════════════════════════════════════════════════════════
    //  Help → 프로그램 업그레이드
    // ═══════════════════════════════════════════════════════════
	
    private static final String UPGRADE_ZIP_URL =
	"https://github.com/GarpsuKim/KootPanKing/releases/download/KootPanKing/KootPanKing.zip";
	
    /**
		* GitHub 에서 KootPanKing.zip 을 내려받아,
		* 기존 설치 폴더(A) 구조를 먼저 파악한 뒤
		* ZIP 내부 구조와 무관하게 A 와 동일한 구조로 임시 폴더(B) 에 재배치한다.
		* 이후 bat 을 생성·실행하고 현재 프로세스를 종료한다.
		*
		* ── 흐름 ──────────────────────────────────────────────────────
		*  [Java]
		*    ① installDir(A) 탐색  (.exe 위치 기준)
		*    ② ZIP 다운로드
		*    ③ ZIP 내부 구조 스캔 → 최상위 폴더명 확인
		*    ④ A 의 폴더 구조 스캔 → 파일명 → 상대경로 맵 생성
		*    ⑤ ZIP 를 풀면서 각 파일을 A 맵 기준 상대경로로 B 에 재배치
		*       (A 에 없는 신규 파일은 ZIP 원래 경로 그대로 B 에 배치)
		*    ⑥ updater.bat 생성 → cmd /c 실행 → System.exit
		*
		*  [bat]
		*    ping 대기 → robocopy B A /E /IS /IT → rmdir B → start exe → del 자신
		*
		* 실행 중인 .exe 를 Java 쪽에서 직접 덮어쓰면 Windows 파일 잠금으로
		* Access Denied 가 발생하므로 bat 으로 회피한다.
	*/
	
	// SplashWindow.java 내 doUpgrade() 메서드
	private void doUpgradeNew() {
		int confirm = JOptionPane.showConfirmDialog(this,
			"GitHub 에서 최신 버전을 다운로드하는 배치 파일을 실행합니다.\n\n"
			+ "배치 파일 실행 후 이 프로그램은 자동으로 종료됩니다.\n\n"
			+ "계속하시겠습니까?",
		"프로그램 업그레이드", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (confirm != JOptionPane.YES_OPTION) return;
		
		new Thread(() -> {
			try {
				// 1. GitHub에서 배치 파일 내용 읽기 (UTF-8)
				String batUrl = "https://raw.githubusercontent.com/GarpsuKim/KootPanKing/main/BAT/QuickUpGrade.BAT";
				java.net.URL url = new java.net.URI(batUrl).toURL();
				java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
				conn.setConnectTimeout(10000);
				conn.setReadTimeout(15000);
				
				// UTF-8로 읽기
				StringBuilder content = new StringBuilder();
				try (java.io.BufferedReader reader = new java.io.BufferedReader(
				new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\r\n");
				}
				}
				conn.disconnect();
				
				String batContent = content.toString();
				System.out.println("[Upgrade] 배치 파일 내용 길이: " + batContent.length());
				
				/*
					// 2. C:\temp 폴더 생성
					java.io.File tempDir = new java.io.File("C:\\temp");
					if (!tempDir.exists()) tempDir.mkdirs();				
					java.io.File batFile = new java.io.File(tempDir, "DownLoad_Release.BAT");
				*/
				
				// 2. app.exePath 기준 폴더 사용 (없으면 시스템 임시 폴더 폴백)
				
				
				String AppDir = resolveAppDir();
				File AppDirFile = new File(AppDir);
				String  saveZip =  AppDirFile.getAbsolutePath() + "appUpGrade.zip";
				GitHubZipDownload(saveZip);

				File parentDir = AppDirFile.getParentFile();  // 부모 폴더
				String parentPath = parentDir != null ? parentDir.getAbsolutePath() : "";
				java.io.File tempDir = new File(parentPath);
				if (!tempDir.exists()) tempDir.mkdirs();				
				java.io.File batFile = new java.io.File(tempDir, "QuickUpGrade.BAT");
				System.out.println("[Upgrade] DownLoad_Release.BAT : " + tempDir.getAbsolutePath());
				
				// 3. BOM 없는 UTF-8로 저장 (원본 그대로)
				try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
				new java.io.FileOutputStream(batFile), java.nio.charset.StandardCharsets.UTF_8)) {
                writer.write(batContent);
                writer.flush();
				}
				
				System.out.println("[Upgrade] 배치 파일 저장 완료 (UTF-8): " + batFile.getAbsolutePath());

				// 4. 배치 파일 실행
				ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "\"\"", batFile.getAbsolutePath());
				pb.directory(tempDir);
				pb.start();

				// 5. 프로그램 종료
				SwingUtilities.invokeLater(() -> {
					log("🚀 업데이터 실행됨 — 프로그램을 종료합니다.");
					new javax.swing.Timer(1000, e -> {
						if (clockHost != null) clockHost.exitAll();
						else System.exit(0);
					}).start();
				});
				} catch (Exception ex) {
				swingLog("❌ 업그레이드 오류: " + ex.getMessage());
				ex.printStackTrace();
				JOptionPane.showMessageDialog(SplashWindow.this,
					"업그레이드 중 오류가 발생했습니다:\n" + ex.getMessage(),
				"업그레이드 오류", JOptionPane.ERROR_MESSAGE);
			}
		}, "UpgradeThread").start();
	}
	
	
	private void doUpgrade() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "GitHub 에서 최신 버전을 내려받아 업그레이드합니다.\n\n"
            + "다운로드 완료 후 프로그램이 자동으로 종료되고\n"
            + "업데이터가 파일을 교체한 뒤 재시작합니다.\n\n"
            + "계속하시겠습니까?",
		"프로그램 업그레이드", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
		
        new Thread(() -> {
			
            // ── ① 설치 폴더(A) 탐색 ─────────────────────────────
            // AppLogger 는 .jar 경로를 반환할 수 있으므로
            // .exe 를 찾을 때까지 최대 2단계 위로 올라가며 탐색
            String exePathStr = AppLogger.getExeFilePath();
            java.io.File exeFile = new java.io.File(exePathStr).getAbsoluteFile();
			
            java.io.File installDir = exeFile.getParentFile() != null
			? exeFile.getParentFile() : new java.io.File(".");
            String exeFileName = null;
			
            for (int up = 0; up <= 2 && exeFileName == null; up++) {
                java.io.File dir = installDir;
                for (int i = 0; i < up; i++)
				if (dir.getParentFile() != null) dir = dir.getParentFile();
                java.io.File[] found = dir.listFiles(
				f -> f.isFile() && f.getName().toLowerCase().endsWith(".exe"));
                if (found != null && found.length > 0) {
                    java.io.File picked = found[0];
                    for (java.io.File f : found)
					if (f.getName().toLowerCase().contains("kootpanking")) { picked = f; break; }
                    exeFileName = picked.getName();
                    installDir  = dir;
				}
			}
            if (exeFileName == null) exeFileName = exeFile.getName();
			
            final java.io.File finalInstallDir = installDir;
            final String       finalExeName    = exeFileName;
            log("📁 설치 폴더 (A): " + finalInstallDir.getAbsolutePath());
            log("▶  재시작 대상  : " + finalExeName);
			
            // ── ② 임시 폴더 준비 ────────────────────────────────
            java.io.File tmpDir = new java.io.File(
			System.getProperty("java.io.tmpdir"), "KootPanKing_upg");
            if (tmpDir.exists()) deleteDir(tmpDir);
            tmpDir.mkdirs();
            final java.io.File finalTmpDir = tmpDir;
            log("📁 임시 폴더    : " + finalTmpDir.getAbsolutePath());
			
            // ── ③ ZIP 다운로드 ───────────────────────────────────
            java.io.File tmpZip = new java.io.File(tmpDir, "update.zip");
            log("⬇️  다운로드 시작: " + UPGRADE_ZIP_URL);
            try {
                java.net.HttpURLConnection con =
				(java.net.HttpURLConnection) new java.net.URI(UPGRADE_ZIP_URL)
				.toURL().openConnection();
                con.setConnectTimeout(15000);
                con.setReadTimeout(120000);
                con.setInstanceFollowRedirects(true);
                con.connect();
                int code = con.getResponseCode();
                if (code != 200) {
                    con.disconnect();
                    swingLog("❌ 다운로드 실패 (HTTP " + code + ")"); return;
				}
                long total = con.getContentLengthLong();
                try (java.io.InputStream in = con.getInputStream();
					java.io.FileOutputStream fos = new java.io.FileOutputStream(tmpZip)) {
                    byte[] buf = new byte[32768]; long done = 0; int n;
                    while ((n = in.read(buf)) != -1) {
                        fos.write(buf, 0, n); done += n;
                        if (total > 0) {
                            final long d = done, t = total;
                            SwingUtilities.invokeLater(() ->
                                setStatus(String.format("다운로드 중... %d KB / %d KB",
								d / 1024, t / 1024)));
						}
					}
				}
                con.disconnect();
                log(String.format("✅ 다운로드 완료 (%.1f KB)", tmpZip.length() / 1024.0));
				} catch (Exception ex) {
                swingLog("❌ 다운로드 오류: " + ex.getMessage()); return;
			}
			
            // ── ④ ZIP 내부 구조 스캔 → 최상위 폴더명 확인 ─────
            // ZIP 가 "KootPanKing/..." 처럼 최상위 폴더를 가지는 경우 제거할 prefix 를 구한다.
            // 모든 엔트리의 공통 최상위 폴더명을 찾아 prefix 로 사용한다.
            // (최상위 폴더가 없거나 엔트리마다 다르면 prefix = "")
            log("🔍 ZIP 구조 분석 중...");
            String zipPrefix = "";
            try (java.util.zip.ZipInputStream zis =
				new java.util.zip.ZipInputStream(
					new java.io.BufferedInputStream(
					new java.io.FileInputStream(tmpZip)))) {
					java.util.zip.ZipEntry entry;
					String commonTop = null;   // 공통 최상위 폴더명
					boolean consistent = true; // 모든 엔트리가 같은 최상위 폴더인지
					while ((entry = zis.getNextEntry()) != null) {
						String name = entry.getName().replace('\\', '/');
						int slash = name.indexOf('/');
						String top = (slash >= 0) ? name.substring(0, slash) : "";
						if (top.isEmpty()) { consistent = false; zis.closeEntry(); break; }
						if (commonTop == null) commonTop = top;
						else if (!commonTop.equals(top)) { consistent = false; zis.closeEntry(); break; }
						zis.closeEntry();
					}
					if (consistent && commonTop != null) {
						zipPrefix = commonTop + "/";
						log("   ZIP 최상위 폴더: [" + commonTop + "] → 경로에서 제거");
						} else {
						log("   ZIP 최상위 폴더 없음 → 경로 그대로 사용");
					}
					} catch (Exception ex) {
					swingLog("❌ ZIP 구조 분석 오류: " + ex.getMessage()); return;
			}
            final String finalZipPrefix = zipPrefix;
			
            // ── ⑤ 기존 설치 폴더(A) 의 파일명 → 상대경로 맵 생성 ─
            // 파일명(소문자) → A 기준 상대경로  (예: "kootpanking.jar" → "app/KootPanKing.jar")
            // 같은 이름이 여러 경로에 있을 수 있으므로 List 로 관리
            log("🗂️  설치 폴더 구조 스캔 중...");
            java.util.Map<String, java.util.List<String>> installMap = new java.util.HashMap<>();
            buildInstallMap(finalInstallDir, finalInstallDir, installMap);
            log("   스캔 완료: " + installMap.size() + " 종류의 파일명 인식");
			
            // ── ⑥ ZIP 를 풀면서 A 구조에 맞게 B(extractDir) 에 재배치 ─
            log("📦 압축 해제 & 구조 재배치 중...");
            java.io.File extractDir = new java.io.File(tmpDir, "extracted");
            extractDir.mkdirs();
            int placed = 0, newFile = 0;
            try (java.util.zip.ZipInputStream zis =
				new java.util.zip.ZipInputStream(
					new java.io.BufferedInputStream(
					new java.io.FileInputStream(tmpZip)))) {
					java.util.zip.ZipEntry entry;
					while ((entry = zis.getNextEntry()) != null) {
						// 최상위 폴더 prefix 제거
						String name = entry.getName().replace('\\', '/');
						if (!finalZipPrefix.isEmpty() && name.startsWith(finalZipPrefix))
                        name = name.substring(finalZipPrefix.length());
						if (name.isEmpty()) { zis.closeEntry(); continue; }
						
						if (entry.isDirectory()) {
							zis.closeEntry(); continue; // 폴더는 파일 복사 시 자동 생성
						}
						
						// 파일명(소문자) 로 A 의 상대경로 조회
						String fileName = name.contains("/")
                        ? name.substring(name.lastIndexOf('/') + 1) : name;
						String targetRelPath = resolveTargetPath(
						fileName.toLowerCase(), name, installMap);
						
						java.io.File dest = new java.io.File(extractDir, targetRelPath);
						java.io.File par  = dest.getParentFile();
						if (par != null) par.mkdirs();
						
						try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
							byte[] buf = new byte[32768]; int n;
							while ((n = zis.read(buf)) != -1) fos.write(buf, 0, n);
						}
						
						// 신규 파일(A 에 없던 것) 여부 로그
						if (targetRelPath.equals(name)) newFile++;
						else placed++;
						
						zis.closeEntry();
					}
					} catch (Exception ex) {
					swingLog("❌ 압축 해제 오류: " + ex.getMessage()); return;
			}
            tmpZip.delete();
            log(String.format("✅ 재배치 완료 — 기존 경로 매핑: %d개, 신규 파일: %d개",
			placed, newFile));
            log("   B 폴더: " + extractDir.getAbsolutePath());
			
            // ── ⑦ updater.bat 생성 ──────────────────────────────
            // ★ GitHub 에서 템플릿을 1줄씩 읽어 플레이스홀더 치환 후 저장
            // ★ bat 을 tmpDir 바깥(TEMP 루트)에 저장 → rmdir 시 자신이 삭제되는 문제 방지
            // ★ MS949 인코딩 → cmd.exe 가 경로의 한글을 올바르게 읽음
            java.io.File batFile = new java.io.File(
			System.getProperty("java.io.tmpdir"), "kpk_updater.bat");
            // bat 실행 로그 파일 — tmpDir 바깥(TEMP 루트)에 저장, 자동 삭제 안 함
            java.io.File batLog = new java.io.File(
			System.getProperty("java.io.tmpdir"), "kpk_updater_log.txt");
            String installAbs = finalInstallDir.getAbsolutePath();
            String extractAbs = extractDir.getAbsolutePath();
            String tmpAbs     = finalTmpDir.getAbsolutePath();
            String logAbs     = batLog.getAbsolutePath();
			
            // ── GitHub 에서 템플릿 다운로드 → 플레이스홀더 치환 → bat 저장 ──
            final String TEMPLATE_URL =
			"https://raw.githubusercontent.com/GarpsuKim/KootPanKing/main/BAT/kpk_updater_template.bat";
            log("⬇️  updater 템플릿 다운로드: " + TEMPLATE_URL);
            try {
                java.net.HttpURLConnection con =
				(java.net.HttpURLConnection) new java.net.URI(TEMPLATE_URL)
				.toURL().openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(15000);
                con.connect();
                int code = con.getResponseCode();
                if (code != 200) {
                    con.disconnect();
                    swingLog("❌ 템플릿 다운로드 실패 (HTTP " + code + ")"); return;
				}
				
				try (java.io.BufferedReader br = new java.io.BufferedReader(
					new java.io.InputStreamReader(con.getInputStream(),
					java.nio.charset.StandardCharsets.UTF_8));
					java.io.PrintWriter pw = new java.io.PrintWriter(
						new java.io.OutputStreamWriter(
						new java.io.FileOutputStream(batFile), "MS949"))) {
						
						String line;
						while ((line = br.readLine()) != null) {
							line = line
                            .replace("{{INSTALL_DIR}}", installAbs)
                            .replace("{{EXTRACT_DIR}}", extractAbs)
                            .replace("{{TMP_DIR}}",     tmpAbs)
                            .replace("{{LOG_PATH}}",    logAbs)
                            .replace("{{EXE_NAME}}",    finalExeName);
							pw.print(line + "\r\n");
						}
				}
                con.disconnect();
				} catch (Exception ex) {
                swingLog("❌ updater.bat 생성 실패: " + ex.getMessage()); return;
			}
			
            log("✅ updater.bat 생성 완료 → " + batFile.getAbsolutePath());
            log("📋 bat 실행 로그  → " + batLog.getAbsolutePath());
            log("   (업그레이드 후 위 파일에서 성공/실패 내용 확인 가능)");
			
            // ── ⑧ bat 독립 실행 후 Java 종료 ──────────────────────────────
            SwingUtilities.invokeLater(() -> {
                log("🚀 업데이터 실행 — 프로그램을 종료합니다...");
                setStatus("업그레이드 중... 재시작 대기");
			});
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
			
            try {
                // cmd /c start "" batFile
                // → 타이틀 "" 로 새 cmd 창 생성, Java 종료해도 bat 독립 실행됨
                String batPath = batFile.getAbsolutePath();
                new ProcessBuilder("cmd", "/c", "start", "", batPath).start();
				} catch (Exception ex) {
                swingLog("❌ updater.bat 실행 실패: " + ex.getMessage()); return;
			}
			
            // bat 기동 여유 후 Java 종료
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(() -> {
                if (clockHost != null) clockHost.exitAll();
                else System.exit(0);
			});
			
		}, "UpgradeThread").start();
	}
	
    /**
		* 설치 폴더(A) 를 재귀 스캔하여
		* 파일명(소문자) → List<상대경로(슬래시 구분)> 맵을 만든다.
		*
		* 예)  "kootpanking.jar" → ["app/KootPanKing.jar"]
		*      "jvm.dll"         → ["runtime/bin/server/jvm.dll", ...]
	*/
    private static void buildInstallMap(java.io.File base, java.io.File cur,
		java.util.Map<String, java.util.List<String>> map) {
        java.io.File[] entries = cur.listFiles();
        if (entries == null) return;
        for (java.io.File f : entries) {
            if (f.isDirectory()) {
                buildInstallMap(base, f, map);
				} else {
                // base 기준 상대경로를 슬래시로 정규화
                String rel = base.toURI().relativize(f.toURI()).getPath();
                String key = f.getName().toLowerCase();
                map.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(rel);
			}
		}
	}
	
    /**
		* ZIP 에서 꺼낸 파일을 B 의 어느 상대경로에 놓을지 결정한다.
		*
		* ① installMap 에 파일명(소문자)이 1건만 있으면 → 그 경로 사용
		* ② 2건 이상이면 → ZIP 상대경로의 상위 디렉터리명이 installMap 경로와
		*    가장 많이 겹치는 것을 선택
		* ③ installMap 에 없으면(신규 파일) → ZIP 원래 경로 그대로 사용
	*/
    private static String resolveTargetPath(String fileNameLower, String zipRelPath,
		java.util.Map<String, java.util.List<String>> installMap) {
        java.util.List<String> candidates = installMap.get(fileNameLower);
        if (candidates == null || candidates.isEmpty()) return zipRelPath; // 신규 파일
		
        if (candidates.size() == 1) return candidates.get(0); // 유일한 매핑
		
        // 2건 이상: ZIP 경로 세그먼트와 가장 많이 일치하는 후보 선택
        String[] zipParts = zipRelPath.toLowerCase().split("/");
        String best = candidates.get(0);
        int bestScore = -1;
        for (String candidate : candidates) {
            String[] cParts = candidate.toLowerCase().split("/");
            int score = 0;
            for (String zp : zipParts)
			for (String cp : cParts)
			if (zp.equals(cp)) score++;
            if (score > bestScore) { bestScore = score; best = candidate; }
		}
        return best;
	}
	
    /** 디렉터리 재귀 삭제 (updater 임시 폴더 초기화용) */
    private static void deleteDir(java.io.File dir) {
        if (dir == null || !dir.exists()) return;
        java.io.File[] files = dir.listFiles();
        if (files != null) for (java.io.File f : files) {
            if (f.isDirectory()) deleteDir(f); else f.delete();
		}
        dir.delete();
	}
	
    /** 백그라운드 스레드에서 EDT 로그 출력 (짧은 람다 대용) */
    private void swingLog(String msg) {
        SwingUtilities.invokeLater(() -> log(msg));
	}
	
    /** File → Close / X버튼 : 창 숨기기 + ini mainWindow 값 제거 */
    private void doClose() {
        if (clockHost != null) clockHost.onClose();
        setVisible(false);
	}
	
    /** File → Exit : 30초 카운트다운 종료 확인 */
    private void doExit() {
        // ── 다이얼로그 구성 ───────────────────────────────────────
        JLabel msgLabel = new JLabel("프로그램을 종료하시겠습니까?");
        msgLabel.setFont(new Font("Malgun Gothic", Font.PLAIN, 13));
        msgLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		
        JPanel msgPanel = new JPanel(new BorderLayout());
        msgPanel.add(new JLabel(UIManager.getIcon("OptionPane.questionIcon")), BorderLayout.WEST);
        msgPanel.add(msgLabel, BorderLayout.CENTER);
		
        JButton yesBtn = new JButton("Yes");
        JButton noBtn  = new JButton("No");
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        btnPanel.add(yesBtn);
        btnPanel.add(noBtn);
		
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(msgPanel, BorderLayout.CENTER);
        root.add(btnPanel, BorderLayout.SOUTH);
		
        JDialog dlg = new JDialog(this, "종료 확인", true);
        dlg.setContentPane(root);
        dlg.pack();
        dlg.setLocationRelativeTo(null);  // 모니터 중앙
        dlg.setAlwaysOnTop(true);
		
        // ── 30초 카운트다운 → 시간 초과 시 취소 ──────────────────
        final int[] sec = { 15 };
        final boolean[] confirmed = { false };
        javax.swing.Timer countdown = new javax.swing.Timer(1000, null);
        countdown.addActionListener(e -> {
            sec[0]--;
            dlg.setTitle("종료 확인  — " + sec[0] + "초 후 취소");
            if (sec[0] <= 0) { countdown.stop(); dlg.dispose(); }
		});
        countdown.start();
		
        yesBtn.addActionListener(e -> { confirmed[0] = true;  countdown.stop(); dlg.dispose(); });
        noBtn .addActionListener(e -> { confirmed[0] = false; countdown.stop(); dlg.dispose(); });
		
        dlg.setVisible(true);  // modal 블로킹
		
        if (!confirmed[0]) return;
		
        if (clockHost != null) {
            clockHost.exitAll();
			} else {
            AppLogger.close();
            System.exit(0);
		}
	}
	
    /** Help → Log조회 */
    private void doShowLogFile() {
        if (clockHost == null) { showNotReady(); return; }
        String logPath = clockHost.getLogFilePath();
        if (logPath == null || logPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "로그 파일 경로를 찾을 수 없습니다.", "Log조회", JOptionPane.WARNING_MESSAGE);
            return;
		}
        java.io.File logFile = new java.io.File(logPath);
        if (!logFile.exists()) {
            JOptionPane.showMessageDialog(this, "로그 파일이 존재하지 않습니다.\n" + logPath, "Log조회", JOptionPane.WARNING_MESSAGE);
            return;
		}
        try {
            String logText;
            try (java.io.BufferedReader br = new java.io.BufferedReader(
			new java.io.InputStreamReader(new java.io.FileInputStream(logFile), "UTF-8"))) {
			StringBuilder sb = new StringBuilder(); String line;
			while ((line = br.readLine()) != null) sb.append(line).append("\n");
			logText = sb.toString();
            }
            String escaped = logText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            java.io.File htmlFile = java.io.File.createTempFile("applog_", ".html");
            htmlFile.deleteOnExit();
            try (java.io.PrintWriter pw = new java.io.PrintWriter(
			new java.io.OutputStreamWriter(new java.io.FileOutputStream(htmlFile), "UTF-8"))) {
			pw.println("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>끝판왕 로그</title><style>");
			pw.println("body{font-family:'Consolas','Malgun Gothic',monospace;background:#0d0d0d;color:#c8ffc8;padding:20px;line-height:1.6;}");
			pw.println("pre{white-space:pre-wrap;font-size:13px;}</style></head><body><pre>");
			pw.println(escaped); pw.println("</pre></body></html>");
            }
            java.awt.Desktop.getDesktop().browse(htmlFile.toURI());
			} catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "로그 파일 열기 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
		}
	}
	
    /** Help → 지난Log데이타 삭제 */
    private void doDeleteOldLogs() {
        if (clockHost == null) { showNotReady(); return; }
        String logPath = clockHost.getLogFilePath();
        if (logPath == null || logPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "로그 파일 경로를 찾을 수 없습니다.", "Log삭제", JOptionPane.WARNING_MESSAGE);
            return;
		}
        java.io.File logDir = new java.io.File(logPath).getParentFile();
        if (logDir == null || !logDir.exists()) {
            JOptionPane.showMessageDialog(this, "로그 폴더를 찾을 수 없습니다.", "Log삭제", JOptionPane.WARNING_MESSAGE);
            return;
		}
        java.io.File currentLog = new java.io.File(logPath);
        java.io.File[] oldFiles = logDir.listFiles(f ->
		f.isFile() && f.getName().endsWith(".txt") && !f.getAbsolutePath().equals(currentLog.getAbsolutePath()));
        if (oldFiles == null || oldFiles.length == 0) {
            JOptionPane.showMessageDialog(this, "삭제할 지난 로그 파일이 없습니다.", "Log삭제", JOptionPane.INFORMATION_MESSAGE);
            return;
		}
        int ans = JOptionPane.showConfirmDialog(this,
            "지난 로그 파일 " + oldFiles.length + "개를 삭제하시겠습니까?\n폴더: " + logDir.getAbsolutePath(),
		"지난Log데이타 삭제", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ans != JOptionPane.YES_OPTION) return;
        int deleted = 0;
        for (java.io.File f : oldFiles) { if (f.delete()) deleted++; }
        JOptionPane.showMessageDialog(this, deleted + "개 삭제 완료.", "Log삭제", JOptionPane.INFORMATION_MESSAGE);
	}
	
    /** Help → 기본 설정 파일 */
    private void doShowConfigFile() {
        if (clockHost == null) {
            showNotReady();
            return;
		}
        clockHost.showConfigFile();
	}
	
    /** Help → About */
    private void doShowAbout() {
        if (clockHost == null) {
            showNotReady();
            return;
		}
        clockHost.showAbout();
	}
	
    // ═══════════════════════════════════════════════════════════
    //  Open : 텍스트 파일 뷰어 서브 창
    // ═══════════════════════════════════════════════════════════
	
    /**
		* 텍스트 파일을 읽어 독립 JFrame 서브 창에 표시.
		* UTF-8 → EUC-KR → ISO-8859-1 순으로 인코딩 자동 탐지.
	*/
    private void openTextFileWindow(File file) {
        new Thread(() -> {
            try {
				TextFileReader.Result r = TextFileReader.read(file);				
                SwingUtilities.invokeLater(() -> showTextWindow(file, r.encLabel , r.content ));							
				log("파일 열기: " + file.getName());
				} catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this,
                        "파일 읽기 실패:\n" + ex.getMessage(),
					"Open", JOptionPane.ERROR_MESSAGE));
					log("[ERROR] 파일 열기 실패: " + file.getName() + " — " + ex.getMessage());
			}
		}, "FileOpen").start();
	}
	/** 텍스트 내용을 새 창에 표시 */
	private void showTextWindow(File file, String encLabel, String content) {
		JFrame sub = new JFrame("📄 " + file.getName());
		sub.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		JTextArea ta = new JTextArea(content);
		ta.setEditable(false);
		ta.setFont(new Font("돋움체", Font.PLAIN, 16));
		ta.setBackground(new Color(235, 245, 255));
		ta.setForeground(new Color( 20,  50,  90));
		ta.setCaretColor(new Color( 20,  50,  90));
		ta.setLineWrap(true);
		ta.setWrapStyleWord(false);
		ta.setMargin(new Insets(6, 8, 6, 8));
		
		// ── 줄번호 패널 (modelToView2D 기반) ─────────────────────────
		final JScrollPane[] spRef = { null };  // 순환 참조 해결용
		JPanel lineNumPanel = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				Graphics2D g2 = (Graphics2D) g;
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g2.setFont(ta.getFont());
				g2.setColor(new Color(100, 130, 170));
				
				FontMetrics fm = g2.getFontMetrics();
				int lineCount = ta.getLineCount();
				for (int i = 0; i < lineCount; i++) {
					try {
						int startOffset = ta.getLineStartOffset(i);
						java.awt.geom.Rectangle2D r =
                        ta.modelToView2D(startOffset);
						if (r == null) continue;
						int y = (int) r.getY() + fm.getAscent();
						String num = String.format("%4d", i + 1);
						g2.drawString(num, 4, y);
					} catch (Exception ignored) {}
				}
			}
			
			@Override
			public Dimension getPreferredSize() {
				int digits = String.valueOf(ta.getLineCount()).length();
				int w = ta.getFontMetrics(ta.getFont()).charWidth('0') * (digits + 2) + 12;
				int viewH = (spRef[0] != null) ? spRef[0].getViewport().getHeight() : 0;
				int h = Math.max(ta.getPreferredSize().height, viewH);
				return new Dimension(w, h);
			}
		};
		lineNumPanel.setBackground(new Color(210, 225, 240));
		lineNumPanel.setOpaque(true);
		
		JScrollPane sp = new JScrollPane(ta);
		spRef[0] = sp;
		sp.setBorder(BorderFactory.createEmptyBorder());
		sp.getViewport().setBackground(new Color(235, 245, 255));
		sp.setRowHeaderView(lineNumPanel);
		
		// ta 크기/내용/스크롤 변경 시 줄번호 패널 갱신
		ta.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e)  { lineNumPanel.revalidate(); lineNumPanel.repaint(); }
			public void removeUpdate(javax.swing.event.DocumentEvent e)  { lineNumPanel.revalidate(); lineNumPanel.repaint(); }
			public void changedUpdate(javax.swing.event.DocumentEvent e) { lineNumPanel.revalidate(); lineNumPanel.repaint(); }
		});
		ta.addComponentListener(new ComponentAdapter() {
			@Override public void componentResized(ComponentEvent e) { lineNumPanel.revalidate(); lineNumPanel.repaint(); }
		});
		sp.getViewport().addChangeListener(e -> { lineNumPanel.revalidate(); lineNumPanel.repaint(); });
		
		// 상태바: 인코딩 + 파일 경로 + 크기
		JLabel info = new JLabel(
		" " + encLabel + "  |  " + file.getAbsolutePath() + "  (" + file.length() + " bytes)");
		info.setFont(new Font("Malgun Gothic", Font.PLAIN, 11));
		info.setForeground(new Color( 20,  60, 120));
		info.setBackground(new Color(200, 225, 245));
		info.setOpaque(true);
		info.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(140, 180, 210)),
		BorderFactory.createEmptyBorder(2, 4, 2, 4)));
		
		sub.setLayout(new BorderLayout());
		sub.add(sp,   BorderLayout.CENTER);
		sub.add(info, BorderLayout.SOUTH);
		
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int w = Math.max(600, Math.min((int)(screen.width  * 0.70), 1100));
		int h = Math.max(400, Math.min((int)(screen.height * 0.70),  800));
		sub.setSize(w, h);
		
		sub.setLocationRelativeTo(this);
		Point p = sub.getLocation();
		sub.setLocation(p.x + 30, p.y + 30);
		
		sub.setVisible(true);
	}
	
    // ═══════════════════════════════════════════════════════════
    //  로그 출력 내부 구현
    // ═══════════════════════════════════════════════════════════
	
    /** EDT 위에서 호출해야 한다 */
    private void appendLog(String message) {
        try {
            String ts = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
			
            // 타임스탬프
            doc.insertString(doc.getLength(), "[" + ts + "] ", tsStyle);
			
            // 메시지
            doc.insertString(doc.getLength(), message, msgStyle);
			
            // 줄바꿈
            doc.insertString(doc.getLength(), "\n", msgStyle);
			
            // 자동 스크롤 맨 아래
            logPane.setCaretPosition(doc.getLength());
			
            // 상태바도 최신 메시지로 갱신
            String shortMsg = message.length() > 80
			? message.substring(0, 78) + "…"
			: message;
            statusBar.setText(" " + shortMsg);
			
		} catch (BadLocationException ignored) {}
	}
	
    // ═══════════════════════════════════════════════════════════
    //  유틸
    // ═══════════════════════════════════════════════════════════
	
    /** 시계 미초기화 안내 */
    private void showNotReady() {
        JOptionPane.showMessageDialog(this,
            "시계가 아직 초기화되지 않았습니다.\n잠시 후 다시 시도하세요.",
		"알림", JOptionPane.INFORMATION_MESSAGE);
	}
	
    /* 파일을 UTF-8 → UTF-16 BE → EUC-KR 순으로 자동 인코딩 탐지하여 읽기
	*  반환: [0] = 인코딩 레이블,  [1] = 파일 내용 */
	private String[] readFileAutoEncoding(File file) throws IOException {	
		try (FileInputStream fis = new FileInputStream(file)) {			
			// ① BOM 3바이트 확인
			byte[] bom = new byte[3];
			int bomRead = fis.read(bom, 0, 3);
			
			// BOM 확정
			if (bomRead >= 2 && bom[0] == (byte)0xFE && bom[1] == (byte)0xFF) {
				// UTF-16 BE BOM → 남은 스트림 이어붙여 바로 읽기
				InputStream rest = new SequenceInputStream(
				new ByteArrayInputStream(bom, 0, bomRead), fis);
				return new String[]{ "[ UTF-16 BE BOM ]", readAll(rest, "UTF-16BE") };
			}
			
			if (bomRead >= 3 && bom[0] == (byte)0xEF && bom[1] == (byte)0xBB && bom[2] == (byte)0xBF) {
				// UTF-8 BOM → BOM 3바이트 건너뛰고 나머지만 읽기
				return new String[]{ "[ UTF-8 BOM ]", readAll(fis, "UTF-8") };
			}
			
			// ② BOM 없음 → 전체를 바이트 버퍼에 읽어서 UTF-8 검증 후 디코딩
			ByteArrayOutputStream baos = new ByteArrayOutputStream((int) Math.min(file.length(), 4 * 1024 * 1024));
			baos.write(bom, 0, bomRead);  // 이미 읽은 3바이트 먼저
			byte[] buf = new byte[8192];
			int n;
			while ((n = fis.read(buf)) != -1) baos.write(buf, 0, n);
			byte[] all = baos.toByteArray();
			
			String enc, encLabel;
			if (isValidUTF8(all)) {
				enc      = "UTF-8";
				encLabel = "[ UTF-8 ]";
				} else {
				enc      = "CP949";
				encLabel = "[ CP949 ]";
			}
			return new String[]{ encLabel, new String(all, enc) };
		}
	}
	
	
	private static String readAll(InputStream is, String enc) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, enc));
		StringBuilder sb = new StringBuilder();
		String line;
		
		boolean first = true;
		while ((line = br.readLine()) != null) {
			if (!first) sb.append("\n");
			sb.append(line);
			first = false;
		}				
		return sb.toString();
	}
	
	private static boolean isValidUTF8(byte[] all) {
		try {
			CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder();
			dec.onMalformedInput(CodingErrorAction.REPORT);
			dec.onUnmappableCharacter(CodingErrorAction.REPORT);
			dec.decode(ByteBuffer.wrap(all));
			return true;
		} catch (Exception e) { return false; }
	}	
	
    // ── 메뉴 스타일 헬퍼 ─────────────────────────────────────────
    private JMenu makeMenu(String text) {
        JMenu m = new JMenu(text);
        m.setForeground(new Color( 20,  50,  90));
        m.setFont(new Font("Malgun Gothic", Font.PLAIN, 13));
        m.getPopupMenu().setBackground(new Color(210, 235, 250));
        return m;
	}
    private JMenuItem makeMenuItem(String text, String tooltip) {
        JMenuItem item = new JMenuItem(text);
        item.setForeground(new Color( 20,  50,  90));
        item.setBackground(new Color(210, 235, 250));
        item.setFont(new Font("Malgun Gothic", Font.PLAIN, 13));
        if (tooltip != null && !tooltip.isEmpty()) item.setToolTipText(tooltip);
        return item;
	}
    /** 외부에서 받은 JMenu 에 스타일 + 텍스트 덮어쓰기 */
    private void styleMenu(JMenu m, String newText) {
        m.setText(newText);
        m.setForeground(new Color( 20,  50,  90));
        m.setFont(new Font("Malgun Gothic", Font.PLAIN, 13));
        m.getPopupMenu().setBackground(new Color(210, 235, 250));
	}
	
    private String resolveAppDir() {
		String EXE_PATH = ""; // ← 추가
		
        File jarDir = null;
        // ① sun.java.command
		
		if (jarDir == null) {
			try {
				String sc = System.getProperty("sun.java.command", "").trim();
				String[] parts = sc.split("\\s+");
				String first = parts[0];
				
				// ★ .exe가 있으면 무조건 .exe 우선
				if (first.endsWith(".exe")) {
					File f = new File(first).getAbsoluteFile();
					EXE_PATH = f.getAbsolutePath();
					jarDir = f.getParentFile();
					System.out.println("[AppDir] EXE 감지: " + EXE_PATH);
					} else if (first.endsWith(".jar")) {
					// .jar일 경우 .exe가 있는지 같은 폴더에서 찾아본다
					File jarFile = new File(first).getAbsoluteFile();
					File parent = jarFile.getParentFile();
					File exeCandidate = new File(parent, "KootPanKing.exe");
					if (exeCandidate.exists()) {
						EXE_PATH = exeCandidate.getAbsolutePath();
						jarDir = parent;
						System.out.println("[AppDir] JAR 옆 EXE 감지: " + EXE_PATH);
						} else {
						EXE_PATH = jarFile.getAbsolutePath();
						jarDir = parent;
						System.out.println("[AppDir] JAR 감지 (EXE 없음): " + EXE_PATH);
					}
				}
			} catch (Exception ignored) {}
		}
		
		
		// ② CodeSource 폴백 (sun.java.command가 없을 때)
		if (jarDir == null) {
			try {
				File loc = new File(KootPanKing.class.getProtectionDomain()
				.getCodeSource().getLocation().toURI());
				if (!loc.isDirectory()) {
					// .class 또는 .jar 파일
					File parent = loc.getParentFile();
					File exeCandidate = new File(parent, "KootPanKing.exe");
					if (exeCandidate.exists()) {
						EXE_PATH = exeCandidate.getAbsolutePath();
						jarDir = parent;
						} else {
						EXE_PATH = loc.getAbsolutePath();
						jarDir = parent;
					}
					} else {
					// IDE/class 직접 실행: loc이 디렉터리 → EXE_PATH 별도 탐색
					jarDir = loc;
					File exeCandidate = new File(loc, "KootPanKing.exe");
					if (exeCandidate.exists()) {
						EXE_PATH = exeCandidate.getAbsolutePath();
					}
					// EXE_PATH 여전히 빈 문자열이면 ProcessHandle 로 보완 (③에서 처리)
				}
				System.out.println("[AppDir] CodeSource 감지: " + EXE_PATH);
			} catch (Exception ignored) {}
		}
		
		// ③ ProcessHandle 보완 - EXE_PATH가 아직 비어있을 때만 시도
		if (EXE_PATH.isEmpty()) {
			try {
				java.util.Optional<String> cmd = ProcessHandle.current().info().command();
				if (cmd.isPresent()) {
					File f = new File(cmd.get());
					String name = f.getName().toLowerCase();
					if (f.exists()
						&& !name.equals("java.exe") && !name.equals("javaw.exe")
						&& !name.equals("java")     && !name.equals("javaw")) {
						EXE_PATH = f.getAbsolutePath();
						if (jarDir == null) jarDir = f.getParentFile();
						System.out.println("[AppDir] ProcessHandle 감지: " + EXE_PATH);
					}
				}
			} catch (Exception ignored) {}
		}
		/*
			if (jarDir == null) {
            try {
			File loc = new File(KootPanKing.class.getProtectionDomain()
			.getCodeSource().getLocation().toURI());
			jarDir = loc.isDirectory() ? loc : loc.getParentFile();
			} catch (Exception ignored) {}
			}
		*/
        // 기존 설정 파일이 JAR 옆에 있으면 → 무조건 JAR 폴더 사용 (설정 유지)
        if (jarDir != null && (
			new File(jarDir, "settings" + File.separator + "clock_settings.ini").exists() ||
		new File(jarDir, "clock_settings.ini").exists())) {
		System.out.println("[AppDir] 기존 설정 발견 → JAR 폴더: " + jarDir.getAbsolutePath());
		return jarDir.getAbsolutePath() + File.separator;
		}
        // JAR 폴더에 쓰기 가능하면 사용
        if (jarDir != null && jarDir.canWrite()) {
            System.out.println("[AppDir] JAR 폴더 사용: " + jarDir.getAbsolutePath());
            return jarDir.getAbsolutePath() + File.separator;
		}
        // 쓰기 불가(C:\Program Files 등) → %APPDATA%\KootPanKing\
        String appData = System.getenv("APPDATA");
        if (appData == null) appData = System.getProperty("user.home");
        File dir = new File(appData + File.separator + "KootPanKing");
        if (!dir.exists()) dir.mkdirs();
        System.out.println("[AppDir] APPDATA 폴더 사용: " + dir.getAbsolutePath());
        return dir.getAbsolutePath() + File.separator;
	}
	
	private void GitHubZipDownload ( String savePath  ) {
	
        System.out.println("다운로드 시작 : " + savePath);
	
        String fileURL = "https://github.com/GarpsuKim/KootPanKing/releases/download/v1.0.0/KootPanKing.zip";
        // String savePath = "KootPanKing.zip";
		
        try {
            URL url = new URL(fileURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true); // GitHub 리다이렉트 대응
			
            int responseCode = conn.getResponseCode();
            System.out.println("응답 코드: " + responseCode);
			
            try (InputStream in = conn.getInputStream();
				FileOutputStream out = new FileOutputStream(savePath)) {
				
                byte[] buffer = new byte[8192];
                int bytesRead;
				
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
				}
			}
			
            System.out.println("다운로드 완료: " + savePath);
			
			} catch (Exception e) {
            e.printStackTrace();
		}
	}	
}   //  public class SplashWindow 
// ═══════════════════════════════════════════════════════════
//  KootPanKing.main() 수정 예시 (참고용 주석)
// ═══════════════════════════════════════════════════════════

/*	*  public static void main(String[] args) {
	*      AppLogger.init();
	*
	*      // ① SplashWindow 를 가장 먼저 생성 (EDT에서)
	*      SplashWindow[] splashRef = {null};
	*      try {
	*          SwingUtilities.invokeAndWait(() -> {
	*              splashRef[0] = new SplashWindow();
	*              splashRef[0].log("끝판왕 시작 중...");
	*          });
	*      } catch (Exception ignored) {}
	*      SplashWindow splash = splashRef[0];
	*
	*      try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
	*      catch (Exception ignored) {}
	*
	*      // ② KootPanKing 생성 (EDT에서)
	*      SwingUtilities.invokeLater(() -> {
	*          if (splash != null) splash.log("설정 파일 로드 중...");
	*          KootPanKing clock = new KootPanKing(splash);
	*
	*          // ③ 콜백 주입
	*          if (splash != null) {
	*              splash.setClockHost(new SplashWindow.ClockHostCallback() {
	*                  @Override public JMenu buildGlobalMenu() {
	*                      return new MenuBuilder(new MenuBuilder.ClockHostContext(clock))
	*                                 .buildGlobalMenuPublic();
	*                  }
	*                  @Override public void exitAll()         { clock.exitAll(); }
	*                  @Override public void showConfigFile()  { clock.showConfigFile(); }
	*                  @Override public void showAbout()       { clock.showAbout(); }
	*                  @Override public String getConfigFilePath() {
	*                      return KootPanKing.CONFIG_FILE;
	*                  }
	*                  // [신규] setMultipleConfigAndSave 구현 예시
	*                  @Override public void setMultipleConfigAndSave(String... entries) {
	*                      for (int i = 0; i + 1 < entries.length; i += 2)
	*                          clock.setConfig(entries[i], entries[i+1]);
	*                      clock.saveConfig(); // 파일 I/O 1회
	*                  }
	*                  @Override public void setConfigAndSave(String key, String value) {
	*                      clock.setConfig(key, value);
	*                      clock.saveConfig();
	*                  }
	*              });
	*              splash.log("시계 초기화 완료.");
	*          }
	*      });
	*  }
*/
