import javax.swing.*;
import java.awt.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CalendarAlarmPoller - Google + 네이버 캘린더 이벤트 폴링 및 알림 트리거 클래스
 *
 * 기능:
 *   ① 1분 주기로 Google Calendar + 네이버 Calendar 이벤트 조회
 *   ② 이벤트 시작 시각 도달 시 알림 발동:
 *      - 메시지 박스 팝업 (무지개 배경)
 *      - 텔레그램 메시지 전송
 *   ③ 매일 아침 자동 브리핑 (지정 시각)
 *
 * 변경 내역:
 *   - NaverCalendarService 추가 (생성자 파라미터)
 *   - checkAlarms() 에서 구글 + 네이버 동시 폴링
 *   - 브리핑에 네이버 일정 병합 출력
 *   - 이벤트 ID 충돌 방지: "G_" / "N_" 접두어 구분
 *
 * 사용법:
 *   CalendarAlarmPoller poller = new CalendarAlarmPoller(
 *       googleCalendarService, naverCalendarService, telegramBot, hostCallback);
 *   poller.start();
 *   poller.stop();
 */
public class CalendarAlarmPoller {

    // ── 알림 설정 ─────────────────────────────────────────────────
    private static final int ALARM_WINDOW_MINUTES        = 1;
    private static final int COOLDOWN_MINUTES            = 5;
    private static final int MORNING_BRIEF_TIME_DEFAULT  = 700;

    // ── 호스트 콜백 인터페이스 ────────────────────────────────────
    public interface HostCallback {
        String getTelegramChatId();
        void prepareMessageBox();
        void setBgColorAndRepaint(java.awt.Color c);
        void restoreBgColor();
        int getRainbowSeconds();
        int getMorningBriefTime();
    }

    // ── 의존성 ────────────────────────────────────────────────────
    private final GoogleCalendarService  googleCalendar;
    private final NaverCalendarService   naverCalendar;   // ★ 추가
    private final TelegramBot            telegramBot;
    private final HostCallback           host;

    // ── 내부 상태 ─────────────────────────────────────────────────
    private Timer   pollTimer  = null;
    private boolean running    = false;
    private final Map<String, Long> firedAlarms = new HashMap<>();
    private int lastBriefDay = -1;

    // ── 생성자 (구글 + 네이버) ────────────────────────────────────

    /**
     * 구글 + 네이버 동시 폴링 생성자.
     * naverCalendar 가 null 이면 구글만 폴링.
     */
    public CalendarAlarmPoller(GoogleCalendarService googleCalendar,
                               NaverCalendarService  naverCalendar,
                               TelegramBot           telegramBot,
                               HostCallback          host) {
        this.googleCalendar = googleCalendar;
        this.naverCalendar  = naverCalendar;
        this.telegramBot    = telegramBot;
        this.host           = host;
    }

    /**
     * 기존 호환 생성자 (구글만 / 네이버 없음).
     * 기존 코드 수정 없이 그대로 사용 가능.
     */
    public CalendarAlarmPoller(GoogleCalendarService googleCalendar,
                               TelegramBot           telegramBot,
                               HostCallback          host) {
        this(googleCalendar, null, telegramBot, host);
    }

    // ── 시작 / 중지 ───────────────────────────────────────────────

    public void start() {
        if (running) return;

        boolean googleOk = googleCalendar != null && googleCalendar.isInitialized();
        boolean naverOk  = naverCalendar  != null && naverCalendar.isInitialized();

        if (!googleOk && !naverOk) {
            System.out.println("[CalPoller] 초기화된 캘린더 서비스 없음 - 시작 불가");
            return;
        }

        running = true;
        pollTimer = new Timer(60_000, e -> {
            new Thread(() -> {
                checkAlarms();
                checkMorningBrief();
            }, "CalendarPoller").start();
        });
        pollTimer.setInitialDelay(3000);
        pollTimer.start();

        String sources = (googleOk ? "구글" : "") + (googleOk && naverOk ? " + " : "") + (naverOk ? "네이버" : "");
        System.out.println("[CalPoller] 폴링 시작 (1분 간격) - " + sources);
    }

    public void stop() {
        if (pollTimer != null) { pollTimer.stop(); pollTimer = null; }
        running = false;
        System.out.println("[CalPoller] 폴링 중지");
    }

    public boolean isRunning() { return running; }

    // ── 알람 체크 ─────────────────────────────────────────────────

