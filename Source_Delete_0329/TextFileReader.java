import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
	* TextFileReader - 인코딩 자동 탐지 텍스트 파일 읽기
	*
	* 탐지 순서: UTF-8 BOM → UTF-16 BE BOM → BOM없는 UTF-8 → CP949
	*
	* 사용법:
	*   TextFileReader.Result r = TextFileReader.read(file);
	*   System.out.println(r.encLabel);  // "[ UTF-8 ]" 등
	*   System.out.println(r.content);   // 파일 내용
*/
public class TextFileReader {
    /** 읽기 결과 */
    public static class Result {
        public final String encLabel;  // 인코딩 레이블
        public final String content;   // 파일 내용
        public Result(String encLabel, String content) {
            this.encLabel = encLabel;
            this.content  = content;
		}
	}
    /**
		* 파일을 인코딩 자동 탐지하여 읽기
		* @param file 읽을 파일
		* @return Result (encLabel + content)
	*/
    public static Result read(File file) throws IOException {
        String enc      = "CP949";
        String encLabel = "[ CP949 ]";
        try (FileInputStream fis = new FileInputStream(file)) {
            // ① BOM 3바이트 확인
            byte[] bom = new byte[3];
            int bomRead = fis.read(bom, 0, 3);
            if (bomRead >= 3 &&
                bom[0] == (byte)0xEF &&
                bom[1] == (byte)0xBB &&
			bom[2] == (byte)0xBF)
            {
                // UTF-8 BOM → BOM 3바이트 건너뛰고 나머지만 읽기
                return new Result("[ UTF-8 BOM ]", readAll(fis, "UTF-8"));
			}
			
            if (bomRead >= 2 &&
                bom[0] == (byte)0xFE &&
			bom[1] == (byte)0xFF)
            {
                // UTF-16 BE BOM → 전체 이어붙여 읽기
                InputStream rest = new SequenceInputStream(
				new ByteArrayInputStream(bom, 0, bomRead), fis);
                return new Result("[ UTF-16 BE BOM ]", readAll(rest, "UTF-16BE"));
			}
            // ② BOM 없음 → 전체 바이트 읽어서 UTF-8 검증
            ByteArrayOutputStream baos = new ByteArrayOutputStream(
			(int) Math.min(file.length(), 4 * 1024 * 1024));
            baos.write(bom, 0, bomRead);
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) baos.write(buf, 0, n);
            byte[] all = baos.toByteArray();
            if (isValidUTF8(all)) {
                enc      = "UTF-8";
                encLabel = "[ UTF-8 ]";
			}
            return new Result(encLabel, readAll(new ByteArrayInputStream(all), enc));
		}
	}
	
	public static Result read(String fullPath) throws IOException {
		return read(new File(fullPath));
	}
	
	/** 내용만 필요할 때 */
	public static String readContent(File file) throws IOException {
		return read(file).content;
	}
	
	public static String readContent(String fullPath) throws IOException {
		return read(new File(fullPath)).content;
	}
    // ── 내부 유틸 ─────────────────────────────────────────────────
    private static String readAll(InputStream is, String enc) throws IOException {
        BufferedReader br = new BufferedReader(
		new InputStreamReader(is, enc), 1024 * 1024);
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
}
/*
	Caller :
	TextFileReader.read(file);       // File 객체
	TextFileReader.read("C:\\a.txt"); // 경로 문자열
	
	// 인코딩도 필요할 때
	TextFileReader.Result r = TextFileReader.read(file);
	TextFileReader.Result r = TextFileReader.read(StringOfFullpath);
	String encoding = r.encLabel  ;
	String text = r.content;
	
	// 내용만 필요할 때
	//  new 필요 없습니다. static 메서드라 그냥:
	[X] String text = new TextFileReader.readContent(file);
	[x] String text = new TextFileReader.readContent(StringOfFullpath);
	[O] String text = TextFileReader.readContent(file);
	[O] String text = TextFileReader.readContent(StringOfFullpath);
*/

/* // 예비용(from)
// 파일을 UTF-8 → UTF-16 BE → EUC-KR 순으로 자동 인코딩 탐지하여 읽기
// 반환: [0] = 인코딩 레이블,  [1] = 파일 내용 
private String[] readFileAutoEncoding(File file) throws IOException {
	String enc, encLabel;
	enc      = "CP949";
	encLabel = "[ CP949 ]";
	byte[] bytes = new byte[4096];
	try (FileInputStream fis = new FileInputStream(file)) {
		int read = fis.read(bytes, 0, 512);
		if (read >= 3 &&
			bytes[0] == (byte)0xEF &&
			bytes[1] == (byte)0xBB &&
		bytes[2] == (byte)0xBF)
		{
			enc      = "UTF-8";
			encLabel = "[ UTF-8 BOM ]";
		} 
		else if (read >= 2 &&
			bytes[0] == (byte)0xFE &&
		bytes[1] == (byte)0xFF)
		{
			enc      = "UTF-16BE";
			encLabel = "[ UTF-16 BE BOM ]";
		} 
		else if (isValidUTF8(bytes, read)) 
		{
			enc      = "UTF-8";
			encLabel = "[ UTF-8 ]";
		} 
		else 
		{
			enc      = "CP949";
			encLabel = "[ CP949 ]";
		}
	}
	try (BufferedReader br = new BufferedReader(
	new InputStreamReader(new FileInputStream(file), enc))) {
	StringBuilder sb = new StringBuilder((int) Math.min(file.length(), 4 * 1024 * 1024));
	String line;
	while ((line = br.readLine()) != null) {
		sb.append(line).append("\n");
	}
	return new String[]{ encLabel, sb.toString() };
	}
}	
private static boolean isValidUTF8(byte[] bytes, int length) {
	try {
		CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder();
		dec.onMalformedInput(CodingErrorAction.REPORT);
		dec.onUnmappableCharacter(CodingErrorAction.REPORT);
		dec.decode(ByteBuffer.wrap(bytes, 0, length));  // length 지정
		return true;
	} catch (Exception e) { return false; }
}
*/  // 예비용(to)