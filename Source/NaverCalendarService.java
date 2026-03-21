import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import java.time.Instant;

// ical4j 4.x
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.util.MapTimeZoneCache;

/**
	* NaverCalendarService - 네이버 캘린더 CalDAV 연동
	* 
	* 원본 기능 모두 보존 + 콘솔 출력 추가
*/
public class NaverCalendarService {
    
    private static final String CALDAV_BASE = "https://caldav.calendar.naver.com";
    private static final int MAX_EVENTS_PER_REQUEST = 1000;
    
    private String naverId = "";
    private char[] naverPassword;
    private boolean initialized = false;
    private String calendarHomeUrl = "";
    private final Set<String> processedEventKeys = Collections.newSetFromMap(
        new LinkedHashMap<String, Boolean>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > 10000;
			}
		});
		
		public NaverCalendarService() {
			System.setProperty("net.fortuna.ical4j.timezone.cache.impl",
			MapTimeZoneCache.class.getName());
			System.setProperty("net.fortuna.ical4j.timezone.update.enabled", "false");
			System.out.println("[NaverCal] ical4j 타임존 설정 완료");
		}
		
		public void setCredentials(String id, String password) {
			this.naverId = id != null ? id.trim() : "";
			if (this.naverPassword != null) {
				Arrays.fill(this.naverPassword, ' ');
			}
			this.naverPassword = password != null ? password.trim().toCharArray() : new char[0];
			System.out.println("[NaverCal] 자격 증명 설정: " + naverId);
		}
		
		public boolean isInitialized() { return initialized; }
		
		public static boolean credentialsExist(String id, String pass) {
			return id != null && !id.trim().isEmpty()
            && pass != null && !pass.trim().isEmpty();
		}
		
		public boolean init() {
			if (naverId.isEmpty() || naverPassword.length == 0) {
				System.out.println("[NaverCal ERROR] 초기화 실패: 자격 증명이 없음");
				return false;
			}
			
			System.out.println("[NaverCal] 네이버 캘린더 초기화 시작...");
			
			try {
				// 1단계: current-user-principal
				System.out.println("[NaverCal] 1. PROPFIND / 요청 중...");
				String rootResp = caldavRequest("PROPFIND", CALDAV_BASE + "/",
				propfindPrincipalBody(), "application/xml", "0");
				if (rootResp == null) {
					System.out.println("[NaverCal ERROR] PROPFIND 루트 응답 없음");
					return false;
				}
				
				String principalUrl = extractHref(rootResp, "current-user-principal");
				if (principalUrl == null || principalUrl.isEmpty()) {
					System.out.println("[NaverCal ERROR] current-user-principal 추출 실패");
					return false;
				}
				String absPrincipalUrl = principalUrl.startsWith("http")
                ? principalUrl : CALDAV_BASE + principalUrl;
				System.out.println("[NaverCal]   → principal URL: " + absPrincipalUrl);
				
				// 2단계: calendar-home-set
				System.out.println("[NaverCal] 2. calendar-home-set 요청 중...");
				String propResp = caldavRequest("PROPFIND", absPrincipalUrl,
				propfindHomeSetBody(), "application/xml", "0");
				if (propResp == null) {
					System.out.println("[NaverCal ERROR] calendar-home-set PROPFIND 실패");
					return false;
				}
				
				String homeUrl = extractHref(propResp, "calendar-home-set");
				if (homeUrl == null || homeUrl.isEmpty()) {
					System.out.println("[NaverCal ERROR] calendar-home-set 추출 실패");
					return false;
				}
				
				calendarHomeUrl = homeUrl.startsWith("http") ? homeUrl : CALDAV_BASE + homeUrl;
				initialized = true;
				System.out.println("[NaverCal] 초기화 성공! 캘린더 홈 URL: " + calendarHomeUrl);
				return true;
				
				} catch (Exception e) {
				System.out.println("[NaverCal ERROR] 초기화 중 예외 발생: " + e.getMessage());
				e.printStackTrace();
				return false;
			}
		}
		
		public List<CalendarEvent> getToday() {
			ZonedDateTime start = LocalDate.now().atStartOfDay(ZoneId.systemDefault());
			ZonedDateTime end = start.plusDays(1).minusSeconds(1);
			System.out.println("[NaverCal] 오늘 일정 조회 중...");
			return getEvents(start, end);
		}
		
		public List<CalendarEvent> getNextDays(int days) {
			ZonedDateTime start = LocalDate.now().atStartOfDay(ZoneId.systemDefault());
			ZonedDateTime end = start.plusDays(days);
			System.out.println("[NaverCal] " + days + "일 후까지 일정 조회 중...");
			return getEvents(start, end);
		}
		
		public List<CalendarEvent> getPastDays(int days) {
			ZonedDateTime end = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault());
			ZonedDateTime start = end.minusDays(days);
			System.out.println("[NaverCal] 지난 " + days + "일 일정 조회 중...");
			return getEvents(start, end);
		}
		
		public List<CalendarEvent> getThisWeek() {
			LocalDate today = LocalDate.now();
			LocalDate monday = today.with(DayOfWeek.MONDAY);
			LocalDate sunday = today.with(DayOfWeek.SUNDAY);
			ZonedDateTime start = monday.atStartOfDay(ZoneId.systemDefault());
			ZonedDateTime end = sunday.atTime(23, 59, 59).atZone(ZoneId.systemDefault());
			System.out.println("[NaverCal] 이번주 일정 조회 중...");
			return getEvents(start, end);
		}
		
		public List<CalendarEvent> getThisMonth() {
			LocalDate today = LocalDate.now();
			LocalDate firstDay = today.withDayOfMonth(1);
			LocalDate lastDay = today.withDayOfMonth(today.lengthOfMonth());
			ZonedDateTime start = firstDay.atStartOfDay(ZoneId.systemDefault());
			ZonedDateTime end = lastDay.atTime(23, 59, 59).atZone(ZoneId.systemDefault());
			System.out.println("[NaverCal] 이번달 일정 조회 중...");
			return getEvents(start, end);
		}
		
		public List<CalendarEvent> getUpcomingAlarms(int withinMinutes) {
			ZonedDateTime now = ZonedDateTime.now();
			ZonedDateTime end = now.plusMinutes(withinMinutes);
			System.out.println("[NaverCal] " + withinMinutes + "분 내 일정 조회 중...");
			return getEvents(now.minusSeconds(30), end);
		}
		
		private List<CalendarEvent> getEvents(ZonedDateTime start, ZonedDateTime end) {
			List<CalendarEvent> result = new ArrayList<>();
			if (!initialized) {
				System.out.println("[NaverCal WARNING] 서비스가 초기화되지 않음");
				return result;
			}
			
			try {
				List<String> calUrls = fetchCalendarUrls(calendarHomeUrl);
				if (calUrls.isEmpty()) {
					System.out.println("[NaverCal WARNING] 캘린더 URL이 없음");
					return result;
				}
				System.out.println("[NaverCal] 캘린더 " + calUrls.size() + "개 발견");
				
				for (String calUrl : calUrls) {
					if (result.size() >= MAX_EVENTS_PER_REQUEST) {
						System.out.println("[NaverCal WARNING] 최대 이벤트 수 도달: " + MAX_EVENTS_PER_REQUEST);
						break;
					}
					
					try {
						String propResp = caldavRequest("PROPFIND", calUrl,
						propfindIcsBody(), "application/xml", "1");
						if (propResp == null || propResp.isEmpty()) continue;
						
						List<String> icsUrls = extractIcsHrefs(propResp);
						String calName = calUrl.replaceAll(".*/calendar/", "");
						// System.out.println("[NaverCal] " + calName + " → .ics " + icsUrls.size() + "개");
						
						for (String icsUrl : icsUrls) {
							try {
								String absIcsUrl = icsUrl.startsWith("http")
                                ? icsUrl : CALDAV_BASE + icsUrl;
								String icsText = caldavRequest("GET", absIcsUrl,
								null, "text/calendar", "0");
								if (icsText == null || icsText.isEmpty()) continue;
								
								List<CalendarEvent> parsed = parseIcsWithIcal4j(icsText, start, end);
								for (CalendarEvent ev : parsed) {
									String key = ev.id + "|" + ev.startTime.toInstant().toEpochMilli();
									if (processedEventKeys.add(key)) {
										result.add(ev);
										if (result.size() >= MAX_EVENTS_PER_REQUEST) break;
									}
								}
								} catch (Exception e) {
								System.out.println("[NaverCal ERROR] ICS 파싱 오류: " + e.getMessage());
							}
						}
						} catch (Exception e) {
						System.out.println("[NaverCal ERROR] 캘린더 처리 오류: " + e.getMessage());
					}
				}
				
				result.sort((a, b) -> a.startTime.compareTo(b.startTime));
				System.out.println("[NaverCal] 총 " + result.size() + "개 일정 찾음");
				
				} catch (Exception e) {
				System.out.println("[NaverCal ERROR] 이벤트 조회 중 오류: " + e.getMessage());
				e.printStackTrace();
			}
			return result;
		}
		
		private List<CalendarEvent> parseIcsWithIcal4j(String icsText,
			ZonedDateTime qStart, ZonedDateTime qEnd) {
			List<CalendarEvent> result = new ArrayList<>();
			try {
				String cleanedIcs = icsText.replaceAll(
				"(?s)BEGIN:VALARM.*?END:VALARM\\r?\\n?", "");
				
				CalendarBuilder builder = new CalendarBuilder();
				Calendar cal = builder.build(new StringReader(cleanedIcs));
				
				for (Object comp : cal.getComponents(Component.VEVENT)) {
					VEvent vevent = (VEvent) comp;
					try {
						String uid = vevent.getUid()
						.map(Uid::getValue)
						.orElse(UUID.randomUUID().toString());
						
						Summary sumProp = vevent.getSummary();
						String title = (sumProp != null) ? sumProp.getValue() : "(제목 없음)";
						
						// DTSTART으로 allDay 여부 확인
						boolean allDay = vevent
						.getProperty(net.fortuna.ical4j.model.Property.DTSTART)
						.flatMap(ds -> ds.getParameter(Parameter.VALUE))
						.map(v -> Value.DATE.equals(v))
						.orElse(false);
						
						if (allDay) {
							// 종일 이벤트: LocalDate 기간으로 검색
							Period<LocalDate> searchPeriod = new Period<>(
							qStart.toLocalDate(), qEnd.toLocalDate());
							Collection<Period<LocalDate>> periods = 
							vevent.calculateRecurrenceSet(searchPeriod);
							
							if (periods == null || periods.isEmpty()) continue;
							
							for (Period<LocalDate> p : periods) {
								ZonedDateTime evStart = p.getStart().atStartOfDay(ZoneId.systemDefault());
								ZonedDateTime evEnd = p.getEnd().atStartOfDay(ZoneId.systemDefault());
								result.add(new CalendarEvent(uid, title, evStart, evEnd, true));
							}
							} else {
							// 시간 기반 이벤트: Instant 기간으로 검색
							Period<Instant> searchPeriod = new Period<>(
							qStart.toInstant(), qEnd.toInstant());
							Collection<Period<Instant>> periods = 
							vevent.calculateRecurrenceSet(searchPeriod);
							
							if (periods == null || periods.isEmpty()) continue;
							
							for (Period<Instant> p : periods) {
								ZonedDateTime evStart = p.getStart().atZone(ZoneId.systemDefault());
								ZonedDateTime evEnd = p.getEnd().atZone(ZoneId.systemDefault());
								result.add(new CalendarEvent(uid, title, evStart, evEnd, false));
							}
						}
						} catch (Exception e) {
						// RRULE 관련 오류는 로그만 남기고 계속 진행
						if (e.getMessage().contains("Unsupported unit") || 
							e.getMessage().contains("Unable to obtain")) {
							System.out.println("[NaverCal DEBUG] 일부 일정 파싱 실패 (무시): " + e.getMessage());
							} else {
							System.out.println("[NaverCal ERROR] VEVENT 처리 오류: " + e.getMessage());
						}
					}
				}
				} catch (Exception e) {
				System.out.println("[NaverCal ERROR] ICS 파싱 오류: " + e.getMessage());
			}
			return result;
		}
		
		private List<CalendarEvent> parseIcsWithIcal4j_(String icsText,
			ZonedDateTime qStart, ZonedDateTime qEnd) {
			List<CalendarEvent> result = new ArrayList<>();
			try {
				String cleanedIcs = icsText.replaceAll(
				"(?s)BEGIN:VALARM.*?END:VALARM\\r?\\n?", "");
				
				CalendarBuilder builder = new CalendarBuilder();
				Calendar cal = builder.build(new StringReader(cleanedIcs));
				
				for (Object comp : cal.getComponents(Component.VEVENT)) {
					VEvent vevent = (VEvent) comp;
					try {
						String uid = vevent.getUid()
						.map(Uid::getValue)
						.orElse(UUID.randomUUID().toString());
						
						Summary sumProp = vevent.getSummary();
						String title = (sumProp != null) ? sumProp.getValue() : "(제목 없음)";
						
						// ✅ 개선: 모든 이벤트를 Instant 기준으로 처리
						Period<Instant> searchPeriod = new Period<>(
						qStart.toInstant(), qEnd.toInstant());
						
						Collection<Period<Instant>> periods = 
						vevent.calculateRecurrenceSet(searchPeriod);
						
						if (periods == null || periods.isEmpty()) continue;
						
						for (Period<Instant> p : periods) {
							// ✅ 각 발생 인스턴스의 시작 시간으로 allDay 여부 재확인
							boolean isAllDay = checkIfAllDayAt(vevent, p.getStart());
							
							if (isAllDay) {
								// 종일: 날짜만 사용
								LocalDate startDate = p.getStart().atZone(ZoneId.systemDefault()).toLocalDate();
								LocalDate endDate = p.getEnd().atZone(ZoneId.systemDefault()).toLocalDate();
								result.add(new CalendarEvent(uid, title, 
									startDate.atStartOfDay(ZoneId.systemDefault()),
									endDate.atStartOfDay(ZoneId.systemDefault()), 
								true));
								} else {
								// 시간 이벤트: Instant 직접 사용
								result.add(new CalendarEvent(uid, title,
									p.getStart().atZone(ZoneId.systemDefault()),
									p.getEnd().atZone(ZoneId.systemDefault()),
								false));
							}
						}
						} catch (Exception e) {
						System.out.println("[NaverCal ERROR] VEVENT 처리 오류: " + e.getMessage());
					}
				}
				} catch (ParserException e) {
				System.out.println("[NaverCal ERROR] ICS 파싱 오류: " + e.getMessage());
				} catch (Exception e) {
				System.out.println("[NaverCal ERROR] 예상치 못한 오류: " + e.getMessage());
			}
			return result;
		}
		
		// ✅ 새 헬퍼 메서드: 특정 날짜에서 allDay 여부 확인
		private boolean checkIfAllDayAt(VEvent vevent, Instant occurrence) {
			try {
				return vevent
				.<net.fortuna.ical4j.model.Property>getProperty(
				net.fortuna.ical4j.model.Property.DTSTART)
				.flatMap(ds -> ds.<net.fortuna.ical4j.model.Parameter>getParameter(
				Parameter.VALUE))
				.map(v -> Value.DATE.equals(v))
				.orElse(false);
				} catch (Exception e) {
				return false;
			}
		}
		
		private List<CalendarEvent> parseIcsWithIcal4j___(String icsText,
			ZonedDateTime qStart, ZonedDateTime qEnd) {
			List<CalendarEvent> result = new ArrayList<>();
			try {
				String cleanedIcs = icsText.replaceAll(	"(?s)BEGIN:VALARM.*?END:VALARM\\r?\\n?", "");
				
				CalendarBuilder builder = new CalendarBuilder();
				Calendar cal = builder.build(new StringReader(cleanedIcs));
				
				Period<Instant> instantPeriod = new Period<>(
				qStart.toInstant(), qEnd.toInstant());
				Period<LocalDate> datePeriod = new Period<>(
				qStart.toLocalDate(), qEnd.toLocalDate());
				
				for (Object comp : cal.getComponents(Component.VEVENT)) 
				{
					VEvent vevent = (VEvent) comp;
					try {
						String uid = vevent.getUid()
						.map(Uid::getValue)
						.orElse(UUID.randomUUID().toString());
						
						Summary sumProp = vevent.getSummary();
						String title = (sumProp != null) ? sumProp.getValue() : "(제목 없음)";
						
						boolean allDay = vevent
						.<net.fortuna.ical4j.model.Property>getProperty(
						net.fortuna.ical4j.model.Property.DTSTART)
						.flatMap(ds -> ds.<net.fortuna.ical4j.model.Parameter>getParameter(
						Parameter.VALUE))
						.map(v -> Value.DATE.equals(v))
						.orElse(false);
						
						if (allDay) {
							// 종일 이벤트 처리 (그대로 유지)
							Collection<Period<LocalDate>> periods = vevent.calculateRecurrenceSet(datePeriod);
							if (periods == null || periods.isEmpty()) continue;
							for (Period<LocalDate> p : periods) 
							{
								ZonedDateTime evStart = p.getStart().atStartOfDay(ZoneId.systemDefault());
								ZonedDateTime evEnd = p.getEnd().atStartOfDay(ZoneId.systemDefault());
								result.add(new CalendarEvent(uid, title, evStart, evEnd, true));
							}
							} else {
							// ✅ 수정: 시간 기반 이벤트 - Instant 직접 사용
							Collection<Period<Instant>> periods = vevent.calculateRecurrenceSet(instantPeriod);
							if (periods == null || periods.isEmpty()) continue;
							for (Period<Instant> p : periods) 
							{
								// Instant를 바로 ZonedDateTime으로 변환
								ZonedDateTime evStart = p.getStart().atZone(ZoneId.systemDefault());
								ZonedDateTime evEnd = p.getEnd().atZone(ZoneId.systemDefault());
								result.add(new CalendarEvent(uid, title, evStart, evEnd, false));
							}
						}
						} catch (Exception e) {
						System.out.println("[NaverCal ERROR] VEVENT 처리 오류: " + e.getMessage());
					}
				}
				} catch (ParserException e) {
				System.out.println("[NaverCal ERROR] ICS 파싱 오류: " + e.getMessage());
				} catch (Exception e) {
				System.out.println("[NaverCal ERROR] 예상치 못한 오류: " + e.getMessage());
			}
			return result;
		}
		
		
		private ZonedDateTime extractZonedDateTime(VEvent vevent, String propName,
			LocalDate occurrenceDate) {
			try {
				Optional<net.fortuna.ical4j.model.Property> propOpt =
                vevent.getProperty(propName);
				if (!propOpt.isPresent()) {
					return occurrenceDate.atStartOfDay(ZoneId.systemDefault());
				}
				String val = propOpt.get().getValue();
				if (val == null || val.length() < 8) {
					return occurrenceDate.atStartOfDay(ZoneId.systemDefault());
				}
				if (val.length() == 8) {
					return occurrenceDate.atStartOfDay(ZoneId.systemDefault());
				}
				int tIdx = val.indexOf('T');
				if (tIdx < 0) return occurrenceDate.atStartOfDay(ZoneId.systemDefault());
				String timePart = val.substring(tIdx + 1).replace("Z", "");
				int hh = Integer.parseInt(timePart.substring(0, 2));
				int mm = Integer.parseInt(timePart.substring(2, 4));
				int ss = timePart.length() >= 6 ? Integer.parseInt(timePart.substring(4, 6)) : 0;
				
				boolean isUtc = val.endsWith("Z");
				if (isUtc) {
					ZonedDateTime utcBase = occurrenceDate.atTime(hh, mm, ss)
                    .atZone(ZoneOffset.UTC);
					return utcBase.withZoneSameInstant(ZoneId.systemDefault());
					} else {
					return occurrenceDate.atTime(hh, mm, ss)
                    .atZone(ZoneId.systemDefault());
				}
				} catch (Exception e) {
				System.out.println("[NaverCal ERROR] extractZonedDateTime 오류: " + e.getMessage());
				return occurrenceDate.atStartOfDay(ZoneId.systemDefault());
			}
		}
		
		private List<String> extractIcsHrefs(String xmlResp) {
			List<String> hrefs = new ArrayList<>();
			try {
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				dbf.setNamespaceAware(true);
				dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
				dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
				DocumentBuilder db = dbf.newDocumentBuilder();
				db.setErrorHandler(null);
				Document doc = db.parse(new InputSource(new StringReader(xmlResp)));
				
				NodeList responses = doc.getElementsByTagNameNS("*", "response");
				for (int i = 0; i < responses.getLength(); i++) {
					Element resp = (Element) responses.item(i);
					NodeList hrefNodes = resp.getElementsByTagNameNS("*", "href");
					if (hrefNodes.getLength() == 0) continue;
					String href = hrefNodes.item(0).getTextContent().trim();
					if (href.endsWith(".ics")) hrefs.add(href);
				}
				} catch (Exception e) {
				System.out.println("[NaverCal ERROR] ICS href 추출 오류: " + e.getMessage());
			}
			return hrefs;
		}
		
		private List<String> fetchCalendarUrls(String homeUrl) {
			List<String> urls = new ArrayList<>();
			try {
				String resp = caldavRequest("PROPFIND", homeUrl,
				propfindCollectionBody(), "application/xml", "1");
				if (resp == null) return urls;
				
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				dbf.setNamespaceAware(true);
				dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
				dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
				DocumentBuilder db = dbf.newDocumentBuilder();
				db.setErrorHandler(null);
				Document doc = db.parse(new InputSource(new StringReader(resp)));
				
				NodeList responses = doc.getElementsByTagNameNS("*", "response");
				for (int i = 0; i < responses.getLength(); i++) {
					Element response = (Element) responses.item(i);
					String xml = nodeToString(response);
					if (!xml.contains("calendar")) continue;
					if (xml.contains("principal")) continue;
					
					NodeList hrefs = response.getElementsByTagNameNS("*", "href");
					if (hrefs.getLength() == 0) continue;
					String href = hrefs.item(0).getTextContent().trim();
					String absUrl = href.startsWith("http") ? href : CALDAV_BASE + href;
					String normalHome = homeUrl.replaceAll("/$", "");
					if (!absUrl.replaceAll("/$", "").equals(normalHome)) {
						urls.add(absUrl);
					}
				}
				} catch (Exception e) {
				System.out.println("[NaverCal ERROR] 캘린더 URL 조회 오류: " + e.getMessage());
			}
			return urls;
		}
		
		private String nodeToString(Node node) {
			StringBuilder sb = new StringBuilder();
			nodeToStringHelper(node, sb);
			return sb.toString();
		}
		
		private void nodeToStringHelper(Node node, StringBuilder sb) {
			sb.append('<').append(node.getNodeName()).append('>');
			NodeList children = node.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (child.getNodeType() == Node.ELEMENT_NODE) nodeToStringHelper(child, sb);
				else if (child.getNodeType() == Node.TEXT_NODE) sb.append(child.getNodeValue());
			}
			sb.append("</").append(node.getNodeName()).append('>');
		}
		
		private String extractHref(String xml, String tagName) {
			Pattern pat = Pattern.compile(
				"<[^>]*" + tagName + "[^>]*>\\s*<[^>]*href[^>]*>\\s*(.*?)\\s*</",
			Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
			Matcher mat = pat.matcher(xml);
			return mat.find() ? mat.group(1).trim() : null;
		}
		
		private String propfindPrincipalBody() {
			return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
			+ "<D:propfind xmlns:D=\"DAV:\">\n"
			+ "  <D:prop><D:current-user-principal/></D:prop>\n"
			+ "</D:propfind>";
		}
		
		private String propfindHomeSetBody() {
			return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
			+ "<D:propfind xmlns:D=\"DAV:\"\n"
			+ "            xmlns:C=\"urn:ietf:params:xml:ns:caldav\">\n"
			+ "  <D:prop><C:calendar-home-set/></D:prop>\n"
			+ "</D:propfind>";
		}
		
		private String propfindCollectionBody() {
			return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
			+ "<D:propfind xmlns:D=\"DAV:\"\n"
			+ "            xmlns:C=\"urn:ietf:params:xml:ns:caldav\">\n"
			+ "  <D:prop>\n"
			+ "    <D:resourcetype/>\n"
			+ "    <D:displayname/>\n"
			+ "  </D:prop>\n"
			+ "</D:propfind>";
		}
		
		private String propfindIcsBody() {
			return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
			+ "<D:propfind xmlns:D=\"DAV:\">\n"
			+ "  <D:prop><D:getetag/></D:prop>\n"
			+ "</D:propfind>";
		}
		
		private String caldavRequest(String method, String urlStr,
			String body, String contentType, String depth) throws IOException {
			URI uri = URI.create(urlStr);
			String host = uri.getHost();
			int port = uri.getPort() < 0 ? 443 : uri.getPort();
			String path = uri.getRawPath();
			if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
			
			byte[] bodyBytes = (body != null && !body.isEmpty())
			? body.getBytes(StandardCharsets.UTF_8)
			: new byte[0];
			
			String password = new String(naverPassword);
			String encodedAuth = Base64.getEncoder().encodeToString(
			(naverId + ":" + password).getBytes(StandardCharsets.UTF_8));
			Arrays.fill(password.toCharArray(), ' ');
			
			StringBuilder req = new StringBuilder();
			req.append(method).append(" ").append(path).append(" HTTP/1.1\r\n");
			req.append("Host: ").append(host).append("\r\n");
			req.append("Authorization: Basic ").append(encodedAuth).append("\r\n");
			req.append("Content-Type: ").append(contentType).append("; charset=UTF-8\r\n");
			req.append("Depth: ").append(depth).append("\r\n");
			req.append("Prefer: return-minimal\r\n");
			if (bodyBytes.length > 0)
            req.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
			req.append("Connection: close\r\n");
			req.append("\r\n");
			/*
				// 요청 로그 (Authorization 마스킹)
				String reqLog = req.toString().replace(
				"Authorization: Basic " + encodedAuth,
				"Authorization: Basic ***MASKED***");
				System.out.println("[NaverCal REQ] " + method + " " + path);
				if (body != null && !body.isEmpty()) {
				System.out.println("[NaverCal REQ BODY] " + body.replaceAll("\\s+", " ").trim());
				}
			*/
			
			javax.net.ssl.SSLSocketFactory sf =
            (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
			
			try (javax.net.ssl.SSLSocket sock =
				(javax.net.ssl.SSLSocket) sf.createSocket(host, port)) {
				
				sock.setSoTimeout(15000);
				javax.net.ssl.SSLParameters params = sock.getSSLParameters();
				params.setServerNames(Collections.singletonList(
				new javax.net.ssl.SNIHostName(host)));
				sock.setSSLParameters(params);
				sock.startHandshake();
				
				OutputStream out = sock.getOutputStream();
				out.write(req.toString().getBytes(StandardCharsets.UTF_8));
				if (bodyBytes.length > 0) out.write(bodyBytes);
				out.flush();
				
				InputStream in = sock.getInputStream();
				ByteArrayOutputStream buf = new ByteArrayOutputStream();
				byte[] tmp = new byte[4096];
				int n;
				while ((n = in.read(tmp)) != -1) buf.write(tmp, 0, n);
				
				String raw = buf.toString(StandardCharsets.UTF_8);
				
				int statusCode = extractStatusCode(raw);
				if (statusCode == 200) {				
				}
				else
				{
					System.out.println("[NaverCal RES] " + method + " " + path + " → " + statusCode);
				}
				
				if (statusCode == 401) {
					System.out.println("[NaverCal ERROR] 인증 실패 (401) - 아이디/비밀번호 확인 필요");
					return null;
				}
				if (statusCode == 0 || statusCode >= 500) {
					System.out.println("[NaverCal ERROR] HTTP 오류: " + statusCode);
					return null;
				}
				
				int headerEnd = raw.indexOf("\r\n\r\n");
				if (headerEnd < 0) return raw;
				String headers = raw.substring(0, headerEnd).toLowerCase(Locale.ROOT);
				String bodyPart = raw.substring(headerEnd + 4);
				
				if (headers.contains("transfer-encoding: chunked")) {
					bodyPart = dechunk(bodyPart);
				}
				/*
					if (bodyPart.length() > 200) {
					System.out.println("[NaverCal RES BODY] " + bodyPart.substring(0, 200) + "...");
					} else {
					System.out.println("[NaverCal RES BODY] " + bodyPart);
					}
				*/
				return bodyPart;
			}
		}
		
		private int extractStatusCode(String response) {
			int crlf = response.indexOf("\r\n");
			if (crlf > 0) {
				String[] parts = response.substring(0, crlf).split(" ", 3);
				if (parts.length >= 2) {
					try {
						return Integer.parseInt(parts[1]);
					} catch (NumberFormatException ignore) {}
				}
			}
			return 0;
		}
		
		private String dechunk(String chunked) {
			StringBuilder sb = new StringBuilder();
			int pos = 0;
			while (pos < chunked.length()) {
				int lineEnd = chunked.indexOf("\r\n", pos);
				if (lineEnd < 0) break;
				String sizeLine = chunked.substring(pos, lineEnd).trim();
				if (sizeLine.isEmpty()) {
					pos = lineEnd + 2;
					continue;
				}
				int size;
				try {
					size = Integer.parseInt(sizeLine.split(";")[0].trim(), 16);
					} catch (Exception e) {
					System.out.println("[NaverCal ERROR] dechunk 오류: " + e.getMessage());
					break;
				}
				if (size == 0) break;
				pos = lineEnd + 2;
				if (pos + size > chunked.length()) size = chunked.length() - pos;
				sb.append(chunked, pos, pos + size);
				pos += size + 2;
			}
			return sb.toString();
		}
		
		public static String formatEvents(String title, List<CalendarEvent> events) {
			StringBuilder sb = new StringBuilder();
			sb.append("📅 ").append(title).append("\n");
			sb.append("─────────────────\n");
			if (events.isEmpty()) {
				sb.append("일정이 없습니다.\n");
				} else {
				DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN);
				DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
				String lastDate = "";
				for (CalendarEvent e : events) {
					String dateStr = e.startTime.format(dateFmt);
					if (!dateStr.equals(lastDate)) {
						if (!lastDate.isEmpty()) sb.append("\n");
						sb.append("📆 ").append(dateStr).append("\n");
						lastDate = dateStr;
					}
					if (e.allDay) {
						sb.append("  • ").append(e.title).append(" (종일)\n");
						} else {
						sb.append("  • ").append(e.startTime.format(timeFmt))
						.append(" ").append(e.title).append("\n");
					}
				}
			}
			return sb.toString();
		}
		
		public void cleanup() {
			processedEventKeys.clear();
			if (naverPassword != null) {
				Arrays.fill(naverPassword, ' ');
			}
			System.out.println("[NaverCal] 리소스 정리 완료");
		}
		
		public static class CalendarEvent {
			public final String id;
			public final String title;
			public final ZonedDateTime startTime;
			public final ZonedDateTime endTime;
			public final boolean allDay;
			
			public CalendarEvent(String id, String title,
				ZonedDateTime startTime, ZonedDateTime endTime, boolean allDay) {
				this.id = id;
				this.title = title;
				this.startTime = startTime;
				this.endTime = endTime;
				this.allDay = allDay;
			}
		}
}