import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * ChimeController - 차임벨 재생 로직 + 설정 다이얼로그
 *
 * ── 책임 ────────────────────────────────────────────────────
 *   ① 매 초 시각을 체크하여 지정된 분에 차임벨 자동 재생
 *   ② wmplayer 를 통한 오디오/비디오 파일 재생 및 중지
 *   ③ OS 기본 앱을 통한 미디어 파일 재생 (텔레그램 수신 파일)
 *   ④ showChimeDialog() 로 설정 UI 표시
 *
 * ── wmplayer 의존성 ──────────────────────────────────────────
 *   Windows Media Player 경로를 우선/차순으로 탐색:
 *     C:\Program Files\Windows Media Player\wmplayer.exe
 *     C:\Program Files (x86)\Windows Media Player\wmplayer.exe
 *   존재하지 않으면 오류 메시지를 표시한다.
 *
 * ── HostCallback 인터페이스 ──────────────────────────────────
 *   ChimeController 는 KootPanKing 의 상태를 직접 참조하지 않고
 *   HostCallback 을 통해 필요한 값만 가져온다.
 *   AlarmController.HostCallback 과 동일한 패턴.
 *
 * ── 사용법 ───────────────────────────────────────────────────
 *   ChimeController chime = new ChimeController(ownerFrame, hostCallback);
 *   chime.startCheckTimer();             // initUI 완료 후 호출
 *   chime.showChimeDialog();             // 팝업 메뉴 → 차임벨 설정
 *   chime.stopChime();                   // 강제 중지
 *   chime.playMediaFile(file);           // 텔레그램 수신 미디어 재생
 *
 *   // INI 저장/로드 시
 *   chime.isEnabled()  chime.getFile()  chime.isFull()  chime.getMinutes()
 *   chime.setEnabled() chime.setFile()  chime.setFull() chime.setMinutes()
 */
public class ChimeController {

    // ── 호스트 콜백 인터페이스 ───────────────────────────────
    public interface HostCallback {
        /** 자식 인스턴스 여부 (자식은 차임벨 비활성) */
        boolean isChild();
        /** 현재 타임존 (차임벨 시각 체크에 사용) */
        ZoneId getTimeZone();
        /** JOptionPane 표시 전 시계 위치 조정 */
        void prepareMessageBox();
        /** JDialog 위치 조정 및 pack */
        void prepareDialog(java.awt.Window dlg);
        /** 무지개 배경 - 지정 색으로 교체 + 리페인트 (AlarmController 와 동일 패턴) */
        void setBgColorAndRepaint(java.awt.Color c);
        /** 무지개 배경 - 원래 배경 복원 */
        void restoreBgColor();
        /** 무지개 지속 시간 (초) — INI rainbowSeconds, 기본 30 */
        int getRainbowSeconds();
    }

    // ── 설정 필드 ────────────────────────────────────────────
    private boolean   enabled  = false;
    private String    file     = "";           // 오디오/비디오 파일 경로
    /** 연주 시간: 0=처음15초, 1=처음30초, 2=끝까지 */
    private int       duration = 0;
    private boolean[] minutes  = new boolean[60]; // 0~59분 체크
    /** 볼륨: 0(무음) ~ 100(최대), 기본 80 */
    private int       volume   = 80;

    // ── 내부 상태 ────────────────────────────────────────────
    private Process   chimeProcess = null;     // 현재 실행중인 wmplayer
    private Thread    wavThread    = null;      // 현재 재생 중인 WAV 스레드
    private volatile boolean wavRunning = false; // WAV 중지 신호
    private Timer     checkTimer   = null;
    private int       lastChimeMinute = -1;    // 중복 실행 방지

    // ── 의존성 ───────────────────────────────────────────────
    private final JFrame       ownerFrame;
    private final HostCallback host;

    // ── 생성자 ───────────────────────────────────────────────
    public ChimeController(JFrame ownerFrame, HostCallback host) {
        this.ownerFrame = ownerFrame;
        this.host       = host;
        minutes[0]      = true;  // 기본값: 정각(0분)에 연주
    }

    // ── 설정 접근자 (INI 저장/로드용) ───────────────────────

