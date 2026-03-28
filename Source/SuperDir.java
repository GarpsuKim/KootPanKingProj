import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
	* SuperDir — 디렉터리 재귀 탐색 뷰어 (서브 윈도우)
	*
	* ── 추가 기능 ────────────────────────────────────────────────
	*   • 확장자 필터: 전체 / 텍스트 / 이미지 / 음악 / 동영상 / 문서 / 기타
	*   • 목록에서 텍스트 파일 선택 후 Enter 또는 더블클릭
	*     → SplashWindow.openTextFileWindow() 에 위임 (리플렉션)
	*     → 연동 불가 시 자체 간이 뷰어 실행
*/
public class SuperDir extends JFrame {
	
    // ── 싱글턴 ──────────────────────────────────────────────────
    private static SuperDir instance = null;
	
    public static void open(JFrame owner) {
        if (instance == null || !instance.isDisplayable()) {
            instance = new SuperDir(owner);
			} else {
            instance.setVisible(true);
            instance.toFront();
            instance.requestFocus();
		}
	}
	
    // ── 색상 상수 (SplashWindow 와 통일) ────────────────────────
    private static final Color BG_COLOR   = new Color(235, 245, 255);
    private static final Color FG_COLOR   = new Color( 20,  50,  90);
    private static final Color HEADER_BG  = new Color(173, 216, 230);
    private static final Color BORDER_CLR = new Color(120, 170, 200);
    private static final Color STATUS_BG  = new Color(200, 225, 245);
    private static final Color STATUS_FG  = new Color( 20,  60, 120);
    private static final Color DIR_COLOR  = new Color( 20,  80, 180);   // 폴더 행
    private static final Color MATCH_BG   = new Color(255, 255, 210);   // 필터 매치 행 배경
	
    // ── 스캔 상수 ────────────────────────────────────────────────
    private static final int BATCH_SIZE = 1000;
	
    // ── 확장자 필터 정의 ─────────────────────────────────────────
    private enum ExtFilter {
        TEXT  ("텍스트", new String[]{"txt","log","ini","java","py","js","ts","css","html","htm",
		"xml","json","md","csv","properties","sh","bat","c","cpp","h","yaml","toml"}),
        IMAGE ("이미지", new String[]{"jpg","jpeg","png","gif","bmp","webp","svg","ico","tif","tiff","raw"}),
        MUSIC ("음악",   new String[]{"mp3","wav","flac","aac","ogg","wma","m4a","opus","mid","midi"}),
        VIDEO ("동영상", new String[]{"mp4","avi","mkv","mov","wmv","flv","webm","m4v","ts","mpg","mpeg","3gp"}),
        DOC   ("문서",   new String[]{"pdf","xls","xlsx","doc","docx","ppt","pptx","hwp","hwpx","odt","ods","odp"}),
        ALL   ("전체",   null),
        OTHER ("기타",   new String[0]);   // 위 카테고리 미해당
		
        final String label;
        final String[] exts;   // null = 전체, empty array = 기타
		
        ExtFilter(String label, String[] exts) {
            this.label = label;
            this.exts  = exts;
		}
		
        boolean matches(String fileName) {
            if (this == ALL) return true;
            String lower = fileName.toLowerCase();
            int dot = lower.lastIndexOf('.');
            if (dot < 0) return this == OTHER;
            String ext = lower.substring(dot + 1);
            if (this == OTHER) {
                for (ExtFilter f : values()) {
                    if (f == ALL || f == OTHER) continue;
                    for (String e : f.exts) if (e.equals(ext)) return false;
				}
                return true;
			}
            for (String e : exts) if (e.equals(ext)) return true;
            return false;
		}
		
        static boolean isTextViewable(String fileName) { return TEXT.matches(fileName); }
	}
	
    // ── UI 컴포넌트 ──────────────────────────────────────────────
    private final JTextField        pathField   = new JTextField();
    private final JButton           browseBtn   = makeBtn("📂 폴더 선택");
    private final JButton           scanBtn     = makeBtn("🔍 스캔");
    private final JButton           stopBtn     = makeBtn("⏹ 중지");
    private final JButton           saveBtn     = makeBtn("💾 저장");
    private final JLabel            statusLabel = new JLabel(" 준비");
    private final JComboBox<String> filterCombo;
	