    private void checkAlarms() {
        // ── 구글 캘린더 알람 체크 ──────────────────────────────
        if (googleCalendar != null && googleCalendar.isInitialized()) {
            try {
                List<GoogleCalendarService.CalendarEvent> events =
                    googleCalendar.getUpcomingAlarms(ALARM_WINDOW_MINUTES);
                ZonedDateTime now = ZonedDateTime.now();
                for (GoogleCalendarService.CalendarEvent event : events) {
                    if (event.allDay) continue;
                    String key = "G_" + event.id;  // ★ "G_" 접두어로 구글 구분
                    if (isCooldown(key)) continue;
                    long diff = java.time.Duration.between(now, event.startTime).toMinutes();
                    if (diff >= 0 && diff <= ALARM_WINDOW_MINUTES) {
                        firedAlarms.put(key, System.currentTimeMillis());
                        fireAlarm("[구글]", event.title, event.startTime);
                    }
                }
            } catch (Exception e) {
                System.out.println("[CalPoller] 구글 알람 체크 오류: " + e.getMessage());
            }
        }

        // ── 네이버 캘린더 알람 체크 ────────────────────────────
        if (naverCalendar != null && naverCalendar.isInitialized()) {
            try {
                List<NaverCalendarService.CalendarEvent> events =
                    naverCalendar.getUpcomingAlarms(ALARM_WINDOW_MINUTES);
                ZonedDateTime now = ZonedDateTime.now();
                for (NaverCalendarService.CalendarEvent event : events) {
                    if (event.allDay) continue;
                    String key = "N_" + event.id;  // ★ "N_" 접두어로 네이버 구분
                    if (isCooldown(key)) continue;
                    long diff = java.time.Duration.between(now, event.startTime).toMinutes();
                    if (diff >= 0 && diff <= ALARM_WINDOW_MINUTES) {
                        firedAlarms.put(key, System.currentTimeMillis());
                        fireAlarm("[네이버]", event.title, event.startTime);
                    }
                }
            } catch (Exception e) {
                System.out.println("[CalPoller] 네이버 알람 체크 오류: " + e.getMessage());
            }
        }

        cleanupFiredAlarms();
    }

    /** 쿨다운 체크 */
    private boolean isCooldown(String key) {
        Long lastFired = firedAlarms.get(key);
        if (lastFired == null) return false;
        return (System.currentTimeMillis() - lastFired) / 60000 < COOLDOWN_MINUTES;
    }

    /** 알람 발동: 팝업 + 무지개 + 텔레그램 */
    private void fireAlarm(String source, String title, ZonedDateTime startTime) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        String alarmTitle   = "📅 캘린더 알람 " + source;
        String alarmContent = "⏰ " + startTime.format(fmt) + "\n" + title;

        System.out.println("[CalPoller] 알람 발동: " + source + " " + title);

