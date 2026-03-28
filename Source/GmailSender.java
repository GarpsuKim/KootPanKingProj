import java.net.HttpURLConnection;
import java.net.URL;



/**
	* GmailSender - 순수 Java SMTP 메일 전송 클래스
	*
	* 외부 라이브러리 불필요.
	* Gmail SMTP + STARTTLS (포트 587) + AUTH LOGIN (Base64) 방식.
	*
	* 설정값(from, pass, lastTo)은 KootPanKing 이
	* clock_config.properties 에서 로드하여 필드에 직접 할당한다.
*/
public class GmailSender {
	// 추가된 필드 (AppLogger 대신 외부 주입)
	public String exeFilePath = "";
	public String logFilePath = "";
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final int    SMTP_PORT = 587;
    public static final String APP_SIGNATURE =
	"\n\n[끝판왕]에서 보내는 알림입니다.\n\n";
	
    // ── 설정 필드 (외부에서 직접 읽기/쓰기) ──────────────────────
    public String from    = "";  // Gmail 주소       (clock_config: emailFrom)
    public String pass    = "";  // Gmail 앱 비밀번호 (clock_config: emailPass)
    public String lastTo  = "";  // 마지막 수신자    (clock_config: emailLastTo)
	
    // ── 생성자 ────────────────────────────────────────────────────
    public GmailSender() {}
	
    // ── 공개 API ──────────────────────────────────────────────────
	
    /** 설정이 충분한지 여부 */
    public boolean isConfigured() {
        return !from.isEmpty() && !pass.isEmpty();
	}
	
    /**
		* 메일 전송 (수신자/제목/본문 직접 지정).
		* @throws Exception SMTP 오류 시
	*/
    public void send(String to, String subject, String body) throws Exception {
        smtpSend(from, pass, from, to, subject, body);
        lastTo = to; // 마지막 수신자 갱신
	}
	
    /**
		* 알람 메일 전송 (AlarmEntry 정보 기반).
		* 오류는 콘솔 출력만 하고 예외를 던지지 않는다.
	*/
    public void sendAlarm(String toAddr, int hour, int minute, String msg) {
        try {
            String subj = "알람 " + String.format("%02d:%02d", hour, minute);
            smtpSend(from, pass, from, toAddr, subj, msg);
			} catch (Exception e) {
            System.out.println("[Alarm Email] " + e.getMessage());
		}
	}
	
    /**
		* 시작 알림 메일 전송 (비동기).
		* from/pass/lastTo 가 모두 설정된 경우에만 전송.
	*/
    public void sendStartupNotice() {
        if (!isConfigured() || lastTo.isEmpty()) return;
        new Thread(() -> {
            try {
                String now      = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
                String pcName   = java.net.InetAddress.getLocalHost().getHostName();
                String userId   = System.getProperty("user.name");
                String osName   = System.getProperty("os.name") + " " + System.getProperty("os.version");
                String javaVer  = System.getProperty("java.version");
                String localIp  = java.net.InetAddress.getLocalHost().getHostAddress();
                String publicIp = getPublicIp();
                String body = APP_SIGNATURE
                + "PC가 시작되었습니다.\n\n"
                + "시작 시각: " + now + "\n\n"
                + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                + "PC 이름  : " + pcName  + "\n"
                + "사용자   : " + userId  + "\n"
                + "IP (내부) : " + localIp   + "\n"
                + "IP (외부) : " + publicIp  + "\n"
                + "OS       : " + osName  + "\n"
                + "Java     : " + javaVer + "\n"
                + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                + "실행 파일: " + exeFilePath + "\n"
                + "로그 파일: " + logFilePath + "\n"
                + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
                send(lastTo, "PC 시작 알림", body);
                System.out.println("[Email] 시작 알림 전송 완료");
            } catch (Exception e) {
                System.out.println("[Email] 시작 알림 전송 실패: " + e.getMessage());
            }
        }, "StartupEmail").start();
    }
	