    // ── 전체 스캔 결과 (필터링 원본) ─────────────────────────────
    // row[0] = 표시 문자열,  row[1] = 절대파일경로 (파일행만, 나머지 null)
    private final java.util.List<String[]> allLines = new ArrayList<>();
	
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String>            lineList  = new JList<>(listModel);
	
    // ── 상태 ─────────────────────────────────────────────────────
    private final JFrame       ownerFrame;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private Thread    scanThread;
    private long      startMs;
    private ExtFilter currentFilter = ExtFilter.ALL;
	
    // ── 날짜 포맷 ────────────────────────────────────────────────
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");
	
    // ═══════════════════════════════════════════════════════════
    //  생성자
    // ═══════════════════════════════════════════════════════════
	
    private SuperDir(JFrame owner) {
        super("📁 SuperDir — 디렉터리 탐색기");
        this.ownerFrame = owner;
        if (owner != null) setIconImage(owner.getIconImage());
		
        String[] labels = Arrays.stream(ExtFilter.values())
		.map(f -> f.label).toArray(String[]::new);
        filterCombo = new JComboBox<>(labels);
        filterCombo.setFont(uiFont());
        filterCombo.setBackground(new Color(248, 252, 255));
        filterCombo.setForeground(FG_COLOR);
		
        buildUI();
        applyStyle();
		
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { stopPrevious(); }
            @Override public void windowClosed(WindowEvent e)  { instance = null; }
		});
		
        setSize(1060, 700);
        if (owner != null) {
            Point op = owner.getLocation();
            setLocation(op.x + 40, op.y + 40);
			} else {
            setLocationRelativeTo(null);
		}
        setVisible(true);
	}
	
    // ═══════════════════════════════════════════════════════════
    //  UI 구성
    // ═══════════════════════════════════════════════════════════
	
    private void buildUI() {
		
        // ── 경로 입력 행 ──────────────────────────────────────────
        JLabel pathLbl = new JLabel("경로: ");
        pathLbl.setFont(uiFont());
        pathLbl.setForeground(FG_COLOR);
        pathField.setFont(uiFont());
        pathField.setBackground(new Color(248, 252, 255));
        pathField.setForeground(FG_COLOR);
        pathField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_CLR),
		BorderFactory.createEmptyBorder(2, 6, 2, 6)));
		
        JPanel pathRow = new JPanel(new BorderLayout(6, 0));
        pathRow.setOpaque(false);
        pathRow.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        pathRow.add(pathLbl,   BorderLayout.WEST);
        pathRow.add(pathField, BorderLayout.CENTER);
        pathRow.add(browseBtn, BorderLayout.EAST);
		
        // ── 버튼 + 필터 행 ────────────────────────────────────────
        JLabel filterLbl = new JLabel("  필터: ");
        filterLbl.setFont(uiFont());
        filterLbl.setForeground(FG_COLOR);
		
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btnRow.setOpaque(false);
        btnRow.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));
        btnRow.add(scanBtn);
        btnRow.add(stopBtn);
        btnRow.add(saveBtn);
        btnRow.add(filterLbl);
        btnRow.add(filterCombo);
		
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(HEADER_BG);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_CLR));
        top.add(pathRow, BorderLayout.NORTH);
        top.add(btnRow,  BorderLayout.SOUTH);
		
        // ── 결과 목록 ──────────────────────────────────────────────
        lineList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        lineList.setBackground(BG_COLOR);
        lineList.setForeground(FG_COLOR);
        lineList.setFixedCellHeight(18);
        lineList.setSelectionBackground(new Color(180, 210, 240));
        lineList.setSelectionForeground(FG_COLOR);
        lineList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
				JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(
				list, value, index, isSelected, cellHasFocus);
                String text = value == null ? "" : value.toString();
                if (isSelected) {
                    lbl.setBackground(new Color(180, 210, 240));
                    lbl.setForeground(FG_COLOR);
					} else if (text.contains("<DIR>")) {
                    lbl.setBackground(BG_COLOR);
                    lbl.setForeground(DIR_COLOR);
					} else if (currentFilter != ExtFilter.ALL && isFileLine(text)) {
                    lbl.setBackground(MATCH_BG);
                    lbl.setForeground(FG_COLOR);
					} else {
                    lbl.setBackground(BG_COLOR);
                    lbl.setForeground(FG_COLOR);
				}
                lbl.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
                return lbl;
			}
		});
		
        JScrollPane scroll = new JScrollPane(lineList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BG_COLOR);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
		
        // ── 상태바 ─────────────────────────────────────────────────
        statusLabel.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        statusLabel.setForeground(STATUS_FG);
		
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(STATUS_BG);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_CLR),
		BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        statusBar.add(statusLabel, BorderLayout.WEST);
		
        // ── 전체 레이아웃 ──────────────────────────────────────────
        getContentPane().setBackground(BG_COLOR);
        setLayout(new BorderLayout());
        add(top,       BorderLayout.NORTH);
        add(scroll,    BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
		
        // ── 버튼 초기 상태 ─────────────────────────────────────────
        stopBtn.setEnabled(false);
        saveBtn.setEnabled(false);
		
        // ── 이벤트 ────────────────────────────────────────────────
        browseBtn.addActionListener(e -> chooseFolder());
        scanBtn  .addActionListener(e -> startScan());
        stopBtn  .addActionListener(e -> cancelScan());
        saveBtn  .addActionListener(e -> saveResult());
        pathField.addActionListener(e -> startScan());
		
        filterCombo.addActionListener(e -> {
            currentFilter = ExtFilter.values()[filterCombo.getSelectedIndex()];
            applyFilter();
		});
		
        // Enter / 더블클릭 → 텍스트 파일 열기
        lineList.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) openSelectedFile();
			}
		});
        lineList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openSelectedFile();
			}
		});
		
        // 단축키
        getRootPane().registerKeyboardAction(e -> startScan(),
		KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> cancelScan(),
		KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> saveResult(),
            KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
		JComponent.WHEN_IN_FOCUSED_WINDOW);
	}
	
    private void applyStyle() {
        JMenuBar bar = new JMenuBar();
        bar.setBackground(HEADER_BG);
        bar.setBorder(BorderFactory.createEmptyBorder());
        JLabel hint = new JLabel(
		"  F5: 스캔    ESC: 중지    Ctrl+S: 저장    Enter/더블클릭: 파일 열기  ");
        hint.setFont(new Font("Malgun Gothic", Font.PLAIN, 11));
        hint.setForeground(new Color(60, 100, 150));
        bar.add(Box.createHorizontalGlue());
        bar.add(hint);
        setJMenuBar(bar);
	}
	
    // ═══════════════════════════════════════════════════════════
    //  텍스트 파일 열기
    // ═══════════════════════════════════════════════════════════
	
    private void openSelectedFile() {
        int idx = lineList.getSelectedIndex();
        if (idx < 0) return;
		
        String displayLine = listModel.getElementAt(idx);
        String filePath    = resolveFilePath(displayLine);
        if (filePath == null) {
            statusLabel.setText(" 파일 행이 아닙니다.");
            return;
		}
        File file = new File(filePath);
        if (!file.exists()) {
            statusLabel.setText(" 파일 없음: " + file.getName());
            return;
		}
        if (!ExtFilter.isTextViewable(file.getName())) {
            statusLabel.setText(" 텍스트 뷰어로 열 수 없는 형식: " + file.getName());
            return;
		}
		
        // SplashWindow.openTextFileWindow(File) 리플렉션 호출
        if (ownerFrame != null) {
            try {
                java.lang.reflect.Method m = ownerFrame.getClass()
				.getDeclaredMethod("openTextFileWindow", File.class);
                m.setAccessible(true);
                m.invoke(ownerFrame, file);
                statusLabel.setText(" 열기: " + file.getName());
                return;
				} catch (NoSuchMethodException ignored) {
                // SplashWindow 가 아닌 경우 자체 뷰어로 fallback
				} catch (Exception ex) {
                statusLabel.setText(" 열기 실패: " + ex.getMessage());
                return;
			}
		}
        // fallback: 자체 간이 뷰어
        openSimpleViewer(file);
	}
	/**
		* 자체 간이 텍스트 뷰어 — fallback 전용
		*
		* ※ SplashWindow 와 연동된 상태에서는 이 메서드가 호출되지 않음.
		*    실제 뷰어는 SplashWindow.showTextWindow() 가 담당.
		*    SuperDir 단독 실행 시에만 동작.
	*/
    /** 자체 간이 텍스트 뷰어 (SplashWindow 연동 불가 시) — SplashWindow.TextFileReader 로 인코딩 자동 탐지 */
    private void openSimpleViewer(File file) {
        new Thread(() -> {
            try {
                SplashWindow.TextFileReader.Result r = SplashWindow.TextFileReader.read(file);   // 인코딩 자동 탐지
                SwingUtilities.invokeLater(() -> {
                    JFrame sub = new JFrame("📄 " + file.getName());
                    sub.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
					
                    JTextArea ta = new JTextArea(r.content);
                    ta.setEditable(false);
                    // ta.setFont(new Font("Malgun Gothic", Font.PLAIN, 13));
					// ta.setFont(new Font("굴림", Font.PLAIN, 14));
					// new Font(Font.MONOSPACED, Font.PLAIN, 13)
					ta.setFont(new Font("돋움체", Font.PLAIN, 16));
                    ta.setBackground(new Color(235, 245, 255));
                    ta.setForeground(FG_COLOR);
                    ta.setLineWrap(true);
                    ta.setWrapStyleWord(false);
                    ta.setMargin(new Insets(6, 8, 6, 8));
					
                    JScrollPane sp = new JScrollPane(ta);
                    sp.setBorder(BorderFactory.createEmptyBorder());
                    sp.getViewport().setBackground(new Color(235, 245, 255));
					
                    // 하단 상태바: 인코딩 + 파일 경로 + 크기
                    JLabel info = new JLabel(
                        " " + r.encLabel + "  |  " + file.getAbsolutePath()
					+ "  (" + file.length() + " bytes)");
                    info.setFont(new Font("Malgun Gothic", Font.PLAIN, 11));
                    info.setForeground(new Color(20, 60, 120));
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
                    sub.setVisible(true);
					
                    statusLabel.setText(" 열기: " + file.getName() + "  " + r.encLabel);
				});
				} catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
				statusLabel.setText(" 읽기 실패: " + ex.getMessage()));
			}
		}, "SD-ViewThread").start();
	}
	
    // ═══════════════════════════════════════════════════════════
    //  필터 적용
    // ═══════════════════════════════════════════════════════════
	
    /**
		* allLines 를 현재 필터로 재구성하여 listModel 갱신.
		*  - ALL: 전부 표시
		*  - 기타 필터: "Directory of" 헤더 기준으로 블록 분리 후
		*    매칭 파일이 있는 블록만 표시 (없는 블록은 통째 생략)
		*
		* 수정: "Directory of" 를 만나는 순간 새 블록을 시작하여
		*       블록 경계 오염(여러 폴더가 한 블록으로 합쳐지는 현상) 수정.
	*/
    private void applyFilter() {
        if (allLines.isEmpty()) return;
        listModel.clear();
		
        if (currentFilter == ExtFilter.ALL) {
            for (String[] row : allLines) listModel.addElement(row[0]);
            updateFilterStatus();
            return;
		}
		
        // "Directory of" 행을 만날 때마다 새 블록 시작
        java.util.List<java.util.List<String[]>> blocks = new ArrayList<>();
        java.util.List<String[]> cur = null;
		
        for (String[] row : allLines) {
            if (row[0].contains("Directory of")) {
                if (cur != null) blocks.add(cur);
                cur = new ArrayList<>();
			}
            if (cur == null) cur = new ArrayList<>();
            cur.add(row);
		}
        if (cur != null && !cur.isEmpty()) blocks.add(cur);
		
        for (java.util.List<String[]> block : blocks) {
            flushFilterBlock(block);
		}
        updateFilterStatus();
	}
	
    private void flushFilterBlock(java.util.List<String[]> block) {
        // 이 블록 안에서 현재 필터에 매칭되는 파일이 하나라도 있는지 확인
        long matched = block.stream()
		.filter(r -> r[1] != null && currentFilter.matches(new File(r[1]).getName()))
		.count();
        if (matched == 0) return;   // 매칭 파일 없으면 블록 전체 생략
		
        for (String[] row : block) {
            if (row[1] != null) {
                // 파일 행: 필터 매칭인 것만 표시
                if (currentFilter.matches(new File(row[1]).getName()))
				listModel.addElement(row[0]);
				} else {
                // 헤더 / 빈 줄 / 합계 행: 그대로 표시
                listModel.addElement(row[0]);
			}
		}
	}
	
    private void updateFilterStatus() {
        statusLabel.setText("🔍 필터: [" + currentFilter.label + "]  —  "
		+ fmt(listModel.getSize()) + " 줄");
	}
	
    // ═══════════════════════════════════════════════════════════
    //  폴더 선택
    // ═══════════════════════════════════════════════════════════
	
    private void chooseFolder() {
        browseBtn.setEnabled(false);
        new Thread(() -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("스캔할 폴더를 선택하세요");
            String cur = pathField.getText().trim();
            if (!cur.isEmpty()) { File f = new File(cur); if (f.isDirectory()) fc.setCurrentDirectory(f); }
            try { JDialog w = new JDialog(); w.add(fc); w.pack(); w.remove(fc); w.dispose(); }
            catch (Exception ignored) {}
            SwingUtilities.invokeLater(() -> {
                browseBtn.setEnabled(true);
                if (fc.showOpenDialog(SuperDir.this) == JFileChooser.APPROVE_OPTION)
				pathField.setText(fc.getSelectedFile().getAbsolutePath());
			});
		}, "SD-BrowseThread").start();
	}
	
    // ═══════════════════════════════════════════════════════════
    //  스캔
    // ═══════════════════════════════════════════════════════════
	
    private void startScan() {
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(this, "경로를 입력하세요.", "SuperDir", JOptionPane.WARNING_MESSAGE);
            return;
		}
        File root = new File(path);
        if (!root.isDirectory()) {
            JOptionPane.showMessageDialog(this, "유효한 폴더가 아닙니다:\n" + path,
			"SuperDir", JOptionPane.WARNING_MESSAGE);
            return;
		}
		
        stopPrevious();
        allLines.clear();
        listModel.clear();
        cancelled.set(false);
        saveBtn.setEnabled(false);
        setScanningState(true);
		
        startMs = System.currentTimeMillis();
        final String startTime = TIME_FMT.format(new Date(startMs));
		
        scanThread = new Thread(() -> {
			
            java.util.List<String[]> batch = new ArrayList<>(BATCH_SIZE);
            Deque<File> stack = new ArrayDeque<>();
            stack.push(root);
            long totalFiles = 0, totalDirs = 0, totalBytes = 0;
			
            while (!stack.isEmpty() && !cancelled.get()) {
                File dir = stack.pop();
				
                addRow(batch, "", null);
                addRow(batch, "  Directory of  " + dir.getAbsolutePath(), null);
                addRow(batch, "", null);
				
                File[] entries = dir.listFiles();
                if (entries == null) {
                    addRow(batch, "    [접근 불가 — 권한 없음]", null);
                    flushIfFull(batch);
                    continue;
				}
				
                Arrays.sort(entries, Comparator
                    .comparing((File f) -> f.isFile() ? 1 : 0)
				.thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
				
                java.util.List<File> subDirs = new ArrayList<>();
                long dirBytes = 0;
				
                for (File f : entries) {
                    if (cancelled.get()) break;
                    String date = DATE_FMT.format(new Date(f.lastModified()));
                    if (f.isDirectory()) {
                        totalDirs++;
                        addRow(batch, String.format("  %-19s  %-18s  %s",
						date, "<DIR>", f.getName()), null);
                        subDirs.add(f);
						} else {
                        totalFiles++;
                        long sz = f.length();
                        totalBytes += sz;
                        dirBytes   += sz;
                        addRow(batch, String.format("  %-19s  %,18d  %s",
						date, sz, f.getName()), f.getAbsolutePath());
					}
                    flushIfFull(batch);
				}
				
                addRow(batch, String.format(
                    "               합계 파일 %d개  /  %s bytes",
				(long)(entries.length - subDirs.size()), fmt(dirBytes)), null);
				
                for (int i = subDirs.size() - 1; i >= 0; i--)
				stack.push(subDirs.get(i));
			}
			
            long endMs     = System.currentTimeMillis();
            long elapsedMs = endMs - startMs;
            String endTime = TIME_FMT.format(new Date(endMs));
			
            addRow(batch, "", null);
            addRow(batch, "═══════════════════════════════════════════════════════", null);
            addRow(batch, String.format("  파일  %s 개    합계  %s bytes  (%s KB)",
			fmt(totalFiles), fmt(totalBytes), fmt(totalBytes / 1024)), null);
            addRow(batch, String.format("  폴더  %s 개", fmt(totalDirs)), null);
            addRow(batch, "", null);
            addRow(batch, String.format("  시작: %s   종료: %s   소요: %,d ms",
			startTime, endTime, elapsedMs), null);
            addRow(batch, "═══════════════════════════════════════════════════════", null);
			
            flushBatch(new ArrayList<>(batch), true);
			
		}, "SD-ScanThread");
        scanThread.setDaemon(true);
        scanThread.start();
	}
	
    private void addRow(java.util.List<String[]> batch, String display, String filePath) {
        String[] row = {display, filePath};
        allLines.add(row);
        batch.add(row);
	}
	
    // ── 배치 flush ────────────────────────────────────────────────
	
    private void flushIfFull(java.util.List<String[]> batch) {
        if (batch.size() >= BATCH_SIZE) {
            flushBatch(new ArrayList<>(batch), false);
            batch.clear();
		}
	}
	
    private void flushBatch(java.util.List<String[]> snapshot, boolean done) {
        SwingUtilities.invokeLater(() -> {
            // 스캔 중: allLines 에만 누적, listModel 은 건드리지 않음
            // 완료 시: applyFilter() 가 현재 필터로 listModel 을 한 번에 구성
            if (done) {
                setScanningState(false);
                saveBtn.setEnabled(!allLines.isEmpty());
                applyFilter();   // ALL 이면 전체, 아니면 선택 필터로 즉시 표시
				} else {
                statusLabel.setText("🔍 스캔 중...  " + fmt(allLines.size()) + " 건 수집");
			}
		});
	}
	
    // ═══════════════════════════════════════════════════════════
    //  중지 / 저장
    // ═══════════════════════════════════════════════════════════
	
    private void cancelScan() {
        cancelled.set(true);
        statusLabel.setText("⏹ 중지 요청됨...");
	}
	
    private void stopPrevious() {
        cancelled.set(true);
        if (scanThread != null && scanThread.isAlive()) {
            scanThread.interrupt();
            scanThread = null;
		}
	}
	
    private void saveResult() {
        if (listModel.getSize() == 0) return;
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("결과 저장");
        fc.setSelectedFile(new File("superdir_result.txt"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("텍스트 파일 (*.txt)", "txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File out = fc.getSelectedFile();
        if (!out.getName().contains(".")) out = new File(out.getAbsolutePath() + ".txt");
        final File finalOut = out;
        new Thread(() -> {
            try (PrintWriter pw = new PrintWriter(
			new OutputStreamWriter(new FileOutputStream(finalOut), "UTF-8"))) {
			int size = listModel.getSize();
			for (int i = 0; i < size; i++) pw.println(listModel.getElementAt(i));
			SwingUtilities.invokeLater(() ->
				JOptionPane.showMessageDialog(SuperDir.this,
					"저장 완료:\n" + finalOut.getAbsolutePath(), "SuperDir",
				JOptionPane.INFORMATION_MESSAGE));
				} catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(SuperDir.this,
                        "저장 실패: " + ex.getMessage(), "SuperDir",
					JOptionPane.ERROR_MESSAGE));
			}
		}, "SD-SaveThread").start();
	}
	
    // ═══════════════════════════════════════════════════════════
    //  유틸
    // ═══════════════════════════════════════════════════════════
	
    /** 화면 표시 문자열 → 실제 파일 절대경로 역추적 */
    private String resolveFilePath(String displayLine) {
        if (displayLine == null || displayLine.isBlank()) return null;
        for (String[] row : allLines) {
            if (row[1] != null && row[0].equals(displayLine)) return row[1];
		}
        return null;
	}
	
    /** 파일 행 여부 판단 (날짜 패턴: "  2024-01-01 ...") */
    private boolean isFileLine(String line) {
        if (line == null || line.length() < 22) return false;
        String t = line.stripLeading();
        return t.length() > 19 && Character.isDigit(t.charAt(0)) && t.charAt(4) == '-'
		&& !t.contains("<DIR>");
	}
	
    private void setScanningState(boolean scanning) {
        scanBtn    .setEnabled(!scanning);
        stopBtn    .setEnabled( scanning);
        browseBtn  .setEnabled(!scanning);
        pathField  .setEnabled(!scanning);
        filterCombo.setEnabled(!scanning);
        statusLabel.setText(scanning ? "🔍 스캔 중..." : " 준비");
	}
	
    private static Font uiFont() { return new Font("Malgun Gothic", Font.PLAIN, 13); }
	
    private static JButton makeBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Malgun Gothic", Font.PLAIN, 13));
        b.setForeground(FG_COLOR);
        b.setBackground(new Color(210, 235, 250));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_CLR),
		BorderFactory.createEmptyBorder(3, 10, 3, 10)));
        return b;
	}
	
    private static String fmt(long n) { return String.format("%,d", n); }
}
