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
    
    public String naverId   = "";
    public String naverPassword = "";
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
			this.naverId       = id       != null ? id.trim()       : "";
			this.naverPassword = password != null ? password.trim() : "";
			System.out.println("[NaverCal] 자격 증명 설정: " + naverId);
		}
		
		public boolean isInitialized() { return initialized; }

		public static boolean credentialsExist(String id, String pass) {
			return id != null && !id.trim().isEmpty()
            && pass != null && !pass.trim().isEmpty();
		}
		
		public boolean init() {
			if (naverId.isEmpty() || naverPassword.isEmpty()) {
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
			processedEventKeys.clear(); // 조회마다 초기화 (누적 중복 방지)
			
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
						List<String> icsTexts = fetchIcsTexts(calUrl, start, end);
						for (String icsText : icsTexts) {
							if (icsText == null || icsText.isEmpty()) continue;
							try {
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
				"(?s)BEGIN:VALARM.*?END:VALARM\r?\n?", "");

				CalendarBuilder builder = new CalendarBuilder();
				Calendar cal = builder.build(new StringReader(cleanedIcs));

				Period<LocalDate> datePeriod = new Period<>(
					qStart.toLocalDate(), qEnd.toLocalDate().plusDays(1)); // 종일 이벤트용

				for (Object comp : cal.getComponents(Component.VEVENT)) {
					VEvent vevent = (VEvent) comp;
					try {
						String uid = vevent.getUid()
						.map(Uid::getValue)
						.orElse(UUID.randomUUID().toString());

						Summary sumProp = vevent.getSummary();
						String title = (sumProp != null) ? sumProp.getValue() : "(제목 없음)";

						// DTSTART VALUE=DATE 이면 종일, 없거나 DATE-TIME 이면 시간 기반
						boolean allDay = vevent
						.getProperty(net.fortuna.ical4j.model.Property.DTSTART)
						.flatMap(ds -> ds.getParameter(Parameter.VALUE))
						.map(v -> Value.DATE.equals(v))
						.orElse(false);

						if (allDay) {
							// ── 종일 이벤트 ──────────────────────────────
							Collection<Period<LocalDate>> periods =
							vevent.calculateRecurrenceSet(datePeriod);
							if (periods == null || periods.isEmpty()) continue;
							for (Period<LocalDate> p : periods) {
								LocalDate s = p.getStart();
								LocalDate e = p.getEnd();
								// 조회 범위와 겹치는지 확인
								if (e.isBefore(qStart.toLocalDate())) continue;
								if (s.isAfter(qEnd.toLocalDate()))    continue;
								result.add(new CalendarEvent(uid, title,
									s.atStartOfDay(ZoneId.systemDefault()),
									e.atStartOfDay(ZoneId.systemDefault()), true));
							}
						} else {
							// ── 시간 기반 이벤트 ─────────────────────────
							// TZID 로컬타임 / UTC(Z) / floating 모두 ZonedDateTime 으로 읽은 뒤
							// 조회 범위와 직접 비교 (ical4j Period<Instant> 타입 미스매치 우회)
							List<ZonedDateTime[]> occurrences = expandTimed(vevent, qStart, qEnd);
							for (ZonedDateTime[] se : occurrences) {
								result.add(new CalendarEvent(uid, title, se[0], se[1], false));
							}
						}
					} catch (Exception e) {
						String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
						if (msg.contains("Unsupported unit") || msg.contains("Unable to obtain")) {
							System.out.println("[NaverCal DEBUG] 일부 일정 파싱 실패 (무시): " + msg);
						} else {
							System.out.println("[NaverCal ERROR] VEVENT 처리 오류: " + msg);
						}
					}
				}
			} catch (Exception e) {
				System.out.println("[NaverCal ERROR] ICS 파싱 오류: " + e.getMessage());
			}
			return result;
		}

		/**
		 * 시간 기반 VEVENT 의 발생 인스턴스를 조회 범위 내에서 수집.
		 *
		 * ical4j 의 calculateRecurrenceSet(Period<Instant>) 은
		 * TZID 로컬타임 DTSTART 와 타입 미스매치가 생겨 빈 결과를 내는 경우가 있으므로,
		 * DTSTART/RRULE/EXDATE 를 직접 파싱하여 ZonedDateTime 으로 전개한다.
		 *
		 * 처리 순서:
		 *  1) DTSTART 를 ZonedDateTime 으로 파싱 (TZID / Z / floating)
		 *  2) DTEND 또는 DURATION 으로 이벤트 길이 계산
		 *  3) RRULE 이 없으면 단일 이벤트
		 *  4) RRULE 이 있으면 FREQ/INTERVAL/COUNT/UNTIL/BYDAY/BYMONTHDAY 지원
		 *  5) EXDATE 로 예외 날짜 제거
		 */
		private List<ZonedDateTime[]> expandTimed(VEvent vevent,
				ZonedDateTime qStart, ZonedDateTime qEnd) {
			List<ZonedDateTime[]> result = new ArrayList<>();
			try {
				// ── DTSTART ───────────────────────────────────────────
				ZonedDateTime dtStart = parseDtProp(vevent, "DTSTART");
				if (dtStart == null) return result;

				// ── DURATION (초 단위) ────────────────────────────────
				long durationSec = 0;
				ZonedDateTime dtEnd = parseDtProp(vevent, "DTEND");
				if (dtEnd != null) {
					durationSec = java.time.Duration.between(dtStart, dtEnd).getSeconds();
				} else {
					// DURATION 프로퍼티
					Optional<net.fortuna.ical4j.model.Property> durProp =
					vevent.getProperty("DURATION");
					if (durProp.isPresent()) {
						durationSec = parseDuration(durProp.get().getValue());
					}
					if (durationSec == 0) durationSec = 3600; // 기본 1시간
				}
				final long dur = durationSec;

				// ── EXDATE 수집 ───────────────────────────────────────
				Set<String> exDates = new HashSet<>();
				for (Object p : vevent.getProperties("EXDATE")) {
					net.fortuna.ical4j.model.Property ep =
					(net.fortuna.ical4j.model.Property) p;
					for (String v : ep.getValue().split(",")) {
						exDates.add(v.trim().replace("Z","").replace("-","").replace(":",""));
					}
				}

				// ── RRULE ─────────────────────────────────────────────
				Optional<net.fortuna.ical4j.model.Property> rruleProp =
				vevent.getProperty("RRULE");

				if (!rruleProp.isPresent()) {
					// 단일 이벤트
					addIfInRange(result, dtStart, dur, qStart, qEnd, exDates);
					return result;
				}

				// RRULE 파싱
				Map<String,String> rrule = parseRRule(rruleProp.get().getValue());
				String freq     = rrule.getOrDefault("FREQ", "");
				int    interval = Integer.parseInt(rrule.getOrDefault("INTERVAL", "1"));
				int    count    = rrule.containsKey("COUNT")
								? Integer.parseInt(rrule.get("COUNT")) : Integer.MAX_VALUE;
				ZonedDateTime until = rrule.containsKey("UNTIL")
								? parseDateStr(rrule.get("UNTIL"), dtStart.getZone()) : null;
				List<Integer> byDay       = parseByDay(rrule.getOrDefault("BYDAY",""));
				List<Integer> byMonthDay  = parseByMonthDay(rrule.getOrDefault("BYMONTHDAY",""));
				List<Integer> byMonth     = parseByMonth(rrule.getOrDefault("BYMONTH",""));

				// 반복 전개 상한: 조회 종료일 또는 UNTIL 중 이른 것
				ZonedDateTime expandEnd = (until != null && until.isBefore(qEnd)) ? until : qEnd;
				// 시작은 DTSTART 부터 (과거 반복이 조회범위 안에 들어올 수 있음)
				ZonedDateTime cur = dtStart;
				int generated = 0;

				while (!cur.isAfter(expandEnd) && generated < count) {
					// BYDAY / BYMONTHDAY 필터
					if (matchesByDay(cur, byDay) && matchesByMonthDay(cur, byMonthDay)
							&& matchesByMonth(cur, byMonth)) {
						addIfInRange(result, cur, dur, qStart, qEnd, exDates);
						generated++;
					}
					// 다음 발생
					switch (freq) {
						case "DAILY":   cur = cur.plusDays(interval);   break;
						case "WEEKLY":  cur = cur.plusWeeks(interval);  break;
						case "MONTHLY": cur = cur.plusMonths(interval); break;
						case "YEARLY":  cur = cur.plusYears(interval);  break;
						default: return result; // 알 수 없는 FREQ
					}
				}
			} catch (Exception e) {
				System.out.println("[NaverCal ERROR] expandTimed 오류: " + e.getMessage());
			}
			return result;
		}

		/** DTSTART/DTEND 프로퍼티를 ZonedDateTime 으로 변환 */
		private ZonedDateTime parseDtProp(VEvent vevent, String propName) {
			try {
				Optional<net.fortuna.ical4j.model.Property> opt = vevent.getProperty(propName);
				if (!opt.isPresent()) return null;
				net.fortuna.ical4j.model.Property prop = opt.get();
				String val  = prop.getValue();           // e.g. 20260328T090000
				String tzid = "";
				Optional<net.fortuna.ical4j.model.Parameter> tzParam = prop.getParameter("TZID");
				if (tzParam.isPresent()) tzid = tzParam.get().getValue();
				return parseDateStr(val, tzid.isEmpty() ? null
					: ZoneId.of(tzid, ZoneId.SHORT_IDS));
			} catch (Exception e) {
				return null;
			}
		}

		/** 날짜/시간 문자열 → ZonedDateTime */
		private ZonedDateTime parseDateStr(String val, ZoneId zone) {
			if (val == null || val.length() < 8) return null;
			val = val.trim();
			try {
				if (val.length() == 8) {
					// DATE only (종일): 00:00 로컬
					LocalDate d = LocalDate.parse(val, DateTimeFormatter.BASIC_ISO_DATE);
					return d.atStartOfDay(zone != null ? zone : ZoneId.systemDefault());
				}
				boolean isUtc = val.endsWith("Z");
				String core = val.replace("Z","").replace("-","").replace(":","");
				// YYYYMMDDTHHmmss
				LocalDate  date = LocalDate.of(
					Integer.parseInt(core.substring(0,4)),
					Integer.parseInt(core.substring(4,6)),
					Integer.parseInt(core.substring(6,8)));
				int hh = core.length() > 9  ? Integer.parseInt(core.substring(9,11))  : 0;
				int mm = core.length() > 11 ? Integer.parseInt(core.substring(11,13)) : 0;
				int ss = core.length() > 13 ? Integer.parseInt(core.substring(13,15)) : 0;
				LocalDateTime ldt = date.atTime(hh, mm, ss);
				if (isUtc) return ldt.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault());
				return ldt.atZone(zone != null ? zone : ZoneId.systemDefault());
			} catch (Exception e) {
				return null;
			}
		}

		/** DURATION 문자열(PT1H30M 등) → 초 */
		private long parseDuration(String dur) {
			if (dur == null) return 0;
			long total = 0;
			java.util.regex.Matcher m =
			java.util.regex.Pattern.compile("(\\d+)([WDHMS])").matcher(dur.toUpperCase());
			while (m.find()) {
				long v = Long.parseLong(m.group(1));
				switch (m.group(2)) {
					case "W": total += v * 7 * 86400; break;
					case "D": total += v * 86400;      break;
					case "H": total += v * 3600;       break;
					case "M": total += v * 60;         break;
					case "S": total += v;              break;
				}
			}
			return total;
		}

		/** RRULE 문자열 → Map */
		private Map<String,String> parseRRule(String rrule) {
			Map<String,String> map = new LinkedHashMap<>();
			for (String part : rrule.split(";")) {
				int eq = part.indexOf('=');
				if (eq > 0) map.put(part.substring(0,eq).trim().toUpperCase(),
									part.substring(eq+1).trim());
			}
			return map;
		}

		/** BYDAY=MO,WE,FR → [2,4,6] (Calendar.DAY_OF_WEEK 값) */
		private List<Integer> parseByDay(String byday) {
			List<Integer> list = new ArrayList<>();
			if (byday.isEmpty()) return list;
			Map<String,Integer> dow = new java.util.HashMap<>();
			dow.put("SU",1); dow.put("MO",2); dow.put("TU",3); dow.put("WE",4);
			dow.put("TH",5); dow.put("FR",6); dow.put("SA",7);
			for (String d : byday.split(",")) {
				String key = d.replaceAll("[^A-Z]","");
				if (dow.containsKey(key)) list.add(dow.get(key));
			}
			return list;
		}

		/** BYMONTHDAY=1,15 → [1,15] */
		private List<Integer> parseByMonthDay(String s) {
			List<Integer> list = new ArrayList<>();
			if (s.isEmpty()) return list;
			for (String v : s.split(",")) {
				try { list.add(Integer.parseInt(v.trim())); } catch (Exception ignored) {}
			}
			return list;
		}

		/** BYMONTH=1,6 → [1,6] */
		private List<Integer> parseByMonth(String s) {
			List<Integer> list = new ArrayList<>();
			if (s.isEmpty()) return list;
			for (String v : s.split(",")) {
				try { list.add(Integer.parseInt(v.trim())); } catch (Exception ignored) {}
			}
			return list;
		}

		private boolean matchesByDay(ZonedDateTime dt, List<Integer> byDay) {
			if (byDay.isEmpty()) return true;
			int dow = dt.getDayOfWeek().getValue() % 7 + 1; // ISO→Calendar (SU=1)
			return byDay.contains(dow);
		}

		private boolean matchesByMonthDay(ZonedDateTime dt, List<Integer> byMonthDay) {
			if (byMonthDay.isEmpty()) return true;
			return byMonthDay.contains(dt.getDayOfMonth());
		}

		private boolean matchesByMonth(ZonedDateTime dt, List<Integer> byMonth) {
			if (byMonth.isEmpty()) return true;
			return byMonth.contains(dt.getMonthValue());
		}

		/** 발생 인스턴스가 조회 범위 안에 있고 EXDATE 가 아니면 result 에 추가 */
		private void addIfInRange(List<ZonedDateTime[]> result,
				ZonedDateTime start, long durSec,
				ZonedDateTime qStart, ZonedDateTime qEnd,
				Set<String> exDates) {
			ZonedDateTime end = start.plusSeconds(durSec);
			// 이벤트가 조회 범위와 겹치는지 (시작 < 조회끝 && 끝 > 조회시작)
			if (!start.isBefore(qEnd) || !end.isAfter(qStart)) return;
			// EXDATE 확인
			String key = start.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
			if (exDates.contains(key)) return;
			result.add(new ZonedDateTime[]{ start, end });
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
		
		/**
		 * calendar-query REPORT 요청 본문.
		 * 서버 측 날짜 범위 필터(time-range) + calendar-data(ICS 본문) 포함.
		 * → 캘린더 1개당 네트워크 요청 1회로 모든 ICS를 한 번에 수신.
		 */
		/**
		 * 캘린더 URL에서 조회 범위 내 ICS 본문 목록을 가져온다.
		 *
		 * 1단계: PROPFIND(depth=1) → 전체 .ics href 목록 + etag 수신 (빠름)
		 * 2단계: calendar-multiget REPORT → href 목록을 서버에 보내 ICS 본문 일괄 수신 (1회)
		 *
		 * 네이버 CalDAV는 time-range 필터(calendar-query)를 무시하므로
		 * multiget 으로 전체 ICS 를 한 번에 받은 뒤 클라이언트에서 날짜 필터링한다.
		 */
		private List<String> fetchIcsTexts(String calUrl,
				ZonedDateTime start, ZonedDateTime end) throws IOException {

			// ── 1단계: PROPFIND → href 목록 ─────────────────────────
			String propResp = caldavRequest("PROPFIND", calUrl,
				propfindIcsBody(), "application/xml", "1");
			if (propResp == null || propResp.isEmpty()) return new ArrayList<>();

			List<String> hrefs = extractIcsHrefs(propResp);
			String calName = calUrl.replaceAll(".*/calendar/", "");
			System.out.println("[NaverCal] " + calName + " → .ics " + hrefs.size() + "건");
			if (hrefs.isEmpty()) return new ArrayList<>();

			// ── 2단계: calendar-multiget REPORT → ICS 본문 일괄 수신 ─
			try {
				String multigetBody = calendarMultigetBody(hrefs);
				String multigetResp = caldavRequest("REPORT", calUrl,
					multigetBody, "application/xml", "1");
				if (multigetResp != null && !multigetResp.trim().isEmpty()) {
					List<String> icsTexts = extractCalendarData(multigetResp);
					if (!icsTexts.isEmpty()) {
						System.out.println("[NaverCal] multiget 성공 → " + calName + " " + icsTexts.size() + "건");
						return icsTexts;
					}
				}
				System.out.println("[NaverCal] multiget 응답 없음 → 개별 GET 폴백");
			} catch (Exception e) {
				System.out.println("[NaverCal] multiget 실패 → 개별 GET 폴백: " + e.getMessage());
			}

			// ── 폴백: 개별 GET ───────────────────────────────────────
			List<String> result = new ArrayList<>();
			for (String href : hrefs) {
				try {
					String absUrl = href.startsWith("http") ? href : CALDAV_BASE + href;
					String icsText = caldavRequest("GET", absUrl, null, "text/calendar", "0");
					if (icsText != null && !icsText.isEmpty()) result.add(icsText);
				} catch (Exception e) {
					System.out.println("[NaverCal ERROR] GET 실패: " + e.getMessage());
				}
			}
			return result;
		}



		/**
		 * calendar-multiget REPORT 요청 본문.
		 * href 목록을 서버에 전달하면 해당 ICS 본문을 한 번에 반환한다.
		 * time-range 필터 없이 href 기반으로 동작하므로 네이버도 지원한다.
		 */
		private String calendarMultigetBody(List<String> hrefs) {
			StringBuilder sb = new StringBuilder();
			sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			sb.append("<C:calendar-multiget xmlns:D=\"DAV:\"\n");
			sb.append("                     xmlns:C=\"urn:ietf:params:xml:ns:caldav\">\n");
			sb.append("  <D:prop>\n");
			sb.append("    <D:getetag/>\n");
			sb.append("    <C:calendar-data/>\n");
			sb.append("  </D:prop>\n");
			for (String href : hrefs) {
				// href가 절대경로(/caldav/...)면 그대로, 상대경로면 그대로 사용
				String h = href.startsWith("http")
					? URI.create(href).getRawPath()  // 절대URL이면 path만 추출
					: href;
				sb.append("  <D:href>").append(h).append("</D:href>\n");
			}
			sb.append("</C:calendar-multiget>");
			return sb.toString();
		}

		private String calendarQueryBody(ZonedDateTime start, ZonedDateTime end) {
			// CalDAV time-range 는 UTC ISO8601 기본형 (Z 접미사)
			DateTimeFormatter fmt = DateTimeFormatter
				.ofPattern("yyyyMMdd'T'HHmmss'Z'")
				.withZone(ZoneOffset.UTC);
			String tStart = fmt.format(start.toInstant());
			String tEnd   = fmt.format(end.toInstant());
			return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<C:calendar-query xmlns:D=\"DAV:\"\n"
				+ "                  xmlns:C=\"urn:ietf:params:xml:ns:caldav\">\n"
				+ "  <D:prop>\n"
				+ "    <D:getetag/>\n"
				+ "    <C:calendar-data/>\n"
				+ "  </D:prop>\n"
				+ "  <C:filter>\n"
				+ "    <C:comp-filter name=\"VCALENDAR\">\n"
				+ "      <C:comp-filter name=\"VEVENT\">\n"
				+ "        <C:time-range start=\"" + tStart + "\" end=\"" + tEnd + "\"/>\n"
				+ "      </C:comp-filter>\n"
				+ "    </C:comp-filter>\n"
				+ "  </C:filter>\n"
				+ "</C:calendar-query>";
		}

		/**
		 * REPORT 응답 XML에서 calendar-data 요소(ICS 본문) 목록 추출.
		 * XML 파서로 calendar-data 태그 내용을 수집한다.
		 */
		private List<String> extractCalendarData(String xmlResp) {
			List<String> list = new ArrayList<>();
			try {
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				dbf.setNamespaceAware(true);
				dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
				dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
				DocumentBuilder db = dbf.newDocumentBuilder();
				db.setErrorHandler(null);
				Document doc = db.parse(new InputSource(new StringReader(xmlResp)));
				// calendar-data 는 네임스페이스 무관하게 로컬명으로 검색
				NodeList nodes = doc.getElementsByTagNameNS("*", "calendar-data");
				for (int i = 0; i < nodes.getLength(); i++) {
					String text = nodes.item(i).getTextContent();
					if (text != null && !text.trim().isEmpty()) list.add(text.trim());
				}
			} catch (Exception e) {
				System.out.println("[NaverCal ERROR] calendar-data 추출 오류: " + e.getMessage());
			}
			return list;
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
			
			String encodedAuth = Base64.getEncoder().encodeToString(
			(naverId + ":" + naverPassword).getBytes(StandardCharsets.UTF_8));
			
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