    public boolean   isEnabled()  { return enabled; }
    public String    getFile()    { return file; }
    /** 연주 시간: 0=처음15초, 1=처음30초, 2=끝까지 */
    public int       getDuration() { return duration; }
    /** @deprecated isFull() 대신 getDuration()==2 사용 권장 */
    @Deprecated
    public boolean   isFull()     { return duration == 2; }
    public boolean[] getMinutes() { return minutes; }
    /** 볼륨: 0~100 */
    public int       getVolume()  { return volume; }

    public void setEnabled(boolean v)  { this.enabled  = v; }
    public void setFile(String v)      { this.file     = v != null ? v : ""; }
    /** 연주 시간 설정: 0=처음15초, 1=처음30초, 2=끝까지 */
    public void setDuration(int v)     { this.duration = (v >= 0 && v <= 2) ? v : 0; }
    /** @deprecated setDuration() 사용 권장 */
    @Deprecated
    public void setFull(boolean v)     { this.duration = v ? 2 : 0; }
    public void setMinutes(boolean[] v) {
        if (v != null && v.length == 60) System.arraycopy(v, 0, minutes, 0, 60);
    }
    /** 볼륨 설정: 0~100 */
    public void setVolume(int v)       { this.volume = Math.max(0, Math.min(100, v)); }

    // ── 공개 API ─────────────────────────────────────────────

    /** 매 초 시각 체크 타이머 시작 (initUI 완료 후 1회 호출) */
    public void startCheckTimer() {
        if (checkTimer != null) checkTimer.stop();
        checkTimer = new Timer(1000, e -> checkAndPlay());
        checkTimer.start();
    }

    /** 차임벨 강제 중지 */
    public void stopChime() {
        // WAV 스레드 중지
        wavRunning = false;
        if (wavThread != null) {
            wavThread.interrupt();
            wavThread = null;
        }
        // wmplayer 프로세스 중지
        if (chimeProcess != null) {
            chimeProcess.destroy();
            chimeProcess = null;
        }
    }

    /**
     * 텔레그램 수신 미디어 파일을 OS 기본 앱으로 재생.
     * @param mediaFile 재생할 파일
     */
    public void playMediaFile(File mediaFile) {
        try {
            Desktop.getDesktop().open(mediaFile);
            System.out.println("[MediaPlay] 기본 앱으로 재생: " + mediaFile.getName());
        } catch (Exception ex) {
            System.out.println("[MediaPlay] 재생 오류: " + ex.getMessage());
        }
    }