        // ① PC 팝업 + 무지개 배경
        SwingUtilities.invokeLater(() -> {
            if (host != null) host.prepareMessageBox();

            javax.swing.JDialog dlg = new javax.swing.JDialog(
                (java.awt.Frame) null, alarmTitle, false);
            dlg.setLayout(new java.awt.BorderLayout(8, 8));
            dlg.getRootPane().setBorder(
                javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));

            javax.swing.JTextArea ta = new javax.swing.JTextArea(alarmContent, 6, 30);
            ta.setEditable(false);
            ta.setFont(new java.awt.Font("Malgun Gothic", java.awt.Font.PLAIN, 14));
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            dlg.add(new javax.swing.JScrollPane(ta), java.awt.BorderLayout.CENTER);

            // 무지개 배경 타이머
            final java.awt.Color[] RAINBOW = {
                new java.awt.Color(255,  0,  0),
                new java.awt.Color(255,127,  0),
                new java.awt.Color(255,255,  0),
                new java.awt.Color(  0,200,  0),
                new java.awt.Color(  0,  0,255),
                new java.awt.Color( 75,  0,130),
                new java.awt.Color(148,  0,211),
            };
            final int totalSecs = (host != null) ? Math.max(1, host.getRainbowSeconds()) : 30;
            final int[] colorIdx = {0};
            final javax.swing.Timer[] rainbowTimer = {null};
            if (host != null) {
                rainbowTimer[0] = new javax.swing.Timer(1000, ev -> {
                    if (colorIdx[0] < totalSecs) {
                        host.setBgColorAndRepaint(RAINBOW[colorIdx[0] % RAINBOW.length]);
                        colorIdx[0]++;
                    } else {
                        rainbowTimer[0].stop();
                        host.restoreBgColor();
                    }
                });
                rainbowTimer[0].start();
            }

            javax.swing.JButton okBtn = new javax.swing.JButton("  확인  ");
            okBtn.addActionListener(ev -> dlg.dispose());
            dlg.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent e) {
                    if (rainbowTimer[0] != null) rainbowTimer[0].stop();
                    if (host != null) host.restoreBgColor();
                }
            });
            javax.swing.JPanel btnPanel = new javax.swing.JPanel(
                new java.awt.FlowLayout(java.awt.FlowLayout.CENTER));
            btnPanel.add(okBtn);
            dlg.add(btnPanel, java.awt.BorderLayout.SOUTH);

            dlg.setAlwaysOnTop(true);
            dlg.pack();
            java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            dlg.setLocation(
                (screen.width  - dlg.getWidth())  / 2,
                (screen.height - dlg.getHeight()) / 2);
            dlg.setVisible(true);
        });

        // ② 텔레그램 전송
        if (telegramBot != null && host != null) {
            String chatId = host.getTelegramChatId();
            if (!chatId.isEmpty()) {
                String msg = alarmTitle + "\n─────────────────\n" + alarmContent;
                new Thread(() -> telegramBot.send(chatId, msg), "CalAlarmTelegram").start();
            }
        }
    }

    // ── 아침 브리핑 ───────────────────────────────────────────────

    private void checkMorningBrief() {
        ZonedDateTime now   = ZonedDateTime.now();
        int hour   = now.getHour();
        int minute = now.getMinute();
        int day    = now.getDayOfYear();

        int hhmm       = (host != null) ? host.getMorningBriefTime() : MORNING_BRIEF_TIME_DEFAULT;
        int briefHour  = hhmm / 100;
        int briefMin   = hhmm % 100;
        int nowTotal   = hour * 60 + minute;
        int briefTotal = briefHour * 60 + briefMin;

        if (nowTotal >= briefTotal && nowTotal < briefTotal + 2 && day != lastBriefDay) {
            lastBriefDay = day;
            new Thread(() -> sendMorningBrief(false), "MorningBrief").start();
        }
    }

    public void sendStartupBrief() {
        new Thread(() -> sendMorningBrief(true), "StartupBrief").start();
    }

    private void sendMorningBrief(boolean isStartup) {
        try {
            // ── 구글 일정 ──────────────────────────────────────
            List<GoogleCalendarService.CalendarEvent> googleEvents = new ArrayList<>();
            if (googleCalendar != null && googleCalendar.isInitialized()) {
                googleEvents = googleCalendar.getNextDays(3);
            }

            // ── 네이버 일정 ────────────────────────────────────
            List<NaverCalendarService.CalendarEvent> naverEvents = new ArrayList<>();
            if (naverCalendar != null && naverCalendar.isInitialized()) {
                naverEvents = naverCalendar.getNextDays(3);
            }

            // ── 메시지 조합 ────────────────────────────────────
            String header = isStartup ? "🖥️ 앱이 시작되었습니다!" : "🌅 좋은 아침입니다!";
            StringBuilder fullMsg = new StringBuilder(header).append("\n\n");

            if (googleEvents.isEmpty() && naverEvents.isEmpty()) {
                fullMsg.append("📭 향후 3일간 일정이 없습니다.");
            } else {
                if (!googleEvents.isEmpty()) {
                    fullMsg.append(GoogleCalendarService.formatEvents("구글 3일 일정", googleEvents));
                }
                if (!naverEvents.isEmpty()) {
                    if (!googleEvents.isEmpty()) fullMsg.append("\n\n");
                    fullMsg.append(NaverCalendarService.formatEvents("네이버 3일 일정", naverEvents));
                }
            }

            String msg = fullMsg.toString();

            // ① PC 팝업
            SwingUtilities.invokeLater(() -> showBriefDialog(msg));

            // ② 텔레그램
            boolean hasEvents = !googleEvents.isEmpty() || !naverEvents.isEmpty();
            if (hasEvents && telegramBot != null && host != null) {
                String chatId = host.getTelegramChatId();
                if (!chatId.isEmpty()) telegramBot.send(chatId, msg);
            }

            System.out.println("[CalPoller] 브리핑 완료 (구글:" + googleEvents.size()
                             + "건 / 네이버:" + naverEvents.size() + "건)");

        } catch (Exception e) {
            System.out.println("[CalPoller] 브리핑 오류: " + e.getMessage());
        }
    }

    private void showBriefDialog(String msg) {
        if (host != null) host.prepareMessageBox();

        javax.swing.JDialog dlg = new javax.swing.JDialog(
            (java.awt.Frame) null, "📅 일정 브리핑", false);
        dlg.setLayout(new java.awt.BorderLayout(8, 8));
        dlg.getRootPane().setBorder(
            javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));

        javax.swing.JTextArea ta = new javax.swing.JTextArea(msg, 20, 40);
        ta.setEditable(false);
        ta.setFont(new java.awt.Font("Malgun Gothic", java.awt.Font.PLAIN, 13));
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        dlg.add(new javax.swing.JScrollPane(ta), java.awt.BorderLayout.CENTER);

        javax.swing.JButton okBtn = new javax.swing.JButton("  확인  ");
        okBtn.addActionListener(ev -> dlg.dispose());
        javax.swing.JPanel btnPanel = new javax.swing.JPanel(
            new java.awt.FlowLayout(java.awt.FlowLayout.CENTER));
        btnPanel.add(okBtn);
        dlg.add(btnPanel, java.awt.BorderLayout.SOUTH);

        dlg.setAlwaysOnTop(true);
        dlg.pack();
        java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        dlg.setLocation(
            (screen.width  - dlg.getWidth())  / 2,
            (screen.height - dlg.getHeight()) / 2);
        dlg.setVisible(true);
    }

    // ── 유틸 ──────────────────────────────────────────────────────

    private void cleanupFiredAlarms() {
        long now = System.currentTimeMillis();
        firedAlarms.entrySet().removeIf(e -> (now - e.getValue()) > 3_600_000);
    }
}