    /** 외부 공인 IP 조회 (api.ipify.org 사용) */
    private String getPublicIp() {
        try {
            // java.net.URL url = new java.net.URL("https://api.ipify.org");
            java.net.URL url = toUrl("https://api.ipify.org");
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(con.getInputStream(), "UTF-8"));
            String ip = br.readLine();
            br.close();
            con.disconnect();
            return (ip != null && !ip.isEmpty()) ? ip.trim() : "(조회 실패)";
        } catch (Exception e) {
            return "(조회 실패)";
        }
    }

    /**
		* from/pass/lastTo 가 모두 설정된 경우에만 전송.
	*/
    public void sendShutdownNotice(Runnable afterSend) {
        if (!isConfigured() || lastTo.isEmpty()) {
            if (afterSend != null) afterSend.run();
            return;
		}
        new Thread(() -> {
            try {
                send(lastTo, "PC 종료 알림",
                    APP_SIGNATURE + "PC가 종료됩니다.\n\n"
                    + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
				.format(new java.util.Date()));
                System.out.println("[Email] 종료 알림 전송 완료");
				} catch (Exception e) {
                System.out.println("[Email] 종료 알림 전송 실패: " + e.getMessage());
				} finally {
                if (afterSend != null) afterSend.run();
			}
		}, "ShutdownEmail").start();
	}
	
    /**
		* 종료 알림 메일 전송 - 제목/본문 직접 지정 버전 (텔레그램 원격 종료 등에 사용).
	*/
    public void sendShutdownNotice(Runnable afterSend, String subject, String body) {
        if (!isConfigured() || lastTo.isEmpty()) {
            if (afterSend != null) afterSend.run();
            return;
		}
        new Thread(() -> {
            try {
                send(lastTo, subject, body);
                System.out.println("[Email] 종료 알림 전송 완료");
				} catch (Exception e) {
                System.out.println("[Email] 종료 알림 전송 실패: " + e.getMessage());
				} finally {
                if (afterSend != null) afterSend.run();
			}
		}, "ShutdownEmail").start();
	}

    // ── 내부 SMTP 구현 ────────────────────────────────────────────
	
    /**
		* 순수 Java SMTP 전송.
		* Gmail SMTP + STARTTLS (포트 587) + AUTH LOGIN (Base64)
	*/
	void smtpSend(		String user, String pass,
		String from, String to,
		String subject, String body) throws Exception {
		
        // 1) 평문 소켓으로 연결
        java.net.Socket sock = new java.net.Socket(SMTP_HOST, SMTP_PORT);
        sock.setSoTimeout(15000);
		
        java.io.BufferedReader  rd = new java.io.BufferedReader(
		new java.io.InputStreamReader(sock.getInputStream(),  "UTF-8"));
        java.io.PrintWriter     wr = new java.io.PrintWriter(
		new java.io.OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true);
		
        // SMTP 헬퍼 (응답 읽기)
        java.util.function.Supplier<String> readLine = () -> {
            try { return rd.readLine(); } catch (Exception e) { return ""; }
		};
        java.util.function.Consumer<String> send = cmd -> wr.println(cmd);
		
        smtpExpect(readLine.get(), "220");              // 서버 인사
		
        send.accept("EHLO localhost");
        String line;
        while ((line = readLine.get()) != null) {       // EHLO 멀티라인
            if (line.startsWith("250 ")) break;
		}
		
        // 2) STARTTLS 업그레이드
        send.accept("STARTTLS");
        smtpExpect(readLine.get(), "220");
		
        javax.net.ssl.SSLSocketFactory sf =
		(javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
        javax.net.ssl.SSLSocket ssl =
		(javax.net.ssl.SSLSocket) sf.createSocket(sock, SMTP_HOST, SMTP_PORT, true);
        ssl.startHandshake();
		
        java.io.BufferedReader  srd = new java.io.BufferedReader(
		new java.io.InputStreamReader(ssl.getInputStream(),  "UTF-8"));
        java.io.PrintWriter     swr = new java.io.PrintWriter(
		new java.io.OutputStreamWriter(ssl.getOutputStream(), "UTF-8"), true);
		
        java.util.function.Supplier<String> sRead = () -> {
            try { return srd.readLine(); } catch (Exception e) { return ""; }
		};
		
        swr.println("EHLO localhost");
        while ((line = sRead.get()) != null) {
            if (line.startsWith("250 ")) break;
		}
		
        // 3) AUTH LOGIN
        swr.println("AUTH LOGIN");
        smtpExpect(sRead.get(), "334");
        swr.println(java.util.Base64.getEncoder().encodeToString(user.getBytes("UTF-8")));
        smtpExpect(sRead.get(), "334");
        swr.println(java.util.Base64.getEncoder().encodeToString(pass.getBytes("UTF-8")));
        smtpExpect(sRead.get(), "235");                 // 인증 성공
		
        // 4) 메일 전송
        swr.println("MAIL FROM:<" + from + ">");
        smtpExpect(sRead.get(), "250");
        swr.println("RCPT TO:<" + to + ">");
        smtpExpect(sRead.get(), "250");
        swr.println("DATA");
        smtpExpect(sRead.get(), "354");
		
        // RFC 2047 Subject 인코딩 (한글 깨짐 방지)
        String encSubj = "=?UTF-8?B?" +
		java.util.Base64.getEncoder().encodeToString(subject.getBytes("UTF-8")) + "?=";
        String date = new java.text.SimpleDateFormat(
		"EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH)
		.format(new java.util.Date());
		
        swr.println("Date: " + date);
        swr.println("From: " + from);
        swr.println("To: " + to);
        swr.println("Subject: " + encSubj);
        swr.println("MIME-Version: 1.0");
        swr.println("Content-Type: text/plain; charset=UTF-8");
        swr.println("Content-Transfer-Encoding: base64");
        swr.println();
        // body를 Base64로 인코딩 (한글 완벽 지원)
        swr.println(java.util.Base64.getMimeEncoder(76, new byte[]{'\r','\n'})
		.encodeToString(body.getBytes("UTF-8")));
        swr.println(".");
        smtpExpect(sRead.get(), "250");                 // 전송 완료
		
        swr.println("QUIT");
        ssl.close();
        sock.close();
        System.out.println("[Email] 전송 완료 → " + to);
	}
	
    private void smtpExpect(String response, String code) throws Exception {
        if (response == null || !response.startsWith(code))
		throw new Exception("SMTP 오류: " + response);
	}
    // ── 개발자 고정 계정 (XOR + Base64 난독화) ───────────────────
    //
    //  DEV_ID_ENC / DEV_PASS_ENC 값은
    //  DevCredentialEncryptor.java 를 로컬 1회 실행 후 교체한다.
    //  실행 후 DevCredentialEncryptor.java 는 즉시 삭제할 것.

    private static final int[] _K = {
        0x4B, 0x6F, 0x6F, 0x74,   // "Koot"
        0x50, 0x61, 0x6E, 0x4B,   // "PanK"
        0x69, 0x6E, 0x67, 0x32,   // "ing2"
        0x30, 0x32, 0x35, 0x21    // "025!"
    };

    /** 개발자 Gmail 주소 — DevCredentialEncryptor 로 생성 */
    private static final String DEV_ID_ENC   = "KgEOGD8GDScGDQxBVV1ATQsIAhU5DUAoBgM=";  // ← 교체

    /** 개발자 Gmail 앱 비밀번호 — DevCredentialEncryptor 로 생성 */
    private static final String DEV_PASS_ENC = "LRgJFzsDACoeBghfX11WVw==";  // ← 교체

    static String devGmailId()   { return xorDecrypt(DEV_ID_ENC);   }
    static String devGmailPass() { return xorDecrypt(DEV_PASS_ENC); }

    private static byte[] xorKey() {
        byte[] k = new byte[_K.length];
        for (int i = 0; i < _K.length; i++) k[i] = (byte) _K[i];
        return k;
    }

    private static String xorDecrypt(String b64) {
        try {
            byte[] key = xorKey();
            byte[] enc = java.util.Base64.getDecoder().decode(b64);
            byte[] out = new byte[enc.length];
            for (int i = 0; i < enc.length; i++)
                out[i] = (byte)(enc[i] ^ key[i % key.length]);
            return new String(out, "UTF-8");
        } catch (Exception e) { return ""; }
    }

    // ── 유틸 ──────────────────────────────────────────────────────
    @SuppressWarnings("deprecation")
    private static URL toUrl(String s) {
        try { return new URL(s); }
        catch (Exception e) { throw new RuntimeException(e); }
	}
}