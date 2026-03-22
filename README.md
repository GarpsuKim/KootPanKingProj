⚠️ Windows 보안 경고 해결 방법

이 프로그램은 개인 개발자 [김갑수 , garpsu@naver.com ]가 Java로 개발하여 배포한 EXE 파일입니다.

Windows SmartScreen이 "알 수 없는 게시자" 경고를 표시할 수 있습니다. 이는 오탐(False Positive)이며, 악성 소프트웨어가 아닙니다.

🛡️ 설치 시 경고가 뜨는 경우
-----------
STEP 1 — ZIP 파일을 다운로드 받은 직후, 압축 풀기 전에 아래 작업을 먼저 하세요.
-----------
다운로드된 .zip 파일을 우클릭
속성 클릭
하단의 "차단 해제" 체크박스 클릭 ✅
적용 , 확인 클릭
이후 압축 해제

<img width="645" height="859" alt="image" src="https://github.com/user-attachments/assets/a51f5d3f-030f-48f9-9d06-0fe9c615757d" />

-----------
STEP 2 — SmartScreen 경고 창이 뜨는 경우
-----------
EXE 실행 시 아래와 같은 파란 경고창이 뜨면:

Windows의 PC 보호
Microsoft SmartScreen이 인식할 수 없는 앱의 시작을 방지했습니다.
→ "추가 정보" 클릭 → "실행" 클릭 하면 정상 실행됩니다.

순서	화면
① 경고창에서 "추가 정보" 클릭	파란 SmartScreen 팝업
② "실행" 버튼 클릭	게시자 정보 + 실행 버튼 표시

-----------
STEP 3 — Windows Defender가 삭제/격리한 경우
-----------

작업 표시줄의 방패 아이콘 클릭 (Windows 보안)

바이러스 및 위협 방지 클릭

보호 기록 클릭

해당 파일 선택 → "복원" 클릭

이후 "장치에서 허용" 클릭

-----------
🔍 이 프로그램이 안전한 이유
-----------

✅ 소스 코드 전체 공개 (이 저장소에서 직접 확인 가능)

✅ 누구나 직접 빌드 가능 (_ReleaseBLD_All.bat 실행)

의심스러우면 직접 빌드하세요 👇

-----------
🔨 직접 빌드하는 방법
-----------

```bat
# 사전 조건: JDK 17 이상 설치
# 저장소 클론 후:

_ReleaseBLD_All.bat
```


# 사전 조건: JDK 17 이상 설치   / 저장소 클론 후: ReleaseBLD_All.bat

빌드 결과물은 dist\KootPanKing\KootPanKing.exe 에 생성됩니다.

📬 문의

경고 외에 실제 오류가 발생하면 garpsu@naver.com 에 제보해 주세요.

■ 프로그램 기능 설명 : 네이버 끝판왕 : (https://blog.naver.com/garpsu/224213400580)

■ ZIP 다운로드 : https://github.com/GarpsuKim/KootPanKing/releases/download/v1.0.0/KootPanKing.zip

//