    /**
     * 차임벨 설정 다이얼로그 표시.
     * 파일 선택, 연주 시간(15초/끝까지), 분 단위 체크박스 60개.
     */
    public void showChimeDialog() {
        JDialog dlg = new JDialog(ownerFrame, "차임벨 설정", true);
        dlg.setLayout(new BorderLayout(8, 8));
        dlg.getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── 상단 패널: on/off, 파일, duration ─────────────────
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createTitledBorder("기본 설정"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 6, 4, 6);
        gc.anchor = GridBagConstraints.WEST;

        // on/off
        gc.gridx=0; gc.gridy=0; gc.gridwidth=1;
        topPanel.add(new JLabel("차임벨:"), gc);
        JCheckBox onOffBox = new JCheckBox("사용", enabled);
        gc.gridx=1; gc.gridwidth=3;
        topPanel.add(onOffBox, gc);

        // 파일 선택
        gc.gridx=0; gc.gridy=1; gc.gridwidth=1;
        topPanel.add(new JLabel("파일:"), gc);
        JTextField fileField = new JTextField(file, 28);
        fileField.setEditable(false);
        gc.gridx=1; gc.gridwidth=2; gc.fill=GridBagConstraints.HORIZONTAL;
        topPanel.add(fileField, gc);
        gc.fill=GridBagConstraints.NONE;
        JButton browseBtn = new JButton("찾기...");
        gc.gridx=3; gc.gridwidth=1;
        topPanel.add(browseBtn, gc);

        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("오디오/비디오 파일 선택");
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "미디어 파일 (mp3, wav, wma, mp4, avi, wmv, m4a, flac, ogg)",
                "mp3","wav","wma","mp4","avi","wmv","m4a","flac","ogg","aac","mkv"));
            fc.setAcceptAllFileFilterUsed(true);
            if (!file.isEmpty()) fc.setSelectedFile(new File(file));
            if (fc.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                fileField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });

        // 테스트 / 중지 버튼
        JButton testBtn = new JButton("▶ 테스트");
        gc.gridx=0; gc.gridy=2; gc.gridwidth=2;
        topPanel.add(testBtn, gc);
        JButton stopBtn = new JButton("■ 중지");
        gc.gridx=2; gc.gridwidth=2;
        topPanel.add(stopBtn, gc);

        stopBtn.addActionListener(e -> stopChime());

        // Duration
        gc.gridx=0; gc.gridy=3; gc.gridwidth=1;
        topPanel.add(new JLabel("연주 시간:"), gc);
        JRadioButton r15   = new JRadioButton("처음 15초만", duration == 0);
        JRadioButton r30   = new JRadioButton("처음 30초만", duration == 1);
        JRadioButton rFull = new JRadioButton("끝까지",      duration == 2);
        ButtonGroup bg = new ButtonGroup();
        bg.add(r15); bg.add(r30); bg.add(rFull);
        gc.gridx=1; gc.gridwidth=1; topPanel.add(r15, gc);
        gc.gridx=2;                 topPanel.add(r30, gc);
        gc.gridx=3;                 topPanel.add(rFull, gc);

        // 볼륨 슬라이더
        gc.gridx=0; gc.gridy=4; gc.gridwidth=1;
        topPanel.add(new JLabel("볼륨:"), gc);
        JSlider volumeSlider = new JSlider(0, 100, volume);
        volumeSlider.setMajorTickSpacing(25);
        volumeSlider.setMinorTickSpacing(5);
        volumeSlider.setPaintTicks(true);
        volumeSlider.setPaintLabels(true);
        volumeSlider.setPreferredSize(new Dimension(220, 45));
        gc.gridx=1; gc.gridwidth=2; gc.fill=GridBagConstraints.HORIZONTAL;
        topPanel.add(volumeSlider, gc);
        gc.fill=GridBagConstraints.NONE;
        JLabel volumeLabel = new JLabel(volume + "%");
        volumeLabel.setPreferredSize(new Dimension(40, 20));
        gc.gridx=3; gc.gridwidth=1;
        topPanel.add(volumeLabel, gc);
        volumeSlider.addChangeListener(e ->
            volumeLabel.setText(volumeSlider.getValue() + "%"));

        // 테스트 버튼 리스너: volumeSlider/rFull/r30 선언 후 등록
        testBtn.addActionListener(e -> {
            String f = fileField.getText().trim();
            if (f.isEmpty()) { JOptionPane.showMessageDialog(dlg, "파일을 먼저 선택하세요."); return; }
            // 현재 UI 값을 인스턴스 변수에 반영 후 재생
            // playChimeInternal()이 내부에서 스냅샷을 찍으므로 복원 불필요
            file     = f;
            volume   = volumeSlider.getValue();
            duration = rFull.isSelected() ? 2 : r30.isSelected() ? 1 : 0;
            playChimeInternal();
        });

        dlg.add(topPanel, BorderLayout.NORTH);

        // ── 중앙: 분 체크박스 60개 ────────────────────────────
        JPanel minPanel = new JPanel(new GridLayout(10, 6, 3, 3));
        minPanel.setBorder(BorderFactory.createTitledBorder("연주 시각 (매 시각 N분에 연주)"));
        JCheckBox[] minBoxes = new JCheckBox[60];
        for (int i = 0; i < 60; i++) {
            minBoxes[i] = new JCheckBox(String.format("%02d분", i), minutes[i]);
            minBoxes[i].setFont(new Font("Malgun Gothic", Font.PLAIN, 11));
            minPanel.add(minBoxes[i]);
        }

        JScrollPane minScroll = new JScrollPane(minPanel);
        minScroll.setPreferredSize(new Dimension(420, 260));
        dlg.add(minScroll, BorderLayout.CENTER);

        // ── 하단: 전체선택 / 해제 / 정각 / 확인 / 취소 ────────
        JPanel botPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton allBtn    = new JButton("전체 선택");
        JButton noneBtn   = new JButton("선택 해제");
        JButton topBtn    = new JButton("정각(0분)");
        JButton okBtn     = new JButton("  확인  ");
        JButton cancelBtn = new JButton("  취소  ");

        allBtn.addActionListener(e  -> { for (JCheckBox cb : minBoxes) cb.setSelected(true); });
        noneBtn.addActionListener(e -> { for (JCheckBox cb : minBoxes) cb.setSelected(false); });
        topBtn.addActionListener(e  -> {
            for (JCheckBox cb : minBoxes) cb.setSelected(false);
            minBoxes[0].setSelected(true);
        });
        okBtn.addActionListener(e -> {
            enabled  = onOffBox.isSelected();
            file     = fileField.getText().trim();
            duration = rFull.isSelected() ? 2 : r30.isSelected() ? 1 : 0;
            volume   = volumeSlider.getValue();
            for (int i = 0; i < 60; i++) minutes[i] = minBoxes[i].isSelected();
            dlg.dispose();
        });
        cancelBtn.addActionListener(e -> dlg.dispose());

        botPanel.add(allBtn); botPanel.add(noneBtn); botPanel.add(topBtn);
        botPanel.add(Box.createHorizontalStrut(20));
        botPanel.add(okBtn); botPanel.add(cancelBtn);
        dlg.add(botPanel, BorderLayout.SOUTH);

        host.prepareDialog(dlg);
        dlg.setVisible(true);
    }

    // ── 내부: 시각 체크 ──────────────────────────────────────

    private void checkAndPlay() {
        if (!enabled || file.isEmpty()) return;
        ZonedDateTime now = ZonedDateTime.now(host.getTimeZone());
        int min = now.getMinute();
        int sec = now.getSecond();
        if (sec == 0 && minutes[min] && min != lastChimeMinute) {
            lastChimeMinute = min;
            System.out.println("[Chime] ★ 차임벨 발동 → " + now.getHour() + "시 " + min + "분");
            playChimeInternal();
        }
        if (sec > 2) lastChimeMinute = -1;
    }

    // ── 내부: wmplayer 재생 ──────────────────────────────────

    private void playChimeInternal() {
        stopChime(); // 이전 연주 종료

        // ── 스레드에 넘기기 전에 현재 값을 스냅샷 (테스트 버튼 복원과 타이밍 충돌 방지) ──
        final String  snapFile     = file;
        final int     snapVolume   = volume;
        final int     snapDuration = duration;

        String lowerFile = snapFile.toLowerCase();
        boolean isWav = lowerFile.endsWith(".wav");
        System.out.println("[Chime] 재생 → " + snapFile + " (wav=" + isWav + ", vol=" + snapVolume + ", dur=" + snapDuration + ")");

        if (isWav) {
            playWavWithVolume(snapFile, snapVolume, snapDuration);
        } else {
            playWithWmplayer(snapFile, snapDuration);
        }

        // ── 무지개 배경 타이머 (7색 순환, getRainbowSeconds()초 후 자동 복귀) ──
        final java.awt.Color[] RAINBOW = {
            new java.awt.Color(255,  0,  0),   // 빨
            new java.awt.Color(255,127,  0),   // 주
            new java.awt.Color(255,255,  0),   // 노
            new java.awt.Color(  0,200,  0),   // 초
            new java.awt.Color(  0,  0,255),   // 파
            new java.awt.Color( 75,  0,130),   // 남
            new java.awt.Color(148,  0,211),   // 보
        };
        final int totalSecs = Math.max(1, host.getRainbowSeconds());
        final int[] colorIdx = {0};
        final Timer[] rainbowTimer = {null};
        rainbowTimer[0] = new Timer(1000, ev -> {
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

    /** WAV 파일을 Java Clip으로 직접 재생 (볼륨 제어 가능) */
    private void playWavWithVolume(final String playFile, final int playVolume, final int playDuration) {
        wavRunning = true;
        wavThread = new Thread(() -> {
            try {
                System.out.println("[Chime] WAV 로드: " + playFile);
                javax.sound.sampled.AudioInputStream ais =
                    javax.sound.sampled.AudioSystem.getAudioInputStream(new File(playFile));

                // ── 포맷 변환: ADPCM 등 비PCM 포맷을 PCM_SIGNED 로 자동 변환 ──
                javax.sound.sampled.AudioFormat baseFmt = ais.getFormat();
                System.out.println("[Chime] WAV 포맷: " + baseFmt.getEncoding()
                    + " " + (int)baseFmt.getSampleRate() + "Hz " + baseFmt.getChannels() + "ch");
                if (!baseFmt.getEncoding().equals(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED)
                 && !baseFmt.getEncoding().equals(javax.sound.sampled.AudioFormat.Encoding.PCM_UNSIGNED)) {
                    System.out.println("[Chime] 비PCM → PCM_SIGNED 변환");
                    javax.sound.sampled.AudioFormat pcmFmt = new javax.sound.sampled.AudioFormat(
                        javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                        baseFmt.getSampleRate(),
                        16,
                        baseFmt.getChannels(),
                        baseFmt.getChannels() * 2,
                        baseFmt.getSampleRate(),
                        false
                    );
                    ais = javax.sound.sampled.AudioSystem.getAudioInputStream(pcmFmt, ais);
                }

                javax.sound.sampled.Clip clip =
                    javax.sound.sampled.AudioSystem.getClip();
                clip.open(ais);
                System.out.println("[Chime] Clip 오픈 성공, 길이=" + clip.getMicrosecondLength()/1000 + "ms");

                // 볼륨 적용 (파라미터 playVolume/playDuration 사용 — 인스턴스 변수 아님)
                if (clip.isControlSupported(javax.sound.sampled.FloatControl.Type.MASTER_GAIN)) {
                    javax.sound.sampled.FloatControl gainControl =
                        (javax.sound.sampled.FloatControl) clip.getControl(
                            javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
                    float maxDb = gainControl.getMaximum();
                    float minDb = gainControl.getMinimum();
                    float dB;
                    if (playVolume == 0) {
                        dB = minDb;
                    } else if (playVolume >= 100) {
                        dB = maxDb;
                    } else {
                        dB = maxDb - (100 - playVolume) * 0.4f;
                        dB = Math.max(minDb, dB);
                    }
                    gainControl.setValue(dB);
                    System.out.println("[Chime] 볼륨=" + playVolume + "% → "
                        + String.format("%.1f", gainControl.getValue())
                        + "dB (max=" + String.format("%.1f", maxDb) + "dB)");
                }

                clip.start();
                System.out.println("[Chime] clip.start() 완료");

                // clip.start()는 비동기 → 실제 재생 시작까지 잠시 대기
                Thread.sleep(50);

                long stopMs = (playDuration == 0) ? 15000L
                            : (playDuration == 1) ? 30000L
                            : Long.MAX_VALUE;

                long startMs = System.currentTimeMillis();
                while (wavRunning && (clip.isActive() || clip.isRunning())) {
                    if (System.currentTimeMillis() - startMs >= stopMs) break;
                    Thread.sleep(100);
                }
                clip.stop();
                clip.close();
                ais.close();
                System.out.println("[Chime] 재생 완료");
            } catch (Exception ex) {
                System.out.println("[Chime] WAV 오류: " + ex.getMessage());
                javax.swing.SwingUtilities.invokeLater(() -> {
                    host.prepareMessageBox();
                    JOptionPane.showMessageDialog(null,
                        "WAV 재생 오류:\n" + ex.getMessage(),
                        "차임벨 오류", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                wavRunning = false;
                wavThread  = null;
            }
        }, "ChimeWav");
        wavThread.setDaemon(true);
        wavThread.start();
    }

    /** WAV 외 파일(mp3/wma/mp4 등)을 wmplayer로 재생 */
    private void playWithWmplayer(final String playFile, final int playDuration) {
        try {
            String wmplayer = "C:\\Program Files\\Windows Media Player\\wmplayer.exe";
            if (!new File(wmplayer).exists()) {
                wmplayer = "C:\\Program Files (x86)\\Windows Media Player\\wmplayer.exe";
            }
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(wmplayer);
            if (playDuration < 2) {
                cmd.add("/play");
                cmd.add("/close");
            }
            cmd.add(playFile);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            chimeProcess = pb.start();

            if (playDuration < 2) {
                int stopMs = (playDuration == 1) ? 30000 : 15000;
                new Timer(stopMs, ev -> {
                    ((Timer) ev.getSource()).stop();
                    stopChime();
                }).start();
            }
        } catch (Exception ex) {
            host.prepareMessageBox();
            JOptionPane.showMessageDialog(null,
                "wmplayer 실행 오류:\n" + ex.getMessage(),
                "차임벨 오류", JOptionPane.ERROR_MESSAGE);
        }
    }
}
