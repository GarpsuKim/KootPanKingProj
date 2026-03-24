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
    private static final String TITLE      = "끝판왕 (KootPanKing)";
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
