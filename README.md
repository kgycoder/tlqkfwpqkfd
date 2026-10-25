# 맞춤법 교정 (SpellFix)

입력하는 즉시 한국어 맞춤법 오류를 찾아 iOS 스타일로 하이라이트하고, ⇧(Shift)를 두 번 빠르게 누르면 한 번에 교정하는 안드로이드 앱입니다.

- **API 키·서버·인터넷 권한 모두 없음.** 규칙 사전(`app/src/main/assets/rules.txt`)이 APK 안에 들어 있어 설치 즉시 오프라인으로 동작합니다.
- 외부 라이브러리(androidx 등) 없이 순수 Android SDK + Kotlin 만 사용합니다.

## APK 받는 법 (GitHub Actions)

1. 이 폴더 전체를 GitHub 저장소에 푸시합니다 (`main` 브랜치).
2. **Actions** 탭 → *Build APK* 실행이 끝나면
   - 실행 화면의 **Artifacts → SpellFix-apk** 에서 내려받거나,
   - **Releases → Nightly build** 의 `SpellFix.apk` 를 바로 받습니다.
3. 폰에서 APK 를 열고 "출처를 알 수 없는 앱 설치"를 허용하면 끝. (debug 키로 서명되어 키스토어 설정이 필요 없습니다.)

수동 실행은 Actions → *Build APK* → *Run workflow*. `v1.0` 같은 태그를 푸시하면 정식 Release 로 올라갑니다.

## 사용법

| 동작 | 설명 |
|---|---|
| 타이핑 | 오류가 생기면 해당 부분이 푸른 반투명 하이라이트로 표시되고, 위/아래에 교정 카드가 뜹니다. |
| 카드 탭 | 그 오류 하나만 교정합니다. |
| ⇧ 키 두 번 | 모든 오류를 한 번에 교정합니다. 화면의 ⇧ 키, 또는 외장(블루투스) 키보드의 Shift 모두 지원합니다. |

## 맞춤법 규칙 추가

`app/src/main/assets/rules.txt` 에 한 줄을 더하면 됩니다.

```
정규식 => 교정 결과 => 이유
됬 => 됐 => '되었'의 준말은 '됐'이에요
```

받침 조건이 필요한 규칙(-ㄹ게, -ㄹ 거, -ㄹ 수 있다 등)은 `SpellEngine.kt` 에 있습니다.
`test/EngineTest.kt` 에 엔진 검증 케이스(오류 34건 / 정상 문장 29건)가 있습니다.

## 구조

```
app/src/main/java/com/spellfix/korean/
  SpellEngine.kt     오프라인 규칙 기반 검사기
  SpellEditText.kt   하이라이트(둥근 반투명 파랑 + 양끝 짙은 커서) 그리는 입력창, Shift 더블탭(외장 키보드)
  CorrectionCard.kt  반투명 파란 교정 카드
  ShiftKeyView.kt    화면 위 ⇧ 키(두 번 탭)
  MainActivity.kt    화면 구성
```
